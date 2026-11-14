package com.qtcreatormobile.buildengine

import java.io.IOException

/** Busca o conteudo de uma URL como texto. Abstrai HTTP para permitir testar sem rede real. */
fun interface TextFetcher {
    fun fetch(url: String): String
}

/**
 * Busca texto via HTTP COM timeouts e novas tentativas. Sem timeout, uma conexao pendurada
 * (comum em rede movel) deixava o setup parado em 0% para sempre, sem erro e sem retomar.
 */
class HttpTextFetcher(
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 30_000,
    private val attempts: Int = 3,
    private val retryDelayMs: Long = 1_500,
) : TextFetcher {
    override fun fetch(url: String): String {
        var lastError: IOException? = null
        repeat(attempts) { attempt ->
            try {
                return fetchOnce(url)
            } catch (e: IOException) {
                lastError = e
                if (attempt < attempts - 1) Thread.sleep(retryDelayMs * (attempt + 1))
            }
        }
        throw IOException("Nao foi possivel obter $url apos $attempts tentativas: ${lastError?.message}", lastError)
    }

    private fun fetchOnce(url: String): String {
        val connection = java.net.URL(url).openConnection()
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        // A API do GitHub exige um User-Agent; sem isso algumas requisicoes sao recusadas.
        connection.setRequestProperty("User-Agent", "QTCreatorMobile")
        return try {
            connection.getInputStream().bufferedReader().use { it.readText() }
        } finally {
            (connection as? java.net.HttpURLConnection)?.disconnect()
        }
    }
}

/**
 * Resolve a URL de download de um asset dentro do release mais recente de um
 * repositorio publico do GitHub, consultando a API de releases em vez de
 * fixar um nome de arquivo no codigo. Se o asset nao for encontrado, falha de
 * forma clara e imediata.
 */
class GitHubReleaseResolver(
    private val textFetcher: TextFetcher = HttpTextFetcher(),
) {
    fun resolveLatestAssetUrl(owner: String, repo: String, assetNameContains: String): String =
        resolveLatestAssetUrlAny(owner, repo, listOf(assetNameContains))

    /**
     * Tenta varios trechos de nome em ordem de preferencia (ex.: "aarch64" e,
     * se nao houver, "arm64"). Retorna o primeiro asset que casar com o
     * primeiro trecho que tiver correspondencia.
     */
    fun resolveLatestAssetUrlAny(owner: String, repo: String, assetNameFragments: List<String>): String {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val release = org.json.JSONObject(textFetcher.fetch(apiUrl))
        val assets = release.getJSONArray("assets")

        for (fragment in assetNameFragments) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").contains(fragment, ignoreCase = true)) {
                    return asset.getString("browser_download_url")
                }
            }
        }

        throw BuildEngineException(
            "Nenhum asset contendo ${assetNameFragments.joinToString(" ou ") { "'$it'" }} " +
                "foi encontrado no ultimo release de $owner/$repo."
        )
    }
}
