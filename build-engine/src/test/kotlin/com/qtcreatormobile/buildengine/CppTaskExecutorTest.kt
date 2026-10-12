package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeProcessRunner(
    private val result: ProcessResult,
) : ProcessRunner {
    var lastCommand: List<String>? = null

    override fun run(command: List<String>, workingDir: File): ProcessResult {
        lastCommand = command
        return result
    }
}

class CppTaskExecutorTest {

    private val config = NdkConfig(
        clangPath = "/toolchain/bin/clang",
        sysrootPath = "/toolchain/sysroot",
    )

    @Test
    fun `succeeds when the process exits with code 0`() {
        val fakeRunner = FakeProcessRunner(ProcessResult(exitCode = 0, stdout = "ok", stderr = ""))
        val executor = CppTaskExecutor(config, fakeRunner, File("."))

        val task = CompileTask(
            id = "compile:app:main.cpp",
            language = Language.CPP,
            sourcePath = "main.cpp",
            outputPath = "build/main.o",
        )

        val result = executor.execute(task)

        assertTrue(result.succeeded)
        assertEquals("compile:app:main.cpp", result.taskId)
        assertTrue(fakeRunner.lastCommand!!.contains("main.cpp"))
    }

    @Test
    fun `accepts c tasks too`() {
        val fakeRunner = FakeProcessRunner(ProcessResult(0, "ok", ""))
        val executor = CppTaskExecutor(config, fakeRunner, File("."))
        val task = CompileTask("compile:app:util.c", Language.C, "util.c", "build/util.o")

        assertTrue(executor.execute(task).succeeded)
    }

    @Test
    fun `reports failure and captures stderr when clang exits non-zero`() {
        val fakeRunner = FakeProcessRunner(
            ProcessResult(exitCode = 1, stdout = "", stderr = "main.cpp:3:1: error: expected ';'")
        )
        val executor = CppTaskExecutor(config, fakeRunner, File("."))

        val task = CompileTask(
            id = "compile:app:main.cpp",
            language = Language.CPP,
            sourcePath = "main.cpp",
            outputPath = "build/main.o",
        )

        val result = executor.execute(task)

        assertEquals(false, result.succeeded)
        assertTrue(result.log.contains("expected ';'"))
    }

    @Test
    fun `refuses to execute tasks in unsupported languages`() {
        val fakeRunner = FakeProcessRunner(ProcessResult(0, "", ""))
        val executor = CppTaskExecutor(config, fakeRunner, File("."))

        val kotlinTask = CompileTask(
            id = "compile:app:Main.kt",
            language = Language.KOTLIN,
            sourcePath = "Main.kt",
            outputPath = "build/Main.class",
        )

        assertFailsWith<BuildEngineException> { executor.execute(kotlinTask) }
    }
}
