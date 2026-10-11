package com.qtcreatormobile.buildengine

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File

/** Extrai um `.tar.gz`/`.tgz` de verdade, preservando o bit de execucao. */
class TarGzExtractor : ArchiveExtractor {
    override fun extract(archive: File, targetDir: File) {
        targetDir.mkdirs()
        TarArchiveInputStream(GzipCompressorInputStream(archive.inputStream().buffered())).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { tar.copyTo(it) }
                    if (entry.mode and 0b001_000_000 != 0) outFile.setExecutable(true)
                }
                entry = tar.nextEntry
            }
        }
    }
}

/** Escolhe o extrator certo pela extensao da URL (.zip, .tar.xz, .tar.gz/.tgz); falha se desconhecida. */
object ArchiveExtractors {
    fun forUrl(url: String): ArchiveExtractor {
        val clean = url.substringBefore('?').lowercase()
        return when {
            clean.endsWith(".zip") -> ZipArchiveExtractor()
            clean.endsWith(".tar.xz") || clean.endsWith(".txz") -> TarXzExtractor()
            clean.endsWith(".tar.gz") || clean.endsWith(".tgz") -> TarGzExtractor()
            else -> throw BuildEngineException("Formato de arquivo nao suportado para extracao: $url")
        }
    }
}

/**
 * Muitos pacotes (tarballs de JDK, por exemplo) trazem tudo dentro de UM
 * diretorio raiz (`jdk-17/bin/java`). Se [dir] contem exatamente um diretorio
 * e mais nada, sobe o conteudo dele um nivel.
 */
object DirectoryFlattener {
    fun flattenSingleRoot(dir: File) {
        val children = dir.listFiles() ?: return
        if (children.size != 1 || !children[0].isDirectory) return

        val root = children[0]
        for (item in root.listFiles().orEmpty()) {
            val destination = File(dir, item.name)
            if (!item.renameTo(destination)) {
                throw BuildEngineException("Falha ao mover '${item.path}' para '${destination.path}'.")
            }
        }
        root.delete()
    }
}
