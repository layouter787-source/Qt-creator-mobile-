package com.qtcreatormobile.buildengine

/** Progresso de um download, para alimentar a barra de progresso na UI. */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
}

fun interface DownloadProgressListener {
    fun onProgress(progress: DownloadProgress)
}
