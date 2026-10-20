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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermuxToolchainInstallerTest {

    // Alias curto no formato real do app (applicationId de 8 caracteres): 29 caracteres, cabe em 31.
    private val alias = "/data/data/com.qtcm/files/usr"
    private val paddedAlias = "/data/data/com.qtcm/files///usr"

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

    private class CountingDownloader(private val debsByUrlSuffix: Map<String, ByteArray>) : Downloader {
        var calls = 0
        override fun download(url: String, destination: File, listener: DownloadProgressListener) {
            calls++
            val bytes = debsByUrlSuffix.entries.first { url.endsWith(it.key) }.value
            destination.writeBytes(bytes)
            listener.onProgress(DownloadProgress(bytes.size.toLong(), bytes.size.toLong()))
        }
    }

    private fun bothDebs() = mapOf(
        "pool/clang.deb" to buildFakeDeb("clang"),
        "pool/libc++.deb" to buildFakeDeb("libc++"),
    )

    @Test
    fun `downloads every dependency, extracts and patches the termux prefix with the short alias`() {
        val installRoot = File(tempDir, "cpp")
        val installer = TermuxToolchainInstaller(
            indexResolver = fakeIndexResolver(),
            downloader = CountingDownloader(bothDebs()),
            prefixAlias = alias,
            linkAlias = false, // nao criar /data/data/... na maquina de CI
        )

        installer.install(installRoot)

        val clang = File(installRoot, "usr/bin/clang")
        val libcxx = File(installRoot, "usr/bin/libc++")
        assertTrue(clang.exists())
        assertTrue(libcxx.exists())

        val clangContent = clang.readBytes().toString(Charsets.US_ASCII)
        assertFalse(clangContent.contains("com.termux"))
        assertTrue(clangContent.contains("$paddedAlias/lib"))
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
            downloader = CountingDownloader(bothDebs()),
            prefixAlias = alias,
            linkAlias = false,
        )

        installer.install(installRoot)

        val leftovers = installRoot.listFiles { f -> f.name.endsWith(".deb") } ?: emptyArray()
        assertTrue(leftovers.isEmpty())
    }

    @Test
    fun `fails BEFORE downloading anything when the install path cannot fit the termux prefix`() {
        val downloader = CountingDownloader(bothDebs())
        // Sem alias: usa o caminho real (a pasta temporaria, muito mais longa que 31 caracteres).
        val installer = TermuxToolchainInstaller(
            indexResolver = fakeIndexResolver(),
            downloader = downloader,
        )

        val error = assertFailsWith<BuildEngineException> { installer.install(File(tempDir, "cpp")) }

        assertEquals(0, downloader.calls)
        assertTrue(error.message!!.contains("applicationId"))
    }

    @Test
    fun `rejects an alias that is too long, such as the original 23 character package name`() {
        val downloader = CountingDownloader(bothDebs())
        val installer = TermuxToolchainInstaller(
            indexResolver = fakeIndexResolver(),
            downloader = downloader,
            prefixAlias = "/data/data/com.qtcreatormobile.app/files/usr",
            linkAlias = false,
        )

        assertFailsWith<BuildEngineException> { installer.install(File(tempDir, "cpp")) }
        assertEquals(0, downloader.calls)
    }

    @Test
    fun `the alias linker creates a symlink to the real usr and replaces an older one`() {
        val realUsr = File(tempDir, "real/usr").apply { mkdirs() }
        File(realUsr, "marker.txt").writeText("conteudo real")
        val aliasLink = File(tempDir, "short/usr")

        PrefixAliasLinker.link(aliasLink, realUsr)
        assertTrue(Files.isSymbolicLink(aliasLink.toPath()))
        assertEquals("conteudo real", File(aliasLink, "marker.txt").readText())

        // Reinstalar nao pode falhar nem apagar o conteudo real.
        PrefixAliasLinker.link(aliasLink, realUsr)
        assertTrue(Files.isSymbolicLink(aliasLink.toPath()))
        assertEquals("conteudo real", File(realUsr, "marker.txt").readText())
    }
}
