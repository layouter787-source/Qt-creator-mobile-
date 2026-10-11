package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File

class JavaTaskExecutor(
    private val config: JavaToolchainConfig,
    private val processRunner: ProcessRunner,
    private val workingDir: File,
) : TaskExecutor {

    override fun execute(task: CompileTask): TaskResult {
        if (task.language != Language.JAVA) {
            throw BuildEngineException(
                "JavaTaskExecutor nao sabe compilar '${task.language}' (tarefa '${task.id}')."
            )
        }

        val command = JavaCompileCommandBuilder.build(task, config)
        File(task.outputPath).parentFile?.mkdirs()

        val result = processRunner.run(command, workingDir)

        return TaskResult(
            taskId = task.id,
            succeeded = result.succeeded,
            log = if (result.succeeded) result.stdout else result.stderr,
        )
    }
}

/**
 * O script `kotlinc` oficial busca o Java via `JAVA_HOME` no ambiente do
 * processo. Por isso este executor sempre roda via [runWithEnv].
 */
class KotlinTaskExecutor(
    private val config: KotlinToolchainConfig,
    private val processRunner: ProcessRunner,
    private val workingDir: File,
) : TaskExecutor {

    override fun execute(task: CompileTask): TaskResult {
        if (task.language != Language.KOTLIN) {
            throw BuildEngineException(
                "KotlinTaskExecutor nao sabe compilar '${task.language}' (tarefa '${task.id}')."
            )
        }

        val command = KotlinCompileCommandBuilder.build(task, config)
        File(task.outputPath).parentFile?.mkdirs()

        val result = processRunner.runWithEnv(command, workingDir, mapOf("JAVA_HOME" to config.javaHome))

        return TaskResult(
            taskId = task.id,
            succeeded = result.succeeded,
            log = if (result.succeeded) result.stdout else result.stderr,
        )
    }
}

/** Encaminha cada [CompileTask] para o executor da linguagem certa. */
class CompositeTaskExecutor(
    private val cppExecutor: TaskExecutor,
    private val javaExecutor: TaskExecutor,
    private val kotlinExecutor: TaskExecutor,
) : TaskExecutor {

    override fun execute(task: CompileTask): TaskResult = when (task.language) {
        Language.C, Language.CPP -> cppExecutor.execute(task)
        Language.JAVA -> javaExecutor.execute(task)
        Language.KOTLIN -> kotlinExecutor.execute(task)
    }
}
