plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autostream"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autostream"
        minSdk = 26
        targetSdk = 35
        // Build-Nummer von GitHub, damit man in Android Auto sieht, welche Version installiert ist
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.0.$run"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Fester Signaturschlüssel aus GitHub-Secrets (siehe SIGNING.md).
    // Ohne Secrets: zufälliger Debug-Schlüssel – Updates lassen sich dann nicht drüberinstallieren.
    val signingStoreFile = System.getenv("SIGNING_STORE_FILE")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    signingConfigs {
        if (!signingStoreFile.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
            create("autostream") {
                storeFile = file(signingStoreFile)
                storePassword = signingPassword
                keyAlias = "autostream"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("autostream")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("autostream") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.car.app:app-testing:1.4.0")

    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
