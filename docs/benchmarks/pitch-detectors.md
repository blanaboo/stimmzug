# Benchmark Tonhöhenerkennung: MPM vs. YIN

Synthetische Tuba-Töne (48000 Hz), Ziel = Nominalton, je Ton 6 Analysefenster.
Töne: Subkontra-A, Kontra-Des, Kontra-F, Kontra-A, großes Des, großes F, großes A, kleines des, kleines f, kleines a, des', f', a', des'', f''.

Fehler in Cent über alle stimmhaften Fenster in der richtigen Oktave (Abweichung ≤ 600 ct).

| Szenario | Verfahren | Median ct | 95 % ct | Max ct | Oktavfehler | stumm | ohne Tuba: Ton | ohne Tuba: Treffer |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| sauber (mf) | MPM | 0.009 | 0.027 | 0.064 | 0 | 0/90 | 0/90 | 0/90 |
| sauber (mf) | YIN | 0.026 | 0.114 | 0.120 | 0 | 0/90 | 0/90 | 0/90 |
| leise (p, -24 dBFS) | MPM | 0.019 | 0.055 | 0.078 | 0 | 0/90 | 0/90 | 0/90 |
| leise (p, -24 dBFS) | YIN | 0.027 | 0.116 | 0.184 | 0 | 0/90 | 0/90 | 0/90 |
| laut (f, -6 dBFS) | MPM | 0.038 | 0.136 | 0.188 | 0 | 0/90 | 0/90 | 0/90 |
| laut (f, -6 dBFS) | YIN | 0.058 | 0.154 | 0.162 | 0 | 0/90 | 0/90 | 0/90 |
| Handy-Mikro (Hochpass 120 Hz, Rauschen) | MPM | 0.028 | 0.083 | 0.106 | 0 | 0/90 | 0/90 | 0/90 |
| Handy-Mikro (Hochpass 120 Hz, Rauschen) | YIN | 0.039 | 0.137 | 0.196 | 0 | 0/90 | 0/90 | 0/90 |
| Handy-Mikro hart (Hochpass 150 Hz, Rauschen, Clipping) | MPM | 0.096 | 0.496 | 1.055 | 0 | 0/90 | 0/90 | 0/90 |
| Handy-Mikro hart (Hochpass 150 Hz, Rauschen, Clipping) | YIN | 0.107 | 0.618 | 0.965 | 0 | 0/90 | 0/90 | 0/90 |
| Drone eine Oktave höher über Lautsprecher (-24 dBFS) | MPM | 0.013 | 0.048 | 0.092 | 0 | 0/90 | 90/90 | 0/90 |
| Drone eine Oktave höher über Lautsprecher (-24 dBFS) | YIN | 0.031 | 0.111 | 0.164 | 0 | 0/90 | 90/90 | 0/90 |

**Fehlauslösungen** („ohne Tuba“): dieselben Störungen des Szenarios (Rauschen, Hochpass, Clipping, Drone-Übersprechen) ohne Tuba-Ton. „Ton“ = als stimmhaft gemeldete Fenster, „Treffer“ = davon innerhalb ±50 ct des Ziels (würden als gespielter Zielton zählen). Der übersprechende Drone eine Oktave höher ist ein echter Ton und wird daher als Ton erkannt, aber nie als Zielton; ihn auszublenden ist Aufgabe des Pegel-Gates (#13).
