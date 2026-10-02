package com.xuzheng.tiyuengine.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiMode { SHARED, OWN_KEY }

data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String,
    val hint: String,
)

object AiProviderCatalog {
    val providers = listOf(
        AiProvider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash-lite", "https://aistudio.google.com/apikey", "国内网络可能无法直接访问"),
        AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-v4-flash", "https://platform.deepseek.com/api_keys", "速度快，适合日常题目解析"),
        AiProvider("moonshot", "Moonshot / Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "https://platform.moonshot.cn/console/api-keys", "支持 Kimi API Key"),
        AiProvider("zhipu", "智谱 AI", "https://open.bigmodel.cn/api/paas/v4", "glm-4.7-flash", "https://open.bigmodel.cn/usercenter/apikeys", "国内可直连，推荐免费 Flash 模型"),
        AiProvider("dashscope", "阿里云通义", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo", "https://bailian.console.aliyun.com/?tab=model#/api-key", "使用百炼平台 API Key"),
        AiProvider("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3", "https://cloud.siliconflow.cn/account/ak", "模型名通常包含厂商前缀"),
        AiProvider("volcengine", "火山引擎 / 豆包", "https://ark.cn-beijing.volces.com/api/v3", "doubao-1-5-pro-32k-250115", "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey", "也可填写控制台 Endpoint ID"),
        AiProvider("github", "GitHub Models", "https://models.github.ai/inference", "openai/gpt-4o-mini", "https://github.com/settings/tokens", "Token 需要 models:read 权限"),
        AiProvider("chatanywhere", "ChatAnywhere 中转", "https://api.chatanywhere.tech/v1", "gpt-4o-mini", "https://api.chatanywhere.tech/v1/oauth/free/render", "国内访问较快的兼容接口"),
    )

    fun find(id: String): AiProvider = providers.find { it.id == id } ?: providers.first()
}

data class AiSettings(
    val mode: AiMode = AiMode.SHARED,
    val providerId: String = AiProviderCatalog.providers.first().id,
    val model: String = "",
    val hasApiKey: Boolean = false,
    val keyHint: String = "",
    val needsApiKeyReentry: Boolean = false,
) {
    val provider: AiProvider get() = AiProviderCatalog.find(providerId)
    val activeModel: String get() = model.ifBlank { provider.defaultModel }
}

data class AiKeyInfo(val hasApiKey: Boolean, val keyHint: String)

internal interface AiCredentialStore {
    fun hasKey(providerId: String): Boolean
    fun hasAnyKey(): Boolean
    fun read(providerId: String): String
    fun write(providerId: String, value: String)
    fun discardLegacyKey()
    fun requiresKeyReentry(): Boolean
    fun clear()
}

class AiSettingsStore internal constructor(
    private val preferences: SharedPreferences,
    private val secretStore: AiCredentialStore,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        AiSecretStore(context),
    )

    fun load(): AiSettings {
        secretStore.discardLegacyKey()
        val providerId = preferences.getString(KEY_PROVIDER, null)
            ?.takeIf { id -> AiProviderCatalog.providers.any { it.id == id } }
            ?: AiProviderCatalog.providers.first().id
        val mode = runCatching { AiMode.valueOf(preferences.getString(KEY_MODE, AiMode.SHARED.name)!!) }.getOrDefault(AiMode.SHARED)
        val keyInfo = if (mode == AiMode.OWN_KEY) keyInfo(providerId) else AiKeyInfo(secretStore.hasKey(providerId), "")
        return AiSettings(
            mode = mode,
            providerId = providerId,
            model = preferences.getString(KEY_MODEL, "").orEmpty(),
            hasApiKey = keyInfo.hasApiKey,
            keyHint = keyInfo.keyHint,
            needsApiKeyReentry = secretStore.requiresKeyReentry(),
        )
    }

    fun save(mode: AiMode, providerId: String, model: String, newApiKey: String) {
        require(AiProviderCatalog.providers.any { it.id == providerId }) { "不支持的 AI 服务商" }
        secretStore.discardLegacyKey()
        if (mode == AiMode.OWN_KEY) {
            if (newApiKey.isNotBlank()) secretStore.write(providerId, newApiKey.trim())
            if (secretStore.read(providerId).isBlank()) error("请填写当前服务商的 API Key")
        }
        val saved = preferences.edit()
            .putString(KEY_MODE, mode.name)
            .putString(KEY_PROVIDER, providerId)
            .putString(KEY_MODEL, model.trim())
            .commit()
        if (!saved) throw IOException("AI 设置保存失败，请重试")
    }

    fun apiKey(providerId: String): String {
        require(AiProviderCatalog.providers.any { it.id == providerId }) { "不支持的 AI 服务商" }
        secretStore.discardLegacyKey()
        return secretStore.read(providerId)
    }

    fun keyInfo(providerId: String): AiKeyInfo {
        val key = apiKey(providerId)
        return AiKeyInfo(key.isNotBlank(), key.takeLast(4))
    }

    fun hasStoredCredentials(): Boolean = secretStore.hasAnyKey()

    fun clear() {
        secretStore.clear()
        if (!preferences.edit().clear().commit()) throw IOException("AI 设置清除失败，请重试")
    }

    private companion object {
        const val PREFS = "ai_settings"
        const val KEY_MODE = "mode"
        const val KEY_PROVIDER = "provider"
        const val KEY_MODEL = "model"
    }
}

internal class AiSecretStore(context: Context) : AiCredentialStore {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun hasKey(providerId: String): Boolean =
        preferences.contains(ciphertextKey(providerId)) && preferences.contains(ivKey(providerId))

    override fun hasAnyKey(): Boolean = preferences.all.keys.any { it.startsWith(KEY_CIPHERTEXT) }

    override fun write(providerId: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(providerId.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val saved = preferences.edit()
            .putString(ivKey(providerId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(ciphertextKey(providerId), Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .remove(KEY_IV).remove(KEY_CIPHERTEXT)
            .remove(KEY_REENTRY)
            .commit()
        if (!saved) throw IOException("API Key 保存失败，请重试")
    }

    override fun read(providerId: String): String {
        if (!hasKey(providerId)) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(preferences.getString(ivKey(providerId), ""), Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(providerId.toByteArray(Charsets.UTF_8))
            val encrypted = Base64.decode(preferences.getString(ciphertextKey(providerId), ""), Base64.NO_WRAP)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrElse {
            if (!preferences.edit().remove(ivKey(providerId)).remove(ciphertextKey(providerId)).commit()) {
                throw IOException("API Key 读取失败，请清除配置后重试")
            }
            ""
        }
    }

    override fun requiresKeyReentry(): Boolean = preferences.getBoolean(KEY_REENTRY, false)

    override fun discardLegacyKey() {
        if (!preferences.contains(KEY_CIPHERTEXT) && !preferences.contains(KEY_IV)) return
        // Legacy preferences may already name a different provider than the key's real issuer.
        // Do not guess that association or ever decrypt/send an unbound legacy credential.
        if (!preferences.edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).putBoolean(KEY_REENTRY, true).commit()) {
            throw IOException("旧 API Key 停用失败，请重试")
        }
    }

    override fun clear() {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
        if (!preferences.edit().clear().commit()) throw IOException("API Key 清除失败，请重试")
    }

    private fun ivKey(providerId: String): String = "${KEY_IV}_$providerId"
    private fun ciphertextKey(providerId: String): String = "${KEY_CIPHERTEXT}_$providerId"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "ai_secrets"
        const val KEY_IV = "api_key_iv"
        const val KEY_CIPHERTEXT = "api_key_ciphertext"
        const val KEY_REENTRY = "legacy_key_requires_reentry"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tiyuengine_ai_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
