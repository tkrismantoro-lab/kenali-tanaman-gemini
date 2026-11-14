package id.kenalitanaman.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.kenalitanaman.core.PlantIdentificationException
import id.kenalitanaman.core.PlantIdentifier
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
    val apiKey: String = "",
    val error: String? = null,
)

class PlantViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val store = HistoryStore(app)

    private val _state = MutableStateFlow(UiState(apiKey = prefs.getString(KEY_API, "").orEmpty()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { store.loadAll() }
            _state.update { it.copy(history = history) }
        }
    }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_API, trimmed).apply()
        _state.update { it.copy(apiKey = trimmed) }
    }

    fun identify(photo: Uri) {
        val apiKey = _state.value.apiKey
        if (apiKey.isEmpty()) {
            _state.update { it.copy(error = "Masukkan API key Gemini di Pengaturan terlebih dahulu.") }
            return
        }
        if (_state.value.screen == Screen.Analyzing) return
        _state.update { it.copy(screen = Screen.Analyzing, error = null) }

        viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) {
                    val bitmap = ImageUtils.loadScaled(getApplication(), photo)
                    val jpeg = ImageUtils.toJpeg(bitmap)
                    val result = PlantIdentifier(apiKey).identify(jpeg)
                    val thumb = ImageUtils.toJpeg(ImageUtils.thumbnail(bitmap), quality = 80)
                    store.save(jpeg, thumb, result.rawJson, result.info)
                }
                _state.update { it.copy(screen = Screen.Result(entry), history = listOf(entry) + it.history) }
            } catch (e: PlantIdentificationException) {
                fail(e.message ?: "Identifikasi gagal.")
            } catch (e: java.io.IOException) {
                fail("Foto tidak dapat dibuka. Coba foto lain.")
            } catch (e: Exception) {
                fail("Terjadi kesalahan: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun open(entry: HistoryEntry) = _state.update { it.copy(screen = Screen.Result(entry)) }

    fun backToHome() = _state.update { it.copy(screen = Screen.Home) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(entry.id) }
            _state.update { s ->
                s.copy(screen = Screen.Home, history = s.history.filterNot { it.id == entry.id })
            }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(screen = Screen.Home, error = message) }

    private companion object {
        const val KEY_API = "gemini_api_key"
    }
}
