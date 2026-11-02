package com.qtcreatormobile.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qtcreatormobile.app.toolchain.ToolchainUiState
import com.qtcreatormobile.app.build.ProjectBuildState
import com.qtcreatormobile.app.home.SoraCodeEditor
import java.io.File
import io.github.rosemoe.sora.widget.CodeEditor

private val Ink = Color(0xFF17231F)
private val Muted = Color(0xFF6D7A73)
private val Green = Color(0xFF087F5B)
private val Canvas = Color(0xFFF5F7F4)

@Composable
fun HomeScreen(
    projectsRoot: File,
    kotlinState: ToolchainUiState,
    onInstallKotlin: () -> Unit,
    onPauseKotlin: () -> Unit = {},
    buildState: ProjectBuildState,
    onBuild: (File) -> Unit,
    onInstallApk: (File) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var activeProject by remember { mutableStateOf<File?>(null) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var showBuildInfo by remember { mutableStateOf(false) }
    var showUnsaved by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var code by remember(activeFile) { mutableStateOf(activeFile?.takeIf { it.isFile }?.readText().orEmpty()) }
    var savedCode by remember(activeFile) { mutableStateOf(activeFile?.takeIf { it.isFile }?.readText().orEmpty()) }
    var codeEditor by remember(activeFile) { mutableStateOf<CodeEditor?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    fun openFile(file: File) {
        if (activeFile != null && code != savedCode) {
            pendingFile = file
            showUnsaved = true
        } else {
            activeFile = file
            code = file.readText()
            savedCode = code
        }
    }
    fun leaveEditor() {
        if (code != savedCode) showUnsaved = true else activeFile = null
    }
    val projects = remember(refresh) { projectsRoot.listFiles()?.filter { File(it, "qtcm.project.yaml").exists() }?.sortedBy { it.name }.orEmpty() }

    Scaffold(containerColor = Canvas, bottomBar = {
        if (activeProject == null) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.White) {
                listOf("Projetos", "Toolchains").forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
        }
    }) { padding ->
        if (activeFile != null) {
            Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFF111915))) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF1D2923)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹  Arquivos", color = Color.White, modifier = Modifier.clickable { leaveEditor() }.padding(end = 16.dp))
                    Text(activeFile!!.name + if (code != savedCode) " •" else "", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { searchOpen = !searchOpen }) { Text("⌕", color = Color(0xFFB7C9BE)) }
                    TextButton(onClick = { val text = codeEditor?.text?.toString() ?: code; activeFile!!.writeText(text); code = text; savedCode = text; refresh++ }) { Text("Salvar", color = Color(0xFF6FE0B5)) }
                }
                if (searchOpen) {
                    Column(Modifier.fillMaxWidth().background(Color(0xFF1D2923)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        OutlinedTextField(value = searchText, onValueChange = { searchText = it }, label = { Text("Localizar", color = Color(0xFFB7C9BE)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${if (searchText.isBlank()) 0 else Regex(Regex.escape(searchText), RegexOption.IGNORE_CASE).findAll(code).count()} ocorrências", color = Color(0xFFB7C9BE), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = replaceText, onValueChange = { replaceText = it }, label = { Text("Substituir", color = Color(0xFFB7C9BE)) }, singleLine = true, modifier = Modifier.weight(1f))
                            TextButton(enabled = searchText.isNotEmpty(), onClick = { val current = codeEditor?.text?.toString() ?: code; val replaced = Regex(Regex.escape(searchText), RegexOption.IGNORE_CASE).replace(current, replaceText); codeEditor?.setText(replaced); code = replaced }) { Text("Tudo", color = Color(0xFF6FE0B5)) }
                        }
                    }
                }
                Text("${activeFile!!.relativeTo(activeProject!!).path}  •  UTF-8", color = Color(0xFF82968A), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                key(activeFile!!.absolutePath) {
                    SoraCodeEditor(
                        fileName = activeFile!!.name,
                        initialText = code,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onTextChanged = { code = it },
                        onEditorReady = { codeEditor = it },
                    )
                }
                Row(Modifier.fillMaxWidth().background(Color(0xFF1D2923)).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${code.count { it == '\n' } + 1} linhas", color = Color(0xFF82968A), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text("${code.length} caracteres  •  ${activeFile!!.extension.uppercase()}", color = Color(0xFF82968A), style = MaterialTheme.typography.labelSmall)
                }
            }
        } else if (activeProject != null) {
            ProjectFiles(project = activeProject!!, onBack = { activeProject = null }, onFile = { f -> openFile(f) }, onBuild = { showBuildInfo = true; onBuild(activeProject!!) }, modifier = Modifier.padding(padding))
        } else if (selectedTab == 0) {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("QT Creator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Desenvolvimento no seu Android", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("{ }", color = Green, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F4EC)), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("AMBIENTE PRONTO", color = Green, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Seu estúdio de código, sempre no bolso.", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Crie projetos básicos C/C++, Java e Kotlin e edite arquivos diretamente no dispositivo.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Seus projetos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showCreate = true }) { Text("＋ Novo", color = Green, fontWeight = FontWeight.Bold) }
                }
                if (projects.isEmpty()) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⌘", color = Green, style = MaterialTheme.typography.headlineMedium)
                            Text("Comece seu primeiro projeto", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("Escolha um modelo para criar a estrutura inicial.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
                            Button(onClick = { showCreate = true }) { Text("Criar projeto") }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(projects, key = { it.absolutePath }) { project ->
                            Card(Modifier.fillMaxWidth().clickable { activeProject = project }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("◈", color = Green, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 14.dp))
                                    Column(Modifier.weight(1f)) { Text(project.name, color = Ink, fontWeight = FontWeight.SemiBold); Text("Projeto local • ${project.listFiles()?.size ?: 0} itens", color = Muted, style = MaterialTheme.typography.bodySmall) }
                                    Text("›", color = Muted)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Toolchains", style = MaterialTheme.typography.headlineSmall, color = Ink, fontWeight = FontWeight.Bold)
                Text("Compiladores disponíveis neste dispositivo", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
                ToolchainCard("C / C++", "Clang + Android NDK", "Instalado", true, {})
                ToolchainCard("Java", "JDK 17 + ferramentas Android", "Instalado", true, {})
                ToolchainCard(
                    "Kotlin + Compose", "Kotlinc e bibliotecas Compose; recursos Android são limitados",
                    when (kotlinState) {
                        ToolchainUiState.Installed -> "Instalado"
                        is ToolchainUiState.Downloading -> "Baixando ${(kotlinState.fraction * 100).toInt()}%"
                        is ToolchainUiState.Paused -> "${kotlinState.reason} ${(kotlinState.fraction * 100).toInt()}%"
                        is ToolchainUiState.Extracting, ToolchainUiState.Integrating -> "Preparando…"
                        else -> "Opcional"
                    }, kotlinState is ToolchainUiState.Installed,
                    when (kotlinState) {
                        is ToolchainUiState.Downloading -> onPauseKotlin
                        is ToolchainUiState.Paused -> onPauseKotlin
                        else -> onInstallKotlin
                    },
                    actionLabel = when (kotlinState) {
                        is ToolchainUiState.Downloading -> "Pausar"
                        is ToolchainUiState.Paused -> if (kotlinState.reason == "Pausado por você") "Continuar" else "Pausar"
                        is ToolchainUiState.Extracting, ToolchainUiState.Integrating -> "Aguarde"
                        else -> "Instalar"
                    },
                )
            }
        }
    }
    if (showCreate) CreateProjectDialog(onDismiss = { showCreate = false }, onCreate = { name, template ->
        val project = createProject(projectsRoot, name, template)
        if (project != null) {
            refresh++
            showCreate = false
            activeProject = project
        }
        project
    })
    if (showUnsaved) AlertDialog(
        onDismissRequest = { showUnsaved = false; pendingFile = null },
        title = { Text("Alterações não salvas") },
        text = { Text("Deseja salvar antes de sair do arquivo?") },
        dismissButton = { TextButton(onClick = { showUnsaved = false; pendingFile = null }) { Text("Continuar editando") } },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val current = activeFile
                    val text = codeEditor?.text?.toString() ?: code
                    if (current != null) { current.writeText(text); refresh++ }
                    code = text
                    savedCode = text
                    showUnsaved = false
                    val next = pendingFile
                    pendingFile = null
                    if (next != null) openFile(next) else { activeFile = null; codeEditor = null }
                }) { Text("Salvar") }
                TextButton(onClick = {
                    showUnsaved = false
                    val next = pendingFile
                    pendingFile = null
                    if (next != null) { activeFile = next; codeEditor = null; code = next.readText(); savedCode = code } else { activeFile = null; codeEditor = null }
                }) { Text("Descartar") }
            }
        },
    )
    if (showBuildInfo) AlertDialog(
        onDismissRequest = { showBuildInfo = false },
        title = { Text(when (buildState) { is ProjectBuildState.Running -> "Compilando projeto"; is ProjectBuildState.Finished -> "APK pronto"; is ProjectBuildState.Failed -> "Falha no build"; ProjectBuildState.Idle -> "Build" }) },
        text = {
            val headline = when (buildState) {
                is ProjectBuildState.Running -> buildState.step
                is ProjectBuildState.Finished -> "APK debug assinado pronto."
                is ProjectBuildState.Failed -> buildState.message
                ProjectBuildState.Idle -> "Preparando compilação…"
            }
            val details = when (buildState) {
                is ProjectBuildState.Finished -> buildState.log
                is ProjectBuildState.Failed -> buildState.log
                else -> ""
            }
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(headline, color = Ink)
                if (details.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    SelectionContainer { Text(details, color = Muted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                }
            }
        },
        confirmButton = {
            Row {
                if (buildState is ProjectBuildState.Finished) TextButton(onClick = { onInstallApk((buildState as ProjectBuildState.Finished).apk) }) { Text("Instalar APK") }
                TextButton(onClick = { showBuildInfo = false }) { Text("Fechar") }
            }
        },
    )
}

@Composable
private fun ToolchainCard(title: String, detail: String, status: String, installed: Boolean, onClick: () -> Unit, actionLabel: String? = null) {
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall); Text(status, color = if (installed) Green else Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp)) }
            if (!installed) OutlinedButton(onClick = onClick) { Text(actionLabel ?: if (status == "Preparando…") "Aguarde" else "Instalar") } else Text("✓", color = Green)
        }
    }
}

@Composable
private fun ProjectFiles(project: File, onBack: () -> Unit, onFile: (File) -> Unit, onBuild: () -> Unit, modifier: Modifier = Modifier) {
    var refresh by remember(project) { mutableStateOf(0) }
    var showNewFile by remember(project) { mutableStateOf(false) }
    var newFilePath by remember(project) { mutableStateOf("") }
    var fileError by remember(project) { mutableStateOf<String?>(null) }
    val files = remember(project, refresh) { project.walkTopDown().filter { it.isFile && "/build/" !in it.path }.toList() }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹  Projetos", color = Green, modifier = Modifier.clickable(onClick = onBack))
            Text(project.name, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 14.dp))
            TextButton(onClick = { showNewFile = true }) { Text("+ Arquivo", color = Green) }
            Button(onClick = onBuild) { Text("Build") }
        }
        Column(Modifier.padding(20.dp)) {
            Text("ARQUIVOS", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("${project.absolutePath}", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        }
        LazyColumn {
            items(files, key = { it.absolutePath }) { file ->
                Row(Modifier.fillMaxWidth().clickable { onFile(file) }.padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (file.extension in listOf("cpp", "c", "h", "kt", "java")) "◉" else "▤", color = Green, modifier = Modifier.padding(end = 12.dp))
                    Column { Text(file.relativeTo(project).path, color = Ink, style = MaterialTheme.typography.bodyMedium); Text("${file.length()} bytes", color = Muted, style = MaterialTheme.typography.labelSmall) }
                }
                Divider(color = Color(0xFFE9EEEA), modifier = Modifier.padding(start = 50.dp))
            }
        }
    }
    if (showNewFile) AlertDialog(
        onDismissRequest = { showNewFile = false; fileError = null },
        title = { Text("Novo arquivo") },
        text = {
            Column {
                OutlinedTextField(value = newFilePath, onValueChange = { newFilePath = it.take(120) }, label = { Text("Caminho relativo") }, placeholder = { Text("src/main/cpp/util.cpp") }, singleLine = true)
                Text("O projeto YAML será atualizado para fontes C, C++, Java e Kotlin.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                fileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val relative = newFilePath.trim().replace('\\', '/')
            val root = project.canonicalFile
            val file = File(project, relative).canonicalFile
            if (relative.isBlank() || !file.toPath().startsWith(root.toPath()) || file.exists() || relative.endsWith('/')) {
                fileError = "Caminho inválido ou arquivo já existente."
            } else {
                file.parentFile?.mkdirs()
                file.writeText("")
                updateProjectSources(project, relative)
                refresh++
                showNewFile = false
                newFilePath = ""
                onFile(file)
            }
        }) { Text("Criar") } },
        dismissButton = { TextButton(onClick = { showNewFile = false }) { Text("Cancelar") } },
    )
}

private fun updateProjectSources(project: File, relativePath: String) {
    val category = when (relativePath.substringAfterLast('.', "").lowercase()) {
        "c" -> "c"
        "cc", "cpp", "cxx" -> "cpp"
        "java" -> "java"
        "kt" -> "kotlin"
        else -> return
    }
    val yamlFile = File(project, "qtcm.project.yaml")
    val lines = yamlFile.readLines().toMutableList()
    addYamlSource(lines, category, relativePath)
    val javaOrKotlin = category == "java" || category == "kotlin"
    val wasNative = javaOrKotlin && lines.any { it.trim() == "entryPoint: native-activity" }
    if (wasNative) {
        val index = lines.indexOfFirst { it.trim() == "entryPoint: native-activity" }
        lines[index] = lines[index].replace("native-activity", "kotlin-wrapper")
        val hasActivity = project.walkTopDown().any { it.isFile && it.name in listOf("MainActivity.kt", "MainActivity.java") }
        if (!hasActivity) {
            val packageName = lines.firstOrNull { it.trim().startsWith("package:") }?.substringAfter(':')?.trim()?.removeSurrounding("\"") ?: "com.example.app"
            val extension = if (category == "java") "java" else "kt"
            val activityPath = "src/main/java/${packageName.replace('.', '/')}/MainActivity.$extension"
            val activity = File(project, activityPath)
            activity.parentFile?.mkdirs()
            activity.writeText(if (extension == "kt") """package $packageName

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TextView(this).apply { text = "Hello from QT Creator Mobile!"; textSize = 22f }.also { setContentView(it) }
    }
}
""" else """package $packageName;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("Hello from QT Creator Mobile!");
        view.setTextSize(22f);
        setContentView(view);
    }
}
""")
            addYamlSource(lines, if (extension == "kt") "kotlin" else "java", activityPath)
        }
    }
    yamlFile.writeText(lines.joinToString("\n") + "\n")
}

private fun addYamlSource(lines: MutableList<String>, category: String, relativePath: String) {
    val targetsIndex = lines.indexOfFirst { it.trim() == "targets:" }.let { if (it < 0) lines.size else it }
    if ((0 until targetsIndex).none { lines[it].trim() == "- $category" }) lines.add(targetsIndex, "  - $category")
    val sourcesIndex = lines.indexOfFirst { it.trim() == "sources:" }
    if (sourcesIndex < 0) return
    val entryIndex = (sourcesIndex + 1 until lines.size).firstOrNull { lines[it].trim().startsWith("entryPoint:") } ?: lines.size
    val categoryIndex = (sourcesIndex + 1 until entryIndex).firstOrNull { lines[it].trim() == "$category:" }
    if (categoryIndex != null) {
        val nextSection = (categoryIndex + 1 until entryIndex).firstOrNull { lines[it].startsWith("      ") && !lines[it].startsWith("        ") } ?: entryIndex
        if ((categoryIndex + 1 until nextSection).none { lines[it].trim() == "- $relativePath" }) lines.add(nextSection, "        - $relativePath")
    } else {
        lines.add(entryIndex, "        - $relativePath")
        lines.add(entryIndex, "      $category:")
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String, String) -> File?) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("C++ NativeActivity") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Novo projeto") }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' || c == ' ' }.take(32) }, label = { Text("Nome do projeto") }, singleLine = true)
            Spacer(Modifier.height(14.dp)); Text("MODELO", color = Muted, style = MaterialTheme.typography.labelMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            listOf("C++ NativeActivity", "Kotlin Android (Views)", "Java Android (Views)").forEach { choice ->
                Row(Modifier.fillMaxWidth().clickable { template = choice }.padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                    Text(if (template == choice) "●" else "○", color = Green, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
                    Column {
                        Text(choice, color = Ink, fontWeight = FontWeight.Medium)
                        Text(when (choice) {
                            "C++ NativeActivity" -> "Esqueleto nativo; não inclui interface gráfica pronta."
                            "Java Android (Views)" -> "Activity Java simples; sem dependências externas."
                            else -> "Activity Kotlin simples; sem dependências externas."
                        }, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { if (onCreate(name, template) == null) error = "Nome inválido ou já existe um projeto com esse nome." }) { Text("Criar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

private fun createProject(root: File, rawName: String, template: String): File? {
    val name = rawName.trim().replace(Regex("\\s+"), "_")
    if (name.isBlank()) return null
    val safeName = name.replace(Regex("[^A-Za-z0-9_-]"), "")
    if (safeName.isBlank()) return null
    val dir = File(root, safeName)
    if (dir.exists()) return null
    val stage = File(root, ".$safeName-${System.nanoTime()}.creating")
    return try {
        val kotlin = template.startsWith("Kotlin")
        val java = template.startsWith("Java")
        val managed = kotlin || java
        val language = if (kotlin) "kotlin" else if (java) "java" else "cpp"
        val extension = if (kotlin) "kt" else if (java) "java" else "cpp"
        val packageSegment = safeName.lowercase().replace(Regex("[^a-z0-9_]"), "_")
            .let { if (it.firstOrNull()?.let(Char::isLetter) == true || it.startsWith("_")) it else "p_$it" }
        val packageName = "com.example.$packageSegment"
        val managedPath = "src/main/java/${packageName.replace('.', '/')}"
        val sourceDir = if (managed) File(stage, managedPath) else File(stage, "src/main/cpp")
        check(sourceDir.mkdirs()) { "Não foi possível criar a pasta de fontes." }
        val sourcePath = if (managed) "$managedPath/MainActivity.$extension" else "src/main/cpp/main.cpp"
        File(stage, "qtcm.project.yaml").writeText("""name: $safeName
package: $packageName
version: "1.0.0"
languages:
  - $language
targets:
  - id: app
    type: android-app
    minSdk: 26
    sources:
      $language:
        - $sourcePath
    entryPoint: ${if (managed) "kotlin-wrapper" else "native-activity"}
""")
        when {
            kotlin -> File(sourceDir, "MainActivity.kt").writeText("""package $packageName

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TextView(this).apply { text = "Hello from QT Creator Mobile!"; textSize = 22f }
            .also { setContentView(it) }
    }
}
""")
            java -> File(sourceDir, "MainActivity.java").writeText("""package $packageName;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView message = new TextView(this);
        message.setText("Hello from QT Creator Mobile!");
        message.setTextSize(22f);
        setContentView(message);
    }
}
""")
            else -> File(sourceDir, "main.cpp").writeText("""#include <android/native_activity.h>

extern "C" void ANativeActivity_onCreate(ANativeActivity* activity, void*, size_t) {
    activity->callbacks->onStart = [](ANativeActivity*) {};
    activity->callbacks->onResume = [](ANativeActivity*) {};
    activity->callbacks->onPause = [](ANativeActivity*) {};
    activity->callbacks->onStop = [](ANativeActivity*) {};
    activity->callbacks->onDestroy = [](ANativeActivity*) {};
}
""")
        }
        check(stage.renameTo(dir)) { "Não foi possível finalizar a criação do projeto." }
        dir
    } catch (_: Exception) {
        stage.deleteRecursively()
        null
    }
}
