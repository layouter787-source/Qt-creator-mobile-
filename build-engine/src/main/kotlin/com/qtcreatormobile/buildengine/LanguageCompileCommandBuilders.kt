package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File

/** Monta a linha de comando do javac para compilar um unico .java, sem executar nada. */
object JavaCompileCommandBuilder {

    fun build(task: CompileTask, config: JavaToolchainConfig): List<String> {
        require(task.language == Language.JAVA) {
            "JavaCompileCommandBuilder so aceita tarefas de linguagem JAVA, recebeu: ${task.language}"
        }

        val outputDir = File(task.outputPath).parentFile?.path ?: "."

        return listOf(
            config.javacPath,
            "-d", outputDir,
            task.sourcePath,
        )
    }
}

/**
 * Monta a linha de comando do kotlinc para compilar um unico .kt, incluindo
 * o classpath de Compose/Material3 e o plugin compilador do Compose quando
 * configurados. NAO inclui `JAVA_HOME` (variavel de ambiente, nao argumento).
 */
object KotlinCompileCommandBuilder {

    fun build(task: CompileTask, config: KotlinToolchainConfig): List<String> {
        require(task.language == Language.KOTLIN) {
            "KotlinCompileCommandBuilder so aceita tarefas de linguagem KOTLIN, recebeu: ${task.language}"
        }

        val outputDir = File(task.outputPath).parentFile?.path ?: "."

        return buildList {
            add(config.kotlincPath)
            if (config.classpathJars.isNotEmpty()) {
                add("-classpath")
                add(config.classpathJars.joinToString(File.pathSeparator))
            }
            config.composeCompilerPluginJar?.let {
                add("-Xplugin=$it")
            }
            add("-d")
            add(outputDir)
            add(task.sourcePath)
        }
    }
}
