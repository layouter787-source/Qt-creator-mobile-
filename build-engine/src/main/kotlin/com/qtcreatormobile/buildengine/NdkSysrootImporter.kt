package com.qtcreatormobile.buildengine

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Resultado de uma importacao do NDK. [ndkRevision] vem de `source.properties` (ex.: "27.1.12297006"). */
data class NdkImportResult(
    val ndkRevision: String?,
    val filesExtracted: Int,
    val bytesExtracted: Long,
)

/**
 * Importa o `sysroot` (cabecalhos + bibliotecas de destino do Android) de um zip do NDK que o
 * USUARIO baixou manualmente - o NDK pesa ~700 MB e nao e baixado pelo app.
 *
 * Qualquer versao do NDK serve, e qualquer sistema hospedeiro (Linux, Windows): o sysroot e o mesmo,
 * pois so ele e usado; o compilador que roda no aparelho e o clang do Termux. So e aceito o caminho
 * `<pasta-do-ndk>/toolchains/llvm/prebuilt/<host>/sysroot/...`.
 *
 * Seguranca: entradas com `..` (zip-slip) abortam a importacao. A extracao e feita numa pasta
 * temporaria e so substitui o sysroot atual depois de validada, entao uma importacao interrompida
 * ou invalida nunca deixa um sysroot pela metade.
 *
 * @param keptTriples arquiteturas de destino mantidas (as demais sao descartadas para poupar espaco
 *   e tempo). Por padrao arm64 e arm 32-bits, que cobrem os aparelhos Android reais.
 */
class NdkSysrootImporter(
    private val keptTriples: Set<String> = DEFAULT_TRIPLES,
) {

    /**
     * Importa de um arquivo no disco usando acesso aleatorio ao zip: le o indice e descompacta SO
     * as entradas necessarias (rapido). [progress] recebe 0..1.
     */
    fun importFromFile(
        zipFile: File,
        sysrootDir: File,
        shouldStop: () -> Boolean = { false },
        progress: (Float) -> Unit = {},
    ): NdkImportResult {
        val staging = prepareStaging(sysrootDir)
        try {
            val result = ZipFile(zipFile).use { zip ->
                val wanted = zip.entries().asSequence()
                    .mapNotNull { entry -> relativeSysrootPath(entry.name)?.let { entry to it } }
                    .toList()
                val total = wanted.sumOf { (entry, _) -> maxOf(entry.size, 0L) }.coerceAtLeast(1L)
                var done = 0L
                var files = 0
                for ((entry, relative) in wanted) {
                    if (shouldStop()) throw CancellationException("Importacao do NDK cancelada.")
                    if (entry.isDirectory) {
                        resolveSafely(staging, relative).mkdirs()
                        continue
                    }
                    zip.getInputStream(entry).use { input ->
                        done += writeEntry(input, resolveSafely(staging, relative)) { chunk ->
                            progress(((done + chunk).toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    files++
                    progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
                val revision = zip.entries().asSequence()
                    .firstOrNull { SOURCE_PROPERTIES.matches(it.name) }
                    ?.let { parseRevision(zip.getInputStream(it).bufferedReader().readText()) }
                NdkImportResult(revision, files, done)
            }
            return finish(staging, sysrootDir, result)
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    /**
     * Importa lendo o zip em sequencia (para quando so ha um fluxo, ex.: um `content://` do seletor
     * de arquivos). Mais lento: precisa percorrer o zip inteiro. [totalBytes] e o tamanho do arquivo
     * (ou <= 0 se desconhecido) e serve so para o progresso.
     */
    fun importFromStream(
        input: InputStream,
        totalBytes: Long,
        sysrootDir: File,
        shouldStop: () -> Boolean = { false },
        progress: (Float) -> Unit = {},
    ): NdkImportResult {
        val staging = prepareStaging(sysrootDir)
        try {
            val counting = CountingInputStream(input)
            var revision: String? = null
            var files = 0
            var extracted = 0L
            ZipInputStream(counting.buffered(BUFFER)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (shouldStop()) throw CancellationException("Importacao do NDK cancelada.")
                    val name = entry.name
                    val relative = relativeSysrootPath(name)
                    if (relative != null) {
                        if (entry.isDirectory) {
                            resolveSafely(staging, relative).mkdirs()
                        } else {
                            extracted += writeEntry(zip, resolveSafely(staging, relative)) { }
                            files++
                        }
                    } else if (SOURCE_PROPERTIES.matches(name)) {
                        revision = parseRevision(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    if (totalBytes > 0) progress((counting.count.toFloat() / totalBytes).coerceIn(0f, 1f))
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            progress(1f)
            return finish(staging, sysrootDir, NdkImportResult(revision, files, extracted))
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    /**
     * Caminho relativo DENTRO do sysroot (ex.: "usr/include/stdio.h") para uma entrada do zip, ou
     * null se a entrada nao deve ser extraida (nao e do sysroot, ou e de outra arquitetura).
     */
    internal fun relativeSysrootPath(entryName: String): String? {
        val relative = SYSROOT_ENTRY.matchEntire(entryName.replace('\\', '/'))?.groupValues?.get(1)
            ?: return null
        for (triple in ALL_TRIPLES - keptTriples) {
            if (relative.startsWith("usr/lib/$triple/") || relative.startsWith("usr/include/$triple/")) return null
        }
        return relative
    }

    private fun prepareStaging(sysrootDir: File): File {
        val parent = sysrootDir.absoluteFile.parentFile ?: throw IOException("Pasta de destino invalida: $sysrootDir")
        parent.mkdirs()
        val staging = File(parent, sysrootDir.name + ".importing")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Nao foi possivel criar a pasta temporaria ${staging.path}")
        return staging
    }

    /** Valida o que foi extraido e troca o sysroot antigo pelo novo. */
    private fun finish(staging: File, sysrootDir: File, result: NdkImportResult): NdkImportResult {
        if (result.filesExtracted == 0) {
            throw BuildEngineException(
                "Este arquivo nao parece ser o NDK do Android: nao ha toolchains/llvm/prebuilt/*/sysroot dentro dele. " +
                    "Baixe o \"NDK para Linux\" (.zip) em developer.android.com/ndk/downloads."
            )
        }
        val missing = REQUIRED_FILES.filterNot { File(staging, it).isFile }
        val hasArm64Libc = File(staging, "usr/lib/aarch64-linux-android").listFiles()
            ?.any { api -> api.isDirectory && File(api, "libc.so").isFile } == true
        if (missing.isNotEmpty() || !hasArm64Libc) {
            val what = missing + if (hasArm64Libc) emptyList() else listOf("usr/lib/aarch64-linux-android/<api>/libc.so")
            throw BuildEngineException(
                "O sysroot do arquivo esta incompleto (faltam: ${what.joinToString()}). " +
                    "O download do NDK pode ter ficado corrompido; baixe-o novamente."
            )
        }
        result.ndkRevision?.let { File(staging, VERSION_FILE).writeText(it) }
        sysrootDir.deleteRecursively()
        if (!staging.renameTo(sysrootDir)) {
            // rename pode falhar entre sistemas de arquivos diferentes; copiar e apagar resolve.
            staging.copyRecursively(sysrootDir, overwrite = true)
            staging.deleteRecursively()
        }
        return result
    }

    /** Resolve [relative] dentro de [root] recusando qualquer caminho que escape dele (zip-slip). */
    private fun resolveSafely(root: File, relative: String): File {
        val target = File(root, relative)
        val rootPath = root.canonicalFile.toPath()
        if (!target.canonicalFile.toPath().startsWith(rootPath)) {
            throw BuildEngineException("Entrada de zip insegura recusada: $relative")
        }
        return target
    }

    /** Copia [input] para [target] e devolve os bytes escritos; [onChunk] recebe o total parcial. */
    private fun writeEntry(input: InputStream, target: File, onChunk: (Long) -> Unit): Long {
        target.parentFile?.mkdirs()
        var written = 0L
        target.outputStream().buffered(BUFFER).use { output ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                written += read
                onChunk(written)
            }
        }
        return written
    }

    private fun parseRevision(sourceProperties: String): String? =
        sourceProperties.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Pkg.Revision") }
            ?.substringAfter('=')?.trim()?.ifEmpty { null }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }
    }

    companion object {
        /** arm64 e arm 32-bits: os aparelhos Android reais. x86/x86_64/riscv64 sao descartados. */
        val DEFAULT_TRIPLES = setOf("aarch64-linux-android", "arm-linux-androideabi")
        private val ALL_TRIPLES = setOf(
            "aarch64-linux-android",
            "arm-linux-androideabi",
            "i686-linux-android",
            "x86_64-linux-android",
            "riscv64-linux-android",
        )

        /** `[pasta-do-ndk/]toolchains/llvm/prebuilt/<host>/sysroot/<resto>` - qualquer versao e host. */
        private val SYSROOT_ENTRY = Regex("""^(?:[^/]+/)?toolchains/llvm/prebuilt/[^/]+/sysroot/(.+)$""")
        private val SOURCE_PROPERTIES = Regex("""^(?:[^/]+/)?source\.properties$""")

        /** Arquivos sem os quais nenhum build C/C++ funciona (checados por ProjectBuildViewModel tambem). */
        private val REQUIRED_FILES = listOf("usr/include/stdio.h", "usr/include/android/native_activity.h")

        const val VERSION_FILE = "NDK_VERSION"
        private const val BUFFER = 64 * 1024
    }
}
