package com.qtcreatormobile.app.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tela inicial do QT Creator Mobile - para onde o usuario e levado assim que o
 * setup do toolchain termina (ou direto, se ja estava instalado).
 *
 * Ainda e um placeholder: aqui entra a navegacao real (lista de projetos,
 * criar novo projeto, editor de codigo) e o botao de build.
 */
@Composable
fun HomeScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("QT Creator Mobile")
        Text("Ambiente pronto. Lista de projetos entra aqui.")
    }
}
