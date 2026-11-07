# Testgeräte

Echte Geräte, auf denen Stimmzug entwickelt und getestet wird. Die Angaben stammen von
`Get-QaDeviceInfo` aus [`tools/qa/adb.ps1`](../tools/qa/adb.ps1), siehe [Gerät erfassen](#gerät-erfassen).

## OnePlus Nord 2 5G (Handy des Besitzers)

Erfasst am 2026-11-05 (#4).

| | |
|---|---|
| Modell | OnePlus Nord 2 5G, `DN2103` (EU-Variante `DN2103EEA`), Codename `OP515BL1` |
| Chip | MediaTek Dimensity 1200 (`MT6893`), `arm64-v8a` |
| Android | 13 (API 33), OxygenOS `DN2103_11_F.58`, Sicherheitspatch 2026-01-01 |
| Ausgeliefert mit | Android 11 (API 30) |
| Display | 1080 × 2400, 480 dpi |

### Audio

| | |
|---|---|
| Audioquelle `UNPROCESSED` | **nicht zugesichert** (`config_supportAudioSourceUnprocessed = false`, d. h. `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` liefert `null`), **funktioniert aber** (#32): roher Weg ohne AGC und Rauschunterdrückung, mindestens 18 dB Reserve bei mf aus 1 m |
| Verarbeiteter Weg | `VOICE_RECOGNITION`, `VOICE_PERFORMANCE`, `MIC` und `CAMCORDER` liefern dasselbe Signal: Hochpass, Limiter (Spitzen schon bei mf bei −1 dBFS), etwa 25 dB mehr Verstärkung. `VOICE_COMMUNICATION` liefert digitale Stille. |
| Mikrofone | zwei eingebaute: unten (`BUILTIN_MIC`, `SPH1642HT5H`, laut Hersteller 80 Hz −2 dB) und Rückseite (`BACK_MIC`). Mit `UNPROCESSED` ist unten etwa 2 dB lauter, sonst kaum ein Unterschied. |
| Lautsprecher | gibt tiefe Grundtöne kaum wieder (Kontra-B erst ab etwa 200 Hz), der Drone kommt mit −27 bis −32 dBFS im Mikrofon an (`UNPROCESSED`, Medienlautstärke 12/16) |
| Bluetooth-Ausgabe | A2DP. `AudioTrack.setPreferredDevice` legt den Drone auf den Lautsprecher, auch wenn ein Kopfhörer verbunden ist. Lautsprecher und Kopfhörer haben getrennte Medienlautstärken. |
| Aufnahmeformat | PCM 16 Bit, 8/16/32/44,1/48 kHz, mono oder stereo |
| Aufnahmepfade | normal, schnell (`FAST`), AAudio-MMAP (`MMAP_NOIRQ`) |
| Externe Eingänge | Headset mit Kabel, USB-Audio (`USB_DEVICE`, `USB_HEADSET`), Bluetooth-SCO |
| Wiedergabe | 48 kHz; schneller Pfad mit 256 Frames HAL-Puffer (5,3 ms) |
| Feature `audio.low_latency` | ja |
| Feature `audio.pro` | nein |
| AAudio-MMAP-Richtlinie | 2 (automatisch) |

### Folgen für Stimmzug

Gemessen im Spike #32, Ergebnisse in [`docs/field-test/audio-spike.md`](field-test/audio-spike.md#ergebnisse),
Entscheidung in [ADR 0005](adr/0005-audio-io.md).

- **Audioquelle `UNPROCESSED`**, obwohl das Gerät sie nicht zusichert. Die App prüft sie durch Öffnen und nimmt
  sonst `VOICE_RECOGNITION`.
- **Standardmikrofon:** Die App wählt kein Mikrofon aus.
- **Drone am besten über Kopfhörer:** Über Bluetooth-Kopfhörer (Bose QC Earbuds II, ANC aus) kommt der Drone nicht
  mehr messbar im Mikrofon an.
- **Lautsprecher-Betrieb:** Er braucht den Tiefpass vor der Erkennung. Bis kleines b ist das gemessen.
- **Externes Mikrofon als Ausweg.** Falls die eingebauten Mikrofone im Bass nicht reichen, nimmt das Gerät
  USB-Audio-Mikrofone an. Bisher war das nicht nötig.

### Verbindung zum Entwicklungsrechner

- **USB:** Unter Windows 10 ist kein zusätzlicher Treiber nötig. Ohne USB-Debugging meldet sich das Handy nur
  als MIDI-Gerät. Mit USB-Debugging erscheint es in `adb devices`, auch im USB-Modus „Nur laden“.
- **WLAN-Debugging koppeln (einmalig):** Entwickleroptionen → „Debugging über WLAN“ → „Gerät über einen
  Kopplungscode koppeln“, dann am PC `adb pair <IP>:<Kopplungsport> <Code>`. Mit gesperrtem Bildschirm schläft
  das WLAN des Handys ein und der PC erreicht es nicht, also den Bildschirm beim Koppeln anlassen.
  Meldet `adb pair` einen „protocol fault“, unter „Gekoppelte Geräte“ nachsehen: Die Kopplung kann trotzdem
  geklappt haben.
- **WLAN-Debugging verbinden:** `adb connect <IP>:<Port>` mit IP und Port aus „Debugging über WLAN → IP-Adresse &
  Port“ (ein anderer Port als beim Koppeln). Nach einem Neustart oder Aus- und Einschalten von „Debugging über
  WLAN“ ändert sich der Port. `adb mdns services` hat das Handy an diesem PC nicht gefunden, der Port muss also
  von Hand nachgeschlagen werden.
- Entwickleroptionen liegen unter Einstellungen → Systemeinstellungen → Entwickleroptionen; freigeschaltet
  werden sie über Einstellungen → Über das Gerät → Version → 7× auf „Build-Nummer“.

## Gerät erfassen

Neues Gerät per USB oder WLAN verbinden, dann in Windows PowerShell im Repository:

```powershell
. .\tools\qa\adb.ps1
Get-QaDevices                 # Gerät sichtbar?
Get-QaDeviceInfo | Format-List
```

Die Ausgabe als neuen Abschnitt in diese Datei übernehmen.
