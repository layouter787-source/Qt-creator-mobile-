package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Language
import java.io.File

/** Monta a linha de comando do javac para compilar um unico .java, sem executar nada. */
object JavaCompileCommandBuilder {

    fun build(task: CompileTask, config: JavaToolchainConfig, sourcePaths: List<String> = listOf(task.sourcePath)): List<String> {
        require(task.language == Language.JAVA) {
            "JavaCompileCommandBuilder so aceita tarefas de linguagem JAVA, recebeu: ${task.language}"
        }

        val outputDir = File(task.outputPath).parentFile?.path ?: "."

        return buildList {
            add(config.javacPath)
            val classpath = listOfNotNull(config.androidJarPath, config.classOutputDir).joinToString(File.pathSeparator)
            if (classpath.isNotEmpty()) addAll(listOf("-classpath", classpath))
            addAll(listOf("-d", outputDir))
            addAll(sourcePaths)
        }
    }
}

/**
 * Monta a linha de comando do kotlinc para compilar um unico .kt, incluindo
 * o classpath de Compose/Material3 e o plugin compilador do Compose quando
 * configurados. NAO inclui `JAVA_HOME` (variavel de ambiente, nao argumento).
 */
object KotlinCompileCommandBuilder {

    fun build(task: CompileTask, config: KotlinToolchainConfig, sourcePaths: List<String> = listOf(task.sourcePath)): List<String> {
        require(task.language == Language.KOTLIN) {
            "KotlinCompileCommandBuilder so aceita tarefas de linguagem KOTLIN, recebeu: ${task.language}"
        }

        val outputDir = File(task.outputPath).parentFile?.path ?: "."

        return buildList {
            add(config.kotlincPath)
            val classpath = config.classpathJars + listOfNotNull(config.classOutputDir)
            if (classpath.isNotEmpty()) {
                add("-classpath")
                add(classpath.joinToString(File.pathSeparator))
            }
            config.composeCompilerPluginJar?.let {
                add("-Xplugin=$it")
            }
            add("-d")
            add(outputDir)
            addAll(sourcePaths)
            addAll(config.javaSourceStubs)
        }
    }
}
