# ADR 0004: Statische Analyse mit detekt 2.0 (Alpha)

- Status: angenommen
- Datum: 2026-11-05
- Bezug: #6

## Kontext

Bisher prüfen `./gradlew check` nur ktlint (über Spotless, Formatierung) und Android Lint. ktlint findet keine
Fehlerquellen wie `!!` auf nullbaren Werten, zu komplexe Funktionen oder ungenutzten Code.

detekt ist das übliche Werkzeug dafür. Stand November 2026:

- **1.23.8** (Februar 2025) ist die letzte stabile Version. Sie ist gegen Kotlin 2.0 gebaut und bricht mit
  Kotlin 2.4 ab. Ein Umweg, der den Kotlin-Compiler für detekt auf 2.0 festnagelt, versteht neuere Sprachmittel
  nicht und bekommt keine Updates mehr.
- **2.0.0-alpha.6** (August 2026) ist gegen Kotlin 2.4.10, Gradle 9.6 und AGP 9.3 gebaut. Stimmzug nutzt
  Kotlin 2.4.21, Gradle 9.8 und AGP 9.5. Eine stabile 2.0 gibt es noch nicht.

## Entscheidung

1. **detekt 2.0.0-alpha.6** läuft in allen Modulen mit Kotlin-Code (`core:dsp`, `tools:replay`, `app`).
   - Die Version ist im Version-Catalog festgelegt.
   - Updates kommen über Dependabot, wie bei allen anderen Abhängigkeiten.
   - detekt betrifft nur den Build und landet nicht in der App. Das Alpha-Risiko ist deshalb klein: Im
     schlimmsten Fall ändert sich bei einem Update die Konfiguration oder es gibt neue Meldungen.
2. **Mit Typauflösung.** `check` führt `detektMain`/`detektTest` (JVM-Module) sowie `detektDebug`,
   `detektDebugUnitTest` und `detektDebugAndroidTest` (App) aus.
   - Erst mit Typauflösung greifen Regeln wie `UnsafeCallOnNullableType` (`!!`).
   - Die einfache `detekt`-Task würde nur einen Teil davon wiederholen und ist deshalb aus `check` genommen.
3. **Standardregeln mit wenigen begründeten Anpassungen** in [`config/detekt/detekt.yml`](../../config/detekt/detekt.yml):
   - **`MagicNumber` aus:** In der Signalverarbeitung und Musiktheorie sind Zahlen wie 12, 1200 oder 20·log10 selbst
     sprechend. Im ersten Lauf waren 108 von 125 Meldungen von dieser Art.
   - **Compose:** `@Composable`-Funktionen dürfen wie Typen heißen. `@Preview`-Funktionen gelten nicht als
     ungenutzt.
   - **Frühe `return`s und `throw`s für Randfälle** (Guard Clauses) zählen nicht.
   - **`when` mit einfachen Zweigen** zählt nicht zur Komplexität.
   - **Parameter mit Standardwert** zählen nicht als lange Parameterliste.
   - **Private Hilfsfunktionen** zählen nicht als zu viele Funktionen.
4. **Keine Baseline.** Die übrigen Meldungen wurden im Code behoben:
   - `!!` ersetzt, sodass der Compiler die Invariante sieht.
   - Lange Zeichenfunktion im Replay-Werkzeug aufgeteilt.
   - Farbpalette der App in eine eigene Datei verschoben.

   Neue Meldungen müssen also behoben oder an der Stelle mit `@Suppress` und Begründung versehen werden.

## Konsequenzen

- Die detekt-Tasks brauchen zusammen gut 30 s (gemessen auf 4 Kernen, alle neu ausgeführt). Ohne Änderung am
  Code überspringt Gradle sie.
- Beim Update auf eine neue Alpha oder auf 2.0 stabil kann die Konfiguration nachzuziehen sein, z. B. umbenannte
  Regeln oder Optionen. detekt prüft die Konfiguration und meldet unbekannte Einträge.
- Sobald detekt 2.0 stabil ist, auf diese Version wechseln und diese ADR aktualisieren.
