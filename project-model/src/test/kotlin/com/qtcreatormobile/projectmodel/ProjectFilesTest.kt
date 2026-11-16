package com.qtcreatormobile.projectmodel

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectFilesTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("qtcm-files-test").toFile()
        File(root, "src/native").mkdirs()
        File(root, "src/native/main.cpp").writeText("int main() { return 0; }\n// TODO\n")
        File(root, "README.md").writeText("Leia-me")
        File(root, "build").mkdirs()
        File(root, "build/out.txt").writeText("TODO artefato")
        File(root, "logo.bin").writeBytes(byteArrayOf(1, 2, 0, 3))
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `entry names reject separators and dots`() {
        assertTrue(ProjectFiles.isValidEntryName("main.cpp"))
        assertFalse(ProjectFiles.isValidEntryName(""))
        assertFalse(ProjectFiles.isValidEntryName(".."))
        assertFalse(ProjectFiles.isValidEntryName("a/b"))
        assertFalse(ProjectFiles.isValidEntryName("a\\b"))
    }

    @Test
    fun `binary files are not editable`() {
        assertTrue(ProjectFiles.isEditableText(File(root, "README.md")))
        assertFalse(ProjectFiles.isEditableText(File(root, "logo.bin")))
        assertFalse(ProjectFiles.isEditableText(File(root, "src")))
    }

    @Test
    fun `tree lists folders first, hides build and only descends into expanded folders`() {
        val collapsed = ProjectFiles.visibleTree(root, emptySet()).map { it.file.name }
        assertEquals(listOf("src", "logo.bin", "README.md"), collapsed)

        val expanded = ProjectFiles.visibleTree(root, setOf(File(root, "src").absolutePath, File(root, "src/native").absolutePath))
        assertEquals(listOf("src", "native", "main.cpp", "logo.bin", "README.md"), expanded.map { it.file.name })
        assertEquals(listOf(0, 1, 2, 0, 0), expanded.map { it.depth })

        assertTrue(ProjectFiles.visibleTree(root, emptySet(), showBuild = true).any { it.file.name == "build" })
    }

    @Test
    fun `search finds text with 1-based lines and skips build and binary files`() {
        val hits = ProjectFiles.search(root, "todo")
        assertEquals(1, hits.size)
        assertEquals("main.cpp", hits[0].file.name)
        assertEquals(2, hits[0].line)
        assertTrue(ProjectFiles.search(root, "").isEmpty())
    }
}
