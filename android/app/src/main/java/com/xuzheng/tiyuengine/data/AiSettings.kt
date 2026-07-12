package com.xuzheng.tiyuengine.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
) {
    val provider: AiProvider get() = AiProviderCatalog.find(providerId)
    val activeModel: String get() = model.ifBlank { provider.defaultModel }
}

class AiSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val secretStore = AiSecretStore(context)

    fun load(): AiSettings {
        val providerId = preferences.getString(KEY_PROVIDER, null) ?: AiProviderCatalog.providers.first().id
        val hasKey = secretStore.hasKey()
        return AiSettings(
            mode = runCatching { AiMode.valueOf(preferences.getString(KEY_MODE, AiMode.SHARED.name)!!) }.getOrDefault(AiMode.SHARED),
            providerId = providerId,
            model = preferences.getString(KEY_MODEL, "").orEmpty(),
            hasApiKey = hasKey,
            keyHint = if (hasKey) secretStore.read().takeLast(4) else "",
        )
    }

    fun save(mode: AiMode, providerId: String, model: String, newApiKey: String) {
        require(AiProviderCatalog.providers.any { it.id == providerId }) { "不支持的 AI 服务商" }
        if (newApiKey.isNotBlank()) secretStore.write(newApiKey.trim())
        if (mode == AiMode.OWN_KEY && !secretStore.hasKey()) error("请填写 API Key")
        preferences.edit()
            .putString(KEY_MODE, mode.name)
            .putString(KEY_PROVIDER, providerId)
            .putString(KEY_MODEL, model.trim())
            .apply()
    }

    fun apiKey(): String = secretStore.read()

    fun clear() {
        preferences.edit().clear().commit()
        secretStore.clear()
    }

    private companion object {
        const val PREFS = "ai_settings"
        const val KEY_MODE = "mode"
        const val KEY_PROVIDER = "provider"
        const val KEY_MODEL = "model"
    }
}

private class AiSecretStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasKey(): Boolean = preferences.contains(KEY_CIPHERTEXT) && preferences.contains(KEY_IV)

    fun write(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .commit()
    }

    fun read(): String {
        if (!hasKey()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(preferences.getString(KEY_IV, ""), Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            val encrypted = Base64.decode(preferences.getString(KEY_CIPHERTEXT, ""), Base64.NO_WRAP)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrElse {
            clear()
            ""
        }
    }

    fun clear() {
        preferences.edit().clear().commit()
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

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
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tiyuengine_ai_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
