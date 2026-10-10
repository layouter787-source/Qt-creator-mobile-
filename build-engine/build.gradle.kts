plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    // api (nao implementation): quem depende de :build-engine (ex.: :app)
    // precisa enxergar tipos de :project-model, como Language, diretamente.
    api(project(":project-model"))
    // Parsing minimo da resposta JSON da API de releases do GitHub
    // (GitHubReleaseResolver) - biblioteca pequena e amplamente usada.
    implementation("org.json:json:20240303")
    // Extracao de pacotes .deb do Termux (formato ar + tar.xz) e tarballs de
    // JDK/ferramentas - bibliotecas reais e amplamente usadas, nao
    // reimplementamos parsing de arquivo binario.
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

kotlin {
    jvmToolchain(17)
}
