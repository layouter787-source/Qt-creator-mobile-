package com.qtcreatormobile.buildengine

import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

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

/** Codigo de saida usado (convencao do shell) quando o programa nem chegou a ser executado. */
const val EXIT_CODE_COMMAND_NOT_RUNNABLE = 127

class RealProcessRunner : EnvironmentProcessRunner {
    override fun run(command: List<String>, workingDir: File): ProcessResult = run(command, workingDir, emptyMap())

    override fun run(command: List<String>, workingDir: File, env: Map<String, String>): ProcessResult {
        val builder = ProcessBuilder(command).directory(workingDir)
        builder.environment().putAll(env)

        val process = try {
            builder.start()
        } catch (e: IOException) {
            // Binario ausente, sem permissao de execucao, etc. Vira uma falha NORMAL (que o
            // pipeline reporta na etapa certa), em vez de uma excecao que derruba tudo.
            return ProcessResult(
                exitCode = EXIT_CODE_COMMAND_NOT_RUNNABLE,
                stdout = "",
                stderr = "Nao foi possivel executar '${command.firstOrNull()}': ${e.message}",
            )
        }

        // stderr e lido em paralelo: ler primeiro todo o stdout e so depois o stderr TRAVA o
        // processo quando ele escreve mais de ~64 KB em stderr (o pipe enche e ele bloqueia).
        // Compiladores com muitos erros fazem exatamente isso.
        val stderrBuffer = StringBuilder()
        val stderrReader = thread(name = "process-stderr-reader") {
            stderrBuffer.append(process.errorStream.bufferedReader().readText())
        }
        val stdout = process.inputStream.bufferedReader().readText()
        stderrReader.join()
        val exitCode = process.waitFor()

        return ProcessResult(exitCode = exitCode, stdout = stdout, stderr = stderrBuffer.toString())
    }
}

/** Roda [this] com [env] se ele suportar ([EnvironmentProcessRunner]), senao ignora o ambiente (fakes de teste). */
fun ProcessRunner.runWithEnv(command: List<String>, workingDir: File, env: Map<String, String>): ProcessResult =
    if (this is EnvironmentProcessRunner) run(command, workingDir, env) else run(command, workingDir)
