package com.qtcreatormobile.buildengine

/**
 * Monta a linha de comando do clang para linkar varios .o num unico .so
 * (biblioteca compartilhada). clang serve como driver de link (invoca o lld).
 */
object CppLinkCommandBuilder {

    fun build(linkTask: LinkTask, objectFilePaths: List<String>, config: NdkConfig): List<String> {
        require(objectFilePaths.isNotEmpty()) {
            "LinkTask '${linkTask.id}' nao tem nenhum arquivo objeto para linkar."
        }

        return buildList {
            add(config.clangPath)
            add("--target=${NdkTarget.tripleFor(config)}")
            add("--sysroot=${config.sysrootPath}")
            add("-shared")
            add("-o")
            add(linkTask.outputPath)
            addAll(objectFilePaths)
            add("-llog")
        }
    }
}
