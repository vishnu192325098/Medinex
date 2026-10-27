
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.medinex"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.medinex"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Production backend HTTPS URL (deployed on Render)
        val productionBackendUrl = "https://medinex-backend-bukx.onrender.com/"
        buildConfigField("String", "PRODUCTION_BACKEND_URL", "\"$productionBackendUrl\"")

        // Centralized backend URL configuration:
        // Priority 1: Command-line property (-PbackendUrl="https://...")
        // Priority 2: local.properties entry (backendUrl=http://<IP>:8000/ or https://...)
        // Priority 3: Production Render HTTPS backend URL (removes local IP dependence)
        val localProps = Properties()
        val localPropsFile = project.rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            FileInputStream(localPropsFile).use { stream ->
                localProps.load(stream)
            }
        }
        val localBackendProp = localProps.getProperty("backendUrl")?.trim()

        val rawBackendUrl = (project.findProperty("backendUrl") as? String)?.trim()
            ?: localBackendProp
            ?: productionBackendUrl
        val configuredBackendUrl = if (rawBackendUrl.endsWith("/")) rawBackendUrl else "$rawBackendUrl/"
        buildConfigField("String", "PHYSICAL_BACKEND_URL", "\"$configuredBackendUrl\"")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Android UI
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.cardview:cardview:1.0.0")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Firebase Authentication, Cloud Firestore, and Cloud Storage
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    // Unit and Instrumentation Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
