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
