import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        FileInputStream(localFile).use { load(it) }
    }
}

android {
    namespace = "io.github.hoons1994.shuttersoundzero"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.hoons1994.shuttersoundzero"
        minSdk = 30
        targetSdk = 37
        versionCode = 157
        versionName = "1.5.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")
                ?: localProperties.getProperty("RELEASE_STORE_FILE")
                ?: "../release.jks"
            val storeFileObj = file(storeFilePath)
            if (storeFileObj.exists()) {
                storeFile = storeFileObj
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    ?: localProperties.getProperty("RELEASE_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: ""
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.md}"
      }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "ShutterSoundZero_v${versionName}_${variant.name}.apk"
                }
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Standalone ADB & Wireless Pairing (100% On-Device)
  implementation(libs.libadb.android) {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
  }
  implementation(libs.bouncycastle.bcpkix)
  implementation(libs.conscrypt.android)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
