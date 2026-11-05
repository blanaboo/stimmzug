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
| Audioquelle `UNPROCESSED` | **nicht unterstützt** (`config_supportAudioSourceUnprocessed = false`, d. h. `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` liefert `null`) |
| Mikrofone | zwei eingebaute: unten (`BUILTIN_MIC`) und Rückseite (`BACK_MIC`) |
| Aufnahmeformat | PCM 16 Bit, 8/16/32/44,1/48 kHz, mono oder stereo |
| Aufnahmepfade | normal, schnell (`FAST`), AAudio-MMAP (`MMAP_NOIRQ`) |
| Externe Eingänge | Headset mit Kabel, USB-Audio (`USB_DEVICE`, `USB_HEADSET`), Bluetooth-SCO |
| Wiedergabe | 48 kHz; schneller Pfad mit 256 Frames HAL-Puffer (5,3 ms) |
| Feature `audio.low_latency` | ja |
| Feature `audio.pro` | nein |
| AAudio-MMAP-Richtlinie | 2 (automatisch) |

### Folgen für Stimmzug

- **Audioquelle im M2-Spike vergleichen.** Die Roadmap setzt auf `UNPROCESSED`, das Gerät sichert diese Quelle
  aber nicht zu. Im Spike werden deshalb `UNPROCESSED`, `VOICE_RECOGNITION` und `MIC` auf dem Nord 2 verglichen:
  Pegel und Klarheit tiefer Töne, Frequenzgang unter 100 Hz, ob Rauschunterdrückung oder automatische
  Verstärkung (AGC) eingreifen. `VOICE_RECOGNITION` soll laut Android-Kompatibilitätsdefinition ohne
  Rauschunterdrückung und AGC arbeiten. Für Frequenzen unter 100 Hz macht Android für keine Quelle Zusagen.
- **Welches Mikrofon?** Das Handy steht beim Üben auf dem Notenständer. Ob das untere oder das hintere Mikrofon
  die Tuba besser aufnimmt, klärt ebenfalls der Spike.
- **Externes Mikrofon als Ausweg.** Falls die eingebauten Mikrofone im Bass nicht reichen, nimmt das Gerät
  USB-Audio-Mikrofone an.

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
