package com.qtcreatormobile.app.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IdeSettingsScreen(settings: EditorSettings, onChange: ((EditorSettings) -> EditorSettings) -> Unit, onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                Text("Configurações", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Editor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Tamanho da fonte: ${settings.fontSizeSp.toInt()} sp")
                    Slider(
                        value = settings.fontSizeSp,
                        onValueChange = { v -> onChange { it.copy(fontSizeSp = v.toInt().toFloat()) } },
                        valueRange = 10f..24f,
                    )
                }
                Column {
                    Text("Tamanho do Tab: ${settings.tabSize}")
                    Slider(
                        value = settings.tabSize.toFloat(),
                        onValueChange = { v -> onChange { it.copy(tabSize = v.toInt().coerceIn(2, 8)) } },
                        valueRange = 2f..8f,
                        steps = 5,
                    )
                }
                ToggleRow("Quebra de linha automática", settings.wordWrap) { v -> onChange { it.copy(wordWrap = v) } }
                ToggleRow("Números de linha", settings.lineNumbers) { v -> onChange { it.copy(lineNumbers = v) } }
                ToggleRow("Salvar automaticamente", settings.autoSave) { v -> onChange { it.copy(autoSave = v) } }
                OutlinedButton(onClick = { onChange { EditorSettings() } }) { Text("Restaurar padrões") }
                Button(onClick = onBack) { Text("Concluir") }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
