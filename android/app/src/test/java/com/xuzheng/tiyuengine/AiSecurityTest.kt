package com.xuzheng.tiyuengine

import android.content.Context
import android.content.SharedPreferences
import com.xuzheng.tiyuengine.data.AiClient
import com.xuzheng.tiyuengine.data.AiCredentialStore
import com.xuzheng.tiyuengine.data.AiHttpTransport
import com.xuzheng.tiyuengine.data.AiMode
import com.xuzheng.tiyuengine.data.AiSecretStore
import com.xuzheng.tiyuengine.data.AiSettings
import com.xuzheng.tiyuengine.data.AiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiSecurityTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("ai_security_test", Context.MODE_PRIVATE)
    private val credentials = FakeCredentials()

    @Before
    fun resetPreferences() {
        preferences.edit().clear().commit()
        context.getSharedPreferences("ai_secrets", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun keepsKeysBoundToTheirProviderAndRejectsBlankKeyForAnotherProvider() {
        val store = AiSettingsStore(preferences, credentials)
        store.save(AiMode.OWN_KEY, "deepseek", "", "dummy-deepseek-key")
        assertEquals("dummy-deepseek-key", store.apiKey("deepseek"))
        assertEquals("", store.apiKey("chatanywhere"))
        assertFalse(store.keyInfo("chatanywhere").hasApiKey)
        assertThrows(IllegalStateException::class.java) {
            store.save(AiMode.OWN_KEY, "chatanywhere", "", "")
        }
        assertEquals("deepseek", store.load().providerId)
        store.save(AiMode.OWN_KEY, "chatanywhere", "", "dummy-chatanywhere-key")
        assertEquals("dummy-chatanywhere-key", store.apiKey("chatanywhere"))
        assertEquals("dummy-deepseek-key", store.apiKey("deepseek"))
    }

    @Test
    fun switchingProviderCannotSendThePreviouslySavedKey() = runBlocking {
        val store = AiSettingsStore(preferences, credentials)
        store.save(AiMode.OWN_KEY, "deepseek", "", "dummy-deepseek-key")
        val requests = mutableListOf<Request>()
        val client = interceptedClient(store, requests)
        val failure = runCatching { client.test(AiSettings(AiMode.OWN_KEY, "chatanywhere"), "") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(requests.isEmpty())
        store.save(AiMode.OWN_KEY, "chatanywhere", "", "dummy-chatanywhere-key")
        client.test(AiSettings(AiMode.OWN_KEY, "chatanywhere"), "")
        assertEquals("api.chatanywhere.tech", requests.single().url.host)
        assertEquals("Bearer dummy-chatanywhere-key", requests.single().header("Authorization"))
    }

    @Test
    fun sharedModeDoesNotReadOrSendPersonalCredentialsOrSaveHiddenDrafts() = runBlocking {
        credentials.keys["deepseek"] = "dummy-deepseek-key"
        preferences.edit().putString("mode", "SHARED").putString("provider", "deepseek").commit()
        val store = AiSettingsStore(preferences, credentials)
        val settings = store.load()
        val requests = mutableListOf<Request>()
        interceptedClient(store, requests).test(settings, "dummy-hidden-draft")
        store.save(AiMode.SHARED, "deepseek", "", "dummy-hidden-draft")
        assertEquals(0, credentials.reads)
        assertEquals("dummy-deepseek-key", credentials.keys["deepseek"])
        assertNull(requests.single().header("Authorization"))
        assertEquals("ai.488227.xyz", requests.single().url.host)
    }

    @Test
    fun legacyUnboundKeyIsNeverAssignedToTheCurrentlySavedProvider() {
        // This is the state left by the vulnerable A-key -> B-provider -> save-blank flow.
        preferences.edit().putString("mode", "OWN_KEY").putString("provider", "chatanywhere").commit()
        val secretPreferences = context.getSharedPreferences("ai_secrets", Context.MODE_PRIVATE)
        secretPreferences.edit()
            .putString("api_key_ciphertext", "dummy-legacy-ciphertext")
            .putString("api_key_iv", "dummy-legacy-iv")
            .commit()
        val store = AiSettingsStore(preferences, AiSecretStore(context))
        val settings = store.load()
        assertTrue(settings.needsApiKeyReentry)
        assertFalse(settings.hasApiKey)
        assertEquals("", store.apiKey("chatanywhere"))
        assertEquals("", store.apiKey("deepseek"))
        assertFalse(secretPreferences.contains("api_key_ciphertext"))
        assertFalse(secretPreferences.contains("api_key_iv"))
        assertFalse(secretPreferences.contains("api_key_ciphertext_chatanywhere"))
        assertThrows(IllegalStateException::class.java) {
            store.save(AiMode.OWN_KEY, "chatanywhere", "", "")
        }
    }

    @Test
    fun settingsPersistenceFailureIsReported() {
        val failingPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val original = preferences.edit()
                return object : SharedPreferences.Editor by original {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        original.putString(key, value)
                        return this
                    }
                    override fun clear(): SharedPreferences.Editor {
                        original.clear()
                        return this
                    }
                    override fun commit(): Boolean = false
                }
            }
        }
        val store = AiSettingsStore(failingPreferences, credentials)
        assertThrows(IOException::class.java) { store.save(AiMode.SHARED, "deepseek", "", "") }
        assertThrows(IOException::class.java) { store.clear() }
    }

    @Test
    fun keyPersistenceFailureDoesNotChangeTheActiveProvider() {
        preferences.edit().putString("provider", "deepseek").commit()
        val failingCredentials = object : AiCredentialStore by credentials {
            override fun write(providerId: String, value: String) {
                throw IOException("dummy storage failure")
            }
        }
        val store = AiSettingsStore(preferences, failingCredentials)
        assertThrows(IOException::class.java) {
            store.save(AiMode.OWN_KEY, "chatanywhere", "", "dummy-new-key")
        }
        assertEquals("deepseek", preferences.getString("provider", null))
    }

    private fun interceptedClient(store: AiSettingsStore, requests: MutableList<Request>): AiClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            val body = """{"choices":[{"message":{"content":"连接成功"}}]}"""
                .toResponseBody("application/json".toMediaType())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body)
                .build()
        }.build()
        return AiClient(store, AiHttpTransport(http, Dispatchers.Unconfined))
    }

    private class FakeCredentials : AiCredentialStore {
        val keys = mutableMapOf<String, String>()
        var reads = 0
        override fun hasKey(providerId: String): Boolean = keys.containsKey(providerId)
        override fun hasAnyKey(): Boolean = keys.isNotEmpty()
        override fun read(providerId: String): String {
            reads++
            return keys[providerId].orEmpty()
        }
        override fun write(providerId: String, value: String) { keys[providerId] = value }
        override fun discardLegacyKey() = Unit
        override fun requiresKeyReentry(): Boolean = false
        override fun clear() { keys.clear() }
    }
}
