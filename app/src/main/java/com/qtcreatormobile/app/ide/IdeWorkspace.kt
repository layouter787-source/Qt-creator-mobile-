package com.qtcreatormobile.app.ide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtcreatormobile.app.ai.AiChatScreen
import com.qtcreatormobile.app.ai.AiViewModel
import com.qtcreatormobile.projectmodel.ProjectFiles
import kotlinx.coroutines.delay
import java.io.File

private enum class Panel { NONE, FILES, SEARCH, BUILD }

private sealed interface TreeDialog {
    data class NewFile(val parent: File) : TreeDialog
    data class NewFolder(val parent: File) : TreeDialog
    data class Rename(val file: File) : TreeDialog
    data class Delete(val file: File) : TreeDialog
}

@Composable
fun IdeWorkspace(viewModel: IdeViewModel, state: IdeUiState, settings: EditorSettings, onSettings: () -> Unit) {
    val project = state.project ?: return
    val handle = remember { EditorHandle() }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<TreeDialog?>(null) }
    var aiOpen by remember { mutableStateOf(false) }
    val aiViewModel: AiViewModel = viewModel()
    var historyTick by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    DisposableEffect(handle) {
        viewModel.attachEditor { path -> handle.currentText(path) }
        onDispose { viewModel.attachEditor(null) }
    }

    // Salvamento automatico ~1,5 s depois da primeira edicao.
    LaunchedEffect(state.dirty, settings.autoSave) {
        if (settings.autoSave && state.dirty.isNotEmpty()) {
            delay(1500)
            viewModel.saveAll()
        }
    }

    // Ao iniciar um build, mostra o painel de build.
    LaunchedEffect(state.build is BuildPanelState.Running) {
        if (state.build is BuildPanelState.Running) panel = Panel.BUILD
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // ----- barra superior -----
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.closeProject() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar projeto") }
                    Text(project.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { viewModel.activeFileUndo(handle); historyTick++ }, enabled = handle.canUndo() || historyTick >= 0) { Text("↶", fontSize = 20.sp) }
                    TextButton(onClick = { handle.redo(); historyTick++ }) { Text("↷", fontSize = 20.sp) }
                    TextButton(onClick = { viewModel.save() }, enabled = state.dirty.isNotEmpty()) { Text("Salvar") }
                    IconButton(onClick = { viewModel.build(BuildVariant.DEBUG, installAfter = true) }, enabled = state.build !is BuildPanelState.Running) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Executar", tint = IdeColors.Accent)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mais") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Compilar APK Debug") }, onClick = { menuOpen = false; viewModel.build(BuildVariant.DEBUG, false) })
                            DropdownMenuItem(text = { Text("Compilar APK Release") }, onClick = { menuOpen = false; viewModel.build(BuildVariant.RELEASE, false) })
                            DropdownMenuItem(text = { Text("Salvar tudo") }, onClick = { menuOpen = false; viewModel.saveAll() })
                            DropdownMenuItem(text = { Text("Buscar no arquivo") }, onClick = { menuOpen = false; findOpen = true })
                            DropdownMenuItem(text = { Text("Configurações") }, onClick = { menuOpen = false; onSettings() })
                        }
                    }
                }

                // ----- abas -----
                if (state.tabs.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        state.tabs.forEachIndexed { index, file ->
                            val active = index == state.activeIndex
                            val dirty = file.absolutePath in state.dirty
                            Row(
                                modifier = Modifier
                                    .clickable { viewModel.selectTab(index) }
                                    .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    (if (dirty) "● " else "") + file.name,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                IconButton(onClick = { viewModel.closeTab(index) }, modifier = Modifier.width(28.dp).height(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Fechar aba", modifier = Modifier.width(14.dp).height(14.dp))
                                }
                            }
                        }
                    }
                    state.activeFile?.let { file ->
                        Text(
                            ProjectFiles.relativePath(project, file).replace("/", "  ›  "),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // ----- busca no arquivo -----
                if (findOpen && state.activeFile != null) {
                    FindBar(handle = handle, onClose = { findOpen = false; handle.stopFind() })
                }

                // ----- editor -----
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val active = state.activeFile
                    if (active == null) {
                        EmptyEditor(onOpenFiles = { panel = Panel.FILES }, onSearch = { viewModel.loadFileIndex(); panel = Panel.SEARCH })
                    } else {
                        CodeEditorView(
                            handle = handle,
                            file = active,
                            text = state.activeText,
                            revision = state.editorRevision,
                            jumpLine = state.jumpLine,
                            settings = settings,
                            onChanged = { viewModel.markDirty(); historyTick++ },
                            onJumped = viewModel::consumeJump,
                        )
                    }
                }

                // ----- painel inferior (busca / build) -----
                if (panel == Panel.SEARCH) {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(MaterialTheme.colorScheme.surface)) {
                        SearchPanel(viewModel, state, onClose = { panel = Panel.NONE })
                    }
                }
                if (panel == Panel.BUILD) {
                    Box(modifier = Modifier.fillMaxWidth().height(320.dp).background(MaterialTheme.colorScheme.surface)) {
                        BuildPanel(viewModel, state, onClose = { panel = Panel.NONE })
                    }
                }

                // ----- barra de simbolos -----
                if (state.activeFile != null) SymbolBar(handle = handle, tabSize = settings.tabSize)

                // ----- barra de ferramentas -----
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToolItem("Arquivos", panel == Panel.FILES) { panel = if (panel == Panel.FILES) Panel.NONE else Panel.FILES }
                    ToolItem("Buscar", panel == Panel.SEARCH) {
                        if (panel == Panel.SEARCH) panel = Panel.NONE else { viewModel.loadFileIndex(); panel = Panel.SEARCH }
                    }
                    ToolItem("IA", aiOpen) { aiOpen = true }
                    ToolItem("Build", panel == Panel.BUILD) { panel = if (panel == Panel.BUILD) Panel.NONE else Panel.BUILD }
                    ToolItem("Ajustes", false) { onSettings() }
                }
            }

            // ----- gaveta da arvore de arquivos -----
            if (panel == Panel.FILES) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxHeight().width(300.dp).statusBarsPadding().navigationBarsPadding(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                    ) {
                        FileTreePanel(
                            state = state,
                            project = project,
                            onToggle = viewModel::toggleFolder,
                            onOpen = { viewModel.openFile(it); panel = Panel.NONE },
                            onDialog = { dialog = it },
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0x66000000)).clickable { panel = Panel.NONE })
                }
            }

            // ----- assistente de IA (sobre o editor, que continua montado) -----
            if (aiOpen) {
                BackHandler { aiOpen = false }
                AiChatScreen(
                    viewModel = aiViewModel,
                    fileName = state.activeFile?.name,
                    fileText = { state.activeFile?.let { viewModel.currentText(it) } },
                    onInsert = { handle.insert(it) },
                    onReplaceFile = { code -> handle.editor?.setText(code) },
                    onClose = { aiOpen = false },
                )
            }
        }
    }

    dialog?.let { current ->
        when (current) {
            is TreeDialog.NewFile -> NameDialog("Novo arquivo", "", "Criar", { name -> viewModel.createFile(current.parent, name) }) { dialog = null; panel = Panel.NONE }
            is TreeDialog.NewFolder -> NameDialog("Nova pasta", "", "Criar", { name -> viewModel.createFolder(current.parent, name) }) { dialog = null }
            is TreeDialog.Rename -> NameDialog("Renomear", current.file.name, "Renomear", { name -> viewModel.rename(current.file, name) }) { dialog = null }
            is TreeDialog.Delete -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Excluir ${current.file.name}?") },
                text = { Text(if (current.file.isDirectory) "A pasta e tudo dentro dela serão apagados." else "O arquivo será apagado.") },
                confirmButton = { TextButton(onClick = { viewModel.delete(current.file); dialog = null }) { Text("Excluir") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
            )
        }
    }
}

private fun IdeViewModel.activeFileUndo(handle: EditorHandle) = handle.undo()

@Composable
private fun ToolItem(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyEditor(onOpenFiles: () -> Unit, onSearch: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nenhum arquivo aberto", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenFiles) { Text("Abrir arquivos") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSearch) { Text("Ir para arquivo") }
    }
}

@Composable
private fun SymbolBar(handle: EditorHandle, tabSize: Int) {
    val symbols = listOf("(", ")", "{", "}", "[", "]", ";", "\"", "'", "<", ">", "/", "=", "+", "-", "*", "&", "|", "!", ".", ",", ":", "_", "#")
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).horizontalScroll(rememberScrollState())) {
        TextButton(onClick = { handle.insert(" ".repeat(tabSize)) }) { Text("⇥", fontSize = 18.sp) }
        symbols.forEach { s ->
            TextButton(onClick = { handle.insert(s) }) { Text(s, fontFamily = FontFamily.Monospace, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun FindBar(handle: EditorHandle, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; handle.find(it) },
                label = { Text("Buscar") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { handle.findPrevious() }) { Text("▲") }
            TextButton(onClick = { handle.findNext() }) { Text("▼") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fechar busca") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it },
                label = { Text("Substituir por") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { handle.replaceCurrent(replacement) }) { Text("Uma") }
            TextButton(onClick = { handle.replaceAll(replacement) }) { Text("Todas") }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> String?, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = value, onValueChange = { value = it; error = null }, singleLine = true, label = { Text("Nome") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onConfirm(value)
                if (result == null) onDismiss() else error = result
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FileTreePanel(state: IdeUiState, project: File, onToggle: (File) -> Unit, onOpen: (File) -> Unit, onDialog: (TreeDialog) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(project.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { onDialog(TreeDialog.NewFile(project)) }) { Text("+ Arquivo") }
            TextButton(onClick = { onDialog(TreeDialog.NewFolder(project)) }) { Text("+ Pasta") }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.tree, key = { it.file.absolutePath }) { entry ->
                var menu by remember { mutableStateOf(false) }
                val expanded = entry.file.absolutePath in state.expanded
                val active = state.activeFile?.absolutePath == entry.file.absolutePath
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { if (entry.isDirectory) onToggle(entry.file) else onOpen(entry.file) }
                        .padding(start = (12 + entry.depth * 16).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (entry.isDirectory) (if (expanded) "▾ 📂" else "▸ 📁") else "   📄", fontSize = 15.sp)
                    Text(
                        entry.file.name,
                        modifier = Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.width(36.dp).height(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Ações", modifier = Modifier.width(18.dp).height(18.dp))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (entry.isDirectory) {
                                DropdownMenuItem(text = { Text("Novo arquivo") }, onClick = { menu = false; onDialog(TreeDialog.NewFile(entry.file)) })
                                DropdownMenuItem(text = { Text("Nova pasta") }, onClick = { menu = false; onDialog(TreeDialog.NewFolder(entry.file)) })
                            }
                            DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; onDialog(TreeDialog.Rename(entry.file)) })
                            DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; onDialog(TreeDialog.Delete(entry.file)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(viewModel: IdeViewModel, state: IdeUiState, onClose: () -> Unit) {
    val project = state.project ?: return
    var mode by remember { mutableIntStateOf(0) }
    var fileQuery by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Ir para arquivo") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Buscar no projeto") })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fechar") }
        }
        if (mode == 0) {
            OutlinedTextField(value = fileQuery, onValueChange = { fileQuery = it }, label = { Text("Nome do arquivo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val matches = state.fileIndex.filter { fileQuery.isBlank() || it.name.contains(fileQuery, ignoreCase = true) }.take(100)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(matches, key = { it.absolutePath }) { file ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { viewModel.openFile(file); onClose() }.padding(vertical = 6.dp, horizontal = 8.dp)) {
                        Text(file.name, style = MaterialTheme.typography.bodyMedium)
                        Text(ProjectFiles.relativePath(project, file), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            OutlinedTextField(value = state.findQuery, onValueChange = viewModel::findInFiles, label = { Text("Texto (mínimo 2 letras)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.findHits) { hit ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { viewModel.openFile(hit.file, hit.line); onClose() }.padding(vertical = 6.dp, horizontal = 8.dp)) {
                        Text("${ProjectFiles.relativePath(project, hit.file)}:${hit.line}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(hit.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildPanel(viewModel: IdeViewModel, state: IdeUiState, onClose: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Build", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fechar") }
        }
        when (val build = state.build) {
            is BuildPanelState.Idle -> {
                Text("Compile o projeto para gerar um APK.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.build(BuildVariant.DEBUG, false) }) { Text("APK Debug") }
                    OutlinedButton(onClick = { viewModel.build(BuildVariant.RELEASE, false) }) { Text("APK Release") }
                }
                state.lastApk?.takeIf { it.isFile }?.let { apk ->
                    Spacer(Modifier.height(8.dp))
                    Text("Último APK: ${apk.name}", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { installApk(context, apk) }) { Text("Instalar") }
                        TextButton(onClick = { shareApk(context, apk) }) { Text("Compartilhar") }
                    }
                }
            }
            is BuildPanelState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("${build.variant.title}: ${build.stage}…")
                }
                Text("A primeira compilação pode levar alguns minutos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is BuildPanelState.Done -> {
                val outcome = build.outcome
                Text(
                    (if (outcome.succeeded) "✔ " else "✖ ") + outcome.summary + "  (${outcome.durationMs / 1000}s)",
                    color = if (outcome.succeeded) IdeColors.Success else IdeColors.Error,
                    style = MaterialTheme.typography.titleSmall,
                )
                Row {
                    outcome.apk?.let { apk ->
                        TextButton(onClick = { installApk(context, apk) }) { Text("Instalar") }
                        TextButton(onClick = { shareApk(context, apk) }) { Text("Compartilhar") }
                    }
                    TextButton(onClick = { copyToClipboard(context, "log", outcome.fullLog) }) { Text("Copiar log") }
                    TextButton(onClick = { viewModel.build(build.variant, false) }) { Text("Repetir") }
                }
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        outcome.fullLog.ifBlank { "(sem log)" },
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
