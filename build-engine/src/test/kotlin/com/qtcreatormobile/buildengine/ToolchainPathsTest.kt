package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolchainPathsTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-paths-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `paths follow the layout used by the installers`() {
        val paths = ToolchainPaths(File(tempDir, "toolchain"))

        assertTrue(paths.clang.path.endsWith("cpp/usr/bin/clang"))
        assertTrue(paths.sysroot.path.endsWith("cpp/sysroot"))
        assertTrue(paths.javac.path.endsWith("java/bin/javac"))
        assertTrue(paths.java.path.endsWith("java/bin/java"))
        assertTrue(paths.keytool.path.endsWith("java/bin/keytool"))
        assertTrue(paths.kotlinc.path.endsWith("kotlin/bin/kotlinc"))
        assertTrue(paths.aapt2.path.endsWith("packaging/bin/aapt2"))
        assertTrue(paths.d8Jar.path.endsWith("packaging/lib/d8.jar"))
        assertTrue(paths.apksignerJar.path.endsWith("packaging/lib/apksigner.jar"))
        assertTrue(paths.androidJar.path.endsWith("packaging/android.jar"))
    }

    @Test
    fun `the layout agrees with ToolchainLayout for every language`() {
        val paths = ToolchainPaths(File(tempDir, "toolchain"))

        assertEquals(File(paths.cppDir, ToolchainLayout.mainBinaryNameFor(Language.CPP)), paths.clang)
        assertEquals(File(paths.javaDir, ToolchainLayout.mainBinaryNameFor(Language.JAVA)), paths.javac)
        assertEquals(File(paths.kotlinDir, ToolchainLayout.mainBinaryNameFor(Language.KOTLIN)), paths.kotlinc)
    }

    @Test
    fun `kotlin classpath lists only jars and leaves the compose plugin out`() {
        val paths = ToolchainPaths(File(tempDir, "toolchain"))
        paths.composeLibsDir.mkdirs()
        File(paths.composeLibsDir, "runtime-1.12.0.jar").writeText("x")
        File(paths.composeLibsDir, "material3-1.4.0.jar").writeText("x")
        File(paths.composeLibsDir, "kotlin-compose-compiler-plugin-embeddable-2.4.0.jar").writeText("x")
        File(paths.composeLibsDir, "notes.txt").writeText("x")

        val classpath = paths.kotlinClasspathJars()

        assertEquals(2, classpath.size)
        assertTrue(classpath.all { it.endsWith(".jar") && !it.contains("compiler-plugin") })
        assertTrue(paths.composeCompilerPluginJar()!!.contains("kotlin-compose-compiler-plugin"))
    }

    @Test
    fun `kotlin helpers are empty when nothing is installed`() {
        val paths = ToolchainPaths(File(tempDir, "toolchain"))

        assertTrue(paths.kotlinClasspathJars().isEmpty())
        assertNull(paths.composeCompilerPluginJar())
        assertFalse(paths.kotlinc.exists())
    }
}
