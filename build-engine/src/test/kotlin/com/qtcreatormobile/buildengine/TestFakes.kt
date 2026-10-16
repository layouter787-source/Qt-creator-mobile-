package com.qtcreatormobile.buildengine

import java.io.File

/**
 * Fake de [ProcessRunner] COMPARTILHADO por todos os testes do pacote.
 *
 * Antes cada arquivo de teste declarava sua propria `private class FakeProcessRunner`,
 * mas classes top-level `private` de arquivos diferentes do MESMO pacote ainda
 * colidem no Kotlin ("Redeclaration"): o nome qualificado e o mesmo. Por isso a
 * definicao vive aqui, uma unica vez.
 */
class FakeProcessRunner(private val result: ProcessResult) : ProcessRunner {
    var lastCommand: List<String>? = null

    override fun run(command: List<String>, workingDir: File): ProcessResult {
        lastCommand = command
        return result
    }
}
