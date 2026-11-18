plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Mini-App nur zur Fehlersuche: erscheint sie in Android Auto, obwohl AutoStream fehlt?
android {
    namespace = "com.autostream.probe"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.autostream.probe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
}
