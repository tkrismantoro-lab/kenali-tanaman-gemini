package id.kenalitanaman.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.kenalitanaman.app.HistoryEntry
import id.kenalitanaman.core.PlantInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(entry: HistoryEntry, onBack: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val info = entry.info

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hasil Identifikasi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus dari riwayat")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FileImage(
                file = entry.photo,
                contentDescription = "Foto tanaman",
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp)),
            )
            if (info.isPlant) PlantDetails(info) else NotAPlant(info)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus dari riwayat?") },
            text = { Text("Foto dan hasil identifikasi ini akan dihapus dari perangkat.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun NotAPlant(info: PlantInfo) {
    Text("Sepertinya ini bukan tanaman", style = MaterialTheme.typography.headlineSmall)
    if (info.description.isNotEmpty()) Text(info.description, style = MaterialTheme.typography.bodyLarge)
    Text(
        "Coba foto ulang dengan tanaman sebagai objek utama dan pencahayaan yang cukup.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlantDetails(info: PlantInfo) {
    Column {
        Text(
            info.commonName.ifEmpty { info.scientificName },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (info.scientificName.isNotEmpty()) {
            Text(info.scientificName, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
        }
        val subtitle = listOfNotNull(
            info.family.takeIf { it.isNotEmpty() }?.let { "Famili $it" },
            info.otherNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
        ).joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (info.confidence.isNotEmpty()) {
            SuggestionChip(onClick = {}, label = { Text("Keyakinan: ${info.confidence}") })
        }
        if (info.difficulty.isNotEmpty()) {
            SuggestionChip(onClick = {}, label = { Text("Perawatan: ${info.difficulty}") })
        }
    }
    if (info.confidence == "rendah") {
        Text(
            "Identifikasi kurang pasti. Coba foto lebih dekat pada daun atau bunga.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (info.description.isNotEmpty()) Text(info.description, style = MaterialTheme.typography.bodyLarge)

    SectionTitle("Cara Merawat")
    CareItem("💧", "Penyiraman", info.care.watering)
    CareItem("☀️", "Cahaya", info.care.light)
    CareItem("🪴", "Media tanam", info.care.soil)
    CareItem("🌡️", "Suhu & kelembapan", info.care.temperatureHumidity)
    CareItem("🧪", "Pemupukan", info.care.fertilizer)
    CareItem("✂️", "Pemangkasan", info.care.pruning)
    CareItem("🌱", "Perbanyakan", info.care.propagation)

    if (info.healthAssessment.isNotEmpty()) {
        SectionTitle("Kondisi Tanaman di Foto")
        Text(info.healthAssessment, style = MaterialTheme.typography.bodyLarge)
    }

    if (info.toxicity.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "⚠️ Keamanan & Toksisitas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    info.toxicity,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    BulletSection("Masalah Umum", info.commonProblems)
    if (info.uses.isNotEmpty()) {
        SectionTitle("Manfaat")
        Text(info.uses, style = MaterialTheme.typography.bodyLarge)
    }
    BulletSection("Fakta Menarik", info.funFacts)

    Text(
        "Hasil dibuat oleh AI dan bisa keliru. Jangan mengonsumsi atau memakai tanaman sebagai obat " +
            "hanya berdasarkan identifikasi ini.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CareItem(emoji: String, title: String, body: String) {
    if (body.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall, color = Color.Unspecified)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun BulletSection(title: String, items: List<String>) {
    if (items.isEmpty()) return
    SectionTitle(title)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
