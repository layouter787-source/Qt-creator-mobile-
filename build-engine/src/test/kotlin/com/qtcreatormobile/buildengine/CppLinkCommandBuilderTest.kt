package com.qtcreatormobile.buildengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CppLinkCommandBuilderTest {

    private val config = NdkConfig(
        clangPath = "/toolchain/cpp/usr/bin/clang",
        sysrootPath = "/toolchain/cpp/sysroot",
    )

    @Test
    fun `builds a shared-library link command with all object files`() {
        val linkTask = LinkTask(
            id = "link:app",
            targetId = "app",
            inputTaskIds = setOf("a", "b"),
            outputPath = "build/app/output/libapp.so",
        )
        val objectFiles = listOf("build/app/obj/a.o", "build/app/obj/b.o")

        val command = CppLinkCommandBuilder.build(linkTask, objectFiles, config)

        assertTrue(command.contains("-shared"))
        assertEquals("build/app/output/libapp.so", command[command.indexOf("-o") + 1])
        assertTrue(command.containsAll(objectFiles))
    }

    @Test
    fun `rejects linking with no object files`() {
        val linkTask = LinkTask("link:app", "app", emptySet(), "build/app/output/libapp.so")

        assertFailsWith<IllegalArgumentException> {
            CppLinkCommandBuilder.build(linkTask, emptyList(), config)
        }
    }
}
