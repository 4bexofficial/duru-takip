package org.duru.takip

// Sunucu adresleri tek yerde. Pilotta sabit; ileride ayarlanabilir yapılabilir.
object Sabit {
	const val SUNUCU = "https://aliozdershane.duckdns.org"
	const val PANEL = "$SUNUCU/"
	const val VERSION_JSON = "$SUNUCU/app/version.json"
	const val APK = "$SUNUCU/app/duru-takip.apk"
	const val KAYIT = "$SUNUCU/api/cihaz/kayit"
	const val YUKLE_KONTROL = "$SUNUCU/api/yukle/kontrol"
	const val YUKLE_MEDYA = "$SUNUCU/api/yukle/medya"
	const val YUKLE_REHBER = "$SUNUCU/api/yukle/rehber"
	const val ALARM = "$SUNUCU/api/cihaz/alarm"
}
