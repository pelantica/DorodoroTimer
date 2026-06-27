// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // TODO(ANR-01): SQLDelight は AGP 9 未対応のため無効化中。対応版が出たら戻す。
    // alias(libs.plugins.sqldelight) apply false
}
