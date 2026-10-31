package org.duru.takip

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class SurumBilgi(val versionCode: Int, val url: String)

// Sunucu ile tüm iletişim tek yerde. Cihaz jetonu (X-Cihaz) her istekte gönderilir.
object SunucuIstemci {
	private val http = OkHttpClient.Builder()
		.connectTimeout(30, TimeUnit.SECONDS)
		.readTimeout(300, TimeUnit.SECONDS)
		.writeTimeout(300, TimeUnit.SECONDS)
		.build()
	private val JSON = "application/json; charset=utf-8".toMediaType()

	// Cihazı kaydeder, jeton döner (hata olursa null).
	fun kayit(ad: String): String? {
		val govde = JSONObject().put("ad", ad).toString().toRequestBody(JSON)
		val req = Request.Builder().url(Sabit.KAYIT).post(govde).build()
		http.newCall(req).execute().use { r ->
			if (!r.isSuccessful) return null
			val metin = r.body?.string() ?: return null
			return JSONObject(metin).optString("token").ifBlank { null }
		}
	}

	// Verilen anahtarlardan sunucuda ZATEN olanları döner (tekrar yüklememek için).
	fun kontrol(token: String, anahtarlar: List<String>): Set<String> {
		val govde = JSONObject().put("keys", JSONArray(anahtarlar)).toString().toRequestBody(JSON)
		val req = Request.Builder().url(Sabit.YUKLE_KONTROL).header("X-Cihaz", token).post(govde).build()
		http.newCall(req).execute().use { r ->
			if (!r.isSuccessful) return emptySet()
			val arr = JSONObject(r.body?.string() ?: "{}").optJSONArray("var") ?: return emptySet()
			return (0 until arr.length()).map { arr.getString(it) }.toSet()
		}
	}

	// Tek bir medya dosyasını yükler. Başarılıysa true.
	fun yukle(token: String, dosya: File, ad: String, mime: String, anahtar: String, tarihMs: Long): Boolean {
		val govde = MultipartBody.Builder().setType(MultipartBody.FORM)
			.addFormDataPart("anahtar", anahtar)
			.addFormDataPart("tarih", tarihMs.toString())
			.addFormDataPart("dosya", ad, dosya.asRequestBody(mime.toMediaType()))
			.build()
		val req = Request.Builder().url(Sabit.YUKLE_MEDYA).header("X-Cihaz", token).post(govde).build()
		http.newCall(req).execute().use { r -> return r.isSuccessful }
	}

	// Sunucudaki en son sürüm bilgisi (kendi kendini güncelleme için).
	fun surumBilgisi(): SurumBilgi? {
		val req = Request.Builder().url(Sabit.VERSION_JSON).build()
		http.newCall(req).execute().use { r ->
			if (!r.isSuccessful) return null
			val o = JSONObject(r.body?.string() ?: return null)
			val kod = o.optInt("versionCode", -1)
			if (kod < 0) return null
			var url = o.optString("url", "/app/duru-takip.apk")
			if (url.startsWith("/")) url = Sabit.SUNUCU + url
			return SurumBilgi(kod, url)
		}
	}

	// APK'yı verilen dosyaya indirir. Başarılıysa true.
	fun indir(url: String, hedef: File): Boolean {
		val req = Request.Builder().url(url).build()
		http.newCall(req).execute().use { r ->
			if (!r.isSuccessful) return false
			val govde = r.body ?: return false
			hedef.outputStream().use { cik -> govde.byteStream().copyTo(cik) }
			return true
		}
	}
}
