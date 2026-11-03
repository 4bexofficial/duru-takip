package org.duru.takip

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.security.MessageDigest

// Uygulama bütünlüğü (kurcalama / yeniden imzalama / debug tespiti).
// Kurcalama görülürse admin paneline bildirim (alarm) gönderir ve uygulamayı durdurur.
// Cihaza/kişiye/veriye dokunmaz — yalnızca uygulamanın kendisi kapanır.
object Butunluk {

	// İmzalama sertifikasının beklenen SHA-256'sı (CI'da BuildConfig'e gömülür).
	private val beklenen: String by lazy { temizle(BuildConfig.SIGN_CERT_SHA256) }

	// Sessiz kontrol: her şey yolundaysa true.
	fun saglam(ctx: Context): Boolean {
		if (BuildConfig.DEBUG) return true // geliştirme derlemesi: kontrol yok
		if ((ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) return false
		if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return false
		if (beklenen.isNotEmpty()) {
			val suan = imzaSha256(ctx) ?: return false
			if (suan != beklenen) return false
		}
		return true
	}

	// Sağlamsa true. Kurcalama varsa önce admin paneline bildirim gönderir,
	// sonra uygulamayı kapatır ve false döner. Cihazda başka hiçbir şeyi etkilemez.
	fun dogrulaVeyaCik(act: Activity): Boolean {
		if (saglam(act)) return true
		val t = Thread {
			try { Depo(act).token?.let { SunucuIstemci.alarm(it, "kurcalama") } } catch (e: Exception) {}
		}
		t.start()
		try { t.join(3000) } catch (e: InterruptedException) {} // bildirim gitsin, sonra kapat
		act.finishAffinity()
		Runtime.getRuntime().halt(0)
		return false
	}

	private fun imzaSha256(ctx: Context): String? {
		return try {
			val pm = ctx.packageManager
			val imzalar = if (Build.VERSION.SDK_INT >= 28) {
				pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
					.signingInfo?.apkContentsSigners
			} else {
				@Suppress("DEPRECATION")
				pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
			}
			if (imzalar.isNullOrEmpty()) return null
			val md = MessageDigest.getInstance("SHA-256")
			temizle(md.digest(imzalar[0].toByteArray()).joinToString("") { "%02x".format(it) })
		} catch (e: Exception) {
			null
		}
	}

	private fun temizle(s: String?): String =
		(s ?: "").lowercase().replace(":", "").replace(" ", "").trim()
}
