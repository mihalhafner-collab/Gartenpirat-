# Abschlussbericht – Haus- & Gartenservice-App 1.46.0

Prüfstand: 16. Oktober 2026  
Ausgangsversion: 1.45.0 (`versionCode 64`)  
Bearbeiteter Stand: 1.46.0 (`versionCode 65`)  
Arbeitsbranch: `codex/v1.46-functional-optimization-20261016`

## Status und Aussagegrenzen

Dieser Bericht dokumentiert den gegenwärtigen Quellcode- und Teststand. Er trennt bewusst zwischen implementiertem Code, automatisierten Tests und einer Freigabe auf dem produktiven Pixel.

- Die Änderungen wurden in einer getrennten Arbeitskopie beziehungsweise einem getrennten Branch vorgenommen.
- Produktive Kunden-, Arbeits-, Rechnungs- und Synchronisationsdaten wurden nicht als Testdaten verwendet und nicht gelöscht.
- Der Android-Build wurde in der vorliegenden Umgebung nicht ausgeführt, weil keine vollständige verwendbare JDK-/Android-SDK-/Gradle-Toolchain verfügbar war.
- Es wurde keine APK aus diesem Stand erzeugt, signiert, installiert oder verteilt.
- Fresh-Install und In-place-Update von 1.45.0 auf 1.46.0 wurden nicht ausgeführt.
- Kamera, Galerie, Pinch-to-Zoom, Android-Zurück und die Migration wurden nicht auf einem realen Android-Gerät geprüft.
- Ein echter Google-Drive-Abgleich über das Benutzerkonto und eine unabhängige Online-Bestätigung wurden nicht ausgeführt.
- Browserprüfungen verwenden ausschließlich isolierte synthetische Daten und simulierte native Schnittstellen. Sie sind kein Ersatz für eine Geräteabnahme.

Der Stand ist daher technisch weitgehend implementiert und automatisiert geprüft, aber **noch nicht als produktive Android-Version freigegeben**.

## Bestandsaufnahme und Architektur

### Repository- und Versionsstand

Die lokale vollständige Ausgangsbasis weist Version 1.45.0 / Code 64 aus. Der untersuchte GitHub-`main`-Stand lag dagegen noch auf Version 1.42.0 / Code 61. Für die Weiterentwicklung wurde deshalb die vollständige lokale 1.45.0-Basis in einem separaten Feature-Branch gesichert; der ältere Hauptbranch wurde nicht überschrieben. Vor einer Zusammenführung muss nachvollziehbar feststehen, dass die vollständige 1.45.0-Historie und alle noch nicht veröffentlichten Zwischenstände berücksichtigt sind.

### Tatsächliche Projektstruktur

| Bereich | Tatsächliche Umsetzung |
| --- | --- |
| Android-App | Native Java-Hülle mit `WebView`; Oberfläche aus lokalen HTML-, CSS- und JavaScript-Assets |
| Laptop-/Web-App | Python-3.11-HTTP-Server mit statischer HTML-/CSS-/JavaScript-Oberfläche |
| Android-Datenspeicher | `SharedPreferences` für den bestätigten Drive-Gesamtstand; SQLite für Offline-Journal, Arbeitssitzungen und nun Fotoanhang-Metadaten; private App-Dateien für Foto-Originale |
| Laptop-Datenspeicher | SQLite in `data/haus-garten.db`; Foto-Originale in `data/photos/`; Sicherungen in `data/backups/` |
| Cloud-Anbindung | Bestehender Google-Drive-Ordner; Android über Storage Access Framework, Laptop über den lokal verbundenen Drive-Ordner |
| Synchronisierung | Gesamtstand plus unveränderlich behandelte Änderungsjournale, Empfangs-/Bestätigungskennungen, Konflikt- und Prüfwarteschlangen |
| Sprach-/MCP-Anbindung | Bestehender privater Pixel-Tunnel und vorhandene MCP-Lese-/Schreibwerkzeuge; keine neue externe Plattform |
| Pflanzenlexikon | Ein gemeinsamer JSON-Katalog und lokale Assets; Android- und Web-Spiegel werden aus derselben Quelle erzeugt |
| Tests | Python-`unittest`, Browser-/Playwright-Suiten, Java-/JVM-Tests, Android-Instrumentierung, Datenschutz- und Release-Skripte |

Verwendete Sprachen und Formate sind Java, Python, JavaScript, HTML, CSS, Gradle/Groovy, JSON, PowerShell und Windows-Batchdateien. Die vorhandene Hybridarchitektur wurde beibehalten; es wurde weder eine zweite App noch eine zweite betriebliche Datenbank eingeführt.

### Vorhandene Daten- und Synchronisationsschutzmechanismen

Die bestehende App arbeitet offline-first. Android schreibt Änderungen zunächst in eine lokale `pending`-Tabelle. Der Drive-Abgleich übergibt diese Journale, führt bestätigte Kennungen zusammen, hält Konflikte beziehungsweise Prüfaufträge fest und entfernt lokale Warteschlangeneinträge erst nach passender Bestätigung. Der bestätigte Gesamtstand liegt getrennt davon in `SharedPreferences`. Auf dem Laptop schützt ein Sync-Lock den Import/Export; Beziehungen und Vergleichsstände werden vor Änderungen geprüft.

Diese Trennung wurde erhalten. Insbesondere bedeutet ein lokales Speichern oder ein Rücklesen durch den Android-Dateianbieter weiterhin nicht automatisch, dass die Datei unabhängig online in Google Drive bestätigt wurde.

### Ausgangsrisiken

1. Foto-Originale waren teilweise als große Base64-Felder in Journalen oder Snapshots enthalten und dadurch speicher- und synchronisationskritisch.
2. Bildreferenzen hatten nicht durchgehend überprüfbare Prüfsumme, Größe, MIME-Typ und einen kanonischen Dateinamen.
3. Beim initialen Öffnen der Arbeitsmaske konnte der automatische Fokus die Bildschirmtastatur einblenden.
4. Der Piratenknopf war nicht streng an die aktive Haupt-/Heute-Oberfläche gebunden.
5. Synchronisationsstatus wurden für Anwender nicht klar genug in lokal, an Provider übergeben und online bestätigt getrennt.
6. Das Pflanzenlexikon hatte keinen fachlich getrennten Neophytenbereich und keine Vollbildnavigation für mehrere Pflanzenbilder.
7. Eine unkontrollierte Löschung eines mit Auftrag oder Arbeitsaufzeichnung verknüpften Fotos könnte Belegdaten beschädigen.

## Technische Entscheidungen

- **Bestehende Architektur statt Neubau:** Die Java-/WebView-, Python-, SQLite- und Drive-Strukturen bleiben erhalten.
- **Additive Datenmodelle:** Bestehende Tabellen, IDs und Journale werden nicht neu nummeriert. Neue Foto-Metadaten werden durch zusätzliche Spalten beziehungsweise eine zusätzliche Android-Tabelle eingeführt.
- **Inhaltsadressierte Fotoablage:** Foto-Originale werden anhand des vollständigen SHA-256-Hashes benannt. Größe, MIME-Typ und Bildinhalt werden vor Verwendung geprüft.
- **Metadaten im Journal, Binärdatei separat:** Neue Fotoaktionen tragen Referenz und Integritätsdaten; das Original liegt separat im vorhandenen Drive-Ziel unter `HausGarten-Fotos`.
- **Crash-sicherer Arbeitsfoto-Retry:** Ist die Arbeitsaufzeichnung bereits gespeichert, das zugehörige Foto aber noch nicht, hält eine private `AtomicFile` den vorbereiteten JPEG-Auftrag mit stabiler Foto-ID bis zum nachgewiesenen Speichern oder ausdrücklichen Verwerfen. Ein WebView- oder App-Neustart verliert diesen Auftrag dadurch nicht.
- **Fail-closed bei Konflikten:** Unpassende Prüfsumme, Größe, MIME-Angabe, doppelte gleichnamige Drive-Dateien oder widersprüchliche Foto-ID führen zum Abbruch der betroffenen Übernahme. Vorhandene Daten bleiben erhalten.
- **Löschen erst nach Bestätigung:** Ein physisches Android-Foto-Original wird erst entfernt, wenn die Löschaktion bestätigt ist, das Foto nicht mehr im bestätigten Snapshot steht, keine offene Fotoaktion geschützt werden muss und kein weiterer Datensatz denselben Inhalt referenziert.
- **Keine zusätzliche Zoom-Bibliothek:** Kunden- und Pflanzenbildansichten verwenden Pointer-Ereignisse, Pinch, Drag, Tastatur und vorhandene Dialogkomponenten.
- **Kamera ohne neue Laufzeitberechtigung:** Die direkte Aufnahme verwendet `ACTION_IMAGE_CAPTURE` und einen eng begrenzten, nicht exportierten Provider für eine zufällige temporäre Datei im App-Cache. Diese Implementierung ist vorhanden, aber noch nicht auf einem echten Gerät geprüft.
- **Eine Pflanzenbibliothek:** Der bestehende Katalog wurde auf Schema 2 erweitert; kein zweites Lexikon und keine zweite Pflanzendatenbank wurden angelegt.
- **Keine neuen kostenpflichtigen Dienste:** Fach- und Bilddaten verwenden amtliche Quellen und zulässige Wikimedia-Commons-Lizenzen; es wurde kein kostenpflichtiger Dienst integriert.

## A. Implementierte Funktionen

### Einstellungen

- Das vorhandene Einstellungsmenü ist in die Bereiche **Bedienung**, **Daten & Synchronisierung**, **Verbindung & Diagnose** und **App-Information** gegliedert.
- Die vorhandene Wischoption wird weiterhin lokal gespeichert. Ein Speicherfehler setzt die Oberfläche auf den tatsächlich aktiven vorherigen Wert zurück und meldet, dass nichts gespeichert wurde.
- Lokale Änderungen, Übertragungsstand, Prüfbedarf und der letzte erfolgreiche Abgleich werden getrennt dargestellt.
- Provider-Rücklesen, App-Bestätigung und unabhängige Online-Bestätigung werden sprachlich nicht gleichgesetzt.
- Der vorhandene Pixel-Verbindungsdialog wird wiederverwendet; es wurde keine parallele Verbindungsfunktion geschaffen.
- Die bestehende Aktiv-/Archiv-Funktion eines Kunden bleibt erreichbar.

### Piratenknopf

- Die vorhandene Komponente `#gp-launcher` wird weiterverwendet; es gibt keine zweite Implementierung.
- Die Sichtbarkeitslogik beobachtet aktive Seiten beziehungsweise Ansichten und offene fremde Dialoge.
- In der Android-App entsprechen Hauptbildschirm und „Heute“ derselben tatsächlichen Dashboard-Oberfläche `#home`; in der Web-App ist das Gegenstück `#view-dashboard`.
- Der Knopf ist nur auf dieser Haupt-/Heute-Oberfläche sichtbar und fokussierbar. In Kunden-, Arbeits-, Einstellungs-, Lexikon- und sonstigen Unteransichten sowie über fremden Dialogen ist er ausgeblendet.
- Programmatisches Öffnen wird ebenfalls verhindert, wenn der Knopf in der aktuellen Ansicht nicht erlaubt ist.
- Bestehendes Öffnen, Zurück, Verschieben und Positionsspeichern bleiben erhalten.

### Arbeit starten und Tastaturverhalten

- Der Auswahl-Dialog fokussiert beim Öffnen eine nicht editierbare Überschrift statt das erste Eingabefeld.
- Auch nach Rückkehr aus einer gestoppten Zeiterfassung erhält die Überschrift der Arbeitsmaske den Fokus.
- Die Bildschirmtastatur wird dadurch erst nach der bewussten Auswahl eines Such- oder Eingabefeldes angefordert.
- Kundenzuordnung, optionale Auftragszuordnung, Timer-Sitzung und vorhandene Entwürfe bleiben im bestehenden Ablauf.
- Das Speichern von Arbeitsaufzeichnung und Foto ist getrennt abgesichert: Scheitert das Foto nach erfolgreichem Speichern der Arbeit, wird die Arbeit nicht erneut angelegt. Das Foto kann gezielt erneut versucht oder bewusst später im Kundenkonto hinzugefügt werden.
- Vor dem ersten Foto-Speicherversuch legt `PendingWorkPhotoStore` den vollständigen Wiederholauftrag mit einer stabilen Foto-ID atomar in der privaten Datei `pending-work-photo.json` ab. Die Bridge-Funktionen `stagePendingWorkPhoto`, `getPendingWorkPhoto` und `clearPendingWorkPhoto` verbinden diesen Speicher mit der bestehenden WebView-Oberfläche.
- Der Wiederholauftrag wird beim WebView-/App-Start wiederhergestellt. Die UI öffnet dann gezielt die Arbeitsmaske und bietet nur „Foto erneut speichern“ oder bewusstes Verwerfen an; die bereits gespeicherte Arbeit wird nicht nochmals geschrieben.
- Vor dem Staging werden Kunden- und Arbeits-ID, Foto-ID, maximale Größe, Base64-Struktur, JPEG-Signatur und tatsächlicher JPEG-Inhalt geprüft. Das Löschen der privaten Wiederholungsdatei erfolgt erst nach erfolgreichem Foto-Speichern oder ausdrücklicher Verwerfbestätigung.
- Bleibt nach einem bereits erfolgreichen Foto-Speichern wegen eines Bereinigungsfehlers eine alte Wiederholungsdatei zurück, erkennt die Wiederherstellung das Foto anhand derselben stabilen ID sowie Arbeits- und Kundenzuordnung als bereits vorhanden. Sie legt keine Dublette an und versucht nur noch die sichere Bereinigung.

### Kundenfotoverwaltung

- Mehrere Fotos können innerhalb des geöffneten Kundenkontos ausgewählt und demselben Kunden zugeordnet werden.
- Es gibt getrennte Aktionen für Galerie und Kamera sowie eine optionale Bildbeschreibung.
- Das erste ausgewählte Bild kann als Profilbild markiert werden; weitere Bilder erhalten die Rolle `documentation`.
- Vorschaubilder werden im Kundenkonto angezeigt. Ein fehlendes Original wird erst nach dem sichtbaren Öffnen des Vollbilddialogs gezielt über `AndroidData.getPhotoData(photoId)` geladen; dadurch löst das bloße Rendern der Kundenübersicht keinen Drive-Zugriff aus.
- Vollbildansicht, vorheriges/nächstes Bild, Doppelklick/-tippen, Mausrad, Tastatur, Verschieben und Pinch-to-Zoom bis 5× sind implementiert.
- Einzelne, nicht mit Auftrag oder Arbeitsaufzeichnung verknüpfte Kundenfotos können nach Rückfrage zum Löschen vorgemerkt und innerhalb des bestehenden Rückgängig-Fensters wiederhergestellt werden.
- Belegfotos mit `worklog_id` oder `order_id` sind in dieser Kundenansicht gegen Löschen geschützt.
- Ein fehlgeschlagenes natives Rückgängigmachen wird als Fehler angezeigt und nicht fälschlich als Wiederherstellung bestätigt.
- Bilder werden vor Speicherung verkleinert; die native und die Laptop-Seite validieren zusätzlich Dateityp, Abmessungen und Maximalgröße von 20 MB.

### Fotoablage und Synchronisierung

- Die gemeinsame Foto-Validierung unterstützt JPEG, PNG, WebP, GIF und HEIC und begrenzt Kantenlänge, Pixelanzahl und Dateigröße.
- Originale werden atomar und inhaltsadressiert abgelegt; doppelte gleiche Inhalte teilen denselben physischen Inhalt, behalten aber getrennte Datensatz-IDs und Zuordnungen.
- Mobile Snapshots und MCP-Leseantworten enthalten keine großen Inline-Binärfelder mehr.
- Die Android-Bridge liefert ein Original nur gezielt für die Bildansicht und kann ein fehlendes lokales Original aus dem verbundenen Drive-Fotoordner nachladen und verifizieren.
- Android schreibt ein neues Foto-Original vor seinem Metadatenjournal in `HausGarten-Fotos`, liest es zurück und prüft Hash, Größe und MIME-Typ.
- Der Laptop-Importer akzeptiert eine Metadatenaktion erst, wenn das passende verifizierte Original vorliegt.
- Bestehende Base64-Journale bleiben als rückwärtskompatibler Migrationsweg lesbar.
- Die Integritätsprüfung erkennt unsichere Dateinamen, fehlende Dateien, Hash-/Größen-/Inhaltsabweichungen und nicht referenzierte Dateien.

### Pflanzenlexikon und Neophyten

- Der vorhandene Katalog wurde von 160 auf 181 eindeutige Pflanzenprofile erweitert.
- 21 in Österreich nachgewiesene Pflanzenarten der EU-Unionsliste wurden mit fachlichen, rechtlichen und praktischen Angaben neu aufgenommen.
- Ein vorhandener Eintrag, Sommerflieder (`Buddleja davidii`), wurde fachlich als etablierter invasiver Neophyt außerhalb der EU-Unionsliste nachklassifiziert.
- Der vorhandene Katalog besitzt nun einen eigenen Bereich **Neophyten** mit getrennten Filtern für alle erfassten Neophyten, etabliert/lokal etabliert, invasiv und EU-rechtlich reguliert.
- Rechtlicher Status und allgemeine fachliche Empfehlung werden in den Datensätzen getrennt.
- 42 lizenzierte, lokal optimierte Bilder wurden ergänzt, jeweils zwei pro neuer Art.
- Pflanzenbilder öffnen innerhalb der bestehenden Gartenpirat-Komponente in einer Vollbildansicht mit Vor/Zurück, Zoom, Pinch, Verschieben, Doppeltippen, Tastatur und Rückkehr zum gleichen Pflanzenprofil.
- Katalog, Bilder und Viewer bleiben offline verfügbar; der Service Worker listet alle 362 Katalogbilder.
- Die Build-Pipeline prüft eindeutige IDs/wissenschaftliche Namen, Bildanzahl, erlaubte Lizenzen, vorhandene Offline-Dateien und Spiegelgleichheit.

### Design und Release-Metadaten

- Neue Einstellungen, Fotoansichten und Lexikonfunktionen verwenden die vorhandenen Farben, Typografie, Karten, Dialoge, Schaltflächen und Navigationslogik.
- Kleine Displaybreiten und fehlender horizontaler Überlauf sind in den Browser-Zieltests berücksichtigt.
- Android wurde auf `versionName 1.46.0` und `versionCode 65` angehoben; Paketname `at.hausgarten.service` bleibt unverändert.
- Der bestehende Datenschutz-Prüfschritt bleibt als Abhängigkeit von `preBuild` erhalten.

## B. Teilweise implementierte Funktionen

1. **Kamera und Galerie:** Auswahl, Mehrfachauswahl, `capture="environment"`, native Kamera-Intent-Weiterleitung und sicherer temporärer Provider sind implementiert. Die tatsächliche Funktion mit den auf dem Ziel-Pixel installierten Kamera-/Galerie-Apps und deren URI-Verhalten ist nicht auf dem Gerät geprüft.
2. **Kundenfoto-Synchronisierung:** Lokale Ablage, Journale, Drive-Dateiablage und Fehlerpfade sind implementiert und isoliert getestet. Ein echter Ende-zu-Ende-Abgleich Pixel → Google Drive → Laptop → zweites Gerät ist noch nicht durchgeführt.
3. **Einstellungs-Neustart:** Browsertests belegen die Wiederherstellung der Wischoption nach Reload sowie den Fehlerpfad. Ein vollständiger Prozessneustart der echten Android-App wurde nicht geprüft.
4. **Pflanzenbilder:** Für jede der 21 neuen Arten stehen zwei belegte Bilder bereit. Eine vollständige Serie aus Gesamtansicht, Blatt, Blüte, Frucht, Rinde und Schadbild ist wegen sicherer Artzuordnung, Lizenzlage und Offline-Größe nicht für jede Art vorhanden.
5. **Pflanzenfachdaten:** Amtlich belegte Erkennung, Ausbreitung, Auswirkungen, Kontrolle und Rechtsangaben sind vorhanden. Nicht zuverlässig belegte gärtnerische Einzelwerte bleiben bewusst leer statt erfunden zu werden.
6. **Performance:** Vorschaubilder, verzögertes Laden und getrennte Originaldateien reduzieren Snapshot- und Speicherlast. Messungen auf dem Zielgerät, Langzeitbetrieb und große reale Fotobestände stehen noch aus.

## C. Nicht umgesetzte beziehungsweise nicht abgenommene Funktionen

- Kein Android-Build und kein Lint-Lauf mit der echten Android-Toolchain.
- Keine signierte Release-APK aus Version 1.46.0.
- Keine Fresh-Install-Prüfung.
- Kein In-place-Update 1.45.0 → 1.46.0 mit Nachweis des Daten- und Warteschlangenerhalts.
- Keine reale Pixel-Abnahme für Kamera, Galerie, Pinch-to-Zoom, Android-Zurück, Bildschirmtastatur und verschiedene Display-/Schriftgrößen.
- Kein echter Drive-Provider-/Cloud-E2E-Test und keine unabhängige Online-Sichtbarkeitsbestätigung.
- Keine Prüfung auf einem zweiten Gerät und keine reale Konflikt-/Netzunterbrechungsserie mit Benutzerkonto.
- Keine vollständige botanische Bebilderung aller 181 Profile mit allen im Auftrag genannten Motivtypen.
- Keine verbindliche Bestimmungs-, Bekämpfungs- oder Rechtsfreigabe durch eine Behörde; die App verweist dafür auf die amtlichen Quellen und den jeweiligen Prüfstand.

## D. Veränderte und neu hinzugefügte Dateien

### Android-Konfiguration und nativer Code

- `android/app/build.gradle`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/at/hausgarten/service/MainActivity.java`
- `android/app/src/main/java/at/hausgarten/service/DriveJournalRules.java`
- `android/app/src/main/java/at/hausgarten/service/DriveSyncCoordinator.java`
- `android/app/src/main/java/at/hausgarten/service/PixelMcpReadTools.java`
- `android/app/src/main/java/at/hausgarten/service/PhotoAttachmentStore.java` (neu)
- `android/app/src/main/java/at/hausgarten/service/PhotoCaptureProvider.java` (neu)
- `android/app/src/main/java/at/hausgarten/service/PendingWorkPhotoStore.java` (neu)

### Android-WebView-Oberfläche

- `android/app/src/main/assets/mobile.html`
- `android/app/src/main/assets/daily-usability.js`
- `android/app/src/main/assets/daily-usability.css`
- `android/app/src/main/assets/design-concept.js`
- `android/app/src/main/assets/design-concept.css`
- `android/app/src/main/assets/gartenpirat.js`
- `android/app/src/main/assets/gartenpirat.css`
- `android/app/src/main/assets/work-location.js`
- `android/app/src/main/assets/workday-ui.js`
- `android/app/src/main/assets/plant-catalog.json`
- `android/app/src/main/assets/plant-catalog.js`
- `android/app/src/main/assets/plant-images/*.jpg`: 42 neue Bilddateien für die 21 neuen Arten

### Laptop-/Web-App und Datenlogik

- `db.py`
- `server.py`
- `photo_storage.py` (neu)
- `static/daily-usability.js` (neu gespiegelt)
- `static/daily-usability.css` (neu gespiegelt)
- `static/gartenpirat.js`
- `static/gartenpirat.css`
- `static/plant-catalog.json`
- `static/plant-catalog.js`
- `static/sw.js`
- `static/plant-images/*.jpg`: Spiegel der 42 neuen Bilddateien

### Pflanzen-Datenpipeline und Dokumentation

- `tools/neophyte_profiles.json` (neu)
- `tools/build_neophyte_catalog.py` (neu)
- `GARTENPIRAT-PFLANZEN-v1.46.md` (neu)
- `ABSCHLUSSBERICHT-v1.46.md` (neu)

### Tests

- `tests/customer-photos.test.cjs` (neu)
- `tests/work-entry-photo-failure.test.cjs` (neu)
- `tests/photo_fixture.py` (neu)
- `tests/test_photo_attachments.py` (neu)
- `tests/work-location.test.cjs`
- `tests/daily-usability.test.cjs`
- `tests/gartenpirat-plants.test.cjs`
- `tests/gartenpirat-web.test.cjs`
- `tests/test_app.py`
- `tests/test_daily_scenarios.py`
- `tests/test_release_quality.py`
- `tests/java/at/hausgarten/service/PixelMcpDailyTests.java`
- `android/app/src/androidTest/java/at/hausgarten/service/DriveProviderAcceptance.java`
- `android/app/src/androidTest/java/at/hausgarten/service/PixelMcpInstrumentation.java`

## E. Datenbankänderungen und Migrationen

### Android SQLite: Schema 2 → 3

Die Datenbank `haus-garten-mobile.db` wird additiv auf Version 3 angehoben. Neu ist:

```text
photo_attachments(
  photo_id PRIMARY KEY,
  content_sha256,
  mime_type,
  byte_size,
  storage_name,
  role,
  transfer_state,
  created_at
)
```

Zusätzlich wird ein Index auf `content_sha256` angelegt. Die bestehende `pending`-Tabelle und die Arbeitssitzungen bleiben unverändert erhalten.

Beim Upgrade werden vorhandene offene Fotoaktionen gelesen. Enthält eine alte Aktion noch ein Inline-Original, wird eine geprüfte private Kopie angelegt und nur die neue Metadatenzeile ergänzt. Die alte Journalaktion wird nicht umgeschrieben oder gelöscht. Schlägt die Übernahme eines einzelnen Altbestands fehl, bleibt die unveränderte Aktion als rückwärtskompatibler Wiederherstellungsweg bestehen.

Auch bereits bestätigte Snapshots mit Inline-Fotos werden nur dann auf Metadaten umgestellt, wenn das Original sicher aufgezeichnet werden konnte. Bei Fehlern bleibt der ursprüngliche Snapshot unangetastet; die öffentliche Zustandsantwort hält Binärfelder dennoch aus MCP/WebView-Ausgaben zurück.

### Laptop-SQLite: additive Foto-Metadaten

Die vorhandene Tabelle `photos` erhält – nur falls noch nicht vorhanden – folgende Spalten:

- `content_sha256`
- `byte_size`
- `mime_type`
- `storage_name`
- `role` mit Standard `documentation`

Ein Index auf `content_sha256` wird ergänzt. Vor der ersten Schemaerweiterung erstellt die Anwendung eine SQLite-Sicherung `before-photo-attachments-*.db`. Ist das Schema schon vorhanden, referenziert aber noch alte Dateinamen, wird vor der kanonischen Referenzumstellung einmalig `before-photo-canonical-*.db` angelegt.

Bestehende gültige Originaldateien werden nicht umbenannt oder gelöscht. Stattdessen wird eine verifizierte, inhaltsadressierte Kopie atomar angelegt und `storage_name` zeigt auf diese Kopie. Die historische Datei bleibt als wiederherstellbare Kopie erhalten. Ungültige, fehlende oder widersprüchliche Dateien werden nicht stillschweigend als gültig markiert; die Integritätsprüfung weist sie aus.

### Pflanzenkatalog

`plant-catalog.json` wurde additiv von Schema 1 auf Schema 2 erweitert. Das ist keine Migration der produktiven Kunden-/Arbeitsdatenbank. Bestehende Pflanzen-IDs und Felder bleiben erhalten; das optionale Objekt `neophyte` ergänzt Status, Herkunft, Erkennung, Ausbreitung, Auswirkungen, Kontrolle, Entsorgung, Rechtshinweise, Prüfstand und Quellen.

### Noch ausstehender Migrationsnachweis

Die Migrationslogik ist durch isolierte synthetische Tests abgedeckt. Eine Migration einer kontrollierten, verschlüsselten Kopie des tatsächlichen 1.45.0-Pixelbestands sowie das anschließende In-place-Update wurden nicht ausgeführt. Deshalb gibt es noch keine produktive Migrationsfreigabe.

## F. Auswirkungen auf die Synchronisierung

### Erhaltene Eigenschaften

- Bestehende Kunden-, Auftrag-, Arbeits-, Rechnungs- und Foto-IDs bleiben erhalten.
- Offene Journalaktionen werden nicht pauschal ersetzt oder geleert.
- Bestehende Bestätigungs-, Konflikt- und Prüfkennungen bleiben Teil des Abgleichs.
- Offline-Eingaben werden weiterhin zuerst lokal gespeichert.
- Alte Inline-Fotoaktionen bleiben importierbar.
- Die vorhandene Drive-Ordnerfreigabe wird weiterverwendet; es entsteht keine zweite Cloud-Anbindung.

### Erweiterung für Foto-Originale

1. Das Gerät validiert und speichert das Original privat.
2. Das Änderungsjournal enthält Foto-ID, Zuordnung, Rolle, SHA-256, Größe, MIME-Typ und kanonischen Dateinamen, aber im normalen neuen Pfad kein wiederholt transportiertes Base64-Original.
3. Vor Journalübergabe schreibt Android die Binärdatei unter einem temporären Namen in `HausGarten-Fotos`, liest sie zurück, prüft sie und benennt sie erst dann auf den endgültigen Hashnamen um.
4. Bereits vorhandene gleichnamige Dateien werden nicht überschrieben, sondern vollständig verifiziert.
5. Der Laptop übernimmt die Fotoaktion nur zusammen mit dem passenden geprüften Original.
6. Scheitert Originalübertragung oder Validierung, bleibt die Aktion offen und der lokale Inhalt erhalten.
7. Das physische Löschen erfolgt erst nach bestätigter Löschung und Referenzprüfung.

Der Laptop meldet die Fähigkeit `photo_attachments_v1`. Snapshots enthalten kleine Vorschaubilder, nicht das vollständige Original. MCP-Leseantworten entfernen Inline-Binärfelder ebenfalls, damit große Fotos nicht versehentlich in Chat-/Werkzeugantworten gelangen.

### Nicht nachgewiesen

Die Logik ist in isolierten Tests geprüft. Nicht nachgewiesen sind das reale Verhalten der Google-Drive-App, lange Offlinephasen, Provider-Neustarts während eines Uploads, Quoten-/Kontofehler und unabhängige Online-Sichtbarkeit. `provider_readback` ist ausdrücklich keine Cloud-Bestätigung.

## G. Testergebnisse

### Erfolgreiche automatisierte Zieltests

| Suite | Ergebnis | Abgedeckter Bereich |
| --- | ---: | --- |
| `py -3.11 tests/run_quality_suite.py` | 180/180 bestanden · 465,623 s | vollständiger isolierter Python-Qualitätslauf einschließlich Geschäftslogik, Datenintegrität, Migration, Sync-Schutz und Release-Prüfungen |
| `tests/daily-usability.test.cjs` | 21/21 bestanden | Einstellungen, Persistenz/Fehlerpfad, Navigation, Entwurfschutz, Sync-Status, kleine Breiten |
| `tests/work-location.test.cjs` | 11/11 bestanden | Standortauswahl, Kundenzuordnung, Timer, Entwurfs-/Löschschutz und kein initialer Fokus auf editierbare Felder |
| `tests/gartenpirat-plants.test.cjs` | 14/14 bestanden | 181 Profile, 21 Unionslistenarten, Quellen/Lizenzen, Neophytenfilter, Launcher-Sichtbarkeit, Pflanzenviewer und Zoom |
| `tests/gartenpirat-web.test.cjs` | 6/6 bestanden | Web-Dashboard-Sichtbarkeit, identischer Katalog, Offline-Service-Worker, Pflanzen- und privater Bildviewer |
| `tests/customer-photos.test.cjs` | 9/9 bestanden | Mehrere Kundenfotos, Rollen, Reload, Vollbild/Zoom, verzögertes Drive-Nachladen, gültiges Providerbild ohne MIME-Deklaration, Löschschutz und Rückgängig-Fehler |
| `tests/work-entry-photo-failure.test.cjs` | 3/3 bestanden | Crash-sicherer Retry über einen tatsächlichen Browser-Seiten-Reload mit simuliertem Native-Store, kein doppelter Arbeitseintrag bei Foto-Fehler, stabile Foto-/Arbeitsverknüpfung und keine Laufzeitfehler |
| `tests/test_photo_attachments.py` | 8/8 bestanden | Inhaltsprüfung, atomare Ablage, Migration, Beziehungen und Foto-Sync-Schutz mit temporären Daten |
| `tests/test_app.py` | 13/13 bestanden | zentrale HTTP-/Anwendungsabläufe im isolierten Teststand |
| `tests/test_daily_scenarios.py` | 12/12 bestanden | tägliche Kernabläufe und Datenbeziehungen mit synthetischen Daten |

Die erzeugten Browser-Nachweise liegen unter anderem in `outputs/Customer-Photos-v1.46-Tests.json`, `outputs/Work-entry-photo-failure-tests.json`, `outputs/Gartenpirat-tests.json`, `outputs/Gartenpirat-web-tests.json`, `outputs/Daily-Usability-v1.41-Tests.json` und `outputs/Timer-Standort-v1.33-Tests.json`.

### Fehlgeschlagene automatisierte Tests

Im finalen vollständigen Python-Qualitätslauf gab es keine fehlgeschlagenen Tests: 180 von 180 Tests bestanden in 465,623 Sekunden, einschließlich der HTTP-/PDF-Prüfungen. Auch die oben aufgeführten abschließenden Browser-Zielsuiten waren vollständig grün.

Im final zusammengeführten und automatisiert geprüften Quellstand wurden keine Integrationsfehler mehr festgestellt. Diese Aussage gilt für die ausgeführten isolierten Python- und Browserprüfungen; sie erweitert die unten genannten, noch offenen Android- und Drive-Abnahmen nicht.

### Vor einer Android-Freigabe noch auszuführende Prüfungen

- Java-/JVM-Tests und Android-Instrumentierung
- `git diff --check`, Spiegel-/Hashprüfung sowie JavaScript-Syntaxprüfung
- APK-Datenschutzprüfung am tatsächlich gebauten Artefakt
- Android-Build und Lint

In `PixelMcpInstrumentation.java` sind zwei neue native Instrumentierungsprobes für `PendingWorkPhotoStore` vorbereitet:

1. Staging, erneutes Öffnen des Stores, identisches Lesen der stabilen Foto-ID und ausdrückliches Leeren.
2. Ablehnung eines ungültigen neuen Staging-Versuchs, ohne den zuvor gültig gespeicherten Wiederholauftrag zu ersetzen.

Diese beiden Probes wurden mangels Android-Toolchain und Gerät nicht ausgeführt. Sie sind daher Testcode, aber noch kein realer Nachweis des Android-`AtomicFile`-Verhaltens.

### Nicht ausführbare beziehungsweise nicht ausgeführte Tests

- Android-Build/Lint: Toolchain in dieser Umgebung nicht vollständig verfügbar
- Emulator: nicht ausgeführt
- reales Pixel: nicht ausgeführt
- die zwei vorbereiteten Android-Instrumentierungsprobes für `PendingWorkPhotoStore` sowie die weitergehende reale `AtomicFile`-/Prozessabbruch-Abnahme während `startWrite`/`finishWrite`: nicht ausgeführt
- reale Kamera und Galerie: nicht ausgeführt
- echter Google-Drive-Provider und Online-Cloud-Bestätigung: nicht ausgeführt
- Fresh-Install: nicht ausgeführt
- In-place-Update und Datenerhalt: nicht ausgeführt
- reale Synchronisationsunterbrechung/Weiteraufnahme zwischen mehreren Geräten: nicht ausgeführt
- finale Signatur- und Installationsprüfung: nicht ausgeführt

Eine erfolgreiche Kompilierung oder ein Browserlauf darf diese ausstehenden Geräteprüfungen nicht ersetzen.

## H. Pflanzenlexikon

### Zählung

| Kennzahl | Anzahl |
| --- | ---: |
| Ursprüngliche Pflanzenarten/-profile | 160 |
| Neu ergänzte Arten/-profile | 21 |
| Gesamtbestand | 181 |
| Fachlich nachklassifizierte bestehende Einträge | 1 |
| Ursprüngliche lokale Bilder | 320 |
| Neu ergänzte Bilder | 42 |
| Gesamtzahl lokaler Katalogbilder | 362 |
| Doppelte IDs im Endbestand | 0 |
| Doppelte wissenschaftliche Namen im Endbestand | 0 |

Die 21 neuen Profile sind Götterbaum, Gewöhnliche Seidenpflanze, Papiermaulbeerbaum, Rundblättriger Baumwürger, Nadelkraut, Kap-Efeu, Schmalblättrige Wasserpest, Riesen-Bärenklau, Japanischer Hopfen, Drüsiges Springkraut, Himalaya-Knöterich, Wechselblatt-Wasserpest, Großblütiges Heusenkraut, Amerikanischer Stinktierkohl, Brasilianisches Tausendblatt, Verschiedenblättriges Tausendblatt, Wassersalat, Japanischer Staudenknöterich, Böhmischer Staudenknöterich, Sachalin-Staudenknöterich und Riesen-Schwimmfarn.

### Kategorien und Neophytenbereich

Es wurde keine parallele Kategorie-Datenbank angelegt. Der bestehende Katalog erhielt einen Neophyten-Einstieg und folgende Statusfilter:

- alle im Katalog erfassten Neophyten: 22
- etabliert oder lokal etabliert: 13
- invasiv eingestuft: 22
- EU-Unionsliste/rechtlich reguliert: 21

Die Einträge unterscheiden Neophyt, etablierter Neophyt, invasive Einstufung und EU-rechtliche Regulierung. Der Katalog ist eine kuratierte Praxisauswahl und kein vollständiges österreichisches Neophytenregister.

### Fachquellen

- Umweltbundesamt Österreich: [Neophyten der EU-Unionsliste](https://www.neobiota-austria.at/ias-unionsliste/neophyten) und die 21 verlinkten Artenprofile
- Umweltbundesamt Österreich: [Maßnahmen gegen invasive gebietsfremde Arten](https://www.neobiota-austria.at/biologische-invasionen/ias-massnahmen)
- Umweltbundesamt Österreich: [Sommerflieder](https://www.neobiota-austria.at/neobiota-national/neobiota-auswahl/sommerflieder)
- Europäische Kommission: [Invasive alien species](https://environment.ec.europa.eu/topics/nature-and-biodiversity/invasive-alien-species_en)
- Europäische Union: [Durchführungsverordnung (EU) 2025/1422](https://eur-lex.europa.eu/eli/reg_impl/2025/1422/oj)
- Glaser et al. (2025), „Checklist of Austrian neophytes (2nd edition)“, *Preslia* 97: 413–539

Jeder neue Datensatz speichert seine konkreten Quellen und den Prüfstand. Nicht zuverlässig belegte Standardfelder bleiben leer und werden in der Oberfläche entsprechend gekennzeichnet.

### Bildquellen und Lizenzen

Alle 42 neuen Bilder stammen von Wikimedia Commons. Im Katalog stehen je Bild Autor, Commons-Dateiseite, Lizenz und Lizenzlink. Die Pipeline akzeptiert nur CC0, Public Domain, CC BY oder CC BY-SA; NC- und ND-Lizenzen werden abgewiesen.

| Lizenz | Bilder |
| --- | ---: |
| CC BY 2.0 | 4 |
| CC BY 3.0 | 5 |
| CC BY 3.0 US | 6 |
| CC BY 4.0 | 2 |
| CC BY-SA 2.0 FR | 1 |
| CC BY-SA 2.5 | 1 |
| CC BY-SA 3.0 | 5 |
| CC BY-SA 4.0 | 12 |
| CC0 | 1 |
| Public Domain | 5 |

Die neuen Bilder wurden auf höchstens 800 × 650 Pixel reduziert, als progressive JPEG-Dateien gespeichert und ohne EXIF-/GPS-Metadaten ausgegeben. Ihr gemeinsamer Umfang beträgt 4.234.523 Byte.

## I. Offene Probleme und Risiken

Nach Priorität:

1. **Release-Sperre:** Ohne Android-Build, Signaturprüfung und In-place-Update auf einer Kopie des tatsächlichen Bestands ist Version 1.46.0 nicht freigegeben.
2. **Daten-/Sync-Abnahme:** Die neue Fotoübertragung muss mit dem echten Drive-Provider, Netzwerkunterbrechung, Neustart und zweitem Gerät geprüft werden. Offene Journale müssen vor und nach dem Test exakt gezählt und verglichen werden.
3. **Gerätebedienung:** Kamera, Galerie-Mehrfachauswahl, Tastaturverhalten, Pinch-to-Zoom, Zurück und kleine/große Schrift müssen auf dem Ziel-Pixel geprüft werden.
4. **Performance:** Speicherbedarf und Ladezeit mit einem großen realistischen Fotobestand sind noch nicht gemessen.
5. **Rechtsstand:** EU- und österreichische Neophytenangaben besitzen den dokumentierten Prüfstand 16. Oktober 2026 und müssen bei künftigen Releases erneut geprüft werden.
6. **Dokumentationsstand:** Die allgemeine `README.md` enthält noch ältere Versions- und Bereitstellungshinweise. Sie muss vor einer Veröffentlichung auf den tatsächlich abgenommenen Release-Stand gebracht werden.
7. **Repository-Historie:** GitHub-`main` war älter als die lokale Ausgangsversion. Die Zusammenführung darf keine Zwischenstände oder nicht veröffentlichte Daten-/Sync-Schutzänderungen verlieren.

## J. Weitere Empfehlungen

1. Eine reproduzierbare Android-Buildumgebung mit festgelegter JDK-, SDK- und Gradle-Version einrichten und den Datenschutz-Prüfschritt im CI ausführen.
2. Vor produktiver Installation eine verschlüsselte Testkopie des 1.45.0-Appbestands verwenden: Anzahl und IDs von Kunden, Arbeiten, offenen Journaleinträgen und Fotos vor/nach Migration automatisiert vergleichen.
3. Den In-place-Update-Test mit demselben Signierschlüssel durchführen; niemals die bestehende App deinstallieren, weil dadurch lokale Offline-Daten verloren gehen könnten.
4. Einen kontrollierten Drive-E2E-Test durchführen: Foto offline anlegen, Übertragung unterbrechen, fortsetzen, Laptop übernehmen lassen, auf zweitem Gerät laden und Hash/Größe auf allen Stufen vergleichen.
5. Für große Fotobestände Speicher-, Startzeit- und Scrollmessungen auf dem Pixel ergänzen; gegebenenfalls Thumbnail-Cache und begrenztes Decoding messen, bevor weiter optimiert wird.
6. Pflanzen- und Rechtsquellen regelmäßig mit gespeichertem Prüfdatum aktualisieren; neue Bilder nur über die bestehende Lizenz- und Metadatenpipeline aufnehmen.
7. Erst nach vollständiger Geräteabnahme die `README.md`, Release-Notizen und APK-Ablage aktualisieren und den Feature-Branch kontrolliert zusammenführen.

## Freigabeentscheidung

**Quellcode-Status:** implementierter, automatisiert weitgehend geprüfter Entwicklungsstand.  
**Datenstatus:** keine produktiven Daten für Tests verändert; additive Schutzmigration vorgesehen.  
**Android-Release:** **nicht freigegeben**, bis Build, Signatur, Privacy-Check, reale Gerätefunktionen, Migration, In-place-Update und echter Drive-E2E-Test erfolgreich nachgewiesen sind.
