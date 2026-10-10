package com.qtcreatormobile.projectmodel

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.InputStream

/**
 * Carrega um `qtcm.project.yaml` e retorna um [ProjectModel] validado.
 */
object ProjectModelLoader {

    fun loadFromFile(file: File): ProjectModel {
        if (!file.exists()) {
            throw ProjectModelException("Arquivo de projeto nao encontrado: ${file.path}")
        }
        return file.inputStream().use { loadFromStream(it) }
    }

    fun loadFromStream(input: InputStream): ProjectModel {
        val yaml = Yaml()
        @Suppress("UNCHECKED_CAST")
        val root = yaml.load(input) as? Map<String, Any?>
            ?: throw ProjectModelException("qtcm.project.yaml esta vazio ou mal formatado.")

        val name = root.requireString("name")
        val packageName = root.requireString("package")
        val version = root["version"]?.toString() ?: "0.1.0"

        val languages = (root["languages"] as? List<*>)
            ?.map { Language.fromYamlValue(it.toString()) }
            ?: throw ProjectModelException("Campo 'languages' ausente ou vazio.")

        val targetsRaw = root["targets"] as? List<*>
            ?: throw ProjectModelException("Campo 'targets' ausente ou vazio.")

        val targets = targetsRaw.map { parseTarget(it as Map<String, Any?>) }

        val model = ProjectModel(
            name = name,
            packageName = packageName,
            version = version,
            languages = languages,
            targets = targets,
        )
        model.validate()
        return model
    }

    private fun parseTarget(raw: Map<String, Any?>): Target {
        val id = raw.requireString("id")
        val type = raw.requireString("type")
        val minSdk = (raw["minSdk"] as? Number)?.toInt()
            ?: throw ProjectModelException("Target '$id': campo 'minSdk' ausente ou invalido.")

        @Suppress("UNCHECKED_CAST")
        val sourcesRaw = raw["sources"] as? Map<String, Any?> ?: emptyMap()
        val sources = TargetSources(
            cpp = sourcesRaw.stringList("cpp"),
            kotlin = sourcesRaw.stringList("kotlin"),
            java = sourcesRaw.stringList("java"),
        )

        val nativeDepsRaw = raw["nativeDeps"] as? List<*> ?: emptyList<Any?>()
        val nativeDeps = nativeDepsRaw.map { depRaw ->
            @Suppress("UNCHECKED_CAST")
            val dep = depRaw as Map<String, Any?>
            NativeDependency(
                name = dep.requireString("name"),
                type = dep.requireString("type"),
                path = dep.requireString("path"),
            )
        }

        val entryPoint = EntryPoint.fromYamlValue(raw.requireString("entryPoint"))

        return Target(
            id = id,
            type = type,
            minSdk = minSdk,
            sources = sources,
            nativeDeps = nativeDeps,
            entryPoint = entryPoint,
        )
    }

    private fun Map<String, Any?>.requireString(key: String): String =
        this[key]?.toString() ?: throw ProjectModelException("Campo obrigatorio ausente: '$key'.")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (this[key] as? List<*>)?.map { it.toString() } ?: emptyList()
}
