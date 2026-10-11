package com.qtcreatormobile.buildengine

/** Fase atual da preparacao do toolchain, para a UI mostrar ao usuario o que esta acontecendo. */
enum class ToolchainStage {
    DOWNLOADING,
    EXTRACTING,
    INTEGRATING,
    DONE,
}

fun interface ToolchainStageListener {
    fun onStageChanged(stage: ToolchainStage)
}
