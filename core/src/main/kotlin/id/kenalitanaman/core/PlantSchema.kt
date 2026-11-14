package id.kenalitanaman.core

/** JSON Schema untuk structured output, supaya jawaban model selalu bisa diurai. */
internal object PlantSchema {
    private fun str(description: String) = mapOf("type" to "string", "description" to description)

    private fun strList(description: String) = mapOf(
        "type" to "array",
        "description" to description,
        "items" to mapOf("type" to "string"),
    )

    private fun obj(properties: Map<String, Any>) = mapOf(
        "type" to "object",
        "properties" to properties,
        "required" to properties.keys.toList(),
    )

    private val care = obj(
        linkedMapOf(
            "watering" to str("Frekuensi dan cara menyiram, termasuk tanda kurang/lebih air"),
            "light" to str("Kebutuhan cahaya dan penempatan yang ideal"),
            "soil" to str("Media tanam dan drainase yang cocok"),
            "temperature_humidity" to str("Suhu dan kelembapan ideal"),
            "fertilizer" to str("Jenis pupuk dan jadwal pemupukan"),
            "pruning" to str("Pemangkasan dan perawatan rutin"),
            "propagation" to str("Cara memperbanyak tanaman"),
        ),
    )

    val root: Map<String, Any> = obj(
        linkedMapOf(
            "is_plant" to mapOf(
                "type" to "boolean",
                "description" to "false jika foto tidak menampilkan tanaman",
            ),
            "common_name" to str("Nama umum dalam Bahasa Indonesia"),
            "other_names" to strList("Nama lain / nama daerah / nama Inggris"),
            "scientific_name" to str("Nama ilmiah (Latin)"),
            "family" to str("Famili tanaman"),
            "confidence" to mapOf(
                "type" to "string",
                "enum" to listOf("tinggi", "sedang", "rendah"),
                "description" to "Tingkat keyakinan identifikasi",
            ),
            "description" to str("Deskripsi singkat: ciri khas, asal, habitat"),
            "care" to care,
            "difficulty" to mapOf(
                "type" to "string",
                "enum" to listOf("mudah", "sedang", "sulit"),
                "description" to "Tingkat kesulitan perawatan",
            ),
            "toxicity" to str("Keamanan bagi manusia, anak-anak, kucing, dan anjing"),
            "health_assessment" to str("Kondisi tanaman yang terlihat di foto dan saran bila ada masalah"),
            "common_problems" to strList("Hama, penyakit, dan masalah umum beserta solusinya"),
            "uses" to str("Manfaat dan kegunaan (hias, obat, pangan, dll.)"),
            "fun_facts" to strList("Fakta menarik"),
        ),
    )
}
