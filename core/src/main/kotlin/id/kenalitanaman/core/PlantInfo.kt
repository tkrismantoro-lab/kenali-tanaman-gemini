package id.kenalitanaman.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Panduan perawatan sebuah tanaman. */
data class CareGuide(
    val watering: String,
    val light: String,
    val soil: String,
    val temperatureHumidity: String,
    val fertilizer: String,
    val pruning: String,
    val propagation: String,
)

/** Hasil identifikasi satu foto. */
data class PlantInfo(
    val isPlant: Boolean,
    val commonName: String,
    val otherNames: List<String>,
    val scientificName: String,
    val family: String,
    val confidence: String,
    val description: String,
    val care: CareGuide,
    val difficulty: String,
    val toxicity: String,
    val healthAssessment: String,
    val commonProblems: List<String>,
    val uses: String,
    val funFacts: List<String>,
) {
    companion object {
        private val mapper = ObjectMapper()

        /** Mengurai JSON yang dihasilkan model (sesuai [PlantSchema]). */
        fun fromJson(json: String): PlantInfo {
            val n = mapper.readTree(json)
            val c = n.path("care")
            return PlantInfo(
                isPlant = n.path("is_plant").asBoolean(false),
                commonName = n.text("common_name"),
                otherNames = n.list("other_names"),
                scientificName = n.text("scientific_name"),
                family = n.text("family"),
                confidence = n.text("confidence"),
                description = n.text("description"),
                care = CareGuide(
                    watering = c.text("watering"),
                    light = c.text("light"),
                    soil = c.text("soil"),
                    temperatureHumidity = c.text("temperature_humidity"),
                    fertilizer = c.text("fertilizer"),
                    pruning = c.text("pruning"),
                    propagation = c.text("propagation"),
                ),
                difficulty = n.text("difficulty"),
                toxicity = n.text("toxicity"),
                healthAssessment = n.text("health_assessment"),
                commonProblems = n.list("common_problems"),
                uses = n.text("uses"),
                funFacts = n.list("fun_facts"),
            )
        }

        private fun JsonNode.text(field: String): String = path(field).asText("").trim()

        private fun JsonNode.list(field: String): List<String> =
            path(field).mapNotNull { it.asText("").trim().takeIf(String::isNotEmpty) }
    }
}
