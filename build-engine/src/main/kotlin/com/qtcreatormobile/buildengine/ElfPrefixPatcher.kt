package com.qtcreatormobile.buildengine

import java.io.File
import java.io.RandomAccessFile

/**
 * Binarios do Termux (incluindo o clang que roda de verdade em Android arm64)
 * tem o caminho de instalacao do Termux (`/data/data/com.termux/files/usr`)
 * gravado como texto dentro dos proprios arquivos.
 *
 * Tecnica: substituir esse texto, no proprio arquivo, por um caminho do nosso
 * app **de exatamente o mesmo tamanho** (assim nenhum offset interno do
 * binario muda). Como o novo caminho e mais curto, o espaco que sobra e
 * preenchido com barras extras ('/'), que o Linux trata como uma barra so.
 *
 * IMPORTANTE: preencher com bytes nulos seria um erro grave - o texto gravado
 * costuma continuar depois do prefixo (`.../usr/lib/libfoo.so`), e um nulo no
 * meio TERMINARIA a string ali, cortando o resto do caminho.
 *
 * O arquivo e processado em blocos (nunca inteiro na memoria), porque
 * bibliotecas como libLLVM passam de 100 MB.
 */
object ElfPrefixPatcher {

    private const val CHUNK_SIZE = 1 shl 20 // 1 MB

    /** Devolve [newPrefix] alargado com barras ate ter o mesmo tamanho de [oldPrefix]. */
    fun padToLength(oldPrefix: String, newPrefix: String): String {
        require(newPrefix.startsWith("/")) { "O novo prefixo precisa ser um caminho absoluto: '$newPrefix'" }
        require(newPrefix.length <= oldPrefix.length) {
            "O novo prefixo ('$newPrefix', ${newPrefix.length} caracteres) precisa caber no espaco do antigo " +
                "('$oldPrefix', ${oldPrefix.length} caracteres) - senao os offsets internos do binario quebram."
        }
        val missing = oldPrefix.length - newPrefix.length
        if (missing == 0) return newPrefix

        val lastSlash = newPrefix.lastIndexOf('/')
        require(lastSlash > 0) { "Caminho curto demais para ser alargado com seguranca: '$newPrefix'" }
        return newPrefix.substring(0, lastSlash) + "/".repeat(missing) + newPrefix.substring(lastSlash)
    }

    /** Substitui toda ocorrencia de [oldPrefix] em [file] por [newPrefix] alargado. Retorna quantas. */
    fun patch(file: File, oldPrefix: String, newPrefix: String): Int {
        val oldBytes = oldPrefix.toByteArray(Charsets.US_ASCII)
        val newBytes = padToLength(oldPrefix, newPrefix).toByteArray(Charsets.US_ASCII)
        check(newBytes.size == oldBytes.size)

        var occurrences = 0
        RandomAccessFile(file, "rw").use { raf ->
            val length = raf.length()
            val overlap = oldBytes.size - 1
            val buffer = ByteArray(CHUNK_SIZE + overlap)
            var position = 0L

            while (position < length) {
                raf.seek(position)
                val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), length - position).toInt())
                if (read <= 0) break

                val isLastChunk = position + read >= length
                // Ultimo indice onde uma ocorrencia pode COMECAR neste bloco. Nos blocos
                // intermediarios so vale comecar em [0, CHUNK_SIZE - 1]: o que comeca depois
                // pertence ao proximo bloco e seria contado duas vezes.
                val lastStart = if (isLastChunk) read - oldBytes.size else minOf(CHUNK_SIZE - 1, read - oldBytes.size)

                var modified = false
                var i = 0
                while (i <= lastStart) {
                    if (matchesAt(buffer, i, oldBytes)) {
                        System.arraycopy(newBytes, 0, buffer, i, newBytes.size)
                        occurrences++
                        modified = true
                        i += oldBytes.size
                    } else {
                        i++
                    }
                }

                if (modified) {
                    raf.seek(position)
                    raf.write(buffer, 0, read)
                }

                if (isLastChunk) break
                position += CHUNK_SIZE
            }
        }
        return occurrences
    }

    private fun matchesAt(buffer: ByteArray, index: Int, sequence: ByteArray): Boolean {
        for (j in sequence.indices) {
            if (buffer[index + j] != sequence[j]) return false
        }
        return true
    }
}
