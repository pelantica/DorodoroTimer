package com.tefumichangdev.dorodorotimer.service.work

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ANR-05] AnrLogUploadWorker のユニットテスト（Robolectric）。
 *
 * 教材の核心: "doWork 自体は無実" を確認する。
 * WorkManager が BG でプロセスを起こすことで ANR-02 の重い onCreate が起動枠で走るが、
 * doWork() 自体は常に軽量で Result.success() を返すことをここで検証する。
 *
 * Context 取得: androidx.test:core（ApplicationProvider）は work-testing の推移的依存に含まれるが
 * Gradle では compileOnly 扱いになるため、Robolectric の RuntimeEnvironment を直接使用する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnrLogUploadWorkerTest {

    @Test
    fun doWork_alwaysReturnsSuccess() = runTest {
        // [ANR-05] doWork は ANRログ送信を行い Result.success() を返す（軽量・無実の証明）
        val context = RuntimeEnvironment.getApplication()
        val worker = TestListenableWorkerBuilder<AnrLogUploadWorker>(context).build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun anrLogUploader_upload_returnsReportCount() {
        val count = AnrLogUploader.upload(listOf("a", "b", "c"))

        assertEquals(3, count)
    }
}
