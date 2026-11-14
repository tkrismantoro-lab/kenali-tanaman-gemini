package id.kenalitanaman.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageUtils {
    /** Sisi terpanjang foto yang dikirim ke AI; lebih besar dari ini tidak menambah akurasi. */
    private const val MAX_UPLOAD_EDGE = 1568
    private const val THUMBNAIL_EDGE = 320

    /** Memuat foto (orientasi EXIF sudah diterapkan) dan mengecilkannya bila perlu. */
    fun loadScaled(context: Context, uri: Uri, maxEdge: Int = MAX_UPLOAD_EDGE): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxEdge) {
                val scale = maxEdge.toFloat() / longest
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).toInt()),
                    max(1, (info.size.height * scale).toInt()),
                )
            }
        }
    }

    fun thumbnail(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= THUMBNAIL_EDGE) return bitmap
        val scale = THUMBNAIL_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).toInt()),
            max(1, (bitmap.height * scale).toInt()),
            true,
        )
    }

    fun toJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}
