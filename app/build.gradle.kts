plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlinx-serialization")
}

android {
    namespace = "com.example.progetto_yatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.progetto_yatch"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // HTTP e Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // JSON Serialization - AGGIORNATO
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ViewModel e Compose integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.runtime:runtime-livedata:1.5.4")

    // WebView integration - AGGIORNATO per telecamera
    implementation("androidx.compose.ui:ui-viewbinding:1.5.4")
    implementation("androidx.webkit:webkit:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Lottie per animazioni
    implementation("com.airbnb.android:lottie-compose:6.1.0")

    // Permessi runtime
    implementation("com.google.accompanist:accompanist-permissions:0.28.0")

    // WorkManager per monitoraggio in background
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // SharedPreferences per configurazioni
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Coil per caricamento immagini - NUOVO per stream video
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("io.coil-kt:coil-gif:2.4.0")

    // Accompanist per WebView - NUOVO
    implementation("com.google.accompanist:accompanist-webview:0.28.0")

    // SwipeRefresh - NUOVO
    implementation("com.google.accompanist:accompanist-swiperefresh:0.28.0")

    // System UI Controller - NUOVO
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.28.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}