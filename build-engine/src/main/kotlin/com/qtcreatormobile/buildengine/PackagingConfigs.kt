package com.qtcreatormobile.buildengine

data class Aapt2Config(
    val aapt2Path: String,
    /** Caminho para o android.jar (framework) contra o qual os recursos sao resolvidos. */
    val androidJarPath: String,
)

/** d8 e uma ferramenta Java: `java -cp d8.jar com.android.tools.r8.D8 ...`. */
data class D8Config(
    val javaPath: String,
    val d8JarPath: String,
)

/** apksigner tambem e uma ferramenta Java: `java -jar apksigner.jar sign ...`. */
data class ApkSignerConfig(
    val javaPath: String,
    val apksignerJarPath: String,
    val keystorePath: String,
    val keystorePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/** keytool E um binario nativo, incluso no JDK (bin/keytool). */
data class KeytoolConfig(
    val keytoolPath: String,
)
