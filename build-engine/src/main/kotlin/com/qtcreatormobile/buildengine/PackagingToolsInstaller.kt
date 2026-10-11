package com.qtcreatormobile.buildengine

import java.io.File

/**
 * Instala tudo que o Packaging precisa (menos o JDK, que fica em [JdkInstaller]):
 * `aapt2` nativo, `d8.jar`/`apksigner.jar` (de dentro do build-tools oficial) e
 * `android.jar` (da plataforma oficial). Cada fonte tem formato e origem
 * diferentes, mas tudo cai no mesmo `installRootDir` (`toolchain/packaging`).
 */
class PackagingToolsInstaller(
    private val platformApiLevel: Int = 34,
    private val repositoryResolver: AndroidRepositoryResolver = AndroidRepositoryResolver(),
    private val downloader: Downloader = HttpDownloader(),
    private val progressListener: DownloadProgressListener = DownloadProgressListener {},
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) : ToolchainInstaller {

    companion object {
        // .so nativo real arm64-v8a do aapt2, distribuido pelo ReVanced Manager (app real, milhoes de usuarios).
        const val AAPT2_DOWNLOAD_URL =
            "https://github.com/revanced/revanced-manager/raw/main/android/app/src/main/jniLibs/arm64-v8a/libaapt2.so"
    }

    override fun install(installRootDir: File) {
        stageListener.onStageChanged(ToolchainStage.DOWNLOADING)

        val steps = listOf<Pair<String, () -> Unit>>(
            "aapt2" to { installAapt2(installRootDir) },
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

    private fun installAapt2(installRootDir: File) {
        val binDir = File(installRootDir, "bin")
        SingleFileInstaller(AAPT2_DOWNLOAD_URL, "aapt2", downloader).install(binDir)
    }

    private fun downloadAndExtract(url: String, extractor: ArchiveExtractor, installRootDir: File) {
        val tempFile = File(installRootDir.parentFile ?: installRootDir, "packaging-download-${System.nanoTime()}.tmp")
        try {
            downloader.download(url, tempFile, DownloadProgressListener {})
            extractor.extract(tempFile, installRootDir)
        } finally {
            tempFile.delete()
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
