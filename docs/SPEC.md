# Systemverktøy – spesifikasjon

Dette dokumentet beskriver **hva** som skal bygges og **hvorfor**. `AGENTS.md` beskriver **hvordan** det skal bygges.
Ved konflikt mellom koden og dette dokumentet er det dette dokumentet som gjelder, til eieren endrer det.

---

## 1. Formål

En liten privat app for en Android TV som lar eieren se **om TV-en har vært brukt i dag, når, til hva, og om noe faktisk ble spilt av** – eller om TV-en bare sto på.

Brukssituasjon: Eieren tar fjernkontrollen, åpner appen fra TV-ens startskjerm og ser svaret på første skjerm på under ett minutt. Ingen menyer å lete i.

## 2. Målenheten

| Felt | Verdi |
|---|---|
| TV | Sony BRAVIA, plattform «VH22» (Sony Google TV-plattform fra 2022) |
| OS | Android TV OS 12 (API 32), Google TV-grensesnitt |
| Bekreftet på TV-en | Menyene «Brukstilgang» og «Tilgang til varsler» finnes under Innstillinger → Apper → Spesiell apptilgang |
| Installasjon | Sideload via appen «Downloader» fra en fast GitHub Releases-lenke |

## 3. Produktbeslutninger (tatt av eier)

| Beslutning | Valg | Konsekvens for koden |
|---|---|---|
| Hvilken periode vises | **Hele dagen** (00:00–24:00 lokal tid). Brukeren tolker selv klokkeslettene. | Ingen innstilling for tidsgrenser i visningen. Dagens sum inkluderer også kveldsbruk. |
| Startskjerm/meny-tid | **Teller ikke** som «sett på». Vises i tidslinjen, men holdes utenfor summen. | Egen kategori `HOME`. |
| Navn og ikon | **Nøytralt**: «Systemverktøy» med tannhjul-ikon. | `app_name = Systemverktøy`, banner 320×180 med tannhjul. Ingen ord som «logg», «overvåking», «skjermtid» synlig i launcher. |
| Avspillingssjekk | **Med i versjon 1** | NotificationListenerService + MediaSession. |
| Titler (hva som ble sett) | **Ikke lovet.** Vises bare hvis appen faktisk sender tittel. | Netflix sender ikke tittel. Vis tittel når `MediaMetadata` har den, ellers ingenting. |
| Data ut av TV-en | **Aldri.** | Ingen `INTERNET`-tillatelse. Ingen analytics, crash-rapportering eller nettverkskall. |
| Mobilvisning | **Ikke i v1** | – |

## 4. Kategorier

Hver tidsperiode i løpet av dagen får nøyaktig én kategori:

| Kategori | Visningsnavn | Farge | Teller i «Sett på»? |
|---|---|---|---|
| `WATCHING` | Sett på | Blå `#4E95F0` | Ja |
| `APP_OPEN` | App åpen, ingenting spilt / App åpen, pauset | Gul `#F7C266` | Nei |
| `IDLE` | Sto bare på | Grå `#56616C` | Nei (egen sum) |
| `HOME` | Startskjerm – teller ikke | Lys grå `#9AA4AF`, stiplet chip | Nei |
| `UNKNOWN` | Ukjent – mangler logg | Oransje `#E8875F`, skravert | Nei |
| `OFF` | (vises ikke som periode) | – | – |

### Klassifiseringsregler (versjon 1 – justeres etter testappen)

Bygges fra tre uavhengige signaler: **forgrunnsapp** (UsageEvents), **avspillingsstatus** (MediaSession) og **skjermsparer** (`ACTION_DREAMING_STARTED/STOPPED`). Lydavspilling (`AudioManager.registerAudioPlaybackCallback`) er reservesignal.

1. `OFF`: mellom `SCREEN_NON_INTERACTIVE` og `SCREEN_INTERACTIVE`, eller mellom `DEVICE_SHUTDOWN` og `DEVICE_STARTUP`.
2. `IDLE`: skjermsparer aktiv (kringkasting mottatt, eller skjermsparer-pakken har en åpen activity i UsageEvents).
3. `HOME`: launcheren (Google TV-hjemmeskjermen) er i forgrunnen. Lyd fra trailere på hjemmeskjermen ignoreres.
4. `WATCHING`: en annen app enn launcheren er i forgrunnen **og** den samme appen har en MediaSession med `STATE_PLAYING` (eller, som reserve, media-lyd er aktiv mens appen er i forgrunnen).
5. `APP_OPEN`: en app er i forgrunnen uten avspilling (pauset, stoppet, ingen sesjon).
6. `UNKNOWN`: hull der appen vet at den ikke har data (f.eks. lytteren var frakoblet, eller logg mangler mellom to tidspunkt der TV-en var på).

Kjent felle: Netflix melder «spiller» allerede på sin egen startside når autoforhåndsvisning er på. Husstanden har flere Netflix-profiler, så autoforhåndsvisning **kan ikke** forutsettes avslått. Klassifiseringen må selv skille meny-forhåndsvisning fra ekte avspilling, f.eks. ved å kreve at Netflix sin avspiller-activity er i forgrunnen (activity-klassenavn logges i fase 1).

Kjent felle: Økter må spores per activity (pakke + klasse), ikke per pakke. En sen `ACTIVITY_STOPPED` fra én activity i en app skal ikke avslutte en annen activity i samme app.

## 5. Skjermer

Referanseskisser ligger i `docs/skisser/` (HTML). De viser layout, tekster og farger med oppdiktede eksempeldata. Skjermene er 16:9 og skal fungere fra 1280×720 til 1920×1080.

### 5.1 I dag (hovedskjerm) – `docs/skisser/Main.dc.html`
- Overskrift «I dag» + dato («onsdag 11. november»).
- Tre oppsummeringsbokser: **Sett på** (sum + fordeling per app, f.eks. «Netflix 1 t 15 · YouTube 20 min»), **Sto bare på** (sum), **Første gang slått på** (klokkeslett + «Klokka er nå …»).
- En horisontal døgnstripe 06–24 med farget periode per kategori og en hvit strek for «nå».
- Tidslinje-liste, én rad per periode: tidsrom, fargeprikk, appnavn, kategori-chip, varighet. Pågående periode viser «pågår».
- Tom dag: «TV-en har ikke vært slått på i dag.»

### 5.2 Detaljer for én økt – `docs/skisser/Detalj.dc.html`
Åpnes med OK på en rad. Viser for én app-økt:
- Stripe som viser fordelingen åpen / spilte av / pauset.
- Liste «Hva skjedde».
- Boks «Hva ble sett?»: tittel hvis kjent, ellers forklaring om at appen ikke oppgir tittel, med tips om appens egen seerhistorikk.
- Liste «Grunnlag – rå signaler» med klokkeslett og kilde (App-logg / Avspilling / Skjermsparer). Dette er sporbarheten: brukeren skal kunne se hvorfor appen konkluderte som den gjorde.

### 5.3 Tidligere dager – `docs/skisser/Igaar.dc.html`
- Samme layout som «I dag», for en tidligere dag.
- Hvis dagen har hull i loggen: et tydelig varsel («Ufullstendig dag: ingen logg mellom 15:50 og 16:30 …») og en `UNKNOWN`-rad. Appen gjetter aldri over hull.

### 5.4 Første oppstart – `docs/skisser/Oppsett.dc.html`
- Vises når en av tillatelsene mangler.
- To rader: **Brukstilgang** og **Tilgang til varsler**, hver med status (Slått på / knapp «Åpne innstillinger»).
- Knappen åpner riktig innstillingsskjerm (`Settings.ACTION_USAGE_ACCESS_SETTINGS`, `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`). Faller tilbake til generelle innstillinger hvis intenten ikke finnes.
- Tekst: «Ingenting sendes ut av TV-en. All logg lagres bare her.»
- Når begge er på: gå rett til «I dag».

### 5.5 Ikon – `docs/skisser/Banner.dc.html`
- Banner 320×180: mørk bakgrunn `#2B3640`, hvitt tannhjul, teksten «Systemverktøy».

### Navigasjon med fjernkontroll
| Tast | Handling |
|---|---|
| Venstre / høyre | Forrige / neste dag (ikke lenger enn i dag) |
| Opp / ned | Flytt fokus i tidslinjen |
| OK | Åpne detaljer for raden |
| Tilbake | Fra detaljer → tidslinje. Fra tidslinje → lukk appen |

Fokusert rad har tydelig hvit ramme. Alt skal kunne brukes uten berøring.

### Visuell stil
Mørkt tema: bakgrunn `#0F1317`, flater `#182028`, fokusflate `#212B35`, tekst `#EEF1F4`, dempet tekst `#A3ADB8`. Skrift: Inter eller systemets sans-serif. Ingen emoji. Tekst minst 15 sp på TV.

## 6. Data

- Appen leser Androids hendelseslogg (`UsageStatsManager.queryEvents`). Den detaljerte loggen slettes av Android etter ca. 10 dager, så appen må **kopiere hendelser til sin egen database jevnlig**: når appen åpnes, periodisk i bakgrunnen og fra varsellytter-tjenesten.
- Avspillings- og skjermsparer-hendelser finnes bare i appens egen database (fra installasjonsdagen).
- Rådata lagres; perioder/kategorier beregnes fra rådata når en dag vises, slik at klassifiseringsregler kan forbedres uten å miste historikk.
- Behold historikk i minst 60 dager.
- Dager følger lokal tid (`Europe/Oslo` via systemets tidssone), inkludert sommertid-overganger.

## 7. Faser

### Fase 1 – Testapp («diagnose»)
Formål: finne ut hva denne TV-en faktisk logger, før klassifiseringen bygges. Se `docs/TESTPLAN.md`.

Krav:
- Samme navn, ikon, applicationId og signering som den ferdige appen (så fase 2 kan installeres oppå uten å miste data).
- Oppsettskjermen (5.4).
- En **diagnoseboks** øverst: status for begge tillatelser, om varsellytteren er tilkoblet nå, tidspunkt for siste hjerteslag, om appen er unntatt fra batterioptimalisering, valgt skjermsparer-komponent (`Settings.Secure` `screensaver_components`), launcher-pakke, eldste hendelse i Androids logg, antall rader i egen database.
- En **rå logg** for valgt dag, nyeste øverst, én rad per hendelse: klokkeslett (sekunder), kilde (`USAGE` / `MEDIA` / `DREAM` / `AUDIO` / `LISTENER` / `APP`), pakkenavn, activity-klasse der den finnes, hendelsestype, og for MEDIA: status og eventuell tittel.
- Venstre/høyre bytter dag. Teksten må være lesbar på et fotografi tatt av TV-skjermen.
- Logging skal skje kontinuerlig i bakgrunnen, ikke bare når appen er åpen. Tre tiltak for å holde den i gang og gjøre bortfall synlig: **hjerteslag** ca. hvert 15. minutt mens skjermen er på, **start ved oppstart** av TV-en, og **visning av strømsparingsstatus** (se AGENTS.md, «Bakgrunn»).

### Fase 2 – Ferdig app
Skjermene i kapittel 5 med klassifiseringen i kapittel 4, justert etter funnene fra fase 1. Rå logg beholdes som en skjult diagnoseskjerm (f.eks. holde OK inne på overskriften i 3 sekunder).

## 8. Ikke i versjon 1
Blokkering, tidsgrenser, PIN, varsler, mobilvisning, sky-synk, eksport, flere brukere/profiler.

## 9. Kjente begrensninger (skal være synlige for brukeren der det er relevant)
- «Sett på» betyr at innhold ble spilt av, ikke at noen satt foran skjermen (autoplay).
- Tid på HDMI-innganger (f.eks. en spillkonsoll) og vanlige TV-kanaler vises sannsynligvis som Sonys TV-app. Må bekreftes i fase 1.
- Hvis TV-en mister strøm eller lytteren faller ut, vises hullet som «Ukjent».
