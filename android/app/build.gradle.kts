plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun quotedBuildConfigValue(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val appVersionCodeValue = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull() ?: 3
val appVersionNameValue = providers.gradleProperty("appVersionName").orNull ?: "1.1.1"
val coreVersionValue = providers.gradleProperty("coreVersion").orNull ?: "8.0.3"

val releaseStoreFile = System.getenv("CLIPROXY_SIGNING_STORE_FILE")
val releaseStorePassword = System.getenv("CLIPROXY_SIGNING_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("CLIPROXY_SIGNING_KEY_ALIAS")
val releaseKeyPassword = System.getenv("CLIPROXY_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning").orNull.toBoolean()

if (requireReleaseSigning && !hasReleaseSigning) {
    throw GradleException("Release signing is required, but one or more CLIPROXY_SIGNING_* variables are missing")
}

android {
    namespace = "com.cliproxy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cliproxy"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCodeValue
        versionName = appVersionNameValue

        buildConfigField("String", "CORE_VERSION", quotedBuildConfigValue(coreVersionValue))

        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "x86_64"))
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
        buildConfig = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
