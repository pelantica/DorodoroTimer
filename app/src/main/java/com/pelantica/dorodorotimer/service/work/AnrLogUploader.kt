package com.pelantica.dorodorotimer.service.work

/**
 * ANR/クラッシュログの送信先スタブ。
 *
 * 実アプリなら Crashlytics 等のSDKや自前バックエンドへネットワーク送信する所。
 * 教材では実送信は行わず、受け取った件数を返すだけ＝明らかに軽量。ここが「重く見えない」
 * ことが重要（ANR-05 の教材ポイントは doWork/送信処理が無罪であること）。
 */
object AnrLogUploader {
    /** レポートを「送信」した体で、送信件数を返す（スタブ）。 */
    fun upload(reports: List<String>): Int {
        // 実送信なし。デモでは件数のみ返す。
        return reports.size
    }
}
