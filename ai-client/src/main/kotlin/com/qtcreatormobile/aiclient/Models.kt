package com.qtcreatormobile.aiclient

/** Protocolo que o provedor fala. */
enum class ApiFormat(val label: String) {
    /** POST {base}/chat/completions com Authorization: Bearer (OpenAI, OpenRouter, NVIDIA, gateways...). */
    OPENAI_COMPATIBLE("OpenAI (chat completions)"),

    /** POST {base}/messages com x-api-key (Anthropic). */
    ANTHROPIC("Anthropic (messages)"),
}

data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }
}

/** Um provedor configurado pelo usuario. [apiKey] fica em memoria; quem persiste deve cifrar. */
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val format: ApiFormat,
    val model: String,
    val apiKey: String,
)

/** Modelo pronto para o usuario so colar a chave. */
data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val format: ApiFormat,
    val defaultModel: String,
    val keyHint: String,
)

object ProviderPresets {
    val OPENAI = ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", ApiFormat.OPENAI_COMPATIBLE, "gpt-4o-mini", "sk-...")
    val ANTHROPIC = ProviderPreset("anthropic", "Anthropic", "https://api.anthropic.com/v1", ApiFormat.ANTHROPIC, "claude-sonnet-4-5", "sk-ant-...")
    val OPENROUTER = ProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", ApiFormat.OPENAI_COMPATIBLE, "openai/gpt-4o-mini", "sk-or-...")
    val NVIDIA = ProviderPreset("nvidia", "NVIDIA", "https://integrate.api.nvidia.com/v1", ApiFormat.OPENAI_COMPATIBLE, "meta/llama-3.1-70b-instruct", "nvapi-...")
    val CUSTOM = ProviderPreset("custom", "Gateway personalizado", "", ApiFormat.OPENAI_COMPATIBLE, "", "chave da API")

    val all: List<ProviderPreset> = listOf(OPENAI, ANTHROPIC, OPENROUTER, NVIDIA, CUSTOM)
}

/** Erro de rede, de protocolo ou devolvido pelo provedor, com mensagem pronta para mostrar. */
class AiException(message: String, val status: Int? = null) : Exception(message)
