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
import com.qtcreatormobile.app.home.HomeScreen
import com.qtcreatormobile.app.toolchain.MandatoryCppSetupOverlay
import com.qtcreatormobile.app.toolchain.ToolchainUiState
import com.qtcreatormobile.app.toolchain.ToolchainViewModel
import com.qtcreatormobile.projectmodel.Language

class MainActivity : ComponentActivity() {

    private val toolchainViewModel: ToolchainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val cppState by toolchainViewModel.state(Language.CPP).collectAsStateWithLifecycle()

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
                        HomeScreen()
                    } else {
                        MandatoryCppSetupOverlay(
                            state = cppState,
                            onRetryClick = { toolchainViewModel.startCppSetup() },
                        )
                    }
                }
            }
        }
    }
}
