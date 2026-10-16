package com.qtcreatormobile.app.toolchain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtcreatormobile.buildengine.AndroidRepositoryResolver
import com.qtcreatormobile.buildengine.CachingToolchainProvisioner
import com.qtcreatormobile.buildengine.DownloadProgressListener
import com.qtcreatormobile.buildengine.FilteredZipExtractor
import com.qtcreatormobile.buildengine.HttpDownloader
import com.qtcreatormobile.buildengine.JdkInstaller
import com.qtcreatormobile.buildengine.KotlinInstaller
import com.qtcreatormobile.buildengine.PackagingLayout
import com.qtcreatormobile.buildengine.PackagingToolsInstaller
import com.qtcreatormobile.buildengine.RemoteToolchainInstaller
import com.qtcreatormobile.buildengine.TermuxToolchainInstaller
import com.qtcreatormobile.buildengine.ToolchainInstaller
import com.qtcreatormobile.buildengine.ToolchainLayout
import com.qtcreatormobile.buildengine.ToolchainStage
import com.qtcreatormobile.buildengine.ToolchainStageListener
import com.qtcreatormobile.projectmodel.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Coordena o download+instalacao de TUDO que qualquer build precisa: o
 * toolchain de cada linguagem (C/C++, Java, Kotlin - este ultimo com suporte
 * COMPLETO a Jetpack Compose + Material3) e as ferramentas de packaging
 * (aapt2, d8.jar, apksigner.jar, android.jar) e o JDK.
 *
 * Do ponto de vista do usuario, cada setup - por mais downloads internos que
 * tenha - e UM UNICO passo silencioso: so a barra de progresso avancando ate
 * `Installed`, nunca uma lista de downloads ou pergunta tecnica.
 */
class ToolchainViewModel(application: Application) : AndroidViewModel(application) {

    private val toolchainRoot = File(application.filesDir, "toolchain")
    private val packagingDir = File(toolchainRoot, PackagingLayout.SUBDIR)

    private val states: Map<Language, MutableStateFlow<ToolchainUiState>> =
        listOf(Language.CPP, Language.KOTLIN).associateWith { language ->
            MutableStateFlow<ToolchainUiState>(
                if (isFullyInstalled(language)) ToolchainUiState.Installed else ToolchainUiState.NotInstalled
            )
        }

    /** Estado observavel do toolchain de [language]. C e CPP compartilham o mesmo estado. */
    fun state(language: Language): StateFlow<ToolchainUiState> =
        states.getValue(if (language == Language.C) Language.CPP else language).asStateFlow()

    fun isInstalled(language: Language): Boolean = isFullyInstalled(language)

    private fun isFullyInstalled(language: Language): Boolean {
        if (!mainBinaryFile(language).exists()) return false
        return when (language) {
            // Java faz parte do setup obrigatorio (JDK), junto com C/C++ e packaging.
            Language.CPP, Language.C -> sysrootMarkerFile().exists() && isPackagingInstalled() && isJdkInstalled()
            Language.KOTLIN -> File(installRootDir(Language.KOTLIN), "compose-libs/material3-1.4.0.jar").exists()
            Language.JAVA -> true
        }
    }

    private fun isPackagingInstalled(): Boolean =
        File(packagingDir, PackagingLayout.AAPT2_BINARY).exists() &&
            File(packagingDir, PackagingLayout.D8_JAR).exists() &&
            File(packagingDir, PackagingLayout.APKSIGNER_JAR).exists() &&
            File(packagingDir, PackagingLayout.ANDROID_JAR).exists()

    private fun isJdkInstalled(): Boolean = mainBinaryFile(Language.JAVA).exists()

    private fun installRootDir(language: Language): File =
        File(toolchainRoot, ToolchainLayout.subdirFor(language))

    private fun mainBinaryFile(language: Language): File =
        File(installRootDir(language), ToolchainLayout.mainBinaryNameFor(language))

    private fun sysrootMarkerFile(): File =
        File(installRootDir(Language.CPP), "sysroot/usr/include/stdio.h")

    /** Caminho absoluto do JDK instalado - necessario como `JAVA_HOME` para rodar `kotlinc`. */
    fun jdkHome(): String = installRootDir(Language.JAVA).absolutePath

    /** Jars de Compose/Material3 + stdlib, prontos para o `-classpath` de qualquer compilacao Kotlin. */
    fun kotlinClasspathJars(): List<String> =
        File(installRootDir(Language.KOTLIN), "compose-libs")
            .listFiles { f -> f.extension == "jar" && !f.name.startsWith("kotlin-compose-compiler-plugin") }
            ?.map { it.absolutePath } ?: emptyList()

    fun kotlinComposeCompilerPluginJar(): String? =
        File(installRootDir(Language.KOTLIN), "compose-libs")
            .listFiles { f -> f.name.startsWith("kotlin-compose-compiler-plugin") }
            ?.firstOrNull()?.absolutePath

    /**
     * Dispara o setup completo e obrigatorio: clang + dependencias + sysroot
     * (C/C++, a linguagem de identidade do produto) MAIS o JDK e as ferramentas
     * de packaging (necessarios para gerar um APK de qualquer projeto).
     * Tudo como uma unica sequencia silenciosa.
     */
    fun startCppSetup() {
        val stateFlow = states.getValue(Language.CPP)
        if (stateFlow.value is ToolchainUiState.Downloading) return
        stateFlow.value = ToolchainUiState.Downloading(0f)

        val progressListener = progressListenerFor(stateFlow)
        val stageListener = intermediateStageListenerFor(stateFlow)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                runStep(
                    binaryPath = mainBinaryFile(Language.CPP).absolutePath,
                    installRoot = installRootDir(Language.CPP),
                    stateFlow = stateFlow, stageListener = stageListener,
                ) { TermuxToolchainInstaller(downloader = HttpDownloader(), progressListener = progressListener, stageListener = stageListener) }

                runStep(
                    binaryPath = sysrootMarkerFile().absolutePath,
                    installRoot = installRootDir(Language.CPP),
                    stateFlow = stateFlow, stageListener = stageListener,
                ) {
                    RemoteToolchainInstaller(
                        downloadUrl = AndroidRepositoryResolver().resolveLatestNdkLinuxUrl(),
                        downloader = HttpDownloader(),
                        extractor = FilteredZipExtractor.ndkSysrootOnly(),
                        progressListener = progressListener,
                        stageListener = stageListener,
                    )
                }

                runStep(
                    binaryPath = mainBinaryFile(Language.JAVA).absolutePath,
                    installRoot = installRootDir(Language.JAVA),
                    stateFlow = stateFlow, stageListener = stageListener,
                ) { JdkInstaller(downloader = HttpDownloader(), progressListener = progressListener, stageListener = stageListener) }

                runStep(
                    binaryPath = File(packagingDir, PackagingLayout.ANDROID_JAR).absolutePath,
                    installRoot = packagingDir,
                    stateFlow = stateFlow, stageListener = stageListener,
                ) { PackagingToolsInstaller(downloader = HttpDownloader(), progressListener = progressListener, stageListener = stageListener) }

                stateFlow.value = ToolchainUiState.Installed
            } catch (e: Exception) {
                stateFlow.value = ToolchainUiState.Failed(e.message ?: "Falha desconhecida ao preparar o ambiente.")
            }
        }
    }

    /**
     * Dispara o setup completo do Kotlin - opcional, so quando o usuario aceita
     * no popup: `kotlinc` + suporte COMPLETO a Jetpack Compose e Material3
     * (plugin compilador + todas as bibliotecas). Um unico download grande,
     * mas ainda um unico passo silencioso.
     */
    fun startKotlinSetup() {
        val stateFlow = states.getValue(Language.KOTLIN)
        if (stateFlow.value is ToolchainUiState.Downloading) return
        stateFlow.value = ToolchainUiState.Downloading(0f)

        val installRoot = installRootDir(Language.KOTLIN)
        val binaryPath = mainBinaryFile(Language.KOTLIN).absolutePath
        val progressListener = progressListenerFor(stateFlow)
        val stageListener = optionalStageListenerFor(stateFlow)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val installer = KotlinInstaller(downloader = HttpDownloader(), progressListener = progressListener, stageListener = stageListener)
                CachingToolchainProvisioner(binaryPath, installRoot, installer, stageListener).ensureAvailable()
            } catch (e: Exception) {
                stateFlow.value = ToolchainUiState.Failed(e.message ?: "Falha desconhecida ao baixar o Kotlin.")
            }
        }
    }

    /** Roda uma etapa de instalacao, reiniciando a barra de progresso (0f) para ela. */
    private fun runStep(
        binaryPath: String,
        installRoot: File,
        stateFlow: MutableStateFlow<ToolchainUiState>,
        stageListener: ToolchainStageListener,
        installerFactory: () -> ToolchainInstaller,
    ) {
        stateFlow.value = ToolchainUiState.Downloading(0f)
        CachingToolchainProvisioner(binaryPath, installRoot, installerFactory(), stageListener).ensureAvailable()
    }

    private fun progressListenerFor(stateFlow: MutableStateFlow<ToolchainUiState>) = DownloadProgressListener { progress ->
        if (stateFlow.value is ToolchainUiState.Downloading) {
            stateFlow.value = ToolchainUiState.Downloading(progress.fraction)
        }
    }

    // Os listeners sao `fun interface` do Kotlin: um valor de tipo funcao `(X) -> Unit` NAO e
    // convertido automaticamente para eles (so lambdas literais sao), entao construimos o
    // ToolchainStageListener explicitamente.
    private fun optionalStageListenerFor(stateFlow: MutableStateFlow<ToolchainUiState>) = ToolchainStageListener { stage ->
        when (stage) {
            ToolchainStage.EXTRACTING -> stateFlow.value = ToolchainUiState.Extracting
            ToolchainStage.INTEGRATING -> stateFlow.value = ToolchainUiState.Integrating
            ToolchainStage.DONE -> stateFlow.value = ToolchainUiState.Installed
            else -> Unit
        }
    }

    /** Igual ao listener opcional, mas NUNCA marca `Installed` sozinho - usado nos passos intermediarios do setup obrigatorio. */
    private fun intermediateStageListenerFor(stateFlow: MutableStateFlow<ToolchainUiState>) = ToolchainStageListener { stage ->
        when (stage) {
            ToolchainStage.EXTRACTING -> stateFlow.value = ToolchainUiState.Extracting
            ToolchainStage.INTEGRATING -> stateFlow.value = ToolchainUiState.Integrating
            else -> Unit
        }
    }
}
