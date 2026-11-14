package id.kenalitanaman.app

import android.content.Context
import id.kenalitanaman.core.PlantInfo
import java.io.File

/** Satu hasil identifikasi yang tersimpan di perangkat. */
data class HistoryEntry(
    val id: Long,
    val info: PlantInfo,
    val photo: File,
    val thumbnail: File,
)

/** Riwayat disimpan sebagai folder per entri: photo.jpg, thumb.jpg, result.json. */
class HistoryStore(context: Context) {
    private val root = File(context.filesDir, "history").apply { mkdirs() }

    fun save(photoJpeg: ByteArray, thumbJpeg: ByteArray, rawJson: String, info: PlantInfo): HistoryEntry {
        val id = System.currentTimeMillis()
        val dir = File(root, id.toString()).apply { mkdirs() }
        File(dir, PHOTO).writeBytes(photoJpeg)
        File(dir, THUMB).writeBytes(thumbJpeg)
        File(dir, RESULT).writeText(rawJson)
        return HistoryEntry(id, info, File(dir, PHOTO), File(dir, THUMB))
    }

    /** Terbaru di atas. Entri yang rusak dilewati. */
    fun loadAll(): List<HistoryEntry> =
        root.listFiles().orEmpty()
            .mapNotNull { dir ->
                val id = dir.name.toLongOrNull() ?: return@mapNotNull null
                runCatching {
                    HistoryEntry(
                        id = id,
                        info = PlantInfo.fromJson(File(dir, RESULT).readText()),
                        photo = File(dir, PHOTO),
                        thumbnail = File(dir, THUMB),
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.id }

    fun delete(id: Long) {
        File(root, id.toString()).deleteRecursively()
    }

    private companion object {
        const val PHOTO = "photo.jpg"
        const val THUMB = "thumb.jpg"
        const val RESULT = "result.json"
    }
}
