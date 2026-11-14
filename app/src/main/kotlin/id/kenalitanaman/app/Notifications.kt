package id.kenalitanaman.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Memberi tahu pengguna ketika foto di antrean selesai dianalisis. */
object Notifications {
    private const val CHANNEL_ID = "hasil_identifikasi"
    private const val NOTIFICATION_ID = 1001

    @SuppressLint("MissingPermission")
    fun resultsReady(context: Context, entries: List<HistoryEntry>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Hasil identifikasi")
                .setDescription("Kabar saat foto di antrean selesai dianalisis")
                .build(),
        )

        val names = entries.joinToString(", ") { entry ->
            val info = entry.info
            if (info.isPlant) info.commonName.ifEmpty { info.scientificName } else "Bukan tanaman"
        }
        val title = if (entries.size == 1) "Hasil identifikasi siap" else "${entries.size} hasil identifikasi siap"
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$names\nKetuk untuk melihat di Riwayat."))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Izin notifikasi dicabut; hasil tetap ada di Riwayat.
        }
    }
}
