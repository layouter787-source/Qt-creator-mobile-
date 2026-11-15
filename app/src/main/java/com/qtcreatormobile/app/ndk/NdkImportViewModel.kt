package com.qtcreatormobile.app.ndk

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtcreatormobile.buildengine.BuildEngineException
import com.qtcreatormobile.buildengine.NdkArchiveCandidate
import com.qtcreatormobile.buildengine.NdkArchiveFinder
import com.qtcreatormobile.buildengine.NdkImportResult
import com.qtcreatormobile.buildengine.NdkSysrootImporter
import com.qtcreatormobile.buildengine.ToolchainPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

data class NdkImportUiState(
    /** O sysroot do NDK ja esta instalado e utilizavel. */
    val ready: Boolean,
    val importing: Boolean = false,
    val fraction: Float = 0f,
    val sourceName: String? = null,
    val error: String? = null,
    /** Mensagem de sucesso mostrada ate o usuario tocar em Continuar. */
    val successMessage: String? = null,
    val candidates: List<NdkArchiveCandidate> = emptyList(),
    val searched: Boolean = false,
    val hasStoragePermission: Boolean = false,
)

/**
 * O NDK (~700 MB) NAO e baixado pelo app: o usuario baixa o zip no site oficial e aqui ele e
 * localizado (Downloads) ou escolhido no seletor de arquivos, e so o `sysroot` e extraido para
 * `toolchain/cpp/sysroot`. A logica de extracao fica no `:build-engine` ([NdkSysrootImporter]).
 */
class NdkImportViewModel(application: Application) : AndroidViewModel(application) {
    private val paths = ToolchainPaths(File(application.filesDir, "toolchain"))
    private val importer = NdkSysrootImporter()

    private val _state = MutableStateFlow(NdkImportUiState(ready = isSysrootReady(), hasStoragePermission = hasStoragePermission()))
    val state: StateFlow<NdkImportUiState> = _state.asStateFlow()

    @Volatile private var job: Job? = null

    // Os mesmos arquivos que ProjectBuildViewModel exige antes de compilar C/C++.
    private fun isSysrootReady(): Boolean =
        File(paths.sysroot, "usr/include/stdio.h").isFile &&
            File(paths.sysroot, "usr/include/android/native_activity.h").isFile

    private fun hasStoragePermission(): Boolean =
        getApplication<Application>().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    fun onStoragePermissionResult(granted: Boolean) {
        _state.update { it.copy(hasStoragePermission = granted) }
        if (granted) searchDownloads()
    }

    /** Procura `android-ndk-*.zip` nas pastas de Downloads. Sem permissao de leitura pode nao achar nada. */
    fun searchDownloads() {
        if (_state.value.importing) return
        _state.update { it.copy(hasStoragePermission = hasStoragePermission()) }
        viewModelScope.launch(Dispatchers.IO) {
            val directories = listOfNotNull(
                @Suppress("DEPRECATION") Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File("/storage/emulated/0/Download"),
                File("/sdcard/Download"),
                getApplication<Application>().getExternalFilesDir(null),
            )
            val found = runCatching { NdkArchiveFinder.find(directories) }.getOrDefault(emptyList())
            _state.update { it.copy(candidates = found, searched = true) }
        }
    }

    fun importCandidate(candidate: NdkArchiveCandidate) {
        startImport(candidate.file.name) { stop, progress ->
            importer.importFromFile(candidate.file, paths.sysroot, stop, progress)
        }
    }

    /** Importa o arquivo escolhido no seletor de arquivos (um `content://`). */
    fun importUri(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val (name, size) = queryNameAndSize(uri)
        startImport(name) { stop, progress ->
            // Caminho rapido: acesso aleatorio ao zip via descritor de arquivo (so le o indice e
            // descompacta o sysroot). Provedores que nao permitem busca (ex.: nuvem) caem no fluxo.
            val viaDescriptor: NdkImportResult? = try {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    importer.importFromFile(File("/proc/self/fd/${pfd.fd}"), paths.sysroot, stop, progress)
                }
            } catch (_: IOException) {
                null
            }
            viaDescriptor
                ?: resolver.openInputStream(uri)?.use { input ->
                    importer.importFromStream(input, size, paths.sysroot, stop, progress)
                }
                ?: throw IOException("Nao foi possivel abrir o arquivo escolhido.")
        }
    }

    fun cancelImport() {
        job?.cancel()
    }

    fun dismissSuccess() {
        _state.update { it.copy(successMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun startImport(
        label: String,
        work: (shouldStop: () -> Boolean, progress: (Float) -> Unit) -> NdkImportResult,
    ) {
        if (job?.isActive == true) return
        _state.update { it.copy(importing = true, fraction = 0f, sourceName = label, error = null, successMessage = null) }
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = work({ !isActive }) { fraction -> _state.update { it.copy(fraction = fraction) } }
                val version = result.ndkRevision?.let { " (versao $it)" }.orEmpty()
                _state.update {
                    it.copy(
                        importing = false,
                        ready = isSysrootReady(),
                        fraction = 1f,
                        error = null,
                        successMessage = "NDK importado$version: ${result.filesExtracted} arquivos. " +
                            "Agora voce ja pode apagar o arquivo do NDK da pasta Downloads para liberar cerca de 700 MB.",
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(importing = false, error = null) }
                throw e
            } catch (e: BuildEngineException) {
                fail(e.message ?: "Arquivo invalido.")
            } catch (e: IOException) {
                fail("Nao foi possivel ler ou gravar o arquivo (${e.message ?: e.javaClass.simpleName}). Verifique o espaco livre e tente novamente.")
            } catch (e: Exception) {
                fail("Falha ao importar o NDK: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(importing = false, error = message) }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        runCatching {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
        }
        return (name ?: uri.lastPathSegment ?: "arquivo do NDK") to size
    }
}
