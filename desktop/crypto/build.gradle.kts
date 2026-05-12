import java.io.File
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("run.koto.desktop.crypto.SmokeTestKt")
}

tasks.register<JavaExec>("smokeTest") {
    group       = "verification"
    description = "Load the Rust libsignal native lib via JNA and exercise generateRegistrationBundle."
    mainClass.set("run.koto.desktop.crypto.SmokeTestKt")
    classpath   = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

// Pin the SHA-256 hash of the bundled native library into `koto_crypto.sha256` so that
// NativeIntegrity can verify bytes at runtime. The hash lives in the :crypto jar alongside
// the library — attacker has to compromise the built artifact, not one file, to escape the check.
val pinNativeHash by tasks.registering {
    group       = "verification"
    description = "Compute SHA-256 of the bundled native library and write koto_crypto.sha256 next to it."

    // Capture concrete Files up-front so the task action has no references to
    // Project/Script objects (configuration cache compatibility).
    val resourcesDir = layout.projectDirectory.dir("src/main/resources").asFile
    val outputFile   = layout.buildDirectory.file("generated/native-hash/koto_crypto.sha256").get().asFile
    val win   = File(resourcesDir, "win32-x86-64/koto_crypto.dll")
    val mac   = File(resourcesDir, "darwin/libkoto_crypto.dylib")
    val lin64 = File(resourcesDir, "linux-x86-64/libkoto_crypto.so")
    val linArm = File(resourcesDir, "linux-aarch64/libkoto_crypto.so")
    // Prefer the library for the host OS (matches NativeIntegrity runtime path), not arbitrary list order.
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val candidates: List<File> = when {
        os.contains("win") -> listOf(win, mac, lin64, linArm)
        os.contains("mac") || os.contains("darwin") -> listOf(mac, linArm, lin64, win)
        else -> if (arch.contains("aarch64")) listOf(linArm, lin64, mac, win) else listOf(lin64, linArm, mac, win)
    }

    inputs.files(candidates.filter { f: File -> f.exists() })
    outputs.file(outputFile)

    doLast {
        val primary: File? = candidates.firstOrNull { f: File -> f.exists() }
        outputFile.parentFile.mkdirs()
        if (primary == null) {
            outputFile.writeText("")
            println("pinNativeHash: no native library found — integrity check will be skipped at runtime")
            return@doLast
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(primary.readBytes())
            .joinToString("") { "%02x".format(it) }
        outputFile.writeText(hash)
        println("pinNativeHash: ${primary.name} sha256=$hash")
    }
}

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/native-hash"))
tasks.named("processResources") { dependsOn(pinNativeHash) }

dependencies {
    api(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.bouncycastle)
    implementation("net.java.dev.jna:jna:5.14.0")
    // OS-native keystore: Windows Credential Manager / macOS Keychain / Linux libsecret.
    // The master AES key used by LocalAead lives here — never on disk unencrypted.
    implementation(libs.java.keyring)
    implementation("org.slf4j:slf4j-api:2.0.17")
}
