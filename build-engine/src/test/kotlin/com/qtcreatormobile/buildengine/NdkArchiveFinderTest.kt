package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import java.io.RandomAccessFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NdkArchiveFinderTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-ndk-finder-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /** Cria um arquivo esparso com o tamanho pedido (sem gastar disco de verdade). */
    private fun sparse(path: String, bytes: Long, modified: Long): File {
        val file = File(tempDir, path)
        file.parentFile.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(bytes) }
        file.setLastModified(modified)
        return file
    }

    private val mb = 1024L * 1024

    @Test
    fun `finds ndk zips by name and size, newest first`() {
        sparse("Download/android-ndk-r29-linux.zip", 700 * mb, 1_000_000)
        sparse("Download/android-ndk-r30-linux.zip", 740 * mb, 3_000_000)
        sparse("Download/android-ndk-r30-windows.zip", 650 * mb, 2_000_000)

        val found = NdkArchiveFinder.find(listOf(File(tempDir, "Download")))

        assertEquals(
            listOf("android-ndk-r30-linux.zip", "android-ndk-r30-windows.zip", "android-ndk-r29-linux.zip"),
            found.map { it.file.name },
        )
        assertEquals("r30", found.first().revisionHint)
        assertEquals(740 * mb, found.first().sizeBytes)
    }

    @Test
    fun `ignores unfinished downloads, other zips and files too small to be the ndk`() {
        sparse("Download/android-ndk-r30-linux.zip.crdownload", 400 * mb, 1)
        sparse("Download/android-ndk-r30-linux.zip.part", 400 * mb, 1)
        sparse("Download/outra-coisa.zip", 900 * mb, 1)
        sparse("Download/android-ndk-r30-linux.zip", 5 * mb, 1) // truncado/falso
        sparse("Download/android-studio-linux.zip", 900 * mb, 1)

        assertTrue(NdkArchiveFinder.find(listOf(File(tempDir, "Download"))).isEmpty())
    }

    @Test
    fun `searches subfolders up to the depth limit and de-duplicates directories`() {
        sparse("Download/Chrome/android-ndk-r28-linux.zip", 700 * mb, 5)
        sparse("Download/a/b/c/android-ndk-r27-linux.zip", 700 * mb, 6) // fundo demais

        val dir = File(tempDir, "Download")
        val found = NdkArchiveFinder.find(listOf(dir, dir, File(dir, "Chrome")))

        assertEquals(listOf("android-ndk-r28-linux.zip"), found.map { it.file.name })
    }

    @Test
    fun `missing directories are skipped without error`() {
        assertTrue(NdkArchiveFinder.find(listOf(File(tempDir, "nao-existe"))).isEmpty())
    }
}
