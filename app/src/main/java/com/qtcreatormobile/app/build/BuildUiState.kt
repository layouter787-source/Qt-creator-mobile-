package com.qtcreatormobile.app.build

import com.qtcreatormobile.buildengine.PipelineStepResult

/** Estado da tela inicial: projetos existentes e o resultado do ultimo build (etapa por etapa). */
data class BuildUiState(
    val projects: List<String> = emptyList(),
    val running: Boolean = false,
    val steps: List<PipelineStepResult> = emptyList(),
    val message: String? = null,
    val apkPath: String? = null,
)
