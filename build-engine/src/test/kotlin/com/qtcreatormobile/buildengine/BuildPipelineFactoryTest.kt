package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildPipelineFactoryTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-factory-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `a kotlin task fails with a clear message when kotlin is not installed`() {
        val paths = ToolchainPaths(File(tempDir, "toolchain"))
        val executor = BuildPipelineFactory.createTaskExecutor(paths, FakeProcessRunner(ProcessResult(0, "", "")), tempDir)

        val result = executor.execute(CompileTask("compile:app:Main.kt", Language.KOTLIN, "Main.kt", "build/Main.class"))

        assertFalse(result.succeeded)
        assertTrue(result.log.contains("Kotlin"))
    }

    @Test
    fun `an installed kotlin gets JAVA_HOME, the compose classpath and the compose plugin`() {
        val paths = ToolchainPaths(File(tempDir, "toolchain"))
        paths.kotlinc.parentFile.mkdirs()
        paths.kotlinc.writeText("fake kotlinc")
        paths.composeLibsDir.mkdirs()
        File(paths.composeLibsDir, "material3-1.4.0.jar").writeText("x")
        File(paths.composeLibsDir, "kotlin-compose-compiler-plugin-embeddable-2.4.0.jar").writeText("x")

        val runner = object : EnvironmentProcessRunner {
            var command: List<String>? = null
            var env: Map<String, String>? = null
            override fun run(command: List<String>, workingDir: File): ProcessResult = run(command, workingDir, emptyMap())
            override fun run(command: List<String>, workingDir: File, env: Map<String, String>): ProcessResult {
                this.command = command
                this.env = env
                return ProcessResult(0, "ok", "")
            }
        }
        val executor = BuildPipelineFactory.createTaskExecutor(paths, runner, tempDir)

        val result = executor.execute(CompileTask("compile:app:Screen.kt", Language.KOTLIN, "Screen.kt", "build/Screen.class"))

        assertTrue(result.succeeded)
        assertEquals(paths.javaDir.absolutePath, runner.env?.get("JAVA_HOME"))
        val command = runner.command!!
        assertTrue(command[command.indexOf("-classpath") + 1].contains("material3-1.4.0.jar"))
        assertTrue(command.any { it.startsWith("-Xplugin=") && it.contains("kotlin-compose-compiler-plugin") })
    }

    @Test
    fun `create generates the debug keystore once and reuses it afterwards`() {
        val paths = ToolchainPaths(File(tempDir, "toolchain"))
        val runner = object : ProcessRunner {
            var keytoolCalls = 0
            override fun run(command: List<String>, workingDir: File): ProcessResult {
                if (command.contains("-genkeypair")) {
                    keytoolCalls++
                    paths.debugKeystore.parentFile.mkdirs()
                    paths.debugKeystore.writeText("keystore")
                }
                return ProcessResult(0, "", "")
            }
        }

        BuildPipelineFactory.create(paths, runner, tempDir)
        assertTrue(paths.debugKeystore.exists())
        assertEquals(1, runner.keytoolCalls)

        BuildPipelineFactory.create(paths, runner, tempDir)
        assertEquals(1, runner.keytoolCalls)
    }
}
