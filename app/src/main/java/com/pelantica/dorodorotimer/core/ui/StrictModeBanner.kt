package com.pelantica.dorodorotimer.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.core.debug.StrictModeBannerSettings
import com.pelantica.dorodorotimer.core.debug.StrictModeViolation
import com.pelantica.dorodorotimer.core.debug.StrictModeViolations

private val BannerHorizontalPadding = 16.dp
private val BannerVerticalPadding = 8.dp

/**
 * StrictMode が検出した違反を画面上部に出すバナー。デバッグビルドでしか違反が
 * 記録されないため、リリースでは何も表示されない（[StrictModeViolations] が常に空）。
 *
 * ANR 再現トグル（demoMode）とは独立していて、トグルが全部 OFF でも動く。
 * 「仕込んだ ANR」ではなく「メインスレッドI/Oに気づくための仕掛け」なので、
 * `[ANR-xx]` マーカーは付けない。
 */
@Composable
fun StrictModeBanner(modifier: Modifier = Modifier) {
    // ミュート中は表示だけ止める（検出・記録・logcat は続いている）。デモ録画用。
    val muted by StrictModeBannerSettings.muted.collectAsState()
    if (muted) return

    val violations by StrictModeViolations.violations.collectAsState()
    // ✕ を押した時点の件数。以降に新しい違反が増えたらまた出す
    // （消したまま二度と出ないと、あとから踏んだ違反に気づけない）。
    var dismissedAt by remember { mutableIntStateOf(0) }
    if (violations.size <= dismissedAt) return

    var showDetail by remember { mutableStateOf(false) }
    StrictModeBannerContent(
        modifier = modifier,
        violations = violations,
        onClick = { showDetail = true },
        onDismiss = { dismissedAt = violations.size },
    )
    if (showDetail) {
        StrictModeDetailDialog(
            violations = violations,
            onClear = {
                StrictModeViolations.clear()
                dismissedAt = 0
                showDetail = false
            },
            onClose = { showDetail = false },
        )
    }
}

@Composable
private fun StrictModeBannerContent(
    modifier: Modifier = Modifier,
    violations: List<StrictModeViolation>,
    onClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val latest = violations.last()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(
                    start = BannerHorizontalPadding,
                    end = BannerVerticalPadding,
                    top = BannerVerticalPadding,
                    bottom = BannerVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.Warning, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.strictmode_banner_title, latest.kind),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.strictmode_banner_summary,
                        stringResource(latest.kind.kindLabelRes()),
                        violations.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.strictmode_banner_hint),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.strictmode_banner_dismiss),
                )
            }
        }
    }
}

/**
 * 違反の一覧。折りたたみ状態では「番号・種類・犯人フレーム（自分のコードの最初の行）」
 * だけの省略表示にし、タップで Android が出力したスタックトレース原文に展開する。
 * 展開後を整形しないのは、「どのフレームが自分のコードか」を原文で読む練習のため。
 */
@Composable
private fun StrictModeDetailDialog(
    violations: List<StrictModeViolation>,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    // 開いた時点のスナップショットで固定する。表示中に新しい違反が来ると
    // 「新しい順」の index がずれて、展開中の項目が別の違反にすり替わるため。
    val newestFirst = remember { violations.asReversed().toList() }
    // 展開中の項目（newestFirst の index）。1件だけなら展開の手間を省いて開いておく。
    var expandedIndices by remember {
        mutableStateOf(if (newestFirst.size == 1) setOf(0) else emptySet<Int>())
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.strictmode_detail_title, newestFirst.size)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 新しいものが上（直前の操作で踏んだ違反が先頭）。番号は古い順に #1〜。
                newestFirst.forEachIndexed { index, violation ->
                    if (index > 0) HorizontalDivider()
                    val isExpanded = index in expandedIndices
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedIndices =
                                    if (isExpanded) expandedIndices - index
                                    else expandedIndices + index
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.strictmode_detail_item_title,
                                        newestFirst.size - index,
                                        violation.kind,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text = violation.culpritFrame(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) {
                                    Icons.Filled.ExpandLess
                                } else {
                                    Icons.Filled.ExpandMore
                                },
                                contentDescription = stringResource(
                                    if (isExpanded) R.string.strictmode_detail_collapse
                                    else R.string.strictmode_detail_expand
                                ),
                            )
                        }
                        if (isExpanded) {
                            Text(
                                text = violation.detail,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.strictmode_detail_close)) }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text(stringResource(R.string.strictmode_detail_clear)) }
        },
    )
}

/**
 * 自分のコードのフレーム。パッケージ名は applicationId ではなくコードのルートパッケージ
 * （debug ビルドで suffix が付いても変わらない）。リネーム時はここも追従すること。
 */
private const val APP_FRAME_PREFIX = "at com.pelantica.dorodorotimer"

/**
 * 省略表示に使う「犯人フレーム」＝トレース中で最初に現れる自分のコードの行。
 * 自分のフレームが無い場合（フレームワーク内で完結する違反）は先頭フレームで代用する。
 */
private fun StrictModeViolation.culpritFrame(): String {
    val frames = detail.lines().map { it.trim() }
    val culprit = frames.firstOrNull { it.startsWith(APP_FRAME_PREFIX) }
        ?: frames.firstOrNull { it.startsWith("at ") }
    return culprit?.removePrefix("at ") ?: kind
}

@StringRes
private fun String.kindLabelRes(): Int = when (this) {
    "DiskReadViolation" -> R.string.strictmode_kind_disk_read
    "DiskWriteViolation" -> R.string.strictmode_kind_disk_write
    "NetworkViolation" -> R.string.strictmode_kind_network
    else -> R.string.strictmode_kind_other
}

@Preview(showBackground = true)
@Composable
private fun StrictModeBannerPreview() {
    DorodoroTimerTheme {
        StrictModeBannerContent(
            violations = listOf(
                StrictModeViolation(
                    kind = "DiskWriteViolation",
                    detail = "android.os.strictmode.DiskWriteViolation\n\tat ...",
                ),
            ),
        )
    }
}
