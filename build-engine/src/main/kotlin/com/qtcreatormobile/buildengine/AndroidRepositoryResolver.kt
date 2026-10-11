package com.qtcreatormobile.buildengine

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Resolve, contra o manifesto XML oficial do Google que lista todos os
 * componentes do Android SDK/NDK (o mesmo que o Android Studio e o
 * `sdkmanager` consultam), a URL de download de um componente - sem
 * hardcodar numeros de versao que ficam desatualizados.
 *
 * Usado para: NDK (sysroot), build-tools (d8.jar e apksigner.jar) e a
 * plataforma Android (android.jar, necessario ao aapt2).
 */
class AndroidRepositoryResolver(
    private val textFetcher: TextFetcher = HttpTextFetcher(),
) {
    companion object {
        const val REPOSITORY_MANIFEST_URL = "https://dl.google.com/android/repository/repository2-3.xml"
        private const val DOWNLOAD_BASE_URL = "https://dl.google.com/android/repository/"
        private val PRE_RELEASE_MARKERS = listOf("-rc", "-alpha", "-beta", "-dev")
    }

    /** URL do .zip do NDK mais recente (versao estavel) para host Linux. */
    fun resolveLatestNdkLinuxUrl(): String =
        resolveLatest(pathPrefix = "ndk;", hostOs = "linux")

    /** URL do .zip do build-tools mais recente (versao estavel) - contem lib/d8.jar e lib/apksigner.jar. */
    fun resolveLatestBuildToolsUrl(): String =
        resolveLatest(pathPrefix = "build-tools;", hostOs = "linux")

    /** URL do .zip da plataforma Android [apiLevel] (contem android.jar). Nao depende de host. */
    fun resolvePlatformUrl(apiLevel: Int): String =
        resolveExact(path = "platforms;android-$apiLevel", hostOs = null)

    private fun resolveLatest(pathPrefix: String, hostOs: String?): String {
        val candidates = loadPackages()
            .filter { it.path.startsWith(pathPrefix) }
            .filter { pkg -> PRE_RELEASE_MARKERS.none { pkg.path.contains(it) } }
            .mapNotNull { pkg -> pkg.archiveUrlFor(hostOs)?.let { url -> pkg.version to url } }

        return candidates.maxWithOrNull(compareBy({ it.first.first }, { it.first.second }, { it.first.third }))?.second
            ?: throw BuildEngineException("Nenhum pacote '$pathPrefix' (host: $hostOs) encontrado no manifesto do Google.")
    }

    private fun resolveExact(path: String, hostOs: String?): String =
        loadPackages().firstOrNull { it.path == path }?.archiveUrlFor(hostOs)
            ?: throw BuildEngineException("Pacote '$path' nao encontrado no manifesto do Google.")

    private class SdkPackage(val path: String, val version: Triple<Int, Int, Int>, private val element: Element) {
        fun archiveUrlFor(hostOs: String?): String? {
            val archives = element.getElementsByTagName("archive")
            for (i in 0 until archives.length) {
                val archive = archives.item(i) as Element
                val archiveHost = archive.getElementsByTagName("host-os").item(0)?.textContent
                if (hostOs != null && archiveHost != hostOs) continue
                val url = archive.getElementsByTagName("url").item(0)?.textContent ?: continue
                return if (url.startsWith("http")) url else DOWNLOAD_BASE_URL + url
            }
            return null
        }
    }

    private fun loadPackages(): List<SdkPackage> {
        val xml = textFetcher.fetch(REPOSITORY_MANIFEST_URL)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val nodes = document.getElementsByTagName("remotePackage")

        return (0 until nodes.length).map { index ->
            val element = nodes.item(index) as Element
            fun part(tag: String) = element.getElementsByTagName(tag).item(0)?.textContent?.toIntOrNull() ?: 0
            SdkPackage(element.getAttribute("path"), Triple(part("major"), part("minor"), part("micro")), element)
        }
    }
}
