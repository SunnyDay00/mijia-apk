from __future__ import annotations

from functools import lru_cache
from typing import Annotated

import uvicorn
from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel

from .config import Settings, load_settings
from .plug import PlugError, XiaomiPlug


class SetPowerRequest(BaseModel):
    on: bool


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return load_settings()


@lru_cache(maxsize=1)
def get_plug() -> XiaomiPlug:
    return XiaomiPlug(get_settings())


def require_api_key(
    settings: Annotated[Settings, Depends(get_settings)],
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
    key: Annotated[str | None, Query()] = None,
) -> None:
    if not settings.api_key:
        return

    supplied = x_api_key or key
    if supplied != settings.api_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing API key",
        )


app = FastAPI(
    title="Mijia Plug API",
    description="LAN HTTP bridge for Xiaomi Smart Plug 3",
    version="0.1.0",
)


@app.get("/health")
def health() -> dict[str, bool]:
    return {"ok": True}


@app.get("/status", dependencies=[Depends(require_api_key)])
async def status_endpoint(plug: Annotated[XiaomiPlug, Depends(get_plug)]) -> dict:
    try:
        return await run_in_threadpool(plug.get_status)
    except PlugError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/plug/set", dependencies=[Depends(require_api_key)])
async def set_endpoint(
    body: SetPowerRequest,
    plug: Annotated[XiaomiPlug, Depends(get_plug)],
) -> dict:
    try:
        return await run_in_threadpool(plug.set_power, body.on)
    except PlugError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/plug/on", dependencies=[Depends(require_api_key)])
async def on_endpoint(plug: Annotated[XiaomiPlug, Depends(get_plug)]) -> dict:
    try:
        return await run_in_threadpool(plug.set_power, True)
    except PlugError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/plug/off", dependencies=[Depends(require_api_key)])
async def off_endpoint(plug: Annotated[XiaomiPlug, Depends(get_plug)]) -> dict:
    try:
        return await run_in_threadpool(plug.set_power, False)
    except PlugError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/plug/toggle", dependencies=[Depends(require_api_key)])
async def toggle_endpoint(plug: Annotated[XiaomiPlug, Depends(get_plug)]) -> dict:
    try:
        return await run_in_threadpool(plug.toggle)
    except PlugError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


def main() -> None:
    settings = get_settings()
    uvicorn.run(
        "mijia_plug_api.server:app",
        host=settings.host,
        port=settings.port,
        reload=False,
    )


if __name__ == "__main__":
    main()
