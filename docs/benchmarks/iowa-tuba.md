# Regressionstest: Tuba-Aufnahmen der University of Iowa

Mirafone 1865U CC-Tuba, ff, C1–C4 (Quelle und Bedingungen: [testdata.md](../testdata.md)).
Ziel = Nominalton bei a′ = 440 Hz. Ausgewertet wird der stabile Teil jedes Tons:
Pegel höchstens 12 dB unter dem Maximum,
stimmhaft mit Klarheit ≥ 0,8.
Erkennung wie in der App mit Tiefpass vor der Erkennung bei max(250 Hz, 1,4 × Ziel) ([ADR 0005](../adr/0005-audio-io.md)).
Die Abweichungen sind die **Intonation des Spielers**, nicht Messfehler der Erkennung.

| Ton | Nominal Hz | Median ct | 10 % ct | 90 % ct | stimmhaft | Oktavfehler | Ziel 8va höher | Ziel 8va tiefer |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Kontra-C (C1) | 32,70 | +12,2 | -10,2 | +30,4 | 85 % | 0 | +12,3 | – |
| Kontra-Des (D♭1) | 34,65 | -4,4 | -28,7 | +7,1 | 85 % | 0 | -5,4 | – |
| Kontra-D (D1) | 36,71 | -15,5 | -66,2 | -5,2 | 87 % | 0 | -17,5 | – |
| Kontra-Es (E♭1) | 38,89 | +76,8 | +72,0 | +85,1 | 93 % | 0 | +77,6 | – |
| Kontra-E (E1) | 41,20 | +17,1 | +5,1 | +32,7 | 99 % | 0 | +18,4 | – |
| Kontra-F (F1) | 43,65 | -19,8 | -39,7 | -3,7 | 96 % | 0 | -21,1 | – |
| Kontra-Ges (G♭1) | 46,25 | +24,6 | +10,5 | +32,1 | 99 % | 0 | +21,7 | – |
| Kontra-G (G1) | 49,00 | -2,6 | -33,7 | +6,7 | 98 % | 0 | -2,1 | – |
| Kontra-As (A♭1) | 51,91 | -13,4 | -75,1 | -2,2 | 99 % | 0 | -14,4 | -13,1 |
| Kontra-A (A1) | 55,00 | +18,3 | +5,2 | +26,9 | 99 % | 0 | +18,2 | +18,2 |
| Kontra-B (B♭1) | 58,27 | +28,3 | +21,4 | +43,9 | 99 % | 0 | +29,3 | +27,6 |
| Kontra-H (B1) | 61,74 | +30,6 | +24,5 | +39,1 | 97 % | 0 | +30,8 | +30,4 |
| großes C (C2) | 65,41 | +24,0 | +18,7 | +26,3 | 99 % | 0 | +23,8 | +23,7 |
| großes Des (D♭2) | 69,30 | -12,6 | -22,8 | -5,4 | 97 % | 0 | -12,2 | -13,2 |
| großes D (D2) | 73,42 | +6,5 | +1,6 | +12,7 | 97 % | 0 | +6,9 | +6,4 |
| großes Es (E♭2) | 77,78 | +22,6 | +13,2 | +32,5 | 100 % | 0 | +22,6 | +22,3 |
| großes E (E2) | 82,41 | +27,0 | +19,7 | +34,2 | 100 % | 0 | +27,0 | +26,9 |
| großes F (F2) | 87,31 | +16,1 | +4,4 | +19,3 | 99 % | 0 | +16,1 | +15,9 |
| großes Ges (G♭2) | 92,50 | +19,4 | +8,9 | +32,8 | 100 % | 0 | +19,4 | +19,8 |
| großes G (G2) | 98,00 | +19,7 | +16,2 | +23,5 | 100 % | 0 | +19,7 | +19,6 |
| großes As (A♭2) | 103,83 | +14,8 | +4,6 | +18,8 | 100 % | 0 | +14,8 | +15,2 |
| großes A (A2) | 110,00 | +31,9 | +22,8 | +34,7 | 100 % | 0 | +31,9 | +32,0 |
| großes B (B♭2) | 116,54 | +37,0 | +31,2 | +41,5 | 100 % | 0 | +37,3 | +37,1 |
| großes H (B2) | 123,47 | +29,6 | +24,7 | +31,9 | 100 % | 0 | +29,4 | +29,7 |
| kleines c (C3) | 130,81 | +16,5 | +12,5 | +21,0 | 100 % | 0 | +16,4 | +16,5 |
| kleines des (D♭3) | 138,59 | +16,1 | +11,4 | +34,9 | 100 % | 0 | +16,0 | +16,1 |
| kleines d (D3) | 146,83 | +0,6 | -1,3 | +6,9 | 100 % | 0 | +0,7 | +0,6 |
| kleines es (E♭3) | 155,56 | +2,0 | -2,0 | +6,3 | 100 % | 0 | +2,0 | +1,9 |
| kleines e (E3) | 164,81 | +32,4 | +17,1 | +42,3 | 100 % | 0 | +33,8 | +32,4 |
| kleines f (F3) | 174,61 | +30,1 | +20,2 | +33,1 | 100 % | 0 | +30,1 | +30,1 |
| kleines ges (G♭3) | 185,00 | +26,0 | +21,4 | +29,8 | 100 % | 0 | +25,8 | +26,0 |
| kleines g (G3) | 196,00 | +35,1 | +19,1 | +57,4 | 100 % | 0 | +35,1 | +35,1 |
| kleines as (A♭3) | 207,65 | +23,7 | +15,9 | +28,1 | 100 % | 0 | +24,0 | +23,7 |
| kleines a (A3) | 220,00 | +26,8 | +23,8 | +29,0 | 100 % | 0 | +26,8 | +26,6 |
| kleines b (B♭3) | 233,08 | +26,8 | +23,0 | +32,7 | 100 % | 0 | +26,8 | +27,0 |
| kleines h (B3) | 246,94 | +10,6 | +6,0 | +14,8 | 100 % | 0 | +10,6 | +10,5 |
| c' (C4) | 261,63 | +15,1 | +9,8 | +18,4 | 100 % | 0 | +15,1 | +15,1 |

Median über alle Töne: +19,4 ct (der Spieler hat etwa auf a′ = 445 Hz gestimmt). „Ziel 8va höher/tiefer“: gemessene Abweichung, wenn das Ziel eine Oktave neben dem gespielten Ton liegt – die Erkennung muss trotzdem die gespielte Tonhöhe melden.
