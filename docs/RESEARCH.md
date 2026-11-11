# Tekniske fakta om målenheten

Oppsummert fra research gjort før prosjektstart (november 2026). Status:
**Bekreftet** = dokumentert i Android-kildekode/dokumentasjon eller sett på TV-en ·
**Sannsynlig** = støttet av andres erfaring på lignende enheter ·
**Må testes** = avklares med testappen (fase 1).

## Tillatelser

| Fakta | Status |
|---|---|
| Menyene «Brukstilgang» og «Tilgang til varsler» finnes på TV-en (Innstillinger → Apper → Spesiell apptilgang) | Bekreftet (sett på TV-en) |
| Android 13s «begrensede innstillinger» for sideinstallerte apper gjelder ikke Android 12 – bryterne kan slås på direkte | Bekreftet |
| Fallback hvis en bryter ikke virker: `adb shell appops set com.systemverktoy.app GET_USAGE_STATS allow` og `adb shell cmd notification allow_listener com.systemverktoy.app/<tjenesteklasse>` | Bekreftet (standard Android) |
| Kjente Sony-problemer med brukstilgang gjelder eldre Android TV 7/8-modeller, ikke denne | Sannsynlig |

## Hendelseslogg (UsageEvents)

| Fakta | Status |
|---|---|
| Detaljerte hendelser slettes etter ca. 10 dager (`UsageStatsDatabase.prune()`) | Bekreftet (AOSP) |
| De siste minuttene av loggen kan mangle når man spør | Bekreftet (dokumentasjon) |
| Tilgjengelige typer på API 31/32: `ACTIVITY_RESUMED` (1), `ACTIVITY_PAUSED` (2), `ACTIVITY_STOPPED` (23), `SCREEN_INTERACTIVE` (15), `SCREEN_NON_INTERACTIVE` (16), `DEVICE_SHUTDOWN` (26), `DEVICE_STARTUP` (27) | Bekreftet |
| `DEVICE_SHUTDOWN` sitt tidsstempel er siste lagring før nedstengning, ikke faktisk avslag | Bekreftet |
| Økter må spores per activity (pakke + klasse); en sen STOPPED fra en meny-activity kan ellers «lukke» avspilleren | Sannsynlig (rapportert på Sony BRAVIA Android TV 10) |
| HDMI-innganger og TV-kanaler logges under Sonys TV-app | Sannsynlig – må testes |
| Hvordan Sony-standby rapporteres (SCREEN_NON_INTERACTIVE?) | Må testes |

## Skjermsparer

| Fakta | Status |
|---|---|
| Google TV sin skjermsparer er «Ambient mode», en DreamService (`com.google.android.apps.tv.dreamx/.service.Backdrop`) | Sannsynlig |
| Valgt skjermsparer kan leses fra `Settings.Secure` `screensaver_components` | Bekreftet (standard Android) |
| `ACTION_DREAMING_STARTED/STOPPED` sendes bare til mottakere registrert i kode mens appen kjører – ikke til manifest-mottakere | Bekreftet |
| Ingen offentlig API sier om skjermspareren *allerede* går når appen starter | Bekreftet |
| Om skjermspareren vises som `ACTIVITY_RESUMED` i UsageEvents (Android 11+ bruker en `DreamActivity`) | Må testes |
| Videoavspilling i Netflix/YouTube hindrer skjermspareren; pause lenge lar den starte | Sannsynlig – må testes |
| Sony har egne strømsparefunksjoner («Idle TV standby», «Auto Power Off», HDMI-CEC) som kan slå av TV-en uten at noen trykker | Bekreftet (Sony) |

## Avspilling (MediaSession)

| Fakta | Status |
|---|---|
| `MediaSessionManager.getActiveSessions(component)` krever at appen er aktivert varsellytter | Bekreftet |
| Netflix (`com.netflix.ninja`) rapporterer spiller/pause på Android TV | Bekreftet (open source-prosjekter) |
| Netflix sender **ikke** tittel | Bekreftet |
| Netflix melder «spiller» på sin egen startside når videoforhåndsvisning er på → slå av autoforhåndsvisning i profilen | Bekreftet |
| YouTube (`com.google.android.youtube.tv`) rapporterer spiller/pause, sannsynligvis med tittel | Sannsynlig |
| NRK TV | Må testes |
| Reservesignal: `AudioManager.registerAudioPlaybackCallback` virker uten spesialtillatelse, men er anonymisert (viser at media-lyd spiller, ikke hvilken app). Kombiner med forgrunnsapp. Ignorer lyd mens launcheren er i forgrunnen (trailere). | Bekreftet |
| En aktivert varsellytter bindes automatisk av systemet etter oppstart | Bekreftet |
| Om lytteren overlever standby over natten og Sony-programvareoppdateringer | Må testes |

## Sideinstallering og bygging

| Fakta | Status |
|---|---|
| «Downloader» (AFTVnews) finnes i Play Store på Google TV; tillatelsen «Installer ukjente apper» gis per app | Bekreftet |
| En oppdatering installeres bare hvis den er signert med samme nøkkel som installert versjon – ellers må appen avinstalleres og data går tapt | Bekreftet |
| Debug-nøkler fra GitHub Actions er nye ved hver kjøring → må bruke fast nøkkel fra Secrets | Bekreftet |
| Play Protect **blokkerte** installasjonen av versjon 1.0.4 («Appen er blokkert for å beskytte enheten din») fordi appen ber om tilgang til bruksdata og varsler. Installasjon gikk når skanningen ble slått av midlertidig. Reservevei: `adb install` fra PC. | Bekreftet (sett på TV-en, 11. nov. 2026) |
| Googles krav om verifiserte utviklere for sideinstallerte apper gjelder foreløpig bare mobil/nettbrett i utvalgte land; planlagt utvidet globalt i 2027. Reserveveier: gratis «limited distribution»-konto, avansert flyt, eller adb. | Bekreftet (Google, nov. 2026) |
| AGP 9.4.x krever Gradle 9.6.0 og JDK 17; Kotlin er innebygd i AGP 9 | Bekreftet |

## Funn fra testappen (v1.0.8, 11.11.2026)

| Fakta | Status |
|---|---|
| Valgt skjermsparer: `com.google.android.apps.tv.dreamx/.service.Backdrop` | Bekreftet |
| Launcher (startskjerm): `com.google.android.apps.tv.launcherx` (`.home.HomeActivity`) | Bekreftet |
| Eldste hendelse i Androids logg ved første lesing: 01.11 18:57 (ca. 10 dager) | Bekreftet |
| `USAGE`-hendelser kommer med activity-klasse | Bekreftet |
| Appen er ikke unntatt batterioptimalisering (standard) | Bekreftet – følg med på hjerteslag |
| Når appen åpner innstillingssiden for varseltilgang direkte, får ingen elementer fokus og fjernkontrollen virker ikke. Via TV-ens innstillinger (Apper → Spesiell apptilgang) virker det. | Bekreftet |

### Kveldstest 11.11 (v1.0.10, 17:19–18:31)

| Fakta | Status |
|---|---|
| **Netflix** (`com.netflix.ninja`): `SESSION_ADDED`/`STATE_PLAYING` ved start, `STATE_PAUSED` ved pause, `STATE_STOPPED` + `SESSION_REMOVED` når man går tilbake til menyen. Ingen tittel. | Bekreftet |
| **Netflix bruker samme activity (`MainActivity`) for meny og avspiller.** Activity-klasse kan derfor *ikke* skille meny fra avspilling – det må MediaSession-status gjøre. | Bekreftet |
| **Netflix-meny i ca. 3 min (17:27:54–17:31:10) ga ingen «spiller»-hendelser** på eierens profil. Andre profiler kan ha forhåndsvisning på – ikke testet. | Bekreftet (én profil) |
| **NRK TV** (`no.nrk.tv`, `MainActivityTv`): `SESSION_ADDED` (`STATE_NONE`) → `STATE_BUFFERING` → `STATE_PLAYING`, pause gir `STATE_PAUSED`. Ingen tittel. Ny episode gir `SESSION_REMOVED` + ny `SESSION_ADDED`. | Bekreftet |
| **Spotify** sender status og tittel. | Bekreftet |
| **AUDIO** `media_active=1; usage=1, content=3` ved avspilling, `media_active=0` ved pause – pause gir altså egen AUDIO-rad. | Bekreftet |
| **Skjermsparer:** `SCREEN_NON_INTERACTIVE (16)` + `DREAMING_STARTED` + `dreamx/DreamActivity` RESUMED samtidig; ved oppvåkning `SCREEN_INTERACTIVE (15)` + `DREAMING_STOPPED`. **Skjermsparer gir altså SCREEN_NON_INTERACTIVE** – klassifiseringen må ikke tolke det som «av» når DREAM er aktiv. | Bekreftet |
| **HDMI-inngang:** `com.sony.dtv.tvx` `MainActivity` + MediaSession `STATE_PLAYING` med tittel «HDMI 4» så lenge inngangen er valgt (uansett hva konsollen viser). Ved bytte bort: `STATE_NONE` + `SESSION_REMOVED`. | Bekreftet |
| **Hjerteslag** kom presist hvert 15. minutt (17:15:36 … 18:30:37). | Bekreftet |
| **Oppdatering** v1.0.8 → v1.0.10 via Downloader beholdt databasen; lytteren koblet seg til igjen (`onListenerConnected`) få sekunder etter installasjon. | Bekreftet |
| **Støy** i USAGE: `EVENT_11` (standby-bøtte), `EVENT_12`, `EVENT_14` for mange system- og Sony-pakker, samt `packageinstaller`, `permissioncontroller`, `com.android.tv.settings`, `com.esaba.downloader`. Bør skjules i fase 2. | Bekreftet |

### Androids egen logg via adb (11.11, `dumpsys usagestats`)
- `adb shell dumpsys usagestats` viser Androids hendelser siste 24 t uten appen. Appens egne MEDIA/DREAM/LISTENER/APP-hendelser finnes ikke der.
- **Nattlig omstart:** 04:38 skjerm av → 04:48:12 `DEVICE_SHUTDOWN` → 04:48:47 `SCREEN_INTERACTIVE` + `com.android.tv.settings/.system.FallbackHome` → `DEVICE_STARTUP` → launcher `HomeActivity`/`ProfileChooserActivity` → 04:50:56 skjerm av. Varer over 2 min, uten at noen bruker TV-en.
- Launcherens `coreservices.bootmode.DispatchActivity` dukker opp rett etter hver `SCREEN_NON_INTERACTIVE` og ved skjerm på. Den er ikke brukeraktivitet.
- **Skjermsparer:** `SCREEN_NON_INTERACTIVE` og så `com.google.android.apps.tv.dreamx/android.service.dreams.DreamActivity` (13:09:07, 17:39:38). Avsluttes med `SCREEN_INTERACTIVE` + `HomeActivity`.
- **Sony TV-appen** (`com.sony.dtv.tvx/.MainActivity`) brukes både for HDMI og TV-kanaler. Hvilken kilde det er, krever MediaSession-tittel.
- Netflix og NRK TV har én activity hver i bruk (`com.netflix.ninja.MainActivity`, `no.nrk.tv.androidtv.MainActivityTv`, i tillegg til splash). Activity-navnet skiller ikke meny fra avspilling.
- Hendelsestyper siste 24 t: STANDBY_BUCKET_CHANGED (925), ACTIVITY_RESUMED/PAUSED/STOPPED (~180 hver), SLICE_PINNED, NOTIFICATION_INTERRUPTION, FOREGROUND_SERVICE_START/STOP, SCREEN_* (5+5), CONFIGURATION_CHANGE, DEVICE_SHUTDOWN/STARTUP, USER_UNLOCKED. Ingen KEYGUARD-hendelser.

## Kilder (utvalg)
- AOSP `UsageStatsDatabase.java` – lagringstid
- developer.android.com: `UsageStatsManager`, `UsageEvents.Event`, `NotificationListenerService`, `MediaSessionManager`, `AudioManager`
- github.com/cbeyls/MediaSession2MQTT – MediaSession på Android TV, Netflix-oppførsel, lydreserve
- github.com/Harrypulvirenti/Fire2MQTT – skjermsparer-kringkastinger
- AFTVnews – sideinstallering på Google TV
- Android Gradle Plugin 9.4.0 release notes
