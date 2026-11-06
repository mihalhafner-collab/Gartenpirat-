# Gartenpirat: verbindliche Arbeitsregeln

## Verbindlicher Ausgangspunkt

Dieses GitHub-Repository ist der einzige verbindliche Einstiegspunkt und die
alleinige aktuelle Quelle für Projektregeln, Arbeitsstand und Entwicklungsaufträge.
Bei jedem neuen Chat und vor jeder Entwicklungsaufgabe zuerst den aktuellen Stand
von `main`, danach diese `AGENTS.md` und `struktur-neu/AKTUELLER-STAND.json` lesen.
Benötigte Fachregeln und Nachweise ausschließlich über die dort referenzierten
Dateien dieses Repositories ermitteln. Keine externen Regel- oder Übersichtsseiten
als zusätzliche Pflichtquelle voraussetzen. Frühere Gespräche und Exporte sind
Quellenmaterial, keine eigenständig geltenden Arbeitsaufträge.
Die Kotlin-App unter `struktur-neu/android/app/src/main/` ist die einzige funktionale Codebasis für TEST und PRODUKTIV.
Vor Arbeit an einem Kandidaten `python3 -B struktur-neu/tools/check_release_baseline.py`
ausführen. Alte Java/WebView-Quellen aus `app/`, `app/src/fullTest/`, historischen
Branches, Commits oder Archiven niemals als Kandidatenbasis wiederherstellen.
Designideen dürfen übernommen werden, ihre Umsetzung erfolgt ausschließlich in
der verifizierten Kotlin-/Compose-Basis. Bei fehlschlagender Quellprüfung stoppen;
keine Ersatzbasis und keine Umgehung der Build-Sperre.
Vor produktiven Geräteaktionen und vor der Auswahl einer produktiven Entwicklungsbasis über
`Haus & Gartenservice Pixel.get_capabilities` die tatsächlich installierte App
prüfen: Paket, version_code, version_name sowie runtime.build_fingerprint mit
Git-Commit und source_sha256 abgleichen. Die installierte Version ist maßgeblich;
keine feste Buildnummer aus Erinnerungen, alten Chats oder Prüfprotokollen ableiten.
Bei fehlender Verbindung, unvollständiger Identität oder abweichendem Quellstand
keine Geräteänderung und keine Entwicklung auf einer vermuteten Ersatzbasis beginnen.
Die Lücke benennen und die passende aktuelle Quelle ermitteln.
Produktive Entwicklungsänderungen ausschließlich von der verifizierten installierten Quelle
ableiten; geänderten Code niemals als bereits installierten Build ausgeben.
Nach einem Geräteupdate die Identität erneut lesen und den vorhandenen Arbeitsstand
aktualisieren. Keine neuen Branches, Tags oder Verweisdateien ohne Nutzerauftrag.

## Gemeinsame Build- und Ablageregel

TEST und PRODUKTIV entstehen ausschließlich aus dem identischen Kotlin-/Compose-
Quellcode unter `struktur-neu/android/app/src/main/`. Der Testkanal verwendet
eine getrennte Paketkennung, eigene Testdaten und seine bisherige Signatur.
Produktive Änderungen werden aus der verifizierten installierten Quelle abgeleitet,
niemals aus historischen APKs oder anderen Implementierungen.

Geschäftsdaten und vollständige ZIP-Sicherungen werden nur lokal auf dem Pixel
geschrieben. Es gibt keine verpflichtende Cloud- oder externe Sicherung für die
Umstellung auf lokalen Betrieb. Historische Drive-Schreiber und versuchsweise importierte sevDesk-Rechnungen
begründen keine Freigabe- oder Nachweispflicht für den übrigen App-Betrieb.
Der gesonderte Auftrag vom 06.11.2026 erlaubt die nachfolgend begrenzte lokale Rechnungsfunktion.
Manuelles Kopieren von Sicherungen liegt außerhalb der App. Google Drive wird nur
für die ausdrücklich freigegebene Verteilung originalsignierter Installations-APKs
verwendet; keine automatische Übertragung von Geschäfts- oder Sicherungsdaten.
Die Verteilung benötigt jeweils einen eigenen ausdrücklichen Auftrag.

## Lokale Abrechnung und Rechnungen (Eigentümerauftrag 06.11.2026)

Der Auftrag ersetzt die bisherige Beschränkung auf sevDesk für gewöhnliche
österreichische Inlandsabrechnungen in Euro mit Kleinunternehmerbefreiung ohne
Umsatzsteuerausweis. Abrechnung behält die getrennten Arten Rechnung und Labor
clandestino, markierte Aufträge, gemeinsame Filter und die Gesamtsumme beider
Arten. Sie ist keine bloße Rechnungsliste. Benutzerzuordnung, aktive
Leistungsbindung, Belegstatus und Zahlungsstand bleiben getrennt.

Beide Arten verwenden dieselben tatsächlichen Leistungen, Mengen und bewusst
bestätigten Preise. Rechnung erhält nach Vorschauprüfung eine eigene lokale
Nummer und ein unveränderliches Original-PDF; Labor wird intern nachvollziehbar
bestätigt, ohne Rechnung, Rechnungsnummer oder künstlichen Beleg. Abschluss,
Wiederholung, Parallelität, Korrekturen, Zahlungen, lokaler Export und ZIP-Sicherung
müssen beide Arten berücksichtigen. Die Kategorie begründet keine steuerliche
Befreiung. Steuerstatus nicht aus App-Umsätzen ableiten oder selbstständig ändern.

Historische sevDesk-Umsätze und Versuchsdaten nicht neu ausstellen oder automatisch
übernehmen. Alte Rechnungs-, WebView-, Cloud- und Migrationsimplementierungen
bleiben stillgelegt. Keine Pflichtcloud, sevDesk-/Bankanbindung, automatischen
Mahnungen, Anzahlungsrechnungen oder allgemeine Steuerverwaltung ergänzen.
Ausgestellte Originale, abgeschlossene interne Vorgänge, Zahlungen und notwendige
Historien vor gewöhnlichem Löschen und Speicherbereinigung schützen; keine
Alterslöschung. Die allgemeine direkte Löschbedienung bleibt für zulässige Daten
erhalten. Nach Restore neue eindeutige Nummernserie verwenden und restaurierte
Quellen vor erneuter Abrechnung bewusst gegen den aktuellen Belegstand prüfen.

Der Auftrag umfasst Entwicklung und isolierte synthetische Validierung mit
unabhängiger technischer und fachlicher Gegenprüfung durch Subagenten. Er erlaubt
keine produktive Installation, Rechnungsausstellung, Geschäftsdatenmigration oder
APK-Verteilung. Bestehende Angebote, Originale und lokale ZIP-Dateien erhalten.

## Eigentümerentscheidung 03.11.2026: Löschen, Speicher und Menüs

„Löschen“ bedeutet endgültiges Entfernen mit einem Antippen, einschließlich
zugehöriger ungeteilter Dateien. Kein neuer Papierkorbeintrag, keine zusätzliche
Wahl zwischen Archivieren, Wiederherstellen und endgültigem Löschen. Zuordnungen
intern bereinigen. Löschaktionen haben keine Bestätigungsdialoge oder Pflichttexte. Aktuelle Versionen, Verknüpfungen und unklare
Vorgangsergebnisse intern prüfen; keine doppelte Ausführung. Diese Entscheidung
ersetzt die bisherigen zusätzlichen UI-Bestätigungen beim Löschen und Verwerfen.
Rückfallkopien einschließlich Erstellen, Prüfen und Wiederherstellen sind
abgeschafft. Vorhandene interne Rückfallkopien und funktionslose Sicherungs-,
Upload- und Restore-Arbeitsdateien samt leeren Unterordnern dürfen entfernt werden.
Insbesondere `full-backup-work` nicht zurücklassen. Laufende Austauschvorgänge,
Gerätekennung, gespeicherte Geschäftsdaten und bewusst gewählte lokale ZIP-Dateien
bleiben getrennt. Menüs verwenden kurze deutsche Texte ohne Rohdatenwände.
Das eigenständige Entwürfe-Menü ist entfernt. Laufende Formularbearbeitung und
Vorgangskennungen bleiben intern erhalten. Endgültiges Löschen markierter Testdaten
schließt ihre verknüpften Test-Arbeiten, Testfotos und übrigen Testdatensätze ein.
Keine zusätzlichen Bestätigungen. Nicht zugehörige Geschäftsdaten und laufende
Zeiterfassung bleiben geschützt; erfolgreiche Löschung anhand des wirksamen
Datenstands prüfen, eine Queue-Quittung allein genügt nicht.

## Umsetzung

KISS und YAGNI: flacher, direkter Code; keine vorsorglichen Abstraktionen,
Abhängigkeiten oder zusätzlichen Dateien. Bestehende Dokumente aktualisieren.
Paketkennung `at.hausgarten.service`, Originalsignatur, SQLite-Schema und bestehendes
Backup-Format erhalten. Nur vollständig synthetische Testdaten verwenden; keine
produktiven Kundendaten, Datenbanken, Fotos oder Sicherungen für Entwicklung lesen.
Autorisierte Aufträge direkt vollständig ausführen; bereits erteilte Freigaben
nicht erneut erfragen. Subagenten nur bei ausdrücklichem Auftrag einsetzen.
Keine Installation, Migration, Wiederherstellung oder Rechnungsfreigabe aus einem
allgemeinen Entwicklungsauftrag ableiten. Produktive Gerätearbeiten ausschließlich über die
aktuelle Pixel-Verbindung; Entwicklungsprüfungen isoliert mit synthetischen Daten.

## Prüfung und Abschluss

Nur passende Prüfungen ausführen. `python3 -B struktur-neu/tools/check_current.py`
prüft den aktuellen Quellstand. Geräteidentität und Quellfingerabdruck sind kein
Beleg für einen Gerätefunktionstest. Fehlende oder fehlgeschlagene Prüfungen nennen.
Nur eigene geprüfte Änderungen nach `main` committen, pushen und zurücklesen.
Ergebnisse und Einschränkungen kurz melden; keine zusätzlichen Prüfberichte anlegen.
