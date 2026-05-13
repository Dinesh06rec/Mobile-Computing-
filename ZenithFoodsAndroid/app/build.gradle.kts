import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun zenithProp(key: String, default: String = ""): String =
    localProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: default

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.zenithfoods.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zenithfoods.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0"

        // Emulator → host machine: 10.0.2.2. Physical device: use your PC LAN IP.
        buildConfigField("String", "API_BASE_URL", zenithProp("ZENITH_API_BASE_URL", "http://10.0.2.2:5000").asBuildConfigString())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", zenithProp("ZENITH_GOOGLE_WEB_CLIENT_ID").asBuildConfigString())
        buildConfigField("String", "FIREBASE_API_KEY", zenithProp("ZENITH_FIREBASE_API_KEY").asBuildConfigString())
        buildConfigField("String", "FIREBASE_AUTH_DOMAIN", zenithProp("ZENITH_FIREBASE_AUTH_DOMAIN").asBuildConfigString())
        buildConfigField("String", "FIREBASE_PROJECT_ID", zenithProp("ZENITH_FIREBASE_PROJECT_ID").asBuildConfigString())
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", zenithProp("ZENITH_FIREBASE_STORAGE_BUCKET").asBuildConfigString())
        buildConfigField("String", "FIREBASE_MESSAGING_SENDER_ID", zenithProp("ZENITH_FIREBASE_MESSAGING_SENDER_ID").asBuildConfigString())
        buildConfigField("String", "FIREBASE_APP_ID", zenithProp("ZENITH_FIREBASE_APP_ID").asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-auth-ktx")

    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("io.socket:socket.io-client:2.1.1") {
        exclude(group = "org.json", module = "json")
    }

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
