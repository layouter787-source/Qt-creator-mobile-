package com.qtcreatormobile.app.ide

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtcreatormobile.app.CrashLog

/** Menu principal de configuracoes (estilo do app de referencia). */
@Composable
fun SettingsHub(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Configurações", onBack) {
        SectionTitle("Aparência e editor")
        SettingsGroup {
            SettingsRow(Icons.Default.Favorite, "Aparência", "Cor de destaque e fundo") { onNavigate("appearance") }
            SettingsRow(Icons.Default.Create, "Editor", "Fonte, quebra de linha, tab e realce") { onNavigate("editor") }
        }
        SectionTitle("Build e execução")
        SettingsGroup {
            SettingsRow(Icons.Default.Lock, "Gerenciador de keystore", "Criar, importar e escolher a chave de assinatura") { onNavigate("keystore") }
        }
        SectionTitle("Extensões")
        SettingsGroup {
            SettingsRow(Icons.Default.Star, "IA", "OpenAI, Anthropic, OpenRouter, NVIDIA e gateways") { onNavigate("ai") }
        }
        SectionTitle("Sistema")
        SettingsGroup {
            SettingsRow(Icons.Default.List, "Logs", "Falhas, eventos e log do build — compartilhe se algo der errado") { onNavigate("logs") }
            SettingsRow(Icons.Default.Info, "Sobre", "Versão, componentes e licenças") { onNavigate("about") }
        }
    }
}

@Composable
fun AppearanceScreen(settings: AppearanceSettings, onChange: ((AppearanceSettings) -> AppearanceSettings) -> Unit, onBack: () -> Unit) {
    var hue by remember { mutableFloatStateOf(0f) }
    var hex by remember { mutableStateOf("") }
    ScreenScaffold("Aparência", onBack) {
        SectionTitle("Cor de destaque")
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentPresets.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { preset ->
                            val selected = preset.argb == settings.accent
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onChange { it.copy(accent = preset.argb) } }) {
                                Box(
                                    modifier = Modifier.size(52.dp).background(Color(preset.argb), CircleShape)
                                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { if (selected) Text("✓", color = Color.Black, fontSize = 22.sp) }
                                Text(preset.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                Text("Cor personalizada", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = hue,
                    onValueChange = { hue = it; onChange { s -> s.copy(accent = Color.hsv(it, 0.62f, 0.96f).toArgb()) } },
                    valueRange = 0f..360f,
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        val clean = text.trim().removePrefix("#")
                        if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                            onChange { s -> s.copy(accent = (0xFF000000L or clean.toLong(16)).toInt()) }
                        }
                    },
                    label = { Text("Código hexadecimal") },
                    placeholder = { Text("#RRGGBB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { onChange { it.copy(accent = DefaultAccentArgb) } }) { Text("Restaurar cor padrão") }
            }
        }
        SectionTitle("Fundo")
        SettingsGroup {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preto puro (AMOLED)", style = MaterialTheme.typography.titleMedium)
                    Text("Economiza bateria em telas OLED", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.amoled, onCheckedChange = { v -> onChange { it.copy(amoled = v) } })
            }
        }
        SectionTitle("Prévia")
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {}) { Text("Botão principal") }
                OutlinedButton(onClick = {}) { Text("Botão secundário") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("Selecionado") })
                    FilterChip(selected = false, onClick = {}, label = { Text("Normal") })
                }
            }
        }
    }
}

@Composable
fun EditorSettingsScreen(settings: EditorSettings, onChange: ((EditorSettings) -> EditorSettings) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Editor", onBack) {
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                ToggleRow("Quebra de linha automática", null, settings.wordWrap) { v -> onChange { it.copy(wordWrap = v) } }
                ToggleRow("Números de linha", null, settings.lineNumbers) { v -> onChange { it.copy(lineNumbers = v) } }
                ToggleRow("Salvar automaticamente", null, settings.autoSave) { v -> onChange { it.copy(autoSave = v) } }
                ToggleRow("Realce de sintaxe", "Desligue se o editor travar ou fechar sozinho (modo seguro).", settings.syntaxHighlight) { v -> onChange { it.copy(syntaxHighlight = v) } }
                OutlinedButton(onClick = { onChange { EditorSettings() } }) { Text("Restaurar padrões") }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, hint: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun LogsScreen(buildLog: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val crash = remember { CrashLog.previous(context) ?: "Nenhuma falha registrada." }
    val events = remember { CrashLog.events(context) }
    val exits = remember { CrashLog.exitReasons(context) }
    val titles = listOf("Falhas", "Eventos", "Saídas", "Build")
    val text = when (tab) {
        0 -> crash
        1 -> events
        2 -> exits
        else -> buildLog?.ifBlank { null } ?: "Nenhum build nesta sessão."
    }
    ScreenScaffold("Logs", onBack) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            titles.forEachIndexed { index, title -> FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyToClipboard(context, "log", text) }) { Text("Copiar") }
            Button(onClick = { shareText(context, "Compartilhar log", text) }) { Text("Compartilhar") }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    text,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    ScreenScaffold("Sobre", onBack) {
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("QT Creator Mobile", style = MaterialTheme.typography.titleLarge)
                Text("Versão $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("IDE para criar apps Android em C++, Java e Kotlin direto no aparelho.")
            }
        }
        SectionTitle("Componentes de terceiros")
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("• Sora Editor (editor de código)")
                Text("• TextMate grammars e tm4e (realce de sintaxe)")
                Text("• Pacotes do Termux (clang, aapt2) — baixados no primeiro uso")
                Text("• OpenJDK 17 para Android (itsaky) — baixado no primeiro uso")
                Text("• Ferramentas de build do Android (d8, apksigner, android.jar)")
                Text("As licenças seguem as de cada projeto, no repositório de origem.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionTitle("Privacidade")
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Seus projetos ficam só no aparelho. O chat de IA envia o texto da conversa (e o arquivo aberto, se você ligar \"Arquivo atual\") ao provedor que você configurou. As chaves de API ficam cifradas no aparelho.")
            }
        }
    }
}
