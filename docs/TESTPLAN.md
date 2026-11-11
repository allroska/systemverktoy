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
| 18 | Fotograferbar rålogg | Åpne en dag med minst 30 hendelser. Ta bilde i 720p og 1080p hvis tilgjengelig. Trykk ned til listen, deretter kanal ned og kanal opp. Bla ned og åpne appen på nytt; gjenta og vent på en ny hendelse | Minst 15 enlinjede rader, hvit tekst på mørk bakgrunn, ingen lyse bokser. Kanal blar én loggside; gjenåpning og nye hendelser flytter listen til toppen. Venstre/høyre bytter dag. Prøv også stor systemskrift: radhøyden skal vokse så bokstavene ikke kuttes |
| 19 | Bare viktige | Trykk OK på «Bare viktige». Se på vanlig app-bruk, avspilling/pause, skjermsparer, skjerm av/på og lytterhendelser. Slå filteret av igjen | Launcherens og Systemverktøys egne app-hendelser som `APP_OPENED` skjules, men APP-hendelsene `BOOT_COMPLETED`, `IMPORT_FAILED`, `USAGE_ACCESS_REVOKED` og `heartbeat` beholdes når de finnes i loggen. Andre appers USAGE/MEDIA og globale DREAM/AUDIO/LISTENER/skjermhendelser vises. Alle råhendelser kommer tilbake når filteret slås av; ingen data slettes |

## Resultat

Fyll inn når testene er gjort (eller gi bildene til Codex og be den fylle inn):

| # | Resultat | Kommentar |
|---|---|---|
| 1 | OK (11.11, v1.0.8) | Begge tillatelser på, lytter tilkoblet. «Tilgang til varsler» måtte slås på via TV-ens innstillinger: fjernkontrollen virket ikke da appen åpnet siden direkte (ingen fokus). |
| 2 | OK (11.11) | Netflix og NRK TV gir USAGE med pakke + activity. Netflix bruker samme MainActivity for meny og avspiller. |
| 3 | OK (11.11) | Netflix og NRK: spiller/pauset/stoppet kommer presist. Ingen titler (Spotify har tittel). AUDIO media_active 1/0 følger pause. YouTube blokkert på TV-en. |
| 4 | OK (11.11, én profil) | Ca. 3 min i Netflix-menyen ga ingen «spiller». Andre profiler ikke testet. |
| 5 | | |
| 6 | OK (11.11) | DREAMING_STARTED/STOPPED + dreamx DreamActivity i USAGE. Skjermsparer gir også SCREEN_NON_INTERACTIVE. |
| 7 | | |
| 8 | | |
| 9 | | |
| 10 | | |
| 11 | Delvis (11.11) | HDMI 4 = com.sony.dtv.tvx med MediaSession «HDMI 4» STATE_PLAYING så lenge inngangen er valgt. Vanlige TV-kanaler ikke testet. |
| 12 | OK (11.11) | v1.0.8 → v1.0.10 beholdt data; lytter koblet til igjen automatisk. |
| 13 | | |
| 14 | | |
| 15 | OK (11.11) | Hjerteslag presist hvert 15. min. |
| 16 | | |
| 17 | | |
| 18 | | |
| 19 | | |

## Fase 2 – skjermer (PR B)

1. Installer oppå dagens versjon. Kontroller at gammel rålogg er beholdt. «I dag»
   skal vise tre oppsummeringskort, egen App åpen-/HDMI-sum, døgnstripe og tidslinje.
   Systemverktøy selv skal ikke vises som periode eller inngå i summer.
2. Første rad skal ha en hvit fokusramme. Opp/ned flytter mellom radene og blar
   listen. Venstre/høyre velger dag, uten å gå lenger frem enn i dag. Kontroller
   både 720p og 1080p og gjerne stor systemskrift.
3. Trykk OK på en Netflix-rad. Hele sammenhengende appøkten skal vises med fordeling,
   «Hva skjedde», tittel/forklaring og «Grunnlag – rå signaler». Tilbake-knappen skal
   ha fokus. Ned velger venstre liste; høyre velger råsignalene. Opp/ned blar. Tilbake
   med fjernkontrollen går til samme rad og rulleposisjon i tidslinjen. Prøv en rad
   langt nede på en dag med mange perioder.
4. Velg en dag med et kjent hjerteslagshull eller lytterfrakobling. Se «Ufullstendig
   dag» og en oransje, skravert Ukjent-periode. Ved avkortet grunnlag kan et eget
   «Begrenset grunnlag»-varsel vises; dette er ikke påstått som et kjent logghull.
5. Trykk opp fra første rad til overskriften. Kort OK skal ikke åpne råloggen.
   Hold OK i tre sekunder: diagnoseskjermen åpnes med filterknappen i fokus. Kontroller
   filter, dagbytte og Kanal +/− som før. Tilbake gjenoppretter tidslinjens posisjon
   og sist valgte rad.
6. La «I dag» være åpen over midnatt. Dato, summer, stripe og rader skal bytte til ny
   dag uten å åpne appen igjen. En uttrykkelig valgt historisk dato skal stå fast.
7. Slå av én tillatelse i TV-innstillingene. Oppsett skal vises uten krasj, med første
   manglende tillatelsesknapp i fokus. Når begge er på, skal «I dag» åpnes.
8. Kontroller en dag med aktivitet over midnatt, avslag og nytt påslag om ettermiddagen:
   «Første gang slått på» skal vise 00:00. Vanlig standby før dagen skal ikke alene
   gi «Begrenset grunnlag»; skjermsparerens skjerm-av-signal er ikke et slikt grunnlag.

## Oppgave 4 – eksport og kompakt visning

1. Åpne appen: summer skal stå på én linje og minst 12 tidslinjerader skal få plass
   i 1080p. Nyeste periode står øverst. Test opp/ned, dagbytte, OK og tilbake.
2. Hold OK på overskriften i tre sekunder. Gå ned til «Eksporter nå», trykk OK og
   vent på «Eksportert hh:mm». Hent filene med `tools/hent-tvlogg.ps1` fra PC-en.
   Kontroller alle tre filer, lokal tidssone og 14 lokale dager inklusive i dag.
   Eksporter igjen: de samme filnavnene skal brukes, uten nummererte kopier.
3. Velg historikk fra før installasjonen: gule appperioder viser «avspilling ukjent».
   Åpne detaljer og kontroller forklaringen med dato for første lyttertilkobling.
   Ingen hullvarsler skal skyldes tiden før appen begynte å følge med.
4. Kontroller en nattlig omstart (også over to minutter) og en systemoppvåkning
   som avsluttes innen fem minutter, etterfulgt av vanlig bruk senere: nattperiodene
   står i listen, men «Første påslag» viser den vanlige bruken. Skjerm-på nær
   DEVICE_STARTUP skal aldri bli påslag.
5. La appen ligge i bakgrunnen minst et døgn. Hent filene igjen og kontroller
   eksporttidspunktet i diagnosefilen (WorkManager kan utsettes under standby).
