package com.juanigsrz.driverhelper

import java.security.MessageDigest

object OfferParser {

    private val PRICE = Regex(
        """(?:\$|ARS)\s*(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{2})?|\d+(?:[.,]\d{2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val THOUSANDS_SEP = Regex("""[.,](?=\d{3}(?:\D|$))""")

    private val SELF_NOTIF = Regex(
        """^\s*(?:[\uD83D-\uDBFF][\uDC00-\uDFFF]\s*)?(?:SKIP|TAKE|MAYBE)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun detectPlatform(pkg: String): String? = when {
        "ubercab" in pkg -> "uber"
        "cabify"  in pkg -> "cabify"
        // TEST fake targets — revert before shipping
        "gallery" in pkg || "chrome" in pkg || "photos" in pkg -> "uber"
        else             -> null
    }

    fun stripSelfNotif(text: String): String =
        text.lineSequence()
            .filterNot { SELF_NOTIF.containsMatchIn(it) }
            .joinToString("\n")

    fun parsePrice(text: String): Double? {
        val m = PRICE.find(text) ?: return null
        val norm = m.groupValues[1]
            .replace(THOUSANDS_SEP, "")
            .replace(",", ".")
        return norm.toDoubleOrNull()
    }

    // "A17 min (7.6 km)" -> deadhead, "Viaje: 16 min (7.6 km)" -> trip.
    // group(1) = minutes, group(2) = km. Ported 1:1 from backend LEG_RE.
    private val LEG = Regex(
        """(\d+)\s*min[^(\n]{0,30}\(?\s*(\d+(?:[.,]\d+)?)\s*km\)?""",
        RegexOption.IGNORE_CASE,
    )

    /** Return up to 2 (km, minutes) legs in document order. */
    fun parseLegs(text: String): List<Leg> =
        LEG.findAll(text)
            .mapNotNull { m ->
                val minutes = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val km = m.groupValues[2].replace(",", ".").toDoubleOrNull()
                    ?: return@mapNotNull null
                Leg(distanceKm = km, durationMin = minutes)
            }
            .take(2)
            .toList()

    /** SHA-1 of normalized lines so dedup ignores cosmetic flicker (surge badges, etc). */
    fun canonicalHash(platform: String, text: String): String {
        val normalized = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(20)
            .joinToString("\n")
            .lowercase()
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$platform|$normalized".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
