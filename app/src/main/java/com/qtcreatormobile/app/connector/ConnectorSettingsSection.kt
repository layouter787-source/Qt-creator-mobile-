package com.qtcreatormobile.app.connector

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun ConnectorSettingsSection() {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("qtcm-android-device") }
    var allowChanges by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Ainda não ligado") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("AI Connector", style = MaterialTheme.typography.titleLarge)
            Text(
                "Liga esta IDE a um servidor cloud MCP. O telemóvel inicia uma ligação segura de saída.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloud WebSocket URL (wss://)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Device token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Device ID") },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {\n                Checkbox(checked = allowChanges, onCheckedChange = { allowChanges = it })\n                Text("Permitir à IA editar ficheiros e iniciar builds")\n            }\n            Text("Desativado por padrão: a IA só pode consultar projetos.", style = MaterialTheme.typography.bodySmall)\n            Text(message, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    val intent = Intent(context, QtcmConnectorService::class.java).apply {
                        putExtra(QtcmConnectorService.EXTRA_ENDPOINT, endpoint.trim())
                        putExtra(QtcmConnectorService.EXTRA_TOKEN, token.trim())
                        putExtra(QtcmConnectorService.EXTRA_DEVICE_ID, deviceId.trim())
                        putExtra(QtcmConnectorService.EXTRA_ALLOW_CHANGES, allowChanges)
                    }
                    ContextCompat.startForegroundService(context, intent)
                    token = ""
                    message = "Pedido de ligação enviado. Consulta a notificação para ver o estado."
                },
                enabled = endpoint.startsWith("wss://") && token.length >= 32 && deviceId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ligar à cloud")
            }
            TextButton(
                onClick = {
                    context.startService(Intent(context, QtcmConnectorService::class.java).apply {
                        action = QtcmConnectorService.ACTION_STOP
                    })
                    message = "Pedido para desligar enviado."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Desligar conector")
            }
        }
    }
}
