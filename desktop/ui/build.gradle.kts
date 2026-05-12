plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

dependencies {
    api(project(":domain"))

    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.animation)
    implementation(compose.components.resources)

    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.collections.immutable)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)

    implementation(libs.bip39)
    implementation(libs.zxing.core)

    // FileKit — KMP file picker that wraps the platform-native Common Item
    // Dialog (Win11 File Explorer-style on Windows via JNA, NSOpenPanel on
    // macOS, XDG Desktop Portal on Linux). What other Compose Multiplatform
    // apps reach for instead of fighting AWT FileDialog or hand-rolling COM.
    // Exposed as `api` so :app can call FileKit.init() at boot.
    api("io.github.vinceglb:filekit-dialogs:0.13.0")

    // JBR (JetBrains Runtime) API stubs — stays compileOnly so we don't add
    // any runtime weight; at runtime we detect whether we're actually on JBR
    // (via Class.forName("com.jetbrains.JBR")) and branch accordingly. On
    // JBR we get native Windows maximize/minimize animations via
    // WindowDecorations.CustomTitleBar (same trick IntelliJ & Jewel use).
    // On standard OpenJDK we fall back to a content-level pulse.
    compileOnly("org.jetbrains.runtime:jbr-api:1.5.0")

    implementation("io.github.alexzhirkevich:compottie:2.1.0")
    implementation("io.github.alexzhirkevich:compottie-network:2.1.0")
}
