import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val keystoreProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.shane.tether"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shane.tether"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.6"
    }

    // Your own signing key lives in app/keystore.properties (never committed — see keystore.properties.example).
    // Without it, the build falls back to the standard debug key so anyone can build and try the app.
    val hasKeystore = keystoreProps.getProperty("storeFile")?.let { file(it).exists() } == true

    signingConfigs {
        if (hasKeystore) {
            create("tether") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Both variants use the same key, so an APK built either way can update the other.
        getByName("debug") {
            if (hasKeystore) signingConfig = signingConfigs.getByName("tether")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasKeystore) "tether" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("org.maplibre.gl:android-sdk:11.5.2")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
