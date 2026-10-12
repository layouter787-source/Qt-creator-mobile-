package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeDownloader(private val fakeContent: ByteArray) : Downloader {
    var lastUrl: String? = null

    override fun download(url: String, destination: File, listener: DownloadProgressListener) {
        lastUrl = url
        destination.writeBytes(fakeContent)
        listener.onProgress(DownloadProgress(fakeContent.size.toLong(), fakeContent.size.toLong()))
    }
}

class RemoteToolchainInstallerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-remote-installer-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun zipBytes(entryName: String, content: String): ByteArray {
        val zipFile = File(tempDir, "source-${System.nanoTime()}.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        val bytes = zipFile.readBytes()
        zipFile.delete()
        return bytes
    }

    @Test
    fun `downloads and extracts the toolchain into the target directory`() {
        val targetDir = File(tempDir, "toolchain")
        val progressEvents = mutableListOf<DownloadProgress>()

        val installer = RemoteToolchainInstaller(
            downloadUrl = "https://example.com/toolchain.zip",
            downloader = FakeDownloader(zipBytes("clang", "fake clang binary")),
            extractor = ZipArchiveExtractor(),
            progressListener = DownloadProgressListener { progressEvents += it },
        )

        installer.install(targetDir)

        val extractedClang = File(targetDir, "clang")
        assertTrue(extractedClang.exists())
        assertEquals("fake clang binary", extractedClang.readText())
        assertTrue(progressEvents.isNotEmpty())
        assertEquals(1f, progressEvents.last().fraction)
    }

    @Test
    fun `flattens a single wrapping root folder when requested`() {
        val targetDir = File(tempDir, "jdk")
        val installer = RemoteToolchainInstaller(
            downloadUrl = "https://example.com/jdk.zip",
            downloader = FakeDownloader(zipBytes("jdk-17.0.9/bin/java", "fake java")),
            extractor = ZipArchiveExtractor(),
            flattenAfterExtract = true,
        )

        installer.install(targetDir)

        assertEquals("fake java", File(targetDir, "bin/java").readText())
        assertTrue(!File(targetDir, "jdk-17.0.9").exists())
    }

    @Test
    fun `cleans up the temporary archive after extracting`() {
        val targetDir = File(tempDir, "toolchain")

        val installer = RemoteToolchainInstaller(
            downloadUrl = "https://example.com/toolchain.zip",
            downloader = FakeDownloader(zipBytes("clang", "fake clang binary")),
            extractor = ZipArchiveExtractor(),
        )

        installer.install(targetDir)

        val leftoverFiles = tempDir.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
        assertTrue(leftoverFiles.isEmpty())
    }
}
