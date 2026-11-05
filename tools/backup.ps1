# Scarica dal tablet (cavo, adb) le copie di sicurezza automatiche di Backline.
# Le copie stanno in Android/data/it.backline.app/files/backups: una al giorno, le ultime 7.
# Uso:  .\tools\backup.ps1              (le mette in .\backup-tablet)
#       .\tools\backup.ps1 -Dest D:\copie
param([string]$Dest = (Join-Path (Split-Path $PSScriptRoot -Parent) "backup-tablet"))
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { Write-Error "adb non trovato: installa platform-tools."; exit 1 }
New-Item -ItemType Directory -Force $Dest | Out-Null
& $adb pull /sdcard/Android/data/it.backline.app/files/backups/. $Dest
if ($LASTEXITCODE -ne 0) { Write-Error "Nessuna copia trovata: apri Backline almeno una volta (versione nuova) e controlla il cavo."; exit 1 }
Write-Host "Copie in $Dest"
