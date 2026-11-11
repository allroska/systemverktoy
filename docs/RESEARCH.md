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
| Googles krav om verifiserte utviklere for sideinstallerte apper gjelder foreløpig bare mobil/nettbrett i utvalgte land; planlagt utvidet globalt i 2027. Reserveveier: gratis «limited distribution»-konto, avansert flyt, eller adb. | Bekreftet (Google, nov. 2026) |
| AGP 9.4.x krever Gradle 9.6.0 og JDK 17; Kotlin er innebygd i AGP 9 | Bekreftet |

## Kilder (utvalg)
- AOSP `UsageStatsDatabase.java` – lagringstid
- developer.android.com: `UsageStatsManager`, `UsageEvents.Event`, `NotificationListenerService`, `MediaSessionManager`, `AudioManager`
- github.com/cbeyls/MediaSession2MQTT – MediaSession på Android TV, Netflix-oppførsel, lydreserve
- github.com/Harrypulvirenti/Fire2MQTT – skjermsparer-kringkastinger
- AFTVnews – sideinstallering på Google TV
- Android Gradle Plugin 9.4.0 release notes
