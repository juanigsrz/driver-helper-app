package com.juanigsrz.driverhelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService

object Notifier {
    private const val CHANNEL_ID = "verdict"
    private const val NOTIF_ID = 99

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Trip verdict",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Live trip-offer scoring result"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }
        )
    }

    fun showVerdict(ctx: Context, v: VerdictOut) {
        ensureChannel(ctx)
        val emoji = when (v.decision) {
            "TAKE"  -> "🟢"
            "MAYBE" -> "🟡"
            else    -> "🔴"
        }
        val title = "$emoji ${v.decision}  $${"%,.0f".format(v.gross_ars)}"
        val why = if (v.reasons.isEmpty()) "" else "\nwhy: " + v.reasons.joinToString("; ")
        val body = """
            trip   ${"%.1f".format(v.total_km)} km / ${"%.0f".format(v.total_min)} min
            dead   ${"%.1f".format(v.deadhead_km)} km (${"%.0f".format(v.deadhead_ratio * 100)}%)
            ARS/km ${"%.0f".format(v.ars_per_km)}   net ${"%.0f".format(v.profit_ars)}
            ARS/hr ${"%.0f".format(v.ars_per_hr)}$why
        """.trimIndent()

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(
                when (v.decision) {
                    "TAKE"  -> Color.parseColor("#16a34a")
                    "MAYBE" -> Color.parseColor("#ca8a04")
                    else    -> Color.parseColor("#dc2626")
                }
            )
            .setAutoCancel(true)
            .build()

        ctx.getSystemService<NotificationManager>()?.notify(NOTIF_ID, notif)
    }
}
