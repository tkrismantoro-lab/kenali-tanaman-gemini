package id.kenalitanaman.core

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Kesalahan identifikasi dengan pesan yang siap ditampilkan ke pengguna.
 * [retryable] = true bila penyebabnya jaringan/kuota/server sibuk, sehingga layak dicoba lagi nanti.
 */
class PlantIdentificationException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false,
) : Exception(message, cause)

/** Hasil identifikasi: data terurai plus JSON mentahnya (untuk disimpan ke riwayat). */
data class IdentificationResult(val info: PlantInfo, val rawJson: String)

internal data class HttpResult(val statusCode: Int, val body: String)

internal fun interface GeminiTransport {
    fun post(url: String, apiKey: String, requestBodyJson: String): HttpResult
}

/**
 * Mengirim foto tanaman ke Gemini API dan mengembalikan identifikasi beserta panduan perawatan.
 * Pemanggilan bersifat blocking; jalankan di thread latar belakang.
 */
class PlantIdentifier internal constructor(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val transport: GeminiTransport = DefaultTransport,
) {
    constructor(apiKey: String, model: String = DEFAULT_MODEL) :
        this(apiKey, model, DefaultTransport)

    fun identify(jpegBytes: ByteArray): IdentificationResult {
        val requestJson = buildRequestJson(Base64.getEncoder().encodeToString(jpegBytes))
        // Streaming (SSE): data mengalir selama model bekerja, sehingga jaringan seluler tidak
        // memutus koneksi yang lama "diam".
        val url = "$BASE_URL/models/$model:streamGenerateContent?alt=sse"

        val result = postWithRetry(url, requestJson)

        if (result.statusCode !in 200..299) {
            throw mapHttpError(result.statusCode, result.body)
        }

        val chunks = try {
            parseChunks(result.body)
        } catch (e: Exception) {
            throw PlantIdentificationException("Jawaban tidak dapat dibaca. Silakan coba lagi.", e)
        }
        if (chunks.isEmpty()) {
            throw PlantIdentificationException("Tidak ada jawaban dari layanan AI. Silakan coba lagi.")
        }

        val blocked = chunks.any { it.path("promptFeedback").path("blockReason").asText("").isNotEmpty() }
        if (blocked) {
            throw PlantIdentificationException("Foto ini tidak dapat dianalisis. Coba foto lain.")
        }

        val candidates = chunks.mapNotNull { it.path("candidates").firstOrNull() }
        if (candidates.isEmpty()) {
            throw PlantIdentificationException("Tidak ada jawaban dari layanan AI. Silakan coba lagi.")
        }

        val finishReason = candidates.lastOrNull { it.path("finishReason").asText("").isNotEmpty() }
            ?.path("finishReason")?.asText("") ?: ""
        if (finishReason in BLOCKED_FINISH_REASONS) {
            throw PlantIdentificationException("Foto ini tidak dapat dianalisis. Coba foto lain.")
        }
        if (finishReason == "MAX_TOKENS") {
            throw PlantIdentificationException("Jawaban terpotong. Silakan coba lagi.")
        }

        val json = candidates.flatMap { candidate ->
            candidate.path("content").path("parts")
                .filterNot { it.path("thought").asBoolean(false) }
                .mapNotNull { it.path("text").takeIf { node -> node.isTextual }?.asText() }
        }.joinToString("")

        if (json.isBlank()) {
            throw PlantIdentificationException("Tidak ada jawaban dari layanan AI. Silakan coba lagi.")
        }

        val info = try {
            PlantInfo.fromJson(json)
        } catch (e: Exception) {
            throw PlantIdentificationException("Jawaban tidak dapat dibaca. Silakan coba lagi.", e)
        }
        return IdentificationResult(info, json)
    }

    /** Mengirim permintaan; bila koneksi terputus (bukan timeout), dicoba sekali lagi. */
    private fun postWithRetry(url: String, requestJson: String): HttpResult {
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return transport.post(url, apiKey, requestJson)
            } catch (e: SocketTimeoutException) {
                throw PlantIdentificationException(
                    "Layanan AI terlalu lama menjawab. Coba lagi, sebaiknya dengan sinyal internet yang lebih kuat.",
                    e,
                    retryable = true,
                )
            } catch (e: IOException) {
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
            }
        }
        val e = lastError!!
        throw PlantIdentificationException(
            "Tidak dapat terhubung ke layanan AI. Periksa koneksi internet Anda. (${describe(e)})",
            e,
            retryable = true,
        )
    }

    /** Respons streaming berformat SSE ("data: {...}" per baris); respons biasa berupa satu JSON. */
    internal fun parseChunks(body: String): List<com.fasterxml.jackson.databind.JsonNode> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("[")) return mapper.readTree(trimmed).toList()
        if (trimmed.startsWith("{")) return listOf(mapper.readTree(trimmed))
        return trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotEmpty() && it != "[DONE]" }
            .map { mapper.readTree(it) }
            .toList()
    }

    private fun describe(e: Throwable): String {
        val detail = e.message?.take(80)?.takeIf { it.isNotBlank() }
        return if (detail != null) "${e.javaClass.simpleName}: $detail" else e.javaClass.simpleName
    }

    internal fun buildRequestJson(base64Jpeg: String): String {
        val body = linkedMapOf(
            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to SYSTEM_PROMPT)),
            ),
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf(
                            "inlineData" to mapOf(
                                "mimeType" to "image/jpeg",
                                "data" to base64Jpeg,
                            ),
                        ),
                        mapOf("text" to USER_PROMPT),
                    ),
                ),
            ),
            "generationConfig" to mapOf(
                "responseMimeType" to "application/json",
                "responseSchema" to PlantSchema.root,
                "maxOutputTokens" to 16000,
                // Berpikir "low" cukup untuk identifikasi dan jauh lebih cepat daripada bawaan model.
                "thinkingConfig" to mapOf("thinkingLevel" to "low"),
            ),
        )
        return mapper.writeValueAsString(body)
    }

    private fun mapHttpError(statusCode: Int, responseBody: String): PlantIdentificationException {
        val isInvalidKey = statusCode == 401 || (statusCode == 400 && isApiKeyInvalid(responseBody))
        val message = when {
            isInvalidKey -> "API key tidak valid. Periksa kembali di Pengaturan."
            statusCode == 403 -> "API key tidak memiliki izin untuk permintaan ini."
            statusCode == 429 -> "Terlalu banyak permintaan atau kuota habis. Coba lagi sebentar lagi."
            statusCode == 503 -> "Layanan AI sedang sibuk. Coba lagi beberapa saat lagi."
            else -> "Layanan AI bermasalah (kode $statusCode). Coba lagi nanti."
        }
        val retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500
        return PlantIdentificationException(message, retryable = retryable)
    }

    private fun isApiKeyInvalid(responseBody: String): Boolean {
        val parsed = runCatching { mapper.readTree(responseBody) }.getOrNull() ?: return false
        // Endpoint streaming kadang membungkus error dalam array.
        val node = if (parsed.isArray) parsed.firstOrNull() ?: return false else parsed
        val message = node.path("error").path("message").asText("")
        if (message.contains("API key not valid", ignoreCase = true)) return true
        return node.path("error").path("details").any { detail ->
            detail.path("reason").asText("") == "API_KEY_INVALID"
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 2_000L
        private val mapper = ObjectMapper()
        private val BLOCKED_FINISH_REASONS = setOf(
            "SAFETY",
            "RECITATION",
            "PROHIBITED_CONTENT",
            "BLOCKLIST",
            "SPII",
            "IMAGE_SAFETY",
            "IMAGE_PROHIBITED_CONTENT",
        )

        private object DefaultTransport : GeminiTransport {
            override fun post(url: String, apiKey: String, requestBodyJson: String): HttpResult {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 180_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                }
                return try {
                    conn.outputStream.use { out ->
                        out.write(requestBodyJson.toByteArray(StandardCharsets.UTF_8))
                    }
                    val status = conn.responseCode
                    val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                    HttpResult(status, body)
                } finally {
                    conn.disconnect()
                }
            }
        }

        private const val SYSTEM_PROMPT =
            "Kamu adalah ahli botani dan hortikultura yang membantu pengguna aplikasi pengenal tanaman " +
                "di Indonesia. Pengguna adalah penghobi tanaman, bukan ahli, jadi gunakan Bahasa Indonesia " +
                "yang jelas dan praktis. Sesuaikan saran perawatan dengan iklim tropis Indonesia kecuali " +
                "tanamannya jelas membutuhkan kondisi lain. Jujurlah soal ketidakpastian: bila foto kurang " +
                "jelas atau ada beberapa spesies yang mirip, pilih kandidat paling mungkin, turunkan tingkat " +
                "keyakinan, dan sebutkan kemungkinan lain di deskripsi. Informasi toksisitas menyangkut " +
                "keselamatan, jadi jangan menyatakan tanaman aman bila kamu tidak yakin."

        private const val USER_PROMPT =
            "Identifikasi tanaman pada foto ini, lalu berikan panduan perawatan dan informasi lengkapnya. " +
                "Jika foto tidak menampilkan tanaman, isi is_plant dengan false, jelaskan singkat di " +
                "description apa yang terlihat, dan kosongkan field lainnya."
    }
}
