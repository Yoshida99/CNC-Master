param(
    [string]$GatewayUrl = $env:CNC_GATEWAY_WS_URL,
    [string]$LocalAiUrl = "http://127.0.0.1:8787",
    [switch]$EnableMiningArbitration
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

if ([string]::IsNullOrWhiteSpace($GatewayUrl)) {
    throw "CNC_GATEWAY_WS_URL не задан."
}
$VenvPython = Join-Path $RepoRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $VenvPython)) {
    python -m venv .venv
}

& $VenvPython -m pip install --disable-pip-version-check -r worker\requirements.txt

$env:CNC_GATEWAY_WS_URL = $GatewayUrl
$env:CNC_LOCAL_AI_URL = $LocalAiUrl

$ArbitrationEnabled = $EnableMiningArbitration -or (
    $env:CNC_GPU_ARBITRATION_ENABLED -match '^(?i:true|1|yes|on)
Write-Host "CNC Master GPU Worker"
Write-Host "Gateway: $GatewayUrl"
Write-Host "Local AI: $LocalAiUrl"
Write-Host "Worker token: локальная identity; raw token наружу не выводится"
Write-Host "Mining arbitration: $($env:CNC_GPU_ARBITRATION_ENABLED)"

& $VenvPython -m worker.identity
& $VenvPython -m worker.cnc_gpu_worker

)
$env:CNC_GPU_ARBITRATION_ENABLED = if ($ArbitrationEnabled) { "true" } else { "false" }

Write-Host "CNC Master GPU Worker"
Write-Host "Gateway: $GatewayUrl"
Write-Host "Local AI: $LocalAiUrl"
Write-Host "Worker token: локальная identity; raw token наружу не выводится"

& $VenvPython -m worker.identity
& $VenvPython -m worker.cnc_gpu_worker
