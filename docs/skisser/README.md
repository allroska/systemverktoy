# Skisser

Referanseskisser for skjermene. Filene er HTML-maler fra et designverktøy: layout, farger og tekster står i markupen, og eksempeldataene (oppdiktede) står i `renderVals()` nederst i hver fil. `<x-dc>`, `<sc-for>` og `{{...}}` er malsyntaks – ikke kopier den inn i appen, bruk filene som visuell fasit.

| Fil | Skjerm | SPEC |
|---|---|---|
| `Main.dc.html` | I dag – oppsummering, døgnstripe, tidslinje | 5.1 |
| `Detalj.dc.html` | Detaljer for én app-økt, med «Grunnlag – rå signaler» | 5.2 |
| `Igaar.dc.html` | Tidligere dag, med varsel om hull i loggen | 5.3 |
| `Oppsett.dc.html` | Første oppstart – to tillatelser | 5.4 |
| `Banner.dc.html` | Banner/ikon på TV-ens startskjerm (320×180) | 5.5 |

Skissene er laget i 1280×720. Appen skal skalere til 1920×1080.
