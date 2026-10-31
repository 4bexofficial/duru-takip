package org.duru.takip

import android.annotation.SuppressLint
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
		web = WebView(this)
		setContentView(web)
		web.settings.javaScriptEnabled = true
		web.settings.domStorageEnabled = true
		web.webViewClient = WebViewClient()
		web.loadUrl(Sabit.PANEL)
	}

	@Suppress("OVERRIDE_DEPRECATION")
	override fun onBackPressed() {
		if (web.canGoBack()) web.goBack() else super.onBackPressed()
	}
}
