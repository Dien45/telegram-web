package com.telegram.webclient

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.telegram.webclient.ui.account.AccountViewModel

class UpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val vm = AccountViewModel()
        vm.checkForUpdates()
        return Result.success()
    }
}