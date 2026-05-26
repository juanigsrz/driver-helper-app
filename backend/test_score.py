import pytest

from score import Leg, ScoreConfig, compute_verdict

CFG = ScoreConfig(
    cost_per_km=400.0,
    platform_commission=0.25,
    min_ars_per_km=600.0,
    min_ars_per_hr=8000.0,
    max_deadhead_ratio=0.40,
)


def test_take_high_margin():
    v = compute_verdict(
        offer_ars=30_000,
        deadhead=Leg(distance_km=1.0, duration_min=3.0),
        trip=Leg(distance_km=10.0, duration_min=20.0),
        cfg=CFG,
    )
    assert v.decision == "TAKE"
    assert v.reasons == []


def test_skip_low_per_km():
    v = compute_verdict(
        offer_ars=4_000,
        deadhead=Leg(distance_km=2.0, duration_min=4.0),
        trip=Leg(distance_km=10.0, duration_min=20.0),
        cfg=CFG,
    )
    assert v.decision == "SKIP"
    assert any("ARS/km" in r for r in v.reasons)


def test_skip_huge_deadhead():
    v = compute_verdict(
        offer_ars=30_000,
        deadhead=Leg(distance_km=8.0, duration_min=15.0),
        trip=Leg(distance_km=10.0, duration_min=20.0),
        cfg=CFG,
    )
    assert v.decision == "SKIP"
    assert any("deadhead" in r for r in v.reasons)


def test_maybe_middling_speed():
    # avg speed 15 km/h (BA traffic), ars/km ~620, ars/hr ~9300
    # both above min, neither above 1.2*min
    v = compute_verdict(
        offer_ars=13_600,
        deadhead=Leg(distance_km=1.0, duration_min=4.0),
        trip=Leg(distance_km=9.0, duration_min=36.0),
        cfg=CFG,
    )
    assert v.decision == "MAYBE"
    assert v.ars_per_km == pytest.approx(620.0, abs=1.0)


def test_math_sanity():
    v = compute_verdict(
        offer_ars=10_000,
        deadhead=Leg(distance_km=2.0, duration_min=5.0),
        trip=Leg(distance_km=8.0, duration_min=15.0),
        cfg=CFG,
    )
    assert v.gross_ars == 10_000
    assert v.net_ars == pytest.approx(7_500)
    assert v.total_km == 10.0
    assert v.total_min == 20.0
    assert v.profit_ars == pytest.approx(7_500 - 10.0 * 400)
    assert v.deadhead_ratio == pytest.approx(0.2)
