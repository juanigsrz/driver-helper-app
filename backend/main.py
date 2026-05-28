from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager
from dataclasses import replace
from typing import Literal

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("dh")

from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

import offer_parse
from geocode import GeocodeClient
from osrm import OSRMClient
from score import Leg, ScoreConfig, Verdict, compute_verdict

load_dotenv()


def _env(name: str, default: str | None = None) -> str:
    v = os.getenv(name, default)
    if v is None:
        raise RuntimeError(f"missing env {name}")
    return v


def _envf(name: str, default: float) -> float:
    return float(os.getenv(name, str(default)))


CFG = ScoreConfig(
    cost_per_km=_envf("COST_PER_KM", 400.0),
    platform_commission=_envf("PLATFORM_COMMISSION", 0.25),
    min_ars_per_km=_envf("MIN_ARS_PER_KM", 600.0),
    min_ars_per_hr=_envf("MIN_ARS_PER_HR", 8000.0),
    max_deadhead_ratio=_envf("MAX_DEADHEAD_RATIO", 0.40),
)
SHARED_SECRET = _env("SHARED_SECRET", "change-me-please")


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.osrm = OSRMClient(_env("OSRM_URL", "http://localhost:5000"))
    app.state.geo = GeocodeClient(_env("NOMINATIM_URL", "http://localhost:7070"))
    try:
        yield
    finally:
        await app.state.osrm.close()
        await app.state.geo.close()


app = FastAPI(title="driver-helper", lifespan=lifespan)


class Point(BaseModel):
    lat: float
    lng: float


class OfferIn(BaseModel):
    platform: Literal["uber", "cabify"]
    price_ars: float | None = Field(default=None, gt=0)
    driver: Point
    pickup_addr: str | None = None
    dropoff_addr: str | None = None
    pickup: Point | None = None
    dropoff: Point | None = None
    raw_text: str | None = None
    cost_per_km: float | None = Field(default=None, ge=0)
    min_ars_per_km: float | None = Field(default=None, ge=0)
    min_ars_per_hr: float | None = Field(default=None, ge=0)
    max_deadhead_ratio: float | None = Field(default=None, ge=0, le=1)


class VerdictOut(BaseModel):
    decision: Literal["TAKE", "MAYBE", "SKIP"]
    gross_ars: float
    net_ars: float
    profit_ars: float
    total_km: float
    total_min: float
    deadhead_km: float
    deadhead_ratio: float
    ars_per_km: float
    ars_per_hr: float
    reasons: list[str]


def _check_auth(secret: str | None) -> None:
    if secret != SHARED_SECRET:
        raise HTTPException(status_code=401, detail="bad secret")


async def _resolve(
    addr: str | None, point: Point | None, geo: GeocodeClient
) -> Point:
    if point is not None:
        return point
    if addr is None:
        raise HTTPException(400, "need addr or point")
    hit = await geo.geocode(addr)
    if hit is None:
        raise HTTPException(422, f"geocode failed: {addr}")
    return Point(lat=hit[0], lng=hit[1])


@app.post("/evaluate", response_model=VerdictOut)
async def evaluate(
    body: OfferIn,
    x_secret: str | None = Header(default=None, alias="X-Secret"),
) -> VerdictOut:
    _check_auth(x_secret)

    geo: GeocodeClient = app.state.geo
    osrm: OSRMClient = app.state.osrm

    if body.raw_text:
        log.info("raw_text:\n%s\n---", body.raw_text)

    # Fill missing fields from raw_text if Android couldn't parse them.
    price = body.price_ars
    if price is None and body.raw_text:
        price = offer_parse.parse_price_ars(body.raw_text)
    if price is None or price <= 0:
        raise HTTPException(422, "missing or unparseable price_ars")

    overrides: dict[str, float] = {}
    if body.cost_per_km is not None:
        overrides["cost_per_km"] = body.cost_per_km
    if body.min_ars_per_km is not None:
        overrides["min_ars_per_km"] = body.min_ars_per_km
    if body.min_ars_per_hr is not None:
        overrides["min_ars_per_hr"] = body.min_ars_per_hr
    if body.max_deadhead_ratio is not None:
        overrides["max_deadhead_ratio"] = body.max_deadhead_ratio
    cfg = replace(CFG, **overrides) if overrides else CFG

    # Preferred path: distance pulled straight from the offer card OCR.
    # Avoids fragile geocoding when Uber prints "(N km)" right in the popup.
    if body.raw_text:
        legs = offer_parse.parse_legs(body.raw_text)
        if legs:
            if len(legs) == 1:
                deadhead_leg = Leg(distance_km=0.0, duration_min=0.0)
                trip_leg = Leg(distance_km=legs[0][0], duration_min=legs[0][1])
            else:
                deadhead_leg = Leg(distance_km=legs[0][0], duration_min=legs[0][1])
                trip_leg = Leg(distance_km=legs[1][0], duration_min=legs[1][1])
            v: Verdict = compute_verdict(price, deadhead_leg, trip_leg, cfg)
            return VerdictOut(**v.__dict__)

    # Fallback: geocode addresses + route via OSRM.
    pickup_addr = body.pickup_addr
    dropoff_addr = body.dropoff_addr
    if (
        body.pickup is None
        and body.dropoff is None
        and (pickup_addr is None or dropoff_addr is None)
        and body.raw_text
    ):
        p, d = offer_parse.parse_addresses(body.raw_text)
        pickup_addr = pickup_addr or p
        dropoff_addr = dropoff_addr or d

    pickup = await _resolve(pickup_addr, body.pickup, geo)
    dropoff = await _resolve(dropoff_addr, body.dropoff, geo)

    deadhead = await osrm.route(
        body.driver.lng, body.driver.lat, pickup.lng, pickup.lat
    )
    trip = await osrm.route(pickup.lng, pickup.lat, dropoff.lng, dropoff.lat)

    v = compute_verdict(price, deadhead, trip, cfg)
    return VerdictOut(**v.__dict__)


@app.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/config")
async def config() -> dict[str, float]:
    return {
        "cost_per_km": CFG.cost_per_km,
        "platform_commission": CFG.platform_commission,
        "min_ars_per_km": CFG.min_ars_per_km,
        "min_ars_per_hr": CFG.min_ars_per_hr,
        "max_deadhead_ratio": CFG.max_deadhead_ratio,
    }
