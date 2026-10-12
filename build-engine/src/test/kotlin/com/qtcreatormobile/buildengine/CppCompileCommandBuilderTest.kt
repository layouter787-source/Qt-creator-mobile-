package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CppCompileCommandBuilderTest {

    private val config = NdkConfig(
        clangPath = "/data/data/com.qtcreatormobile.app/toolchain/cpp/usr/bin/clang",
        sysrootPath = "/data/data/com.qtcreatormobile.app/toolchain/cpp/sysroot",
        abi = "arm64-v8a",
        apiLevel = 26,
    )

    @Test
    fun `builds a valid clang command line for arm64`() {
        val task = CompileTask(
            id = "compile:app:src/native/main.cpp",
            language = Language.CPP,
            sourcePath = "src/native/main.cpp",
            outputPath = "build/app/obj/main.o",
        )

        val command = CppCompileCommandBuilder.build(task, config)

        assertTrue(command.contains("--target=aarch64-linux-android26"))
        assertTrue(command.contains("--sysroot=${config.sysrootPath}"))
        assertTrue(command.contains("-std=c++17"))
        assertEquals("src/native/main.cpp", command[command.indexOf("-c") + 1])
        assertEquals("build/app/obj/main.o", command[command.indexOf("-o") + 1])
    }

    @Test
    fun `uses the c standard for c sources`() {
        val task = CompileTask("compile:app:util.c", Language.C, "util.c", "util.o")

        val command = CppCompileCommandBuilder.build(task, config)

        assertTrue(command.contains("-std=c17"))
    }

    @Test
    fun `rejects non-cpp tasks`() {
        val javaTask = CompileTask(
            id = "compile:app:Main.java",
            language = Language.JAVA,
            sourcePath = "Main.java",
            outputPath = "Main.class",
        )

        assertFailsWith<IllegalArgumentException> {
            CppCompileCommandBuilder.build(javaTask, config)
        }
    }
}
