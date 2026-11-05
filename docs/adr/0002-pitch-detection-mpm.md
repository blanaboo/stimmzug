# ADR 0002: Tonhöhenerkennung mit MPM, zielgeführt mit Oktavprüfung

- Status: angenommen
- Datum: 2026-11-04
- Bezug: #11, #12, Benchmark [`docs/benchmarks/pitch-detectors.md`](../benchmarks/pitch-detectors.md)

## Kontext

Tuba-Töne reichen bis ca. 29 Hz (B-Tuba-Pedal) hinunter; 1 Cent entspricht dort ~0,02 Hz. Handy-Mikrofone und der
Android-Audiopfad dämpfen unter ~100 Hz stark, der Grundton ist im Signal oft schwächer als die 2.–4. Harmonische –
klassische Ursache für Oktavfehler. FFT-Spitzen sind bei vertretbarer Fensterlänge viel zu grob.

## Entscheidung

1. **Periodenschätzung im Zeitbereich** auf Basis von Autokorrelation r(τ) und Energieterm m(τ), FFT-beschleunigt.
   Verglichen wurden **MPM** (NSDF n(τ) = 2r/m) und **YIN** (CMNDF).
2. **MPM** wird verwendet: im Benchmark durchgehend genauer als YIN (sauber: Median 0,009 ct vs. 0,026 ct;
   harte Handy-Bedingungen: 95-%-Wert 0,50 ct vs. 0,62 ct), keine Oktavfehler, keine Fehlauslösung bei Rauschen
   und kein Treffer ohne gespielten Ton in irgendeinem Szenario.
   Die NSDF-Spitzenhöhe dient direkt als Klarheitsmaß (Stimmhaftigkeit). YIN bleibt als Vergleich im Benchmark.
3. **Zielgeführte Suche**: Da die App den Zielton kennt, wird nur ±6 Halbtöne um die Zielperiode gesucht. Danach,
   in dieser Reihenfolge:
   - *Oktave tiefer*: Der beste Peak im Bereich der doppelten Periode liegt deutlich über dem Kandidaten.
   - *Oktave höher*: Kandidat ist auch bei halber Periode (fast) gleich periodisch → Ton liegt eine Oktave höher.
   So wird ein Ton in der falschen Oktave mit seiner wahren Frequenz gemeldet (→ Hinweis „falsche Oktave“) statt
   fälschlich als Treffer. Die Reihenfolge ist wichtig: Die Prüfung „Oktave höher“ vergleicht nur ein Verhältnis,
   das bei einem Ton eine Oktave tiefer auch ein schwaches Nebenmaximum samt ebenso schwachem Wert bei halber
   Periode erfüllen kann (zunächst so bei Zielen von 55 Hz und 87 Hz aufgetreten: Ergebnis „stumm“ statt
   „Oktave tiefer“). Getestet wird beides über den ganzen Tonumfang unter Handy-Mikrofon-Bedingungen.
   Die tiefere Oktave wird **nur innerhalb des Messbereichs (ab 25 Hz)** genommen. Sehr tiefe Töne im ff zeigen
   teils Periodenverdopplung (eine Subharmonische auf halber Frequenz), die ebenso periodisch ist wie der Ton
   selbst. In den Iowa-Aufnahmen (#18) wurden E1 und F1 dadurch zeitweise als 20,8 bzw. 21,8 Hz gemeldet, also
   unter jedem spielbaren Tuba-Ton.
4. **Fensterplanung**: ≥ 5 Perioden des Zieltons (mind. 60 ms); Dezimierung um Faktor 4/2 für Ziele unter 90/180 Hz
   (4.-Ordnung-Butterworth als Anti-Aliasing). Das tiefste Ziel braucht ~180 ms Audio.
5. **Zeitbasis** ist die Samplezeit (`PitchTracker`), Hop 20 ms.

## Konsequenzen

- Freie Suche (ohne Ziel) nutzt MPMs Key-Maximum-Regel; sie dient nur Anzeigezwecken.
- Die Kennzahlen stammen aus synthetischen Signalen. Echte Aufnahmen (Iowa-Korpus #18, Praxistest M4) können die
  Parameter (Klarheits-Schwelle, Oktav-Schwellen) noch verschieben; der Benchmark läuft in jeder CI mit.
- **Iowa-Korpus** (37 Töne C1–C4, ff, [Bericht](../benchmarks/iowa-tuba.md)):
  - **Oktavfehler:** Mit der Grenze bleibt ein einziger Frame übrig, im Anschlag von A1.
  - **Ziel eine Oktave daneben:** Auch dann wird bei allen Tönen die gespielte Tonhöhe gemeldet.
  - **Pedaltöne:** Bei C1–F1 sind nur 56–80 % der Frames klar genug (Klarheit ≥ 0,8). Ob das in der Praxis
    reicht, zeigt erst M4.
