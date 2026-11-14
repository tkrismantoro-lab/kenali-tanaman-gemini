package id.kenalitanaman.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.kenalitanaman.core.PlantIdentificationException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Analyzing : Screen
    data class Result(val entry: HistoryEntry) : Screen
}

data class UiState(
    val screen: Screen = Screen.Home,
    val history: List<HistoryEntry> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val processingId: Long? = null,
    val apiKey: String = "",
    val error: String? = null,
    /** Pesan informasi (bukan kegagalan), misalnya "foto disimpan di antrean". */
    val notice: String? = null,
)

/** Foto yang sedang dianalisis langsung; [queuedId] terisi bila pengguna memilih "analisis nanti". */
private class PendingPhoto(val jpeg: ByteArray, val thumb: ByteArray) {
    var queuedId: Long? = null
}

class PlantViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PlantRepository.PREFS, Context.MODE_PRIVATE)
    private val historyStore = PlantRepository.history(app)
    private val queueStore = PlantRepository.queue(app)

    private val _state = MutableStateFlow(
        UiState(apiKey = prefs.getString(PlantRepository.KEY_API, "").orEmpty()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pending: PendingPhoto? = null

    init {
        viewModelScope.launch {
            PlantRepository.history.collect { list -> _state.update { it.copy(history = list) } }
        }
        viewModelScope.launch {
            PlantRepository.queue.collect { list -> _state.update { it.copy(queue = list) } }
        }
        viewModelScope.launch {
            PlantRepository.processingId.collect { id -> _state.update { it.copy(processingId = id) } }
        }
        viewModelScope.launch {
            PlantRepository.refresh(app)
            // Pastikan antrean lama tetap diproses (misalnya setelah HP dinyalakan ulang).
            if (PlantRepository.queue.value.any { it.error == null }) QueueWorker.schedule(app)
        }
    }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(PlantRepository.KEY_API, trimmed).apply()
        _state.update { it.copy(apiKey = trimmed) }
        if (_state.value.queue.isNotEmpty()) QueueWorker.schedule(getApplication())
    }

    fun identify(photo: Uri) {
        if (_state.value.screen == Screen.Analyzing) return
        val app = getApplication<Application>()

        viewModelScope.launch {
            val prepared = try {
                withContext(Dispatchers.IO) {
                    val bitmap = ImageUtils.loadScaled(app, photo)
                    PendingPhoto(
                        jpeg = ImageUtils.toJpeg(bitmap),
                        thumb = ImageUtils.toJpeg(ImageUtils.thumbnail(bitmap), quality = 80),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Foto tidak dapat dibuka. Coba foto lain.")
                return@launch
            }

            if (!isOnline()) {
                addToQueue(
                    prepared,
                    "Tidak ada sinyal internet. Foto disimpan di antrean dan akan dianalisis otomatis " +
                        "saat HP mendapat sinyal, walaupun aplikasi ditutup.",
                )
                return@launch
            }

            pending = prepared
            _state.update { it.copy(screen = Screen.Analyzing, error = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    PlantRepository.identifier(app).identify(prepared.jpeg)
                }
                val queuedId = prepared.queuedId
                val entry = withContext(Dispatchers.IO) {
                    val saved = historyStore.save(
                        prepared.jpeg,
                        prepared.thumb,
                        result.rawJson,
                        result.info,
                        queuedId ?: System.currentTimeMillis(),
                    )
                    if (queuedId != null) queueStore.delete(queuedId)
                    saved
                }
                PlantRepository.refresh(app)
                // Bila pengguna sudah memilih "analisis nanti", hasilnya cukup muncul di Riwayat.
                if (queuedId == null) _state.update { it.copy(screen = Screen.Result(entry)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlantIdentificationException) {
                if (prepared.queuedId != null) return@launch // sudah di antrean, worker yang melanjutkan
                if (e.retryable) {
                    addToQueue(
                        prepared,
                        "Sinyal internet lemah. Foto disimpan di antrean dan akan dianalisis otomatis " +
                            "saat sinyal membaik.",
                    )
                } else {
                    fail(e.message ?: "Identifikasi gagal.")
                }
            } catch (e: Exception) {
                if (prepared.queuedId == null) fail("Terjadi kesalahan: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                if (pending === prepared) pending = null
            }
        }
    }

    /** Dari layar "Menganalisis…": berhenti menunggu dan pindahkan foto ke antrean. */
    fun analyzeLater() {
        val photo = pending ?: return
        if (photo.queuedId != null) return
        viewModelScope.launch {
            addToQueue(
                photo,
                "Foto disimpan di antrean. Hasilnya akan muncul di Riwayat setelah selesai dianalisis.",
            )
        }
    }

    fun retryQueued(item: QueueItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { queueStore.clearError(item.id) }
            PlantRepository.refresh(getApplication())
            QueueWorker.schedule(getApplication(), restartNow = true)
        }
    }

    fun retryQueueNow() {
        QueueWorker.schedule(getApplication(), restartNow = true)
    }

    fun deleteQueued(item: QueueItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { queueStore.delete(item.id) }
            PlantRepository.refresh(getApplication())
        }
    }

    fun open(entry: HistoryEntry) = _state.update { it.copy(screen = Screen.Result(entry)) }

    fun backToHome() = _state.update { it.copy(screen = Screen.Home) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.delete(entry.id) }
            PlantRepository.refresh(getApplication())
            _state.update { it.copy(screen = Screen.Home) }
        }
    }

    private suspend fun addToQueue(photo: PendingPhoto, notice: String) {
        val item = withContext(Dispatchers.IO) { queueStore.add(photo.jpeg, photo.thumb) }
        photo.queuedId = item.id
        PlantRepository.refresh(getApplication())
        QueueWorker.schedule(getApplication())
        _state.update { it.copy(screen = Screen.Home, notice = notice) }
    }

    private fun isOnline(): Boolean {
        val manager = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
            ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun fail(message: String) = _state.update { it.copy(screen = Screen.Home, error = message) }
}
