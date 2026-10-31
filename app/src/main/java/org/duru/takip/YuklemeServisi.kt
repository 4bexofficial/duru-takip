package org.duru.takip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

// Görünür bildirimle ilk toplu yüklemeyi yapar, sonra periyodik worker'ı planlar.
class YuklemeServisi : Service() {

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		kanalKur()
		startForeground(BILDIRIM_ID, bildirim("Fotoğraf ve videolar hazırlanıyor…"))

		val token = Depo(this).token
		if (token == null) { stopSelf(); return START_NOT_STICKY }

		thread {
			try {
				Yukleme.calistir(this, token) { yapilan, toplam ->
					guncelle(if (toplam == 0) "Her şey güncel ✓" else "Yükleniyor: $yapilan / $toplam")
				}
			} catch (e: Exception) {
				// sessiz geç; worker tekrar deneyecek
			}
			YuklemeWorker.planla(this)
			stopSelf()
		}
		return START_STICKY
	}

	private fun kanalKur() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val k = NotificationChannel(KANAL, "Yükleme", NotificationManager.IMPORTANCE_LOW)
			getSystemService(NotificationManager::class.java).createNotificationChannel(k)
		}
	}

	private fun bildirim(metin: String): Notification =
		NotificationCompat.Builder(this, KANAL)
			.setContentTitle("Duru Takip")
			.setContentText(metin)
			.setSmallIcon(R.drawable.ic_launcher)
			.setOngoing(true)
			.build()

	private fun guncelle(metin: String) {
		getSystemService(NotificationManager::class.java).notify(BILDIRIM_ID, bildirim(metin))
	}

	companion object {
		const val KANAL = "duru_yukleme"
		const val BILDIRIM_ID = 1

		fun baslat(ctx: Context) {
			if (Depo(ctx).token == null) return
			ContextCompat.startForegroundService(ctx, Intent(ctx, YuklemeServisi::class.java))
		}
	}
}
