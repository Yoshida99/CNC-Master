param(
    [string]$Model = "qwen2.5-coder:7b-instruct",
    [int]$Port = 8787,
    [int]$Context = 8192
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

$Ollama = Get-Command ollama -ErrorAction SilentlyContinue
if (-not $Ollama) {
    $OllamaCandidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'),
        (Join-Path $env:LOCALAPPDATA 'Ollama\ollama.exe'),
        'C:\Program Files\Ollama\ollama.exe'
    )
    $OllamaPath = $OllamaCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $OllamaPath) {
        throw "Ollama не найден. Установи Ollama и повтори запуск."
    }
    $Ollama = Get-Item $OllamaPath
}

$Python = Get-Command python -ErrorAction SilentlyContinue
if (-not $Python) {
    throw "Python не найден."
}

$VenvPython = Join-Path $RepoRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $VenvPython)) {
    python -m venv .venv
}

& $VenvPython -m pip install --disable-pip-version-check -r backend\requirements.txt

$env:CNC_AI_PROVIDER = "ollama"
$env:OLLAMA_BASE_URL = "http://127.0.0.1:11434"
$env:OLLAMA_MODEL = $Model
$env:OLLAMA_NUM_CTX = "$Context"
# Important for the mining PC: release model VRAM immediately after every answer.
$env:OLLAMA_KEEP_ALIVE = "0s"

Write-Host "CNC Master Local AI"
Write-Host "Provider: ollama"
Write-Host "Model: $Model"
Write-Host "Context: $Context"
Write-Host "API: http://127.0.0.1:$Port"

& $VenvPython -m uvicorn backend.main:app --host 127.0.0.1 --port $Port
