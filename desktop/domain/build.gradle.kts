plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.coroutines.core)
    api(libs.collections.immutable)
    api(libs.serialization.json)
}
