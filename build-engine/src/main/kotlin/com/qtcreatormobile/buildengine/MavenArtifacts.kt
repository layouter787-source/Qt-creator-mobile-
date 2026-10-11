package com.qtcreatormobile.buildengine

import java.io.File
import java.util.zip.ZipFile

data class MavenCoordinate(val group: String, val artifact: String, val version: String) {
    /** Caminho relativo padrao Maven: group com pontos -> barras, depois artifact/versao. */
    fun path(extension: String): String {
        val groupPath = group.replace('.', '/')
        return "$groupPath/$artifact/$version/$artifact-$version.$extension"
    }
}

/**
 * Baixa artefatos Maven (jar ou aar) de um repositorio real - Google Maven
 * (onde vivem todas as bibliotecas `androidx.*`, incluindo Compose e
 * Material3) ou Maven Central (onde vive o `kotlin-stdlib`). Mesmo formato de
 * URL que o proprio Gradle usa.
 */
class MavenArtifactDownloader(
    private val downloader: Downloader = HttpDownloader(),
) {
    companion object {
        const val GOOGLE_MAVEN = "https://maven.google.com"
        const val MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
    }

    fun download(repoBaseUrl: String, coordinate: MavenCoordinate, extension: String, destination: File) {
        destination.parentFile?.mkdirs()
        downloader.download("$repoBaseUrl/${coordinate.path(extension)}", destination, DownloadProgressListener {})
    }
}

/**
 * Um `.aar` e um `.zip` contendo `classes.jar` (o bytecode de verdade), recursos
 * e manifesto. Para compilar Kotlin contra uma biblioteca `.aar` (como qualquer
 * `androidx.compose.*`), so o `classes.jar` de dentro interessa ao classpath.
 */
object AarClassesJarExtractor {
    fun extractClassesJar(aarFile: File, destinationJar: File) {
        ZipFile(aarFile).use { zip ->
            val entry = zip.getEntry("classes.jar")
                ?: throw BuildEngineException("'${aarFile.name}' nao contem classes.jar - nao e um .aar valido?")
            destinationJar.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input -> destinationJar.outputStream().use { input.copyTo(it) } }
        }
    }
}
