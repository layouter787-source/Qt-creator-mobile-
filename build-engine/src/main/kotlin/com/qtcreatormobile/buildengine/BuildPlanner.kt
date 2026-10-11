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
 */
object BuildPlanner {

    fun plan(target: Target): TaskGraph {
        val compileTasks = mutableListOf<CompileTask>()

        target.sources.cpp.forEach { src ->
            compileTasks += CompileTask(
                id = "compile:${target.id}:$src",
                language = Language.CPP,
                sourcePath = src,
                outputPath = objectFileFor(target.id, src),
            )
        }
        target.sources.kotlin.forEach { src ->
            compileTasks += CompileTask(
                id = "compile:${target.id}:$src",
                language = Language.KOTLIN,
                sourcePath = src,
                outputPath = classFileFor(target.id, src),
            )
        }
        target.sources.java.forEach { src ->
            compileTasks += CompileTask(
                id = "compile:${target.id}:$src",
                language = Language.JAVA,
                sourcePath = src,
                outputPath = classFileFor(target.id, src),
            )
        }

        val linkTask = LinkTask(
            id = "link:${target.id}",
            targetId = target.id,
            inputTaskIds = compileTasks.map { it.id }.toSet(),
            outputPath = "build/${target.id}/output",
        )

        return TaskGraph(compileTasks = compileTasks, linkTasks = listOf(linkTask))
    }

    private fun objectFileFor(targetId: String, source: String): String =
        "build/$targetId/obj/${source.substringAfterLast('/').substringBeforeLast('.')}.o"

    private fun classFileFor(targetId: String, source: String): String =
        "build/$targetId/classes/${source.substringAfterLast('/').substringBeforeLast('.')}.class"
}
