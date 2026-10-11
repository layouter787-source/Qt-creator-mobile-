package com.qtcreatormobile.buildengine

/**
 * Onde as ferramentas de empacotamento Android ficam no armazenamento do app
 * (dentro de `toolchain/packaging/`). d8 e apksigner sao jars Java; java e
 * keytool vem do JDK (subdiretorio `java`).
 */
object PackagingLayout {
    const val SUBDIR = "packaging"

    const val AAPT2_BINARY = "bin/aapt2"
    const val D8_JAR = "lib/d8.jar"
    const val APKSIGNER_JAR = "lib/apksigner.jar"
    const val ANDROID_JAR = "android.jar"
    const val DEBUG_KEYSTORE = "debug.keystore"

    /** Caminhos relativos a raiz do JDK instalado (subdiretorio `java`). */
    const val JAVA_BINARY = "bin/java"
    const val KEYTOOL_BINARY = "bin/keytool"
}
