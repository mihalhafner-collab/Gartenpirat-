# Gültiger App-Vertrag

## Betrieb und gemeinsame Fachlogik

Die App auf dem Pixel führt die Geschäftsdaten lokal. Oberfläche und MCP verwenden
dieselben nativen Fachpfade. Kunden, Einsatzobjekte und Rechnungsempfänger sind
unterschiedliche Beziehungen. Auftragslose Arbeiten bleiben möglich. Persönliches
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

## Sicherung

Neue Sicherungen sind unverschlüsselte ZIP-Dateien: Speicherknopf,
Android-Dateiauswahl, Drive oder lokalen Speicher als Ziel wählen und die
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

## Datenbank, Bilder und Reaktionszeit beim Kotlin-Refactoring

Indizes an den tatsächlichen Filter- und Sortierabfragen ausrichten. Für eine
Objekthistorie insbesondere den vorhandenen Objektbezug zusammen mit dem
Arbeitsdatum prüfen; keine neue Beziehung allein für einen Beispielindex erfinden.
Bei Room die passenden Indizes mit `@Index` ausdrücken. Die Verwendung mit
`EXPLAIN QUERY PLAN` und realistischen synthetischen Daten prüfen. Zusätzliche
Indizes benötigen eine konkrete Abfrage, weil sie auch Schreibaufwand verursachen.

WAL ausdrücklich konfigurieren und zur Laufzeit prüfen; bei Room
`JournalMode.WRITE_AHEAD_LOGGING` verwenden. WAL erlaubt gleichzeitige Leser und
einen Schreiber, beseitigt aber nicht jede Sperre. SQLite-Sicherungen müssen einen
konsistenten, vollständig bestätigten Stand einschließlich der Änderungen im WAL
aufnehmen. Das isolierte Kopieren der Hauptdatei einer laufenden WAL-Datenbank ist
keine vollständige Sicherung. Den bestehenden Sicherungs-/Wiederherstellungsvertrag
auch bei gleichzeitigem Schreiben und nach erneutem Öffnen prüfen.

Listen laden nur die benötigten Spalten und einen begrenzten Ergebnisumfang,
beispielsweise Kundenkennung, Name und Adresse. Filter und Sortierung möglichst
in der Datenbank ausführen. Historien, Notizen, Beziehungen und Originaldateien
erst beim Öffnen des jeweiligen Details laden. Ein ausgewähltes JSON-Feld mit
dem gesamten Bestand erfüllt diese Projektion nicht. Vollständige Bestandslesungen
bleiben den Bedienwegen vorbehalten, die sie tatsächlich benötigen, etwa Sicherungen.

Fotos und neue temporäre Fotoübertragungen als Dateien speichern; in SQLite nur
Dateireferenz und benötigte Metadaten halten. Vorhandene BLOBs und ihre
MCP-/Sicherungsbezüge bei einer Umstellung vollständig erhalten; keine bestehende
Dateiübertragung ersatzlos entfernen. Listen verwenden auf die tatsächliche
Anzeigegröße verkleinerte Vorschauen statt dekodierter Kameraoriginale. Für eine
Compose-Oberfläche Coil mit gemeinsamem ImageLoader, größenbegrenzter Dekodierung
und begrenzten RAM-/Disk-Caches berücksichtigen; keine zweite eigene Cacheverwaltung.
Originale bleiben für Detailansicht, Export und Sicherung verfügbar.

Datenbankabfragen, Dateizugriffe und Bildverarbeitung dürfen den UI-Thread nicht
blockieren. In Kotlin suspendierende Room-Zugriffe und für blockierende Datei- und
Datenbankarbeit `Dispatchers.IO` verwenden. Room-Abfragen über `Flow` beobachten und
an den Lebenszyklus der Oberfläche binden. Room führt betroffene Abfragen nach
Invalidierung erneut aus; es liefert nicht automatisch nur einzelne geänderte
Zeilen. Abfrageumfang und unnötige UI-Neuberechnungen deshalb begrenzen.
Oberfläche und MCP behalten dieselben transaktionalen Fachschreiber.

Room, Coroutines/Flow und Coil zusammen mit den relevanten Kotlin- und
Oberflächenvarianten auf Kompatibilität und konkreten Nutzen nach KISS/YAGNI
prüfen. Durchsatz, Antwortzeit, Speicherverbrauch und flüssiges Scrollen am
tatsächlichen Zielgerät messen. 50.000 Lesevorgänge pro Sekunde, 0,1 Millisekunden
oder 120 Bilder pro Sekunde sind keine zugesicherten Eigenschaften der App.
