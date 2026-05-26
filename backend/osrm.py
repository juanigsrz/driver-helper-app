import httpx

from score import Leg


class OSRMClient:
    """Thin async client for an OSRM /route endpoint.

    Note: OSRM coordinate order is (lng, lat), not (lat, lng).
    """

    def __init__(self, base_url: str, client: httpx.AsyncClient | None = None):
        self.base_url = base_url.rstrip("/")
        self._client = client or httpx.AsyncClient(timeout=5.0)

    async def route(
        self, lng_a: float, lat_a: float, lng_b: float, lat_b: float
    ) -> Leg:
        url = f"{self.base_url}/route/v1/driving/{lng_a},{lat_a};{lng_b},{lat_b}"
        params = {"overview": "false", "steps": "false", "alternatives": "false"}
        r = await self._client.get(url, params=params)
        r.raise_for_status()
        data = r.json()
        if data.get("code") != "Ok" or not data.get("routes"):
            raise RuntimeError(f"OSRM error: {data.get('code')}")
        route = data["routes"][0]
        return Leg(
            distance_km=route["distance"] / 1000.0,
            duration_min=route["duration"] / 60.0,
        )

    async def close(self) -> None:
        await self._client.aclose()
