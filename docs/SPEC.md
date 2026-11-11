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
| `HDMI` | HDMI 1–4 (konsoll o.l.) | Lilla `#9B8AF0` | Nei (egen sum «HDMI») |
| `OFF` | (vises ikke som periode) | – | – |

### Klassifiseringsregler (versjon 2 – basert på TV-testen 11.11, se docs/RESEARCH.md)

Signaler: **forgrunnsapp** (UsageEvents), **avspillingsstatus** (MediaSession), **skjermsparer** (DREAM + `dreamx/DreamActivity`), **skjerm av/på**, **hjerteslag** og **lytter tilkoblet/frakoblet**. AUDIO (`media_active`) er reservesignal for apper uten MediaSession.

Prioritet når flere regler treffer samtidig: `UNKNOWN` > `IDLE` > `OFF` > `HDMI` > `WATCHING` > `HOME` > `APP_OPEN`.

1. `IDLE`: fra `DREAMING_STARTED` (eller `dreamx … DreamActivity` RESUMED) til `DREAMING_STOPPED` (eller `SCREEN_INTERACTIVE`). **Skjermspareren gir også `SCREEN_NON_INTERACTIVE` – det skal tolkes som IDLE, ikke OFF.**
2. `OFF`: `SCREEN_NON_INTERACTIVE` uten aktiv skjermsparer, til `SCREEN_INTERACTIVE`; eller `DEVICE_SHUTDOWN` → `DEVICE_STARTUP`/`BOOT_COMPLETED`. Vises ikke som periode.
3. `HDMI`: `com.sony.dtv.tvx` i forgrunnen. Visningsnavn tas fra MediaSession-tittelen («HDMI 4»). Telles i egen sum, ikke i «Sett på» (konsollen sier ikke om noe spilles).
4. `WATCHING`: en app (ikke launcher, ikke tvx) i forgrunnen **og** samme app har MediaSession `STATE_PLAYING` (eller `STATE_BUFFERING` rett før `PLAYING`). Reserve uten MediaSession: AUDIO `media_active=1` mens appen er i forgrunnen.
   - **Netflix bruker samme `MainActivity` for meny og avspiller** – activity-klasse kan ikke brukes. MediaSession-status avgjør.
   - Forhåndsvisninger (andre Netflix-profiler kan ha dem på): sammenhengende `PLAYING`-perioder **kortere enn 60 sekunder** telles som `APP_OPEN`, ikke `WATCHING`.
   - **Korte avbrudd slås sammen:** bufring eller annet avbrudd i avspillingen som varer **under 30 sekunder** i samme app teller som én sammenhengende `WATCHING`-periode (produktvalg 11.11). Sammenslåingen skjer før 60-sekundersregelen.
5. `HOME`: `com.google.android.apps.tv.launcherx` i forgrunnen. Lyd/trailere der ignoreres.
6. `APP_OPEN`: annen app i forgrunnen uten avspilling (pauset, stoppet, ingen sesjon). Innstillinger, Downloader o.l. vises med appnavn. **Unntak:** før første `onListenerConnected` noensinne finnes ingen avspillingsdata – da vises «App åpen – avspilling ukjent», ikke «ingenting spilt».
7. `UNKNOWN`: skjermen er på, men det mangler hjerteslag i mer enn 35 minutter, eller lytteren er frakoblet. Appen gjetter aldri over hull.

Støy som ignoreres i tidslinjen (beholdes i rå logg): USAGE `EVENT_11`, `EVENT_12`, `EVENT_14` og andre hendelsestyper enn ACTIVITY_*/SCREEN_*/DEVICE_*; pakkene `android`, `com.android.systemui`, `com.google.android.gsf`, `com.google.android.tungsten.setupwraith`, `com.sony.dtv.*` bortsett fra `tvx`.

Kjent felle: Økter må spores per activity (pakke + klasse), ikke per pakke. En sen `ACTIVITY_STOPPED` fra én activity i en app skal ikke avslutte en annen activity i samme app.

## 5. Skjermer

Referanseskisser ligger i `docs/skisser/` (HTML). De viser layout, tekster og farger med oppdiktede eksempeldata. Skjermene er 16:9 og skal fungere fra 1280×720 til 1920×1080.

### 5.1 I dag (hovedskjerm) – `docs/skisser/Kompakt.png` (kilde: `Kompakt.html`)
Kompakt layout (eier 11.11): tidslinjen skal ta mesteparten av skjermen. Målt i 960×540 dp:
- **Linje 1** (11 sp, dempet): «I dag · onsdag 11. november» til venstre, «← / → dag · ↑ / ↓ rad · OK detaljer» til høyre. Ingen stor overskrift.
- **Linje 2** (14 sp, tall i fet skrift): «Sett på 7 min (Netflix 4 · NRK TV 3) | Sto bare på < 1 min | HDMI 4 t 35 min | Første påslag 17:22», med liten fargefirkant foran hver sum. Ingen bokser.
- **Linje 3** (11 sp, kun når aktuelt): «Appen fulgte med fra kl. 17:15» (dempet), eller «Ufullstendig: 2 hull, 1 t 10 min» (dempet oransje). Ingen stor varselramme; detaljene ligger i detaljer/rålogg.
- **Døgnstripe** 06–24, 8 dp høy, med små timetall under og hvit strek for «nå».
- **Tidslinje-liste**, nyeste øverst, én linje per periode, ca. 28 dp høy, 13 sp: tidsrom, fargeprikk, appnavn, kategori-chip, varighet. Pågående periode viser «–nå» og «pågår». Minst 12 rader synlige på 1920×1080.
- Tom dag: «TV-en har ikke vært slått på i dag.»
- Den gamle skissen `Main.dc.html` (store bokser) gjelder ikke lenger for layout, bare for farger og kategorinavn.

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
Mørkt tema: bakgrunn `#0F1317`, flater `#182028`, fokusflate `#212B35`, tekst `#EEF1F4`, dempet tekst `#A3ADB8`. Skrift: Inter eller systemets sans-serif. Ingen emoji. Tekst minst 13 sp i lister og summer, minst 11 sp for hjelpetekst (65" 4K-skjerm, eier 11.11).

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
- Råloggen vises kompakt på mørk bakgrunn (`#0F1317`) med hvit tekst og uten lyse bokser. Én linje per hendelse: klokkeslett · kilde · type · pakke · kort activity-klasse/status/tittel. Minst 15 rader skal få plass per skjerm; lange linjer avkortes med «…».
- Listen går til toppen når appen åpnes, dagen eller filteret endres, eller nye hendelser kommer. Nyeste hendelse vises først.
- Knappen «Bare viktige» filtrerer kun visningen: skjuler launcherens og Systemverktøys egne app-hendelser, men beholder MEDIA for andre apper, DREAM, AUDIO, LISTENER, skjerm av/på og USAGE for andre apper. APP-hendelsene `BOOT_COMPLETED`, `IMPORT_FAILED`, `USAGE_ACCESS_REVOKED` og `heartbeat` beholdes også, med eller uten eget pakkenavn, slik at oppstart, feil og hjerteslag fortsatt forklarer mulige hull i loggen. `APP_OPENED` skjules. Globale signaler beholdes også uten pakkenavn. Filteret er av i utgangspunktet.
- Kanal opp/ned blar én synlig loggside om gangen. Opp/ned blar én rad når listen har fokus; opp fra toppen går til filterknappen. Venstre/høyre bytter fortsatt dag.
- Logging skal skje kontinuerlig i bakgrunnen, ikke bare når appen er åpen. Tre tiltak for å holde den i gang og gjøre bortfall synlig: **hjerteslag** ca. hvert 15. minutt mens skjermen er på, **start ved oppstart** av TV-en, og **visning av strømsparingsstatus** (se AGENTS.md, «Bakgrunn»).

### Fase 2 – Ferdig app
Skjermene i kapittel 5 med klassifiseringen i kapittel 4, justert etter funnene fra fase 1. Rå logg beholdes som en skjult diagnoseskjerm (f.eks. holde OK inne på overskriften i 3 sekunder).

## 8. Ikke i versjon 1
Blokkering, tidsgrenser, varsler, mobilvisning, sky-synk, flere brukere/profiler. (PIN er bestemt for en senere oppgave. Eksport til PC over hjemmenettet kommer i Oppgave 4.)

## 9. Kjente begrensninger (skal være synlige for brukeren der det er relevant)
- «Sett på» betyr at innhold ble spilt av, ikke at noen satt foran skjermen (autoplay).
- Tid på HDMI-innganger (f.eks. en spillkonsoll) og vanlige TV-kanaler vises sannsynligvis som Sonys TV-app. Må bekreftes i fase 1.
- Hvis TV-en mister strøm eller lytteren faller ut, vises hullet som «Ukjent».
