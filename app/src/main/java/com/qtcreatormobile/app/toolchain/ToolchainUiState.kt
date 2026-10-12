package com.qtcreatormobile.app.toolchain

/** Estado observado pela UI (overlay de setup, popup opcional e tela de Configuracoes). */
sealed interface ToolchainUiState {
    data object NotInstalled : ToolchainUiState
    data class Downloading(val fraction: Float) : ToolchainUiState
    data object Extracting : ToolchainUiState
    data object Integrating : ToolchainUiState
    data object Installed : ToolchainUiState
    data class Failed(val message: String) : ToolchainUiState
}
