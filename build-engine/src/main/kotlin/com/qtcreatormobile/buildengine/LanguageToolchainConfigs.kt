package com.qtcreatormobile.buildengine

/** Configuracao do toolchain Java on-device (javac). */
data class JavaToolchainConfig(
    val javacPath: String,
    val androidJarPath: String? = null,
    val classOutputDir: String? = null,
    val projectSources: List<String> = emptyList(),
)

/**
 * Configuracao do toolchain Kotlin on-device (kotlinc).
 *
 * @param javaHome o script oficial `kotlinc` procura o Java via `JAVA_HOME` no
 *   ambiente do processo, nao aceita isso como argumento.
 * @param classpathJars jars do `-classpath` de toda compilacao (Compose/Material3/stdlib).
 * @param composeCompilerPluginJar quando presente, ativa `@Composable` via `-Xplugin=`.
 */
data class KotlinToolchainConfig(
    val kotlincPath: String,
    val javaHome: String,
    val classpathJars: List<String> = emptyList(),
    val composeCompilerPluginJar: String? = null,
    val classOutputDir: String? = null,
    val projectSources: List<String> = emptyList(),
    val javaSourceStubs: List<String> = emptyList(),
)
