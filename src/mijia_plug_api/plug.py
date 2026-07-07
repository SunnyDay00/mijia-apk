from __future__ import annotations

from dataclasses import asdict
from typing import Any

from .config import MiotProperty, Settings
from .miio_lan import MiioLanClient, MiioProtocolError


class PlugError(RuntimeError):
    pass


class XiaomiPlug:
    def __init__(self, settings: Settings):
        self._settings = settings
        self._client = MiioLanClient(settings.plug_ip, settings.plug_token)

    def set_power(self, on: bool) -> dict[str, Any]:
        response = self._raw("set_properties", [self._settings.switch.as_set_payload(on)])
        self._raise_on_miot_error(response)
        return {"on": on, "raw": response}

    def get_status(self) -> dict[str, Any]:
        properties = [
            ("on", self._settings.switch),
            ("power_w", self._settings.power),
            ("consumption_kwh", self._settings.consumption),
        ]
        enabled = [(name, prop) for name, prop in properties if prop is not None]
        response = self._raw("get_properties", [prop.as_get_payload() for _, prop in enabled])
        values = self._parse_property_values(enabled, response)
        self._raise_on_miot_error(response, required=[self._settings.switch])
        return {"values": values, "raw": response}

    def toggle(self) -> dict[str, Any]:
        status = self.get_status()
        current = status["values"].get("on")
        if not isinstance(current, bool):
            raise PlugError(f"Cannot toggle because current switch value is not bool: {current!r}")
        return self.set_power(not current)

    def _raw(self, command: str, params: list[dict[str, Any]]) -> Any:
        try:
            return self._client.call(command, params)
        except MiioProtocolError as exc:
            raise PlugError(str(exc)) from exc

    @staticmethod
    def _parse_property_values(
        requested: list[tuple[str, MiotProperty]],
        response: Any,
    ) -> dict[str, Any]:
        if not isinstance(response, list):
            return {}

        values: dict[str, Any] = {}
        for name, prop in requested:
            match = XiaomiPlug._find_response_item(prop, response)
            if match is not None and "value" in match:
                values[name] = match["value"]
        return values

    @staticmethod
    def _find_response_item(prop: MiotProperty, response: list[Any]) -> dict[str, Any] | None:
        target = asdict(prop)
        for item in response:
            if not isinstance(item, dict):
                continue
            if item.get("siid") == target["siid"] and item.get("piid") == target["piid"]:
                return item
            if item.get("did") == target["did"]:
                return item
        return None

    @staticmethod
    def _raise_on_miot_error(
        response: Any,
        required: list[MiotProperty] | None = None,
    ) -> None:
        if not isinstance(response, list):
            return

        if required is not None:
            response = [
                item
                for prop in required
                if (item := XiaomiPlug._find_response_item(prop, response)) is not None
            ]

        errors = [
            item
            for item in response
            if isinstance(item, dict) and item.get("code") not in (None, 0)
        ]
        if errors:
            raise PlugError(f"MIOT command failed: {errors!r}")
