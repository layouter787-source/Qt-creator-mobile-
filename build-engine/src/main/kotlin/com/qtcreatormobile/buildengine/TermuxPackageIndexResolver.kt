package com.qtcreatormobile.buildengine

/**
 * Resolve a URL de download de pacotes .deb dentro do repositorio real do
 * Termux (formato apt padrao), incluindo suas DEPENDENCIAS - sem isso, baixar
 * so o `.deb` do `clang` nao e suficiente: ele depende de `libllvm`, `libc++`
 * etc., e sem essas bibliotecas o binario nao roda.
 */
class TermuxPackageIndexResolver(
    private val textFetcher: TextFetcher = HttpTextFetcher(),
) {
    /** Resolve so a URL de um pacote, sem as dependencias. */
    fun resolveDownloadUrl(repoBaseUrl: String, packageName: String, arch: String = "aarch64"): String {
        val index = fetchIndex(repoBaseUrl, arch)
        val pkg = index[packageName]
            ?: throw BuildEngineException("Pacote '$packageName' nao encontrado no indice de $repoBaseUrl.")
        return "$repoBaseUrl/${pkg.filename}"
    }

    /**
     * Resolve [packageName] e toda a arvore de dependencias dele, na ordem
     * certa de instalacao (dependencias primeiro). Pacotes citados em `Depends`
     * que nao existem no indice entram em [DependencyClosure.unresolved].
     */
    fun resolveDependencyClosure(repoBaseUrl: String, packageName: String, arch: String = "aarch64"): DependencyClosure {
        val index = fetchIndex(repoBaseUrl, arch)
        val visited = LinkedHashSet<String>()
        val unresolved = mutableSetOf<String>()

        fun visit(name: String) {
            if (name in visited) return
            val pkg = index[name] ?: run { unresolved += name; return }
            visited += name
            for (group in pkg.depends) {
                val chosen = group.firstOrNull { it in index }
                if (chosen != null) visit(chosen) else unresolved += group.first()
            }
        }
        visit(packageName)

        val ordered = visited.sortedBy { name -> dependencyDepth(name, index) }
        val packages = ordered.map { name -> ResolvedPackage(name, "$repoBaseUrl/${index.getValue(name).filename}") }
        return DependencyClosure(packages, unresolved)
    }

    /** Profundidade = 0 para folhas, maior para quem depende de mais coisas. */
    private fun dependencyDepth(name: String, index: Map<String, AptPackage>, seen: Set<String> = emptySet()): Int {
        if (name in seen) return 0
        val pkg = index[name] ?: return 0
        val childDepths = pkg.depends.mapNotNull { group -> group.firstOrNull { it in index } }
            .map { dependencyDepth(it, index, seen + name) }
        return 1 + (childDepths.maxOrNull() ?: -1)
    }

    private fun fetchIndex(repoBaseUrl: String, arch: String): Map<String, AptPackage> {
        val indexUrl = "$repoBaseUrl/dists/stable/main/binary-$arch/Packages"
        return AptIndexParser.parse(textFetcher.fetch(indexUrl))
    }
}
