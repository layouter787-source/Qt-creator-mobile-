# Assinatura e distribuição Android

O projeto gera dois artefactos de release:

- APK: \`app/build/outputs/apk/release/app-release.apk\`
- AAB: \`app/build/outputs/bundle/release/app-release.aab\`

## 1. Criar a chave de assinatura

Crie a chave num computador ou ambiente local de confiança. Não a gere dentro do repositório e não envie o ficheiro \`.jks\`/\`.keystore\` para o Git.

\`\`\`sh
keytool -genkeypair -v \\
  -keystore qtcm-release.jks \\
  -alias qtcm-release \\
  -keyalg RSA -keysize 4096 -validity 10000
\`\`\`

Guarde com segurança o ficheiro \`qtcm-release.jks\`, a palavra-passe do keystore, o alias e a palavra-passe da chave. Faça uma cópia de segurança offline. Perder a chave pode impedir atualizações futuras da aplicação assinadas com a mesma identidade.

## 2. Configurar os GitHub Actions Secrets

No repositório, abra **Settings → Secrets and variables → Actions → New repository secret** e crie:

| Secret | Conteúdo |
|---|---|
| \`RELEASE_KEYSTORE_BASE64\` | Keystore codificado em Base64 |
| \`RELEASE_STORE_PASSWORD\` | Palavra-passe do keystore |
| \`RELEASE_KEY_ALIAS\` | Alias, por exemplo \`qtcm-release\` |
| \`RELEASE_KEY_PASSWORD\` | Palavra-passe da chave |

Para gerar o valor Base64 sem quebras de linha:

- Linux: \`base64 -w 0 qtcm-release.jks\`
- macOS: \`base64 < qtcm-release.jks | tr -d '\\n'\`
- Termux: \`base64 -w 0 qtcm-release.jks\`

Copie o resultado diretamente para o secret \`RELEASE_KEYSTORE_BASE64\`. Nunca publique o Base64, as palavras-passe ou o keystore em issues, commits ou mensagens.

## 3. Gerar APK e AAB

Depois de configurar os quatro secrets, execute **Actions → Signed Android Release → Run workflow**. Também pode criar e enviar uma tag \`v*\`, por exemplo:

\`\`\`sh
git tag v0.0.1
git push origin v0.0.1
\`\`\`

O workflow compila, verifica as assinaturas e publica ambos os ficheiros como artefactos. Quando iniciado por uma tag, também cria uma GitHub Release com os dois ficheiros.

## 4. Compilação local opcional

Defina as propriedades Gradle \`RELEASE_STORE_FILE\`, \`RELEASE_STORE_PASSWORD\`, \`RELEASE_KEY_ALIAS\` e \`RELEASE_KEY_PASSWORD\` num ambiente local protegido e execute:

\`\`\`sh
gradle :app:assembleRelease :app:bundleRelease
\`\`\`

Não coloque credenciais em \`gradle.properties\` versionado.

## Limitação de publicação na Google Play

A geração de um AAB assinado não garante aceitação pela Play Store. O projeto mantém atualmente \`targetSdk = 28\` porque o modelo de execução dos toolchains depende disso, conforme documentado em \`docs/EXECUTION_MODEL.md\`. A publicação na Play exige cumprir o nível de API alvo vigente e as restantes políticas da loja. Não aumente o target SDK sem primeiro redesenhar e testar o isolamento/execução dos toolchains.
