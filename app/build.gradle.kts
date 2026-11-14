import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Desde o Kotlin 2.0 o compilador do Compose vem como plugin Gradle do Kotlin;
    // composeOptions.kotlinCompilerExtensionVersion nao e mais usado.
    id("org.jetbrains.kotlin.plugin.compose")
}

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

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":build-engine"))

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Editor de codigo (Sora Editor) com realce TextMate; usado por home/SoraCodeEditor.kt.
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.6"))
    implementation("io.github.Rosemoe.sora-editor:editor")
    implementation("io.github.Rosemoe.sora-editor:language-textmate")
}
