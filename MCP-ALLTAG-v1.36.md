# MCP-Alltagsworkflow – Version 1.36.0

## Ergebnis und Freigabegrenzen

Die bestehende Pixel-Schnittstelle wurde erweitert, nicht ersetzt. Kunden- und Auftragswerkzeuge waren bereits implementiert. Neue Werkzeuge nutzen denselben lokalen Speicher, dieselben IDs, dieselben mobilen Drive-Dateien und denselben privaten Tunnel. Keine neue Registrierung, API-Schlüssel-Erneuerung, Datenbank oder produktive Migration.

Die neue Version ist programmiert und automatisiert geprüft. Die native SQLite-Ausführung der **neuen Timer-MCP-Schreibabläufe auf einem Android-Gerät ist noch nicht nachgewiesen**: Bei der Prüfung war kein ADB-Gerät verbunden. Ein erfolgreiches Build und JVM-Regeltests ersetzen diesen Test nicht. Deshalb ist die vollständige Live-Abnahme dieses Auftrags noch offen.

## Bestand und gezielte Änderungen

Ausgangspunkt sind die Architekturprüfung v1.34 und das Design v1.35. Erneut geprüft wurden PixelMcpReadTools, PixelMcpWriteTools, PixelMcpEntityWriteTools, der Dispatch im PixelTunnelService, LocalStore in MainActivity, WorkSessionStore, die vorhandenen Validierungen sowie Python-MCP-/Importer-Tests. SDK-/Build-Ausgaben wurden nicht als eigener Anwendungscode behandelt oder vollständig erneut gelesen.

Pixel-Daten: bestätigter Snapshot plus lokale SQLite-Pending-Aktionen; Timer in bestehenden SQLite-Sitzungs-/Ereignistabellen. Laptop: bestehende SQLite-Datenbank und Importer. Material bleibt ein Textfeld; keine Lager-/Kosten-Datenbank erfunden. Der Laptop bestätigt die Übernahme mobiler Aktionen. Finale Rechnungsfunktionen bleiben unverändert in der App vorhanden, sind aber für den neuen MCP-Auftrag gesperrt.

### Neue Werkzeuge

| Werkzeug | Aufgabe |
| --- | --- |
| get_customer_work_records | Kundenhistorie mit tatsächlicher Kunden-ID und Zeitraum |
| get_work_record_summary | Stundensumme, Abrechnungsevidenz und Qualitätsprobleme; unabhängig vom normalen Listenlimit |
| get_sync_overview | Pending-Anzahl, Importfehler und letzter Drive-Versuch |
| get_active_work_session | Vorhandene aktuelle Pixel-Zeitsitzung lesen |
| start_work | Für tatsächlichen Kunden starten, optional Auftrag; Datum sonst heute in Europe/Vienna |
| start_break / end_break | Vorhandene Sitzung pausieren/fortsetzen |
| stop_work | Sitzung dauerhaft stoppen, noch keine Arbeitsaufzeichnung erzeugen |
| complete_work_session | Geprüfte gestoppte Sitzung mit Tätigkeit/Material/Notizen als Arbeitsaufzeichnung abschließen |

Bestehende create_customer, update_customer, create_order, update_order und sämtliche bisherigen Lese-/Arbeitswerkzeuge bleiben unter ihren Namen erhalten. Rechnungsentwurfs-Werkzeugnamen bleiben kompatibel erkennbar, geben jedoch serverseitig INVOICE_PERMISSION_REQUIRED zurück; kein Schreib-Sink wird dafür aufgerufen. Das entspricht der neuen ausdrücklichen Vorgabe, Rechnungen nicht ohne gesonderte Freigabe zu verändern.

### Arbeitsaufzeichnungen und Materialkosten

Kunde ist verpflichtend, Auftrag optional. Kunde/Auftrag dürfen nicht widersprüchlich sein. Vor Änderungen Datensatz lesen und expected_record_hash verwenden. Bestehende Importer-Capability-Prüfungen und Schutz abgerechneter Leistungen wurden nicht deaktiviert. Die Kunden-ID einer bestehenden Arbeitsaufzeichnung ist weiterhin nicht über den allgemeinen Update-Patch änderbar; Relationsänderungen benötigen einen gesondert abgesicherten Ablauf.

Optionales material_cost akzeptiert eine nicht negative EUR-Zahl mit höchstens zwei Nachkommastellen. Speicherung erfolgt ausdrücklich als **Materialkosten [EUR]: 15.00** im bestehenden Materialfeld. Eine spätere Kostenkorrektur ersetzt nur diese gekennzeichnete Zeile, nicht den übrigen Materialtext. Es handelt sich nicht um ein neues strukturiertes Datenbankfeld oder automatisch verrechnete Rechnungspositionen. Arbeitsbeschreibung kann im vorhandenen Tätigkeits-/Notizfeld erfasst werden.

Normale Uhrzeit-Erfassung berechnet Ende minus Beginn minus Pause; widersprüchliche mitgelieferte Stunden werden abgewiesen. Stunden ohne Uhrzeiten gelten als Nettozeit. Wenn eine Spracheingabe nicht erkennen lässt, ob fünf Stunden brutto oder netto gemeint sind, muss ChatGPT nachfragen.

Timer-Abschluss übernimmt die bereits pausengekürzte Nettozeit: kein erneuter Pausenabzug. Pausenminuten werden aus vorhandenen Ereigniszeiten gerundet. Diese Pausenmessung basiert auf der Geräte-Wanduhr, nicht einem neuen monotonen Pausenspeicher; rückläufige Uhrwidersprüche blockieren den unbeaufsichtigten Abschluss. Beginn/Ende bleiben als Sitzungsereignisse erhalten und werden zusätzlich als Herkunftshinweis in der Notiz dokumentiert. Sie werden nicht als erfundene minutengenaue Formularzeiten ausgegeben. Genauigkeit bei Geräteuhrwechseln, Neustarts und Sitzungswiederherstellung benötigt native Gerätetests.

### Synchronisationsstatus

Alte status-Werte bleiben erhalten; neue sync_state-/local_state-Felder sind additiv:

| Bedeutung | Neues Kennzeichen | Keine Verwechslung mit |
| --- | --- | --- |
| Dauerhaft lokal gespeichert | local_state=local_saved | Drive-Bestätigung |
| Vorgemerkt oder in Drive übertragen | sync_state=sync_pending | Übernahme durch Laptop |
| Vorhandener Importbeleg accepted_ids | sync_state=sync_confirmed | Bloßer Datei-Upload |
| Importfehler / letzter Drive-Versuch fehlgeschlagen | sync_state=sync_failed | Gelöschter Datensatz; lokale Daten bleiben erhalten |
| Lokal ersetzte Aktion / expliziter WRITE_CONFLICT | sync_state=sync_conflict | Erfolgreiche Speicherung |
| Timeraktion ohne Arbeitsdatensatz | sync_state=not_applicable | Noch fehlende Drive-Timerdatei |
| Unbekannte Anfrage | unknown | Automatisch angenommener Erfolg |

Gleiche request_id plus gleiche Angaben liefern den gespeicherten Beleg; andere Angaben unter derselben Kennung werden abgewiesen. Timeraktion und MCP-Beleg liegen in derselben bestehenden SQLite-Transaktion. Start-/Zustandsprüfung des Timers wurden ebenfalls in Transaktionen verlegt, um parallele UI-/MCP-Aktionen nicht widersprüchlich zuzulassen. Der Abschluss verwendet die vorhandene stabile Sitzung→Arbeits-ID und den vorhandenen mobilen Kanal. Keine internen Belege werden als Drive-Betriebsaktion exportiert.

Die bestehende automatische Wiederholung wurde beibehalten. Vollständige Konfliktfreiheit zwischen mehreren Drive-Clients ist damit nicht bewiesen. Ohne Laptop-Import bleibt sync_pending möglich, auch wenn die Datei bereits in Drive liegt.

### Qualitätskontrolle und Abrechnung

Neue Erfassung beziehungsweise Zeitkorrektur wird bei sich überschneidenden vollständigen Uhrzeitintervallen abgewiesen. Reine Notizkorrekturen historischer Einträge werden nicht nachträglich wegen alter Zeitüberschneidungen blockiert. Der Bericht prüft doppelte Einträge, ungültige Kundenzuordnung, Zeitvalidierung, Stundenabweichung und belegte Rechnungs-/Offen-Widersprüche. Es gibt keine automatische Löschung, Zusammenführung oder Abrechnungsänderung.

Abrechnungsevidenz unterscheidet offen markiert ohne Nachweis, Rechnungsentwurf ohne Nachweis, ungeklärte Markierung und direkt zugeordneten Rechnungsnachweis. Ein fremder oder nur dem gleichen Kunden gehörender Rechnungsdatensatz reicht nicht. Stornierte Rechnungen gelten nicht als positiver Nachweis. Zahlungen werden nicht behauptet. Fehlende Rechnungsrelationen und historische Freitexte bleiben ungeklärt; keine sevDesk-Verbindung wurde aufgebaut.

Grenzen: Überschneidungen sind nur mit vollständigen vergleichbaren Uhrzeiten überprüfbar. Die neue Prüfung vergleicht normale Intervalle desselben Datums; Sonderfälle über Mitternacht und reine Stundenangaben erfordern manuelle Prüfung. Qualitätsberichte sind auf 5.000 Datensätze begrenzt; für große Bestände Zeitraum einschränken.

## Geänderte Dateien

- PixelMcpReadTools.java: additive Werkzeuge, Dispatcher, Abrechnungsevidenz und Statusfelder; Rechnungs-Schreibsperre.
- PixelMcpWriteTools.java: Materialkosten-Abbildung und Überlappungsprüfung.
- PixelMcpSessionTools.java: neue typisierte Timeradapter und Validierungen.
- PixelMcpBusinessTools.java: neue lesende Qualitäts-/Abrechnungsauswertung und Statusmapping.
- MainActivity.java / LocalStore: vorhandene persistente Timer-/Receipt-/Queue-Wege, Zeitstempel und gespeicherter Drive-Fehlerstatus.
- WorkSessionStore.java: transaktionale Start-/Zustandsprüfungen und Pausenauskunft aus vorhandenen Ereignissen; kein Schemawechsel.
- android/app/build.gradle: Version 1.36.0, Code 53; Originalpaket/Signatur unverändert.
- Tests: PixelMcpDailyTests.java neu, vorhandene Vertragstests/Runner für zusätzliche Tools erweitert.

## Tests und Nachweise

| Funktion | Testart / wirklich ausgeführt | Ergebnis / Grenze |
| --- | --- | --- |
| Bisherige Read-/HTTP-/Protokoll-/Auth-Werkzeuge | JVM, echter lokaler HTTP-Testserver | 64 erfolgreich; ursprüngliche Identität/Fehler-/Replay-Prüfungen erhalten |
| Arbeits-Schreibschemata, Validierung, Hash-/Duplikatregeln | JVM | 44 erfolgreich; nicht native Speicherung |
| Kunden/Aufträge und interne Rechnungsroutinen | JVM | 11 erfolgreich; MCP-Rechnungszugriff separat als gesperrt getestet |
| Kunden-/Foto-/Rechnungsrelationen | JVM | 17 erfolgreich |
| Neue tägliche Werkzeuge, Materialkosten, Status, Pausen, Berechtigungen | JVM, echte Schemas/Dispatcher; isolierter Sink | 29 erfolgreich; native Timerpersistenz nicht simuliert |
| Bestehendes Backend/Offline/Importer/Recovery | Isolierte Python-Suite | 106 erfolgreich |
| Laptop-MCP → Aktionsdatei → App-Import → Snapshot | Echter lokaler MCP-Client, temporäre Dateien | 55 erfolgreiche Prüfungen; keine Live-Cloud-Schreibprüfung |
| Bedienung ohne ChatGPT | Echter Chromium, isolierte Android-Brücke | 7 Design-/Bearbeitungsabläufe erfolgreich |
| Produktive Datenbank | Nur lesend | Integrität ok, keine gefundenen FK-Verletzungen; Datei unverändert |
| Bestehende Pixel-Live-Verbindung | Health ausschließlich lesend | ready, pixel_business_write; bestätigt nur die bisher installierte Version |
| Neue native Timertransaktionen / Neustart / zwei gleichzeitige Clients | Android-Gerät | Noch nicht ausgeführt: kein ADB-Gerät verbunden |
| Neue Version → reales ChatGPT → reales Drive → Importbeleg | Live-Schreibtest | Noch offen; keine produktiven Testeinträge erzeugt |

Während der Entwicklung scheiterten alte Listenlängen-Assertions an den absichtlich zusätzlichen Tools. Sie wurden auf die neue erwartete Anzahl erweitert, nicht entfernt. Ein Compilerfehler einer falsch platzierten Pausenprüfung wurde vor Bereitstellung korrigiert. Abschließende Build-/Testausgaben sind maßgeblich; kein fehlgeschlagener Zwischenbuild wird als erfolgreicher Nachweis gewertet.

Produktive Lesekontrolle: 44 Kunden, 1 Auftrag, 164 Arbeitsaufzeichnungen, 95 Rechnungen, 12 Fotos. Keine vorhandenen IDs verändert, kein Datensatz geschrieben oder migriert. Keine neuen Secrets, Zugriffsadressen oder Tunnel-IDs im Bericht.

## Kurze Anleitung

1. Neue APK als Update installieren; bestehende App nicht deinstallieren.
2. Haus-&-Garten-App öffnen und mit dem bestehenden Drive-Ordner abgleichen. Vorhandene Verbindung und Schreibfreigabe verwenden; keinen neuen Schlüssel oder Tunnel anlegen.
3. ChatGPT mit dem vorhandenen Pixel-Plugin verwenden. Falls neue Werkzeuge nicht erscheinen, Werkzeugliste/Verbindung aktualisieren beziehungsweise neuen Chat mit derselben App beginnen; keine Neuregistrierung erzwingen.
4. Beispiel: „Suche Krassnitzer und erfasse heute von 08:00 bis 14:30, 60 Minuten Pause, Thuja pflegen, drei Säcke Mulch.“ Bei mehreren Treffern entscheidet der Nutzer. Ergebnis: 5,5 Netto-Stunden.
5. Materialbeispiel: „15 Euro Materialkosten“ wird als gekennzeichnete EUR-Zeile erfasst, nicht automatisch verrechnet.
6. Laufende Arbeit: Kunde suchen → start_work → start_break/end_break → stop_work → Tätigkeit prüfen → complete_work_session. Die gestoppte Sitzung darf nicht als fertige Arbeitsaufzeichnung gemeldet werden.
7. Nach Erfassung client_id aus der Antwort verwenden und get_sync_status prüfen. Bis sync_confirmed nur „lokal gespeichert / Abgleich ausstehend“ bestätigen.

Für rein lokale Erfassung braucht das Pixel kein Internet. ChatGPT/Tunnel benötigen Internet und einen laufenden Pixel-Dienst. Für die bestehende Laptop-Importbestätigung muss weiterhin der Importer laufen. Die App bleibt unabhängig von ChatGPT bedienbar. Neue laufende kostenpflichtige Dienste wurden nicht hinzugefügt; bestehende Kontotarife bleiben unberührt.

Nächster erforderlicher Abnahmeschritt: Pixel per USB verbinden, neue Version installieren und native Timer-/Receipt-/Offline-/Drive-Abläufe mit ausdrücklich isolierten Testdaten überprüfen. Bis dahin die neuen Timer-MCP-Werkzeuge nicht als vollständig live abgenommen behandeln.

## Bereitstellung bestätigt

Finaler Release-Build erfolgreich, Originalsignatur verifiziert, Paket at.hausgarten.service / Version 1.36.0 / Code 53 / target SDK 37. APK im vorhandenen privaten Drive-Ordner hochgeladen und Größe/Ordnerzuordnung rückgelesen: 2.648.582 Bytes. Version 1.35 in vorhandene APK-Backups verschoben und Zielzuordnung bestätigt, nicht gelöscht.

SHA-256: 010455b75cc2991d5f4a7a4b3f756b14a661cbe896003999a22a14a2faaa1cd9.

Insgesamt 165 erfolgreiche JVM-/Vertrags-/Relationsprüfungen, zusätzlich 106 Python-Tests, 55 lokale MCP-End-to-End-Prüfungen und 7 Browserabläufe. Die neue APK wurde nicht auf dem Pixel installiert; die Live-Health-Prüfung betrifft den zuvor installierten Server.
