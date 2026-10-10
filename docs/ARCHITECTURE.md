# Arquitetura - QT Creator Mobile

## Principio de engenharia (importante)

Este e um aplicativo de PRODUCAO, nao um protótipo/simulacao. Toda a logica de
runtime chama processos, rede e criptografia de verdade: `HttpDownloader` faz
requisicoes HTTP reais, `ZipArchiveExtractor`/`DebPackageExtractor` descompactam
bytes reais, `CppTaskExecutor`/`JavaTaskExecutor`/`KotlinTaskExecutor`/
`CppLinkExecutor`/os executores de packaging invocam o binario real via
`ProcessBuilder`, `DebugKeystoreProvisioner` gera um par de chaves RSA real
via `keytool`, `ApkAssembler` manipula bytes de .zip reais, e `ElfPrefixPatcher`
reescreve bytes reais de binarios ELF. Os testes unitarios usam substitutos
falsos (`FakeProcessRunner` etc.) - pratica padrao em qualquer projeto de
producao - mas nenhum desses fakes e usado fora dos testes. Quando uma URL ou
fonte de binario ainda nao foi verificada como real, isso e dito
explicitamente aqui em vez de fixada como se fosse definitiva.

Parte deste principio e revisar criticamente o que ja foi marcado como
"pronto": numa passada de revisao, tres bugs reais foram encontrados em
codigo que ja estava descrito como funcionando (ver "Bugs encontrados e
corrigidos" abaixo). Marcar algo como resolvido e depois achar um bug nele
nao e falha do processo - e o processo funcionando.

## Principio de UX (importante)

O unico trabalho que o usuario tem em qualquer setup de toolchain e ESPERAR.
O setup obrigatorio (`startCppSetup`) encadeia CINCO downloads internos -
clang + dependencias (Termux), sysroot (NDK oficial), JDK, e as ferramentas
de packaging (aapt2 + build-tools + platform) - tudo como um UNICO fluxo
silencioso. O setup do Kotlin (`startKotlinSetup`, opcional) baixa kotlinc +
plugin compilador do Compose + todas as bibliotecas Compose/Material3 do
mesmo jeito. A UI so mostra uma barra de progresso avancando ate `Installed`.
Nenhuma tela pede URL, versao, ou qualquer decisao tecnica ao usuario final.

## Modulos

1. UI/Editor (:app) - Jetpack Compose.
2. Language Services (:lang-c-cpp, :lang-java, :lang-kotlin) - ainda nao iniciados.
3. Toolchains nativas on-device (dentro de :build-engine) - binarios reais rodando no ARM64 do aparelho.
4. Build Engine (:build-engine) - `BuildPlanner` + `BuildPipeline` orquestrando tudo fim-a-fim.
5. Packaging (dentro de :build-engine) - aapt2, D8, apksigner.
6. Project Model (:project-model) - formato `qtcm.project.yaml`.

## Bugs encontrados e corrigidos (revisao de integracao)

1. **`CachingToolchainProvisioner` escrevia no diretorio errado.** Derivava o
   diretorio de instalacao como `expectedBinaryPath.parentFile`, o que so
   funciona se o binario verificado estiver DIRETO na raiz da instalacao.
   Corrigido: `installRootDir` agora e um parametro explicito, nunca derivado.
2. **`TermuxToolchainInstaller` baixava so o pacote `clang`, sem as
   dependencias dele** (`libllvm`, `libc++`, etc.) - sem elas o binario falha
   ao carregar em tempo de execucao. Corrigido com
   `TermuxPackageIndexResolver.resolveDependencyClosure` (`AptIndexParser`
   interpreta `Depends` de verdade, inclusive alternativas `a | b`).
3. **`ElfPrefixPatcher` preenchia o espaco sobrando com bytes nulos**, o que
   cortaria qualquer caminho continuando depois do prefixo
   (`.../usr/lib/libc++_shared.so`). Corrigido para preencher com barras
   (`/`) extras; passou a processar em blocos (bibliotecas passam de 100 MB)
   e o `DebPackageExtractor` passou a tratar links simbolicos corretamente.

## Origens reais dos binarios (pesquisado, nao inventado)

- **aapt2 (RESOLVIDO E LIGADO)**: `.so` nativo arm64-v8a real, do ReVanced
  Manager, via `SingleFileInstaller` dentro de `PackagingToolsInstaller`.
- **javac + keytool / JDK (RESOLVIDO E LIGADO)**: `itsaky/openjdk-17-android`
  via `JdkInstaller`.
- **d8.jar e apksigner.jar (RESOLVIDO E LIGADO)**: extraidos do build-tools
  oficial via `AndroidRepositoryResolver.resolveLatestBuildToolsUrl()` +
  `FilteredZipExtractor.buildToolsJarsOnly()`.
- **android.jar (RESOLVIDO E LIGADO)**: `AndroidRepositoryResolver.resolvePlatformUrl(34)`
  + `FilteredZipExtractor.platformAndroidJarOnly()`.
- **kotlinc + Compose + Material3 (RESOLVIDO E LIGADO)**: distribuicao
  oficial JetBrains (`kotlinc`) via `GitHubReleaseResolver`, MAIS o plugin
  compilador do Compose e as bibliotecas `androidx.compose.*`/`material3`
  baixadas via `MavenArtifactDownloader` (Google Maven / Maven Central - o
  mesmo repositorio que o Gradle usa), extraindo `classes.jar` de cada
  `.aar` com `AarClassesJarExtractor`. Versoes reais confirmadas: Kotlin
  2.4.0, Compose runtime/ui/foundation 1.12.0, Material3 1.4.0.
  `KotlinTaskExecutor` passa `JAVA_HOME` via `EnvironmentProcessRunner`
  (o script oficial `kotlinc` exige isso no ambiente, nao aceita como
  argumento).
- **clang/C++ (RESOLVIDO E LIGADO)**: cadeia completa via Termux (clang +
  dependencias reais) + NDK oficial (sysroot).

NOTA HONESTA sobre Compose/Material3: a lista de bibliotecas em
`ComposeMaterial3Artifacts` e curada a dedo (nao e um resolvedor de
dependencias transitivas completo via POM) - cobre o necessario para telas
comuns de Compose+Material3; bibliotecas adicionais (navigation,
lifecycle-viewmodel-compose, etc.) exigiriam novas entradas ate um
resolvedor transitivo de verdade existir.

## Decisao: distribuicao do toolchain (CONFIRMADA)

- **Setup obrigatorio (`startCppSetup`)**: clang+dependencias, sysroot, JDK e ferramentas de packaging - sequencia silenciosa, sem perguntar nada.
- **Kotlin (`startKotlinSetup`, opcional)**: kotlinc + Compose + Material3 completos, mesmo principio - popup pergunta-antes-de-baixar, com fallback em Configuracoes.
- Cada linguagem/ferramenta e extraida no seu proprio subdiretorio (`toolchain/cpp`, `toolchain/java`, `toolchain/kotlin`, `toolchain/packaging`).

## Estado atual

- [x] Scaffold do host app, project model (`:project-model`): parser, validacao, testes.
- [x] Build engine: `BuildPlanner`, toolchains das 3 linguagens, `CompositeTaskExecutor`, `ToolchainLayout`, `CachingToolchainProvisioner` (com `installRootDir` explicito).
- [x] Link C/C++ (`.o` -> `.so`): `CppLinkCommandBuilder` + `CppLinkExecutor`.
- [x] Packaging real: `Aapt2LinkExecutor`, `D8Executor`, `ApkSignerExecutor`, `DebugKeystoreProvisioner`, `ApkAssembler`.
- [x] `BuildPipeline`: orquestrador fim-a-fim, para na primeira falha.
- [x] Resolvedores dinamicos reais: `GitHubReleaseResolver`, `TermuxPackageIndexResolver` (+ `AptIndexParser`), `AndroidRepositoryResolver`, `MavenArtifactDownloader`.
- [x] Formatos de arquivo: `ZipArchiveExtractor`, `FilteredZipExtractor`, `TarXzExtractor`, `TarGzExtractor`, `DebPackageExtractor`, `DirectoryFlattener`, `AarClassesJarExtractor`, `ArchiveExtractors.forUrl`.
- [x] `PackagingToolsInstaller`, `JdkInstaller`, `KotlinInstaller` (com Compose+Material3 completos) implementados, testados, e ligados em `ToolchainViewModel`.
- [x] Cadeia completa de C/C++ + JDK + packaging + Kotlin/Compose/Material3, com os bugs de integracao corrigidos, ligada no `:app`.
- [x] UI de setup 100% automatica (C/C++ obrigatorio) + popup opcional (Kotlin) + `HomeScreen`.
- [ ] Keystore de RELEASE nao existe - decisao do usuario, o app nao pode gerar isso sozinho com seguranca.
- [ ] `BuildPipeline` ainda nao esta ligado a UI do `:app` - proximo passo natural.
- [ ] Nenhuma navegacao/tela de Configuracoes existe ainda no app (necessaria para o popup opcional do Kotlin ser de fato acionavel pelo usuario).

## Aviso: CI

O repositorio provavelmente nao tera permissao de escrita em `.github/workflows/` pelo conector do GitHub (confirmar). Se for o caso, o workflow precisa ser criado manualmente - usar `gradle/actions/setup-gradle` + `gradle assembleDebug`, ja que nao ha Gradle Wrapper versionado.
