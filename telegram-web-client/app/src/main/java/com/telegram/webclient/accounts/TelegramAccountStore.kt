package com.telegram.webclient.accounts

import androidx.webkit.Profile
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.telegram.webclient.TelegramApp
import java.util.UUID

/** Only non-sensitive labels/IDs are stored here; SharedPreferences encrypts them at rest. */
data class TelegramAccount(
    val id: String,
    val displayName: String,
    val profileName: String
)

class TelegramAccountStore {
    private val prefs get() = TelegramApp.securePrefs()
    private val gson = Gson()
    private val accountListType = object : TypeToken<List<TelegramAccount>>() {}.type

    fun getAccounts(): List<TelegramAccount> {
        val json = prefs.getString(TelegramApp.KEY_ACCOUNT_LIST, null) ?: return emptyList()
        return try {
            gson.fromJson<List<TelegramAccount>>(json, accountListType).orEmpty()
                .filter { it.id.isNotBlank() && it.displayName.isNotBlank() && it.profileName.isNotBlank() }
        } catch (e: JsonSyntaxException) {
            // Do not silently replace a damaged list: that could orphan saved WebView profiles.
            throw IllegalStateException("Encrypted account list is unreadable", e)
        }
    }

    fun getActiveAccount(): TelegramAccount? {
        val accounts = getAccounts()
        val activeId = prefs.getString(TelegramApp.KEY_ACTIVE_ACCOUNT, null)
        return accounts.firstOrNull { it.id == activeId } ?: accounts.firstOrNull()
    }

    fun ensureInitialAccount(): TelegramAccount {
        getActiveAccount()?.let { return it }
        return createAccount("Akun 1", supportsMultiProfile = false)
    }

    /**
     * The first account uses WebView's existing default profile. Additional accounts require
     * AndroidX WebKit's runtime MULTI_PROFILE feature; we never silently share cookies.
     */
    fun createAccount(displayName: String, supportsMultiProfile: Boolean): TelegramAccount {
        val label = displayName.trim().take(MAX_LABEL_LENGTH)
        require(label.isNotEmpty()) { "Account name cannot be empty" }

        val accounts = getAccounts()
        if (accounts.isNotEmpty() && !supportsMultiProfile) {
            throw MultiProfileUnsupportedException()
        }

        val id = UUID.randomUUID().toString()
        val profileName = if (accounts.isEmpty()) {
            Profile.DEFAULT_PROFILE_NAME
        } else {
            "tg_${id.replace("-", "")}"
        }
        val account = TelegramAccount(id, label, profileName)
        save(accounts + account, account.id)
        return account
    }

    /**
     * Renaming changes only the label. The id and profileName stay untouched, so the account's
     * WebView profile, cookies and login session are unaffected.
     */
    fun renameAccount(accountId: String, displayName: String): TelegramAccount {
        val label = displayName.trim().take(MAX_LABEL_LENGTH)
        require(label.isNotEmpty()) { "Account name cannot be empty" }

        val accounts = getAccounts()
        require(accounts.any { it.id == accountId }) { "Unknown account" }
        val renamed = accounts.map { if (it.id == accountId) it.copy(displayName = label) else it }
        save(renamed, activeId = prefs.getString(TelegramApp.KEY_ACTIVE_ACCOUNT, null))
        return renamed.first { it.id == accountId }
    }

    fun setActiveAccount(accountId: String) {
        require(getAccounts().any { it.id == accountId }) { "Unknown account" }
        check(prefs.edit().putString(TelegramApp.KEY_ACTIVE_ACCOUNT, accountId).commit()) {
            "Could not save the active account"
        }
    }

    fun removeAccount(accountId: String): List<TelegramAccount> {
        val remaining = getAccounts().filterNot { it.id == accountId }
        require(remaining.size != getAccounts().size) { "Unknown account" }
        val currentActive = prefs.getString(TelegramApp.KEY_ACTIVE_ACCOUNT, null)
        val nextActive = if (currentActive == accountId) remaining.firstOrNull()?.id else currentActive
        save(remaining, nextActive)
        return remaining
    }

    private fun save(accounts: List<TelegramAccount>, activeId: String?) {
        val editor = prefs.edit()
            .putString(TelegramApp.KEY_ACCOUNT_LIST, gson.toJson(accounts))
        if (activeId == null) {
            editor.remove(TelegramApp.KEY_ACTIVE_ACCOUNT)
        } else {
            editor.putString(TelegramApp.KEY_ACTIVE_ACCOUNT, activeId)
        }
        check(editor.commit()) { "Could not save encrypted account data" }
    }

    companion object {
        const val MAX_LABEL_LENGTH = 32
    }
}

class MultiProfileUnsupportedException : IllegalStateException(
    "This Android System WebView does not support isolated profiles"
)
