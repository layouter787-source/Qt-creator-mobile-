package com.qtcreatormobile.buildengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidRepositoryResolverTest {

    // Estrutura real (simplificada) do repository2-3.xml que o Android Studio/sdkmanager consultam.
    private val fakeManifest = """
        <?xml version="1.0" encoding="UTF-8"?>
        <sdk:sdk-repository xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/3">
          <remotePackage path="ndk;26.1.10909125">
            <revision><major>26</major><minor>1</minor></revision>
            <archives>
              <archive>
                <host-os>linux</host-os>
                <complete><url>android-ndk-r26b-linux.zip</url></complete>
              </archive>
            </archives>
          </remotePackage>
          <remotePackage path="ndk;27.1.12297006">
            <revision><major>27</major><minor>1</minor></revision>
            <archives>
              <archive>
                <host-os>linux</host-os>
                <complete><url>android-ndk-r27b-linux.zip</url></complete>
              </archive>
              <archive>
                <host-os>windows</host-os>
                <complete><url>android-ndk-r27b-windows.zip</url></complete>
              </archive>
            </archives>
          </remotePackage>
          <remotePackage path="build-tools;34.0.0">
            <revision><major>34</major><minor>0</minor><micro>0</micro></revision>
            <archives>
              <archive>
                <host-os>linux</host-os>
                <complete><url>build-tools_r34-linux.zip</url></complete>
              </archive>
            </archives>
          </remotePackage>
          <remotePackage path="build-tools;35.0.0">
            <revision><major>35</major><minor>0</minor><micro>0</micro></revision>
            <archives>
              <archive>
                <host-os>linux</host-os>
                <complete><url>build-tools_r35-linux.zip</url></complete>
              </archive>
            </archives>
          </remotePackage>
          <remotePackage path="build-tools;36.0.0-rc1">
            <revision><major>36</major><minor>0</minor><micro>0</micro></revision>
            <archives>
              <archive>
                <host-os>linux</host-os>
                <complete><url>build-tools_r36-rc1-linux.zip</url></complete>
              </archive>
            </archives>
          </remotePackage>
          <remotePackage path="platforms;android-34">
            <revision><major>3</major></revision>
            <archives>
              <archive>
                <complete><url>platform-34_r03.zip</url></complete>
              </archive>
            </archives>
          </remotePackage>
        </sdk:sdk-repository>
    """.trimIndent()

    @Test
    fun `resolves the highest-version ndk linux archive url`() {
        val resolver = AndroidRepositoryResolver(TextFetcher { fakeManifest })

        assertEquals(
            "https://dl.google.com/android/repository/android-ndk-r27b-linux.zip",
            resolver.resolveLatestNdkLinuxUrl(),
        )
    }

    @Test
    fun `resolves the latest stable build-tools and ignores release candidates`() {
        val resolver = AndroidRepositoryResolver(TextFetcher { fakeManifest })

        assertEquals(
            "https://dl.google.com/android/repository/build-tools_r35-linux.zip",
            resolver.resolveLatestBuildToolsUrl(),
        )
    }

    @Test
    fun `resolves a platform archive that has no host-os restriction`() {
        val resolver = AndroidRepositoryResolver(TextFetcher { fakeManifest })

        assertEquals(
            "https://dl.google.com/android/repository/platform-34_r03.zip",
            resolver.resolvePlatformUrl(34),
        )
    }

    @Test
    fun `fails clearly when the requested platform is not in the manifest`() {
        val resolver = AndroidRepositoryResolver(TextFetcher { fakeManifest })

        assertFailsWith<BuildEngineException> { resolver.resolvePlatformUrl(99) }
    }

    @Test
    fun `fails clearly when the manifest has no linux ndk archive`() {
        val emptyManifest = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sdk:sdk-repository xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/3">
              <remotePackage path="platforms;android-34"><revision><major>2</major></revision></remotePackage>
            </sdk:sdk-repository>
        """.trimIndent()
        val resolver = AndroidRepositoryResolver(TextFetcher { emptyManifest })

        assertFailsWith<BuildEngineException> { resolver.resolveLatestNdkLinuxUrl() }
    }
}
