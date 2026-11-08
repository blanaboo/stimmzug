# ADR 0006: Drone-Klang aus gemessenen Obertönen einer echten Tuba

- Status: angenommen
- Datum: 2026-11-08
- Bezug: #72, Messung und Verfahren in [`docs/drone-klang.md`](../drone-klang.md),
  Lautsprecher-Übersprechen in [ADR 0005](0005-audio-io.md)

## Kontext

Der Drone (`DroneSynth`) erzeugte seinen Klang bisher aus einem einfachen Modell (`TubaSpectrum`): eine glatte
Hüllkurve, für jeden Ton dieselbe Form, nur gegen die Frequenz verschoben. Der Besitzer wollte einen Drone, der wie
eine echte Tuba klingt. In #72 standen zwei Wege zur Wahl:

1. **Aufnahmen abspielen:** echte Töne auf den Cent korrigieren und als Schleife abspielen.
2. **Obertöne messen:** nur die Stärke der Teiltöne je Ton messen und den Synthesizer damit füttern.

Der Besitzer hat sich für Weg 2 entschieden. Am 08.11. hat er seine B-Tuba (B&S GR55) chromatisch vom
Subkontra-B bis zum kleinen h aufgenommen, mit MD 421 und UR22 in 50–60 cm Abstand.

## Entscheidung

1. **Der Drone nimmt die gemessenen Teiltonstärken des nächstgelegenen Tons** (`MeasuredSpectrum`,
   Tabelle `Gr55Spectrum`).
   - Tonhöhe, Phasenlage (Schroeder) und Ein- und Ausblenden bleiben wie bisher. Der Drone ist weiterhin exakt
     gestimmt, rauschfrei und beliebig lang.
   - Unter- und oberhalb des gemessenen Bereichs gilt der tiefste bzw. höchste gemessene Ton.
2. **Die Tabelle wird erzeugt, nicht von Hand gepflegt:** `replay --spectrum <aufnahme> --kotlin <datei.kt>`.
   - Ins Repository kommen nur die gemessenen Teiltonstärken, nicht die Aufnahme.
   - Weitere Instrumente (F-Tuba) kommen mit derselben Messung als weitere Tabelle dazu.
3. **„Aufnahme“ ist Standard, „Synthetisch“ bleibt als Einstellung** („Drone-Klang“). So kann der Besitzer beide
   nach Gehör vergleichen.

## Folgen

- **Klang von Ton zu Ton:**
  - Tiefe Töne haben einen sehr schwachen Grundton (−19 dB beim Kontra-F, bis −35 dB darunter) und viele kräftige
    Obertöne.
  - Ab dem kleinen f führt der Grundton.
  - Benachbarte Töne unterscheiden sich so, wie sie es auf der Tuba tun. Der Drone ist damit weniger gleichförmig
    als das Modell.
- **Lautsprecher:**
  - Nach Lautsprecher-Modell (Hochpass 500 Hz) und Tiefpass vor der Erkennung kommt im Mittel 1,6 dB mehr Drone
    ins Mikrofon als mit dem Modell, im ungünstigsten Ton (großes As) 3,6 dB.
  - Der Rest-Zug über den Lautsprecher (ADR 0005: etwa 10 % bei gleichem Pegel) kann damit etwas größer werden.
  - Über Kopfhörer, die empfohlene Ausgabe, ändert sich nichts.
- **Raum und Mikrofon:** Die Messung enthält den Raum und das MD 421. Der Abstand von 50–60 cm hält deren Einfluss
  klein. Bei 1 m schwankte der Grundton gegenüber der Oktave um ±10 dB von Ton zu Ton.
- **Andere Instrumente:** Für die F-Tuba gibt es noch keine Messung. Sie bekommt vorerst denselben Klang.
- **Tests:** `TubaSpectrum` bleibt das Modell für synthetische Testsignale (virtueller Tubist, Szenario-Tests).
