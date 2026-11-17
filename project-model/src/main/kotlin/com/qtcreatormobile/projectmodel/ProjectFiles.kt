package com.qtcreatormobile.projectmodel

import java.io.File

/** Utilitarios de arquivos de um projeto: arvore, nomes validos, deteccao de texto e busca. */
object ProjectFiles {
    const val MAX_EDITABLE_BYTES: Long = 2L * 1024 * 1024

    private val ALWAYS_HIDDEN = setOf(".git", ".gradle", ".idea", ".DS_Store")

    /** Uma linha da arvore de arquivos. [depth] 0 = filho direto da raiz. */
    data class Entry(val file: File, val depth: Int, val isDirectory: Boolean)

    data class SearchHit(val file: File, val line: Int, val text: String)

    /** Nome valido para criar/renomear um arquivo ou pasta (sem separadores nem "."/".."). */
    fun isValidEntryName(name: String): Boolean =
        name.isNotBlank() && name.length <= 100 && name != "." && name != ".." &&
            '/' !in name && '\\' !in name && '\u0000' !in name

    /** True se [file] e um arquivo de texto pequeno o bastante para o editor (sem bytes nulos). */
    fun isEditableText(file: File): Boolean {
        if (!file.isFile || file.length() > MAX_EDITABLE_BYTES) return false
        return runCatching {
            file.inputStream().use { input ->
                val head = ByteArray(8000)
                val read = input.read(head)
                (0 until maxOf(read, 0)).none { head[it] == 0.toByte() }
            }
        }.getOrDefault(false)
    }

    private fun visibleChildren(dir: File, showBuild: Boolean): List<File> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.name !in ALWAYS_HIDDEN && (showBuild || !(it.isDirectory && it.name == "build")) }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))

    /** Lista a arvore como linhas planas; so desce nas pastas de [expanded] (caminhos absolutos). */
    fun visibleTree(root: File, expanded: Set<String>, showBuild: Boolean = false): List<Entry> {
        val rows = mutableListOf<Entry>()
        fun walk(dir: File, depth: Int) {
            for (child in visibleChildren(dir, showBuild)) {
                rows += Entry(child, depth, child.isDirectory)
                if (child.isDirectory && child.absolutePath in expanded) walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return rows
    }

    /** Todos os arquivos editaveis do projeto (para "ir para arquivo" e busca), sem a pasta build. */
    fun allTextFiles(root: File, limit: Int = 5000): List<File> =
        root.walkTopDown()
            .onEnter { it.name !in ALWAYS_HIDDEN && !(it != root && it.name == "build") }
            .filter { it.isFile && isEditableText(it) }
            .take(limit)
            .toList()

    /** Busca [query] nos arquivos de texto do projeto. [line] e 1-based. */
    fun search(root: File, query: String, ignoreCase: Boolean = true, maxHits: Int = 300): List<SearchHit> {
        if (query.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (file in allTextFiles(root)) {
            val lines = runCatching { file.readLines() }.getOrNull() ?: continue
            for ((index, text) in lines.withIndex()) {
                if (text.contains(query, ignoreCase)) {
                    hits += SearchHit(file, index + 1, text.trim().take(200))
                    if (hits.size >= maxHits) return hits
                }
            }
        }
        return hits
    }

    /** Caminho de [file] relativo a [root], sempre com '/'. */
    fun relativePath(root: File, file: File): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')
}
