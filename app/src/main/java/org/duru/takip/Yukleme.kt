package org.duru.takip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File

// Tarama + yükleme işinin ortak mantığı (hem servis hem periyodik worker kullanır).
object Yukleme {
	const val MAKS_BOYUT = 300L * 1024 * 1024 // sunucu sınırı 300MB

	fun atlanmali(boyut: Long): Boolean = boyut > MAKS_BOYUT || boyut <= 0L

	// Galeriyi tarar, sunucuda olmayanları yükler. ilerleme(yapilan, toplam) bildirimi günceller.
	fun calistir(ctx: Context, token: String, ilerleme: (Int, Int) -> Unit = { _, _ -> }) {
		if (!Butunluk.saglam(ctx)) return // kurcalanmış kopya: arka planda da çalışmasın

		rehberiYukle(ctx, token) // önce rehberi gönder (izin varsa)

		val hepsi = MedyaTarayici.tara(ctx).filterNot { atlanmali(it.boyut) }
		if (hepsi.isEmpty()) { ilerleme(0, 0); return }

		// Zaten yüklenmiş olanları sunucuya sorup ele (parçalar hâlinde).
		val mevcut = HashSet<String>()
		hepsi.map { it.anahtar }.chunked(200).forEach { parca ->
			mevcut += SunucuIstemci.kontrol(token, parca)
		}
		val yuklenecek = hepsi.filterNot { mevcut.contains(it.anahtar) }

		var yapilan = 0
		for (oge in yuklenecek) {
			val gecici = kopyala(ctx, oge) ?: continue
			try {
				SunucuIstemci.yukle(token, gecici, oge.ad, oge.mime, oge.anahtar, oge.tarihMs)
			} catch (e: Exception) {
				// bağlantı hatası: bu dosyayı atla, worker tekrar deneyecek; dedup kopya oluşturmaz
			} finally {
				gecici.delete()
			}
			yapilan++
			ilerleme(yapilan, yuklenecek.size)
		}
	}

	// Rehber izni varsa kişileri tarayıp sunucuya gönderir. İzin yoksa sessizce atlar.
	private fun rehberiYukle(ctx: Context, token: String) {
		val izinVar = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
		if (!izinVar) return
		try {
			val kisiler = RehberTarayici.tara(ctx)
			if (kisiler.length() > 0) SunucuIstemci.rehberYukle(token, kisiler)
		} catch (e: Exception) {
			// bağlantı/okuma hatası: sonraki turda tekrar denenir
		}
	}

	private fun kopyala(ctx: Context, oge: MedyaOge): File? {
		return try {
			val gecici = File.createTempFile("yukle", null, ctx.cacheDir)
			val girdi = ctx.contentResolver.openInputStream(oge.uri) ?: return null
			girdi.use { g -> gecici.outputStream().use { g.copyTo(it) } }
			gecici
		} catch (e: Exception) {
			null
		}
	}
}
