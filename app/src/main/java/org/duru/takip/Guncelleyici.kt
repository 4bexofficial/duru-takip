package org.duru.takip

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlin.concurrent.thread

// Kendi kendini güncelleme: sunucudaki sürümü kontrol eder, yeniyse APK indirip kurulum ekranını açar.
object Guncelleyici {

	fun gerekli(uzakKod: Int, yerel: Int): Boolean = Surum.dahaYeni(uzakKod, yerel)

	fun kontrolEt(ctx: Context) {
		thread {
			try {
				val bilgi = SunucuIstemci.surumBilgisi() ?: return@thread
				if (!gerekli(bilgi.versionCode, BuildConfig.VERSION_CODE)) return@thread

				val apk = File(ctx.cacheDir, "duru-takip-${bilgi.versionCode}.apk")
				if (!SunucuIstemci.indir(bilgi.url, apk)) return@thread

				ctx.startActivity(kurulumIntent(ctx, apk))
			} catch (e: Exception) {
				// sessiz geç; bir sonraki kontrolde tekrar denenir
			}
		}
	}

	private fun kurulumIntent(ctx: Context, apk: File): Intent {
		val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
		return Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}
}
