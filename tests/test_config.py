from __future__ import annotations

from mijia_plug_api.config import load_settings


def test_load_settings_from_env(monkeypatch):
    monkeypatch.setenv("PLUG_IP", "192.168.31.10")
    monkeypatch.setenv("PLUG_TOKEN", "a" * 32)
    monkeypatch.setenv("API_KEY", "secret")

    settings = load_settings()

    assert settings.plug_ip == "192.168.31.10"
    assert settings.plug_token == "a" * 32
    assert settings.api_key == "secret"
    assert settings.switch.siid == 2
    assert settings.switch.piid == 1
