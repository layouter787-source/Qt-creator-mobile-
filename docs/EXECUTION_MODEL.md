# Modelo de execucao: por que o app tem `targetSdk 28`

## O problema

O QT Creator Mobile baixa programas (clang, aapt2, java...) para a pasta de
dados do proprio app e os EXECUTA la. Desde o **Android 10 (API 29)**, o Android
bloqueia (SELinux) a execucao de arquivos da pasta de dados de apps que declaram
`targetSdk >= 29` - o erro e "permission denied" ao tentar rodar o binario.

Apps com `targetSdk <= 28` continuam podendo executar esses arquivos. E por isso
que o Termux e a AndroidIDE (as duas unicas referencias reais de compilar no
aparelho) usam `targetSdk 28`.

## A decisao

`targetSdk = 28` (com `minSdk = 26`). Sem isso, o setup baixaria tudo e o primeiro
build falharia na hora de executar o clang.

## Consequencias (importante para a estrategia do produto)

- **Play Store: nao da.** A Play Store exige que apps novos e atualizacoes tenham
  `targetSdk` recente (hoje 34 ou mais). Com 28 o app nao pode ser publicado la.
  Nao ha como contornar mantendo o download sob demanda das ferramentas: a unica
  alternativa seria empacotar cada executavel dentro do APK como biblioteca nativa
  (e o que o ReVanced faz com o aapt2), o que e inviavel para clang + LLVM
  (centenas de MB).
- **Distribuicao:** site proprio / GitHub Releases / F-Droid / lojas alternativas
  (ex.: Samsung Galaxy Store, Amazon Appstore, Aptoide). Isso muda as opcoes de
  monetizacao: compra via loja do Google (Play Billing) e assinaturas da Play
  nao estao disponiveis; seria preciso um sistema proprio de licenca/pagamento
  (ex.: pagamento no site + chave de ativacao) ou uma loja alternativa com cobranca propria.
- O Android 14+ ainda instala apps com `targetSdk 28` (so bloqueia abaixo de 23),
  mostrando um aviso de que o app foi feito para uma versao antiga.
- Os apps que o QT Creator Mobile GERA (como o exemplo `HelloNative`) sao
  independentes: cada um declara o seu proprio `targetSdk`.

## Ainda nao verificado em aparelho real

Que a execucao do clang/aapt2/java baixados funciona de fato nesse modelo - a
mecanica (download, extracao, patch de prefixo, montagem do pipeline) esta
coberta por testes, mas a execucao no Android so se confirma rodando o app.
