# Übungsprotokoll (#37)

Die App speichert jede Übung mit den Messwerten jedes Versuchs. Daraus lernen später die Problemton-Auswertung
(#38), der Stimm-Assistent (#39) und die Auswertung des Praxistests.

- **Wann gespeichert wird:** Eine Übung kommt ins Protokoll, sobald sie endet: fertig gespielt, beendet oder
  verlassen. Übungen ohne einen einzigen Ton (nur Kalibrierung) werden nicht gespeichert.
- **Wo:** Nur auf dem Handy, in `files/session_log.jsonl`, eine Übung pro Zeile (JSON mit Versionsnummer). Neue
  Felder kommen mit Standardwerten dazu, ältere Zeilen bleiben lesbar.
- **Teilen:** Einstellungen → Übungsprotokoll → **Als CSV teilen** öffnet das Teilen-Menü (z. B. Mail oder Drive).
  Die App selbst sendet nichts und hat keine Internet-Berechtigung.

## Versuche

Ein Versuch ist ein gespielter Ton: vom Einsetzen des Drones bis zum Treffer oder bis der Spieler weitergeht. Ein Ton,
der nach „Zurück“ oder einer Pause noch einmal kommt, ist ein neuer Versuch. Das Ergebnis eines Versuchs:

| Ergebnis | Bedeutung |
|---|---|
| `HIT` | Treffer |
| `SKIPPED` | weiter oder zurück, ohne Treffer |
| `STOPPED` | Übung beendet oder pausiert, ohne Treffer |

## Griff

Die App hört nur die Tonhöhe. Den Griff leitet sie aus dem Instrumentprofil ab, wie eine übliche Grifftabelle:

1. **Naturton:** der nächste Naturton auf oder über dem Ton. Der 7. und 11. Naturton werden gemieden, sie liegen zu
   weit daneben.
2. **Ventile:** die übliche Kombination für den Abstand. 1+2 statt 3, 4 statt 1+3, 2+4 statt 1+2+3.
3. **Pedal:** Ist der Ton für den 2. Naturton zu tief, bleibt nur das Pedal.

Beispiele B-Tuba mit 4 Ventilen: großes C = 3. Naturton mit 4, Kontra-H = 3. Naturton mit 2+4, kleines g =
8. Naturton mit 1+2. Alternativgriffe kann der Spieler später pro Ton markieren.

## Spalten der CSV

Eine Zeile pro Versuch, vorne die Daten der Übung. Komma als Trenner, Punkt als Dezimalzeichen.

| Spalte | Inhalt |
|---|---|
| `session_start` | Beginn der Übung, Ortszeit (ISO 8601) |
| `profile`, `tuba_key`, `valve_count`, `extra_valves`, `compensating`, `triggers` | Instrumentprofil. `triggers`: `main` = Hauptzug, Zahlen = Ventilzüge |
| `a4_hz`, `tolerance_cents`, `hold_sec` | Einstellungen |
| `audio_source` | Mikrofonquelle, z. B. `UNPROCESSED` |
| `drone_route`, `drone_timbre` | Wo der Drone klang (`HEADPHONES` mit Gerätename, `SPEAKER`) und welcher Klang (`RECORDED`, `SYNTHETIC`) |
| `ambient_db`, `threshold_db` | Raum- und Drone-Pegel der Kalibrierung und die Schwelle des Pegel-Gates in dBFS |
| `warm_up` | Antwort auf den Einspiel-Hinweis des Tages (#44): `WARMED_UP` („Bin eingespielt“), `WARMED_UP_NOW` (mit der Einspiel-Uhr der App), leer ohne Antwort an diesem Tag |
| `warm_up_sec` | wie lange die Einspiel-Uhr lief |
| `warm_up_age_min` | Minuten von der Antwort bis zum Beginn dieser Übung |
| `exercise` | Übung, z. B. `KEY_NOTES B-Dur Kontra-F–kleines f UP` |
| `attempt` | laufende Nummer des Versuchs in der Übung, um Ermüdung zu sehen |
| `index` | Position des Tons in der Übung |
| `note`, `midi`, `target_hz` | Zielton |
| `partial`, `valves` | Naturton und Ventile des Standardgriffs, `0` = offen |
| `start_s` | Zeit seit Übungsbeginn beim Einsetzen des Drones |
| `previous_midi`, `interval` | vorheriger Ton und Intervall in Halbtönen |
| `status` | `HIT`, `SKIPPED` oder `STOPPED` |
| `time_to_hit_s` | Zeit vom Ende des Vorhörens bis zum Treffer |
| `entry_cents` | Einstieg: Median der Abweichung in den ersten 0,5 s nach dem Einsetzen, bevor nachkorrigiert wird |
| `hold_mean_cents`, `hold_spread_cents` | Mittelwert und Streuung beim Halten bis zum Treffer |
| `wrong_octave_share`, `wrong_note_share` | Anteil der Hörzeit in falscher Oktave bzw. auf falschem Ton |
| `listened_s` | Hörzeit: vom Ende des Vorhörens bis zum Treffer oder Ende des Versuchs |
| `display` | Anzeige beim Spielen: `EAR`, `TUNER` oder `MIXED` (gewechselt). Wer die Nadel sieht, korrigiert schneller. |
| `drone` | Drone lief (bisher immer `true`) |

## Einspielen (#44)

Vor der ersten Übung des Tages fragt die App „Schon eingespielt?“. Eine kalte Tuba liegt leicht 10–20 Cent zu tief,
grob 3 Cent pro Grad Lufttemperatur im Rohr, und der Ansatz braucht ein paar Minuten.

- **„Bin eingespielt“** startet die Übung.
- **„Jetzt einspielen“** öffnet die Einspiel-Uhr. „Eingespielt – Übung starten“ beendet sie und startet die Übung.

Die Antwort gilt bis Mitternacht. Jede Übung des Tages trägt sie mit ihrem Alter in Minuten. So erkennt die
Auswertung (#38), ob Abweichungen am Anfang vom Einspielen kommen.
