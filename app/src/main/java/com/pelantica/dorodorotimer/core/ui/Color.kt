package com.pelantica.dorodorotimer.core.ui

import androidx.compose.ui.graphics.Color

// ポモドーロ＝トマトに由来する赤をアクセントにし、地は暖色寄りのニュートラルで整える。
// 直接参照するのは Theme.kt だけ。画面側は MaterialTheme.colorScheme 経由で使う。

/** アクセントのトマト赤。リング・主ボタン・選択中の強調に使う。 */
internal val TomatoRed = Color(0xFFC0392B)
internal val TomatoRedDark = Color(0xFF7B1F14)

/** 赤を薄く敷いた面。フェーズチップ・選択中タブのピルなど。 */
internal val TomatoTint = Color(0xFFF9DEDA)
internal val TomatoTintStrong = Color(0xFFFADDD8)

/** 暖色ニュートラル。地〜面〜区切り線の階調。 */
internal val WarmWhite = Color(0xFFFFFFFF)
internal val WarmBackground = Color(0xFFFBF6F3)
internal val WarmSurfaceLow = Color(0xFFFDF7F5)
internal val WarmSurface = Color(0xFFF7E9E5)
internal val WarmSurfaceHigh = Color(0xFFF2E1DD)
internal val WarmSurfaceHighest = Color(0xFFEDDAD5)
internal val WarmVariant = Color(0xFFEFE3DF)

/** 文字色。本文は赤みを含んだダークブラウン、補助はさらに退かせる。 */
internal val WarmOnSurface = Color(0xFF3B2C28)
internal val WarmOnSurfaceVariant = Color(0xFF8A736E)

/** 枠線。実線の枠と、区切り線レベルの薄い線。 */
internal val WarmOutline = Color(0xFFC9B4AF)
internal val WarmOutlineVariant = Color(0xFFE8DAD6)

/** 補助のブラウン。赤ほど主張させたくない二次要素に。 */
internal val WarmBrown = Color(0xFF7D5F58)
internal val WarmBrownDark = Color(0xFF4A322D)

/** エラー。地の暖色に馴染むよう、アクセント赤とは別の彩度にしてある。 */
internal val ErrorRed = Color(0xFFB3261E)
internal val ErrorContainer = Color(0xFFF9DEDC)
internal val OnErrorContainer = Color(0xFF410E0B)
