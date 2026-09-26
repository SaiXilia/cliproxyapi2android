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
        private const val CHANNEL_ID = "cliproxy_channel"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopProxyService()
            return START_NOT_STICKY
        }

        val configDir = intent?.getStringExtra(EXTRA_CONFIG_DIR)
            ?: "${noBackupFilesDir.absolutePath}/cliproxy"
        val host = intent?.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
        val port = intent?.getIntExtra(EXTRA_PORT, 8317) ?: 8317

        startForegroundNotification(host, port)

        serviceScope.launch {
            val res = CLIProxy.startServer(configDir, host, port)
            if (res < 0) {
                stopProxyService()
                return@launch
            }

            // 轮询 OAuth 授权 URL 并自动拉起浏览器
            while (isActive) {
                val url = CLIProxy.pollOAuthURL(1000)
                if (!url.isNullOrBlank()) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification(host: String, port: Int) {
        val stopIntent = Intent(this, CLIProxyForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CLIProxy API 服务正在运行")
            .setContentText("监听地址: http://$host:$port")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止服务", stopPendingIntent)
            .setOngoing(true)
            .build()

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CLIProxy 本地服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CLIProxy API 本地前台运行通知"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopProxyService() {
        serviceScope.cancel()
        CLIProxy.stopServer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        // 严格遵循单会话进程契约：独立 :proxy 进程受控自退出，彻底回收全部 Go Runtime 单例与资源
        exitProcess(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        CLIProxy.stopServer()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
