package org.duru.takip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurumTest {
	@Test fun dahaBuyukVersionCodeYeni() {
		assertTrue(Surum.dahaYeni(5, 4))
	}

	@Test fun ayniVeyaEskiYeniDegil() {
		assertFalse(Surum.dahaYeni(4, 4))
		assertFalse(Surum.dahaYeni(3, 4))
	}
}
