package org.duru.takip

// Uygulama ilk açılışta nereye gitmeli? Saf (test edilebilir) karar fonksiyonu.
enum class Hedef { KAYIT, ONAY, PANEL }

fun akisHedefi(token: String?, onayli: Boolean): Hedef = when {
	token.isNullOrBlank() -> Hedef.KAYIT
	!onayli -> Hedef.ONAY
	else -> Hedef.PANEL
}
