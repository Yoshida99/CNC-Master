$ErrorActionPreference = "Stop"
$Keystore = "cnc-master-release.jks"
$Alias = "cnc-master"

Write-Host "Создание постоянного ключа подписи CNC Master" -ForegroundColor Cyan
Write-Host "Придумай и СОХРАНИ пароль. Потеря ключа = старые установки нельзя будет обновить новым APK." -ForegroundColor Yellow

keytool -genkeypair -v `
  -keystore $Keystore `
  -alias $Alias `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000

$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $Keystore))
$base64 = [Convert]::ToBase64String($bytes)

Write-Host ""
Write-Host "Добавь в GitHub -> Settings -> Secrets and variables -> Actions:" -ForegroundColor Green
Write-Host "CNC_KEYSTORE_BASE64 =" -ForegroundColor Cyan
Write-Host $base64
Write-Host "CNC_KEY_ALIAS = $Alias" -ForegroundColor Cyan
Write-Host "CNC_KEYSTORE_PASSWORD = пароль keystore" -ForegroundColor Cyan
Write-Host "CNC_KEY_PASSWORD = пароль ключа" -ForegroundColor Cyan
