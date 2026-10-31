package org.duru.takip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedyaTest {
	@Test fun anahtarKararli() {
		assertEquals(
			MedyaTarayici.anahtar(12, 3456, 1700000000000),
			MedyaTarayici.anahtar(12, 3456, 1700000000000)
		)
	}

	@Test fun farkliOgeFarkliAnahtar() {
		assertTrue(MedyaTarayici.anahtar(12, 3456, 1700000000000) != MedyaTarayici.anahtar(13, 3456, 1700000000000))
	}

	@Test fun buyukDosyaAtlanir() {
		assertTrue(Yukleme.atlanmali(301L * 1024 * 1024))
		assertFalse(Yukleme.atlanmali(10L * 1024 * 1024))
	}
}
