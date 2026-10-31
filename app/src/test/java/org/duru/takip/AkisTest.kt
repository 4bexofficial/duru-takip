package org.duru.takip

import org.junit.Assert.assertEquals
import org.junit.Test

class AkisTest {
	@Test fun tokenYoksaKayit() {
		assertEquals(Hedef.KAYIT, akisHedefi(null, false))
		assertEquals(Hedef.KAYIT, akisHedefi("", false))
	}

	@Test fun tokenVarOnayYoksaOnay() {
		assertEquals(Hedef.ONAY, akisHedefi("t", false))
	}

	@Test fun ikisiVarsaPanel() {
		assertEquals(Hedef.PANEL, akisHedefi("t", true))
	}
}
