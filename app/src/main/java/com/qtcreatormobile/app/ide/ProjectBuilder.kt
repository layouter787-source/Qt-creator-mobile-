package com.qtcreatormobile.app.ide

import android.app.Application
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
import com.qtcreatormobile.buildengine.Dexer
import com.qtcreatormobile.buildengine.JavaTaskExecutor
import com.qtcreatormobile.buildengine.JavaToolchainConfig
import com.qtcreatormobile.buildengine.KeytoolConfig
import com.qtcreatormobile.buildengine.KotlinTaskExecutor
import com.qtcreatormobile.buildengine.KotlinToolchainConfig
import com.qtcreatormobile.buildengine.NdkConfig
import com.qtcreatormobile.buildengine.PackagingLayout
import com.qtcreatormobile.buildengine.PackagingStepResult
import com.qtcreatormobile.buildengine.PipelineStepResult
import com.qtcreatormobile.buildengine.RealProcessRunner
import com.qtcreatormobile.buildengine.ToolchainLayout
import com.qtcreatormobile.projectmodel.EntryPoint
import com.qtcreatormobile.projectmodel.Language
import com.qtcreatormobile.projectmodel.ProjectModelLoader
import java.io.File

enum class BuildVariant(val label: String, val title: String) {
    DEBUG("debug", "Debug"),
    RELEASE("release", "Release"),
}

/** Chave de assinatura de um build release. */
data class SigningSpec(
    val keystore: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

data class BuildOutcome(
    val succeeded: Boolean,
    val summary: String,
    val steps: List<PipelineStepResult>,
    val apk: File?,
    val durationMs: Long,
) {
    val fullLog: String
        get() = steps.joinToString("\n\n") { "[${if (it.succeeded) "OK" else "ERRO"}] ${it.stepName}\n${it.log}".trimEnd() }
}

/** Compila projetos inteiramente no aparelho, com as ferramentas instaladas (clang, javac, kotlinc, d8, aapt2, apksigner). */
class ProjectBuilder(private val app: Application) {

    fun build(
        projectDir: File,
        variant: BuildVariant,
        signing: SigningSpec?,
        onStage: (String) -> Unit = {},
    ): BuildOutcome {
        val started = System.currentTimeMillis()
        return try {
            execute(projectDir, variant, signing, onStage, started)
        } catch (e: Exception) {
            BuildOutcome(false, e.message ?: "Falha ao compilar o projeto.", emptyList(), null, System.currentTimeMillis() - started)
        }
    }

    private fun execute(projectDir: File, variant: BuildVariant, signing: SigningSpec?, onStage: (String) -> Unit, started: Long): BuildOutcome {
        onStage("Lendo o projeto")
        val model = ProjectModelLoader.loadFromFile(File(projectDir, "qtcm.project.yaml"))
        val declared = model.targets.firstOrNull() ?: error("O projeto precisa ter pelo menos um target.")
        check(declared.id.matches(Regex("[A-Za-z0-9_-]+"))) { "ID de target inválido: ${declared.id}" }
        check(declared.minSdk in 26..34) { "minSdk deve estar entre 26 e 34 para este toolchain." }
        if (variant == BuildVariant.RELEASE) {
            checkNotNull(signing) { "Para o build Release escolha uma chave de assinatura (Configurações > Gerenciador de keystore)." }
        }

        fun expand(patterns: List<String>): List<String> = patterns.flatMap { pattern ->
            if ('*' !in pattern && '?' !in pattern && '{' !in pattern) {
                listOf(pattern)
            } else {
                val fs = projectDir.toPath().fileSystem
                val matcher = fs.getPathMatcher("glob:$pattern")
                projectDir.walkTopDown()
                    .onEnter { it == projectDir || it.name != "build" }
                    .filter { it.isFile }
                    .map { it.relativeTo(projectDir).path.replace(File.separatorChar, '/') }
                    .filter { matcher.matches(fs.getPath(it)) }
                    .toList()
            }
        }.distinct().sorted()

        val target = declared.copy(
            sources = declared.sources.copy(
                c = expand(declared.sources.c),
                cpp = expand(declared.sources.cpp),
                java = expand(declared.sources.java),
                kotlin = expand(declared.sources.kotlin),
            ),
        )
        val all = target.sources.c + target.sources.cpp + target.sources.java + target.sources.kotlin
        check(all.isNotEmpty()) { "Nenhuma fonte encontrada. Confira os padrões em qtcm.project.yaml." }
        val canonicalProject = projectDir.canonicalFile
        all.forEach { relative ->
            val source = File(projectDir, relative).canonicalFile
            check(source.toPath().startsWith(canonicalProject.toPath())) { "Fonte fora da pasta do projeto: $relative" }
            check(source.isFile) { "Arquivo fonte não encontrado: $relative" }
        }

        val toolchain = File(app.filesDir, "toolchain")
        val javaRoot = File(toolchain, ToolchainLayout.subdirFor(Language.JAVA))
        val cppRoot = File(toolchain, ToolchainLayout.subdirFor(Language.CPP))
        val kotlinRoot = File(toolchain, ToolchainLayout.subdirFor(Language.KOTLIN))
        val packagingRoot = File(toolchain, PackagingLayout.SUBDIR)
        val java = File(javaRoot, "bin/java")
        val javac = File(javaRoot, "bin/javac")
        val keytool = File(javaRoot, "bin/keytool")
        val androidJar = File(packagingRoot, PackagingLayout.ANDROID_JAR)
        val d8Jar = File(packagingRoot, PackagingLayout.D8_JAR)
        val signerJar = File(packagingRoot, PackagingLayout.APKSIGNER_JAR)
        val aapt2 = File(packagingRoot, PackagingLayout.AAPT2_BINARY)
        listOf(java, javac, keytool, androidJar, d8Jar, signerJar, aapt2).forEach {
            check(it.exists()) { "Ferramenta ausente: ${it.name}. Reinstale o ambiente em Configurações." }
        }
        val usesNative = target.sources.c.isNotEmpty() || target.sources.cpp.isNotEmpty()
        val clang = File(cppRoot, "usr/bin/clang")
        val sysroot = File(cppRoot, "sysroot")
        if (usesNative) {
            check(clang.exists() && File(sysroot, "usr/include/android/native_activity.h").exists()) {
                "Toolchain C/C++ ou headers do NDK não estão instalados."
            }
        }
        if (target.sources.kotlin.isNotEmpty()) {
            check(File(kotlinRoot, "bin/kotlinc").exists()) { "Instale o Kotlin em Configurações antes de compilar este projeto." }
        }

        val buildDir = File(projectDir, "build")
        buildDir.mkdirs()
        val manifest = File(buildDir, "AndroidManifest.xml")
        manifest.writeText(androidManifest(model.packageName, model.name, target.minSdk, target.entryPoint, target.id))
        val unsigned = File(buildDir, "${target.id}-${variant.label}-unsigned.apk")
        unsigned.delete()
        File(buildDir, "dex").deleteRecursively()
        File(buildDir, "intermediates").deleteRecursively()

        val process = RealProcessRunner()
        val libs = File(kotlinRoot, "compose-libs").listFiles()?.filter { it.extension == "jar" }.orEmpty()
        val composeJars = libs.filter { !it.name.startsWith("kotlin-compose-compiler-plugin") }.map { it.absolutePath }
        val kotlinPlugin = libs.firstOrNull { it.name.startsWith("kotlin-compose-compiler-plugin") }?.absolutePath
        val usesCompose = target.sources.kotlin.any { File(projectDir, it).readText().contains("androidx.compose") }
        // So empacota no dex o que o app usa: a stdlib do Kotlin e, se houver Compose, as bibliotecas dele.
        val runtimeJars = composeJars.filter { path ->
            usesCompose || File(path).name.startsWith("kotlin-stdlib")
        }
        val androidClasspath = listOf(androidJar.absolutePath) + composeJars
        val classesDir = File(buildDir, "intermediates/${target.id}/classes")
        val ndk = NdkConfig(clang.absolutePath, sysroot.absolutePath, apiLevel = target.minSdk)

        val executor = CompositeTaskExecutor(
            cppExecutor = CppTaskExecutor(ndk, process, projectDir),
            javaExecutor = JavaTaskExecutor(JavaToolchainConfig(javac.absolutePath, androidJar.absolutePath, classesDir.absolutePath, target.sources.java), process, projectDir),
            kotlinExecutor = KotlinTaskExecutor(
                KotlinToolchainConfig(File(kotlinRoot, "bin/kotlinc").absolutePath, javaRoot.absolutePath, androidClasspath, kotlinPlugin, classesDir.absolutePath, target.sources.kotlin, target.sources.java),
                process,
                projectDir,
            ),
        )
        val dexer = object : Dexer {
            override fun dex(classFilePaths: List<String>, outputDir: String): PackagingStepResult {
                val classes = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.map { it.absolutePath }.toList()
                return D8Executor(D8Config(java.absolutePath, d8Jar.absolutePath), process, projectDir).dex(classes + runtimeJars, outputDir)
            }
        }

        val spec = if (variant == BuildVariant.RELEASE) {
            signing!!
        } else {
            onStage("Preparando a chave de debug")
            val debugKeystore = File(packagingRoot, PackagingLayout.DEBUG_KEYSTORE)
            DebugKeystoreProvisioner(debugKeystore.absolutePath, KeytoolConfig(keytool.absolutePath), process, projectDir).ensureAvailable()
            SigningSpec(debugKeystore, DebugKeystoreDefaults.STORE_PASSWORD, DebugKeystoreDefaults.KEY_ALIAS, DebugKeystoreDefaults.KEY_PASSWORD)
        }
        check(spec.keystore.isFile) { "Keystore não encontrado: ${spec.keystore.name}" }

        val pipeline = BuildPipeline(
            executor,
            CppLinkExecutor(ndk, process, projectDir),
            dexer,
            Aapt2LinkExecutor(Aapt2Config(aapt2.absolutePath, androidJar.absolutePath), process, projectDir),
            ApkSignerExecutor(
                ApkSignerConfig(java.absolutePath, signerJar.absolutePath, spec.keystore.absolutePath, spec.storePassword, spec.keyAlias, spec.keyPassword),
                process,
                projectDir,
            ),
            nativeRuntimeLibraries = if (usesNative) {
                mapOf("lib/arm64-v8a/libc++_shared.so" to File(sysroot, "usr/lib/aarch64-linux-android/libc++_shared.so").absolutePath)
            } else {
                emptyMap()
            },
        )
        onStage("Compilando e empacotando")
        val result = pipeline.build(target, manifest.absolutePath, unsigned.absolutePath, File(buildDir, "intermediates").absolutePath)
        val elapsed = System.currentTimeMillis() - started
        if (!result.succeeded || !unsigned.isFile) {
            val failed = result.steps.lastOrNull { !it.succeeded }
            val summary = failed?.let { "A etapa '${it.stepName}' falhou." } ?: "Falha ao gerar o APK."
            return BuildOutcome(false, summary, result.steps, null, elapsed)
        }
        val finalApk = File(buildDir, "${target.id}-${variant.label}.apk")
        unsigned.copyTo(finalApk, overwrite = true)
        unsigned.delete()
        return BuildOutcome(true, "APK ${variant.title} gerado.", result.steps, finalApk, System.currentTimeMillis() - started)
    }

    private fun androidManifest(packageName: String, appName: String, minSdk: Int, entryPoint: EntryPoint, targetId: String): String {
        require(packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) { "Pacote inválido: $packageName" }
        val escapedName = appName.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
        val native = entryPoint == EntryPoint.NATIVE_ACTIVITY
        val activity = if (native) "android.app.NativeActivity" else ".MainActivity"
        val nativeMeta = if (native) "<meta-data android:name=\"android.app.lib_name\" android:value=\"$targetId\"/>" else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$packageName">
    <uses-sdk android:minSdkVersion="$minSdk" />
    <application android:label="$escapedName" android:hasCode="${!native}" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name="$activity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
            $nativeMeta
        </activity>
    </application>
</manifest>
"""
    }
}
