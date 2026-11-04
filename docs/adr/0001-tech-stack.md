# ADR 0001: Technologie-Stack und Modulstruktur

- Status: angenommen
- Datum: 2026-11-04

## Kontext

Die App verarbeitet Audio in Echtzeit (Mikrofon + Drone-Wiedergabe). Die Erkennungslogik muss sehr gründlich und schnell testbar sein, auch ohne Gerät, und soll später mit echten Aufnahmen kalibriert werden.

## Entscheidung

- **Kotlin + Jetpack Compose (Material 3)**, MVVM mit `ViewModel` + `StateFlow`, Coroutines, DataStore. Kein DI-Framework – ein manueller `AppContainer` genügt für die Projektgröße.
- **Modul `core/dsp` ist reines Kotlin/JVM ohne Android-Abhängigkeiten.** Tonhöhenerkennung, Drone-Synthese, Notenmathematik, Treffer-Logik und Tonfolgen liegen dort und werden mit JUnit auf der JVM getestet (schnell, in der CI ohne Emulator). Dieselbe Implementierung nutzen das Replay-Werkzeug und die App.
- **Audio-I/O mit `AudioRecord`/`AudioTrack`** (Kotlin) statt Oboe/NDK: Die Latenz ist für einen Dauerton und eine Erkennung mit ~100 ms Fenster unkritisch; kein NDK hält den Build einfach.
- **Zeitbasis der Logik = Audio-Samplezeit**, nicht die Wanduhr → deterministisch testbar.
- **Eigene DSP-Implementierung** (kein TarsosDSP, da GPL-3.0).
- minSdk 26, compile/targetSdk 37.

## Konsequenzen

- Plattformspezifisches (Audio-Routing, Berechtigungen) bleibt dünn im `app`-Modul und wird über Instrumented-/E2E-Tests abgedeckt.
- Die austauschbare Audioquelle (`AudioInput`: Mikrofon, WAV-Datei, „Virtueller Tubist“) ermöglicht E2E-Tests ohne echte Tuba.
