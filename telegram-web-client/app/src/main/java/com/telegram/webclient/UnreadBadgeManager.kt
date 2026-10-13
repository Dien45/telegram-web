package com.telegram.webclient

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import me.leolin.shortcutbadger.ShortcutBadger

/**
 * Mengelola badge launcher (ala Telegram native) yang menampilkan jumlah
 * chat belum terbaca di ikon aplikasi.
 *
 * Strategi ganda untuk kompatibilitas lintas launcher:
 *  - ShortcutBadger: menulis badge langsung untuk launcher yang mendukung
 *    (Samsung, MIUI, OPPO, Vivo, dst) tanpa butuh notifikasi terlihat.
 *  - Notifikasi senyap dengan setNumber(): mekanisme native Android 8+
 *    untuk launcher yang membaca badge dari notifikasi (Pixel/stock, One UI).
 */
object UnreadBadgeManager {

    private const val CHANNEL_ID = "unread_badge"
    private const val NOTIFICATION_ID = 9001

    /** Guard anti-redudan: hanya update saat angkanya berubah. */
    private var lastCount = -1

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.badge_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.badge_channel_desc)
                setSound(null, null)
                enableVibration(false)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    /** Dipanggil dari JS bridge setiap kali jumlah unread berubah. */
    fun update(context: Context, count: Int) {
        if (count == lastCount) return
        lastCount = count
        if (count > 0) {
            applyBadge(context, count)
            showNotification(context, count)
        } else {
            clearBadge(context)
        }
    }

    /** Reset penuh (mis. saat aplikasi ditutup / pengguna keluar). */
    fun clear(context: Context) {
        lastCount = -1
        clearBadge(context)
    }

    private fun clearBadge(context: Context) {
        try {
            ShortcutBadger.removeCount(context.applicationContext)
        } catch (_: Exception) {
            // launcher tidak mendukung ShortcutBadger; abaikan
        }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun applyBadge(context: Context, count: Int) {
        try {
            ShortcutBadger.applyCount(context.applicationContext, count)
        } catch (_: Exception) {
            // fallback ke notifikasi setNumber di bawah
        }
    }

    private fun showNotification(context: Context, count: Int) {
        // Android 13+: tanpa izin, notifikasi tidak akan muncul → badge ikut hilang.
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = context.resources.getQuantityString(R.plurals.unread_messages, count, count)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.badge_open_hint))
            .setNumber(count)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowBadge(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Minta izin notifikasi (API 33+) sekali di awal, penting untuk badge. */
    fun requestNotificationPermissionIfNeeded(
        activity: AppCompatActivity,
        launcher: ActivityResultLauncher<String>
    ) {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission(activity)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
