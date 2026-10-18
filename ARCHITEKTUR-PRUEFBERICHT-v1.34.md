# Architektur- und Qualitätsprüfung – Version 1.34.0

Stand: 12.10.2026. Bestehende Anwendung verbessert, nicht neu entwickelt.

## Ergebnis und Grenzen

Die unten aufgeführten automatisierten Tests, Browserabläufe, lokale MCP-Verarbeitung und der Release-Build waren erfolgreich. Produktive Daten wurden ausschließlich lesend geprüft. Keine Datenmigration, keine Änderung bestehender IDs, keine automatische Zusammenführung oder Abrechnungsänderung wurde durchgeführt.

Dies ist keine Behauptung vollständiger Fehlerfreiheit: Die neue APK wurde nicht auf einem echten Pixel installiert und getestet. GPS, Kamera, Android-Berechtigungen, Telefon, WhatsApp, Energiesparverhalten und eine dauerhafte Tunnelverbindung benötigen weiterhin Gerätetests. Der zuvor verwendete Emulator zeigte SystemUI-/Launcher-Probleme; frühere Gerätetests werden nicht als Nachweis dieser Version gezählt.

## 1. Tatsächliche Architektur

| Bereich | Vorhandene Umsetzung | Bedeutung |
| --- | --- | --- |
| Android | Java Activity, WebView, HTML/JavaScript/CSS; AndroidData-, AndroidSpeech- und AndroidPhone-Brücken | Keine neue React-/Compose-App oder parallele Datenlogik |
| Pixel-Speicher | SQLite für ausstehende Änderungen und Zeitsitzungen; bestätigter Drive-Snapshot in SharedPreferences | Angezeigte Daten bestehen aus Snapshot plus lokalen Änderungen |
| Laptop | Python ThreadingHTTPServer, SQLite/WAL mit Fremdschlüsseln; Vanilla-JavaScript-Frontend | Weiterhin Import-, Archiv- und Rechnungsinstanz |
| Datenmodell | Kunden, Aufträge einschließlich Projekte, Arbeitsaufzeichnungen, Rechnungen, Fotos, Einstellungen | Projekte besitzen keine neu erfundene zweite Tabelle |
| Offline | Native Pending-Warteschlange; Web-Warteschlange in localStorage | Offline-Eingaben und stabile IDs bleiben erhalten |
| Drive | HausGarten-Laptop.json, HausGarten-ChatGPT.json und geräteeigene HausGarten-Mobile-Datei | Bestehende Kanäle unverändert; keine neue Ordnerstruktur |
| Laptop-MCP | haus-garten-private, Version 1.1.0, stdio; validierte Aktionsdatei | Bestandswerkzeuge und Konfiguration bleiben erhalten |
| Pixel-MCP | Privater ausgehender Tunnel, Benutzerfreigabe, getrennte Lese-/Schreibwerkzeuge | Keine generische Datei-, SQL- oder endgültige Löschfunktion |
| Rechnungen | ReportLab-PDF, Decimal-Berechnung, transaktionale Nummernvergabe und Prüfung ausgestellter PDF-Dateien | Finale Ausstellung weiterhin am Laptop |
| Sprache | Android-Systemdiktat und separater ChatGPT-MCP-Kanal | Kein kostenloser autonomer KI-Dienst in der App behauptet |

Arbeitsaufzeichnungen benötigen einen Kunden und können optional einem Auftrag zugeordnet sein. Material bleibt im vorhandenen Textfeld; Kilometer bleiben numerisch. Rechnungsbezüge verwenden vorhandene Rechnungsdetails mit Arbeitsaufzeichnungs-IDs. Fotos besitzen Kunden- und gegebenenfalls Auftrags-/Arbeitsbezüge.

Der Pixel ist damit noch nicht alleinige zentrale relationale Datenbank. Ein vollständiger Wechsel der Datenhoheit wäre ein eigenes, abgesichertes Vorhaben; er wurde nicht stillschweigend durchgeführt.

## 2. Gefundene und behobene Probleme

| Schweregrad | Problem und Ursache | Korrektur |
| --- | --- | --- |
| Hoch | Versteckte Zeitsitzungs-ID konnte einen Formular-Reset überleben; nachfolgende manuelle Eingaben konnten eine alte Sitzung wiederverwenden | Sitzungseingabe beim Reset ausdrücklich entfernen; Regressionstest für neue manuelle Erfassung |
| Hoch | Native Bearbeitung schützte bestehende Foto-/Rechnungszuordnungen nicht ebenso wie der Laptop | Gemeinsame native Relationsprüfung vor dem Speichern; widersprüchliche Zuordnungen und Änderungen ausgestellter Leistungen abweisen |
| Hoch | Quelle einer Arbeitsaufzeichnung konnte bei Bearbeitung überschrieben werden | Vorhandene Quelle erhalten; Pixel-Standard nur bei fehlender Quelle |
| Mittel | Tages-/Wochen-/Auftragskennzahlen führten mit alten Filtern zu unerwarteten Listen | Passende Filter setzen und störende Altfilter zurücksetzen |
| Mittel | Kundenhistorie konnte durch vorherige Such-/Zeitraumfilter unvollständig erscheinen | Gesamte Kundenhistorie mit explizit zurückgesetzten Filtern öffnen |
| Mittel | Fotohistorie war auf wenige Bilder begrenzt; fehlende lokale Bilder reagierten unklar | Erweiterbare vollständige Galerie und verständlicher Hinweis |
| Mittel | Rechnungen im Kundenprofil waren nicht zuverlässig direkt erreichbar | Entwurf direkt bearbeiten; ausgestellte/importierte Rechnung schreibgeschützt anzeigen, Original-PDF nur sofern vorhanden |
| Mittel | Notiz-/Material-only Eingaben wurden nicht durchgängig als ungespeicherter Entwurf erkannt | Zentrale Entwurfserkennung einschließlich Ort, Material, Notizen und Fotos |
| Niedrig | Wiederholtes Suchen/Filtern pro Listenzeile verursachte unnötige Arbeit | Pro Darstellung vorbereitete Zuordnungs- und Fotozähl-Maps, ohne riskanten globalen Cache |
| Niedrig | Fokus, Abstände und Profilaktionen waren uneinheitlich | Sichtbarer Tastaturfokus, größere Profilzeilen, sichere Fußleistenabstände, übersichtlichere Sekundärtexte |
| Niedrig | README enthielt überholte Versions-/Tunnelangaben | Dokumentation an tatsächliche Version und Grenzen angepasst |

Neue Datensätze mit bereits vorgegebener ID werden anhand ihrer tatsächlichen Existenz erkannt. Sie müssen ebenfalls positive Arbeitsdauer besitzen. Historische Nullstunden-Datensätze wurden nicht nachträglich verändert.

Ausgestellte Rechnungen sperren relevante Leistungsdaten sowie das Zurücksetzen des Abrechnungskennzeichens. Interne Notizen bleiben bearbeitbar. Auch bei Rechnungsentwürfen dürfen Kunden-/Auftragsbezüge nicht widersprüchlich geändert werden. Bestehende MCP-Regeln sind teilweise bewusst strenger als die normale Entwurfsbearbeitung; diese Sicherheitsregeln wurden nicht gelockert.

## 3. Geänderte Dateien

- android/app/src/main/assets/design-concept.js und design-concept.css: Navigation, Filter, Profile, Galerie, Bedienbarkeit und effizientere Listen.
- android/app/src/main/assets/mobile.html: echter Heute-Filter.
- android/app/src/main/assets/workday-ui.js und work-location.js: gemeinsame Entwurfserkennung und sicherer Sitzungsreset.
- android/app/src/main/assets/invoice-draft.js: direkter Rechnungszugriff und schreibgeschützte Bestandsansicht.
- MainActivity.java: native Validierung vor Pending-Speicherung und Erhaltung der Quelle.
- WorklogRelationRules.java: neue native Relations-/Rechnungsschutzregeln ohne Schemaänderung.
- android/app/build.gradle: Version 1.34.0, Versionscode 51.
- README.md sowie neue/angepasste Architektur-, Browser- und JVM-Tests einschließlich Test-Runner.

Keine neue Abhängigkeit, kein neuer Primary Key, kein neues Produktionsschema. Authentifizierung, Tunnelidentität, Werkzeugnamen und Drive-Struktur wurden nicht umgestellt.

## 4. Tatsächlich durchgeführte Tests

| Funktion/Bereich | Testart | Tatsächlich ausgeführt | Ergebnis | Korrektur/Regression |
| --- | --- | --- | --- | --- |
| Architektur-Workflow, Filter, Profil, Galerie, Entwurf, Sitzungsreset, Bildschirmbreiten | Chromium mit Android-Testbrücke | Ja, 10 Szenarien | Erfolgreich | Neue Regressionstests |
| Design-/Profilbedienung | Browser mit Testbrücke | Ja, 11 Szenarien | Erfolgreich | Bestand erneut geprüft |
| Standort-/Zeiterfassungsoberfläche | Regeln und Browser mit simuliertem GPS | Ja, 11 Szenarien | Erfolgreich | Kein realer GPS-Test |
| Arbeitsaufzeichnungsfenster | Browser mit Testbrücke | Ja, 8 Szenarien | Erfolgreich | Bestand erneut geprüft |
| Rechnungseditor und Rechnungsfluss | Automatisierte JavaScript-Tests | Ja, 22 plus 12 Tests | Erfolgreich | Bestand erneut geprüft |
| Native Relationsregeln | JVM mit JSON-Testdaten | Ja, 17 Tests | Erfolgreich | Foto-/Rechnungsbezüge, neue IDs, historische Werte |
| Pixel-MCP-Protokoll und Schreibverträge | JVM/Vertragstests | Ja, 119 Tests | Erfolgreich | Rückwärtskompatibilität geprüft |
| Laptop CRUD, Fotos, Rechnungsansicht, Offline/Reload; mobile Fenster | Browser, isolierter HTTP-Testserver und Android-Testbrücke | Ja, 20 Szenarien | Erfolgreich | Neue Profilnavigation berücksichtigt |
| Backend, Datenintegrität, Synchronisation und Sicherung | Python mit isolierten Daten | Ja, 106 Tests, 0 übersprungen | Erfolgreich | Voller Suite-Rerun |
| MCP → Aktionsdatei → App-Import → Laptop-Snapshot | Echter lokaler MCP-SDK-Testclient, temporäre Dateien | Ja, 55 Prüfungen | Erfolgreich | Keine produktiven Schreibaktionen |
| Gebündelte Ausgangsdaten in mobiler Oberfläche | Browser-Smoke-Test | Ja | Erfolgreich | Anzeige ohne produktive Änderung |
| Produktive Laptop-Datenbank | SQLite ausschließlich lesend | Ja, zweimal | Integrität ok; Datei unverändert | Keine Datenkorrektur erforderlich |
| Bestehender Pixel-Pluginzugriff | Live, ausschließlich lesend | Ja, Health und Zusammenfassung | Bereit, Schreibfreigabe gemeldet | Kein Live-Schreibtest der neuen APK |
| Bestehender Laptop-Tunnel | Live, ausschließlich lesend | Ja | Client nicht erreichbar | Kein Zurücksetzen/Erneuern der Verbindung |
| Release-Build, Signatur, Version, eingebettete Assets | Gradle und APK-Prüfung | Ja | Erfolgreich | Geprüfte Assets entsprechen Quellcode |
| Neue APK auf echtem Pixel; Hardware-/Akku-/Dauerbetrieb | Gerätetest | Nein | Noch offen | USB-Gerätetest erforderlich |

Testbelege liegen unter outputs, insbesondere QA-Python-results.json, QA-MCP-E2E.json, QA-Browser-results.json, Architecture-v1.34-Browser.json, Worklog-Relation-Tests.txt und QA-Produktivdaten-readonly.json. Statische Architektur-/Sicherheitsprüfung ersetzt keine Gerätetests.

Der lokale End-to-End-Test verwendet echte MCP-Prozesskommunikation und den vorhandenen App-Importer, aber temporäre lokale Drive-Dateien. Er beweist nicht die komplette Google-Cloud-Verarbeitung oder einen produktiven ChatGPT-Schreibvorgang.

## 5. Produktive Datenprüfung

Laptop: 44 Kunden, 1 Auftrag, 164 Arbeitsaufzeichnungen, 95 Rechnungen, 12 Fotos. SQLite-Integrität ok, keine Fremdschlüsselverletzungen, keine gefundenen doppelten Primary Keys oder Quellschlüssel, keine fehlenden Fotodateien oder geprüften Rechnungsreferenzfehler. Alle 164 historischen Arbeitsaufzeichnungen ohne Auftrag bleiben gültig und unverändert.

Live-Pixel-Auskunft: 45 Kunden, 1 Auftrag, 164 Arbeitsaufzeichnungen, 966 Stunden, 164 als nicht abgerechnet markiert. Die unterschiedlichen Kundenanzahlen sind wegen Snapshot/lokaler Änderungen kein ausreichender Nachweis eines Datenverlusts; keine automatische Angleichung durchgeführt.

Ein vorhandener Rechnungsbestand beweist keine Zuordnung bestimmter Leistungen. Insbesondere darf aus 164 nicht-abgerechnet-Markierungen nicht ungeprüft eine tatsächliche Forderungssumme abgeleitet werden. Historische Abrechnungskennzeichen wurden nicht geändert.

## 6. Offene Risiken und sinnvolle nächste Schritte

1. Physischer Pixel-Test: Update ohne Deinstallation, Offline-Anlage/Bearbeitung, Start/Pause/Stop/Abbrechen, Zurück und GPS mit verweigerter/ungenauer Berechtigung. Testdaten isolieren oder klar kennzeichnen.
2. Dauerbetrieb des Pixel-Tunnels unter Mobilfunk, Netzwerkwechsel und Energiesparen tatsächlich messen. Android kann Hintergrunddienste trotz Gegenmaßnahmen beenden; keine Garantie ununterbrochener Erreichbarkeit.
3. Drive ist verzögert synchronisiert. Eine vorgemerkte Aktion ist noch keine bestätigte App-Speicherung. Vorhandene Bestätigungslogik weiter nutzen.
4. Thread-Locks und atomare lokale Dateien bieten keinen universellen Konfliktschutz zwischen mehreren Computern/Drive-Clients. Konfliktfälle und manuelle parallele Bearbeitung weiter absichern, bevor ein Mehrgerätebetrieb erweitert wird.
5. Umfangreiche mobile Skripte und geschichtete Render-/Zurück-Wrapper erschweren Wartung. Nur schrittweise, mit Regressionstests entflechten; kein kosmetischer Architektur-Neubau.
6. Rechnungsfinalisierung und bisherige Datenhoheit benötigen weiterhin den Laptop. Eine alleinige Pixel-Hauptdatenbank wurde nicht vorgetäuscht oder migriert.
7. Laptop-HTTP ist nicht verschlüsselt; nur vertrauenswürdiges LAN nutzen, nicht ungeschützt ins Internet veröffentlichen. Native Brücken bleiben auf vertrauenswürdige App-Assets beschränkt; externe Seiten öffnen außerhalb der WebView.
8. Die private APK enthält betriebliche Ausgangsdaten. APK nicht öffentlich teilen. Schlüssel verbleiben außerhalb des Quellcodes beziehungsweise verschlüsselt im Android-Schlüsselspeicher; keine Schlüssel in diesem Bericht.
9. Frühere Gemini-Quellcodekopie ist nicht automatisch auf diese Version aktualisiert. Bei Bedarf ausdrücklich eine bereinigte neue Kopie erzeugen.

## 7. Bereitstellung

Originalpaket at.hausgarten.service, Version 1.34.0, Versionscode 51; compile/target SDK 37, min SDK 26; bestehende Signatur beibehalten.

APK: outputs/Haus-und-Gartenservice-Pixel-v1.34.0.apk; Größe 2.636.294 Bytes.

SHA-256: 153c49992f51adede3b65630bb90980a6c57dcd256884f507cfaf2d1636721d1

Neue APK in bestehendem privaten Drive-Ordner gespeichert und Größe/Ordnerzuordnung rückgelesen. Vorgängerversion 1.33 in vorhandenen APK-Backups-Ordner verschoben und Zielzuordnung überprüft, nicht gelöscht. Auf dem Pixel die APK als Update installieren, nicht vorher deinstallieren.
