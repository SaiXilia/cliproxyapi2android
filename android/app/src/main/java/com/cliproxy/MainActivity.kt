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
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.security.SecureRandom
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvEndpointLocal: TextView
    private lateinit var tvEndpointLan: TextView
    private lateinit var tvDataDir: TextView
    private lateinit var statusIndicator: View
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnOpenWeb: MaterialButton
    private lateinit var btnCopyLocalhost: MaterialButton
    private lateinit var btnCopyLan: MaterialButton
    private lateinit var tvApiKeyStatus: TextView
    private lateinit var tvApiKeyValue: TextView
    private lateinit var btnCopyKey: MaterialButton
    private lateinit var btnToggleAuthMode: MaterialButton
    private lateinit var tvMgmtKeyStatus: TextView
    private lateinit var tvMgmtKeyValue: TextView
    private lateinit var btnCopyMgmtKey: MaterialButton
    private lateinit var btnEditMgmtKey: MaterialButton
    private lateinit var btnTestModels: MaterialButton
    private lateinit var btnTestHealthz: MaterialButton

    private var isRunning = false
    private var localIp: String = "127.0.0.1"
    private var currentApiKey: String? = null
    private var currentMgmtKey: String = "admin8317"

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvEndpointLocal = findViewById(R.id.tvEndpointLocal)
        tvEndpointLan = findViewById(R.id.tvEndpointLan)
        tvDataDir = findViewById(R.id.tvDataDir)
        statusIndicator = findViewById(R.id.statusIndicator)
        btnToggle = findViewById(R.id.btnToggle)
        btnOpenWeb = findViewById(R.id.btnOpenWeb)
        btnCopyLocalhost = findViewById(R.id.btnCopyLocalhost)
        btnCopyLan = findViewById(R.id.btnCopyLan)
        tvApiKeyStatus = findViewById(R.id.tvApiKeyStatus)
        tvApiKeyValue = findViewById(R.id.tvApiKeyValue)
        btnCopyKey = findViewById(R.id.btnCopyKey)
        btnToggleAuthMode = findViewById(R.id.btnToggleAuthMode)
        tvMgmtKeyStatus = findViewById(R.id.tvMgmtKeyStatus)
        tvMgmtKeyValue = findViewById(R.id.tvMgmtKeyValue)
        btnCopyMgmtKey = findViewById(R.id.btnCopyMgmtKey)
        btnEditMgmtKey = findViewById(R.id.btnEditMgmtKey)
        btnTestModels = findViewById(R.id.btnTestModels)
        btnTestHealthz = findViewById(R.id.btnTestHealthz)

        val configDir = "${noBackupFilesDir.absolutePath}/cliproxy"
        tvDataDir.text = "数据目录: $configDir"

        updateLocalIp()
        refreshAuthSettingsUI()

        btnToggle.setOnClickListener {
            if (isRunning) {
                stopProxy()
            } else {
                startProxy(configDir)
            }
        }

        btnCopyLocalhost.setOnClickListener {
            val apiUrl = "http://127.0.0.1:8317/v1"
            copyToClipboard("CLIProxy 本机地址", apiUrl)
            Toast.makeText(this, "已复制本机地址: $apiUrl", Toast.LENGTH_SHORT).show()
        }

        btnCopyLan.setOnClickListener {
            val apiUrl = "http://$localIp:8317/v1"
            copyToClipboard("CLIProxy 局域网地址", apiUrl)
            Toast.makeText(this, "已复制局域网地址: $apiUrl", Toast.LENGTH_SHORT).show()
        }

        btnOpenWeb.setOnClickListener {
            openManagementPage()
        }

        btnCopyKey.setOnClickListener {
            currentApiKey?.let { key ->
                copyToClipboard("CLIProxy API Key", key)
                Toast.makeText(this, "API Key 已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggleAuthMode.setOnClickListener {
            toggleAuthMode(configDir)
        }

        btnCopyMgmtKey.setOnClickListener {
            copyToClipboard("CLIProxy 管理密钥", currentMgmtKey)
            Toast.makeText(this, "管理密钥已复制: $currentMgmtKey", Toast.LENGTH_SHORT).show()
        }

        btnEditMgmtKey.setOnClickListener {
            showEditManagementKeyDialog(configDir)
        }

        btnTestModels.setOnClickListener {
            val url = if (currentApiKey.isNullOrBlank()) {
                "http://127.0.0.1:8317/v1/models"
            } else {
                "http://127.0.0.1:8317/v1/models?key=$currentApiKey"
            }
            openUrlInBrowser(url)
        }

        btnTestHealthz.setOnClickListener {
            openUrlInBrowser("http://127.0.0.1:8317/healthz")
        }

        checkNotificationPermission()
        startStatusChecker()
        handleServiceIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleServiceIntent(intent)
    }

    private fun handleServiceIntent(intent: Intent?) {
        if (intent == null) return
        val configDir = "${noBackupFilesDir.absolutePath}/cliproxy"
        if (intent.getBooleanExtra("start_service", false)) {
            startProxy(configDir)
        } else if (intent.getBooleanExtra("stop_service", false)) {
            stopProxy()
        }
    }

    override fun onResume() {
        super.onResume()
        updateLocalIp()
    }

    private fun updateLocalIp() {
        localIp = detectLocalIpAddress()
        tvEndpointLocal.text = "本机地址: http://127.0.0.1:8317/v1"
        tvEndpointLan.text = "局域网地址: http://$localIp:8317/v1"
    }

    private fun detectLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // 优先查找物理 Wi-Fi / 以太网私有 IP (192.168.x / 10.x / 172.16-31.x 排除 VPN)
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.contains("tun") || name.contains("dummy") || name.contains("p2p")) continue

                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.") || isPrivate172(host)) {
                            return host
                        }
                    }
                }
            }
            // 次选任意非回环 IPv4
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    private fun isPrivate172(ip: String): Boolean {
        if (!ip.startsWith("172.")) return false
        val parts = ip.split(".")
        if (parts.size >= 2) {
            val second = parts[1].toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun getConfigFile(): File {
        val dir = File(noBackupFilesDir, "cliproxy")
        return File(dir, "config.yaml")
    }

    private fun getManagementKeyFile(): File {
        val dir = File(noBackupFilesDir, "cliproxy")
        return File(dir, "management_key.txt")
    }

    private fun loadManagementKey(): String {
        val file = getManagementKeyFile()
        if (file.exists()) {
            val key = file.readText().trim()
            if (key.isNotEmpty()) return key
        }
        val defaultKey = "admin8317"
        try {
            file.parentFile?.mkdirs()
            file.writeText(defaultKey)
        } catch (_: Exception) {}
        return defaultKey
    }

    private fun saveManagementKey(newKey: String) {
        val file = getManagementKeyFile()
        try {
            file.parentFile?.mkdirs()
            file.writeText(newKey.trim())
        } catch (_: Exception) {}
    }

    private fun showEditManagementKeyDialog(configDir: String) {
        val currentKey = loadManagementKey()
        val input = android.widget.EditText(this).apply {
            setText(currentKey)
            setSelection(currentKey.length)
            hint = "请输入新的管理密钥"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val container = android.widget.FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("修改 WebUI 管理密钥")
            .setMessage("用于浏览器访问 WebUI 控制台的登录密码:")
            .setView(container)
            .setPositiveButton("保存并重启") { _, _ ->
                val newKey = input.text.toString().trim()
                if (newKey.isNotEmpty()) {
                    saveManagementKey(newKey)
                    refreshAuthSettingsUI()
                    if (isRunning) {
                        stopProxy()
                        lifecycleScope.launch {
                            delay(1200)
                            startProxy(configDir)
                        }
                    }
                    Toast.makeText(this, "管理密钥已更新: $newKey", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshAuthSettingsUI() {
        currentApiKey = loadApiKeyFromConfigFile()
        if (currentApiKey.isNullOrBlank()) {
            tvApiKeyStatus.text = "当前: 免密模式"
            tvApiKeyStatus.setTextColor(Color.parseColor("#34D399"))
            tvApiKeyValue.text = "无需 API Key"
            btnCopyKey.isEnabled = false
            btnToggleAuthMode.text = "启用密钥保护"
        } else {
            tvApiKeyStatus.text = "当前: 已启用密钥验证"
            tvApiKeyStatus.setTextColor(Color.parseColor("#38BDF8"))
            tvApiKeyValue.text = currentApiKey
            btnCopyKey.isEnabled = true
            btnToggleAuthMode.text = "切换为免密访问"
        }

        currentMgmtKey = loadManagementKey()
        tvMgmtKeyValue.text = currentMgmtKey
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
            File(noBackupFilesDir, "cliproxy").mkdirs()
            file.createNewFile()
        }

        try {
            var content = file.readText()
            if (currentApiKey.isNullOrBlank()) {
                val newKey = "cpa-" + generateRandomHex(16)
                content = if (content.contains("api-keys:")) {
                    content.replace(Regex("""(?m)^api-keys:(\s*(\n\s*-\s*[^\n]+)+|\s*\[\])"""), "api-keys:\n  - \"$newKey\"")
                } else {
                    "$content\napi-keys:\n  - \"$newKey\"\n"
                }
                file.writeText(content)
                Toast.makeText(this, "已生成 API Key: $newKey", Toast.LENGTH_LONG).show()
            } else {
                content = content.replace(Regex("""(?m)^api-keys:(\s*(\n\s*-\s*[^\n]+)+|\s*\[\])"""), "api-keys: []")
                file.writeText(content)
                Toast.makeText(this, "已切换为免密模式，重启生效", Toast.LENGTH_SHORT).show()
            }

            refreshAuthSettingsUI()

            if (isRunning) {
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
            putExtra(CLIProxyForegroundService.EXTRA_HOST, "0.0.0.0")
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
        val mgmtKey = loadManagementKey()
        val url = if (mgmtKey.isBlank()) {
            "http://127.0.0.1:8317/management.html"
        } else {
            "http://127.0.0.1:8317/management.html?mgmt_key=$mgmtKey"
        }
        openUrlInBrowser(url)
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
            val connection = URL("http://127.0.0.1:8317/healthz").openConnection() as HttpURLConnection
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
            btnCopyLocalhost.isEnabled = false
            btnCopyLan.isEnabled = false
            btnCopyMgmtKey.isEnabled = false
            btnEditMgmtKey.isEnabled = false
            return
        }

        btnToggle.isEnabled = true
        btnCopyLocalhost.isEnabled = true
        btnCopyLan.isEnabled = true
        btnCopyMgmtKey.isEnabled = true
        btnEditMgmtKey.isEnabled = true
        if (running) {
            tvStatus.text = "运行中"
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
