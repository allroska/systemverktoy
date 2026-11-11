# Henter eksportfilene fra Systemverktøy på TV-en til PC-en, og kan installere nyeste versjon.
# Bruk:  .\tools\hent-tvlogg.ps1 -Tv 192.168.1.50          (bare hent logg)
#        .\tools\hent-tvlogg.ps1 -Tv 192.168.1.50 -Installer   (installer nyeste APK først)
param(
  [Parameter(Mandatory = $true)][string]$Tv,
  [switch]$Installer,
  [string]$Mappe = "$HOME\Documents\Systemverktoy-logg"
)
$ErrorActionPreference = "Stop"
$R = "allroska/systemverktoy"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  Write-Error "adb mangler. Kjør: winget install Google.PlatformTools  (og åpne et nytt PowerShell-vindu)"
}

Write-Host "Kobler til TV-en ($Tv)..."
adb connect "${Tv}:5555" | Out-Host
$state = (adb -s "${Tv}:5555" get-state 2>$null)
if ($state -ne "device") {
  Write-Error "Fikk ikke kontakt. Sjekk at TV-en er på, at USB-feilsøking er på, og godkjenn PC-en på TV-skjermen hvis den spør."
}

if ($Installer) {
  $tmp = Join-Path $env:TEMP "systemverktoy-apk"
  New-Item -ItemType Directory -Force -Path $tmp | Out-Null
  Remove-Item "$tmp\*.apk" -ErrorAction SilentlyContinue
  Write-Host "Laster ned nyeste versjon fra GitHub..."
  gh release download -R $R -p "systemverktoy.apk" -D $tmp --clobber
  Write-Host "Installerer (oppå gammel versjon, data beholdes)..."
  adb -s "${Tv}:5555" install -r "$tmp\systemverktoy.apk" | Out-Host
}

$stamp = Get-Date -Format "yyyy-MM-dd_HHmm"
$dest = Join-Path $Mappe $stamp
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Write-Host "Henter eksportfiler..."
adb -s "${Tv}:5555" pull "/sdcard/Download/Systemverktoy/." $dest | Out-Host
Write-Host ""
Write-Host "Ferdig. Filene ligger i: $dest"
Get-ChildItem $dest | Format-Table Name, Length, LastWriteTime
