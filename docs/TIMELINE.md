# Tidslinjeberegning (PR A)

`TimelineBuilder.build(events, day, zone, now)` beregner perioder uten Android-kall,
skriving eller egen klokke. Send inn beholdt historikk frem til `now`, også hendelser
før valgt dag. Bare dagens rålogg er ikke nok til å videreføre tilstand over midnatt.
Resultatet klippes til lokal dag og `now`; OFF utelates. Varigheter er faktisk
forløpt tid, også på dager med 23 eller 25 timer. `signals` beholder råhendelser som
underbygger periodene, inkludert relevante signaler fra før dagsgrensen.

Periodene kan summeres etter kategori og pakke. Bare WATCHING inngår i «Sett på»;
IDLE og HDMI har hver sin sum. Råhendelsene endres eller slettes aldri av beregningen.

## Åpne spørsmål / antakelser

- **Hjerteslag under skjermsparer:** SPEC sier UNKNOWN ved manglende hjerteslag
  mens skjermen er på, men `EventRepository.heartbeat()` skriver bare mens
  `PowerManager.isInteractive` er sann. Kveldstesten viser at skjermsparer gir
  SCREEN_NON_INTERACTIVE. Derfor starter beregningen ikke nye hjerteslagshull under
  en kjent skjermsparer. Et allerede påvist hull eller en frakoblet lytter har
  fortsatt prioritet over IDLE. Ved oppvåkning starter en ny 35-minuttersfrist.
  Ellers ville normal skjermsparer etter 35 minutter alltid bli «Ukjent».
- **Støy:** RESEARCH nevner innstillinger og Downloader som støy. SPEC v2 sier
  uttrykkelig at de skal vises med appnavn; beregningen følger SPEC.
- **Tidspunkter i testene:** RESEARCH inneholder signalrekkefølge og utvalgte
  klokkeslett, ingen komplett råloggeksport. Menyintervallet 17:27:54–17:31:10 og
  hjerteslagets start 17:15:36 brukes direkte. Andre varigheter er konstruerte
  testdata for de dokumenterte Netflix-, NRK-, skjermsparer- og HDMI 4-signalene.
- **Hull og gjenopptak:** Uten et første hjerteslag starter 35-minuttersfristen ved
  første på-signal/forgrunnsapp. Hull begynner ved fristens utløp. Etter et hull,
  frakobling eller vanlig avslag gjenbrukes ikke gammel app-/avspillingsstatus.
  Manglende forgrunnsapp mens skjermen er på vises også som UNKNOWN. Før første
  observerte på-signal legges det ikke inn perioder.
- **Korte avspillinger:** 60-sekundersregelen gjelder sammenhengende avspilling
  med MediaSession, også før klipping ved midnatt. Metadata og hjerteslag bryter
  ikke avspillingen. Pause eller ny BUFFERING bryter PLAYING-forløpet.
  BUFFERING tas med når neste status i samme sesjonsforløp er
  PLAYING, men buffer-tid oppfyller ikke kravet om 60 sekunder PLAYING. En pågående
  kort avspilling vises først som APP_OPEN og omberegnes etter 60 sekunder.
  AUDIO-reserven har ingen PLAYING-status og filtreres derfor ikke etter lengde.
- Råloggen har pakkenavn, men ingen MediaSession-identifikator. Beregningen bruker
  siste sesjonssignal per pakke; samtidige sesjoner i samme app kan ikke skilles
  sikkert i eksisterende data.

SPEC er ikke endret. Skjermene og innkobling i dagvisningen hører til PR B
etter at eieren har merget PR A.
