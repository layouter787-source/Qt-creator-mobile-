package com.qtcreatormobile.app.toolchain

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Popup usado para toolchains OPCIONAIS (Kotlin com Compose/Material3) -
 * pergunta antes de baixar, porque nao e a linguagem de identidade do produto
 * e e um download grande. Para C/C++ (obrigatorio, silencioso), ver
 * [MandatoryCppSetupOverlay].
 */
@Composable
fun FirstLaunchToolchainDialog(
    languageLabel: String,
    state: ToolchainUiState,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit,
    onPauseClick: () -> Unit = {},
) {
    when (state) {
        is ToolchainUiState.NotInstalled -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Baixar suporte a $languageLabel?") },
                text = {
                    Text(
                        "O QT Creator Mobile pode compilar projetos $languageLabel, mas isso " +
                            "exige um download opcional adicional. Voce pode fazer isso agora ou " +
                            "depois, em Configuracoes."
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDownloadClick) { Text("Baixar agora") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Agora nao") }
                },
            )
        }
        is ToolchainUiState.Downloading, is ToolchainUiState.Paused -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Baixando suporte a $languageLabel...") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { when (state) { is ToolchainUiState.Downloading -> state.fraction; is ToolchainUiState.Paused -> state.fraction; else -> 0f } },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        val fraction = when (state) { is ToolchainUiState.Downloading -> state.fraction; is ToolchainUiState.Paused -> state.fraction; else -> 0f }
                        Text(if (state is ToolchainUiState.Paused) "${state.reason} · ${(fraction * 100).toInt()}%" else "${(fraction * 100).toInt()}%")
                    }
                },
                confirmButton = {
                    TextButton(onClick = onPauseClick) { Text(if (state is ToolchainUiState.Paused) "Continuar" else "Pausar") }
                },
            )
        }
        is ToolchainUiState.Extracting, is ToolchainUiState.Integrating -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Instalando suporte a $languageLabel...") },
                text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) },
                confirmButton = {},
            )
        }
        is ToolchainUiState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Nao foi possivel baixar") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDownloadClick) { Text("Tentar novamente") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Fechar") }
                },
            )
        }
        is ToolchainUiState.Installed -> {
            // Nada a mostrar - toolchain ja pronto.
        }
    }
}
