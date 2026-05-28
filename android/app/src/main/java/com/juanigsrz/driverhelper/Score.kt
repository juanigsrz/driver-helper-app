package com.juanigsrz.driverhelper

import java.util.Locale
import kotlin.math.roundToInt

/**
 * On-device port of the backend `score.py` + the preferred (OCR-distance) path
 * of `main.py`. Field names on [VerdictOut] stay snake_case so [Notifier] reads
 * them unchanged from when the verdict came over the wire.
 */

data class Leg(val distanceKm: Double, val durationMin: Double)

data class ScoreConfig(
    val costPerKm: Double,
    val platformCommission: Double,
    val minArsPerKm: Double,
    val minArsPerHr: Double,
    val maxDeadheadRatio: Double,
) {
    companion object {
        /** Mirrors the backend CFG defaults in `main.py`. */
        val DEFAULT = ScoreConfig(
            costPerKm = 400.0,
            platformCommission = 0.25,
            minArsPerKm = 600.0,
            minArsPerHr = 8000.0,
            maxDeadheadRatio = 0.40,
        )
    }
}

data class VerdictOut(
    val decision: String,
    val gross_ars: Double,
    val net_ars: Double,
    val profit_ars: Double,
    val total_km: Double,
    val total_min: Double,
    val deadhead_km: Double,
    val deadhead_ratio: Double,
    val ars_per_km: Double,
    val ars_per_hr: Double,
    val reasons: List<String>,
)

private fun Double.fmt0(): String = String.format(Locale.US, "%.0f", this)

fun computeVerdict(
    offerArs: Double,
    deadhead: Leg,
    trip: Leg,
    cfg: ScoreConfig,
): VerdictOut {
    val totalKm = deadhead.distanceKm + trip.distanceKm
    val totalMin = deadhead.durationMin + trip.durationMin
    val deadheadRatio = if (totalKm > 0) deadhead.distanceKm / totalKm else 0.0

    val gross = offerArs
    val net = gross * (1.0 - cfg.platformCommission)
    val cost = totalKm * cfg.costPerKm
    val profit = net - cost

    val arsPerKm = if (totalKm > 0) profit / totalKm else 0.0
    val arsPerHr = if (totalMin > 0) profit / (totalMin / 60.0) else 0.0

    val reasons = mutableListOf<String>()
    if (arsPerKm < cfg.minArsPerKm) {
        reasons.add("ARS/km ${arsPerKm.fmt0()} < min ${cfg.minArsPerKm.fmt0()}")
    }
    if (arsPerHr < cfg.minArsPerHr) {
        reasons.add("ARS/hr ${arsPerHr.fmt0()} < min ${cfg.minArsPerHr.fmt0()}")
    }
    if (deadheadRatio > cfg.maxDeadheadRatio) {
        reasons.add(
            "deadhead ${(deadheadRatio * 100).roundToInt()}% > " +
                "max ${(cfg.maxDeadheadRatio * 100).roundToInt()}%"
        )
    }

    val decision = when {
        reasons.isNotEmpty() -> "SKIP"
        arsPerKm > 1.2 * cfg.minArsPerKm && arsPerHr > 1.2 * cfg.minArsPerHr -> "TAKE"
        else -> "MAYBE"
    }

    return VerdictOut(
        decision = decision,
        gross_ars = gross,
        net_ars = net,
        profit_ars = profit,
        total_km = totalKm,
        total_min = totalMin,
        deadhead_km = deadhead.distanceKm,
        deadhead_ratio = deadheadRatio,
        ars_per_km = arsPerKm,
        ars_per_hr = arsPerHr,
        reasons = reasons,
    )
}

/**
 * Mirrors `main.py:144-154`: first leg is the deadhead to pickup, second is the
 * trip. A single leg means the card only printed the trip itself. Returns null
 * when the offer can't be scored (no usable price, or no distance row parsed).
 */
fun evaluateOffer(price: Double?, legs: List<Leg>, cfg: ScoreConfig): VerdictOut? {
    if (price == null || price <= 0) return null
    if (legs.isEmpty()) return null
    val (deadhead, trip) = if (legs.size == 1) {
        Leg(0.0, 0.0) to legs[0]
    } else {
        legs[0] to legs[1]
    }
    return computeVerdict(price, deadhead, trip, cfg)
}
