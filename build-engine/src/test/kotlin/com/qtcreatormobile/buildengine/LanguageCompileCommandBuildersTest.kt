package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JavaCompileCommandBuilderTest {

    @Test
    fun `builds a javac command with output directory and source`() {
        val config = JavaToolchainConfig(javacPath = "/toolchain/java/bin/javac")
        val task = CompileTask(
            id = "compile:app:Main.java",
            language = Language.JAVA,
            sourcePath = "src/Main.java",
            outputPath = "build/app/classes/Main.class",
        )

        val command = JavaCompileCommandBuilder.build(task, config)

        assertEquals("/toolchain/java/bin/javac", command[0])
        assertEquals("build/app/classes", command[command.indexOf("-d") + 1])
        assertEquals("src/Main.java", command.last())
    }

    @Test
    fun `rejects non-java tasks`() {
        val config = JavaToolchainConfig(javacPath = "/toolchain/java/bin/javac")
        val cppTask = CompileTask(
            id = "compile:app:main.cpp",
            language = Language.CPP,
            sourcePath = "main.cpp",
            outputPath = "main.o",
        )

        assertFailsWith<IllegalArgumentException> { JavaCompileCommandBuilder.build(cppTask, config) }
    }
}

class KotlinCompileCommandBuilderTest {

    private val plainConfig = KotlinToolchainConfig(
        kotlincPath = "/toolchain/kotlin/bin/kotlinc",
        javaHome = "/toolchain/java",
    )

    @Test
    fun `builds a kotlinc command with output directory and source`() {
        val task = CompileTask(
            id = "compile:app:Main.kt",
            language = Language.KOTLIN,
            sourcePath = "src/Main.kt",
            outputPath = "build/app/classes/Main.class",
        )

        val command = KotlinCompileCommandBuilder.build(task, plainConfig)

        assertEquals("/toolchain/kotlin/bin/kotlinc", command[0])
        assertEquals("build/app/classes", command[command.indexOf("-d") + 1])
        assertEquals("src/Main.kt", command.last())
    }

    @Test
    fun `includes classpath and compose plugin when configured`() {
        val config = plainConfig.copy(
            classpathJars = listOf("/toolchain/kotlin/compose-libs/runtime-1.12.0.jar", "/toolchain/kotlin/compose-libs/material3-1.4.0.jar"),
            composeCompilerPluginJar = "/toolchain/kotlin/compose-libs/kotlin-compose-compiler-plugin-embeddable-2.4.0.jar",
        )
        val task = CompileTask("compile:app:Screen.kt", Language.KOTLIN, "src/Screen.kt", "build/Screen.class")

        val command = KotlinCompileCommandBuilder.build(task, config)

        val classpathArg = command[command.indexOf("-classpath") + 1]
        assertTrue(classpathArg.contains("runtime-1.12.0.jar"))
        assertTrue(classpathArg.contains("material3-1.4.0.jar"))
        assertTrue(command.any { it.startsWith("-Xplugin=") && it.contains("kotlin-compose-compiler-plugin") })
    }

    @Test
    fun `omits classpath and plugin flags when not configured`() {
        val task = CompileTask("compile:app:Plain.kt", Language.KOTLIN, "src/Plain.kt", "build/Plain.class")

        val command = KotlinCompileCommandBuilder.build(task, plainConfig)

        assertTrue(command.none { it == "-classpath" })
        assertTrue(command.none { it.startsWith("-Xplugin=") })
    }

    @Test
    fun `rejects non-kotlin tasks`() {
        val javaTask = CompileTask(
            id = "compile:app:Main.java",
            language = Language.JAVA,
            sourcePath = "Main.java",
            outputPath = "Main.class",
        )

        assertFailsWith<IllegalArgumentException> { KotlinCompileCommandBuilder.build(javaTask, plainConfig) }
    }
}
