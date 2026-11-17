package com.qtcreatormobile.app.ide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.qtcreatormobile.projectmodel.ProjectTemplates
import java.text.DateFormat
import java.util.Date

@Composable
fun IdeHomeScreen(
    state: IdeUiState,
    onOpen: (ProjectEntry) -> Unit,
    onDelete: (ProjectEntry) -> Unit,
    onCreate: (templateId: String, name: String, packageName: String) -> String?,
    suggestPackage: (String) -> String,
    onSettings: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectEntry?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("QT Creator Mobile", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Text("IDE para Android, direto no celular", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Configurações") }
                }
                if (state.projects.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nenhum projeto ainda", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Toque em \"Novo projeto\" e escolha um modelo: C++, Java + C++, Kotlin + C++ e mais.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text("Projetos", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    ) {
                        items(state.projects, key = { it.dir.absolutePath }) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                        if (entry.languages.isNotEmpty()) {
                                            Text(entry.languages, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(
                                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.modified)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = entry }) { Icon(Icons.Default.Delete, contentDescription = "Excluir projeto") }
                                }
                            }
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo projeto") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
            )
        }
    }

    if (creating) {
        NewProjectDialog(suggestPackage = suggestPackage, onCreate = onCreate, onDismiss = { creating = false })
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir projeto?") },
            text = { Text("\"${entry.name}\" e todos os seus arquivos serão apagados. Não dá para desfazer.") },
            confirmButton = { TextButton(onClick = { onDelete(entry); pendingDelete = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun NewProjectDialog(
    suggestPackage: (String) -> String,
    onCreate: (String, String, String) -> String?,
    onDismiss: () -> Unit,
) {
    var template by remember { mutableStateOf(ProjectTemplates.all.first().id) }
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var pkgEdited by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo projeto") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Modelo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ProjectTemplates.all.forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { template = t.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = template == t.id, onClick = { template = t.id })
                        Column {
                            Text(t.title, style = MaterialTheme.typography.bodyLarge)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (!pkgEdited) pkg = if (it.isBlank()) "" else suggestPackage(it)
                        error = null
                    },
                    label = { Text("Nome do projeto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it; pkgEdited = true; error = null },
                    label = { Text("Pacote") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onCreate(template, name, pkg)
                if (result == null) onDismiss() else error = result
            }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
