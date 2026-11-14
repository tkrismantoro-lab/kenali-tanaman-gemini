package id.kenalitanaman.core

import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlantIdentifierTest {
    private val mapper = ObjectMapper()

    private val sample = """
        {"is_plant":true,"common_name":"Lidah Mertua","other_names":["Snake Plant"," "],
         "scientific_name":"Dracaena trifasciata","family":"Asparagaceae","confidence":"tinggi",
         "description":"Sukulen berdaun tegak.",
         "care":{"watering":"2 minggu sekali","light":"Teduh hingga terang","soil":"Porous",
                 "temperature_humidity":"18-32 C","fertilizer":"Sebulan sekali","pruning":"Jarang",
                 "propagation":"Stek daun"},
         "difficulty":"mudah","toxicity":"Beracun ringan bagi kucing dan anjing",
         "health_assessment":"Sehat","common_problems":["Busuk akar"],"uses":"Tanaman hias",
         "fun_facts":["Tahan kekeringan"]}
    """.trimIndent()

    @Test
    fun parsesModelJson() {
        val info = PlantInfo.fromJson(sample)
        assertTrue(info.isPlant)
        assertEquals("Lidah Mertua", info.commonName)
        assertEquals(listOf("Snake Plant"), info.otherNames)
        assertEquals("Stek daun", info.care.propagation)
        assertEquals(listOf("Busuk akar"), info.commonProblems)
    }

    @Test
    fun toleratesMissingFields() {
        val info = PlantInfo.fromJson("""{"is_plant":false,"description":"Sebuah kursi"}""")
        assertFalse(info.isPlant)
        assertEquals("", info.commonName)
        assertTrue(info.funFacts.isEmpty())
    }

    @Test
    fun buildsGeminiRequestWithImageAndSchema() {
        val body = PlantIdentifier(apiKey = "test").buildRequestJson("QUJD")
        val tree = mapper.readTree(body)

        assertTrue(tree["systemInstruction"]["parts"][0]["text"].asText().isNotBlank())

        val parts = tree["contents"][0]["parts"]
        assertEquals("image/jpeg", parts[0]["inlineData"]["mimeType"].asText())
        assertEquals("QUJD", parts[0]["inlineData"]["data"].asText())
        assertTrue(parts[1]["text"].asText().isNotBlank())

        val config = tree["generationConfig"]
        assertEquals("application/json", config["responseMimeType"].asText())
        assertEquals("low", config["thinkingConfig"]["thinkingLevel"].asText())
        val schema = config["responseSchema"]
        assertFalse(schema.has("additionalProperties"))
        assertEquals(14, schema["required"].size())
        assertEquals(7, schema["properties"]["care"]["required"].size())
    }

    @Test
    fun identifiesPlantFromGeminiResponse() {
        var capturedUrl = ""
        var capturedKey = ""
        val escapedSample = mapper.writeValueAsString(sample)
        val responseBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [ { "text": $escapedSample } ],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
        """.trimIndent()

        val identifier = PlantIdentifier(
            apiKey = "AIza-test-key",
            transport = { url, headers, _ ->
                capturedUrl = url
                capturedKey = headers["x-goog-api-key"].orEmpty()
                HttpResult(200, responseBody)
            },
        )

        val result = identifier.identify(byteArrayOf(1, 2, 3))
        assertTrue(capturedUrl.endsWith("/models/${PlantIdentifier.DEFAULT_MODEL}:streamGenerateContent?alt=sse"))
        assertEquals("AIza-test-key", capturedKey)
        assertEquals("Lidah Mertua", result.info.commonName)
        assertEquals(sample, result.rawJson)
    }

    @Test
    fun mapsInvalidApiKeyAndRateLimitErrors() {
        val invalidKeyJson = """
            {
              "error": {
                "code": 400,
                "message": "API key not valid. Please pass a valid API key.",
                "details": [{"reason": "API_KEY_INVALID"}]
              }
            }
        """.trimIndent()

        val invalidKeyIdentifier = PlantIdentifier(
            apiKey = "bad",
            transport = { _, _, _ -> HttpResult(400, invalidKeyJson) },
        )
        val keyError = assertFailsWith<PlantIdentificationException> {
            invalidKeyIdentifier.identify(byteArrayOf(1))
        }
        assertTrue(keyError.message!!.contains("tidak valid"))
        assertFalse(keyError.retryable)

        val rateLimitedIdentifier = PlantIdentifier(
            apiKey = "key",
            transport = { _, _, _ -> HttpResult(429, "{}") },
        )
        val rateError = assertFailsWith<PlantIdentificationException> {
            rateLimitedIdentifier.identify(byteArrayOf(1))
        }
        assertTrue(rateError.message!!.contains("Terlalu banyak permintaan"))
        assertTrue(rateError.retryable)
    }

    @Test
    fun joinsStreamedSseChunks() {
        val json = mapper.writeValueAsString(sample)
        val half = json.length / 2
        val first = mapper.readValue(json, String::class.java).let { it.substring(0, it.length / 2) }
        val second = mapper.readValue(json, String::class.java).let { it.substring(it.length / 2) }
        fun chunk(text: String, finish: String?) = mapper.writeValueAsString(
            mapOf(
                "candidates" to listOf(
                    buildMap<String, Any> {
                        put("content", mapOf("role" to "model", "parts" to listOf(mapOf("text" to text))))
                        if (finish != null) put("finishReason", finish)
                    },
                ),
            ),
        )
        assertTrue(half > 0)
        val body = "data: ${chunk(first, null)}\r\n\r\ndata: ${chunk(second, "STOP")}\r\n\r\n"

        val identifier = PlantIdentifier(apiKey = "k", transport = { _, _, _ -> HttpResult(200, body) })
        val result = identifier.identify(byteArrayOf(1))
        assertEquals("Lidah Mertua", result.info.commonName)
        assertEquals(sample, result.rawJson)
    }

    @Test
    fun retriesOnceWhenConnectionDrops() {
        var calls = 0
        val ok = mapper.writeValueAsString(
            mapOf("candidates" to listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to sample)))))),
        )
        val identifier = PlantIdentifier(
            apiKey = "k",
            transport = { _, _, _ ->
                calls++
                if (calls == 1) throw java.net.SocketException("Connection reset")
                HttpResult(200, "data: $ok\n\n")
            },
        )
        assertEquals("Lidah Mertua", identifier.identify(byteArrayOf(1)).info.commonName)
        assertEquals(2, calls)

        val alwaysDown = PlantIdentifier(
            apiKey = "k",
            transport = { _, _, _ -> throw java.net.SocketException("Connection reset") },
        )
        val error = assertFailsWith<PlantIdentificationException> { alwaysDown.identify(byteArrayOf(1)) }
        assertTrue(error.message!!.contains("SocketException"))
        assertTrue(error.retryable)
    }

    @Test
    fun usesProxyWithoutApiKey() {
        var capturedUrl = ""
        var capturedHeaders = emptyMap<String, String>()
        val ok = mapper.writeValueAsString(
            mapOf("candidates" to listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to sample)))))),
        )
        val identifier = PlantIdentifier(
            apiKey = "",
            transport = { url, headers, _ ->
                capturedUrl = url
                capturedHeaders = headers
                HttpResult(200, "data: $ok\n\n")
            },
            proxy = ProxyConfig("https://contoh.workers.dev/", "token-aplikasi"),
        )
        assertEquals("Lidah Mertua", identifier.identify(byteArrayOf(1)).info.commonName)
        assertEquals(
            "https://contoh.workers.dev/v1beta/models/${PlantIdentifier.DEFAULT_MODEL}:streamGenerateContent?alt=sse",
            capturedUrl,
        )
        assertEquals("token-aplikasi", capturedHeaders["x-app-token"])
        assertFalse(capturedHeaders.containsKey("x-goog-api-key"))
    }
}
