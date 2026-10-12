package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeInstaller(private val shouldSucceed: Boolean = true) : ToolchainInstaller {
    var installCallCount = 0
    var lastInstallRootDir: File? = null

    override fun install(installRootDir: File) {
        installCallCount++
        lastInstallRootDir = installRootDir
        if (shouldSucceed) {
            // Simula uma instalacao "em arvore": o binario fica ALGUNS NIVEIS abaixo da
            // raiz, nao direto nela - exatamente o caso que quebrava antes da correcao.
            File(installRootDir, "usr/bin").mkdirs()
            File(installRootDir, "usr/bin/clang").apply {
                createNewFile()
                setExecutable(true)
            }
        }
    }
}

class ToolchainProvisionerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-toolchain-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `installs into the explicit install root, not the binary's immediate parent`() {
        val installRoot = File(tempDir, "cpp")
        val binaryPath = File(installRoot, "usr/bin/clang").absolutePath
        val installer = FakeInstaller()
        val provisioner = CachingToolchainProvisioner(binaryPath, installRoot, installer)

        provisioner.ensureAvailable()

        assertEquals(1, installer.installCallCount)
        assertEquals(installRoot.absolutePath, installer.lastInstallRootDir?.absolutePath)
        assertTrue(File(binaryPath).exists())
    }

    @Test
    fun `does not reinstall when the toolchain is already present`() {
        val installRoot = File(tempDir, "cpp")
        val binaryPath = File(installRoot, "usr/bin/clang").absolutePath
        File(installRoot, "usr/bin").mkdirs()
        File(binaryPath).createNewFile()
        val installer = FakeInstaller()
        val provisioner = CachingToolchainProvisioner(binaryPath, installRoot, installer)

        provisioner.ensureAvailable()

        assertEquals(0, installer.installCallCount)
    }

    @Test
    fun `fails loudly when installation does not produce the expected binary`() {
        val installRoot = File(tempDir, "cpp")
        val binaryPath = File(installRoot, "usr/bin/clang").absolutePath
        val installer = FakeInstaller(shouldSucceed = false)
        val provisioner = CachingToolchainProvisioner(binaryPath, installRoot, installer)

        assertFailsWith<BuildEngineException> { provisioner.ensureAvailable() }
    }
}
