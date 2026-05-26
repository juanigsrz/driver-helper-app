import httpx

from cache import LRUCache


class GeocodeClient:
    """Async Nominatim client with in-memory LRU cache."""

    def __init__(
        self,
        base_url: str,
        client: httpx.AsyncClient | None = None,
        cache_size: int = 10_000,
        country_code: str = "ar",
    ):
        self.base_url = base_url.rstrip("/")
        self.country_code = country_code
        self._client = client or httpx.AsyncClient(
            timeout=10.0,
            headers={"User-Agent": "driver-helper-app/0.1"},
        )
        self._cache: LRUCache[str, tuple[float, float]] = LRUCache(cache_size)

    async def geocode(self, address: str) -> tuple[float, float] | None:
        key = address.strip().lower()
        if not key:
            return None
        hit = self._cache.get(key)
        if hit is not None:
            return hit

        url = f"{self.base_url}/search"
        params = {
            "q": address,
            "format": "jsonv2",
            "limit": 1,
            "countrycodes": self.country_code,
        }
        try:
            r = await self._client.get(url, params=params)
            r.raise_for_status()
            data = r.json()
        except httpx.HTTPError:
            return None
        if not data:
            return None
        lat = float(data[0]["lat"])
        lng = float(data[0]["lon"])
        self._cache.set(key, (lat, lng))
        return (lat, lng)

    async def close(self) -> None:
        await self._client.aclose()
