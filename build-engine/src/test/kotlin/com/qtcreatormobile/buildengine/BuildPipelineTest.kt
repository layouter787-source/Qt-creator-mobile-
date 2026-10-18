package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.EntryPoint
import com.qtcreatormobile.projectmodel.Target
import com.qtcreatormobile.projectmodel.TargetSources
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class AlwaysSucceedsTaskExecutor : TaskExecutor {
    override fun execute(task: CompileTask) = TaskResult(task.id, true, "ok")
}

private class FailingTaskExecutor(private val failingSourcePath: String) : TaskExecutor {
    override fun execute(task: CompileTask) =
        if (task.sourcePath == failingSourcePath) TaskResult(task.id, false, "erro de compilacao simulado no teste")
        else TaskResult(task.id, true, "ok")
}

/** Cria o .so de verdade no disco, como o linker real faria (o pipeline le esse arquivo depois). */
private class WritingLinkExecutor : LinkExecutor {
    override fun execute(linkTask: LinkTask, graph: TaskGraph): TaskResult {
        File(linkTask.outputPath).parentFile?.mkdirs()
        File(linkTask.outputPath).writeText("fake shared library bytes")
        return TaskResult(linkTask.id, true, "linked")
    }
}

/** Linker que diz ter dado certo mas NAO produz o arquivo - o pipeline precisa reportar isso, nao estourar. */
private class LyingLinkExecutor : LinkExecutor {
    override fun execute(linkTask: LinkTask, graph: TaskGraph) = TaskResult(linkTask.id, true, "linked (mentira)")
}

/** Fake que cria um .apk (zip) real e vazio, como se o aapt2 tivesse rodado de verdade. */
private class FakeAapt2Linker : Aapt2Linker {
    override fun link(manifestPath: String, outputApkPath: String): PackagingStepResult {
        File(outputApkPath).parentFile?.mkdirs()
        ZipOutputStream(File(outputApkPath).outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("fake compiled manifest".toByteArray())
            zip.closeEntry()
        }
        return PackagingStepResult(true, "linked (real zip criado no teste)")
    }
}

private class AlwaysSucceedsDexer : Dexer {
    override fun dex(classFilePaths: List<String>, outputDir: String): PackagingStepResult {
        File(outputDir).mkdirs()
        File(outputDir, "classes.dex").writeText("fake dex bytes")
        return PackagingStepResult(true, "dexed")
    }
}

private class AlwaysSucceedsApkSigner : ApkSigner {
    override fun sign(apkPath: String): PackagingStepResult = PackagingStepResult(true, "signed")
}

class BuildPipelineTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-pipeline-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private val target = Target(
        id = "app",
        type = "android-app",
        minSdk = 26,
        sources = TargetSources(cpp = listOf("src/main.cpp"), kotlin = listOf("src/Main.kt")),
        entryPoint = EntryPoint.KOTLIN_WRAPPER,
    )

    private fun buildDir() = File(tempDir, "build").absolutePath

    @Test
    fun `runs every step and produces a signed apk containing the so and dex`() {
        val pipeline = BuildPipeline(
            taskExecutor = AlwaysSucceedsTaskExecutor(),
            linkExecutor = WritingLinkExecutor(),
            dexer = AlwaysSucceedsDexer(),
            aapt2Linker = FakeAapt2Linker(),
            apkSigner = AlwaysSucceedsApkSigner(),
        )

        val outputApk = File(tempDir, "out/app.apk").absolutePath
        val result = pipeline.build(target, "AndroidManifest.xml", outputApk, buildDir())

        assertTrue(result.succeeded)
        assertEquals(outputApk, result.outputApkPath)
        assertTrue(File(outputApk).exists())
        assertTrue(result.steps.any { it.stepName.startsWith("compile:") })
        assertTrue(result.steps.any { it.stepName.startsWith("link:") })
        assertTrue(result.steps.any { it.stepName.startsWith("dex:") })
        assertTrue(result.steps.any { it.stepName.startsWith("assemble:") })
        assertTrue(result.steps.any { it.stepName.startsWith("sign:") })

        // O APK final tem de conter de verdade o manifesto, o .so e o classes.dex.
        ZipFile(outputApk).use { zip ->
            assertTrue(zip.getEntry("AndroidManifest.xml") != null)
            assertTrue(zip.getEntry("lib/arm64-v8a/libapp.so") != null)
            assertTrue(zip.getEntry("classes.dex") != null)
        }
    }

    @Test
    fun `stops immediately when a compile task fails, never reaching later steps`() {
        val pipeline = BuildPipeline(
            taskExecutor = FailingTaskExecutor(failingSourcePath = "src/main.cpp"),
            linkExecutor = WritingLinkExecutor(),
            dexer = AlwaysSucceedsDexer(),
            aapt2Linker = FakeAapt2Linker(),
            apkSigner = AlwaysSucceedsApkSigner(),
        )

        val outputApk = File(tempDir, "out/app.apk").absolutePath
        val result = pipeline.build(target, "AndroidManifest.xml", outputApk, buildDir())

        assertFalse(result.succeeded)
        assertEquals(null, result.outputApkPath)
        assertFalse(result.steps.any { it.stepName.startsWith("sign:") })
        assertFalse(File(outputApk).exists())
    }

    @Test
    fun `reports a failed assemble step instead of crashing when the linked so is missing`() {
        val pipeline = BuildPipeline(
            taskExecutor = AlwaysSucceedsTaskExecutor(),
            linkExecutor = LyingLinkExecutor(),
            dexer = AlwaysSucceedsDexer(),
            aapt2Linker = FakeAapt2Linker(),
            apkSigner = AlwaysSucceedsApkSigner(),
        )

        val outputApk = File(tempDir, "out/app.apk").absolutePath
        val result = pipeline.build(target, "AndroidManifest.xml", outputApk, buildDir())

        assertFalse(result.succeeded)
        assertEquals(null, result.outputApkPath)
        val assemble = result.steps.first { it.stepName.startsWith("assemble:") }
        assertFalse(assemble.succeeded)
        assertFalse(result.steps.any { it.stepName.startsWith("sign:") })
    }
}
