package com.qtcreatormobile.buildengine

/** Monta o comando `aapt2 link`: gera o APK base (recursos + AndroidManifest.xml compilados). */
object Aapt2LinkCommandBuilder {

    fun build(
        manifestPath: String,
        outputApkPath: String,
        config: Aapt2Config,
    ): List<String> = listOf(
        config.aapt2Path,
        "link",
        "-I", config.androidJarPath,
        "--manifest", manifestPath,
        "-o", outputApkPath,
    )
}

/** Monta o comando que roda o d8 via JVM: `java -cp d8.jar com.android.tools.r8.D8 ...`. */
object D8CommandBuilder {

    fun build(
        classFilePaths: List<String>,
        outputDir: String,
        config: D8Config,
    ): List<String> {
        require(classFilePaths.isNotEmpty()) { "Nenhum arquivo .class fornecido para o d8." }

        return buildList {
            add(config.javaPath)
            add("-cp")
            add(config.d8JarPath)
            add("com.android.tools.r8.D8")
            add("--output")
            add(outputDir)
            addAll(classFilePaths)
        }
    }
}

/** Monta o comando que roda o apksigner via JVM: `java -jar apksigner.jar sign ...`. */
object ApkSignerCommandBuilder {

    fun build(apkPath: String, config: ApkSignerConfig): List<String> = listOf(
        config.javaPath,
        "-jar", config.apksignerJarPath,
        "sign",
        "--ks", config.keystorePath,
        "--ks-pass", "pass:${config.keystorePassword}",
        "--ks-key-alias", config.keyAlias,
        "--key-pass", "pass:${config.keyPassword}",
        apkPath,
    )
}
