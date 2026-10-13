package com.telegram.webclient

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.telegram.webclient.ui.account.AccountViewModel
import java.util.concurrent.Executor

class LockActivity : AppCompatActivity() {

    private lateinit var vm: AccountViewModel
    private var failedAttempts = 0
    private var lockoutTimer: CountDownTimer? = null
    private var biometricPrompt: BiometricPrompt? = null
    private lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        executor = ContextCompat.getMainExecutor(this)
        vm = ViewModelProvider(this)[AccountViewModel::class.java]
        setupBiometric()
        observeLockout()
    }

    private fun setupBiometric() {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_use_pin))
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

    companion object {
        fun createIntent(source: android.content.Context): Intent =
            Intent(source, LockActivity::class.java)
    }
}
