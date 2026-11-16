package com.qtcreatormobile.buildengine

import java.io.File

/**
 * Instala o que o Packaging precisa do repositorio oficial do Android (menos o JDK, que fica em
 * [JdkInstaller], e o `aapt2` nativo, que vem do Termux via [Aapt2TermuxInstaller]):
 * `d8.jar`/`apksigner.jar` (de dentro do build-tools) e `android.jar` (da plataforma).
 * Tudo cai no mesmo `installRootDir` (`toolchain/packaging`).
 */
class PackagingToolsInstaller(
    private val platformApiLevel: Int = 34,
    private val repositoryResolver: AndroidRepositoryResolver = AndroidRepositoryResolver(),
    private val downloader: Downloader = HttpDownloader(),
    private val progressListener: DownloadProgressListener = DownloadProgressListener {},
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) : ToolchainInstaller {

    override fun install(installRootDir: File) {
        stageListener.onStageChanged(ToolchainStage.DOWNLOADING)

        val steps = listOf<Pair<String, () -> Unit>>(
            "build-tools" to {
                downloadAndExtract(repositoryResolver.resolveLatestBuildToolsUrl(), FilteredZipExtractor.buildToolsJarsOnly(), installRootDir)
            },
            "platform" to {
                downloadAndExtract(repositoryResolver.resolvePlatformUrl(platformApiLevel), FilteredZipExtractor.platformAndroidJarOnly(), installRootDir)
            },
        )

        steps.forEachIndexed { index, (_, action) ->
            action()
            progressListener.onProgress(DownloadProgress((index + 1).toLong(), steps.size.toLong()))
        }
    }

    private fun downloadAndExtract(url: String, extractor: ArchiveExtractor, installRootDir: File) {
        val stableKey = url.hashCode().toUInt().toString(16)
        val tempFile = File(installRootDir.parentFile ?: installRootDir, "packaging-$stableKey.tmp")
        var extracted = false
        try {
            downloader.download(url, tempFile, DownloadProgressListener {})
            extractor.extract(tempFile, installRootDir)
            extracted = true
        } finally {
            if (extracted) tempFile.delete()
        }
    }
}

/**
 * Instala o JDK (javac, keytool, java) via [GitHubReleaseResolver] contra
 * `itsaky/openjdk-17-android` - o build real de OpenJDK que RODA em Android
 * arm64. O formato do arquivo e detectado pela extensao da URL resolvida, e o
 * tarball vem com tudo dentro de uma pasta raiz versionada (`flattenAfterExtract`).
 */
class JdkInstaller(
    private val releaseResolver: GitHubReleaseResolver = GitHubReleaseResolver(),
    private val downloader: Downloader = HttpDownloader(),
    private val progressListener: DownloadProgressListener = DownloadProgressListener {},
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) : ToolchainInstaller {

    override fun install(installRootDir: File) {
        val url = releaseResolver.resolveLatestAssetUrlAny(
            owner = "itsaky",
            repo = "openjdk-17-android",
            assetNameFragments = listOf("aarch64", "arm64"),
        )
        RemoteToolchainInstaller(
            downloadUrl = url,
            downloader = downloader,
            extractor = ArchiveExtractors.forUrl(url),
            flattenAfterExtract = true,
            progressListener = progressListener,
            stageListener = stageListener,
        ).install(installRootDir)
    }
}
