plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    // Mesma biblioteca JSON que o :build-engine ja usa.
    implementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

kotlin {
    jvmToolchain(17)
}
