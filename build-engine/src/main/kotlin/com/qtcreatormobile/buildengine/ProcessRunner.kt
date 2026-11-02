package com.qtcreatormobile.buildengine

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val succeeded: Boolean get() = exitCode == 0
}

/**
 * Abstracao sobre "rodar um processo externo". Existe para que o build engine
 * possa ser testado sem depender de um compilador real instalado.
 */
interface ProcessRunner {
    fun run(command: List<String>, workingDir: File): ProcessResult
}

/**
 * Extensao OPCIONAL de [ProcessRunner] para quem precisa de variaveis de
 * ambiente (ex.: `kotlinc` exige `JAVA_HOME` no ambiente do processo). Interface
 * separada, em vez de acrescentar um parametro a [ProcessRunner], para nao
 * quebrar nenhuma implementacao ja escrita (fakes de teste inclusive).
 */
interface EnvironmentProcessRunner : ProcessRunner {
    fun run(command: List<String>, workingDir: File, env: Map<String, String>): ProcessResult
}

class RealProcessRunner : EnvironmentProcessRunner {
    override fun run(command: List<String>, workingDir: File): ProcessResult = run(command, workingDir, emptyMap())

    override fun run(command: List<String>, workingDir: File, env: Map<String, String>): ProcessResult {
        val builder = ProcessBuilder(command).directory(workingDir)
        builder.environment().putAll(env)
        val process = builder.start()
        val readers = Executors.newFixedThreadPool(2)
        try {
            // Drain both pipes concurrently so a noisy compiler cannot block on a full stderr/stdout pipe.
            val stdoutFuture = readers.submit<String> { process.inputStream.bufferedReader().use { it.readText() } }
            val stderrFuture = readers.submit<String> { process.errorStream.bufferedReader().use { it.readText() } }
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                throw IllegalStateException("Processo excedeu o limite de 15 minutos: ${command.firstOrNull() ?: "comando"}")
            }
            return ProcessResult(process.exitValue(), stdoutFuture.get(), stderrFuture.get())
        } finally {
            readers.shutdownNow()
            if (process.isAlive) process.destroyForcibly()
        }
    }
}

/** Roda [this] com [env] se ele suportar ([EnvironmentProcessRunner]), senao ignora o ambiente (fakes de teste). */
fun ProcessRunner.runWithEnv(command: List<String>, workingDir: File, env: Map<String, String>): ProcessResult =
    if (this is EnvironmentProcessRunner) run(command, workingDir, env) else run(command, workingDir)
