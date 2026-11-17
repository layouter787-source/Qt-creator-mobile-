package com.qtcreatormobile.aiclient

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(val status: Int, val body: String)

/**
 * Camada HTTP minima, trocavel nos testes. Se [onLine] != null e a resposta for 2xx, cada linha
 * do corpo e entregue a [onLine] conforme chega (streaming) e `body` volta vazio; caso contrario
 * o corpo inteiro volta em `body`.
 */
interface HttpTransport {
    fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        onLine: ((String) -> Unit)?,
        shouldStop: () -> Boolean,
    ): HttpResult
}

class JavaHttpTransport(
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 120_000,
) : HttpTransport {

    override fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        onLine: ((String) -> Unit)?,
        shouldStop: () -> Boolean,
    ): HttpResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiException("URL inválida: $url")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status in 200..299 && onLine != null) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        if (shouldStop()) break
                        val line = reader.readLine() ?: break
                        onLine(line)
                    }
                }
                return HttpResult(status, "")
            }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResult(status, text)
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            if (shouldStop()) return HttpResult(499, "")
            throw AiException("Falha de rede: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }
}
