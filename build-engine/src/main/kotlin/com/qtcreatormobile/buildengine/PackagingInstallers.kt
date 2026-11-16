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

/**
 * Instala o `aapt2` nativo (arm64) a partir do pacote `aapt2` do repositorio do Termux - o mesmo
 * mecanismo (fechamento de dependencias, patch de prefixo) usado para o clang, porque o aapt2
 * depende de libprotobuf, abseil etc. O link `<packaging>/bin/aapt2` aponta para o binario no
 * `usr` do Termux ([termuxRoot]), de modo que o resto do app continua achando o aapt2 no mesmo lugar.
 *
 * O link anterior de download direto (ReVanced Manager) saiu do ar com HTTP 404.
 */
class Aapt2TermuxInstaller(
    private val termuxRoot: File,
    private val termuxInstaller: ToolchainInstaller,
) : ToolchainInstaller {

    override fun install(installRootDir: File) {
        termuxInstaller.install(termuxRoot)
        val real = File(termuxRoot, "usr/bin/aapt2")
        if (!real.isFile) {
            throw BuildEngineException("O pacote aapt2 do Termux foi instalado, mas ${real.path} nao existe.")
        }
        real.setExecutable(true)
        val link = File(installRootDir, PackagingLayout.AAPT2_BINARY)
        link.parentFile?.mkdirs()
        java.nio.file.Files.deleteIfExists(link.toPath())
        java.nio.file.Files.createSymbolicLink(link.toPath(), real.toPath())
    }
}
