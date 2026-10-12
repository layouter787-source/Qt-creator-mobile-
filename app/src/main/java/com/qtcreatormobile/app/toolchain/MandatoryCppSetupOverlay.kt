package com.qtcreatormobile.app.toolchain

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun stageCaption(state: ToolchainUiState): String = when (state) {
    is ToolchainUiState.Downloading -> "Baixando... ${(state.fraction * 100).toInt()}%"
    is ToolchainUiState.Extracting -> "Extraindo arquivos..."
    is ToolchainUiState.Integrating -> "Integrando ao sistema..."
    is ToolchainUiState.Installed -> "Concluido!"
    else -> ""
}

/**
 * C/C++ e a linguagem de identidade do QT Creator Mobile, entao o setup e
 * baixado automaticamente e em silencio na primeira abertura - sem perguntar
 * nada ao usuario. Mostra a barra de progresso, uma legenda pequena com a fase
 * atual, e uma nota explicando por que isso esta acontecendo.
 */
@Composable
fun MandatoryCppSetupOverlay(
    state: ToolchainUiState,
    onRetryClick: () -> Unit,
) {
    when (state) {
        is ToolchainUiState.Downloading,
        is ToolchainUiState.Extracting,
        is ToolchainUiState.Integrating -> {
            val fraction = (state as? ToolchainUiState.Downloading)?.fraction
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Preparando o QT Creator Mobile") },
                text = {
                    Column {
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        } else {
                            // Extraindo/integrando: duracao imprevisivel, progresso indeterminado.
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }

                        // Letras pequenas: em qual parte do processo estamos agora.
                        Text(
                            text = stageCaption(state),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Nota explicando o que e por que.
                        Text(
                            text = "OBS: este download acontece apenas uma vez e permite compilar " +
                                "codigo C/C++ diretamente neste aparelho, sem precisar de computador.",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                },
                confirmButton = {},
            )
        }
        is ToolchainUiState.Failed -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Nao foi possivel preparar o ambiente") },
                text = { Text("${state.message}\n\nSem isso o QT Creator Mobile nao consegue compilar C/C++.") },
                confirmButton = {
                    TextButton(onClick = onRetryClick) { Text("Tentar novamente") }
                },
            )
        }
        is ToolchainUiState.NotInstalled, is ToolchainUiState.Installed -> {
            // NotInstalled e transitorio: o MainActivity dispara o setup automaticamente.
            // Installed -> MainActivity troca para a tela inicial, este overlay some sozinho.
        }
    }
}
