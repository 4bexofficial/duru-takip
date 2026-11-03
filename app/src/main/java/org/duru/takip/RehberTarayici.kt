package org.duru.takip

import android.content.Context
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

// Telefon rehberini okur: kişi adı + numaraları. İzin yoksa boş döner.
object RehberTarayici {

	// Sunucunun /api/yukle/rehber beklediği biçim: [{ad, numaralar:[...]}, ...]
	fun tara(ctx: Context): JSONArray {
		val harita = LinkedHashMap<String, MutableSet<String>>() // ad -> numaralar
		val sutunlar = arrayOf(
			ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
			ContactsContract.CommonDataKinds.Phone.NUMBER
		)
		try {
			ctx.contentResolver.query(
				ContactsContract.CommonDataKinds.Phone.CONTENT_URI, sutunlar, null, null,
				ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
			)?.use { c ->
				val iAd = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
				val iNo = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
				while (c.moveToNext()) {
					val ad = (c.getString(iAd) ?: "").trim()
					val no = (c.getString(iNo) ?: "").replace("\\s".toRegex(), "")
					if (ad.isEmpty() && no.isEmpty()) continue
					val anahtar = ad.ifEmpty { no }
					harita.getOrPut(anahtar) { linkedSetOf() }.apply { if (no.isNotEmpty()) add(no) }
				}
			}
		} catch (e: Exception) {
			return JSONArray() // izin yok ya da okunamadı
		}

		val dizi = JSONArray()
		for ((ad, numaralar) in harita) {
			dizi.put(JSONObject().put("ad", ad).put("numaralar", JSONArray(numaralar.toList())))
		}
		return dizi
	}
}
