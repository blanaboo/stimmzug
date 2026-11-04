# Roadmap

## Idee

Intonation mit dem Ohr üben: Die App spielt einen exakt gestimmten Referenzton (Drone). Der Tubist passt seinen Ton an, bis keine Schwebung mehr zu hören ist. Die App hört über das Mikrofon mit und bestätigt akustisch und visuell, sobald der Ton **eine einstellbare Zeit (Standard 3 s) innerhalb einer einstellbaren Toleranz (Standard ±5 Cent)** liegt. Danach folgt automatisch der nächste Ton.

## Übungsablauf (freihändig – das Handy steht auf dem Notenständer)

1. Instrument (F-Tuba / B-Tuba) und Übung wählen (Tonfolge, Bereich, Tonart) → Start. Das Display bleibt an.
2. **Kalibrierung** (~3 s, nicht spielen): misst Raumgeräusch und Drone-Übersprechen ins Mikrofon.
3. Pro Ton: Drone blendet ein → **Vorhören** (Standard 1 s) → spielen → Ton lange genug in Toleranz → **Bestätigung** (Glockenklang, grüner Ring, optional Vibration) → Drone blendet aus → Atempause (Standard 2 s) → nächster Ton.
4. Pause, Zurück, Überspringen, Stopp jederzeit. Am Ende eine Zusammenfassung pro Ton.

## Tonerkennung für die Tuba

| Herausforderung | Lösung |
|---|---|
| Sehr tiefe Töne (B-Tuba-Pedal ≈ 29 Hz, Kernbereich ≈ 41–350 Hz; 1 Cent bei 41 Hz = 0,024 Hz) | Periodenschätzung im Zeitbereich (**MPM**, McLeod Pitch Method) mit FFT-beschleunigter Autokorrelation, Fenster ≥ 4 Perioden des Zieltons, parabolische Interpolation → Auflösung < 1 Cent |
| Handy-Mikrofone dämpfen unter 100 Hz → schwacher Grundton, Gefahr von Oktavfehlern | Periodizität funktioniert auch bei schwachem Grundton; **zielgeführte Suche** (der Zielton ist bekannt) + **Oktavprüfung**; Audioquelle `UNPROCESSED` (ohne Sprachfilter/AGC) |
| Natürliche Tonschwankungen | Median-Glättung, Gnadenzeit für kurze Aussetzer, Klarheits-Schwelle – alles einstellbar und im Praxistest kalibriert |
| Der Drone gelangt über den Lautsprecher ins Mikrofon | **Pegel-Gate** aus der Kalibrierung (die Tuba ist deutlich lauter), Hinweis auf Kopfhörer/externen Lautsprecher, Option „Drone eine/zwei Oktaven höher“ |

## Meilensteine

| | Inhalt | Release |
|---|---|---|
| **M0 Fundament** | Repo, Gradle-Gerüst, CI/CD (Checks, Emulator-Tests, signierte Releases), Templates | – |
| **M1 DSP-Kern** | Notenmathematik, FFT, MPM (+ YIN zum Vergleich), Pegel-Gate, Treffer-Logik, Tonfolgen, Drone-Synthese, Replay-Werkzeug, Genauigkeits-Benchmark | – |
| **M2 MVP** | Spike auf echtem Gerät, Übungs-Screen (Ohr-Modus + Live-Stimmgerät), Drone + Mikrofon, Auto-Weiter, Einstellungen, Onboarding/Kalibrierung, „Virtueller Tubist“ für Tests, Sitzungsprotokoll | v0.1.0 |
| **M3 Explorative Tests** | Test-Charters, Befunde als Issues, Fixes, Nachtests | v0.2.0 |
| **M4 Praxistest** | 1–2 Wochen Üben mit echter Tuba, Auswertung der Protokolle, Parameter-Feinjustierung | v0.3.x |
| **M5 Ausbau** | Eigene Tonlisten, Verlaufskurve, Statistik/Problemtöne, echte Tuba-Aufnahmen als Drone, Feinschliff; optional Intervalle (reine Stimmung) | v1.0 |

## Qualitätssicherung

1. **DSP-Unit-Tests** (JVM): synthetische Tuba-Signale (Obertöne, Einschwingen, Jitter, Rauschen, Mikrofon-Hochpass, Drone-Übersprechen) über 27–700 Hz und freie Tuba-Aufnahmen der University of Iowa (werden zur Testzeit geladen, nicht im Repo).
2. **App-Tests**: ViewModels, Einstellungen, Screenshot-Tests.
3. **E2E im Emulator** mit dem „Virtuellen Tubisten“ (simulierte Tuba als Audioquelle).
4. **Exploratives Testen** nach Charters (`docs/qa/`), Befunde → Issues → Fixes.
5. **Praxistest** mit echter Tuba (`docs/field-test/`).
