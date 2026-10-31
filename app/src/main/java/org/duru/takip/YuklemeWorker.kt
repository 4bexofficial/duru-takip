package org.duru.takip

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// Periyodik: sonradan gelen yeni foto/videoları yakalar.
class YuklemeWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
	override fun doWork(): Result {
		val token = Depo(applicationContext).token ?: return Result.success()
		return try {
			Yukleme.calistir(applicationContext, token)
			Result.success()
		} catch (e: Exception) {
			Result.retry()
		}
	}

	companion object {
		private const val AD = "duru-yukleme-periyodik"

		fun planla(ctx: Context) {
			val istek = PeriodicWorkRequestBuilder<YuklemeWorker>(15, TimeUnit.MINUTES)
				.setConstraints(
					Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
				)
				.build()
			WorkManager.getInstance(ctx)
				.enqueueUniquePeriodicWork(AD, ExistingPeriodicWorkPolicy.KEEP, istek)
		}
	}
}
