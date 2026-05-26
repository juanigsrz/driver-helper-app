# driver-helper-app

Helps an Uber / Cabify driver decide in seconds whether a live trip offer is worth taking.

Pipeline:

```
Android (MediaProjection + ML Kit OCR)
  → reads offer card from driver app (Uber Driver / Cabify Driver)
  → extracts: price, pickup, dropoff
  → POST /evaluate to backend
Backend (FastAPI, on home PC behind Cloudflare Tunnel)
  → geocodes addresses (Nominatim)
  → routes deadhead + trip (OSRM, Argentina extract)
  → scores: profit, ARS/km, ARS/hr, deadhead %
  → returns TAKE / MAYBE / SKIP + reasons
Android
  → renders heads-up local notification (color verdict, vibrate)
```

No public Driver API exists for live trip offers, so capture is done locally on the
driver's phone via Android's `MediaProjection` API (read-only, no automation).

## Repo layout

- `backend/` — FastAPI service, OSRM + Nominatim via docker-compose. See `backend/README.md`.
- `android/` — Kotlin app (to be added). MediaProjection capture + ML Kit OCR + heads-up notif.

## Status

- [x] Backend skeleton + decision math (unit-tested)
- [x] docker-compose for OSRM + Nominatim
- [ ] OSM extract download + OSRM preprocess (`make osrm-prep`)
- [ ] Curl test `/evaluate`
- [ ] Cloudflare Tunnel
- [ ] Android app
