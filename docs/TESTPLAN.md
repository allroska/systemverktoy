# Testplan for fase 1 (testappen)

Mål: finne ut hva akkurat denne TV-en logger, slik at den ferdige appen bygges på fakta. La testappen stå installert i **minst 2 uker**. Ta bilde av diagnoseboksen og den rå loggen med mobilen etter hver test, og legg bildene i en ny issue eller PR-kommentar.

Forberedelse: ingen endring i Netflix-innstillinger (flere profiler i huset; appen må tåle forhåndsvisninger). YouTube er blokkert på denne TV-en – bruk en annen strømmeapp der testene nevner YouTube.

| # | Test | Slik gjør du det | Hva vi ser etter i loggen |
|---|---|---|---|
| 1 | Tillatelser | Installer, åpne appen, slå på begge bryterne via knappene | Appen vises i begge listene; diagnoseboksen viser begge som «på» og lytteren som «tilkoblet» |
| 2 | Vanlig app-bruk | Åpne Netflix, så YouTube, så NRK TV, så tilbake til startskjermen | `USAGE`-hendelser med pakke- og klassenavn for hver app og for startskjermen |
| 3 | Avspilling | Spill noe i hver av de tre appene i 2 min, pause 1 min, spill videre | `MEDIA`-hendelser «spiller» / «pauset» per app; om tittel kommer med. Uendret status og tittel skal ikke fylle loggen med like rader; pause, videre avspilling og ny tittel skal fortsatt logges. Samme gjelder endret/uforandret media-lyd (`AUDIO`) |
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
| 14 | Play Protect | Slå Play Protect på igjen etter installasjon og vent et døgn | Blir appen flagget, deaktivert eller fjernet? |
| 15 | Hjerteslag | Se på diagnoseboksen etter noen timer med TV-en på | Siste hjerteslag er under ca. 15 min gammelt; ingen uforklarte hull |
| 16 | Manglende tillatelser | Slå av én tillatelse om gangen i TV-innstillingene, åpne appen, og slå den på igjen via oppsettet | Ingen krasj; oppsett vises når en tillatelse mangler, diagnose vises igjen når begge er på |
| 17 | Gjentatt kopiering | Åpne appen flere ganger etter vanlig app-bruk, og se på samme tidsrom i råloggen. Vent også på en bakgrunnskopiering | Hver `USAGE`-hendelse vises én gang. Nye `APP`- og andre sanntidshendelser er forventet |

## Resultat

Fyll inn når testene er gjort (eller gi bildene til Codex og be den fylle inn):

| # | Resultat | Kommentar |
|---|---|---|
| 1 | OK (11.11, v1.0.8) | Begge tillatelser på, lytter tilkoblet. «Tilgang til varsler» måtte slås på via TV-ens innstillinger: fjernkontrollen virket ikke da appen åpnet siden direkte (ingen fokus). |
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
| 14 | | |
| 15 | | |
| 16 | | |
| 17 | | |
