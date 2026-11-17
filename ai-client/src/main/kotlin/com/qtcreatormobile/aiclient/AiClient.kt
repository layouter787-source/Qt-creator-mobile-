package com.qtcreatormobile.aiclient

import org.json.JSONArray
import org.json.JSONObject

/** Monta a URL base removendo sufixos que o usuario costuma colar junto (ex.: /chat/completions). */
internal fun normalizeBase(baseUrl: String): String {
    var base = baseUrl.trim().trimEnd('/')
    var changed = true
    while (changed) {
        changed = false
        for (suffix in listOf("/chat/completions", "/messages", "/models")) {
            if (base.endsWith(suffix)) {
                base = base.removeSuffix(suffix).trimEnd('/')
                changed = true
            }
        }
    }
    return base
}

/** Interpreta as linhas de uma resposta em streaming (SSE) e devolve so o texto novo. */
internal class DeltaParser(private val format: ApiFormat) {
    var error: String? = null
        private set
    private val raw = StringBuilder()

    fun accept(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith(":") || t.startsWith("event:")) return null
        if (!t.startsWith("data:")) {
            raw.append(line).append('\n')
            return null
        }
        val data = t.removePrefix("data:").trim()
        if (data.isEmpty() || data == "[DONE]") return null
        val json = try {
            JSONObject(data)
        } catch (_: Exception) {
            return null
        }
        val err = json.opt("error")
        if (err is JSONObject) {
            error = err.optString("message", "Erro do provedor")
            return null
        }
        if (err is String) {
            error = err
            return null
        }
        if (json.optString("type") == "error") {
            error = "Erro do provedor"
            return null
        }
        val text = when (format) {
            ApiFormat.OPENAI_COMPATIBLE ->
                json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content", "")
            ApiFormat.ANTHROPIC ->
                if (json.optString("type") == "content_block_delta") json.optJSONObject("delta")?.optString("text", "") else null
        }
        return text?.takeIf { it.isNotEmpty() }
    }

    /** Para gateways que ignoram `stream` e devolvem um JSON comum. */
    fun fallback(): String? {
        val body = raw.toString().trim()
        if (body.isEmpty()) return null
        return try {
            val json = JSONObject(body)
            when (format) {
                ApiFormat.OPENAI_COMPATIBLE ->
                    json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")
                ApiFormat.ANTHROPIC -> {
                    val parts: JSONArray = json.optJSONArray("content") ?: return null
                    buildString {
                        for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
                    }
                }
            }?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}

/** Cliente de chat para um provedor. Uma instancia por requisicao e suficiente. */
class AiClient(
    private val config: ProviderConfig,
    private val transport: HttpTransport = JavaHttpTransport(),
) {

    private fun base(): String {
        val base = normalizeBase(config.baseUrl)
        if (!(base.startsWith("https://") || base.startsWith("http://"))) {
            throw AiException("A URL base precisa começar com https:// (ou http://).")
        }
        return base
    }

    private fun headers(): Map<String, String> {
        val headers = linkedMapOf("Content-Type" to "application/json", "Accept" to "application/json, text/event-stream")
        when (config.format) {
            ApiFormat.OPENAI_COMPATIBLE -> if (config.apiKey.isNotBlank()) headers["Authorization"] = "Bearer ${config.apiKey.trim()}"
            ApiFormat.ANTHROPIC -> {
                headers["x-api-key"] = config.apiKey.trim()
                headers["anthropic-version"] = "2023-06-01"
            }
        }
        return headers
    }

    internal fun chatUrl(): String = base() + if (config.format == ApiFormat.ANTHROPIC) "/messages" else "/chat/completions"

    internal fun chatBody(messages: List<ChatMessage>, maxTokens: Int): String {
        val json = JSONObject()
        json.put("model", config.model.trim())
        json.put("stream", true)
        when (config.format) {
            ApiFormat.OPENAI_COMPATIBLE -> {
                json.put("messages", JSONArray(messages.map { JSONObject().put("role", it.role).put("content", it.content) }))
            }
            ApiFormat.ANTHROPIC -> {
                json.put("max_tokens", maxTokens)
                val system = messages.filter { it.role == ChatMessage.SYSTEM }.joinToString("\n\n") { it.content }
                if (system.isNotBlank()) json.put("system", system)
                json.put(
                    "messages",
                    JSONArray(messages.filter { it.role != ChatMessage.SYSTEM }.map { JSONObject().put("role", it.role).put("content", it.content) }),
                )
            }
        }
        return json.toString()
    }

    /** Envia a conversa e entrega o texto aos poucos em [onDelta]. Devolve a resposta completa. */
    fun chat(
        messages: List<ChatMessage>,
        maxTokens: Int = 4096,
        shouldStop: () -> Boolean = { false },
        onDelta: (String) -> Unit,
    ): String {
        if (config.model.isBlank()) throw AiException("Escolha um modelo nas configurações do provedor.")
        val parser = DeltaParser(config.format)
        val full = StringBuilder()
        val result = transport.send(
            "POST", chatUrl(), headers(), chatBody(messages, maxTokens),
            onLine = { line -> parser.accept(line)?.let { full.append(it); onDelta(it) } },
            shouldStop = shouldStop,
        )
        if (result.status == 499) return full.toString()
        if (result.status !in 200..299) throw AiException(errorMessage(result), result.status)
        parser.error?.let { throw AiException(it) }
        if (full.isEmpty()) {
            parser.fallback()?.let { full.append(it); onDelta(it) }
        }
        return full.toString()
    }

    /** Lista os modelos do provedor (tambem serve de teste de chave/URL). */
    fun listModels(): List<String> {
        val result = transport.send("GET", base() + "/models", headers(), null, null) { false }
        if (result.status !in 200..299) throw AiException(errorMessage(result), result.status)
        return try {
            val data = JSONObject(result.body).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }.sorted()
        } catch (e: Exception) {
            throw AiException("Resposta inesperada ao listar modelos.")
        }
    }

    internal fun errorMessage(result: HttpResult): String {
        val detail = try {
            val json = JSONObject(result.body)
            when (val err = json.opt("error")) {
                is JSONObject -> err.optString("message")
                is String -> err
                else -> json.optString("message")
            }
        } catch (_: Exception) {
            result.body.trim().take(300)
        }
        val hint = when (result.status) {
            401, 403 -> " (confira a chave de API)"
            404 -> " (confira a URL base e o nome do modelo)"
            429 -> " (limite de uso ou saldo)"
            else -> ""
        }
        return "HTTP ${result.status}$hint${if (detail.isNotBlank()) ": $detail" else ""}"
    }
}
