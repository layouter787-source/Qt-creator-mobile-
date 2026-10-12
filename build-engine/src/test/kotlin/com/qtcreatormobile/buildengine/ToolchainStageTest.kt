package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeInstallerForStages : ToolchainInstaller {
    override fun install(installRootDir: File) {
        File(installRootDir, "clang").createNewFile()
    }
}

class ToolchainStageTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("qtcm-stage-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `provisioner emits INTEGRATING then DONE after a successful install`() {
        val installRoot = tempDir
        val binaryPath = File(installRoot, "clang").absolutePath
        val stages = mutableListOf<ToolchainStage>()

        val provisioner = CachingToolchainProvisioner(
            expectedBinaryPath = binaryPath,
            installRootDir = installRoot,
            installer = FakeInstallerForStages(),
            stageListener = ToolchainStageListener { stages += it },
        )

        provisioner.ensureAvailable()

        assertEquals(listOf(ToolchainStage.INTEGRATING, ToolchainStage.DONE), stages)
    }
}
