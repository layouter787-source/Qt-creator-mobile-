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

class PackagingToolsInstallerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-packaging-tools-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        val file = File(tempDir, "src-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        val bytes = file.readBytes()
        file.delete()
        return bytes
    }

    private val fakeManifest = """
        <?xml version="1.0" encoding="UTF-8"?>
        <sdk:sdk-repository xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/3">
          <remotePackage path="build-tools;35.0.0">
            <revision><major>35</major><minor>0</minor><micro>0</micro></revision>
            <archives><archive><host-os>linux</host-os><complete><url>build-tools.zip</url></complete></archive></archives>
          </remotePackage>
          <remotePackage path="platforms;android-34">
            <revision><major>3</major></revision>
            <archives><archive><complete><url>platform.zip</url></complete></archive></archives>
          </remotePackage>
        </sdk:sdk-repository>
    """.trimIndent()

    @Test
    fun `installs the two jars and android jar under the packaging root`() {
        val buildToolsZip = zipBytes(
            "android-15/lib/d8.jar" to "d8 bytes",
            "android-15/lib/apksigner.jar" to "signer bytes",
        )
        val platformZip = zipBytes("android-14/android.jar" to "framework bytes")

        val downloader = object : Downloader {
            override fun download(url: String, destination: File, listener: DownloadProgressListener) {
                val bytes = when {
                    url.endsWith("build-tools.zip") -> buildToolsZip
                    url.endsWith("platform.zip") -> platformZip
                    else -> error("URL inesperada no teste: $url")
                }
                destination.writeBytes(bytes)
                listener.onProgress(DownloadProgress(bytes.size.toLong(), bytes.size.toLong()))
            }
        }

        val installer = PackagingToolsInstaller(
            repositoryResolver = AndroidRepositoryResolver(TextFetcher { fakeManifest }),
            downloader = downloader,
        )
        val installRoot = File(tempDir, "packaging")

        installer.install(installRoot)

        assertEquals("d8 bytes", File(installRoot, PackagingLayout.D8_JAR).readText())
        assertEquals("signer bytes", File(installRoot, PackagingLayout.APKSIGNER_JAR).readText())
        assertEquals("framework bytes", File(installRoot, PackagingLayout.ANDROID_JAR).readText())
    }
}

class Aapt2TermuxInstallerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-aapt2-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `links packaging bin aapt2 to the termux binary`() {
        val termuxRoot = File(tempDir, "cpp")
        val packaging = File(tempDir, "packaging")
        val fakeTermux = object : ToolchainInstaller {
            override fun install(installRootDir: File) {
                File(installRootDir, "usr/bin").mkdirs()
                File(installRootDir, "usr/bin/aapt2").writeText("termux aapt2")
            }
        }

        Aapt2TermuxInstaller(termuxRoot, fakeTermux).install(packaging)

        val link = File(packaging, PackagingLayout.AAPT2_BINARY)
        assertEquals("termux aapt2", link.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertTrue(link.canExecute())
    }

    @Test
    fun `fails clearly when the termux package does not provide aapt2`() {
        val installer = Aapt2TermuxInstaller(File(tempDir, "cpp"), object : ToolchainInstaller { override fun install(installRootDir: File) = Unit })
        val error = kotlin.test.assertFailsWith<BuildEngineException> { installer.install(File(tempDir, "packaging")) }
        assertTrue(error.message!!.contains("aapt2"))
    }
}

class JdkInstallerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-jdk-installer-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private val fakeReleaseJson = """
        {
          "assets": [
            { "name": "openjdk-17-android-aarch64.zip", "browser_download_url": "https://example.com/jdk-aarch64.zip" }
          ]
        }
    """.trimIndent()

    @Test
    fun `resolves the real asset, extracts and flattens the wrapping root folder`() {
        val jdkZip = File(tempDir, "jdk.zip")
        ZipOutputStream(jdkZip.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("jdk-17.0.9+9/bin/javac"))
            zip.write("fake javac".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("jdk-17.0.9+9/bin/keytool"))
            zip.write("fake keytool".toByteArray())
            zip.closeEntry()
        }
        val jdkBytes = jdkZip.readBytes()

        val downloader = object : Downloader {
            override fun download(url: String, destination: File, listener: DownloadProgressListener) {
                destination.writeBytes(jdkBytes)
                listener.onProgress(DownloadProgress(jdkBytes.size.toLong(), jdkBytes.size.toLong()))
            }
        }

        val installer = JdkInstaller(
            releaseResolver = GitHubReleaseResolver(TextFetcher { fakeReleaseJson }),
            downloader = downloader,
        )
        val installRoot = File(tempDir, "java")

        installer.install(installRoot)

        assertEquals("fake javac", File(installRoot, "bin/javac").readText())
        assertEquals("fake keytool", File(installRoot, "bin/keytool").readText())
        assertTrue(!File(installRoot, "jdk-17.0.9+9").exists())
    }
}
