package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeEnvironmentProcessRunner(private val result: ProcessResult) : EnvironmentProcessRunner {
    var lastCommand: List<String>? = null
    var lastEnv: Map<String, String>? = null
    override fun run(command: List<String>, workingDir: File): ProcessResult = run(command, workingDir, emptyMap())
    override fun run(command: List<String>, workingDir: File, env: Map<String, String>): ProcessResult {
        lastCommand = command
        lastEnv = env
        return result
    }
}

class JavaTaskExecutorTest {
    @Test
    fun `compiles a java task successfully`() {
        val runner = FakeProcessRunner(ProcessResult(0, "ok", ""))
        val executor = JavaTaskExecutor(JavaToolchainConfig("/toolchain/java/bin/javac"), runner, File("."))
        val task = CompileTask("compile:app:Main.java", Language.JAVA, "Main.java", "build/Main.class")

        val result = executor.execute(task)

        assertTrue(result.succeeded)
        assertTrue(runner.lastCommand!!.contains("Main.java"))
    }

    @Test
    fun `refuses non-java tasks`() {
        val runner = FakeProcessRunner(ProcessResult(0, "", ""))
        val executor = JavaTaskExecutor(JavaToolchainConfig("/toolchain/java/bin/javac"), runner, File("."))
        val task = CompileTask("compile:app:Main.kt", Language.KOTLIN, "Main.kt", "build/Main.class")

        kotlin.test.assertFailsWith<BuildEngineException> { executor.execute(task) }
    }
}

class KotlinTaskExecutorTest {

    private fun config() = KotlinToolchainConfig(kotlincPath = "/toolchain/kotlin/bin/kotlinc", javaHome = "/toolchain/java")

    @Test
    fun `compiles a kotlin task successfully`() {
        val runner = FakeProcessRunner(ProcessResult(0, "ok", ""))
        val executor = KotlinTaskExecutor(config(), runner, File("."))
        val task = CompileTask("compile:app:Main.kt", Language.KOTLIN, "Main.kt", "build/Main.class")

        val result = executor.execute(task)

        assertTrue(result.succeeded)
        assertTrue(runner.lastCommand!!.contains("Main.kt"))
    }

    @Test
    fun `passes JAVA_HOME to the process when the runner supports environment`() {
        val runner = FakeEnvironmentProcessRunner(ProcessResult(0, "ok", ""))
        val executor = KotlinTaskExecutor(config(), runner, File("."))
        val task = CompileTask("compile:app:Main.kt", Language.KOTLIN, "Main.kt", "build/Main.class")

        executor.execute(task)

        assertEquals("/toolchain/java", runner.lastEnv?.get("JAVA_HOME"))
    }

    @Test
    fun `refuses non-kotlin tasks`() {
        val runner = FakeProcessRunner(ProcessResult(0, "", ""))
        val executor = KotlinTaskExecutor(config(), runner, File("."))
        val task = CompileTask("compile:app:Main.java", Language.JAVA, "Main.java", "build/Main.class")

        kotlin.test.assertFailsWith<BuildEngineException> { executor.execute(task) }
    }
}

class CompositeTaskExecutorTest {
    @Test
    fun `dispatches each task to the matching language executor`() {
        val cppRunner = FakeProcessRunner(ProcessResult(0, "cpp-ok", ""))
        val javaRunner = FakeProcessRunner(ProcessResult(0, "java-ok", ""))
        val kotlinRunner = FakeProcessRunner(ProcessResult(0, "kotlin-ok", ""))

        val composite = CompositeTaskExecutor(
            cppExecutor = CppTaskExecutor(NdkConfig("/t/clang", "/t/sysroot"), cppRunner, File(".")),
            javaExecutor = JavaTaskExecutor(JavaToolchainConfig("/t/javac"), javaRunner, File(".")),
            kotlinExecutor = KotlinTaskExecutor(
                KotlinToolchainConfig(kotlincPath = "/t/kotlinc", javaHome = "/t/java"), kotlinRunner, File("."),
            ),
        )

        val cppTask = CompileTask("compile:app:a.cpp", Language.CPP, "a.cpp", "a.o")
        val javaTask = CompileTask("compile:app:B.java", Language.JAVA, "B.java", "B.class")
        val kotlinTask = CompileTask("compile:app:C.kt", Language.KOTLIN, "C.kt", "C.class")

        assertEquals("cpp-ok", composite.execute(cppTask).log)
        assertEquals("java-ok", composite.execute(javaTask).log)
        assertEquals("kotlin-ok", composite.execute(kotlinTask).log)
    }
}
