package com.qtcreatormobile.buildengine

import java.io.File

/**
 * Sabe como colocar o binario do toolchain no disco. A implementacao real
 * (baixar de uma URL, extrair, etc.) fica nos instaladores concretos.
 */
interface ToolchainInstaller {
    /** Deve deixar, ao final, o toolchain completo dentro de `installRootDir`. */
    fun install(installRootDir: File)
}

/**
 * Garante que um toolchain esteja disponivel antes de qualquer compilacao.
 *
 * IMPORTANTE: [installRootDir] e passado explicitamente, e NAO derivado de
 * `expectedBinaryPath.parentFile` - um toolchain quase sempre extrai uma
 * arvore inteira (ex.: `usr/bin/`, `usr/lib/` do Termux), e o binario que
 * verificamos pode estar varios niveis abaixo da raiz da instalacao.
 *
 * Se o binario ja existir, nao faz nada. Caso contrario, delega a instalacao
 * e valida o resultado. Emite INTEGRATING e DONE.
 */
class CachingToolchainProvisioner(
    private val expectedBinaryPath: String,
    private val installRootDir: File,
    private val installer: ToolchainInstaller,
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) {

    fun ensureAvailable() {
        val binaryFile = File(expectedBinaryPath)

        if (!binaryFile.exists()) {
            installRootDir.mkdirs()
            installer.install(installRootDir)
        }

        stageListener.onStageChanged(ToolchainStage.INTEGRATING)

        if (!binaryFile.exists()) {
            throw BuildEngineException(
                "Instalacao do toolchain terminou mas o binario nao foi encontrado em: $expectedBinaryPath"
            )
        }

        stageListener.onStageChanged(ToolchainStage.DONE)
    }
}
