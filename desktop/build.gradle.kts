plugins {
    alias(libs.plugins.kotlin.jvm)              apply false
    alias(libs.plugins.kotlin.serialization)    apply false
    alias(libs.plugins.kotlin.compose)          apply false
    alias(libs.plugins.compose.multiplatform)   apply false
    alias(libs.plugins.sqldelight)              apply false
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlin.RequiresOptIn",
            )
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}
