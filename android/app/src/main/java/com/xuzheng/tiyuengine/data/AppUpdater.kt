package com.xuzheng.tiyuengine.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.xuzheng.tiyuengine.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
)

object UpdateVersions {
    fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = current.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val difference = remoteParts.getOrElse(index) { 0 } - currentParts.getOrElse(index) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }
}

class AppUpdater(private val context: Context) {
    fun checkForUpdate(): UpdateInfo? {
        val connection = openConnection(RELEASE_API)
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) error("开发者尚未发布可供更新的安装包")
            check(connection.responseCode in 200..299) { "检查更新失败 (${connection.responseCode})" }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = release.getString("tag_name").removePrefix("v")
            if (!UpdateVersions.isNewer(version, BuildConfig.VERSION_NAME)) return null
            val assets = release.getJSONArray("assets")
            val apkUrl = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
                ?.getString("browser_download_url")
                ?: error("新版本没有附带 APK 安装包")
            return UpdateInfo(version, release.optString("body").ifBlank { "修复问题并优化使用体验。" }, apkUrl)
        } finally {
            connection.disconnect()
        }
    }

    fun download(update: UpdateInfo): File {
        val directory = updateCacheDirectory().apply { deleteRecursively(); mkdirs() }
        val target = File(directory, "题域引擎-${update.versionName}.apk")
        var lastError: Throwable? = null
        repeat(2) {
            runCatching { downloadTo(update.downloadUrl, target) }
                .onSuccess {
                    require(target.length() > 0) { "下载的安装包为空" }
                    require(hasCurrentSignature(target)) { "安装包签名与当前应用不一致，已拒绝安装" }
                    return target
                }
                .onFailure { error -> lastError = error; target.delete() }
        }
        error(lastError?.message?.let { "下载更新失败：$it" } ?: "下载更新失败，请检查网络后重试")
    }

    fun cachedUpdateBytes(): Long = updateCacheDirectory().takeIf(File::exists)
        ?.walkTopDown()?.filter(File::isFile)?.sumOf(File::length) ?: 0L

    fun clearCachedUpdates(): Boolean {
        val directory = updateCacheDirectory()
        return !directory.exists() || directory.deleteRecursively()
    }

    private fun updateCacheDirectory(): File = File(context.cacheDir, "updates")

    private fun downloadTo(url: String, target: File) {
        val connection = openConnection(url)
        try {
            check(connection.responseCode in 200..299) { "服务器返回 ${connection.responseCode}" }
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }

    fun install(apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }

    @Suppress("DEPRECATION")
    private fun hasCurrentSignature(apk: File): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val downloaded = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        return certificateDigests(current) == certificateDigests(downloaded)
    }

    @Suppress("DEPRECATION")
    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    private fun openConnection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "TiyuEngine/${BuildConfig.VERSION_NAME}")
    }

    private companion object {
        const val RELEASE_API = "https://api.github.com/repos/q2126221702-ux/geren/releases/latest"
    }
}
