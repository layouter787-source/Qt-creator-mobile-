package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File

data class TaskResult(
    val taskId: String,
    val succeeded: Boolean,
    val log: String,
)

/** Executa uma [CompileTask] de verdade, invocando a toolchain apropriada. */
interface TaskExecutor {
    fun execute(task: CompileTask): TaskResult
}

/**
 * Toolchain C/C++ via clang rodando on-device. Aceita tanto C quanto C++.
 * Para Java/Kotlin, ver JavaTaskExecutor e KotlinTaskExecutor.
 */
class CppTaskExecutor(
    private val ndkConfig: NdkConfig,
    private val processRunner: ProcessRunner,
    private val workingDir: File,
) : TaskExecutor {

    override fun execute(task: CompileTask): TaskResult {
        if (task.language != Language.CPP && task.language != Language.C) {
            throw BuildEngineException(
                "CppTaskExecutor nao sabe compilar '${task.language}' (tarefa '${task.id}'). " +
                    "Use JavaTaskExecutor ou KotlinTaskExecutor para essa linguagem."
            )
        }

        val command = CppCompileCommandBuilder.build(task, ndkConfig)
        File(task.outputPath).parentFile?.mkdirs()

        val result = processRunner.run(command, workingDir)

        return TaskResult(
            taskId = task.id,
            succeeded = result.succeeded,
            log = if (result.succeeded) result.stdout else result.stderr,
        )
    }
}
