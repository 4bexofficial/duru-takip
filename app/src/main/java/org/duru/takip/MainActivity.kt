package org.duru.takip

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Paneli (WhatsApp mesaj sistemi) WebView ile gösterir. Panel web olduğu için
// arayüz/özellik değişiklikleri APK güncellemeden anında yansır.
class MainActivity : AppCompatActivity() {
	private lateinit var web: WebView
	private var panelAcik = false // panel açıldıysa (KAYIT/ONAY'a yönlenmediyse) true

	// Paneldeki <input type="file"> (mesaja foto/video/dosya ekleme) için telefonun dosya seçicisi.
	// WebView bunu kendisi açmaz; WebChromeClient.onShowFileChooser ile biz açarız.
	private var dosyaGeriDonus: ValueCallback<Array<Uri>>? = null
	private val dosyaSecici = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { sonuc ->
		val uris = WebChromeClient.FileChooserParams.parseResult(sonuc.resultCode, sonuc.data)
		dosyaGeriDonus?.onReceiveValue(uris) // iptal edilirse null: input yeniden çalışabilsin diye yine bildir
		dosyaGeriDonus = null
	}

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// İlk açılış yönlendirmesi: kayıt yoksa Kayıt, onay yoksa Onay, ikisi varsa panel.
		when (Depo(this).hedef()) {
			Hedef.KAYIT -> { startActivity(Intent(this, KayitActivity::class.java)); finish(); return }
			Hedef.ONAY -> { startActivity(Intent(this, OnayActivity::class.java)); finish(); return }
			Hedef.PANEL -> { /* panel aşağıda açılır */ }
		}
		panelAcik = true
		// Not: yükleme taraması onResume'da tetiklenir ki uygulamaya her dönüşte
		// (sadece ilk açılışta değil) sonradan çekilen foto/videolar da yakalansın.
		pilMuafiyetiIste() // arka plan yüklemesi kesilmesin

		web = WebView(this)
		setContentView(web)
		web.settings.javaScriptEnabled = true
		web.settings.domStorageEnabled = true
		// Web sitesi gibi değil, uygulama gibi dursun: esneme/parlama yok, yakınlaştırma yok.
		web.overScrollMode = View.OVER_SCROLL_NEVER
		web.isVerticalScrollBarEnabled = false
		web.isHorizontalScrollBarEnabled = false
		web.settings.setSupportZoom(false)
		web.settings.builtInZoomControls = false
		web.settings.displayZoomControls = false
		web.settings.textZoom = 100 // sistem yazı boyutu düzeni bozmasın
		web.setBackgroundColor(ContextCompat.getColor(this, R.color.zemin))
		web.webViewClient = WebViewClient()
		web.webChromeClient = object : WebChromeClient() {
			override fun onShowFileChooser(
				view: WebView,
				geriDonus: ValueCallback<Array<Uri>>,
				params: FileChooserParams
			): Boolean {
				dosyaGeriDonus?.onReceiveValue(null) // yarım kalan önceki seçimi kapat
				dosyaGeriDonus = geriDonus
				val secim = params.createIntent().apply {
					addCategory(Intent.CATEGORY_OPENABLE)
					if (type.isNullOrEmpty() || type == "*/*") {
						type = "*/*"
						putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "*/*"))
					}
				}
				return try {
					dosyaSecici.launch(Intent.createChooser(secim, "Dosya seç"))
					true
				} catch (e: ActivityNotFoundException) {
					dosyaGeriDonus = null
					false
				}
			}
		}
		web.loadUrl(Sabit.PANEL)

		Guncelleyici.kontrolEt(this) // açılışta yeni sürüm var mı diye bak
	}

	// Uygulamaya her dönüşte yeni foto/videoları tara (en çok dakikada bir, boşa tam tarama olmasın).
	override fun onResume() {
		super.onResume()
		if (!panelAcik || Depo(this).token == null) return
		val simdi = System.currentTimeMillis()
		if (simdi - Depo(this).sonTarama < 60_000) return
		Depo(this).sonTarama = simdi
		YuklemeServisi.baslat(this)
	}

	// Pil optimizasyonu muafiyeti: OEM telefonlarda arka plan yükleme worker'ı
	// kısılmasın diye kullanıcıdan bir kez ister (sideload APK; reddedilse de çalışır).
	private fun pilMuafiyetiIste() {
		if (Depo(this).pilSoruldu) return
		val pm = getSystemService(PowerManager::class.java)
		if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) return
		Depo(this).pilSoruldu = true
		try {
			@SuppressLint("BatteryLife")
			val it = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
			startActivity(it)
		} catch (e: Exception) {
			// bu ekranı açamayan cihaz: sessiz geç, uygulama yine çalışır
		}
	}

	@Suppress("OVERRIDE_DEPRECATION")
	override fun onBackPressed() {
		if (web.canGoBack()) web.goBack() else super.onBackPressed()
	}
}
