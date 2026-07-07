from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv


@dataclass(frozen=True)
class MiotProperty:
    did: str
    siid: int
    piid: int

    def as_get_payload(self) -> dict[str, str | int]:
        return {"did": self.did, "siid": self.siid, "piid": self.piid}

    def as_set_payload(
        self,
        value: bool | int | float | str,
    ) -> dict[str, str | int | bool | float]:
        return {**self.as_get_payload(), "value": value}


@dataclass(frozen=True)
class Settings:
    plug_ip: str
    plug_token: str
    api_key: str | None
    host: str
    port: int
    switch: MiotProperty
    power: MiotProperty | None
    consumption: MiotProperty | None


def _env(name: str, default: str | None = None) -> str | None:
    value = os.getenv(name, default)
    if value is None:
        return None
    value = value.strip()
    return value or None


def _env_int(name: str, default: int | None = None) -> int | None:
    value = _env(name, None)
    if value is None:
        return default
    return int(value)


def _required_env(name: str) -> str:
    value = _env(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def _optional_property(did_name: str, siid_name: str, piid_name: str) -> MiotProperty | None:
    did = _env(did_name)
    siid = _env_int(siid_name)
    piid = _env_int(piid_name)
    if did is None or siid is None or piid is None:
        return None
    return MiotProperty(did=did, siid=siid, piid=piid)


def load_settings() -> Settings:
    load_dotenv()

    return Settings(
        plug_ip=_required_env("PLUG_IP"),
        plug_token=_required_env("PLUG_TOKEN"),
        api_key=_env("API_KEY"),
        host=_env("HOST", "0.0.0.0") or "0.0.0.0",
        port=_env_int("PORT", 8787) or 8787,
        switch=MiotProperty(
            did=_env("PLUG_SWITCH_DID", "switch:on") or "switch:on",
            siid=_env_int("PLUG_SWITCH_SIID", 2) or 2,
            piid=_env_int("PLUG_SWITCH_PIID", 1) or 1,
        ),
        power=_optional_property("PLUG_POWER_DID", "PLUG_POWER_SIID", "PLUG_POWER_PIID"),
        consumption=_optional_property(
            "PLUG_CONSUMPTION_DID",
            "PLUG_CONSUMPTION_SIID",
            "PLUG_CONSUMPTION_PIID",
        ),
    )
