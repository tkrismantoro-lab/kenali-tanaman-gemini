package id.kenalitanaman.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.kenalitanaman.core.PlantIdentificationException
import id.kenalitanaman.core.PlantIdentifier
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Menganalisis foto di antrean satu per satu. Android hanya menjalankannya saat ada koneksi
 * internet, dan tetap berjalan walaupun aplikasi ditutup.
 */
class QueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val apiKey = PlantRepository.apiKey(context)
        // Tanpa API key tidak ada yang bisa dikerjakan; antrean dijadwalkan lagi saat key disimpan.
        if (apiKey.isEmpty()) return Result.success()

        val queue = PlantRepository.queue(context)
        val history = PlantRepository.history(context)
        val finished = mutableListOf<HistoryEntry>()

        return PlantRepository.processingLock.withLock {
            try {
                processAll(apiKey, queue, history, finished)
            } finally {
                // Tetap rapikan status walaupun worker dihentikan Android (mis. sinyal hilang).
                withContext(NonCancellable) {
                    PlantRepository.setProcessing(null)
                    PlantRepository.refresh(context)
                    if (finished.isNotEmpty()) Notifications.resultsReady(context, finished)
                }
            }
        }
    }

    private suspend fun processAll(
        apiKey: String,
        queue: QueueStore,
        history: HistoryStore,
        finished: MutableList<HistoryEntry>,
    ): Result {
        while (true) {
            val item = withContext(Dispatchers.IO) { queue.loadAll() }
                .firstOrNull { it.error == null }
                ?: return Result.success()

            PlantRepository.setProcessing(item.id)
            try {
                val entry = withContext(Dispatchers.IO) {
                    val jpeg = item.photo.readBytes()
                    val result = PlantIdentifier(apiKey).identify(jpeg)
                    // Id riwayat = waktu foto diambil, sehingga urutan riwayat tetap benar.
                    val saved = history.save(jpeg, item.thumbnail.readBytes(), result.rawJson, result.info, item.id)
                    queue.delete(item.id)
                    saved
                }
                finished += entry
            } catch (e: PlantIdentificationException) {
                if (e.retryable) {
                    // Sinyal hilang/lemah atau kuota habis: coba lagi nanti, sisanya ikut menunggu.
                    return Result.retry()
                }
                withContext(Dispatchers.IO) { queue.markFailed(item.id, e.message ?: "Identifikasi gagal.") }
            } catch (e: IOException) {
                withContext(Dispatchers.IO) { queue.markFailed(item.id, "Foto tidak dapat dibaca. Hapus lalu foto ulang.") }
            }
            PlantRepository.refresh(applicationContext)
        }
    }

    companion object {
        private const val WORK_NAME = "antrean-identifikasi"

        /**
         * Menjadwalkan pemrosesan antrean. [restartNow] = true membatalkan jeda tunggu yang sedang
         * berjalan (dipakai tombol "Coba sekarang").
         */
        fun schedule(context: Context, restartNow: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<QueueWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val policy = if (restartNow) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
