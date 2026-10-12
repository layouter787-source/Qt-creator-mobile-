package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.EntryPoint
import com.qtcreatormobile.projectmodel.Target
import com.qtcreatormobile.projectmodel.TargetSources
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

private class AlwaysSucceedsTaskExecutor : TaskExecutor {
    override fun execute(task: CompileTask) = TaskResult(task.id, true, "ok")
}

private class FailingTaskExecutor(private val failingSourcePath: String) : TaskExecutor {
    override fun execute(task: CompileTask) =
        if (task.sourcePath == failingSourcePath) TaskResult(task.id, false, "erro de compilacao simulado no teste")
        else TaskResult(task.id, true, "ok")
}

private class AlwaysSucceedsLinkExecutor : LinkExecutor {
    override fun execute(linkTask: LinkTask, graph: TaskGraph) = TaskResult(linkTask.id, true, "linked")
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

    @Test
    fun `runs every step and produces a signed apk when everything succeeds`() {
        val pipeline = BuildPipeline(
            taskExecutor = AlwaysSucceedsTaskExecutor(),
            linkExecutor = AlwaysSucceedsLinkExecutor(),
            dexer = AlwaysSucceedsDexer(),
            aapt2Linker = FakeAapt2Linker(),
            apkSigner = AlwaysSucceedsApkSigner(),
        )

        val outputApk = File(tempDir, "app.apk").absolutePath
        val result = pipeline.build(target, "AndroidManifest.xml", outputApk)

        assertTrue(result.succeeded)
        assertEquals(outputApk, result.outputApkPath)
        assertTrue(File(outputApk).exists())
        assertTrue(result.steps.any { it.stepName.startsWith("compile:") })
        assertTrue(result.steps.any { it.stepName.startsWith("link:") })
        assertTrue(result.steps.any { it.stepName.startsWith("dex:") })
        assertTrue(result.steps.any { it.stepName.startsWith("sign:") })
    }

    @Test
    fun `stops immediately when a compile task fails, never reaching later steps`() {
        val pipeline = BuildPipeline(
            taskExecutor = FailingTaskExecutor(failingSourcePath = "src/main.cpp"),
            linkExecutor = AlwaysSucceedsLinkExecutor(),
            dexer = AlwaysSucceedsDexer(),
            aapt2Linker = FakeAapt2Linker(),
            apkSigner = AlwaysSucceedsApkSigner(),
        )

        val outputApk = File(tempDir, "app.apk").absolutePath
        val result = pipeline.build(target, "AndroidManifest.xml", outputApk)

        assertFalse(result.succeeded)
        assertEquals(null, result.outputApkPath)
        assertFalse(result.steps.any { it.stepName.startsWith("sign:") })
        assertFalse(File(outputApk).exists())
    }
}
