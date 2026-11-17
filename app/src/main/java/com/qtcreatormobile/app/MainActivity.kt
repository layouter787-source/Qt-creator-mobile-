package com.qtcreatormobile.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtcreatormobile.app.ide.IdeApp
import com.qtcreatormobile.app.ide.IdeTheme
import com.qtcreatormobile.app.ide.IdeViewModel
import com.qtcreatormobile.app.ide.copyToClipboard
import com.qtcreatormobile.app.ndk.NdkImportScreen
import com.qtcreatormobile.app.ndk.NdkImportViewModel
import com.qtcreatormobile.app.toolchain.MandatoryCppSetupOverlay
import com.qtcreatormobile.app.toolchain.ToolchainUiState
import com.qtcreatormobile.app.toolchain.ToolchainViewModel
import com.qtcreatormobile.projectmodel.Language

class MainActivity : ComponentActivity() {

    private val toolchainViewModel: ToolchainViewModel by viewModels()
    private val ideViewModel: IdeViewModel by viewModels()
    private val ndkImportViewModel: NdkImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Se a execucao anterior terminou em falha, nao reabre o ultimo projeto sozinho (evita
        // um ciclo de falhas) e mostra o relatorio do erro.
        val crashAtLaunch = CrashLog.pending(this)
        CrashLog.event(this, "abriu o app")
        setContent {
            val cppState by toolchainViewModel.state(Language.CPP).collectAsStateWithLifecycle()
            val ndkState by ndkImportViewModel.state.collectAsStateWithLifecycle()
            val appearance by ideViewModel.settings.appearance.collectAsStateWithLifecycle()
            var crash by remember { mutableStateOf(crashAtLaunch) }

            // O setup do C/C++ roda sozinho; o usuario so espera a barra de progresso.
            LaunchedEffect(Unit) {
                if (cppState is ToolchainUiState.NotInstalled) {
                    toolchainViewModel.startCppSetup()
                }
            }

            IdeTheme(appearance.accent, appearance.amoled) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (cppState is ToolchainUiState.Installed) {
                        if (ndkState.ready && ndkState.successMessage == null) {
                            IdeApp(ideViewModel, restoreLast = crashAtLaunch == null)
                        } else {
                            NdkImportScreen(
                                state = ndkState,
                                onSearch = ndkImportViewModel::searchDownloads,
                                onStoragePermissionResult = ndkImportViewModel::onStoragePermissionResult,
                                onPickedUri = ndkImportViewModel::importUri,
                                onUseCandidate = ndkImportViewModel::importCandidate,
                                onCancel = ndkImportViewModel::cancelImport,
                                onDismissSuccess = ndkImportViewModel::dismissSuccess,
                            )
                        }
                    } else {
                        MandatoryCppSetupOverlay(
                            state = cppState,
                            onRetryClick = { toolchainViewModel.startCppSetup() },
                            onPauseClick = { toolchainViewModel.toggleManualPause() },
                        )
                    }

                    crash?.let { report ->
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("O app fechou na última vez") },
                            text = {
                                SelectionContainer {
                                    Text(
                                        report.take(3500),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        modifier = Modifier.verticalScroll(rememberScrollState()),
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, report)
                                    }
                                    startActivity(Intent.createChooser(send, "Enviar relatório"))
                                }) { Text("Compartilhar") }
                            },
                            dismissButton = {
                                Row2(
                                    onCopy = { copyToClipboard(this@MainActivity, "falha", report) },
                                    onSafeMode = {
                                        ideViewModel.settings.update { it.copy(syntaxHighlight = false) }
                                        CrashLog.dismiss(this@MainActivity)
                                        crash = null
                                    },
                                    onClose = {
                                        CrashLog.dismiss(this@MainActivity)
                                        crash = null
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Row2(onCopy: () -> Unit, onSafeMode: () -> Unit, onClose: () -> Unit) {
    androidx.compose.foundation.layout.Column {
        TextButton(onClick = onCopy) { Text("Copiar") }
        TextButton(onClick = onSafeMode) { Text("Modo seguro (sem realce)") }
        TextButton(onClick = onClose) { Text("Fechar") }
    }
}
