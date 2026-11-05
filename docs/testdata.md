# Testdaten

## Tuba-Aufnahmen der University of Iowa (Regressionstest)

Der Regressionstest `IowaTubaRegressionTest` (Modul `:tools:replay`) prüft die Tonhöhenerkennung an echten
Tuba-Aufnahmen. Ergebnis: [benchmarks/iowa-tuba.md](benchmarks/iowa-tuba.md).

### Quelle

- **Herkunft:** University of Iowa Electronic Music Studios, *Musical Instrument Samples* (MIS),
  <https://theremin.music.uiowa.edu/MIS.html>, Tuba (ab 2012):
  <https://theremin.music.uiowa.edu/MIS-Pitches-2012/MISTuba2012.html>
- **Instrument:** Mirafone 1865U CC-Tuba, gespielt von David Spies (November 2003)
- **Aufnahme:** reflexionsarmer Raum, Earthworks-Mikrofon in 5 Fuß Abstand, 44,1 kHz, 16 Bit
- **Umfang:** ff, C1–C4, 37 Einzeltöne zu je ca. 1,8 s

### Nutzungsbedingungen

Laut der Iowa-Seite dürfen die Aufnahmen „downloaded and used for any projects, without restrictions“ werden. Eine
weitere Lizenz ist nicht angegeben.

Stimmzug verteilt die Dateien **nicht**. Sie liegen nicht im Repository und nicht in der App, sondern werden zur
Testzeit vom Original-Server geladen.

### Ablage und Download

- **Gradle-Task:** `:tools:replay:downloadIowaTuba` läuft automatisch vor den Tests von `:tools:replay`.
- **Speicherort:** `testdata/cache/iowa-tuba-ff/`, ist von Git ausgeschlossen und bleibt bei `clean` erhalten.
- **Prüfsummen:** SHA-256 in `tools/replay/src/test/resources/iowa/tuba-ff.sha256`. Eine falsche Prüfsumme bricht
  den Build ab.
- **Ohne Netz** (`./gradlew --offline` oder Server nicht erreichbar) gibt es eine Warnung, und der Test wird
  **übersprungen**.

### Auffälligkeiten der Aufnahmen

- **Stimmung:** Die Töne liegen im Mittel **+20 ct** über der Nominalfrequenz bei 440 Hz. Der Spieler hat also etwa
  auf a′ = 445 Hz gestimmt. Die Abweichungen je Ton sind Intonation, nicht Messfehler.
- **„Eb1“ klingt auf 40,68 Hz**, das ist E1 −22 ct bzw. Es +77 ct. MPM und YIN messen beide diesen Wert, er
  stammt also aus der Aufnahme.
- **Instabile tiefste Töne:** C1–F1 schwanken innerhalb des Tons um bis zu ±30 ct. Im ff zeigen sie teils
  **Periodenverdopplung**, also eine Subharmonische auf der halben Frequenz. Deshalb sind dort nur 56–80 % der
  Frames klar genug.
- **Folge für den Detektor:** Die Subharmonische führte zu Oktavfehlern (z. B. 20,8 Hz statt E1). Seitdem nimmt der
  Detektor die tiefere Oktave nur noch, wenn sie in seinem Messbereich (ab 25 Hz) liegt, siehe
  [ADR 0002](adr/0002-pitch-detection-mpm.md).

### Baseline aktualisieren

Der Test vergleicht den Median je Ton (±1 ct), den Anteil klarer Frames (−5 Prozentpunkte) und die Oktavfehler mit
der Baseline `tools/replay/src/test/resources/iowa/tuba-ff-baseline.csv`.

Nach einer **gewollten** Änderung an der Erkennung:

1. `./gradlew :tools:replay:test` ausführen.
2. `tools/replay/build/reports/iowa/tuba-ff.md` prüfen.
3. `tuba-ff-baseline.csv` von dort nach `tools/replay/src/test/resources/iowa/` kopieren.
4. `tuba-ff.md` als `docs/benchmarks/iowa-tuba.md` übernehmen.
