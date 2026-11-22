package org.duru.takip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Telefon yeniden başlayınca ya da uygulama güncellenince kamera servisini tekrar ayağa kaldırır,
// böylece öğretmen uygulamayı açmasa bile müdürün "kameraya yönelt" isteği çalışır.
// (Yalnız cihaz kayıtlıysa; kamera gerçekten ancak müdür istediğinde ve izin varsa açılır.)
class BootReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent?) {
		when (intent?.action) {
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_MY_PACKAGE_REPLACED -> KameraServisi.baslat(context)
		}
	}
}
