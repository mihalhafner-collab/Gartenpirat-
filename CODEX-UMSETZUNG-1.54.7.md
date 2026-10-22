# Umsetzung 1.54.7 / Build96

Ausgang: Original95 `6972780dc9adbe46e0a91f1f77aa990e303fdaf0`, Abschlussdokumentation `d279945e9122dd83822b8a0a4c12b2814e37bde3`. Nutzerauftrag AP1–AP7 vom22.10.2026. Bearbeitung auf `codex/function-sync-audit-20261022`, kein main-Merge/Force-Push. Version erst nach grünen Implementierungsgates erhöhen; Original95/Tag erhalten.

## Verbindliche Grenzen

Keine produktive Pixelaktion, kein request_sync/Queueeingriff, keine produktiven Testdaten. Linkes Randmenü einschließlich Wisch-/Zurückgeste bleibt unverändert. Tests nur synthetisch und isoliert. Originalsignierung/Quellsicherung/Export gemäß AGENTS.md. Geschäftsdaten-/CAS-/Journal-/Bestätigungsregeln erhalten; cloud_confirmed ausschließlich drive_server_manifest. Kein Laptop-/Web-Fallback. Neue Quellen/Artefakte nicht mit installiertem Pixelstand gleichsetzen.

## Arbeitspakete und messbare Abnahme

1. **AP1 Fehlerklassifikation:** FOLDER_NOT_SELECTED/DEVICE_ID_MISSING benötigen Nutzeraktion, Versuch1 und keine Wiederholungsplanung; Netzwerkfehler NETWORK_UNAVAILABLE mit Backoff. Pure Produktionsregeln für Code/Text/Aktionsbedarf/Retry; TRANSFER_FAILED-Diagnose höchstens120Zeichen, keine Nutzdaten/Pfade/Tokens. read_step erhalten. JVM- und native Adapterprüfungen von tatsächlichen gespeicherten Feldern und Diagnosedarstellung.
2. **AP2 Commitgrenze:** rejected_invoice_ids in dieselbe commitSnapshot-/Ack-Transaktion. Nach tatsächlich gesichertem Snapshot Preferencesfehler STATUS_NOT_SAVED, kein Übertragungsfehlversuch; genaue Erfolgsaussage. Erzwungener Persistenzfehler sowie tatsächliche Queue-/Snapshotprüfung.
3. **AP3 Produktionstests:** SyncFailureRules durch Coordinator verwenden und direkt prüfen. Cleanup-Testdouble ohne Produktionsnamen, echter Produktionsklassifikator. LocalOwnerRepository.runRequest: Ausnahme rollt Action+Receipt zurück; identischer Request/Fingerprint replayed=true; anderer Fingerprint REPLAY_MISMATCH. Tests im bestehenden QA-Pfad, echte Produktionsklassen; SQLite-/Androidabnahme zusätzlich isoliert.
4. **AP4 Quittungsaufbewahrung:** erst nach bestätigtem Abgleich, älter90Tage und ohne offene Action-/Sicherungs-/Tombstonereferenz. Nur Anzahl protokollieren. Wiederholung darf niemals erneut fachlich wirken. Vor Implementierung den Fall bereits bestätigter/entfernter pending-Zeilen reproduzieren: ein Primary Key schützt nach Entfernung allein nicht. Falls nötig minimalen dauerhaften Replay-Schutz erhalten und Abweichung vom angenommenen PK-Schutz begründen. Alte unklar datierte Quittungen konservativ erhalten.
5. **AP5 Arbeitsdetail:** vorhandenen tatsächlichen Leave-Guard zunächst prüfen; flushWorklogDetail bereitstellen. Menü-/Seitenwechsel speichert gültige Änderungen, bei Fehler bestätigtes Verwerfen; Abbrechen bleibt samt Entwurf. Browserfälle einschließlich fehlendem Kunden und Android-Zurück.
6. **AP6 Heute/Planung:** ein zuständiger Heute-Renderer statt weiterer Wrapper. Heute: Datum/Syncchip, Jetzt(Start/Nachtragen bzw. laufende Arbeit/Stoppen), heutige Termine(3+Alle), bedarfsabhängige Offen-Chips, kleine Wochenstunden. Logo ins Seitenmenü; versteckte Ordnerbuttons entfernen, Ziele unter Mehr erhalten. Planung: Zeitraum+Tag/Woche in einer Zeile, Zu-heute-Symbol, Wochenstreifen mit Punkten, direkt folgende nach Tagen gruppierte Liste, fester +Termin-Button, Zusatzwerkzeuge im ⋯-Menü, Verbindung/Diagnose bei Bedarf. Datenaufrufe/Timer/Entwurfsschutz/Randmenü erhalten; ID-/Baseline-Diffs bewusst dokumentieren.
7. **AP7 feste Zugänglichkeitsregression:** Browser360×640 bei100/150/200Prozent und eigener Emulator font_scale1.5/2.0. Normalschrift: erster Planungstermin ohne Scrollen und maximal3Bedienzeilen; Heute Start+mindestens2Termine sichtbar. Große Schrift: Hauptaktionen erreichbar, kein abgeschnittener Text/horizontales Scrollen. Touchziele mindestens48dp. Timerwechsel Heute→Planung→Heute/Entwürfe geschützt. Bestehende Continuity/Daily/Sync-trash/Planungssuiten weiter grün.

## Hypothesen H1–H7

H1 defektes Fremdjournal: nur isoliert reproduzieren, bei Befund Quarantänevorschlag mit Datenschutzabwägung; keine ungeprüfte Protokolländerung.
H2 Netzvalidierung: tatsächliche Bedingung und sichtbaren Wartezustand prüfen, allenfalls wahrheitsgemäßen Hinweis.
H3 localChanged/readLocal bei Fokus: synthetisches Formular reproduzieren, nur nach Befund ändern.
H4 render/openPage bei Rechnungs-/Auftragsentwürfen: reproduzieren, vorhandene Guards erhalten.
H5 Planungsbridge: tatsächliche Aufrufzahl/Hauptthreadzeit messen, nur belegte Mehrfacharbeit optimieren.
H6 Randzone: physische Geräteprüfung in diesem Auftrag ausgeschlossen, ausdrücklich ungeprüft lassen.
H7 Randgeste bei Dialog: keine Änderung; Nutzerentscheidung erhalten.

## Abschließendes APK-Gate

AP1–AP7 und jeden Unterpunkt vor APK-Erstellung anhand tatsächlicher Quellen/Ergebnisse einzeln abgleichen. Offene Implementierung vor Build schließen. Source/Workspace/Artefakt-Privacy und passender Gesamtbestand grün. Erst dann Version96, gesicherten exakten Quellcommit bauen, originalsignieren, Tag/Provenienz/Original-ZIP/Drive-Readback. Emulator-Update95→96 mit lesendem Vor-/Nachledger, kein Reset/Uninstall; Fresh-isolated, WebView, große Schrift, finaler Erhaltungscheck. Abschließender Bericht je AP mit Dateien/Funktionen, selbst ausgeführten Tests, Abweichungen und offenen externen Grenzen. Pixel/echtes Google Drive bleiben ausdrücklich ungeprüft und Aufgabe des Nutzers.
