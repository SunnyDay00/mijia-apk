from __future__ import annotations

from fastapi.testclient import TestClient

from mijia_plug_api.config import MiotProperty, Settings
from mijia_plug_api.server import app, get_plug, get_settings


class FakePlug:
    def __init__(self):
        self.on = False

    def get_status(self):
        return {"values": {"on": self.on}, "raw": [{"code": 0, "value": self.on}]}

    def set_power(self, on: bool):
        self.on = on
        return {"on": on, "raw": [{"code": 0}]}

    def toggle(self):
        self.on = not self.on
        return {"on": self.on, "raw": [{"code": 0}]}


def _settings(api_key: str | None = "secret") -> Settings:
    return Settings(
        plug_ip="192.168.31.10",
        plug_token="a" * 32,
        api_key=api_key,
        host="127.0.0.1",
        port=8787,
        switch=MiotProperty(did="switch:on", siid=2, piid=1),
        power=None,
        consumption=None,
    )


def test_api_key_is_required():
    app.dependency_overrides[get_settings] = lambda: _settings("secret")
    app.dependency_overrides[get_plug] = FakePlug
    client = TestClient(app)

    response = client.post("/plug/on")

    app.dependency_overrides.clear()
    assert response.status_code == 401


def test_turn_on_with_query_key():
    app.dependency_overrides[get_settings] = lambda: _settings("secret")
    app.dependency_overrides[get_plug] = FakePlug
    client = TestClient(app)

    response = client.post("/plug/on?key=secret")

    app.dependency_overrides.clear()
    assert response.status_code == 200
    assert response.json()["on"] is True


def test_health_does_not_require_key():
    app.dependency_overrides[get_settings] = lambda: _settings("secret")
    client = TestClient(app)

    response = client.get("/health")

    app.dependency_overrides.clear()
    assert response.status_code == 200
    assert response.json() == {"ok": True}
