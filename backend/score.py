from dataclasses import dataclass
from typing import Literal

Decision = Literal["TAKE", "MAYBE", "SKIP"]


@dataclass(frozen=True)
class Leg:
    distance_km: float
    duration_min: float


@dataclass(frozen=True)
class ScoreConfig:
    cost_per_km: float
    platform_commission: float
    min_ars_per_km: float
    min_ars_per_hr: float
    max_deadhead_ratio: float


@dataclass(frozen=True)
class Verdict:
    decision: Decision
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


def compute_verdict(
    offer_ars: float,
    deadhead: Leg,
    trip: Leg,
    cfg: ScoreConfig,
) -> Verdict:
    total_km = deadhead.distance_km + trip.distance_km
    total_min = deadhead.duration_min + trip.duration_min
    deadhead_ratio = deadhead.distance_km / total_km if total_km > 0 else 0.0

    gross = offer_ars
    net = gross * (1.0 - cfg.platform_commission)
    cost = total_km * cfg.cost_per_km
    profit = net - cost

    ars_per_km = profit / total_km if total_km > 0 else 0.0
    ars_per_hr = profit / (total_min / 60.0) if total_min > 0 else 0.0

    reasons: list[str] = []
    if ars_per_km < cfg.min_ars_per_km:
        reasons.append(f"ARS/km {ars_per_km:.0f} < min {cfg.min_ars_per_km:.0f}")
    if ars_per_hr < cfg.min_ars_per_hr:
        reasons.append(f"ARS/hr {ars_per_hr:.0f} < min {cfg.min_ars_per_hr:.0f}")
    if deadhead_ratio > cfg.max_deadhead_ratio:
        reasons.append(
            f"deadhead {deadhead_ratio:.0%} > max {cfg.max_deadhead_ratio:.0%}"
        )

    if reasons:
        decision: Decision = "SKIP"
    elif (
        ars_per_km > 1.2 * cfg.min_ars_per_km
        and ars_per_hr > 1.2 * cfg.min_ars_per_hr
    ):
        decision = "TAKE"
    else:
        decision = "MAYBE"

    return Verdict(
        decision=decision,
        gross_ars=gross,
        net_ars=net,
        profit_ars=profit,
        total_km=total_km,
        total_min=total_min,
        deadhead_km=deadhead.distance_km,
        deadhead_ratio=deadhead_ratio,
        ars_per_km=ars_per_km,
        ars_per_hr=ars_per_hr,
        reasons=reasons,
    )
