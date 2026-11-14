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
            transport = { url, key, _ ->
                capturedUrl = url
                capturedKey = key
                HttpResult(200, responseBody)
            },
        )

        val result = identifier.identify(byteArrayOf(1, 2, 3))
        assertTrue(capturedUrl.endsWith("/models/${PlantIdentifier.DEFAULT_MODEL}:generateContent"))
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

        val rateLimitedIdentifier = PlantIdentifier(
            apiKey = "key",
            transport = { _, _, _ -> HttpResult(429, "{}") },
        )
        val rateError = assertFailsWith<PlantIdentificationException> {
            rateLimitedIdentifier.identify(byteArrayOf(1))
        }
        assertTrue(rateError.message!!.contains("Terlalu banyak permintaan"))
    }
}
