package id.kenalitanaman.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.kenalitanaman.app.HistoryEntry
import id.kenalitanaman.app.QueueItem
import id.kenalitanaman.app.UiState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
    onRetryQueued: (QueueItem) -> Unit,
    onDeleteQueued: (QueueItem) -> Unit,
    onRetryQueueNow: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kenali Tanaman", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CaptureCard(
                    onTakePhoto = onTakePhoto,
                    onPickPhoto = onPickPhoto,
                )
            }
            if (state.queue.isNotEmpty()) {
                item {
                    QueueHeader(
                        count = state.queue.size,
                        canRetryNow = state.processingId == null && state.queue.any { it.error == null },
                        onRetryNow = onRetryQueueNow,
                    )
                }
                items(state.queue, key = { "antrean-${it.id}" }) { item ->
                    QueueRow(
                        item = item,
                        processing = item.id == state.processingId,
                        onRetry = { onRetryQueued(item) },
                        onDelete = { onDeleteQueued(item) },
                    )
                }
            }
            item {
                Text(
                    "Riwayat",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        "Belum ada tanaman. Foto tanaman pertama Anda untuk mulai.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.history, key = { "riwayat-${it.id}" }) { entry ->
                HistoryRow(entry, onClick = { onOpen(entry) })
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentKey = state.apiKey,
            onSave = {
                onSaveApiKey(it)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }

    state.notice?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissNotice,
            confirmButton = { TextButton(onClick = onDismissNotice) { Text("Mengerti") } },
            title = { Text("Disimpan ke antrean") },
            text = { Text(message) },
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = { TextButton(onClick = onDismissError) { Text("Tutup") } },
            title = { Text("Tidak berhasil") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun CaptureCard(
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🌿", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tanaman apa ini?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Foto daun, bunga, atau seluruh tanaman dengan jelas untuk mengetahui nama dan cara merawatnya.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                Text("Ambil Foto")
            }
            OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) {
                Text("Pilih dari Galeri")
            }
        }
    }
}

@Composable
private fun QueueHeader(count: Int, canRetryNow: Boolean, onRetryNow: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Antrean ($count)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (canRetryNow) {
                TextButton(onClick = onRetryNow) { Text("Coba sekarang") }
            }
        }
        Text(
            "Foto akan dianalisis otomatis saat HP mendapat sinyal internet, walaupun aplikasi ditutup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QueueRow(item: QueueItem, processing: Boolean, onRetry: () -> Unit, onDelete: () -> Unit) {
    val failed = item.error != null
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileImage(
                file = item.thumbnail,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        processing -> "Sedang dianalisis…"
                        failed -> "Gagal dianalisis"
                        else -> "Menunggu sinyal internet"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (failed && !processing) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
                if (failed && !processing) {
                    Text(
                        item.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Difoto " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(item.id)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!processing) {
                    Row {
                        if (failed) {
                            TextButton(onClick = onRetry) { Text("Coba lagi") }
                        }
                        TextButton(onClick = onDelete) { Text("Hapus") }
                    }
                }
            }
            if (processing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val info = entry.info
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileImage(
                file = entry.thumbnail,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (info.isPlant) info.commonName.ifEmpty { info.scientificName } else "Bukan tanaman",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info.isPlant && info.scientificName.isNotEmpty()) {
                    Text(
                        info.scientificName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.id)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(currentKey: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by rememberSaveable { mutableStateOf(currentKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan") },
        text = {
            Column {
                Text(
                    "Aplikasi sudah bisa langsung dipakai tanpa API key. Opsional: bila ingin memakai " +
                        "kuota Gemini Anda sendiri, buat API key di " +
                        "aistudio.google.com/apikey lalu tempel di sini. Kosongkan untuk memakai server bawaan.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key Gemini (opsional)") },
                    placeholder = { Text("AIza...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun AnalyzingScreen(onAnalyzeLater: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text("Menganalisis foto…", style = MaterialTheme.typography.titleMedium)
        Text(
            "Biasanya butuh 10–30 detik.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "Sinyal lemah? Simpan fotonya ke antrean; analisis berlanjut otomatis.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAnalyzeLater, modifier = Modifier.padding(top = 8.dp)) {
            Text("Analisis nanti")
        }
    }
}
