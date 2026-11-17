package com.qtcreatormobile.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.qtcreatormobile.aiclient.ApiFormat
import com.qtcreatormobile.aiclient.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra as chaves de API com uma chave AES guardada no Android Keystore (nunca sai do aparelho). */
object KeyCipher {
    private const val ALIAS = "qtcm_ai_keys"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /** Devolve "" se nao conseguir decifrar (ex.: dados restaurados em outro aparelho). */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

/** Guarda os provedores de IA em SharedPreferences, com as chaves cifradas. */
class AiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai-providers", Context.MODE_PRIVATE)

    fun load(): List<ProviderConfig> = try {
        val array = JSONArray(prefs.getString("providers", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ProviderConfig(
                id = o.getString("id"),
                name = o.getString("name"),
                baseUrl = o.getString("baseUrl"),
                format = runCatching { ApiFormat.valueOf(o.getString("format")) }.getOrDefault(ApiFormat.OPENAI_COMPATIBLE),
                model = o.optString("model", ""),
                apiKey = KeyCipher.decrypt(o.optString("key", "")),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(providers: List<ProviderConfig>) {
        val array = JSONArray()
        providers.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("baseUrl", p.baseUrl)
                    .put("format", p.format.name).put("model", p.model)
                    .put("key", runCatching { KeyCipher.encrypt(p.apiKey) }.getOrDefault("")),
            )
        }
        prefs.edit().putString("providers", array.toString()).apply()
    }

    var activeId: String?
        get() = prefs.getString("active", null)
        set(value) { prefs.edit().putString("active", value).apply() }
}
