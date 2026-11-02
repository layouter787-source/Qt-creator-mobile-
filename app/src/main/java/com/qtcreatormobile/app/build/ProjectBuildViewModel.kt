package com.qtcreatormobile.app.build

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtcreatormobile.buildengine.Aapt2Config
import com.qtcreatormobile.buildengine.Aapt2LinkExecutor
import com.qtcreatormobile.buildengine.ApkSignerConfig
import com.qtcreatormobile.buildengine.ApkSignerExecutor
import com.qtcreatormobile.buildengine.BuildPipeline
import com.qtcreatormobile.buildengine.CompositeTaskExecutor
import com.qtcreatormobile.buildengine.CppLinkExecutor
import com.qtcreatormobile.buildengine.CppTaskExecutor
import com.qtcreatormobile.buildengine.D8Config
import com.qtcreatormobile.buildengine.D8Executor
import com.qtcreatormobile.buildengine.DebugKeystoreDefaults
import com.qtcreatormobile.buildengine.DebugKeystoreProvisioner
import com.qtcreatormobile.buildengine.JavaTaskExecutor
import com.qtcreatormobile.buildengine.JavaToolchainConfig
import com.qtcreatormobile.buildengine.KeytoolConfig
import com.qtcreatormobile.buildengine.KotlinTaskExecutor
import com.qtcreatormobile.buildengine.KotlinToolchainConfig
import com.qtcreatormobile.buildengine.NdkConfig
import com.qtcreatormobile.buildengine.PackagingLayout
import com.qtcreatormobile.buildengine.RealProcessRunner
import com.qtcreatormobile.buildengine.ToolchainLayout
import com.qtcreatormobile.projectmodel.EntryPoint
import com.qtcreatormobile.projectmodel.ProjectModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface ProjectBuildState {
    data object Idle : ProjectBuildState
    data class Running(val step: String) : ProjectBuildState
    data class Finished(val apk: File, val log: String) : ProjectBuildState
    data class Failed(val message: String, val log: String = "") : ProjectBuildState
}

private class ProjectBuildFailure(message: String, val buildLog: String) : IllegalStateException(message)

/** Builds projects entirely on-device using the installed native/Java toolchains. */
class ProjectBuildViewModel(application: Application) : AndroidViewModel(application) {
    private val stateFlow = MutableStateFlow<ProjectBuildState>(ProjectBuildState.Idle)
    val state = stateFlow.asStateFlow()

    fun build(projectDir: File) {
        if (stateFlow.value is ProjectBuildState.Running) return
        stateFlow.value = ProjectBuildState.Running("Lendo projeto")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stateFlow.value = ProjectBuildState.Running("Compilando e empacotando")
                val apk = buildProject(projectDir)
                stateFlow.value = ProjectBuildState.Finished(apk, "APK debug gerado com sucesso.\n${apk.absolutePath}")
            } catch (e: Exception) {
                stateFlow.value = ProjectBuildState.Failed(
                    e.message ?: "Falha ao compilar o projeto.",
                    (e as? ProjectBuildFailure)?.buildLog.orEmpty(),
                )
            }
        }
    }

    private fun buildProject(projectDir: File): File {
        val app = getApplication<Application>()
        val modelFile = File(projectDir, "qtcm.project.yaml")
        val model = ProjectModelLoader.loadFromFile(modelFile)
        val declaredTarget = model.targets.firstOrNull() ?: error("O projeto precisa ter pelo menos um target.")
        fun expand(patterns: List<String>): List<String> = patterns.flatMap { pattern ->
            if ('*' !in pattern && '?' !in pattern && '{' !in pattern) listOf(pattern) else {
                val matcher = projectDir.toPath().fileSystem.getPathMatcher("glob:$pattern")
                projectDir.walkTopDown().filter { it.isFile && "/build/" !in it.path }
                    .map { it.relativeTo(projectDir).path.replace(File.separatorChar, '/') }
                    .filter { matcher.matches(projectDir.toPath().fileSystem.getPath(it)) }.toList()
            }
        }.distinct().sorted()
        check(declaredTarget.id.matches(Regex("[A-Za-z0-9_-]+"))) { "ID de target inválido: ${declaredTarget.id}" }
        check(declaredTarget.minSdk in 26..34) { "minSdk deve estar entre 26 e 34 para este toolchain." }
        val target = declaredTarget.copy(sources = declaredTarget.sources.copy(
            c = expand(declaredTarget.sources.c), cpp = expand(declaredTarget.sources.cpp),
            java = expand(declaredTarget.sources.java), kotlin = expand(declaredTarget.sources.kotlin),
        ))
        check(target.sources.c.isNotEmpty() || target.sources.cpp.isNotEmpty() || target.sources.java.isNotEmpty() || target.sources.kotlin.isNotEmpty()) { "Nenhuma fonte encontrada. Confira os padrões em qtcm.project.yaml." }
        val buildDir = File(projectDir, "build")
        buildDir.mkdirs()
        val javaRoot = File(app.filesDir, "toolchain/${ToolchainLayout.subdirFor(com.qtcreatormobile.projectmodel.Language.JAVA)}")
        val cppRoot = File(app.filesDir, "toolchain/${ToolchainLayout.subdirFor(com.qtcreatormobile.projectmodel.Language.CPP)}")
        val kotlinRoot = File(app.filesDir, "toolchain/${ToolchainLayout.subdirFor(com.qtcreatormobile.projectmodel.Language.KOTLIN)}")
        val packagingRoot = File(app.filesDir, "toolchain/${PackagingLayout.SUBDIR}")
        val java = File(javaRoot, "bin/java")
        val javac = File(javaRoot, "bin/javac")
        val keytool = File(javaRoot, "bin/keytool")
        val androidJar = File(packagingRoot, PackagingLayout.ANDROID_JAR)
        val d8Jar = File(packagingRoot, PackagingLayout.D8_JAR)
        val signerJar = File(packagingRoot, PackagingLayout.APKSIGNER_JAR)
        val aapt2 = File(packagingRoot, PackagingLayout.AAPT2_BINARY)
        listOf(java, javac, keytool, androidJar, d8Jar, signerJar, aapt2).forEach { check(it.exists()) { "Ferramenta ausente: ${it.name}. Reinstale o ambiente em Configurações." } }
        val clang = File(cppRoot, "usr/bin/clang")
        val sysroot = File(cppRoot, "sysroot")
        check(!target.sources.c.isNotEmpty() && !target.sources.cpp.isNotEmpty() || clang.exists() && File(sysroot, "usr/include/android/native_activity.h").exists()) {
            "Toolchain C/C++ ou headers do NDK não estão instalados."
        }
        if (target.sources.kotlin.isNotEmpty()) check(File(kotlinRoot, "bin/kotlinc").exists()) { "Instale Kotlin na aba Toolchains antes de compilar este projeto." }

        val sourceLists = listOf(target.sources.c, target.sources.cpp, target.sources.java, target.sources.kotlin)
        sourceLists.flatten().forEach { relative ->
            val source = File(projectDir, relative).canonicalFile
            check(source.toPath().startsWith(projectDir.canonicalFile.toPath())) { "Fonte fora da pasta do projeto: $relative" }
            check(source.isFile) { "Arquivo fonte não encontrado: $relative" }
        }
        val manifest = File(buildDir, "AndroidManifest.xml")
        manifest.writeText(androidManifest(model.packageName, model.name, target.minSdk, target.entryPoint, target.id))
        val output = File(buildDir, "${target.id}-debug-unsigned.apk")
        output.delete()
        File(buildDir, "dex").deleteRecursively()
        File(buildDir, "intermediates").deleteRecursively()
        val process = RealProcessRunner()
        val composeJars = File(kotlinRoot, "compose-libs").listFiles()
            ?.filter { it.extension == "jar" && !it.name.startsWith("kotlin-compose-compiler-plugin") }
            ?.map { it.absolutePath }.orEmpty()
        val androidCp = listOf(androidJar.absolutePath) + composeJars
        val kotlinPlugin = File(kotlinRoot, "compose-libs").listFiles()
            ?.firstOrNull { it.name.startsWith("kotlin-compose-compiler-plugin") }?.absolutePath
        val classesOutputDir = File(buildDir, "intermediates/${target.id}/classes").absolutePath
        val taskExecutor = CompositeTaskExecutor(
            cppExecutor = CppTaskExecutor(NdkConfig(clang.absolutePath, File(cppRoot, "sysroot").absolutePath, apiLevel = target.minSdk), process, projectDir),
            javaExecutor = JavaTaskExecutor(JavaToolchainConfig(javac.absolutePath, androidJar.absolutePath, classesOutputDir, target.sources.java), process, projectDir),
            kotlinExecutor = KotlinTaskExecutor(KotlinToolchainConfig(File(kotlinRoot, "bin/kotlinc").absolutePath, javaRoot.absolutePath, androidCp, kotlinPlugin, classesOutputDir, target.sources.kotlin, target.sources.java), process, projectDir),
        )
        val link = CppLinkExecutor(NdkConfig(clang.absolutePath, File(cppRoot, "sysroot").absolutePath, apiLevel = target.minSdk), process, projectDir)
        val dexInputs = object : com.qtcreatormobile.buildengine.Dexer {
            override fun dex(classFilePaths: List<String>, outputDir: String): com.qtcreatormobile.buildengine.PackagingStepResult {
                val classRoot = File(buildDir, "intermediates/${target.id}/classes")
                val classes = classRoot.walkTopDown().filter { it.isFile && it.extension == "class" }.map { it.absolutePath }.toList()
                val runtimeJars = composeJars.filter { it.substringAfterLast('/').startsWith("kotlin-") || it.substringAfterLast('/').startsWith("kotlinx-") || it.contains("compose-libs") }
                return D8Executor(D8Config(java.absolutePath, d8Jar.absolutePath), process, projectDir)
                    .dex(classes + runtimeJars, outputDir)
            }
        }
        val keystore = File(packagingRoot, PackagingLayout.DEBUG_KEYSTORE)
        DebugKeystoreProvisioner(keystore.absolutePath, KeytoolConfig(keytool.absolutePath), process, projectDir).ensureAvailable()
        val pipeline = BuildPipeline(
            taskExecutor, link, dexInputs,
            Aapt2LinkExecutor(Aapt2Config(aapt2.absolutePath, androidJar.absolutePath), process, projectDir),
            ApkSignerExecutor(ApkSignerConfig(java.absolutePath, signerJar.absolutePath, keystore.absolutePath, DebugKeystoreDefaults.STORE_PASSWORD, DebugKeystoreDefaults.KEY_ALIAS, DebugKeystoreDefaults.KEY_PASSWORD), process, projectDir),
            nativeRuntimeLibraries = mapOf("lib/arm64-v8a/libc++_shared.so" to File(cppRoot, "sysroot/usr/lib/aarch64-linux-android/libc++_shared.so").absolutePath),
        )
        val result = pipeline.build(target, manifest.absolutePath, output.absolutePath, File(buildDir, "intermediates").absolutePath)
        val log = result.steps.joinToString("\n\n") { "[${if (it.succeeded) "OK" else "ERRO"}] ${it.stepName}\n${it.log}" }
        if (!result.succeeded || !output.isFile) {
            val failedStep = result.steps.lastOrNull { !it.succeeded }
            val summary = failedStep?.stepName?.let { "$it falhou ao gerar o APK." } ?: "Falha ao gerar APK."
            throw ProjectBuildFailure(summary, log)
        }
        val signed = File(buildDir, "${target.id}-debug.apk")
        if (signed != output) output.copyTo(signed, overwrite = true)
        return signed
    }

    private fun androidManifest(packageName: String, appName: String, minSdk: Int, entryPoint: EntryPoint, targetId: String): String {
        require(packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) { "Package inválido: $packageName" }
        val escapedName = appName.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
        val activity = if (entryPoint == EntryPoint.NATIVE_ACTIVITY) "android.app.NativeActivity" else ".MainActivity"
        val nativeMeta = if (entryPoint == EntryPoint.NATIVE_ACTIVITY) "<meta-data android:name=\"android.app.lib_name\" android:value=\"$targetId\"/>" else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$packageName">
    <uses-sdk android:minSdkVersion="$minSdk" />
    <application android:label="$escapedName" android:hasCode="${entryPoint != EntryPoint.NATIVE_ACTIVITY}" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name="$activity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
            $nativeMeta
        </activity>
    </application>
</manifest>
"""
    }
}
