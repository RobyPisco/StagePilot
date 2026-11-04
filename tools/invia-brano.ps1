<#
  Manda un tab o un ChordPro dal PC a Backline sul tablet/telefono collegato con adb.

  Uso:
    .\tools\invia-brano.ps1                 # prende il testo dagli appunti del PC
    .\tools\invia-brano.ps1 -File brano.txt # oppure da un file di testo

  Su Backline si apre la schermata di importazione già con il testo: si vede l'anteprima
  ("così si leggerà"), si tocca Continua, si controllano titolo, BPM e durata e si salva.
#>
param([string]$File)

$ErrorActionPreference = 'Stop'

$text = if ($File) { Get-Content -Raw -Encoding UTF8 -LiteralPath $File } else { Get-Clipboard -Raw }
if ([string]::IsNullOrWhiteSpace($text)) {
    throw "Niente da inviare: gli appunti sono vuoti (o il file è vuoto). Copia prima il tab."
}

$devices = (& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
if (-not $devices) { throw "Nessun dispositivo collegato con adb. Collega il tablet con il cavo e consenti il debug USB." }

# il testo passa da un file sul dispositivo: così non ci sono limiti di riga di comando né problemi di virgolette
$tmp = Join-Path ([IO.Path]::GetTempPath()) 'backline-brano.txt'
[IO.File]::WriteAllText($tmp, $text.Replace("`r`n", "`n"), (New-Object Text.UTF8Encoding $false))
& adb push $tmp /sdcard/Download/backline-brano.txt | Out-Null

# lo script gira sul dispositivo: legge il file e lo passa a Backline come testo condiviso
$cmd = 'am start -n it.backline.app/.MainActivity -a android.intent.action.SEND -t text/plain ' +
       '--es android.intent.extra.TEXT "$(cat /sdcard/Download/backline-brano.txt)"'
& adb shell $cmd | Out-Null

"Inviato a Backline ($($text.Length) caratteri). Controlla il tablet."
