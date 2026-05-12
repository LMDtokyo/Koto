plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("org.mozilla.rust-android-gradle.rust-android")
}

android {
    namespace  = "run.koto"
    compileSdk = 36
    ndkVersion = "27.2.12479018"  // pinned to installed NDK version

    defaultConfig {
        applicationId  = "run.koto"
        minSdk         = 26          // Android 8.0 — covers 98%+ devices
        targetSdk      = 36
        versionCode    = 1
        versionName    = "1.0.0"

        buildConfigField("String", "BASE_URL",    "\"http://10.0.2.2:8081\"")  // emulator → host gateway (8081 if host :8080 busy)
        buildConfigField("String", "WS_BASE_URL", "\"ws://10.0.2.2:9080\"")
    }

    buildTypes {
        debug {
            isDebuggable          = true
            isMinifyEnabled       = false
            buildConfigField("String", "BASE_URL",    "\"http://10.0.2.2:8081\"")
            buildConfigField("String", "WS_BASE_URL", "\"ws://10.0.2.2:9080\"")
        }
        release {
            isMinifyEnabled       = true
            isShrinkResources     = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BASE_URL",    "\"https://koto.run\"")
            buildConfigField("String", "WS_BASE_URL", "\"wss://koto.run\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }

    // ── Rust crypto core (koto-crypto crate via uniffi) ──────────────────────────
    // rust-android-gradle compiles crypto/ for all ABIs and places the .so files
    // into jniLibs/. uniffi then generates the Kotlin binding class.
    sourceSets {
        getByName("main") {
            // uniffi-generated Kotlin (one file per library: uniffi/koto_crypto/koto_crypto.kt)
            java.srcDirs("${layout.buildDirectory.get()}/generated/uniffi")
            // rust-android-gradle adds build/rustJniLibs/android/ automatically.
            // Our pre-bundled libtor.so stays in src/main/jniLibs (default).
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true   // required by tor-android-binary native .so
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        resources.excludes += "META-INF/MANIFEST.MF"
        resources.excludes += "META-INF/*.kotlin_module"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.process)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Coroutines
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.serialization.json)

    // Collections
    implementation(libs.kotlinx.collections.immutable)

    // Crypto
    implementation(libs.bouncycastle)

    // Tor: TorManager uses ProcessBuilder with bundled libtor.so if available,
    // falls back to existing SOCKS5 proxy (Orbot, InviZible, etc.)
    // To bundle tor binary: download from Guardian Project releases and place in jniLibs/

    // uniffi runtime (needed by the generated Kotlin bindings)
    implementation("net.java.dev.jna:jna:5.14.0@aar")
}

// ── Rust build via cargo (rust-android-gradle plugin) ────────────────────────
// Compiles crypto/ for arm64-v8a (+ arm and x86_64 as needed) and places
// libkoto_crypto.so into jniLibs/. Run `./gradlew cargoBuild` manually or
// let it run automatically as part of the normal build.
cargo {
    module  = "../../crypto"       // path to crypto/Cargo.toml directory
    libname = "koto_crypto"        // must match [lib] name in crypto/Cargo.toml
    targets = listOf("arm64", "x86_64")   // add "arm", "x86" for full production build
    profile = "debug"

    // After Rust build, run `uniffi-bindgen generate` to produce Kotlin bindings.
    // The generated file is placed into build/generated/uniffi/koto_crypto.kt
    // and picked up by sourceSets above.
    extraCargoBuildArguments = listOf("--features", "uniffi/cli")
}
