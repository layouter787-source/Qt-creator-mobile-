package com.qtcreatormobile.buildengine

import java.io.File
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Estes testes rodam PROCESSOS DE VERDADE (/bin/sh) - sao a unica cobertura do
 * [RealProcessRunner], que nos outros testes e sempre substituido por um fake.
 */
class RealProcessRunnerTest {

    private val sh = "/bin/sh"

    @Test
    fun `captures stdout and the exit code of a real process`() {
        val result = RealProcessRunner().run(listOf(sh, "-c", "echo hello; exit 3"), File("."))

        assertEquals("hello", result.stdout.trim())
        assertEquals(3, result.exitCode)
        assertFalse(result.succeeded)
    }

    @Test
    fun `passes environment variables to the process`() {
        val result = RealProcessRunner().run(
            listOf(sh, "-c", "echo \$JAVA_HOME"),
            File("."),
            mapOf("JAVA_HOME" to "/toolchain/java"),
        )

        assertEquals("/toolchain/java", result.stdout.trim())
    }

    @Test
    fun `does not deadlock when a process writes a lot to stderr`() {
        // 300 KB em stderr ANTES de qualquer saida em stdout: com a leitura sequencial antiga
        // (stdout inteiro primeiro) o processo bloqueava com o pipe de stderr cheio, para sempre.
        var result: ProcessResult? = null
        val worker = thread {
            result = RealProcessRunner().run(
                listOf(sh, "-c", "head -c 300000 /dev/zero | tr '\\0' x 1>&2; echo done"),
                File("."),
            )
        }
        worker.join(30_000)

        assertFalse(worker.isAlive, "o RealProcessRunner travou lendo stdout/stderr")
        assertEquals("done", result!!.stdout.trim())
        assertTrue(result!!.stderr.length >= 300_000)
    }

    @Test
    fun `reports a missing program as a normal failure instead of throwing`() {
        val result = RealProcessRunner().run(listOf("/nonexistent/bin/xyz"), File("."))

        assertEquals(EXIT_CODE_COMMAND_NOT_RUNNABLE, result.exitCode)
        assertTrue(result.stderr.contains("Nao foi possivel executar"))
        assertFalse(result.succeeded)
    }
}
