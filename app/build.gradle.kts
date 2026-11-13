import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Email service settings (Firebase Authentication) are read from local.properties, which is NOT committed.
// See EMAIL_SETUP.md in the project root. Example:
//   firebase.apiKey=AIzaSy...
//   firebase.authDomain=tally-1234.firebaseapp.com
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val firebaseApiKey: String = localProperties.getProperty("firebase.apiKey", "").trim()
val firebaseAuthDomain: String = localProperties.getProperty("firebase.authDomain", "").trim()

android {
    namespace = "com.franc1sdev.tally"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.franc1sdev.tally"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_AUTH_DOMAIN", "\"$firebaseAuthDomain\"")
        // The domain the activation / reset-password links point to (opens the app via an App Link)
        manifestPlaceholders["firebaseAuthDomain"] = firebaseAuthDomain.ifEmpty { "email-links.invalid" }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.cardview)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
