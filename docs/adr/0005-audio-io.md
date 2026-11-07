# ADR 0005: Audioquelle, Tiefpass vor der Erkennung und Drone-Ausgabe

- Status: angenommen
- Datum: 2026-11-08
- Bezug: Spike #32, Labor #33 und #35, [ADR 0002](0002-pitch-detection-mpm.md), [ADR 0003](0003-level-gate.md),
  Messungen in [`docs/field-test/audio-spike.md`](../field-test/audio-spike.md#ergebnisse)
- Umsetzung: #60 (Tiefpass), #61 (Ein- und Ausgabe der App)

## Kontext

Der Spike hat auf dem Handy des Besitzers (OnePlus Nord 2) mit der B-Tuba gemessen, Abstand etwa 1 m:

| Datum | Messung | Drone |
|---|---|---|
| 05.11. | Selbsttest ohne Spieler: alle Quellen und Mikrofone | Lautsprecher |
| 06.11. | Tuba: Quellen und Mikrofone, dann Drone | Lautsprecher, a' = 440 Hz |
| 07.11. | Tuba mit Drone: Lautsprecher gegen Bluetooth-Kopfhörer (Bose QC Earbuds II, ANC aus, ein Ohrhörer) | a' = 442 Hz |

Die wichtigsten Befunde:

- **Zwei Eingangswege:**
  - `VOICE_RECOGNITION`, `VOICE_PERFORMANCE`, `MIC` und `CAMCORDER` liefern dasselbe verarbeitete Signal. Es hat
    einen Hochpass und einen Limiter, die Spitzen liegen schon bei mf genau bei −1 dBFS.
  - `UNPROCESSED` ist roh: mindestens 18 dB Reserve, Grundton 8–16 dB stärker, keine automatische Verstärkung, keine
    Rauschunterdrückung.
  - Das Gerät sichert `UNPROCESSED` nicht zu (die Property ist `null`), die Quelle funktioniert aber.
- **Mikrofon:** Unten und hinten unterscheiden sich kaum, unten ist etwa 2 dB lauter.
- **Der Drone aus dem Lautsprecher überdeckt die Tuba:**
  - Am Mikrofon kommen Drone und Tuba etwa gleich laut an.
  - Der Lautsprecher gibt tiefe Grundtöne kaum wieder. Die vielen exakt periodischen hohen Drone-Teiltöne bestimmen
    deshalb die Periodizität des Signals.
  - MPM meldet den Drone statt der Tuba, und das Pegel-Gate (ADR 0003) bleibt meist zu.
- **Kopfhörer lösen das Problem:**
  - Über Bluetooth-Kopfhörer kommt der Drone mit −75 bis −79 dBFS am Mikrofon an, über den Lautsprecher mit −27 bis
    −32 dBFS. Das sind rund 45 dB weniger, der Drone geht im Raumrauschen unter.
  - MPM erkennt die Tuba ohne jeden Vorfilter, 95–100 % der Frames sind stimmhaft, Klarheit ≥ 0,98.
  - Der Spieler hörte Drone und Tuba gut, auch die Schwebungen.
- **Ein Tiefpass knapp über dem Grundton rettet den Lautsprecher-Betrieb** (offline, Butterworth 4. Ordnung):
  - Mit der Grenzfrequenz max(250 Hz, 1,4 × Zielton) folgt der Detektor in allen Lautsprecher-Aufnahmen der Tuba,
    von Kontra-B bis kleines b. Die Werte stimmen mit einer unabhängigen Teiltonanalyse überein.
  - Liegt die Grenze bei 2 × Zielton oder höher, ziehen die Drone-Teiltöne die Messung zum Zielton. Beim kleinen f
    lag die Tuba bei −25 ct, gemessen wurden −6 ct (700 Hz), −22 ct (350 Hz) und −25 ct (250 Hz). Das ist der
    gefährlichste Fehler, denn die App würde „sauber“ melden, obwohl der Spieler deutlich daneben liegt.
  - In den Kopfhörer-Aufnahmen ändert der Tiefpass die Ergebnisse um höchstens 1 ct.
- **Drone abziehen** mit festem Modell aus dem Vorspann bringt nur 7–21 dB, weil der Drone am Mikrofon nicht stationär
  genug ist.
- **Ausgabe frei wählbar:** `AudioTrack.setPreferredDevice` legt den Drone auf den Lautsprecher, auch wenn ein
  Bluetooth-Kopfhörer verbunden ist. Android führt für Lautsprecher und Kopfhörer getrennte Medienlautstärken.
- **Rechenzeit** der Erkennung in der Debug-Version: 10–46 % eines Kerns.

Der Besitzer übt lieber mit Kopfhörer. Den Lautsprecher-Betrieb sieht er als nützliche Ergänzung.

## Entscheidung

1. **Audioquelle `UNPROCESSED`**, 48 kHz, mono, PCM 16 Bit.
   - Ob die Quelle geht, prüft die App durch Öffnen, nicht über die Property.
   - Lässt sie sich nicht öffnen oder liefert sie nur digitale Stille, nimmt die App `VOICE_RECOGNITION`.
   - Mikrofon ist das eingebaute Standardmikrofon, die App wählt keines aus.
   - Ein Bluetooth-Mikrofon (SCO) nutzt die App nie. Es schaltet den Kopfhörer in den Telefonie-Modus und
     verschlechtert den Drone.
2. **Zieltonabhängiger Tiefpass vor der Erkennung** (#60):
   - Butterworth 4. Ordnung, Grenzfrequenz `max(250 Hz, 1,4 × Zielfrequenz)`, im Kern (`core/dsp`).
   - Immer aktiv, wenn ein Zielton bekannt ist, mit Kopfhörer wie mit Lautsprecher. So gibt es nur eine Kette, und
     Replay-Auswertungen gelten für beide Fälle.
   - Ohne Zielton (freie Erkennung) bleibt der Tiefpass aus.
   - Das Pegel-Gate misst den gefilterten Pegel. Weil die Grenzfrequenz vom Zielton abhängt, kalibriert es pro Ton
     in der Vorhörphase nach (#24).
3. **Drone-Ausgabe mit Kopfhörer als Empfehlung** (#61):
   - Einstellung „Drone über“ mit **Automatisch** (Standard: Kopfhörer, wenn verbunden, sonst Lautsprecher) und
     **Lautsprecher**.
   - Die App zeigt, wohin der Drone tatsächlich geht.
   - Läuft er über den Lautsprecher, empfiehlt ein abschaltbarer Hinweis beim Übungsstart Kopfhörer: ANC aus, ein
     Ohrhörer reicht.
4. **Keine Echounterdrückung**, solange Kopfhörer und Tiefpass reichen. Sie kommt erst infrage, wenn der Praxistest
   (M4) sie nötig macht.

## Konsequenzen

- **Hohe Lage über Lautsprecher:** Über kleinem b ist der Lautsprecher-Betrieb nicht gemessen. Der Praxistest (M4)
  prüft das, z. B. mit der F-Tuba. Reicht der Tiefpass dort nicht, empfiehlt die App für diese Töne Kopfhörer.
- **Oktave zu hoch:** Spielt der Spieler eine Oktave über dem Zielton, dämpft der Tiefpass seinen Ton, bei höheren
  Zieltönen um bis zu rund 12 dB. Die Oktavprüfung (ADR 0002) muss ihn trotzdem erkennen, #60 testet das.
- **Bluetooth-Latenz** (typisch 100–300 ms) ist für den gehaltenen Drone egal. Einblenden und Bestätigungsklang kommen
  etwas später an. Ob das stört, zeigt der Praxistest.
- **Medienlautstärke:** Mit Kopfhörer gilt dessen eigene Lautstärke. Die App muss sie nicht verwalten, sollte aber den
  Ausgang anzeigen.
- **Rechenzeit:** In der Release-Version auf dem Nord 2 nachmessen, bevor die App live erkennt (#61).
- **Ein Gerät, eine Tuba:** Alle Werte stammen vom Nord 2 und der B-Tuba des Besitzers, bei der Drone-Messung war die
  Tuba noch kalt. Weitere Geräte kommen in [`docs/devices.md`](../devices.md).
