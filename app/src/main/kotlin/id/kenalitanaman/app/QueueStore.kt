package id.kenalitanaman.app

import android.content.Context
import java.io.File

/** Foto yang menunggu dianalisis. [error] terisi bila gagal karena sebab selain sinyal. */
data class QueueItem(
    val id: Long,
    val photo: File,
    val thumbnail: File,
    val error: String?,
)

/** Antrean disimpan sebagai folder per foto: photo.jpg, thumb.jpg, dan error.txt bila gagal. */
class QueueStore(context: Context) {
    private val root = File(context.filesDir, "queue").apply { mkdirs() }

    @Synchronized
    fun add(photoJpeg: ByteArray, thumbJpeg: ByteArray): QueueItem {
        var id = System.currentTimeMillis()
        while (File(root, id.toString()).exists()) id++
        val dir = File(root, id.toString()).apply { mkdirs() }
        File(dir, THUMB).writeBytes(thumbJpeg)
        // Tulis ke berkas sementara dulu supaya worker tidak pernah membaca foto setengah jadi.
        val temp = File(dir, "$PHOTO.tmp")
        temp.writeBytes(photoJpeg)
        temp.renameTo(File(dir, PHOTO))
        return QueueItem(id, File(dir, PHOTO), File(dir, THUMB), null)
    }

    /** Terlama di atas, supaya dianalisis sesuai urutan pengambilan foto. */
    fun loadAll(): List<QueueItem> =
        root.listFiles().orEmpty()
            .mapNotNull { dir ->
                val id = dir.name.toLongOrNull() ?: return@mapNotNull null
                val photo = File(dir, PHOTO)
                if (!photo.exists()) return@mapNotNull null
                val errorFile = File(dir, ERROR)
                QueueItem(
                    id = id,
                    photo = photo,
                    thumbnail = File(dir, THUMB),
                    error = if (errorFile.exists()) errorFile.readText() else null,
                )
            }
            .sortedBy { it.id }

    fun contains(id: Long): Boolean = File(File(root, id.toString()), PHOTO).exists()

    fun markFailed(id: Long, message: String) {
        val dir = File(root, id.toString())
        if (dir.exists()) File(dir, ERROR).writeText(message)
    }

    fun clearError(id: Long) {
        File(File(root, id.toString()), ERROR).delete()
    }

    fun delete(id: Long) {
        File(root, id.toString()).deleteRecursively()
    }

    private companion object {
        const val PHOTO = "photo.jpg"
        const val THUMB = "thumb.jpg"
        const val ERROR = "error.txt"
    }
}
