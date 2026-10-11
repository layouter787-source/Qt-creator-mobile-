package com.qtcreatormobile.buildengine

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File

/** Extrai um arquivo `.tar.xz` de verdade, preservando o bit de execucao. */
class TarXzExtractor : ArchiveExtractor {

    override fun extract(archive: File, targetDir: File) {
        targetDir.mkdirs()
        TarArchiveInputStream(XZCompressorInputStream(archive.inputStream().buffered())).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { tar.copyTo(it) }
                    if (entry.mode and 0b001_000_000 != 0) {
                        outFile.setExecutable(true)
                    }
                }
                entry = tar.nextEntry
            }
        }
    }
}

/**
 * [ToolchainInstaller] para ferramentas distribuidas como UM unico arquivo
 * (como o `libaapt2.so` nativo). Baixa direto para o destino final e marca
 * como executavel.
 */
class SingleFileInstaller(
    private val downloadUrl: String,
    private val fileName: String,
    private val downloader: Downloader,
    private val progressListener: DownloadProgressListener = DownloadProgressListener {},
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) : ToolchainInstaller {

    override fun install(installRootDir: File) {
        installRootDir.mkdirs()
        val destination = File(installRootDir, fileName)
        stageListener.onStageChanged(ToolchainStage.DOWNLOADING)
        downloader.download(downloadUrl, destination, progressListener)
        destination.setExecutable(true)
    }
}
