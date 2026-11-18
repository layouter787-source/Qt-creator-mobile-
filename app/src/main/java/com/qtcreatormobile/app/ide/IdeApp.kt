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
import com.qtcreatormobile.app.ai.AiProvidersScreen
import com.qtcreatormobile.app.ai.AiViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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

/** Raiz da IDE: Home -> Workspace, com Configuracoes (hub e subtelas) por cima. */
@Composable
fun IdeApp(viewModel: IdeViewModel, restoreLast: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editorSettings by viewModel.settings.editor.collectAsStateWithLifecycle()
    val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
    val keystoreViewModel: KeystoreViewModel = viewModel()
    val aiViewModel: AiViewModel = viewModel()
    val aiState by aiViewModel.state.collectAsStateWithLifecycle()
    // Rota de configuracoes: null = fechado; "hub" = menu; demais = subtelas.
    var route by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val store = KeystoreStore(context)
        viewModel.signingProvider = { store.selectedSpec() }
    }

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

    val current = route
    when {
        current != null -> {
            val back: () -> Unit = { route = if (current == "hub") null else "hub" }
            BackHandler(onBack = back)
            when (current) {
                "appearance" -> AppearanceScreen(appearance, viewModel.settings::updateAppearance, back)
                "editor" -> EditorSettingsScreen(editorSettings, viewModel.settings::update, back)
                "keystore" -> KeystoreScreen(keystoreViewModel, back)
                "ai" -> AiProvidersScreen(aiState, aiViewModel, back)
                "logs" -> LogsScreen((state.build as? BuildPanelState.Done)?.outcome?.fullLog, back)
                "about" -> AboutScreen(back)
                else -> SettingsHub(onNavigate = { route = it }, onBack = { route = null })
            }
        }
        state.project == null -> IdeHomeScreen(
            state = state,
            onOpen = { viewModel.openProject(it.dir) },
            onDelete = viewModel::deleteProject,
            onCreate = viewModel::createProject,
            suggestPackage = viewModel::suggestPackage,
            onSettings = { route = "hub" },
        )
        else -> {
            BackHandler { viewModel.closeProject() }
            IdeWorkspace(viewModel = viewModel, state = state, settings = editorSettings, onSettings = { route = "hub" })
        }
    }
}
