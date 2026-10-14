package cu.todus.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import cu.todus.download.data.AppPreferences
import cu.todus.download.data.DownloadItem
import cu.todus.download.data.DownloadRepository
import cu.todus.download.engine.DownloadEngine
import kotlinx.coroutines.*

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "todus_downloads_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START_DOWNLOAD = "cu.todus.download.START"
        const val ACTION_PAUSE_DOWNLOAD = "cu.todus.download.PAUSE"
        const val ACTION_CANCEL_DOWNLOAD = "cu.todus.download.CANCEL"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        private var engineInstance: DownloadEngine? = null
        private var repoInstance: DownloadRepository? = null

        fun getEngine(context: Context): DownloadEngine {
            if (engineInstance == null) {
                val prefs = AppPreferences(context.applicationContext)
                val repo = getRepository(context)
                engineInstance = DownloadEngine(context.applicationContext, prefs, repo)
            }
            return engineInstance!!
        }

        fun getRepository(context: Context): DownloadRepository {
            if (repoInstance == null) {
                repoInstance = DownloadRepository(context.applicationContext)
            }
            return repoInstance!!
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "toDusDownload::DownloadWakeLock"
        )

        // Observe repository and update notification or acquire/release wakeLock
        val repo = getRepository(this)
        serviceScope.launch {
            repo.downloads.collect { list ->
                val activeDownloads = list.filter {
                    it.status == DownloadItem.Status.DOWNLOADING || it.status == DownloadItem.Status.CONNECTING
                }

                if (activeDownloads.isNotEmpty()) {
                    if (wakeLock?.isHeld == false) {
                        try { wakeLock?.acquire(24 * 60 * 60 * 1000L) } catch (_: Exception) {}
                    }
                    val notification = buildOngoingNotification(activeDownloads)
                    startForeground(NOTIFICATION_ID, notification)
                } else {
                    if (wakeLock?.isHeld == true) {
                        try { wakeLock?.release() } catch (_: Exception) {}
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)
        val engine = getEngine(this)
        val repo = getRepository(this)

        when (action) {
            ACTION_START_DOWNLOAD -> {
                if (downloadId != null) {
                    val item = repo.downloads.value.find { it.id == downloadId }
                    if (item != null) engine.startDownload(item)
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                if (downloadId != null) {
                    engine.pauseDownload(downloadId)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                if (downloadId != null) {
                    engine.cancelDownload(downloadId)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas toDus",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente de descarga en tarifa cero"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(activeDownloads: List<DownloadItem>): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val totalSpeed = activeDownloads.sumOf { it.speedBps }
        val speedStr = when {
            totalSpeed >= 1024 * 1024 -> String.format("%.2f MB/s", totalSpeed.toDouble() / (1024 * 1024))
            totalSpeed >= 1024 -> String.format("%.1f KB/s", totalSpeed.toDouble() / 1024)
            else -> "$totalSpeed B/s"
        }

        val title = if (activeDownloads.size == 1) {
            activeDownloads.first().name
        } else {
            "Descargando ${activeDownloads.size} archivos toDus"
        }

        val firstItem = activeDownloads.first()
        val text = "⚡ $speedStr  •  ${firstItem.percentage}%  •  Tarifa Cero Activa"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, firstItem.percentage, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
