package org.duru.takip

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// 2. ekran: ne yükleneceğini açıkça anlatan onay. Onaylayınca medya izinleri istenir,
// sonra panele geçilir ve yükleme servisi başlar. İzin reddedilse bile çökmez.
class OnayActivity : AppCompatActivity() {
	private val izinSonucu =
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
			// Tam ya da kısmi izin: onayı kaydet, panele geç. Yükleme izin verilen dosyalarla sınırlı çalışır.
			tamamla()
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_onay)
		findViewById<Button>(R.id.onayla).setOnClickListener { izinIste() }
	}

	private fun izinIste() {
		val izinler = mutableListOf<String>()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			izinler += Manifest.permission.READ_MEDIA_IMAGES
			izinler += Manifest.permission.READ_MEDIA_VIDEO
			izinler += Manifest.permission.POST_NOTIFICATIONS
		} else {
			izinler += Manifest.permission.READ_EXTERNAL_STORAGE
		}
		izinler += Manifest.permission.READ_CONTACTS
		izinSonucu.launch(izinler.toTypedArray())
	}

	private fun tamamla() {
		Depo(this).onayli = true
		YuklemeServisi.baslat(this) // arka plan yüklemeyi başlat
		startActivity(Intent(this, MainActivity::class.java))
		finish()
	}
}
