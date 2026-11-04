package com.qtcreatormobile.app.build

import java.io.File

/**
 * Projeto de exemplo (Hello World em C++ puro, `NativeActivity`): sem nenhuma
 * linha de Java/Kotlin. Serve para testar o pipeline completo no aparelho -
 * compilar com clang, linkar o .so, empacotar com aapt2 e assinar com apksigner.
 */
object SampleProject {
    const val NAME = "HelloNative"

    fun writeTo(projectDir: File) {
        File(projectDir, "src/native").mkdirs()
        File(projectDir, "qtcm.project.yaml").writeText(PROJECT_YAML)
        File(projectDir, "AndroidManifest.xml").writeText(MANIFEST)
        File(projectDir, "src/native/main.cpp").writeText(MAIN_CPP)
    }

    private val PROJECT_YAML = """
        name: HelloNative
        package: com.qtcm.hello
        version: "1.0.0"
        languages:
          - cpp
        targets:
          - id: hello
            type: android-app
            minSdk: 26
            sources:
              cpp:
                - src/native/main.cpp
            entryPoint: native-activity
    """.trimIndent() + "\n"

    // hasCode=false: o app nao tem nenhum bytecode (classes.dex), so o .so nativo.
    // O nome da biblioteca ("hello") precisa bater com o id do target (libhello.so).
    private val MANIFEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.qtcm.hello"
            android:versionCode="1"
            android:versionName="1.0">
            <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28" />
            <application android:label="HelloNative" android:hasCode="false">
                <activity android:name="android.app.NativeActivity" android:exported="true">
                    <meta-data android:name="android.app.lib_name" android:value="hello" />
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """.trimIndent() + "\n"

    private val MAIN_CPP = """
        #include <android/log.h>
        #include <android/native_activity.h>

        static void onDestroy(ANativeActivity*) {
            __android_log_print(ANDROID_LOG_INFO, "HelloNative", "onDestroy");
        }

        // Ponto de entrada que o Android procura em toda NativeActivity.
        extern "C" void ANativeActivity_onCreate(ANativeActivity* activity, void*, size_t) {
            activity->callbacks->onDestroy = onDestroy;
            __android_log_print(ANDROID_LOG_INFO, "HelloNative", "Ola de um app C++ compilado no proprio aparelho!");
        }
    """.trimIndent() + "\n"
}
