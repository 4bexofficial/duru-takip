package org.duru.takip

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MedyaOge(
	val id: Long,
	val uri: Uri,
	val ad: String,
	val boyut: Long,
	val tarihMs: Long,
	val mime: String,
	val video: Boolean
) {
	val anahtar: String get() = MedyaTarayici.anahtar(id, boyut, tarihMs, video)
}

object MedyaTarayici {
	// Cihazda sabit dedup anahtarı (aynı dosya için her zaman aynı).
	fun anahtar(id: Long, boyut: Long, tarih: Long, video: Boolean = false): String =
		"${if (video) "v" else "i"}-$id-$boyut-$tarih"

	fun tara(ctx: Context): List<MedyaOge> {
		val liste = ArrayList<MedyaOge>()
		liste += sorgula(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
		liste += sorgula(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
		return liste
	}

	private fun sorgula(ctx: Context, taban: Uri, video: Boolean): List<MedyaOge> {
		val sonuc = ArrayList<MedyaOge>()
		val sutunlar = arrayOf(
			MediaStore.MediaColumns._ID,
			MediaStore.MediaColumns.DISPLAY_NAME,
			MediaStore.MediaColumns.SIZE,
			MediaStore.MediaColumns.DATE_ADDED,
			MediaStore.MediaColumns.MIME_TYPE
		)
		ctx.contentResolver.query(taban, sutunlar, null, null, null)?.use { c ->
			val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
			val iAd = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
			val iBoyut = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
			val iTarih = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
			val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
			while (c.moveToNext()) {
				val id = c.getLong(iId)
				val boyut = c.getLong(iBoyut)
				val tarih = c.getLong(iTarih) * 1000L // DATE_ADDED saniye → ms
				val ad = c.getString(iAd) ?: "medya-$id"
				val mime = c.getString(iMime) ?: if (video) "video/mp4" else "image/jpeg"
				sonuc += MedyaOge(id, ContentUris.withAppendedId(taban, id), ad, boyut, tarih, mime, video)
			}
		}
		return sonuc
	}
}
