package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File

/**
 * Todos os caminhos das ferramentas instaladas, num unico lugar (a partir da
 * pasta `toolchain/` do app), para quem monta um build nao precisar repetir
 * a logica de layout de cada instalador.
 */
class ToolchainPaths(val toolchainRoot: File) {
    val cppDir = File(toolchainRoot, ToolchainLayout.subdirFor(Language.CPP))
    val javaDir = File(toolchainRoot, ToolchainLayout.subdirFor(Language.JAVA))
    val kotlinDir = File(toolchainRoot, ToolchainLayout.subdirFor(Language.KOTLIN))
    val packagingDir = File(toolchainRoot, PackagingLayout.SUBDIR)

    val clang = File(cppDir, ToolchainLayout.mainBinaryNameFor(Language.CPP))
    val sysroot = File(cppDir, "sysroot")

    val javac = File(javaDir, ToolchainLayout.mainBinaryNameFor(Language.JAVA))
    val java = File(javaDir, PackagingLayout.JAVA_BINARY)
    val keytool = File(javaDir, PackagingLayout.KEYTOOL_BINARY)

    val kotlinc = File(kotlinDir, ToolchainLayout.mainBinaryNameFor(Language.KOTLIN))
    val composeLibsDir = File(kotlinDir, "compose-libs")

    val aapt2 = File(packagingDir, PackagingLayout.AAPT2_BINARY)
    val d8Jar = File(packagingDir, PackagingLayout.D8_JAR)
    val apksignerJar = File(packagingDir, PackagingLayout.APKSIGNER_JAR)
    val androidJar = File(packagingDir, PackagingLayout.ANDROID_JAR)
    val debugKeystore = File(packagingDir, PackagingLayout.DEBUG_KEYSTORE)

    /** Jars de Compose/Material3 + stdlib para o `-classpath` do kotlinc (sem o plugin do compilador). */
    fun kotlinClasspathJars(): List<String> =
        composeLibsDir.listFiles { f -> f.extension == "jar" && !f.name.startsWith(COMPOSE_PLUGIN_PREFIX) }
            ?.map { it.absolutePath }?.sorted() ?: emptyList()

    /** Jar do plugin compilador do Compose (`-Xplugin=`), se estiver instalado. */
    fun composeCompilerPluginJar(): String? =
        composeLibsDir.listFiles { f -> f.name.startsWith(COMPOSE_PLUGIN_PREFIX) }?.firstOrNull()?.absolutePath

    companion object {
        const val COMPOSE_PLUGIN_PREFIX = "kotlin-compose-compiler-plugin"
    }
}
