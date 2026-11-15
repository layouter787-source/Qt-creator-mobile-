package com.qtcreatormobile.buildengine

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NdkSysrootImporterTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-ndk-import-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private val prefix = "android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/sysroot"

    private fun zipOf(name: String = "ndk.zip", block: ZipOutputStream.(add: (String, String) -> Unit) -> Unit): File {
        val file = File(tempDir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            val add = { entry: String, content: String ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            zip.block(add)
        }
        return file
    }

    private fun fakeNdk(name: String = "ndk.zip", withTarget: Boolean = true): File = zipOf(name) { add ->
        add("android-ndk-r30/source.properties", "Pkg.Desc = Android NDK\nPkg.Revision = 30.0.14904198\n")
        add("android-ndk-r30/CHANGELOG.md", "nao faz parte do sysroot")
        add("android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/bin/clang", "compilador do host, nao usado")
        if (withTarget) {
            add("$prefix/usr/include/stdio.h", "stdio")
            add("$prefix/usr/include/android/native_activity.h", "native activity")
            add("$prefix/usr/include/aarch64-linux-android/asm/types.h", "arm64 asm")
            add("$prefix/usr/include/x86_64-linux-android/asm/types.h", "x86_64 asm (descartado)")
            add("$prefix/usr/lib/aarch64-linux-android/26/libc.so", "libc arm64")
            add("$prefix/usr/lib/aarch64-linux-android/libc++_shared.so", "libc++ arm64")
            add("$prefix/usr/lib/arm-linux-androideabi/26/libc.so", "libc arm32")
            add("$prefix/usr/lib/x86_64-linux-android/26/libc.so", "libc x86_64 (descartado)")
            add("$prefix/usr/lib/riscv64-linux-android/35/libc.so", "libc riscv64 (descartado)")
        }
    }

    private fun assertImported(sysroot: File) {
        assertEquals("stdio", File(sysroot, "usr/include/stdio.h").readText())
        assertTrue(File(sysroot, "usr/include/android/native_activity.h").isFile)
        assertEquals("libc arm64", File(sysroot, "usr/lib/aarch64-linux-android/26/libc.so").readText())
        assertTrue(File(sysroot, "usr/lib/aarch64-linux-android/libc++_shared.so").isFile)
        assertTrue(File(sysroot, "usr/lib/arm-linux-androideabi/26/libc.so").isFile)
        // Outras arquiteturas e arquivos fora do sysroot ficam de fora.
        assertFalse(File(sysroot, "usr/lib/x86_64-linux-android").exists())
        assertFalse(File(sysroot, "usr/lib/riscv64-linux-android").exists())
        assertFalse(File(sysroot, "usr/include/x86_64-linux-android").exists())
        assertFalse(File(sysroot, "CHANGELOG.md").exists())
        assertFalse(File(sysroot, "bin").exists())
        assertEquals("30.0.14904198", File(sysroot, NdkSysrootImporter.VERSION_FILE).readText())
    }

    @Test
    fun `imports the sysroot from a file and reports progress up to 100 percent`() {
        val sysroot = File(tempDir, "toolchain/cpp/sysroot")
        val progress = mutableListOf<Float>()

        val result = NdkSysrootImporter().importFromFile(fakeNdk(), sysroot, progress = { progress += it })

        assertImported(sysroot)
        assertEquals("30.0.14904198", result.ndkRevision)
        assertEquals(6, result.filesExtracted)
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a }, "o progresso nunca deve recuar: $progress")
        assertFalse(File(sysroot.parentFile, "sysroot.importing").exists(), "pasta temporaria deve sumir")
    }

    @Test
    fun `imports the same result from a plain stream`() {
        val zip = fakeNdk()
        val sysroot = File(tempDir, "toolchain/cpp/sysroot")

        val result = zip.inputStream().use { NdkSysrootImporter().importFromStream(it, zip.length(), sysroot) }

        assertImported(sysroot)
        assertEquals("30.0.14904198", result.ndkRevision)
        assertEquals(6, result.filesExtracted)
    }

    @Test
    fun `accepts the windows host layout too`() {
        val zip = zipOf("win.zip") { add ->
            val win = "android-ndk-r30/toolchains/llvm/prebuilt/windows-x86_64/sysroot"
            add("$win/usr/include/stdio.h", "stdio")
            add("$win/usr/include/android/native_activity.h", "na")
            add("$win/usr/lib/aarch64-linux-android/26/libc.so", "libc")
        }
        val sysroot = File(tempDir, "cpp/sysroot")

        NdkSysrootImporter().importFromFile(zip, sysroot)

        assertTrue(File(sysroot, "usr/include/stdio.h").isFile)
    }

    @Test
    fun `replaces an existing sysroot instead of merging into it`() {
        val sysroot = File(tempDir, "cpp/sysroot")
        File(sysroot, "usr/include/velho.h").apply { parentFile.mkdirs() }.writeText("de uma versao anterior")

        NdkSysrootImporter().importFromFile(fakeNdk(), sysroot)

        assertFalse(File(sysroot, "usr/include/velho.h").exists())
        assertImported(sysroot)
    }

    @Test
    fun `rejects a zip that is not an ndk and leaves the current sysroot untouched`() {
        val sysroot = File(tempDir, "cpp/sysroot")
        File(sysroot, "usr/include/stdio.h").apply { parentFile.mkdirs() }.writeText("sysroot bom que ja existia")
        val notNdk = zipOf("outro.zip") { add -> add("alguma-coisa/readme.txt", "isto nao e o NDK") }

        val error = assertFailsWith<BuildEngineException> { NdkSysrootImporter().importFromFile(notNdk, sysroot) }

        assertTrue(error.message!!.contains("nao parece ser o NDK"), error.message)
        assertEquals("sysroot bom que ja existia", File(sysroot, "usr/include/stdio.h").readText())
        assertFalse(File(sysroot.parentFile, "sysroot.importing").exists())
    }

    @Test
    fun `rejects an incomplete sysroot instead of installing it`() {
        val sysroot = File(tempDir, "cpp/sysroot")
        val incomplete = zipOf("incompleto.zip") { add -> add("$prefix/usr/include/stdio.h", "so tem um cabecalho") }

        val error = assertFailsWith<BuildEngineException> { NdkSysrootImporter().importFromFile(incomplete, sysroot) }

        assertTrue(error.message!!.contains("incompleto"), error.message)
        assertFalse(sysroot.exists())
    }

    @Test
    fun `refuses zip-slip entries and writes nothing outside the target`() {
        val sysroot = File(tempDir, "cpp/sysroot")
        val evil = zipOf("evil.zip") { add ->
            add("$prefix/usr/include/stdio.h", "stdio")
            add("$prefix/../../../../../../evil.txt", "fora do destino")
        }

        assertFailsWith<BuildEngineException> { NdkSysrootImporter().importFromFile(evil, sysroot) }

        assertFalse(File(tempDir, "evil.txt").exists())
        assertFalse(File(tempDir.parentFile, "evil.txt").exists())
        assertFalse(sysroot.exists())
    }

    @Test
    fun `can be cancelled and cleans up its temporary folder`() {
        val sysroot = File(tempDir, "cpp/sysroot")

        assertFailsWith<java.util.concurrent.CancellationException> {
            NdkSysrootImporter().importFromFile(fakeNdk(), sysroot, shouldStop = { true })
        }

        assertFalse(sysroot.exists())
        assertFalse(File(sysroot.parentFile, "sysroot.importing").exists())
    }

    @Test
    fun `entry mapping ignores non-sysroot entries and other architectures`() {
        val importer = NdkSysrootImporter()
        assertEquals("usr/include/stdio.h", importer.relativeSysrootPath("android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/stdio.h"))
        assertEquals("usr/include/stdio.h", importer.relativeSysrootPath("toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include/stdio.h"))
        assertNull(importer.relativeSysrootPath("android-ndk-r27c/build/sysroot/usr/include/stdio.h"))
        assertNull(importer.relativeSysrootPath("android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/sysroot.txt"))
        assertNull(importer.relativeSysrootPath("android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/i686-linux-android/21/libc.so"))
    }
}
