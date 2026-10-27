# Gartenpirat Arbeitsstand

Stand: 27. Oktober 2026. Repository: `mihalhafner-collab/Gartenpirat-`.
Entwicklungsanweisungen und neue Sicherungen ausschließlich in GitHub nach
`AGENTS.md`. Vollständige frühere Berichte sind im festen
[Vorbereinigungsstand a41fc67](https://github.com/mihalhafner-collab/Gartenpirat-/blob/a41fc67f9e2d74fb3912c688b0f57cf7476ec598/CODEX-STATUS.md)
erhalten und gelten nicht als neue Arbeitsanweisung.

## Aktuell: Gerätewartung und Heute-Ansicht – noch nicht gebaut

Gerätewartung im Quellcode umgesetzt: eigene Intervalle pro Gerät und Tätigkeit
in Tagen, Wochen, Monaten und Jahren; „Heute erledigt“ mit änderbarem Datum,
einmaliges Vorziehen sowie dauerhaftes Verkürzen bei Fehlschnitten oder erneutem
Nachschärfen. Antippen der Tätigkeit öffnet die Intervallbearbeitung. Änderungen
benötigen einen Grund; alte Wartungen bleiben unverändert. Vorschläge für Gründe
und die57 recherchierten Wartungstexte erleichtern Eingaben; eigene Texte bleiben
möglich. Das sind Aufzeichnungen eigener Arbeiten, keine Herstellerempfehlungen
oder automatische Modell-/Ausstattungserkennung. Öffnen und Abbruch speichern
keine erledigte Wartung; Verwerfen entfernt den jeweiligen Entwurf.
Speicherantworten und Fehler bleiben an ihrer ursprünglichen Editiersitzung.
Ein schneller Formularwechsel darf einen neueren Entwurf weder löschen noch
schließen; Eingaben werden bereits beim Absenden vor Änderungen geschützt.

Pläne liegen am vorhandenen Gerät, erledigte Wartungen separat im gleichen lokalen
Owner-Bestand. UI und MCP verwenden dieselben Fachregeln, CAS, Transaktion,
Audit und Requestquittung. Bei verlorener Antwort zuerst lesend prüfen; bestätigt
bedeutet kein zweiter Schreibvorgang, unbekannt erlaubt nur den ausdrücklich
wiederholten ursprünglichen Request. Ein restaurierter unbekannter Request bleibt
gesperrt. Rückdatierte Nachträge verdrängen keine neuere Planbasis/Erledigung.
Monatsende und Schaltjahr verwenden Kalenderarithmetik. Manuell vorgemerkte
Wartungsdaten bleiben getrennt, ein Wartungseintrag gibt kein defektes Gerät frei.
Kein Drive-, Backup- oder Kalenderaufruf im Wartungsschreibpfad.

Geräte mit Wartungsplänen und Wartungsbelege verwenden gezielt Datensatzformat2;
bestehende Geräte bleiben1. Der tatsächliche Vorgänger-Leser vom6cd9ff2 lehnt
Format2 bereits in der Restore-Beziehungsprüfung ab; aktueller Leser akzeptiert
dieselben synthetischen Daten. Keine Datenbankmigration und kein neues Backupformat.
Rechnungsautorität und produktive Umstellungs-/Integritätsgates bleiben separat.

Quellkatalog jetzt169 MCP-Werkzeuge und212 UI-Aktionen: genau drei zusätzliche
Wartungsfälle, bisherige Tooldefinitionen und originale P1-Baseline unverändert.
Das ist kein Nachweis eines aktualisierten installierten Pixel- oder gebundenen
Connector-Katalogs. Dafür wäre eine gesonderte APK-/Connectoraktualisierung nötig.
Keine neue APK, Signierung, Installation oder produktive Datenprüfung durchgeführt.
Gerätefoto-Zuordnung und physische Pixel-Abnahme bleiben offen.

91 gezielte Fachprüfungen und alle80 Java-Prüfgruppen PASS. Dreizehn neue Browser-
prüfungen sowie18 Revier-,18 Field-,8 Navigations-,41 Übersichts- und7 Design-
prüfungen PASS. Quelle2343Dateien und gesamter Worktree6033Dateien einschließlich
ignorierter/unversionierter Dateien bestehen die Privacy-Gates;0 Betriebsdaten-
Startzeilen. Kein APK-Artefakt geprüft.

Prüfbeleg: `docs/verification/equipment-maintenance-2026-10-27.json`;
synthetische Browseransicht: `docs/verification/equipment-maintenance-360.png`.
Der Katalog umfasst36 Gerätetypen,57 Texte und20 Quellen, einschließlich STIHL
ASA20 mit ausdrücklich gewünschter Dokumentationsoption „Klinge geschärft“.
Der frühere reine Katalogprüfbeleg im6cd9ff2 bleibt historische Vorstufe;
Modellprofile sind weiterhin Referenzdaten, keine automatisch angelegten Geräte.

### Kurzes Testblatt für die Bedienung

Vorbereitet, noch nicht durchgeführt. Start jeweils auf Heute; nur synthetischen
Einsatz verwenden, vor jedem Weg denselben Vorschau-Ausgangszustand herstellen.
Die folgenden Wege sind Prüfszenarien, keine Behauptung eingebauter Schaltflächen.

| Aufgabe ohne Bedienhinweis | Woran wir das Ergebnis erkennen |
| --- | --- |
| Starte die Arbeit beim nächsten Einsatz. | Richtiger Einsatz erkennbar, Arbeitsaufnahme bewusst ausgelöst, laufende Zeit sichtbar; kein zweiter Timer durch erneutes Tippen. |
| Verschiebe einen Termin auf morgen um9Uhr. | Datum/Zeit leicht wählbar, Änderung bewusst gespeichert, neuer Termin sichtbar. Online-Kalenderbestätigung und offline „ausstehend“ getrennt; fehlenden Abgleich nicht als Erfolg darstellen. |
| Zeige die Anfahrt zum nächsten Einsatz. | Zieladresse stimmt; vorhandene Karten-/Routenansicht ohne erneutes Adressentippen erreichbar. Fehlende Adresse klar melden, kein geratenes Ziel. |

Pro Weg festhalten: Tippen bis zur Hauptaktion / Tippen insgesamt / benötigte
Zeit / Zurückwege oder Suchstellen / Ergebnis korrekt ja-nein / Hilfe nötig.
Zwei Klicks bis zur Hauptaktion dienen nur dem Vergleich; Datenwahl, Eingabe
und notwendige Bestätigung zusätzlich zählen. Bei einer späteren Variante
gleiche Aufgaben und Testdaten verwenden; schneller allein genügt nicht,
Ergebnis und Verständlichkeit müssen stimmen. Keine produktiven Änderungen,
keine echte Kalenderübertragung oder Navigation aus dieser Vorbereitung.
Noch keine Nutzungsprüfung oder neue interaktive Vorschau durchgeführt.

Bedienungsrecherche27.10.: offizielle Anleitungen von Todoist, Google Kalender,
Google Maps, Google Keep und Clockify verglichen, keine installierten Fremd-Apps
getestet. Nutzerpräzisierung: bestehende Menüstruktur beibehalten; einzelne Abläufe
vereinfachen. Wiederkehrendes Muster: kurzer häufiger Eingabeweg, bekannte
Auswahl weiterverwenden, Zusatzoptionen im jeweiligen Zusammenhang anbieten.

- [Todoist](https://www.todoist.com/help/todoist/features/use-task-quick-add-in-todoist-va4Lhpzz):
  Schnellerfassung verbindet Aufgabentext mit Datum/Zeit; weitere Angaben optional.
- [Google Kalender](https://support.google.com/calendar/answer/72143?co=GENIE.Platform%3DAndroid&hl=de):
  freie Zeitfläche antippen, Dauer direkt ändern; zusätzliche Angaben aufklappen.
- [Google Maps](https://support.google.com/maps/answer/3273406?co=GENIE.Platform%3DAndroid&hl=de):
  Ziel wählen und Navigation starten; Zusatzoptionen über die Informationskarte.
- [Google Keep](https://support.google.com/keep/answer/2888246?co=GENIE.Platform%3DAndroid&hl=de):
  direkter Textnotiz-Einstieg konfigurierbar; weitere Notizarten bleiben erreichbar.
- [Clockify](https://clockify.me/help/apps/android-app): Dauerknöpfe und
  Beschreibungsvorschläge verringern Tippen; Zusatzaktionen am einzelnen Eintrag.

Ableitung, noch keine Umsetzung: Einsatzkontext nutzen, Tätigkeitstext gezielt
übernehmen, Datum/Zeit einfacher wählen; freie genaue Eingaben und ausdrückliches
Speichern erhalten. Vorhandene Textvorschläge/Dauerknöpfe zuerst konsolidieren.
Keine geratenen Kunden/Zeiten; Keep-Autospeichern beim Zurückgehen nicht auf
Geschäftssätze übertragen. Offline-Kalenderänderungen als ausstehend gewünscht;
kein neuer Kalenderabgleich eingebaut. Nur bestehende Auftrags-/Statusdateien
ergänzt, keine App-Änderung, keine APK, keine produktiven Datenzugriffe.

Nutzerpriorität27.10.: Heute soll der zentrale, übersichtliche Einstieg sein;
bevorstehende Arbeitseinsätze müssen sichtbar bleiben. Emulator, ADB-Protokoll,
GitHub-Konsolidierung und weitere Leistungsanalyse ruhen. Ihr letzter Stand ist
im separaten [Entwurf14](https://github.com/mihalhafner-collab/Gartenpirat-/pull/14)
gesichert und wird durch diese UI-Arbeit nicht als abgeschlossen erklärt.

Quelländerung im Branch `codex/heute-uebersicht-20261027`, auf aktuellem main:
kompakte heutige/fällige Einsätze, je Besuchs-ID nur einmal, vorhandene Tourreihenfolge
bleibt erhalten. Tourbearbeitung und vollständige Besuchsliste bleiben über
bestehende Ziele erreichbar. Weitere Termine bleiben sichtbar; eine zusätzliche
leere Terminkarte entfällt nach erfolgreichem Lesen der Einsatzübersicht.

Weitere Nutzerpräzisierung umgesetzt: unten nur Heute/Büro. Objekte und Pflege
stehen im vorhandenen Mehr-Menü in Arbeit & Planung. Die Menü-Gruppierung kannte
die Revier-Ziele bisher nicht und ordnete sie fälschlich Status & Einstellungen
zu; diese Zuordnung ist korrigiert. Zurückwege und Fach-/Entwurfskontext bleiben
erhalten, der Menübereich wird aktiv kenntlich gemacht.

Kennzahlen, frühere Arbeiten und Sicherungsdetails sind aufklappbar. Laufende
Arbeit und vorhandene Arbeitsaktionen bleiben direkt erreichbar. Lesefehler
behalten die letzte Übersicht mit sichtbarem Hinweis und Wiederholung; keine
Schreiboperation beim Aufruf der Übersicht. Fachregeln, Timer, Entwürfe, Backup,
Rechnungsautorität und Geschäftsdaten werden durch diese Darstellung nicht geändert.

18 Revier-UI- und41 Übersichts-/Zugänglichkeitsprüfungen PASS; zusätzlich der
bestehende Sicherungszugang mit Tastatur-/Größen-/Fehlerfällen PASS. Quelle2333
Dateien und gesamter Worktree6013Dateien einschließlich ignorierter Dateien
bestehen die Privacy-Gates. Keine neue native oder physische Abnahme.
Prüfbeleg: `docs/verification/today-overview-2026-10-27.json`.
Vorschau ausschließlich synthetische Browserdaten. Keine APK gebaut, keine
Installation oder produktive Geräteprüfung. Der unveränderte106-Release-Tag
bleibt die letzte geprüfte Paketquelle; diese Quellüberarbeitung ist kein
neuer freigegebener Build. Ruckelige Übergänge und zunächst alte Startoberfläche
sind noch keine nachgewiesen behobenen Gerätefehler.

### Bedienungsprüfung und Bildvorschau

Quell-/Browserprüfung mit synthetischen Bridges: Arbeit erfassen5 sichtbare
Hauptfelder, Termin planen4, Einsatz bearbeiten10. Die Arbeitsmaske hat bereits
Textvorschläge aus bis zu drei früheren Tätigkeiten; die Terminplanung hat
Dauerknöpfe1h/2h/halber Tag. Diese sind keine neu eingebauten Hilfen. Datum/Zeit
verwenden vorhandene native Eingabetypen; ihre praktische Picker-Bedienung auf
dem Pixel ist hier nicht geprüft. Während dieser drei Maskenaufrufe keine
Revier-Schreibvorgänge oder Scriptfehler.

Priorisierte offene Vereinfachungen: Text gezielt übernehmen statt eine ganze
Arbeit mit Stunden duplizieren; Datum-/Uhrzeitwahl über die Masken vereinheitlichen;
Einsatzbearbeitung auf Kerneingaben konzentrieren. Exakte freie Eingaben,
Änderungsgrund, Gerätemängel, Drafts und ausdrückliches Speichern bleiben notwendig.
Weitere Formularänderungen auf Nutzerwunsch zunächst zurückgestellt: zuerst
Bilder der tatsächlich gerenderten neuen Heute-/Menüansicht liefern.

Bilder mit Testdaten: `docs/verification/today-overview-360.png`,
`more-menu-360.png` und `more-menu-sections-360.png` im selben Ordner.
Elf betroffene Browser-Prüfgruppen (162 Einzelprüfungen) PASS; nach Menüzuordnung
Revier-, Menü-/Kalender- und einfache Designregression erneut PASS. Native
Navigationsfixture angepasst, aber keine neue APK oder native Abnahme.

## Zurückgestellte Ablagebereinigung

GitHub als einzige Entwicklungszentrale; keine Drive-Quellkopien oder externen
Regel-/Statusabrufe. Widersprüchliche aktuelle Anweisungen werden ersetzt,
überholte Dokumente/Windows-Launcher nach Abhängigkeitsprüfung aus dem aktiven
Baum entfernt. Aktuelle Quellen, Installation und offene Gates bleiben getrennt.

**Nutzerklärung27.10.:** APKs ab Revier Solo behalten. Grenze:1.55.0/Build99
(`00974518426e1a2bdfcb07c5eba1223071a49ca3`).16 ältere administrative APKs
(536.600.591Bytes) endgültig gelöscht: jede ursprüngliche ID liefert NOT_FOUND;
keine Ziel-ID mehr in den geprüften APK-Ordnern. Elf Versions-Tags88–98 lokal
und auf GitHub entfernt;105/106 samt ursprünglichen Tagobjekten unverändert.
Drei eigenständige Originalarchiv-Commits88–90 vorab durch reinen Historienmerge
`2813cd0972d6fb649cccd328ad93eb3feee83365` erhalten, identischer aktueller
Dateibaum. Alle alten Original-Commits über main erreichbar; kein History-Rewrite.
APKs105/106 und ihre vorhandenen Teile, App-Backups sowie einzigartige
Pflanzenarbeit bleiben erhalten. Keine neue Release-/Migrationsfreigabe.

**Cloud-Starttext vom Nutzer veröffentlicht:** Nach dem gezeigten Start-Skill-
Änderungsvergleich und der Anleitung Speichern → Veröffentlichen bestätigt der
Nutzer ausdrücklich „Veröffentlicht“. Die manuelle Veröffentlichung ist damit als
Nutzerbestätigung festgehalten; keine erneute Veröffentlichung angefordert.
Der zuletzt direkt kopierbar übergebene Text steht jetzt wortgleich im vorhandenen
Starttextblock von `AGENTS.md`; Inhalt und Schutzgrenzen unverändert.

Technische Nachweisgrenze: kein vollständiges Rücklesen des veröffentlichten
Start-Skills und keine neue Laufzeit durch Codex. Der aktuelle Umgebungsstatus
meldet weiterhin die bereits laufende Instanz aus Konfigurationsrevision16.
Dieser Wert beschreibt nicht den neuesten veröffentlichten Stand und widerlegt
die Nutzerbestätigung nicht. Neue veröffentlichte Revision unbekannt; Übernahme
beim nächsten tatsächlichen Umgebungsstart noch nicht technisch geprüft.

Die vorigen rein lesenden Browserläufe konnten den Editor im gespeicherten Profil
nicht erreichen und bleiben historische Zugriffsevidenz. Screenshots/Privatinhalte
nicht im Repository abgelegt. Kein APK-Bau, keine Installation, Betriebsdaten-,
Setupskript-, Signierwert- oder Netzänderung durch Codex in diesem Schritt.

## Quellkandidat und beobachtete Installation

| Zustand | Version | Originalquelle | Nachweisgrenze |
|---|---|---|---|
| Bereitgestellter originalsignierter Kandidat | 1.55.1 / 106 | `source/v1.55.1-build106`, `99ab63afbdfdb9fd32dac70134f9461b1ba15b17` | Hostprüfung bestanden; native UI-/physische Updateabnahme offen |
| Zuletzt vom Pixel gemeldet | 1.55.0 / 105 | `source/v1.55.0-build105`, `4087d8b5b83294d75dba6d9fc87e2d6472d3be28` | Lesender MCP-Readback vom 26.10.; keine Installation durch Codex |

106: DB6, MCP9, Speichervertrag3. Operativer Quellfingerprint
`6da86aad5019a93520c50794b72f53b45b5847b7dd30dfb650f290fe99e22928`.
Original-APK SHA256
`4b6bf5656dadfcb4a6b76baabe661c8d9f75f0a2d8c78431a4354f66a8192e3c`.
Originalquelle, Signierung und Downloadprovenienz bleiben in `source-releases.json`.
Keine neue APK für die aktuelle administrative Bereinigung.

## Tatsächlich geprüfte Software

Die folgenden Resultate gehören zu den genannten Appquellen, nicht zu einem neuen
Testlauf dieses Dokumentationsauftrags:

- 106/99ab63a: 79 JVM-Suiten, 441 Pythonprüfungen, zwei betroffene Backup-
  Browsersuiten, P1, Build/Lint/Privacy und Originalsignatur bestanden.
  166 Werkzeuge und 209 UI-Aktionen im Codevertrag. Lint: 0 Fehler/103 Warnungen.
- 106-Original-APK-Nutzlast aus drei tatsächlich zurückgelesenen Teilen erneut
  gehasht/signaturgeprüft. Die separate große rohe APK-ID war nur durch Metadaten
  und vollständige Streamgröße bestätigt; kein unabhängiger Bytehash dieser ID.
- Native106-UI-/Updateprobe nicht ausgeführt: Cloud-AVD verlor den Paketmanager.
  Kompilierte Tests sind kein nativer PASS. Keine produktive Pixelinstallation.
- 105/4087d8b: gemeinsame native Cloud-Abnahme 443 PASS/0 FAIL, fünf tatsächliche
  OS-Prozesspaare und fünf Flugmodus-UI-Fälle. Cloud-Suchlatenzziel RS-14 nicht
  erreicht; physische Suchleistung nicht gemessen. Kein produktiver Gesamt-PASS.
- 55 synthetische MCP-stdio-Szenarien sind historische Vorgängerevidenz und wurden
  nicht als neuer106-Lauf ausgegeben. Privacy- und Originalsigniergates bleiben.

## Offene produktive Transition

Die source-only Umsetzung106 entfernt produktive Drive-Geschäftstransporte,
Syncauslöser, Remote-Original-Fallbacks und alten Teilrestore; vorhandenes
verschlüsseltes Vollbackup bleibt. Historische lokale Evidenz und Finanzgates
bleiben separat erhalten. Keine neue Appfunktion in der aktuellen Bereinigung.

Der letzte vorherige105-Readback meldete `pixel_local_v2`, Vertrag3,
`backup_only` und `business_sync_allowed=false`. Gleichzeitig:130 ungeklärte
Beziehungsreferenzen,107 nicht lokal nachgewiesene Originale und vier historische
Konflikte. Der produktive Gesamtauftrag bleibt deshalb in der Integritätsklärung;
keine Migration, Queueleerung, Rechnungsfreigabe oder manuelle Freigabeflags.

App-/Serverkatalog damals166 Werkzeuge, gebundene ChatGPT-Verbindung117;
20 Lese-/29 Schreibwerkzeuge fehlen dort. Externer Connector-Refresh offen.
Kalender war verbunden, zeigte aber `CALENDAR_NETWORK`/Retry; kein heutiger
Live-Endnachweis. Aktuelle Bereinigung verwendet keine produktiven Tests.

## Backup und Gerät

106 vereinfacht die vorhandene Einrichtung: Konto/Ordner wählen → Notfallschlüssel
als Datei speichern, zurücklesen und getrennte Aufbewahrung bestätigen → bewusst
Backup einschalten. Keine manuelle Ordner-ID oder erneutes Abtippen nach Read-back.
Restore, Reparatur, USB und Historie bleiben zusätzliche geschützte Abläufe.

Letzte vorherige105-Gerätebeobachtung: Ziel, Schlüssel, Automatik und passender
Kontext noch nicht eingerichtet. Die privaten Sicherungsordner bleiben erhalten.
Der aktuelle Backupordner war beim letzten Bereinigungs-Readback leer. Ein
bereitgestelltes Installationspaket beweist kein vollständiges Betriebsdatenbackup.
Physische Einrichtung, echtes verschlüsseltes Backup/Remote-Hash und Datenerhalt
beim105→106-Update sind weiterhin auf dem Pixel zu prüfen, nicht automatisch ausführen.

## Nachgewiesene Drive-Bereinigung

Vorheriger ausdrücklich beauftragter Schritt:61 Verwaltungsordner und1216
Duplikatdateien mit2.256.778.662Bytes endgültig entfernt.1151 Textpakete,9 direkte
Git-Dateien und52 Teile von elf rekonstruierten vollständigen Quellarchiven
gegen Git geprüft. Vier weitere Review-ZIPs nach Erhalt ihrer eigenständigen
Versionshinweise entfernt.216 Originalobjekte unter denselben privaten IDs erhalten.

Alle Ziel-IDs/Ordner fehlten im Abschluss-Readback.15 unterbrochene Einzelantworten
über ursprüngliche IDs lesend geklärt, keine blinden Wiederholungen. Backupordner,
App/Installationspakete und Betriebsdaten unverändert.36 große Einzelarchive ohne
vollständigen Bytezugriff sowie drei Archive mit eigenständigen alten Quellen/
QA-/Bildnachweisen blieben erhalten. Kein Vollabschluss dieser Altarchive behauptet.

Vollständige IDs/Hashes/Lösch- und Verschiebebelege sowie die vier ursprünglichen
Versionshinweise liegen jetzt selbständig in GitHub:
`docs/verification/drive-cleanup-2026-10-26.json`. Das frühere Drive-Register ist
historische Evidenz und keine heutige Anweisungs-/Sicherungsquelle.

## Repositorystruktur und einzigartige Arbeit

Produktive Quellen, öffentliche Pflanzenassets, Host-Regressionsmodule, Tests und
Legacy-Fixtures bleiben erhalten. Es gibt keinen separaten Quellcodeordner pro
alter Appversion; diese Stände liegen in Git-Commits/Referenzen. Gespiegelte
Pflanzenassets sind benötigte Laufzeitpfade, keine entbehrlichen Altkopien.

Fünf ältere Arbeitsbranches sind nach frischem Remotevergleich vollständig in
`main` enthalten (jeweils null einzigartige Commits) und wurden atomar gelöscht.
Remote-Read-back: alle fünf Referenzen fehlen, `main`, aktueller Arbeitsbranch
und Pflanzenbranch unverändert. Namen/Original-SHAs waren vorher in GitHub-Commit
`3f9e41ceb99b44ee1cc8f1f1fd1cad79e6e232dc` gesichert; die vollständige Historie
bleibt erhalten. Beim damaligen Read-back waren alle13 Versions-Tags unverändert;
die jetzt geklärte Bereinigung steht oben. Branch `codex/pflanzenbibliothek-20261023` dagegen hat sieben
nicht integrierte Commits, letzter Stand
`7d499b225ed28f501333227e0ce92a54d4c5d542`. Öffentliche Pflanzenquellen und
redaktionelle Nachweise darin nicht als erledigte Kopie löschen oder ungeprüft
integrieren. Aktueller Arbeitsbranch bleibt `codex/revier-solo-20261023`.

## Validierung dieser Konsolidierung

48 Source-/Lifecycle-/Privacy-Regressionen,18 Transfer-Metadatenregressionen und
45 Originalsignier-/Installationsbaseline-Prüfungen bestanden:111 PASS/0 FAIL.
Source-Privacy:2325 Dateien/0 operative Seedzeilen PASS. Quellworktree-Privacy:
6002 Dateien einschließlich ignored/untracked PASS. Aktuelle Markdownlinks und
Installation-/Quelltrennung geprüft; alte CLI-Einstiege weisen vor Dateizugriff ab.

26 überholte Dokumente/Windows-Launcher aus dem aktiven Baum entfernt; Originale
mit Pfad/Hash/festem Git-Commit im Strukturprüfbeleg nachgewiesen. Die drei
Administrationsautomationen auf reine GitHub-Bootstraps umgestellt und zurückgelesen;
Zeitpläne/Aktivzustände unverändert (täglich pausiert, wöchentlich/monatlich aktiv).
Drei ältere technische Vertrags-/Abnahmeprotokolle und der Integrationsbericht
sind ausdrücklich historisch gekennzeichnet; die aktiven Signierschutzgates bleiben.

GitHub-Zwischensicherung `0050d1089af6866d12ec0fc8e89662a4929438a9`
exakt zurückgelesen. PR #7 ohne History-Rewrite nach `main` integriert:
`6092fa1d40b295453df28ef9e9300f94a60d7e33`, Remote-Read-back bestanden.
Die Android-Appdateien entsprechen unverändert dem Originalquelltag106.
Keine neue App-Abnahme oder veröffentlichte Umgebungskonfiguration daraus ableiten.

## Aufbewahrungsprüfung27.10.

51 Source-/Lifecycle-/Privacy-Regressionen und45 Originalsignierbaseline-Prüfungen
bestanden:96 PASS/0 FAIL. Neue synthetische Gegenproben: alte Quellen werden nicht
für aktuelle Quell-Wiederherstellung gewählt, Kandidaten dadurch nicht freigegeben,
fehlende Grenze wird abgewiesen; lokale APKs99 und107 bleiben auch bei registrierter
Retirement-Anfrage erhalten, synthetisches98 wird tatsächlich entfernt.

Quell-Wiederherstellung105/106 tatsächlich aus Git rekonstruiert:2338/2349 Dateien,
Privacy und jeweiliger Originalfingerprint PASS. Kein App-/Betriebsdatenrestore.
Der CLI-Pfad wählt jetzt die aufbewahrten Originalkandidaten statt stillgelegter
88–90-Tags. Keine Appcodeänderung und kein neuer APK-Bau.
Quellprivacy2326 Dateien/0 operative Seedzeilen und Worktree-Privacy6003 Dateien
einschließlich ignored/untracked PASS. Auf GitHub sind keine Releases/Release-Assets
vorhanden; ältere Installationsdateien werden im bestehenden privaten APK-Bereich
bereinigt. Rohbetriebsdaten wurden dafür weder gelesen noch geändert.

Löschumfang/Einzel-IDs, frische Identitätsprüfungen,16 NOT_FOUND-Readbacks,
elf ursprüngliche Tagobjekte und Historienerhalt:
`docs/verification/revier-version-retention-2026-10-27.json`. PR #9 vor Löschung
nach main integriert und exakt zurückgelesen (`00cc32c20153be9742033581971684bc87875888`).
Die14 vorhandenen Installations-/Begleitobjekte105/106 in beiden Ordnern
bytegrößen-/namens-/ID-gleich vorhanden; keine neue Byteprüfung behauptet.
Alle drei Backupordner per Metadaten erreichbar; kein Backupinhalt gelesen oder
verändert. Alte technische Nachweise sind erhalten; keine pauschale Drive-Löschung.
Nach tatsächlicher Löschung erneut96 relevante Regressionen und vollständige
Quell-Wiederherstellung105/106 PASS; JSON, aktuelle Links und Privacy aller fünf
geänderten Beleg-/Statusdateien PASS. Android-Appbaum gegenüber106 unverändert.
