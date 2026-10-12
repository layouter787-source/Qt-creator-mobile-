package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApkAssemblerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-apk-assembler-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `adds new entries while preserving the original ones`() {
        val apkFile = File(tempDir, "app.apk")
        ZipOutputStream(apkFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("original manifest".toByteArray())
            zip.closeEntry()
        }

        val soFile = File(tempDir, "libapp.so").apply { writeText("fake native library bytes") }
        val dexFile = File(tempDir, "classes.dex").apply { writeText("fake dex bytes") }

        ApkAssembler.addEntries(
            apkFile.absolutePath,
            mapOf(
                "lib/arm64-v8a/libapp.so" to soFile.absolutePath,
                "classes.dex" to dexFile.absolutePath,
            ),
        )

        ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            assertEquals("original manifest", zip.getInputStream(manifestEntry).bufferedReader().readText())

            val soEntry = zip.getEntry("lib/arm64-v8a/libapp.so")
            assertEquals("fake native library bytes", zip.getInputStream(soEntry).bufferedReader().readText())

            val dexEntry = zip.getEntry("classes.dex")
            assertEquals("fake dex bytes", zip.getInputStream(dexEntry).bufferedReader().readText())
        }
    }
}
