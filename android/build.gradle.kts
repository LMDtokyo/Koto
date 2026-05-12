plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt)                apply false
    alias(libs.plugins.ksp)                 apply false
    // Mozilla rust-android-gradle: compiles the crypto/ Rust crate for all ABIs
    id("org.mozilla.rust-android-gradle.rust-android") version "0.9.4" apply false
}
