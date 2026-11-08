# Replay-Werkzeug

Spielt eine Aufnahme (WAV oder AIFF) durch dieselbe Kette wie die App: Tiefpass knapp über dem Zielton →
Tonhöhenerkennung → Pegel-Gate → Treffer-Logik.
Gedacht für die Auswertung von Praxistest-Aufnahmen und zum Ausprobieren von Parametern.

## Aufruf

Aus dem Repository-Wurzelverzeichnis:

```sh
./gradlew :tools:replay:run --args="testdata/local/kontra-b.wav --target Kontra-B --calibrate 1.5"
```

Relative Pfade beziehen sich auf das Repository. Alle Optionen zeigt `--help`:

| Option | Bedeutung | Standard |
|---|---|---|
| `--target <ton>` | Zielton: `34`, `Bb1`, `Kontra-B`, `großes F`, `f'` oder `58.27Hz`. Ohne Zielton: freie Erkennung ohne Treffer-Logik. | – |
| `--a4 <Hz>` | Kammerton a' | 440 |
| `--calibrate <s>` | Die ersten s Sekunden (niemand spielt, ggf. läuft der Drone) kalibrieren das Pegel-Gate. Ohne: kein Gate. | 0 |
| `--tolerance <ct>` | Toleranz | 5 |
| `--hold <s>` | Haltedauer | 5 |
| `--grace <s>` | Gnadenzeit für Aussetzer und kurze Ausflüge | 0,3 |
| `--min-clarity <0..1>` | Mindest-Klarheit | 0,8 |
| `--median <n>` | Median-Glättung über n Frames (à 20 ms) | 5 |
| `--detector mpm\|yin` | Erkennungsverfahren | mpm |
| `--no-lowpass` | Ohne Tiefpass vor der Erkennung, zum Vergleich. Mit Zielton filtert die Kette sonst wie die App bei max(250 Hz, 1,4 × Zielton), damit ein Drone aus dem Handy-Lautsprecher die Erkennung nicht übernimmt ([ADR 0005](../../docs/adr/0005-audio-io.md)). Ohne Zielton wird nie gefiltert. | mit Tiefpass |
| `--out <ordner>` | Ausgabeordner | neben der Aufnahme |

Dezimalzahlen dürfen mit Komma oder Punkt angegeben werden.

## Ausgabe

Neben der Aufnahme (oder in `--out`) entstehen drei Dateien:

- **`<name>.png`**: Diagramm.
  - Oben die Abweichung vom Zielton in Cent: grünes Toleranzband, graue Rohwerte, blaue geglättete Kurve, blau hinterlegt „im Bereich“, grüne Linie beim Treffer.
  - Werte außerhalb ±50 ct (falsche Oktave oder falscher Ton) erscheinen als rote Marken am Rand.
  - Unten der Pegel mit der Gate-Schwelle (orange).
- **`<name>.csv`**: ein Frame pro Zeile (alle 20 ms), Spalten:
  - `time_s`: Zeit in Sekunden
  - `frequency_hz`, `clarity`, `level_dbfs`: Messwerte des Frames
  - `calibration`, `passed_gate`: Kalibrierphase bzw. Gate durchgelassen (1/0)
  - `nearest_note`, `cents_from_nearest`: nächster Ton und Abweichung davon
  - `cents_from_target`, `smoothed_cents`: Abweichung vom Zielton, roh und geglättet
  - `verdict`, `phase`, `hold_progress`: Entscheidung der Treffer-Logik
- **`<name>.txt`**: Zusammenfassung, wie sie auch auf der Konsole erscheint. Sie enthält Treffer und Zeit bis zum Treffer, die Einstiegsabweichung, Mittel und Streuung beim Halten sowie die Zeit auf falscher Oktave bzw. falschem Ton.

## Obertöne messen

Mit `--spectrum` sucht das Werkzeug die gehaltenen Töne einer Aufnahme und misst je Ton die Stärke der Teiltöne. Daraus
entsteht der Klang des Drones ([docs/drone-klang.md](../../docs/drone-klang.md), [ADR 0006](../../docs/adr/0006-drone-klang.md)):

```sh
./gradlew :tools:replay:run --args="--spectrum testdata/local/drones/gr55.wav --a4 442 --name 'B&S GR55' --kotlin core/dsp/src/main/kotlin/io/github/blanaboo/stimmzug/dsp/synth/Gr55Spectrum.kt"
```

- **`<name>-obertoene.csv`**: jeder gefundene Ton mit Zeit, Ton, f0, Abweichung, Streuung und Pegel, dazu die
  Teiltöne 1–16 relativ zum stärksten und ihr Abstand zum Raumrauschen.
- **`<name>-obertoene.md`**: die Tabelle je Ton, für `docs/`.
- **`--kotlin <datei.kt>`**: die Klangtabelle für `core/dsp`. Der Objektname ist der Dateiname, `--name` das
  Instrument.

Unter Windows schneidet `gradlew.bat` Argumente mit `&` ab. Dann das installierte Werkzeug direkt aufrufen:
`./gradlew :tools:replay:installDist`, danach
`java -cp "tools/replay/build/install/replay/lib/*" io.github.blanaboo.stimmzug.tools.replay.MainKt --spectrum …`.

## Sitzungen des Audio-Labors

Statt einer Datei lässt sich ein ganzer Sitzungsordner des Audio-Labors (Debug-App, #33) auswerten:

```sh
./gradlew :tools:replay:run --args="testdata/local/lab/20261106-101500-tuba"
```

- **Zielton und Kalibrierung** kommen aus `takes.csv` und `segments.csv`. Kalibriert wird über den ersten
  Abschnitt, wenn darin niemand spielt.
- **`--detector`**, `--no-lowpass` und die Optionen der Treffer-Logik gelten wie bei einer einzelnen Datei.
- **Tonhöhe mit, Pegel ohne Tiefpass:** Abweichung, Klarheit und Oktavfehler kommen aus der Kette mit Tiefpass. Pegel,
  Trend und Teiltöne misst der Bericht am ungefilterten Signal, denn sie beschreiben, was das Gerät liefert.
- **Ausgabe** in `<sitzung>/auswertung/` (oder `--out`): `report.md`, `report.csv` und je Aufnahme Diagramm und CSV
  in `charts/`. Die Messgrößen beschreibt [docs/field-test/audio-spike.md](../../docs/field-test/audio-spike.md).

## Hinweise

- **Eigene Aufnahmen** gehören nach `testdata/local/`. Der Ordner wird nicht eingecheckt.
- **Pre-Listen-Phase:** Die Treffer-Logik startet ohne Vorhören direkt nach der Kalibrierung, denn das Timing des Spielers steckt bereits in der Aufnahme.
- **Parameter-Sweeps:** `Replay.run(samples, sampleRate, options)` ist eine reine Funktion und lässt sich aus einem Test oder Skript mit verschiedenen `ReplayOptions` mehrfach aufrufen.
