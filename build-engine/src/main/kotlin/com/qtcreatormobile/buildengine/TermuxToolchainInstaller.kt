package com.qtcreatormobile.buildengine

import java.io.File

/** Constantes reais do layout de instalacao do Termux, usadas para o patch de prefixo. */
object TermuxConstants {
    const val PREFIX = "/data/data/com.termux/files/usr"
    const val REPO_BASE_URL = "https://packages.termux.dev/apt/termux-main"
}

/**
 * [ToolchainInstaller] real para C/C++: resolve a arvore de dependencias
 * COMPLETA do `clang` no repositorio do Termux (nao so o pacote `clang` -
 * ele depende de `libllvm`, `libc++`, etc.), baixa e extrai cada `.deb`, corrige
 * links simbolicos e o prefixo `/data/data/com.termux/files/usr` gravado nos
 * binarios/bibliotecas para o diretorio real do nosso app.
 */
class TermuxToolchainInstaller(
    private val rootPackage: String = "clang",
    private val repoBaseUrl: String = TermuxConstants.REPO_BASE_URL,
    private val indexResolver: TermuxPackageIndexResolver = TermuxPackageIndexResolver(),
    private val downloader: Downloader = HttpDownloader(),
    private val progressListener: DownloadProgressListener = DownloadProgressListener {},
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) : ToolchainInstaller {

    override fun install(installRootDir: File) {
        stageListener.onStageChanged(ToolchainStage.DOWNLOADING)
        val closure = indexResolver.resolveDependencyClosure(repoBaseUrl, rootPackage)

        val usrDir = File(installRootDir, "usr")
        val symlinkRewrite = TermuxConstants.PREFIX to usrDir.absolutePath

        val totalPackages = closure.packages.size
        closure.packages.forEachIndexed { index, pkg ->
            val debFile = File(installRootDir, "download-${pkg.name}.deb")
            try {
                downloader.download(pkg.downloadUrl, debFile) { progress ->
                    // Progresso combinado: pacotes ja concluidos + fracao do atual, sobre o total.
                    val overall = (index + progress.fraction) / totalPackages
                    progressListener.onProgress(DownloadProgress((overall * 100).toLong(), 100))
                }
                DebPackageExtractor.extract(debFile, installRootDir, symlinkPrefixRewrite = symlinkRewrite)
            } finally {
                debFile.delete()
            }
        }

        stageListener.onStageChanged(ToolchainStage.EXTRACTING)
        patchAllBinaries(installRootDir)
    }

    /** O prefixo Termux pode aparecer em qualquer binario/biblioteca extraida (`usr/bin` e `usr/lib`). */
    private fun patchAllBinaries(installRootDir: File) {
        val newPrefix = File(installRootDir, "usr").absolutePath
        require(newPrefix.length <= TermuxConstants.PREFIX.length) {
            "O caminho de instalacao do app ('$newPrefix') e mais longo que o prefixo do Termux " +
                "('${TermuxConstants.PREFIX}') - o patch de binario nao e seguro nesse caso."
        }

        listOf(File(installRootDir, "usr/bin"), File(installRootDir, "usr/lib"))
            .filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && !java.nio.file.Files.isSymbolicLink(f.toPath()) } }
            .forEach { file -> ElfPrefixPatcher.patch(file, TermuxConstants.PREFIX, newPrefix) }
    }
}
