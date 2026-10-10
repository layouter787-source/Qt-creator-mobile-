plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

kotlin {
    jvmToolchain(17)
}
