plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.falah.pvault"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.falah.pvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 10 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.4"
    }
    signingConfigs {
        create("pv") {
            storeFile = file("pvault.jks")
            storePassword = "pvault123"
            keyAlias = "pvault"
            keyPassword = "pvault123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("pv") }
        getByName("release") {
            signingConfig = signingConfigs.getByName("pv")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
}
