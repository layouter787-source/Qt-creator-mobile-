package com.qtcreatormobile.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtcreatormobile.app.build.BuildViewModel
import com.qtcreatormobile.app.home.HomeScreen
import com.qtcreatormobile.app.ndk.NdkImportScreen
import com.qtcreatormobile.app.ndk.NdkImportViewModel
import com.qtcreatormobile.app.toolchain.MandatoryCppSetupOverlay
import com.qtcreatormobile.app.toolchain.ToolchainUiState
import com.qtcreatormobile.app.toolchain.ToolchainViewModel
import com.qtcreatormobile.projectmodel.Language

class MainActivity : ComponentActivity() {

    private val toolchainViewModel: ToolchainViewModel by viewModels()
    private val buildViewModel: BuildViewModel by viewModels()
    private val ndkImportViewModel: NdkImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val cppState by toolchainViewModel.state(Language.CPP).collectAsStateWithLifecycle()
            val buildState by buildViewModel.state.collectAsStateWithLifecycle()
            val ndkState by ndkImportViewModel.state.collectAsStateWithLifecycle()

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
                        // Setup automatico concluido. O NDK (~700 MB) e baixado pelo usuario e importado
                        // aqui; enquanto faltar (ou a mensagem de sucesso estiver na tela), mostra a importacao.
                        if (ndkState.ready && ndkState.successMessage == null) {
                            HomeScreen(
                                state = buildState,
                                onCreateSample = buildViewModel::createSampleProject,
                                onBuild = buildViewModel::build,
                            )
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
                }
            }
        }
    }
}
