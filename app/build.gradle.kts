import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

data class UploadSigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun resolveUploadSigning(): UploadSigningConfig? {
    System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
        val file = File(path)
        if (file.isFile && file.length() > 100L) {
            return UploadSigningConfig(
                storeFile = file,
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android",
                keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey",
                keyPassword = System.getenv("KEY_PASSWORD") ?: "android",
            )
        }
    }
    val centralDir = File(System.getProperty("user.home"), ".android/signing")
    val centralKeystore = File(centralDir, "upload-keystore.jks")
    if (centralKeystore.isFile && centralKeystore.length() > 100L) {
        val props = Properties()
        File(centralDir, "signing.properties").takeIf { it.isFile }?.inputStream()?.use {
            props.load(it)
        }
        return UploadSigningConfig(
            storeFile = centralKeystore,
            storePassword = props.getProperty("storePassword", "android"),
            keyAlias = props.getProperty("keyAlias", "androiddebugkey"),
            keyPassword = props.getProperty("keyPassword", "android"),
        )
    }
    return null
}

val uploadSigning = resolveUploadSigning()

android {
    namespace = "com.example.expensetracker"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.expensetracker"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadSigning != null) {
            create("upload") {
                storeFile = uploadSigning.storeFile
                storePassword = uploadSigning.storePassword
                keyAlias = uploadSigning.keyAlias
                keyPassword = uploadSigning.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (uploadSigning != null) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
            isCrunchPngs = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            pickFirst("META-INF/DEPENDENCIES")
            pickFirst("META-INF/LICENSE")
            pickFirst("META-INF/NOTICE")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    lint {
        disable.add("InvalidFragmentVersionForActivityResult")
    }
}

dependencies {
    // Supabase SDK
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.1.3")
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(libs.gson)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Google Drive API dependencies
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    implementation("com.google.api-client:google-api-client-android:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
