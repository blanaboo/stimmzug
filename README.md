# Stimmzug

**Intonationstrainer für Tuba (Android).** Die App spielt einen exakt gestimmten Referenzton (Drone), hört über das Mikrofon mit und bestätigt, sobald dein Ton lange genug sauber im Toleranzbereich liegt – dann geht es automatisch zum nächsten Ton. Ziel ist, Intonation mit dem **Ohr** zu trainieren, nicht mit den Augen.

> **Status:** in Entwicklung – siehe [Meilensteine](../../milestones) und [Roadmap](docs/roadmap.md).

## Funktionen (geplant)

- Drone mit synthetischem Tuba-Klang: exakt gestimmt, endlos haltbar, Stimmton A4 einstellbar (430–450 Hz)
- Tonerkennung speziell für tiefe Töne (bis ca. 25 Hz, inkl. Pedaltöne): MPM-Verfahren, zielgeführte Suche, Oktavprüfung
- Treffer, wenn der Ton eine einstellbare Zeit (Standard 3 s) innerhalb einer einstellbaren Toleranz (Standard ±5 Cent) liegt
- **Ohr-Modus** (Fortschritt ohne Richtungsanzeige), optional Live-Stimmgerät und Verlaufskurve
- Tonfolgen: chromatisch, Tonleitern, zufällig, eigene Listen
- Profile für F-Tuba und B-Tuba, deutsche Notennamen (B/H, „Kontra-B“) oder international
- **Keine Internet-Berechtigung** – Audio wird ausschließlich auf dem Gerät verarbeitet

## Projektstruktur

| Modul | Inhalt |
|---|---|
| `core/dsp` | Reines Kotlin/JVM: Tonhöhenerkennung, Drone-Synthese, Notenmathematik, Treffer-Logik – ohne Android, vollständig unit-getestet |
| `app` | Android-App (Jetpack Compose): UI, Audio-Ein-/Ausgabe, Einstellungen |
| `tools/` | Analyse- und Testwerkzeuge (Replay von Aufnahmen, adb-Helfer) |
| `docs/` | Roadmap, Architektur, Entscheidungen (ADR), Testprotokolle |

## Entwickeln

Voraussetzungen: JDK 21, Android SDK (`ANDROID_HOME`).

```sh
./gradlew check                      # Formatierung, Lint, Unit-Tests, Coverage
./gradlew assembleDebug              # Debug-APK bauen
./gradlew connectedDebugAndroidTest  # Instrumented-Tests auf Emulator/Gerät
./gradlew spotlessApply              # Code formatieren
```

## Arbeitsweise

Jede Änderung beginnt mit einem Issue → Branch → Pull Request („Fixes #N“) → CI grün → Squash-Merge. Releases entstehen über Tags `vX.Y.Z` und enthalten eine signierte APK.

## Installation

APK aus den [Releases](../../releases) laden – oder mit [Obtainium](https://github.com/ImranR98/Obtainium) die Repo-URL eintragen, dann aktualisiert sich die App automatisch.

Vorabversionen (Tags mit Bindestrich, z. B. `v0.0.1-m0`) findet Obtainium nur, wenn beim Hinzufügen der App
„Vorabversionen einbeziehen“ eingeschaltet ist.

## Lizenz

[MIT](LICENSE)
