plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.raffelino.rechenwuerfel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.raffelino.rechenwuerfel"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Das Spiel nutzt nur das Android-Framework und die Kotlin-Standardbibliothek.
    testImplementation("junit:junit:4.13.2")
    // Oberflächentests laufen als JVM-Tests mit Robolectric (kein Emulator nötig).
    testImplementation("org.robolectric:robolectric:4.14.1")
}
