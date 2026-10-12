package com.qtcreatormobile.buildengine

import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermuxToolchainInstallerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-termux-installer-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /** .deb real (ar + tar.xz) contendo um binario fake com o prefixo Termux embutido, como no binario de verdade. */
    private fun buildFakeDeb(binaryContentSuffix: String): ByteArray {
        val embeddedPath = TermuxConstants.PREFIX + "/lib"
        val content = "START-$binaryContentSuffix".toByteArray() + embeddedPath.toByteArray() +
            "-END-$binaryContentSuffix".toByteArray()

        val tarBytes = ByteArrayOutputStream().also { buffer ->
            TarArchiveOutputStream(buffer).use { tar ->
                val entry = TarArchiveEntry("./usr/bin/$binaryContentSuffix")
                entry.size = content.size.toLong()
                entry.mode = "100755".toInt(8)
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
        }.toByteArray()

        val xzBytes = ByteArrayOutputStream().also { buffer ->
            XZCompressorOutputStream(buffer).use { it.write(tarBytes) }
        }.toByteArray()

        return ByteArrayOutputStream().also { buffer ->
            ArArchiveOutputStream(buffer).use { ar ->
                ar.putArchiveEntry(ArArchiveEntry("data.tar.xz", xzBytes.size.toLong()))
                ar.write(xzBytes)
                ar.closeArchiveEntry()
            }
        }.toByteArray()
    }

    /** Indice fake real (parseia texto de verdade), simulando clang dependendo de libc++. */
    private fun fakeIndexResolver() = TermuxPackageIndexResolver(
        TextFetcher {
            """
                Package: libc++
                Version: 1.0
                Filename: pool/libc++.deb

                Package: clang
                Version: 1.0
                Depends: libc++
                Filename: pool/clang.deb
            """.trimIndent()
        }
    )

    /** Downloader fake que devolve o .deb certo de acordo com a URL pedida. */
    private fun fakeDownloader(debsByUrlSuffix: Map<String, ByteArray>) = object : Downloader {
        override fun download(url: String, destination: File, listener: DownloadProgressListener) {
            val bytes = debsByUrlSuffix.entries.first { url.endsWith(it.key) }.value
            destination.writeBytes(bytes)
            listener.onProgress(DownloadProgress(bytes.size.toLong(), bytes.size.toLong()))
        }
    }

    @Test
    fun `downloads every dependency, extracts and patches the termux prefix in all of them`() {
        val installRoot = File(tempDir, "cpp")
        val installer = TermuxToolchainInstaller(
            indexResolver = fakeIndexResolver(),
            downloader = fakeDownloader(
                mapOf(
                    "pool/clang.deb" to buildFakeDeb("clang"),
                    "pool/libc++.deb" to buildFakeDeb("libc++"),
                )
            ),
        )

        installer.install(installRoot)

        val clang = File(installRoot, "usr/bin/clang")
        val libcxx = File(installRoot, "usr/bin/libc++")
        assertTrue(clang.exists())
        assertTrue(libcxx.exists())

        val clangContent = clang.readBytes().toString(Charsets.US_ASCII)
        assertFalse(clangContent.contains("com.termux"))
        assertTrue(clangContent.contains(installRoot.absolutePath))
        assertTrue(clangContent.contains("START-clang"))
        assertTrue(clangContent.contains("END-clang"))

        val libcxxContent = libcxx.readBytes().toString(Charsets.US_ASCII)
        assertFalse(libcxxContent.contains("com.termux"))
    }

    @Test
    fun `cleans up every downloaded deb file after extracting`() {
        val installRoot = File(tempDir, "cpp")
        val installer = TermuxToolchainInstaller(
            indexResolver = fakeIndexResolver(),
            downloader = fakeDownloader(
                mapOf(
                    "pool/clang.deb" to buildFakeDeb("clang"),
                    "pool/libc++.deb" to buildFakeDeb("libc++"),
                )
            ),
        )

        installer.install(installRoot)

        val leftovers = installRoot.listFiles { f -> f.name.endsWith(".deb") } ?: emptyArray()
        assertTrue(leftovers.isEmpty())
    }
}
