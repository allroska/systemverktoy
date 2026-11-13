# Status og overlevering

Denne filen er for Claude når eieren jobber med prosjektet lokalt i **Claude Code** (på egen PC), ikke for GitHub-reviewen. Den oppsummerer hvor prosjektet står, hvordan vi jobber, og hva som gjenstår. Oppdater den når noe viktig endres.

Sist oppdatert: 13.11.2026.

## Din rolle lokalt

- Du er **prosjektleder og hjelper**, ikke den som skriver appkoden. **Codex** skriver koden (egen branch → PR). **Claude-reviewen** på GitHub (`CLAUDE.md` + `.github/workflows/claude-review.yml`) reviewer automatisk.
- Du: skriver oppgavetekster i `docs/OPPGAVER.md`, oppdaterer `docs/SPEC.md` etter eierens produktvalg, lager skisser i `docs/skisser/`, leser PR-er og CI, hjelper med `gh`/`adb`/PowerShell, og analyserer eksportfilene fra TV-en.
- Små rettelser i dokumentasjon kan du committe og pushe rett til `main` (de utløser ikke ny app-versjon). Kode endres via Codex, med mindre eieren eksplisitt ber deg gjøre det.
- **Eieren er ikke programmerer**, men sterk på produkt og analyse. Svar på norsk, kort og konkret, steg for steg («klikk for klikk»). Forklar tekniske ord kort hver gang. Gi kommandoer som kan limes inn i PowerShell, én om gangen. Skill tydelig mellom produktvalg (eieren bestemmer) og tekniske valg.
- Repoet er **offentlig**: ingen personopplysninger, navn, IP-adresser, PIN-koder eller nøkler i filer, commits eller PR-er.

## Hva appen er

Privat, helt lokal Android TV-app («Systemverktøy») for en Sony BRAVIA med Google TV (Android TV 12). Viser hva TV-en ble brukt til i løpet av dagen, og skiller «sett på» (faktisk avspilling) fra «sto bare på» (skjermsparer) og «app åpen». Ingen `INTERNET`-tillatelse; ingen data forlater TV-en. Se `docs/SPEC.md`.

## Hvor vi står

| | |
|---|---|
| Installert versjon | 1.0.15 (Release fra PR #7, merget 11.11) |
| Ferdig | Logging i bakgrunnen, klassifisering v2 (SPEC kap. 4), kompakt detaljert tidslinje (5.1), **Oversikt** som startskjerm (5.0), **PIN** (5.6), fileksport til PC (Oppgave 4), riktig «Første påslag» etter omstart/over midnatt |
| Bekreftet på TV | Netflix og NRK TV melder avspilling via MediaSession. Skjermsparer, HDMI 4 (Sony TV-appen), automatisk omstart (tidspunktet varierer). Oversikt-tallet for 12.11 stemmer med rådata. Se `docs/RESEARCH.md` |
| Ikke testet ennå | Avspilling i TV 2 Play, Prime, Viaplay, Disney+. PIN på selve TV-en (`docs/TESTPLAN.md`) |
| Neste naturlige steg | Review og TV-test av Oppgave 6: Ukesoversikt, avrunding og PIN-meldinger (se `docs/TESTPLAN.md`) |

Oppgave 6 (denne PR-en): Ukesoversikt mandag–søndag med snitt, appfordeling og
24-timers døgnkart. Opp fra første økt/tom Oversikt åpner uka. Økter og appfordeling
fordeler rest-minutter slik at radene summerer riktig; PIN-feilmeldinger tømmes ved
ny inntasting. TV-test gjenstår, se «Oppgave 6» i `docs/TESTPLAN.md`.

## Arbeidsflyt (én oppgave = én PR)

1. Du skriver/oppdaterer oppgaven i `docs/OPPGAVER.md` og pusher til `main`.
2. Eieren starter en **ny** oppgave i Codex-appen (nivå Middels) og limer inn f.eks.: «Gjør Oppgave N i docs/OPPGAVER.md. Følg AGENTS.md og docs/SPEC.md.»
3. Codex lager PR. `build.yml` (bygg, tester, lint) og Claude-reviewen kjører automatisk.
4. Du sjekker: CI grønn? Hva sier reviewen? **Maks én retterunde** per PR: rettelser bes om i **samme Codex-chat** (ikke `@codex` på GitHub – den pusher ikke). «Kan vente»-punkter samles i neste oppgave.
5. Eieren merger, `release.yml` bygger signert APK (`v<run_number>`, asset `systemverktoy.apk`), eieren installerer med henteskriptet.

Kjente feller:
- Codex kan jobbe i en utdatert kopi. Hvis den ikke finner nye filer: be den kjøre `git checkout main && git pull origin main`.
- Interaktive `gh`-menyer med piltaster virker ikke i eierens terminal. Bruk eksplisitte kommandoer.
- Windows PowerShell 5.1: kommandoer på én linje. `>>` betyr at den venter på mer input (Ctrl+C).
- `release.yml` ignorerer endringer i `docs/**` (også bilder og HTML-skisser) og i alle `*.md`-filer. Andre filer utløser ny versjon.

## Kommandoer eieren bruker (PowerShell)

```
gh pr list -R allroska/systemverktoy
gh pr checks <nr> -R allroska/systemverktoy
gh pr merge <nr> -R allroska/systemverktoy --merge --delete-branch
gh run watch -R allroska/systemverktoy $(gh run list -R allroska/systemverktoy -w Release -L 1 --json databaseId -q ".[0].databaseId")
powershell -ExecutionPolicy Bypass -File $HOME\Systemverktoy\hent-tvlogg.ps1 -Tv <TV-IP> -Installer
powershell -ExecutionPolicy Bypass -File $HOME\Systemverktoy\hent-tvlogg.ps1 -Tv <TV-IP>
```

- Henteskriptet (`tools/hent-tvlogg.ps1`, kopi i `$HOME\Systemverktoy\`) kobler til TV-en med adb over hjemmenettet, kan installere nyeste APK (`-Installer`, beholder data) og henter eksportfilene til `Documents\Systemverktoy-logg\<dato_klokkeslett>\`: `systemverktoy-raa.csv`, `systemverktoy-tidslinje.csv`, `systemverktoy-diagnose.txt`.
- TV-ens IP-adresse står ikke i repoet. Spør eieren, eller se `CLAUDE.local.md` hvis den finnes (ikke sjekket inn).
- Androids egen logg uten appen: `adb -s <TV-IP>:5555 shell dumpsys usagestats` (siste 24 t, bare forgrunnsapp/skjerm, ikke avspilling).
- Glemt PIN: `adb -s <TV-IP>:5555 shell am broadcast -a com.systemverktoy.app.RESET_PIN -n com.systemverktoy.app/.PinResetReceiver` (sletter ikke historikk).
- Engangsoppsett er gjort: adb (`winget install Google.PlatformTools`), USB/ADB-feilsøking på TV-en, PC-en godkjent på TV-en.

## Produktvalg som er tatt (eieren)

- Hele døgnet vises; eieren tolker klokkeslett selv. Startskjerm teller ikke. HDMI/TV-kanaler er egen sum, ikke «TV-titting».
- Oversikt: én rad per økt, bare minutter sett, strek som teller opp mot dagens total i én farge.
- Tekst minst 13 sp (65" 4K), hjelpetekst 11 sp.
- «Første påslag» = første gang TV-en slås på etter å ha vært av; omstart om natten teller ikke.
- PIN: fire sifre, tall- eller piltaster, låses når appen går i bakgrunnen.
- Ukesoversikt (13.11, variant C4 fra skisserunden): alltid mandag–søndag, ikke siste 7 dager. Toppfelt med snitt per dag mot forrige uke, uten streker. Apper med prosent og tid, uten streker, maks 4 linjer (5+ apper → 3 største + «Andre (N apper) ›»). Døgnkart med én blåfarge og alle timetall 00–23. «Sto bare på» vises ikke der.
- Data ut av TV-en: aldri fra appen. Sjekk hjemmefra: ikke bestemt (anbefalt: PC hjemme laster opp til eierens egen Google Drive; appen forblir uten internett). App som sender selv (INTERNET) er frarådet (Play Protect kan stemple den som overvåkingsapp).

## Backlog

Se «Senere» i `docs/OPPGAVER.md`, og «Kan vente» i Claude-reviewene på PR #6 og #7 (f.eks. grå «avspilling ukjent»-økter for systemapper, `DREAMING_STOPPED`-rekkefølge, PIN-sperre ved klokkejustering). Ideer: strammere støyfilter, test av flere strømmeapper, branch protection på `main`. Åpent produktvalg: skal lyd uten bilde (f.eks. Spotify Connect med skjermen av, sett 12.11) vises, og i så fall hvordan?

## Hemmeligheter (ikke i repoet)

Signeringsnøkkelen og GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) ligger i eierens private Google Drive og i GitHub Secrets. `CLAUDE_CODE_OAUTH_TOKEN` brukes av reviewen (abonnement, ikke API-betaling). Mister man nøkkelen, kan appen ikke oppdateres uten avinstallasjon (= tap av historikk).
