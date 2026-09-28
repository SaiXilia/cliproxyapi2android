package com.cliproxy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cliproxy.CLIProxy
import com.cliproxy.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class CLIProxyForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.cliproxy.action.START"
        const val ACTION_STOP = "com.cliproxy.action.STOP"
        const val EXTRA_CONFIG_DIR = "extra_config_dir"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"

        private const val NOTIFICATION_ID = 8317
        private const val CHANNEL_ID = "cliproxy_service_channel_v2"
        private const val TAG = "CLIProxyService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProxyService()
                return START_NOT_STICKY
            }
            ACTION_START -> Unit
            else -> {
                // A null intent is an Android service recreation, not a user start request.
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        val configDir = intent.getStringExtra(EXTRA_CONFIG_DIR)
            ?: "${noBackupFilesDir.absolutePath}/cliproxy"
        val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
        val port = intent.getIntExtra(EXTRA_PORT, 8317)

        acquireWakeLock()
        startForegroundNotification("CLIProxy API 正在启动")

        serviceScope.launch {
            val res = CLIProxy.startServer(configDir, host, port)
            if (res < 0) {
                stopProxyService()
                return@launch
            }

            var runningNotificationShown = false

            // Poll OAuth authorization URLs and keep the notification synchronized with the native server.
            while (isActive) {
                when (CLIProxy.getServerStatus()) {
                    CLIProxy.STATUS_RUNNING -> {
                        if (!runningNotificationShown) {
                            updateForegroundNotification("CLIProxy API 正在运行")
                            runningNotificationShown = true
                        }
                    }
                    CLIProxy.STATUS_STOPPED, CLIProxy.STATUS_FAILED -> {
                        stopProxyService()
                        return@launch
                    }
                }

                val url = CLIProxy.pollOAuthURL(1000)
                if (!url.isNullOrBlank()) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open OAuth authorization URL", e)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(title: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(title)
        .setSmallIcon(com.cliproxy.R.drawable.ic_proxy_running)
        .setContentIntent(createMainPendingIntent())
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .build()

    private fun createMainPendingIntent(): PendingIntent {
        val mainIntent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startForegroundNotification(title: String) {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(title), foregroundType)
    }

    private fun updateForegroundNotification(title: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CLIProxy 本地服务",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "CLIProxy API 本地前台运行通知"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CLIProxy::ProxyWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wakeLock = null
        }
    }

    private fun stopProxyService() {
        releaseWakeLock()
        serviceScope.cancel()
        CLIProxy.stopServer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        // The dedicated :proxy process exits after each session to release all Go runtime state.
        exitProcess(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
        CLIProxy.stopServer()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
