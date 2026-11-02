# QT Creator Mobile

IDE Android on-device para desenvolvimento nativo em C, C++, Java e Kotlin. O app prepara toolchains reais no dispositivo e oferece projetos locais com modelos iniciais, explorador de arquivos e editor de código.

## Estado do aplicativo

- Setup obrigatório de C/C++, JDK e ferramentas Android com progresso.
- Tela de projetos locais com modelos C++ NativeActivity, Kotlin Views e Java Views.
- Arquivo `qtcm.project.yaml` criado para cada projeto.
- Explorador dos arquivos do projeto e editor de texto com salvamento local, desfazer/refazer e localizar/substituir.
- Build no dispositivo: APKs debug simples com fontes C/C++, Java ou Kotlin; padrões glob são expandidos no build.
- Instalação do APK gerado a partir do app.
- Tela de toolchains com instalação opcional de Kotlin + Jetpack Compose + Material 3.
- Motor de compilação e empacotamento real no módulo `:build-engine`.

O build gera um manifesto Android mínimo automaticamente, compila as fontes declaradas e assina com chave debug. O target inicial do projeto é compilado. Projetos que precisem de recursos Android customizados, múltiplos targets ou dependências nativas externas ainda precisam de suporte adicional.

Os projetos ficam em `files/projects` no armazenamento privado do app. Importação/exportação por armazenamento compartilhado ainda não está implementada. A assinatura debug serve para instalar e testar, não para publicar na Play Store.

Veja `docs/ARCHITECTURE.md` para arquitetura e estado dos módulos.

## Compilar o app Android

```sh
./gradlew :app:assembleDebug
```

O APK debug fica em `app/build/outputs/apk/debug/app-debug.apk`.


## Limites atuais

O app é uma IDE móvel em desenvolvimento, com editor Sora, explorador, busca/substituição e pipeline próprio para gerar APKs simples a partir dos modelos C++ NativeActivity, Kotlin Android Views e Java Android Views. O modelo também aceita fontes C, Java e Kotlin.

Esse pipeline não executa Gradle de projetos arbitrários: ainda não resolve dependências Maven/Gradle, manifests e recursos XML do projeto, assets, múltiplos targets, plugins, flavors ou depuração. O modelo C++ NativeActivity é um esqueleto sem interface gráfica. Kotlin/Compose tem compilador e bibliotecas locais, mas o pipeline não processa o sistema de recursos do Android. Portanto, o app ainda não equivale ao Qt Creator ou ao VS Code.
