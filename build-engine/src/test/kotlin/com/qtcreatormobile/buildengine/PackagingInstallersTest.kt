package com.qtcreatormobile.buildengine

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeDownloaderWritingBytes(private val bytes: ByteArray) : Downloader {
    override fun download(url: String, destination: File, listener: DownloadProgressListener) {
        destination.writeBytes(bytes)
        listener.onProgress(DownloadProgress(bytes.size.toLong(), bytes.size.toLong()))
    }
}

class PackagingInstallersTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-packaging-installers-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `tar xz extractor unpacks files and keeps the executable bit`() {
        val archive = File(tempDir, "jdk.tar.xz")
        XZCompressorOutputStream(archive.outputStream()).use { xz ->
            TarArchiveOutputStream(xz).use { tar ->
                val content = "fake java binary".toByteArray()
                val entry = TarArchiveEntry("jdk/bin/java")
                entry.size = content.size.toLong()
                entry.mode = "100755".toInt(8)
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
        }

        val targetDir = File(tempDir, "out")
        TarXzExtractor().extract(archive, targetDir)

        val javaFile = File(targetDir, "jdk/bin/java")
        assertTrue(javaFile.exists())
        assertEquals("fake java binary", javaFile.readText())
        assertTrue(javaFile.canExecute())
    }

    @Test
    fun `single file installer downloads to the requested name and marks it executable`() {
        val installer = SingleFileInstaller(
            downloadUrl = "https://example.com/libaapt2.so",
            fileName = "aapt2",
            downloader = FakeDownloaderWritingBytes("fake aapt2 bytes".toByteArray()),
        )
        val targetDir = File(tempDir, "packaging/bin")

        installer.install(targetDir)

        val aapt2 = File(targetDir, "aapt2")
        assertTrue(aapt2.exists())
        assertEquals("fake aapt2 bytes", aapt2.readText())
        assertTrue(aapt2.canExecute())
    }
}
