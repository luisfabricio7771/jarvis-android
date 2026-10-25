package dev.jarvis.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSettings(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
    var accent: String
        get() = prefs.getString("accent", "cyan") ?: "cyan"
        set(value) { check(prefs.edit().putString("accent", value).commit()) }
    var wakeEnabled: Boolean
        get() = prefs.getBoolean("wake", true)
        set(value) { check(prefs.edit().putBoolean("wake", value).commit()) }
    fun hasKey(): Boolean = prefs.contains("api_key_ciphertext")
    private fun secret(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "La clave protegida ya no está disponible. Vuelve a introducirla." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveKey(value: String) {
        require(value.length in 20..200 && value.none { it.isWhitespace() }) { "La clave no tiene un formato válido." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret(true)) }
        val payload = Base64.getEncoder().encodeToString(cipher.iv) + ":" +
            Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
        check(prefs.edit().putString("api_key_ciphertext", payload).commit())
    }
    fun readKey(): String {
        val value = prefs.getString("api_key_ciphertext", null) ?: return ""
        val parts = value.split(':'); check(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret(false), GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
        }
        return cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8)
    }
    fun removeKey() { check(prefs.edit().remove("api_key_ciphertext").commit()) }
    companion object { private const val ALIAS = "jarvis_gemini_v1" }
}
