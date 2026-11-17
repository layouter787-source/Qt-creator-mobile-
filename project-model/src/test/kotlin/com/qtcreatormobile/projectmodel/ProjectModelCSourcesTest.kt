package com.qtcreatormobile.projectmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectModelCSourcesTest {

    @Test
    fun `reads c sources next to cpp sources`() {
        val yaml = """
            name: Misto
            package: com.exemplo.misto
            version: "1.0.0"
            languages:
              - c
              - cpp
            targets:
              - id: app
                type: android-app
                minSdk: 26
                sources:
                  c:
                    - src/native/util.c
                  cpp:
                    - src/native/main.cpp
                entryPoint: native-activity
        """.trimIndent()

        val sources = ProjectModelLoader.loadFromStream(yaml.byteInputStream()).targets.single().sources

        assertEquals(listOf("src/native/util.c"), sources.c)
        assertEquals(listOf("src/native/main.cpp"), sources.cpp)
    }
}
