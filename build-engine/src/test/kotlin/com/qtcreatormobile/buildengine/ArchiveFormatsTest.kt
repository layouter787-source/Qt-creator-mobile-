package com.qtcreatormobile.buildengine

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArchiveFormatsTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-archive-formats-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `tar gz extractor unpacks files and keeps the executable bit`() {
        val archive = File(tempDir, "jdk.tar.gz")
        GzipCompressorOutputStream(archive.outputStream()).use { gz ->
            TarArchiveOutputStream(gz).use { tar ->
                val content = "fake keytool".toByteArray()
                val entry = TarArchiveEntry("bin/keytool")
                entry.size = content.size.toLong()
                entry.mode = "100755".toInt(8)
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
        }

        TarGzExtractor().extract(archive, File(tempDir, "out"))

        val keytool = File(tempDir, "out/bin/keytool")
        assertEquals("fake keytool", keytool.readText())
        assertTrue(keytool.canExecute())
    }

    @Test
    fun `picks the extractor from the url extension`() {
        assertIs<ZipArchiveExtractor>(ArchiveExtractors.forUrl("https://x.com/a.zip"))
        assertIs<TarXzExtractor>(ArchiveExtractors.forUrl("https://x.com/a.tar.xz"))
        assertIs<TarGzExtractor>(ArchiveExtractors.forUrl("https://x.com/a.tar.gz?download=1"))
        assertFailsWith<BuildEngineException> { ArchiveExtractors.forUrl("https://x.com/a.rar") }
    }

    @Test
    fun `flattener moves a single root folder's content up one level`() {
        val dir = File(tempDir, "jdk").apply { mkdirs() }
        File(dir, "jdk-17/bin").mkdirs()
        File(dir, "jdk-17/bin/java").writeText("java")

        DirectoryFlattener.flattenSingleRoot(dir)

        assertEquals("java", File(dir, "bin/java").readText())
        assertFalse(File(dir, "jdk-17").exists())
    }

    @Test
    fun `flattener leaves an already flat layout untouched`() {
        val dir = File(tempDir, "flat").apply { mkdirs() }
        File(dir, "bin").mkdirs()
        File(dir, "bin/java").writeText("java")
        File(dir, "release").writeText("info")

        DirectoryFlattener.flattenSingleRoot(dir)

        assertEquals("java", File(dir, "bin/java").readText())
        assertTrue(File(dir, "release").exists())
    }

    @Test
    fun `build-tools preset extracts only the two jars into lib`() {
        val zip = File(tempDir, "build-tools.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            fun add(name: String, text: String) {
                out.putNextEntry(ZipEntry(name)); out.write(text.toByteArray()); out.closeEntry()
            }
            add("android-15/lib/d8.jar", "d8 bytes")
            add("android-15/lib/apksigner.jar", "signer bytes")
            add("android-15/aapt2", "not wanted here")
            add("android-15/lib/other.jar", "not wanted")
        }

        val target = File(tempDir, "packaging")
        FilteredZipExtractor.buildToolsJarsOnly().extract(zip, target)

        assertEquals("d8 bytes", File(target, PackagingLayout.D8_JAR).readText())
        assertEquals("signer bytes", File(target, PackagingLayout.APKSIGNER_JAR).readText())
        assertFalse(File(target, "lib/other.jar").exists())
        assertFalse(File(target, "aapt2").exists())
    }

    @Test
    fun `platform preset extracts android jar to the destination root`() {
        val zip = File(tempDir, "platform.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("android-14/android.jar")); out.write("framework".toByteArray()); out.closeEntry()
            out.putNextEntry(ZipEntry("android-14/build.prop")); out.write("props".toByteArray()); out.closeEntry()
        }

        val target = File(tempDir, "packaging2")
        FilteredZipExtractor.platformAndroidJarOnly().extract(zip, target)

        assertEquals("framework", File(target, PackagingLayout.ANDROID_JAR).readText())
        assertFalse(File(target, "build.prop").exists())
    }
}
