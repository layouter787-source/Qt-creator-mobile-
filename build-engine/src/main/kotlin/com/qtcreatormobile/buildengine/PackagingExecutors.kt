package com.qtcreatormobile.buildengine

import java.io.File

data class PackagingStepResult(
    val succeeded: Boolean,
    val log: String,
)

interface Aapt2Linker {
    fun link(manifestPath: String, outputApkPath: String): PackagingStepResult
}

interface Dexer {
    fun dex(classFilePaths: List<String>, outputDir: String): PackagingStepResult
}

interface ApkSigner {
    fun sign(apkPath: String): PackagingStepResult
}

/** Gera o APK base (recursos + manifesto compilados) via `aapt2 link` de verdade. */
class Aapt2LinkExecutor(
    private val config: Aapt2Config,
    private val processRunner: ProcessRunner,
    private val workingDir: File,
) : Aapt2Linker {
    override fun link(manifestPath: String, outputApkPath: String): PackagingStepResult {
        File(outputApkPath).parentFile?.mkdirs()
        val command = Aapt2LinkCommandBuilder.build(manifestPath, outputApkPath, config)
        val result = processRunner.run(command, workingDir)
        return PackagingStepResult(result.succeeded, if (result.succeeded) result.stdout else result.stderr)
    }
}

/** Converte .class em classes.dex via `d8` de verdade (rodado como `java -cp d8.jar ...`). */
class D8Executor(
    private val config: D8Config,
    private val processRunner: ProcessRunner,
    private val workingDir: File,
) : Dexer {
    override fun dex(classFilePaths: List<String>, outputDir: String): PackagingStepResult {
        File(outputDir).mkdirs()
        val command = D8CommandBuilder.build(classFilePaths, outputDir, config)
        val result = processRunner.run(command, workingDir)
        return PackagingStepResult(result.succeeded, if (result.succeeded) result.stdout else result.stderr)
    }
}

/** Assina o APK via `apksigner` de verdade - sem isso o Android recusa a instalacao. */
class ApkSignerExecutor(
    private val config: ApkSignerConfig,
    private val processRunner: ProcessRunner,
    private val workingDir: File,
) : ApkSigner {
    override fun sign(apkPath: String): PackagingStepResult {
        val command = ApkSignerCommandBuilder.build(apkPath, config)
        val result = processRunner.run(command, workingDir)
        return PackagingStepResult(result.succeeded, if (result.succeeded) result.stdout else result.stderr)
    }
}
