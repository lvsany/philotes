plugins {
    alias(libs.plugins.android.application)
}

android {
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    namespace = "com.example.philotes"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.philotes"
        minSdk = 24
        targetSdk = 36
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("org.mockito:mockito-inline:4.11.0")
        testImplementation("org.robolectric:robolectric:4.10.3")
        androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.mediapipe.genai)
    implementation("com.github.equationl.paddleocr4android:paddleocr4android:v1.2.9")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
}
