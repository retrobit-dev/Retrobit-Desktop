plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI build number -> versionCode, so every GitHub build can be installed over the previous one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

// Signing: use your own keystore via env vars (GitHub secrets), otherwise fall back to the
// public keystore committed in /keystore so CI builds always share the same signature.
val ksFile = System.getenv("RB_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }
val ksPassword = System.getenv("RB_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val ksAlias = System.getenv("RB_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val ksKeyPassword = System.getenv("RB_KEY_PASSWORD")?.takeIf { it.isNotBlank() }

android {
    namespace = "dev.retrobit.desktop"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.retrobit.desktop"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("retrobit") {
            if (ksFile != null && ksPassword != null && ksAlias != null) {
                storeFile = file(ksFile)
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword ?: ksPassword
            } else {
                storeFile = rootProject.file("keystore/retrobit-public.p12")
                storeType = "pkcs12"
                storePassword = "retrobit"
                keyAlias = "retrobit"
                keyPassword = "retrobit"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("retrobit")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("retrobit")
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
