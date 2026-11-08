import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.memetowhats.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.memetowhats.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.9.0"

        // Solo procesadores ARM (todos los móviles actuales): Vosk trae su motor nativo para
        // cada tipo y con todos la APK pasaba de 50 MB. Para el emulador x86 habría que añadir "x86_64".
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.gif)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    // Voz a texto en el móvil para los subtítulos de los GIF. JNA tiene que ir como AAR
    // (trae las librerías nativas de Android), no como el JAR que Vosk pide por defecto.
    implementation(libs.vosk.android) { exclude(group = "net.java.dev.jna") }
    implementation(libs.jna) { artifact { type = "aar" } }
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
