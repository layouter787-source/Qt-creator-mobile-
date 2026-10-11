package com.qtcreatormobile.buildengine

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Adiciona arquivos extras (bibliotecas .so, classes.dex) a um APK ja
 * existente (a saida do `aapt2 link`), que ja e um .zip valido.
 */
object ApkAssembler {

    /**
     * @param entries mapa de "caminho dentro do apk" (ex.: "lib/arm64-v8a/libapp.so",
     *   "classes.dex") para o arquivo real no disco que deve ser copiado para la.
     */
    fun addEntries(apkPath: String, entries: Map<String, String>) {
        val originalApk = File(apkPath)
        val tempApk = File(apkPath + ".tmp")

        ZipFile(originalApk).use { zipIn ->
            ZipOutputStream(tempApk.outputStream()).use { zipOut ->
                val zipEntries = zipIn.entries()
                while (zipEntries.hasMoreElements()) {
                    val entry = zipEntries.nextElement()
                    zipOut.putNextEntry(ZipEntry(entry.name))
                    zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }

                for ((entryPath, sourceFilePath) in entries) {
                    zipOut.putNextEntry(ZipEntry(entryPath))
                    File(sourceFilePath).inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }

        if (!originalApk.delete() || !tempApk.renameTo(originalApk)) {
            throw BuildEngineException("Falha ao substituir o APK original em $apkPath pela versao atualizada.")
        }
    }
}
