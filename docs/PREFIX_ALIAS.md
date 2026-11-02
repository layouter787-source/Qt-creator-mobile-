# Alias curto do prefixo do Termux (por que o applicationId e `com.qtcm`)

## O problema

Os binarios e bibliotecas do clang do Termux tem o caminho
`/data/data/com.termux/files/usr` (31 caracteres) gravado como texto dentro
deles. Para rodar no nosso app, esse texto precisa mudar para um caminho que
exista no nosso aparelho.

A unica troca segura e **de exatamente o mesmo tamanho**: mudar o tamanho de uma
string dentro de um binario desloca tudo que vem depois e o quebra
(`ElfPrefixPatcher`). Logo o caminho novo precisa ter **no maximo 31 caracteres**
(o que sobra e preenchido com barras `/`, que o Linux trata como uma so).

O caminho real do app tem ~60 caracteres
(`/data/user/0/<pacote>/files/toolchain/cpp/usr`), entao nao cabe.

## A solucao

Um **link simbolico curto**: gravamos nos binarios
`/data/data/<applicationId>/files/usr`, e esse caminho e um link simbolico para a
pasta real (`PrefixAliasLinker`). `ToolchainViewModel` passa esse alias ao
`TermuxToolchainInstaller`; o alvo real continua em `files/toolchain/cpp/usr`.
Para caber:

    "/data/data/" (11) + applicationId + "/files/usr" (10) <= 31
    => applicationId com no maximo 10 caracteres

Por isso o `applicationId` e **`com.qtcm`** (8 caracteres). O codigo continua nos
pacotes `com.qtcreatormobile.app.*` (o `namespace` e independente do `applicationId`).

## Consequencias

- O `applicationId` NAO pode passar de 10 caracteres sem quebrar o C/C++. O
  `TermuxToolchainInstaller` confere isso **antes** de baixar qualquer coisa e
  falha com mensagem clara, em vez de desperdicar centenas de MB.
- Depois de publicar na Play Store o `applicationId` nao pode mais mudar. Se voce
  quiser outro nome (ex.: um dominio proprio), ele precisa respeitar o limite de
  10 caracteres - decida antes de publicar.
- Ainda NAO verificado em aparelho real: se o clang do Termux funciona de fato
  com o prefixo corrigido dessa forma (os testes cobrem a mecanica - extracao,
  patch, link - mas nao a execucao do clang no Android).
