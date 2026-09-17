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
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
}
