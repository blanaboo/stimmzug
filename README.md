# Stimmzug

**Intonationstrainer für Tuba (Android).** Die App spielt einen exakt gestimmten Referenzton (Drone), hört über das Mikrofon mit und bestätigt, sobald dein Ton lange genug sauber im Toleranzbereich liegt – dann geht es automatisch zum nächsten Ton. Ziel ist, Intonation mit dem **Ohr** zu trainieren, nicht mit den Augen.

> **Status:** in Entwicklung – siehe [Meilensteine](../../milestones) und [Roadmap](docs/roadmap.md).
>
> **Hintergrundwissen:** [Tuba und Intonation](docs/wissen/intonation.md): was ein Cent ist, Stimmton, Temperatur und Einspielen, Naturtonreihe, Ventilkombinationen, Schwebungen.

## Funktionen (geplant)

- Drone mit synthetischem Tuba-Klang: exakt gestimmt, endlos haltbar, Stimmton a' einstellbar (430–450 Hz, Standard 442 Hz wie bei Blechbläsern üblich)
- Tonerkennung speziell für tiefe Töne (bis ca. 25 Hz, inkl. Pedaltöne): MPM-Verfahren, zielgeführte Suche, Oktavprüfung
- Treffer, wenn der Ton eine einstellbare Zeit (Standard 3 s) innerhalb einer einstellbaren Toleranz (Standard ±5 Cent) liegt
- **Ohr-Modus** (Fortschritt ohne Richtungsanzeige), optional Live-Stimmgerät und Verlaufskurve
- Tonfolgen: chromatisch, Tonleitern, zufällig, eigene Listen
- **Instrumentprofile** für jede eigene Tuba (B, F, Es, C; Ventile, Kompensation, Trigger), deutsche Notennamen (B/H, „Kontra-B“) oder international
- **Problemtöne** erkennen und gezielt üben, **Fortschritt pro Tuba** mit Verlauf, Serien und Meilensteinen
- **Stimm-Assistent:** misst Naturtöne, Ventile und Kombinationen und gibt Hinweise für Hauptstimmzug und Ventilzüge
- **Keine Internet-Berechtigung** – Audio wird ausschließlich auf dem Gerät verarbeitet
- Zuerst für Android (später im Play Store), danach fürs iPhone

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
