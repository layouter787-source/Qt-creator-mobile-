package com.qtcreatormobile.app.toolchain

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtcreatormobile.buildengine.AndroidRepositoryResolver
import com.qtcreatormobile.buildengine.CachingToolchainProvisioner
import com.qtcreatormobile.buildengine.DownloadControl
import com.qtcreatormobile.buildengine.DownloadPausedException
import com.qtcreatormobile.buildengine.DownloadProgressListener
import com.qtcreatormobile.buildengine.FilteredZipExtractor
import com.qtcreatormobile.buildengine.HttpDownloader
import com.qtcreatormobile.buildengine.JdkInstaller
import com.qtcreatormobile.buildengine.KotlinInstaller
import com.qtcreatormobile.buildengine.MavenArtifactDownloader
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ToolchainViewModel(application: Application) : AndroidViewModel(application) {
    private val toolchainRoot = File(application.filesDir, "toolchain")
    private val packagingDir = File(toolchainRoot, PackagingLayout.SUBDIR)
    private val downloadControl = DownloadControl()
    private val downloader = HttpDownloader(downloadControl)
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val pausePreferences = application.getSharedPreferences("toolchain-downloads", Application.MODE_PRIVATE)
    private val autoPauseReasons = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var manuallyPaused = pausePreferences.getBoolean("manual_pause", false)
    @Volatile private var activeLanguage: Language? = null
    @Volatile private var activeJob: kotlinx.coroutines.Job? = null

    /**
     * Reserva ATOMICA da instalacao. startCppSetup() pode ser chamado ao mesmo tempo pelo
     * MainActivity, pelo callback de rede (resumeSetup) e pelo botao "Tentar novamente"; checar
     * so activeJob nao basta porque ele so e atribuido depois do launch. Duas instalacoes
     * baixando o mesmo .part faziam uma delas falhar ao finalizar o arquivo.
     */
    private val setupRunning = AtomicBoolean(false)

    private val lastFractions = ConcurrentHashMap<Language, Float>().apply {
        put(Language.CPP, 0f)
        put(Language.KOTLIN, 0f)
    }

    private val states: Map<Language, MutableStateFlow<ToolchainUiState>> =
        listOf(Language.CPP, Language.KOTLIN).associateWith { language ->
            MutableStateFlow<ToolchainUiState>(if (isFullyInstalled(language)) ToolchainUiState.Installed else ToolchainUiState.NotInstalled)
        }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { refreshNetworkPause() }
        override fun onLost(network: Network) { refreshNetworkPause() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { refreshNetworkPause() }
    }

    init {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching { connectivity.registerNetworkCallback(request, networkCallback) }
        refreshNetworkPause()
    }

    fun state(language: Language): StateFlow<ToolchainUiState> =
        states.getValue(if (language == Language.C) Language.CPP else language).asStateFlow()

    fun isInstalled(language: Language): Boolean = isFullyInstalled(language)

    private fun isFullyInstalled(language: Language): Boolean {
        if (!mainBinaryFile(language).exists()) return false
        return when (language) {
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
    private fun installRootDir(language: Language): File = File(toolchainRoot, ToolchainLayout.subdirFor(language))
    private fun mainBinaryFile(language: Language): File = File(installRootDir(language), ToolchainLayout.mainBinaryNameFor(language))
    private fun sysrootMarkerFile(): File = File(installRootDir(Language.CPP), "sysroot/usr/include/stdio.h")
    fun jdkHome(): String = installRootDir(Language.JAVA).absolutePath

    fun kotlinClasspathJars(): List<String> =
        File(installRootDir(Language.KOTLIN), "compose-libs")
            .listFiles { f -> f.extension == "jar" && !f.name.startsWith("kotlin-compose-compiler-plugin") }
            ?.map { it.absolutePath } ?: emptyList()

    fun kotlinComposeCompilerPluginJar(): String? =
        File(installRootDir(Language.KOTLIN), "compose-libs")
            .listFiles { f -> f.name.startsWith("kotlin-compose-compiler-plugin") }
            ?.firstOrNull()?.absolutePath

    fun startCppSetup() {
        if (states.getValue(Language.CPP).value is ToolchainUiState.Downloading || activeJob?.isActive == true) return
        launchSetup(Language.CPP) { stateFlow, progress, stages ->
            val prefixAlias = "/data/data/${getApplication<Application>().packageName}/files/usr"
            runStep(mainBinaryFile(Language.CPP), installRootDir(Language.CPP), stateFlow, stages) {
                TermuxToolchainInstaller(downloader = downloader, prefixAlias = prefixAlias, progressListener = progress, stageListener = stages)
            }
            runStep(sysrootMarkerFile(), installRootDir(Language.CPP), stateFlow, stages) {
                RemoteToolchainInstaller(
                    downloadUrl = AndroidRepositoryResolver().resolveLatestNdkLinuxUrl(), downloader = downloader,
                    extractor = FilteredZipExtractor.ndkSysrootOnly(), progressListener = progress, stageListener = stages,
                )
            }
            runStep(mainBinaryFile(Language.JAVA), installRootDir(Language.JAVA), stateFlow, stages) {
                JdkInstaller(downloader = downloader, progressListener = progress, stageListener = stages)
            }
            runStep(File(packagingDir, PackagingLayout.ANDROID_JAR), packagingDir, stateFlow, stages) {
                PackagingToolsInstaller(downloader = downloader, progressListener = progress, stageListener = stages)
            }
        }
    }

    fun startKotlinSetup() {
        if (states.getValue(Language.KOTLIN).value is ToolchainUiState.Downloading || activeJob?.isActive == true) return
        launchSetup(Language.KOTLIN) { stateFlow, progress, stages ->
            val installer = KotlinInstaller(
                downloader = downloader,
                mavenDownloader = MavenArtifactDownloader(downloader),
                progressListener = progress,
                stageListener = stages,
            )
            CachingToolchainProvisioner(mainBinaryFile(Language.KOTLIN).absolutePath, installRootDir(Language.KOTLIN), installer, stages).ensureAvailable()
        }
    }

    fun toggleManualPause() {
        manuallyPaused = !manuallyPaused
        pausePreferences.edit().putBoolean("manual_pause", manuallyPaused).apply()
        syncPauseState()
        if (!isPaused() && activeJob?.isActive != true) resumeSetup()
    }

    fun pauseForBackground() {
        autoPauseReasons += "background"
        syncPauseState()
    }

    fun resumeFromBackground() {
        autoPauseReasons -= "background"
        syncPauseState()
        if (!isPaused() && activeJob?.isActive != true) resumeSetup()
    }

    private fun refreshNetworkPause() {
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (hasInternet) autoPauseReasons -= "network" else autoPauseReasons += "network"
        syncPauseState()
        if (hasInternet && !isPaused() && activeJob?.isActive != true) resumeSetup()
    }

    private fun isPaused() = manuallyPaused || autoPauseReasons.isNotEmpty()

    private fun syncPauseState() {
        if (isPaused()) {
            downloadControl.pause()
            activeLanguage?.let { language ->
                val message = pauseMessage()
                states.getValue(language).value = ToolchainUiState.Paused(lastFractions[language] ?: 0f, message)
                ToolchainDownloadService.paused(getApplication(), message)
            }
        } else {
            downloadControl.resume()
        }
    }

    private fun pauseMessage(): String = when {
        manuallyPaused -> "Pausado por você"
        "network" in autoPauseReasons -> "Aguardando conexão com a internet"
        else -> "Pausado enquanto o app está em segundo plano"
    }

    private fun launchSetup(language: Language, work: suspend (MutableStateFlow<ToolchainUiState>, DownloadProgressListener, ToolchainStageListener) -> Unit) {
        // Reserva atomica: so uma instalacao por vez, mesmo com chamadas simultaneas.
        if (!setupRunning.compareAndSet(false, true)) return
        val stateFlow = states.getValue(language)
        val progress: DownloadProgressListener
        val stages: ToolchainStageListener
        try {
            activeLanguage = language
            lastFractions[language] = (stateFlow.value as? ToolchainUiState.Paused)?.fraction ?: lastFractions[language] ?: 0f
            progress = progressListenerFor(language, stateFlow)
            stages = intermediateStageListenerFor(stateFlow)
            ToolchainDownloadService.start(getApplication())
        } catch (e: Throwable) {
            activeLanguage = null
            setupRunning.set(false)
            throw e
        }
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    if (isPaused()) {
                        stateFlow.value = ToolchainUiState.Paused(lastFractions[language] ?: 0f, pauseMessage())
                        delay(400)
                        continue
                    }
                    try {
                        stateFlow.value = ToolchainUiState.Downloading(lastFractions[language] ?: 0f)
                        work(stateFlow, progress, stages)
                        while (isPaused()) {
                            stateFlow.value = ToolchainUiState.Paused(lastFractions[language] ?: 0f, pauseMessage())
                            delay(400)
                        }
                        stateFlow.value = ToolchainUiState.Installed
                        lastFractions[language] = 1f
                        ToolchainDownloadService.finish(getApplication(), success = true)
                        break
                    } catch (_: DownloadPausedException) {
                        // O .part fica no disco; o loop retoma pela faixa HTTP na próxima tentativa.
                    } catch (e: IOException) {
                        if (isPaused()) {
                            // Desconectar a requisição ao pausar pode gerar IOException em alguns servidores.
                        } else if (!hasValidatedInternet()) {
                            autoPauseReasons += "network"
                            syncPauseState()
                        } else throw e
                    }
                }
            } catch (e: Exception) {
                val message = e.message ?: "Falha desconhecida ao preparar o ambiente."
                stateFlow.value = ToolchainUiState.Failed(message)
                ToolchainDownloadService.finish(getApplication(), success = false, message = message)
            } finally {
                activeJob = null
                activeLanguage = null
                setupRunning.set(false)
            }
        }
    }

    private fun resumeSetup() {
        when (activeLanguage) {
            Language.CPP -> startCppSetup()
            Language.KOTLIN -> startKotlinSetup()
            else -> if (states.getValue(Language.CPP).value !is ToolchainUiState.Installed) startCppSetup()
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun runStep(
        expectedFile: File,
        installRoot: File,
        stateFlow: MutableStateFlow<ToolchainUiState>,
        stages: ToolchainStageListener,
        installerFactory: () -> ToolchainInstaller,
    ) {
        stateFlow.value = ToolchainUiState.Downloading(0f)
        CachingToolchainProvisioner(expectedFile.absolutePath, installRoot, installerFactory(), stages).ensureAvailable()
    }

    private fun progressListenerFor(language: Language, stateFlow: MutableStateFlow<ToolchainUiState>) = DownloadProgressListener { progress ->
        lastFractions[language] = progress.fraction.coerceIn(0f, 1f)
        if (stateFlow.value is ToolchainUiState.Downloading) {
            stateFlow.value = ToolchainUiState.Downloading(lastFractions[language] ?: 0f)
            ToolchainDownloadService.progress(getApplication(), lastFractions[language] ?: 0f)
        }
    }

    private fun intermediateStageListenerFor(stateFlow: MutableStateFlow<ToolchainUiState>) = ToolchainStageListener { stage ->
        when (stage) {
            ToolchainStage.EXTRACTING -> stateFlow.value = ToolchainUiState.Extracting
            ToolchainStage.INTEGRATING -> stateFlow.value = ToolchainUiState.Integrating
            else -> Unit
        }
    }

    override fun onCleared() {
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        downloadControl.pause()
        super.onCleared()
    }
}
