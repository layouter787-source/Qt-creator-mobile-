package com.qtcreatormobile.buildengine

import java.io.File

/** Um zip do NDK encontrado no armazenamento do aparelho. */
data class NdkArchiveCandidate(
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long,
    /** Revisao deduzida do nome do arquivo (ex.: "r27c"), se houver. */
    val revisionHint: String?,
)

/**
 * Procura zips do NDK que o usuario baixou (ex.: na pasta Downloads): nome `android-ndk-*.zip`
 * e tamanho plausivel. Downloads incompletos (`.crdownload`, `.part`, `.tmp`) nao terminam em
 * `.zip`, entao ficam de fora. O mais recente vem primeiro.
 */
object NdkArchiveFinder {
    /** O NDK completo tem varios centenas de MB; abaixo disso nao pode ser o pacote certo. */
    const val MIN_BYTES: Long = 300L * 1024 * 1024

    private val NAME = Regex("""(?i)^android-ndk-.*\.zip$""")
    private val REVISION = Regex("""(?i)android-ndk-(r\d+[a-z]?)""")

    fun find(directories: List<File>, maxDepth: Int = 2, minBytes: Long = MIN_BYTES): List<NdkArchiveCandidate> {
        val found = LinkedHashMap<String, NdkArchiveCandidate>()
        for (directory in directories) {
            if (!directory.isDirectory) continue
            // Profundidade limitada: a busca e rapida mesmo em pastas Downloads grandes.
            directory.walk().maxDepth(maxDepth).onEnter { it.isDirectory }
                .filter { it.isFile && NAME.matches(it.name) && it.length() >= minBytes }
                .forEach { file ->
                    val key = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                    found.putIfAbsent(
                        key,
                        NdkArchiveCandidate(
                            file = file,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            revisionHint = REVISION.find(file.name)?.groupValues?.get(1),
                        ),
                    )
                }
        }
        return found.values.sortedByDescending { it.lastModified }
    }
}
