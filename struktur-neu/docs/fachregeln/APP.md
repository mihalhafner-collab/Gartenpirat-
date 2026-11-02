# Gültiger App-Vertrag

## Betrieb und gemeinsame Fachlogik

Die App auf dem Pixel führt die Geschäftsdaten lokal. Die Android-Anwendung ist
in Kotlin auf Android/JVM mit nativer Compose-Oberfläche implementiert. Oberfläche
und MCP verwenden dieselben transaktionalen Fachpfade. Paketkennung
`at.hausgarten.service`, Originalsignatur und Updatefähigkeit bleiben erhalten.
Ein Update benötigt eine höhere Versionsnummer. Die frühere eigenständige
Weboberfläche wird nicht mehr gepflegt.

Kunden, Einsatzobjekte und Rechnungsempfänger sind unterschiedliche Beziehungen.
Auftragslose Arbeiten bleiben möglich. Persönliches
„b.b./bezahlt“ ist vom Rechnungsstatus getrennt. Fehlende Daten, Preise, Zeiten,
Beziehungen und Belegnummern nicht erfinden.

## Speichern und Löschen

Ein unberührtes Formular erzeugt keinen Entwurf. Bearbeitung ist ein Entwurf;
bestätigtes Speichern erzeugt einen Geschäftssatz. Verwerfen entfernt den Entwurf
vollständig. Nach unklarer Antwort dieselbe Vorgangskennung und Argumente erhalten
und zuerst lesen. Ein bestätigter Geschäftssatz bleibt bei einem späteren Foto-,
Oberflächen- oder Sicherungsfehler erhalten.

Löschen, Archivieren und Verwerfen haben unterschiedliche Bedeutung. Referenzen,
ausgestellte Belege und erforderliche Aufbewahrung vor dem Löschen berücksichtigen.
Neustart, verspätete Antworten und Wiederherstellung dürfen verworfene Inhalte
nicht unbeabsichtigt wieder aktivieren.

Die lokale Einzelnutzer-Oberfläche verwendet zum Archivieren oder endgültigen
Löschen einen verständlichen Bestätigungsdialog mit demselben dauerhaften
Aktionsbeleg und Vergleichsschutz wie die übrigen Schreibvorgänge. Technische
Kennungen bleiben ausgeblendet. Es gibt keine vorab einzuholenden
Vorschau- oder Fotoverknüpfungs-Hashes. Fachliche Sperren für ausgestellte
Rechnungen, abgeschlossene Angebote und laufende Zeiterfassung bleiben bestehen.
Beim Entfernen von Auftrag oder Termin werden optionale Bezüge an Arbeiten,
Fotos, Einsätzen und Anfragen gelöst; die Datensätze und ihre Kunden bleiben
vorhanden. Endgültiges Löschen entfernt den Archivdatensatz wirklich.

Die anschließende lokale Bereinigung schreibt den bereits wirksamen Datenstand
transaktional fest und entfernt nur erfolgreich übernommene Journalaktionen,
alte Snapshot-Duplikate und Löschbelege. Ungeklärte lokale Aktionen bleiben
vorhanden. Dateien werden gegen aktive Datensätze, Papierkorb, Entwürfe und
verbliebene Aktionen geprüft. Erst nach dem Datenbank-Commit werden ungenutzte
Originale, Vorschauen, Medien und Entwurfs-PDFs entfernt. Gerade veröffentlichte
Formular-Fotodateien und laufende Uploads erhalten höchstens einen Tag Schonfrist,
weil ihre Formularreferenz erst unmittelbar danach gespeichert wird.

Einstellungen → Sicherung und Diagnose zeigt die tatsächlichen Dateigrößen der
Datenbank einschließlich WAL, Fotos/Medien, PDFs, weiteren App-Dateien, lokalen
Arbeitsdaten und Cache. „Speicher bereinigen“ entfernt verwaiste Backup-Arbeitsdateien und
ungenutzte Medien/Uploads, anschließend laufen WAL-Checkpoint und VACUUM außerhalb
der Transaktion. Geschäftsdaten und ausgestellte Originalbelege werden nicht
aufgrund ihres Alters gelöscht. Die Bereinigung ist keine produktive Migration
oder Rechnungsfreigabe. Lokale Fotoentwürfe werden nach Wiederherstellung nicht
an einer früheren Geräte-/Restore-Epoche abgewiesen.

Der Abschluss einer Formularsichtung nach Wiederherstellung wird erst nach
erfolgreichem Speichern des Kernzustands bestätigt. Ein optionaler Medienfehler
darf diesen Abschluss nicht unbemerkt zurückrollen. Ausdrücklich ausgewählte
Medien und Kernzustand werden weiterhin gemeinsam übernommen; zurückgestellte
Medien bleiben getrennt prüfbar. Ungültige oder wiederholte Bestätigungen dürfen
keine zusätzliche Übernahme auslösen.

Native Entwürfe und lesbare bekannte Formularhüllen aus älteren Sicherungen werden
zur ausdrücklichen Sichtung den passenden nativen Editoren zugeordnet. Die
Quellhülle und unklare Vorgangsbelege bleiben erhalten. Unbekannte Hüllen dürfen
nicht als abgeschlossene Geschäftsaktionen oder automatisch ausführbare Entwürfe
ausgelegt werden. Archivierte rohe IndexedDB-/LevelDB-Dateien bleiben erhalten;
die native Oberfläche kann daraus keinen vollständigen Browserdatenbank-Inhalt
ableiten. Diese Grenze muss sichtbar bleiben. Medien ohne vollständigen lesbaren
Spiegel oder erforderliche Nachweise werden ausdrücklich zurückgestellt oder ihre
Aktivierung bleibt gesperrt; ihre bloße Dateierhaltung ist kein Übernahmenachweis.

## Sicherung

SQLite-Schema7 und ZIP-Manifestformat1 bleiben kompatibel. Neue Sicherungen sind
unverschlüsselte ZIP-Dateien: Speicherknopf, Android-Dateiauswahl und einen
lokalen Speicherort auf dem Pixel wählen. Die geschriebene Datei wird vollständig
zurückgelesen. Es gibt weder einen Cloudzugang noch eine automatische Übertragung.
Eine spätere Kopie zu einem anderen Speicherort erledigt der Eigentümer manuell
außerhalb der App. Die lokale Datenbank bleibt führend. Das erfolgreiche
Rücklesen beweist die Integrität der lokalen Sicherungsdatei.
Vorhandene ältere Archive und Schlüssel bleiben für eine bewusst beauftragte
Wiederherstellung erhalten. Deren Lesbarkeit rechtfertigt keine neue komplizierte
Einrichtung für aktuelle Sicherungen.

Sicherung bewahrt den vorhandenen Bestand. Ein konsistenter SQLite-Snapshot,
typisierte Einstellungen und der zuletzt lesbare Formularstand werden aufgenommen.
Originalfotos, PDFs und weitere private Dateien werden direkt in den ZIP-Stream
des gewählten Dateianbieters gelesen; keine vollständige Medienkopie und kein
Komplett-Entpacken zur Sicherungsprüfung. Dateigrößen und SHA256 werden beim
Schreiben geprüft, die geschriebene ZIP danach vollständig im Stream zurückgelesen. Die bisherige Sicherungs-Auftragsverwaltung
ist entfernt; es werden nur letzter Versuch und letzter Erfolg gespeichert.
Rechnungsnummernkollisionen, bereits fehlerhafte Datenbezüge, fehlende oder nicht
verifizierte Originale verhindern das Sichern und Wiederherstellen nicht. Konkrete
Datensatzkennungen und Gründe stehen in `data/backup-notes.json`; Oberfläche und
MCP nennen die Anzahl der Hinweise. Ist eine Bestandsübersicht nicht auswertbar,
bleiben Rohdaten erhalten und die fehlende Auswertbarkeit wird ausdrücklich genannt.
Eine Sicherung mit Hinweisen bestätigt keine vollständige Verfügbarkeit aller
referenzierten Originale. Sie repariert oder normalisiert den Ausgangsbestand nicht.

Technisch unlesbare Datenbanken, Schreibfehler und beschädigte oder veränderte
Archive bleiben echte Fehler. Ein Erfolg setzt das vollständige Zurücklesen der
geschriebenen ZIP voraus. Eine Wiederholung derselben Vorgangskennung liest das Ergebnis zurück; ein neuer
Sicherungslauf darf dieselbe ausdrücklich gewählte Zieldatei überschreiben.
Verwaiste Inhalte des ausschließlich temporären Verzeichnisses `pixel-backup`
werden beim Start sowie vor und nach jedem Sicherungslauf entfernt. Ein laufender
Sicherungsvorgang wird dabei nicht abgeräumt. Es gibt keine Verbindung zur Migrationsprüfung oder zur
Rechnungsfreigabe. Der Leser älterer verschlüsselter Archive bleibt ausschließlich
für bereits vorhandene Altsicherungen erhalten; er erzeugt keine neuen Sicherungen.

Wiederherstellung erfolgt ausdrücklich nach technischer Archivprüfung und mit
einer Rückfallkopie des aktuellen Bestands. Der bestehende atomare Dateiaustausch
mit Wiederanlauf und Rollback bleibt erhalten. Sicherungsfehler blockieren die
Geschäftserfassung nicht. Ein echter Pixel-Nachweis ist getrennt von JVM- und
synthetischen Emulatorprüfungen zu führen.

Statusanzeigen ordnen Versuche nach tatsächlichem Zeitpunkt, unabhängig von
zufälligen Kennungen und Listenseiten. Ein neuerer Fehler bleibt sichtbar, auch
wenn eine ältere Sicherung bestätigt ist. Eine gespeicherte Capture-Phase allein
beweist keinen laufenden Worker. Der Selbsttest unterscheidet fehlende Nachweise,
Fehler und protokolliertes Dateirücklesen; letzteres ist keine neue Dateiprüfung
und kein Restore-Test. Fehlercodes dürfen keine Finanzdaten ausgeben.

## Rechnungen: ausschließlich sevDesk

Rechnungen werden ausschließlich über sevDesk erstellt, nummeriert, freigegeben
und versendet; auch die Zahlungsverwaltung erfolgt dort. Gartenpirat stellt
keine neuen Rechnungen aus, erzeugt keine Rechnungsentwürfe, vergibt keine
Rechnungsnummern und bearbeitet keine Zahlungen. Altlogik und ehemalige
Freigabe-/Migrationsnachweise sind entfernt.

Arbeitsaufzeichnungen mit Abrechnungsmarkierungen, Auftraggebern und
Rechnungsempfängern bleiben lokal zugänglich. Sie dienen als fachliche
Arbeitsgrundlage für eine manuelle Rechnungserstellung in sevDesk.
Historische Belege in vorhandenen Sicherungen und Geschäftsdaten werden nicht
aufgrund dieser Codebereinigung gelöscht. Die Wiederherstellung ist eine
technische Datensicherung, keine Freigabe zur Rechnungsausstellung.

Die Eigentümerfreigabe vom 02.11.2026 akzeptiert, dass alte und nicht mehr
gebrauchte Drive-, Rechnungs- oder Migrationsverknüpfungen nach der
Verschlankung unlesbar werden können. Aus diesem Grund keine
Kompatibilitätsschicht oder Ersatzmigration für abgeschaffte Funktionen bauen.
Der aktuelle Betrieb, lokale Sicherungen und die Wiederherstellung bleiben
erhalten.

## Pflanzen und Geräte

`android/app/src/main/assets/plant-catalog.json` und die dort referenzierten Bilder
sind die einzige verteilte Pflanzenquelle. `tools/sync_plant_assets.py`
prüft nur diese Android-Quellen und deren Bildnachweise. Fachquellen, Bildrechte und die vom vorhandenen
Importwerkzeug benötigten redaktionellen Eingaben bleiben in `libraries/plants/`.

Gerätefotos gehören zum Gerät. Abgebrochene Auswahl und verworfene Bearbeitung
dürfen keine Geschäftsdaten erzeugen. Bestehende Wartungsintervalle und ihr Verlauf
bleiben erhalten. Der Wortlautkatalog wird über das vorhandene Werkzeug erzeugt.

## Bedienung und Listenfilter

Der Nutzer hat kleine Hände: häufige Aktionen am unteren Bildschirmrand gut
erreichbar anbieten, mit kompakten, gut treffbaren Schaltflächen statt übergroßer
Buttons. Aktionsleisten müssen Platz im Layout erhalten und dürfen weder Inhalte
noch Tastatur oder Systemnavigation verdecken; Systemabstände und verkleinerte
Bildschirmhöhe beachten. Die vorhandene Suche bleibt erhalten.
Kunden nach aktiv/archiviert und Abrechnungsweg, Geräte nach vorhandenem Zustand,
Aufträge nach vorhandenem Status und Kunde filtern. Aktive Auswahl,
Trefferzahl und einfaches Zurücksetzen sichtbar halten. Keine gespeicherten
Filterprofile oder zusätzlichen Datenmodelle.

Die vier Hauptbereiche bleiben Heute, Arbeit (vollständiger Seitentitel:
Arbeitsaufzeichnungen), Kunden und Büro. Unteransichten zeigen ihren Bereich weiter an.
Gemeinsame Übersichten führen in dieselben Fachansichten und bewahren Kunden-,
Objekt-, Auftrags- und Zeitraumbezüge. Rechnungsempfänger wird gesondert benannt.
Eine Übersicht oder Planung löst keine Buchung aus. Suchtext und Auswahl bleiben
beim Tastaturabschluss erhalten; „Keine Treffer“ und „Keine Zuordnung“ unterscheiden.
Die verbindliche Funktionszuordnung und tatsächlich erreichte Abnahme stehen in der
bestehenden Prüfnotiz `docs/verification/performance-current.md` und im aktuellen Stand.

## Datenbank, Bilder und Reaktionszeit

Die vorhandene SQLite-Datei bleibt der Geschäftsspeicher. Die Tabellen
`native_read_rows`, `native_read_records` und `native_read_meta` enthalten eine
abgeleitete, wiederaufbaubare Leseprojektion innerhalb derselben Datenbank. Sie
ersetzen weder Fachschreiber noch Originaldaten oder Sicherungsbelege. Eine
Schemaumstellung oder ein destruktiver Fallback folgt daraus nicht.

Listen laden nur benötigte Anzeigespalten und eine begrenzte Seite. Filter und
Sortierung möglichst in SQL ausführen. Historien, Notizen, Beziehungen und
Originaldateien erst beim Öffnen des jeweiligen Details laden. Ein JSON-Feld mit
dem gesamten Bestand erfüllt diese Listenprojektion nicht. Der Aufbau einer
ungültigen Leseprojektion darf den vollständigen Fachstand im Hintergrund auswerten;
ungeänderte Projektionen müssen für weitere Seiten und Suchanfragen wiederverwendet
werden. Eine Änderung invalidiert die betroffene Sicht über die Quellrevision;
Flow bedeutet keine automatische Verarbeitung nur einzelner geänderter Zeilen.

Indizes an tatsächlichen Filter-, Sortier- und Beziehungsabfragen ausrichten.
Bei einer Objekthistorie den vorhandenen Objektbezug mit dem Arbeitsdatum verwenden.
`EXPLAIN QUERY PLAN` und realistische synthetische Bestände müssen die Verwendung
belegen. Zusätzliche Indizes benötigen eine konkrete Abfrage, weil sie auch
Schreibaufwand verursachen. Keine neue Fachbeziehung nur für einen Index erfinden.

WAL ausdrücklich konfigurieren und zur Laufzeit prüfen. WAL erlaubt gleichzeitige
Leser und einen Schreiber, beseitigt aber nicht jede Sperre. SQLite-Sicherungen
müssen einen konsistenten, vollständig bestätigten Stand einschließlich der
Änderungen im WAL aufnehmen. Das isolierte Kopieren der Hauptdatei einer laufenden
WAL-Datenbank ist keine vollständige Sicherung. Sicherung, Wiederherstellung und
erneutes Öffnen auch bei gleichzeitigem Schreiben prüfen.

Fotos und neue temporäre Fotoübertragungen als Dateien speichern; in SQLite nur
Dateireferenz und benötigte Metadaten halten. Vorhandene BLOBs und ihre MCP- und
Sicherungsbezüge vollständig erhalten. Listen bevorzugen verifizierte kleine
Vorschaudateien aus der Projektion. Ein gültiges Vorschaubild darf keinen Zugriff
auf das Original erzwingen. Bei fehlender Vorschau darf der geprüfte Originalpfad
verwendet werden; Coil dekodiert passend zur Anzeigegröße. Originale bleiben für
Detailansicht, Export und Sicherung verfügbar.

Gestückelte Übertragungen dürfen keine dauerhaft wachsende Folge vollständiger
Dateizwischenstände ansammeln. Nicht mehr referenzierte Uploadstände erst nach
bestätigtem Commit und abgestimmt mit laufenden Sicherungsaufnahmen entfernen.
Rollback, andere Uploads, ursprüngliche BLOBs und bereits aufgenommene Dateien
müssen dabei erhalten bleiben. Ein Aufräumfehler hebt einen bestätigten Commit
nicht auf und darf keinen erneuten Geschäftsvorgang auslösen.

Coil verwendet einen gemeinsamen ImageLoader mit begrenztem RAM- und Disk-Cache;
keine zweite eigene Bildcacheverwaltung. Unlesbare Bilder sichtbar kennzeichnen.
Datenbankabfragen, Dateizugriffe, Integritätsprüfung und Bildverarbeitung dürfen
den UI-Thread nicht blockieren. Blockierende Fach- und Dateioperationen laufen auf
`Dispatchers.IO`. Leseprojektionen werden über Flow beobachtet und in Compose an
den Lebenszyklus gebunden. Die Startprüfung einer unterbrochenen Wiederherstellung
hält gewöhnliche Speicherzugriffe zurück, ohne den ersten Bildschirm zu blockieren.

Antwortzeit, Speicherverbrauch und flüssiges Scrollen am tatsächlichen Zielgerät
messen. Durchsatz, einzelne Millisekundenwerte oder Bildraten sind keine zugesicherten
Eigenschaften der App. Ein Quellcheck oder synthetischer Emulatorlauf ersetzt
keinen entsprechenden Nachweis auf dem Pixel.

## Vereinfachte native Bedienung und Arbeitszubehör

Heute, Arbeitsaufzeichnungen, Kunden und Büro sind die vier festen Hauptbereiche.
Heute folgt der am 02.11.2026 zusätzlich beauftragten Bildvorlage: lokale
Speicher-/Sicherungsangabe, nächster geplanter Einsatz, Tagestour und gespeicherte
Tagesstunden. Während einer Arbeit zeigt die Ansicht die persistente Nettozeit,
Pause/Fortsetzen und Beenden. Beenden öffnet die vorhandene Zeitprüfung und erzeugt
keine ungeprüfte Arbeitsaufzeichnung. Foto und Material verwenden die bestehende
Erfassung mit dem tatsächlichen Kunden-/Auftrags-/Sitzungsbezug. Das unten geöffnete
Erfassen-Menü bietet Foto, Material, manuelle Arbeit und den Arbeitseintrag mit
vorangestelltem Notizfeld. Vorhandene Entwürfe bleiben vorrangig. Es gibt keine
neue Menügeste; Android-Zurück schließt den Dialog. Hauptaktionen sind mindestens
56 dp hoch, große Schrift bleibt ungekürzt. Gerätewartungen und offene lokale
Hinweise erscheinen nur aus vorhandenen Daten. Die Ansicht behauptet keinen
externen Sicherungsnachweis. Der Kunden-Menüaufruf beginnt
am Listenanfang; Suchtext und Filter werden dadurch nicht geändert. Arbeitsnotizen
stehen ohne Aufklappen im Detail. Büro erhält die vorhandenen Funktionen einschließlich
Maschinenpark und Gerätewartung. Häufige Aktionen liegen kompakt am unteren Rand.
Bei großer Schrift dürfen Navigationsbeschriftungen den Formularbereich nicht
verdrängen. Auswahlwerte und Formularaktionen wechseln bei Platzmangel in eine
weitere Zeile; die eingestellte Schriftgröße bleibt erhalten.

Arbeit erfassen folgt Kunde, Beginn/Ende, Pause, Tätigkeit, optionale Entsorgung
und Fotos. Datum und weitere bestehende Angaben bleiben erreichbar. Pause bietet
15/30/45/60 Minuten und freie Eingabe; aus Uhrzeiten wird sie genau einmal abgezogen.
Eine ausschließlich eingegebene Dauer ist die Nettoarbeitszeit. Textbausteine ergänzen
den freien Tätigkeitstext. Vorschläge öffnen den vorhandenen Auswahldialog mit Suche.
Entsorgung ist ein optionaler Eurobetrag mit 50/100/150-Euro-
Auswahl und freier Eingabe; kein automatischer Preis und keine Zusatzpflichten.
Der vorhandene Arbeitsdatensatz speichert den Betrag als `disposal_cost`.

Verwerfen fragt „Ungespeicherte Eingaben und Foto verwerfen?“ mit OK/Abbrechen.
Nur der eigene ungespeicherte Formularstand und seine temporären Fotodaten werden
verworfen. Bestätigte Arbeit und bereits gespeicherte Fotos bleiben erhalten, auch
wenn ein nachfolgender Foto- oder Bereinigungsschritt fehlschlägt.

Gerätebezeichnungen erlauben durchsuchbare Vorschläge und freien Text. Zubehör
liegt im vorhandenen Gerätedatensatz (`accessories`) und ist im eigenen Reiter
sichtbar. `current_chain_id` ist die einzige aktuelle Kettenzuordnung und muss auf
eine Kette derselben Maschine zeigen. Bezeichnung oder Hersteller/Modell identifizieren
das Zubehör; unbekannte Spezifikationen bleiben leer. Kettenteilung, Treibgliedstärke,
Treibgliederzahl, Artikelnummer, Zahntyp, Feilengröße und weitere Angaben bleiben
optional. Zubehörfotos verwenden den vorhandenen Gerätefotopfad und behalten ihren
Maschinenbezug. Keine neue Datenbank, Lagerverwaltung oder Wartungsautomatik.

Aus der Kundenakte begrenzt die Übersicht offener Stunden die Arbeitsaufzeichnungen
auf den gewählten Einsatzkunden, auch wenn weitere Kunden denselben
Rechnungsempfänger verwenden. „Arbeitsaufzeichnungen für sevDesk prüfen“ öffnet
dieselbe gefilterte Arbeitsliste. Die Abrechnungsmarkierung bleibt unabhängig von
sevDesk-Belegen. Angebote und Kostenvoranschläge sind außerdem direkt aus Büro
zugänglich. Geplante Einsätze erzeugen keine Arbeitsaufzeichnungen.

Die lokale Aktionsprojektion hat einen einzigen Ausführungspfad. Sie verwendet
keine Drive-Verteilung, entfernten Fehlerzustände oder Cloud-Feldzusammenführung.
Die bereits im Backupformat vorhandenen Schlüssel `drive_applied_ids`,
`drive_action_hashes`, `drive_action_receipts` und `drive_journal_errors` bleiben
Speicherbezeichnungen für lokale Übernahmebelege und Fehler. Sie setzen weder
Drive noch eine Rechnungsautorität voraus. Bestätigte Aktionen werden vor der
Bereinigung transaktional in SQLite materialisiert. Veraltete Rechnungsaktionen
werden nicht ausgeführt. Alte Rechnungs-/Zahlungsformulare erhalten keinen
Editor und keinen Versandweg; ihre Rohdaten bleiben in vollständigen Sicherungen.
Die begrenzte JSON-Teilsicherung und ihr gesperrter Teilrestore sind entfernt.
Bereits vorhandene Altsicherungen als Dateien oder SQLite-BLOBs bleiben auch bei
der Speicherbereinigung bytegetreu erhalten; nur temporäre Backup-Arbeitsdateien
werden automatisch entfernt.
Für vollständige Sicherung und Wiederherstellung gilt allein der lokale ZIP-Pfad.
