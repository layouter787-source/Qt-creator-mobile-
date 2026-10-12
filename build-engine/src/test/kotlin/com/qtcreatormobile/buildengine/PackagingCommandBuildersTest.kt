package com.qtcreatormobile.buildengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Aapt2LinkCommandBuilderTest {
    @Test
    fun `builds an aapt2 link command referencing the manifest and android jar`() {
        val config = Aapt2Config(aapt2Path = "/toolchain/packaging/bin/aapt2", androidJarPath = "/toolchain/packaging/android.jar")

        val command = Aapt2LinkCommandBuilder.build("app/AndroidManifest.xml", "build/app/output/app.apk", config)

        assertEquals("link", command[1])
        assertEquals("/toolchain/packaging/android.jar", command[command.indexOf("-I") + 1])
        assertEquals("app/AndroidManifest.xml", command[command.indexOf("--manifest") + 1])
        assertEquals("build/app/output/app.apk", command[command.indexOf("-o") + 1])
    }
}

class D8CommandBuilderTest {
    @Test
    fun `builds a d8 command invoked through java -cp with the D8 main class`() {
        val config = D8Config(javaPath = "/toolchain/java/bin/java", d8JarPath = "/toolchain/packaging/lib/d8.jar")
        val classFiles = listOf("build/app/classes/Main.class", "build/app/classes/Util.class")

        val command = D8CommandBuilder.build(classFiles, "build/app/dex", config)

        assertEquals("/toolchain/java/bin/java", command[0])
        assertEquals("-cp", command[1])
        assertEquals("/toolchain/packaging/lib/d8.jar", command[2])
        assertEquals("com.android.tools.r8.D8", command[3])
        assertEquals("build/app/dex", command[command.indexOf("--output") + 1])
        assertTrue(command.containsAll(classFiles))
    }

    @Test
    fun `rejects an empty class file list`() {
        val config = D8Config(javaPath = "/toolchain/java/bin/java", d8JarPath = "/toolchain/packaging/lib/d8.jar")
        assertFailsWith<IllegalArgumentException> { D8CommandBuilder.build(emptyList(), "build/app/dex", config) }
    }
}

class ApkSignerCommandBuilderTest {
    @Test
    fun `builds an apksigner sign command invoked through java -jar`() {
        val config = ApkSignerConfig(
            javaPath = "/toolchain/java/bin/java",
            apksignerJarPath = "/toolchain/packaging/lib/apksigner.jar",
            keystorePath = "/data/app/debug.keystore",
            keystorePassword = "android",
            keyAlias = "androiddebugkey",
            keyPassword = "android",
        )

        val command = ApkSignerCommandBuilder.build("build/app/output/app.apk", config)

        assertEquals("/toolchain/java/bin/java", command[0])
        assertEquals("-jar", command[1])
        assertEquals("/toolchain/packaging/lib/apksigner.jar", command[2])
        assertEquals("sign", command[3])
        assertEquals("/data/app/debug.keystore", command[command.indexOf("--ks") + 1])
        assertEquals("pass:android", command[command.indexOf("--ks-pass") + 1])
        assertEquals("androiddebugkey", command[command.indexOf("--ks-key-alias") + 1])
        assertEquals("build/app/output/app.apk", command.last())
    }
}

class DebugKeystoreCommandBuilderTest {
    @Test
    fun `builds a keytool command using the fixed debug credentials`() {
        val command = DebugKeystoreCommandBuilder.build("/data/app/debug.keystore", "/toolchain/java/bin/keytool")

        assertEquals("-genkeypair", command[1])
        assertEquals("/data/app/debug.keystore", command[command.indexOf("-keystore") + 1])
        assertEquals(DebugKeystoreDefaults.KEY_ALIAS, command[command.indexOf("-alias") + 1])
        assertEquals(DebugKeystoreDefaults.STORE_PASSWORD, command[command.indexOf("-storepass") + 1])
    }
}
