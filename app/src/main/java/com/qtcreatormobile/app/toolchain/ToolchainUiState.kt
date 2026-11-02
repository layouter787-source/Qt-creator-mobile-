package com.qtcreatormobile.app.toolchain

sealed interface ToolchainUiState {
    data object NotInstalled : ToolchainUiState
    data class Downloading(val fraction: Float) : ToolchainUiState
    data class Paused(val fraction: Float, val reason: String) : ToolchainUiState
    data object Extracting : ToolchainUiState
    data object Integrating : ToolchainUiState
    data object Installed : ToolchainUiState
    data class Failed(val message: String) : ToolchainUiState
}
