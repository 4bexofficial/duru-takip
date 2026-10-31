package org.duru.takip

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

// Paneli (WhatsApp mesaj sistemi) WebView ile gösterir. Panel web olduğu için
// arayüz/özellik değişiklikleri APK güncellemeden anında yansır.
class MainActivity : AppCompatActivity() {
	private lateinit var web: WebView

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// İlk açılış yönlendirmesi: kayıt yoksa Kayıt, onay yoksa Onay, ikisi varsa panel.
		when (Depo(this).hedef()) {
			Hedef.KAYIT -> { startActivity(Intent(this, KayitActivity::class.java)); finish(); return }
			Hedef.ONAY -> { startActivity(Intent(this, OnayActivity::class.java)); finish(); return }
			Hedef.PANEL -> { /* panel aşağıda açılır */ }
		}
		YuklemeServisi.baslat(this) // panel açılırken yükleme servisini de ayağa kaldır

		web = WebView(this)
		setContentView(web)
		web.settings.javaScriptEnabled = true
		web.settings.domStorageEnabled = true
		web.webViewClient = WebViewClient()
		web.loadUrl(Sabit.PANEL)

		Guncelleyici.kontrolEt(this) // açılışta yeni sürüm var mı diye bak
	}

	@Suppress("OVERRIDE_DEPRECATION")
	override fun onBackPressed() {
		if (web.canGoBack()) web.goBack() else super.onBackPressed()
	}
}
