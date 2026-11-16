package com.qtcreatormobile.app.ide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import java.io.File

fun installApk(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir o instalador: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareApk(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
}

/** Raiz da IDE: Home -> Workspace, com Configuracoes por cima. */
@Composable
fun IdeApp(viewModel: IdeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.editor.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissNotice()
        }
    }

    // Instalacao automatica depois de um build "Executar" bem-sucedido.
    LaunchedEffect(state.build) {
        val build = state.build
        if (build is BuildPanelState.Done && build.autoInstall && build.outcome.apk != null) {
            viewModel.consumeInstall()
            installApk(context, build.outcome.apk)
        }
    }

    when {
        showSettings -> {
            BackHandler { showSettings = false }
            IdeSettingsScreen(settings = settings, onChange = viewModel.settings::update, onBack = { showSettings = false })
        }
        state.project == null -> IdeHomeScreen(
            state = state,
            onOpen = { viewModel.openProject(it.dir) },
            onDelete = viewModel::deleteProject,
            onCreate = viewModel::createProject,
            suggestPackage = viewModel::suggestPackage,
            onSettings = { showSettings = true },
        )
        else -> {
            BackHandler { viewModel.closeProject() }
            IdeWorkspace(viewModel = viewModel, state = state, settings = settings, onSettings = { showSettings = true })
        }
    }
}
