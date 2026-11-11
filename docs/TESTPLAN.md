# Testplan for fase 1 (testappen)

Mål: finne ut hva akkurat denne TV-en logger, slik at den ferdige appen bygges på fakta. La testappen stå installert i **minst 2 uker**. Ta bilde av diagnoseboksen og den rå loggen med mobilen etter hver test, og legg bildene i en ny issue eller PR-kommentar.

Forberedelse: slå av **autoforhåndsvisning** i Netflix-profilen som skal brukes (netflix.com → Profiler → velg profil → Avspillingsinnstillinger).

| # | Test | Slik gjør du det | Hva vi ser etter i loggen |
|---|---|---|---|
| 1 | Tillatelser | Installer, åpne appen, slå på begge bryterne via knappene | Appen vises i begge listene; diagnoseboksen viser begge som «på» og lytteren som «tilkoblet» |
| 2 | Vanlig app-bruk | Åpne Netflix, så YouTube, så NRK TV, så tilbake til startskjermen | `USAGE`-hendelser med pakke- og klassenavn for hver app og for startskjermen |
| 3 | Avspilling | Spill noe i hver av de tre appene i 2 min, pause 1 min, spill videre | `MEDIA`-hendelser «spiller» / «pauset» per app; om tittel kommer med |
| 4 | Netflix-meny | Bla i Netflix-menyen i 3 min uten å starte noe | Kommer det «spiller» likevel? |
| 5 | Lang avspilling | Spill en film i 30 min uten å røre fjernkontrollen | Ingen skjermsparer-hendelse i perioden |
| 6 | Skjermsparer | La TV-en stå på startskjermen til skjermspareren starter, trykk så en knapp | `DREAM` start/stopp; om skjermspareren også vises som `USAGE`; verdien for skjermsparer i diagnoseboksen |
| 7 | Lang pause | Pause Netflix og vent til skjermspareren starter | `MEDIA` pauset → `DREAM` start |
| 8 | Av/på | Slå av med fjernkontrollen, vent 5 min, slå på | Skjerm av/på-hendelser; at lytteren fortsatt er tilkoblet |
| 9 | Natt | La TV-en være av over natten | Neste morgen: lytteren tilkoblet; ingen hull etter påslag |
| 10 | Strømbrudd | Trekk ut strømkontakten 1 min, sett i, slå på | Oppstart/nedstengning; lytteren kobler seg til igjen av seg selv |
| 11 | Konsoll/TV-kanal | Bytt til HDMI-inngangen (spillkonsoll) og evt. en vanlig TV-kanal | Hvilken pakke tiden havner på |
| 12 | Oppdatering | Når en ny versjon er ute: installer oppå via Downloader | Gammel logg er fortsatt der |
| 13 | Historikk | Etter 14 dager | Hvor gammel er eldste hendelse i Androids logg (forventet ca. 10 dager)? Har appens egen database eldre data? |

## Resultat

Fyll inn når testene er gjort (eller gi bildene til Codex og be den fylle inn):

| # | Resultat | Kommentar |
|---|---|---|
| 1 | | |
| 2 | | |
| 3 | | |
| 4 | | |
| 5 | | |
| 6 | | |
| 7 | | |
| 8 | | |
| 9 | | |
| 10 | | |
| 11 | | |
| 12 | | |
| 13 | | |
