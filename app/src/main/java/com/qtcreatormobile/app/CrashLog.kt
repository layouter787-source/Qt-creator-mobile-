package com.qtcreatormobile.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Application: instala o registrador de falhas antes de qualquer tela abrir. */
class QtcmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

/**
 * Guarda o erro exato de qualquer falha do app em arquivo, para aparecer na proxima abertura
 * (tela de relatorio) e em Mais > Logs. Tambem le o motivo das ultimas saidas do processo
 * (crash nativo, falta de memoria, ANR...) no Android 11+.
 */
object CrashLog {
    private fun dir(context: Context) = File(context.filesDir, "logs").apply { mkdirs() }
    private fun lastFile(context: Context) = File(dir(context), "crash-last.txt")
    private fun previousFile(context: Context) = File(dir(context), "crash-previous.txt")
    private fun eventsFile(context: Context) = File(dir(context), "events.txt")

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = buildString {
                    appendLine("Falha em ${stamp()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} - Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Ultimos eventos:")
                    appendLine(recentEvents(app, 12))
                    appendLine()
                    append(Log.getStackTraceString(error))
                }
                lastFile(app).writeText(report)
            } catch (_: Throwable) {
                // nunca atrapalhar o encerramento normal
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Marca um passo importante (abrir projeto, iniciar editor...) para dar contexto ao erro. */
    fun event(context: Context, message: String) {
        try {
            val file = eventsFile(context)
            if (file.length() > 32_000) file.writeText(file.readText().takeLast(8_000))
            file.appendText("${stamp()}  $message\n")
        } catch (_: Throwable) {
        }
    }

    private fun recentEvents(context: Context, count: Int): String =
        try {
            eventsFile(context).readLines().takeLast(count).joinToString("\n").ifBlank { "(nenhum)" }
        } catch (_: Throwable) {
            "(nenhum)"
        }

    /** Relatorio da falha da execucao anterior, ainda nao visto pelo usuario. */
    fun pending(context: Context): String? =
        try {
            lastFile(context).takeIf { it.isFile }?.readText()
        } catch (_: Throwable) {
            null
        }

    /** Marca como visto (continua disponivel em Logs). */
    fun dismiss(context: Context) {
        try {
            val last = lastFile(context)
            if (last.isFile) {
                previousFile(context).writeText(last.readText())
                last.delete()
            }
        } catch (_: Throwable) {
        }
    }

    fun previous(context: Context): String? =
        try {
            previousFile(context).takeIf { it.isFile }?.readText() ?: pending(context)
        } catch (_: Throwable) {
            null
        }

    fun events(context: Context): String = recentEvents(context, 60)

    private fun reasonName(reason: Int): String = when (reason) {
        1 -> "saiu sozinho (EXIT_SELF)"
        2 -> "morto por sinal (SIGNALED)"
        3 -> "falta de memoria (LOW_MEMORY)"
        4 -> "falha Java (CRASH)"
        5 -> "falha nativa (CRASH_NATIVE)"
        6 -> "app travado (ANR)"
        7 -> "falha ao iniciar"
        8 -> "permissao alterada"
        9 -> "uso excessivo de recursos"
        10 -> "fechado pelo usuario"
        11 -> "parado pelo usuario"
        12 -> "dependencia encerrada"
        14 -> "congelado pelo sistema"
        else -> "outro motivo ($reason)"
    }

    /** Motivo das ultimas saidas do processo (so existe no Android 11+). */
    fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < 30) return "Disponivel apenas no Android 11 ou mais novo."
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val list = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            if (list.isEmpty()) {
                "Nenhuma saida registrada."
            } else {
                list.joinToString("\n\n") {
                    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it.timestamp))
                    "$time - ${reasonName(it.reason)}\n${it.description.orEmpty()}".trim()
                }
            }
        } catch (e: Throwable) {
            "Nao foi possivel ler: ${e.message}"
        }
    }
}
