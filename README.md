# Systemverktøy

En liten, helt lokal Android TV-app som viser en tidslinje over hvordan TV-en har vært brukt i løpet av dagen: hvilke apper, når, og om noe faktisk ble spilt av eller om TV-en bare sto på.

- Ingen data forlater TV-en (appen har ikke internett-tilgang).
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
