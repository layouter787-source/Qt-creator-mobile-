package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ToolchainLayoutTest {

    @Test
    fun `each distinct toolchain gets its own non-overlapping subdirectory`() {
        val subdirs = Language.entries.map { ToolchainLayout.subdirFor(it) }
        // C e CPP compartilham "cpp"; Java e Kotlin tem os seus -> 3 diretorios distintos.
        assertEquals(3, subdirs.distinct().size)
    }

    @Test
    fun `c and cpp share the same toolchain (both are the clang toolchain)`() {
        assertEquals(ToolchainLayout.subdirFor(Language.C), ToolchainLayout.subdirFor(Language.CPP))
        assertEquals(ToolchainLayout.mainBinaryNameFor(Language.C), ToolchainLayout.mainBinaryNameFor(Language.CPP))
    }

    @Test
    fun `java and kotlin have distinct binaries and directories`() {
        assertNotEquals(ToolchainLayout.subdirFor(Language.JAVA), ToolchainLayout.subdirFor(Language.KOTLIN))
        assertNotEquals(
            ToolchainLayout.mainBinaryNameFor(Language.JAVA),
            ToolchainLayout.mainBinaryNameFor(Language.KOTLIN),
        )
    }
}
