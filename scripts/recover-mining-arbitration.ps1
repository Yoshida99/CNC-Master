$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$Python = Join-Path $RepoRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $Python)) {
    exit 0
}

$env:CNC_GPU_ARBITRATION_ENABLED = "true"
Set-Location $RepoRoot

& $Python -m worker.mining_arbitration --recover
exit $LASTEXITCODE
