plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.safemode.llconnect"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.safemode.llconnect"
        minSdk = 24
        targetSdk = 37
        versionCode = 8
        versionName = "1.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OpenCV ships large native libraries per ABI. The shipped (release) APK carries only
        // the two ARM ABIs real devices use; debug additionally includes x86_64 for the emulator
        // (see buildTypes below). This keeps the released APK from ballooning.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            ndk {
                abiFilters += "x86_64"
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Name the built APKs: release -> llconnect.apk, debug -> llconnect-debug.apk.
androidComponents {
    onVariants { variant ->
        val apkName = when (variant.buildType) {
            "debug" -> "llconnect-debug.apk"
            else -> "llconnect.apk"
        }
        variant.outputs
            .filterIsInstance<com.android.build.api.variant.impl.VariantOutputImpl>()
            .forEach { it.outputFileName.set(apkName) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Storage
    implementation(libs.androidx.datastore.preferences)

    // Images
    implementation(libs.coil.compose)

    // Camera + document scanning (no Google Play Services)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.opencv)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
