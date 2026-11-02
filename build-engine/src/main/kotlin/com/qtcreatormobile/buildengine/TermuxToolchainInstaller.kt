package com.qtcreatormobile.buildengine

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Constantes reais do layout de instalacao do Termux, usadas para o patch de prefixo. */
object TermuxConstants {
    const val PREFIX = "/data/data/com.termux/files/usr"
    const val REPO_BASE_URL = "https://packages.termux.dev/apt/termux-main"
}

/**
 * Cria (ou substitui) o link simbolico curto que faz o caminho gravado nos
 * binarios ([TermuxToolchainInstaller.prefixAlias]) apontar para a pasta real
 * onde o `usr` do Termux foi extraido.
 */
object PrefixAliasLinker {
    fun link(alias: File, target: File) {
        alias.parentFile?.mkdirs()
        // Se ja houver um link (de uma instalacao anterior), remove so o link, nao o alvo.
        Files.deleteIfExists(alias.toPath())
        Files.createSymbolicLink(alias.toPath(), target.toPath())
    }
}

/**
 * [ToolchainInstaller] real para C/C++: resolve a arvore de dependencias
 * COMPLETA do `clang` no repositorio do Termux (nao so o pacote `clang` -
 * ele depende de `libllvm`, `libc++`, etc.), baixa e extrai cada `.deb`, corrige
 * links simbolicos e o prefixo `/data/data/com.termux/files/usr` gravado nos
 * binarios/bibliotecas.
 *
 * O prefixo e trocado no proprio arquivo por um caminho de EXATAMENTE o mesmo
 * tamanho (ver [ElfPrefixPatcher]), entao o caminho novo precisa ter no maximo
 * 31 caracteres. O caminho real do app no aparelho tem ~60, por isso se usa um
 * ALIAS curto ([prefixAlias], ex.: `/data/data/com.qtcm/files/usr`) que e um link
 * simbolico para a pasta real. Isso exige um nome de pacote (applicationId)
 * curto, de ate 10 caracteres.
 *
 * @param prefixAlias caminho que sera gravado nos binarios. Se nulo, usa o proprio
 *   `<installRootDir>/usr` (so funciona se esse caminho for curto o bastante - tipico de testes).
 * @param linkAlias quando true e [prefixAlias] foi informado, cria o link simbolico
 *   alias -> `<installRootDir>/usr`.
 */
class TermuxToolchainInstaller(
    private val rootPackage: String = "clang",
    private val repoBaseUrl: String = TermuxConstants.REPO_BASE_URL,
    private val indexResolver: TermuxPackageIndexResolver = TermuxPackageIndexResolver(),
    private val downloader: Downloader = HttpDownloader(),
    private val prefixAlias: String? = null,
    private val linkAlias: Boolean = true,
    private val progressListener: DownloadProgressListener = DownloadProgressListener {},
    private val stageListener: ToolchainStageListener = ToolchainStageListener {},
) : ToolchainInstaller {

    override fun install(installRootDir: File) {
        val usrDir = File(installRootDir, "usr")
        val patchPrefix = prefixAlias ?: usrDir.absolutePath

        // Valida ANTES de baixar qualquer coisa: falhar depois de centenas de MB seria um desperdicio.
        requirePrefixFits(patchPrefix)

        // O instalador precisa funcionar sozinho, sem depender de quem o chamou ter criado a
        // pasta (os .deb sao baixados para dentro dela antes de qualquer extracao).
        installRootDir.mkdirs()

        stageListener.onStageChanged(ToolchainStage.DOWNLOADING)
        val closure = indexResolver.resolveDependencyClosure(repoBaseUrl, rootPackage)

        // Alvos absolutos de links simbolicos apontam para o diretorio REAL (qualquer tamanho serve).
        val symlinkRewrite = TermuxConstants.PREFIX to usrDir.absolutePath

        val totalPackages = closure.packages.size
        val completedPackages = File(installRootDir, ".completed-packages").apply { mkdirs() }
        closure.packages.forEachIndexed { index, pkg ->
            val marker = File(completedPackages, pkg.name)
            if (marker.exists()) {
                progressListener.onProgress(DownloadProgress((index + 1).toLong(), totalPackages.toLong()))
                return@forEachIndexed
            }
            val debFile = File(installRootDir, "download-${pkg.name}.deb")
            var extracted = false
            try {
                downloader.download(pkg.downloadUrl, debFile) { progress ->
                    // Progresso combinado: pacotes ja concluidos + fracao do atual, sobre o total.
                    val overall = (index + progress.fraction) / totalPackages
                    progressListener.onProgress(DownloadProgress((overall * 100).toLong(), 100))
                }
                verifyPackage(debFile, pkg.size, pkg.sha256)
                DebPackageExtractor.extract(debFile, installRootDir, symlinkPrefixRewrite = symlinkRewrite)
                marker.createNewFile()
                extracted = true
            } finally {
                if (extracted) {
                    debFile.delete()
                    File(debFile.parentFile, debFile.name + ".source").delete()
                }
            }
        }

        if (prefixAlias != null && linkAlias) {
            PrefixAliasLinker.link(File(prefixAlias), usrDir)
        }

        stageListener.onStageChanged(ToolchainStage.EXTRACTING)
        patchAllBinaries(installRootDir, patchPrefix)
    }

    private fun requirePrefixFits(patchPrefix: String) {
        if (patchPrefix.length > TermuxConstants.PREFIX.length) {
            throw BuildEngineException(
                "O caminho de instalacao ('$patchPrefix', ${patchPrefix.length} caracteres) nao cabe no espaco do " +
                    "prefixo do Termux (${TermuxConstants.PREFIX.length} caracteres) - o patch de binario so e seguro " +
                    "se o caminho novo couber. Use um nome de pacote (applicationId) curto, de ate 10 caracteres, " +
                    "e instale em /data/data/<pacote>/files/usr."
            )
        }
    }

    /** O prefixo pode aparecer em binarios, bibliotecas e arquivos de configuracao do clang. */
    private fun patchAllBinaries(installRootDir: File, patchPrefix: String) {
        listOf("usr/bin", "usr/lib", "usr/libexec", "usr/etc")
            .map { File(installRootDir, it) }
            .filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && !Files.isSymbolicLink(f.toPath()) } }
            .forEach { file -> ElfPrefixPatcher.patch(file, TermuxConstants.PREFIX, patchPrefix) }
    }

    private fun verifyPackage(file: File, expectedSize: Long?, expectedSha256: String?) {
        if (expectedSize != null && file.length() != expectedSize) {
            val actualSize = file.length()
            file.delete()
            File(file.parentFile, file.name + ".source").delete()
            throw BuildEngineException("O download de ${file.name} veio incompleto ($actualSize de $expectedSize bytes). Toque em Tentar novamente.")
        }
        if (expectedSha256 != null) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                file.delete()
                File(file.parentFile, file.name + ".source").delete()
                throw BuildEngineException("A verificação SHA-256 falhou para ${file.name}; o arquivo será baixado novamente ao tentar de novo.")
            }
        }
    }

}
