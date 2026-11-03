package org.duru.takip

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

// 1. ekran: "Hoş geldin Duru" karşılaması. İsim sorulmaz; cihaz sessizce kaydolur (ad = cihaz modeli).
class KayitActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_kayit)
		val btn = findViewById<Button>(R.id.kaydet)
		btn.setOnClickListener {
			btn.isEnabled = false
			// Cihazın kendi adı (ör. "Samsung SM-A155F"). Panelde cihazları ayırt etmek için.
			val marka = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
			val cihazAdi = (if (Build.MODEL.startsWith(marka, true)) Build.MODEL else "$marka ${Build.MODEL}").take(60)
			thread {
				val token = try { SunucuIstemci.kayit(cihazAdi) } catch (e: Exception) { null }
				runOnUiThread {
					if (token != null) {
						Depo(this).token = token
						startActivity(Intent(this, OnayActivity::class.java))
						finish()
					} else {
						btn.isEnabled = true
						Toast.makeText(this, "Kayıt başarısız. İnternet bağlantınızı kontrol edin.", Toast.LENGTH_LONG).show()
					}
				}
			}
		}
	}
}
