package com.cliproxy.update

import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.cliproxy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val coreVersion: String,
    val packageName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String
) {
    fun toJson(): String = JSONObject()
        .put("version_code", versionCode)
        .put("version_name", versionName)
        .put("core_version", coreVersion)
        .put("package_name", packageName)
        .put("apk_url", apkUrl)
        .put("sha256", sha256)
        .put("release_notes", releaseNotes)
        .toString()

    companion object {
        fun fromJson(raw: String): AppUpdate {
            val json = JSONObject(raw)
            val packages = json.optJSONObject("packages")
            val selectedPackage = if (packages == null) {
                json
            } else {
                Build.SUPPORTED_ABIS
                    .firstNotNullOfOrNull { abi -> packages.optJSONObject(abi) }
                    ?: error("No update package is available for this device architecture")
            }
            return AppUpdate(
                versionCode = json.getLong("version_code"),
                versionName = json.getString("version_name"),
                coreVersion = json.getString("core_version"),
                packageName = json.getString("package_name"),
                apkUrl = selectedPackage.getString("apk_url"),
                sha256 = selectedPackage.getString("sha256").lowercase(Locale.US),
                releaseNotes = json.optString("release_notes")
            )
        }
    }
}

sealed class UpdateCheckResult {
    data class Available(val update: AppUpdate) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data object SourceUnavailable : UpdateCheckResult()
    data class Failed(val reason: String) : UpdateCheckResult()
}

object UpdateManager {
    private const val MANIFEST_URL =
        "https://github.com/SaiXilia/cliproxyapi2android/releases/latest/download/update.json"
    private const val USER_AGENT = "CLIProxyAPI-Android/${BuildConfig.VERSION_NAME}"
    private const val PREFS_NAME = "app_update"
    private const val KEY_AVAILABLE_UPDATE = "available_update"
    private const val LEGACY_UPDATE_JOB_ID = 8318
    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val MAX_APK_BYTES = 512L * 1024L * 1024L
    private const val MAX_REDIRECTS = 5

    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val manifest = fetchManifest()
            if (manifest.packageName != context.packageName) {
                return@withContext UpdateCheckResult.Failed("Update package name does not match")
            }
            if (manifest.versionCode <= BuildConfig.VERSION_CODE.toLong()) {
                clearCachedUpdate(context)
                return@withContext UpdateCheckResult.UpToDate
            }

            cacheUpdate(context, manifest)
            UpdateCheckResult.Available(manifest)
        } catch (e: HttpStatusException) {
            if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND ||
                e.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                e.statusCode == HttpURLConnection.HTTP_FORBIDDEN
            ) {
                UpdateCheckResult.SourceUnavailable
            } else {
                UpdateCheckResult.Failed("Update server returned HTTP ${e.statusCode}")
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: "Unknown update error")
        }
    }

    fun cancelLegacyBackgroundCheck(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(LEGACY_UPDATE_JOB_ID)
    }

    fun loadCachedUpdate(context: Context): AppUpdate? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AVAILABLE_UPDATE, null)
            ?: return null
        return try {
            AppUpdate.fromJson(raw).takeIf { it.versionCode > BuildConfig.VERSION_CODE.toLong() }
        } catch (_: Exception) {
            clearCachedUpdate(context)
            null
        }
    }

    fun clearCachedUpdate(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_AVAILABLE_UPDATE)
            .apply()
    }

    suspend fun downloadAndVerify(context: Context, update: AppUpdate): File =
        withContext(Dispatchers.IO) {
            require(update.versionCode > BuildConfig.VERSION_CODE.toLong()) {
                "Downloaded update is not newer than the installed app"
            }

            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists() && !updateDir.mkdirs()) {
                error("Unable to create update cache")
            }
            updateDir.listFiles()?.forEach { existing ->
                if (existing.name != "cliproxy-update.apk") existing.delete()
            }

            val temporary = File(updateDir, "cliproxy-update.apk.part")
            val target = File(updateDir, "cliproxy-update.apk")
            temporary.delete()
            target.delete()

            val connection = openTrustedConnection(update.apkUrl, "application/vnd.android.package-archive")
            try {
                val contentLength = connection.contentLengthLong
                if (contentLength > MAX_APK_BYTES) {
                    error("Update package is too large")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var totalBytes = 0L
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            if (totalBytes > MAX_APK_BYTES) error("Update package is too large")
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }

                val actualSha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                if (!actualSha256.equals(update.sha256, ignoreCase = true)) {
                    error("Update checksum verification failed")
                }
                verifyApkIdentity(context, temporary, update)

                if (!temporary.renameTo(target)) {
                    error("Unable to finalize downloaded update")
                }
                target
            } catch (e: Exception) {
                temporary.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }

    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun cacheUpdate(context: Context, update: AppUpdate) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AVAILABLE_UPDATE, update.toJson())
            .apply()
    }

    private fun fetchManifest(): AppUpdate {
        val connection = openTrustedConnection(MANIFEST_URL, "application/json")
        return try {
            val output = ByteArrayOutputStream()
            BufferedInputStream(connection.inputStream).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_MANIFEST_BYTES) {
                        error("Update manifest is too large")
                    }
                    output.write(buffer, 0, read)
                }
            }

            val json = JSONObject(output.toString(Charsets.UTF_8.name()))
            if (json.optInt("schema_version") != 1) {
                error("Unsupported update manifest version")
            }
            AppUpdate.fromJson(json.toString()).also { update ->
                require(update.versionName.isNotBlank()) { "Update version name is empty" }
                require(update.coreVersion.isNotBlank()) { "Core version is empty" }
                require(update.sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                    "Update checksum is invalid"
                }
                require(URL(update.apkUrl).protocol.equals("https", ignoreCase = true)) {
                    "Update URL must use HTTPS"
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openTrustedConnection(initialUrl: String, accept: String): HttpURLConnection {
        var currentUrl = URL(initialUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            validateTrustedUrl(currentUrl)
            val connection = currentUrl.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val statusCode = connection.responseCode
            if (statusCode in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("Update redirect is missing a destination")
                connection.disconnect()
                if (redirectCount >= MAX_REDIRECTS) error("Too many update redirects")
                currentUrl = URL(currentUrl, location)
                return@repeat
            }
            if (statusCode !in 200..299) {
                connection.disconnect()
                throw HttpStatusException(statusCode)
            }
            return connection
        }
        error("Too many update redirects")
    }

    private fun validateTrustedUrl(url: URL) {
        require(url.protocol.equals("https", ignoreCase = true)) { "Update URL must use HTTPS" }
        val host = url.host.lowercase(Locale.US)
        require(host == "github.com" || host.endsWith(".githubusercontent.com")) {
            "Untrusted update host"
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApkIdentity(context: Context, apkFile: File, update: AppUpdate) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        require(archiveInfo.packageName == context.packageName) { "Update package name does not match" }
        require(packageVersionCode(archiveInfo) == update.versionCode) { "Update version code does not match" }

        val installedInfo = context.packageManager.getPackageInfo(context.packageName, flags)
        val installedSigners = signingCertificateDigests(installedInfo)
        val updateSigners = signingCertificateDigests(archiveInfo)
        require(installedSigners.isNotEmpty() && updateSigners.isNotEmpty()) {
            "Unable to read APK signing certificate"
        }
        require(installedSigners.any(updateSigners::contains)) { "Update signing certificate does not match" }
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signingCertificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            info.signatures
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}

private class HttpStatusException(val statusCode: Int) : Exception()
