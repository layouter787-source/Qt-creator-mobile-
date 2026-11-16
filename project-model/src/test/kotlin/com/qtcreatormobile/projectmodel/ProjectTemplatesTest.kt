package com.qtcreatormobile.projectmodel

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTemplatesTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("qtcm-templates-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `every template creates a project that the loader accepts and whose sources exist`() {
        for (template in ProjectTemplates.all) {
            val dir = ProjectTemplates.create(template.id, "Proj-${template.id}", "com.exemplo.app", root)
            val model = ProjectModelLoader.loadFromFile(File(dir, "qtcm.project.yaml"))
            assertEquals("Proj-${template.id}", model.name, template.id)
            val sources = model.targets.single().sources
            val declared = sources.cpp + sources.java + sources.kotlin
            assertTrue(declared.isNotEmpty(), template.id)
            // Padroes glob precisam encontrar pelo menos um arquivo real.
            val onDisk = ProjectFiles.allTextFiles(dir).map { ProjectFiles.relativePath(dir, it) }
            assertTrue(onDisk.any { it.startsWith("src/") }, "${template.id}: $onDisk")
        }
    }

    @Test
    fun `jni function name matches the package so the native method is found`() {
        val dir = ProjectTemplates.create(ProjectTemplates.KOTLIN_CPP, "Jni", "com.meu_app.demo", root)
        val cpp = File(dir, "src/native/native-lib.cpp").readText()
        assertTrue("Java_com_meu_1app_demo_MainActivity_nativeGreeting" in cpp, cpp)
        assertTrue(File(dir, "src/kotlin/com/meu_app/demo/MainActivity.kt").exists())
    }

    @Test
    fun `mixed template wires kotlin to the java helper`() {
        val dir = ProjectTemplates.create(ProjectTemplates.KOTLIN_JAVA_CPP, "Mix", "com.exemplo.mix", root)
        assertTrue("Greeter.decorate(nativeGreeting())" in File(dir, "src/kotlin/com/exemplo/mix/MainActivity.kt").readText())
        assertTrue(File(dir, "src/java/com/exemplo/mix/Greeter.java").exists())
    }

    @Test
    fun `rejects invalid names, packages and duplicates and leaves nothing behind`() {
        assertFailsWith<IllegalArgumentException> { ProjectTemplates.create(ProjectTemplates.JAVA, "1ruim", "com.a.b", root) }
        assertFailsWith<IllegalArgumentException> { ProjectTemplates.create(ProjectTemplates.JAVA, "Ok", "com.new.app", root) }
        assertFailsWith<IllegalArgumentException> { ProjectTemplates.create(ProjectTemplates.JAVA, "Ok", "semponto", root) }
        assertFailsWith<IllegalArgumentException> { ProjectTemplates.create("nao-existe", "Ok", "com.a.b", root) }
        assertFalse(File(root, "Ok").exists())

        ProjectTemplates.create(ProjectTemplates.JAVA, "Ok", "com.a.b", root)
        assertFailsWith<IllegalArgumentException> { ProjectTemplates.create(ProjectTemplates.JAVA, "Ok", "com.a.b", root) }
    }

    @Test
    fun `suggested packages are valid`() {
        for (name in listOf("Meu-App", "HelloNative", "A", "x_y")) {
            assertTrue(ProjectNames.isValidPackage(ProjectNames.suggestPackage(name)), name)
        }
        assertEquals("com.qtcm.meuapp", ProjectNames.suggestPackage("Meu-App"))
    }
}
