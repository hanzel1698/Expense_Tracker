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

// CI overrides the version code so every distributed build is a distinct
// release: Firebase App Distribution can tell builds apart, installs upgrade
// cleanly, and the in-app What's New screen re-triggers. Local builds leave
// CI_VERSION_CODE unset and use the committed values below.
val ciVersionCode = System.getenv("CI_VERSION_CODE")?.toIntOrNull()?.takeIf { it > 0 }

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
        ciVersionCode?.let { build ->
            versionCode = build
            versionName = versionName!!.substringBefore('.') + "." + build
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Pin the debug key to the keystore checked into the repo root so every
        // build — local or CI, on any machine — shares one stable signing
        // certificate. Without this, Android Gradle Plugin auto-generates a
        // fresh random debug keystore per machine/CI run, so each build gets a
        // different SHA-1 fingerprint: installs conflict ("App not installed")
        // over any previous build, and Google Sign-In's registered OAuth
        // client (matched by package name + SHA-1) stops matching.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
            // Sign the release with the Play upload key when the keystore was
            // resolved (resolveUploadSigning() found it, so the "upload" config
            // was created above). Otherwise — e.g. CI/local builds without the
            // Play secrets — fall back to the debug signing key so the release
            // APK is still installable for testing (as noted in the README).
            // Release workflows install the upload keystore, so their signing
            // is unchanged.
            signingConfig = signingConfigs.findByName("upload")
                ?: signingConfigs.getByName("debug")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(libs.gson)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Google Sign-In — used only for the account identity/gate; backup storage goes through
    // the Storage Access Framework (see SimpleGoogleDriveManager), not the Drive REST API.
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
