package com.qtcreatormobile.buildengine

/**
 * Um pacote de um repositorio apt. [depends] e uma lista de grupos: cada grupo
 * e um conjunto de alternativas (`a | b` no formato Debian) - basta uma delas.
 */
data class AptPackage(
    val name: String,
    val filename: String,
    val depends: List<List<String>>,
)

/** Le o arquivo `Packages` de um repositorio apt/Debian (blocos `Campo: valor` separados por linha em branco). */
object AptIndexParser {

    fun parse(text: String): Map<String, AptPackage> {
        val packages = LinkedHashMap<String, AptPackage>()
        for (stanza in text.replace("\r\n", "\n").split(Regex("\n[ \t]*\n"))) {
            val fields = parseFields(stanza)
            val name = fields["Package"] ?: continue
            val filename = fields["Filename"] ?: continue
            packages[name] = AptPackage(name, filename, parseDepends(fields["Depends"]))
        }
        return packages
    }

    /** "libllvm (= 1.0), libc++, lld | ld.lld" -> [[libllvm], [libc++], [lld, ld.lld]] */
    fun parseDepends(raw: String?): List<List<String>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .map { group ->
                group.split("|")
                    .map { alternative ->
                        alternative.trim().substringBefore('(').trim().substringBefore(' ').substringBefore(':')
                    }
                    .filter { it.isNotEmpty() }
            }
            .filter { it.isNotEmpty() }
    }

    private fun parseFields(stanza: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        for (line in stanza.lines()) {
            // Linhas de continuacao (ex.: Description em varias linhas) comecam com espaco/tab.
            if (line.isEmpty() || line[0] == ' ' || line[0] == '\t') continue
            val separatorIndex = line.indexOf(':')
            if (separatorIndex == -1) continue
            fields[line.substring(0, separatorIndex).trim()] = line.substring(separatorIndex + 1).trim()
        }
        return fields
    }
}

data class ResolvedPackage(val name: String, val downloadUrl: String)

/**
 * Resultado de resolver um pacote e todas as suas dependencias: [packages] ja
 * na ordem certa de instalacao (dependencias antes de quem depende delas);
 * [unresolved] sao dependencias que nao existem no indice.
 */
data class DependencyClosure(
    val packages: List<ResolvedPackage>,
    val unresolved: Set<String>,
)
