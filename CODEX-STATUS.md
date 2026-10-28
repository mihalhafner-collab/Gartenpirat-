# Gartenpirat Arbeitsstand

Stand: 28. Oktober 2026. Repository: `mihalhafner-collab/Gartenpirat-`.
Entwicklungsanweisungen und neue Sicherungen ausschließlich in GitHub nach
`AGENTS.md`. Vollständige frühere Berichte sind im festen
[Vorbereinigungsstand a41fc67](https://github.com/mihalhafner-collab/Gartenpirat-/blob/a41fc67f9e2d74fb3912c688b0f57cf7476ec598/CODEX-STATUS.md)
erhalten und gelten nicht als neue Arbeitsanweisung.

## Prüfstand: Funktionstest von App 1.55.3 / Build 108 am 28.10.2026

Direkter Nutzerauftrag: vollständiges Testkonzept und nachvollziehbarer Bericht.
Geprüfter, unveränderter Quellcommit:
`9415c8da6c26314969b731bc01708c5e8f424349`. Spätere Übergabe-, Wissens- und
Statusänderungen sind kein neuer geprüfter Appstand. 1.307 Originaldateien gegen
Git-Blobs geprüft; Privacy über 1.571 Dateien PASS, keine operativen Testdaten.

464 eindeutige Pythonmethoden und 7 Node-Suiten PASS. Von 84 Java-Hostprogrammen
81 PASS, 1 Kamera-Sollkonflikt FAIL und 2 BLOCKED. Die drei zusätzlichen
Java/Python-Abgleiche sind keine weiteren Programme. 62 Browser-Suiten in
81 begründeten Ausführungen: 52 PASS, 1 FLAKY (Revier-Entwurf PASS→FAIL→PASS),
2 reproduzierbare Rechnungsdialog-Layoutfehler bei 320 px/200 % Schrift,
6 Testvertrags-/Timing-/Portabilitätsfehler und 1 binärer Exportblocker.
Eine weitere historische Git-Browsersuite und ein Python-Historienabgleich
bleiben blockiert. Originaltests und Appquellen blieben unverändert;
abgebrochene Folgefälle wurden nicht als bestanden gezählt.

Vier nachträglich verkürzte Browserlogs sind als Beweiskettenvorfall dokumentiert.
Alle vier vollständigen Originalhashes wurden durch unveränderte Nachläufe
reproduziert. Die Ursache der späteren Kürzung bleibt ungeklärt. Ein späterer
PASS beseitigt den instabilen Revier-Entwurfsbefund nicht.

Konzept: 169 Funktionen in 15 Fachgruppen, 212 UI-Aktionen, RS-01–RS-16 und
P-01–P-06. Die semantischen Quellkataloge stimmen mit der beobachteten Laufzeit
überein. MCP-Diagnose ausschließlich lesend: 13 Aufrufe über 6 Funktionen;
166 verfügbare Bindings gegenüber 169 Laufzeitfunktionen. Drei Wartungsbindings
fehlen. Keine vollständige Ausführung aller 169 Fachfunktionen behauptet.

Keine vollständige Funktionsfreigabe: native Android-/SQLite-Persistenz,
Pixelkamera/-galerie, TalkBack, reale Provider, zwei USB-Ziele und vollständiger
Geräterestore bleiben offen. 48-MiB-Kryptotests enthalten synthetische Medien
und einen Textplatzhalter namens database.sqlite, keinen Android-DB-Restore.
Vorbereitete Zahlungszustände beweisen keine native Buchungsfolge.
Kein produktiver Testschreibzugriff, keine Installation, Migration oder APK-Bau.

Einzelnachweis: `docs/verification/function-test-2026-10-28.json`.
Zwei gesondert geprüfte synthetische Dialogbilder:
`docs/verification/function-test-2026-10-28/`.
Das bestehende Nutzerdossier enthält Konzept, Ergebnisse und Restabnahmeplan:
https://chatgpt.com/space/page_7f18b1e34e808191b3851b6c15ebf582

## Aktuell28.10.: ADB-Kampagne vorbereitet, Geräteabnahme BLOCKED

Direkter neuer Auftrag aus diesem Gespräch in CODEX-AUFTRAG-GARTENPIRAT.md erfasst.
Frisches main05eac2d und die drei vorhandenen Arbeitsbranches verglichen.
main und neuester Appbranch771183d verbieten den Laptop weiterhin. Nur
Revier371384f enthält eine enge, noch nicht integrierte ADB-/Protokollausnahme.
Diese widersprüchlichen Branchstände wurden nicht als allgemeine Testfreigabe
ausgelegt. Gemäß Auftrag ausschließlich zulässige Vorbereitung über GitHub.
Aktuelles Arbeitsverzeichnis ohne Git-Checkout; keine lokale Quellkopie erstellt.
Keine Behauptung über ungeprüfte lokale Änderungen in anderen Arbeitsverzeichnissen.

Vorbereitung auf bestehendem Appbranch codex/heute-uebersicht-20261027, dessen
Appdateien dem Originalquelltag108/9415c8d entsprechen; kein neuer Appbuild.
Revier- und Pflanzenarbeit unverändert erhalten. Heute kein ADB-Gerät ausgewählt,
kein Modell/Android-/Versionsreadback, keine Installation, kein Appvorgang,
kein Rohlog/Privatbild und keine Produktivdaten gelesen oder verändert.

Frische Quellauswertung: main166 MCP-Werkzeuge/209 semantische UI-Gruppen;
App108169/212 (73 read,96 write;197 business,15 platform).
Aktuell exponierte Verbindung166 Werkzeugnamen, gleich main; gegenüber App108
fehlen get_equipment_maintenance, save_equipment_maintenance_plan und
record_equipment_maintenance. Nur Metadatenvergleich, kein aktueller
Installations-/Erreichbarkeitsbeweis und kein reproduzierter Gerätefehler.

64 Szenarien definiert und alle169 Werkzeuge/212 UI-Gruppen geplanten Szenarien
zugeordnet:39 BLOCKED,25 NOT TESTED,0 PASS/0 FAIL. Teilweise vorhandene Suites
sind keine vollständige Szenarienausführung. 50 vorbereitete Messzeilen haben
n=0 und Zeit-/Fehlerquoten null; keine neuen Leistungswerte oder Teilnehmer.
Lastprofile50/500,500/5000 und2000/20000 spezifiziert, nicht implementiert/generiert.
Vorhandene Factory bleibt minimal/edge_cases/scale mit1 Kunde/1 Auftrag und
maximal3000 Arbeitseinträgen; Macrobenchmark/Perfetto-Pipeline fehlt.

Konkrete Quellenbefunde: aktueller Cloudrunner akzeptiert syntaktisch RESULT0
und prüft Android-Kerndienste noch nicht; strengere Guards existieren auf Revier.
Alter Fonthostrunner verlangt Build98 und ist kein108-Einstieg. Keine
Produktänderung oder ungeprüfte Guardintegration. Suchpfad mit Vollscan/Sortierung
als plausible Kostenhypothese dokumentiert; keine heutige Laufzeitursache belegt.
Historische105-Cloudsuche772,582006ms p95 bei25 Samples bleibt ausdrücklich
historisch; kein aktueller Pixelwert, kein Vorher-/Nachhervergleich.

Größter erwarteter Nutzen: eindeutige isolierte Umgebung/Abnahmeguards;
realistische zentrale Factory plus sichtbare Release-Messpunkte; danach
tracegestützte Such-/Navigationsoptimierung mit Beziehungs-/Löschregressionen.
Produktive Transition-/Rechnungs-/Restore-/Versandgates bleiben geschlossen.

Einstieg: [tests/ADB-Testkampagne.md](tests/ADB-Testkampagne.md).
Matrix/Kataloge/Profile/Befunde: docs/verification/adb-campaign-2026-10-28.json;
Szenarien- und Mess-CSV liegen daneben. Dokument-/JSON-/CSV-/Quellenverweise
und nur eigene neue Inhalte auf Privacy geprüft. Keine erneute vollständige
Source-/Worktree-Privacyprüfung und keine Androidtests in diesem Auftrag.
Die eigentliche Geräteabnahme und Benchmarkimplementierung bleiben offen.

## Android Fachwissen für Codex recherchiert am 28. Oktober 2026

Der aktuelle Nutzerauftrag zur Android-Wissensgrundlage ist in der
[Android-Fachsektion des bestehenden Auftrags](CODEX-AUFTRAG-GARTENPIRAT.md#android-fachwissen-für-die-stabilisierung-vom-28-oktober-2026)
gebündelt: 27 geöffnete offizielle Quellen, zwölf auf Gartenpirat zugeschnittene
Themen, zehn priorisierte Prüffälle und ein nachvollziehbarer Reparaturablauf.
Es wurde gezielt Quellcode gelesen, keine vollständige App-Abnahme ausgeführt.

Geprüfte Recherchebasis: Arbeitsbranch `codex/heute-uebersicht-20261027` bei
`71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729`, dokumentierter Kandidat 1.55.3/108.
Die zuletzt gelesene Installationsdokumentation nennt 1.55.1/106; keine neue
Runtime-Abfrage oder Geräteinstallation. Der aktuelle App-Aufbau ist
Java/WebView/JavaScript mit nativen SQLite-/Owner-Pfaden.
Vorhandene Host-/Browserprüfungen ersetzen die offenen nativen und physischen
Nachweise für Start, Navigation, Medien, Updateerhalt und Backup/Restore nicht.

Bereits vorhandene Kernservice-/Paketmanager-Arbeit im pausierten Revier-Branch
und Entwurf #14 ist ausdrücklich berücksichtigt. Mehrere native Testmodi lesen
zusätzlich den normalen Default-Store: deshalb nur garantiert leere oder
synthetische Testumgebungen verwenden. Die Recherche hebt keine Pause auf.
Beobachtete Untersuchungsstellen im Lifecycle-/Callback-Code sind als
Hypothesen markiert, nicht als bestätigte Ursache der Gerätebeschwerden.

Abschlussumfang: Ergänzung dieses Status und des bestehenden Auftrags,
fachliche Gegenprüfung und exaktes Remote-Read-back. Keine Änderung des
Appcodes, kein Build oder Testlauf, keine Skill-/CLI-Installation,
keine Änderung der Cloud-Konfiguration, Signierung oder Betriebsdaten.
Die Wissensgrundlage ist im Codex-Leseweg verfügbar; ihre tatsächliche
Übernahme ist erst im nächsten Codex-Lauf anhand Branch/Commit/Leseweg belegt.

## Aktuell: private Claude-Übergabe vollständig bereitgestellt

Expliziter Nutzerauftrag vom28.10.2026 erfüllt: privater Drive-Ordner
[Claude](https://drive.google.com/drive/folders/1plepbi35nyIoE8SgSIm9-aCIpPmvQXEt)
mit19geprüften Dateien und kleinem App-Code-Unterordner. Einstieg über
`00_START_HIER_CLAUDE.txt`, danach vorhandene zentrale Projektstände/Regeln und
Dateiindex.10 kleine Code-Teile erschließen286App-Quelldateien. Zwei tatsächliche
UI-Vorschauen enthalten nur Beispieldaten. Vollständiger kopierbarer Text umfasst
1114lesbare Dateien. Quell-/App-ZIP und static-Medien-ZIP ergeben disjunkt alle
2394versionierten Dateien einschließlich1280Binärdateien; jede ZIP-Datei unter
der100-MiB-Grenze des Uploadzugangs. Zuerst abgewiesenes163MB-Gesamt-ZIP wurde
nicht in Drive angelegt. Keine Quellinhalte wegen der Größenaufteilung ausgelassen.

Unveränderlicher Übergabecommit `71c6fbfa4ce328bbec15fbbfbc7fad6ca6b29729` exakt
auf GitHub zurückgelesen. Die Übergabe enthält nur spätere Metadaten gegenüber
APK108; App-Quellfingerprint und Originaltag bleiben unverändert. Namen, Größen,
Elternordner und Privatstatus aller19Dateien sowie beider Ordner zurückgelesen;
START-HIER und Dateiindex inhaltlich exakt bestätigt. Lokale Archive/Textdaten
Privacy PASS; die Vereinigung beider ZIPs gegen das vollständige Git-Archiv
byteweise geprüft. Unabhängiger Remote-SHA der großen ZIPs nicht bestätigt;
Metadaten/Größen und lesbarer Einstieg sind geprüft.
Nachweis: `docs/verification/claude-source-handoff-2026-10-28.json`.

Eng begrenzte manuelle Ausnahme nach direktem Nutzerauftrag, keine Exportautomatik
oder zweite Regelquelle. GitHub bleibt Entwicklungs-/Regelzentrale. Keine
Betriebsdaten, privaten Signierwerte, .git-Historie oder Buildausgaben übertragen;
keine neue APK, Installation, Migration oder Rechnungsfreigabe. Die zuvor im Chat
genannten internen Workspace-Links sind keine funktionierende Downloadbereitstellung;
für diese Übergabe stattdessen den bestätigten privaten Drive-Ordner verwenden.

## Aktuell: Geräteprofilfoto, moderne Bedienoberfläche und Original-APK108 erstellt

Direkter Nutzerauftrag umgesetzt: optionales Gerätefoto aufnehmen/auswählen,
Vorschau beim Bearbeiten, Erkennung in Geräteliste und Detail; Austausch gehört
weiter nur zu diesem Gerät. Wartungsintervalle und Verlauf bleiben erhalten.
Die ausdrücklich verworfenen Entwürfe Revierbuch/Abendgrün werden nicht ausgeliefert.
Neue Gestaltung: Graphit, kühle helle Flächen, Limettenakzent, kompaktere sichtbare
Buttons mit mindestens48px Bedienfläche; Heute/Kunden/Büro und alle Fachwege bleiben.
Normale kurze Erfolgspopups sind beruhigt, Fehler/Undo/unklare Commits und nötige
Android-/Finanz-/Backupbestätigungen bleiben. Keine Android-Systemrechte verändert.

UI übernimmt direkt den vorhandenen privaten Browsermedia-Entwurf. Kein zweiter
MCP-Uploadcache entsteht beim UI-Speichern. Native Prüfung des vorhandenen
Formularjournals einschließlich Verwerfen, exakter Sitzung/Auswahl/Aktionskennung
und Installations-/Restore-Epoche; danach aktiver Medienverweis, Originalhash und
Einmalverbrauch im gemeinsamen lokalen Commit. MCP kann den vorhandenen Upload
als zweite Eingabequelle zum selben save_equipment-Fachpfad verwenden. Keine neue
DB, kein Kundenfoto, kein Drive-Geschäftsvorgang. Unklare Antwort: ursprüngliche
Kennung lesen; bestätigtes Gerät bei späterem Vorschau-/Cleanupfehler nicht erneut
anlegen. Vollbackup prüft Gerätefoto im tatsächlich erfassten/stagierten Verzeichnis;
fehlendes Original bleibt fail-closed, normale Geräteangaben bleiben bearbeitbar.
Neue Gerätefotos verwenden Equipmentformat3; alte Leser dürfen sie nicht schreiben,
Wartung setzt die Version nicht zurück. Schema6 und MCP-Protokoll9 bleiben.

43 relevante Browser-Prüfgruppen inklusive gezielter Wiederholungen PASS;
12 gezielte Abschlussgruppen erneut geprüft. Neue Fotofälle decken Abbruch,
Verwerfen, verspätete Auswahl/Antwort, neue Geräte ohne versteckte Anlage,
Offlinebetrieb, eindeutige Ablehnung, Austausch und verlorene Antwort/Reload ab.
54 Foto-,91 Wartungs-,328 Revier-,78 Medienjournal- und22 Medien-Dateiprüfungen PASS;
1405 UI/MCP-Paritätsprüfungen,169er-Discovery über vier Protokolle und45
Originalsignier-Schutztests PASS. Die Abschlusserstellung wird separat nachgewiesen;
ein früherer Compilefehler (fehlender File-Import) wurde korrigiert; die vollständige
Android-Kompilierung danach erfolgreich erneut ausgeführt.
Technische Kaskade: `docs/verification/equipment-modern-ui-2026-10-28.json`.
Die fünf modern-ui-*-390.png zeigen tatsächlichen Quellcode mit Beispieldaten.

Original-APK **1.55.3/108** fertig, signiert und im bestehenden privaten
Installationsbereich bereitgestellt. Release-Build, Lint0Fehler/103Warnungen,
Privacy und tatsächlicher Paket-/Quellabgleich PASS:705 exakt passende Assets,
205 aktuelle Produktionsklassen, leere Startdaten, kein Debug/Preview. Ursprüngliche
Paketidentität und Originalzertifikat stimmen mit107 überein; Nutzlast nach
Signierung unverändert. Unveränderlicher Quelltag `source/v1.55.3-build108` auf
Commit `9415c8da6c26314969b731bc01708c5e8f424349`, Fingerprint
`84e1d90175fe3a5863aaba14735f8ea25193fe5cf43005c99bc2e6e746315da5`.
Quellcommit vor Bau/Signierung exakt auf GitHub zurückgelesen; ein zuvor
verfrüht gestarteter Dirty-Bau wurde abgebrochen und nicht ausgeliefert.
Die vermeintliche Remote-Abweichung war eine veraltete lokale Trackingreferenz.

[APK1.55.3/108 herunterladen](https://drive.google.com/file/d/1s_5LCAinPU5MhXWAUhXkM2em1a1tiOhq/view?usp=drivesdk).
82903548Bytes; lokaler SHA256
`5bdaa005664ac2a436ac50b7b4acff3f19bcaa09ce05f3568a1c6147937bb7d2`.
Dateiname, Größe, privater Versionsordner und vollständiger authentifizierter
Abruf als Dateireferenz mit identischer Größe geprüft. Unabhängiger Remote-SHA
bleibt offen:83MB überschreiten die32-MiB-Materialisierungsgrenze; keine
Netzwerkumgehung. Entwicklungsquelle/Nachweise bleiben GitHub. Der spätere
ausdrückliche Claude-Übergabeauftrag ist die oben begrenzte Ausnahme.
Abschluss: `docs/verification/release-108-2026-10-28.json`.
64 synthetische Schutztests zu Signierung/Quellsicherung/Identität PASS.
Keine automatische Installation, keine produktiven Daten gelesen/verändert.
Offen bleiben Pixelkamera/-galerie, native Vorschau, physischer Update-Datenerhalt,
reale Wechsel-/Startzeit und vollständige geräteseitige Backup-/Restoreabnahme.
Keine Behebung der gemeldeten1s Wechselzeit ohne Messung behauptet.

## Historisch: Kundenpunkt und Designstudie nach APK107

Neuer Nutzerauftrag: unten Heute/Kunden/Büro, übersichtliche Kundendatei und
persönliches einfaches Design; gemeldete Wechselverzögerung von etwa1s untersuchen.
Der dritte Kundenpunkt ist in der Entwicklungsquelle umgesetzt. Kundenliste,
Akte und Bearbeitung gehören zum Kundenpunkt. Explizite Bottomwechsel und
kontextuelle Kundenauswahl haben unterschiedliche, geprüfte Rückwege; Suche,
Formulare und ursprüngliche Commitkennungen bleiben erhalten.

Zwei frühere klickbare, rein synthetische Entwürfe, inzwischen ausdrücklich verworfen:
**Revierbuch** (warmes Papier/Waldgrün) und **Abendgrün** (dunkles Grün/warmes Gelb). Große
Bedienziele, ruhige Listen, eine Hauptaktion je Ansicht; Zusatzangaben bei Bedarf.
Keine zusätzliche Produkt-App, Datenbank, Backendanbindung oder Fremdframework.
Die neue Bildsprache und die zusammengeführte Kundenübersicht sind Designstudie,
noch nicht vollständig auf die produktiven Masken übertragen. Der echte neue
Bottompunkt öffnet die bereits vorhandene Kundenliste und Kundenakte.

Praktischer Aufbau der Kundendatei; vorhandene Fachpfade weiterverwenden:

| Ansicht | Sofort sichtbar | Vorhandener Daten-/Bedienpfad |
| --- | --- | --- |
| Kundenliste | Name, Ort, Suche; Neu | `customers()`/HGDataOrder und bestehende Kundenanlage; keine Detailabfragen pro Listenzeile |
| Kundenakte | Kontakt, nächster Einsatz, Objekte | bestehender `openCustomer`/CustomerHub; Kontakte nur bei vorhandenen Angaben anbieten |
| Einsatzobjekt | Name, bestätigte Einsatzadresse, Zugangshinweise | `list_properties(customer_id)` und `get_property(property_id)`; Auswahl anhand der ID |
| Nächster Einsatz | Tätigkeit, Tag/Zeit, konkretes Objekt | `list_visits(customer_id,from_date)` mit Paging und Statusprüfung; bestehende Kalendertermine separat erkennen, keine Namens-/Datums-Deduplizierung |
| Arbeiten/Fotos/Dokumente | erst beim Öffnen der jeweiligen Rubrik | vorhandene Kundenakte, lokale Arbeitsdaten, Fotovorschauen und Dokumentpfade; Finanzgates beibehalten |
| Arbeit starten | bewusst gewählter Kunde; Objekt nur bei bekanntem Kontext | vorhandener WorkStart; auftragslose/allgemeine Arbeit weiterhin möglich; kein Start allein durch Öffnen |

Keine zweite Kundenkopie. Objektanzahl in der späteren Liste nur aus bereits
geladenen Daten ableiten, sonst weglassen. Nächster Einsatz und Objektabschnitt
der Studie müssen noch an diese vorhandenen Reads angeschlossen werden;
der aktuelle CustomerHub zeigt teilweise Kalender-/Garteninformationen.
Kontaktadresse, Einsatzobjekt und Rechnungsempfänger bleiben verschieden.
Bei mehreren Objekten keine erste Adresse als Einsatzort voraussetzen. Echte
leere Zustände ohne Kontakt/Objekt/Termin bleiben bedienbar und ehrlich.

Gezielte Korrekturen am Wechselweg: Heute verwendet die bereits vollständige
Tour aus `get_revier_overview`; nur ältere Antworten ohne dieses Feld verwenden
den bestehenden lokalen Tour-Read. Vorhandene widersprüchliche Tourdaten bleiben
sichtbare Lesefehler. Büro liest keinen kompletten Revier-/Tourbestand mehr nur
für eine optionale Anfrageanzahl. Frische unveränderte Kunden-/Auftragsoptionen
behalten ihre DOM-Knoten; tatsächliche Änderungen erneuern sie. Ein beim Wechsel
bereits verarbeitetes Änderungssignal löst keine zweite Timeraktualisierung aus.
Kein Geschäftsdaten-/TTL-Cache und keine ausgeblendeten fachlichen Fehler.

Recherche an tatsächlichem öffentlichen Quellcode aus drei Kategorien:
Fossify Contacts (bestehenden Listenadapter aktualisieren), Tasks.org (getrennte
Ansichtszustände und Änderungen gezielt verarbeiten) und AntennaPod (globale
Navigation sowie Haupt-/Detailansicht mit explizitem Rückweg). AntennaPod ersetzt
seine Hauptfragmente; daraus wird keine allgemeine Wiederverwendung behauptet.
Zusätzlich offizielle Google-Contacts- und Todoist-Bedienhilfen: Kontakt direkt
öffnen, kurze Erfassung mit optionalen Details. Keine fremde App ausgeführt oder
deren Geschwindigkeit gemessen; keine fremden Frameworks/Verzögerungen übernommen.
Exakte Commit-/Dateiverweise und Übertragung im technischen Nachweis.

41 bestehende Browser-Prüfgruppen,12 neue Navigationsfälle und11 Designfälle
PASS;1399 aktuelle UI/MCP-Paritätschecks und169er-Discovery mit vier Protokollen
PASS. Zwei alte Kundennavigations-Erwartungen und eine Root-ID-Liste an den
expliziten dritten Punkt angepasst; ursprüngliche Timeoutversuche dokumentiert,
betroffene Suiten danach erfolgreich erneut geprüft. Cloud-Mediane Heute55,6→55,4ms
und Büro46,4→47,7ms: kein belegter Gesamtgewinn. Owner-Reads pro Wechsel Heute3→2,
Büro1→0. Kaskade: `docs/verification/customer-design-2026-10-27.json`.
HTML und gerenderte Bilder liegen daneben. Nur Beispieldaten, keine Pixelaufnahme.
Privacy: Quelle2377Dateien und gesamter Worktree6287Dateien PASS, einschließlich
ignorierter/unversionierter Dateien; ergänzte numerische Prüfmetadaten separat
geprüft.0 operative Startzeilen.
Pixel-Wechselzeit und native Bedienung sind weiterhin offen; Browsermessungen
belegen keine Behebung der gemeldeten1s. Keine neue APK, Versionserhöhung oder
Installation aus diesem Auftrag. Das bereits ausgelieferte Originalpaket107
und sein unveränderter Quelltag enthalten diese späteren Änderungen nicht.

## Historisch: Original-APK1.55.2/107 erstellt

Explizit beauftragter Release-Bau aus sauberem, auf GitHub nachgewiesenem
Quellcommit `eb3df6b884cfcf00b3083ad32e6a957f2e83e85d`, unverändertem Tag
`source/v1.55.2-build107` und Quellfingerprint
`ff2a7d7fc78723fce48cc85d6e805b44a43059b7c09b3a8d13fb836dacaf0830`.
Originalpaket `at.hausgarten.service`, Version1.55.2/107 über beobachteter106,
minSdk26/targetSdk37, Release ohne Debug/Preview, DB-Schema weiterhin6.

Release-Build und Lint PASS:0Fatal/0Error,103Warnungen im Bericht. Obligatorische
Vorbuild-/Artefakt-Privacygates PASS. Offizieller Originalsignierer PASS, genau ein
Originalzertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`;
finale Signatur und16-KiB-Ausrichtung erneut geprüft. Temporäre Signierordner0.
64 gezielte synthetische Pythonchecks PASS (45Signierung/13Quellsicherung/6Identität).
703 Quellassets exakt gleich; beide generierten ART-Profile mit Compileroutput
abgeglichen. Alle204 aktuellen Produktionsklassen enthalten, alter
DriveDirectFiles-Transport fehlt weiterhin. Gepackte BuildConfig-Felder im DEX
geprüft, signierte Nutzlast unverändert zur geprüften unsignierten APK.
0 operative Startzeilen; keine Betriebsdaten für Entwicklung verwendet.
Finale Übergabeprüfung: Quelle2362Dateien, gesamter Worktree6212Dateien
und tatsächlich signierte APK PASS; ignorierte/unversionierte Dateien enthalten.
Final ergänzte Metadaten nochmals separat geprüft.

[Geprüfte APK herunterladen](https://drive.google.com/file/d/1ylfMaW2PD-F1Cqj7xcVxE3wFAbSWvayc/view?usp=drivesdk)
im bestehenden privaten Installationsbereich, neuer Versionsunterordner107.
Dateigröße82891120Bytes, lokaler SHA256
`7d04665514f7ef3cd62f9ecf3e972cc3c820e0147f7f8db619271c3037f7ba71`.
Drive-Dateiname/Elternordner/Größe zurückgelesen; vollständiger authentifizierter
Abruf als Dateireferenz mit derselben Größe gelungen. Unabhängiger Remote-SHA256
bleibt ausdrücklich offen: Cloud-Proxy verweigert Materialisierung der Dateireferenz
mit CONNECT403; keine Umgehung. Keine Quellen/Entwicklungsbackups nach Drive.

Paket enthält die bereits geprüfte einheitliche Bedienung, Gerätewartung und
Startkorrektur;169MCP-Werkzeuge/212UI-Aktionen. Die37Browser-Prüfgruppen und80Java-
Gruppen der jeweiligen früheren Quellprüfungen bleiben datierte separate Evidenz,
keine neu behauptete Pixelprüfung. Keine Installation, produktive Migration oder
Rechnungsfreigabe. Tatsächlicher Datenerhalt beim Update, native Startzeit,
Pixel-Bedienung und echtes Backup/Restore bleiben gesondert abzuprüfen.
Installiert beobachtet bleibt106 mit166Werkzeugen; erst nach separat ausgeführtem
107-Update den tatsächlichen169er-MCP-Katalog abgleichen.
Beleg: `docs/verification/release-107-2026-10-27.json`.

## Aktuell: MCP-Katalog der installierten App abgeglichen

Nach der vom Nutzer gemeldeten Aktualisierung ist die neue Bereitstellung jetzt
auch in dieser laufenden Verbindung sichtbar: installierte App1.55.1/106 und
ChatGPT enthalten jeweils166 Werkzeuge. Keine fehlenden oder zusätzlichen Namen.
Das zuvor fehlende rein lesende `get_restore_status` erfolgreich aufgerufen;
Phase NONE, keine Wiederherstellung ausgelöst. Aktuelle169er-Entwicklungsquelle
und ihre drei zusätzlichen Wartungswerkzeuge bleiben getrennt und uninstalliert.

Vor der Übernahme enthielt die Verbindung117 Werkzeuge, deren Namensmenge exakt
den Deklarationen des früheren Builds98 entsprach. Fehlend waren20 lokale Reads,
28 lokale Writes und zusätzlich `review_work_session`. Nutzeraktualisierung und
neuer Chat waren zunächst noch ohne sichtbaren Effekt; während dieser Prüfung
meldete der Nutzer166 und der anschließende echte Werkzeugvergleich bestätigte166.
Interne Scan-/Refreshzeitpunkte sind nicht einsehbar. Kein weiterer Chatwechsel
oder wiederholtes Aktualisieren für diesen166er-Stand erforderlich.

Original106 erzeugt in der isolierten Prüfung exakt dieselben166 Definitionen
wie die Live-Capabilities, einschließlich semantischem Hash. tools/list, vier
Protokollversionen und serialisierte Tunnelantwort behalten die komplette Liste.
Auch bei deaktivierten Writes bleiben lokale Reads sichtbar. Alle166 Schemata
nach JSON Schema2020-12 gültig; aktuelle169er-Quelle separat bestätigt. Kein
belegter Fehler an Produktionsquelle oder Schemata; keine Änderung an listChanged,
Version, Berechtigungen, Geschäfts-/Autoritätsgates oder Pluginpaket erforderlich.
Allgemeine Pixel-App-Berechtigung „Allow all actions“ und Schreibgate vorhanden;
produktive Writes wurden nicht getestet und Rechnungsfreigabe bleibt gesperrt.

Bestehendes privates App-Plugin lesend identifiziert: Paket mit App-Verweis und
Präsentationsmanifest, ohne eigene MCP-Liste. Pluginidentität, Paketversion und
Release blieben bei erfolgreicher Metadatenübernahme unverändert. Die verfügbare
Paketverwaltung ist kein Aktions-Refresh-Endpunkt. Keine zweite Verbindung erstellt.
OpenAI unterscheidet [Metadaten-Refresh](https://developers.openai.com/plugins/deploy/connect-chatgpt)
von gesonderten [Aktionsfreigaben für Workspace-Apps](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt).
Die vormals offene Frage zur sichtbaren Verwaltungsanzahl ist durch den echten
166er-Read-back überholt. Ein unabhängiger roher Live-Scanlog bleibt unzugänglich;
Quell-/Tunnelreproduktion und tatsächlich gebundener Katalog sind unterschieden.

Gezielte Nachweise PASS: neuer Discovery-Test auf Original106 und aktueller
Quelle, McpMetadataTests,65 Tunnel-Vertragsfälle und1399 UI/MCP-Paritätschecks;
Originalverträge/Quellfingerprints PASS. Normative Schema-Prüfung PASS. Discovery
rein synthetisch mit0 fachlichen Schreibaufrufen. Betriebsdatensätze, Backupinhalte
und private Laufzeitkonfiguration nicht in Prüfbelege übernommen. Privacy: Quelle
2361Dateien und gesamter Worktree6220Dateien PASS; finale geänderte Quelldateien
anschließend separat geprüft. Keine neue APK, Installation, Migration oder
Rechnungsfreigabe. Beleg: `docs/verification/mcp-catalog-diagnosis-2026-10-27.json`.

## Aktuell: Startkorrektur und Vergleichsrecherche

Startablauf im Quellcode korrigiert: feste Erststartpausen von50–700ms entfernt,
Abrechnungsfelder ausdrücklich hinter DOMContentLoaded eingeordnet, fertige
Formulardarstellung an den Revier-Aufbau gebunden. Echte Abhängigkeits-/Dialog-
prüfungen, Eingabe-/Foto-/Retryfristen und Fachgates bleiben erhalten. Die drei
Produktionsstile laden früh; ihre bisherige endgültige Kaskadenreihenfolge bleibt
erhalten. Sichtbar wird die fertige Bedienung mit Heute/Büro/Mehr. Ein kleiner
Startzustand überbrückt den Aufbau. Das ist keine fachliche Freigabe und keine
Abhängigkeit von Drive, Backup oder erfolgreichem Geschäftsdatensync.

Langer Start darf später fertig werden. Fehlende Skripte/Stile oder ein kritischer
Initialisierungsfehler zeigen eine verständliche Wiederöffnungsmöglichkeit;
kein automatisches Reload oder Rückfall auf die alte Oberfläche. Auch ein fehlendes
Startskript bleibt durch den statischen Startzustand geschützt. Vor bewusster
Wiederöffnung muss ein offener Dialog abgeschlossen werden. Gesicherte Entwürfe
und ihre IDs bleiben über Fehler und Reload erhalten. Fehler in späteren Abläufen
reaktivieren keine Startsperre. Wiederherstellungsprüfung bleibt getrennt.

Vergleich auf identischer synthetischer Fixture, je fünf frische Browserseiten:
Median ab Navigation1000,5→693,7ms; ab DOMContentLoaded555,4→222,5ms.
Sichtbare unfertige Zwischenframes32→0 je Lauf. Die externe Stilreihenfolge
entspricht im Test exakt dem Ausgangsstand. Das sind Cloud-Chromium-Werte,
keine Android-/Pixel-Kaltstartmessung. Native Startfenster, Java-/SQLite-Kosten
und der tatsächliche Gewinn auf dem Pixel bleiben ungemessen.

37 betroffene Browser-Prüfgruppen PASS, darunter die neue Startprüfung mit10
Gegenfällen/Messfällen. Katalogparität und ursprüngliche Fachverträge PASS;
unverändert169Tools und212semantische UI-Aktionen. Java-Fachregeln und native
Quellen wurden in diesem Schritt nicht geändert. Die80 Java-Prüfgruppen des
vorherigen Bedienungsstands bleiben dessen separat dokumentierter Nachweis.

Aktuelle Privacy-Prüfung: Quelle2359Dateien und gesamter Worktree6178Dateien
PASS, einschließlich ignorierter/unversionierter Dateien;0 Betriebsdaten-
Startzeilen. Kein APK-Artefakt erzeugt oder abgenommen.

Zehn neue Startprüffälle einschließlich fünf Messwiederholungen: normal, offline/
Backup nicht eingerichtet, lokaler Lesefehler, offener Dialog, langsames Skript
über acht Sekunden, fehlende Kernskripte/CSS/Startskript, Initialisierungsfehler
und bewusste Wiederöffnung mit demselben erhaltenen Entwurf. Keine fachlichen
Schreibvorgänge aus diesen Prüfungen. Betroffene Bedienungsregressionen sind
gegen den tatsächlich fertigen UI-Zustand geprüft. Keine APK erstellt/signiert/
installiert; keine Betriebsdaten oder produktive Geräteaktionen verwendet.

Originalquellenvergleich, ausschließlich als Recherchematerial:

| App / Quelle | Beobachteter Mechanismus | Bedeutung für unsere App |
| --- | --- | --- |
| [Signal – AppStartup](https://github.com/signalapp/Signal-Android/blob/6e9dddc01f1b26e608ac683807c51e139c141ed2/app/src/main/java/org/thoughtcrime/securesms/util/AppStartup.java) und [Startbenchmarks](https://github.com/signalapp/Signal-Android/blob/6e9dddc01f1b26e608ac683807c51e139c141ed2/benchmark/src/main/java/org/thoughtcrime/benchmark/StartupBenchmarks.kt) | Getrennte Pflicht-, Hintergrund- und nach dem Rendern ausgeführte Aufgaben; wiederholte echte Kaltstartmessungen. Die500ms-Frist betrifft nachgelagerte Aufgaben beim Hintergrundstart. | Fertigstellung an tatsächliche Voraussetzungen binden; Darstellung und native Kaltstartkosten getrennt messen. Native Profile erst nach Pixelmessung bewerten. |
| [AntennaPod – ClientConfigurator](https://github.com/AntennaPod/AntennaPod/blob/d3e41c6cb37c8e1a060b142686f818999de89859/app/src/main/java/de/danoeh/antennapod/ClientConfigurator.java) | Zentraler, synchronisierter und gegen doppelte Ausführung geschützter Aufbau; Dienste werden über ihre bestehenden Schnittstellen registriert. | Klare Initialisierungsreihenfolge und einmaliger Abschluss. Keine neuen Dienste oder Synchronisationspfade übernehmen. |
| [Now in Android – MainActivity](https://github.com/android/nowinandroid/blob/2982e63cb691ad49cd009aab43dc206fee6699c2/app/src/main/kotlin/com/google/samples/apps/nowinandroid/MainActivity.kt) und [Architektur](https://github.com/android/nowinandroid/blob/2982e63cb691ad49cd009aab43dc206fee6699c2/docs/ArchitectureLearningJourney.md), Google-Referenzapp | Startfenster richtet sich nach UI-Zustand; unveränderte Werte werden gefiltert; lokale Repositorydaten bestimmen die Anzeige. | Startdarstellung bis zur fertigen Oberfläche. Gezielte statt vollständiger Ansichtsaktualisierung bleibt ein nächster Messpunkt. |

Keine Fremd-App installiert oder auf Geschwindigkeit verglichen, kein fremdes
Framework integriert. Der vorhandene lokale Fach-/Datenaufbau bleibt erhalten.
Quellbelege und Messdaten: `docs/verification/startup-before-2026-10-27.json`
und `docs/verification/startup-2026-10-27.json`.

## Aktuell: einheitliche Bedienung im Quellcode

Nach der gezeigten Beispielmaske wurde die appweite Umsetzung ausdrücklich
beauftragt. Die gemeinsame Präsentation gilt für bestehende Seiten, Formulare
und dynamische Dialoge: gleiche Bedienelementgrößen, Rückwege und Aktionsleisten,
kurze Kernformulare, direkte Datums-/Uhrzeitauswahl und aufklappbare Zusatzangaben.
Heute/Büro und Mehr bleiben bestehen. Es gibt keinen zweiten Facheditor oder
neuen Speicherweg. Vorhandene Felder und Listener werden erhalten.

Arbeitsaufnahme: Beginn/Ende direkt sichtbar, Dauer ohne Uhrzeiten weiterhin
wählbar, Notizen/Fotos optional, Zurück im Kopf und Verwerfen neben Speichern.
Objekte, Anfragen, Pflege/Einsätze, Material/Geräte und Wartung verwenden dasselbe
Muster. Tätigkeit/Dauer und Heute/Morgen sind bewusste Auswahlen im Originalfeld;
Öffnen oder Gestalten erzeugt keine Geschäftssätze oder Entwürfe. Vorhandene
Zusatzwerte und Hinweise auf defekte Geräte bleiben sichtbar auffindbar.
Pflicht-/Fachfehler öffnen den betroffenen Zusatzbereich. Gesperrte Felder
sperren ihre Helfer; auch verspätete Knopfereignisse dürfen sie nicht verändern.

Kunden/Aufträge/Arbeit, Planung, Angebots-/Rechnungsdialoge und Unternehmensdaten
verwenden die gemeinsame Darstellung. Finanzbestätigungen und Aktionen wie
Ausstellen, Archivieren und endgültiges Löschen behalten ihre eindeutige Bedeutung.
Die vorhandene native Sicherung erhält einen Zurück-Knopf über ihren bisherigen
Unterbrechungs-/Entwurfsschutz. Wiederherstellungsprüfung und Android-Systemauswahl
bleiben getrennt. Keine Änderung an Fachregeln, Datenbank, Owner-/MCP-Schreibern,
Legacy-Queue, Backup-/Restoregates oder Rechnungsfreigabe.

Zurück verlässt Formularansichten auch bei aufgeklappten Zusatzangaben und
behält den Entwurf. Fortsetzbare Revier-Entwürfe werden nach der Rückkehr in die
Liste aktualisiert. Verwerfen nutzt die bisherigen vollständigen Entwurfslöschpfade.
Unklare Commitantworten behalten Originalkennung/Argumente und die vorhandene
lesende Prüfung. Erfolgreiche Commits werden nicht wegen Anzeige-/Fotofehlern
wiederholt. Gerätefoto-Zuordnung bleibt ein gesonderter offener Arbeitspunkt.

36 betroffene Browser-Prüfgruppen einschließlich8 neuer appweiter Bedienfälle
PASS. Alle80 Java-Prüfgruppen einschließlich des tatsächlichen lokalen Repository-
Adapters PASS; Quellparität1398Checks mit unverändert169Tools/212Aktionen PASS.
Android-Java vollständig mit `:app:compileReleaseJavaWithJavac` kompiliert;
kein APK erzeugt. Quelle2355Dateien und gesamter Worktree6128Dateien
(einschließlich ignorierter/unversionierter Dateien) bestehen die Privacy-Gates;
0 Betriebsdaten-Startzeilen. Kein auszulieferndes APK-Artefakt geprüft.
Kaltstartnavigation der Browserprüfung wird getrennt von
Interaktionsfristen gewertet; kurze Cloud-Ladezeitüberschreitungen wurden
kontrolliert erneut geprüft. Bisherige fachliche Assertions bleiben erhalten.

Prüfbeleg: `docs/verification/simple-forms-2026-10-27.json`;
Browserbilder: `docs/verification/simple-work-capture-360.png` und
`docs/verification/simple-visit-form-360.png`.

Prüfbelege und tatsächliche Browseransichten stehen in `docs/verification/`;
Pixel-Abnahme bleibt offen. Keine neue APK, Signierung, Installation oder
produktive Datenprüfung. Der frühere statische Bildschirm ist als Entwurf erhalten,
die neuen Browserbilder zeigen die echte geänderte Oberfläche mit Testdaten.

Die zuvor nur untersuchte Startverzögerung ist im aktuellen Quellstand korrigiert;
Messung, Gegenfälle und Grenzen stehen im vorangestellten Startabschnitt.
Die früheren539/540/563ms ab DOMContentLoaded waren ein historischer Vorlauf
vor der appweiten Bedienungsvereinheitlichung; der neue Vergleich nutzt denselben
jüngeren Ausgangsstand9f3112e und dieselbe Fixture vor/nach der Startkorrektur.

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

Der vorherige117er-Connectorstand ist überholt: Nach aktueller Metadatenübernahme
stehen alle166 Werkzeuge der installierten App bereit. Namenvergleich und neuer
Read-Aufruf erfolgreich; technischer Nachweis steht oben.
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
