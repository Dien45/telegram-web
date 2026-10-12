package com.telegram.webclient.ui.account

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.IOException
import java.util.concurrent.TimeUnit

class AccountViewModel : ViewModel() {
    private val prefs by lazy { getApplication<TelegramApp>().securePrefs() }

    private val _locked = mutableLiveDataOf(false)
    val locked: LiveData<Boolean> = _locked

    private val _timeoutExpired = mutableLiveDataOf(false)
    val timeoutExpired: LiveData<Boolean> = _timeoutExpired

    private val _lockoutRemaining = mutableLiveDataOf(0L)
    val lockoutRemaining: LiveData<Long> = _lockoutRemaining

    private var failed = 0
    private var lastFailedTime = 0L
    private val maxAttempts = 5
    private val lockoutDuration = 300_000L

    fun checkAuth(pin: String): Boolean {
        val stored = prefs.getString(TelegramApp.KEY_PIN_HASH, null)
        if (stored == null) {
            prefs.edit().putString(TelegramApp.KEY_PIN_HASH, pin).apply()
            _locked.value = false
            return true
        }
        if (stored == pin) {
            _locked.value = false
            return true
        } else {
            failed++
            lastFailedTime = SystemClock.uptimeMillis()
            if (failed >= maxAttempts) {
                _timeoutExpired.value = true
                _lockoutRemaining.value = lockoutDuration
            }
            return false
        }
    }

    fun lock() {
        _locked.value = true
    }

    fun logoutWipe() {
        prefs.edit().clear().apply()
        _locked.value = false
        _timeoutExpired.value = false
    }

    private fun scheduleUpdateCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val updateWork: PeriodicWorkRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "updateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWork
        )
    }
}