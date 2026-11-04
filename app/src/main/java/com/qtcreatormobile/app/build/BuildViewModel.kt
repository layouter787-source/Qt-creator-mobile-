package com.qtcreatormobile.app.build

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtcreatormobile.buildengine.BuildPipelineFactory
import com.qtcreatormobile.buildengine.BuildPipelineResult
import com.qtcreatormobile.buildengine.RealProcessRunner
import com.qtcreatormobile.buildengine.ToolchainPaths
import com.qtcreatormobile.projectmodel.ProjectModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Liga a interface ao BuildPipeline REAL: carrega o `qtcm.project.yaml` do
 * projeto, monta o pipeline com as ferramentas instaladas (clang, aapt2, d8,
 * apksigner...) e roda tudo em segundo plano, expondo cada etapa na tela.
 */
class BuildViewModel(application: Application) : AndroidViewModel(application) {

    private val paths = ToolchainPaths(File(application.filesDir, "toolchain"))
    private val projectsRoot = File(application.filesDir, "projects")

    private val _state = MutableStateFlow(BuildUiState(projects = listProjects()))
    val state: StateFlow<BuildUiState> = _state.asStateFlow()

    private fun listProjects(): List<String> =
        projectsRoot.listFiles { f -> f.isDirectory && File(f, "qtcm.project.yaml").exists() }
            ?.map { it.name }?.sorted() ?: emptyList()

    fun createSampleProject() {
        SampleProject.writeTo(File(projectsRoot, SampleProject.NAME))
        _state.value = _state.value.copy(
            projects = listProjects(),
            message = "Projeto de exemplo criado: ${SampleProject.NAME}",
        )
    }

    fun build(projectName: String) {
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true, steps = emptyList(), message = null, apkPath = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = runBuild(projectName)
                _state.value = _state.value.copy(
                    running = false,
                    steps = result.steps,
                    apkPath = result.outputApkPath,
                    message = if (result.succeeded) {
                        "Build concluido com sucesso."
                    } else {
                        "O build falhou - veja a etapa marcada abaixo."
                    },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    running = false,
                    message = "Erro antes de comecar o build: ${e.message}",
                )
            }
        }
    }

    private fun runBuild(projectName: String): BuildPipelineResult {
        val projectDir = File(projectsRoot, projectName)
        val model = ProjectModelLoader.loadFromFile(File(projectDir, "qtcm.project.yaml"))
        val target = model.targets.first()

        // Os caminhos dos fontes no .yaml sao relativos a pasta do projeto: ela e o diretorio de trabalho.
        val pipeline = BuildPipelineFactory.create(paths, RealProcessRunner(), projectDir)
        val buildDir = File(projectDir, "build")
        return pipeline.build(
            target = target,
            manifestPath = File(projectDir, "AndroidManifest.xml").absolutePath,
            outputApkPath = File(buildDir, "${target.id}.apk").absolutePath,
            buildDir = buildDir.absolutePath,
        )
    }
}
