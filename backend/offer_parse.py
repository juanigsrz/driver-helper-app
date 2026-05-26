"""Best-effort parser for OCR'd Uber / Cabify offer cards (ES-AR).

Lives backend-side so the regex can be iterated without rebuilding the APK.
The Android app always forwards the raw recognized text; this module pulls
price and pickup / dropoff addresses out of it.
"""

from __future__ import annotations

import re

# Money: "$ 4.250" / "$4.250,00" / "ARS 3200,00" / "ARS3200" / "ARS2,340"
# (some OCR variants put comma as thousand separator instead of dot)
PRICE_RE = re.compile(
    r"(?:\$|ARS)\s*(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{2})?|\d+(?:[.,]\d{2})?)",
    re.IGNORECASE,
)

# Distance / duration line: "9 min (3,2 km)", "28 min 15,7 km"
DISTANCE_RE = re.compile(
    r"\d+\s*min[^\n]{0,20}\d+[.,]?\d*\s*km",
    re.IGNORECASE,
)
LEG_RE = re.compile(
    r"(\d+)\s*min[^(\n]{0,30}\(?\s*(\d+(?:[.,]\d+)?)\s*km\)?",
    re.IGNORECASE,
)

STREET_HINT_RE = re.compile(
    r"\b(?:av|avda|avenida|calle|jr|psje|pasaje|diag|diagonal|ruta|autopista)\.?\b",
    re.IGNORECASE,
)
HAS_NUMBER_RE = re.compile(r"\d")
CLOCK_RE = re.compile(r"\b\d{1,2}:\d{2}\b")
DATE_RE = re.compile(
    r"\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec|"
    r"ene|abr|ago|dic)\b\.?\s+\d",
    re.IGNORECASE,
)
DIGIT_LETTER_RE = re.compile(r"(\d)([A-Za-zÁÉÍÓÚÑáéíóúñ])")

PICKUP_HINTS: tuple[str, ...] = (
    "recoger en", "recogida", "origen", "recoge en", "punto de partida",
    "pickup",
)
DROPOFF_HINTS: tuple[str, ...] = (
    "entregar en", "destino", "drop off", "dropoff", "dejar en",
)

STOPWORDS = frozenset({
    "aceptar", "rechazar", "tarifa", "garantizada", "largo",
    "viaje", "trip", "uberx", "uber", "comfort", "premium",
    "today", "yesterday", "hoy", "ayer",
    "dni", "verificado", "verified",
})

# A line with two capitalized tokens separated by space or comma is
# probably "<Street>, <Zone>" or "<First> <Last>" — only treat as address
# when accompanied by other address cues.
CAP_PAIR_RE = re.compile(r"\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\b[,\s]+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+")
SURGE_RE = re.compile(r"^\s*[+\-]\s*\d")


def parse_legs(text: str) -> list[tuple[float, float]]:
    """Return up to 2 (km, minutes) tuples in document order.

    Typical Uber AR offer card shows two lines:
        "A17 min (7.6 km)"      -> deadhead to pickup
        "Viaje: 16 min (7.6 km)"-> trip itself
    """
    out: list[tuple[float, float]] = []
    for m in LEG_RE.finditer(text):
        try:
            minutes = float(m.group(1))
            km = float(m.group(2).replace(",", "."))
        except ValueError:
            continue
        out.append((km, minutes))
        if len(out) >= 2:
            break
    return out


def parse_price_ars(text: str) -> float | None:
    m = PRICE_RE.search(text)
    if not m:
        return None
    raw = m.group(1)
    # Treat any separator followed by 3 digits as thousands; trailing
    # ",dd" or ".dd" as decimals.
    norm = re.sub(r"[.,](?=\d{3}(?:\D|$))", "", raw).replace(",", ".")
    try:
        return float(norm)
    except ValueError:
        return None


def _looks_like_address(s: str) -> bool:
    s = s.strip()
    if len(s) < 6:
        return False
    if SURGE_RE.match(s):
        return False
    tokens = set(s.lower().split())
    if tokens & STOPWORDS:
        return False
    if DISTANCE_RE.search(s):
        return False
    if PRICE_RE.search(s):
        return False
    if CLOCK_RE.search(s):
        return False
    if DATE_RE.search(s):
        return False
    return bool(
        STREET_HINT_RE.search(s)
        or HAS_NUMBER_RE.search(s)
        or CAP_PAIR_RE.search(s)
    )


def _clean_for_geocode(s: str) -> str:
    return DIGIT_LETTER_RE.sub(r"\1 \2", s).strip()


def _extract_after_hint(
    lines: list[str], hints: tuple[str, ...]
) -> str | None:
    for i, raw in enumerate(lines):
        lower = raw.lower()
        for h in hints:
            idx = lower.find(h)
            if idx == -1:
                continue
            tail = raw[idx + len(h):].strip(":- \t")
            if tail and _looks_like_address(tail):
                return _clean_for_geocode(tail)
            for j in range(i + 1, min(i + 4, len(lines))):
                cand = lines[j].strip()
                if _looks_like_address(cand):
                    return _clean_for_geocode(cand)
            break
    return None


def _score_candidate(s: str) -> int:
    score = 0
    if STREET_HINT_RE.search(s):
        score += 3
    if HAS_NUMBER_RE.search(s):
        score += 2
    if "," in s:
        score += 1
    if len(s) >= 20:
        score += 2
    return score


def parse_addresses(text: str) -> tuple[str | None, str | None]:
    lines = [l.strip() for l in text.splitlines() if l.strip()]

    pickup = _extract_after_hint(lines, PICKUP_HINTS)
    dropoff = _extract_after_hint(lines, DROPOFF_HINTS)

    if pickup is not None and dropoff is not None:
        return pickup, dropoff

    # Fallback: score every address-like line, pick the two best, then
    # restore document order so pickup/dropoff stay in original sequence.
    indexed = [(i, l) for i, l in enumerate(lines) if _looks_like_address(l)]
    indexed.sort(key=lambda x: (-_score_candidate(x[1]), x[0]))
    top = sorted(indexed[:2], key=lambda x: x[0])
    addrs = [_clean_for_geocode(l) for _, l in top]

    if pickup is None and addrs:
        pickup = addrs.pop(0)
    if dropoff is None and addrs:
        dropoff = addrs.pop(0)
    return pickup, dropoff
