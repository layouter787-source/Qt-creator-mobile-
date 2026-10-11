package com.qtcreatormobile.buildengine

/**
 * Conjunto curado (nao resolucao transitiva automatica de POM) das bibliotecas
 * essenciais para compilar um app Kotlin com Jetpack Compose + Material3.
 * Versoes reais e atuais, confirmadas em developer.android.com/jetpack/androidx/releases/compose
 * e no indice de plugins do Gradle (Kotlin 2.4.0 / Compose Compiler plugin 2.4.0).
 *
 * NOTA HONESTA: lista fixa, escolhida a dedo - nao e um resolvedor de
 * dependencias transitivas completo (que exigiria interpretar POMs
 * recursivamente, incluindo BOM e heranca de POM pai). Cobre o necessario para
 * telas comuns de Compose+Material3; bibliotecas adicionais exigiriam entradas
 * novas aqui ate um resolvedor transitivo de verdade existir.
 */
object ComposeMaterial3Artifacts {

    private const val ANDROIDX_ANNOTATION_VERSION = "1.9.1"
    private const val ANDROIDX_COLLECTION_VERSION = "1.5.0"
    private const val KOTLIN_STDLIB_VERSION = "2.4.0"

    /** (coordenada, "aar" ou "jar", repositorio) */
    val artifacts: List<Triple<MavenCoordinate, String, String>> = listOf(
        Triple(MavenCoordinate("org.jetbrains.kotlin", "kotlin-stdlib", KOTLIN_STDLIB_VERSION), "jar", MavenArtifactDownloader.MAVEN_CENTRAL),

        Triple(MavenCoordinate("androidx.annotation", "annotation-jvm", ANDROIDX_ANNOTATION_VERSION), "jar", MavenArtifactDownloader.MAVEN_CENTRAL),
        Triple(MavenCoordinate("androidx.collection", "collection-jvm", ANDROIDX_COLLECTION_VERSION), "jar", MavenArtifactDownloader.MAVEN_CENTRAL),

        Triple(MavenCoordinate("androidx.compose.runtime", "runtime", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.ui", "ui", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.ui", "ui-graphics", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.ui", "ui-unit", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.ui", "ui-text", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.foundation", "foundation", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.foundation", "foundation-layout", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.animation", "animation", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),

        Triple(MavenCoordinate("androidx.compose.material3", "material3", "1.4.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.material", "material-ripple", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.compose.material", "material-icons-core", "1.12.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),

        Triple(MavenCoordinate("androidx.activity", "activity-compose", "1.13.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.activity", "activity", "1.13.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.lifecycle", "lifecycle-runtime", "2.10.0"), "aar", MavenArtifactDownloader.GOOGLE_MAVEN),
        Triple(MavenCoordinate("androidx.lifecycle", "lifecycle-common", "2.10.0"), "jar", MavenArtifactDownloader.GOOGLE_MAVEN),
    )

    /** Plugin compilador do Compose para Kotlin 2.4.0 - necessario para compilar @Composable. */
    val composeCompilerPlugin = MavenCoordinate("org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", "2.4.0")
}

/**
 * Instala o Kotlin completo: `kotlinc` + plugin compilador do Compose + as
 * bibliotecas de Compose/Material3. Tudo dentro de `toolchain/kotlin/` - `bin/`
 * para o compilador, `compose-libs/` para os jars (extraidos de `.aar` quando
 * necessario) que entram no classpath de qualquer compilacao que use Compose.
 */
class KotlinInstaller(
    private val releaseResolver: GitHubReleaseResolver = GitHubReleaseResolver(),
    private val mavenDownloader: MavenArtifactDownloader = MavenArtifactDownloader(),
    private val downloader: Downloader = HttpDownloader(),
    private val progressListener: DownloadProgressListener = DownloadProgressListener {},
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) : ToolchainInstaller {

    override fun install(installRootDir: java.io.File) {
        // 1) O compilador em si - distribuicao oficial JetBrains (JVM pura).
        stageListener.onStageChanged(ToolchainStage.DOWNLOADING)
        val kotlincUrl = releaseResolver.resolveLatestAssetUrlAny(
            owner = "JetBrains", repo = "kotlin",
            assetNameFragments = listOf("kotlin-compiler-"),
        )
        RemoteToolchainInstaller(
            downloadUrl = kotlincUrl,
            downloader = downloader,
            extractor = ArchiveExtractors.forUrl(kotlincUrl),
            flattenAfterExtract = true,
            progressListener = progressListener,
            stageListener = stageListener,
        ).install(installRootDir)

        // 2) Plugin compilador do Compose + libs de Compose/Material3.
        stageListener.onStageChanged(ToolchainStage.EXTRACTING)
        val libsDir = java.io.File(installRootDir, "compose-libs")
        libsDir.mkdirs()

        downloadJarInto(libsDir, ComposeMaterial3Artifacts.composeCompilerPlugin, "jar", MavenArtifactDownloader.MAVEN_CENTRAL)

        val total = ComposeMaterial3Artifacts.artifacts.size
        ComposeMaterial3Artifacts.artifacts.forEachIndexed { index, (coordinate, packaging, repo) ->
            downloadJarInto(libsDir, coordinate, packaging, repo)
            progressListener.onProgress(DownloadProgress((index + 1).toLong(), total.toLong()))
        }
    }

    private fun downloadJarInto(libsDir: java.io.File, coordinate: MavenCoordinate, packaging: String, repo: String) {
        val finalJar = java.io.File(libsDir, "${coordinate.artifact}-${coordinate.version}.jar")
        if (finalJar.exists()) return

        if (packaging == "jar") {
            mavenDownloader.download(repo, coordinate, "jar", finalJar)
        } else {
            val aarFile = java.io.File(libsDir, "${coordinate.artifact}-${coordinate.version}.aar.tmp")
            try {
                mavenDownloader.download(repo, coordinate, "aar", aarFile)
                AarClassesJarExtractor.extractClassesJar(aarFile, finalJar)
            } finally {
                aarFile.delete()
            }
        }
    }
}
