package com.juanigsrz.driverhelper

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    private const val PREFS = "dh_settings"
    private const val KEY_URL = "backend_url"
    private const val KEY_COST = "cost_per_km"
    private const val KEY_MIN_KM = "min_ars_per_km"
    private const val KEY_MIN_HR = "min_ars_per_hr"
    private const val KEY_DEADHEAD = "max_deadhead_ratio"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun backendUrl(ctx: Context): String =
        prefs(ctx).getString(KEY_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.BACKEND_URL

    fun setBackendUrl(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_URL, value.trim()).apply()
    }

    private fun getDouble(ctx: Context, key: String): Double? {
        val p = prefs(ctx)
        return if (p.contains(key)) p.getFloat(key, 0f).toDouble() else null
    }

    private fun setDouble(ctx: Context, key: String, value: Double?) {
        val e = prefs(ctx).edit()
        if (value == null) e.remove(key) else e.putFloat(key, value.toFloat())
        e.apply()
    }

    fun costPerKm(ctx: Context): Double? = getDouble(ctx, KEY_COST)
    fun setCostPerKm(ctx: Context, value: Double?) = setDouble(ctx, KEY_COST, value)

    fun minArsPerKm(ctx: Context): Double? = getDouble(ctx, KEY_MIN_KM)
    fun setMinArsPerKm(ctx: Context, value: Double?) = setDouble(ctx, KEY_MIN_KM, value)

    fun minArsPerHr(ctx: Context): Double? = getDouble(ctx, KEY_MIN_HR)
    fun setMinArsPerHr(ctx: Context, value: Double?) = setDouble(ctx, KEY_MIN_HR, value)

    fun maxDeadheadRatio(ctx: Context): Double? = getDouble(ctx, KEY_DEADHEAD)
    fun setMaxDeadheadRatio(ctx: Context, value: Double?) = setDouble(ctx, KEY_DEADHEAD, value)
}
