plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.satnamapps.shapecenterfinder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.satnamapps.shapecenterfinder"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Offline learned visual descriptors for similar-object counting.
    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    // OpenCV
    implementation("org.opencv:opencv:5.0.0.1")

    // CameraX
    val cameraxVersion = "1.4.1"

    // Core library using the Camera2 implementation
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")

    // Lifecycle library to automatically bind to lifecycle owners
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")

    // Video capture capabilities (Optional)
    implementation("androidx.camera:camera-video:$cameraxVersion")

    // View classes for PreviewView and UI features (Optional)
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Vendor extensions like Bokeh, HDR, and Night Mode (Optional)
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // Navigation dependency
    implementation("androidx.navigation:navigation-compose:2.9.3")

    // icons
    implementation("androidx.compose.material:material-icons-extended")
}