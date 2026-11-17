package com.qtcreatormobile.app.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtcreatormobile.aiclient.ApiFormat
import com.qtcreatormobile.aiclient.ChatMessage
import com.qtcreatormobile.aiclient.MarkdownBlocks
import com.qtcreatormobile.aiclient.MessageBlock
import com.qtcreatormobile.aiclient.ProviderConfig
import com.qtcreatormobile.aiclient.ProviderPresets
import com.qtcreatormobile.app.ide.copyToClipboard
import java.util.UUID

/** Tela cheia do assistente de IA. Os callbacks ligam as respostas ao editor. */
@Composable
fun AiChatScreen(
    viewModel: AiViewModel,
    fileName: String?,
    fileText: () -> String?,
    onInsert: (String) -> Unit,
    onReplaceFile: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showProviders by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    if (showProviders) {
        BackHandler { showProviders = false }
        AiProvidersScreen(state = state, viewModel = viewModel, onBack = { showProviders = false })
        return
    }

    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastLength) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                Column(modifier = Modifier.weight(1f).clickable { showProviders = true }) {
                    Text("Assistente IA", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.active?.let { "${it.name} · ${it.model.ifBlank { "sem modelo" }}" } ?: "Nenhum provedor — toque para configurar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { showProviders = true }) { Text("Provedores") }
                TextButton(onClick = viewModel::clearChat, enabled = state.messages.isNotEmpty()) { Text("Limpar") }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.providers.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Conecte uma IA", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Use OpenAI, Anthropic, OpenRouter, NVIDIA ou qualquer gateway compatível com chat completions. Sua chave fica cifrada no aparelho.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showProviders = true }) { Text("Configurar provedor") }
                    }
                } else if (state.messages.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pergunte sobre seu código", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Com \"Arquivo atual\" ligado, a IA lê o arquivo aberto no editor. Nos blocos de código você pode copiar, inserir no cursor ou substituir o arquivo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(state.messages, key = { _, m -> m.id }) { _, message ->
                            MessageBubble(message, onInsert, onReplaceFile, fileName != null)
                        }
                    }
                }
            }

            state.error?.let { error ->
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::dismissError) { Icon(Icons.Default.Close, contentDescription = "Fechar aviso") }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.includeFile && fileName != null,
                    onClick = { viewModel.setIncludeFile(!state.includeFile) },
                    enabled = fileName != null,
                    label = { Text(if (fileName != null) "Arquivo atual: $fileName" else "Nenhum arquivo aberto", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mensagem") },
                    maxLines = 5,
                )
                Spacer(Modifier.width(8.dp))
                if (state.sending) {
                    OutlinedButton(onClick = viewModel::stop) { Text("Parar") }
                } else {
                    Button(
                        onClick = {
                            val text = input
                            input = ""
                            viewModel.send(text, fileName, fileText())
                        },
                        enabled = input.isNotBlank() && state.providers.isNotEmpty(),
                    ) { Icon(Icons.Default.Send, contentDescription = "Enviar") }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: UiMessage, onInsert: (String) -> Unit, onReplaceFile: (String) -> Unit, hasFile: Boolean) {
    val mine = message.role == ChatMessage.USER
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(if (mine) 0.85f else 1f),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine) {
                    SelectionContainer { Text(message.text) }
                } else {
                    val blocks = MarkdownBlocks.split(message.text)
                    if (blocks.isEmpty()) Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    blocks.forEach { block ->
                        when (block) {
                            is MessageBlock.Text -> SelectionContainer { Text(block.text) }
                            is MessageBlock.Code -> CodeBlock(block, hasFile, onCopy = { copyToClipboard(context, "código", block.code) }, onInsert = onInsert, onReplace = onReplaceFile)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MessageBlock.Code, hasFile: Boolean, onCopy: () -> Unit, onInsert: (String) -> Unit, onReplace: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.background) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                Text(block.language.ifBlank { "código" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = onCopy) { Text("Copiar") }
                if (hasFile) {
                    TextButton(onClick = { onInsert(block.code) }) { Text("Inserir") }
                    TextButton(onClick = { onReplace(block.code) }) { Text("Substituir arquivo") }
                }
            }
            Text(
                block.code,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                softWrap = false,
            )
        }
    }
}

/** Lista, edita e escolhe os provedores de IA. */
@Composable
fun AiProvidersScreen(state: AiUiState, viewModel: AiViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var creating by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                Text("Provedores de IA", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "Adicionar provedor") }
            }
            if (state.providers.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nenhum provedor ainda", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("OpenAI, Anthropic, OpenRouter, NVIDIA ou um gateway próprio (URL base + chave).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { creating = true }) { Text("Adicionar provedor") }
                }
            } else {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.providers.forEach { provider ->
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.active?.id == provider.id, onClick = { viewModel.setActive(provider.id) })
                                Column(modifier = Modifier.weight(1f).clickable { viewModel.setActive(provider.id) }) {
                                    Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                    Text(provider.model.ifBlank { "sem modelo" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text(provider.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { editing = provider }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                                IconButton(onClick = { viewModel.deleteProvider(provider.id) }) { Icon(Icons.Default.Delete, contentDescription = "Excluir") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        ProviderEditorDialog(
            initial = editing,
            state = state,
            onFetchModels = viewModel::fetchModels,
            onSave = { viewModel.saveProvider(it); viewModel.clearModels(); creating = false; editing = null },
            onDismiss = { viewModel.clearModels(); creating = false; editing = null },
        )
    }
}

@Composable
private fun ProviderEditorDialog(
    initial: ProviderConfig?,
    state: AiUiState,
    onFetchModels: (ProviderConfig) -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = ProviderPresets.OPENAI
    var name by remember { mutableStateOf(initial?.name ?: first.name) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: first.baseUrl) }
    var format by remember { mutableStateOf(initial?.format ?: first.format) }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: first.defaultModel) }
    var error by remember { mutableStateOf<String?>(null) }

    fun current() = ProviderConfig(initial?.id ?: UUID.randomUUID().toString(), name.trim(), baseUrl.trim(), format, model.trim(), apiKey.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo provedor" else "Editar provedor") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Modelo pronto", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderPresets.all.forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                name = preset.name
                                baseUrl = preset.baseUrl
                                format = preset.format
                                model = preset.defaultModel
                                error = null
                            },
                            label = { Text(preset.name) },
                        )
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL base") },
                    placeholder = { Text("https://.../v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Protocolo", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ApiFormat.entries.forEach { f ->
                        FilterChip(selected = format == f, onClick = { format = f }, label = { Text(if (f == ApiFormat.ANTHROPIC) "Anthropic" else "OpenAI") })
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Chave de API") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onFetchModels(current()) }, enabled = !state.modelsLoading && baseUrl.isNotBlank()) {
                    Text(if (state.modelsLoading) "Buscando…" else "Buscar modelos / testar")
                }
                state.modelsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (state.models.isNotEmpty()) {
                    Text("${state.models.size} modelos — toque para escolher", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.height(160.dp).verticalScroll(rememberScrollState())) {
                        state.models.forEach { id ->
                            Text(id, modifier = Modifier.fillMaxWidth().clickable { model = id }.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val config = current()
                error = when {
                    config.name.isEmpty() -> "Informe um nome."
                    !(config.baseUrl.startsWith("https://") || config.baseUrl.startsWith("http://")) -> "A URL base precisa começar com https://"
                    config.model.isEmpty() -> "Informe o modelo (ou use Buscar modelos)."
                    else -> null
                }
                if (error == null) onSave(config)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
