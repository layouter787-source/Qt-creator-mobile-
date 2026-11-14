plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// A chave nunca fica no repositório. Os valores são fornecidos por Gradle properties
// (localmente) ou pelos GitHub Actions Secrets (CI).
val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    // O codigo continua nos pacotes com.qtcreatormobile.app.*; so o applicationId e curto.
    namespace = "com.qtcreatormobile.app"
    compileSdk = 34

    defaultConfig {
        // IMPORTANTE: precisa ter NO MAXIMO 10 caracteres. Os binarios do clang (Termux) tem
        // o caminho "/data/data/com.termux/files/usr" (31 caracteres) gravado dentro deles, e so
        // podem ser corrigidos para um caminho que caiba nesse espaco:
        // "/data/data/<applicationId>/files/usr" = 21 + tamanho do id <= 31. Ver docs/PREFIX_ALIAS.md.
        applicationId = "com.qtcm"
        minSdk = 26

        // IMPORTANTE: precisa ser 28. Desde o Android 10 (API 29), apps com targetSdk >= 29 NAO
        // podem executar arquivos da propria pasta de dados - e o clang, o aapt2 e o java baixados
        // moram la. Com 28 a execucao e permitida (e o que o Termux e a AndroidIDE fazem).
        // Consequencia: o app nao pode ser publicado na Play Store. Ver docs/EXECUTION_MODEL.md.
        targetSdk = 28
        versionCode = 1
        versionName = "0.0.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Sem credenciais, o Gradle pode compilar localmente um release nao assinado.
            // O workflow de release valida os quatro secrets antes de iniciar a compilacao.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // A API 28 e uma restricao intencional do modelo atual de execucao das toolchains.
    // Desativamos apenas este aviso do Lint para permitir gerar builds Release para instalacao
    // direta. Isto NAO torna o app elegivel para publicacao na Google Play.
    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    // Varias bibliotecas do build-engine (commons-compress, xz, json, snakeyaml) trazem os
    // mesmos arquivos de licenca em META-INF; sem excluir, o AGP falha com "2 files found
    // with path 'META-INF/...'" ao juntar os recursos Java.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/*.md",
                "META-INF/versions/**",
            )
        }
    }
}

dependencies {
    implementation(project(":build-engine"))

    // Sora Editor 0.23.6 mantém os pacotes io.github.rosemoe.sora usados pelo wrapper atual.
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.6"))
    implementation("io.github.Rosemoe.sora-editor:editor")
    implementation("io.github.Rosemoe.sora-editor:language-textmate")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
