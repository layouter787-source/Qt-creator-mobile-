package com.qtcreatormobile.buildengine

import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Extrai um pacote `.deb` de verdade: um arquivo `ar` contendo um
 * `data.tar.xz` (ou `.gz`/`.zst`) com binarios, bibliotecas, etc.
 *
 * Detalhes reais dos pacotes do Termux:
 * - Tudo fica sob o prefixo fixo (`./data/data/com.termux/files/usr/...`).
 *   Esse prefixo de pasta e removido, para o conteudo cair em `<destino>/usr/...`.
 * - Muitos executaveis sao links simbolicos (ex.: `clang` -> `clang-21`);
 *   tratar como arquivo comum criaria um arquivo VAZIO no lugar do binario.
 * - Nenhuma entrada pode escapar do diretorio de destino (path traversal).
 */
object DebPackageExtractor {

    private const val TERMUX_ROOT_IN_DEB = "data/data/com.termux/files/"

    /**
     * @param symlinkPrefixRewrite se informado, alvos absolutos de links simbolicos que comecam
     *   com `first` (o prefixo do Termux) passam a comecar com `second` (o prefixo real no aparelho).
     */
    fun extract(debFile: File, targetDir: File, symlinkPrefixRewrite: Pair<String, String>? = null) {
        targetDir.mkdirs()

        ArArchiveInputStream(debFile.inputStream().buffered()).use { ar ->
            var entry = ar.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith("data.tar")) {
                    extractDataTar(ar, name, targetDir, symlinkPrefixRewrite)
                    return
                }
                entry = ar.nextEntry
            }
        }

        throw BuildEngineException("Pacote .deb '${debFile.path}' nao contem nenhuma entrada data.tar.*.")
    }

    /** Remove `./` inicial e o prefixo de pasta do Termux, se houver. Retorna "" para a raiz. */
    internal fun normalizeEntryName(name: String): String {
        var n = name.removePrefix("./").removePrefix("/")
        if (n.startsWith(TERMUX_ROOT_IN_DEB)) n = n.removePrefix(TERMUX_ROOT_IN_DEB)
        return n.trimEnd('/')
    }

    private fun extractDataTar(
        rawStream: InputStream,
        entryName: String,
        targetDir: File,
        symlinkPrefixRewrite: Pair<String, String>?,
    ) {
        val decompressed = when {
            entryName.endsWith(".xz") -> XZCompressorInputStream(rawStream)
            entryName.endsWith(".gz") -> GzipCompressorInputStream(rawStream)
            entryName.endsWith(".zst") -> ZstdCompressorInputStream(rawStream)
            entryName == "data.tar" -> rawStream
            else -> throw BuildEngineException("Formato de compressao desconhecido para '$entryName'.")
        }

        val rootCanonical = targetDir.canonicalPath
        val deferredHardLinks = mutableListOf<Pair<File, String>>()

        TarArchiveInputStream(decompressed).use { tar ->
            var tarEntry: TarArchiveEntry? = tar.nextEntry
            while (tarEntry != null) {
                val relative = normalizeEntryName(tarEntry.name)
                if (relative.isNotEmpty()) {
                    val outFile = File(targetDir, relative)
                    requireInside(rootCanonical, outFile, tarEntry.name)

                    when {
                        tarEntry.isDirectory -> outFile.mkdirs()
                        tarEntry.isSymbolicLink -> createSymlink(outFile, tarEntry.linkName, symlinkPrefixRewrite)
                        tarEntry.isLink -> deferredHardLinks += outFile to normalizeEntryName(tarEntry.linkName)
                        else -> {
                            outFile.parentFile?.mkdirs()
                            if (outFile.exists()) outFile.delete()
                            outFile.outputStream().use { tar.copyTo(it) }
                            if (tarEntry.mode and 0b001_000_000 != 0) outFile.setExecutable(true)
                        }
                    }
                }
                tarEntry = tar.nextEntry
            }
        }

        // Hardlinks apontam para outro arquivo do mesmo pacote; ja extraido a essa altura.
        for ((linkFile, targetRelative) in deferredHardLinks) {
            val source = File(targetDir, targetRelative)
            if (source.isFile) {
                linkFile.parentFile?.mkdirs()
                source.copyTo(linkFile, overwrite = true)
                if (source.canExecute()) linkFile.setExecutable(true)
            }
        }
    }

    private fun createSymlink(link: File, target: String, rewrite: Pair<String, String>?) {
        link.parentFile?.mkdirs()
        Files.deleteIfExists(link.toPath())
        val finalTarget = if (rewrite != null && target.startsWith(rewrite.first)) {
            rewrite.second + target.removePrefix(rewrite.first)
        } else {
            target
        }
        Files.createSymbolicLink(link.toPath(), Paths.get(finalTarget))
    }

    private fun requireInside(rootCanonical: String, file: File, originalName: String) {
        val parentCanonical = (file.parentFile ?: file).also { it.mkdirs() }.canonicalPath
        val inside = parentCanonical == rootCanonical || parentCanonical.startsWith(rootCanonical + File.separator)
        if (!inside) {
            throw BuildEngineException("Entrada '$originalName' tenta escrever fora do diretorio de destino.")
        }
    }
}
