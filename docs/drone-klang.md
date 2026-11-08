# Drone-Klang: gemessene Obertöne (#72)

Der Drone klingt wie die B-Tuba des Besitzers (B&S GR55). Er ist trotzdem exakt gestimmt, rauschfrei und beliebig
lang: Die App spielt keine Aufnahme ab. Der Synthesizer baut jeden Ton aus den Teiltonstärken, die an diesem Ton
gemessen wurden ([ADR 0006](adr/0006-drone-klang.md)). In den Einstellungen lässt sich unter „Drone-Klang“ auf
„Synthetisch“ (das alte Modell) umschalten.

## Aufnahme

- **Instrument:** B&S GR55, eingespielt. Stimmung 442 Hz, ein paar Cent darunter.
- **Töne:** Jeder Ton etwa 10 s in mf, ruhig und ohne Vibrato, chromatisch vom Kontra-B aufwärts bis zum kleinen h,
  dann vom Kontra-B abwärts bis zum Subkontra-B. Davor 20 s Stille für das Raumrauschen.
- **Kette:**
  - Sennheiser MD 421 U-5 mit Schalter auf M (ohne Bassabsenkung).
  - Etwa 50–60 cm vom Trichterrand, etwas höher als der Rand, schräg auf den Rand gerichtet.
  - Steinberg UR22 mk2, GarageBand, WAV mit 24 Bit und 44,1 kHz.
- **Ablage:** Die Aufnahme liegt nur lokal in `testdata/local/drones/` und gehört nicht ins Repository.

## Verfahren

`./gradlew :tools:replay:run --args="--spectrum <aufnahme.wav> --a4 442 --name <instrument> --kotlin <Tabelle.kt>"`
([tools/replay/README.md](../tools/replay/README.md#obertöne-messen)):

1. **Töne finden:** Gehaltene Töne sind Abschnitte mindestens 2 s lang, mehr als 20 dB über dem Raumrauschen und
   höchstens 40 dB unter den lautesten Stellen.
2. **Tonhöhe messen:** Gemessen wird ab 1 s nach Beginn bis 0,5 s vor Ende, mit der Erkennung der App (MPM, freie
   Suche). Ergebnis sind Median und Streuung.
3. **Teiltöne messen:** Gemittelte Spektren mit Hann-Fenstern von 0,37 s (Überlappung 50 %). Je Teilton k zählt die
   Leistung im Band (k ± ½)·f0, abzüglich der Leistung desselben Bands in der längsten Stille.
4. **Zusammenfassen:**
   - Aufnahmen desselben Tons werden in der Leistung gemittelt. Aufnahmen mit mehr als 20 ct Streuung bleiben außen
     vor.
   - Jeder Ton wird auf seinen stärksten Teilton bezogen (0 dB).
   - Die Tabelle eines Tons endet mit dem letzten Teilton, der mindestens −50 dB erreicht und mindestens 10 dB über
     dem Raumrauschen liegt.
   - Fehlende Töne werden aus den beiden nächsten gemessenen Nachbarn gemittelt.
5. **Tabelle schreiben:** Das Ergebnis geht als Kotlin-Tabelle nach `core/dsp`, hier `Gr55Spectrum.kt`, auf ganze dB
   gerundet.

## Ergebnis B&S GR55

Die ersten 16 Teiltöne in dB, bezogen auf den stärksten Teilton. „Teiltöne“ ist die Länge der Tabelle bis −50 dB.

| Ton | Aufnahmen | f0 Hz | Teiltöne | H1 | H2 | H3 | H4 | H5 | H6 | H7 | H8 | H9 | H10 | H11 | H12 | H13 | H14 | H15 | H16 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Subkontra-B | 1 | 30,0 | 56 | -35 | -13 | -5 | 0 | -1 | -5 | -1 | 0 | -4 | -3 | -4 | -3 | -4 | -2 | -6 | -4 |
| Subkontra-H | ergänzt | – | 56 | -31 | -12 | -4 | -3 | -1 | -3 | 0 | 0 | -3 | -1 | -3 | -2 | -4 | -3 | -6 | -5 |
| Kontra-C | ergänzt | – | 56 | -31 | -12 | -4 | -3 | -1 | -3 | 0 | 0 | -3 | -1 | -3 | -2 | -4 | -3 | -6 | -5 |
| Kontra-Des | 1 | 34,8 | 58 | -29 | -12 | -5 | -7 | -2 | -2 | 0 | -2 | -2 | -1 | -2 | -3 | -5 | -5 | -7 | -8 |
| Kontra-D | 1 | 37,2 | 51 | -25 | -10 | -6 | -2 | -4 | -1 | -5 | -1 | -3 | 0 | -3 | -5 | -6 | -8 | -9 | -10 |
| Kontra-Es | 1 | 39,1 | 46 | -22 | -8 | -2 | -2 | -3 | -1 | 0 | -1 | -1 | -4 | -4 | -4 | -11 | -13 | -12 | -15 |
| Kontra-E | 2 | 41,4 | 40 | -20 | -6 | -1 | -2 | -1 | 0 | -3 | -2 | -3 | -5 | -8 | -12 | -15 | -15 | -15 | -17 |
| Kontra-F | 1 | 43,7 | 94 | -19 | -6 | -6 | -4 | 0 | -3 | -3 | 0 | -3 | -7 | -10 | -12 | -14 | -16 | -17 | -18 |
| Kontra-Ges | 1 | 46,4 | 35 | -16 | -6 | -6 | -3 | -2 | 0 | -1 | -2 | -6 | -7 | -10 | -14 | -17 | -18 | -21 | -26 |
| Kontra-G | 1 | 48,8 | 31 | -16 | -6 | -4 | -5 | 0 | -3 | -2 | -5 | -8 | -14 | -15 | -15 | -21 | -22 | -24 | -30 |
| Kontra-As | 1 | 51,9 | 30 | -12 | -3 | -2 | 0 | -2 | 0 | 0 | -3 | -5 | -14 | -18 | -15 | -19 | -19 | -25 | -28 |
| Kontra-A | 1 | 54,8 | 28 | -12 | -4 | -2 | -1 | 0 | -3 | -3 | -6 | -12 | -18 | -20 | -17 | -18 | -24 | -30 | -31 |
| Kontra-B | 2 | 58,4 | 28 | -11 | 0 | -2 | -1 | -4 | -6 | -10 | -7 | -12 | -17 | -23 | -23 | -24 | -27 | -30 | -33 |
| Kontra-H | 1 | 62,7 | 26 | -15 | -3 | -5 | 0 | -2 | -8 | -17 | -18 | -18 | -17 | -20 | -23 | -27 | -29 | -32 | -38 |
| großes C | 1 | 65,5 | 29 | -12 | -3 | 0 | -2 | -1 | -5 | -14 | -13 | -13 | -15 | -18 | -25 | -26 | -25 | -30 | -37 |
| großes Des | 1 | 69,5 | 21 | -14 | -6 | -2 | 0 | -2 | -7 | -19 | -18 | -16 | -18 | -21 | -29 | -35 | -32 | -36 | -41 |
| großes D | 1 | 73,4 | 22 | -12 | -4 | 0 | -1 | -1 | -10 | -22 | -16 | -14 | -16 | -22 | -26 | -29 | -36 | -41 | -43 |
| großes Es | 1 | 77,9 | 16 | -10 | -4 | -1 | 0 | -4 | -9 | -28 | -18 | -18 | -23 | -28 | -40 | -44 | -49 | -48 | -50 |
| großes E | 1 | 82,0 | 17 | -9 | -4 | 0 | -2 | -7 | -18 | -22 | -17 | -21 | -24 | -35 | -43 | -46 | -48 | -51 | -51 |
| großes F | 2 | 87,2 | 13 | -6 | -3 | -3 | 0 | -9 | -22 | -17 | -19 | -26 | -33 | -42 | -46 | -48 |  |  |  |
| großes Ges | 1 | 92,9 | 17 | -6 | -3 | 0 | -2 | -10 | -24 | -18 | -23 | -27 | -37 | -43 | -46 | -49 | -45 | -49 | -53 |
| großes G | 1 | 98,6 | 13 | -7 | -3 | 0 | -3 | -13 | -21 | -22 | -26 | -30 | -40 | -44 | -45 | -47 |  |  |  |
| großes As | 1 | 104,4 | 12 | -6 | -2 | 0 | -3 | -13 | -24 | -25 | -28 | -38 | -48 | -50 | -47 |  |  |  |  |
| großes A | 1 | 110,4 | 9 | -4 | 0 | -3 | -11 | -22 | -20 | -29 | -39 | -47 |  |  |  |  |  |  |  |
| großes B | 1 | 117,1 | 9 | -1 | 0 | -1 | -15 | -16 | -21 | -32 | -42 | -47 |  |  |  |  |  |  |  |
| großes H | 1 | 124,1 | 10 | -2 | 0 | -4 | -23 | -21 | -31 | -45 | -45 | -52 | -46 |  |  |  |  |  |  |
| kleines c | 1 | 131,5 | 10 | 0 | 0 | -2 | -20 | -18 | -29 | -35 | -43 | -51 | -47 |  |  |  |  |  |  |
| kleines des | 1 | 139,6 | 7 | -4 | 0 | -7 | -24 | -29 | -36 | -42 |  |  |  |  |  |  |  |  |  |
| kleines d | 1 | 147,5 | 8 | -3 | 0 | -11 | -22 | -30 | -39 | -47 | -47 |  |  |  |  |  |  |  |  |
| kleines es | 1 | 155,9 | 9 | -2 | 0 | -19 | -15 | -27 | -39 | -42 | -41 | -49 |  |  |  |  |  |  |  |
| kleines e | ergänzt | – | 9 | 0 | -1 | -13 | -16 | -29 | -36 | -41 | -42 | -47 |  |  |  |  |  |  |  |
| kleines f | 1 | 175,7 | 10 | 0 | -4 | -9 | -19 | -32 | -35 | -42 | -44 | -46 | -48 |  |  |  |  |  |  |
| kleines ges | 1 | 186,9 | 8 | 0 | -11 | -13 | -26 | -31 | -39 | -46 | -49 |  |  |  |  |  |  |  |  |
| kleines g | 1 | 197,4 | 6 | 0 | -13 | -14 | -25 | -38 | -39 |  |  |  |  |  |  |  |  |  |  |
| kleines as | 1 | 209,1 | 6 | 0 | -19 | -17 | -30 | -38 | -46 |  |  |  |  |  |  |  |  |  |  |
| kleines a | 3 | 220,0 | 5 | 0 | -15 | -20 | -36 | -42 |  |  |  |  |  |  |  |  |  |  |  |
| kleines b | 3 | 231,2 | 6 | 0 | -13 | -29 | -41 | -40 | -48 |  |  |  |  |  |  |  |  |  |  |
| kleines h | 1 | 246,1 | 4 | 0 | -20 | -36 | -47 |  |  |  |  |  |  |  |  |  |  |  |  |

Auffällig:

- **Tiefe Lage:** Bis zum Kontra-B ist der Grundton 11–35 dB schwächer als die stärksten Teiltöne. Zwischen dem
  3. und 10. Teilton liegen viele fast gleich starke Teiltöne.
- **Mittellage:** Vom großen C bis zum großen G tragen die Teiltöne 2–5 den Klang. Oberhalb von etwa 400 Hz fällt es
  steil ab.
- **Höhe:** Ab dem kleinen c ist der Grundton der stärkste oder fast der stärkste Teilton. Ab dem kleinen ges liegt der
  Grundton mehr als 10 dB über allen anderen Teiltönen.
- **Ergänzt:** Subkontra-H, Kontra-C und kleines e fehlen in der Aufnahme und sind aus den Nachbartönen gemittelt.
