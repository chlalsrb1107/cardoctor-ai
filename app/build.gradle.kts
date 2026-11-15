import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// API keys: TMAP_APP_KEY=..., GEMINI_API_KEY=...
// api-keys.properties holds the team keys (committed to the private team repository);
// local.properties (git-ignored) overrides them. Without keys the app falls back to sample
// places and rule-based explanations.
val localProps = Properties().apply {
    for (name in listOf("api-keys.properties", "local.properties")) {
        val f = rootProject.file(name)
        if (f.exists()) f.inputStream().use { load(it) }
    }
}
fun secret(name: String) = "\"${localProps.getProperty(name, "")}\""

android {
    namespace = "com.cardoctor.demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cardoctor.demo"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        buildConfigField("String", "TMAP_APP_KEY", secret("TMAP_APP_KEY"))
        buildConfigField("String", "GEMINI_API_KEY", secret("GEMINI_API_KEY"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Contest APK must install on the judging emulator; the debug key is enough there.
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.vehicle.sdk)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.zxing.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation("androidx.webkit:webkit:1.11.0") // serves the 3D viewer assets over https

    testImplementation(libs.junit)
}
