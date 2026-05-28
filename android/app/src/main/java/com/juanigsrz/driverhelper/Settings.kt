package com.juanigsrz.driverhelper

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    private const val PREFS = "dh_settings"
    private const val KEY_URL = "backend_url"
    private const val KEY_COST = "cost_per_km"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun backendUrl(ctx: Context): String =
        prefs(ctx).getString(KEY_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.BACKEND_URL

    fun setBackendUrl(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_URL, value.trim()).apply()
    }

    fun costPerKm(ctx: Context): Double? {
        val p = prefs(ctx)
        return if (p.contains(KEY_COST)) p.getFloat(KEY_COST, 0f).toDouble() else null
    }

    fun setCostPerKm(ctx: Context, value: Double?) {
        val e = prefs(ctx).edit()
        if (value == null) e.remove(KEY_COST) else e.putFloat(KEY_COST, value.toFloat())
        e.apply()
    }
}
