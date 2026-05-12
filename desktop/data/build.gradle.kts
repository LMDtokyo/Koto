plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
}

sqldelight {
    databases {
        create("KotoDb") {
            packageName.set("run.koto.desktop.data.local.db")
        }
    }
}

dependencies {
    api(project(":domain"))
    implementation(project(":crypto"))

    // Embedded Tor — SOCKS5 proxy for censorship circumvention and metadata hygiene.
    implementation(libs.kmp.tor.runtime)
    implementation(libs.kmp.tor.resource.exec.tor)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.json)

    implementation(libs.sqldelight.jvm.driver)
    implementation(libs.sqldelight.coroutines.extensions)

    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.slf4j.simple)
}
