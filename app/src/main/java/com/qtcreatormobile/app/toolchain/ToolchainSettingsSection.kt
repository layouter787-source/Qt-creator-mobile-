package com.qtcreatormobile.app.toolchain

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Secao de Configuracoes para quem recusou o download opcional no popup (ou
 * quer verificar/reinstalar o toolchain depois).
 */
@Composable
fun ToolchainSettingsSection(
    state: ToolchainUiState,
    onDownloadClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Ferramentas de compilacao")

        when (state) {
            is ToolchainUiState.NotInstalled -> {
                Text("Ainda nao instaladas.")
                Button(onClick = onDownloadClick, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Baixar toolchain")
                }
            }
            is ToolchainUiState.Downloading -> {
                Text("Baixando... ${(state.fraction * 100).toInt()}%")
                LinearProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            is ToolchainUiState.Extracting, is ToolchainUiState.Integrating -> {
                Text("Instalando...")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            is ToolchainUiState.Installed -> {
                Text("Instaladas e prontas para uso.")
            }
            is ToolchainUiState.Failed -> {
                Text("Falha no ultimo download: ${state.message}")
                Button(onClick = onDownloadClick, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Tentar novamente")
                }
            }
        }
    }
}
