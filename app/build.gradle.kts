import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Firebase Crashlytics（ANRレポートの収集）。google-services は google-services.json を読む。
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    // TODO(ANR-01): SQLDelight を再有効化する。2.1.0 は AGP 9.1.0 で BaseExtension 参照エラーになるため
    //  一旦無効化中（AGP 9 対応版が出たら戻す／またはサンプルだけ AGP を下げる）。
    //  alias(libs.plugins.sqldelight)
}

// 署名情報は keystore.properties（gitignore）から読む。無い環境では未署名 release になるだけ。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.pelantica.dorodorotimer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pelantica.dorodorotimer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // ANR 再現コードが minify で消えると教材にならないため無効のまま。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// TODO(ANR-01): SQLDelight 再有効化時にこのブロックを戻す。
//  「ライブラリがスレッドを管理してくれない」側の対比に使う永続化。生成コードは
//  com.pelantica.dorodorotimer.db に出力。src/main/sqldelight/.../Stats.sq は配置済み。
// sqldelight {
//     databases {
//         create("StatsDatabase") {
//             packageName.set("com.pelantica.dorodorotimer.db")
//         }
//     }
// }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)

    // --- DorodoroTimer 追加 ---
    // DI: demoMode の実装差し替え／②③⑦ の lazyModule 処方の土台
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    // バックグラウンドジョブ（事例⑤ の土台）
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // TODO(ANR-01): SQLDelight 再有効化時に戻す（AGP 9 未対応のため無効化中）
    // implementation(libs.sqldelight.android.driver)
    // implementation(libs.sqldelight.coroutines.extensions)
    // Firebase Crashlytics: ANRレポートを ApplicationExitInfo 経由で収集し、
    //  「ダッシュボードにANRがどう表示されるか」を実物で確認するために導入（登壇用）。
    //  analytics は Crashlytics がクラッシュ前のユーザー行動を紐付けるために併せて入れる。
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
