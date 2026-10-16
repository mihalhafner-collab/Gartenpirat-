# Gartenpirat 1.47 – integrierte Fotodokumentation (Implementierungsauftrag)

Status: geplant, nicht implementiert. Grundlage: Entwicklungsbranch 1.46.0. Keine produktiven Daten anfassen.

## Ziel
Eine Fotoaufnahme pro Kundenbesuch mit minimaler Interaktion: laufender Arbeitseinsatz -> Kamera/Galerie -> optional Vorher/Während/Nachher und Sprach-/Textnotiz -> automatisch verknüpft mit Kunde, Auftrag und Arbeitsaufzeichnung -> chronologisch im Kundenkonto. Profilbild bleibt getrennt.

## Bestehende Komponenten wiederverwenden
- Android WebView: `android/app/src/main/assets/mobile.html`
- Kundenoberfläche und Foto-Viewer: `android/app/src/main/assets/design-concept.js`
- Native Brücke: `android/app/src/main/java/at/hausgarten/service/MainActivity.java`
- Bestehende Foto-Metadaten, `customer_id`, `order_id`, `worklog_id`, lokale Ablage und Drive-Journale
- Vorhandener `PendingWorkPhotoStore` und bestehende Integritäts-/Datenschutzprüfungen.
- `gartenpirat-photo-store.js` ist ein separater Bildspeicher für den Gartenpiraten, **nicht** die betriebliche Kundenfotodokumentation. Nicht vermischen.

## UX und Workflow
1. Während eines aktiven Timers sichtbarer, aber nicht aufdringlicher Kamera-Button; Kunde/Auftrag aus aktiver Sitzung übernehmen. Kein erneutes Ausfüllen.
2. Aufnahme per Kamera oder Mehrfachauswahl aus Galerie; jede Datei erhält Vorschaubild und optional Kennzeichnung Vorher/Während/Nachher. Zeitstempel automatisch, editierbare Beschreibung optional. Bei fehlender eindeutiger Kundenzuordnung vor dem Speichern nachfragen, niemals per GPS stillschweigend zuordnen.
3. Beim Beenden: kompakte Zusammenfassung von Arbeitszeit, Tätigkeit und Fotoanzahl. Arbeit unabhängig von Foto-Upload sicher speichern. Fehler je Foto separat wiederholen.
4. Kundenkonto: Profilbild separat; Tab „Dokumentation“ zeigt zeitlich sortierte Einsätze mit Tätigkeiten, Zeiten, Notizen und Foto-Vorschau; Tab „Fotos“ bietet Galerie und Filter nach Datum/Auftrag/Phase. Klick auf Foto öffnet bestehenden Viewer; von Foto zur zugehörigen Arbeit springen.
5. Später hinzugefügte Fotos können Kunde/Auftrag/Arbeit explizit zugeordnet werden; Berechtigungen und Kundenfreigabe standardmäßig privat. Keine automatische Freigabe.
6. PDF-Bericht erst als gesonderte, nachgelagerte Ausbaustufe; keine Abhängigkeit des Kernworkflows davon.

## Daten und Konsistenz
- Bestehende Foto-IDs und Beziehungen erhalten. Metadaten additiv um Aufnahmezeit und Dokumentationsphase erweitern, soweit nicht vorhanden; keine zweite Fototabelle.
- Kein Kopieren identischer Originaldateien; bestehende SHA-256-Ablage und Integritätsprüfung nutzen.
- Mehrfachfoto-Speicherung idempotent je Foto-ID, auch bei Absturz/Neustart/Drive-Retry. Vorhandene Einzelfoto-Wiederherstellung auf Warteschlange mehrerer Fotos erweitern, ohne alte Datensätze zu verlieren.
- Bestehende Offline-First-Synchronisierung, Konfliktprüfung und Löschschutz für Belegfotos beibehalten.
- Keine personenbezogenen Testdaten, keine produktive Migration im Test, keine Signatur- oder MCP-Tunneländerung.

## Abnahmekriterien
- Mindestens 3 Fotos zu einem Einsatz, unterschiedliche Phasen, nach Neustart vorhanden.
- Chronologische Kundenhistorie zeigt die richtige Arbeitsaufzeichnung und Fotos; Profilbild nicht als Arbeitseinsatz klassifiziert.
- Zuordnung zu Kunde/Auftrag/Arbeits-ID korrekt; Wechsel des Kunden während der Aufnahme erzeugt keine falsche Zuordnung.
- Offline erfassen, danach synchronisieren; fehlgeschlagenes zweites Foto wiederholen ohne Duplikat der Arbeit oder des ersten Fotos.
- Browser-/Python-/Android-Tests und Privacy Gate bestehen; reale Pixel-QA separat dokumentiert.
- QA-APK mit separater Paketkennung via bestehendem manuellen GitHub-Actions-Workflow; keine produktive Installation.
- Versionsnummer für neue Freigabe 1.47.0, interner versionCode erst nach Prüfung des installierten Codes festlegen.

## Reihenfolge
A. Bestehende Schreib-/Lesewege und Datenmodell vollständig kartieren.
B. Dokumentations-Timeline lesend implementieren.
C. Mehrfachfoto-Erfassung und crashsichere Wiederholung ergänzen.
D. Zuordnung, Filter, Bedienbarkeit und Tests abschließen.
E. QA-Build, reale Pixel-Tests und erst danach Release-Entscheidung.

Kein Merge nach main ohne gesonderte Freigabe.

## Verbindliche Erweiterung: direkte Kamera im Arbeitsablauf
- Im laufenden Timer und in der Arbeitsmaske eine gut erreichbare Aktion „Foto aufnehmen“ anzeigen; in der Kunden-Dokumentation ebenfalls Kamera und Galerie anbieten.
- Bestehenden nativen Android-Kamera-Intent und temporären Datei-Provider wiederverwenden; keine zweite Kameraimplementierung und keine neue Laufzeitberechtigung, sofern nicht technisch erforderlich.
- Aktive Kunden-/Auftrags-/Arbeitszuordnung aus der Sitzung übernehmen. Wenn noch keine Arbeits-ID existiert, Aufnahmen unter einer stabilen Sitzungs-ID crashsicher zwischenlagern und nach erfolgreichem Speichern der Arbeit zuordnen.
- Nach jeder Aufnahme eine kompakte Vorschau mit „Weiteres Foto“, „Vorher/Während/Nachher“ und optionaler Sprachnotiz anbieten. Kein verpflichtendes Formular.
- Bei Kameraabbruch oder App-Neustart keine leeren Fotos oder doppelten Arbeitsaufzeichnungen erzeugen. Temporäre Bilder erst nach bestätigter lokaler Speicherung bereinigen.
- Für mehrere Bilder stabile Foto-IDs und eine idempotente Wiederholwarteschlange verwenden; bestehende Einzelfoto-Recovery nicht ersatzlos entfernen.
- Profilbild-Funktion strikt von der Arbeitsdokumentation trennen; standardmäßig private Sichtbarkeit.
- QA: Kamera-Intent vorhanden/abgebrochen, mehrere Aufnahmen, Timer-Wechsel, Neustart, offline, Kundenwechsel und Foto-Retry testen; echte Pixel-Geräteabnahme erforderlich.

## Zwischenprüfung 16.10.2026 – nicht freigegeben
Implementiert im Entwicklungsbranch:
- Kundenhistorie mit Arbeitsdaten und Fotovorschauen, ohne neue Datentabelle.
- Fotoaufnahme im Kundenkonto mit optionaler Zuordnung zu einer bereits vorhandenen Arbeitsaufzeichnung und deren Auftrag.
- Vorher/Während/Nachher als Beschriftungsphase; Profilfotos bleiben von Arbeitszuordnungen getrennt.
- Direkter Kameraeinstieg beim laufenden Timer, wenn die Sitzung eine gültige Kunden-ID besitzt. Ohne Kunden-ID wird bewusst zur Auswahl in die Arbeitsmaske gewechselt; die Aufnahme muss danach explizit per Kameraknopf ausgelöst werden (Android-Gestenrestriktion).
- Statische Regressionstests und JavaScript-Syntaxprüfung im QA-Workflow.

Gegenprüfung: Die geänderten JavaScript-Dateien wurden auf Syntax geprüft; 15 statische Quellcode-Prüfungen wurden erfolgreich gegen den Branch ausgeführt. Das beweist keine Android-Funktion.

Noch offen und ausdrücklich **nicht** als implementiert auszugeben:
- Mehrfachfoto-Warteschlange für die noch nicht abgeschlossene Timer-Arbeit mit crashsicherer Zuordnung zur späteren Arbeits-ID.
- Sichere Wiederholung jedes einzelnen Fotos bei Prozessabbruch, ohne Verlust der übrigen Bilder.
- Aufnahmephase als eigenes strukturiertes Metadatenfeld (derzeit nur in Bildbeschreibung kodiert).
- Sprachkommentar-Integration, Filter nach Datum/Auftrag/Phase und PDF-Export.
- Automatisierte End-to-End-/Gerätetests für Kamera, Timer, Offline-Retry und Drive; neue QA-APK nach diesen Änderungen noch nicht gebaut.
- Der Timer-Kameraeinstieg speichert vor Abschluss der Arbeitsaufzeichnung derzeit Kundenfotos; er stellt **keine** automatische Verknüpfung mit einer erst später entstehenden Arbeits-ID her.
Kein Merge oder produktives Update vor vollständiger Abnahme.

## Ergänzende Gegenprüfung – Entwicklungsstand nach Freigabe
- Dokumentationsbereich um Filter „Alle Einsätze“, „Nur mit Fotos“ und nach Auftrag erweitert; nur bestehende Datensätze lesend gefiltert.
- 18 statische Prüfungen auf dem zuletzt abgerufenen GitHub-Code erfolgreich ausgeführt; die geänderten JavaScript-Dateien ließen sich syntaktisch parsen.
- Vorhandene Kamera-/Kundenfoto-Mehrfachauswahl ist weiterhin vorhanden, jedoch **nicht** gleichbedeutend mit einer absturzsicheren Mehrfachfoto-Warteschlange beim laufenden Timer.
- **Release gesperrt:** Kein neuer GitHub-Actions-Build ausgelöst; über die verbundene GitHub-Schnittstelle ist kein Workflow-Dispatch verfügbar. Android-Geräteprüfung, Offline-/Sync-E2E und vollständige crashsichere Timer-Fotozuordnung bleiben offen.
- Das Ziel „ein finaler Build statt vieler Zwischenbuilds“ bleibt bestehen. Keine Änderung an main, Signatur oder produktiven Daten.
