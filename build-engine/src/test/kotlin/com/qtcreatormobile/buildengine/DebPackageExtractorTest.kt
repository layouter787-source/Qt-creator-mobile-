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
import kotlin.test.assertTrue

class DebPackageExtractorTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-deb-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private sealed interface Item {
        data class Regular(val name: String, val content: String, val executable: Boolean = false) : Item
        data class Symlink(val name: String, val target: String) : Item
        data class Hardlink(val name: String, val target: String) : Item
    }

    /** Constroi um .deb de verdade (ar contendo data.tar.xz) com os itens dados. */
    private fun buildDeb(vararg items: Item): File {
        val tarBytes = ByteArrayOutputStream().also { buffer ->
            TarArchiveOutputStream(buffer).use { tar ->
                for (item in items) {
                    when (item) {
                        is Item.Regular -> {
                            val bytes = item.content.toByteArray()
                            val entry = TarArchiveEntry(item.name)
                            entry.size = bytes.size.toLong()
                            entry.mode = if (item.executable) "100755".toInt(8) else "100644".toInt(8)
                            tar.putArchiveEntry(entry)
                            tar.write(bytes)
                            tar.closeArchiveEntry()
                        }
                        is Item.Symlink -> {
                            val entry = TarArchiveEntry(item.name, TarArchiveEntry.LF_SYMLINK)
                            entry.linkName = item.target
                            tar.putArchiveEntry(entry)
                            tar.closeArchiveEntry()
                        }
                        is Item.Hardlink -> {
                            val entry = TarArchiveEntry(item.name, TarArchiveEntry.LF_LINK)
                            entry.linkName = item.target
                            tar.putArchiveEntry(entry)
                            tar.closeArchiveEntry()
                        }
                    }
                }
            }
        }.toByteArray()

        val xzBytes = ByteArrayOutputStream().also { buffer ->
            XZCompressorOutputStream(buffer).use { it.write(tarBytes) }
        }.toByteArray()

        val debFile = File(tempDir, "pkg-${System.nanoTime()}.deb")
        ArArchiveOutputStream(debFile.outputStream()).use { ar ->
            ar.putArchiveEntry(ArArchiveEntry("data.tar.xz", xzBytes.size.toLong()))
            ar.write(xzBytes)
            ar.closeArchiveEntry()
        }
        return debFile
    }

    @Test
    fun `extracts a binary and keeps the executable bit`() {
        val deb = buildDeb(Item.Regular("./usr/bin/clang-21", "fake clang binary bytes", executable = true))
        val targetDir = File(tempDir, "extracted")

        DebPackageExtractor.extract(deb, targetDir)

        val clang = File(targetDir, "usr/bin/clang-21")
        assertEquals("fake clang binary bytes", clang.readText())
        assertTrue(clang.canExecute())
    }

    @Test
    fun `strips the termux root folder so content always lands under usr`() {
        val deb = buildDeb(
            Item.Regular("./data/data/com.termux/files/usr/lib/libfoo.so", "lib bytes"),
            Item.Regular("./data/data/com.termux/files/usr/bin/tool", "tool bytes", executable = true),
        )
        val targetDir = File(tempDir, "extracted")

        DebPackageExtractor.extract(deb, targetDir)

        assertEquals("lib bytes", File(targetDir, "usr/lib/libfoo.so").readText())
        assertEquals("tool bytes", File(targetDir, "usr/bin/tool").readText())
    }

    @Test
    fun `creates a real symlink instead of an empty file`() {
        val deb = buildDeb(
            Item.Regular("./usr/bin/clang-21", "real clang", executable = true),
            Item.Symlink("./usr/bin/clang", "clang-21"),
        )
        val targetDir = File(tempDir, "extracted")

        DebPackageExtractor.extract(deb, targetDir)

        val link = File(targetDir, "usr/bin/clang")
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals("real clang", link.readText()) // seguir o link chega no binario de verdade
    }

    @Test
    fun `rewrites absolute symlink targets that point into the termux prefix`() {
        val deb = buildDeb(
            Item.Regular("./usr/bin/real", "content", executable = true),
            Item.Symlink("./usr/bin/alias", "/data/data/com.termux/files/usr/bin/real"),
        )
        val targetDir = File(tempDir, "extracted")
        val realUsr = File(targetDir, "usr").absolutePath

        DebPackageExtractor.extract(
            deb, targetDir,
            symlinkPrefixRewrite = "/data/data/com.termux/files/usr" to realUsr,
        )

        val link = File(targetDir, "usr/bin/alias")
        assertEquals("content", link.readText())
    }

    @Test
    fun `materializes hard links as copies`() {
        val deb = buildDeb(
            Item.Regular("./usr/bin/original", "shared content", executable = true),
            Item.Hardlink("./usr/bin/copy", "./usr/bin/original"),
        )
        val targetDir = File(tempDir, "extracted")

        DebPackageExtractor.extract(deb, targetDir)

        assertEquals("shared content", File(targetDir, "usr/bin/copy").readText())
    }

    @Test
    fun `rejects entries that try to escape the destination directory`() {
        val deb = buildDeb(Item.Regular("./../evil.txt", "malicious"))
        val targetDir = File(tempDir, "extracted")

        assertFailsWith<BuildEngineException> { DebPackageExtractor.extract(deb, targetDir) }
        assertTrue(!File(tempDir, "evil.txt").exists())
    }

    @Test
    fun `fails clearly when the deb has no data tar entry`() {
        val emptyDeb = File(tempDir, "empty.deb")
        ArArchiveOutputStream(emptyDeb.outputStream()).use { ar ->
            ar.putArchiveEntry(ArArchiveEntry("control.tar.xz", 0))
            ar.closeArchiveEntry()
        }

        assertFailsWith<BuildEngineException> { DebPackageExtractor.extract(emptyDeb, File(tempDir, "out")) }
    }

    @Test
    fun `normalizes entry names`() {
        assertEquals("usr/bin/clang", DebPackageExtractor.normalizeEntryName("./usr/bin/clang"))
        assertEquals("usr/bin/clang", DebPackageExtractor.normalizeEntryName("./data/data/com.termux/files/usr/bin/clang"))
        assertEquals("usr", DebPackageExtractor.normalizeEntryName("./data/data/com.termux/files/usr/"))
        assertEquals("", DebPackageExtractor.normalizeEntryName("./"))
    }
}
