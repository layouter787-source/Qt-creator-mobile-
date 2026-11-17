package com.qtcreatormobile.aiclient

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeTransport(private val status: Int = 200, private val lines: List<String> = emptyList(), private val body: String = "") : HttpTransport {
    var lastUrl = ""
    var lastHeaders: Map<String, String> = emptyMap()
    var lastBody: String? = null
    override fun send(method: String, url: String, headers: Map<String, String>, body: String?, onLine: ((String) -> Unit)?, shouldStop: () -> Boolean): HttpResult {
        lastUrl = url
        lastHeaders = headers
        lastBody = body
        if (status in 200..299 && onLine != null) {
            lines.forEach { if (!shouldStop()) onLine(it) }
            return HttpResult(status, "")
        }
        return HttpResult(status, this.body)
    }
}

class AiClientTest {
    private fun openai(base: String = "https://api.openai.com/v1") =
        ProviderConfig("1", "OpenAI", base, ApiFormat.OPENAI_COMPATIBLE, "gpt-x", "sk-test")

    private val anthropic = ProviderConfig("2", "Anthropic", "https://api.anthropic.com/v1", ApiFormat.ANTHROPIC, "claude-x", "sk-ant")

    @Test
    fun `normalizes pasted endpoint suffixes`() {
        assertEquals("https://x.com/v1", normalizeBase("https://x.com/v1/chat/completions/"))
        assertEquals("https://x.com/v1", normalizeBase(" https://x.com/v1/ "))
        assertEquals("https://api.anthropic.com/v1", normalizeBase("https://api.anthropic.com/v1/messages"))
    }

    @Test
    fun `openai streaming collects deltas and sends bearer token`() {
        val transport = FakeTransport(
            lines = listOf(
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"content\":\"Olá\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\" mundo\"}}]}",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]",
            ),
        )
        val parts = mutableListOf<String>()
        val result = AiClient(openai(), transport).chat(listOf(ChatMessage("user", "oi"))) { parts += it }
        assertEquals("Olá mundo", result)
        assertEquals(listOf("Olá", " mundo"), parts)
        assertEquals("https://api.openai.com/v1/chat/completions", transport.lastUrl)
        assertEquals("Bearer sk-test", transport.lastHeaders["Authorization"])
        val body = JSONObject(transport.lastBody!!)
        assertEquals("gpt-x", body.getString("model"))
        assertTrue(body.getBoolean("stream"))
        assertEquals("user", body.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test
    fun `anthropic puts system prompt aside and parses text deltas`() {
        val transport = FakeTransport(
            lines = listOf(
                "event: message_start",
                "data: {\"type\":\"message_start\"}",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Oi\"}}",
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"!\"}}",
                "data: {\"type\":\"message_stop\"}",
            ),
        )
        val result = AiClient(anthropic, transport).chat(
            listOf(ChatMessage("system", "seja breve"), ChatMessage("user", "oi")),
        ) { }
        assertEquals("Oi!", result)
        assertEquals("https://api.anthropic.com/v1/messages", transport.lastUrl)
        assertEquals("sk-ant", transport.lastHeaders["x-api-key"])
        assertEquals("2023-06-01", transport.lastHeaders["anthropic-version"])
        val body = JSONObject(transport.lastBody!!)
        assertEquals("seja breve", body.getString("system"))
        assertEquals(1, body.getJSONArray("messages").length())
        assertTrue(body.getInt("max_tokens") > 0)
    }

    @Test
    fun `falls back to a plain json response when the gateway ignores stream`() {
        val transport = FakeTransport(lines = listOf("{\"choices\":[{\"message\":{\"content\":\"resposta inteira\"}}]}"))
        assertEquals("resposta inteira", AiClient(openai(), transport).chat(listOf(ChatMessage("user", "x"))) { })
    }

    @Test
    fun `http errors carry the provider message and a hint`() {
        val transport = FakeTransport(status = 401, body = "{\"error\":{\"message\":\"Incorrect API key\"}}")
        val error = assertFailsWith<AiException> { AiClient(openai(), transport).chat(listOf(ChatMessage("user", "x"))) { } }
        assertTrue(error.message!!.contains("401"))
        assertTrue(error.message!!.contains("Incorrect API key"))
        assertTrue(error.message!!.contains("chave"))
    }

    @Test
    fun `errors streamed inside a 200 response are raised`() {
        val transport = FakeTransport(lines = listOf("data: {\"error\":{\"message\":\"quota exceeded\"}}"))
        val error = assertFailsWith<AiException> { AiClient(openai(), transport).chat(listOf(ChatMessage("user", "x"))) { } }
        assertTrue(error.message!!.contains("quota exceeded"))
    }

    @Test
    fun `rejects a base url without scheme and a blank model`() {
        assertFailsWith<AiException> { AiClient(openai("api.openai.com/v1"), FakeTransport()).chat(listOf(ChatMessage("user", "x"))) { } }
        assertFailsWith<AiException> { AiClient(openai().copy(model = " "), FakeTransport()).chat(listOf(ChatMessage("user", "x"))) { } }
    }

    @Test
    fun `lists models sorted`() {
        val transport = FakeTransport(body = "{\"data\":[{\"id\":\"b-model\"},{\"id\":\"a-model\"}]}")
        assertEquals(listOf("a-model", "b-model"), AiClient(openai(), transport).listModels())
        assertEquals("https://api.openai.com/v1/models", transport.lastUrl)
    }

    @Test
    fun `presets cover the requested providers`() {
        val ids = ProviderPresets.all.map { it.id }
        assertEquals(listOf("openai", "anthropic", "openrouter", "nvidia", "custom"), ids)
        assertEquals(ApiFormat.ANTHROPIC, ProviderPresets.ANTHROPIC.format)
        assertTrue(ProviderPresets.NVIDIA.baseUrl.startsWith("https://integrate.api.nvidia.com"))
    }
}

class MarkdownBlocksTest {
    @Test
    fun `splits text and code blocks`() {
        val blocks = MarkdownBlocks.split("Veja:\n```kotlin\nval a = 1\n```\nPronto.")
        assertEquals(3, blocks.size)
        assertEquals(MessageBlock.Text("Veja:"), blocks[0])
        assertEquals(MessageBlock.Code("kotlin", "val a = 1"), blocks[1])
        assertEquals(MessageBlock.Text("Pronto."), blocks[2])
    }

    @Test
    fun `an unclosed block while streaming becomes code`() {
        val blocks = MarkdownBlocks.split("```cpp\nint main() {")
        assertEquals(listOf<MessageBlock>(MessageBlock.Code("cpp", "int main() {")), blocks)
    }

    @Test
    fun `plain text stays a single block`() {
        assertEquals(listOf<MessageBlock>(MessageBlock.Text("só texto")), MarkdownBlocks.split("só texto"))
    }
}
