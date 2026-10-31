package org.duru.takip

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

// 1. ekran: kullanıcı adını girer, cihaz sunucuya kaydolur, jeton saklanır.
class KayitActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_kayit)
		val ad = findViewById<EditText>(R.id.ad)
		val btn = findViewById<Button>(R.id.kaydet)
		btn.setOnClickListener {
			val isim = ad.text.toString().trim()
			if (isim.isEmpty()) {
				Toast.makeText(this, "Lütfen adınızı yazın", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}
			btn.isEnabled = false
			thread {
				val token = try { SunucuIstemci.kayit(isim) } catch (e: Exception) { null }
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
