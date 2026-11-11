# Systemverktøy

En liten, helt lokal Android TV-app som viser en tidslinje over hvordan TV-en har vært brukt i løpet av dagen: hvilke apper, når, og om noe faktisk ble spilt av eller om TV-en bare sto på.

- Appen har ikke internett-tilgang. Eieren kan hente lokale eksportfiler til PC med adb.
- Målenhet: Sony BRAVIA med Google TV (Android TV 12).

## Dokumentasjon

| Fil | Innhold |
|---|---|
| [`docs/SPEC.md`](docs/SPEC.md) | Hva som skal bygges, og produktbeslutningene |
| [`AGENTS.md`](AGENTS.md) | Tekniske regler for den som skriver koden (Codex) |
| [`CLAUDE.md`](CLAUDE.md) | Instrukser for kodereviewen (Claude) |
| [`docs/RESEARCH.md`](docs/RESEARCH.md) | Tekniske fakta om TV-en |
| [`docs/TESTPLAN.md`](docs/TESTPLAN.md) | Hva som skal testes på TV-en |
| [`docs/OPPGAVER.md`](docs/OPPGAVER.md) | Ferdige oppgavetekster til Codex |
| [`docs/skisser/`](docs/skisser/) | Skisser av skjermene |

## Installere på TV-en

1. Installer **Downloader by AFTVnews** fra Play Store på TV-en.
2. **Slå av Play Protect-skanning midlertidig** (Play Butikk → profil → Innstillinger → Google Play Protect). Ellers blokkeres appen fordi den ber om tilgang til bruksdata og varsler.
3. Åpne nyeste versjon i Downloader:
   `https://github.com/allroska/systemverktoy/releases/latest/download/systemverktoy.apk`
4. Tillat installasjon fra Downloader første gang, og installer.
5. **Slå Play Protect på igjen.**
6. Åpne «Systemverktøy» og slå på de to tillatelsene appen ber om.

Samme fremgangsmåte ved hver oppdatering. Appen må alltid signeres med samme nøkkel, ellers må den avinstalleres først (og historikken går tapt).

## Eksport til PC

Appen overskriver tre filer i `Download/Systemverktoy/` når den åpnes og omtrent
én gang i døgnet (Android kan utsette bakgrunnsjobben i standby):

- `systemverktoy-raa.csv`: råhendelser fra i dag og de 13 foregående lokale dagene.
- `systemverktoy-tidslinje.csv`: beregnede perioder med grunnlag for samme tidsrom.
- `systemverktoy-diagnose.txt`: diagnosestatus, appversjon og eksporttidspunkt.

CSV-filene er UTF-8 med komma som skilletegn og ISO-tid med lokal tidssoneoffset.
Eksporten endrer eller sletter ingen historikk i appen. I skjult diagnosevisning
(opp til overskriften, hold OK i tre sekunder) kan du velge **Eksporter nå**.
Vent på «Eksportert hh:mm» før du henter filene.

På PC: installer Android Platform Tools (`winget install Google.PlatformTools`),
slå på USB-feilsøking på TV-en og godkjenn PC-en. Begge må være på hjemmenettet.
Kjør i PowerShell fra prosjektmappen (bytt ut eksempeladressen med TV-ens IP):

```powershell
.\tools\hent-tvlogg.ps1 -Tv 192.168.1.50
```

[`tools/hent-tvlogg.ps1`](tools/hent-tvlogg.ps1) henter filene med adb til en
mappe med dato og klokkeslett under Dokumenter/Systemverktoy-logg. `-Mappe` velger
et annet mål. `-Installer` installerer nyeste APK først (krever GitHub CLI);
åpne deretter appen på TV-en for å lage fersk eksport. Selve appen sender ingenting
på nett og trenger ingen lagringstillatelse.

## PIN-kode

Ved første åpning velger du fire sifre og gjentar dem. Bruk tallknappene, eller
opp/ned for å endre siffer fra 0 og venstre/høyre for å flytte. Urørte plasser
vises som ○ og tastede som ●. Ved pilbruk vises sifferet under markøren til du
flytter videre. OK bekrefter når alle fire plasser er fylt.
PIN kreves igjen når appen har vært i bakgrunnen. Fem feil gir ett minutts pause;
bakgrunnsloggingen fortsetter.

Glemt PIN kan nullstilles fra en godkjent PC uten å slette historikken:

```sh
adb shell am broadcast -a com.systemverktoy.app.RESET_PIN -n com.systemverktoy.app/.PinResetReceiver
```

Åpne appen på nytt og velg ny kode. Ikke bruk `pm clear` eller avinstaller appen,
siden det sletter historikken. PIN lagres som saltet hash og tas ikke med i eksport.
