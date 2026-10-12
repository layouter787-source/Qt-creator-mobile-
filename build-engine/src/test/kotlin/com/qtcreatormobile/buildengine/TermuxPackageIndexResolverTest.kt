package com.qtcreatormobile.buildengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TermuxPackageIndexResolverTest {

    // Formato real do arquivo Packages de um repositorio apt (Termux inclusive), com Depends.
    private val fakePackagesIndex = """
        Package: bash
        Version: 5.2.21
        Filename: pool/main/b/bash/bash_5.2.21_aarch64.deb

        Package: libc++
        Version: 21.1.8
        Filename: pool/main/libc/libc++/libc++_21.1.8_aarch64.deb

        Package: libllvm
        Version: 21.1.8-3
        Depends: libc++
        Filename: pool/main/libl/libllvm/libllvm_21.1.8-3_aarch64.deb

        Package: clang
        Version: 21.1.8-3
        Depends: libllvm (= 21.1.8-3), libc++, libandroid-glob | libandroid-support
        Filename: pool/main/c/clang/clang_21.1.8-3_aarch64.deb
    """.trimIndent()

    private fun resolver() = TermuxPackageIndexResolver(TextFetcher { fakePackagesIndex })

    @Test
    fun `resolves the download url from the package's Filename field`() {
        val url = resolver().resolveDownloadUrl("https://packages.termux.dev/apt/termux-main", "clang")

        assertEquals(
            "https://packages.termux.dev/apt/termux-main/pool/main/c/clang/clang_21.1.8-3_aarch64.deb",
            url,
        )
    }

    @Test
    fun `fails clearly when the package is not in the index`() {
        assertFailsWith<BuildEngineException> {
            resolver().resolveDownloadUrl("https://packages.termux.dev/apt/termux-main", "nonexistent-package")
        }
    }

    @Test
    fun `resolves the full dependency closure with dependencies before dependents`() {
        val closure = resolver().resolveDependencyClosure("https://packages.termux.dev/apt/termux-main", "clang")

        val names = closure.packages.map { it.name }
        assertTrue(names.indexOf("libc++") < names.indexOf("libllvm"))
        assertTrue(names.indexOf("libllvm") < names.indexOf("clang"))
        assertEquals(listOf("libc++", "libllvm", "clang"), names)
    }

    @Test
    fun `reports an unresolved alternative dependency that is not in the index`() {
        val closure = resolver().resolveDependencyClosure("https://packages.termux.dev/apt/termux-main", "clang")

        // Nem libandroid-glob nem libandroid-support estao no indice de teste.
        assertTrue(closure.unresolved.contains("libandroid-glob"))
    }

    @Test
    fun `dependency closure urls point to the right filenames`() {
        val closure = resolver().resolveDependencyClosure("https://packages.termux.dev/apt/termux-main", "libllvm")

        val libcxx = closure.packages.first { it.name == "libc++" }
        assertEquals(
            "https://packages.termux.dev/apt/termux-main/pool/main/libc/libc++/libc++_21.1.8_aarch64.deb",
            libcxx.downloadUrl,
        )
    }
}
