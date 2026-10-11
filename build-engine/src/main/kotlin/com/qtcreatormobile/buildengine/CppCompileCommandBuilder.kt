package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language

/**
 * Monta a linha de comando do clang para compilar um unico arquivo .c/.cpp em .o,
 * sem executar nada - pura montagem de argumentos, testavel sem um clang de verdade.
 * Aceita tanto C quanto C++: clang compila ambos.
 */
object CppCompileCommandBuilder {

    fun build(task: CompileTask, config: NdkConfig): List<String> {
        require(task.language == Language.CPP || task.language == Language.C) {
            "CppCompileCommandBuilder so aceita tarefas C ou CPP, recebeu: ${task.language}"
        }

        return buildList {
            add(config.clangPath)
            add("--target=${NdkTarget.tripleFor(config)}")
            add("--sysroot=${config.sysrootPath}")
            if (task.language == Language.C) {
                add("-std=c17")
            } else {
                add("-std=c++17")
            }
            add("-c")
            add(task.sourcePath)
            add("-o")
            add(task.outputPath)
            add("-fPIC")
            add("-O2")
            add("-Wall")
        }
    }
}
