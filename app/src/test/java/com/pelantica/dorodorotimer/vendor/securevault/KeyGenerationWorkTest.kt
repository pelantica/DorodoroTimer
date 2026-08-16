package com.pelantica.dorodorotimer.vendor.securevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KeyGenerationWork] のユニットテスト。検証するのは「鍵として正しいか」ではなく、
 * ANR-04 の前提＝**呼び出し側を指定時間ぶん確実に待たせること**。
 *
 * 本番の 10 秒（[SecureVaultService.KEYGEN_WORK_MILLIS]）ではなく [WORK_MILLIS] を注入して軽量に回す。
 * Binder / Service には触らない（Android 依存はユニットテストの対象外）。
 */
class KeyGenerationWorkTest {

    @Test
    fun deriveKeyMaterial_takesAtLeastTheRequestedDuration() {
        // 時間基準ループなので、端末が速くても短くならない（＝再校正が要らない）。
        val startNanos = System.nanoTime()
        KeyGenerationWork.deriveKeyMaterial(ALIAS, WORK_MILLIS)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue("指定時間より短い（${elapsedMillis}ms）", elapsedMillis >= WORK_MILLIS)
    }

    @Test
    fun deriveKeyMaterial_returnsNonEmptyHexDigest() {
        val material = KeyGenerationWork.deriveKeyMaterial(ALIAS, WORK_MILLIS)

        // SHA-256 の hex = 64文字。中身は回した回数に依存するので値そのものは検証しない。
        assertEquals(64, material.length)
        assertTrue("hex 以外の文字がある: $material", material.all { it in "0123456789abcdef" })
    }

    @Test
    fun deriveKeyMaterial_withZeroWorkMillis_stillReturnsDigest() {
        // 待ち時間 0 でも空文字を返さない（alias を必ず1回はダイジェストに通している）。
        val material = KeyGenerationWork.deriveKeyMaterial(alias = "", workMillis = 0L)

        assertEquals(64, material.length)
    }

    @Test
    fun deriveKeyMaterial_differentAliases_produceDifferentMaterial() {
        // 種が違えば結果も違う＝作業がデッドコードとして消えていないことの確認も兼ねる。
        val a = KeyGenerationWork.deriveKeyMaterial("alias-a", 0L)
        val b = KeyGenerationWork.deriveKeyMaterial("alias-b", 0L)

        assertNotEquals(a, b)
    }

    private companion object {
        /** テスト用の作業時間。本番の 10 秒は長すぎるので短縮する。 */
        const val WORK_MILLIS = 200L

        const val ALIAS = "dorodoro-test"
    }
}
