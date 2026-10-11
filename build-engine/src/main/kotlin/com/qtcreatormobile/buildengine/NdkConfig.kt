package com.qtcreatormobile.buildengine

/**
 * Localizacao das ferramentas do toolchain C/C++ que rodam DENTRO do proprio
 * dispositivo Android: clang (do Termux) e o sysroot (do NDK oficial).
 */
data class NdkConfig(
    val clangPath: String,
    val sysrootPath: String,
    val abi: String = "arm64-v8a",
    val apiLevel: Int = 26,
)

/** Monta o "target triple" do clang a partir de um [NdkConfig]. Compartilhado entre compilacao e link. */
object NdkTarget {
    fun tripleFor(config: NdkConfig): String {
        val archTriple = when (config.abi) {
            "arm64-v8a" -> "aarch64-linux-android"
            "armeabi-v7a" -> "armv7a-linux-androideabi"
            "x86_64" -> "x86_64-linux-android"
            "x86" -> "i686-linux-android"
            else -> throw BuildEngineException("ABI nao suportada: ${config.abi}")
        }
        return "$archTriple${config.apiLevel}"
    }
}
