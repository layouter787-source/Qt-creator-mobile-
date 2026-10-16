package com.qtcreatormobile.buildengine

import com.qtcreatormobile.projectmodel.EntryPoint
import com.qtcreatormobile.projectmodel.Target
import com.qtcreatormobile.projectmodel.TargetSources
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CppLinkExecutorTest {

    private val config = NdkConfig(clangPath = "/toolchain/cpp/usr/bin/clang", sysrootPath = "/toolchain/cpp/sysroot")

    private val target = Target(
        id = "app",
        type = "android-app",
        minSdk = 26,
        sources = TargetSources(cpp = listOf("src/a.cpp", "src/b.cpp")),
        entryPoint = EntryPoint.NATIVE_ACTIVITY,
    )

    @Test
    fun `links all compiled object files from the graph into the final so`() {
        val graph = BuildPlanner.plan(target)
        val runner = FakeProcessRunner(ProcessResult(0, "linked ok", ""))
        val executor = CppLinkExecutor(config, runner, File("."))

        val result = executor.execute(graph.linkTasks[0], graph)

        assertTrue(result.succeeded)
        assertEquals(2, graph.compileTasks.size)
        graph.compileTasks.forEach { compileTask ->
            assertTrue(runner.lastCommand!!.contains(compileTask.outputPath))
        }
    }

    @Test
    fun `fails clearly when the link task references no known compile task`() {
        val orphanLinkTask = LinkTask("link:ghost", "ghost", setOf("nonexistent"), "build/ghost/output/lib.so")
        val emptyGraph = TaskGraph(compileTasks = emptyList(), linkTasks = listOf(orphanLinkTask))
        val runner = FakeProcessRunner(ProcessResult(0, "", ""))
        val executor = CppLinkExecutor(config, runner, File("."))

        assertFailsWith<BuildEngineException> { executor.execute(orphanLinkTask, emptyGraph) }
    }
}
