package com.qtcreatormobile.app.ide

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun KeystoreScreen(viewModel: KeystoreViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var toDelete by remember { mutableStateOf<KeystoreEntry?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importUri = uri }

    ScreenScaffold("Gerenciador de keystore", onBack) {
        Text(
            "A chave escolhida aqui assina o APK Release. Guarde uma cópia do arquivo e da senha: se você perder a chave, não consegue mais atualizar o app já instalado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { creating = true }, enabled = !state.busy) { Text("Criar keystore") }
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text("Importar") }
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (state.items.isEmpty()) {
            Text("Nenhum keystore ainda. Crie um ou importe o seu (.jks, .keystore ou .p12).")
        }
        state.items.forEach { entry ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.selectedId == entry.id, onClick = { viewModel.select(entry.id) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium)
                            Text("alias: ${entry.alias}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { viewModel.describe(entry) }) { Text("Detalhes") }
                        TextButton(onClick = { toDelete = entry }) { Text("Excluir") }
                    }
                }
            }
        }
    }

    if (creating) {
        CreateKeystoreDialog(viewModel, onDismiss = { creating = false })
    }
    importUri?.let { uri ->
        ImportKeystoreDialog(viewModel, uri, onDismiss = { importUri = null })
    }
    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Excluir \"${entry.name}\"?") },
            text = { Text("O arquivo do keystore será apagado do aparelho. Sem uma cópia, não dá para recuperá-lo.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(entry.id); toDelete = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
    state.details?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDetails,
            title = { Text("Detalhes da chave") },
            text = {
                SelectionContainer {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDetails) { Text("Fechar") } },
        )
    }
}

@Composable
private fun CreateKeystoreDialog(viewModel: KeystoreViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Minha chave") }
    var alias by remember { mutableStateOf("key0") }
    var password by remember { mutableStateOf("") }
    var commonName by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var years by remember { mutableStateOf("25") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Criar keystore") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(alias, { alias = it }, label = { Text("Alias da chave") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Senha (mín. 6)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(commonName, { commonName = it }, label = { Text("Nome do titular (CN)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(org, { org = it }, label = { Text("Organização (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(country, { country = it }, label = { Text("País, 2 letras (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(years, { years = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Validade (anos)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (working) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                working = true
                error = null
                viewModel.create(name, alias.trim(), password, commonName, org, country, years.toIntOrNull() ?: 25) { result ->
                    working = false
                    if (result == null) onDismiss() else error = result
                }
            }) { Text("Criar") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ImportKeystoreDialog(viewModel: KeystoreViewModel, uri: Uri, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Keystore importado") }
    var alias by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Importar keystore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(alias, { alias = it }, label = { Text("Alias da chave") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Senha do keystore") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("A senha da chave precisa ser igual à do keystore (padrão do PKCS12).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (working) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                working = true
                error = null
                viewModel.import(uri, name, alias.trim(), password) { result ->
                    working = false
                    if (result == null) onDismiss() else error = result
                }
            }) { Text("Importar") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancelar") } },
    )
}
