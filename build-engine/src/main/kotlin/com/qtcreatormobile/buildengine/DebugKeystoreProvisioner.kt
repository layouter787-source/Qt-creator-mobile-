package com.qtcreatormobile.buildengine

import java.io.File

/**
 * Alias/senha padrao do keystore de DEBUG (identicos aos do Android Studio).
 * So serve para instalar e testar no proprio aparelho - builds de RELEASE
 * para a Play Store exigem um keystore proprio do desenvolvedor, com senha
 * escolhida por ele e guardado com seguranca. Um app assinado so com o
 * keystore de debug nunca deve ser publicado em producao.
 */
object DebugKeystoreDefaults {
    const val STORE_PASSWORD = "android"
    const val KEY_ALIAS = "androiddebugkey"
    const val KEY_PASSWORD = "android"
    const val DISTINGUISHED_NAME = "CN=Android Debug,O=Android,C=US"
}

/** Monta o comando `keytool -genkeypair` que gera um keystore de debug de verdade (par de chaves RSA real). */
object DebugKeystoreCommandBuilder {

    fun build(keystorePath: String, keytoolPath: String): List<String> = listOf(
        keytoolPath,
        "-genkeypair",
        "-keystore", keystorePath,
        "-storepass", DebugKeystoreDefaults.STORE_PASSWORD,
        "-alias", DebugKeystoreDefaults.KEY_ALIAS,
        "-keypass", DebugKeystoreDefaults.KEY_PASSWORD,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", DebugKeystoreDefaults.DISTINGUISHED_NAME,
    )
}

/** Garante que exista um keystore de debug utilizavel, gerando um novo via keytool se necessario. */
class DebugKeystoreProvisioner(
    private val keystorePath: String,
    private val keytoolConfig: KeytoolConfig,
    private val processRunner: ProcessRunner,
    private val workingDir: File,
) {
    fun ensureAvailable() {
        val keystoreFile = File(keystorePath)
        if (keystoreFile.exists()) return

        keystoreFile.parentFile?.mkdirs()
        val command = DebugKeystoreCommandBuilder.build(keystorePath, keytoolConfig.keytoolPath)
        val result = processRunner.run(command, workingDir)
        if (!result.succeeded) {
            throw BuildEngineException("Falha ao gerar o keystore de debug: ${result.stderr}")
        }
    }
}
