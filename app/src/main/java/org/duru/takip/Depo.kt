package org.duru.takip

import android.content.Context

// Cihaz jetonu ve onay durumunu saklar (SharedPreferences).
class Depo(ctx: Context) {
	private val p = ctx.getSharedPreferences("duru", Context.MODE_PRIVATE)

	var token: String?
		get() = p.getString("token", null)
		set(v) { p.edit().putString("token", v).apply() }

	var onayli: Boolean
		get() = p.getBoolean("onayli", false)
		set(v) { p.edit().putBoolean("onayli", v).apply() }

	fun hedef(): Hedef = akisHedefi(token, onayli)
}
