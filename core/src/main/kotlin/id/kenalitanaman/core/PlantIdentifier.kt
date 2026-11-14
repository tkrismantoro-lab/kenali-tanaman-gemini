package id.kenalitanaman.core

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Kesalahan identifikasi dengan pesan yang siap ditampilkan ke pengguna. */
class PlantIdentificationException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
        val url = "$BASE_URL/models/$model:generateContent"

        val result = try {
            transport.post(url, apiKey, requestJson)
        } catch (e: IOException) {
            throw PlantIdentificationException("Tidak dapat terhubung. Periksa koneksi internet Anda.", e)
        }

        if (result.statusCode !in 200..299) {
            throw mapHttpError(result.statusCode, result.body)
        }

        val root = try {
            mapper.readTree(result.body)
        } catch (e: Exception) {
            throw PlantIdentificationException("Jawaban tidak dapat dibaca. Silakan coba lagi.", e)
        }

        val blockReason = root.path("promptFeedback").path("blockReason").asText("")
        if (blockReason.isNotEmpty()) {
            throw PlantIdentificationException("Foto ini tidak dapat dianalisis. Coba foto lain.")
        }

        val candidate = root.path("candidates").firstOrNull()
            ?: throw PlantIdentificationException("Tidak ada jawaban dari layanan AI. Silakan coba lagi.")

        val finishReason = candidate.path("finishReason").asText("")
        if (finishReason in BLOCKED_FINISH_REASONS) {
            throw PlantIdentificationException("Foto ini tidak dapat dianalisis. Coba foto lain.")
        }
        if (finishReason == "MAX_TOKENS") {
            throw PlantIdentificationException("Jawaban terpotong. Silakan coba lagi.")
        }

        val json = candidate.path("content").path("parts")
            .filterNot { it.path("thought").asBoolean(false) }
            .mapNotNull { it.path("text").takeIf { node -> node.isTextual }?.asText() }
            .joinToString("")

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
            else -> "Layanan AI bermasalah (kode $statusCode). Coba lagi nanti."
        }
        return PlantIdentificationException(message)
    }

    private fun isApiKeyInvalid(responseBody: String): Boolean {
        val node = runCatching { mapper.readTree(responseBody) }.getOrNull() ?: return false
        val message = node.path("error").path("message").asText("")
        if (message.contains("API key not valid", ignoreCase = true)) return true
        return node.path("error").path("details").any { detail ->
            detail.path("reason").asText("") == "API_KEY_INVALID"
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
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
                    readTimeout = 90_000
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
