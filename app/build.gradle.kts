import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The version and the engine version live in version.properties, which the release workflow bumps.
val versions = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val gecko: String = versions.getProperty("gecko")

// Release signing from keystore.properties (git-ignored; CI writes it from secrets). Every Void Web APK must be
// signed with the same key, or Android refuses to install it over the last one.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// GeckoView ships native code per CPU. Phones are arm64; -Pabi=x86_64 builds for the emulator instead.
val abi = (findProperty("abi") as String?) ?: "arm64-v8a"

android {
    namespace = "dev.m4s4t0.voidweb"
    // GeckoView 158 needs the Android 17 QPR2 APIs (37.2); AGP fetches that platform into the SDK if it is missing.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.m4s4t0.voidweb"
        minSdk = 29
        targetSdk = 36
        versionCode = versions.getProperty("versionCode").toInt()
        versionName = versions.getProperty("versionName")
        buildConfigField("String", "GECKO_VERSION", "\"$gecko\"")
        // Where updates come from: this repository's GitHub releases (see .github/workflows/release.yml).
        buildConfigField("String", "UPDATE_REPO", "\"${keystoreProps.getProperty("updateRepo") ?: "M4S4T0-V01D/void-web"}\"")
        ndk { abiFilters += abi }
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // GeckoView's native libraries stay compressed in the APK (smaller download; Android unpacks them once).
        jniLibs.useLegacyPackaging = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-$abi:$gecko")

    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // The icon set is no longer in the BOM; its last version still works with current Compose.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
