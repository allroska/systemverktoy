# Oppgaver til Codex – ferdige tekster å lime inn

Lim inn én oppgave om gangen i Codex (chatgpt.com/codex), med repoet `systemverktoy` valgt. Vent til PR-en er merget før du starter neste.

**Regel:** bare «Må fikses» stopper en merge, og maks én retterunde per PR. «Bør fikses» og «Kan vente» samles i neste oppgave.

Flyten for hver oppgave:
1. Lim inn oppgaven i Codex (nivå **Høy** for store oppgaver) → Codex lager en branch og åpner PR-en selv.
2. Claude reviewer PR-en automatisk (3–10 minutter).
3. Hvis Claude har funn: gå til **samme Codex-chat** som laget PR-en og be den rette funnene og pushe til samme branch (mal under). Claude reviewer på nytt automatisk.
   (`@codex` på GitHub gjør jobben i den eldre Codex Cloud, men pusher ikke av seg selv, og lenken «View task» virker ikke. Ikke bruk den.)
4. Når Claude sier «Kan merges: Ja»: klikk **Merge pull request** på GitHub.
5. Etter merge bygges en ny APK automatisk. Installer på TV-en via Downloader.

---

## Oppgave 1 – Grunnmur og første installerbare app

```
Les AGENTS.md og docs/SPEC.md.

Lag grunnmuren til prosjektet, uten selve logikken ennå:
1. Gradle-prosjekt for Android TV etter rammene i AGENTS.md (Kotlin, Compose for TV, minSdk 31, compileSdk 36, targetSdk 35, AGP 9.4.x, Gradle 9.6.0-wrapper, applicationId com.systemverktoy.app, appnavn «Systemverktøy»).
2. Manifest med leanback-feature, LEANBACK_LAUNCHER-activity og banner 320x180 slik docs/skisser/Banner.dc.html viser (mørk bakgrunn, hvitt tannhjul, teksten «Systemverktøy»). Ingen INTERNET-tillatelse.
3. Én enkel skjerm i mørkt tema (farger fra SPEC kap. 5) med overskriften «Systemverktøy» og versjonsnummeret, så eieren kan se at installasjonen virker.
4. .github/workflows/build.yml (PR: bygg debug + enhetstester + lint).
5. .github/workflows/release.yml (push til main: signert release-APK fra secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD → GitHub Release med tagg v<run_number> og asset nøyaktig systemverktoy.apk). Feil tydelig hvis secrets mangler.
6. .gitignore for Android.

Hold PR-en liten. PR-beskrivelse på norsk etter malen i AGENTS.md.
```

---

## Oppgave 2 – Testappen (fase 1)

```
Les AGENTS.md, docs/SPEC.md (særlig kap. 6 og 7, Fase 1), docs/RESEARCH.md og docs/TESTPLAN.md.

Bygg fase 1-testappen oppå grunnmuren:
1. Oppsettskjerm for de to tillatelsene (SPEC 5.4, skisse docs/skisser/Oppsett.dc.html). Appen skal aldri krasje når en tillatelse mangler.
2. Room-database for rå hendelser med kilde USAGE / MEDIA / DREAM / AUDIO / LISTENER / APP.
3. NotificationListenerService som:
   - logger onListenerConnected/onListenerDisconnected (og kaller requestRebind ved frakobling),
   - registrerer ACTION_DREAMING_STARTED/STOPPED og skjerm på/av dynamisk,
   - følger MediaSession-endringer for alle aktive sesjoner (pakke, status, tittel hvis den finnes),
   - logger media-lyd via AudioManager.registerAudioPlaybackCallback.
4. Kopiering av UsageEvents til databasen uten duplikater: når appen åpnes og periodisk med WorkManager. Ta med pakke OG activity-klasse.
5. Hovedskjerm = diagnoseboks + rå logg for valgt dag (SPEC kap. 7, Fase 1). Venstre/høyre bytter dag. Lesbart på et mobilbilde av TV-skjermen.
6. Enhetstester for duplikatsjekk og dagsgrenser (midnatt, sommertid).

Ingen klassifisering eller pene tidslinjer ennå – det kommer i fase 2 når vi vet hva TV-en logger.
PR-beskrivelse på norsk etter malen i AGENTS.md, med «Hvordan teste på TV-en» som peker til docs/TESTPLAN.md.
```

---

## Oppgave 3 – Den ferdige appen (fase 2)

Kan startes nå (TV-testen 11.11 er lagt inn i docs/RESEARCH.md og docs/TESTPLAN.md). Nivå **Høy**.

```
Les AGENTS.md, docs/SPEC.md (særlig kap. 4 versjon 2 og kap. 5), docs/RESEARCH.md («Kveldstest 11.11») og docs/TESTPLAN.md.

Bygg den ferdige appen (fase 2). Del opp i to PR-er:

PR A – logikk:
1. Ren Kotlin-TimelineBuilder som gjør rå hendelser fra databasen om til perioder med kategoriene og prioriteten i SPEC kap. 4 (versjon 2), inkludert HDMI, skjermsparer = IDLE (ikke OFF), 60-sekunders-regelen for korte PLAYING-perioder, støyfilteret og UNKNOWN ved manglende hjerteslag.
2. Enhetstester for hver regel, bygget på det faktiske hendelsesforløpet i docs/RESEARCH.md (Netflix, NRK, skjermsparer, HDMI 4).

PR B – skjermer (når PR A er merget):
3. Skjermene i SPEC kap. 5 etter skissene i docs/skisser/: I dag (oppsummering, døgnstripe, tidslinje), tidligere dager med hull-varsel, detaljer med «Grunnlag – rå signaler». Mørkt tema og høy kontrast som i dagens rålogg.
4. Rå logg beholdes som skjult diagnoseskjerm (hold OK på overskriften i 3 s).
5. «I dag» skal ikke stå fast over midnatt, og første element skal ha fokus når en skjerm åpnes.

Si fra under «Åpne spørsmål» hvis noe i SPEC ikke stemmer med dataene – ikke endre SPEC-en stille.
```

## Oppgave 4 – Eksport til PC (alternativ B)

Nivå **Middels**. Produktvalg 11.11: data skal fortsatt aldri sendes ut på nett (ingen `INTERNET`-tillatelse), men eieren skal kunne hente en fil til PC-en over hjemmenettet med `tools/hent-tvlogg.ps1`.

```
Les AGENTS.md og docs/SPEC.md (kap. 6 og 8). Legg til eksport av data til fil på TV-en, uten å legge til INTERNET-tillatelse eller nye nettverksbiblioteker:

1. Appen skriver eksportfiler til Download/Systemverktoy/ via MediaStore.Downloads (ingen lagringstillatelse trengs på Android 12). Filene overskrives hver gang:
   - systemverktoy-raa.csv: alle rå hendelser de siste 14 dagene (tid ISO 8601 med offset, kilde, type, pakke, activity-klasse, status, tittel, detaljer).
   - systemverktoy-tidslinje.csv: perioder fra TimelineBuilder for de siste 14 dagene (dag, start, slutt, kategori, app, varighet i sekunder, grunnlag).
   - systemverktoy-diagnose.txt: innholdet i diagnoseboksen + appversjon + eksporttidspunkt.
2. Eksport skjer automatisk når appen åpnes, og én gang i døgnet via WorkManager. I diagnoseskjermen (skjult rålogg): knapp «Eksporter nå» med bekreftelse «Eksportert hh:mm».
3. Feil ved eksport skal aldri krasje appen; logg APP-hendelsen EXPORT_FAILED.
4. Enhetstester for CSV-formatet (escaping av komma/anførselstegn, tidssone).
5. Oppdater README med kort om eksport og tools/hent-tvlogg.ps1.
6. Tidslinje før lytteren fantes: perioder før første onListenerConnected noensinne (f.eks. historikk hentet fra Android fra før appen ble installert) har ingen avspillingsdata. De skal ikke vises som «App åpen, ingenting spilt», men som «App åpen – avspilling ukjent» (egen undertekst, samme gule farge, teller ikke i «Sett på»). Detaljskjermen skal forklare: «Appen fulgte ikke med på avspilling før <dato kl>.» Test for dette.
7. «Ufullstendig dag» skal ikke liste hull fra før første hjerteslag/onListenerConnected noensinne (appen fantes ikke da). Vis i stedet én linje: «Appen fulgte med fra <dato kl>.» Hull på 0 minutter (f.eks. «13:09 – 13:09») skal aldri vises. Tester for begge.
8. Ny, kompakt layout på «I dag» etter skissen docs/skisser/Kompakt.png (kilde Kompakt.html) og SPEC 5.1. Erstatt dagens topp (stor overskrift, tre bokser, stor oransje varselramme):
   - Linje 1 (11 sp, dempet): «I dag · onsdag 11. november» til venstre, «← / → dag · ↑ / ↓ rad · OK detaljer» til høyre.
   - Linje 2 (14 sp, tall i fet skrift): «Sett på 7 min (Netflix 4 · NRK TV 3) | Sto bare på < 1 min | HDMI 4 t 35 min | Første påslag 17:22». Ingen bokser.
   - Linje 3 (11 sp, kun når aktuelt): «Appen fulgte med fra kl. 17:15» eller «Ufullstendig: 2 hull, 1 t 10 min» (dempet oransje). Ingen ramme.
   - Døgnstripe 8 dp høy med små timetall under.
   - Tidslinje-rader: én linje hver, ca. 28 dp høye, 13 sp. Fokusert rad: hvit ramme.
   Compose-UI-test at minst 12 rader er synlige i 960×540 dp.
9. «Første gang slått på» skal ikke telle TV-ens egne oppvåkninger. Androids logg 11.11 viser at 04:48 var en automatisk omstart: DEVICE_SHUTDOWN 04:48:12 → SCREEN_INTERACTIVE + com.android.tv.settings/.system.FallbackHome 04:48:47 → DEVICE_STARTUP → launcher HomeActivity/ProfileChooserActivity → SCREEN_NON_INTERACTIVE 04:50:56 (over 2 min!). Regel: en skjerm-på-periode teller ikke som påslag hvis den starter innen 1 min etter DEVICE_STARTUP, eller hvis bare systempakker (com.android.tv.settings, launcher-klassene FallbackHome/DispatchActivity/HomeActivity/ProfileChooserActivity, Sony-systempakker) er i forgrunnen og skjermen går av igjen innen 5 min. Periodene vises fortsatt i tidslinjen. Tester for begge, med eksempelet over.
```

---

## Oppgave 5 – Oversikt, PIN og rettelser

Nivå **Middels**. Produktvalg 11.11: ny startskjerm som bare viser TV-titting, PIN-kode, og riktig «første påslag».

```
Les AGENTS.md og docs/SPEC.md (særlig 5.0, 5.1 og 5.6). Gjør dette:

1. Ny startskjerm «Oversikt» etter SPEC 5.0 og skissen docs/skisser/Oversikt.png (kilde Oversikt.html):
   - Stort tall «TV-titting i dag» (bare WATCHING), «N økter · første påslag hh:mm».
   - Én rad per økt, eldste øverst: tidsrom, appnavn, minutter sett. Ingen «av N»-tekst.
   - Streken teller opp mot dagens total i én farge: rad i fyller fra 0 til (sum av økt 1..i) / total. Siste rad er full.
   - Økt-regel (ren Kotlin, f.eks. SessionBuilder): perioder fra samme pakke slås sammen når avbruddene er ≤ 5 min og det ikke er avspilling i en annen app imellom. Vis bare økter med ≥ 1 min WATCHING. HDMI/TV-kanaler er ikke økter, bare sum i bunnlinjen sammen med «Sto bare på».
   - Økter før appen fulgte med: grå, «avspilling ukjent», teller ikke.
   - OK → detaljer for hele økten. Ned fra siste rad → dagens detaljerte tidslinje (dagens «I dag»-skjerm). Tilbake: detaljer → forrige skjerm, tidslinje → Oversikt, Oversikt → lukk. Venstre/høyre bytter dag.
   - Tester: økt-sammenslåing (pause og kort tur til startskjerm innenfor samme økt, ny økt etter > 5 min, avspilling i annen app avslutter økten), kumulativ strek, tom dag.
2. PIN-kode etter SPEC 5.6: velg PIN første gang, tallknapper + piltaster, prikker, 5 feil → 1 min pause, låses når appen går i bakgrunnen, lagres som saltet hash. Nullstilling bare fra adb via mottaker beskyttet med android.permission.DUMP. Diagnoseskjermen (hold OK) ligger også bak PIN. Ingen nye tillatelser, ingen INTERNET.
3. «Første påslag»: hvis TV-en var på ved midnatt (skjerm på / app i forgrunnen fra før 00:00), skal «Første påslag» være første gang skjermen ble slått på igjen etter å ha vært av, ikke 00:00. Eksempel 11.11: på fra før midnatt → standby 04:38 → omstart 04:48–04:50 (teller ikke, punkt 9 i Oppgave 4) → på 08:44. Svaret skal være 08:44. Hvis TV-en aldri var av hele dagen: vis «på fra i går». Test for dette.
4. Fra review av PR #6:
   a. firstMeaningfulOn: DREAMING_STOPPED uten samtidig skjerm-på mens skjermen er av = avslag (som i TimelineBuilder). Test: omstart → skjermsparer → standby → vanlig bruk 07:00 gir første påslag 07:00.
   b. DataExport.write: ta med pending-rader i MediaStore-søket; etter insert, sjekk at DISPLAY_NAME ikke er endret (f.eks. «… (1).csv»); hvis endret: slett raden og logg EXPORT_FAILED. Utvid DataExportTest.
5. tools/hent-tvlogg.ps1: lagre som UTF-8 med BOM, så æøå vises riktig i Windows PowerShell 5.1. Ingen andre endringer i skriptet.
6. Oppdater README kort (PIN og nullstilling).

Hvis PR-en blir over ca. 800 linjer: lag gjerne to PR-er (først punkt 3–5, så 1–2).
```

---

## Oppgave 6 – Ukesoversikt og to små rettelser

Nivå **Middels**. Produktvalg 13.11: ukesoversikt (variant C4 fra skisserunden), alltid mandag–søndag.

```
Les AGENTS.md og docs/SPEC.md (særlig 5.0 og 5.7). Gjør dette:

1. Ny skjerm «Ukesoversikt» etter SPEC 5.7 og skissen docs/skisser/Ukesoversikt.png (kilde Ukesoversikt.html):
   - Uke = mandag 00:00 til søndag 24:00 lokal tid (systemets tidssone), ISO-ukenummer. Bruk java.time, ingen hardkodet UTC-offset.
   - Toppfelt venstre: snitt per dag denne uka og forrige uke (bare tall, ingen streker), og linjen «N min mindre/mer per dag enn forrige uke · totalt X så langt». Snitt = ukens TV-titting / antall dager i uka som har startet (i dag teller med); forrige uke / 7. Dager før appen fulgte med telles ikke. Mangler forrige uke: «Forrige uke –» og ingen sammenligningslinje.
   - Toppfelt høyre: «Andel av TV-tittingen denne uka», én linje per app (blå prikk, navn, prosent, tid), mest først, ingen streker. Maks 4 linjer: 5 apper eller flere → de 3 største + «Andre (N apper) ›» (grå prikk) med summen av resten. Under: «HDMI / TV-kanaler <sum>». Ikke «Sto bare på».
   - Døgnkart: 7 rader (man–søn) × 24 ruter (klokketimer 00–23), timetall over hver kolonne, dagens sum til høyre. Rutefarge: blå hvis ≥ 1 min WATCHING i timen, ellers lilla hvis ≥ 1 min HDMI, ellers oransje skravert hvis ≥ 1 min UNKNOWN, ellers tom. Timer/dager som ikke har kommet ennå: bare tynn ramme, sum «–». Sommertid: se SPEC 5.7.
   - Tallene skal stemme med Oversikt: dagssummen er nøyaktig samme tall som Oversikt viser for dagen, ukens total = summen av dagssummene, og appenes minutter fordeles slik at de summerer til ukens total.
   - Fjernkontroll: ↑ fra første rad i Oversikt (eller tom dag) åpner Ukesoversikt for uka dagen hører til. ↑ / ↓ flytter mellom dagene, OK → Oversikt for den dagen, ← / → forrige/neste uke (ikke etter denne uka, ikke før første data). Finnes «Andre»: ↑ fra mandag flytter fokus dit, OK viser enkel liste med alle ukens apper (navn, prosent, tid). Tilbake: liste → Ukesoversikt → Oversikt. Hvit fokusramme, alt med D-pad. Oppdater hint-teksten i Oversikts topplinje («↑ uke»).
   - Beregningen som ren Kotlin (f.eks. WeekSummaryBuilder) som bygger på den samme dagsberegningen som Oversikt bruker. Tester: ukegrense søndag→mandag ved midnatt, uke med sommertid (både mars og oktober), snitt midt i uka, dager før appen fulgte med, «Andre»-regelen (4 apper = 4 linjer, 5 apper = 3 + Andre), rutefarge-prioritet, avrunding som summerer riktig, tom uke.
2. Avrunding i Oversikt (review PR #7, «Kan vente» 2): radene summerer seg ikke alltid til totalen fordi hver rad rundes ned for seg. Regn fra sekunder: totalen = avrundet sum, og fordel minuttene på radene slik at de summerer nøyaktig til totalen (største rest først). Bruk samme hjelpefunksjon i Ukesoversikt. Test for dette.
3. PIN-skjermen (review PR #7, «Kan vente» 1, PinGate.kt ca. linje 95–98): tøm meldingen («Tast alle fire sifrene.», «Feil PIN-kode.») når et nytt siffer tastes og når koden godtas.

Ingen nye tillatelser, ingen INTERNET, ingen endring i applicationId eller signering. Hvis PR-en blir over ca. 800 linjer: lag gjerne to PR-er (først punkt 2–3, så 1).
```

---

## Senere (bestemt, ikke startet)

- **Sjekke hjemmefra:** produktvalg ikke tatt ennå. Appen skal fortsatt ikke ha `INTERNET`.
- **Skjermsparer med bilder og Ruter-avganger:** egen app, ikke en del av Systemverktøy. Tas en annen gang.
- **Lyd uten bilde** (f.eks. Spotify Connect med skjermen av, sett 12.11): produktvalg ikke tatt ennå.

---

## Maler

**Rette review-funn** (i samme Codex-chat som laget PR-en):
```
Claude-reviewen på PR #<nr> har funn. Rett «Må fikses» og det som er rimelig under «Bør fikses», og push til samme branch – ikke lag ny PR. Les Claude-kommentarene på PR-en for detaljer.
```

**Feil funnet på TV-en** (ny oppgave i Codex, legg ved bilde):
```
Les AGENTS.md og docs/SPEC.md. På TV-en skjer dette: <beskriv hva du gjorde og hva du så>. Forventet: <hva du forventet>. Finn årsaken og rett det i en ny PR.
```

**Spørre Claude om noe i en PR** (kommentar på PR-en):
```
@claude forklar på enkel norsk hva denne endringen gjør, og hva jeg bør teste på TV-en
```

---

## Valgfritt: oppsettskript for Codex-miljøet

Gjør at Codex kan bygge Android-appen selv før den pusher. Lim inn under **Environment → Setup script** i Codex. Hvis det feiler, kan du droppe det: GitHub Actions bygger uansett, og du kan skrive `@codex fix the CI failures` på PR-en.

```bash
set -e
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd /tmp
curl -sSLo cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "export ANDROID_HOME=$ANDROID_HOME" >> "$HOME/.bashrc"
```

---

## Kommandoer i stedet for å klikke på GitHub (PowerShell)

Engangsoppsett: `winget install --id GitHub.cli -e`, åpne nytt vindu, `gh auth login` (GitHub.com → HTTPS → Login with a web browser).

```powershell
$R = "allroska/systemverktoy"

gh pr list -R $R                               # åpne PR-er
gh pr view <nr> -R $R --comments               # les Claude-reviewen
gh pr checks <nr> -R $R --watch                # vent på bygg + review
gh pr merge <nr> -R $R --merge --delete-branch # merge
gh pr close <nr> -R $R; gh pr reopen <nr> -R $R  # start review på nytt
gh run watch -R $R                             # følg byggingen av APK
gh release view -R $R                          # nyeste versjon
```
