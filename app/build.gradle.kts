plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciRunNumber = providers.environmentVariable("GITHUB_RUN_NUMBER")
val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_PATH")

android {
    namespace = "com.systemverktoy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.systemverktoy.app"
        minSdk = 31
        targetSdk = 35
        versionCode = ciRunNumber.orNull?.toInt() ?: 1
        versionName = ciRunNumber.orNull?.let { "1.0.$it" } ?: "1.0.0-dev"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore.orNull?.let { file(it) }
            storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.1")
}
