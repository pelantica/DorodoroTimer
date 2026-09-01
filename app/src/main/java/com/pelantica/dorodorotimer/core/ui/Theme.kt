package com.pelantica.dorodorotimer.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// トマト赤のアクセント＋暖色ニュートラルのライトテーマ。ダークテーマは未対応。
private val DorodoroLightColors = lightColorScheme(
    primary = TomatoRed,
    onPrimary = WarmWhite,
    primaryContainer = TomatoTint,
    onPrimaryContainer = TomatoRedDark,

    secondary = WarmBrown,
    onSecondary = WarmWhite,
    // NavigationBar の選択中インジケータがこの色。
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
