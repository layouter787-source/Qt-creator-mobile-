package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language

/**
 * Define, para cada linguagem, onde o toolchain correspondente e extraido
 * dentro do armazenamento do app e qual e o nome do binario principal.
 */
object ToolchainLayout {

    /** Subdiretorio dentro de `<filesDir>/toolchain/` reservado para esta linguagem. */
    fun subdirFor(language: Language): String = when (language) {
        Language.C, Language.CPP -> "cpp"
        Language.JAVA -> "java"
        Language.KOTLIN -> "kotlin"
    }

    /**
     * Nome do binario principal esperado dentro do subdiretorio da linguagem.
     * C/C++: "usr/bin/clang" (estrutura do pacote .deb do Termux).
     */
    fun mainBinaryNameFor(language: Language): String = when (language) {
        Language.C, Language.CPP -> "usr/bin/clang"
        Language.JAVA -> "bin/javac"
        Language.KOTLIN -> "bin/kotlinc"
    }
}
