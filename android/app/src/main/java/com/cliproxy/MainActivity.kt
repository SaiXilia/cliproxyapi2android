package com.cliproxy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cliproxy.service.CLIProxyForegroundService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvEndpoint: TextView
    private lateinit var tvDataDir: TextView
    private lateinit var statusIndicator: View
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnOpenWeb: MaterialButton

    private var isRunning = false
    private val serverUrl = "http://127.0.0.1:8317"

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvEndpoint = findViewById(R.id.tvEndpoint)
        tvDataDir = findViewById(R.id.tvDataDir)
        statusIndicator = findViewById(R.id.statusIndicator)
        btnToggle = findViewById(R.id.btnToggle)
        btnOpenWeb = findViewById(R.id.btnOpenWeb)

        val configDir = "${noBackupFilesDir.absolutePath}/cliproxy"
        tvDataDir.text = "数据目录: $configDir"

        btnToggle.setOnClickListener {
            if (isRunning) {
                stopProxy()
            } else {
                startProxy(configDir)
            }
        }

        btnOpenWeb.setOnClickListener {
            openManagementPage()
        }

        checkNotificationPermission()
        startStatusChecker()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startProxy(configDir: String) {
        val intent = Intent(this, CLIProxyForegroundService::class.java).apply {
            action = CLIProxyForegroundService.ACTION_START
            putExtra(CLIProxyForegroundService.EXTRA_CONFIG_DIR, configDir)
            putExtra(CLIProxyForegroundService.EXTRA_HOST, "127.0.0.1")
            putExtra(CLIProxyForegroundService.EXTRA_PORT, 8317)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI(starting = true)
    }

    private fun stopProxy() {
        val intent = Intent(this, CLIProxyForegroundService::class.java).apply {
            action = CLIProxyForegroundService.ACTION_STOP
        }
        startService(intent)
        updateUI(running = false)
    }

    private fun openManagementPage() {
        val mgmtUrl = "$serverUrl/management.html"
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this, Uri.parse(mgmtUrl))
        } catch (_: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mgmtUrl))
            startActivity(browserIntent)
        }
    }

    private fun startStatusChecker() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val ok = checkServerHealth()
                withContext(Dispatchers.Main) {
                    if (isRunning != ok) {
                        isRunning = ok
                        updateUI(running = ok)
                    }
                }
                delay(1500)
            }
        }
    }

    private fun checkServerHealth(): Boolean {
        return try {
            val connection = URL("$serverUrl/healthz").openConnection() as HttpURLConnection
            connection.connectTimeout = 500
            connection.readTimeout = 500
            connection.requestMethod = "GET"
            val code = connection.responseCode
            connection.disconnect()
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun updateUI(running: Boolean = isRunning, starting: Boolean = false) {
        if (starting) {
            tvStatus.text = "正在启动..."
            statusIndicator.setBackgroundColor(Color.parseColor("#F59E0B")) // 橙色
            btnToggle.text = "启动中..."
            btnToggle.isEnabled = false
            btnOpenWeb.isEnabled = false
            return
        }

        btnToggle.isEnabled = true
        if (running) {
            tvStatus.text = "运行中 (127.0.0.1:8317)"
            statusIndicator.setBackgroundColor(Color.parseColor("#10B981")) // 绿色
            btnToggle.text = "停止服务"
            btnToggle.setBackgroundColor(Color.parseColor("#EF4444")) // 红色
            btnOpenWeb.isEnabled = true
        } else {
            tvStatus.text = "已停止"
            statusIndicator.setBackgroundColor(Color.parseColor("#64748B")) // 灰色
            btnToggle.text = "启动服务"
            btnToggle.setBackgroundColor(Color.parseColor("#2563EB")) // 蓝色
            btnOpenWeb.isEnabled = false
        }
    }
}
