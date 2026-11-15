package com.qtcreatormobile.app.connector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.qtcreatormobile.buildengine.BuildPipelineFactory
import com.qtcreatormobile.buildengine.RealProcessRunner
import com.qtcreatormobile.buildengine.ToolchainPaths
import com.qtcreatormobile.projectmodel.ProjectModelLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Outbound-only agent. Exposes bounded project operations, never arbitrary shell execution. */
class QtcmConnectorService : Service() {
    private var socket: WebSocket? = null
    private var client: OkHttpClient? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopConnector()
            stopSelf()
            return START_NOT_STICKY
        }
        val endpoint = intent?.getStringExtra(EXTRA_ENDPOINT)?.trim()?.trimEnd('/')
        val token = intent?.getStringExtra(EXTRA_TOKEN)?.trim()
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID)?.trim().orEmpty()
        if (endpoint.isNullOrBlank() || !endpoint.startsWith("wss://") ||
            token.isNullOrBlank() || token.length < 32 || deviceId.isBlank()
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("A ligar à cloud…"))
        connect(endpoint, token, deviceId)
        return START_STICKY
    }

    private fun connect(endpoint: String, token: String, deviceId: String) {
        stopConnector()
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
        client = http
        val request = Request.Builder()
            .url(endpoint + "/agent")
            .header("Authorization", "Bearer " + token)
            .header("X-Qtcm-Device", deviceId)
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                updateNotification("Conectado à cloud · " + deviceId)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                Thread {
                    val reply = runCatching { handleCommand(JSONObject(text)) }.getOrElse { error ->
                        val id = runCatching { JSONObject(text).optString("id") }.getOrDefault("")
                        JSONObject().put("type", "result").put("id", id)
                            .put("ok", false).put("error", error.message ?: "Falha no comando")
                    }
                    webSocket.send(reply.toString())
                }.start()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                updateNotification("Desconectado · " + (t.message ?: "erro de rede"))
            }
        })
    }

    private fun handleCommand(message: JSONObject): JSONObject {
        val id = message.optString("id")
        require(message.optString("type") == "command") { "Mensagem inválida" }
        val payload = message.optJSONObject("payload") ?: JSONObject()
        val result: Any = when (message.optString("command")) {
            "list_projects" -> listProjects()
            "read_file" -> readProjectFile(payload)
            "write_file" -> writeProjectFile(payload)
            "build_project" -> buildProject(payload)
            else -> error("Comando não permitido")
        }
        return JSONObject().put("type", "result").put("id", id)
            .put("ok", true).put("result", JSONObject.wrap(result))
    }

    private val projectsRoot: File get() = File(filesDir, "projects").canonicalFile

    private fun listProjects(): JSONArray {
        val projects = projectsRoot.listFiles()
            ?.filter { it.isDirectory && File(it, "qtcm.project.yaml").isFile }
            ?.sortedBy { it.name } ?: emptyList()
        return JSONArray().apply {
            projects.forEach { put(JSONObject().put("name", it.name)) }
        }
    }

    private fun projectDirectory(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,79}"))) { "Nome de projeto inválido" }
        val root = projectsRoot
        val project = File(root, name).canonicalFile
        require(project.path.startsWith(root.path + File.separator)) { "Projeto fora da área permitida" }
        require(File(project, "qtcm.project.yaml").isFile) { "Projeto não encontrado" }
        return project
    }

    private fun projectFile(payload: JSONObject, forWrite: Boolean): File {
        val project = projectDirectory(payload.getString("project"))
        val relative = payload.getString("path")
        require(relative.isNotBlank() && !relative.startsWith("/") && !relative.contains('\\')) {
            "Caminho relativo inválido"
        }
        val parts = relative.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) { "Caminho não permitido" }
        require(parts.firstOrNull() != ".git" && parts.firstOrNull() != "build") {
            "Pasta não disponível para operações remotas"
        }
        val file = File(project, relative).canonicalFile
        require(file.path.startsWith(project.path + File.separator)) { "Ficheiro fora do projeto" }
        if (!forWrite) require(file.isFile) { "Ficheiro não encontrado" }
        return file
    }

    private fun readProjectFile(payload: JSONObject): JSONObject {
        val file = projectFile(payload, false)
        require(file.length() <= MAX_TEXT_BYTES) { "Ficheiro demasiado grande" }
        return JSONObject().put("path", file.name).put("content", file.readText(Charsets.UTF_8))
    }

    private fun writeProjectFile(payload: JSONObject): JSONObject {
        val file = projectFile(payload, true)
        val content = payload.getString("content")
        val bytes = content.toByteArray(Charsets.UTF_8).size
        require(bytes <= MAX_TEXT_BYTES) { "Conteúdo demasiado grande" }
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        return JSONObject().put("written", true).put("bytes", bytes)
    }

    private fun buildProject(payload: JSONObject): JSONObject {
        val projectDir = projectDirectory(payload.getString("project"))
        val model = ProjectModelLoader.loadFromFile(File(projectDir, "qtcm.project.yaml"))
        val target = model.targets.firstOrNull() ?: error("Projeto sem target")
        val paths = ToolchainPaths(File(filesDir, "toolchain"))
        val buildDir = File(projectDir, "build")
        val pipeline = BuildPipelineFactory.create(paths, RealProcessRunner(), projectDir)
        val result = pipeline.build(
            target = target,
            manifestPath = File(projectDir, "AndroidManifest.xml").absolutePath,
            outputApkPath = File(buildDir, target.id + ".apk").absolutePath,
            buildDir = buildDir.absolutePath,
        )
        return JSONObject().put("succeeded", result.succeeded)
            .put("apkPath", result.outputApkPath ?: JSONObject.NULL)
            .put("steps", JSONArray().apply {
                result.steps.forEach { step ->
                    put(JSONObject().put("name", step.stepName)
                        .put("succeeded", step.succeeded).put("log", step.log.take(12000)))
                }
            })
    }

    private fun notification(message: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AI Connector", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setContentTitle("QT Creator Mobile · AI Connector")
            .setContentText(message).setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true).build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
    }

    private fun stopConnector() {
        socket?.close(1000, "Service stopped")
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }

    override fun onDestroy() {
        stopConnector()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.qtcreatormobile.app.connector.STOP"
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_DEVICE_ID = "device_id"
        private const val CHANNEL_ID = "qtcm_ai_connector"
        private const val NOTIFICATION_ID = 4307
        private const val MAX_TEXT_BYTES = 200_000
    }
}
