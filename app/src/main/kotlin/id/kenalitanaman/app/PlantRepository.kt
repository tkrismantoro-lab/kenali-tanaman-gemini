package id.kenalitanaman.app

import android.content.Context
import id.kenalitanaman.core.PlantIdentifier
import id.kenalitanaman.core.ProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Satu sumber data riwayat dan antrean untuk seluruh proses aplikasi, supaya layar langsung
 * berubah ketika worker latar belakang selesai menganalisis foto.
 */
object PlantRepository {
    const val PREFS = "settings"
    const val KEY_API = "gemini_api_key"

    private var historyStore: HistoryStore? = null
    private var queueStore: QueueStore? = null

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    /** Id foto antrean yang sedang dikirim ke AI, atau null. */
    private val _processingId = MutableStateFlow<Long?>(null)
    val processingId: StateFlow<Long?> = _processingId.asStateFlow()

    /** Mencegah dua worker memproses antrean bersamaan. */
    val processingLock = Mutex()

    @Synchronized
    fun history(context: Context): HistoryStore =
        historyStore ?: HistoryStore(context.applicationContext).also { historyStore = it }

    @Synchronized
    fun queue(context: Context): QueueStore =
        queueStore ?: QueueStore(context.applicationContext).also { queueStore = it }

    fun apiKey(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API, "").orEmpty()

    /**
     * Bila pengguna mengisi API key sendiri di Pengaturan, Gemini dipanggil langsung; bila tidak,
     * lewat server perantara sehingga aplikasi bisa langsung dipakai tanpa API key.
     */
    fun identifier(context: Context): PlantIdentifier {
        val ownKey = apiKey(context)
        return if (ownKey.isNotEmpty()) {
            PlantIdentifier(ownKey)
        } else {
            PlantIdentifier(ProxyConfig(BuildConfig.PROXY_URL, BuildConfig.PROXY_APP_TOKEN))
        }
    }

    fun setProcessing(id: Long?) {
        _processingId.value = id
    }

    suspend fun refresh(context: Context) {
        val historyList = withContext(Dispatchers.IO) { history(context).loadAll() }
        val queueList = withContext(Dispatchers.IO) { queue(context).loadAll() }
        _history.value = historyList
        _queue.value = queueList
    }
}
