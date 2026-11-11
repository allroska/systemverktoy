# Tidslinjeberegning (PR A)

`TimelineBuilder.build(events, day, zone, now)` beregner perioder uten Android-kall,
skriving eller egen klokke. Send inn beholdt historikk frem til `now`, også hendelser
før valgt dag. Bare dagens rålogg er ikke nok til å videreføre tilstand over midnatt.
Resultatet klippes til lokal dag og `now`; OFF utelates. Varigheter er faktisk
forløpt tid, også på dager med 23 eller 25 timer. `signals` beholder råhendelser som
underbygger periodene, inkludert relevante signaler fra før dagsgrensen.

Periodene kan summeres etter kategori og pakke. Bare WATCHING inngår i «Sett på»;
IDLE og HDMI har hver sin sum. Råhendelsene endres eller slettes aldri av beregningen.
Skjerm på uten kjent app utelates også når loggen ellers er komplett; dette er
ikke en påstand om at TV-en er av. Manglende hjerteslag eller frakoblet lytter gir
fortsatt UNKNOWN også uten kjent app.

## Åpne spørsmål / antakelser

- **Hjerteslag under skjermsparer:** SPEC sier UNKNOWN ved manglende hjerteslag
  mens skjermen er på, men `EventRepository.heartbeat()` skriver bare mens
  `PowerManager.isInteractive` er sann. Kveldstesten viser at skjermsparer gir
  SCREEN_NON_INTERACTIVE. Derfor starter beregningen ikke nye hjerteslagshull under
  en kjent skjermsparer. Et allerede påvist hull eller en frakoblet lytter har
  fortsatt prioritet over IDLE. Ved oppvåkning starter en ny 35-minuttersfrist.
  Ellers ville normal skjermsparer etter 35 minutter alltid bli «Ukjent».
  Strømbrudd mens skjermspareren går kan dermed ikke tidfestes sikkert med dagens
  hjerteslag; det krever at innsamleren også bekrefter at loggingen går under skjermsparer.
- **Støy:** RESEARCH nevner innstillinger og Downloader som støy. SPEC v2 sier
  uttrykkelig at de skal vises med appnavn; beregningen følger SPEC.
- **Tidspunkter i testene:** RESEARCH inneholder signalrekkefølge og utvalgte
  klokkeslett, ingen komplett råloggeksport. Menyintervallet 17:27:54–17:31:10 og
  hjerteslagets start 17:15:36 brukes direkte. Andre varigheter er konstruerte
  testdata for de dokumenterte Netflix-, NRK-, skjermsparer- og HDMI 4-signalene.
- **Hull og gjenopptak:** Uten et første hjerteslag starter 35-minuttersfristen ved
  første på-signal/forgrunnsapp. Hull begynner ved fristens utløp. Ved hull eller
  frakobling nullstilles media og lyd, mens kjent forgrunnsapp fra Androids egen
  USAGE-logg beholdes. Nye avspillingssignaler kan dermed bekrefte avspilling uten
  at brukeren må bytte app først. Bare nye RESUMED-hendelser bytter forgrunnsapp;
  PAUSED og STOPPED lager ikke falske logghull ved appbytte eller systemoverlegg.
  Ved vanlig skjerm av eller nedstengning nullstilles også forgrunnsappen.
  Første oppstartssignal nullstiller gammel app/avspilling og starter en ny
  35-minuttersfrist, også etter strømbrudd uten et av-signal. BOOT_COMPLETED etter
  en DEVICE_STARTUP innen 10 minutter, uten DEVICE_SHUTDOWN imellom, legges bare
  til som råbevis. Da beholdes ny app, media, lyd og hjerteslagsfrist fra samme
  oppstart. Kommer BOOT_COMPLETED alene eller senere, beholdes full nullstilling.
  Ti minutter følger reviewens forslag til «kort tid før»; SPEC setter ingen grense.
- **Korte avspillinger:** Etter produktvalget i SPEC kap. 4 slås bekreftede
  avspillinger i samme forgrunnsapp sammen over avbrudd under 30 sekunder, også
  pause eller bytte av MediaSession. Sammenslått varighet, inkludert disse avbruddene,
  vurderes deretter mot 60-sekundersregelen, før klipping ved midnatt. Avbrudd på
  30 sekunder eller mer beholdes som APP_OPEN og slås ikke sammen. Appbytte, HOME,
  HDMI, skjermsparer, skjerm av og UNKNOWN bryter alltid sammenhengen. Et pågående
  avbrudd fylles ikke uten et nytt avspillingssignal. Metadata og hjerteslag bryter
  ikke avspillingen. Bufring før første kvalifiserte avspilling kan tas med som
  grunnlag for starttidspunktet, men inngår ikke i 60-sekundersgrunnlaget.
  AUDIO-reserven har ingen PLAYING-status og filtreres derfor ikke etter lengde.
  Reserven brukes bare når forgrunnsappen mangler MediaSession og ingen annen
  pakke har en aktiv PLAYING-sesjon. Dermed tilordnes ikke kjent bakgrunnslyd fra
  f.eks. Spotify til Innstillinger eller Downloader. Et eventuelt lengdefilter for
  AUDIO må avklares som en egen produktregel; SPECs 60-sekundersregel gjelder PLAYING.
- Råloggen har pakkenavn, men ingen MediaSession-identifikator. Beregningen bruker
  siste sesjonssignal per pakke; samtidige sesjoner i samme app kan ikke skilles
  sikkert i eksisterende data.

## Dagvisning (PR B)

- Databasen leses fra maksimalt 48 timer før lokal dagsstart til to minutter etter
  dagens slutt. De to minuttene gir kort etterfølgende grunnlag for 30-/60-sekunders-
  reglene over midnatt. Hendelser etter nå brukes aldri. Beregningen skjer utenfor
  UI-tråden og oppdateres mens skjermen er åpen.
- Innen dette vinduet kortes input videre ned fra siste DEVICE_STARTUP eller
  DEVICE_SHUTDOWN før dagen. Siste tidligere lytterstatus beholdes, siden en
  oppstart ikke beviser at lytteren er tilkoblet. BOOT_COMPLETED brukes ikke som
  avkortingspunkt: den kan komme etter at Netflix allerede spiller.
- Systemverktøys egne perioder filtreres bort etter beregningen, fra både tidslinje,
  døgnstripe og alle summer. Derved forlenges ikke appen som var i forgrunnen før
  Systemverktøy. Råhendelser og klassifiseringsregler endres ikke.
- Detaljer samler tilstøtende perioder for samme app. Det slås ikke sammen over
  hull, avslag, en annen app eller skjult bruk av Systemverktøy. Råsignalene beholder
  opprinnelige tider, også når grunnlaget kommer fra før økten eller dagen.
- «I dag» følger klokken over midnatt; en valgt historisk dato står fast. Venstre/
  høyre bytter dag. Første tidslinjerad får fokus; på en tom dag får overskriften
  fokus. Opp fra første rad går til overskriften. Hold OK der i tre sekunder for
  råloggen; slipp eller flytt fokus før tiden for å avbryte. Tilbake går til tidslinjen.

### Åpne spørsmål / antakelser for visningen

- Ved avkorting på 48 timer uten en oppstart/nedstengning kan eldre tilstand mangle.
  Dette vises som «Begrenset grunnlag», uten å påstå at det er et kjent logghull.
- To minutters etterfølgende data dekker korte avbrudd og avspillingsfilteret.
  En lang innledende BUFFERING som først får PLAYING langt senere, kan dermed
  forbli APP_OPEN i en tidligere dags visning. Det finnes ingen komplett råeksport
  som fastsetter en øvre grense for slik oppstartsbufring.
- «Første gang slått på» viser registrert på-signal, eller 00:00 hvis kjent aktivitet
  allerede pågår ved dagsstart. Uten et slikt grunnlag vises «—». Manglende eller
  avkortet historikk omtales særskilt; en dag før lagret historikk erklæres ikke ubrukt.
- Døgnstripen følger skissen 06–24. Summer og tidslinje omfatter likevel hele lokale
  døgnet, inkludert aktivitet før 06 og sommertidsoverganger.

SPEC er ikke endret av PR B.
