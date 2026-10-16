# Haus- & Gartenservice

## Alltag und Bedienung 1.41

Kundensuche und Hinzufügen sind unten erreichbar. Beim Verlassen/Öffnen der Kundenliste
wird nur der Suchtext geleert. Archivierte Kunden und ihre Statuswerte bleiben erhalten;
das Aktiv-Feld ist in der Oberfläche ausgeblendet. Mehr → Einstellungen bündelt die
bestehenden Drive-/ChatGPT-Zugänge und eine standardmäßig ausgeschaltete Seitenwisch-Option.
Zurück führt schrittweise über Dialog/Detail und Bereichsübersicht nach Heute.
Kundeneingaben werden vor versehentlichem Verwerfen geschützt; vorhandene Arbeitsentwürfe bleiben.
Ausstehende Übertragung und zurückgehaltene Prüfung werden getrennt angezeigt. Das
Rücklesen durch den Android-Dateianbieter ist weiterhin keine unabhängige Cloud-Bestätigung.
Keine Migration, keine neuen IDs für bestehende Datensätze und keine geänderte Tunnelkonfiguration.


Kostenlose, lokal betriebene Verwaltungs-App für Windows-Laptop und Google Pixel.

## Enthalten

- Kunden, Projekte und Aufträge
- Arbeitszeiten, Pausen, Kilometer, Material und Notizen
- deutsche Spracheingabe ohne kostenpflichtige KI-API
- Fotozuordnung und getrennte Kundenansicht
- Rechnungsübersicht
- mobile Offline-Warteschlange mit späterer Synchronisierung
- lokale SQLite-Datenbank und ZIP-Sicherungen, optional in einen Google-Drive-Ordner
- Airtable-inspirierte, aufgeräumte Laptop- und Mobilansicht

## Starten

1. `start-app.cmd` doppelt anklicken.
2. Die App öffnet sich unter `http://localhost:8765`.
3. Das angezeigte WLAN-Ziel am Android-Handy öffnen, solange Laptop und Handy im
   selben WLAN sind.
4. Am Handy einmalig den sechsstelligen Zugangscode aus dem Startfenster eingeben.

Python 3.11 oder neuer wird benötigt. Der Webserver nutzt die Standardbibliothek;
die Rechnungserstellung benötigt ReportLab und die mitgelieferten Schriftdateien.
Pillow ermöglicht verkleinerte Fotovorschauen. Die vorhandenen lokalen Pakete liegen
unter `.dependencies`; diese beim Kopieren der Anwendung mitnehmen.

## Pixel-App installieren

Die Original-App verwendet Android 17 / API 37 als Compile- und Target-Ziel.
Paketname at.hausgarten.service und bisherige Update-Signatur bleiben erhalten.
Neue APKs als Aktualisierung installieren, nicht durch Deinstallation.
Quellcodestand: 1.38.1 (Code 56). Bereitstellung und tatsächliche Testgrenzen stehen im
zugehörigen Architektur-Prüfbericht.

Version 1.38.1 entfernt den Gartenpirat-Zusatzbereich. Normale Spracheingabe
im Arbeitsformular und die bestehende ChatGPT-/MCP-Anbindung bleiben erhalten.
Nachweis und Installationsstand: outputs/Gartenpirat-entfernt-v1.38.1-ABSCHLUSS.txt.

Die fertige Installationsdatei liegt unter
`outputs/Haus-und-Gartenservice-Pixel-v1.38.1.apk`. Sie ist für Android 8 oder neuer gebaut
und wird ohne Play Store direkt auf dem eigenen Pixel installiert. Android fragt dabei
einmalig nach der Erlaubnis, Apps aus dieser Quelle zu installieren.

Die mobile Startseite läuft eigenständig auf dem Pixel. Arbeitszeiten, Material,
Kilometer, Notizen und Fotos werden zuerst lokal gespeichert. **Mobil** öffnet diese
Erfassung. Die untere Navigation führt zu Heute, Kunden, Arbeiten und Mehr.
Drive ist über den Status oben beziehungsweise die Startseite erreichbar.
Ein gemeinsames WLAN ist für mobile Erfassung und Drive-Abgleich nicht erforderlich.
Der endgültige Rechnungsabschluss mit Nummer und PDF benötigt aktuell weiterhin
den Laptop und dessen Abgleich. Drive ist ein Austausch-/Sicherungsweg, keine
gleichzeitig beschreibbare SQLite-Datenbank.

Der Zugriff vom Laptop ist direkt möglich. Andere Geräte im WLAN erhalten ohne den
bei jedem Start neu erzeugten Zugangscode keinen Zugriff auf APIs, Fotos oder Kundendaten.

## Google-Drive-Sicherung und Pixel-Abgleich

Google Drive für Desktop kann kostenlos installiert werden. In der App unter
**Einstellungen** den lokalen Google-Drive-Ordner als Sicherungsordner eintragen.
Die aktive Datenbank bleibt bewusst außerhalb des Sync-Ordners; geschlossene
ZIP-Sicherungen und kleine Austauschdateien werden dort abgelegt.

Auf dem Pixel beim ersten Druck auf **Drive** denselben Ordner über die Android-
Dateiauswahl auswählen. Das Pixel legt seine noch nicht übernommenen Einträge dort
ab und liest den aktuellen Kunden- und Auftragsstand ein. Die Laptop-App prüft den
Ordner alle 30 Sekunden, solange sie läuft. Bereits bestätigte mobile Einträge werden
nicht doppelt importiert.

## ChatGPT-Tunnel automatisch starten

Für die Sprachsteuerung muss der jeweilige private MCP-Tunnel laufen.
Die Pixel-App besitzt einen eigenen Tunnel mit gesonderter Schreibfreigabe und
lokaler Offline-Warteschlange. Der Pixel-Tunnel benötigt keinen eingeschalteten
Laptop; für dessen dauerhafte Erreichbarkeit bleiben Internet, Android-Service und
Akku-Einstellungen relevant. Ein laufender Laptop ist weiterhin für den endgültigen
Rechnungsabschluss und die Übernahme ins Laptop-Datenarchiv erforderlich.

Der frühere Windows-Tunnel wurde am 12.10.2026 auf Wunsch entfernt, einschließlich
OpenAI-Tunnel, Autostart-Aufgabe und aktiver Startskripte/Laufzeit. Die lokale
Wiederherstellungskopie liegt unter data/backups/retired-laptop-tunnel-*/.
Der separate Pixel-Tunnel, seine Schlüssel und Freigaben wurden nicht verändert.
Die Web-App bleibt ohne eigenen Tunnel an denselben Drive-Ordner angebunden.

Ab 1.40 verwendet das Pixel den vorhandenen Gesamtstand plus die erhaltenen
Änderungsdateien als Drive-Datenbasis. Kunden, Aufträge, Arbeiten und Fotos werden
aus diesen Journals ohne Laptop wiederhergestellt. Konflikte bleiben zur Prüfung
erhalten. Rechnungsabschluss und geschützte Löschvorgänge verwenden weiterhin den
vorhandenen Web-Importer. Physische Löschaktionen aus Drive-Journals bleiben im
Pixel-first-Abgleich zur gesonderten Freigabe zurückgehalten. Die Sicherheitsprüfung
hat eine automatische Durchleitung nicht freigegeben. Keine zusätzliche Drive-Ordnerstruktur.

Die Android-Dateifreigabe bestätigt das Rücklesen durch die Drive-App, nicht
unabhängig den Upload in die Google-Cloud. Entsprechend gekennzeichnete Übergaben
dürfen nicht als unabhängig verifizierte Cloud-Synchronisierung verstanden werden.

Wichtig: Der tatsächliche Google-Drive-Abgleich muss einmal auf dem Pixel und Laptop
mit dem eigenen Google-Konto getestet werden. Dieser Anmeldeschritt kann nicht im
Voraus automatisiert werden.

## Datenspeicher

- Datenbank: `data/haus-garten.db`
- Fotos: `data/photos/`
- Standardsicherungen: `data/backups/`

Vorhandene Kunden und Rechnungen können wiederholbar mit
`py -3.11 import_existing_data.py` importiert werden. Bereits vorhandene technische
sevDesk-IDs werden nicht doppelt angelegt.

## Reproduzierbare Qualitätsprüfung

`py -3.11 -m unittest discover -s tests -v` prüft Geschäftslogik, Beziehungen,
HTTP-Endpunkte, lokale Drive-Austauschdateien und Sicherungen ausschließlich mit
temporären Testdaten. Die beiden JavaScript-Suiten können direkt mit
`node tests/invoice-flow.test.js` und `node tests/invoice-editor.test.js` laufen.

`tests/release_qa_server.py` startet mit `PYTHONPATH=.` die echte Laptop-Anwendung
auf Port 18765 mit einer temporären Datenbank. `tests/mobile_invoice_preview_server.py`
stellt die Android-Oberfläche mit einem ausdrücklich simulierten Android-Speicher
bereit. `tests/release-browser.test.js` führt Browser-Workflows aus; die zweite URL
wird als `QA_MOBILE_URL` gesetzt. Der Test verwendet lokal installiertes Chrome und
die vorhandene Playwright-Laufzeit. Ein Android-Oberflächentest im Browser ist kein
Test des echten Android-Speichers oder der Google-Drive-App.

Produktive Import-/Reparaturskripte nicht als Test starten. Sie ändern reale Daten.
Der aktuelle Prüfbericht und die Testnachweise liegen unter `outputs/`.

## Gesamtabnahme (12.10.2026)

`py -3.11 tests/full_quality_audit.py` führt die isolierten Python-, MCP-, JVM-
und Browser-Suiten aus. Jeder Lauf erhält einen eigenen Ordner in `outputs/`.
Fehlgeschlagene Schutztests werden nicht als Erfolg behandelt. Die acht am
12.10.2026 nachgewiesenen Schutzlücken wurden mit Version 1.38 behoben und erneut
geprüft. Externe Provider, Versand und Langzeitbetrieb bleiben gesonderte Testbereiche;
ein erfolgreiches Build allein bedeutet keine vollständige Freigabe.

`tests/native_device_acceptance.py` prüft die Originalversion 1.37 auf einem
autorisierten Pixel 10 Pro mit eigener Testinstrumentierung und isolierten Daten.
Es aktualisiert nicht die Betriebs-App; erwartet wird derzeit Code 55 (über
HG_EXPECTED_VERSION_CODE ausdrücklich überschreibbar). Die Diagnose-APK muss vorher gebaut und
mit der vorhandenen Signatur signiert werden. Der Test kann den Hintergrunddienst
unterbrechen: ein anschließender Startversuch ist noch kein Nachweis der aktiven
ChatGPT-Verbindung. Der Lauf versucht ausschließlich eine zuvor aktivierte Verbindung
ohne Änderung ihrer Konfiguration wieder zu starten. Danach tatsächliche
Erreichbarkeit prüfen, gegebenenfalls in der App „Verbindung starten“ verwenden.

Der vollständige Bericht mit Testmatrix liegt unter
`outputs/ARCHITEKTUR-FUNKTIONSANALYSE-2026-10-12.txt`.
Der anschließende Korrektur- und Testbericht steht unter
`outputs/SCHUTZLUECKEN-v1.38-ABSCHLUSS.txt`. Beim Öffnen startet die Original-App
ausschließlich eine bereits aktivierte/eingerichtete Tunnelverbindung erneut,
falls der Dienst nicht läuft. Schlüssel und Schreibfreigaben werden dabei nicht geändert.
