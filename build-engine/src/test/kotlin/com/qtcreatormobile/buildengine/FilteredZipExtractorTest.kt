package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilteredZipExtractorTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-filtered-zip-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun buildFakeNdkZip(): File {
        val zipFile = File(tempDir, "ndk.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            addEntry("android-ndk-r27b/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/stdio.h", "stdio contents")
            addEntry("android-ndk-r27b/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/26/libc.so", "fake libc")
            addEntry("android-ndk-r27b/CHANGELOG.md", "changelog, not part of sysroot")
            addEntry("android-ndk-r27b/source.properties", "irrelevant metadata")
        }
        return zipFile
    }

    @Test
    fun `extracts only sysroot entries and strips the ndk-version prefix`() {
        val zip = buildFakeNdkZip()
        val targetDir = File(tempDir, "extracted")

        FilteredZipExtractor.ndkSysrootOnly().extract(zip, targetDir)

        val stdioHeader = File(targetDir, "sysroot/usr/include/stdio.h")
        val libc = File(targetDir, "sysroot/usr/lib/aarch64-linux-android/26/libc.so")
        assertTrue(stdioHeader.exists())
        assertEquals("stdio contents", stdioHeader.readText())
        assertTrue(libc.exists())
        assertEquals("fake libc", libc.readText())

        assertFalse(File(targetDir, "android-ndk-r27b/CHANGELOG.md").exists())
        assertFalse(File(targetDir, "CHANGELOG.md").exists())
        assertFalse(File(targetDir, "source.properties").exists())
    }
}
