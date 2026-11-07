# Audio-Spike: Tuba-Messung

Bezug: Spike #32, Werkzeug #33.

Bevor die App zuhören lernt, klären wir auf dem echten Handy drei Fragen:

1. Welche **Audioquelle** liefert tiefe Tuba-Töne am saubersten?
2. Ist das **untere oder das hintere Mikrofon** besser?
3. Wie stark stört der **Drone aus dem Handy-Lautsprecher** die Erkennung?

Dafür nimmt das **Audio-Labor** in der Debug-App nacheinander mit jeder Quelle und jedem Mikrofon auf. Du spielst
nur, was der Bildschirm sagt. Die Auswertung macht danach das Replay-Werkzeug.

## Für dich: Messung mit der Tuba (ca. 10 Minuten)

### Vorbereitung

- **Handy aufstellen** wie beim Üben: auf dem Notenständer, Display zu dir. Den Abstand von der Schallstückmitte zum
  Handy grob merken (z. B. „1 m“).
- **Ruhiger Raum**, Fenster zu. „Bitte nicht stören“ einschalten, damit kein Benachrichtigungston in die
  Aufnahmen kommt.
- **Keine Kopfhörer und keinen Bluetooth-Lautsprecher** verbinden: Der Drone soll aus dem Handy kommen.
- **Tuba warm spielen**, wie vor dem Üben.

### Ablauf

App **„Stimmzug Labor“** öffnen (eigenes Symbol neben „Stimmzug Debug“) und **„Tuba-Messung starten“** tippen. Vor
jedem Teil wartet die App, bis du **„Weiter“** tippst. Dazwischen läuft alles von selbst. Jede Aufnahme beginnt
mit einem kurzen Countdown, in dem du atmen kannst.

| Teil | Ton | Aufnahmen | Jede Aufnahme |
|---|---|---|---|
| 1 | **Kontra-F** (43,7 Hz) | 10 | 2 s still, dann 6 s Ton halten |
| 2 | **großes F** (87,3 Hz) | 10 | 2 s still, dann 6 s Ton halten |
| 3 | **Kontra-B** (58,3 Hz) mit Drone | 4 | 3 s nur zuhören, dann ohne abzusetzen: 5 s **etwas zu tief**, 6 s **sauber**, 5 s **etwas zu hoch** |

- Wenn die Anweisung **grün** ist, spielst du. In Teil 1 und 2 **immer gleich laut (mf)** und mit **gleichem
  Abstand** spielen, damit sich die Aufnahmen vergleichen lassen. Die Stimmung ist hier egal.
- In Teil 3 ist „etwas zu tief/hoch“ so weit, dass du eine deutliche Schwebung hörst, etwa 15–30 Cent. Vor
  Teil 3 die **Medienlautstärke** so einstellen, wie du mit Drone üben würdest.
- **Verspielt?** „Nochmal“ wiederholt die laufende Aufnahme. Im Countdown danach wiederholt es die vorige.
- **Abbrechen** ist jederzeit möglich. Alles bis dahin Aufgenommene bleibt gespeichert.

### Danach

Handy per USB anschließen und entsperren. Ich hole die Aufnahmen ab und werte sie aus. Dazu brauche ich von dir:

- F- oder B-Tuba?
- Abstand zum Handy und Raum (z. B. „Wohnzimmer, Teppich“)
- Ging bei einer Aufnahme etwas schief (Nummer, z. B. „T07 zu spät eingesetzt“)?
- Teil 3: Hast du den Drone beim Spielen gut gehört? Welche Medienlautstärke war angenehm?

## Zweite Messung: Drone über Lautsprecher und Kopfhörer (ca. 5 Minuten)

Bezug: #35. Die erste Messung hat gezeigt, dass der Drone aus dem Handy-Lautsprecher die Tuba für die Erkennung
überdeckt. Jetzt vergleichen wir den Lautsprecher mit einem Bluetooth-Kopfhörer, auch in höherer Lage. Der Drone ist
auf a' = 442 Hz gestimmt.

**Vorbereitung:**

- Handy wie bei der ersten Messung aufstellen, etwa 1 m Abstand.
- Den Bluetooth-Kopfhörer geladen bereitlegen, aber noch im Etui lassen.

**Ablauf:** im „Stimmzug Labor“ **„Drone-Messung starten“** tippen. Jede Aufnahme läuft wie Teil 3 der ersten
Messung: 3 s zuhören, dann ohne abzusetzen 5 s **etwas zu tief**, 6 s **sauber**, 5 s **etwas zu hoch**.

| Teil | Drone über | Töne |
|---|---|---|
| 1 | Handy-Lautsprecher, Medienlautstärke wie beim Üben | Kontra-B, großes F, kleines f, kleines b |
| 2 | Bluetooth-Kopfhörer: verbinden, einsetzen, Geräuschunterdrückung (ANC) aus oder niedrig, ein Ohrhörer reicht | dieselben 4 Töne |
| 3 | Handy-Lautsprecher, Kopfhörer bleibt verbunden, aber herausgenommen | Kontra-B |

- „Etwas zu tief/hoch“ heißt: so weit, dass du die Schwebung deutlich hörst.
- Ist der Kopfhörer vor Teil 2 nicht verbunden, wartet die App mit einem Hinweis.
- Teil 3 prüft, ob die App den Drone auch dann auf den Lautsprecher legen kann, wenn ein Kopfhörer verbunden ist. Landet
  er trotzdem im Kopfhörer, zeigt die App nach der Aufnahme eine Warnung. Dann einfach weiterspielen, das ist auch ein
  Ergebnis.

**Danach** brauche ich von dir:

- Hast du den Drone mit Kopfhörer gut gehört, und die Tuba auch?
- Welche ANC-Stufe, und ein oder zwei Ohrhörer?
- Ging bei einer Aufnahme etwas schief?

## Für die Entwicklung

### Selbsttest ohne Spieler

Prüft vorab, ohne Tuba, für jede Quelle und jedes Mikrofon:

- ob die Quelle sich öffnen lässt
- welches Mikrofon tatsächlich aufnimmt
- wie laut Raum und Drone ankommen
- ob ein gleichbleibender Ton leiser geregelt wird, also ob AGC oder Rauschunterdrückung eingreifen

Je Quelle und Mikrofon gibt es zwei Aufnahmen, mit Drone auf Kontra-B und auf kleines b. Das sind 24 Aufnahmen
und etwa 3 Minuten, in denen das Handy Drone-Töne spielt.

```powershell
. .\tools\qa\adb.ps1
Install-QaApk                        # app\build\outputs\apk\debug\app-debug.apk
Start-QaLab selbsttest               # Handy entsperrt; startet sofort (oder: tuba, drone)
Get-QaLabSessions -Latest            # -> testdata\local\lab\<sitzung>
```

### Tuba-Messung abholen und auswerten

```powershell
Get-QaLabSessions -Latest
.\gradlew :tools:replay:run --args="testdata/local/lab/<sitzung>"
```

Die Aufnahmen bleiben auf diesem Rechner, denn `testdata/local/` wird nicht eingecheckt.

Die Auswertung landet in `<sitzung>/auswertung/`:

- `report.md`: Geräteangaben, eine Tabelle je Aufnahme (Quelle, gemeldetes und tatsächlich aktives Mikrofon,
  Effekte, Rechenzeit der Erkennung) und eine Tabelle je Abschnitt.
- `report.csv`: die Abschnittstabelle maschinenlesbar.
- `charts/`: Diagramm und Frame-CSV je Aufnahme, wie beim Replay einer einzelnen Datei.

Messgrößen je Abschnitt, gemessen ab 0,5 s nach Abschnittsbeginn:

| Größe | Bedeutung |
|---|---|
| Pegel | Median der Frame-Pegel in dBFS |
| Trend | Pegel der letzten minus der ersten Sekunde. Wird ein gleichbleibender Ton (Drone, gehaltener Tuba-Ton) deutlich leiser, regelt AGC oder Rauschunterdrückung. |
| stimmhaft, Klarheit | Anteil erkannter Frames und Median ihrer Klarheit |
| Abweichung, Streuung | Median und Interquartilsabstand in Cent zum Ton, nur Frames innerhalb ±100 ct |
| Oktavfehler | Anteil der Frames eine Oktave über oder unter dem Ton |
| H1–H4 | Anteil des 1. bis 4. Teiltons an der Gesamtleistung in dB. Ein Mikrofon ohne Bass zeigt H1 deutlich unter H2. |

### Aufnahmedateien

Ort auf dem Handy: `/sdcard/Android/data/io.github.blanaboo.stimmzug.debug/files/lab/<JJJJMMTT-hhmmss>-<ablauf>/`.

| Datei | Inhalt |
|---|---|
| `Tnn-<quelle>-<mikro>-<ton>.wav` | 48 kHz, 16 Bit, mono, unverändert vom Gerät |
| `takes.csv` | je Aufnahme: Quelle, Mikrofon, Ton, Status, Abtastrate, Puffergröße, gemeldeter Eingang, aktive Mikrofone, AGC/NS/AEC an oder aus, Drone-Ausgang, Medienlautstärke, Rechenzeit der Erkennung (Anteil der Echtzeit) |
| `segments.csv` | Abschnitte je Aufnahme mit Start und Ende in Sekunden, spielt der Spieler, läuft der Drone |
| `session.txt` | Gerät, Android-Version, alle Eingänge und Mikrofone mit Position und (falls vom Hersteller angegeben) Frequenzgang |

## Ergebnisse

Alle Messungen auf dem OnePlus Nord 2 mit der B-Tuba des Besitzers, Abstand etwa 1 m. Ausführliche Tabellen stehen in
#32. Die Entscheidung steht in [ADR 0005](../adr/0005-audio-io.md), umgesetzt wird sie in #60 und #61.

### Selbsttest ohne Spieler (05.11.)

Sitzung `20261105-200857-selbsttest`.

- **Zwei Eingangswege:**
  - `VOICE_RECOGNITION`, `VOICE_PERFORMANCE`, `MIC` und `CAMCORDER` liefern dasselbe verarbeitete Signal. Die
    Mikrofonwahl wirkt dort nicht.
  - `UNPROCESSED` ist ein eigener, roher Weg mit etwa 25 dB weniger Verstärkung.
- **Keine Regelung:** Ein gleichbleibender Ton wird in keiner Quelle leiser geregelt (keine AGC, keine
  Rauschunterdrückung).
- **`VOICE_COMMUNICATION`** liefert digitale Stille.

### Erste Tuba-Messung (06.11., Drone über Lautsprecher, a' = 440 Hz)

Sitzung `20261106-221217-tuba`, 24 Aufnahmen.

- **Quellen:**
  - Alle erkennen Kontra-F und großes F zuverlässig.
  - Der verarbeitete Weg hat einen Limiter, die Spitzen liegen schon bei mf genau bei −1 dBFS.
  - `UNPROCESSED` hat mindestens 18 dB Reserve und einen 8–16 dB stärkeren Grundton.
- **Mikrofon:** Unten und hinten unterscheiden sich kaum, unten ist etwa 2 dB lauter.
- **Drone über Lautsprecher:**
  - Der Detektor meldet in allen 4 Aufnahmen den Drone statt der Tuba.
  - Ein Tiefpass bei 250 Hz vor der Erkennung behebt das.

### Zweite Messung (07.11., Lautsprecher gegen Bluetooth-Kopfhörer, a' = 442 Hz)

Sitzung `20261107-231315-drone`, alle 9 Aufnahmen ok.

- **Kopfhörer:** Bose QC Earbuds II, ANC aus, ein Ohrhörer. Drone und Tuba waren gut zu hören, auch die Schwebungen.
- **Medienlautstärke:** Lautsprecher 12/16, Kopfhörer 9/16. Android führt beide getrennt.
- **Tuba noch kalt:** Der Spieler musste viel arbeiten, um die Stimmung hochzubekommen.

| | Lautsprecher | Kopfhörer |
|---|---|---|
| Drone am Mikrofon, Spieler still | −27 … −32 dBFS | −75 … −79 dBFS, also Raumrauschen |
| Erkennung ohne Filter | folgt dem Drone, das Pegel-Gate bleibt zu | folgt der Tuba, 95–100 % stimmhaft |
| Erkennung mit Tiefpass max(250 Hz, 1,4 × Ton) | folgt der Tuba, Kontra-B bis kleines b | unverändert (≤ 1 ct) |

Wo die Tuba im Abschnitt „sauber“ lag (Median, mit Tiefpass, Replay bei a' = 442 Hz):

| Ton | Lautsprecher | Kopfhörer |
|---|---|---|
| Kontra-B | −10 ct (T01), −13 ct (T09) | −10 ct |
| großes F | −16 ct | −12 ct |
| kleines f | −25 ct | −17 ct |
| kleines b | −24 ct | −14 ct |

- **Tiefpass knapp über dem Grundton:**
  - Die Werte mit Lautsprecher stimmen mit einer unabhängigen Teiltonanalyse überein.
  - Bei höherer Grenzfrequenz zieht der Drone die Messung zum Zielton. Beim kleinen f ergaben 700 Hz −6 ct statt
    −25 ct, die App würde also fälschlich „sauber“ melden.
- **„Sauber“ lag überall 10–25 ct zu tief.** Das ist die kalte Tuba, nicht die Erkennung. Bei der ersten Messung mit
  440 Hz lag „sauber“ beim Kontra-B etwa bei 0 bis +9 ct. Passt zum Hinweis aufs Einspielen (#44).
- **„Etwas zu hoch“** blieb beim kleinen f und kleinen b mit Kopfhörer unter dem Drone. Mit der kalten Tuba reichten
  Luft und Ansatz nicht, um über den Drone zu kommen.
- **Teil 3:** Der Drone lief über den Lautsprecher, obwohl die Bose verbunden war. Die App kann den Ausgang also frei
  wählen.
- **Bluetooth-Mikrofon:** Der Eingang blieb immer das eingebaute Mikrofon.
- **Rechenzeit** der Erkennung (Debug-Version): 12–33 % eines Kerns.

### Folgerungen

- **Audioquelle `UNPROCESSED`**, Fallback `VOICE_RECOGNITION`.
- **Drone am besten über Kopfhörer**, der Lautsprecher bleibt wählbar.
- **Tiefpass vor der Erkennung**, Grenzfrequenz max(250 Hz, 1,4 × Zielton).
- **Offen:**
  - Rechenzeit in der Release-Version (#61).
  - Im Praxistest (M4): Lautsprecher-Betrieb über kleinem b, Bluetooth-Latenz beim Einblenden und Bestätigungsklang.
