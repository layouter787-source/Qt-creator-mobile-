package com.qtcreatormobile.buildengine

import java.io.File
import java.net.URL

/** Baixa um arquivo de uma URL para um destino no disco, reportando progresso. */
interface Downloader {
    fun download(url: String, destination: File, listener: DownloadProgressListener)
}

class HttpDownloader : Downloader {
    override fun download(url: String, destination: File, listener: DownloadProgressListener) {
        val connection = URL(url).openConnection()
        val totalBytes = connection.contentLengthLong

        connection.getInputStream().use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    listener.onProgress(DownloadProgress(downloaded, totalBytes))
                }
            }
        }
    }
}

/** Extrai um arquivo compactado para um diretorio, preservando permissao de execucao. */
interface ArchiveExtractor {
    fun extract(archive: File, targetDir: File)
}

class ZipArchiveExtractor : ArchiveExtractor {
    override fun extract(archive: File, targetDir: File) {
        java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                    // Binarios de toolchain (clang, ld, etc.) precisam ser executaveis.
                    outFile.setExecutable(true)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}

/**
 * [ToolchainInstaller] real: baixa um pacote de uma URL e extrai para o
 * diretorio esperado. Emite DOWNLOADING e EXTRACTING via [stageListener].
 *
 * @param flattenAfterExtract muitos pacotes de terceiros (tarballs de JDK) embrulham
 *   tudo numa unica pasta raiz versionada; quando true, acha esse caso com
 *   [DirectoryFlattener] apos extrair.
 */
class RemoteToolchainInstaller(
    private val downloadUrl: String,
    private val downloader: Downloader,
    private val extractor: ArchiveExtractor,
    private val flattenAfterExtract: Boolean = false,
    private val progressListener: DownloadProgressListener = DownloadProgressListener {},
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) : ToolchainInstaller {

    override fun install(installRootDir: File) {
        val archiveFile = File(installRootDir.parentFile ?: installRootDir, "toolchain-download.tmp")
        try {
            stageListener.onStageChanged(ToolchainStage.DOWNLOADING)
            downloader.download(downloadUrl, archiveFile, progressListener)

            stageListener.onStageChanged(ToolchainStage.EXTRACTING)
            extractor.extract(archiveFile, installRootDir)
            if (flattenAfterExtract) DirectoryFlattener.flattenSingleRoot(installRootDir)
        } finally {
            archiveFile.delete()
        }
    }
}
