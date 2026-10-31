package org.duru.takip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuncelleyiciTest {
	private val yerel = 7

	@Test fun ayniKodGuncellemeGerektirmez() {
		assertFalse(Guncelleyici.gerekli(yerel, yerel))
	}

	@Test fun eskiKodGuncellemeGerektirmez() {
		assertFalse(Guncelleyici.gerekli(yerel - 1, yerel))
	}

	@Test fun buyukKodGuncellemeGerektirir() {
		assertTrue(Guncelleyici.gerekli(yerel + 1, yerel))
	}
}
