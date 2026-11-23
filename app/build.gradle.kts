plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.parraanimation.parraamp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.parrasoftware.fidelidac"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }
    // Firma fija: la clave la crea y guarda GitHub (rama "signing") y se reutiliza en cada compilación
    signingConfigs {
        create("fixed") {
            val ks = System.getenv("KEYSTORE_PATH")
            val pw = System.getenv("SIGNING_PASSWORD")
            if (ks != null && pw != null) {
                storeFile = file(ks)
                storePassword = pw
                keyAlias = "fidelidac"
                keyPassword = pw
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
}
