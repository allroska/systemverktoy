# AGENTS.md – regler for Codex

Les **`docs/SPEC.md`** før du gjør noe. Den beskriver hva som skal bygges og produktbeslutningene som allerede er tatt. Les også `docs/RESEARCH.md` (tekniske fakta om målenheten) og `docs/TESTPLAN.md`. Skisser ligger i `docs/skisser/`.

## Roller i dette repoet

| Rolle | Hvem | Hva |
|---|---|---|
| Bygger | **Codex (deg)** | Skriver kode på en egen branch og åpner en pull request |
| Kodereviewer | **Claude** (GitHub Action, se `CLAUDE.md`) | Reviewer hver PR automatisk og kommenterer |
| Eier | Mennesket | Ber deg rette review-funn, merger, tester på TV-en |

- Du merger aldri selv.
- Når eieren skriver noe som «@codex fiks punktene fra Claude-reviewen»: les **alle** Claude-kommentarer på PR-en (både inline-kommentarer og oppsummeringen), rett alt merket **Må fikses** og det som er rimelig under **Bør fikses**, og push til samme branch. Svar kort i PR-en hvilke punkter du rettet, og hvilke du bevisst lot være og hvorfor.
- Er du uenig i et review-funn, si det i PR-en med begrunnelse i stedet for å ignorere det.

## Arbeidsform

- Én oppgave = én branch = én PR. Hold PR-er små nok til å reviewes (helst under ~800 endrede linjer, unntatt genererte filer).
- PR-beskrivelsen skrives på **norsk** for en eier som ikke er programmerer, med disse overskriftene:
  - **Hva er endret** (punktliste, vanlig språk)
  - **Hvordan teste på TV-en** (konkrete steg med fjernkontrollen)
  - **Åpne spørsmål / antakelser**
- Commit-meldinger på engelsk, korte og beskrivende.
- Følg `docs/SPEC.md`. Hvis noe der er umulig eller uklart, skriv det under «Åpne spørsmål» i stedet for å gjette stille.

## Tekniske rammer

- **Språk/UI:** Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`). Ingen Leanback-fragmenter.
- **Versjoner:** minSdk 31, compileSdk 36, targetSdk 35. JDK 17. Android Gradle Plugin 9.4.x med Gradle 9.6.0 (Gradle-wrapperen skal ligge i repoet). AGP 9 har Kotlin innebygd.
- **applicationId:** `com.systemverktoy.app` – **skal aldri endres** (endring = ny app = tap av data).
- **Appnavn:** `Systemverktøy`. Banner 320×180 og ikon som i `docs/skisser/Banner.dc.html`.
- **Manifest:**
  - `<uses-feature android:name="android.software.leanback" android:required="true"/>`
  - `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`
  - Launcher-activity med `MAIN` + `LEANBACK_LAUNCHER`, og `android:banner`.
  - `PACKAGE_USAGE_STATS` (med `tools:ignore="ProtectedPermissions"`).
  - En `NotificationListenerService` med `BIND_NOTIFICATION_LISTENER_SERVICE`.
  - `<queries>` for å slå opp navn/ikon på andre apper (eller `QUERY_ALL_PACKAGES`).
  - **Ingen `INTERNET`-tillatelse.** Ingen nettverkskall, analytics, crash-rapportering eller tredjeparts-SDK-er med nettverk.
- **Lagring:** Room. Rå hendelser lagres; perioder beregnes ved visning. Se SPEC kap. 6.
- **Bakgrunn:**
  - Varsellytteren (`NotificationListenerService`) er appens «alltid på»-del. Registrer `ACTION_DREAMING_STARTED/STOPPED`- og skjerm-på/av-mottakere **dynamisk i tjenesten** (de kan ikke registreres i manifestet). Lytt på `MediaSessionManager.addOnActiveSessionsChangedListener` med lytterens `ComponentName`, og på `MediaController.Callback` for hver sesjon.
  - Logg `onListenerConnected` / `onListenerDisconnected` som egne hendelser. Ved frakobling: `requestRebind`.
  - Periodisk kopiering av `UsageEvents` til databasen med WorkManager (hvert 15.–60. minutt) og når appen åpnes. Ikke dupliser hendelser.
  - Appen skal aldri krasje hvis en tillatelse mangler – vis oppsettskjermen i stedet.
- **Logikk som kan testes uten TV skal være ren Kotlin** (f.eks. en `TimelineBuilder` som tar rå hendelser inn og gir perioder ut), med enhetstester for reglene i SPEC kap. 4: per-activity-økter, startskjerm teller ikke, skjermsparer = IDLE, pause = APP_OPEN, hull = UNKNOWN, dagsgrense ved midnatt og sommertid-overgang.
- **Tid:** bruk `java.time` og systemets tidssone. Ikke hardkod UTC-offset.

## Bygg og utgivelse (GitHub Actions)

- `.github/workflows/build.yml`: på hver PR – bygg debug, kjør enhetstester og lint.
- `.github/workflows/release.yml`: på push til `main` – bygg **signert release-APK** og publiser den som GitHub Release med tagg `v<run_number>` og et asset som heter **nøyaktig `systemverktoy.apk`**, slik at denne lenken alltid peker på nyeste versjon:
  `https://github.com/<eier>/<repo>/releases/latest/download/systemverktoy.apk`
- `versionCode` = `github.run_number`, `versionName` = `1.0.<run_number>` (lokalt: 1 / 1.0.0-dev).
- **Signering:** release signeres med en fast nøkkel fra GitHub Secrets:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
  Dekod keystore til en midlertidig fil i workflowen. **Aldri** commit keystore eller passord. **Aldri** signer release med debug-nøkkel. Hvis secrets mangler, skal release-jobben feile med en tydelig melding – ikke falle tilbake til debug.
- Ikke endre `.github/workflows/claude*.yml` med mindre eieren ber om det.

## Ditt miljø

Codex-miljøet ditt kan mangle Android SDK. Hvis det mangler og oppsettskriptet ikke har installert det: skriv koden, kjør det du kan (ren Kotlin), og la GitHub Actions (`build.yml`) være fasiten for om prosjektet bygger. Si i PR-en hva du ikke fikk kjørt.

## Personvern i repoet

Repoet er offentlig. Ingen navn på personer, e-postadresser, adresser, bilder fra hjemmet eller ord om hvem appen brukes på – i kode, kommentarer, commit-meldinger, PR-er eller testdata. Bruk nøytrale testdata.

## Code Review Rules

### Signering og identitet
- Flagg enhver endring av `applicationId`, signeringsoppsett eller release-asset-navnet `systemverktoy.apk`. Det bryter oppdatering på TV-en og kan slette brukerens historikk.

### Ingen data ut
- Flagg `INTERNET`-tillatelse, nettverkskall eller biblioteker som sender data. Appen skal være helt lokal.

### Personvern i offentlig repo
- Flagg personopplysninger, hemmeligheter eller nøkler i kode, testdata eller dokumentasjon.
