# Etappe 1 – synthetische Cloud-Browsermessung, 28.10.2026

40 Wiederholungen je Weg, 360 × 840 CSS-Pixel, Cloud Chromium. Ausschließlich
synthetisch: 300 Kunden, 5.000 Arbeiten, 3.000 Fotometadaten, 500 Rechnungen.
Vorher: Appquellen aus b448642; nachher: nur Messinstrumentierung ergänzt.
Kein Optimierungsnachweis: der Messlauf zeigt Streuung und einzelne Ausreißer.
Gemeinsam genutzter Cloudhost, keine kontrollierte CPU-/Energieumgebung.

Zeiten in ms. „Langsamste 5 %“ ist der Mittelwert der zwei langsamsten von
40 Werten; P95 zusätzlich in JSON. Wechsel dauern bis zum zweiten Animationsframe,
Suche enthält 130 ms Wartezeit für den bestehenden Debounce. Start misst Navigation
bis hg:startup-ready; pro Stichprobe neue Seite, gemeinsamer warmer Browsercache.
Speichern benutzt den vorhandenen Kundenformular-Submit mit anschließendem Read-back.
Die native Schnittstelle ist simuliert; keine Android-/SQLite-Latenz ableiten.

| Bedienweg | Vorher Mittel | Vorher langsamste 5 % | Messinstrumentiert Mittel | Messinstrumentiert langsamste 5 % |
| --- | ---: | ---: | ---: | ---: |
| App-Start | 1768.5 | 2524.5 | 1823.3 | 3107.6 |
| Heute → Büro | 261.6 | 381.4 | 279.1 | 529.8 |
| Büro → Heute | 231.9 | 400.0 | 268.2 | 998.2 |
| Kunden öffnen | 375.4 | 610.4 | 368.4 | 602.1 |
| Suche | 155.3 | 170.2 | 157.8 | 209.2 |
| Speichern | 911.0 | 1373.5 | 1003.0 | 1882.9 |

Reproduktion: bestehendes `tests/startup-ui.test.cjs` mit
`HG_PERFORMANCE_REPORT=/tmp/qa_performance.json` und optional
`HG_PERFORMANCE_PHASE=instrumented` ausführen. Browser-/Acorn-Pfade wie bei den
bestehenden Cloudtests konfigurieren; keine Verbindung zu Gerät oder Betriebsstore.

Native Erfassung: `BusinessDataBridge.getPerformanceMetrics()` liest je höchstens
128 technische Stichproben für getState, ownerCall, getWorkSession (Dauer in ms,
Antwortgröße in UTF-8-Bytes). Keine Argumente, Antworten, Toolnamen, Datensatz-IDs,
Zeitstempel, Persistenz oder Übertragung. `HGPerformance.snapshot()` liest die
begrenzten UI-Zeitwerte für render, page-change und app-start. Vollständige
Render-/Navigationsadapter werden nach Bereitschaft der Oberfläche gemessen;
Initialisierung separat durch app-start. Messpuffer verfallen beim Prozessende.

Selbst ausgeführt: Cloud-Browserlauf mit simulierten Bridges; vollständige Tabellen
und Bridge-Statistik in den beiden performance-JSON-Dateien. Nicht ausgeführt:
native Android-/Emulator-Zeitmessung und physische Pixelmessung. Pixel bleibt bis
zur bewussten Ausführung durch den Nutzer offen. Keine Freigabeflags gesetzt.
