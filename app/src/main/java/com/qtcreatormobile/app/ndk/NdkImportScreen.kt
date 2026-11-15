package com.qtcreatormobile.app.ndk

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.qtcreatormobile.buildengine.NdkArchiveCandidate
import java.util.Locale

private const val NDK_DOWNLOADS_URL = "https://developer.android.com/ndk/downloads"

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    else -> String.format(Locale.US, "%d MB", bytes / (1024L * 1024L))
}

/**
 * Tela mostrada quando o setup automatico terminou mas falta o NDK: o usuario baixa o zip no
 * site oficial (fora do app) e o app o localiza/importa. Qualquer versao serve.
 */
@Composable
fun NdkImportScreen(
    state: NdkImportUiState,
    onSearch: () -> Unit,
    onStoragePermissionResult: (Boolean) -> Unit,
    onPickedUri: (Uri) -> Unit,
    onUseCandidate: (NdkArchiveCandidate) -> Unit,
    onCancel: () -> Unit,
    onDismissSuccess: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickedUri(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onStoragePermissionResult(granted)
    }

    // Procura ao abrir e ao voltar do navegador (o download pode ter terminado enquanto isso).
    LaunchedEffect(Unit) { onSearch() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onSearch() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Falta o NDK do Android", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        when {
            state.importing -> {
                Text("Importando ${state.sourceName ?: "o NDK"}...", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${(state.fraction * 100).toInt()}% - extraindo apenas os arquivos de que o app precisa. " +
                        "Mantenha o app aberto; pode levar alguns minutos.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }

            state.successMessage != null -> {
                Text(state.successMessage, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onDismissSuccess, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
            }

            else -> {
                Text(
                    "O NDK pesa cerca de 700 MB, por isso o download e feito por voce, uma unica vez. " +
                        "Qualquer versao serve - de preferencia a mais recente.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "1. Abra a pagina oficial do NDK.\n" +
                        "2. Baixe o \"NDK for Linux\" (arquivo android-ndk-rXX-linux.zip).\n" +
                        "3. Volte para o app: o arquivo baixado aparece aqui para importar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { uriHandler.openUri(NDK_DOWNLOADS_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir a pagina de download do NDK")
                }

                if (state.error != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            state.error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Text("Arquivos encontrados", style = MaterialTheme.typography.titleMedium)
                if (state.candidates.isEmpty()) {
                    Text(
                        if (state.searched) "Nenhum arquivo do NDK foi encontrado nos Downloads ainda."
                        else "Procurando nos Downloads...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.candidates.forEach { candidate ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.file.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        formatSize(candidate.sizeBytes),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(onClick = { onUseCandidate(candidate) }) { Text("Importar") }
                            }
                        }
                    }
                }

                if (!state.hasStoragePermission) {
                    OutlinedButton(
                        onClick = { permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Permitir buscar nos Downloads") }
                }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Escolher o arquivo manualmente") }
                OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("Procurar novamente") }
            }
        }
    }
}
