package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElfPrefixPatcherTest {

    private val oldPrefix = "/data/data/com.termux/files/usr" // 31 caracteres
    private val newPrefix = "/data/data/com.qtcm/files/usr"    // 29 caracteres
    private val chunk = 1 shl 20

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-elf-patch-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `padding widens the new prefix with slashes to the exact old length and never uses NUL`() {
        val padded = ElfPrefixPatcher.padToLength(oldPrefix, newPrefix)

        assertEquals(oldPrefix.length, padded.length)
        assertEquals("/data/data/com.qtcm/files//usr", padded)
        assertFalse(padded.contains('\u0000'))
    }

    @Test
    fun `patching keeps the rest of the path that follows the prefix intact`() {
        // O caso que o padding com NUL quebrava: o prefixo e seguido de mais caminho.
        val file = File(tempDir, "fake-lib").apply {
            writeBytes(
                "HEADER\u0000".toByteArray() + "$oldPrefix/lib/libc++_shared.so\u0000".toByteArray() +
                    "MIDDLE\u0000".toByteArray() + "$oldPrefix/etc/clang.cfg\u0000".toByteArray()
            )
        }
        val originalSize = file.length()

        val occurrences = ElfPrefixPatcher.patch(file, oldPrefix, newPrefix)

        assertEquals(2, occurrences)
        assertEquals(originalSize, file.length())
        val text = file.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(text.contains("/data/data/com.qtcm/files//usr/lib/libc++_shared.so\u0000"))
        assertTrue(text.contains("/data/data/com.qtcm/files//usr/etc/clang.cfg\u0000"))
        assertFalse(text.contains("com.termux"))
    }

    @Test
    fun `returns zero and leaves the file untouched when the prefix does not appear`() {
        val original = "nothing to see here".toByteArray()
        val file = File(tempDir, "no-match").apply { writeBytes(original) }

        assertEquals(0, ElfPrefixPatcher.patch(file, oldPrefix, newPrefix))
        assertTrue(file.readBytes().contentEquals(original))
    }

    @Test
    fun `refuses a replacement prefix longer than the original`() {
        val file = File(tempDir, "any").apply { writeBytes(oldPrefix.toByteArray()) }

        assertFailsWith<IllegalArgumentException> {
            ElfPrefixPatcher.patch(file, oldPrefix, "/data/data/com.qtcreatormobile.app/files/usr")
        }
    }

    @Test
    fun `accepts a replacement with exactly the same length`() {
        val same = "/data/data/com.termuy/files/usr"
        val file = File(tempDir, "same").apply { writeBytes("x$oldPrefix/bin".toByteArray()) }

        assertEquals(1, ElfPrefixPatcher.patch(file, oldPrefix, same))
        assertEquals("x$same/bin", file.readText())
    }

    @Test
    fun `patches occurrences that straddle the 1MB chunk boundary exactly once each`() {
        val size = chunk * 2 + 100
        val bytes = ByteArray(size) { 'A'.code.toByte() }
        val old = oldPrefix.toByteArray()
        // Tres ocorrencias sem sobreposicao entre si: uma antes da fronteira, uma que a CRUZA
        // (comeca 10 bytes antes), e uma logo depois.
        val positions = listOf(chunk - 100, chunk - 10, chunk + 60)
        for (p in positions) System.arraycopy(old, 0, bytes, p, old.size)
        val file = File(tempDir, "big").apply { writeBytes(bytes) }

        val occurrences = ElfPrefixPatcher.patch(file, oldPrefix, newPrefix)

        assertEquals(positions.size, occurrences)
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("com.termux"))
        assertEquals(size.toLong(), file.length())
    }

    @Test
    fun `an occurrence starting exactly at the last index of a chunk is counted once`() {
        val size = chunk * 2
        val bytes = ByteArray(size) { 'B'.code.toByte() }
        System.arraycopy(oldPrefix.toByteArray(), 0, bytes, chunk - 1, oldPrefix.length)
        val file = File(tempDir, "edge").apply { writeBytes(bytes) }

        assertEquals(1, ElfPrefixPatcher.patch(file, oldPrefix, newPrefix))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("com.termux"))
    }

    @Test
    fun `an occurrence starting exactly at the first index of the second chunk is counted once`() {
        val size = chunk * 2
        val bytes = ByteArray(size) { 'C'.code.toByte() }
        System.arraycopy(oldPrefix.toByteArray(), 0, bytes, chunk, oldPrefix.length)
        val file = File(tempDir, "edge2").apply { writeBytes(bytes) }

        assertEquals(1, ElfPrefixPatcher.patch(file, oldPrefix, newPrefix))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("com.termux"))
    }
}
