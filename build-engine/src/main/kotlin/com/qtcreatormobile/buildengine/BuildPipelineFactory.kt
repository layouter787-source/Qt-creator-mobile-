package com.qtcreatormobile.buildengine

import java.io.File

/** Executor de uma linguagem cujo toolchain nao esta instalado: falha com uma mensagem clara. */
class MissingToolchainExecutor(private val message: String) : TaskExecutor {
    override fun execute(task: CompileTask) = TaskResult(task.id, false, message)
}

/**
 * Monta um [BuildPipeline] REAL (executores de verdade, sem fakes) a partir das
 * ferramentas instaladas em [ToolchainPaths]. E aqui que todas as pecas
 * construidas separadamente (clang, javac, kotlinc, aapt2, d8, apksigner,
 * keystore de debug) se encontram.
 */
object BuildPipelineFactory {

    fun createTaskExecutor(paths: ToolchainPaths, processRunner: ProcessRunner, workingDir: File): TaskExecutor {
        val cpp = CppTaskExecutor(ndkConfig(paths), processRunner, workingDir)
        val java = JavaTaskExecutor(JavaToolchainConfig(paths.javac.absolutePath), processRunner, workingDir)
        val kotlin: TaskExecutor = if (paths.kotlinc.exists()) {
            KotlinTaskExecutor(
                KotlinToolchainConfig(
                    kotlincPath = paths.kotlinc.absolutePath,
                    javaHome = paths.javaDir.absolutePath,
                    classpathJars = paths.kotlinClasspathJars(),
                    composeCompilerPluginJar = paths.composeCompilerPluginJar(),
                ),
                processRunner,
                workingDir,
            )
        } else {
            MissingToolchainExecutor(
                "O suporte a Kotlin ainda nao foi instalado neste aparelho (download opcional em Configuracoes)."
            )
        }
        return CompositeTaskExecutor(cpp, java, kotlin)
    }

    /**
     * Cria o pipeline completo. Gera o keystore de debug (via `keytool` do JDK) se ainda
     * nao existir - sem ele o apksigner nao tem com o que assinar.
     */
    fun create(paths: ToolchainPaths, processRunner: ProcessRunner, workingDir: File): BuildPipeline {
        DebugKeystoreProvisioner(
            keystorePath = paths.debugKeystore.absolutePath,
            keytoolConfig = KeytoolConfig(paths.keytool.absolutePath),
            processRunner = processRunner,
            workingDir = workingDir,
        ).ensureAvailable()

        return BuildPipeline(
            taskExecutor = createTaskExecutor(paths, processRunner, workingDir),
            linkExecutor = CppLinkExecutor(ndkConfig(paths), processRunner, workingDir),
            dexer = D8Executor(D8Config(paths.java.absolutePath, paths.d8Jar.absolutePath), processRunner, workingDir),
            aapt2Linker = Aapt2LinkExecutor(
                Aapt2Config(paths.aapt2.absolutePath, paths.androidJar.absolutePath), processRunner, workingDir,
            ),
            apkSigner = ApkSignerExecutor(
                ApkSignerConfig(
                    javaPath = paths.java.absolutePath,
                    apksignerJarPath = paths.apksignerJar.absolutePath,
                    keystorePath = paths.debugKeystore.absolutePath,
                    keystorePassword = DebugKeystoreDefaults.STORE_PASSWORD,
                    keyAlias = DebugKeystoreDefaults.KEY_ALIAS,
                    keyPassword = DebugKeystoreDefaults.KEY_PASSWORD,
                ),
                processRunner,
                workingDir,
            ),
        )
    }

    private fun ndkConfig(paths: ToolchainPaths) =
        NdkConfig(clangPath = paths.clang.absolutePath, sysrootPath = paths.sysroot.absolutePath)
}
