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
| **M2 MVP** | Spike auf echtem Gerät, Instrumentprofile (#40), Übungs-Screen (Ohr-Modus + Live-Stimmgerät), Tuba-Wahl und Einspiel-Hinweis beim Übungsstart (#44), Drone + Mikrofon, Auto-Weiter, Einstellungen (Stimmton a' Standard 442 Hz), Onboarding/Kalibrierung, „Virtueller Tubist“ für Tests, Sitzungsprotokoll mit Messwerten pro Versuch (#37) | v0.1.0 |
| **M3 Explorative Tests** | Test-Charters, Befunde als Issues, Fixes, Nachtests | v0.2.0 |
| **M4 Praxistest** | 1–2 Wochen Üben mit echter Tuba, Auswertung der Protokolle, Parameter-Feinjustierung | v0.3.x |
| **M5 Ausbau** | Problemtöne erkennen und gezielt üben (#38), Stimm-Assistent für Hauptstimmzug und Ventilzüge (#39), Fortschritt pro Tuba mit Verlauf, Serien und Meilensteinen (#45), eigene Tonlisten, echte Tuba-Aufnahmen als Drone, Feinschliff; optional Intervalle (reine Stimmung) und Einspielübungen (Konzept folgt) | v1.0 |
| **M6 Veröffentlichung** | Google Play Store mit geschlossenem Test, Datenschutzerklärung und Store-Eintrag; optional F-Droid (#48) | v1.x im Store |
| **M7 iPhone-App** | Gemeinsamer Kern mit Kotlin Multiplatform, Audio für iOS, Spike auf dem iPhone 17 Pro, TestFlight und App Store (#49) | iOS v1.0 |

## Plattformen

- **Android zuerst:** Die App wird bis v1.0 für Android entwickelt und im Alltag erprobt. Danach wird sie im Play
  Store veröffentlicht (M6).
- **iPhone danach (M7):**
  - Der Kern in `core/dsp` hat keine Android-Abhängigkeiten und soll später als Kotlin-Multiplatform-Modul auch auf
    iOS laufen. Neuer Kern-Code kommt deshalb möglichst ohne Java-spezifische APIs aus.
  - Neu zu schreiben ist für iOS vor allem die Audio-Ein- und -Ausgabe. Die Oberfläche lässt sich mit Compose
    Multiplatform größtenteils teilen.
- **Ohne Internet auf beiden Plattformen:** Messwerte, Profile und Fortschritt bleiben auf dem Gerät.

## Qualitätssicherung

1. **DSP-Unit-Tests** (JVM): synthetische Tuba-Signale (Obertöne, Einschwingen, Jitter, Rauschen, Mikrofon-Hochpass, Drone-Übersprechen) über 27–700 Hz und freie Tuba-Aufnahmen der University of Iowa (werden zur Testzeit geladen, nicht im Repo).
2. **App-Tests**: ViewModels, Einstellungen, Screenshot-Tests.
3. **E2E im Emulator** mit dem „Virtuellen Tubisten“ (simulierte Tuba als Audioquelle).
4. **Exploratives Testen** nach Charters (`docs/qa/`), Befunde → Issues → Fixes.
5. **Praxistest** mit echter Tuba (`docs/field-test/`).
