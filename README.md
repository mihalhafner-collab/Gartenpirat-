# Aktueller verbindlicher Arbeitsweg: Codex Cloud only

**Entwicklung, Build, Tests, Originalsignierung und Releasevorbereitung erfolgen ausschließlich in Codex Cloud.**

Der Laptop/Desktop ist ab 18. Oktober 2026 **kein Bestandteil der aktiven Arbeitsweise, keine Voraussetzung und kein Fallback**. Der operative Zielpfad ist **Pixel-App ↔ Google Drive direkt**; der Entwicklungs- und Releasepfad ist **Codex Cloud ↔ GitHub**. Historische Laptop-/Web-Hinweise weiter unten dokumentieren frühere Architekturstufen und sind keine aktuelle Handlungsanweisung.

Funktionen, die technisch noch einen lokalen Web-/Laptop-Prozessor voraussetzen, gelten als **noch nicht vollständig migriert**. Sie müssen vor produktiver Freigabe in Codex Cloud auf Pixel/Drive-direkte Verarbeitung umgestellt werden. Historische Dateinamen wie `HausGarten-Laptop.json` können aus Kompatibilitätsgründen bestehen bleiben und bedeuten keine Laptop-Abhängigkeit.

# Cloud-Prüfkandidat 1.49.2 / 71

[Originalsignierte APK und Prüfsumme](downloads/README.md) · [Fortlaufender Auftrag-/Abnahmestatus](CODEX-STATUS.md)

Die native Pixel-/Drive-Verarbeitung ist implementiert und isoliert geprüft. Der Pixel läuft weiterhin auf 1.49.0/69; neun Reviews bleiben erhalten. **Gesamtauftrag und Gerätefreigabe sind offen.** Vor einem Update vollständige Sicherung und ausdrückliche Freigabe; keine automatische Installation oder Datenlöschung. Originalsignatur allein beweist keinen Datenerhalt.

# Historischer gemeinsamer Integrationsstand: 18. Oktober 2026

Die bestehenden Entwicklungsstände wurden im selben Repository zusammengeführt.
Der Update-Kandidat 1.46.1 (`versionCode 66`) verwendet `at.hausgarten.service`.
Pflanzenlexikon und Fotodokumentation sind Bestandteile dieser App.
Der aktive manuelle Workflow baut ausschließlich einen **unsignierten, nicht zur
Installation freigegebenen Kandidaten**. Originalsignatur, tatsächlicher
Pixel-Paket-/Versionsstand und Datenerhalt beim Update sind noch nachzuweisen.
Keine neue QA-App installieren und keine Originaldaten löschen.

[Integrations- und Prüfnachweis](INTEGRATIONSNACHWEIS-2026-10-18.md).
Die nachfolgenden älteren Versions- und Bereitstellungshinweise sind historischer
Projektkontext und keine Freigabe des aktuellen Kandidaten.

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


Private Verwaltungs-App mit Google Pixel als operativer Arbeits-App und Google Drive als zentralem Datenabgleich. Entwicklung und Release erfolgen ausschließlich in Codex Cloud.

## Enthalten

- Kunden, Projekte und Aufträge
- Arbeitszeiten, Pausen, Kilometer, Material und Notizen
- deutsche Spracheingabe ohne kostenpflichtige KI-API
- Fotozuordnung und getrennte Kundenansicht
- Rechnungsübersicht
- mobile Offline-Warteschlange mit späterer Synchronisierung
- lokale SQLite-Datenbank und ZIP-Sicherungen, optional in einen Google-Drive-Ordner
- aufgeräumte mobile Bedienung; historische Web-Oberfläche bleibt nur als Legacy-Code erhalten

## Entwicklung und Build

Quellcodeänderungen, Qualitätsprüfung, Android-Build und Originalsignierung werden ausschließlich in der persönlichen Gartenpirat-Codex-Cloud-Umgebung ausgeführt. Keine lokale Laptop-/Desktop-Entwicklungsumgebung ist Teil des freigegebenen Ablaufs. GitHub `main` ist die gemeinsame Codebasis; Pixel und Drive sind die operative Datenebene.

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
Der Zielzustand für Rechnungsabschluss, Nummernvergabe, Angebote und Fotos ist Pixel/Drive-direkt. Eine Funktion, die noch einen lokalen Web-/Laptop-Prozessor verlangt, ist nicht produktionsreif und wird in Codex Cloud weiter migriert. Drive ist Austausch- und Sicherungsweg; Konfliktschutz und zentrale Nummernvergabe müssen dabei erhalten bleiben.

Ein lokaler Laptop-Zugriff ist nicht Bestandteil der aktuellen Arbeitsweise. Der private Pixel-Tunnel und seine Berechtigungen bleiben der vorgesehene ChatGPT-/MCP-Zugriff auf die operative App.

## Google-Drive-Sicherung und Pixel-Abgleich

Der Pixel greift direkt auf den bestehenden Google-Drive-Datenbereich zu. Änderungen werden lokal sicher vorgemerkt, mit stabilen IDs und Prüfsummen übertragen und erst nach bestätigter Verarbeitung aus der Warteschlange entfernt. Ein Laptop-/Desktop-Importer ist kein zulässiger Pflichtbestandteil des Zielsystems. Alte Austauschdateien und Dateinamen bleiben bis zu einer geprüften Migration kompatibel.

Provider-Rücklesen und unabhängige Google-Cloud-Bestätigung bleiben unterschiedliche Zustände. Konflikte werden fail-closed erhalten und nicht automatisch überschrieben.

## ChatGPT-Tunnel automatisch starten

Für die Sprachsteuerung muss der jeweilige private MCP-Tunnel laufen.
Die Pixel-App besitzt einen eigenen Tunnel mit gesonderter Schreibfreigabe und
lokaler Offline-Warteschlange. Der Pixel-Tunnel benötigt keinen eingeschalteten
Laptop; für dessen dauerhafte Erreichbarkeit bleiben Internet, Android-Service und
Akku-Einstellungen relevant. Ein laufender Laptop ist nicht erforderlich und darf künftig nicht als Voraussetzung eingeführt werden. Noch vorhandene Abhängigkeiten im Legacy-Code müssen auf Pixel/Drive-direkte Verarbeitung migriert werden.

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

Wichtig: Der tatsächliche Google-Drive-Abgleich muss auf dem Pixel mit dem eigenen Google-Konto getestet werden. Geräte- und Kontofreigaben können nicht durch Quellcode ersetzt werden.

## Datenspeicher

Die operative Datenhaltung liegt auf dem Pixel und im bestehenden Drive-Abgleich. Die früheren lokalen Python-/SQLite-Pfade im Repository sind Legacy-/Testbestand und keine produktive Laptop-Voraussetzung.

Vorhandene Kunden und Rechnungen können wiederholbar mit
`py -3.11 import_existing_data.py` importiert werden. Bereits vorhandene technische
sevDesk-IDs werden nicht doppelt angelegt.

## Reproduzierbare Qualitätsprüfung

`py -3.11 -m unittest discover -s tests -v` prüft Geschäftslogik, Beziehungen,
HTTP-Endpunkte, lokale Drive-Austauschdateien und Sicherungen ausschließlich mit
temporären Testdaten. Die beiden JavaScript-Suiten können direkt mit
`node tests/invoice-flow.test.js` und `node tests/invoice-editor.test.js` laufen.

`tests/release_qa_server.py` startet mit `PYTHONPATH=.` die historische Web-Anwendung zu isolierten Regressionstests
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
