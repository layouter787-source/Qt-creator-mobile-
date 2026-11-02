package com.qtcreatormobile.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtcreatormobile.app.home.HomeScreen
import com.qtcreatormobile.app.build.ProjectBuildViewModel
import com.qtcreatormobile.app.toolchain.MandatoryCppSetupOverlay
import com.qtcreatormobile.app.toolchain.ToolchainUiState
import com.qtcreatormobile.app.toolchain.ToolchainViewModel
import com.qtcreatormobile.projectmodel.Language

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        toolchainViewModel.resumeFromBackground()
    }

    private val toolchainViewModel: ToolchainViewModel by viewModels()
    private val projectBuildViewModel: ProjectBuildViewModel by viewModels()

    private fun installBuiltApk(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", apk)
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7403)
        }
        setContent {
            val cppState by toolchainViewModel.state(Language.CPP).collectAsStateWithLifecycle()
            val kotlinState by toolchainViewModel.state(Language.KOTLIN).collectAsStateWithLifecycle()
            val buildState by projectBuildViewModel.state.collectAsStateWithLifecycle()

            // C/C++ e a linguagem de identidade do QT Creator Mobile: o setup inteiro
            // (resolver versoes, baixar, extrair, corrigir caminhos, integrar) acontece
            // sozinho, sem perguntar nada - o usuario so espera a barra de progresso.
            LaunchedEffect(Unit) {
                if (cppState is ToolchainUiState.NotInstalled) {
                    toolchainViewModel.startCppSetup()
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (cppState is ToolchainUiState.Installed) {
                        // Setup concluido (agora ou em uma abertura anterior) -> tela inicial.
                        HomeScreen(
                            projectsRoot = File(filesDir, "projects").apply { mkdirs() },
                            kotlinState = kotlinState,
                            onInstallKotlin = { toolchainViewModel.startKotlinSetup() },
                            onPauseKotlin = { toolchainViewModel.toggleManualPause() },
                            buildState = buildState,
                            onBuild = { projectBuildViewModel.build(it) },
                            onInstallApk = { apk -> installBuiltApk(apk) },
                        )
                    } else {
                        MandatoryCppSetupOverlay(
                            state = cppState,
                            onRetryClick = { toolchainViewModel.startCppSetup() },
                            onPauseClick = { toolchainViewModel.toggleManualPause() },
                        )
                    }
                }
            }
        }
    }
}
