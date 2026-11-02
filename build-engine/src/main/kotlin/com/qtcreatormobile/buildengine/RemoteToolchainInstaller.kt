package com.qtcreatormobile.buildengine

import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Baixa um arquivo de uma URL para um destino no disco, reportando progresso. */
interface Downloader {
    fun download(url: String, destination: File, listener: DownloadProgressListener)
}

class DownloadPausedException : IOException("Download pausado; será retomado quando continuar.")

/** Controle compartilhado entre a UI e o downloader ativo. */
class DownloadControl {
    private val paused = AtomicBoolean(false)
    private val active = AtomicReference<HttpURLConnection?>(null)

    fun isPaused(): Boolean = paused.get()

    fun pause() {
        paused.set(true)
        active.getAndSet(null)?.disconnect()
    }

    fun resume() { paused.set(false) }

    internal fun bind(connection: HttpURLConnection) {
        active.set(connection)
        if (paused.get()) connection.disconnect()
    }

    internal fun unbind(connection: HttpURLConnection) {
        active.compareAndSet(connection, null)
    }
}

class HttpDownloader(private val control: DownloadControl = DownloadControl()) : Downloader {
    override fun download(url: String, destination: File, listener: DownloadProgressListener) {
        if (control.isPaused()) throw DownloadPausedException()
        destination.parentFile?.mkdirs()
        val completeSource = File(destination.parentFile ?: File("."), destination.name + ".source")
        if (destination.isFile && destination.length() > 0L && completeSource.isFile && completeSource.readText() == url) {
            listener.onProgress(DownloadProgress(destination.length(), destination.length()))
            return
        }
        val partial = File(destination.parentFile ?: File("."), destination.name + ".part")
        val partialSource = File(partial.parentFile ?: File("."), partial.name + ".source")
        if (partial.exists() && (!partialSource.isFile || partialSource.readText() != url)) partial.delete()
        partialSource.writeText(url)
        var offset = partial.length()
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            control.bind(connection)
            val code = connection.responseCode
            if (code == 416 && offset > 0L) {
                val completeLength = connection.getHeaderField("Content-Range")?.substringAfter("*/")?.toLongOrNull()
                if (completeLength == offset) {
                    moveCompletedFile(partial, destination)
                    completeSource.writeText(url)
                    partialSource.delete()
                    listener.onProgress(DownloadProgress(offset, offset))
                    return
                }
            }
            if (code !in 200..299) throw IOException("Servidor respondeu HTTP $code para $url")
            val append = offset > 0 && code == HttpURLConnection.HTTP_PARTIAL
            if (offset > 0 && !append) {
                partial.delete()
                offset = 0L
            }
            val totalBytes = connection.contentLengthLong.let { if (it < 0) -1 else it + offset }
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = offset
                    while (true) {
                        if (control.isPaused()) throw DownloadPausedException()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        listener.onProgress(DownloadProgress(downloaded, totalBytes))
                    }
                    output.flush()
                }
            }
            moveCompletedFile(partial, destination)
            completeSource.writeText(url)
            partialSource.delete()
        } catch (e: IOException) {
            if (control.isPaused()) throw DownloadPausedException()
            throw e
        } finally {
            connection?.let {
                control.unbind(it)
                it.disconnect()
            }
        }
    }

    private fun moveCompletedFile(partial: File, destination: File) {
        try {
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw IOException("Não foi possível finalizar ${destination.name}: ${e.message}", e)
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
        var extracted = false
        try {
            stageListener.onStageChanged(ToolchainStage.DOWNLOADING)
            downloader.download(downloadUrl, archiveFile, progressListener)

            stageListener.onStageChanged(ToolchainStage.EXTRACTING)
            try {
                extractor.extract(archiveFile, installRootDir)
                if (flattenAfterExtract) DirectoryFlattener.flattenSingleRoot(installRootDir)
                extracted = true
            } catch (e: Exception) {
                archiveFile.delete()
                File(archiveFile.parentFile, archiveFile.name + ".source").delete()
                throw e
            }
        } finally {
            if (extracted) {
                archiveFile.delete()
                File(archiveFile.parentFile, archiveFile.name + ".source").delete()
            }
        }
    }
}
