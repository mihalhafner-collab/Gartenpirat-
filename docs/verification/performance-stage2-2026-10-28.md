# Etappe 2 – Ladepfad und Listen-Cache, 28.10.2026

Vergleich gegen die messinstrumentierte Etappe 1, gleicher bestehender Testlauf,
je40 Wiederholungen pro Weg. Cloud Chromium,360×840 CSS-Pixel; ausschließlich
300 Kunden,5000 Arbeiten,3000 Fotometadaten und500 Rechnungen aus der synthetischen
Factory. Schnittstelle simuliert, keine Android-/SQLite- oder Pixelmessung.
Langsamste5% = Mittel der zwei langsamsten Stichproben. Zeiten in ms.

| Bedienweg | Etappe1 Mittel | Etappe1 langsamste5% | Etappe2 Mittel | Etappe2 langsamste5% |
| --- | ---: | ---: | ---: | ---: |
| App-Start | 1823.3 | 3107.6 | 1458.2 | 1754.7 |
| Heute → Büro | 279.1 | 529.8 | 179.3 | 244.6 |
| Büro → Heute | 268.2 | 998.2 | 181.1 | 285.0 |
| Kunden öffnen | 368.4 | 602.1 | 292.4 | 355.7 |
| Suche | 157.8 | 209.2 | 150.9 | 164.5 |
| Speichern | 1003.0 | 1882.9 | 757.1 | 1012.0 |

Die gemessenen mittleren Wechselzeiten sind im Cloudlauf niedriger; der gemeinsam
genutzte Host und der warme Browsercache begrenzen die Aussage. Kein physisches
Geschwindigkeitsversprechen. Suche enthält weiterhin die feste130ms-Debouncewartezeit.
Methodik und Vorhermessung stehen im Etappe1-Bericht; P95 und Größen im JSON.

Änderungen:

- `load()` übernimmt `sync_status`; die native Bridge ergänzt vorher über denselben
  bestehenden Statushelfer alle Warn-/Hintergrundfelder. Der zweite explizite
  Vollaufbau durch `getSyncStatus()` entfällt. Alte Bridges ohne eingebetteten
  Status behalten den bisherigen Fallback; Lesefehler behalten die letzte Ansicht.
- `mobile.html`: Zustand-/Pendingwechsel invalidieren den Cache. Nur aktive Listen
  werden berechnet; Filter, Anzeigegrenze und Tageswechsel sind Teil des Schlüssels.
  Gleiche Seiten behalten ihre DOM-Knoten. Keine zusätzliche App oder Datenbank.
- `design-concept.js`, `billing-ui.js`: keine versteckten Listen-/Vorschlagsberechnungen.
  Das Wiederaufnehmen eines Arbeitsentwurfs bereitet notwendige Kundenoptionen
  ausdrücklich vor, bevor dieselben gespeicherten Formularfelder eingesetzt werden.
- `today-ui.js` und `revier-solo-ui.js`: Datenableitungen/Übersicht bleiben bei gleichem
  Zustand erhalten. Timer bleiben live, Tages-/Minutenwechsel aktualisieren die
  zeitabhängige Heute-Darstellung. Explizites Neulesen und native Änderungsmeldungen
  umgehen alte Caches; fehlgeschlagene Übersicht wird nie als erfolgreiche Leere gecacht.
- `business-actions-ui.js`: Sicherungsstatus bleibt beim unveränderten Wechsel erhalten;
  Zustandwechsel, Einstellungen, Fokus und Rückkehr aus dem Hintergrund lesen neu.
  `PixelMcpReadTools` liest diesen einzelnen technischen Status über den bereits
  vorhandenen typisierten Repositorypfad ohne unnötigen Geschäftssnapshot. Schema,
  Validierung und Antwortflags bleiben unverändert; keine Sicherung wird ausgelöst.

Abnahmetests im bestehenden Kontext-/Navigationstest: ein getState/getSyncStatus-
Vollaufbau im expliziten load-Pfad mit allen Statusfeldern; beim unveränderten
Heute→Büro→Heute null Zustands-/Owner-Aufrufe, null HGDataOrder-Neusortierungen,
identische Daten-/DOM-Referenzen. Anlage, Änderung, Löschung und native Änderungsmeldung
aktualisieren sichtbare und später geöffnete Listen. Native Start-/Fachabfragen
außerhalb des load/getSyncStatus-Paares sind damit nicht pauschal als ein einziger
Gesamt-App-Snapshot nachgewiesen; native Profilierung bleibt offen.

Bestehende Tests auf explizite neue Testdaten/Neulesesignale angepasst: sie greifen
auf Auswahllisten erst beim Öffnen der jeweiligen Maske zu und signalisieren
Änderungen der simulierten nativen Datenquelle. Fach-, Entwurfs- und Fehlerprüfungen
bleiben erhalten. Zwei historische Prüfprobleme werden separat ausgewiesen:
P1-Altkatalog-Schemaversatz und der alte Build97-Barrierefreiheitstest mit zwei
Tabs/entferntem Action-Dock. Keine Schutzprüfung übersprungen oder abgeschwächt.
Aktuelle Drei-Tab-Navigation und Revier-Bedienung bei320/360/426px und200% Schrift
werden durch die aktuellen Navigation-/Revier-/Gerätefototests geprüft.

Pixel, TalkBack, native Laufzeitmessung und physischer Update-Datenerhalt offen.
Keine Installation, Migration oder Rechnungsfreigabe. Etappen3–5 nicht umgesetzt.
