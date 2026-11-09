param(
    [string]$GatewayUrl = $env:CNC_GATEWAY_WS_URL,
    [string]$WorkerToken = $env:CNC_WORKER_TOKEN,
    [string]$LocalAiUrl = "http://127.0.0.1:8787"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

if ([string]::IsNullOrWhiteSpace($GatewayUrl)) {
    throw "CNC_GATEWAY_WS_URL не задан."
}
if ([string]::IsNullOrWhiteSpace($WorkerToken)) {
    throw "CNC_WORKER_TOKEN не задан."
}

$VenvPython = Join-Path $RepoRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $VenvPython)) {
    python -m venv .venv
}

& $VenvPython -m pip install --disable-pip-version-check -r worker\requirements.txt

$env:CNC_GATEWAY_WS_URL = $GatewayUrl
$env:CNC_WORKER_TOKEN = $WorkerToken
$env:CNC_LOCAL_AI_URL = $LocalAiUrl

Write-Host "CNC Master GPU Worker"
Write-Host "Gateway: $GatewayUrl"
Write-Host "Local AI: $LocalAiUrl"

& $VenvPython -m worker.cnc_gpu_worker
