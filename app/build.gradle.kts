plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ph.saklolo"
    compileSdk = 36
    defaultConfig {
        applicationId = "ph.saklolo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }
    // The AI files (git-ignored ../models) ship inside the APK, so one install is enough. Stored, not deflated.
    sourceSets["main"].assets.srcDir("../models")
    androidResources { noCompress += listOf("tflite", "onnx", "model") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("com.google.ai.edge.litert:litert:2.2.0") // EmbeddingGemma 300M on CPU
    // Offline Whisper speech input (Filipino/Taglish). Not on Maven Central: AAR from github.com/k2-fsa/sherpa-onnx releases.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // Android's org.json is a stub on the JVM
}
