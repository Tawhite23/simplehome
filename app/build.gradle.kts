plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "jp.kantanhome.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.kantanhome.launcher"
        minSdk = 29          // MR01 は Android 14（API 34）
        targetSdk = 35
        versionCode = 2
        versionName = "1.2"
    }

    // ビルドのたびに署名が変わると上書きインストールできないので、固定の鍵で署名する。
    // 鍵はリポジトリには入れず、GitHub Actions のシークレットから渡す（ワークフロー参照）。
    // 環境変数がないとき（手元のビルドなど）は通常のデバッグ署名になる。
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    val keystorePassword = System.getenv("SIGNING_PASSWORD")
    val fixedSigning = if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()) {
        signingConfigs.create("fixed") {
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            keyAlias = "kantanhome"
            keyPassword = keystorePassword
        }
    } else null

    buildTypes {
        debug {
            if (fixedSigning != null) signingConfig = fixedSigning
        }
        release {
            isMinifyEnabled = false
            if (fixedSigning != null) signingConfig = fixedSigning
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
