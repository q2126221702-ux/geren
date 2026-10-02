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
import java.io.IOException
import java.security.MessageDigest

data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
    val versionCode: Int? = null,
)

data class UpdateReleaseMetadata(
    val versionName: String,
    val versionCode: Int?,
    val notes: String,
)

object UpdateVersions {
    private val versionNameMarker = Regex("""<!--\s*android-version-name:\s*([^\s<>]+)\s*-->""")
    private val versionCodeMarker = Regex("""<!--\s*android-version-code:\s*([^\s<>]+)\s*-->""")

    fun parseReleaseMetadata(tagName: String, body: String): UpdateReleaseMetadata {
        val versionName = versionNameMarker.find(body)?.groupValues?.get(1)
            ?: tagName.trim()
        require(Regex("v?[0-9]+(?:\\.[0-9]+){1,3}").matches(versionName) && versionName.length <= 40) { "发布版本信息无效" }
        val versionCode = versionCodeMarker.find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it > 0 }
        val notes = body.replace(versionNameMarker, "").replace(versionCodeMarker, "").trim()
        return UpdateReleaseMetadata(
            versionName.removePrefix("v"),
            versionCode,
            notes.ifBlank { "修复问题并优化使用体验。" },
        )
    }

    fun isNewer(remote: String, current: String, remoteVersionCode: Int? = null, currentVersionCode: Int? = null): Boolean {
        val remoteParts = remote.trim().removePrefix(
            "v"
        ).split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = current.trim().removePrefix(
            "v"
        ).split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val difference = remoteParts.getOrElse(index) { 0 }.compareTo(currentParts.getOrElse(index) { 0 })
            if (difference != 0) return difference > 0
        }
        return remoteVersionCode != null && currentVersionCode != null && remoteVersionCode > currentVersionCode
    }
}

class AppUpdater(private val context: Context) {
    fun checkForUpdate(): UpdateInfo? {
        return GithubDownloads.read(RELEASE_API, MAX_METADATA_BYTES) { input ->
            val text = input.readUtf8Bounded(MAX_METADATA_BYTES, "发布信息过大")
            requireBoundedJson(text)
            val release = JSONObject(text)
            val metadata = UpdateVersions.parseReleaseMetadata(release.getString("tag_name"), release.optString("body"))
            if (!UpdateVersions.isNewer(
                    metadata.versionName,
                    BuildConfig.VERSION_NAME,
                    metadata.versionCode,
                    BuildConfig.VERSION_CODE,
                )
            ) {
                return@read null
            }
            val assets = release.getJSONArray("assets")
            val apkUrl = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
                ?.getString("browser_download_url")
                ?: error("新版本没有附带 APK 安装包")
            require(apkUrl.startsWith(RELEASE_DOWNLOAD_PREFIX)) { "安装包来源不是官方发布仓库" }
            UpdateInfo(metadata.versionName, metadata.notes, apkUrl, metadata.versionCode)
        }
    }

    fun download(update: UpdateInfo): File {
        require(update.downloadUrl.startsWith(RELEASE_DOWNLOAD_PREFIX)) { "安装包来源不是官方发布仓库" }
        val directory = updateCacheDirectory().apply {
            check(!exists() || deleteRecursively()) { "无法清理旧安装包" }
            check(mkdirs()) { "无法创建安装包缓存" }
        }
        val target = File(directory, "update.apk")
        var lastError: IOException? = null
        repeat(2) {
            var complete = false
            try {
                downloadTo(update.downloadUrl, target)
                require(target.length() > 0) { "下载的安装包为空" }
                validateDownloadedApk(target, update)
                complete = true
                return target
            } catch (error: IOException) {
                lastError = error
            } finally {
                if (!complete) target.delete()
            }
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
        GithubDownloads.read(url, MAX_APK_BYTES) { input ->
            target.outputStream().use { input.copyBoundedTo(it, MAX_APK_BYTES, "安装包超过大小限制") }
        }
    }

    fun install(apk: File): Boolean {
        require(apk.canonicalFile.parentFile == updateCacheDirectory().canonicalFile) { "安装包不在更新缓存中" }
        validateDownloadedApk(apk, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }

    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(apk: File, update: UpdateInfo?) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val downloaded = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("安装包格式无效")
        val currentDigests = certificateDigests(current)
        require(currentDigests.isNotEmpty() && currentDigests == certificateDigests(downloaded)) {
            "安装包签名与当前应用不一致，已拒绝安装"
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) downloaded.longVersionCode else downloaded.versionCode.toLong()
        require(
            UpdatePackagePolicy.accepts(
                downloaded.packageName,
                downloaded.versionName,
                versionCode,
                context.packageName,
                BuildConfig.VERSION_CODE.toLong(),
                update,
            )
        ) { "安装包的应用或版本与更新信息不一致，已拒绝安装" }
    }

    @Suppress("DEPRECATION")
    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    private companion object {
        const val RELEASE_API = "https://api.github.com/repos/q2126221702-ux/geren/releases/latest"
        const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/q2126221702-ux/geren/releases/download/"
        const val MAX_METADATA_BYTES = 1024L * 1024
        const val MAX_APK_BYTES = 100L * 1024 * 1024
    }
}

internal object UpdatePackagePolicy {
    fun accepts(
        packageName: String?,
        versionName: String?,
        versionCode: Long,
        currentPackageName: String,
        currentVersionCode: Long,
        update: UpdateInfo?,
    ): Boolean = packageName == currentPackageName && versionCode > currentVersionCode &&
        (
            update == null || (
                versionName == update.versionName &&
                    (update.versionCode == null || versionCode == update.versionCode.toLong())
                )
            )
}
