plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // TODO(ANR-01): SQLDelight を再有効化する。2.1.0 は AGP 9.1.0 で BaseExtension 参照エラーになるため
    //  一旦無効化中（AGP 9 対応版が出たら戻す／またはサンプルだけ AGP を下げる）。
    //  alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.tefumichangdev.dorodorotimer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tefumichangdev.dorodorotimer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // minSdk=24 でも java.time (LocalDate 等) を使えるようにする
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// TODO(ANR-01): SQLDelight 再有効化時にこのブロックを戻す。
//  「ライブラリがスレッドを管理してくれない」側の対比に使う永続化。生成コードは
//  com.tefumichangdev.dorodorotimer.db に出力。src/main/sqldelight/.../Stats.sq は配置済み。
// sqldelight {
//     databases {
//         create("StatsDatabase") {
//             packageName.set("com.tefumichangdev.dorodorotimer.db")
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)

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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
