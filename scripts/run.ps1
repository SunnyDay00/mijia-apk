param(
    [string]$HostName = "0.0.0.0",
    [int]$Port = 8787
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

if (-not (Test-Path ".env")) {
    throw "Missing .env. Copy .env.example to .env and fill PLUG_IP / PLUG_TOKEN first."
}

$env:HOST = $HostName
$env:PORT = "$Port"
uv run mijia-plug-api
