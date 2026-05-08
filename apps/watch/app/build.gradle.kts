plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = mutableMapOf<String, String>()
rootProject.file("keystore.properties").let { file ->
    if (file.exists()) {
        file.readLines().filter { it.contains("=") && !it.trimStart().startsWith("#") }.forEach { line ->
            val (key, value) = line.split("=", limit = 2)
            keystoreProperties[key.trim()] = value.trim()
        }
    }
}

android {
    namespace = "com.basehaptic.watch"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties["storeFile"] ?: "")
            storePassword = keystoreProperties["storePassword"] ?: ""
            keyAlias = keystoreProperties["keyAlias"] ?: ""
            keyPassword = keystoreProperties["keyPassword"] ?: ""
        }
    }

    defaultConfig {
        applicationId = "com.basehaptic.mobile"
        minSdk = 30  // Wear OS 3.0+
        targetSdk = 35
        versionCode = 20
        versionName = "1.0.3"

        val backendBaseUrl = (
            System.getenv("BACKEND_BASE_URL")
                ?: "https://baseballclassic-production.up.railway.app"
            ).replace("\"", "\\\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Wear OS
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    
    // Wear OS specific
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    
    // Compose for Wear OS
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    
    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    // Icons
    implementation("androidx.compose.material:material-icons-extended")
    
    // Horologist (Wear OS best practices)
    implementation("com.google.android.horologist:horologist-compose-layout:0.6.20")
    implementation("com.google.android.horologist:horologist-compose-material:0.6.20")

    // Ongoing Activity (prevents system kill, shows on watch face)
    implementation("androidx.wear:wear-ongoing:1.1.0")

    // Tiles (swipe-left panel)
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.tiles:tiles-material:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.1")

    // Guava (required by Tiles ListenableFuture)
    implementation("com.google.guava:guava:33.0.0-android")

    // Video playback for home-run transition clip
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Testing
    debugImplementation("androidx.compose.ui:ui-tooling")
}
