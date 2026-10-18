# Haus & Garten App – dauerhafte Projektregeln

## Codex-Cloud-Einstieg

Für den aktuellen P0-Auftrag gilt diese Lesereihenfolge verbindlich:

1. `AGENTS.md` – übergeordnete Regeln und Sicherheitsgrenzen
2. `CODEX-AUFTRAG-GARTENPIRAT.md` – vollständiger ausführbarer Arbeitsauftrag
3. `CODEX-STATUS.md` – fortlaufender technischer Arbeitsnachweis

Codex Cloud soll den Auftrag aus `CODEX-AUFTRAG-GARTENPIRAT.md` selbstständig phasenweise abarbeiten und `CODEX-STATUS.md` nach jeder abgeschlossenen Phase aktualisieren. Historische Dokumentation dient nur als Referenz und darf den dort festgelegten Cloud-only-/Pixel-Drive-Zielpfad nicht wieder durch eine Laptop-/Desktop-Abhängigkeit ersetzen.

## Verbindlicher Cloud-only-Arbeitsweg (Auftrag vom 18. Oktober 2026)

- **Entwicklung, Codeänderungen, Builds, Tests, Originalsignierung und Releasevorbereitung ausschließlich in Codex Cloud.**
- Ausschließlich das bestehende Repository `mihalhafner-collab/Gartenpirat-` verwenden. Keine zweite operative App, kein zusätzliches Repository und kein paralleler Desktop-/Laptop-Quellstand.
- **Laptop/Desktop ist kein Bestandteil der aktiven Arbeitsweise, keine Voraussetzung und kein Fallback.** Alte Laptop-/Web-Pfade dürfen für historische Kompatibilität im Repository verbleiben, aber nicht erneut als notwendiger Betriebs-, Entwicklungs-, Sync-, Rechnungs- oder Releasepfad eingeführt werden.
- Operativer Zielpfad: **Pixel-App ↔ Google Drive direkt**. Entwicklungs-/Releasepfad: **Codex Cloud ↔ GitHub**. Funktionen, die technisch noch einen lokalen Web-/Laptop-Prozessor voraussetzen, gelten als nicht fertig migriert und müssen vor Freigabe auf Pixel/Drive-direkte Verarbeitung umgestellt werden.
- Keine Anweisung darf künftig verlangen, einen Laptop einzuschalten, eine Desktop-App zu starten, lokal zu bauen, lokal zu signieren oder einen lokalen Webserver für den produktiven Ablauf zu betreiben.
- Historische Dateinamen wie `HausGarten-Laptop.json` dürfen zur Wahrung der Datenkompatibilität zunächst unverändert bleiben. Der Name begründet keine Laptop-Abhängigkeit. Umbenennungen nur mit verlustfreier Migration und Rückwärtskompatibilität.
- Originalsignierung ausschließlich über die geschützte persönliche Gartenpirat-Codex-Cloud-Umgebung nach `ORIGINAL-SIGNIERUNG-CLOUD.md`; niemals GitHub Actions, Debug-/QA-Schlüssel oder lokale Ersatzschlüssel verwenden.
- Produktive Geräteaktionen auf dem Pixel bleiben gesondert geschützt: keine Deinstallation, keine App-Datenlöschung, keine automatische Installation ohne Sicherung und Freigabe.

## Verbindliche gemeinsame Codebasis

- Vor Änderungen den aktuellen Stand von `main`, vorhandene Cloud-Branches und die Git-Historie vergleichen. Neuere Funktionen und Schutzkorrekturen erhalten; kein lokaler Desktop-Stand ist maßgeblich.
- Pflanzenlexikon und Neophytenbereich nutzen den vorhandenen Katalog unter `android/app/src/main/assets/plant-catalog.json`. Adapter, Webspiegel und Bildauslieferung mit `python3 tools/sync_plant_assets.py --write` erzeugen; `python3 tools/sync_plant_assets.py` muss vor Builds bestehen. Generierte Spiegel niemals unabhängig redigieren.
- Integration im tatsächlichen operativen APK und in der bestehenden Navigation nachweisen, einschließlich Bilder, Suche, Vollbild und Offline-Verhalten. Technische Struktur-/Lizenzmetadatenprüfungen sind keine vollständige botanische Verifikation.
- Persönliches `b.b.` ist ausschließlich eine Geräteübersicht bei gespeicherten Arbeitsaufzeichnungen; unter Heute erscheint `bezahlt`. Keine Verbindung zu `billed`, Rechnungen oder Rechnungserstellung herstellen.
- Parallelcode erst nach vollständiger Übernahme, Abhängigkeitsprüfung, geprüftem gemeinsamen Build und gesicherter Git-Historie entfernen. Tests, Datenschutzprüfungen und synthetische Fixtures erhalten; keine pauschale Löschung nach Dateinamen.
- Operative Paketkennung und verifizierten Originalschlüssel erhalten; versionCode erhöhen. Kein Ersatzschlüssel und kein QA-Build als Update-Nachweis. Pixel-Installation erst nach Sicherung und ausdrücklicher Freigabe; keine Deinstallation oder App-Datenlöschung.
- Übernahmen, Erreichbarkeit, tatsächliche Prüfungen, entfernten Code und ausstehende Geräte-/Update-Nachweise dokumentieren. Aktueller Nachweis: `INTEGRATIONSNACHWEIS-2026-10-18.md`.

## Selbstständige Verbesserung

Setze bessere, einfachere und funktionellere Lösungen innerhalb des beauftragten
Bereichs selbstständig um. Bevorzuge kleine, reversible Änderungen an der
bestehenden Anwendung. Prüfe die Änderungen mit passenden Tests und dokumentiere
das Ergebnis. Keine Rückfrage für eindeutig sinnvolle, risikoarme technische
Details; keine zusätzlichen Funktionen ohne praktischen Nutzen.

Frage vorher bei Risiken für produktive Betriebsdaten, bestehende Schnittstellen
oder Kontoverbindungen, bei neuen Kosten, zusätzlichen Berechtigungen und
irreversiblen Änderungen. Diese Arbeitsregel ersetzt keine Sicherheitsfreigaben
und erweitert nicht den Umfang des jeweiligen Auftrags.

## Bestehende App erhalten

- Keine neue App und keine parallele betriebliche Datenbank aufbauen.
- Vor einer Änderung den betroffenen Ablauf, das Datenmodell und seine Schnittstellen verstehen; nicht jedes Mal unnötig das gesamte Projekt analysieren.
- Bestehende IDs, Beziehungen, Offline-Daten, Drive-Dateiformate und MCP-Werkzeuge erhalten. Erweiterungen rückwärtskompatibel ausführen.
- Pixel ist die primäre und einzige operative Arbeits-App. Drive bleibt der zentrale Datenabgleich. Ein lokaler Web-/Laptop-Prozessor darf nicht Voraussetzung für neue oder bestehende Kernfunktionen sein.
- Einsatzkunde und Rechnungsempfänger getrennt halten. Arbeitsaufzeichnungen ohne Auftrag bleiben gültig. Historische Zuordnungen nicht erraten.
- Keine produktiven Daten löschen, zusammenführen, überschreiben oder als Testdaten verwenden. Keine automatische Massenmigration oder ID-Neuvergabe.
- Keine Secrets, Signaturschlüssel, Betriebsdaten oder private Drive-Inhalte in Quellcode, Testfixtures, Logs, APKs oder Repository-Uploads einbetten.
- Originalsignierung: ausschließlich temporäre Nutzung der fünf `HG_ORIGINAL_*`-Variablen in der ausgewählten persönlichen Gartenpirat-Codex-Cloud-Umgebung gemäß `ORIGINAL-SIGNIERUNG-CLOUD.md`. Keystore nur im privaten OS-Temp außerhalb des Repositorys; keine Werte in Git, Logs, Artefakten, CI oder anderen Umgebungen. Kein Ersatzschlüssel, keine automatische Installation oder Update-Freigabe.

## Alltagstauglichkeit

Die App bleibt vollständig ohne KI bedienbar. Prioritäten: Datenintegrität,
Zuverlässigkeit, Übersichtlichkeit und einfache, schnelle Bedienung.
Wichtige Aktionen für kleine Hände gut erreichbar machen, eindeutige Beschriftungen
verwenden und unnötige Navigation, Doppeleingaben und technische Hinweise vermeiden.
Zurück geht genau einen Schritt; Entwürfe und laufende Zeiterfassung schützen.
Keine versteckten automatischen Datenänderungen und keine dekorative Komplexität.

## Prüfung und Freigabe

- Vor jeder Umsetzung kurze, messbare Abnahmekriterien festlegen. Für jeden betroffenen Benutzerablauf konkrete Eingaben, erwartete Ergebnisse und Fehlerfälle definieren.
- Arbeitspriorität: zuerst Datenintegrität und Synchronisierung, danach Bedienbarkeit und Geschwindigkeit, erst dann Zusatzfunktionen.
- Vorhandene Tests weiterverwenden und bei konkreten Fehlern gezielt ergänzen. Tests ausschließlich mit isolierten, synthetischen Daten ausführen.
- Python-, Java-, UI-, MCP- und Android-Prüfungen in Codex Cloud ausführen. Keine lokale Desktop-/Laptop-Testumgebung als Freigabevoraussetzung.
- Zwischen statisch geprüft, automatisiert getestet, tatsächlich ausgeführt, End-to-End getestet und nicht testbar ausdrücklich unterscheiden.
- Lokale Pixel-Speicherung, Drive-Übergabe und bestätigte Synchronisierung nicht gleichsetzen; Konflikte und ausstehende Aktionen sichtbar erhalten.
- Android-Releases: gleiche Application ID und vorhandenen Signing Key erhalten, versionCode erhöhen. Vor Freigabe Fresh-Install-/In-place-Update- und Datenerhalt-Nachweise soweit technisch möglich; fehlende Geräteabnahme ausdrücklich kennzeichnen.
- Vor jeder APK-Bereitstellung die betroffenen Regressionstests, Offline-/Sync-Abläufe und das fertige Artefakt prüfen. Ein erfolgreicher Build allein ist keine Abnahme.
- Änderungen, Tests, Ergebnisse und offene Einschränkungen verständlich berichten. Keine Erfolgsbehauptung ohne tatsächlich vorliegenden Nachweis.

## Fachliche MCP-Eigentümerfunktionen

Aktueller Umfang und technische Grenzen: `docs/MCP-EIGENTUEMERZUGRIFF-2026-10-18.md`.
Eigentümerfunktionen einschließlich Rechnungsentwurf, Korrektur/Storno, Archiv,
Dateitransfer und nicht geheimer Rechnungskonfiguration sind Produktfunktionen.
Konkrete Betriebsdatenaktionen benötigen den jeweiligen Benutzerauftrag.
Schreibschalter, Vergleichsstände, Belege und externe Konto-/Gerätefreigaben erhalten.

Für die Einführung gilt ab jetzt: Der gemeinsame Sync-/Nummern-/Angebotspfad muss
**Pixel/Drive-direkt** funktionieren. Kein alter Laptop-/Web-Importer darf als
Pflichtkomponente oder unsicherer Fallback verwendet werden. Unbekannte neue
Journaltypen fail-closed zurückhalten. Eine Geschäfts-MCP-Sicherung ersetzt keine
vollständige Gerätesicherung.

## Drive-Abgleich und Kundenakte

Direkter Drive-Serverbeleg und Android-Provider-Rücklesen bleiben unterschiedliche
Zustände. Bestehende Journal-IDs, Veröffentlichungsfolgen und Originaldateien
erhalten; keine Cloud-Bestätigung aus einer lokalen Kopie ableiten. Neue
UI-Entfernungen archivieren mit Versionsschutz. Alte Löschaufträge benötigen
eine ausdrückliche, nachvollziehbare Entscheidung. Kunden-Fotoentwürfe behalten
Originalbytes und Request-ID. Google-/Tunnel-Freigaben werden nicht durch Code
erteilt. Fachliche Betriebsdatenaktionen benötigen den jeweiligen Benutzerauftrag.
