# Bauen und prüfen

Alle folgenden Befehle beginnen im Ordner `struktur-neu/`. Benötigt werden
Python3, JDK17, Gradle9.3.1 und Android-SDK37 mit Build-Tools37.0.0 gemäß den
Gradle-Dateien. `JAVA_HOME`, `ANDROID_HOME` und gegebenenfalls `GRADLE_USER_HOME`
auf vorhandene Werkzeuge setzen; keine Maschinenpfade oder Geheimwerte in Git
speichern. In der verwalteten Cloud die vorgegebenen Proxy- und CA-Einstellungen
auch für die Gradle-JVM verwenden. Ein vollständiger Cache erlaubt Offline-Bauten.

## Android-Architektur und Baugrundlage

Die Anwendung bleibt ein Update von `at.hausgarten.service` mit Originalsignatur
und höherer Versionsnummer. Alle Anwendungseinträge, Speicher- und Fachpfade sowie
die Android-Oberfläche liegen als Kotlin im bestehenden `:app`-Modul. Der
Quellordner heißt weiterhin `android/app/src/main/java/at/hausgarten/service`;
das ist kein Java-Anwendungspfad. Java-Dateien in den synthetischen Tests sind
Testprogramme oder Adapter, keine parallel ausgelieferte Implementierung.

AGP9.1.1 enthält Kotlin2.2.10. Kein zusätzliches `org.jetbrains.kotlin.android`-Plugin
anwenden. Der Compose-Compiler verwendet `org.jetbrains.kotlin.plugin.compose`
2.2.10. Die aufeinander abgestimmten Abhängigkeiten stehen in
`android/app/build.gradle`: Compose-BOM2025.08.01, Activity-Compose1.10.1,
Lifecycle2.9.2, Coroutines1.10.2 und Coil3.3.0. Änderungen gemeinsam auflösen,
kompilieren und gegen die betroffenen Bedienwege prüfen.

`MainActivity` und die weiteren Activities zeigen native Compose-Inhalte.
`NativeAppController` verbindet Formulare mit denselben transaktionalen
Fachschreibern wie MCP; Geschäftsregeln werden nicht in UI-Code dupliziert.
Die App benötigt weder WebView noch eine aktive JavaScript-Oberfläche.
Die eigenständige Website unter `static/` bleibt davon getrennt.

Die vorhandene SQLite-Datenbank mit Schema7 bleibt erhalten. `NativeDataRepository`
verwaltet darin die abgeleiteten Tabellen `native_read_rows`, `native_read_records`
und `native_read_meta`. Listen lesen begrenzte Anzeigespalten und Seiten, Details
und Historien werden getrennt geladen. Quellrevisionen invalidieren den Cache;
der notwendige Wiederaufbau erfolgt im Hintergrund und kann den vollständigen
Fachstand auswerten. Eine unveränderte Revision wird für weitere Abfragen
wiederverwendet. Indizes müssen zu tatsächlichen Filtern, Sortierungen und
Beziehungen passen. Room ist nicht Teil dieses Speicherwegs; seine Einführung
würde eine eigene, verlustfrei geprüfte Migration benötigen.

WAL wird am Store aktiviert und beim Öffnen geprüft. Blockierende Datenbank- und
Dateiarbeit läuft auf `Dispatchers.IO`; Flow liefert Aktualisierungen an
lebenszyklusgebundene Compose-Sichten. Die gemeinsame Startprüfung schützt den
Bestandsaustausch vor gewöhnlichen Storezugriffen, ohne Androids UI-Thread warten
zu lassen. Fotolisten bevorzugen verifizierte Vorschaudateien. Coil verwendet
einen gemeinsamen ImageLoader, begrenzte Caches und an die Anzeige angepasste
Dekodiergrößen; die Integritätsprüfung eines erforderlichen Originals läuft auf IO.

Dateiproduzenten schreiben und synchronisieren neue Inhalte zunächst im Cache
und veröffentlichen sie atomar als unveränderliche Datei. Uploadreferenz und
Veröffentlichung werden durch den SQLite-Schreiber serialisiert. Nach einem
bestätigten Upload-Commit bereinigt der gemeinsame Owner-Zugang unreferenzierte
Uploadzwischenstände unter Aufnahmesperre und anschließendem SQLite-Schreiber.
Die Sperrreihenfolge nicht umkehren; aktive Referenzen und aufgenommene Hardlinks
erhalten. Aufräumfehler separat als ausstehend melden. Auch lange Folgen kleiner
Chunks mit begrenztem Dateibestand, Wiederholung und Rollback prüfen.

Ein aktueller vorhandener Lesecache muss auch beim ersten Zugriff eines neuen
Store-Helfers ohne Schreibtransaktion lesbar sein. Logische Abfragen und
Transaktionen halten ihre SQLite-Referenz bis zum Ende, damit ein gleichzeitiges
Schließen keine offene Sitzung verwaist zurücklässt. Diese Grenzen mit echten
Android-Verbindungen, Hilfsneuerstellung und gleichzeitigen Schreibern prüfen.

### Auswahl nach KISS und YAGNI

Die Auswahl richtet sich nach dem vorhandenen Android-Update und seinem
Datensicherungsvertrag. Weitere Plattformen oder ein zweiter Speichervertrag
gehören nicht zum Auftrag.

| Entscheidung | Geprüfte Alternative und Grund |
|---|---|
| Kotlin/JVM im vorhandenen Android-Modul | Kotlin Multiplatform, Native, JS/Wasm und ein Servermodul ergänzen hier keine benötigte Zielplattform; JVM erhält die Android- und Java-Bibliotheksverträge. |
| Native Compose-Oberfläche | XML/Views wären möglich; Compose erfüllt die gewünschte Kotlin-Oberfläche und bindet Flow direkt an den Lebenszyklus. Ein zusätzlicher WebView-Laufzeitpfad entfällt. |
| Vorhandene SQLite-Datei und gezielte SQL-Projektionen | Room oder SQLDelight würden neue Mapping- und Migrationsverträge erfordern. Bestehende Tabellen, unbekannte Altwerte und SQLite6/7-Backups bleiben über den bisherigen Store lesbar. |
| Coroutines und Flow | Manuelle Threads/Callbacks bleiben an bestehenden Plattformgrenzen; für neue UI-Abfragen vermeiden strukturierte Coroutines einen zweiten Scheduler oder RxJava. |
| Coil mit begrenzten Vorschauen | Glide ist ebenfalls möglich; Coil bindet direkt an Compose. Eigene Bilddecoder und ein weiterer Bildcache sind nicht nötig. |
| Ein `:app`-Modul mit Fachklassen | Zusätzliche Gradle-Module, DI-Frameworks und eine zweite Geschäftsregel-Schicht haben ohne konkrete unabhängige Laufzeit keinen Nutzen. |

Compose-Compiler und eingebautes Kotlin müssen dieselbe Version verwenden;
Bibliotheksauflösung, Kotlin-/Java-Interoperabilität, Android-Bau und die
synthetischen Regressionen prüfen diese konkrete Kombination. Eine Bibliothek
nur entfernen, wenn kein verbleibender Funktionspfad sie benötigt: Google Auth
wird weiterhin für Kalender verwendet, Tink ausschließlich zum Lesen älterer
verschlüsselter Sicherungen. Keine Drive-API wird dadurch wieder eingeführt.

## Sicherungs- und Wiederherstellungsvertrag

ZIP-Manifestformat1 und SQLite-Schema7 bleiben kompatibel. `PixelBackupService`
verwendet den konsistenten DB-Snapshot über `LocalBackupDataset.forStreaming`
und streamt Originaldateien ohne lokale Vollkopie direkt in eine unverschlüsselte
ZIP beim gewählten Android-Dokument. Größen-/SHA256-Prüfung pro Eintrag und
vollständiges Zurücklesen des ZIP-Streams ersetzen `capture/`, lokales `backup.zip`
und die komplette `verify/`-Entpackung im Sicherungsweg. Der Restoreleser behält
seine notwendige isolierte Staging-/Rollback-Funktion. `local_backup` prüft
verwaiste Arbeitsdateien, fehlende Medien-Vollkopien, Überschreiben desselben Ziels
mit neuer Kennung sowie Wiederholung und Fehlerbereinigung. Drive ist ausschließlich ein durch den Android-Dateianbieter
gewähltes Ziel oder eine ausdrücklich gewählte Restorequelle. Es gibt keine eigene
Drive-Anmeldung, API, Berechtigung oder Geschäftsdaten-Synchronisierung.
Kalenderfunktionen und ihre gesonderte Kontoauswahl bleiben erhalten.

Alle Tabellen, unbekannte Zusatzdaten, BLOBs, IDs, Beziehungen, private Dateien,
typisierte Einstellungen und lesbare Formularstände erhalten. `BackupInventory`
erzeugt Hinweise in `data/backup-notes.json`, ohne Rechnungsnummernkollisionen,
bereits vorhandene Beziehungsfehler oder fehlende Originale zu reparieren oder
allein deswegen abzubrechen. Unlesbare Datenbanken, beschädigte Archive und
Schreib-/Rücklesefehler bleiben echte Fehler. Die vollständige Dateiliste mit
Größen und Prüfsummen ist zwingend. Ältere verschlüsselte Archive werden nur gelesen;
der aktuelle Schreiber erzeugt keine neuen verschlüsselten Sicherungen.

Wiederherstellung benötigt eine ausdrückliche Auswahl und Bestätigung, technische
Prüfung, Rückfallkopie, geschützten atomaren Dateiaustausch sowie Wiederanlauf und
Rollback. Sie ist weder eine produktive Migration noch eine Rechnungsfreigabe.
Sicherung, Restore und Schemawechsel nicht aus einem allgemeinen Entwicklungsauftrag
ableiten. In diesem synthetischen Entwicklungs- und Prüfverfahren wird kein
persönliches oder aus Kundendaten abgeleitetes ZIP geöffnet. Formatunterstützung
beweist nicht die individuelle Lesbarkeit oder Unversehrtheit eines solchen Archivs.

`NativeRestoredDraftScreens` ordnet native Entwürfe und bekannte lesbare ältere
Formularhüllen passenden nativen Editoren zu. Quellhüllen, unklare Vorgangsbelege
und die dauerhafte Sichtungsentscheidung bleiben erhalten; alte Schreibaktionen
werden nicht automatisch wiederholt. Unbekannte Hüllen bleiben zur Sichtung
verfügbar. Rohe archivierte IndexedDB-/LevelDB-Dateien werden bewahrt, aber nicht
als vollständig interpretierte Browserdatenbank importiert. Ein unvollständiger
Medienspiegel oder fehlende Nachweise blockieren die Medienaktivierung oder werden
bewusst zurückgestellt. Diese Grenzen auch bei synthetischen Restorefällen prüfen;
Dateierhaltung und vollständige Bedienbarkeit sind unterschiedliche Nachweise.

Die native Datensuite prüft zusätzlich die lokale Speicherbereinigung in einem
neuen synthetischen Kontext: Auftrags-/Terminlöschung mit geleisteter Arbeit,
Purge/Leerung ohne Vorschau-Hashes, gemeinsam verwendete Fotos, Papierkorb,
Entwurfs-PDF gegenüber ausgestelltem Beleg, alte Upload-BLOBs/Snapshot-Duplikate,
Lösch-Sicherungen und erledigte Löschbelege, WAL/VACUUM und erneutes Öffnen.
Die Compose-Suite betätigt zusätzlich „Speicher bereinigen“, vergleicht die
Geschäftsdaten und fotografiert Aktion/Ergebnis bei 100 % und 200 % Schrift.
Diese Prüfungen ändern keine produktiven Dateien.

## Regressionsschleife für das Kotlin-Refactoring

Auftrag, Freigaben, Akzeptanzmatrix und tatsächliche Prüfergebnisse stehen
ausschließlich in `AKTUELLER-STAND.json`. Diese Anleitung führt keine zweite
Statusliste. Ausschließlich frei erfundene synthetische Fixtures, generierte
Testbilder und neue isolierte temporäre Datenbanken verwenden. Keine produktiven
oder daraus abgeleiteten Kundendaten, Fotos, Datenbanken oder Backups öffnen.
Tests entfernen nur ihren eigenen temporären Bestand.

1. Betroffene Bedienwege, Speichergrenzen und Abhängigkeiten nach KISS/YAGNI prüfen;
   jeder Anforderung ein prüfbares Sollverhalten und passende Nachweise zuordnen.
2. Begrenzte Änderung umsetzen und die betroffenen Quell-, Fach-, UI- und Bauprüfungen
   ausführen. Ein Hauptagent integriert ausdrücklich beauftragte, abgegrenzte
   Teilaufgaben und prüft deren Ergebnisse unabhängig.
3. Fehler an der Ursache beheben; Tests und Sollverhalten nicht zum Bestehen
   abschwächen. Unveränderte, fehlende, JSON-null- und optionale Altfelder ebenso
   prüfen wie Rückfall, Abbruch und Wiederholung mit derselben Vorgangskennung.
4. Nach der letzten funktionalen Änderung die betroffene Gesamtsuite einschließlich
   `tools/check_current.py` auf dem finalen Stand ausführen. Spätere Quelländerungen
   entwerten davon berührte Nachweise und benötigen passende Nachtests.
5. Anforderungen gegen Implementierung und Ergebnisse abgleichen. Fehlende
   Werkzeuge, Laufzeittests und Pixel-Abnahmen bleiben ausdrücklich offen.

Jeder Nachweis nennt Quellcommit oder abweichenden Fingerabdruck, Testbefehl,
Umgebung, Ergebnis und Messgrenzen. `PASS`, `FAIL` und `NOT_RUN` unterscheiden.
Ein Build ist kein ausgeführter Emulatorlauf; ein synthetischer Emulatorlauf ist
keine Pixel-Abnahme oder Prüfung eines echten Drive-Kontos. Vorhandener Testcode
und ein definierter CI-Schritt allein belegen keinen erfolgreichen Lauf.

## Quellprüfung und Android-Bau

```sh
python3 -B tools/check_current.py
```

Der Befehl prüft aktuelle Quellen, Originalidentität, Datenschutz, generierte
Verträge, synthetische Fixtures, Pflanzenressourcen und Wartungstexte. Er baut
keine APK, signiert nicht und greift auf kein Gerät zu.
`tools/verify_ui_mcp_inventory.py` prüft die 210 semantischen Aktionen gegen den
nativen Katalog und die geprüften Kotlin-Quellfingerabdrücke. Plattformausnahmen
und stillgelegte Drive-Aktionen bleiben ausdrücklich erkennbar. Nach tatsächlicher
Quellsichtung mit `--record-reviewed` aufzeichnen und anschließend ohne Schalter
prüfen; die Aufzeichnung ersetzt keine ausgeführten Bedienwege.

```sh
cd android
gradle --no-daemon --no-configuration-cache --no-build-cache :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac
```

Release- und Testpaket für einen beauftragten synthetischen Android-Lauf:

```sh
gradle --no-daemon --no-configuration-cache --no-build-cache --max-workers=2 :app:assembleRelease :app:assembleReleaseAndroidTest :app:lintRelease
```

Vor- und Artefaktprüfungen des Gradle-Baus bleiben zwingend. Die Release-APK bleibt
hier unsigniert; die Debugvariante ist deaktiviert. Für einen Benutzer-Updateauftrag
gilt `docs/entwicklung/SIGNIERUNG.md`: Originalzertifikat, Paket, höhere Version,
leere Startdaten, Quellbindung und APK-Prüfsumme prüfen. Erfolgreiche Kompilierung
erteilt keine Bereitstellungs-, Installations- oder produktive Restorefreigabe.

## Aktuelle JVM-Prüfungen

`tools/run-followup-java.py` kompiliert die aktuellen Kotlin-Fachquellen zusammen
mit Java-Testprogrammen und synthetischen Speicheradaptern. Er entdeckt die
vorhandenen `*Tests.java` und Kotlin-Tests automatisch. Dazu gehören Domain-,
Finanz-, MCP-/Journal-, Gerätewartungs-, Gerätefoto-, ZIP- und Formatprüfungen.
Benötigt werden JDK17, SDK37, Kotlin2.2.10 und die aufgelösten Gradle-Abhängigkeiten.
`HG_KOTLINC` kann einen passenden Compiler bezeichnen; andernfalls verwendet der
Runner die festgelegten Compiler-JARs aus dem Cache. Er lädt nichts nach.
`HG_JSON_JAR` verweist auf `org.json:json:20240303`, SHA256
`3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed`.

```sh
python3 -B tools/run-followup-java.py
```

Für reale SQLite-Transaktionen in einer neuen synthetischen JDBC-Datei zusätzlich
`--tables` verwenden. `HG_SQLITE_JDBC_JAR` bezeichnet SQLite-JDBC3.46.1.0,
`HG_SLF4J_API_JAR` SLF4J-API1.7.36; der Runner prüft ihre festgelegten SHA256-Werte.

```sh
python3 -B tools/run-followup-java.py --tables
```

Die Tabellensuite prüft Kopierabbruch, Fortsetzung, CAS, Replay, Filter- und
Seitengleichheit, Unicode-Großdaten, Journalprojektion, atomaren Fehler-Rollback
und den Rückweg mit aktuellen Änderungen. Java-/Kotlin-Nullgrenzen und alte
optionale JSON-Felder gehören zu den Domain- und MCP-Regressionen. JDBC prüft
weder Androids Transaktionslistener noch CursorWindow oder die echte Oberfläche.

## MCP-Katalog prüfen

`get_capabilities.tools` mit den tatsächlich im verwendeten ChatGPT-Connector
exponierten Werkzeugen vergleichen. `run_self_test.ui_mcp_parity` prüft den
App-internen Katalog, nicht einen im Client gespeicherten Werkzeugstand. Nach
einer Katalogaktualisierung Anzahl, Namen, Schemas und Beschreibungen erneut
vergleichen. Pluginmanifest und Appkennung bleiben bei einer reinen
Katalogaktualisierung unverändert.

## Native Android-Prüfungen

`qa-apk.yml` definiert Release-/Testbau, JVM-Lauf mit `--tables` und acht native
Phasen auf einem frischen API35-Wegwerf-Emulator in genau dieser Reihenfolge:

| Suite | Prüfgrenze |
|---|---|
| `local_backup` | Synthetischer vollständiger Bestand, ZIP, Hinweise, Fehler, Rückfallkopie und geschützter Restore |
| `local_backup_reopen` | Derselbe isolierte Restorebestand nach Prozessneustart |
| `browser_checkpoint` | Dauerhafte Formularsichtung, reale SQLite-Transaktionen, aktive Medienauswahl, Fehler und Replay |
| `browser_checkpoint_reopen` | Gespeicherte Sichtungsentscheidung und Medienbezüge im neuen Prozess |
| `native_data` | Echte Leseprojektion mit 40 000 Zeilen, Indexpläne, Seitenauswahl, WAL-Leser während eines Schreibers, Flow und Main-Thread-Diskprüfung |
| `document_provider` | Separater nicht lokaler Testanbieter, echte URI-Rechte, Dateipicker, Abbruch, Fehler, Rücklesen und Activity-Neuerstellung |
| `document_provider_reopen` | Dauerhafte Pickerrechte und Rücklesen nach Neustart beider Prozesse |
| `native_compose` | Echte MainActivity, Kundenbearbeitung/Suche/Seiten, MCP-Änderung in sichtbarer Liste, Entwürfe bei Neuerstellung, Timer, Planungsaufruf und 200-%-Schrift |

Die Backup-, Checkpoint- und `native_data`-Phasen verwenden isolierte synthetische
Kontextpfade. Der `DocumentsProvider` liegt ausschließlich in der Test-APK mit
separater UID und
eigenem Prozess; seine Shell-Steuerung verlangt das Testpfadrecht und die Shell-UID.
Er meldet sich als nicht lokal, benutzt aber ausschließlich neue synthetische
Dateien ohne Konto oder Netzverbindung. `native_compose` verlangt den bekannten
synthetischen Bestand aus `document_provider_reopen` auf demselben Emulator.
Reihenfolge und Schutzbedingungen nicht umgehen. Die Test-APK enthält die für
ihren separaten Anbieterprozess nötige Kotlin-Laufzeit; Testanbieter, Testklassen
und Testrechte dürfen nicht in die Release-APK gelangen.

Die CI sammelt Ergebnisse aller acht Suiten und schlägt bei jedem einzelnen
Fehler insgesamt fehl. Jeder Versuch startet mit gestoppten App-Prozessen und
behält seine eigenen Schutzbedingungen; fehlende Vorbedingungen einer abhängigen
Suite bleiben ein Fehler. Dadurch verdeckt ein Datenbankfehler keine unabhängig
prüfbaren Anbieter- oder Oberflächenfehler.

Die CI-Signatur ist ein temporärer Schlüssel ausschließlich für diese
Wegwerf-Emulatorpakete. Sie signiert kein Benutzerupdate und veröffentlicht keine
APK. Aus Laufdefinition oder Kompilierung keinen aktuellen Laufzeiterfolg ableiten;
Ergebnisse und Blockaden gehören ausschließlich in den aktuellen Arbeitsstand.

Nach eigener Auswahl des synthetischen Testgeräts und passend bereitgestellter
App/Test-APK lautet der Instrumentierungsaufruf:

```powershell
& $gpAdb -s $gpSerial shell am instrument -w -e suite native_data at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation
```

Für die acht Phasen den Suitewert gemäß Tabelle wählen und die erforderlichen
Prozessneustarts wie im Workflow ausführen. Ein Ergebnis muss die verlangten
Schutz- und Fachnachweise enthalten; Exitcode oder Prüfanzahl allein genügen nicht.
Die Namen `browser_checkpoint*` bezeichnen den erhaltenen Sicherungs-/Sichtungsvertrag,
keinen WebView-Bildschirm.

`business_tables` bleibt ein zusätzlicher ausdrücklich gewählter ADB-Einstieg mit
neuer synthetischer SQLite-Datei. Er prüft den Android-Speichervertrag,
Projektionsrevisionen und gemeinsamen Commit/Rollback von Journal, Zeile und
Requestbeleg. Er ersetzt keinen vollständigen Restore oder einen Durchlauf aller
UI- und MCP-Schreiber.

```powershell
& $gpAdb -s $gpSerial shell am instrument -w -e suite business_tables at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation
```

Fotos zusätzlich am tatsächlichen nativen Bildpfad prüfen: verifizierte Vorschau
ohne Originalzugriff, geprüfter Originalfallback, Coil-Dekodiergröße, sichtbarer
Fehler, Auswahlabbruch, Gerätezuordnung, Export und Neustart. Ebenso die ausgewählten
und ausdrücklich zurückgestellten Restoremedien prüfen. Ein alter DOM-/Bridge-Test
belegt diese Compose-Bedienwege nicht. Physischer Pixel, reales Drive-Konto und ein
individuell gewähltes Sicherungsarchiv benötigen ihre eigenen zulässigen Nachweise.

Für die vereinfachte Bedienung prüft `native_compose` zusätzlich das erneute Öffnen
der Kundenliste bei erhaltenem Filter, freie Arbeits-/Pausen-/Entsorgungswerte,
Textbausteine, sofortige Notizen und den Verwerfungsdialog mit eigenen temporären
Fotodateien. Gespeicherte Fotobytes werden davor und danach verglichen. Ein frisches
Formular darf keine Speicherbestätigung oder Fotoauswahl des vorherigen Formulars
übernehmen. Tastatur- und 200-%-Schriftprüfungen vergleichen die tatsächlichen
Aktionsgrenzen mit dem sichtbaren Bereich und den Systemabständen.
Bei 200 % Schrift zusätzlich Beginn/Ende sowie Pausen- und Entsorgungsauswahl
bedienen. Die gezielten synthetischen Bildschirmbilder prüfen, statt allein aus
erreichbaren Semantikknoten auf ein lesbares Layout zu schließen. Der vorhandene
CI-Prüflauf bewahrt dazu nur seine benannten Testbilder und die SHA256-Werte der
temporär signierten Emulatorpakete auf; keine Nutzer-APK wird veröffentlicht.
Die gemeinsame Feldkomponente stellt ihre Beschriftung als stabile Testkennung
bereit. Der Emulator liest frische Accessibility-Knoten und bestätigt den konkreten
Feldwert nach der Eingabe; abgeschnittene schwebende Beschriftungen sind keine
Feldidentität. Grundlage ist die bestehende
[Compose-/UiAutomator-Interoperabilität](https://developer.android.com/develop/ui/compose/testing/interoperability).

Die Zubehörprüfung verbindet die native Geräteauswahl, freie Bezeichnung,
Kettenspezifikationen und aktuelle Kette mit SQLite-Rücklesung. Das generierte Foto
läuft über den vorhandenen Uploadpfad; nach Activity-Neuerstellung wird auf eine
erfolgreiche Coil-Dekodierung gewartet. Die Zubehörprüfung allein ist kein Nachweis
der Kamera- oder Systempickerbedienung; diese bleiben getrennte Prüfungen.

`native_data.office_reads` misst Abfragen mit 120 synthetischen Rechnungen und
240 Zahlungen. `native_compose.office_opening` misst vom ausgelösten Öffnen bis zu
sichtbaren bereiten Inhalten: Abrechnungsformular, Rechnungsliste sowie die
gemeinsame Ansicht für Zahlungen und Guthaben. Je Pfad zwei Aufwärmläufe und
40 Messungen, Mittelwert, P95 und langsamste 5 % ausgeben. UI-Zeiten enthalten
Semantik-Abfrage/Polling; Hostlast ist nicht kontrolliert. Ersten Lauf als Ausgangs-
messung führen. Repositoryzeiten, Emulatoranzeige und physischer Pixel dürfen
weder gleichgesetzt noch als unbelegter Vorher-/Nachhergewinn dargestellt werden.
Der gezielte Zahlungsleservergleich führt den bisherigen Leser und den aktuellen
Leser im selben Prozess mit wechselnder Reihenfolge je 40-mal aus. Reihenfolge,
Inhalte und Hash müssen gleich bleiben; große Unicodeeinträge prüfen weiterhin
den vorhandenen gestückelten Rückfallpfad. Diese Teilmessung belegt keine
Beschleunigung der gesamten Zahlungsansicht oder des physischen Pixels.

## Eigenständige Website

`static/` und seine JavaScript-/CSS-Ressourcen bleiben eine eigenständige Website.
Der verteilte Pflanzenkatalog ist `android/app/src/main/assets/plant-catalog.json`;
`tools/sync_plant_assets.py` prüft daraus erzeugte Webspiegel und referenzierte Bilder.
Browserprüfungen bei Änderungen an der Website auf ihre tatsächlichen Ressourcen
ausrichten und getrennt von Android auswerten. Browser- und Node-Tests sind kein
Nachweis für die Kotlin-/Compose-App; insbesondere darf ein Test keine entfernte
Android-HTML- oder Bridge-Datei als aktuelle App laden.

Offizielle Grundlagen:
[Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin),
[AGP9.1-Kompatibilität](https://developer.android.com/build/releases/agp-9-1-0-release-notes),
[Compose-Compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler),
[Coil-Kompatibilität](https://coil-kt.github.io/coil/changelog/).

## ADB-Analyse und Lese-/Schreibtests über den Laptop

Der konkrete aktuelle ADB-Auftrag muss Lesen, Schreiben und Testumfang abdecken.
Eine frühere Freigabe oder diese Anleitung ersetzt den aktuellen Auftrag nicht.
Der Windows-Laptop wird konkret als USB-/ADB-Verbindung zum Pixel benötigt;
Android Studio, lokale Builds und ein Emulator sind dafür nicht erforderlich.
Quellarbeit und Auswertung möglichst in der Cloud belassen. Eine Cloud-Sitzung
besitzt durch eine lokale USB-Verbindung nicht automatisch Zugriff auf ADB.
Den tatsächlichen Ausführungsort und die erreichbare Verbindung feststellen.

### Verbindung und eindeutig gebundener Test

Vorhandene Android Platform Tools und die bewusst bestätigte Gerätefreigabe
verwenden. `adb devices -l` lokal zur Auswahl lesen; keine Seriennummern in GitHub
übernehmen. Bei mehreren Geräten immer die konkrete Auswahl `-s` verwenden.
Keinen offenen ADB-Port ins Internet stellen. Keine Verbindung, `offline` oder
`unauthorized` bedeutet **NICHT AUSGEFÜHRT**, nicht PASS.

PowerShell, lokal auf dem verbundenen Laptop:

```powershell
$gpAdb = 'adb'
& $gpAdb devices -l
$gpSerial = Read-Host 'Seriennummer des ausdrücklich ausgewählten Testgeräts'
& $gpAdb -s $gpSerial get-state
& $gpAdb -s $gpSerial shell getprop ro.build.version.release
& $gpAdb -s $gpSerial shell getprop ro.build.version.sdk
& $gpAdb -s $gpSerial shell dumpsys package at.hausgarten.service |
  Select-String '^\s*versionCode=|^\s*versionName='
& $gpAdb -s $gpSerial shell wm size
& $gpAdb -s $gpSerial shell wm density
& $gpAdb -s $gpSerial shell settings get system font_scale
```

Vor einem schreibenden **App-Test** müssen exakte Quell-/APK-Version, Paketkennung,
Testfall und synthetischer Bestand feststehen. Kein Import von Produktivdaten in
Testprofile. Keine zweite operative App oder Datenbank einführen. Die bestehende
Installation nicht durch eine ungeprüfte Test-APK ersetzen. Die allgemeine
ADB-Testfreigabe setzt weder Installations- noch Rechnungs-/Migrationsgates außer
Kraft. Ein bloßes `test_record`-Flag beweist keine Isolation eines echten Bestands.
Native App-Prüfungen verwenden ausschließlich den ausdrücklich ausgewählten,
isolierten synthetischen Bestand. Die Emulator-Suiten mit Schutz gegen echte
Geräte dürfen für eine Pixel-Abnahme nicht umgangen werden.

### Prüfen des ADB-Lese-/Schreibtransports

Die folgenden Schritte schreiben ausschließlich eine neu erzeugte synthetische
Prüfdatei in ein neues Verzeichnis unter `/data/local/tmp`, lesen genau diese Datei
zurück und vergleichen Größe und SHA256. Das ist ein Transporttest, kein Nachweis
für App-Speichern, SQLite, Backup oder Restore. Der Verzeichnisname wird vom Gerät
erzeugt und geprüft; es werden keine vorhandenen Appdateien gelesen oder gelöscht.

```powershell
$gpTestDir = (& $gpAdb -s $gpSerial shell mktemp -d /data/local/tmp/qa_gartenpirat_XXXXXXXX).Trim()
if ($LASTEXITCODE -ne 0 -or $gpTestDir -notmatch '^/data/local/tmp/qa_gartenpirat_[A-Za-z0-9]+$') {
  throw 'Kein eindeutiges neues ADB-Testverzeichnis; Schreibtest nicht begonnen.'
}
$gpLocal = Join-Path ([IO.Path]::GetTempPath()) ('qa_gartenpirat_' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($gpLocal) | Out-Null
$gpSource = Join-Path $gpLocal 'probe.bin'
$gpReadback = Join-Path $gpLocal 'readback.bin'
$gpBytes = [Text.Encoding]::UTF8.GetBytes(('SYNTHETIC_GARTENPIRAT_ADB_0123456789' * 4096))
[IO.File]::WriteAllBytes($gpSource, $gpBytes)
try {
  & $gpAdb -s $gpSerial push $gpSource "$gpTestDir/probe.bin"
  if ($LASTEXITCODE -ne 0) { throw 'ADB-Schreiben fehlgeschlagen.' }
  & $gpAdb -s $gpSerial pull "$gpTestDir/probe.bin" $gpReadback
  if ($LASTEXITCODE -ne 0) { throw 'ADB-Rücklesen fehlgeschlagen.' }
  $gpExpected = (Get-FileHash -Algorithm SHA256 $gpSource).Hash
  $gpActual = (Get-FileHash -Algorithm SHA256 $gpReadback).Hash
  if ($gpExpected -ne $gpActual -or (Get-Item $gpReadback).Length -ne $gpBytes.Length) {
    throw 'ADB-Rücklesen: Inhalt oder Größe abweichend.'
  }
  [PSCustomObject]@{ scope='SYNTHETIC_ADB_TRANSPORT'; bytes=$gpBytes.Length; sha256=$gpActual; result='PASS' }
} finally {
  & $gpAdb -s $gpSerial shell rm -f "$gpTestDir/probe.bin"
  & $gpAdb -s $gpSerial shell rmdir $gpTestDir
  Remove-Item -LiteralPath $gpSource -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $gpReadback -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $gpLocal -ErrorAction SilentlyContinue
}
```

Bei Verbindungsabbruch den bekannten erzeugten Testpfad zur gezielten späteren
Bereinigung behalten. Kein pauschales `rm -rf`, `pm clear`, Löschen der Appdaten,
Zurücksetzen von Rechnungszählern oder Überschreiben einer produktiven Datenbank.

### Messwerte während der synthetischen App-Testfälle

Vergleichbare Bedienwege mit demselben synthetischen Fixture und mindestens
40 Wiederholungen je Variante messen: Start, gefilterte Liste/Seitenwechsel,
Detailansicht, Bearbeiten/Speichern, Timerabschluss und Fotoansicht. Kalte Starts
und warme Wechsel getrennt messen. Vor jedem Vergleich Build, Schriftgröße, Anzeige, Energielage,
thermischen Zustand und Hintergrundlast festhalten. Neustart-/Abbruch-/Restoretests
schreiben ausschließlich in den expliziten synthetischen Testbestand.

Gezielte Abfragen, ohne Rohprotokolle im Repository zu speichern:

```powershell
& $gpAdb -s $gpSerial shell dumpsys meminfo at.hausgarten.service |
  Select-String '^\s*(Native Heap|Dalvik Heap|Java Heap|Graphics|Stack|Code|Private Other|System|TOTAL|TOTAL PSS|TOTAL RSS|TOTAL SWAP PSS|Views:|AppContexts:|Local Binders:|SQL|MEMORY_USED:|PAGECACHE_OVERFLOW:|MALLOC_SIZE:)'
& $gpAdb -s $gpSerial shell dumpsys cpuinfo |
  Select-String '^\s*[0-9.]+%\s+\d+/at\.hausgarten\.service(?:\s|:|/)'
& $gpAdb -s $gpSerial shell dumpsys gfxinfo at.hausgarten.service framestats |
  Where-Object { $_ -match '^Flags,|^\d+(,\d+)+,?$|^\s*(Total frames rendered:|Janky frames:|\d+th percentile:)' }
```

`gfxinfo ... reset` ist als schreibender Messzugriff freigegeben, wenn bewusst
ein neues Messfenster begonnen wird; vor dem Reset benötigte Werte erhalten.
Keine ungefilterten `logcat`-/Bugreport-/Heap-/Bildschirm-/UI-Hierarchy-Abzüge.
SQL-Zeit, Abfragezahl, gelesene Zeilen/Bytes und Vollsnapshotzähler aus der
synthetischen Instrumentierung erfassen, ohne SQL-Argumente oder Datensätze zu
protokollieren. Projektionsdiagnose, Cache-Wiederaufbauten, Flow-Aktualisierungen
und native Bildschirmwechsel im gleichen Messfenster zuordnen. Nur tatsächlich
erfasste Messwerte auswerten; fehlende Werte als fehlend ausweisen.
ADB-Übertragungszeit ist keine Datenbank- oder UI-Laufzeit.

Bei `framestats` nur gültige, vollständige Frames auswerten; nicht abgeschlossene,
markierte oder doppelt gelesene Frames getrennt zählen. Die Framezeit aus
`FrameCompleted - IntendedVsync` berechnen. Variable Bildwiederholrate beachten;
kein pauschales 16-ms-Limit als Gerätebeweis. `gfxinfo` allein belegt weder
vollständige Compose-Neuberechnungen noch die Ursache eines langsamen Frames.
Speicherwerte PSS, RSS, privat und Swap unterscheiden; mehrere Messpunkte statt eines Einzelwerts verwenden. CPU-Messungen
mit anderem Zeitfenster oder anderem Prozessumfang nicht direkt vergleichen.

### Ergebnisse in Verbesserungen überführen

Alle nutzbaren, datensparsamen Testbefunde zusammenführen: fachliche Ergebnisse,
Fehler-/Abbruchpfade, Hash-/Anzahlvergleiche, Lese-/Schreibumfang, Ausreißer,
Speicherverlauf, CPU und Bildaufbau. Je Befund konkrete Quellstelle, Hypothese,
kleinste passende Änderung und denselben Nachtest nennen. Keine Funktionen auf
Vorrat. Zuerst Regressions-/Integritätsfehler beheben, dann belegte Engpässe.
Jede Verbesserung gegen die ursprünglichen Fach- und Schutzregeln prüfen.

Der abschließende Beleg unter `docs/verification/` enthält pro Bedienweg Mittelwert,
P95 und Mittelwert der langsamsten 5 %, Stichprobenzahl, Bedingungen, Messgrenzen
und Fehler. Cloud-Browser, JVM/SQLite, Emulator, ADB-Transport und physischer Pixel
werden getrennt ausgewiesen. Aus bislang ungemessenen Änderungen keinen Gewinn
ableiten. Fehlt eine vergleichbare Geräte-Vorhermessung, den ersten Gerätelauf als
Ausgangsmessung führen; alte/produktive Daten nicht zur Vergleichbarkeit zurückspielen.

Offizielle Grundlagen:
[Android Debug Bridge](https://developer.android.com/tools/adb),
[dumpsys: Speicher und Framezeiten](https://developer.android.com/tools/dumpsys),
[AndroidJUnitRunner](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner).

## Codex-Regeln neu einlesen

Die einzige Regelfassung ist `AGENTS.md` im Repository-Hauptverzeichnis.
`.codex/hooks.json` ruft bei `UserPromptSubmit` den kleinen Lesehelfer auf.
Er stellt die aktuellen Checkout-Dateien samt Prüfsummen bereit; er lädt keine
externen Aufträge und verändert weder Dateien noch die App. Fehlende oder
ungültige Pflichtdateien führen zu einer expliziten Blockierantwort.

Eine unterstützte Codex-CLI muss das Projekt und die konkrete Hookdefinition
zuerst als vertrauenswürdig akzeptieren. Dazu `/hooks` öffnen und die Definition
prüfen/freigeben. Geänderte Definitionen benötigen eine neue Prüfung. Die
Konfiguration im Repository allein beweist keine Aktivierung in anderen Clients.
Lokale Command-Hooks werden nicht in jeder Cloud-Oberfläche unterstützt; die
laufende ChatGPT-Unterhaltung wird durch einen Git-Commit nicht umkonfiguriert.
Der tatsächliche Aktivierungs-/Prüfstand steht im aktuellen Arbeitsstand.

Die Kundenkontext-Prüfung öffnet Fotos und Angebote über zwei verschiedene
synthetische Kundenakten, prüft den Ausschluss des jeweils anderen Kunden und
die Rückkehr aus dem Angebotsdetail. Sie prüft außerdem Bereichszuordnung,
erneutes Öffnen der Übersicht und das Entfernen alter Meldungen bei Navigation.
Die Bilder `qa-native-customer-photos.png` und `qa-native-customer-offers.png`
gehören zum vorhandenen synthetischen CI-Artefakt.

Die gemeinsame Auswahlansicht wird in sechs Kombinationen geprüft: Schrift 100/130 %
bei normaler Höhe und 200 % bei verkleinerter Höhe, jeweils im Hoch-/Querformat. Echte IME-Fenstergrenzen,
Treffergrenzen, Android-Touchereignisse und Bilder werden zusammen ausgewertet.
Suchen/Fertig muss die Eingabe beenden und Suchtext/Treffer erhalten. Nulltreffer,
leere Suche, Seitenwechsel, Auswahlabbruch und die Rückkehr nach Activity-Neuerstellung
gehören zu derselben synthetischen Compose-Suite.

Zusätzlich prüft dieselbe Suite 16 fachliche Suchwege: Arbeitsverlauf, Angebote,
Rechnungen, Planung, Geräte, Fotos, Leistungskatalog, Pflanzen, lokale Suche,
Textbausteine, gleichnamige Kunden, Geräteauswahl, Planungsauswahl, Fotozuordnung,
Angebotspositionen und Wartungstexte. Nulltreffer, IME-Aktion, erhaltener Suchtext,
sichtbare Treffergrenzen und tatsächliche Touchauswahl werden zusammen geprüft.
38 Einstiege ergänzen den Abgleich aller Bereiche und der Sonderseiten, darunter
Einstellungen/Kalender/Sicherung bei 200 %. Verbindungen werden dabei nur gelesen,
keine Kontofreigabe, produktive Sicherung oder Migration ausgelöst. Die
ChatGPT-Zugangsdatenansicht behält `FLAG_SECURE`; für Screenshots wird dieser
Schutz nicht aufgehoben.

Eine getrennte Bedienfolge aktiviert die Android-Gestennavigation ausschließlich
im Wegwerf-Emulator, schließt den Kundenwähler mit echten Rand-Touchereignissen
und prüft den unveränderten Formularzustand. Das zuvor aktive Navigations-Overlay
wird anschließend wiederhergestellt. System-Zurück per Taste und Randgeste
bleiben getrennte Prüfungen.

Die Bedienfolge `filter_keyboard` prüft Arbeitszeit- und lokale Datumsfilter bei
geöffneter Tastatur: sichtbare Feldgrenzen, Eingabe, Fertig, erhaltene Werte,
Schließen der Filter durch Hauptsuchfokus und Activity-Neuerstellung.
`qa-native-filter-work-date.png` und `qa-native-filter-local-date.png` zeigen
die jeweiligen tatsächlichen Eingaben mit IME.

Der native Datenzugriffstest schließt den SQLite-Helper auch mitten in einer vollständigen Bestandsabfrage. Erfolg und Abfragefehler müssen die Transaktion beenden, die letzte Datenbankreferenz freigeben und anschließendes Lesen/Schreiben ohne verbleibende Sperre erlauben.

Ergänzend prüft `list_viewports` elf Fachlisten bei 200 % und verkleinerter Höhe im Hochformat; Kunden, Arbeit, Angebote, Rechnungen, Leistungskatalog und lokale Suche zusätzlich im Querformat (17 Fälle). Suche und Trefferzahl müssen vollständig oberhalb der IME liegen. Bei langen Detailkarten darf der restliche Karteninhalt scrollbar außerhalb des kleinen Ausschnitts liegen; der sichtbare Titelanfang und eine mindestens 48 × 48 dp große tatsächlich injizierte Touchfläche müssen innerhalb der Bildschirm-/IME-Grenzen liegen. Die bisherigen Dialog- und normalen Suchprüfungen verlangen weiterhin vollständig sichtbare Ergebnisflächen. IME-Fertig, erhaltener Suchwert und Öffnen desselben Treffers werden getrennt geprüft.
