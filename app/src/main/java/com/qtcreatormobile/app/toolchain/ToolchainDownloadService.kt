package com.qtcreatormobile.app.toolchain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.qtcreatormobile.app.MainActivity
import com.qtcreatormobile.app.R
import java.util.concurrent.atomic.AtomicLong

class ToolchainDownloadService : Service() {
    companion object {
        private const val CHANNEL_ID = "toolchain_downloads"
        private const val ONGOING_ID = 7401
        private const val RESULT_ID = 7402
        private const val ACTION_START = "com.qtcm.toolchain.START"
        private const val ACTION_PROGRESS = "com.qtcm.toolchain.PROGRESS"
        private const val ACTION_PAUSED = "com.qtcm.toolchain.PAUSED"
        private const val ACTION_SUCCESS = "com.qtcm.toolchain.SUCCESS"
        private const val ACTION_FAILURE = "com.qtcm.toolchain.FAILURE"
        private const val EXTRA_FRACTION = "fraction"
        private const val EXTRA_MESSAGE = "message"
        private val lastProgressUpdate = AtomicLong(0)

        fun start(context: Context) {
            val intent = Intent(context, ToolchainDownloadService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun progress(context: Context, fraction: Float) {
            val now = SystemClock.elapsedRealtime()
            val previous = lastProgressUpdate.get()
            if (now - previous < 900 || !lastProgressUpdate.compareAndSet(previous, now)) return
            context.startService(Intent(context, ToolchainDownloadService::class.java)
                .setAction(ACTION_PROGRESS).putExtra(EXTRA_FRACTION, fraction))
        }

        fun paused(context: Context, message: String) {
            context.startService(Intent(context, ToolchainDownloadService::class.java)
                .setAction(ACTION_PAUSED).putExtra(EXTRA_MESSAGE, message))
        }

        fun finish(context: Context, success: Boolean, message: String = "") {
            context.startService(Intent(context, ToolchainDownloadService::class.java)
                .setAction(if (success) ACTION_SUCCESS else ACTION_FAILURE).putExtra(EXTRA_MESSAGE, message))
        }
    }

    private lateinit var manager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads do toolchain", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Progresso da preparação dos compiladores" }
            )
        }
        startForeground(ONGOING_ID, buildProgressNotification(null, "Preparando compiladores…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PROGRESS -> {
                val fraction = intent.getFloatExtra(EXTRA_FRACTION, 0f).coerceIn(0f, 1f)
                manager.notify(ONGOING_ID, buildProgressNotification((fraction * 100).toInt(), "Baixando ferramentas de compilação…"))
            }
            ACTION_PAUSED -> {
                manager.notify(ONGOING_ID, buildProgressNotification(null, intent.getStringExtra(EXTRA_MESSAGE) ?: "Download pausado"))
            }
            ACTION_SUCCESS, ACTION_FAILURE -> {
                val success = intent.action == ACTION_SUCCESS
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                manager.notify(RESULT_ID, buildResultNotification(success, message))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return START_STICKY
    }

    private fun buildProgressNotification(percent: Int?, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("QT Creator Mobile")
            .setContentText(text)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun buildResultNotification(success: Boolean, detail: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (success) "Compiladores prontos" else "Falha ao preparar compiladores")
            .setContentText(if (success) "O QT Creator Mobile está pronto para compilar." else detail.take(180))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
