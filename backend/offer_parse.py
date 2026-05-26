"""Best-effort parser for OCR'd Uber / Cabify offer cards (ES-AR).

Lives backend-side so the regex can be iterated without rebuilding the APK.
The Android app always forwards the raw recognized text; this module pulls
price and pickup / dropoff addresses out of it.
"""

from __future__ import annotations

import re

# Money: "$ 4.250" / "$4.250,00" / "ARS 3200,00" / "ARS3200"
PRICE_RE = re.compile(
    r"(?:\$|ARS)\s*(\d{1,3}(?:\.\d{3})+(?:,\d{2})?|\d+(?:,\d{2})?)",
    re.IGNORECASE,
)

# Distance / duration line: "9 min (3,2 km)", "28 min 15,7 km"
DISTANCE_RE = re.compile(
    r"\d+\s*min[^\n]{0,20}\d+[.,]?\d*\s*km",
    re.IGNORECASE,
)

STREET_HINT_RE = re.compile(
    r"\b(?:av|avda|avenida|calle|jr|psje|pasaje|diag|diagonal|ruta|autopista)\.?\b",
    re.IGNORECASE,
)
HAS_NUMBER_RE = re.compile(r"\d")

PICKUP_HINTS: tuple[str, ...] = (
    "recoger en", "recogida", "origen", "recoge en", "punto de partida",
    "pickup",
)
DROPOFF_HINTS: tuple[str, ...] = (
    "entregar en", "destino", "drop off", "dropoff", "dejar en",
)

STOPWORDS = frozenset({
    "aceptar", "rechazar", "tarifa garantizada", "largo viaje",
    "viaje", "trip", "uberx", "uber comfort", "comfort", "premium",
})


def parse_price_ars(text: str) -> float | None:
    m = PRICE_RE.search(text)
    if not m:
        return None
    raw = m.group(1).replace(".", "").replace(",", ".")
    try:
        return float(raw)
    except ValueError:
        return None


def _looks_like_address(s: str) -> bool:
    s = s.strip()
    if len(s) < 6:
        return False
    if s.lower() in STOPWORDS:
        return False
    if DISTANCE_RE.search(s):
        return False
    if PRICE_RE.search(s):
        return False
    return bool(STREET_HINT_RE.search(s) or HAS_NUMBER_RE.search(s))


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
                return tail
            for j in range(i + 1, min(i + 4, len(lines))):
                cand = lines[j].strip()
                if _looks_like_address(cand):
                    return cand
            break
    return None


def parse_addresses(text: str) -> tuple[str | None, str | None]:
    lines = [l.strip() for l in text.splitlines() if l.strip()]

    pickup = _extract_after_hint(lines, PICKUP_HINTS)
    dropoff = _extract_after_hint(lines, DROPOFF_HINTS)

    if pickup is not None and dropoff is not None:
        return pickup, dropoff

    # Fallback: first two address-like lines, in document order.
    addrs: list[str] = []
    for line in lines:
        if _looks_like_address(line):
            addrs.append(line)
        if len(addrs) >= 2:
            break

    if pickup is None and addrs:
        pickup = addrs.pop(0)
    if dropoff is None and addrs:
        dropoff = addrs.pop(0)
    return pickup, dropoff
