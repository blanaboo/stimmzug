# Übungs-Mitschnitt (Debug-Build)

Der Debug-Build speichert jede Übung (#75). Wenn beim Üben etwas nicht stimmt, etwa „Ton war sauber, wurde aber nicht
erkannt“, lässt sich die Übung damit am Schreibtisch genau so nachspielen, wie das Handy sie gehört hat.

Der Release-Build schneidet nichts mit. Die App hat keine INTERNET-Berechtigung: Die Mitschnitte bleiben auf dem
Handy, bis sie per USB abgeholt werden.

## Was gespeichert wird

Ort auf dem Handy: `/sdcard/Android/data/io.github.blanaboo.stimmzug.debug/files/practice/<JJJJMMTT-hhmmss>/`.
Es bleiben nur die letzten 5 Übungen. Eine Minute braucht etwa 6 MB.

| Datei | Inhalt |
|---|---|
| `input.wav` | das Mikrofonsignal, 16 Bit, mono, in der Abtastrate des Eingangs (48 kHz), unverändert vom Gerät |
| `timeline.csv` | der Verlauf, alle 50 ms und bei jedem Wechsel von Ton, Phase oder Urteil. Die Spalten stehen in der Tabelle unten. |
| `session.txt` | App-Version, Gerät, Beginn, Einstellungen (a′, Toleranz, Haltedauer, Gnadenzeit), Übung und Tonfolge. Nach dem Ende kommen Eingang (z. B. `UNPROCESSED`), Ausgabe-Route (z. B. Bluetooth-Kopfhörer), Dauer, Ergebnis und gegebenenfalls ein Fehler dazu. |

Spalten von `timeline.csv`:

| Spalte | Inhalt |
|---|---|
| `time_s` | Zeit in der Aufnahme in Sekunden, passend zu `input.wav` |
| `index` | Nummer des Tons in der Übung, ab 0 |
| `note`, `midi`, `target_hz` | Zielton mit deutschem Namen, MIDI-Nummer und Frequenz |
| `session_phase` | `CALIBRATING`, `PLAYING`, `PAUSED` oder `FINISHED` |
| `note_phase` | `PRE_LISTEN`, `LISTENING`, `IN_RANGE`, `HIT`, `PAUSE` oder `DONE` |
| `verdict` | `SILENT`, `IN_TOLERANCE`, `FLAT`, `SHARP`, `WRONG_OCTAVE` oder `WRONG_NOTE` |
| `cents` | geglättete Abweichung vom Zielton |
| `hold` | Haltefortschritt von 0 bis 1 |
| `route` | Ausgabe des Drones: `HEADPHONES`, `SPEAKER`, `OTHER` oder `UNKNOWN` |

## Abholen

Handy per USB anschließen, dann in Windows PowerShell:

```powershell
. .\tools\qa\adb.ps1
Get-QaPracticeRecordings -Latest     # -> testdata\local\practice\<zeitstempel>
```

`testdata/local/` ist von Git ausgeschlossen. Aufnahmen vom echten Spielen bleiben auf diesem Rechner.

## Auswerten

Einen Abschnitt mit dem Zielton aus `timeline.csv` durch die Erkennung spielen, siehe
[tools/replay/README.md](../../tools/replay/README.md):

```sh
./gradlew :tools:replay:run --args="testdata/local/practice/<zeitstempel>/input.wav --target C2 --a4 442 --calibrate 2.5"
```
