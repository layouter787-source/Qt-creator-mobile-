package com.qtcreatormobile.projectmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectModelLoaderTest {

    private val validYaml = """
        name: MeuApp
        package: com.exemplo.meuapp
        version: "1.0.0"
        languages:
          - cpp
          - kotlin
        targets:
          - id: app
            type: android-app
            minSdk: 26
            sources:
              cpp:
                - src/native/foo.cpp
              kotlin:
                - src/kotlin/Main.kt
            entryPoint: kotlin-wrapper
    """.trimIndent()

    @Test
    fun `parses a valid project file`() {
        val model = ProjectModelLoader.loadFromStream(validYaml.byteInputStream())
        assertEquals("MeuApp", model.name)
        assertEquals("com.exemplo.meuapp", model.packageName)
        assertEquals(listOf(Language.CPP, Language.KOTLIN), model.languages)
        assertEquals(1, model.targets.size)
        assertEquals(EntryPoint.KOTLIN_WRAPPER, model.targets[0].entryPoint)
    }

    @Test
    fun `rejects native-activity target with kotlin sources`() {
        val invalidYaml = """
            name: MeuApp
            package: com.exemplo.meuapp
            version: "1.0.0"
            languages:
              - cpp
              - kotlin
            targets:
              - id: app
                type: android-app
                minSdk: 26
                sources:
                  cpp:
                    - src/native/foo.cpp
                  kotlin:
                    - src/kotlin/Main.kt
                entryPoint: native-activity
        """.trimIndent()

        assertFailsWith<ProjectModelException> {
            ProjectModelLoader.loadFromStream(invalidYaml.byteInputStream())
        }
    }

    @Test
    fun `rejects target with no sources at all`() {
        val invalidYaml = """
            name: MeuApp
            package: com.exemplo.meuapp
            version: "1.0.0"
            languages:
              - cpp
            targets:
              - id: app
                type: android-app
                minSdk: 26
                sources: {}
                entryPoint: native-activity
        """.trimIndent()

        assertFailsWith<ProjectModelException> {
            ProjectModelLoader.loadFromStream(invalidYaml.byteInputStream())
        }
    }
}
