package com.juanigsrz.driverhelper

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService

object ForegroundDetector {
    private const val LOOKBACK_MS = 10_000L

    private val TARGETS = setOf(
        "com.ubercab.driver",
        "com.cabify.driver",
        // TEST fake targets — revert before shipping
        "com.miui.gallery",
        "com.android.chrome",
        "com.google.android.apps.photos",
    )

    /** Returns the matching target package if it has been used within LOOKBACK_MS. */
    fun foregroundTargetPkg(ctx: Context): String? {
        val usm = ctx.getSystemService<UsageStatsManager>() ?: return null
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - LOOKBACK_MS,
            now,
        ) ?: return null

        val recent = stats
            .filter { it.packageName in TARGETS }
            .maxByOrNull { it.lastTimeUsed }
            ?: return null

        return if (now - recent.lastTimeUsed < LOOKBACK_MS) recent.packageName else null
    }
}
