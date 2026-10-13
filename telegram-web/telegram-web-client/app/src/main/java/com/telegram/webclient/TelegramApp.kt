package com.telegram.webclient

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TelegramApp : Application() {

    companion object {
        const val PREFS_NAME = "tg_webclient_secure"
        const val KEY_MASTER = "master_key"
        const val KEY_SESSION = "session_token"
        const val KEY_PROXY_HOST = "proxy_host"
        const val KEY_PROXY_PORT = "proxy_port"
        const val KEY_PROXY_TYPE = "proxy_type"
        const val KEY_LOCK_TIMEOUT = "lock_timeout_minutes"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_DEBUG = "debug_mode"

        const val TELEGRAM_WEB_URL = "https://web.telegram.org"
        const val TELEGRAM_K_URL = "https://k.telegram.org"

        @Volatile
        var instance: TelegramApp? = null
            private set

        fun appContext(): Context = instance!!.applicationContext

        fun securePrefs(): EncryptedSharedPreferences {
            val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                instance,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        }

        fun encryptData(plain: String, key: SecretKey): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val enc = cipher.doFinal(plain.toByteArray())
            return Base64.getEncoder().encodeToString(iv + enc)
        }

        fun decryptData(encrypted: String, key: SecretKey): String {
            val raw = Base64.getDecoder().decode(encrypted)
            val iv = raw.copyOfRange(0, 12)
            val enc = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(enc))
        }

        fun sessionKey(): SecretKey {
            val prefs = securePrefs()
            var encoded = prefs.getString(KEY_MASTER, null)
            if (encoded == null) {
                val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
                encoded = Base64.getEncoder().encodeToString(key.encoded)
                prefs.edit().putString(KEY_MASTER, encoded).apply()
                return SecretKeySpec(Base64.getDecoder().decode(encoded), "AES")
            }
            return SecretKeySpec(Base64.getDecoder().decode(encoded), "AES")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}