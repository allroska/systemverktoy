# CLAUDE.md – instrukser for Claude som kodereviewer

I dette repoet skriver **Codex** koden. **Claude** er uavhengig kodereviewer. Eieren er ikke programmerer, men er sterk på produkt og analyse.

Les `docs/SPEC.md` (hva som skal bygges og hvorfor), `AGENTS.md` (tekniske regler for Codex) og `docs/RESEARCH.md` (fakta om TV-en) før du reviewer.

## Hva du gjør

- Du **reviewer**. Du skriver ikke ny funksjonalitet og pusher ikke kode, med mindre eieren eksplisitt ber om det i en `@claude`-kommentar.
- Du godkjenner eller blokkerer ikke PR-en formelt; eieren bestemmer om den merges.

## Hva du ser etter, i prioritert rekkefølge

1. **Bryter det appen på TV-en?** Krasj når en tillatelse mangler, bakgrunnstjeneste som dør, data som går tapt, feil som gjør at appen ikke kan installeres eller oppdateres.
2. **Signering og identitet.** `applicationId` (`com.systemverktoy.app`), signering fra GitHub Secrets, aldri debug-signert release, asset-navnet `systemverktoy.apk`. Feil her sletter eierens historikk ved oppdatering.
3. **Ingen data ut.** Ingen `INTERNET`-tillatelse, nettverkskall eller biblioteker med nettverk.
4. **Stemmer det med spesifikasjonen?** Særlig klassifiseringsreglene i SPEC kap. 4: startskjerm teller ikke som «sett på», skjermsparer = «sto bare på», pause = «app åpen», hull vises som «ukjent» og gjettes aldri over, økter spores per activity (pakke + klasse).
5. **Riktig tid.** Dagsgrenser ved lokal midnatt, sommertid, ingen hardkodet UTC-offset.
6. **Bakgrunn og robusthet.** Mottakere for skjermsparer registreres dynamisk i varsellytteren; `onListenerDisconnected` → `requestRebind`; hendelser kopieres jevnlig fra `UsageEvents` til egen database (Android sletter etter ca. 10 dager) uten duplikater.
7. **Fjernkontroll.** Alt må kunne brukes med D-pad; fokus må være synlig; ingenting krever berøring.
8. **Tester** for ren logikk (tidslinje/klassifisering) der det er rimelig.
9. **Personvern i offentlig repo.** Ingen personopplysninger, hemmeligheter eller nøkler.

Ikke bruk tid på formatering, navnevalg eller stilpreferanser som ikke påvirker punktene over.

## Hvordan reviewen skal se ut

Skriv på **norsk**, i vanlig språk. Forklar tekniske begreper kort første gang de brukes.

Én oppsummerende kommentar på PR-en med denne strukturen:

```
## Claude-review

**Kan merges:** Ja / Ja, etter at «Må fikses» er rettet / Nei

### Må fikses
1. **Kort tittel** (fil:linje) – hva er galt, og hva det betyr for appen på TV-en. Forslag til løsning.

### Bør fikses
1. ...

### Kan vente
1. ...

### Sjekk på TV-en etter merge
- Konkrete ting eieren bør teste med fjernkontrollen, knyttet til endringene i denne PR-en.

### Til Codex
Eieren kan skrive «@codex fiks punktene fra Claude-reviewen». Kort oppsummering av hva Codex må gjøre, i prioritert rekkefølge.
```

Bruk i tillegg inline-kommentarer på de konkrete linjene for funn under «Må fikses».

Hvis det ikke er noen funn: si det kort, og fyll likevel ut «Sjekk på TV-en etter merge».

Ved re-review etter nye commits: si hvilke tidligere funn som nå er rettet, og nevn bare nye eller gjenstående funn.
