package com.qtcreatormobile.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtcreatormobile.app.build.BuildUiState

/**
 * Tela inicial do QT Creator Mobile: lista os projetos, permite criar o
 * projeto de exemplo e compilar, mostrando o resultado de CADA etapa do
 * pipeline (compilar, linkar, dex, aapt2, montar, assinar).
 */
@Composable
fun HomeScreen(
    state: BuildUiState,
    onCreateSample: () -> Unit,
    onBuild: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("QT Creator Mobile", style = MaterialTheme.typography.headlineSmall)
        Text("Ambiente pronto para compilar.")

        Button(onClick = onCreateSample, enabled = !state.running) {
            Text("Criar projeto de exemplo (C++)")
        }

        if (state.projects.isEmpty()) {
            Text("Nenhum projeto ainda.")
        } else {
            Text("Projetos", style = MaterialTheme.typography.titleMedium)
            state.projects.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, modifier = Modifier.weight(1f))
                    Button(onClick = { onBuild(name) }, enabled = !state.running) {
                        Text("Compilar")
                    }
                }
            }
        }

        if (state.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Compilando...")
        }

        state.message?.let { Text(it) }

        state.steps.forEach { step ->
            Text(
                text = (if (step.succeeded) "OK     " else "FALHOU ") + step.stepName,
                fontWeight = if (step.succeeded) FontWeight.Normal else FontWeight.Bold,
            )
            if (!step.succeeded && step.log.isNotBlank()) {
                Text(
                    text = step.log.take(1500),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        state.apkPath?.let { Text("APK gerado em:\n$it") }
    }
}
