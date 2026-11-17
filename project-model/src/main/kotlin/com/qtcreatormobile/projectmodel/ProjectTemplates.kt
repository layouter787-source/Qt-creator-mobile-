package com.qtcreatormobile.projectmodel

import java.io.File

/** Um modelo de projeto que o usuario pode escolher ao criar um projeto novo. */
data class ProjectTemplate(
    val id: String,
    val title: String,
    val description: String,
    val languages: List<String>,
)

/** Regras de nome de projeto e de pacote Android. */
object ProjectNames {
    private val PROJECT_NAME = Regex("[A-Za-z][A-Za-z0-9_-]{0,39}")
    private val PACKAGE = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
    private val RESERVED = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null",
    )

    fun isValidProjectName(name: String): Boolean = PROJECT_NAME.matches(name)

    fun isValidPackage(packageName: String): Boolean =
        PACKAGE.matches(packageName) && packageName.split('.').none { it in RESERVED }

    /** Sugere um pacote a partir do nome do projeto (ex.: "Meu-App" -> "com.qtcm.meuapp"). */
    fun suggestPackage(projectName: String): String {
        val tail = projectName.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.ifEmpty { "app" }
        val segment = if (tail.first().isDigit()) "app$tail" else tail
        val candidate = "com.qtcm.$segment"
        return if (isValidPackage(candidate)) candidate else "com.qtcm.app"
    }
}

/**
 * Cria projetos novos a partir de modelos. Todo projeto tem um unico target chamado `app`
 * (a biblioteca nativa se chama `libapp.so`).
 */
object ProjectTemplates {
    const val CPP_NATIVE = "cpp-native"
    const val JAVA_CPP = "java-cpp"
    const val KOTLIN_CPP = "kotlin-cpp"
    const val KOTLIN_JAVA_CPP = "kotlin-java-cpp"
    const val JAVA = "java"
    const val KOTLIN = "kotlin"

    val all: List<ProjectTemplate> = listOf(
        ProjectTemplate(CPP_NATIVE, "C++ (NativeActivity)", "App 100% nativo, sem Java nem Kotlin.", listOf("C++")),
        ProjectTemplate(JAVA_CPP, "Java + C++", "Activity em Java chamando C++ por JNI.", listOf("Java", "C++")),
        ProjectTemplate(KOTLIN_CPP, "Kotlin + C++", "Activity em Kotlin chamando C++ por JNI.", listOf("Kotlin", "C++")),
        ProjectTemplate(KOTLIN_JAVA_CPP, "Kotlin + Java + C++", "Kotlin, uma classe Java e C++ por JNI.", listOf("Kotlin", "Java", "C++")),
        ProjectTemplate(JAVA, "Java", "App simples em Java.", listOf("Java")),
        ProjectTemplate(KOTLIN, "Kotlin", "App simples em Kotlin.", listOf("Kotlin")),
    )

    /** Cria a pasta do projeto dentro de [projectsRoot] e devolve a pasta criada. */
    fun create(templateId: String, name: String, packageName: String, projectsRoot: File): File {
        require(ProjectNames.isValidProjectName(name)) {
            "Nome de projeto invalido. Use letras, numeros, '-' ou '_' e comece com uma letra."
        }
        require(ProjectNames.isValidPackage(packageName)) {
            "Pacote invalido. Exemplo: com.exemplo.meuapp (minusculas, sem palavras reservadas do Java)."
        }
        require(all.any { it.id == templateId }) { "Modelo desconhecido: $templateId" }
        val dir = File(projectsRoot, name)
        require(!dir.exists()) { "Ja existe um projeto chamado $name." }
        try {
            dir.mkdirs()
            when (templateId) {
                CPP_NATIVE -> {
                    dir.put("qtcm.project.yaml", yaml(name, packageName, listOf("cpp"), cpp = true, java = false, kotlin = false, entry = "native-activity"))
                    dir.put("src/native/main.cpp", NATIVE_ACTIVITY_CPP)
                }
                JAVA_CPP -> {
                    dir.put("qtcm.project.yaml", yaml(name, packageName, listOf("java", "cpp"), cpp = true, java = true, kotlin = false, entry = "kotlin-wrapper"))
                    dir.put("src/native/native-lib.cpp", jniCpp(packageName))
                    dir.put("src/java/${packageName.replace('.', '/')}/MainActivity.java", javaActivity(packageName, native = true))
                }
                KOTLIN_CPP -> {
                    dir.put("qtcm.project.yaml", yaml(name, packageName, listOf("kotlin", "cpp"), cpp = true, java = false, kotlin = true, entry = "kotlin-wrapper"))
                    dir.put("src/native/native-lib.cpp", jniCpp(packageName))
                    dir.put("src/kotlin/${packageName.replace('.', '/')}/MainActivity.kt", kotlinActivity(packageName, native = true, useJavaHelper = false))
                }
                KOTLIN_JAVA_CPP -> {
                    dir.put("qtcm.project.yaml", yaml(name, packageName, listOf("kotlin", "java", "cpp"), cpp = true, java = true, kotlin = true, entry = "kotlin-wrapper"))
                    dir.put("src/native/native-lib.cpp", jniCpp(packageName))
                    dir.put("src/kotlin/${packageName.replace('.', '/')}/MainActivity.kt", kotlinActivity(packageName, native = true, useJavaHelper = true))
                    dir.put("src/java/${packageName.replace('.', '/')}/Greeter.java", greeterJava(packageName))
                }
                JAVA -> {
                    dir.put("qtcm.project.yaml", yaml(name, packageName, listOf("java"), cpp = false, java = true, kotlin = false, entry = "kotlin-wrapper"))
                    dir.put("src/java/${packageName.replace('.', '/')}/MainActivity.java", javaActivity(packageName, native = false))
                }
                KOTLIN -> {
                    dir.put("qtcm.project.yaml", yaml(name, packageName, listOf("kotlin"), cpp = false, java = false, kotlin = true, entry = "kotlin-wrapper"))
                    dir.put("src/kotlin/${packageName.replace('.', '/')}/MainActivity.kt", kotlinActivity(packageName, native = false, useJavaHelper = false))
                }
            }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return dir
    }

    private fun File.put(relative: String, text: String) {
        val target = File(this, relative)
        target.parentFile?.mkdirs()
        target.writeText(text)
    }

    private fun yaml(name: String, packageName: String, languages: List<String>, cpp: Boolean, java: Boolean, kotlin: Boolean, entry: String): String =
        buildString {
            appendLine("name: $name")
            appendLine("package: $packageName")
            appendLine("version: \"1.0.0\"")
            appendLine("languages:")
            languages.forEach { appendLine("  - $it") }
            appendLine("targets:")
            appendLine("  - id: app")
            appendLine("    type: android-app")
            appendLine("    minSdk: 26")
            appendLine("    sources:")
            if (cpp) {
                appendLine("      cpp:")
                appendLine("        - src/native/*.cpp")
                appendLine("        - src/native/**/*.cpp")
            }
            if (java) {
                appendLine("      java:")
                appendLine("        - src/java/**/*.java")
            }
            if (kotlin) {
                appendLine("      kotlin:")
                appendLine("        - src/kotlin/**/*.kt")
            }
            appendLine("    entryPoint: $entry")
        }

    /** Nome C da funcao JNI: '_' no pacote vira '_1' e '.' vira '_'. */
    internal fun jniMangle(packageName: String): String = packageName.replace("_", "_1").replace('.', '_')

    private fun jniCpp(packageName: String): String = """
        #include <jni.h>
        #include <string>

        // Chamada por MainActivity.nativeGreeting() (Java/Kotlin -> C++ via JNI).
        extern "C" JNIEXPORT jstring JNICALL
        Java_${jniMangle(packageName)}_MainActivity_nativeGreeting(JNIEnv* env, jobject /* this */) {
            std::string text = "Ola do C++ compilado no proprio aparelho!";
            return env->NewStringUTF(text.c_str());
        }
    """.trimIndent() + "\n"

    private fun javaActivity(packageName: String, native: Boolean): String = buildString {
        val text = if (native) "nativeGreeting()" else "\"Ola do Java!\""
        appendLine("package $packageName;")
        appendLine()
        appendLine("import android.app.Activity;")
        appendLine("import android.os.Bundle;")
        appendLine("import android.view.Gravity;")
        appendLine("import android.widget.TextView;")
        appendLine()
        appendLine("public class MainActivity extends Activity {")
        if (native) {
            appendLine("    static {")
            appendLine("        System.loadLibrary(\"app\");")
            appendLine("    }")
            appendLine()
            appendLine("    private native String nativeGreeting();")
            appendLine()
        }
        appendLine("    @Override")
        appendLine("    protected void onCreate(Bundle savedInstanceState) {")
        appendLine("        super.onCreate(savedInstanceState);")
        appendLine("        TextView view = new TextView(this);")
        appendLine("        view.setText($text);")
        appendLine("        view.setTextSize(22f);")
        appendLine("        view.setGravity(Gravity.CENTER);")
        appendLine("        setContentView(view);")
        appendLine("    }")
        appendLine("}")
    }

    private fun kotlinActivity(packageName: String, native: Boolean, useJavaHelper: Boolean): String = buildString {
        val base = if (native) "nativeGreeting()" else "\"Ola do Kotlin!\""
        val text = if (useJavaHelper) "Greeter.decorate($base)" else base
        appendLine("package $packageName")
        appendLine()
        appendLine("import android.app.Activity")
        appendLine("import android.os.Bundle")
        appendLine("import android.view.Gravity")
        appendLine("import android.widget.TextView")
        appendLine()
        appendLine("class MainActivity : Activity() {")
        if (native) {
            appendLine("    external fun nativeGreeting(): String")
            appendLine()
            appendLine("    companion object {")
            appendLine("        init {")
            appendLine("            System.loadLibrary(\"app\")")
            appendLine("        }")
            appendLine("    }")
            appendLine()
        }
        appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
        appendLine("        super.onCreate(savedInstanceState)")
        appendLine("        val view = TextView(this)")
        appendLine("        view.text = $text")
        appendLine("        view.textSize = 22f")
        appendLine("        view.gravity = Gravity.CENTER")
        appendLine("        setContentView(view)")
        appendLine("    }")
        appendLine("}")
    }

    private fun greeterJava(packageName: String): String = """
        package $packageName;

        /** Classe Java usada pelo Kotlin: mostra a mistura das tres linguagens. */
        public final class Greeter {
            private Greeter() {}

            public static String decorate(String text) {
                return "[Java] " + text;
            }
        }
    """.trimIndent() + "\n"

    private val NATIVE_ACTIVITY_CPP = """
        #include <android/log.h>
        #include <android/native_activity.h>
        #include <android/native_window.h>
        #include <stdint.h>

        // Pinta a janela inteira de verde assim que ela e criada.
        static void onNativeWindowCreated(ANativeActivity*, ANativeWindow* window) {
            ANativeWindow_setBuffersGeometry(window, 0, 0, WINDOW_FORMAT_RGBA_8888);
            ANativeWindow_Buffer buffer;
            if (ANativeWindow_lock(window, &buffer, nullptr) != 0) return;
            uint32_t* pixels = static_cast<uint32_t*>(buffer.bits);
            for (int y = 0; y < buffer.height; ++y) {
                for (int x = 0; x < buffer.width; ++x) {
                    pixels[y * buffer.stride + x] = 0xFF50C84A;  // A B G R
                }
            }
            ANativeWindow_unlockAndPost(window);
        }

        // Ponto de entrada que o Android procura em toda NativeActivity.
        extern "C" void ANativeActivity_onCreate(ANativeActivity* activity, void*, size_t) {
            activity->callbacks->onNativeWindowCreated = onNativeWindowCreated;
            __android_log_print(ANDROID_LOG_INFO, "QtCm", "Ola de um app C++ compilado no proprio aparelho!");
        }
    """.trimIndent() + "\n"
}
