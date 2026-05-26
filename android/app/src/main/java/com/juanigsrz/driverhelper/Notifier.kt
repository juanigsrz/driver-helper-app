package com.juanigsrz.driverhelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import java.util.Locale

object Notifier {
    private const val CHANNEL_ID = "verdict"
    private const val NOTIF_ID = 99
    private val AR = Locale("es", "AR")

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
        val price = String.format(AR, "%,.0f", v.gross_ars)
        val arsKm = String.format(AR, "%,.0f", v.ars_per_km)
        val title = "$emoji ${v.decision} \$$price · $arsKm/km"
        val why = if (v.reasons.isEmpty()) "" else "\nwhy: " + v.reasons.joinToString("; ")
        val body = """
            trip   ${String.format(AR, "%.1f", v.total_km)} km / ${String.format(AR, "%.0f", v.total_min)} min
            dead   ${String.format(AR, "%.1f", v.deadhead_km)} km (${String.format(AR, "%.0f", v.deadhead_ratio * 100)}%)
            ARS/km ${String.format(AR, "%,.0f", v.ars_per_km)}   net ${String.format(AR, "%,.0f", v.profit_ars)}
            ARS/hr ${String.format(AR, "%,.0f", v.ars_per_hr)}$why
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
