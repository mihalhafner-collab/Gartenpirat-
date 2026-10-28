# Aktueller Gartenpirat-Auftrag

## Neuer direkter Nutzerauftrag: nur lokale unverschlüsselte Sicherung

„Entferne restlos alle Backup pfade zu drive. Sicherung auf Pixel ohne
verschlüsselung. Halte Backup möglichst einfach und userfreundlich.“
Dieser neuere ausdrückliche Auftrag ersetzt Drive-/Verschlüsselungsvorgaben für
neu erzeugte App-Sicherungen. Eine lokale ZIP mit vollständigem Bestand und
bewusster Wiederherstellung, ohne Konto, Netz, Schlüssel oder Hintergrundjobs.
Drive-Transport, Auswahl und automatische Backupstarts aus Produktion entfernen;
historische Belege und alte Sicherungsdateien/Schlüssel nicht löschen. Alte
verschlüsselte Archive bleiben nur zur Wiederherstellung lesbar. APK-Bereitstellung
auf Drive ist davon getrennt weiterhin vom Nutzer verlangt. Keine Installation,
Migration, produktiven Tests oder Rechnungsfreigabe. Hash-/Vollständigkeitsprüfung
und Rückweg beim bewussten Restore bleiben erhalten.

## Direkter Backup-Folgeauftrag28.10.: Hänger statt Screenshot-Schalter

Nutzer meldet weiter hängenden Backup-Prozess und verlangt funktionierende
Sicherung statt weiterer Bedienhürden. Screenshotänderung nicht bestätigt.
Hängepfade reproduzierbar prüfen und korrigieren; keine Schutzgates pauschal
umgehen. Konkreter Gerätestatus/Version separat erfragt, noch nicht belegt.

## Direkte Bereitstellungspräzisierung28.10.

Nutzer: „Bisherige zur verfügungstellung wie gewohnt Drive“. Geprüfte
originalsignierte APKs weiterhin im bestehenden privaten Drive-APK-Bereich
bereitstellen und zurücklesen; GitHub bleibt die einzige Entwicklungsquelle.
Keine öffentliche Freigabe, keine Installation und keine Quell-/Statuskopien
nach Drive. Aktueller beauftragter Prüfkandidat1.55.6/111.

## Direkter Folgeauftrag28.10.: Etappen3–5 abschließen; Backupfehler zuerst klären

Nutzerauftrag: „Schließe Etappen 3-5 vollständig ab. Vorher zwichenbericht von
etappe 1 und 2 für mich“. Der dokumentierte Tabellen-/Übernahme-/Rückwegplan ist
hiermit für die Quellimplementierung freigegeben. Die bewusste produktive Übernahme,
verschlüsseltes Backup mit Rücklesen und Gerätebestätigung bleiben Vorbedingungen;
keine automatische Migration, Installation oder Rechnungsfreigabe.

Anschließende Nutzerklärung: „Backup Funktion arbeitet nicht wirklich“;
Symptom: „Sicherung startet nicht oder bleibt hängen“. Keine erfolgreiche Pixel-
Sicherung oder Wiederherstellung bestätigt. Deshalb zuerst den vorhandenen Backup-
Start/Fortsetzungsweg prüfen und reproduzierte Ursachen minimal reparieren.
Eine bestätigte Sicherungsanforderung bei Schedulerfehlern erhalten, dieselbe
Request-ID/Argumente erneut lesend prüfen, keine versteckte Neuanlage. Keine
Sicherungs-/Wiederherstellungs-/Migrationsfreigabe aus synthetischen Tests ableiten.
Etappe5-Löschungen bleiben bis bestätigtem Pixel-Backup/Restore gesperrt.
Zwischenbericht: `docs/verification/interim-stages1-2-2026-10-28.md`.

Weitere direkte Nutzerpräzisierung: „Ändere bei Backup Einstellungen wlan vorgabe
zu mobile Daten und krine Android Entscheidung sondern auf wunsch des users.“
Neue Einrichtung erlaubt mobile Daten; manuelles „Jetzt sichern“ führt den
angeforderten bestehenden Job unmittelbar im Vordergrund aus, keine bloße
Android-Jobvormerkung. Zusätzliche tägliche Automatik bleibt bewusst wählbar,
standardmäßig aus. Gespeicherte ausdrückliche Netz-/Automatikentscheidungen nicht
still überschreiben. Original-ID, Fortschritt, Unterbrechung und vollständiger
externer Hashnachweis bleiben erhalten; keine Zusicherung gegen OS-Prozessabbruch.


Weitere direkte Nutzeranweisung: „Sollten noch soerren bestehen diese gleich mit beheben.“
Unbeabsichtigte Backup-Bedienblockaden prüfen und korrigieren; Schutzgates bleiben
erhalten. Insbesondere ausdrückliche Wiederholung eines wartenden bestehenden Jobs
anbieten, ohne automatische Wartezeiten oder Prüfsummenprüfungen zu umgehen.

## Ausführungsauftrag vom 28.10.2026

Direkte Nutzeranweisung: „Umsetzten und nach abschluss apk erstellen“.
Etappen strikt nacheinander umsetzen, prüfen, auf GitHub sichern und zurücklesen.
Nach Abschluss der freigegebenen Arbeiten eine geprüfte originalsignierte APK
erstellen. Keine Installation, automatische Migration oder Rechnungsfreigabe.
Die ausdrückliche gesonderte Freigabe für Etappe 3 und die Pixel-Bestätigung vor
Etappe 5 werden dadurch nicht ersetzt. Geräteabnahmen offen ausweisen.

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

### Konkreter Plan zu Etappe 3 – vor Freigabe, nicht umgesetzt

Stand nach Etappe2: nur Plan; kein Schemawechsel, keine Datenübernahme und kein
neuer Schreiber. Die folgende technische Umsetzung benötigt die ausdrückliche
Freigabe dieses Plans. Physisches verschlüsseltes Backup und Rücklesen bleiben
zusätzliche Vorbedingungen der späteren bewussten Geräteübernahme.

**Tabellen in derselben bestehenden SQLite-Datenbank:**

| Tabelle | Einzelner Datensatz und gezielte Indizes |
| --- | --- |
| `business_customers` | `id` als Primärschlüssel; Name, Aktivstatus; Index Name/Aktivstatus |
| `business_orders` | `id`; Einsatzkunde, Rechnungsempfänger, Status, Planungsdatum; Indizes Kunde/Status/Datum |
| `business_worklogs` | `id`; Einsatzkunde, Rechnungsempfänger, optionaler Auftrag, Arbeitsdatum, Billed-Status; Indizes Kunde/Datum und Auftrag |
| `business_invoices` | `id`; Kunde/Rechnungsempfänger, Belegnummer, Status, Ausstellungsdatum; Indizes Kunde/Datum/Status; keine neue Nummernvergabe |
| `business_photos` | `id`; Kunde/Auftrag/Arbeit, bestehende Original-/Thumbnailreferenz und Inhaltshash; Indizes für diese Beziehungen |

Jede Zeile erhält zusätzlich den verlustfrei erhaltenen einzelnen `record_json`
und dessen nach bestehender Kanonisierung berechneten `record_hash`. Der JSON-Wert
enthält nur einen Datensatz, keine komplette Sammlung; gezieltes Lesen/Schreiben
über ID und Indizes ersetzt den Gesamtsnapshot. Die oben genannten Suchspalten
sind aus genau diesem Datensatz abgeleitet; Index und JSON werden atomar geändert.
Unbekannte optionale Felder, Dezimaldarstellungen, leere Werte und Original-IDs
bleiben erhalten. Keine geratenen Fremdschlüssel oder automatische Datenreparatur.
Beziehungsregeln bleiben in den gemeinsamen UI-/MCP-Fachpfaden unverändert.

Bestehende Tabellen für Arbeitssitzungen, Medien, Belege und andere Facharten
bleiben bestehen. `owner_state` behält Einstellungen, Entwürfe, Request-/Commit-
Belege, historische Journale/Queue und sonstige geschützte Werte. Keine zweite
operative Datenbank, parallele Oberfläche oder zusätzliche Laufzeitskripte.
Keine automatische Übernahme in `onUpgrade`, beim Appstart oder beim Restore.

**Bewusste Übernahme:**

1. Zuerst synthetisch alle Lese-/Schreib-/Suchpfade der fünf Arten erfassen und
   dieselben Fachregeln und Hashfunktionen an den Tabellenadapter binden. Noch
   kein produktiver Tabellenleser/Schreiber aktiv. Vollständige Tests vor Gerätepfad.
2. Vor Geräteübernahme bewusst vollständiges verschlüsseltes Backup anlegen und
   extern vollständig zurücklesen, Hash/Umfang bestätigen. Dies erledigt der Nutzer;
   Codex ruft dafür weder Betriebsbestand noch Sicherungsinhalt ab. Ungeklärte
   Originale oder Integrität blockieren die Übernahme, keine Gatefreischaltung.
3. Genau den nach bisherigen Prioritätsregeln aktiven `owner_state/snapshot` samt
   aktivem Pending-/Projektionszustand erfassen; nicht mit einer älteren Preference-
   oder Drivekopie vermischen. Originalbytes, kanonische Sammlungs-/Datensatzhashes,
   Anzahl, fachliche Summen, Beziehungen und Medien-/Belegmanifest binden.
4. Während der bewusst gestarteten Übernahme Fachschreiben kurz sperren. In derselben
   DB leere Zieltabelle/Staginggeneration füllen; einen versionierten Fortschritts-
   und Prüfnachweis führen. Alte Snapshotbytes und alle bestehenden Belege bleiben
   unverändert. Bis zur Umschaltung ist ausschließlich der alte Leser autoritativ.
5. Alle Zeilen zurücklesen und gegen denselben eingefrorenen Ausgangsstand prüfen:
   gleiche IDs/Anzahlen, kanonische Hashes, exakte Dezimalsummen, Referenzen und
   Originaldatei-Hashes. Keine Floattoleranz, keine stillschweigende Bereinigung.
6. Nur nach vollständigem PASS und bewusster Bestätigung den Leser/Schreiber durch
   einen atomaren Format-/Generationszeiger in einer Transaktion umschalten. Keine
   Dual-Writes. Rechnungs-, Entwurfs-, Commit-ID- und Restoregates unverändert.
   Alte Snapshotgeneration bis zur bestätigten Geräteprüfung weiter erhalten.

**Abbruch, Neustart und Rückweg:**

- Vor Umschaltung bleibt der alte Stand aktiv. Unvollständige Stagingdaten werden
  beim Neustart erkannt, aber weder automatisch aktiviert noch blind entfernt.
  Fortsetzung nur bewusst, bei identischem Ausgangshash und gültigem Backupnachweis.
- Direkt nach Umschaltung ohne neue Fachschreibvorgänge ist ein bewusster atomarer
  Rückwechsel auf den unveränderten alten Snapshot nach Hashprüfung möglich.
- Nach neuen Tabellenänderungen niemals einfach auf den veralteten Snapshot zeigen:
  unter Schreibsperre einen aktuellen Snapshot aus allen Tabellen und den unverändert
  erhaltenen übrigen Sammlungen rekonstruieren, identische Hashes/Summen zurücklesen,
  erst dann bewusst umschalten. Alte Originalbytes getrennt erhalten. Ein Restore
  eines älteren Backups ist kein verlustfreier Rückweg für neuere Geschäftsvorgänge.
- Vollrestore bleibt bewusst, getrennt und fail-closed. Format und Generation werden
  vor Aktivierung geprüft; keine Mischung alter/neuer Stände, kein Reaktivieren
  verworfener Entwürfe, alter Queue-Schreiber oder Rechnungsautorität.

**Abnahme vor produktiver Übernahme:** synthetische Profile einschließlich
300/5000/3000/500; unbekannte Felder, optionale/ungültige Referenzen, Entwürfe und
bekannte/unklare Commit-IDs. Fehler nach jeder Kopiergrenze, Speicherfehler,
Prozessabbruch vor/in/nach Umschalttransaktion, erneuter Start, Wiederholung mit
identischer ID, falscher Ausgangshash, fehlendes/verändertes Original, vollständiger
Restore und beide Rückwege. Vorher/nachher identische Fachprüfsummen und Summen;
anschließend dieselben Leistungsmessungen wie in Etappe1. Reale Geräteabnahme und
Backup/Restore-Bestätigung bleiben beim Nutzer. Bis dahin keine Umsetzung der
Etappe3 und keine Entfernung historischer Belege oder Queue-Reste aus Etappe5.

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

## Nutzerpräzisierung28.10.: ADB jetzt ausführen

Direkte Nutzeranweisung: „Test per adb auszuführen“. Damit ist der Laptop für
die notwendige ADB-Verbindung und technische Protokolle ausdrücklich erlaubt;
Quelländerungen/Builds bleiben in GitHub/Cloud. Die anschließende Gerätefrage
wurde mit „Produktive Installation mit echten Daten“ beantwortet. Diese
Präzisierung erlaubt keine synthetischen Schreibversuche auf dem produktiven
Pixel, keinen Reset, Appstart/-abbruch, Installation, Restore oder echten Versand.

Zulässige Ausführung: tatsächlich verbundenes Gerät ausdrücklich über adb -s
adressieren, Geräte-/Appmetadaten und APK-Hash sowie vorhandene gfxinfo/meminfo/
procstats-/CPU-/Exit-Aggregate rein lesend erfassen. Seriennummer nur flüchtig
zur Auswahl, keine privaten Kennungen, Rohdumps oder Geschäftsdaten sichern.
Vorhandene Grafik-/Exitstatistik nicht als neu ausgeführten Last- oder Crashtest
ausgeben. Die64 synthetischen Fachszenarien bleiben ohne isolierte Testinstallation
offen. Ausführbare reine Diagnoseprotokolle und bereinigte Ergebnisse in diesem
Repository sichern; Produktivbestand unverändert erhalten.


## Paralleler Prüfauftrag vom 28.10.2026: Testkonzept und Funktionstestbericht

Direkter Nutzerauftrag: „Erstelle Konzept um app vollumfänglich zu testen.
Erstelle nach vollem Funktionstest einen vollständigen Funktionstest Bericht.“
Die erste Anweisung lautete, die Haus und Gartenapp über MCP zu testen.

Umfang: alle aktuellen 169 MCP-Funktionen, 212 semantischen UI-Aktionen,
15 Fachgruppen, bestehende RS-01–RS-16/P-01–P-06 und vollständige Fachketten.
Testfälle mit positivem Ablauf, Validierung, alten Hashes, identischer Request-ID,
Antwortverlust, Offlinebetrieb, Persistenz und unabhängigem Read-back planen.
Nur saubere Quellen, leeren Bootstrap und synthetische Fixtures verwenden.
Produktive Testdatensätze, automatische Migration, Installation und Restore
bleiben nach den vorhandenen Regeln ausgeschlossen.

Ausführung am unveränderten Originalquellcommit 9415c8d für 1.55.3/108:
Host-/Browserergebnisse und konkrete Fehler stehen im aktuellen CODEX-STATUS
sowie `docs/verification/function-test-2026-10-28.json`. Vollständige Abnahme erst
nach isolierter nativer Geräte-/MCP-Ausführung, Fehlernachtests und echten
externen Sicherungs-/Restorebelegen. Offene Pflichtfälle sind keine PASS-Fälle.
Bestehendes Nutzerdossier weiterführen; keine zweite App oder operative Datenbank
anlegen. Kein APK-Bau wegen dieses Testberichts.

## Neuester Auftrag28.10.: reproduzierbare ADB-Testkampagne

Direkter Nutzerauftrag: Funktionsfähigkeit, Performance, Datenbeständigkeit,
Bedienbarkeit und Barrierefreiheit auf einer nachgewiesen isolierten Android-
Testumgebung prüfen; ein Build oder eine Codeprüfung ist keine Geräteabnahme.
Zuerst frisches main und bestehende Branches prüfen, fremde Arbeit erhalten.
Falls die geltende Laptop-Sperre entgegensteht, ausschließlich zulässige
Vorbereitungen ausführen. Produktive Daten, Installation/Reset des produktiven
Pixels, Migration, Rechnungsfreigabe und echter Versand bleiben ausgeschlossen.

Kampagnenumfang: Kunden/Objekte/Rechnungsempfänger; Anfrage/Angebot/Entscheidung;
Aufträge/Arbeiten/Timer/Pausen; Pflegepläne/Besuche/Termine/Tour; Material/Bestand/
Geräte/Wartung; Entwürfe/Zahlungen/geschützte Belege; Fotos/Originale; Suche/
große Listen; Einstellungen/Rechte; Archiv/Papierkorb/Restore/Purge; verschlüsseltes
Backup/Vollrestore; Offline- und Wiederanlaufpfade. Tatsächliche MCP-/UI-Kataloge
neu zählen. Jeder Fall erhält Vorbedingungen, synthetische Daten, Schritte,
Sollzustand, Messwerte, Ergebnisstatus und Beleg.

Vorhandene synthetische Factory verwenden und deterministische verknüpfte Profile
50/500, 500/5000, 2000/20000 Kunden/Arbeiten nach Ressourcenprüfung vorbereiten;
Umlaute, Duplikate, Langtexte, optionale Leerfelder, abweichende Rechnungsempfänger,
Datumsgrenzen, Medien und archivierte/gelöschte Fälle einschließen. Kein
Produktivmaterial übernehmen oder leeren Bootstrap als Testseed ersetzen.

Releasevergleich mit Macrobenchmark/FrameTimingMetric, passenden WebView-Markern,
Perfetto-Ursachenläufen und gezielten gfxinfo/meminfo/procstats-Messungen:
Start, Navigation, sichtbare Suche/Akten, Commit plus Readback, Scrollen,
Foto/Original, Kalender/Tour, PDF und synthetisches Backup/Restore. Warmups getrennt;
mindestens30 Messwiederholungen anstreben, Median/p90/p95/Maximum/Fehlerquote sowie
Gerät, Profil, Hz, Temperatur, Ladung und Hintergrundlast erfassen. Bestehende
Ziele erhalten; neue Ziele nur als Vorschlag. Keine Emulatorwerte als Pixelwerte.

Robustheit: Netz/Timeoutadapter, einzelne Rechte, Hintergrund/Rotation, echter
OS-Prozessabbruch, Mehrfachtap, stabile Request-ID, verlorene/verspätete Antwort,
injizierte Testuhr und begrenzte Speichersimulation. Bestätigte Commits, Draft-
Verwerfen, Timer-/Medienbeziehungen, stillgelegte Drive-Schreiber und alle
Autoritäts-/Finanzgates regressieren. Nutzertestkarte für Kunde→Arbeit→Foto→Termin→
Rechnungsentwurf vorbereiten; Ergebnisse nur mit echten Teilnehmern.

Auslieferung: vorhandene ausführbare Tests mit dokumentiertem Einstieg,
versionierte Matrix, JSON/CSV, priorisierte Ursachen-/Verbesserungsliste,
offene Prüfungen und ggf. tatsächlich gemessener Vorher-/Nachhervergleich.
Nur eigene bereinigte Evidenz nach docs/verification, große Traces nur über
vorhandenen genehmigten Artefaktweg. Auftrag/Status fortschreiben, vor Sicherung
Privacy prüfen, gezielt committen und exakten Remote-Commit/Inhalt zurücklesen.

Historischer Vorbereitungsumfang vor der obigen direkten ADB-Präzisierung:
nur Remote-Vorbereitung über
GitHub, keine lokale Quellkopie oder ADB-Ausführung. Der separate Revierbranch
enthält eine noch nicht auf main/Appbranch integrierte ADB-Ausnahme; keine
stillschweigende Auswahl der weniger restriktiven Regel. Einstieg und Grenzen:
[ADB-Testkampagne](tests/ADB-Testkampagne.md).

## Android Fachwissen für die Stabilisierung vom 28. Oktober 2026

### Auftrag und Ergebnis der Recherche

Michael möchte das relevante Android-Programmierwissen für seine bereits betriebene App bündeln und Codex damit bei der Beseitigung ihrer Kinderkrankheiten unterstützen. Dieser Abschnitt ist die technische Wissens- und Prüfgrundlage im vorhandenen Auftrag. Maßgeblich bleiben die bestehenden Projektregeln und Fachverträge; öffentliche Dokumentation liefert technische Informationen und erteilt keine zusätzlichen Freigaben.

Die Recherche und gezielte Quellprüfung ergeben als wichtigste nächste Qualitätsaufgabe eine durchgängige native Abnahme des aktuellen Kandidaten. Host-, Java-, Python- und Browserprüfungen sind bereits vorhanden. Für Start, Wechselzeiten, Kamera/Galerie, native Vorschau, Updateerhalt und geräteseitiges Backup/Restore bleiben Nachweise offen. Die konkrete Ursache eines gemeldeten Gerätefehlers ist damit noch nicht bestimmt. Der Rechercheauftrag startet keine pausierten Emulator-/ADB-/Leistungsläufe, baut keine APK und verändert keine Betriebsdaten.

### Geprüfte Ausgangsbasis und vorhandene Vorarbeit

Recherchebasis ist der am 28.10.2026 gelesene Arbeitsbranch `codex/heute-uebersicht-20261027` bei `71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729`. Vor späterer Umsetzung den Remote-Stand erneut bestimmen und parallele Arbeit erhalten.

| Gegenstand | Belegter Stand und Bedeutung |
| --- | --- |
| App-Aufbau | Java/Android-WebView mit lokalen HTML-/CSS-/JavaScript-Assets; native SQLite-/Owner-Fachpfade; Python für vorhandene Host-/Werkzeugkomponenten. Kotlin, Compose, Flutter und Room sind keine Grundlage dieses Reparaturauftrags. |
| Neuester auf dem Arbeitsbranch dokumentierter APK-Kandidat | 1.55.3 / 108; Originalquelltag `source/v1.55.3-build108`, Quellcommit `9415c8da6c26314969b731bc01708c5e8f424349`. Dokumentierter Build-/Signiernachweis und tatsächliche Pixelinstallation bleiben getrennt. |
| Letzte gelesene Installationsdokumentation | 1.55.1 / 106 mit 166 verfügbaren MCP-Werkzeugen; im neueren Quellstand 169. Heute wurde keine Runtime abgefragt. Den früheren 117er-Connectorbefund nicht wieder als aktuellen Fehler ausgeben. |
| `main` | `05eac2d5769bed55a42317ed542be9baccda0e77`; enthält einen älteren Entwicklungsstand. Sein 106-Status ist nicht der neueste App-Kandidat. |
| Vorhandene pausierte native Arbeit | `codex/revier-solo-20261023`, gelesen bei `371384fe5aa49d055cb558c645f82226761fecc8`, Entwurf #14. Enthält bereits Kernservice-/Paketmanager-Prüfungen. Erst abgleichen, keine konkurrierende Neuimplementierung oder automatische Integration. |
| Buildvertrag | AGP 9.1.1, Gradle 9.3.1 und JDK 17 im vorhandenen Workflow; minSdk 26, compileSdk/targetSdk 37, Build-Tools 37.0.0. Die offizielle AGP-Matrix unterstützt API 37.0 und nennt Gradle 9.3.1/JDK 17. [A01](https://developer.android.com/build/releases/agp-9-1-0-release-notes)[A02](https://developer.android.com/build/jdks) |
| Varianten | Paket `at.hausgarten.service`; Instrumentierung gegen `release`, Debugvarianten deaktiviert, `minifyEnabled false`. R8 ist damit keine belegte Ursache der aktuellen Beschwerden. |
| Buildaufruf | Der vorhandene Workflow richtet Gradle ausdrücklich ein. In den gelesenen Root-/Android-Verzeichnissen fehlt ein Wrapper; das macht den dokumentierten Buildweg nicht ungültig. Generische Anleitungen mit `./gradlew assembleDebug` passen hier nicht. |
| Automatische Prüfungen | `.github/workflows/qa-apk.yml` wird manuell über `workflow_dispatch` gestartet. Es prüft Host-/Browser-/Java-Code und einen unsignierten Releasekandidaten; es führt selbst keinen nativen Android-Test aus. |

Quellbelege: [App-Gradle](https://github.com/mihalhafner-collab/Gartenpirat-/blob/71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729/android/app/build.gradle), [AGP](https://github.com/mihalhafner-collab/Gartenpirat-/blob/71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729/android/build.gradle), [Workflow](https://github.com/mihalhafner-collab/Gartenpirat-/blob/71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729/.github/workflows/qa-apk.yml), [damals aktueller Status](https://github.com/mihalhafner-collab/Gartenpirat-/blob/71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729/CODEX-STATUS.md), [MainActivity](https://github.com/mihalhafner-collab/Gartenpirat-/blob/71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729/android/app/src/main/java/at/hausgarten/service/MainActivity.java), [native Instrumentierung](https://github.com/mihalhafner-collab/Gartenpirat-/blob/71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729/android/app/src/androidTest/java/at/hausgarten/service/PixelMcpInstrumentation.java), [vorbereiteter Kernservice-Test](https://github.com/mihalhafner-collab/Gartenpirat-/blob/371384fe5aa49d055cb558c645f82226761fecc8/tests/run_cloud_emulator_tests.py).

### Wissen passend zu Java WebView und SQLite

Die folgenden Prüfungen sind technische Ableitungen für Gartenpirat. Ein Prüfpunkt ist erst dann ein bestätigter Appfehler, wenn der konkrete Codepfad oder eine reproduzierbare Ausführung ihn belegt.

**1. JavaScript-Brücke und Threads.** `addJavascriptInterface` führt die Java-Methode auf einem Hintergrundthread aus, blockiert den aufrufenden JavaScript-Ablauf aber synchron bis zur Rückkehr. Ein langer SQLite-/Dateiaufruf kann deshalb die Weboberfläche aufhalten, obwohl er nicht unmittelbar auf dem Android-Hauptthread läuft. `evaluateJavascript` und sein Callback gehören zum UI-Thread. [A03](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge)[A04](https://developer.android.com/reference/android/webkit/WebView) Den tatsächlichen Weg JS → Bridge → Fachservice → SQLite → Rückruf → Darstellung verfolgen; Laufzeit und Sperren je Übergang messen. Gegenseitiges synchrones Warten zwischen Bridge, UI und JavaScript gezielt untersuchen. Einen gefundenen langsamen Pfad an seiner Ursache korrigieren; Umstellung aller Brücken auf ein neues Verfahren wäre ein eigener Architekturentscheid.

**2. Lebenszyklus und verspätete Antworten.** Activity-Neuerstellung, Hintergrund/Vordergrund, kompletter App-Prozessverlust und WebView-Rendererverlust sind unterschiedliche Fälle. Dauerhafte Geschäftswerte gehören in den lokalen Fachspeicher; kleine UI-Zustände brauchen eine bewusste Wiederherstellung. Gespeicherter UI-Zustand ersetzt keine Datenbank. [A05](https://developer.android.com/topic/libraries/architecture/saving-states) Antworten an ursprüngliche Aktionskennung, Editiersitzung und gültige Bildschirmgeneration binden. Vor einem verzögerten UI-Aufruf Gültigkeit erneut prüfen; bereits bestätigte Commits bei anschließendem Darstellungsfehler erhalten.

Im gelesenen `MainActivity.java` existiert kein `onRenderProcessGone`-Handler; der allgemeine `callback()`-Helfer enthält selbst keinen sichtbaren Instanz-/Generationscheck. Das sind konkrete Untersuchungsstellen, noch keine Diagnose der Nutzerbeschwerden. Andere Pfade besitzen bereits Seiten-/Sitzungsprüfungen. Nach Rendererabbruch darf die beschädigte WebView nicht weiterverwendet werden; Wiederaufbau und begrenzte Fehlerbehandlung müssen auch laufende Entwürfe berücksichtigen. [A06](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination)

**3. Start und Wechselzeiten.** Kalt-, Warm- und Heißstart getrennt messen. Android unterscheidet das erste sichtbare Bild (TTID) und die vollständig nutzbare Ansicht (TTFD). [A07](https://developer.android.com/topic/performance/issues/launch-time) Für Gartenpirat zusätzlich tatsächliche Bridge-Bereitschaft, erste erfolgreiche lokale Anzeige und Tipp-zu-nutzbarer-Zielansicht erfassen. Gleicher Quell-/APK-Stand, WebView-Provider, synthetischer Bestand und vergleichbarer Gerätezustand sind Voraussetzung für Vorher/Nachher. Einzelwerte und ausreichend wiederholte Messungen dokumentieren; Median und Streuung sind aussagekräftiger als ein bester Lauf. Die gemeldete ungefähr einsekündige Wechselzeit ist mit Cloud-Browserzeiten nicht als behoben nachgewiesen.

Feste Startverzögerungen, mehrfaches Initialisieren, synchrone Großabfragen, JSON-Verarbeitung, DOM-Neuaufbau und Bilddekodierung sind prüfbare Ursachen. Bereits umgesetzte Start-/Navigationsänderungen erhalten. Ein Startbild darf einen langsameren nutzbaren Zustand nicht als Leistungsgewinn verdecken. Bei einem ANR den betroffenen Thread, blockierende Ein-/Ausgabe und Sperren untersuchen; ein Screenshot allein erklärt die Ursache nicht. [A08](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)

**4. SQLite, atomare Speicherung und Wiederholungen.** Zusammengehörige fachliche Änderungen und ihre Quittung brauchen den bestehenden gemeinsamen Transaktionspfad. Das Android-Muster markiert eine Transaktion erfolgreich und beendet sie anschließend; nach `setTransactionSuccessful()` soll keine weitere Datenbankarbeit erfolgen. [A09](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase) Ein UI-Erfolg muss zum tatsächlich abgeschlossenen Commit passen. Synthetisch zwischen Teilschritten scheitern lassen, verlorene Antwort und Wiederholung derselben Aktionskennung prüfen. Gleiche Kennung mit anderen Argumenten muss entsprechend dem bestehenden Vertrag abgewiesen werden. Ein deaktivierter Speicherknopf allein schützt nicht gegen doppelte Verarbeitung.

**5. Entwürfe, Verwerfen und konkurrierende Änderungen.** Bestehende Unterscheidung unberührt → Entwurf → gespeicherter Geschäftssatz durch alle UI-/MCP-Adapter erhalten. Verwerfen muss die tatsächliche Editiersitzung betreffen; späte Foto- oder Speicherantworten dürfen keine andere geöffnete Maske überschreiben. Lösch-/Verwerfbelege und vorhandene Versionsprüfungen auch nach Reload, Wiederholung und Restore beachten. Ein Anzeige-Cache darf keine eigene Schreibautorität werden. Fachliches Archivieren, Löschen und Verwerfen behalten ihre bereits festgelegte Bedeutung. Dies sind vorhandene Gartenpirat-Fachverträge, keine aus einer Android-Bibliothek abgeleiteten neuen Geschäftsregeln.

**6. Updates und Migrationen.** `SQLiteOpenHelper.onUpgrade` läuft in einer Transaktion. Ein schon ausgelieferter und ausgeführter Migrationsschritt wird durch nachträgliches Bearbeiten nicht erneut angewendet; eine notwendige Korrektur braucht einen neuen Schritt. [A10](https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper) Bei unverändertem DB-Schema trotzdem neue Datensatzformate prüfen: Gerätewartung und Gerätefotos verwenden laut aktuellem Auftrag Format 2 beziehungsweise 3 bei weiterhin Schema 6. Alte Leser dürfen unbekannte Formate nicht als vollständig geprüft restaurieren. Rein synthetischen Altbestand aus dem jeweiligen Schema aufbauen, Update und Neustart prüfen, IDs, Beziehungen, Werte, Entwürfe und Medienreferenzen vergleichen. Deinstallation, Datenlöschung und Neuanlage sind kein Update-Nachweis. Paketkennung, Signatur und Versionskennung müssen zur vorhandenen Installation passen. [A11](https://developer.android.com/google/play/app-updates) Originalsignierung ausschließlich über den bestehenden geschützten Cloudweg; keinen Ersatzschlüssel einführen.

**7. Kamera, Galerie, Dateien und Bilder.** Dokumentauswahl liefert einen URI samt konkreten Zugriffsrechten; ein URI ist kein gewöhnlicher Dateipfad. Persistierbare Rechte nur übernehmen, wenn der verwendete Auswahlweg sie tatsächlich gewährt. Auch dann können Dateien verschwinden oder verschoben werden. [A12](https://developer.android.com/training/data-storage/shared/documents-files) Photo Picker beschränkt Freigaben auf ausgewählte Medien. [A13](https://developer.android.com/training/data-storage/shared/photo-picker) Den tatsächlich vorhandenen Android-Auswahl-/Providerpfad verwenden und API-Verfügbarkeit gegen minSdk 26 prüfen. Abbruch, Rechteentzug, große/beschädigte Datei, falscher Typ, verspätete Auswahl und Austausch des Fotos synthetisch testen. Vorschau begrenzen, Streams schließen, Original und Vorschaubild unterscheiden. Eine spätere Bildstörung erzeugt kein zweites Gerät und hebt keinen bereits bestätigten Geschäftssatz auf.

**8. Berechtigungen und Hintergrundbetrieb.** Standort, Benachrichtigungen und externe Auswahl können verweigert oder später entzogen werden. Die betroffene Funktion soll verständlich reagieren; andere lokale Arbeit bleibt möglich. [A14](https://developer.android.com/training/permissions/requesting) Vorhandene `JobService`-/Foreground-Service-Pfade anhand ihrer wirklichen Aufgabe und Android-Zielversion prüfen. Hintergrundarbeit ist nicht automatisch sofortige Arbeit; Planung und fachliche Einmalverarbeitung sind getrennte Probleme. [A15](https://developer.android.com/develop/background-work/background-tasks) Keine pauschale Batterieausnahme, kein pauschales WorkManager-Refactoring. Standortfehler dürfen insbesondere keinen geratenen Einsatzkunden oder automatischen Arbeitsbeginn erzeugen.

**9. Lokale Autorität, Backup und externe Dienste.** Pixel/App bleibt die operative Autorität; Drive ist das vorhandene verschlüsselte Backupziel. Die generische Android-Empfehlung für Offline-Synchronisierung darf hier keine stillgelegten Drive-Geschäftsschreiber reaktivieren. Methodennamen wie `requestAutomaticSync` beweisen keinen aktiven Geschäftssync: den aufgerufenen Pfad lesen. Backup fehlend, pausiert oder fehlgeschlagen muss lokale Erfassung ermöglichen. Lokales Archiv, Upload und externer Inhaltsnachweis getrennt ausweisen. Vollrestore, private Originale und produktive Integritätsklärung bleiben eigenständige geschützte Abläufe. Entwicklung verwendet ausschließlich Quellcode, leeren Bootstrap und synthetische Testdaten.

**10. MCP und UI-Gleichheit.** Katalog der jeweiligen Appversion, tatsächlich gebundene Verbindung und Quellvertrag unterscheiden. MCP-Discovery mit Version, vollständiger Pagination, Namen und Schemas prüfen; protokollspezifische Änderungsbenachrichtigungen nur verwenden, wenn unterstützt. [A16](https://modelcontextprotocol.io/specification/2025-11-25/server/tools) Die interne App-Protokollnummer 9 ist nicht automatisch eine MCP-Spezifikationsrevision. Gleiche synthetische Fachaktion über UI und MCP muss denselben lokalen Endzustand liefern. Discovery beweist Erreichbarkeit eines Werkzeugs, noch nicht seine vollständige fachliche Korrektheit. Den inzwischen abgeglichenen 166er-Katalog der dokumentierten Installation106 nicht mit den 169 Werkzeugen der neueren APK-Quelle verwechseln.

**11. WebView-Vertrauensgrenze.** Eine `addJavascriptInterface`-Brücke ist grundsätzlich in allen Frames erreichbar; `getUrl()` eignet sich nicht als verlässlicher Herkunftsnachweis des aufrufenden Frames. [A03](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge)[A17](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges) Die vorhandenen Netz-/Dateisperren, externe Linkbehandlung und CSP erhalten; Eingaben als Daten serialisieren. Synthetische Anführungszeichen, Zeilenumbrüche, HTML-Inhalte, externe Navigation und Redirects prüfen. Android empfiehlt `WebViewAssetLoader` für lokale Inhalte. [A18](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content) Ein Wechsel vom bestehenden `file:///android_asset/`-Ursprung muss origin-gebundene Webspeicher und alte Entwürfe berücksichtigen; nicht als beiläufige Fehlerkorrektur durchführen.

**12. Versionskompatibilität und Fachwerte.** Neue API-Beispiele nur nach Prüfung von minSdk, targetSdk, WebView-Provider und vorhandenen Bibliotheken verwenden. Android-17-Verhaltensänderungen gelten teilweise für alle Apps, teilweise erst ab entsprechendem targetSdk. [A19](https://developer.android.com/about/versions/17/behavior-changes-17) Build-JDK, Java-Sprachversion und tatsächlich auf dem Gerät verfügbare Java-APIs auseinanderhalten. [A02](https://developer.android.com/build/jdks) Relevante Fachgegenproben bleiben: Europe/Vienna und Sommerzeitwechsel, Datum ohne erfundene Uhrzeit, Dezimalkomma, exakte Geld-/Rundungsregeln, Kunde versus Einsatzobjekt versus Rechnungsempfänger. Bestehende Berechnungs- und Finanzverträge liefern die Sollwerte; die Recherche erfindet keine neuen Preise, Zeiten oder Freigaben.

### Vorhandene Tests sinnvoll einsetzen

Android unterscheidet Tests auf dem Host von Instrumentierung auf Android-Gerät oder Emulator. [A20](https://developer.android.com/training/testing/fundamentals) Für hybride Apps prüft Espresso-Web die Verbindung zwischen WebView und nativen Komponenten. [A21](https://developer.android.com/training/testing/espresso/web) Gartenpirat besitzt bereits eigene native Instrumentierung; zuerst deren Abdeckung nutzen und nur bei einer konkreten Lücke zusätzliche Testabhängigkeiten erwägen.

| Prüfebene | Was sie beweist | Was zusätzlich erforderlich bleibt |
| --- | --- | --- |
| Python/Java-Fachtests | Fachlogik, Fehlerbehandlung und Verträge im ausgeführten Testaufbau | Android-Runtime, echte Plattform-APIs und sichtbare Bedienung |
| Browser mit synthetischer Bridge | DOM, Formulare, Navigation und JS-Reaktionen | Tatsächliche Android-Brücke, SQLite-/Provider-Integration |
| Native Instrumentierung im leeren Cloud-AVD | Ausgeführte Android-/WebView-/SQLite-Pfade für genau diesen Kandidaten | Physisches Pixel, echte Kamera-/Galeriebedienung sowie ein separat freigegebener Geräte- und Update-Nachweis |
| Release-Build, Lint, Privacy und Signatur | Baubarkeit, statische Befunde, Paketinhalt und Identität im geprüften Umfang | Nutzungsabläufe, Prozess-/Rendererfälle, Updateinhalt und Laufzeitmessung |

Vorhandene Einstiegspunkte sind `tests/run_cloud_emulator_tests.py` und `android/app/src/androidTest/java/at/hausgarten/service/PixelMcpInstrumentation.java`. Unter anderem existieren `ui`, `backup_setup`, `drive_backup_only`, `revier_core`, `revier_ui`, `revier_authority`, `revier_finance`, `revier_recovery`, `prepare`/`verify` und Prozess-Prüfpaare. Namen und CLI-Verfügbarkeit vor einem späteren Lauf aus dem dann aktuellen Code lesen. Der vorhandene Prozesslauf benutzt `am force-stop`; das ist als Force-stop-Test auszuweisen und deckt nicht automatisch jeden Android-Prozessverlust ab.

**Wichtige konkrete Testgrenze:** Einige native Modi berechnen zusätzlich `businessSnapshotHash()` oder `reportProductionCounts()` des normalen Default-Stores. Deshalb auch diese Instrumentierung ausschließlich im garantiert leeren beziehungsweise synthetischen Cloud-AVD ausführen. Eine eigene synthetische Fixture allein beweist nicht, dass kein produktiver Store gelesen wird. Instrumentierung, vollständige Bugreports oder Trace-Uploads vom produktiven Pixel sind kein Standardmaterial dieses Auftrags.

Vor einem nativen Lauf müssen Emulatoridentität, Boot, PackageManager, ActivityManager, WebView-Provider, Installierbarkeit und tatsächlich ausgeführte Tests nachgewiesen sein. `boot_completed=1` allein genügt nicht. Die passenden Kernservice-Prüfungen existieren bereits im pausierten Revier-Branch. Ein Emulatorausfall ist zunächst ein Umgebungsfehler; Null Tests, kompilierte Tests und ein erfolgreicher Build ergeben keinen nativen PASS. Cloud-CI ist der passende Ausführungsort für automatisierte Prüfungen. [A22](https://developer.android.com/training/testing/continuous-integration) Der Windows-Laptop wird dafür nicht als Ersatz benötigt.

### Priorisierte Fälle für einen späteren Reparaturlauf

Alle Fälle beginnen mit leerem Bootstrap und ausdrücklich erfundenen Testwerten. Bereits vorhandene passende Regressionen wiederverwenden. Die Sollwerte ergeben sich aus den bestehenden Fachverträgen.

| Fall | Auslöser | Abnahme |
| --- | --- | --- |
| S01 Start und Navigation | Kaltstart, Wiederaufnahme, Heute/Kunden/Büro, langsame Bridge | Richtige erste nutzbare Ansicht; keine alte Zwischenoberfläche; Laufzeiten samt Umgebung belegt |
| S02 Speichern und Wiederholung | Doppeltes Tippen, Timeout nach Commit, erneuter Aufruf mit gleicher ID | Genau der vereinbarte Geschäftssatz; ursprüngliche Quittung; keine neue Aktion bei unklarer Antwort |
| S03 Entwurf und Verwerfen | Bearbeiten, Zurück, Verwerfen, Reload, spätes Fotoergebnis | Richtiger Entwurf bleibt oder wird nach Vertrag entfernt; kein heimlich angelegter Satz |
| S04 Lifecycle | Activity-Neuerstellung, App-Prozessende und Rendererende getrennt | Keine veralteten UI-Callbacks; bestätigte Daten bleiben; definierte Wiederaufnahme |
| S05 Foto | Auswahlabbruch, Rechteentzug, große/beschädigte Datei, Gerätewechsel | Richtige Zuordnung, begrenzte Vorschau, klare Fehler; kein doppelter Gerätesatz |
| S06 Offline und Backupausfall | Flugmodus, kein Backupkonto, unterbrochener Upload | Lokale Arbeit bleibt möglich; fachlicher Commit und Backupstatus getrennt; keine Drive-Geschäftsqueue |
| S07 Update und Datensatzformate | Unterstützte synthetische Altversion → aktueller Kandidat → Neustart | Schlüssel, Werte, Beziehungen und Medienreferenzen erhalten; unbekannte Formate kontrolliert abgewiesen |
| S08 MCP und UI | Gleiche Fachaktion über beide Adapter, vollständiger versionierter Katalog | Gleiches Ergebnis und gleiche Fachfehler; Versionsabweichungen richtig erklärt |
| S09 Bedienung | Tastatur, Zurück-Geste, große Schrift, schmaler Bildschirm, versteckte Pflichtfehler | Aktion erreichbar; richtige Rückkehr; verständliche und sichtbare Fehler |
| S10 Fachgrenzen | Monatsende, Sommerzeit, Dezimalkomma, allgemeine Arbeit ohne Auftrag | Bestehender Vertrag erfüllt; keine erfundenen Zeiten, Beziehungen oder Rechnungsfreigaben |

### Offizielle Android-Wissenswerkzeuge für Codex

Google veröffentlicht Android Skills nach dem Agent-Skills-Standard. Die Android CLI dokumentiert `codex` als Ziel und projektbezogene Installation. [A23](https://developer.android.com/tools/agents/android-skills)[A24](https://developer.android.com/tools/agents/android-cli/commands/skills_add) Für Gartenpirat passt zunächst die Prüfung von `testing-setup`; der Skill wird in Googles Testgrundlagen genannt. Ein R8-Skill ist erst bei tatsächlich relevanter Optimierung sinnvoll. Bestehende Minifizierung ist aus. Compose-/Room-/AGP-Migrationsskills sind kein pauschaler Reparaturauftrag.

Zusätzlich bietet `android docs` Zugriff auf die Android Knowledge Base: zuerst suchen, dann den tatsächlich gelieferten `kb://`-Verweis vollständig abrufen. [A25](https://developer.android.com/tools/agents/android-cli/commands/docs) Vor Nutzung feststellen, ob die aktuelle Cloud diese CLI bereits enthält und ob deren Zugriff funktioniert. Die folgenden Befehle sind dokumentierte Beispiele, hier nicht ausgeführt:

```sh
android --version
android docs search "WebView JavaScript bridge threading lifecycle"
android docs fetch '<kb://-Verweis aus dem tatsächlichen Suchergebnis>'
```

Eine mögliche, vorher inhaltlich zu prüfende projektbezogene Einrichtung lautet:

```sh
android skills add --agent=codex --project='<vorhandener Repositorypfad>' testing-setup
```

Skillinhalt, Skripte, Zielpfad und bestehende Projektregeln vor Installation vergleichen; keine globale Sammelinstallation mit `--all`. Bei Übernahme Quelle/Version im vorhandenen Repository nachvollziehbar halten. Die Recherche installiert nichts und ändert weder den veröffentlichten Start-Skill noch Rechte, Netzkonfiguration oder Signierwerte. Ist CLI-Zugriff nicht verfügbar, die hier verlinkten offiziellen Webseiten gezielt nutzen. Neue kostenpflichtige Dienste sind dafür nicht erforderlich.

OpenAI dokumentiert `AGENTS.md` als vorab geladene Projektanweisung. Die bestehenden Projektanweisungen verweisen bereits auf diese Auftragsdatei; diese Fachsektion gehört damit in den vorhandenen Leseweg. Eine zweite Regeldatei oder ein riesiges allgemeines Android-Handbuch in `AGENTS.md` ist dafür nicht erforderlich. [A26](https://learn.chatgpt.com/docs/agent-configuration/agents-md) Ein Chattext allein aktualisiert keine bestehende Cloud-Laufzeit. Im nächsten tatsächlichen Codex-Lauf den geladenen Branch, Commit und diesen Abschnitt nachweisen. Ein ungelesener Link gilt nicht als geladenes Wissen. Fehlt der GitHub-Zugriff, greift die bestehende Zugriffsgrenze; keine fremde Kopie als aktuellen Repository-Stand ausgeben. Veröffentlichung und Übernahme des bereits bestätigten Start-Skills nicht nochmals ohne Anlass anfordern.

Aktuelle Dienständerung: Firebase Test Lab ist laut offizieller Mitteilung vom 27.10.2026 abgekündigt, Abschaltung am 30.09.2027; Developer Device Platform ist Preview. [A27](https://firebase.google.com/docs/test-lab/migrate) Die vorhandene Cloud-Testarbeit weiterverwenden. Daraus entsteht kein Auftrag, einen neuen Testdienst oder Tarif einzurichten.

### Arbeitsmethode und Abschlussnachweis für Codex

1. **Kontext belegen:** aktuellen main und passende Arbeitsbranches vergleichen; bereits erledigte Änderungen, Originalquelltag und nur beobachtete Installation trennen. Bestehende pausierte Arbeit berücksichtigen.
2. **Fehler benennen:** konkreter Nutzerablauf, erwarteter Endzustand, reproduzierbare Schritte, Auswirkung und geeignete synthetische Gegenprobe. Datenverlust/Fehlbuchung vor Absturz/Hänger, danach zentrale Bedienfehler und Leistung priorisieren.
3. **Ursache belegen:** betroffene Datei/Funktion, Thread, Datenzustand oder Plattformbedingung zeigen. Vermutung ausdrücklich als Hypothese führen. Keine Reparatur allein aus einem fehlenden Methodennamen ableiten.
4. **Gezielt korrigieren:** kleinster fachlich vollständiger Eingriff. Betroffene Testfälle, erforderlichenfalls Fehlerprovokation und relevante Gegenpfade prüfen. Bestehende Schutzgrenzen und erfolgreiche Fachpfade erhalten.
5. **Nachweis binden:** Commit, Variante, Paketversion, APK-Hash, Android-/WebView-Version, Testfall, tatsächliche Anzahl, Ergebnis und passende Log-/Screenshotstelle in bestehender technischer Evidenz erfassen. Sensitive Werte ausschließen.
6. **Wahrheitsgemäß abschließen:** ausgeführte Hostprüfung, nativer Test, reale Pixelabnahme und externer Dienstnachweis getrennt melden. Blockierte, übersprungene und nicht ausgeführte Prüfungen bleiben offen. Die Zahl grüner Tests ersetzt keine passende Abdeckung.

Erwarteter Ergebnisblock je Fehler: **Symptom → reproduzierter Fall → bestätigte Ursache → geänderte Stellen → tatsächlich ausgeführte Prüfung → verbleibender Gerätenachweis**. Der abgeschlossene Rechercheumfang liefert diese Grundlage; App-Reparatur, Wiederaufnahme pausierter Laufzeittests und produktive Abnahme bleiben davon unterscheidbare Arbeitsschritte.

### Offizielle technische Quellen

Geöffnet und geprüft am 28.10.2026. Gezielt die zur Projektversion passende API verwenden; die Quellenliste ist eine Arbeitsauswahl für diesen Bestand und beansprucht keine Vollständigkeit sämtlicher Android-APIs.

- [A01 Android Gradle Plugin 9.1.1 und Kompatibilität](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [A02 Java-Versionen in Android-Builds](https://developer.android.com/build/jdks)
- [A03 Native JavaScript-Brücken](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge)
- [A04 WebView API einschließlich evaluateJavascript](https://developer.android.com/reference/android/webkit/WebView)
- [A05 UI-Zustände erhalten](https://developer.android.com/topic/libraries/architecture/saving-states)
- [A06 WebView-Rendererabbruch behandeln](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination)
- [A07 App-Startzeit und Nutzbarkeit](https://developer.android.com/topic/performance/issues/launch-time)
- [A08 ANRs diagnostizieren](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)
- [A09 SQLiteDatabase und Transaktionen](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase)
- [A10 SQLiteOpenHelper und Migrationen](https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper)
- [A11 Android-Updatebedingungen](https://developer.android.com/google/play/app-updates)
- [A12 Dokumente und URI-Rechte](https://developer.android.com/training/data-storage/shared/documents-files)
- [A13 Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)
- [A14 Laufzeitberechtigungen](https://developer.android.com/training/permissions/requesting)
- [A15 Hintergrundaufgaben auswählen](https://developer.android.com/develop/background-work/background-tasks)
- [A16 MCP-Werkzeugkatalog, Revision 2025-11-25 als versionsgebundene Referenz](https://modelcontextprotocol.io/specification/2025-11-25/server/tools)
- [A17 Sicherheit nativer WebView-Brücken](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)
- [A18 Lokale WebView-Inhalte laden](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)
- [A19 Android 17 Änderungen für targetSdk 37](https://developer.android.com/about/versions/17/behavior-changes-17)
- [A20 Android-Testgrundlagen und Testing Setup Skill](https://developer.android.com/training/testing/fundamentals)
- [A21 Espresso-Web für hybride Apps](https://developer.android.com/training/testing/espresso/web)
- [A22 Android Continuous Integration](https://developer.android.com/training/testing/continuous-integration)
- [A23 Android Skills Übersicht](https://developer.android.com/tools/agents/android-skills)
- [A24 Android Skills gezielt für Codex installieren](https://developer.android.com/tools/agents/android-cli/commands/skills_add)
- [A25 Android Knowledge Base über android docs](https://developer.android.com/tools/agents/android-cli/commands/docs)
- [A26 OpenAI Projektanweisungen mit AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [A27 Firebase Test Lab Migration](https://firebase.google.com/docs/test-lab/migrate)

## Neuester Auftrag: manuelle Quellcode-Übergabe an Claude

Nutzerauftrag vom28.10.2026: Drive-Ordner `Claude` erstellen und aktuellen
Quellcode sowie nötige Informationen zur KI-Übergabe bereitstellen, nachdem
interne Cloud-Dateilinks für den Nutzer nicht herunterladbar waren.
Einmaliger privater Snapshot aus exakt auf GitHub zurückgelesenem Commit:
vollständiges Quell-ZIP, kopierbarer Text, vorhandene zentrale Projektstände,
kurzer Wegweiser und für Claude handhabbare App-Code-Teile. Originalpfade,
Commitbezug und ausgelieferte APK108 unterscheiden. Keine neue Regelquelle;
Einstieg verweist auf AGENTS.md, AKTUELLER-STAND.json, diesen Auftrag und Status.
Keine Betriebsdaten, Schlüssel, .git-Historie, Buildausgaben oder erneuter APK-Bau.
Uploads anhand Namen, Ordnern, Größen und lesbaren Einstiegsdateien zurückprüfen.
GitHub bleibt Entwicklungszentrale; keine neue Drive-Exportautomatik einrichten.

## Neuester Auftrag: Gerätefoto, modernere Oberfläche und anschließend APK

Direkte Nutzersteuerung: Geräte-Profilfoto zum Wiedererkennen implementieren.
Beide früheren Vorschläge Revierbuch/Abendgrün sind wegen langweiligem Aussehen
verworfen. Modernere UI näher an heutigen Apps, kleinere sichtbare Buttons,
weniger Systemmeldungen; Aufbau und Funktionalität ausdrücklich erhalten.
Anschließend fertige original signierte APK erstellen, keine automatische Installation.
Quellcode/Betriebsdaten-Grenze, Finanz-/Restore-/Commitgates unverändert.

Endzustand und geprüfter minimaler Plan vor Umsetzung:

- Gerätefoto ist optional und gehört ausschließlich zum Gerät, nicht zu einem
  erfundenen Kunden oder Einsatz. Vorhandene Geräteanlage und Wartung bleiben.
  Aufnahme/Dateiauswahl über bestehende Android-Auswahl; ein kleiner Avatar in
  Liste und Gerätedetail, Foto hinzufügen/ersetzen in der vorhandenen Bearbeitung.
- Original und Vorschau im vorhandenen privaten PhotoAttachmentStore; Metadaten
  am vorhandenen Equipment-Ownersatz, Binärdaten nicht im JSON/Draftmirror.
  UI übernimmt unmittelbar den aktiven Browsermedia-Entwurf über save_equipment
  mit profile_photo_draft (Sitzung/Auswahl/Installation/Restore-Epoche), ohne zweite
  Uploadkopie. MCP kann alternativ den vorhandenen begrenzten Uploadpfad über
  profile_photo_upload_id verwenden. Beide Adapter führen in dieselbe lokale
  Foto-/Geräte-Transaktion mit CAS, Verbrauchsbeleg und ursprünglicher Quittung. Keine neue DB/Fotoautorität,
  keine Gerätefotos in Kundenfotolisten, kein Drive-Geschäftspfad.
- Native RevierRepository-Fotoport und rein lesender Snapshot-Port prüfen
  Metadaten/Originalhash. Vollbackup enthält bereits alle privaten Fotodateien und
  SQLite-Tabellen; erweiterte bestehende Beziehungsprüfung verifiziert Gerätefotos
  im tatsächlich erfassten/restaurierten Verzeichnis. Fehlendes Original wird
  nicht als vollständiges Backup/Restore akzeptiert; normale lokale Erfassung
  bleibt unabhängig. Geräteneudaten mit Foto erhalten gezielt Format3; alte
  Leser lehnen diese bereits ab. Wartungsänderungen dürfen Format3 nicht auf2
  zurücksetzen. Vorhandene Geräte ohne Foto bleiben unverändert lesbar.
- Fotoentwurf über vorhandenen nativen Browsermedienspeicher und HGFormDrafts,
  eindeutige Editiersitzung, Back behält, Verwerfen retiert Draft/Medien und
  verspätete Callback-Ergebnisse. Unberührt/abgebrochen legt kein Gerät an.
  Unklare Geräteantwort zuerst inspect_edit_action mit originalen Argumenten;
  bestätigte Anlage bei Anzeige-/Fotocleanupfehler nicht erneut schreiben.
  Historische Originale bleiben unveränderliche Evidenz, keine automatische
  destruktive Bildbereinigung oder Umdeutung vorhandener Löschpfade.
- Aktuelle Gestaltung in bestehenden Revier-/gemeinsamen Stilen modernisieren:
  klare serifenlose Typografie, kompaktere Zeilen, kühle helle Flächen und dunkle
  Akzente mit markantem Signalton. Drei Hauptpunkte und sämtliche Fachziele,
  Feldreihenfolge, Dialoge und Bestätigungen erhalten. Kleinere sichtbare Buttons
  mit weiter gut erreichbarer Touchfläche; keine Showanimationen/Frameworks.
- Nur eindeutig benannte normale Erfolgspopups beruhigen und knappe Rückmeldung
  direkt am Eintrag. Fehler, unklare Speicherung, Undo, offene Kalender-/Backup-
  Zustände und notwendige Android-Berechtigungs-/Bestätigungsdialoge behalten.
  Keine globalen Android-Benachrichtigungsrechte ändern.
- Kaskade: korrekte Geräte-ID, mehrere Geräte, Austausch, beschädigte/zu große
  Datei, Abbruch, CAS-Konflikt, verlorene Antwort, Retry/ID, Discard/Reload,
  verspätete Callbacks, Vorschaufehler, Format-/Neustart-/Restorepfade und
  UI/MCP-Gleichheit prüfen; bestehende Kundenfotos/Wartung/Formulare/Finanzgates
  regressieren. Rein synthetische Bilder. Bilder der tatsächlichen neuen
  Oberfläche, Cloud-Prüfungen und Pixelgrenzen getrennt dokumentieren.
- Vor APK: Privacy, gezielter Commit, Push, exakter GitHub-Read-back und
  source_preservation; neue Buildnummer über letzter APK107, bestehendes
  Originalzertifikat. Build/Lint/Paket-/Signatur-/Quellabgleich aus sauberem
  Commit; geprüfte APK ausschließlich im bestehenden Installationsbereich.
  Quellberichte nur GitHub. Bei Gates Fehler beheben statt Freigabeflags.

Rollback vor Auslieferung durch Quellrücknahme, keine operative Migration.
Nach gespeicherten Format3-Gerätefotos keinen alten Leser als vollständigen
Restoreprüfer ausgeben. Pixelkamera, physische Bedienung und Updatebestand
bleiben ohne tatsächlichen Gerätenachweis ausdrücklich offen.

## Neuester Auftrag: Kunden als dritter Hauptpunkt, Design und kurze Wechselwege

Nutzer meldet ungefähr1s zwischen Heute/Büro und beauftragt ausdrücklich einen
dritten Bottompunkt für eine übersichtliche Kundendatei. Navigation jetzt
Heute/Kunden/Büro; die frühere Vorgabe zweier Punkte ist damit für diese Quelle
überholt. Selbstständig praktische Informations-/Bedienarchitektur entwickeln,
Bedienung und Originalcode anderer App-Kategorien vergleichen, Zwischenstände
und Bildvorschläge liefern, danach alle Anforderungen mit Gegenpfaden prüfen.
Designarbeit/Quelländerungen sind keine weitere APK-/Installationsfreigabe.

Gewünschter Endzustand: vorhandene Kundenliste/Kundenakte direkt erreichbar,
Suche und Rückweg behalten ihren Zustand; keine neue Kundendatenhaltung oder
Fachfunktion. Kundenidentität, Einsatzobjekt und Rechnungsempfänger unterscheiden.
Nächste Arbeit/Kontakt vor technischen Details; weitere Arbeiten, Fotos und Belege
über vorhandene Fachziele. Leere/fehlende Angaben und mehrere Objekte bewusst
darstellen; nie Rechnungsadresse als Einsatzadresse voraussetzen.

Ursachen-/Änderungsplan vor Umsetzung:

- `revier-solo-ui.js/.css`: bestehende ROOTS/Nav auf3 erweitern; Kundenliste,
  Profil und Bearbeitung dem Kundenpunkt zuordnen. Aktiven Punkt, Historie,
  Zurück, Suchtext/Scrollzustand und Navigation aus Entwürfen prüfen. Mehr bleibt
  vorhandener Zugang zu weiteren Bereichen, keine neue Menühierarchie erzeugen.
- Heute nutzt die bereits vorhandene vollständige tour-Antwort innerhalb von
  get_revier_overview, statt dieselbe Tour ein zweites Mal synchron zu lesen.
  Fehlender alter Antwortteil darf den vorhandenen lokalen Read nutzen;
  widersprüchliche/vorhandene ungültige Daten müssen als Lesefehler sichtbar bleiben.
  Kein TTL-Cache, kein Verstecken oder zeitgesteuertes Freigeben alter Geschäftsdaten.
- Büro benötigt den vollständigen Revier-/Tour-Read nur für eine optionale
  Anfrageanzahl. Diesen unnötigen Read entfernen; Anfragen bleiben über ihren
  vorhandenen Listenpfad vollständig und frisch erreichbar. Formulare und
  Geschäfts-/Rechnungs-/Backupgates unverändert. Keine Abfrage durch Blind-Caching
  ersetzen und keine Geschäftsaktion auf Navigation auslösen.
- CPU-Profil zeigt außerdem wiederholtes Ersetzen identischer Select-Optionen.
  Kunden-/Auftragsoptionen nur bei verändertem vollständigem Optioneninhalt
  ersetzen; Schlüssel umfasst alle verwendeten IDs/Anzeigen/Status und Kunden-
  kontext. Das ist DOM-Wiederverwendung, kein Daten-/TTL-Cache: aktuelle Daten
  weiterhin bei jedem Render ableiten. Neue/geänderte Kunden, Kontextwechsel,
  Auswahl, Timer/Draft und unveränderte Knotenidentität gesondert testen.
- Gleiches synthetisches Vorher/Nachher-Profil für Seitenwechsel, Native-Read-
  Anzahl und sichtbare Darstellung. Cloud-Browserwerte sind keine Pixelmessung.
  Bei weiterem belegten Engpass gezielt Ursache untersuchen, kein allgemeines
  Render-/Native-Refactoring aus diesem Designauftrag ableiten.
- Bild-/Interaktionsstudie in `docs/verification/`: zwei optische Richtungen,
  empfohlen warmes klares Revierbuch mit Waldgrün und großen Bedienelementen.
  Tatsächliche HTML-Darstellung mit Beispieldaten, keine als Pixelaufnahme
  ausgegebenen Bilder. Kundenliste/Akte/Heute/Büro, schmaler Bildschirm, große
  Schrift, leere Suche, fehlender Kontakt, mehrere Objekte und Lesefehler prüfen.
  Entwürfe sind keine zweite produktive App; ausschließlich synthetische Daten.

Originalquellen als Recherchematerial festhalten, Mechanismus/Übertragbarkeit
von ungetesteten Fremd-App-Geschwindigkeitsbehauptungen trennen. Kein Fremdcode
oder Frameworkimport. Abschließende Kaskade: Pflichtpunkte einzeln auf Evidenz
abbilden; Navigation/Profil/Formulare/Drafts/Timer/Commit-ID/Finanz-/Löschschutz
und Katalog/Privacy gegenprüfen. Nur bestehende zentrale Projektstände ändern,
Nachweise und Bilder in GitHub sichern. Rücknahme betrifft Präsentation und
redundante Reads; keine Geschäfts-/Backupdaten oder ausgelieferte107 verändern.

## Neuester Auftrag: Original-APK1.55.2/107 erstellt

Ergebnis: Original-APK1.55.2/107 gebaut, signiert, lokal vollständig geprüft und
im bestehenden privaten APK-Bereich bereitgestellt. Drive-Größenreadback PASS;
unabhängiger Remote-SHA wegen Cloud-Proxy403 ausdrücklich offen. Keine Installation
oder produktive Freigabe. Nachweise im vorhandenen Status und Releasebeleg.

Nutzer beauftragt ausdrücklich den APK-Bau des aktuellen geprüften Quellstands.
Da die installierte App inzwischen106 meldet, nächste Version1.55.2/107 verwenden;
Originalpaket/Originalzertifikat und leeren Bootstrap behalten. Keine zusätzlichen
Fachfunktionen, keine Installation, produktive Migration oder Rechnungsfreigabe.

Vor Bau: aktuellen Stand gezielt sichern und exakt von GitHub zurücklesen,
source_preservation PASS, Version über beobachteter106, Quell-/Worktree-Privacy.
Bestehende Build-/Lint-/Vertragsgates ausführen; Originalsignierung ausschließlich
über den vorhandenen geschützten Cloud-Befehl. Fertiges Paket erneut auf Signatur,
Identität, Version, 16-KiB-Ausrichtung, Quellassets/Fingerprint und Privacy prüfen.
Vorherige37 Browser-Prüfgruppen und Fachprüfungen eindeutig vom neuen Buildnachweis
trennen; nur bei neuem Befund gezielt wiederholen. Installationspaket nach Prüfung
im bestehenden privaten APK-Bereich bereitstellen, keine Quellen/Entwicklungs-
sicherungen nach Drive. Resultate/Prüfsumme/Provenienz in vorhandenen GitHub-Ständen
festhalten. Geräteseitiger Datenerhalt, Startzeit und Bedienungsabnahme bleiben
ohne ausdrücklich beauftragte physische Prüfung offen. Bei Gatefehler sicher
anhalten; keine Freigabeflags, Ersatzsignatur oder automatische Rückmigration.

## Neueste Priorität: MCP-Katalog geprüft und166er-Übernahme nachgewiesen

Nutzer beauftragt nach eigener Werkzeugaktualisierung und erneutem Chat die
Prüfung von Registrierung und Definitionen: installierte App106 meldet166,
gebundener ChatGPT-Katalog enthält117 Werkzeuge. Keine weitere Chatumschaltung
als nachgewiesene Lösung darstellen. Keine APK, Betriebsdatenänderung oder
Erweiterung von Berechtigungen aus diesem Diagnoseauftrag ableiten.

Ergebnis dieses Auftrags: Nach weiterer Nutzeraktualisierung wurden hier alle166
Werkzeuge sichtbar. Kein fehlender/zusätzlicher Name, neuer lesender Statusaufruf
erfolgreich. Keine Produktions-/Paket-/Berechtigungsänderung erforderlich; den
folgenden Diagnoseplan als durchgeführten Prüfumfang erhalten.169er-Quellstand
weiter von installierter106 trennen. Nachweise im bestehenden Status.

Prüfplan: tatsächliche Capability-Metadaten, Gesundheitsmeldung und sichtbare
Werkzeugnamen gegeneinander vergleichen; alle fehlenden Lese-/Schreibfälle
klassifizieren. Den originalen106-Kataloggenerator und den aktuellen169er-
Quellstand mit JSON-RPC tools/list, Initialisierung, Schreibgate und Tunnel-
Serialisierung prüfen. JSON-Schemata vollständig validieren; Quelle und installierte
Selbstauskunft unterscheiden. Kein produktiver Schreibtest und keine Rohantwort
mit Geschäftsdaten/privater Konfiguration ins Repository übernehmen.

Bestehende Plugin-Identität und Paketdateien lesend prüfen. Bei App-verwalteter
Registrierung weder zweite Verbindung bauen noch durch bloßes Versionsbumping
oder neue Berechtigungen einen Refresh vortäuschen. Nur einen belegten Fehler
am zuständigen Ursprung korrigieren; sonst die externe Aktualisierungsgrenze
präzise dokumentieren. Tests, Metadatenvergleich und aktuellen Stand in den
vorhandenen Projektständen/GitHub sichern. Rollback einer Diagnose ändert keine
Geschäftsdaten, Rechnungsautorität, Backup- oder Tunnelkonfiguration.

## Neueste Priorität: App-Start beschleunigen und Vergleichsrecherche

Nutzer beauftragt den bereits untersuchten Startablauf jetzt ausdrücklich zur
Umsetzung; parallel belegte Ansätze anderer Apps für flüssige Bedienung recherchieren.
Vorher/nachher am aktuellen Quellstand mit denselben synthetischen Daten messen.
Cloud-Browserwerte sind keine Pixel-/Android-Kaltstartmessung. Keine APK oder
Installation aus diesem Auftrag ableiten. Bestehende vereinfachte Bedienung erhalten.

Ergänzter Ursachen-/Gegenpfadplan: Auch die160ms der Abrechnungsfelder,240ms
der Datensatzaktionen und700ms der lokalen Rechnungsdarstellung prüfen. Nur deren
feste Erststartpausen auf den nächsten Ereignisumlauf verkürzen; echte Voraussetzungen,
Draft-/Medien-/Retryfristen und Autoritätsgates behalten. Abrechnungsfelder müssen
vor der Arbeitsmaske vorhanden sein. Die frühere doppelte0ms-Initialisierung von
Angeboten/Unternehmenseinstellungen darf vollständig fertig werden.

`startup-ui.js` mit kleinem statischem Startzustand in `mobile.html`: erst die
fertige Bedienoberfläche zeigen; Initialisierungs-/Gestaltungsfehler sichtbar
behandeln. Stile früh laden und ihre bisherige endgültige Reihenfolge erhalten,
auch gegenüber nachträglichen Inline-Stilen. Bereits vorhandene Startdialoge bleiben
bedienbar; keine Dialogschutzprüfung überspringen. Langer Start darf später
erfolgreich enden. Bewusstes erneutes Öffnen führt keinen Geschäftsvorgang aus
und verwirft keine Daten automatisch. Datenlesefehler, fehlendes Backup und
nicht erreichbares Drive dürfen keine neue fachliche Startsperre werden.

Zusätzlich gefundene Ursache: ein defer-Skript sieht bereits readyState
„interactive“, obwohl die Window-Initialisierung noch aussteht. Abrechnungsfelder
deshalb ausdrücklich hinter DOMContentLoaded einordnen statt früh in den40ms-
Wiederholungsweg zu fallen. Nach dem Umordnen bereits geladener Stil-Links kann
deren CSSOM kurz fehlen, ohne ein weiteres Load-Ereignis; echte Verfügbarkeit
bis zur Bereitschaft weiter beobachten. Kein zeitgesteuertes Freigeben.

Fertigstellung der vereinfachten Formulare an die tatsächliche fertige Revier-
Darstellung binden. Startbereitschaft ist nur ein Präsentationszustand, keine
fachliche Freigabe. Restore-/Beleg-/Migrationsschutz bleibt unverändert.
Gegenprüfung: Normalstart, Neustart, verzögerte/fehlende Skripte und CSS,
Startdialog, Lesefehler, offline/kein Backup, Draft-Erhalt, keine Writes,
erste sichtbare Ansicht und bisherige Darstellung/Kataloge. Relevante bestehende
Browser-/Vertragsregressionen, Privacy und exakten GitHub-Read-back ausführen.
Rollback betrifft nur Start-/Präsentationsquellen, keine Geschäftsdaten.

Vergleichsrecherche ist Quellenmaterial: öffentliche Originalquellen mit festem
Quellstand nennen, beobachteten Mechanismus und übertragbare Idee unterscheiden.
Keine fremde Architektur oder deren Geschäftssynchronisation übernehmen und keine
unbelegte Geschwindigkeitsbehauptung über Fremd-Apps oder den Pixel formulieren.

## Neueste Priorität: appweit einheitliche, einfache Bedienung umsetzen

Nach gezeigter Beispielmaske ausdrücklich beauftragt: den vereinfachten
Bedienungsablauf überall implementieren. Keine erneute Grundsatzfreigabe nötig.
Endzustand: bestehende Bereiche und Fachfunktionen bleiben erreichbar; gleiche
Zurück-/Verwerfen-/Speichern-Semantik, gleich platzierte Aktionen, direkte
Datums-/Uhrzeitauswahl, kurze Kernformulare und aufklappbare Zusatzangaben.

Geprüfter Umsetzungsplan:

- `simple-forms.js/.css` und `mobile.html`: eine gemeinsame Darstellung für
  vorhandene Formulare, Karten, Dialoge und Rückwege. Bekannte optionale Felder
  gezielt gruppieren; ursprüngliche Elemente, IDs, Formzugehörigkeit und
  Listener erhalten. Keine generische Geschäftslogik, kein zweiter Editor.
  Dynamisch aufgebaute Masken ebenfalls erfassen, eigene DOM-Änderungen nicht
  wiederholt beobachten. Pflichtfelder und Finanzbestätigungen nicht ausblenden.
- `field-ui.js`: Arbeitsaufnahme an das Beispiel angleichen: Zeiten direkt,
  Notizen optional, Verwerfen neben Speichern, Zurück im Kopf. Dauer ohne
  Uhrzeiten bleibt möglich, Timer-Abschluss und Foto-Nachbearbeitung bleiben
  getrennte bestehende Abläufe. Keine unverlangte Zeitberechnung/Vorbelegung.
- `revier-solo-ui.js`, Planung, Kunde/Auftrag/Arbeit, Unternehmensdaten sowie
  Angebots-/Rechnungsdialoge: vorhandene Speicher-/Verwerfenknöpfe vereinheitlichen,
  Zusatzfelder über explizite Zuordnung gruppieren. Bedarfsweise Helfer ändern
  ausschließlich das Originalfeld und lösen dessen bestehendes Eingabeereignis
  aus. Keine automatische Speicherung, Erledigung, Nummernvergabe oder Freigabe.
- Alle Menüziele einschließlich Fotos/Pflanzen, Archiv/Status/Einstellungen
  auf Erreichbarkeit prüfen. Native Sicherung und Wiederherstellungsprüfung
  getrennt beachten; bewusste Auswahl, Klartextwarnungen, Autoritätsschutz und
  Restore-Gates erhalten. Android-Systemauswahlen nicht als App-Editor umbauen.

Gegenpfade: dynamischer Neuaufbau, ausgefüllte Zusatzfelder, Pflicht-/Fachfehler
im zugeklappten Bereich, Tastatur/schmale Darstellung, Timer-Abschluss, schwebende
Foto-Speicherung, verzögerte oder unklare Commitantwort und restaurierte Entwürfe.
Neue Helfer bei gesperrten Originalfeldern ebenfalls sperren und vor jeder Aktion
nochmals prüfen. Keine Eingabeereignisse allein durch Öffnen/Gestaltung erzeugen.
Zurück erhält den Entwurf, Verwerfen verwendet nur vorhandene echte Löschpfade.
Rechnungsfinalisierung, Ausgabe, Archiv und Purge behalten eindeutige Aktionsnamen.

Risiko/Ursache: mehrere Präsentationsmodule erzeugen unterschiedliche Feldgruppen
und Aktionspositionen. Eine gemeinsame Präsentationsschicht konsolidiert diese;
Fachregeln, SQLite/Owner-/MCP-Schreiber, Queue, Backup und Restore unverändert.
Tests: neue appweite Browserprüfung mit vorhandenen synthetischen Bridges,
Feld-/Formidentität, keine Writes beim Öffnen, Helfer/Draft/Verwerfen/Fehler,
alle Menüziele und schmale Breiten; bestehende betroffene Regressionen und
Quellkatalog/Privacy. Read-back über vorhandene Formwerte/Quittungen und exakten
GitHub-Commit. Rollback nur Darstellung vor APK-Auslieferung; kein Datenrollback.
Noch kein APK-Bau und keine Installation oder produktive Datenprüfung.

Nutzer verlangt ausdrücklich alle Menüstrukturen und Masken der gesamten App
einzubeziehen. Gleiche Begriffe, gleiche Platzierung und gleiche Bedeutung von
Zurück, Speichern und Verwerfen; Datum/Zeit und Texte konsistent auswählbar.
Funktionsumfang und Schutzregeln erhalten. Keine neue untere Navigation aus
einem bloßen Gesprächsvorschlag ableiten: derzeit Heute/Büro mit Mehr beibehalten.
Betroffene Bereiche umfassen Heute, Kunden/Objekte, Pflege/Einsätze/Planung,
Arbeit/Timer, Anfragen/Aufträge, Angebote/Rechnungen/Zahlungen, Material/Geräte,
Fotos/Pflanzen sowie Einstellungen/Sicherung/Archiv/Status.

Die zuvor ausdrücklich gewünschte Beispielmaske „Arbeit erfassen“ ist erstellt
und wurde gezeigt; danach wurde die appweite Umsetzung ausdrücklich beauftragt.
Die Beispielwerte sind ausschließlich synthetisch. Keine automatische Kundenwahl
oder Erledigung aus der Gestaltung ableiten. Startanalyse und bereits gesicherte
Gerätewartung bleiben erhalten; kein APK-Bau oder produktive Geräteprüfung.

## Aktuell: verzögerter App-Start und sichtbare alte Oberfläche

Neue Nutzerbeschreibung: Beim Start erscheint zuerst eine andere/alte Oberfläche,
danach die neue Ansicht mit Verzögerung. Bestehenden Startablauf untersuchen und
minimal korrigieren; keine neue Navigation oder Fachfunktion daraus ableiten.
Quellbefund vor Änderung: mehrere DOMContentLoaded-Starts warten pauschal50/120/
180/260/280/500ms, drei Gestaltungsdateien kommen erst dynamisch hinzu. Die alte
HTML-Oberfläche ist bis zum Revier-Aufbau sichtbar. Drei synthetische Cloudläufe
zeigen539/540/563ms von DOMContentLoaded bis Revier-Ready und29–33 sichtbare
Zwischenframes mit Heute/Kunden/Mehr. Keine native oder Pixel-Zeitmessung.

Änderungsplan und Gegenpfade:

- `mobile.html`: die drei vorhandenen Gestaltungsdateien in gleicher endgültiger
  Reihenfolge fest laden. Ein kleiner statischer Startzustand gibt erst die fertig
  initialisierte Oberfläche frei. Bei fehlenden Skripten/Stilen sichtbare Meldung
  und bewusstes erneutes Öffnen, kein Rückfall auf die alte Bedienoberfläche.
  Längerer Start bleibt beobachtet und darf später fertig werden; kein automatisches
  Reload, Löschen, Zurücksetzen, Backup oder erneutes fachliches Speichern.
- `design-concept.js`, `daily-usability.js`, `workflow-ui.js`, `planning-ui.js`,
  `navigation-status.js`, `field-ui.js`, `revier-solo-ui.js`: nur feste Startpausen
  auf den bestehenden nächsten Ereignisumlauf verkürzen; Registrierungsreihenfolge
  und tatsächliche Abhängigkeitsprüfungen erhalten. Inline-Window-Initialisierung
  muss vor diesen Aufgaben abgeschlossen sein. Eingabe-, Speicher-, Gesten-,
  Timer- und Retry-Fristen nicht pauschal ändern. Feld-Ready erst nach vollständigem
  Aufbau, statische Stile nicht noch einmal dynamisch anhängen.
- Geöffnete Startdialoge bleiben bedienbar; deren Schutz nicht entfernen. Fehlender
  Geschäftsdaten-Read und nicht eingerichtetes Backup sind keine neue Startsperre.
  Native Android-Startfenster/Bridge-/SQLite-Latenzen bleiben getrennt und ungemessen.
- Vorher/nachher mit denselben synthetischen Browserdaten prüfen: erstes sichtbares
  Bild, keine interaktive Zwischenoberfläche, Skript-/CSS-Fehler, langsamer Start,
  Dialog, Read-Fehler, keine Geschäftsschreibvorgänge und erhaltene Entwürfe.
  Bestehende betroffene UI-, Navigations-, Wartungs- und Katalogprüfungen erneut.
  Native Fachpfade unverändert. Read-back: Originalaktionen/P1-Verträge, Privacy,
  vorhandener GitHub-Entwurf15 und exakter Remote-Commit. Kein APK-Bau/Pixelzugriff.

Risiko: falsche Initialisierungsreihenfolge oder dauerhaft verdeckte Bedienung.
Deshalb keine Abhängigkeit durch bloße Bereitschaftsflags überspringen und keinen
Fehler durch zeitgesteuertes Freigeben der alten Oberfläche verbergen. Rollback
betrifft ausschließlich diese Präsentations-/Startänderungen; Fachdatensätze,
Entwürfe und die zuvor gespeicherte Wartungsimplementierung werden nicht verändert.

## Bereits umgesetzt: einfache Gerätewartung

Neuester ausdrücklicher Implementierungsauftrag: eigene Wartungsintervalle pro
Gerät/Tätigkeit und „Heute erledigt“, einschließlich kürzerer Abstände bei
Fehlschnitten oder erneut nötig gewordenem Nachschärfen. Das sind eigene
Planungsentscheidungen, keine übernommenen Herstellerintervalle. Einmaliges
Vorziehen und dauerhaftes Verkürzen bewusst unterscheiden; keine automatische
Erledigung oder stille Gerätefreigabe. Dieser konkrete Gerätewartungsauftrag
ist bereits im Quellstand umgesetzt; keine sonstigen neuen Funktionen daraus ableiten.

Geprüfter Änderungsplan für diese Umsetzung:

- `EquipmentMaintenanceRules.java` (neu, reine Fachregeln): Tage/Wochen/Monate/
  Jahre mit Kalenderarithmetik, positive begrenzte Intervalle, eindeutige Plan-IDs,
  abgeschlossene Arbeiten mit tatsächlichem Datum. Monatsende/Schaltjahr und
  vorgezogene Einzeltermine getrennt prüfen; keine geschätzten Schnittzähler.
- `RevierApplicationService`: Pläne im vorhandenen Gerätesatz, einzelne
  Wartungen als getrennte lokale Owner-Einträge im bestehenden Revier-Bestand.
  CAS, Transaktion, Audit und stabile Requestquittung gemeinsam nutzen. Rückdatierte
  Einträge dürfen neuere Erledigungen/Fälligkeiten nicht überschreiben; archivierte
  Geräte nicht beschreiben; Referenz-/Restoreprüfung um Wartungsbezüge ergänzen.
  Kein neuer Store, keine Datenbankmigration, kein Drive-/Backupaufruf im Fachpfad.
- `RevierRules.version`: Geräte mit neuen Wartungsplänen und deren Wartungsbelege
  erhalten gezielt Datensatzformat2. Alte Geräte behalten Format1. Der bisherige
  Leser lehnt Format2 bereits ab und kann neue Wartungsdaten dadurch nicht als
  vollständig geprüft restaurieren. Neuer Leser akzeptiert2 ausschließlich für
  diese zwei Eintragsarten; kein allgemeines Freischalten unbekannter Formate und
  kein neues Backupformat. Erst die bewusste Plananlage erzeugt den neuen Vertrag.
- `LocalWorkflowTools` und vorhandener UI-/MCP-Aktionskatalog: zwei benannte
  Schreibfälle (Plan/Anpassung und Erledigung) und ein lesender Wartungsverlauf,
  identische Fachpfade für UI/MCP. Neue Quellverträge präzise in bestehender Fixture
  ergänzen; bisherige Tooldefinitionen unverändert. Installierter Katalog bleibt
  bis zu einer gesonderten APK-/Connectoraktualisierung der alte Stand.
- `revier-solo-ui.js`: auf der vorhandenen Geräteseite Pläne, „Heute erledigt“ und
  „Früher warten“; Datum korrigierbar, Intervall nur bewusst ändern. Vorhandenen
  Editor-/Draft-/Commitpfad verwenden. Tätigkeitstexte bleiben Vorschläge, eigene
  Angaben möglich; keine versteckte Erledigung durch Öffnen, Abbruch oder Verwerfen.
  Ergänzende Gegenprüfung: vor einer verzögerten Speicherantwort kann ein anderer
  Editor offen sein. Bereinigung, Fehler und Bestätigung müssen an der ursprünglichen
  Editiersitzung bleiben; keine neue Maske löschen, entsperren oder umleiten.
- Regressionen: Kalendergrenzen, einmalig/dauerhaft, Rückdatierung, wiederholte
  Requests, Konflikt, Transaktionsfehler, Neustart/Restore, Referenzschutz, UI/MCP-
  Ergebnis, Draftverwerfen und unklare Commitantwort. Nur synthetische Daten.
  Read-back über gespeichertes Gerät und Wartungsverlauf, nach GitHub exakten Commit
  lesen. Kein APK-Bau, keine Installation oder produktive Migration in diesem Schritt.

Risiko: vorhandene Fälligkeit nicht mit erledigter Wartung verwechseln, keine
verwaisten Wartungsbezüge und keine doppelte Erledigung bei verlorener Antwort.
Recovery verwendet dieselbe Request-ID und zuerst lesende Prüfung. Bestehende
manuelle Wartungsdaten bleiben erhalten. Rollback erfordert Quellrücknahme vor
Auslieferung; nach gespeicherten Wartungen keinen alten Leser als vollständige
Wiederherstellungsprüfung ausgeben. Historische Ereignisse nicht automatisch löschen.

Direkter Nutzerauftrag27.10.: Geräte überwiegend selbst warten; den vorhandenen
Gerätebereich zu einem verständlichen Menüpunkt Gerätewartung ausbauen. Einfache
Geräteanlage, möglichst mit Foto, und direkt auswählbare Arbeiten wie „Kette
gewechselt“ und „Öl gewechselt“. Bestehende Geräte weiterverwenden, keine zweite
Geräteverwaltung oder technische Einrichtung für den Nutzer. Nutzerklärung:
Foto ausschließlich zur visuellen Identifikation des Geräts; keine Hersteller-
oder Modellsuche. Einfache Wartungstätigkeiten stehen im Mittelpunkt.

Weiterer direkter Auftrag: gängige Gartengeräte recherchieren und passende
Textbausteine anlegen, ausdrücklich mit Akku-Gartenschere STIHL ASA20 als Beispiel.
Umsetzung dieses Teils: öffentlicher statischer Textkatalog im vorhandenen
App-Quellbaum; keine Anlage tatsächlicher Geräte oder Wartungen. Tätigkeiten
nach Gerät/Antrieb und bestätigter Ausstattung trennen; „Motoröl gewechselt“
nicht bei Akku-/Zweitaktgeräten und nicht mit Kettenöl auffüllen vermischen.
ASA20 nach eigener Herstelleranleitung behandeln. Neueste Nutzerpräzisierung:
„Schärfen darf auch erlaubt sein.“ Deshalb „Klinge geschärft“ auch bei ASA20
als Dokumentation einer tatsächlich ausgeführten Tätigkeit auswählbar erhalten;
nicht als Herstellerempfehlung ausgeben. Gegenprüfung: Quellenbezüge,
eindeutige IDs, passende Gerätetexte, keine automatischen Fälligkeiten oder
Schreibpfade und keine Änderung des leeren Startbestands. Datei entfernen
rollt nur Vorschlagstexte zurück, niemals Geschäftsdaten.

Vor einer Umsetzung den vorhandenen Gerätepfad, lokale Fotozuordnung und echte
Wartungsereignisse prüfen. Gerätenutzung ist keine Wartung; Wartungen nicht als
Kundeneinsatz oder bloßes Überschreiben der letzten Notiz behandeln. Entwürfe,
Speichern, Wiederholung, Löschen, Restore und gemeinsame UI-/MCP-Fachpfade
einschließen. Ein Wartungseintrag darf den Gerätezustand nicht ungeprüft freigeben
oder automatisch erfundene Fälligkeiten setzen. Noch keine APK beauftragt.

## Aktuell: Heute als übersichtlicher Einstieg

Neueste Präzisierung zur Bedienung: Die Menüstruktur ist in Ordnung; die Abläufe
innerhalb der Menüs sind zu aufwendig. Eine breite Auswahl anderer Apps anhand
offizieller Bedienungsanleitungen vergleichen, nicht nur Haus-/Garten-Apps.
Funktionen erhalten, häufige Eingaben vereinfachen, Zusatzoptionen bei Bedarf.
Die Recherche ist keine Freigabe für neue Funktionen oder weitere Menüumbauten.
Anschließend ausdrücklich beauftragt: ein kurzes Testblatt für Arbeit starten,
Termin verschieben und Anfahrt sehen erstellen. Als Vorschau mit synthetischen
Einsätzen erproben; Schritte, Verständlichkeit und Ergebnis erfassen. Zwei
Klicks bis zur Hauptaktion sind ein Vergleichsziel, keine neue feste Menüregel
und keine Erlaubnis, Eingaben oder notwendige Bestätigungen zu überspringen.
Kalenderpräzisierung: Termine bleiben in der App bearbeitbar; Änderungen sollen
zeitnah mit Google Kalender abgeglichen werden. Offline kalenderbezogene
Änderungen als „ausstehend“ behandeln und bei Verbindung übertragen; übriger
Geschäftsbetrieb bleibt lokal. Keine Speichersperre bis zur Cloudbestätigung
ableiten und keinen Drive-Geschäftssync reaktivieren. Hier noch nicht implementiert.

Neueste Nutzersteuerung27.10.: Heute zuerst überarbeiten; die neue Oberfläche ist
zu komplex und die Übersicht verloren gegangen. Bevorstehende Arbeitseinsätze
müssen sichtbar bleiben. Emulator, ADB-Protokoll, GitHub-Konsolidierung und weitere
Leistungsanalyse sind vorerst zurückgestellt. Bereits gemeldete träge Übergänge
und zunächst alte Startoberfläche sind damit noch nicht als behoben erklärt.

Weitere Nutzerpräzisierung: „Objekte“ und „Pflege“ aus der unteren Hauptnavigation
nehmen und im bestehenden Mehr-Menü führen. Unten bleiben Heute und Büro. Vor
Änderung: sichtbare Wurzeln, aktive Menüanzeige, Rückweg aus Liste/Detail/Entwurf,
vorhandene Direktziele sowie Browser-/native Navigationsfixtures gegeneinander
prüfen. ROOTS/Navdarstellung und Spaltenzahl gemeinsam ändern; Fach-/Editor-Kontext
nicht in einen anderen Fachbereich umdeuten. Mehr → Bereich → Detail → Zurück
muss wieder über den Bereich ins Menü führen; laufende Sitzung und Entwürfe
bleiben erhalten. Bereits vorhandene Menüeinträge verwenden, keine zweite Funktion.
Die vorhandene Gruppenzuordnung in `workflow-ui.js` muss die Revier-Ziele kennen:
Objekte/Pflege/Tour/Material/Geräte/Suche zu Arbeit & Planung, Büro zu Belegen;
keinen Fallback nach Status & Einstellungen als fachliche Zuordnung stehen lassen.
Zuerst Bilder der aktuellen Heute-/Menüansichten mit synthetischen Daten liefern;
weitere Formularänderungen dafür zurückstellen.

Weitere Nutzersteuerung: Funktionsumfang erhalten, Bedienung massiv vereinfachen.
App soweit ohne produktive Daten möglich im Code und mit synthetischen Masken
prüfen: vorbereitete Texte, einfache Datums-/Uhrzeitwahl, weniger Eingaben und
Rückfragen. Vorhandene Vorschläge und Dauerknöpfe zuerst erfassen; keine zweite
Vorlagen-Datenhaltung oder unbewiesene Funktionslücke erfinden. Datums-/Zeithilfen
dürfen Eingaben nur bewusst setzen, nie tatsächliche Zeiten runden oder fachliche
Bestätigung, Änderungsgrund, Commit-ID und Rechnungs-/Backupgates überspringen.
Der derzeitige Zusatzauftrag umfasst die konkrete Bedienungsprüfung und
Vereinfachungspunkte; neue Eingabehilfen sind dadurch noch nicht als eingebaut erklärt.

Minimaler Änderungsplan vor Umsetzung:

- `android/app/src/main/assets/revier-solo-ui.js`, `refreshToday`: statt vollständiger
  Tourbearbeitung plus doppelt aufgeführter Besuche eine kompakte Einsatzliste.
  Bestehende Tourreihenfolge erhalten; nur dieselbe Besuchs-ID deduplizieren.
  Weitere Besuche und Sortierung bleiben über bestehende Detail-/Listen-/Tourziele
  erreichbar. Keine neue Geschäftslogik oder zusätzliche Native-Schreibaufrufe.
- `today-ui.js`: leere zusätzliche Kalenderkarte aus der Übersicht nehmen, sobald
  die Einsatzübersicht erfolgreich gelesen ist; echte künftige Termine weiter
  anzeigen und Erstlesefehler nicht durch einen vermeintlichen Leerzustand ersetzen.
- `mobile.html`/`today-ui.css` und der reine DOM-Zielort der vorhandenen
  Sicherungsstatuszeile in `business-actions-ui.js`: Kennzahlen, frühere Arbeiten und allgemeine Hinweise
  in eine beschriftete aufklappbare Übersicht; Einsätze/Termine und laufende Arbeit
  bleiben vorne. Ungeplante heutige Aufträge nicht als Historie verstecken.
- Leere Übersicht, Lesefehler, abgesagte/abgeschlossene Besuche, Tour-/Fälligkeits-
  Doppelungen, lange Texte,320/360/426px und200Prozent-Schrift einzeln prüfen.
  Fehler dürfen nicht als leerer erfolgreicher Datenstand erscheinen.
- Timer-, Speicher-, Entwurfs-, Lösch-, Backup- und Rechnungsfachpfade unverändert;
  bestehende DOM-IDs/Navigation erhalten. Gezielte Browserregressionen mit
  synthetischen Fixtures; Sichtprüfung der echten gebündelten Assets. Native/
  physische Abnahme bleibt getrennt. Kein APK-Bau oder Geräteinstallation hier.

Die vorhandenen Kalendertermine sowie heutigen/fälligen Besuche bleiben dieselben
fachlichen Datenquellen. Weitere/künftige Besuche sind über die bestehende Liste
zugänglich; keine unbewiesene Zusammenführung verschiedener Fachdatensätze.


## Nutzerauftrag vom 26. Oktober 2026

Die bestehende Codex-Gartenumgebung soll Entwicklungsanweisungen ausschließlich
aus dem privaten Repository `mihalhafner-collab/Gartenpirat-` beziehen. Sämtliche
Änderungen passend in GitHub sichern. Anweisungen zur Drive-Ablage von Quellen,
Entwicklungsbackups und Statuskopien entfernen. Einzige Codex-Ausnahme ist die
Bereitstellung einer fertig erstellten, geprüften APK für die Paketinstallation.
Die verschlüsselte App-Sicherungsfunktion bleibt davon getrennt erhalten.

Anschließend Repositorystruktur und alte Quellstände prüfen und bereinigen,
aktuelle Informationen nachvollziehbar zusammenführen. Nutzerklärung vom27.10.:
APKs **ab Umstellung Revier Solo behalten**, ältere entfernen. Die Quellgrenze ist
1.55.0/Build99 (`00974518426e1a2bdfcb07c5eba1223071a49ca3`); vorhandene
Pakete ab99 bleiben, ältere administrative APKs und Versionsreferenzen entfallen.
Git-Historie und historische Nachweise erhalten; keine Betriebsdaten-/Backuplöschung.
Den vorbereiteten Cloud-Starttext in die bestehende Gartenumgebung übernehmen;
Benutzer hat die sichere ChatGPT-Anmeldung im Cloudbrowser beauftragt.

## Konkrete Arbeit und Abnahme

1. Aktuelle GitHub-Branches/Tags und Arbeitsstand lesen; einzigartige ungesicherte
   oder nicht integrierte Arbeit erkennen. Keine Branchlöschung nur nach Alter.
2. AGENTS, README, Auftrag, Status und maschinenlesbare Quelle widerspruchsfrei
   auf GitHub ausrichten. Alte aktive Drive-/Pages-Anweisungen entfernen.
3. Bestehende Administrationsautomationen auf GitHub umstellen; keine neuen
   Drive-Quellgenerationen und keine weiteren automatischen Fremdablagen.
4. Exakten Bootstrap für die bestehende Gartenumgebung vorbereiten. Tatsächliche
   veröffentlichte Konfiguration getrennt von Git-Dateien prüfen und nachweisen.
   Fehlender Schreib-/Anmeldezugriff bleibt offen, keine lokale Datei als
   erfolgreich veröffentlichte Cloud-Einstellung ausgeben.
5. Überholte Dokumente/Launcher nur bei geprüften Abhängigkeiten aus dem aktiven
   Baum entfernen. Vollständige Fassungen mit festen Git-Commitlinks erhalten;
   aktuelle Zusammenfassungen führen, keine parallele Regelzentrale erzeugen.
6. Versionsreferenzen vor Build99 nach belegter Git-Historienerhaltung bereinigen. Eingebaute Fachfunktionen,
   aktive Schutzprüfungen, benötigte Tests und Fixtures sind keine alten Kopien.
7. Relevante bestehende Source-/Workflow-/Signierbaseline-Prüfungen sowie Privacy
   und Links/Referenzen prüfen. Commit, Push und exakten GitHub-Read-back liefern.
   Keine neue APK und keine Installation für diese administrative Bereinigung.

## Bestehender Transition-Vertrag

Dieser Abschnitt erhält die weiterhin gültigen fachlichen Grenzen. Er ist kein
Auftrag, während der Repositorybereinigung weitere Appfunktionen zu entwickeln.

Pixel/App ist die einzige operative Datenautorität; MCP und UI verwenden dieselben
lokalen Fachregeln. Drive ist ausschließlich verschlüsseltes Backupziel. Legacy-
Queue und Journal können historische Evidenz enthalten, aber keine neuen
Drive-Geschäftsaufträge erzeugen, den lokalen Betrieb blockieren oder durch Retry,
Neustart oder Restore wieder zu aktiven Schreibern werden. Backup ist kein Sync.

Die Quellimplementierung106 enthält keine produktiven Drive-Geschäftstransporte.
Das ist kein Nachweis eines abgeschlossenen produktiven Datenübergangs. Letzte
vorherige Pixelbeobachtung105:130 ungeklärte Beziehungsreferenzen,107 nicht lokal
verifizierte Originale und vier historische Konflikte. Vor Migration oder
Autoritätsfreigabe den realen Bestand gesondert klären; keine Annahmen, Flag-
Freischaltung, produktive Testdaten oder destruktive Queue-/Datenbereinigung.

Rechnungsnummern, Belegautorität, Finalisierung und Original-PDFs bleiben separat
geschützt. Keine automatische Freigabe durch allgemeine lokale Autorität.
Kunden, Einsatzobjekt und Rechnungsempfänger unterscheiden. Auftragslose Arbeit
bleibt gültig; b.b./bezahlt ist kein Rechnungs-/Billed-Status.

### Formulare und lokale Commits

- Unberührt: kein Draft. Bearbeitet: Draft. Bestätigtes Speichern: Geschäftssatz.
- Verwerfen entfernt die Bearbeitung aus allen aktiven Speichern. Abbruch erzeugt
  keinen versteckten Geschäftssatz. Delete ist weder Archive noch Inactive/Hidden.
- Unklare Commitantwort: ursprüngliche Request-ID und Argumente behalten, zuerst
  lesend prüfen; keine zweite Anlage durch neue ID. Bestätigter Commit bleibt bei
  späteren Foto-/UI-/Backupfehlern bestehen.
- Reload, Prozessabbruch, verspätete Callbacks, Draftmirror, Pending Action,
  Cache und Restore dürfen verworfene/gelöschte Inhalte nicht wieder aktivieren.
- Fachliche Aufbewahrungspflichten und referenzierte Belege respektieren;
  fachliches Löschen, Archivieren, Verwerfen, Papierkorb, Purge und Audit trennen.

### Backup und Restore

Lokale Erfassung funktioniert ohne eingerichtetes Backup und bei Driveausfall.
Lokales Archiv, Upload und vollständiger externer Hashnachweis sind getrennte
Zustände. Bestehende Verschlüsselung und bewusste Geräteeinrichtung erhalten.
Ein APK-Upload ist kein Betriebsdatenbackup. Bewusster Vollrestore ist getrennt
und fail-closed; er aktiviert weder alte Queue/Schreiber/Autorität noch verworfene
Drafts und vermischt keine älteren Cloudstände mit neueren lokalen Geschäftsdaten.

### Prüfkaskade bei späteren technischen Änderungen

Endzustand definieren → realistische Gegenpfade einzeln untersuchen → tatsächliche
Code-/Datenmodellursache bestimmen → minimal korrigieren → denselben Zustand
reproduzierbar testen/zurücklesen → neu entstandene Gegenpfade prüfen. Nicht beim
ersten sichtbaren Fehler aufhören. Je nach Änderung UI, Java/native, SQLite,
Owner-Stores, MCP-Lesen/Schreiben, Journale, aktuelle/historische Queue, Backup,
Restore, Drafts, Timer, Medien, Angebote, Rechnungen, Planung, Löschen und Fixtures
einschließen. Historische Daten nicht blind löschen oder nach Alter erledigt nennen.

Neue lokale UI-/MCP-Mutationen dürfen keinen Legacy-Drive-Syncauftrag erzeugen.
UI und MCP müssen den gleichen fachlichen Endzustand erzeugen; Drive-/Backupfehler
ändern keinen Geschäftssatz. Prozessabbruch, bekannte Commit-ID, Restart, Retry,
Restore und Löschung erhalten Idempotenz, lokale Autorität und Verwerfbelege.

## Daten- und Releasegrenzen

Nur Quellcode, öffentliche Assets, exakt leerer Bootstrap und synthetische QA.
Keine privaten Datensätze, Fotos, Datenbanken, Snapshots, Rohbackups oder Secrets
für Entwicklung abrufen. Auch ignorierte/unversionierte Dateien und Artefakte
prüfen. Git-Historie nicht automatisch umschreiben.

Paketkennung und Originalschlüssel erhalten; Signierung nach
`ORIGINAL-SIGNIERUNG-CLOUD.md`, niemals in GitHub Actions. Vor Build Quellen auf
GitHub nachweisen, Buildprovenienz exakt binden. Physische Pixelinstallation,
Bestandserhalt und externe Connector-/Providerprüfungen sind eigene Nachweise.
Kein Laptop, kein neuer Dienst/Tarif, keine zweite App/DB/Repository, keine neuen
Features vor Transition-Abschluss. Keine produktive Migration oder Installation.

## Nachvollziehbarkeit

Aktueller Stand: `CODEX-STATUS.md` und `AKTUELLER-STAND.json`. Technische Evidenz:
`docs/verification/`. Originalversionen: `source-releases.json`. Vollständige
vorherige Anforderungen und datierte Zwischenstände sind im festen
[Git-Stand a41fc67](https://github.com/mihalhafner-collab/Gartenpirat-/blob/a41fc67f9e2d74fb3912c688b0f57cf7476ec598/CODEX-AUFTRAG-GARTENPIRAT.md)
erhalten. Diese alten Fassungen sind kein zusätzlicher aktiver Auftrag.
