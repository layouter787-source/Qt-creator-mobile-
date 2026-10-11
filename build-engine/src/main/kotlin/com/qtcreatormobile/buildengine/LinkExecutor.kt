package com.qtcreatormobile.buildengine

import java.io.File

/** Executa uma [LinkTask] de verdade: junta os .o (ja compilados) num .so. */
interface LinkExecutor {
    fun execute(linkTask: LinkTask, graph: TaskGraph): TaskResult
}

/**
 * Implementacao C/C++: resolve, a partir do [TaskGraph], quais [CompileTask]
 * alimentam esta [LinkTask] e usa os `outputPath` delas (os `.o`) como entrada.
 */
class CppLinkExecutor(
    private val ndkConfig: NdkConfig,
    private val processRunner: ProcessRunner,
    private val workingDir: File,
) : LinkExecutor {

    override fun execute(linkTask: LinkTask, graph: TaskGraph): TaskResult {
        val objectFiles = graph.compileTasks
            .filter { it.id in linkTask.inputTaskIds }
            .map { it.outputPath }

        if (objectFiles.isEmpty()) {
            throw BuildEngineException(
                "LinkTask '${linkTask.id}' nao encontrou nenhum CompileTask correspondente no TaskGraph."
            )
        }

        val command = CppLinkCommandBuilder.build(linkTask, objectFiles, ndkConfig)
        File(linkTask.outputPath).parentFile?.mkdirs()

        val result = processRunner.run(command, workingDir)

        return TaskResult(
            taskId = linkTask.id,
            succeeded = result.succeeded,
            log = if (result.succeeded) result.stdout else result.stderr,
        )
    }
}
