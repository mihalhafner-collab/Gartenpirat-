# Gartenpirat: Geschwindigkeit und schlanker Umbau

Verbindliche Regeln: [AGENTS.md](AGENTS.md). Einziger aktueller Arbeitsstand:
[struktur-neu/AKTUELLER-STAND.json](struktur-neu/AKTUELLER-STAND.json).
Die Fachregeln bleiben in [APP.md](struktur-neu/docs/fachregeln/APP.md).

## Aktueller direkter Folgeauftrag vom 28. Oktober 2026

Michael: „Soweit ich informiert bin sind Etappe 1. Und 2. Bereits erledigt
überprüfen. Danach Etappe drei bis fünf ausführen“. Anschließende Präzisierung:
„Aktuell build 113“.

Arbeitsbasis ist deshalb ausschließlich der aktuelle Bestand unter `struktur-neu/`,
Version 1.55.8 / Build 113. Der unten genannte Build 108 ist die historische
Prüfgrundlage des Nutzertexts, keine Anweisung zur Rückkehr auf alte Appquellen.
Die Nutzerpräzisierung wird nicht als neue unabhängige Pixel-Installationsmessung
oder als Bestätigung von Backup/Restore ausgelegt.

Dieser Folgeauftrag beauftragt die Fortsetzung der Quellarbeit an Etappen 3–5.
Die innerhalb der Vorgabe ausdrücklich verlangten Voraussetzungen bleiben gültig.
Historische Freigaben sind keine zusätzliche Berechtigung; maßgeblich ist dieser
aktuelle Auftrag. Keine APK-Veröffentlichung, Installation, produktive Übernahme,
Wiederherstellung, Rechnungsfreigabe oder Abfrage echter Betriebsdaten ableiten.

Die Vorgabe wird hier als Nutzerauftrag geführt, nicht als zweite AGENTS-Regelfassung.
Nachweise zu Etappen 1/2 und die aktuelle Ausgangsmessung sind in
[struktur-neu/docs/verification/performance-build113.md](struktur-neu/docs/verification/performance-build113.md)
gebunden. Relative Fach-, Quell- und Prüfpfade im folgenden Plan beginnen in
`struktur-neu/`.

## Direkter Nutzerauftrag vom 28.10.2026: Geschwindigkeit und schlanker Umbau

Grundlage: unabhängige Quellcodeprüfung des Stands 1.55.3 / Build 108 (Commit
71c6fbf). Alle Befunde stammen aus der Quellcodeanalyse, nichts davon ist am Pixel
gemessen. Diese Prüfgrundlage ist keine neue Installations- oder Releaseprovenienz.
Alle Regeln aus `AGENTS.md` gelten unverändert (nur GitHub, keine echten Daten,
keine automatische Migration, keine Installation, keine Rechnungsfreigabe).

### Leitregeln für alle Etappen

1. Eine Oberfläche, eine Datenbank, keine zusätzlichen Skripte. Neue Funktionen sind nicht Teil dieser Vorgabe.
2. Immer nur eine Etappe, danach prüfen, sichern, zurücklesen. Nie mehrere Umbauten gleichzeitig.
3. Vor jeder Änderung messen, nach jeder Änderung dieselbe Messung wiederholen. Erst dann „besser“ behaupten.
4. Bestehende Schutzregeln (Entwürfe, Commit-IDs, Rechnungsgates, Backup/Restore, Löschschutz) bleiben unverändert.

### Etappe 1: Messen (zuerst, klein)

- In `getState`, `ownerCall` und `getWorkSession` Dauer und Datengröße erfassen. In der Oberfläche Dauer von Seitenwechsel und `render()` erfassen. Nur Zeiten, keine Geschäftsdaten.
- Messung mit synthetischem Bestand realistischer Größe (z. B. 300 Kunden, 5 000 Arbeitseinträge, 3 000 Fotoeinträge, 500 Rechnungen). Kein produktiver Bestand.
- Ergebnis als Tabelle (Mittelwert und langsamste 5 %) für: Heute → Büro, Büro → Heute, Kunden öffnen, Suche, Speichern, App-Start.
- Abnahme: Tabelle liegt unter `docs/verification/`. Pixel-Messung bleibt offen, bis der Nutzer sie ausführt.

### Etappe 2: Zwei schnelle Verbesserungen

- `load()` ruft `getState()` und `getSyncStatus()` nacheinander auf, und `getSyncStatus()` baut den ganzen Zustand noch einmal auf. Der Sync-Status steckt bereits im ersten Ergebnis (`sync_status`) und wird von dort übernommen.
  Abnahme: pro Laden genau ein Vollaufbau (Zähler im Test), Statusinhalt unverändert.
- `render()` löscht bei jedem Aufruf den Listen-Cache (`clearDataCache`) und rechnet alle Listen neu, auch unsichtbare. Cache nur bei geändertem Zustand verwerfen und nur die aktive Seite neu zeichnen.
  Abnahme: Heute → Büro → Heute ohne Datenänderung löst keinen Vollaufbau und keine Neuberechnung aus. Neue, geänderte und gelöschte Einträge erscheinen weiterhin sofort.

### Etappe 3: Datenbank in Tabellen (nur nach Freigabe durch den Nutzer)

Ist-Zustand laut Quellcodeprüfung: Kunden, Aufträge, Arbeiten, Rechnungen und Fotos
liegen als ein großer JSON-Text (`owner_state`, Schlüssel `snapshot`), der bei fast
jeder Aktion komplett gelesen und geparst wird.
Soll-Zustand: eine Tabelle je Art, einzelne Einträge werden gezielt gelesen und geschrieben.

- Zuerst ein Plan im Auftrag (Tabellen, Übernahme, Rückweg). Keine Umsetzung ohne ausdrückliche Freigabe des Nutzers, weil `AGENTS.md` automatische Migration verbietet.
- Übernahme nur bewusst, mit vorherigem verschlüsseltem Backup und Rücklesen. Alte Daten bleiben bis zur bestätigten Prüfung unverändert erhalten.
- Gleiche Fachregeln und Prüfsummen müssen vor und nach der Übernahme dieselben Zahlen und Hashes ergeben.
- Abnahme: Tests mit synthetischen Daten für Übernahme, Abbruch mittendrin, Neustart, Restore und Rückweg. Geräteabnahme bleibt Sache des Nutzers.

### Etappe 4: Oberfläche schrittweise erneuern

- Jeweils ein Bildschirm, beginnend mit Heute, dann Kunden, dann Büro. Alte und neue Version laufen nie gleichzeitig auf demselben Bildschirm.
- Ziel: eine gemeinsame Darstellung, keine Skripte, die fremde Seiten umschreiben. Vorhandene Funktionen, Zurück/Speichern/Verwerfen und Entwurfsschutz bleiben erhalten.
- Vor dem Neuaufbau eines Bildschirms: Liste seiner Funktionen und Tests. Nach dem Umbau muss jede Funktion der Liste belegt sein.
- Abnahme je Bildschirm: bestehende Tests grün, Bedienung auf kleiner Anzeige und großer Schrift geprüft, Wechselzeiten aus Etappe 1 nicht schlechter.

### Etappe 5: Altlasten entfernen

- Reste der alten Drive-Synchronisierung und alte Warteschlangen erst entfernen, wenn Sicherung und Wiederherstellung am Pixel bestätigt sind.
- Historische Belege und Schutzprüfungen werden nicht nach Alter gelöscht. Löschen nur mit Nachweis, dass nichts darauf zugreift.

### Abschlussbericht je Etappe

- Was geändert wurde (Dateien und Funktionen), welche Tests liefen, Ergebnis der Vorher-Nachher-Messung.
- Getrennt ausweisen: selbst ausgeführter Test, Emulator- oder Cloudmessung, nicht geprüft (insbesondere Pixel).
- Offene Punkte klar nennen, keine Freigabeflags setzen.

## Konkretisierter Plan für Etappe 3 auf Build 113

Die vorhandene Fünf-Tabellen-Planung aus dem
[Importstand](https://github.com/mihalhafner-collab/Gartenpirat-/blob/012b0433c5ffcf912a85ea95f898473bce10ae3a/CODEX-AUFTRAG-GARTENPIRAT.md)
wird weiterverwendet und auf die aktuellen Quellpfade bezogen. Sie ist noch keine
umgesetzte oder geprüfte Tabellenmigration.

### Einfachster Weg, Alternative und Empfehlung

Empfohlen: den bestehenden SQLite-/Repositorypfad erweitern. Pro Fachart eine
Tabelle mit einem verlustfreien JSON-Datensatz je Zeile, bestehendem kanonischem
Hash und gezielten Suchspalten. Das erhält unbekannte optionale Felder und dieselbe
Fachlogik. Die sinnvolle Alternative wäre eine vollständige Zerlegung sämtlicher
Felder und Unterobjekte in relationale Spalten/Kindtabellen. Dafür müssten erheblich
mehr fachliche Formate und Rückwege umgestellt werden; dieser Umfang ist für die
fünf benannten Sammlungen derzeit nicht erforderlich. Keine neue Bibliothek,
zweite operative Datenbank, Laufzeitskripte oder neuen Produktfunktionen einführen.

| Tabelle | Schlüssel und gezielte Suchspalten |
| --- | --- |
| `business_customers` | `id`; Name, Aktivstatus; Index Name/Aktivstatus |
| `business_orders` | `id`; Einsatzkunde, Rechnungsempfänger, Status, Planungsdatum |
| `business_worklogs` | `id`; Einsatzkunde, Rechnungsempfänger, optionaler Auftrag, Arbeitsdatum, Billed-Status |
| `business_invoices` | `id`; Kunde/Rechnungsempfänger, Nummer, Status, Ausstellungsdatum; keine Nummernvergabe ändern |
| `business_photos` | `id`; Kunde/Auftrag/Arbeit, Original-/Thumbnailreferenzen, Inhaltshash |

Jede Zeile erhält `record_json` und `record_hash`. Suchspalten werden ausschließlich
aus dem jeweiligen Datensatz abgeleitet; Zeile und Indizes ändern sich in derselben
Transaktion. IDs, leere Werte, Dezimaldarstellungen, unbekannte Felder und fachliche
Referenzen bleiben erhalten. Keine geratenen Fremdschlüssel oder Datenreparaturen.
Bestehende Sitzungs-, Medien- und Belegtabellen bleiben in derselben Datenbank.
`owner_state` behält Einstellungen, Entwürfe, Request-/Commitbelege und historische
Nachweise. Die fünf Geschäftssammlungen werden nach der bewussten Umschaltung
nicht gleichzeitig im alten Snapshot weitergeschrieben.

### Tatsächlich betroffene Quellpfade

- `PixelLocalStore.java`: `snapshotText`, `getState`, `commitSnapshot`,
  `commitLocalSnapshot` und die fachlichen Mutationspfade. Ein Tabellenadapter,
  der hinter jedem Aufruf alle Zeilen wieder zu JSON zusammenbaut, erfüllt das
  Leistungsziel nicht. Vollmaterialisierung nur für tatsächlich vollständige
  Prüfungen/Export; ID-/Filter-/Seitenabfragen gezielt lesen.
- `LocalOwnerRepository.java`, `PixelOwnerAccess.java`, `PixelMcpReadTools.java`,
  `BusinessQueries.java`, `SnapshotReadSupport.java`, `NativeOwnerQueries.java`:
  gemeinsame UI-/MCP-Leser und gleiche Antwort-/Hashverträge erhalten.
- `LocalInvoiceService.java`, `LocalInvoiceJobs.java`,
  `InvoiceIssueReviewSnapshot.java`, `LocalAuthorityMigration.java`:
  Rechnungsnummern, Atomarität, Originale und unveränderte Autoritätsgates prüfen.
- `LocalBackupDataset.java`, `BackupRestoreService.java`,
  `RestoreStartupProtection.java`: Formaterkennung, vollständige Sicherung,
  bewusste Aktivierung und Rückweg für beide Speicherformate gemeinsam prüfen.
- `DriveJournalRules.java`, `SyncCommitRules.java`, `LocalTestCleanup.java`,
  Kalender-/Foto-/Revierpfade: bisherige Projektion und Beziehungen erhalten;
  historische Queue nicht als neuen Schreiber aktivieren oder vorzeitig löschen.

### Bewusste Übernahme und Rückweg

1. Vor jeder Quelländerung dieselbe synthetische Leistungsmessung und Fachprüfungen
   durchführen. Alle Leser/Schreiber der fünf Arten erfassen; die bestehenden
   fachlichen Prüfungen und Hashfunktionen an den Tabellenadapter binden.
2. Kein automatisches Kopieren oder Aktivieren in `onUpgrade`, Appstart oder Restore.
   Produktive Übernahme bleibt ein separater bewusster Geräteauftrag. Vorher müssen
   Sicherungsformat und vollständiger Rücklesenachweis eindeutig geklärt sein.
3. Genau den tatsächlich aktiven Snapshot samt wirksamer Pending-Projektion binden:
   Originalbytes, IDs/Anzahlen, kanonische Hashes, exakte Summen, Beziehungen und
   Medien-/Belegmanifest. Nicht mit älteren Preferences oder Drivekopien mischen.
4. Während der bewussten Übernahme Fachschreiben sperren, leere Zielgeneration in
   derselben DB füllen und versionierten Fortschritt/Prüfnachweis erhalten. Der alte
   Leser bleibt bis zum erfolgreichen Vergleich allein autoritativ. Originalbytes
   und vorhandene Belege unverändert erhalten.
5. Alle Zeilen zurücklesen und gleiche IDs/Anzahlen/Hashes, exakte Dezimalsummen,
   Beziehungen und Originalhashes prüfen. Bei Abweichung keine Umschaltung.
6. Erst bei vollständigem Nachweis und bewusster Bestätigung Format/Generation in
   einer Transaktion umschalten. Keine Dual-Writes und keine Rechnungsfreigabeflags.
7. Abbruch vor Umschaltung: alter Leser bleibt aktiv; unvollständige Zielgeneration
   erkennen, weder automatisch aktivieren noch löschen. Bewusste Fortsetzung nur
   bei identischem Ausgangshash und gültigem Sicherungsnachweis.
8. Rückweg ohne neue Fachänderungen: unveränderte alte Snapshotgeneration nach
   Hashprüfung bewusst atomar aktivieren. Nach neuen Tabellenänderungen zuerst
   einen aktuellen Snapshot aus den Tabellen und erhaltenen übrigen Sammlungen
   rekonstruieren und zurücklesen; erst dann umschalten. Niemals neue Einträge
   durch Rückgriff auf den alten Snapshot oder ein älteres Backup verlieren.
9. Vollrestore bleibt bewusst und fail-closed, prüft Format/Generation vor
   Aktivierung und mischt keine Stände. Kein Wiederbeleben verworfener Entwürfe,
   alter Drive-Schreiber oder Rechnungsautorität.

### Noch zu klärende Anforderung vor Festlegung des Migrationspfads

Die aktuelle Nutzerfassung verlangt ein **verschlüsseltes** Backup vor der
Tabellenübernahme. Build 113 und der aktuelle App-Vertrag verwenden dagegen
**unverschlüsselte lokale Pixel-ZIPs** (`PIXEL_PLAINTEXT_ZIP`).
Die jüngere Verschlüsselungsanforderung wird nicht stillschweigend durch ZIP ersetzt;
der aktuelle einfache Sicherungsablauf wird aber auch nicht ungefragt erweitert.
Die Entscheidung muss ausdrücklich klären, ob die Tabellenübernahme das bestehende
geprüfte Pixel-ZIP voraussetzen darf oder die Verschlüsselungsanforderung gilt.
Bis dahin keinen produktiven Aktivierungspfad implementieren und keine Etappe 3
als abgeschlossen melden. Der allgemeine Ausführungsauftrag wird nicht erneut
zur Freigabe vorgelegt; offen ist diese konkrete widersprüchliche Voraussetzung.

### Abnahme von Etappe 3

Synthetischer Bestand mindestens 300 Kunden/5 000 Arbeiten/3 000 Fotometadaten/
500 Rechnungen. Übernahme, Fehler nach Kopierschritten, Prozessabbruch vor/in/nach
Umschaltung, Neustart, verlorene Antwort mit gleicher Request-ID, falscher
Ausgangshash, unbekannte Felder, ungültige Beziehungen, fehlende/veränderte Originale,
Restore sowie beide Rückwege. Fachzahlen und kanonische Hashes vor/nachher exakt
identisch; keine Floattoleranz. Gezielte Leser dürfen keinen Vollsnapshot laden.
Danach dieselben Zeitmessungen wie zuvor. SQL-/Android-Laufzeit und physische
Geräteabnahme getrennt ausweisen; eine simulierte Browserbridge beweist beides nicht.

## Reihenfolge und Abnahme von Etappen 4 und 5

Etappe 4 beginnt erst nach geprüftem und gesichertem Abschluss von Etappe 3.
Die Funktionsliste jedes Bildschirms wird vor seiner Änderung aus dem dann aktuellen
Code vervollständigt, nicht aus Screenshots geraten. Ausgangspunkte:

| Bildschirm | Zu erhaltender Funktionsumfang | Bestehende Prüfungen/Quellbereiche |
| --- | --- | --- |
| Heute | Übersicht, Datum/Terminwechsel, Timerzustände, Erfassung, Navigation, Entwurfsfortsetzung, Fehlerzustände | `today-ui.js`, `revier-solo-ui.js`, `simple-forms.test.cjs`, `startup-ui.test.cjs` |
| Kunden | Suche/Sortierung, Profil, Anlegen/Bearbeiten, Gartenangaben, Aufträge, Fotos, Dokumente, Speichern/Verwerfen/Zurück | `mobile.html`, `design-concept.js`, `customer-hub.js`, historische aktuelle Kontext-/Navigationstests |
| Büro | Anfrage/Besichtigung/Angebot/Auftrag/Abrechnung, Rechnungen/Zahlungen, Material/Geräte, Suche, Mehr-Menü, offene Entwürfe | `revier-solo-ui.js`, bestehende Fachmodule, `revier-solo-ui.test.cjs` |

Pro Bildschirm eine verantwortliche Darstellung in vorhandenen Modulen herstellen;
zugehörige fremde DOM-Umschreiber im selben Schritt ablösen. Keine gleichzeitig
aktiven alten/neuen Renderer. Vorhandene Fachaufrufe und Entwurfs-/Rückwege erhalten.
Nach jedem Bildschirm separat Funktionsliste, 320/360px und 200%-Schrift, relevante
Tests und Wechselzeiten prüfen, committen und den exakten Remote-Stand zurücklesen.

Etappe 5 bleibt an die ausdrückliche Nutzerbestätigung von **Speichern und
Wiederherstellen auf dem Pixel** gebunden. Ein Hinweis auf Build 113 allein ist
keine solche Bestätigung. Vor jeder Löschung Quellreferenzen, Reflexion/Registrierung,
Build-/Test-Fixtures, Historien- und Restoreleser prüfen. Insbesondere Namen wie
`DriveJournalRules` oder `Retired...` beweisen keine Entbehrlichkeit. Nachweislich
noch referenzierte Beleg-/Queue-/Prüfpfade bleiben erhalten.

## Historischer Auftrag

Die vorherige Fassung dieser Datei ist unverändert im
[Stand bd25fa9](https://github.com/mihalhafner-collab/Gartenpirat-/blob/bd25fa9b9cc1ea183974c5d2434ef11f56b4d12a/CODEX-AUFTRAG-GARTENPIRAT.md)
erhalten. Erledigte Anweisungsbereinigung und frühere Backup-/Drive-Aufträge sind
keine parallel auszuführenden Aufträge. Die aktuellen Schutzregeln stehen in
`AGENTS.md` und dem verlinkten App-Vertrag; eine Regeländerung ist hier nicht erfolgt.
