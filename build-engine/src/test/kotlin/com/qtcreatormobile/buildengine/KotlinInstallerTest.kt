package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class KotlinInstallerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-kotlin-installer-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun zipBytes(entryName: String, content: String): ByteArray {
        val f = File(tempDir, "z-${System.nanoTime()}.zip")
        ZipOutputStream(f.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        val bytes = f.readBytes()
        f.delete()
        return bytes
    }

    /** .aar minimo de verdade: um zip contendo classes.jar. */
    private fun aarBytes(classesJarContent: String): ByteArray {
        val innerJar = File(tempDir, "inner-${System.nanoTime()}.jar")
        ZipOutputStream(innerJar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("dummy.class"))
            zip.write(classesJarContent.toByteArray())
            zip.closeEntry()
        }
        val jarBytes = innerJar.readBytes()
        innerJar.delete()

        val aar = File(tempDir, "a-${System.nanoTime()}.aar")
        ZipOutputStream(aar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.jar"))
            zip.write(jarBytes)
            zip.closeEntry()
        }
        val bytes = aar.readBytes()
        aar.delete()
        return bytes
    }

    private val fakeKotlinRelease = """
        { "assets": [ { "name": "kotlin-compiler-2.4.0.zip", "browser_download_url": "https://example.com/kotlin-compiler-2.4.0.zip" } ] }
    """.trimIndent()

    @Test
    fun `installs kotlinc flattened and all compose-material3 jars (extracting aars) into compose-libs`() {
        val kotlincZip = zipBytes("kotlinc/bin/kotlinc", "fake kotlinc launcher")

        val downloader = object : Downloader {
            override fun download(url: String, destination: File, listener: DownloadProgressListener) {
                val bytes = when {
                    url.endsWith("kotlin-compiler-2.4.0.zip") -> kotlincZip
                    url.endsWith(".aar") -> aarBytes("class bytes for ${url.substringAfterLast('/')}")
                    else -> "jar bytes for ${url.substringAfterLast('/')}".toByteArray()
                }
                destination.writeBytes(bytes)
                listener.onProgress(DownloadProgress(bytes.size.toLong(), bytes.size.toLong()))
            }
        }

        val installer = KotlinInstaller(
            releaseResolver = GitHubReleaseResolver(TextFetcher { fakeKotlinRelease }),
            mavenDownloader = MavenArtifactDownloader(downloader),
            downloader = downloader,
        )
        val installRoot = File(tempDir, "kotlin")

        installer.install(installRoot)

        assertTrue(File(installRoot, "bin/kotlinc").exists())
        assertTrue(!File(installRoot, "kotlinc").exists()) // pasta-raiz do zip foi achatada

        val libsDir = File(installRoot, "compose-libs")
        assertTrue(File(libsDir, "runtime-1.12.0.jar").exists())
        assertTrue(File(libsDir, "material3-1.4.0.jar").exists())
        assertTrue(File(libsDir, "kotlin-stdlib-2.4.0.jar").exists())
        assertTrue(File(libsDir, "kotlin-compose-compiler-plugin-embeddable-2.4.0.jar").exists())
    }
}
