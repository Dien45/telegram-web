package com.telegram.webclient

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Application-wide encrypted preferences backed by Android Keystore. */
class TelegramApp : Application() {

    companion object {
        private const val PREFS_NAME = "tg_webclient_secure"

        const val KEY_PIN_HASH = "app_pin_hash_v2"
        const val KEY_PIN_SALT = "app_pin_salt_v2"
        const val KEY_PIN_FAILURES = "app_pin_failures"
        const val KEY_PIN_LOCKOUT_UNTIL = "app_pin_lockout_until"
        const val KEY_ACCOUNT_LIST = "accounts_json_v1"
        const val KEY_ACTIVE_ACCOUNT = "active_account_id_v1"

        const val TELEGRAM_WEB_URL = "https://web.telegram.org"
        const val TELEGRAM_K_URL = "https://k.telegram.org"

        @Volatile
        private var instance: TelegramApp? = null

        @Volatile
        private var cachedSecurePrefs: SharedPreferences? = null

        fun appContext(): Context = requireNotNull(instance) {
            "TelegramApp has not been initialized"
        }.applicationContext

        fun securePrefs(): SharedPreferences {
            cachedSecurePrefs?.let { return it }
            return synchronized(this) {
                cachedSecurePrefs ?: run {
                    val context = appContext()
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    ).also { cachedSecurePrefs = it }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
