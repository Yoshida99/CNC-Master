param(
    [string]$TaskName = "CNC-Master-Mining-Recovery"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$RecoveryScript = Join-Path $RepoRoot "scripts\recover-mining-arbitration.ps1"

if (-not (Test-Path $RecoveryScript)) {
    throw "Recovery script not found: $RecoveryScript"
}

$quotedScript = '"'+$RecoveryScript+'"'
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument ("-WindowStyle Hidden -NoProfile -NonInteractive -ExecutionPolicy Bypass -File " + $quotedScript)
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 1)
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 2)

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description "Fail-safe recovery for CNC Master AI mining arbitration lease." -Force | Out-Null
Write-Host "Installed scheduled recovery task: $TaskName"
