# Project Model - QT Creator Mobile

Define o formato de arquivo de projeto usado pelo IDE para descrever o que compilar, com quais linguagens e com quais dependencias nativas.

## Arquivo: `qtcm.project.yaml` (na raiz do projeto do usuario)

```yaml
name: MeuApp
package: com.exemplo.meuapp
version: "1.0.0"

languages:
  - cpp
  - kotlin

targets:
  - id: app
    type: android-app
    minSdk: 26
    sources:
      cpp:
        - src/native/**/*.cpp
        - src/native/**/*.c
      kotlin:
        - src/kotlin/**/*.kt
    nativeDeps:
      - name: libpng
        type: prebuilt
        path: libs/arm64-v8a/libpng.so
    entryPoint: native-activity   # ou "kotlin-wrapper"
```

## Campos

- `languages`: lista das linguagens usadas neste projeto (`c`, `cpp`, `java`, `kotlin`). Determina quais toolchains o build engine precisa carregar.
- `targets`: um projeto pode ter mais de um target (ex.: app + biblioteca nativa compartilhada).
- `entryPoint`:
  - `native-activity`: gera um APK sem nenhuma linha Kotlin/Java, usando `android_native_app_glue` (NativeActivity pura).
  - `kotlin-wrapper`: gera uma `Activity` Kotlin minima que carrega a lib nativa via JNI (necessario quando o projeto mistura C/C++ com Kotlin/Java).
- `nativeDeps`: dependencias nativas pre-compiladas (.so/.a) ou buscadas de um repositorio futuro de pacotes nativos.

## Responsabilidade do modulo `:project-model`

- Parsear `qtcm.project.yaml` para uma arvore de objetos tipada (`ProjectModel`, `Target`, `NativeDependency`).
- Validar consistencia (ex.: `entryPoint: native-activity` nao pode conter fontes Kotlin/Java).
- Servir de contrato entre o Editor/UI, o Build Engine e o Packaging - nenhum desses modulos le o YAML diretamente, todos consultam o `ProjectModel` ja carregado.

## Proximos passos

- [x] Parser (`ProjectModelLoader`) em Kotlin puro (sem depender de Android SDK).
- [ ] Schema de validacao e mensagens de erro amigaveis para o usuario final do IDE (parcialmente feito).
- [ ] Decidir formato de dependencias nativas remotas (fase futura, fora do MVP).
