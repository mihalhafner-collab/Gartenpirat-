# Haus & Garten1.54.8 / Build 97 – Heute, Performance und MCP

Der Originalkandidat ist in Codex Cloud vollständig abgenommen. Heute übernimmt die tatsächliche Struktur aus 1.54.6 / Build 95 mit einer blätterbaren Einsatzkarte; die wichtigsten Arbeitsaktionen liegen direkt oberhalb der unteren Navigation. Der kompakte laufende Timer lässt bei Normalschrift auch mit vorhandenen Arbeiten Uhrzeit und Kunde des nächsten Termins sichtbar. Bestehende Daten-, Entwurfs-, Timer- und Syncschutzregeln sowie das Menü am linken Rand bleiben erhalten.

[Original-APK](https://drive.google.com/file/d/19w2tUJRWlKr_0R37IU5jRkP34sCl6CTc/view), [Versionsnachweis](https://drive.google.com/file/d/1v22KPWGIMqNjtDkYFlvk9nZJn8feqx_r/view), [vollständige QA-Daten und117 Tool-Beobachtungen](https://drive.google.com/file/d/1Z3pg6Be03ESDpc6iguHfQ7XrfIr09QvH/view).

## Änderungen und Abnahme

| Bereich | Dateien/Funktionen | Umsetzung und selbst ausgeführte Prüfung |
|---|---|---|
| Heute/Bedienung | `mobile.html`, `today-ui.js/css`, `PlanningUI.homeRows` |95-Einzelkarte, Datum, Stunden und Verlauf in einem Renderer. Start/Pause/Fortsetzen/Stoppen/Foto, Arbeit erfassen und Planung unten. Dynamische Scrollreserve, Rückgängig oberhalb der Leiste.54 Browser-Suiten/13 Übersichtsfälle PASS. |
| Lesbare Reihenfolge | `field-ui.css`, `workday-ui.css`, `renderTimer` |12 alte Home-order-Regeln entfernt; DOM-Reihenfolge ist maßgeblich. Verlauf folgt Terminen/Stunden. Timer kompakt, seltenes Löschen über48 dp-Mehr-Menü mit zugänglicher Beschriftung. Neuer360×616-Fall zunächst rot, anschließend grün; native Normalschrift bestätigt Terminzeit/Kunde oberhalb der Aktionen. |
| Konkrete Fehler | `browseHome`, `OwnerToolSchemas` |Abgelaufene Auswahl übersprang den nächsten gültigen Termin; leere Liste behielt alte Karte. Beides korrigiert. Angebotenes `invoice_numbering_authority=""` wurde durch minLength1 abgewiesen; ausschließlich dieses Feld auf0 korrigiert. Positive/negative Produktionstests, enger Vertragsdelta, keine Änderung von117 Namen/Rechten/Request-/CAS-/Nummernregeln. |
| Leistung | `PixelMcpReadTools`, `planning-ui.js`, `TodayUI` |Metadatenkatalog je Antwort einmal statt dreimal, nächste Antwort weiterhin frisch; Hashkonsistenz/0 Schreibzugriffe geprüft. Datumsformatierer für Europe/Vienna wiederverwendet. Nur Details der gewählten Karte; warme Aktualisierungen ohne zusätzliche Bridgeabrufe. |
| MCP/Testabläufe | `McpAuditTools`, `McpDispatchAcceptance`, `McpWorkflowAcceptance`, `Isolated`, `ReleaseLifecycleProbe` |Echte Produktions-RPC-/SQLite-Aufrufe mit gültigen und ungültigen Eingaben; ohne Geschäftsargumente im Bericht. Eigene DB/Preferences/Dateien nach jedem neuen MCP-Fall entfernt und geprüft. SQLite-Sidecars sowie noch nicht geschriebene asynchron geschriebene Preferences korrekt abgearbeitet. UI-Test stellt seinen vorherigen Planungsentwurf wieder her. |
| Datenerhalt | Originalsignierte App/Test-APK, vollständiger Updateledger |Master, Beziehungen,1 ausstehende Aktion und persistierter RUNNING-Timer beim isolierten96→97-Update identisch. Installierte APK bytegleich. Bestehende synthetische Updatefixture3Kunden/1Auftrag/2Arbeiten bewusst erhalten. Reservierte temporäre QA-Dateibereiche im AVD bereinigt; erneuter vollständiger Ledger danach PASS. |

Vollständige43-Dateiliste und genaue Beobachtungen stehen im QA-JSON. Die 95-Referenz wurde mit denselben synthetischen115 Terminen angesehen. Das Logo war bereits dort im vorhandenen Menü; alte konkurrierende Renderer wurden nicht wieder eingeführt. Die jüngste Nutzeranweisung ersetzt bewusst die 96-Dreierliste auf Heute durch das95-Karussell. Historische Vertrags-/Präsentationsbaselines bleiben erhalten; die neue `overview97-presentation-baseline.json` dokumentiert die geprüften Unterschiede.

## Tatsächlich ausgeführte Tests

- 395 Python-Prüfungen PASS,0 Skips;37 historische Ersatzschlüssel-Erzeugungstests ausdrücklich ausgeschlossen. Kein Ersatz-/QA-Schlüssel erzeugt.
- 50 JVM-Suiten PASS;1050 UI–MCP-Paritätsprüfungen nach letzter UI-Änderung nochmals PASS. Direkte Tests echter Produktionsklassen.
- 54 Browser-Suiten vollständig PASS;13gezielte Übersichtsfälle einschließlich320/360Breite,616/640Höhe,150/200 Prozent Schrift, Timer/Foto/Entwurf/Rückgängig und Scrollreserve.
- 55lokale synthetische MCP-stdio-Kompatibilitätsszenarien,29 Kamera- und47 Cleanup-Hostfälle PASS. Dies ist kein produktiver Pixel-/Drive-Nachweis.
- 290 native SQLite/MCP/SAF-Prüfungen plus20 ergänzende gültige MCP-/Providerprüfungen PASS,0 FAIL. Die 20 enthalten 15 wiederholte Providerfälle; es sind keine 310 unterschiedlichen Szenarien.
- Fresh-isolated und tatsächlicher Android-WebView-/Navigationslauf PASS. Alle3 OS-Schriftgrößen1.0/1.5/2.0 auf360×640 dp PASS;9 unverdeckte Bilder einzeln angesehen. Mindestens48 dp-Touchflächen, keine horizontale Überbreite; Hauptaktionen erreichbar. Bei großer Schrift scrollt der Seiteninhalt vertikal.
- Release/AndroidTest/Lint PASS84Tasks;0 Lintfehler/54 bestehende Warnungen. Pflanzen181 Profile/362 Bilder/370 Spiegel und Angebotsvertrag PASS.
- Source-, Workspace- und tatsächliche signierte APK-Privacy PASS;0 operative Seeds. Originalpaket/Zertifikat,424Assets,2 generierte ART-Profile, eingebetteter Commit/Fingerprint und installierter APK-SHA geprüft.

## MCP-Abdeckung und Grenzen

Alle117 registrierten Werkzeuge wurden im finalen APK über den echten JSON-RPC-Router erreicht.113 Toolnamen haben beobachtete erfolgreiche Aufrufe;4sind in diesem Auftrag nur mit Schutz-/Validierungspfaden und gesonderten Produktionsregeln/Kernen geprüft: `prepare_legacy_conflict_backup`, `request_invoice_issue`, `prepare_offer_delivery`, `confirm_offer_delivery`. Vollständige Außenabläufe mit echten Drive-/Nummern-/Gmail-SENT-Voraussetzungen sind nicht abgenommen. Ein erwarteter Schutzfehler ist kein erfolgreicher Geschäftsvorgang.

156 eindeutige UI-Aktionen:145 fachliche Zuordnungen zu MCP und11 ausdrückliche Plattform-/Darstellungsausnahmen. Kameraaufnahme und Android-/Kontoeinwilligungen werden durch einen registrierten Toolnamen nicht ersetzt. Auf dem produktiven Pixel wurden ausschließlich technische Versions-/Katalog-/Syncmetadaten ausgewertet; keine produktiven Schreibtests, Testkunden, Zeitaufzeichnungen, Fotos oder Sicherungen erzeugt. Nach Nutzerinstallation den Werkzeugkatalog neu laden: die117 Namen bleiben gleich, der semantische Kataloghash ändert sich durch die enge Schemareparatur.

## Gemessene Leistung

Gleicher Cloud-Chromium, identische115 synthetische Termine,25Wiederholungen je Ansicht:

| Ansicht | Heute-Render Median/p95 | Navigation Median/p95 | Kalte Termindetailabrufe / warme25Render |
|---|---|---|---|
|95-Referenz |2/3 ms |3/4 ms |1/0 |
|96-Ausgang |8/10 ms |9/12 ms |3/0 |
|97-final |1/2 ms |3/5 ms |1/0 |

Reine Produktions-JVM-Metadatenmessung mit15 Stichproben: get_capabilities Median17,3→12,7 ms, integration_health9,1→5,1 ms. Keine globalen veränderlichen Cacheobjekte und keine Metadatenschreibzugriffe. Drei lesende Live96-Aufrufe benötigten5,2–6,3 Sekunden einschließlich Verbindung/Tunnel; das isoliert keine Geräte-CPU. Kein Versprechen zu Pixel-, Netz- oder produktiver Drive-Geschwindigkeit aus Host-/Softwareemulatormessungen.

## Noch offen: produktiver Sync und Pixel

Letzte lesende Beobachtung23.10.2026, 06:45 UTC: Pixel weiterhin Original 1.54.7 / Build 96, Quellcode998d71f/Fingerprint8a29c1b7. Sync `REMOTE_STATE_REFERENCE_MISSING`, `publication_chain`, `action_required`,9 ausstehende Änderungen/0Review, Wiederholungszähler1, `cloud_confirmed=false`, `provider_readback`.

Der bestehende Wiedergewinnungspfad akzeptiert geänderte Providerkennungen bereits ausschließlich über exakten Namen plus SHA;7 JVM-Fälle prüfen fehlende/geänderte/unterbrochene/verweigerte Referenzen. Aus technischem Status allein lässt sich ein fehlender unveränderlicher Originalzustand nicht sicher rekonstruieren. Keine Quellzustände erfunden, keine Queue gelöscht/wiederholt, keine produktive Drive-Datei umgeschrieben und kein request_sync ausgelöst. Dieser echte Syncfehler bleibt offen.

Keine Pixelinstallation oder physische Kamera-/Randgesten-/Google-Konto-/Kalender-/Wetter-/Mail-Abnahme. Installation mit Sicherung, request_sync und Rücklesen auf dem Pixel bleiben nach Protokoll Aufgabe des Nutzers. Emulatorerfolg beweist keinen produktiven Google-Drive-Abgleich.

Die Softwareemulation(API 30 / WebView 83, TCG ohne KVM) zeigte vor der gültigen Schriftabnahme einen Android-SystemUI-ANR. Verdeckte Bilder wurden ausdrücklich verworfen. Ausschließlich den eigenen AVD mit temporärem Runtime-Timeout10 und vorab eingerichtetem Testdisplay stabilisiert, exakt identifizierten OS-Wait bedient; Fensterwächter unverändert streng. Alle finalen Bilder zeigen die App unverdeckt. Originalfont1.0, physische480×960/Dichte240 restauriert; Emulator anschließend ohne Wipe/Snapshot ordentlich beendet. Dies ist keine native Performance-Abnahme.

## Originalquellen und Versionsnachweis

- App-/Testquellcommit: [`edd16a44c13dc17a774cff26f91e760efc123848`](https://github.com/mihalhafner-collab/Gartenpirat-/commit/edd16a44c13dc17a774cff26f91e760efc123848); unveränderliches Tag `source/v1.54.8-build97`. Bestehender Auditbranch, kein main-Merge/Force-Push.
- Quellfingerprint: `5bf13d01331b5492c0de05d691aa58e10c31fc744295683948d8adfc23a41595`.
- Original-APK:36.217.051 Bytes; SHA256 `7e394a7603d79ffb264e1f113c122c1e1700453a82a3e9be521831a425df4e48`. Originalzertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`.
- [Originalquellen](https://drive.google.com/drive/folders/1dMX0byFIGCzOLSk3N3Cs6U4WVWyfZ3t8), [Original-ZIP](https://drive.google.com/file/d/18z0qPxs27gc_J7JmfFMx-QDZQydeSbqF/view):1290 Dateien/559 UTF8/731 öffentliche Binärressourcen; ZIP-SHA256 `eb2244f707fe620c32a6168a92f2c2bfb0f3f97f9d47bff5f1c54e25cb18da3d`.
- [Abschlussquellen mit neuestem QA-/Dokumentationsstand](https://drive.google.com/drive/folders/1rxp9cbTf2srnopH-X8p9Xar6ZGQViPAn). Der separate Dokumentationscommit und dessen ZIP werden durch01-MANIFEST.json und den Abschlussbeleg im [Kandidatenordner](https://drive.google.com/drive/folders/1ZPwhieYXLRDwqdaSoY_SkqdgPffsN341) eindeutig gebunden; dies ist kein anderer APK-Quellcommit.
- 19 Originalquellen-Belege:18 tatsächliche Byte-SHA und1 vollständiger ZIP-Stream/Größe. Frischer Transferplan0 Upload/19 Reuse/0 Duplikate. GroßeAPK ebenfalls vollständig authentifiziert gestreamt/Größe geprüft; wegen32MiB-Materialisierungsgrenze kein Remote-Byte-SHA behauptet. Kleine QA-/Nachweisdateien tatsächlich heruntergeladen und SHA256-verglichen.

Vorgänger1.54.7/96 bleibt unverändert erhalten: [APK](https://drive.google.com/file/d/1YOT0voQQZ_ntiA_O9jBWVr3ifR8AnBQ8/view), Tag `source/v1.54.7-build96`, Code998d71f. Zwischenkandidat9cc4e18 ausdrücklich nicht ausgeliefert; seine Quellen als Zwischenstand gekennzeichnet und erhalten. Keine Schutzmenge entfernt oder Altgeneration gelöscht.
