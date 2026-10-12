package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.EntryPoint
import com.qtcreatormobile.projectmodel.Target
import com.qtcreatormobile.projectmodel.TargetSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildPlannerTest {

    @Test
    fun `plans one compile task per source file plus one link task`() {
        val target = Target(
            id = "app",
            type = "android-app",
            minSdk = 26,
            sources = TargetSources(
                cpp = listOf("src/native/a.cpp", "src/native/b.cpp"),
                kotlin = listOf("src/kotlin/Main.kt"),
            ),
            entryPoint = EntryPoint.KOTLIN_WRAPPER,
        )

        val graph = BuildPlanner.plan(target)

        assertEquals(3, graph.compileTasks.size)
        assertEquals(1, graph.linkTasks.size)
        assertEquals(graph.compileTasks.map { it.id }.toSet(), graph.linkTasks[0].inputTaskIds)
    }

    @Test
    fun `execution order runs compile tasks before the link task`() {
        val target = Target(
            id = "app",
            type = "android-app",
            minSdk = 26,
            sources = TargetSources(cpp = listOf("src/native/a.cpp")),
            entryPoint = EntryPoint.NATIVE_ACTIVITY,
        )

        val graph = BuildPlanner.plan(target)
        val order = graph.executionOrder()

        val linkIndex = order.indexOf("link:app")
        val compileIndex = order.indexOf(graph.compileTasks[0].id)

        assertTrue(compileIndex < linkIndex)
    }
}
