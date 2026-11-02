package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import com.qtcreatormobile.projectmodel.Target

/** Uma unidade de compilacao: um arquivo fonte que produz um artefato intermediario. */
data class CompileTask(
    val id: String,
    val language: Language,
    val sourcePath: String,
    val outputPath: String,
    val dependsOn: Set<String> = emptySet(),
)

/** Uma unidade de link/empacotamento: combina artefatos intermediarios num binario ou biblioteca. */
data class LinkTask(
    val id: String,
    val targetId: String,
    val inputTaskIds: Set<String>,
    val outputPath: String,
)

/**
 * Grafo de tarefas de um build. Guarda tarefas de compilacao e de link,
 * junto com as dependencias entre elas, para permitir execucao incremental
 * e execucao paralela.
 */
data class TaskGraph(
    val compileTasks: List<CompileTask>,
    val linkTasks: List<LinkTask>,
) {
    /** Ordem topologica de execucao respeitando dependencias declaradas. */
    fun executionOrder(): List<String> {
        val allTasks: Map<String, Set<String>> = buildMap {
            compileTasks.forEach { put(it.id, it.dependsOn) }
            linkTasks.forEach { put(it.id, it.inputTaskIds) }
        }

        val visited = mutableSetOf<String>()
        val order = mutableListOf<String>()
        val visiting = mutableSetOf<String>()

        fun visit(taskId: String) {
            if (taskId in visited) return
            if (taskId in visiting) {
                throw BuildEngineException("Dependencia ciclica detectada envolvendo a tarefa '$taskId'.")
            }
            visiting += taskId
            allTasks[taskId]?.forEach { dep -> visit(dep) }
            visiting -= taskId
            visited += taskId
            order += taskId
        }

        allTasks.keys.forEach { visit(it) }
        return order
    }
}

class BuildEngineException(message: String) : Exception(message)

/**
 * Converte um [Target] do project-model num [TaskGraph] executavel: uma
 * CompileTask por arquivo fonte e uma LinkTask final por target. Dependencias
 * entre arquivos (analise de #include/import) ficam para uma fase futura.
 *
 * @param buildDir pasta onde ficam todos os artefatos intermediarios e finais.
 *   Parametro (em vez de fixo) para o chamador escolher - no app, uma pasta do
 *   projeto; nos testes, uma pasta temporaria.
 */
object BuildPlanner {

    fun plan(target: Target, buildDir: String = "build"): TaskGraph {
        val compileTasks = mutableListOf<CompileTask>()

        target.sources.c.forEach { src ->
            compileTasks += CompileTask(
                id = "compile:${target.id}:$src",
                language = Language.C,
                sourcePath = src,
                outputPath = objectFileFor(buildDir, target.id, src),
            )
        }
        target.sources.cpp.forEach { src ->
            compileTasks += CompileTask(
                id = "compile:${target.id}:$src",
                language = Language.CPP,
                sourcePath = src,
                outputPath = objectFileFor(buildDir, target.id, src),
            )
        }
        target.sources.kotlin.forEach { src ->
            compileTasks += CompileTask(
                id = "compile:${target.id}:$src",
                language = Language.KOTLIN,
                sourcePath = src,
                outputPath = classFileFor(buildDir, target.id, src),
            )
        }
        target.sources.java.forEach { src ->
            compileTasks += CompileTask(
                id = "compile:${target.id}:$src",
                language = Language.JAVA,
                sourcePath = src,
                outputPath = classFileFor(buildDir, target.id, src),
            )
        }

        val linkTask = LinkTask(
            id = "link:${target.id}",
            targetId = target.id,
            inputTaskIds = compileTasks.map { it.id }.toSet(),
            // Um .so de verdade, com nome de biblioteca (antes era um arquivo chamado so "output").
            outputPath = "$buildDir/${target.id}/lib/lib${target.id}.so",
        )

        return TaskGraph(compileTasks = compileTasks, linkTasks = listOf(linkTask))
    }

    private fun outputStem(source: String): String =
        "${source.substringAfterLast('/').substringBeforeLast('.')}-${source.hashCode().toUInt().toString(16)}"

    private fun objectFileFor(buildDir: String, targetId: String, source: String): String =
        "$buildDir/$targetId/obj/${outputStem(source)}.o"

    private fun classFileFor(buildDir: String, targetId: String, source: String): String =
        "$buildDir/$targetId/classes/${source.substringAfterLast('/').substringBeforeLast('.')}.class"
}
