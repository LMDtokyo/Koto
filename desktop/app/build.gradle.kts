import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":crypto"))

    implementation(compose.desktop.currentOs)

    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.auth)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.slf4j.simple)
}

compose.desktop {
    application {
        mainClass = "run.koto.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName   = "Koto"
            packageVersion = "1.0.0"
            description   = "Koto Messenger"
            vendor        = "Koto"
            copyright     = "Koto"

            windows {
                menuGroup = "Koto"
                upgradeUuid = "4B3D4EDA-9C22-4E34-8E2E-2B0D5B3E0B7C"
            }
            macOS {
                bundleID = "run.koto.desktop"
            }
            linux {
                packageName = "koto-desktop"
            }

            modules("java.sql", "java.naming", "jdk.unsupported")
        }
    }
}
