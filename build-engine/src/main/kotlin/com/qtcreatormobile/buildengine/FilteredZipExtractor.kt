package com.qtcreatormobile.buildengine

import java.io.File

/**
 * Extrai apenas as entradas de um .zip cujo caminho passa em [entryFilter],
 * opcionalmente renomeando o caminho de destino via [pathTransform]. Usado
 * para o NDK oficial (~700 MB), do qual so precisamos do sysroot.
 */
class FilteredZipExtractor(
    private val entryFilter: (String) -> Boolean,
    private val pathTransform: (String) -> String = { it },
) : ArchiveExtractor {

    override fun extract(archive: File, targetDir: File) {
        java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entryFilter(entry.name)) {
                    val outFile = File(targetDir, pathTransform(entry.name))
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zip.copyTo(it) }
                        outFile.setExecutable(true)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    companion object {
        /** Aceita qualquer entrada contendo "/sysroot/" e reescreve o destino para comecar em "sysroot/". */
        fun ndkSysrootOnly(): FilteredZipExtractor {
            val marker = "/sysroot/"
            return FilteredZipExtractor(
                entryFilter = { name -> name.contains(marker) },
                pathTransform = { name -> "sysroot/" + name.substringAfter(marker) },
            )
        }

        /** Do zip de build-tools, extrai so `lib/d8.jar` e `lib/apksigner.jar` em `lib/` no destino. */
        fun buildToolsJarsOnly(): FilteredZipExtractor {
            val wanted = listOf("/lib/d8.jar", "/lib/apksigner.jar")
            return FilteredZipExtractor(
                entryFilter = { name -> wanted.any { name.endsWith(it) } },
                pathTransform = { name -> "lib/" + name.substringAfterLast('/') },
            )
        }

        /** Do zip da plataforma Android, extrai so o `android.jar` (raiz do destino). */
        fun platformAndroidJarOnly(): FilteredZipExtractor = FilteredZipExtractor(
            entryFilter = { name -> name.endsWith("/android.jar") },
            pathTransform = { "android.jar" },
        )
    }
}
