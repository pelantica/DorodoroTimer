package com.pelantica.dorodorotimer.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// トマト赤のアクセント＋暖色ニュートラルのライトテーマ。
// ダークテーマは未対応（端末がダークでもこの配色のまま）。必要になったら darkColorScheme を足す。
private val DorodoroLightColors = lightColorScheme(
    primary = TomatoRed,
    onPrimary = WarmWhite,
    primaryContainer = TomatoTint,
    onPrimaryContainer = TomatoRedDark,

    secondary = WarmBrown,
    onSecondary = WarmWhite,
    // NavigationBar の選択中インジケータがこの色。タブのピルを赤寄りにするためここに置く。
    secondaryContainer = TomatoTintStrong,
    onSecondaryContainer = TomatoRedDark,

    tertiary = WarmBrown,
    onTertiary = WarmWhite,
    tertiaryContainer = WarmSurfaceHigh,
    onTertiaryContainer = WarmBrownDark,

    background = WarmBackground,
    onBackground = WarmOnSurface,
    surface = WarmBackground,
    onSurface = WarmOnSurface,
    surfaceVariant = WarmVariant,
    onSurfaceVariant = WarmOnSurfaceVariant,

    // surfaceContainer 系は M3 コンポーネントの既定色に効く。
    // 白カード＝Lowest、ボトムナビの帯＝surfaceContainer。
    surfaceContainerLowest = WarmWhite,
    surfaceContainerLow = WarmSurfaceLow,
    surfaceContainer = WarmSurface,
    surfaceContainerHigh = WarmSurfaceHigh,
    surfaceContainerHighest = WarmSurfaceHighest,

    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,

    error = ErrorRed,
    onError = WarmWhite,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

@Composable
fun DorodoroTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DorodoroLightColors,
        content = content,
    )
}
