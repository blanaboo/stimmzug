# ADR 0003: Pegel-Gate – Marge bezogen auf den Tuba-Pegel

- Status: angenommen
- Datum: 2026-11-05
- Bezug: #13, [ADR 0002](0002-pitch-detection-mpm.md)

## Kontext

Der Drone kommt über den Handy-Lautsprecher auch ins Mikrofon. MPM erkennt ihn zuverlässig als Ton, im Einklang
genau auf dem Zielton. Ohne Gate würde die App einen Treffer melden, obwohl niemand spielt. Deshalb misst die
Kalibrierung (~3 s, Drone an, Spieler still) Raumgeräusch und Drone-Übersprechen. Danach lässt das Gate nur Frames
durch, die deutlich lauter sind.

Der naheliegende Ansatz „Schwelle = Kalibrierpegel + 10 dB“ scheitert an der Phasenlage. Spielt die Tuba sauber im
Einklang (oder eine Oktave unter dem Drone), liegen ihre Obertöne auf denen des Drones und stehen in fester Phase
zueinander. Je nach Phase verstärken sie sich oder löschen sich teilweise aus. Eine Tuba, die 10 dB lauter ist als
der Drone, ergibt zusammen mit ihm:

- im ungünstigsten Fall nur +6,7 dB, wenn alle Obertöne gegenphasig sind: (√10 − 1)²
- im günstigsten Fall +12,4 dB, wenn alle gleichphasig sind

Gemessen (synthetisch, 15 Töne × 8 Phasenlagen, sauber im Einklang): +6,7 bis +12,3 dB. Ein Gate bei +10 dB bliebe
also oft zu, und zwar gerade dann, wenn sauber gespielt wird. Bei Schwebung (leicht verstimmt) schwankt der Pegel
im selben Bereich.

## Entscheidung

1. **Kalibrierpegel** = 90. Perzentil der Frame-Pegel der Kalibrierphase, mindestens −70 dBFS. Der gleichmäßige
   Drone ist damit voll erfasst, ein kurzes Husten oder Klicken verschiebt den Wert kaum. Die Untergrenze verhindert
   bei stummem Eingang, dass das Gate bei jedem Geräusch öffnet.
2. **Marge** (Standard 10 dB) heißt: Eine Tuba, die mindestens so viel lauter ist als der Kalibrierpegel, wird sicher
   erkannt, unabhängig von der Phasenlage.
3. Das Gate **öffnet** deshalb beim ungünstigsten Gesamtpegel einer solchen Tuba, abzüglich 1 dB Messtoleranz:
   Kalibrierpegel + 20·log10(10^(Marge/20) − 1) − 1 dB, mindestens aber Kalibrierpegel + 3 dB.
   Bei 10 dB Marge sind das **+5,7 dB**.
4. **Hysterese** 3 dB: Einmal offen, schließt das Gate erst unter Kalibrierpegel + 2,7 dB.
5. Ein Frame zählt nur als Spieler, wenn er stimmhaft ist **und** das Gate offen ist (`LevelGate.passes`).

## Konsequenzen

- **Messwerte:** Bei Tuba +10 dB liegt der leiseste Frame bei +6,66 dB, also ≈ 1 dB über der Schwelle. Bei reinem
  Drone liegt der lauteste Frame bei +0,18 dB, also ≈ 5,5 dB darunter.
- **Szenario-Tests** decken alle 15 Testtöne ab: Einklang und Oktave, sauber und schwebend, Drone bis −12 dBFS.
- **Drone wird lauter:** Das Gate gilt für den Drone-Pegel der Kalibrierung. Wird der Drone danach lauter (Lautstärke
  geändert, oder ein Ton, den der Lautsprecher besser wiedergibt), kann er das Gate öffnen. Vorschlag für #14 und M2:
  in der Vorhör-Phase jedes Tons (Drone an, Spieler wartet) nachkalibrieren oder die Schwelle anheben.
- **Kleine Margen:** Unter ≈ 8,3 dB greift die Untergrenze von +3 dB. Dann ist die Erkennung bei ungünstiger Phase
  nicht mehr garantiert.
- **Echounterdrückung** wäre eine Alternative, denn das Drone-Signal ist bekannt. Sie ist aber deutlich aufwendiger,
  und mit der Audioquelle `UNPROCESSED` liefert das System sie nicht mit. Sie kommt erst infrage, wenn der
  Praxistest (M4) sie nötig macht.
- Alle Werte stammen aus synthetischen Signalen und werden im Praxistest (M4) überprüft.

## Nachtrag: Schwelle pro Ton (#24, 2026-11-08)

Die Folge „Drone wird lauter“ ist mit `NoteLevelGate` gelöst. Der Tiefpass vor der Erkennung
([ADR 0005](0005-audio-io.md)) macht das nötig, denn mit ihm hängt der gemessene Drone-Pegel vom Zielton ab.

- **Messen:**
  - Ab 0,4 s nach Beginn des Tons zählen die Frames. Davor liegen das Einblenden des Drones und das
    Analysefenster.
  - Je 0,2 s ergibt der Median der Pegel einen Wert. Der leiseste Wert bisher gilt als Drone-Pegel dieses Tons.
- **Anheben:** Die Schwelle steigt um den Abstand dieses Pegels zum Kalibrierpegel, höchstens um 10 dB. Sie sinkt
  nie unter die Schwelle der Sitzung.
- **Spieler setzt zu früh ein:**
  - Setzt er im Vorhören ein, bleiben die Abschnitte davor beim Drone-Pegel.
  - Spielt er schon beim Start des Tons, zeigt sein erster Atemzug den Drone-Pegel, und die Schwelle sinkt wieder.
  - Schlimmstenfalls, wenn ein leiser Abschnitt kein Drone war, gilt einfach die Schwelle der Sitzung wie bisher.
- **Szenario-Tests** (`NoteLevelGateScenarioTest`) für die Töne MIDI 34, 41, 53 und 65:
  - Ein Drone 6 dB über der Kalibrierung führt nicht zum Treffer. Mit der Schwelle der Sitzung allein wäre es einer.
  - Ein Spieler, der 0,3 s vor dem Hören einsetzt, trifft wie gewohnt.
  - Ein Spieler, der schon beim Start spielt, trifft nach dem ersten Atemzug.
