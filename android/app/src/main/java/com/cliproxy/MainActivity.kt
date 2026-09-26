package com.cliproxy

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvEndpoint: TextView
    private lateinit var tvDataDir: TextView
    private lateinit var statusIndicator: View
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnOpenWeb: MaterialButton
    private lateinit var tvApiKeyStatus: TextView
    private lateinit var tvApiKeyValue: TextView
    private lateinit var btnCopyKey: MaterialButton
    private lateinit var btnToggleAuthMode: MaterialButton
    private lateinit var btnTestModels: MaterialButton
    private lateinit var btnTestHealthz: MaterialButton

    private var isRunning = false
    private val serverUrl = "http://127.0.0.1:8317"
    private var currentApiKey: String? = null

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
        tvApiKeyStatus = findViewById(R.id.tvApiKeyStatus)
        tvApiKeyValue = findViewById(R.id.tvApiKeyValue)
        btnCopyKey = findViewById(R.id.btnCopyKey)
        btnToggleAuthMode = findViewById(R.id.btnToggleAuthMode)
        btnTestModels = findViewById(R.id.btnTestModels)
        btnTestHealthz = findViewById(R.id.btnTestHealthz)

        val configDir = "${noBackupFilesDir.absolutePath}/cliproxy"
        tvDataDir.text = "数据目录: $configDir"

        refreshAuthSettingsUI()

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

        btnCopyKey.setOnClickListener {
            currentApiKey?.let { key ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("CLIProxy API Key", key)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "API Key 已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggleAuthMode.setOnClickListener {
            toggleAuthMode(configDir)
        }

        btnTestModels.setOnClickListener {
            val url = if (currentApiKey.isNullOrBlank()) {
                "$serverUrl/v1/models"
            } else {
                "$serverUrl/v1/models?key=$currentApiKey"
            }
            openUrlInBrowser(url)
        }

        btnTestHealthz.setOnClickListener {
            openUrlInBrowser("$serverUrl/healthz")
        }

        checkNotificationPermission()
        startStatusChecker()
    }

    private fun getConfigFile(): File {
        val dir = File(noBackupFilesDir, "cliproxy")
        return File(dir, "config.yaml")
    }

    private fun refreshAuthSettingsUI() {
        currentApiKey = loadApiKeyFromConfigFile()
        if (currentApiKey.isNullOrBlank()) {
            tvApiKeyStatus.text = "当前: 免密模式 (浏览器与客户端可直接访问)"
            tvApiKeyStatus.setTextColor(Color.parseColor("#34D399"))
            tvApiKeyValue.text = "无需 API Key (开放本地访问)"
            btnCopyKey.isEnabled = false
            btnToggleAuthMode.text = "启用密钥保护"
        } else {
            tvApiKeyStatus.text = "当前: 已启用密钥验证"
            tvApiKeyStatus.setTextColor(Color.parseColor("#38BDF8"))
            tvApiKeyValue.text = currentApiKey
            btnCopyKey.isEnabled = true
            btnToggleAuthMode.text = "切换为免密访问 (推荐)"
        }
    }

    private fun loadApiKeyFromConfigFile(): String? {
        val file = getConfigFile()
        if (!file.exists()) return null
        return try {
            val lines = file.readLines()
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("api-keys:")) {
                    if (line == "api-keys: []") return null
                    for (j in (i + 1) until lines.size) {
                        val subLine = lines[j].trim()
                        if (subLine.startsWith("-")) {
                            val key = subLine.removePrefix("-").trim()
                                .removeSurrounding("\"").removeSurrounding("'")
                            if (key.isNotBlank()) return key
                        } else if (subLine.isNotEmpty() && !subLine.startsWith("#")) {
                            break
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun toggleAuthMode(configDir: String) {
        val file = getConfigFile()
        if (!file.exists()) {
            // 如果文件还没创建，直接生成免密或有密模式
            File(noBackupFilesDir, "cliproxy").mkdirs()
            file.createNewFile()
        }

        try {
            var content = file.readText()
            if (currentApiKey.isNullOrBlank()) {
                // 生成新 Key
                val newKey = "cpa-" + generateRandomHex(16)
                content = if (content.contains("api-keys:")) {
                    content.replace(Regex("""(?m)^api-keys:(\s*(\n\s*-\s*[^\n]+)+|\s*\[\])"""), "api-keys:\n  - \"$newKey\"")
                } else {
                    "$content\napi-keys:\n  - \"$newKey\"\n"
                }
                file.writeText(content)
                Toast.makeText(this, "已生成 API Key: $newKey", Toast.LENGTH_LONG).show()
            } else {
                // 切换为免密模式
                content = content.replace(Regex("""(?m)^api-keys:(\s*(\n\s*-\s*[^\n]+)+|\s*\[\])"""), "api-keys: []")
                file.writeText(content)
                Toast.makeText(this, "已切换为免密模式，重启服务后生效", Toast.LENGTH_SHORT).show()
            }

            refreshAuthSettingsUI()

            if (isRunning) {
                // 重启服务以应用新配置
                stopProxy()
                lifecycleScope.launch {
                    delay(1200)
                    startProxy(configDir)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "修改配置失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateRandomHex(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun openUrlInBrowser(url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this, Uri.parse(url))
        } catch (_: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        }
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
        openUrlInBrowser("$serverUrl/management.html")
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
            btnTestModels.isEnabled = false
            btnTestHealthz.isEnabled = false
            return
        }

        btnToggle.isEnabled = true
        if (running) {
            tvStatus.text = "运行中 (127.0.0.1:8317)"
            statusIndicator.setBackgroundColor(Color.parseColor("#10B981")) // 绿色
            btnToggle.text = "停止服务"
            btnToggle.setBackgroundColor(Color.parseColor("#EF4444")) // 红色
            btnOpenWeb.isEnabled = true
            btnTestModels.isEnabled = true
            btnTestHealthz.isEnabled = true
        } else {
            tvStatus.text = "已停止"
            statusIndicator.setBackgroundColor(Color.parseColor("#64748B")) // 灰色
            btnToggle.text = "启动服务"
            btnToggle.setBackgroundColor(Color.parseColor("#2563EB")) // 蓝色
            btnOpenWeb.isEnabled = false
            btnTestModels.isEnabled = false
            btnTestHealthz.isEnabled = false
        }
    }
}
