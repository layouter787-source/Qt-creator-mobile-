package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.Target
import java.io.File

/** Resultado de uma etapa individual do pipeline, para diagnostico claro de onde algo falhou. */
data class PipelineStepResult(
    val stepName: String,
    val succeeded: Boolean,
    val log: String,
)

data class BuildPipelineResult(
    val steps: List<PipelineStepResult>,
    val outputApkPath: String?,
) {
    val succeeded: Boolean get() = steps.all { it.succeeded }
}

/**
 * Orquestra o pipeline completo: codigo-fonte -> compilar -> linkar (C/C++) ->
 * dex (Java/Kotlin) -> empacotar (aapt2) -> assinar (apksigner). Para na
 * primeira etapa que falhar - nunca continua "fingindo sucesso".
 */
class BuildPipeline(
    private val taskExecutor: TaskExecutor,
    private val linkExecutor: LinkExecutor,
    private val dexer: Dexer,
    private val aapt2Linker: Aapt2Linker,
    private val apkSigner: ApkSigner,
) {

    fun build(target: Target, manifestPath: String, outputApkPath: String): BuildPipelineResult {
        val steps = mutableListOf<PipelineStepResult>()
        val graph = BuildPlanner.plan(target)

        // 1) Compilar cada arquivo fonte com o executor da linguagem correspondente.
        for (compileTask in graph.compileTasks) {
            val result = taskExecutor.execute(compileTask)
            steps += PipelineStepResult("compile:${compileTask.sourcePath}", result.succeeded, result.log)
            if (!result.succeeded) return BuildPipelineResult(steps, null)
        }

        // 2) Linkar os .o (C/C++) em .so, se houver algum nesta target.
        val hasNativeSources = target.sources.cpp.isNotEmpty()
        var linkedSoPath: String? = null
        if (hasNativeSources) {
            val linkTask = graph.linkTasks.first()
            val result = linkExecutor.execute(linkTask, graph)
            steps += PipelineStepResult("link:${target.id}", result.succeeded, result.log)
            if (!result.succeeded) return BuildPipelineResult(steps, null)
            linkedSoPath = linkTask.outputPath
        }

        // 3) Converter .class (Java/Kotlin) em classes.dex, se houver algum.
        val classFiles = graph.compileTasks
            .filter { it.language.name == "JAVA" || it.language.name == "KOTLIN" }
            .map { it.outputPath }
        var dexPath: String? = null
        if (classFiles.isNotEmpty()) {
            val dexOutputDir = File(outputApkPath).parentFile?.resolve("dex")?.path ?: "dex"
            val result = dexer.dex(classFiles, dexOutputDir)
            steps += PipelineStepResult("dex:${target.id}", result.succeeded, result.log)
            if (!result.succeeded) return BuildPipelineResult(steps, null)
            dexPath = File(dexOutputDir, "classes.dex").path
        }

        // 4) Gerar o APK base (recursos + manifesto) via aapt2 link.
        val linkResult = aapt2Linker.link(manifestPath, outputApkPath)
        steps += PipelineStepResult("aapt2-link:${target.id}", linkResult.succeeded, linkResult.log)
        if (!linkResult.succeeded) return BuildPipelineResult(steps, null)

        // 5) Injetar .so/.dex compilados dentro do APK base.
        val extraEntries = buildMap {
            linkedSoPath?.let { put("lib/arm64-v8a/lib${target.id}.so", it) }
            dexPath?.let { put("classes.dex", it) }
        }
        if (extraEntries.isNotEmpty()) {
            try {
                ApkAssembler.addEntries(outputApkPath, extraEntries)
                steps += PipelineStepResult("assemble:${target.id}", true, "Entradas adicionadas: ${extraEntries.keys}")
            } catch (e: BuildEngineException) {
                steps += PipelineStepResult("assemble:${target.id}", false, e.message ?: "Falha ao montar o APK.")
                return BuildPipelineResult(steps, null)
            }
        }

        // 6) Assinar o APK final - sem isso o Android recusa a instalacao.
        val signResult = apkSigner.sign(outputApkPath)
        steps += PipelineStepResult("sign:${target.id}", signResult.succeeded, signResult.log)
        if (!signResult.succeeded) return BuildPipelineResult(steps, null)

        return BuildPipelineResult(steps, outputApkPath)
    }
}
