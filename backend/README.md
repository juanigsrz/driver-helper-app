# backend

FastAPI service that scores Uber / Cabify trip offers for an Argentine driver.

Inputs (from Android): platform, offered price (ARS), driver lat/lng, pickup + dropoff (lat/lng or text address).
Output: TAKE / MAYBE / SKIP verdict, with profit, ARS/km, ARS/hr, deadhead %, and reasons.

## Setup

```bash
# 1. python deps (uses the repo-root venv)
source ../venv/bin/activate
pip install -r requirements.txt

# 2. env
cp .env.example .env
# edit COST_PER_KM, thresholds, SHARED_SECRET

# 3. OSM data + OSRM preprocess (one time, ~30 min CPU, ~250 MB download)
make osrm-prep

# 4. spin up OSRM + Nominatim
make up
# Nominatim import is automatic on first start and takes ~1-2h on first boot.
# Watch progress with: make logs

# 5. run API (host-side, not in compose)
make run
```

## Test

```bash
make test
```

## Sample call

```bash
curl -s http://127.0.0.1:8000/evaluate \
  -H "Content-Type: application/json" \
  -H "X-Secret: change-me-please" \
  -d '{
    "platform": "uber",
    "price_ars": 13600,
    "driver":  {"lat": -34.6037, "lng": -58.3816},
    "pickup":  {"lat": -34.6087, "lng": -58.3700},
    "dropoff": {"lat": -34.5870, "lng": -58.4100}
  }' | jq
```

If you only have addresses (no lat/lng), use `pickup_addr` / `dropoff_addr` instead and the server will geocode via Nominatim.

## Files

- `main.py`  FastAPI app, `/evaluate` and `/healthz`
- `score.py` pure decision math (TAKE / MAYBE / SKIP)
- `osrm.py`  async OSRM client
- `geocode.py` async Nominatim client with LRU cache
- `cache.py` tiny in-memory LRU
- `test_score.py` unit tests for the math
- `docker-compose.yml` OSRM + Nominatim
- `Makefile` setup / run shortcuts
