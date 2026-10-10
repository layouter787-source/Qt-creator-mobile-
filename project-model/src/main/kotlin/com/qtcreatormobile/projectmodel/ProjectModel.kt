package com.qtcreatormobile.projectmodel

/** Linguagens suportadas em um projeto do usuario. */
enum class Language {
    C, CPP, JAVA, KOTLIN;

    companion object {
        fun fromYamlValue(value: String): Language = when (value.lowercase()) {
            "c" -> C
            "cpp", "c++" -> CPP
            "java" -> JAVA
            "kotlin" -> KOTLIN
            else -> throw ProjectModelException("Linguagem desconhecida: '$value'")
        }
    }
}

/** Como o APK final expoe o codigo nativo ao Android. */
enum class EntryPoint {
    NATIVE_ACTIVITY,
    KOTLIN_WRAPPER;

    companion object {
        fun fromYamlValue(value: String): EntryPoint = when (value.lowercase()) {
            "native-activity" -> NATIVE_ACTIVITY
            "kotlin-wrapper" -> KOTLIN_WRAPPER
            else -> throw ProjectModelException("entryPoint desconhecido: '$value'")
        }
    }
}

data class NativeDependency(
    val name: String,
    val type: String,
    val path: String,
)

data class TargetSources(
    val cpp: List<String> = emptyList(),
    val kotlin: List<String> = emptyList(),
    val java: List<String> = emptyList(),
)

data class Target(
    val id: String,
    val type: String,
    val minSdk: Int,
    val sources: TargetSources,
    val nativeDeps: List<NativeDependency> = emptyList(),
    val entryPoint: EntryPoint,
)

data class ProjectModel(
    val name: String,
    val packageName: String,
    val version: String,
    val languages: List<Language>,
    val targets: List<Target>,
) {
    /**
     * Valida consistencia entre `entryPoint` e as fontes declaradas.
     * Um target NATIVE_ACTIVITY nao pode conter fontes Kotlin/Java, pois o
     * APK final e puramente nativo (sem ART carregando bytecode gerado pelo usuario).
     */
    fun validate() {
        for (target in targets) {
            if (target.entryPoint == EntryPoint.NATIVE_ACTIVITY) {
                if (target.sources.kotlin.isNotEmpty() || target.sources.java.isNotEmpty()) {
                    throw ProjectModelException(
                        "Target '${target.id}' usa entryPoint 'native-activity' mas declara fontes " +
                            "Kotlin/Java. Use 'kotlin-wrapper' para projetos mistos."
                    )
                }
            }
            if (target.sources.cpp.isEmpty() && target.sources.kotlin.isEmpty() && target.sources.java.isEmpty()) {
                throw ProjectModelException("Target '${target.id}' nao declara nenhum arquivo fonte.")
            }
        }
    }
}

class ProjectModelException(message: String) : Exception(message)
