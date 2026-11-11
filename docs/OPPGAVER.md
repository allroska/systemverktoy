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
