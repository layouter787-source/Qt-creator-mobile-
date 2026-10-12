package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeProcessRunner(private val result: ProcessResult) : ProcessRunner {
    var lastCommand: List<String>? = null
    override fun run(command: List<String>, workingDir: File): ProcessResult {
        lastCommand = command
        return result
    }
}

class Aapt2LinkExecutorTest {
    @Test
    fun `runs aapt2 link and reports success`() {
        val config = Aapt2Config("/t/aapt2", "/t/android.jar")
        val runner = FakeProcessRunner(ProcessResult(0, "linked", ""))
        val executor = Aapt2LinkExecutor(config, runner, File("."))

        val result = executor.link("Manifest.xml", "build/app.apk")

        assertTrue(result.succeeded)
        assertTrue(runner.lastCommand!!.contains("Manifest.xml"))
    }
}

class D8ExecutorTest {
    @Test
    fun `runs d8 and reports failure with stderr`() {
        val config = D8Config(javaPath = "/t/java", d8JarPath = "/t/d8.jar")
        val runner = FakeProcessRunner(ProcessResult(1, "", "d8: bad class file"))
        val executor = D8Executor(config, runner, File("."))

        val result = executor.dex(listOf("Main.class"), "build/dex")

        assertEquals(false, result.succeeded)
        assertTrue(result.log.contains("bad class file"))
    }
}

class ApkSignerExecutorTest {
    @Test
    fun `runs apksigner and reports success`() {
        val config = ApkSignerConfig(
            javaPath = "/t/java",
            apksignerJarPath = "/t/apksigner.jar",
            keystorePath = "/t/debug.keystore",
            keystorePassword = "android",
            keyAlias = "androiddebugkey",
            keyPassword = "android",
        )
        val runner = FakeProcessRunner(ProcessResult(0, "signed", ""))
        val executor = ApkSignerExecutor(config, runner, File("."))

        val result = executor.sign("build/app.apk")

        assertTrue(result.succeeded)
        assertTrue(runner.lastCommand!!.contains("build/app.apk"))
    }
}

class DebugKeystoreProvisionerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-keystore-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `generates the keystore when it does not exist yet`() {
        val keystorePath = File(tempDir, "debug.keystore").absolutePath
        val runner = object : ProcessRunner {
            override fun run(command: List<String>, workingDir: File): ProcessResult {
                File(keystorePath).createNewFile() // o keytool real criaria o arquivo
                return ProcessResult(0, "keystore gerado", "")
            }
        }
        val provisioner = DebugKeystoreProvisioner(keystorePath, KeytoolConfig("/t/keytool"), runner, tempDir)

        provisioner.ensureAvailable()

        assertTrue(File(keystorePath).exists())
    }

    @Test
    fun `does not regenerate an existing keystore`() {
        val keystoreFile = File(tempDir, "debug.keystore")
        keystoreFile.createNewFile()
        var callCount = 0
        val runner = object : ProcessRunner {
            override fun run(command: List<String>, workingDir: File): ProcessResult {
                callCount++
                return ProcessResult(0, "", "")
            }
        }
        val provisioner = DebugKeystoreProvisioner(keystoreFile.absolutePath, KeytoolConfig("/t/keytool"), runner, tempDir)

        provisioner.ensureAvailable()

        assertEquals(0, callCount)
    }

    @Test
    fun `fails loudly when keytool reports an error`() {
        val keystorePath = File(tempDir, "debug.keystore").absolutePath
        val runner = object : ProcessRunner {
            override fun run(command: List<String>, workingDir: File): ProcessResult =
                ProcessResult(1, "", "keytool error: disk full")
        }
        val provisioner = DebugKeystoreProvisioner(keystorePath, KeytoolConfig("/t/keytool"), runner, tempDir)

        assertFailsWith<BuildEngineException> { provisioner.ensureAvailable() }
    }
}
