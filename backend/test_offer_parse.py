from offer_parse import parse_addresses, parse_price_ars


def test_price_uber_dotted_no_decimals():
    assert parse_price_ars("Tarifa\n$ 4.250\nblah") == 4250.0


def test_price_cabify_ars_with_decimals():
    assert parse_price_ars("ARS 3.200,00") == 3200.0


def test_price_inline_no_separator():
    assert parse_price_ars("$13600") == 13600.0


def test_price_none_when_absent():
    assert parse_price_ars("no money here") is None


UBER_FIXTURE = """Tarifa garantizada
$ 4.250
9 min (3,2 km) en auto
Av. Corrientes 1234
28 min (15,7 km) de viaje
Av. del Libertador 5678
Aceptar"""


def test_uber_addresses_via_fallback_order():
    p, d = parse_addresses(UBER_FIXTURE)
    assert p is not None and "Corrientes" in p
    assert d is not None and "Libertador" in d


CABIFY_HINT_FIXTURE = """ARS 3.200
Origen
Av. Cabildo 2233
Destino
Av. Santa Fe 4455"""


def test_cabify_addresses_with_hint_lines():
    p, d = parse_addresses(CABIFY_HINT_FIXTURE)
    assert p is not None and "Cabildo" in p
    assert d is not None and "Santa Fe" in d


CABIFY_INLINE_FIXTURE = """ARS 3.200
Origen: Av. Cabildo 2233
Destino: Av. Santa Fe 4455"""


def test_cabify_addresses_inline_hint():
    p, d = parse_addresses(CABIFY_INLINE_FIXTURE)
    assert p is not None and "Cabildo" in p
    assert d is not None and "Santa Fe" in d


def test_addresses_none_when_empty():
    p, d = parse_addresses("")
    assert p is None and d is None
