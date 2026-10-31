package org.duru.takip

// Sürüm karşılaştırması: uzak (sunucudaki) versionCode yereldekinden büyükse güncelleme gerekir.
object Surum {
	fun dahaYeni(uzak: Int, yerel: Int): Boolean = uzak > yerel
}
