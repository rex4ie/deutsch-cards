plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.flashcards.german"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.flashcards.german"
        minSdk = 24
        targetSdk = 35
        // CI passes the GitHub run number so every pushed build is a newer version.
        val build = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionCode = build
        versionName = "1.0.$build"
    }
    val ksFile = System.getenv("SIGNING_KEYSTORE")?.let { file(it) }
    signingConfigs {
        if (ksFile != null && ksFile.exists()) create("release") {
            storeFile = ksFile
            storeType = "pkcs12"
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = "deutsch"
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // A fixed key (from CI secrets) is required so in-app updates install over the old app.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    androidResources { noCompress += listOf("mp3", "webm") }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
