package com.telegram.webclient

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPromptInfo
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class LockActivity : AppCompatActivity() {

    private lateinit var vm: AccountViewModel
    private var failedAttempts = 0
    private var lockoutTimer: CountDownTimer? = null
    private var biometricPrompt: BiometricPrompt? = null
    private val executor: Executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        vm = ViewModelProvider(this)[AccountViewModel::class.java]
        setupBiometric()
        observeLockout()
    }

    private fun setupBiometric() {
        val info = BiometricPromptInfo.Builder()
            .setTitle("Buka Telegram Web Client")
            .setSubtitle("Gunakan biometrik atau PIN")
            .setNegativeButtonText("Gunakan PIN")
            .setConfirmationRequired(false)
            .build()

        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                runIntent(RESULT_OK)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(this@LockActivity, errString, Toast.LENGTH_SHORT).show()
                }
            }
        })
        biometricPrompt?.authenticate(info)
    }

    private fun runIntent(code: Int) {
        setResult(code)
        finish()
    }

    private fun observeLockout() {
        vm.lockoutRemaining.observe(this) { remaining ->
            if (remaining > 0) {
                lockoutTimer?.cancel()
                lockoutTimer = object : CountDownTimer(remaining, 1000) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        failedAttempts = 0
                        setupBiometric()
                    }
                }.start()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lockoutTimer?.cancel()
    }
}