package com.cliproxy

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import com.google.android.material.textfield.TextInputEditText
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var btnLanguage: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvEndpointLocal: TextView
    private lateinit var tvEndpointLan: TextView
    private lateinit var tvDataDir: TextView
    private lateinit var statusIndicator: View
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnOpenWeb: MaterialButton
    private lateinit var btnCopyLocalhost: MaterialButton
    private lateinit var btnCopyLan: MaterialButton
    private lateinit var tvClaudeEndpointLocal: TextView
    private lateinit var tvClaudeEndpointLan: TextView
    private lateinit var btnCopyClaudeLocalhost: MaterialButton
    private lateinit var btnCopyClaudeLan: MaterialButton
    private lateinit var tvApiKeyStatus: TextView
    private lateinit var etApiKey: TextInputEditText
    private lateinit var btnSaveApiKey: MaterialButton
    private lateinit var btnGenerateApiKey: MaterialButton
    private lateinit var btnCopyKey: MaterialButton
    private lateinit var btnDisableApiKey: MaterialButton
    private lateinit var tvMgmtKeyStatus: TextView
    private lateinit var tvMgmtKeyValue: TextView
    private lateinit var btnCopyMgmtKey: MaterialButton
    private lateinit var btnEditMgmtKey: MaterialButton

    private var isRunning = false
    private var localIp: String = "127.0.0.1"
    private var currentApiKey: String? = null
    private var currentMgmtKey: String = "admin8317"

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnLanguage = findViewById(R.id.btnLanguage)
        tvStatus = findViewById(R.id.tvStatus)
        tvEndpointLocal = findViewById(R.id.tvEndpointLocal)
        tvEndpointLan = findViewById(R.id.tvEndpointLan)
        tvDataDir = findViewById(R.id.tvDataDir)
        statusIndicator = findViewById(R.id.statusIndicator)
        btnToggle = findViewById(R.id.btnToggle)
        btnOpenWeb = findViewById(R.id.btnOpenWeb)
        btnCopyLocalhost = findViewById(R.id.btnCopyLocalhost)
        btnCopyLan = findViewById(R.id.btnCopyLan)
        tvClaudeEndpointLocal = findViewById(R.id.tvClaudeEndpointLocal)
        tvClaudeEndpointLan = findViewById(R.id.tvClaudeEndpointLan)
        btnCopyClaudeLocalhost = findViewById(R.id.btnCopyClaudeLocalhost)
        btnCopyClaudeLan = findViewById(R.id.btnCopyClaudeLan)
        tvApiKeyStatus = findViewById(R.id.tvApiKeyStatus)
        etApiKey = findViewById(R.id.etApiKey)
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey)
        btnGenerateApiKey = findViewById(R.id.btnGenerateApiKey)
        btnCopyKey = findViewById(R.id.btnCopyKey)
        btnDisableApiKey = findViewById(R.id.btnDisableApiKey)
        tvMgmtKeyStatus = findViewById(R.id.tvMgmtKeyStatus)
        tvMgmtKeyValue = findViewById(R.id.tvMgmtKeyValue)
        btnCopyMgmtKey = findViewById(R.id.btnCopyMgmtKey)
        btnEditMgmtKey = findViewById(R.id.btnEditMgmtKey)

        val configDir = "${noBackupFilesDir.absolutePath}/cliproxy"
        tvDataDir.text = getString(R.string.data_directory, configDir)

        updateLocalIp()
        refreshAuthSettingsUI()

        btnLanguage.setOnClickListener {
            val nextLanguage = if (AppLanguage.get(this) == AppLanguage.CHINESE) {
                AppLanguage.ENGLISH
            } else {
                AppLanguage.CHINESE
            }
            AppLanguage.set(this, nextLanguage)
            recreate()
        }

        btnToggle.setOnClickListener {
            if (isRunning) {
                stopProxy()
            } else {
                startProxy(configDir)
            }
        }

        btnCopyLocalhost.setOnClickListener {
            val apiUrl = "http://127.0.0.1:8317/v1"
            copyToClipboard(getString(R.string.clipboard_local_address), apiUrl)
            Toast.makeText(this, getString(R.string.toast_local_address_copied, apiUrl), Toast.LENGTH_SHORT).show()
        }

        btnCopyLan.setOnClickListener {
            val apiUrl = "http://$localIp:8317/v1"
            copyToClipboard(getString(R.string.clipboard_lan_address), apiUrl)
            Toast.makeText(this, getString(R.string.toast_lan_address_copied, apiUrl), Toast.LENGTH_SHORT).show()
        }

        btnCopyClaudeLocalhost.setOnClickListener {
            val apiUrl = "http://127.0.0.1:8317"
            copyToClipboard(getString(R.string.clipboard_claude_local_address), apiUrl)
            Toast.makeText(this, getString(R.string.toast_local_address_copied, apiUrl), Toast.LENGTH_SHORT).show()
        }

        btnCopyClaudeLan.setOnClickListener {
            val apiUrl = "http://$localIp:8317"
            copyToClipboard(getString(R.string.clipboard_claude_lan_address), apiUrl)
            Toast.makeText(this, getString(R.string.toast_lan_address_copied, apiUrl), Toast.LENGTH_SHORT).show()
        }

        btnOpenWeb.setOnClickListener {
            openManagementPage()
        }

        btnCopyKey.setOnClickListener {
            currentApiKey?.let { key ->
                copyToClipboard(getString(R.string.clipboard_api_key), key)
                Toast.makeText(this, R.string.toast_api_key_copied, Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveApiKey.setOnClickListener {
            val newKey = etApiKey.text?.toString()?.trim().orEmpty()
            if (newKey.isBlank()) {
                etApiKey.error = getString(R.string.api_key_required)
            } else {
                saveApiKey(newKey, configDir, generated = false)
            }
        }

        btnGenerateApiKey.setOnClickListener {
            val newKey = "cpa-" + generateRandomHex(16)
            saveApiKey(newKey, configDir, generated = true)
        }

        btnDisableApiKey.setOnClickListener {
            saveApiKey(null, configDir, generated = false)
        }

        btnCopyMgmtKey.setOnClickListener {
            copyToClipboard(getString(R.string.clipboard_management_key), currentMgmtKey)
            Toast.makeText(this, getString(R.string.toast_management_key_copied, currentMgmtKey), Toast.LENGTH_SHORT).show()
        }

        btnEditMgmtKey.setOnClickListener {
            showEditManagementKeyDialog(configDir)
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
        tvEndpointLocal.text = getString(R.string.local_address, "http://127.0.0.1:8317/v1")
        tvEndpointLan.text = getString(R.string.lan_address, "http://$localIp:8317/v1")
        tvClaudeEndpointLocal.text = getString(R.string.local_address, "http://127.0.0.1:8317")
        tvClaudeEndpointLan.text = getString(R.string.lan_address, "http://$localIp:8317")
    }

    private fun detectLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prefer physical Wi-Fi or Ethernet private addresses and exclude VPN interfaces.
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
            // Fall back to any non-loopback IPv4 address.
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
            hint = getString(R.string.management_key_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val container = android.widget.FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_management_key_title)
            .setMessage(R.string.edit_management_key_message)
            .setView(container)
            .setPositiveButton(R.string.save_and_restart) { _, _ ->
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
                    Toast.makeText(this, getString(R.string.toast_management_key_updated, newKey), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshAuthSettingsUI() {
        currentApiKey = loadApiKeyFromConfigFile()
        if (currentApiKey.isNullOrBlank()) {
            tvApiKeyStatus.setText(R.string.auth_mode_keyless)
            tvApiKeyStatus.setTextColor(Color.parseColor("#34D399"))
            etApiKey.setText("")
            btnCopyKey.isEnabled = false
            btnDisableApiKey.isEnabled = false
        } else {
            tvApiKeyStatus.setText(R.string.auth_mode_protected)
            tvApiKeyStatus.setTextColor(Color.parseColor("#38BDF8"))
            etApiKey.setText(currentApiKey)
            etApiKey.setSelection(etApiKey.text?.length ?: 0)
            btnCopyKey.isEnabled = true
            btnDisableApiKey.isEnabled = true
        }
        etApiKey.error = null

        currentMgmtKey = loadManagementKey()
        tvMgmtKeyValue.text = currentMgmtKey
    }

    private fun loadApiKeyFromConfigFile(): String? {
        val file = getConfigFile()
        if (!file.exists()) return null
        return try {
            val lines = file.readLines()
            for (i in lines.indices) {
                val rawLine = lines[i]
                val line = rawLine.trim()
                if (rawLine.isNotBlank() && !rawLine.first().isWhitespace() && line.startsWith("api-keys:")) {
                    val inlineValue = line.substringAfter(':').trim()
                    if (inlineValue == "[]") return null
                    for (j in (i + 1) until lines.size) {
                        val rawSubLine = lines[j]
                        val subLine = rawSubLine.trim()
                        if (rawSubLine.isNotBlank() && !rawSubLine.first().isWhitespace() && !subLine.startsWith("#")) {
                            break
                        }
                        if (subLine.startsWith("-")) {
                            val key = decodeYamlScalar(subLine.removePrefix("-").trim())
                            if (key.isNotBlank()) return key
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun saveApiKey(newKey: String?, configDir: String, generated: Boolean) {
        val file = getConfigFile()
        try {
            file.parentFile?.mkdirs()
            val content = if (file.exists()) file.readText() else ""
            val updatedContent = updateApiKeysSection(content, newKey)
            writeConfigAtomically(file, updatedContent)

            if (newKey == null) {
                Toast.makeText(this, R.string.toast_switched_to_keyless, Toast.LENGTH_SHORT).show()
            } else if (generated) {
                Toast.makeText(this, R.string.toast_api_key_generated, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_api_key_saved, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.toast_config_update_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateApiKeysSection(content: String, apiKey: String?): String {
        val lineEnding = if (content.contains("\r\n")) "\r\n" else "\n"
        val hadTrailingLineEnding = content.endsWith("\n")
        val lines = content.split(Regex("\r?\n")).toMutableList()
        val sectionIndex = lines.indexOfFirst { line ->
            line.isNotBlank() && !line.first().isWhitespace() && line.trim().startsWith("api-keys:")
        }
        val sectionHeader = if (apiKey == null) "api-keys: []" else "api-keys:"

        if (sectionIndex == -1) {
            val prefix = when {
                content.isEmpty() -> ""
                content.endsWith("\n") -> ""
                else -> lineEnding
            }
            val keyLine = apiKey?.let { "$lineEnding  - ${encodeYamlScalar(it)}" }.orEmpty()
            return "$content$prefix$sectionHeader$keyLine$lineEnding"
        }

        lines[sectionIndex] = sectionHeader
        var cursor = sectionIndex + 1
        while (cursor < lines.size) {
            val line = lines[cursor]
            val trimmed = line.trim()
            if (line.isNotBlank() && !line.first().isWhitespace() && !trimmed.startsWith("#")) {
                break
            }
            if (trimmed.startsWith("-")) {
                lines.removeAt(cursor)
            } else {
                cursor++
            }
        }
        if (apiKey != null) {
            lines.add(sectionIndex + 1, "  - ${encodeYamlScalar(apiKey)}")
        }

        val updated = lines.joinToString(lineEnding)
        return if (hadTrailingLineEnding && !updated.endsWith(lineEnding)) updated + lineEnding else updated
    }

    private fun encodeYamlScalar(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun decodeYamlScalar(value: String): String {
        if (value.length < 2) return value
        return when {
            value.startsWith("\"") && value.endsWith("\"") -> value.substring(1, value.length - 1)
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            value.startsWith("'") && value.endsWith("'") -> value.substring(1, value.length - 1)
                .replace("''", "'")
            else -> value
        }
    }

    private fun writeConfigAtomically(file: File, content: String) {
        val parent = requireNotNull(file.parentFile)
        val temporaryFile = File.createTempFile("config-", ".yaml.tmp", parent)
        try {
            temporaryFile.writeText(content)
            try {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
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
            tvStatus.setText(R.string.status_starting)
            statusIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            btnToggle.setText(R.string.action_starting)
            btnToggle.isEnabled = false
            btnOpenWeb.isEnabled = false
            btnCopyLocalhost.isEnabled = false
            btnCopyLan.isEnabled = false
            btnCopyClaudeLocalhost.isEnabled = false
            btnCopyClaudeLan.isEnabled = false
            btnSaveApiKey.isEnabled = false
            btnGenerateApiKey.isEnabled = false
            btnCopyKey.isEnabled = false
            btnDisableApiKey.isEnabled = false
            btnCopyMgmtKey.isEnabled = false
            btnEditMgmtKey.isEnabled = false
            return
        }

        btnToggle.isEnabled = true
        btnCopyLocalhost.isEnabled = true
        btnCopyLan.isEnabled = true
        btnCopyClaudeLocalhost.isEnabled = true
        btnCopyClaudeLan.isEnabled = true
        btnSaveApiKey.isEnabled = true
        btnGenerateApiKey.isEnabled = true
        btnCopyKey.isEnabled = !currentApiKey.isNullOrBlank()
        btnDisableApiKey.isEnabled = !currentApiKey.isNullOrBlank()
        btnCopyMgmtKey.isEnabled = true
        btnEditMgmtKey.isEnabled = true
        if (running) {
            tvStatus.setText(R.string.status_running)
            statusIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            btnToggle.setText(R.string.action_stop)
            btnToggle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            btnOpenWeb.isEnabled = true
        } else {
            tvStatus.setText(R.string.status_stopped)
            statusIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))
            btnToggle.setText(R.string.action_start)
            btnToggle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))
            btnOpenWeb.isEnabled = false
        }
    }
}
