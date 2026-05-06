import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

val backendBaseUrlValue = (
    (project.findProperty("backendBaseUrl") as String?)
        ?: localProperties.getProperty("backendBaseUrl")
        ?: System.getenv("BACKEND_BASE_URL")
        ?: "https://baseballclassic-production.up.railway.app"
    )
val backendBaseUrl = backendBaseUrlValue.replace("\"", "\\\"")

fun isLocalBackendUrl(url: String): Boolean {
    val normalizedUrl = url.trim().lowercase()
    return normalizedUrl.contains("://10.0.2.2") ||
        normalizedUrl.contains("://localhost") ||
        normalizedUrl.contains("://127.0.0.1") ||
        normalizedUrl.contains("://0.0.0.0")
}

gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { task -> task.name.contains("Release") }
    if (buildsRelease && isLocalBackendUrl(backendBaseUrlValue)) {
        throw GradleException(
            "Release builds must use a public backend URL, but backendBaseUrl is '$backendBaseUrlValue'. " +
                "Pass -PbackendBaseUrl=https://baseballclassic-production.up.railway.app or set BACKEND_BASE_URL."
        )
    }
}

val supabaseUrl = (
    localProperties.getProperty("supabaseUrl")
        ?: System.getenv("SUPABASE_URL")
        ?: "https://snrafqoqpmtoannnnwdq.supabase.co"
    ).replace("\"", "\\\"")

val supabaseAnonKey = (
    localProperties.getProperty("supabaseAnonKey")
        ?: System.getenv("SUPABASE_ANON_KEY")
        ?: ""
    ).replace("\"", "\\\"")

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
    namespace = "com.basehaptic.mobile"
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
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.0.2"
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    
    // DataStore (for preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // Lottie for animations
    implementation("com.airbnb.android:lottie-compose:6.6.2")

    // WebSocket realtime stream
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Supabase Auth + Database
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.1"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    // Wear OS Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // In-App Update
    implementation("com.google.android.play:app-update:2.1.0")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
