# Gültiger App-Vertrag

## Betrieb und gemeinsame Fachlogik

Die App auf dem Pixel führt die Geschäftsdaten lokal. Die Android-Anwendung ist
in Kotlin auf Android/JVM mit nativer Compose-Oberfläche implementiert. Oberfläche
und MCP verwenden dieselben transaktionalen Fachpfade. Paketkennung
`at.hausgarten.service`, Originalsignatur und Updatefähigkeit bleiben erhalten.
Ein Update benötigt eine höhere Versionsnummer. Die Website unter `static/` bleibt
eigenständig.

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
unverschlüsselte ZIP-Dateien: Speicherknopf, Android-Dateiauswahl, Drive oder lokalen Speicher als Ziel wählen und die
gespeicherte Datei zurücklesen. Drive ist ausschließlich Speicherort des fertigen
Backup-Archivs und Quelle einer ausdrücklich gewählten Wiederherstellung.
Die installierte Drive-App übernimmt die Ablage über den Android-Dateianbieter.
Es gibt keine eigene Drive-API, Drive-Anmeldung, Drive-Berechtigungen,
Geschäftsdaten-Synchronisierung oder automatischen Uploadjob in der App.
Die lokale Datenbank bleibt führend. Eine Rücklesung durch den Dateianbieter
bestätigt die gelesenen Dateibytes; sie belegt keine unabhängige Cloud-Bestätigung.
Vorhandene ältere Archive und Schlüssel bleiben für eine bewusst beauftragte
Wiederherstellung erhalten. Deren Lesbarkeit rechtfertigt keine neue komplizierte
Einrichtung für aktuelle Sicherungen.

Sicherung kopiert den vorhandenen Bestand unverändert. Die direkte Aufnahme umfasst
alle SQLite-Tabellen, die vorhandenen privaten Dateien, typisierte Einstellungen
und den zuletzt lesbaren Formularstand. Die bisherige Sicherungs-Auftragsverwaltung
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
geschriebenen ZIP voraus. Bestätigte Ziele werden bei Wiederholung nur geprüft;
eine neue Sicherung benötigt eine neue Zieldatei. Temporäre Aufnahmen werden nach
dem Versuch entfernt. Es gibt keine Verbindung zur Migrationsprüfung oder zur
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

## Rechnungen und Datenübernahme

Die Rechnungsfunktion ist bei künftigen Rechnungserstellungen für korrekte und
eindeutige Nummern verantwortlich. Ihre Prüfungen von Integrität, Nummern und
Originalbelegen bleiben wirksam. Eine erfolgreiche Sicherung oder Wiederherstellung
bestätigt keine fachliche Gültigkeit der vorhandenen Rechnungen.
Eine allgemeine lokale Datenautorität ist keine Rechnungsfreigabe. Ausgestellte
Rechnungsangaben und Original-PDFs erhalten; wiederholtes Bestätigen darf keine
zweite Nummer vergeben. Preise und Pflichtangaben müssen vor Abschluss feststehen.
Teilen eines PDFs belegt keinen erfolgreichen Versand. Gesetzliche Aufbewahrung
beachten; bisherige Nummern oder externe Kontowerte nicht als aktuelle Vorgabe kopieren.

Produktive Migration, Rechnungsfreigabe, Installation und Wiederherstellung werden
nicht durch die Repositorybereinigung ausgelöst. Die noch fehlenden Nachweise
stehen im aktuellen Arbeitsstand.

## Pflanzen und Geräte

`android/app/src/main/assets/plant-catalog.json` und die dort referenzierten Bilder
sind die verteilte Pflanzenquelle. Die erzeugten Webspiegel über
`tools/sync_plant_assets.py` pflegen. Fachquellen, Bildrechte und die vom vorhandenen
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
Aufträge und Rechnungen nach vorhandenem Status und Kunde filtern. Aktive Auswahl,
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
Heute zeigt erfasste Arbeiten und Hinweise direkt. Der Kunden-Menüaufruf beginnt
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

Aus der Kundenakte übernimmt die Abrechnung den Arbeitskunden zusätzlich zum
Rechnungsempfänger. Die Auswahl offener Arbeiten bleibt auf diesen Kunden
begrenzt, auch wenn weitere Kunden denselben Empfänger verwenden. Dieser
Anzeigekontext bleibt im Formularentwurf erhalten und wird nicht als zusätzliche
Rechnungsfachinformation gespeichert. Die Auswahl kann ausdrücklich auf alle
offenen Arbeiten des Empfängers erweitert werden. Geplante Einsätze erzeugen
weder Arbeitsaufzeichnungen noch Rechnungspositionen.
