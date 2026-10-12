package com.qtcreatormobile.buildengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GitHubReleaseResolverTest {

    private val fakeReleaseJson = """
        {
          "tag_name": "01-01-2022",
          "assets": [
            { "name": "openjdk-17-aarch64.tar.xz", "browser_download_url": "https://example.com/openjdk-17-aarch64.tar.xz" },
            { "name": "openjdk-17-armv7.tar.xz", "browser_download_url": "https://example.com/openjdk-17-armv7.tar.xz" }
          ]
        }
    """.trimIndent()

    @Test
    fun `resolves the asset url matching the requested name fragment`() {
        val resolver = GitHubReleaseResolver(TextFetcher { fakeReleaseJson })

        val url = resolver.resolveLatestAssetUrl("itsaky", "openjdk-17-android", "aarch64")

        assertEquals("https://example.com/openjdk-17-aarch64.tar.xz", url)
    }

    @Test
    fun `fails clearly when no asset matches`() {
        val resolver = GitHubReleaseResolver(TextFetcher { fakeReleaseJson })

        assertFailsWith<BuildEngineException> {
            resolver.resolveLatestAssetUrl("itsaky", "openjdk-17-android", "windows-x86_64")
        }
    }

    @Test
    fun `falls back to the next candidate fragment when the first has no match`() {
        val resolver = GitHubReleaseResolver(TextFetcher { fakeReleaseJson })

        val url = resolver.resolveLatestAssetUrlAny("itsaky", "openjdk-17-android", listOf("arm64", "armv7"))

        assertEquals("https://example.com/openjdk-17-armv7.tar.xz", url)
    }

    @Test
    fun `prefers the earlier candidate when several would match`() {
        val resolver = GitHubReleaseResolver(TextFetcher { fakeReleaseJson })

        val url = resolver.resolveLatestAssetUrlAny("itsaky", "openjdk-17-android", listOf("aarch64", "armv7"))

        assertEquals("https://example.com/openjdk-17-aarch64.tar.xz", url)
    }
}
