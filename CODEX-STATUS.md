# CODEX-STATUS

Stand: 24. Oktober 2026
Repository: `mihalhafner-collab/Gartenpirat-`

## Auf Nutzerwunsch pausiert – 24.10.2026

Der Nutzer hat ausdrücklich „Sichern und vorerst pausieren“ angeordnet. Alle Agenten
haben ihre Quelldateien eingefroren; keine weiteren Implementierungs-, Build-,
Signier-, Geräte- oder Testaufgaben laufen nach der Sicherung weiter. Der vollständige
aktuelle WIP wird auf dem vorhandenen privaten Branch `codex/revier-solo-20261023`
committet, gepusht und der exakte Remote-Commit zurückgelesen. Kein Merge nach main,
keine Pixelinstallation, produktive Autoritätsumstellung, echte Rechnung/Zahlung
oder Nachricht wurde ausgeführt. **Der pausierte WIP ist kein geprüfter Release.**

### Letzter gebauter Original-Prüfstand und tatsächlicher Gerätetestumfang

| Nachweis | Ergebnis |
| --- | --- |
| Originalbuild aus `cc637c91f7c3f4eee6b59d04c495c0412d3d7525`,1.55.0/99 | assembleRelease/assembleReleaseAndroidTest/lintRelease PASS;0Fehler/109Warnungen. Keine Aussage über den späteren WIP. |
| Eingebetteter Quellfingerprint | `c3d70c8040cf6752be964e4bc09ff7865a6644835c198ef45ce4438830436ba8`; Commit und Fingerprint in APK-DEX nachgewiesen, SOURCE_DIRTY=false. |
| Originalsigniertes App-APK | SHA256 `a2cb6a2f5c616101a8c17a728f7c9de924f5635dbdeb3180d5824400091f85da` |
| Originalsigniertes Test-APK | SHA256 `0039f2ec1b2bb89bc6b4bb2e345e56ea3f1f24fee08789ffdc0600cf13c4544a` |
| Originalzertifikat beider APKs | SHA256 `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8` |
| Vorhandener synthetischer Cloudbestand, In-place-Update Schema4→5 | PASS: vollständiger Snapshot, IDs, Pendingaktionen, Einstellungen, deaktivierte synthetische Tunnelkonfiguration und laufende Sitzung samt Rechnungsempfänger erhalten. Keine Datenlöschung/Neuinstallation. |
| API30-x86_64-Cloudemulator ohne KVM, Flugmodus1 | Native Gesamtsuite teilweise ausgeführt:264 PASS/9FAIL, danach Host-Zeitlimit900s. Kein RESULT, kein Gesamt-PASS. Flugmodus danach nachweislich auf bisherigen Wert0 zurückgestellt. |
| Native Leistungsziele, isolierter Cloudemulator |20Tourstopps506,25ms; vollständige Suche300Kunden/450Objekte,3Warmups/25Messungen p95=3597,75ms,300ms-Suchziel NICHT erreicht. Kein Pixel-Leistungsnachweis. |
| Physischer Pixel, erneut nur get_capabilities | Unverändert1.54.9/98,DB4,Protokoll9/117Tools,pixel_drive_v1/android_provider,invoice_finalization=pixel_drive_online_compare_required; dbe9ed0b/fd0979de. Selbstauskunft, kein unabhängiger Pixel-APK-Hash. |

Der partielle Native-Log liegt außerhalb des Checkout unter
`/workspace/revier-artifacts/cc637c9-native-partial.log`, SHA256
`98ff0d621c3cadf2d1affce401f5190f75c0ab8bf0ad80783e6a82b9701cd27d`;
passende Artefakt-/Umgebungsquittung in `cc637c9-native-partial-receipt.json`.
Der gescheiterte Gesamtlauf bleibt erhalten. Das signierte cc637-APK ist ein interner
Fehlersuchbuild und wird nicht als fertiger Revier-Updatekandidat ausgeliefert.

Die neun beobachteten Fehler: unvollständig aufgebautes synthetisches Schema2,
unvollständiger handgebauter Pending-Rechnungszustand, fehlendes weather_profile im
Besichtigungsadapter, fünf Finanzfixtures ohne exklusiven Migrationspermit und
UI-Pilot-Preflight auf dem falschen Thread. Quellenkorrekturen sind im WIP enthalten,
aber noch nicht mit einem neuen Original-APK nativ wiederholt. Der Besichtigungs-
Anwendungsfall validiert nun den echten Terminvertrag vor Effekten;99 fokussierte
JVM-Prüfungen bestanden. Beschädigter lokal gespeicherter Zustand bricht künftig mit
festem LOCAL_STATE_BUILD_FAILED statt einer teilinitialisierten Sicht/Rohfehlern ab.

### Zusätzlich gesicherte, noch nicht vollständig integrierte Korrekturrunde

- Gespeicherte Angebots-/Rechnungsentwürfe öffnen lesend; bewusstes Bearbeiten,
  Entwurfsrückkehr und ursprünglicher CAS-Stand bleiben erhalten. Bestätigte CSS-
  Überläufe behoben; zehn neue Entwurfsfälle und60Layoutfälle ohne Überlauf bestanden.
- Restore-PDF-Sicherheit: vorhandene exakt verifizierte Dateiquittungen bleiben
  wiederverwendbar. Neue Bytes unbekannter alter Pendingjobs benötigen passende
  Ursprungsinstallation/Epoche oder einen ausdrücklich aktuellen, beleggebundenen
  Nicht-Erstellungsnachweis. Externes OriginalABC wird über den vorhandenen SAF-Import
  und aktuelle vollständige Ledgerprüfung unverändert übernommen; finanzielle
  Snapshots, Leistungsquellen und Nummern bleiben fix. Recovery-Audit ist append-only
  und in Sicherung/Portabilität berücksichtigt. Native Wiederholung steht aus.
- Die Rechnungsausgabe bindet jetzt den angezeigten versionierten Prüfkontext samt
  Aussteller, Empfänger, Leistungs-/Materialquellen und Restmengen. WIP umfasst die
  gemeinsamen InvoiceIssueReviewRules/InvoiceIssueReviewSnapshot, lokale Ausgabe-CAS,
  additive MCP-Antwort/expected_context_hash sowie UI-Anzeige. Der UI-Fehler entity_hash
  statt record_hash ist korrigiert. Bestehende15Finanzbrowserfälle bestanden; dedizierte
  Kontextwechsel-/Layout-/Nativefälle und gemeinsame Integration fehlen noch.

Letzte schmale Rückmeldung vor der Sicherung: sämtliche aktuellen Produktions-Java-
Quellen kompilieren gegen API37 (javac PASS). Das ersetzt weder Gradle/Lint noch
Testausführung oder native Abnahme; Helpertests bleiben offen.

Zum Pausieren ausdrücklich offen gelassen: die aktuelle komplette Gradle-/Lint-/
Native-/MCP-/Privacy-Artefaktregression, endgültiger UI-Fingerprint-/Toolkatalogabgleich
und signierter Kandidat für DIESEN WIP. `reviewed_sources` und erzeugter Toolkatalog
sind absichtlich nicht ungeprüft freigegeben. Bei Fortsetzung zunächst die neuen
Pureklassen `LocalInvoiceCreationRules` und `InvoiceIssueReviewRules` in
`tools/run-followup-java.py` aufnehmen; neue *Tests.java werden bereits automatisch
erfasst. Die noch ungeprüften Helpertests und Kontextwechselprüfungen nachholen.
Der Hostrunner hat vorbereitete getrennte Modi revier_core/revier_ui und1800s für die
volle Suite; diese Modi sind im cc637-Test-APK noch NICHT enthalten. Erst aus erneut
sauber gesichertem Quellstand bauen/signieren und diese Gruppen prüfen, anschließend
volle Regression. Native Runtime/PDF-/Restorefälle sowie Suche-Leistung nachmessen.
Keine produktive Umstellung ohne aktuelle Original-/Nummern-/Schreiberprüfung.

### Dauerhafte Quellen und historische Datenschutzgrenze

GitHub bleibt maßgebliche Originalquelle. Die zusätzliche ältere administrative
Drive-Generation da7 wurde inzwischen vollständig abgeschlossen:53/53Dateien,
0fehlend/0Dubletten/0unerwartet,52TXT/JSON aktuell SHA256-bytegeprüft; das82.403.208Byte
XZ besitzt weiterhin nur vollständigen Streamgrößenbeleg, keinen Remote-SHA wegen
32MiB-Materialisierungsgrenze. Payloads→Indizes→00-START eingehalten, ab1 behalten.
Nachweise außerhalb Checkout: `/workspace/revier-da7-completion-report.json`,
`revier-da7-helper-receipts.json`, `revier-da7-complete-inventory.json`.
Diese da7-Kopie ist NICHT die zusätzliche Kopie des neuesten pausierten WIP.

Der aktuelle Quellbaum besteht die Datenschutzprüfung mit0betrieblichen Seedzeilen.
Ein separates ausschließlich lesendes Historienaudit über zwölf bekannte Bootstrap-/
Ausstellerobjekte markiert vier historische Objekte als sensibel. Keine Inhalte wurden
in Ausgaben oder neue Exporte übernommen, kein History-Rewrite/Force-Push ausgeführt.
Das ist keine vollständige forensische Historienprüfung. Alte sensibel markierte Git-
Objekte bleiben im privaten Repository erhalten; aktueller Quellbaum/APK und
historische Objekte sind ausdrücklich getrennte Prüfbereiche.

## Revier Solo – integrierte Fertigstellungsarbeit, 24.10.2026, 03:20 UTC

Auf ausdrücklichen Auftrag „Starte Fertigstellung“ wurde der vorhandene Java-/WebView-/
SQLite-Kern erweitert. Dieser Abschnitt beschreibt den neuen Arbeitsstand auf
`codex/revier-solo-20261023`, ausgehend vom gesicherten Commit
`613875759c2e29d6c8fc9efc18990e3b49763ea1`. Er ist noch kein neuer APK-/Pixelnachweis.
Die weiter unten belegten306 Androidtests und signierten APKs gehören ausschließlich
zum älteren da7c16f-Stand. Keine Produktivinstallation, Datenumstellung oder echte
Rechnung/Zahlung/Nachricht wurde ausgeführt.

### Implementierter gemeinsamer Stand

- Vier Bereiche Heute, Objekte, Pflege, Büro; Kunden bleiben eigene Entitäten unter
  Objekte. Bestehende Pflanzen-/Neophyten-, Foto-, Auftrags- und Mehr-Einstiege bleiben.
- Gemeinsame lokale Revier-Anwendungsfälle für Objekt, Anfrage/Besichtigung, versionierte
  Pflege, Besuch, manuelle Tourreihenfolge, Materialbewegung/Gegenbuchung, Geräte und
  vollständige Suche. Erweiterungen liegen in derselben vorhandenen SQLite-Datenbank.
- Explizite versionierte Angebotsannahme/-ablehnung mit Nachweis und Historie;
  wiederholte Umwandlung derselben angenommenen Fassung erzeugt nur einen Auftrag.
- Persistenter Timer übernimmt Objekt/Besuch aus dem unveränderlichen START-Ereignis;
  Abschluss und Fotos behalten diesen Bezug. Historische auftragslose Arbeiten und
  persönliche b.b.-Markierungen bleiben unabhängig von Abrechnung/Zahlung.
- Lokale Belegausgabe mit atomarer Nummernreservierung, eingefrorenem Snapshot,
  expliziten Quellmengen und dauerhaftem PDF-Job. Dokument und PDF haben getrennte
  Zustände. Dateiquittung vor SQL-Veröffentlichung erhält nach Absturz dieselben Bytes.
  Teilabrechnung verwendet die geprüfte Restmenge, ohne historische Stunden zu ändern.
- Manueller Zahlungsledger in Cent mit Teil-/Sammelzahlungen, Guthaben und
  nachvollziehbarer Gegenbuchung/Korrektur; keine Änderung ursprünglicher Positionen.
- Vollsicherung über native Einrichtung, Recovery-Code, tägliche aufschiebbare Jobs,
  Wiederholung, SAF/USB, vollständiges Rücklesen und getrennten tatsächlichen
  Drive-Servernachweis. `LocalBackupCoordinator` ist der sachgerecht umbenannte
  frühere Android-Capture-/Upload-Koordinator `BackupApplicationService`; die bestehenden
  reinen fachlichen ApplicationServices bleiben frei von Android-/SQLite-Abhängigkeiten.
- Restore wird zunächst vollständig in einem temporären Bestand geprüft und nur nach
  ausdrücklicher Entscheidung beim nächsten Prozessstart ausgetauscht. Alter Bestand
  und verschlüsselte Vor-Restore-Sicherung bleiben erhalten; keine automatische Mischung.
  Recovery funktioniert ohne alten Keystore; dessen bewusste Reparatur prüft zuerst
  ein bekanntes vollständiges Archiv. Klartext-Portabilitätsexport ist eine eigene Aktion.

Die SQLite-Version steigt von4 auf5 als Schreibsperre gegen alte Appversionen; die
bestehenden Tabellen, IDs und historischen Werte bleiben erhalten. Neue fachliche
Namespaces, Revisionen, Requestquittungen und Audit liegen im vorhandenen `owner_state`.
Die private, nicht aus Backups wiederhergestellte Autoritätsdatei bindet Installation,
Migrationsepoche, Restore-Epoche und Nummern-Höchststand. Restore sperrt die Ausgabe.
Ein alter Ledger mit neuer request_id genügt nicht: eine frische ausdrückliche aktuelle
Original-/Schreiberprüfung muss an das aktuelle Gerät und die Restore-Epoche gebunden sein.
Die externe Vollständigkeit dieser Originalnachweise bleibt ein bewusster Geräteschritt.

Die kontrollierte Umstellung verlangt ein aktuelles vollständig geprüftes externes
Vollbackup, Konflikt-/Originalprüfung und Nachweise über stillgelegte externe Schreiber.
Danach sind alte Drive-Schreib-, Lese-Fallback- und Zweiwege-Sync-Pfade gesperrt;
Drive dient ausschließlich Sicherung/Export. Laufende Legacyjournale werden weder
pauschal gelöscht noch durch einen älteren Cloudstand ersetzt. Nach Wiederherstellung
bleiben Nummernausgabe und alte externe Kontowiederaktivierung gesperrt.

Die anschließende Bestandsschutzprüfung ergänzt belegte Sevdesk-Altbelege auch ohne
`issued_at`; es wird kein Ausgabedatum erfunden. Eine vollständige Zahlungsrücknahme
verwendet `correct_payment` mit Betrag0, leeren Zuordnungen, Grund und ausdrücklicher
Bestätigung. Negative Gegenbuchung und sichtbare Nullquittung erhalten den Verlauf;
andere Teilzahlungen bleiben bestehen. Neue Zahlungen mit Betrag0 bleiben gesperrt.
Der echte gebündelte Android-UI-/SQLite-Pilot und25verschiedene native Bitmap-Fotos
sind jetzt vollständig als isolierte Testklassen vorbereitet. Der reguläre Gradle-
Compile von App und AndroidTests bestand in59s; neue native Ausführung folgt erst
nach gesichertem, sauberem Quellstand und Originalbuild. Kein Geräte-PASS daraus.

Releaseprüfung des gesicherten16ee087-Stands: beide APKs wurden assembliert,
`lintRelease` meldete14ERRORs/109Warnungen; deshalb **keine Signierung**. Die Fehler
betreffen API28-Heading-Aufrufe beiMinSDK26, zwei BigInteger-Exact-Aufrufe erst abAPI31
und unklare URI-Grantflags. Gezielte Korrektur: vorhandenes ViewCompat, kompatible
BigDecimal-Exact-Arithmetik und ausdrückliche geprüfte SAF-Konstanten. Kein Anheben der
MinSDK, keine Lintunterdrückung. Breite Warnungen sind überwiegend bestehende/neue
Textressourcen- und synchrone Preferencehinweise; ihr aktueller Gesamtstand wird im
folgenden regulären Build erneut ausgewiesen. Der gezielte Revier-Fachtest besteht
nach der numerisch identischen API-Korrektur erneut.

### Bisherige Prüfungen dieses Integrationsstands

| Umgebung / Eingabe | Erwartung | Tatsächlicher Nachweis / Grenze |
| --- | --- | --- |
| Vollständige Pythonregression, isolierte Fixtures | Schutzverträge/Privacy/Bestand bestehen |424 PASS,0SKIP;37 vorhandene Ersatzsignier-/QA-Schlüsseltests ausdrücklich ausgeschlossen. Erster Lauf fand den inzwischen durch sachgerechte Koordinatortrennung behobenen Architekturverstoß. |
| API37/JDK17, Produktions- und AndroidTestquellen | Java-Typen/API-Verbindungen passen zusammen | Gemeinsame javac-Prüfung PASS; kein Gradle-/APK-/Gerätebeweis und nach letzter Härtung erneut zu prüfen. |
| Tatsächliche gebündelte Assets im Chromium, synthetische Owner-Gegenstelle | Navigation, Geschäftsabläufe, Retry, Rechnung148EUR und60+88EUR |39 neue Pilotfälle PASS; darunter Teilabrechnung und200%Schrift bei320/360/426px. Gemeinsamer nativer UI-/SQLite-Pilot steht noch aus; diese Gegenstelle ist kein Gerätespeichernachweis. |
| Bestehende Browser-/Node-Regressionsskripte, synthetische Fixtures | Bestehende Geschäfts-/Timer-/Entwurfs-/CASabläufe bleiben |48/48 PASS nach zwei belegten CSSkorrekturen und gezielter Anpassung veralteter Navigations-/Palettenfixtures; Originalfehler erhalten. |
| Vollbackup-/Export-Dateiports,48MiB bei64MiBHeap | Streaming, Abbruch, fehlende/kaputte Datei, Commit/Replay |20 Transfer-,140 Austausch-/Rollback- und38 Portabilitätsprüfungen PASS; reale USB-/Driveanbieter noch nicht geprüft. |
| Gesamter JVM-Runner,72Suiten einschließlich tatsächlichem Repositoryadapter | Restmengen, Entscheidungen, Kontext, Geld, Schemas, Korruption und bestehende Regression |72/72 PASS nach regulärer Android-Kompilierung und finalem Vertragsabgleich; native Persistenz separat offen. |
| Bisher installierter Original-Cloudbuild mit Schema4 | Vorhandener synthetischer Update-Ausgangsbestand bleibt intakt | Bestehende lifecycle-verify-Prüfung PASS vor neuem Update. Erneutes prepare wurde sicher abgewiesen, weil der vorhandene Ausgangsbestand bereits besteht; nichts gelöscht/überschrieben. |

Zusätzliche Sicherheitsbefunde wurden im Code geschlossen: alte Kameraentwürfe werden
nicht wegen ihres Alters gelöscht; neue Providerzugriffe sind während Restore gesperrt;
eine allein verbliebene AtomicFile-.bak bleibt lesbar; Tunnel-Dienst/Bootreceiver stoppen
vor einer während Recovery verbotenen Storeöffnung. Frame-/srcdoc-Schutz und
vertrauenswürdige native Entwurfcheckpoints sind integriert. Lokale PDF-Teilen-Wege
verwenden verifizierte Originale und eingefrorene Empfänger.

### Aktuelle externe Quellen und verbleibende Freigabegrenzen

Am24.10. wurden die drei zentralen Pages erneut gelesen. GitHub-Connector meldete keine
offenen PRs und acht Branches; Remote-main bleibt e499e745, Arbeitsbranch war6138757.
Andere Cloudthreads sind mangels dauerhafter Thread-Leseschnittstelle nicht als beendet
nachgewiesen. Dieser fehlende Schreiberstilllegungsnachweis verhindert die produktive
Umstellung; er verhindert keine unabhängige Implementierung.

Open-Meteo gestattet den freien gehosteten Dienst weiterhin nur nichtkommerziell.
Der Kandidat kennzeichnet Wetter deshalb ausdrücklich als optionale, gewerblich
ungeklärte Erweiterung und bestätigt keine Lizenz automatisch. Bestehende Schalter,
Konten und Abrufe bleiben erhalten; kein Tarif und kein neuer Anbieter wurden aktiviert.
[Terms](https://open-meteo.com/en/terms). GeoSphere ist kein belegter vollständiger
Ersatz für die bestehende16-Tage-Prognose.

Die [USP-Rechnungsinformationen](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/rechnung.html),
[Kleinunternehmerregeln](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/weitere-steuertatbestaende-und-befreiungen/kleinunternehmen.html),
[Aufbewahrung](https://www.usp.gv.at/themen/steuern-finanzen/steuerliche-gewinnermittlung/weitere-informationen-zur-steuerlichen-gewinnermittlung/betriebliches-rechnungswesen/aufbewahrungspflicht.html)
und [E-Rechnung](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/weitere-informationen-zur-umsatzsteuer/vorsteuerabzug-und-rechnung/e-rechnung.html)
wurden am24.10. gelesen. Kein automatischer Wechsel des persönlichen Steuerprofils und
keine pauschale Rechtsabnahme. Der direkte Zugriff auf die datierte RIS-Tagesfassung
§11UStG24.10.2026 scheiterte mit nicht erreichbarer URL/HTTP503; dies bleibt ein fehlender
Tagesquellnachweis. Rechnungen an den Bund benötigen gegebenenfalls strukturierten
E-Rechnungsversand; ein PDF allein ist dafür kein vollständiger Weg.

Laut frisch gelesener [Android-Verifizierungs-FAQ](https://developer.android.com/developer-verification/guides/faq)
betrifft der Start30.09.2026 ausgewählte Stores in vier Startregionen, derzeit keine
direkten Sideloads in Österreich; weltweiter Ausbau ab2027. Daher keine aktuelle neue
Registrierungs-/Gebührenpflicht für den erhaltenen direkten Pixel-Updateweg behaupten.
Keine Registrierung oder Zahlung ausgelöst. Paket und Originalsignatur müssen erhalten
bleiben; die aktuell neue Gesamt-APK und deren Update-/Datenerhaltstest sind noch offen.

Offen bleiben die neue vollständige Native-/Gradle-/Privacy-/Originalsignaturprüfung,
der echte gebündelte UI-/SQLite-Pilot, die dokumentierte Softwareabnahme RS01–16 sowie
die getrennten Geräteschritte: Pixel/TalkBack/Kamera/Biometrie, reale zwei USB-Ziele,
provider- und serververifizierter Upload, aktuelle Original-/Schreiberprüfung und
bewusste produktive Entscheidung.10GB/60min ist ein ungemessenes Pilotziel. Keine neue
Produktivfreigabe aus diesem Zwischenstand ableiten.

## Revier Solo – geprüfter Zwischenstand am 24. Oktober 2026

Der Wiederholungslauf aus dem exakt gesicherten Quellcommit
`da7c16f064e677edf56f06bff3958282dab1dd39` ist abgeschlossen:
**306 Android-SQLite-/MCP-Prüfungen PASS, 0 FAIL**, darunter elf native
Sicherungsproben. **58 JVM-Suiten PASS**, einschließlich zehn Speicherreserve-
prüfungen. Der vorherige Deadline-Testfehler in f507 ist korrigiert; die unten
stehenden ausstehenden/failing Befunde bleiben als datierte Historie erhalten.
Kein neuer Funktionsumbau oder erneuter Testlauf durch diese Statusfortschreibung.

| Prüfung / Eingabe | Erwartung | Tatsächliches Ergebnis / Grenze |
| --- | --- | --- |
| API30-x86_64-Cloudemulator, isolierte qa_-Fixtures, interner Originalbuild1.55.0/99 aus da7c16f | Bestehende Regression und Sicherungs-/Epochenschutz bestehen | 306 PASS,0FAIL; kein Pixel-, USB- oder produktiver Google-Drive-Test |
| JobInfo-Tagesauftrag und Wiederholungsauftrag | persistent, Netzvorgaben, tägliche aufschiebbare Periodik, keine Deadline | PASS der erzeugten Android-Spezifikationen; keine Messung realer täglicher Hintergrundausführung |
| Gemeinsamer Sicherungsablauf mit synthetischer Gegenstelle und verlorener Uploadantwort | gleiche Generation/Datei, kein zweiter Uploadabschluss, Neuöffnung erhält Fortschritt | PASS; reale SQLite-/Keystore-/Dateipfade, synthetischer Netzwerkport |
| 58 JVM-Suiten, einschließlich48MiB-Streamingarchiv bei64MiB-Heap | Regeln/Replay/Backoff/Reserve und letzte gute Generation erhalten | PASS; Speicherverlust über isolierten Kapazitätsport, kein physischer Vollpartitionnachweis |
| App-/Testbuild, Originalsignierung und Privacy | originaler Schlüssel und exakter sauberer Remote-Quellstand | PASS; assembleRelease/assembleReleaseAndroidTest/lintRelease bestanden,0Fehler/55Warnungen |
| Physischer Pixelstatus am24.10., nur get_capabilities | Installation und Entwicklung unterscheiden | meldet unverändert1.54.9/98, DB4, MCP9/117Tools, pixel_drive_v1/android_provider; Selbstauskunft, keine unabhängige Pixel-APK-Prüfung |

Gebauter Quellfingerprint
`b7e7bfa533ab1e4e51d3ec79732e08f3becbb31b369302f25d368884f291cb64`,
App-SHA256 `8d20b108aaafa10f57e4a912d33727a19fa39ce99dceeec4ee8c3c40a3262195`,
Test-SHA256 `4d9a3aa5ffa6a485ac3ef8735fbff58dc32292483b5e336a8345360f0f79a87a`,
Originalzertifikat
`a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`.
Log-SHA256 `46611c3fb3b9bc891adfbfc88931a36c57937a5e741477ef1f139512a4f28124`.
Die Zuordnung beruht auf dem Runneraufruf mit den expliziten Original-APKs,
BuildConfig mit da7c16f/sauberem Quellstand und den APK-Prüfsummen; der Textlog selbst
enthält keinen eingebetteten Commit. Am24.10. stimmen die tatsächlich im dedizierten
Cloudemulator installierten App-/Test-APK-Hashes mit beiden obigen Originalen überein.
Keine Freigabe, Auslieferung, Pixelinstallation
oder produktive Autoritäts-/Datenumstellung und kein99-Release-Tag.

### Nutzbarkeit und offene Schritte

Der Timer-Prüfpfad und das lesende Migrationsinventar sind über bestehende UI/MCP-
Wege erreichbar. Aufnahme, verschlüsseltes Streamingarchiv, Keystore/Recovery,
dauerhafte Jobs, Upload-Wiederaufnahme und täglicher Android-Scheduler sind
implementierte Infrastruktur. **Der neue Vollsicherungspfad ist noch nicht über
UI/MCP einrichtbar und kein eingerichtetes automatisches Vollbackup.** Die bisherigen
create_backup/restore_backup verwenden weiterhin das ältere begrenzte Format.

Als nächster Integrationsschritt fehlen Geräteeinrichtung, echte Browser-/Formular-
Entwurferfassung, Statusanzeige und gemeinsame UI-/MCP-Aktionen. Danach bleiben SAF/USB,
kontrollierter atomarer Restore und globale Rechnungs-/Schreibersperre, vollständige
lokale Autorität, Objekte/Anfragen/Pflege/Touren, Material/Geräte, lokaler Rechnungs-
und Zahlungsledger sowie beide vollständigen UI-Piloten offen. Der Pixel meldet
weiter `pixel_drive_online_compare_required`. Keine vollständige Revier-Abnahme.

### Zusätzliche administrative Quellkopie bleibt unvollständig

GitHub enthält den exakten da7c16f-Quellstand. Im privaten Entwicklungsordner
[Generation da7](https://drive.google.com/drive/folders/1UXPhal4j-7dN8wkE3xBx1KtzgDDor9lR)
liegen laut frischer read-only Gegenprüfung am24.10. **24 von53 geplanten Dateien**:
23Textdateien mit bisherigen SHA256-Bytebelegen und ein82.403.208Byte großes XZ
mit vollständigem Streamgrößenbeleg, ohne Remote-SHA. Keine Dubletten oder
unerwarteten Einträge;29Dateien fehlen (26Payloads, zwei Indizes, Startdatei).
Alle53lokalen Artefakte stimmen in Größe/SHA mit dem Plan überein.

Original-ZIP-SHA256 `5f911ada5c60a5df1781c1bc923f773592b00dab8cfb33449696bca5c9d5b760`,
lokaler XZ-SHA256 `07e8334d7a56c23b373496b109d93ce2570f51308080ea8b99a812efe3171e6e`;
verlustfreier lokaler Rundlauf geprüft.32MiB-Downloadmaterialisierungsgrenze verhindert
einen Remote-Bytehash des XZ. Die flachen JSONL-Belege sind keine bereits normierten,
metadatengebundenen Wiederverwendungsbelege des Transferhelfers. Vor Fortsetzung
frisches Inventar/Plan und passende Belege erstellen; keine blinden POST-Retries,
keine Bereinigung und keine Vollständigkeitsbehauptung. Bisherige gute Generation
bleibt erhalten. Dies ist Quellcodesicherung, keine betriebliche Datensicherung.

## Revier Solo – Scheduler-Nachprüfung und freier Speicher für den lokalen Betrieb

Interner Originalbuild `f507c77ec3a9c98563d4f95bed9a7c890fea3f4a` (1.55.0/99),
API30-x86_64-Cloudemulator: **305native Prüfungen PASS,1FAIL**. Zwei neue Prüfungen
für Einstellungs-/Browser-CAS, Rollback/Replay/Neuöffnung/Restore-Epoche und den
automatischen Sicherungsablauf nach verlorener Uploadantwort bestehen. Der dritte
neue Fall scheitert ausschließlich an der erwarteten Deadline-Repräsentation:
Android30 meldet bei nicht gesetzter Deadline0; der Test hatte Long.MAX_VALUE erwartet.
Eine eigenständige native JobInfo-Probe ohne Appdatenzugriff bestätigt beide Werte:
Tagesjob86400000ms/Netz2/persistent, Einzeljob60000ms/Netz1/persistent, maxDelay jeweils0.
Die Testbedingung ist entsprechend korrigiert; Wiederholung noch ausstehend.
Der gesamte f507-Lauf ist **kein PASS und keine Releaseabnahme**. Sein unverändert
gesicherter Log hat SHA256
`fa7846af353887d3157dbee5de46733e1935dabf3b236cd78e4d8a28dc12ee9c`.

f507-App-SHA256 `d402a37e2cf50e2445988bbea0224f3d534f38addfd12fdc0516af93a1864b99`,
Test-SHA256 `5c97ae745d27f366a99e1759f4cb92ed88531e9585a6861075b5bd0103974c7c`,
Quellfingerprint `17f1d8c9d646eb5e809dec69fc0205fd0cc52d932630eafde60ae8b57c69473f`.
Originalzertifikat wie unten; sauberer Remote-Commit vor Build/Signierung geprüft.
assembleRelease/assembleReleaseAndroidTest/lintRelease PASS,0Fehler/54Warnungen.
Keine Pixelinstallation und kein Zugriff auf produktives Drive.

Danach implementiert: `BackupStorageBudget` hält256MiB freien Platz für lokale
Fachschreibvorgänge zurück. Dies begrenzt freien Arbeitsraum, nicht Archivgröße.
SQLite-Aufnahme prüft zusätzlichen Platz vor VACUUM, Kopie/Verschlüsselung/Prüf-
Extraktion prüfen während des Schreibens. Ein abgewiesener neuer Versuch lässt
Quellen und letzte gute Generation unangetastet. Sicherungsthread erhält Android-
Hintergrundpriorität. Unbekannte neuere Jobformate derselben Epoche halten den
Scheduler zur Prüfung zurück, statt unbemerkt eine neue Generation anzulegen.
**58JVM-Suiten PASS**, darunter10Speicherreserveprüfungen mit isoliertem
Kapazitätsport (lange Größen, Platzverlust während Schreiben, Abbruch, letzter
guter Stand). Kein physischer Vollpartition-/USB-Abzugnachweis daraus ableiten.
Neue App-/Instrumentierungskompilierung PASS nach56s. Die native Wiederholung
steht noch aus; Software-/Geräteabnahme bleibt offen.

Frische Repositoryabfrage über den vorhandenen GitHub-Connector gelingt, nachdem
Cloud-GitHub-REST/GraphQL403 lieferten: keine offenen PRs, acht sichtbare Branches.
main bleibt e499e745. Pflanzenbibliotheksbranch7d499b2 wurde nur gelesen/verglichen:
Integrationsanweisung, Revision2-Datenpaket und Verteilnachweis stimmen mit der
bereits übernommenen Kopie überein (49154b5); dortiger operativer Katalog ist älter.
Kein Zurücksetzen der aktuellen314Profile/628Bilder, keine fremde Arbeit integriert.
Andere laufende Cloud-Threads und externe operative Schreiber sind damit nicht
als stillgelegt nachgewiesen.

## Revier Solo – täglicher Sicherungsauftrag und native Prüffälle vorbereitet

Abnahmekriterien vor Umsetzung: standardmäßig ausgeschaltet; Einrichtung erst nach
bestätigtem Recovery-Code und ausdrücklicher Zielwahl; CAS/Request-Replay und atomare
Einstellungsbelege; Browsercheckpoint und Einstellungen nach SQLite-Neuöffnung;
Restore-Epoche hält alte Einstellungen/Jobs zurück; bestehende Generation vor neuer
Aufnahme fortsetzen; höchstens8Versuche und begrenztes Backoff; Geschäftssnapshot bleibt
unverändert. Keine Kontoerlaubnis oder operative Drive-Synchronisierung im Backupjob.

`BackupSettingsRepository` speichert diese versionierten technischen Einstellungen
und explizite Browsercheckpoints in der vorhandenen Datenbank. `BackupAutomation`
verbindet die Tagesregel mit dem bereits geprüften Aufnahme-/Uploaddienst.
`FullBackupJob` nutzt Android JobScheduler mit täglichem persistentem Auftrag und
separatem Wiederholungsauftrag, standardmäßig nicht getaktetes Netz. Keine exakte Uhrzeit,
kein Dauerdienst und kein Polling. Der Scheduler startet noch nicht automatisch:
Geräteeinrichtung/UI/MCP-Anbindung stehen als nächster Integrationsschritt aus.
Die neuen drei nativen Prüfungen sind vorbereitet, noch nicht ausgeführt.

Quellenzugriff23.10.2026: [JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder)
bestätigt aufschiebbare Periodik und Bootpersistenz mit vorhandener Bootberechtigung.
Ein erster Instrumentierungskompilierlauf scheiterte an einer nicht öffentlichen
Testabfrage (`hasLateConstraint`); korrigiert auf die öffentliche Deadline-Abfrage.
Kein solcher fehlgeschlagener Lauf wird als PASS gezählt. Die anschließende
App-/Instrumentierungskompilierung besteht.57JVM-Suiten,8 gezielte Python-
Vertrags-/OS-Backuprichtlinienprüfungen und Source-/Workspace-Privacy PASS.
Zusätzlich bestehen26Streamingarchiv-Fehlerprüfungen (48MiB bei64MiB-Heap),
7Dateipinning-/Kopierprüfungen und23Tagesregelprüfungen einschließlich Jobabbruch.
Noch kein nativer Ausführungsbeleg des neuen Schedulers.

Aktuelle österreichische Quellenprüfung und ersetzte historische Laptop-/PDF-Angaben
stehen in `RECHNUNGEN-OESTERREICH.md`; RIS-Normfassung nach erstem fehlgeschlagenem
Tagesabruf erfolgreich gelesen. Keine Änderung produktiver Steuer-/Ausstellerdaten.

## Revier Solo –303 native Prüfungen im exakten Sicherungsjob-Quellstand

Quellcommit `d2ecc05f44efa20bdb6d4ddc3bec50dd93795e05`, interner Original-
Prüfbau1.55.0/99: **303Android-SQLite-/MCP-Prüfungen PASS,0FAIL** auf API30-x86_64
in Codex Cloud. Die zuvor nur vorbereiteten vier Job-/Epochenschritte sind jetzt
ausgeführt: SQL-Rollback/Replay/CAS/Neuöffnung, separate Restore-Epoche und gemeinsamer
Aufnahme-/Verschlüsselungs-/Uploadpfad nach verlorener Endantwort. Dieselbe Generation
mit identischem Archivhash und genau einer externen Datei wird wiederaufgenommen.
Die Gegenstelle war ein isolierter synthetischer Port; kein produktiver Drivezugriff.
Bestehende Geschäftssnapshots bleiben unverändert. Acht native Sicherungsproben
insgesamt, keine vollständige RS-11-/Pixel-/USB-/Restoreaktivierungsabnahme.

App-SHA256 `8ea0920138683829831981b6b25d180ea79b7c8800931c38b1ab7656566ce56d`,
Test-SHA256 `ac5e8c0456650c3a36495beee6bed70899e19d885c6a2c35df064fc921591aa1`,
Quellfingerprint `2560839827fb0623bc3d591c73b419a833c55c6765471e722f8ba5af33cd44f9`.
Beide Originalzertifikat wie unten; sauberer gepushter Quellstand/source_preservation
vor Signierung PASS. assembleRelease/assembleReleaseAndroidTest/lintRelease PASS
(0Fehler,54Warnungen). Log-SHA256
`e992a6d78111eb5382a2958975e4cba5222145797f678b2978c158923bf1a92b`.
Keine Auslieferung/Pixelinstallation/Produktivumstellung; kein99-Release-Tag.

Anschließend57JVM-Suiten PASS, darunter21 neue Regeln für täglichen Sicherungs-
Rhythmus, maximal8Versuche, begrenztes Backoff, Ziel-/Restorebindung und tatsächliches
Datenalter. Das ist noch kein Ausführungsnachweis des Android-Schedulers.
Aktueller Remote-main unverändert e499e745; eigener Branch exakt d2ecc05 zurückgelesen.
Frische PR-Abfragen über GitHub-GraphQL und REST erhielten403; deshalb keine aktuelle Behauptung
über offene PRs oder ausgeschlossene fremde Cloud-Arbeit.

## Revier Solo – gemeinsamer Sicherungsablauf mit dauerhaften Jobs vorbereitet

Nachfolgend zum gesicherten Uploadadapter6f347eb: `BackupJobRepository` speichert
Job und Requestbeleg atomar in der vorhandenen owner_state-Haltung. Stabile
request_id, unveränderliche Eingangsbindung, Revisionen und persistente Upload-
Checkpoints statt Last-write-wins. Separate Device-/Restore-Epoche liegt in
noBackupFilesDir und wird nicht in die Sicherung aufgenommen. Wiederholung eines
Epochenschritts ist idempotent; andere Installation/Restore-Epoche hält alte Jobs
zur Prüfung zurück. Diese Sicherungs-Epoche ersetzt noch nicht die beauftragte
globale Migrations-/Rechnungsautoritätssperre.

`BackupApplicationService` verbindet denselben lokalen Aufnahmepfad mit den Jobs:
Browserzustand eindeutig binden → SQLite/Dateien aufnehmen → unveränderliches
verschlüsseltes Archiv schreiben/fsync → vollständig entschlüsseln und Referenzen
prüfen → optional über vorhandenen Drive-Transport hochladen/zurücklesen.
Nur technische Sicherungsjobs werden geschrieben; kein Drive-Geschäftsstand wird
eingelesen/angewandt. Nach Unterbrechung bleiben Dateistages/Jobcheckpoints erhalten.
Ein bereits festgeschriebenes fehlendes/verändertes Archiv oder veränderte
Aufnahmemetadaten wird nicht automatisch neu erzeugt oder mit neuem Hash akzeptiert.
Schlüsselwerte und Uploadsession-URLs werden nicht in Diagnosen ausgegeben.

App-/Instrumentierungsquellen kompilieren, Source-/Workspace-Privacy PASS.
Vier neue native Proben für SQL-Rollback/Replay/CAS/Job-Neuöffnung, separate
Restore-Epoche sowie den gemeinsamen Aufnahme-/Upload-Wiederanlauf sind vorbereitet,
**noch nicht ausgeführt**. Die299PASS unten gelten exakt fürab1a0c0, nicht als
Ausführungsnachweis dieser neuen Klassen. UI-Anbindung, täglicher Android-Scheduler,
SAF und kontrollierte Restoreaktivierung bleiben offen; keine produktive Freigabe.

## Revier Solo –299 native Prüfungen und Backup-Uploadadapter

Exakt gesicherte App-/Testquelle `ab1a0c026a9a8bff15a481b08ab77a713587e070`,
interner Prüfbau1.55.0/99, API30-x86_64-Cloudemulator:
**299Android-SQLite-/MCP-Prüfungen PASS,0FAIL**. Vier neue Prüfungen führen
Android-Keystore-Rückbestätigung/Replay, Recovery nach Löschung ausschließlich des
isolierten QA-Schlüssels, vollständiges SQLite-Abbild mit großem Unicodewert/BLOB
und Dataset→verschlüsseltesArchiv→temporärerErsatzbestand tatsächlich aus.
Journalbelege, IDs, Beträge, offenerTimer, Fotos/PDF, typisierte Einstellungen,
Browser-/Fotoentwurf, persönliche Markierung samt Migrationsflag und offener
technischer Job bleiben erhalten; aktive Originale unverändert. Dies ist keine
produktive Restoreaktivierung, keine echte USB-Prüfung und kein Pixelbeleg.

App-SHA256 `f80d884265f0af9b8424bfd5c733db8305e35d750f3f805baab4f8c4e9ff149f`,
Test-SHA256 `c6599069d08f14d6f24d4952c86f9998aea99286eb70fac6e38719fb397dbf7b`,
Originalzertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`,
Quellfingerprint `e6da2cb4843b96e28f60b001137f9c098f7524fcdcc1448247caf1621ccf3a0c`.
Build/App-/Testsignierung mitsource_preservation PASS aus sauberemRemote-Commit;
lintRelease0Fehler/53Warnungen. Nativer Log-SHA256
`c9ba19e09f4866d67e8bf0b91aa5f2bfaf834edc6a643ede1b4d70ec9767ce48`.
Keine Bereitstellung/Installation amPixel und kein99-Release-Tag.

Originalquellen dieses Prüfschritts extern gesichert:
[private Entwicklungsgenerationab1](https://drive.google.com/drive/folders/1iuPAdHOThezrpWICnEdX_e0mF4VDPI6j).
52Text-/Indexdateien bytegleich perSHA256 zurückgelesen, frisches Inventar53Dateien
eindeutig/privat; anschließend Prüfbeleg ebenfalls bytegleich zurückgelesen
(`1obg9mWkMmqicmSSU8GFpwWNVqePs4oU8`,SHA256
`3896c350f9aadd2292cb677d22472bbf209cebc45e4a95892d57a0054d1a30b7`).
ZIP-SHA256 `049f30f981d06319de1a75e4181129e7c91724ed28cd4015cad31a70edfec1ba`;
verlustfreierXZ82364352Bytes lokal rundlaufgeprüft und vomDrive-Connector als ganzer
Stream passender Größe zurückgelesen. Kein Remote-Bytehash fürXZ wegen32MiB-
Materialisierungsgrenze; Originalbytes inGitHub. Keine betriebliche Datensicherung.
Die danach beschriebene Uploadadapter-Änderung ist noch nicht in dieser Generation.

Danach: neuer Backup-Uploadadapter verwendet den vorhandenen Drive-Transport und
Kontozugang, ausschließlich für versiegelte Generationen. Vorab generierte,
dauerhaft zu speichernde Drive-ID verhindert Dubletten bei unklaren Antworten.
Resumable-Sitzungen/Serverposition,1MiB-Dateichunks und vollständiges Remote-
Streamingrücklesen/SHA256 statt RAM-Archiv. Kein Lesen/Anwenden operativer
Drive-Snapshots, keine Änderung/Löschung bestehender Generationen. Redirect-/Fremd-
Uploadziele, abweichende Datei/Eltern/Größe/Hash und widersprüchliche Range scheitern.
Ein vorheriger Erfolg wird bei neuer Prüfung nicht als aktueller Erfolg übernommen.

**56JVM-Suiten PASS** im nachfolgenden Quellstand; neueSuite380Assertions mit48MiB-
Datei bei64MiB-Heap, tatsächlichem Streaming-HTTP-Hashleser und synthetischem
Drive-Port. Chunk-/Endantwortverlust, Checkpointabbruch, Neustart, Replaykonflikt,
verfälschtesRücklesen und fremdesZiel geprüft. Kein Google-Konto/kein produktiver
Uploadtest. Ein zusätzlicher Vergleich erkennt während der Aufnahme geänderte
Legacy-Einstellungen und verlangt Wiederholung statt gemischter Sicherung.
Jobpersistenz/automatischer Android-Scheduler/UI-Anbindung bleiben zu integrieren;
dieser Adapter allein ist **noch kein automatisches Drive-Backup**.
Cloud-Neustart ließ Dateien erhalten, beendete aber die anschließenden Android-/
Python-Läufe. Abgebrochene Läufe sind kein PASS; die anschließende Wiederholung
besteht:423Python-Prüfungen/0Skip und App-/Instrumentierungskompilierung PASS.
37QA-Ersatzsigniertests bleiben ausdrücklich ausgeschlossen.

Primärquellenzugriff23.10.2026 jetzt über den vorhandenen Webconnector möglich,
während direkte Cloud-HTTP-Zugriffe auf die Dokumentationsdomains weiter begrenzt
sind. Tatsächlich gelesen:
[Drive-Uploadprotokoll](https://developers.google.com/workspace/drive/api/guides/manage-uploads),
[vorgegebeneDatei-IDs](https://developers.google.com/workspace/drive/api/guides/create-file),
[Android-Autobackup](https://developer.android.com/identity/data/autobackup),
[SAF](https://developer.android.com/training/data-storage/shared/documents-files),
[TinkStreaming](https://developers.google.com/tink/streaming-aead).
Diese Quellen stützen Protokoll-/Formatentscheidungen, ersetzen keinen Gerätetest.

[Open-Meteo-Bedingungen](https://open-meteo.com/en/terms): kostenlose API ist auf
nichtkommerzielle Verwendung beschränkt; keine gewerbliche Freigabe aus CC-BY
ableiten. Kein Tarif aktiviert und keine bestehende Verbindung abgeschaltet.
Kommerziell zulässiger kostenfreier Ersatz bleibt optional/offen.
[Android-Verifizierung](https://developer.android.com/developer-verification):
regionale Einführung seit30.09.2026, weitere Ausweitung2027; konkrete gewerbliche
Einzelgeräte-Verteilung vor Produktivinstallation prüfen. Keine neue Registrierung,
Gebühr oder angenommene Hobby-Ausnahme.
[USP-Rechnung](https://www.usp.gv.at/themen/steuern-finanzen/umsatzsteuer-ueberblick/rechnung.html):
Pflichtangaben/Empfänger, Leistungszeitraum und Dokumentart weiter prüfen;
strukturierte Bundesrechnung ist ein eigener Fall. GezielterRIS-Abruf der
Tagesfassung23.10.2026 war nicht lesbar. Keine vollständige Rechtsabnahme und
keine Änderung bestätigter Aussteller-/Steuerdaten aus diesen Quellen.

## Revier Solo – SQLite-/Dateiaufnahme und konkretisierte Drive-Rolle

Nutzerpräzisierung23.10.2026 ist im bestehenden Auftrag konsolidiert:
Funktionen/maßgebliche Speicherung ausschließlich Pixel/App; Drive ausschließlich
Sicherung mit regelmäßigem automatischem Upload aus der App. Kein Zweiwegesync
im Zielbetrieb. Automatischer Upload mit dauerhaftem Job/Remote-Prüfung noch offen;
Altbetrieb und produktiver Pixel bleiben bis kontrolliertem Übergang unverändert.

Neuer interner Aufnahmepfad (noch ohne produktiven UI-/SAF-Einstieg): SQLite erstellt
perVACUUM INTO ein konsistentes eigenes Abbild einschließlichWAL. Integrität,
Fremdschlüssel, alle Tabellen/Schema, Größe/SHA256 werden geprüft. Große Text-/BLOB-
Zeilen benötigen kein vollständiges CursorWindow. Live- und Sicherungsansicht lesen
das pending-Journal jetzt über denselben Code: Reihenfolge, getrennte MCP-Belege,
Undo-Schutz und Foto-Metadaten bleiben erhalten. Katalog-/Legacy-PDF-/Tombstone-Regeln
sind gemeinsam; kein eigener konkurrierender Fachbestand.

Unveränderliche Originale werden vor der DB-Aufnahme gepinnt, mutable private Dateien
separat kopiert/gehasht. Die Aufnahme umfasst sämtliche DB-Tabellen, private Dateien,
ausgewählte Entwurfscaches, bestehende Lösch-/Konfliktsicherungen, typisierte
App-/Planungs-/Mail-Einstellungen sowie explizit übergebene Browserentwürfe. Unicode-
Dateinamen bleiben erhalten; Symlinks/Pfadflucht und rekursive Aufnahmeziele scheitern.
Keystore/Recovery-Kapsel/aktuelle Backupstages werden ausgeschlossen. Bekannte
Zugangsschlüsselfelder der strukturierten Einstellungen werden ausgelassen;
historische Sicherungsevidenz bleibt verschlüsselt und darf keine Kontorechte oder
alten Schreiber aktivieren. Dieser Aufnahmepfad aktiviert keine Wiederherstellung.

Abnahme dieses Sicherungsschritts: unveränderte IDs/Journal/Zeiten/Beträge/Originale,
erhaltener Timer/b.b./offene Jobs/Entwürfe, echte SQLite-Rundlaufprüfung, Wiederherstellung
perCode ohne alten Keystore; Fehler lassen aktiven Bestand und gute Generation intakt.
Host/JVM: **55Suiten PASS**, darunter23Archiv-Fehler-/Grenzprüfungen mit48MiB bei64MiB-
Heap sowie Pin/Kopie/Unicode/Symlink-/Katalog-Bestandsschutz. **423Python-Prüfungen PASS**,
0Skip; bestehende37QA-Ersatzsigniertests ausdrücklich ausgeschlossen. App und native
Testquellen kompilieren. Vier neue echte Android-Keystore-/SQLite-/Dataset-Rundlaufproben
sind vorbereitet, aber **noch nicht ausgeführt**. Kein nativer PASS für diese neuen
Klassen aus den älteren295Prüfungen ableiten. Die erste Wiederholung stoppte korrekt
an veralteten UI-Quellprüfsummen; nach Review sind157Aktionen/118Tools unverändert.

Android-Autobackup bleibt deaktiviert; zusätzlich schließen explizite ExtractionRules
Cloudbackup und DeviceTransfer für alle privaten Domains aus. Zwei Policytests PASS;
kein ausgeführter OEM-/Ersatzgeräte-Transfernachweis.

Ergänzung zum älteren ANR-Befund unten: Nach Beenden des tatsächlich angezeigten
SystemUI-Dialoges wurde derselbe isolierte Accessibility-Lauf erneut ausgeführt:
Heute und Planung bei100/150/200Prozent Schrift PASS; ursprüngliche Emulator-
Schriftgröße/Dichte anschließend wiederhergestellt. Synthetische Lesefixture, keine
Geschäftsschreibaktion. Das ist kein TalkBack-/Pixelbeleg und keine Prüfung aller
neun noch zu bauenden Revier-Kernansichten. Der initiale Fehlbefund bleibt datiert.

Noch offen: konsistente UI-Draft-Erfassung im echten Ablauf, dauerhafte Sicherungsjobs,
automatischer Drive-Upload/Remote-Prüfung, SAF/USB-Pilot, vollständige referenzielle
Restoreprüfung/Aktivierung mit unabhängiger Ausgabesperre, lokale Autorität und PhasenC–F.
Keine neue APK ausgeliefert, kein99-Tag, keine produktive Installation/Umstellung.

## Revier Solo – native295-Prüfung und verschlüsselter Archivkern

Originalsignierte Appquelle `2fb5b8e9a2b243122b2e6039ae5c8161d9a3f9e5`,
Instrumentierungsquelle `1b00545c65aadca911547b98e34f5c7656caa444`:
**295 native Android-SQLite-/MCP-Prüfungen PASS,0FAIL** auf dem isolierten
API30-x86_64-Cloudemulator (Softwareemulation ohneKVM). App-SHA unten909846…,
Test-APK-SHA `3ab39bfc270e2d10afe55b18f4270e5e256a50bc03394251ee6fb60494503f19`;
beide Originalzertifikat. Die drei zuvor protokollierten Fixturefehler sind
gezielt behoben; keine Zeit-/Schnittstellenschutzregel wurde abgeschwächt.

Zusätzlich: Leerbootstrap isoliert PASS. Der erste UI-Aufruf verwendete den
leeren Bestand, obwohl die bestehende Probe gefüllte QA-Datensätze voraussetzt;
nach expliziter synthetischer Vorbereitung auf99 besteht der native UI-Lauf
mit Navigation, Arbeitsauswahl, Kundenakte, Planung/Entwurf, Papierkorb und
Verbindungsansichten. **Kein98→99-Updatebeleg.** Die zusätzliche Accessibility-
Probe wird durch ein tatsächlich fokussiertes Android-SystemUI-ANR-Fenster
blockiert; kein PASS daraus ableiten. TalkBack/Pixel und alle neun Revier-
Zielansichten bleiben offen. Zwischenzeitlicher Cloud-Zugriffsausfall hat die
Arbeitsdateien erhalten; die Sicherungspunkte aufGitHub waren unabhängig davon.

Neu danach: `BackupCrypto` und `EncryptedBackupArchive` verwenden Tink-Android
1.24.0 (Apache2.0; Primärquellen/Release-Metadaten am23.10.2026 gelesen).
AES256-GCM-HKDF-Streaming mit1MiB-Segmenten, frischem Zufallsschlüssel proArchiv;
Tink verschlüsselt diesen unter einem zufälligen256Bit-Recovery-Schlüssel.
Kein eigenes Verschlüsselungsverfahren. Recovery-Code mit Tippfehlerprüfsumme;
keine Passwortableitung, kein Internetdienst und keine laufende Gebühr.
Manifest und personenbezogene Metadaten sind innerhalb der Verschlüsselung.

Der Leser authentifiziert den gesamten Stream bisEOF vor ZIP-Verarbeitung,
prüft Version, Datei-/Größen-/Hashinventar und extrahiert ausschließlich in ein
neu angelegtes temporäres Verzeichnis. Pfadtraversierung, Dubletten, abgebrochene
Dateien, fehlender Platz und neuere Schemata scheitern ohne Bestandsaustausch.
Kein festes Gesamtarchivlimit;32MiB gelten nur als technische Manifestobergrenze.
`BackupRecoveryKeyStore` kapselt den Code lokal per Android-Keystore/AES-GCM in
noBackupFilesDir, verlangt Rückbestätigung und stabile Setup-Anfragekennung,
verhindert stilles Überschreiben/Neuerzeugen bei verlorener Kapsel und serialisiert
konkurrierende Instanzen. **Plattformlauf dieser neuen Keystore-Klasse noch offen.**

Tatsächlich ausgeführt:53JVM-Suiten PASS; darin21Archiv-Grenz-/Fehlerprüfungen
mit48MiB-Payload und25synthetischen Medienreferenzen bei64MiB-Java-Heap.
Falscher Code, beschädigter letzter Authentifizierungsblock, Trunkierung,
simulierter USB-Abzug/volles Ziel, Platzmangel, neueres Schema und ZIP-Pfadflucht
lassen Originalgeneration und simulierten aktiven Bestand bytegleich. Rücklauf
benötigt keinen alten Keystore. Das sind **Containerprüfungen**, keine vollständige
Gerätesicherung: reale SQLite-Aufnahme/Medienreferenzprüfung, UI-Entwürfe, SAF,
physische USB-Ziele, produktive Restoreaktivierung und Rechnungssperre nachRestore
sind noch zu integrieren. Der neue Archivpfad ist noch nicht produktiv erreichbar.
Die frühere Aussage „noch keine Kryptobibliothek integriert“ unten ist damit für
diesen nachfolgenden Entwicklungsstand ersetzt. Bestehende Sicherungswege bleiben.

Externe Zwischenquellen aus2fb5b8e im bestehenden privaten Entwicklungsordner:
`https://drive.google.com/drive/folders/1rEQlDxYJu5ykJuIfBVWlQ6TeOPUh0bi-`.
52Text-/Indexdateien einzeln hochgeladen und bytegleich perSHA256 zurückgelesen;
OriginalZIP161281003Bytes verlustfrei alsXZ82328264Bytes transportiert.
Lokaler XZ-Rundlauf ergibt exakt den unten genannten ZIP-Hash; Drive-XZ wurde als
vollständiger Stream mit passender Größe zurückgelesen, **kein Remote-Bytehash**
behauptet. Der Connector verweigert Uploads über100MiB, die Dateimaterialisierung
ist auf32MiB begrenzt. GitHub enthält die vollständigen Originalbytes. Diese
Generation enthält noch nicht den nach1b00545begonnenen Archivkern; keine neue
Auslieferung, kein99-Tag und keine Pixelinstallation/Umstellung.

## Revier Solo – native Erstprüfung und korrigierte gemeinsame Uhrenquelle

Erneuter originalsignierter API30-Cloudlauf aus
`2fb5b8e9a2b243122b2e6039ae5c8161d9a3f9e5`: Referenztag135/150Minuten,
idempotenter Zweitstart, persistenter Boot-/Uhrprüfmarker und manuelle MCP-Prüfung
einschließlich Ursprungszeile tatsächlich ausgeführt und PASS. Nativer Gesamtlauf:292PASS/3FAIL von295Prüfungen; die drei Fehler liegen in
den unten beschriebenen Testvorbereitungen. Noch keine Gesamtfreigabe. Eine Altprüfung erwartet weiterhin52/35Bridge-Methoden statt
53/36mit der exakt genehmigten Prüfaktion; zwei weitere isolierte Foto-/Termin-
Fixtures mischen weiterhin FakeClock mit der Geräteuhr beim Lesen derselben
offenen Sitzung. Diese Testvorbereitung wird auf die gemeinsame injizierte Uhr
umgestellt. Keine Produktivregel gelockert; abschließender Wiederholungslauf offen.

Prüfbau2fb5b8e: APK-SHA256
`90984624275e55383e39f832d2b7ae46252610128d2cb7c7a14e8d9b498ce6a5`,
Quellfingerprint
`95e60d45114193010d42ccf1f5094d2d3624fccc0d96053204bba9e0be45683f`;
Originalzertifikat unten unverändert. Vollständiger Originalquell-ZIP lokal
bytegeprüft (`9f8878e0c5a0708ca7f4b6a0373891474193e4b6b4e7e8ae7ed94f47dc2be67b`).
GitHub-Branch exakt zurückgelesen, kein99-Tag und keine Auslieferung/Pixelinstallation.

Primärquellenergänzung: Tink1.24.0 wurde am23.10.2026 über Maven-Central-Metadaten
und die Maintainerquellen unter `github.com/tink-crypto/tink-java` (Tagv1.24.0)
lesend geprüft. Die zuvor genannten Dokumentationsdomains bleiben gesperrt.
Noch keine Kryptobibliothek integriert, kein Backup-/Recovery-Nachweis daraus.

Erster originalsignierter interner Prüfbau99 aus00974518426e1a2bdfcb07c5eba1223071a49ca3:
Signatur/Privacy bestanden, jedoch keine native Gesamtfreigabe. Der ältere native
Test mischte FakeClock und Geräteuhr in derselben SQLite-Sitzung; der neue Schutz
blockierte korrekt statt nach Bootwechsel zu schätzen. Gemeinsames Repository
bietet nun eine einheitliche injizierbare Uhrenquelle (Produktivstandard weiter
Android SystemClock/BOOT_COUNT). Bridge, Store und MCP verwenden dieselbe
Sitzungsinstanzgrenze; Test öffnet den Store auch nach Neustart mit derselben
synthetischen Uhr. Keine Lockerung der Zeitprüfung. Prüfung wird wiederholt.

Korrigierter Stand:52 JVM-Suiten und App-/Instrumentierungskompilierung PASS.
11 Zeitregelprüfungen, zusätzlich persistenter Prüfmarker beim direkten Startreplay
und tatsächlicher MCP-Korrekturpfad in der nativen Fixture. Der Auditbeleg
behält zusätzlich die vollständige ursprüngliche SQLite-Sitzungszeile einschließlich
der alten Segment-/Bootwerte, bevor die Korrektur aktive Segmentfelder schließt. Prüfdialog bewahrt bei
geänderter Revision die Eingaben und verlangt eine neue Bestätigung;5 Browserfälle
bei320/360/426px und200Prozent PASS. Kein geschätztes Arbeitsende.

Zusätzlich bleibt bei direkten Replay-Aufrufen der persistente Uhrprüfmarker
auch im Transaktionspfad erhalten. Fehlende Bootkennung erzwingt Prüfung statt
Vergleich eines unzuverlässigen wall-minus-elapsed-Werts. Erstbau99 hat SHA256
77eec9a4dae8888b2191e77a72d52ec778d34bed025fc0b82def2164c8f33865;
Originalzertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8.
Nicht ausgeliefert, kein99-Tag, keine Pixelinstallation. Exakte0097451-Originalquellen
als geprüfter ZIP-/TXT-Rundlauf außerhalb des Checkouts erhalten; nachfolgende
Quelländerung benötigt eigenen sauberen Commit/Remote-Readback und Neubau.

## Revier Solo – tatsächlicher Zwischenstand, 23.10.2026

**Auftrag läuft; Gesamtziel und Phasen B–F sind nicht abgeschlossen.** Kein
produktiver Autoritätswechsel, keine Installation und keine reale Rechnung,
Nachricht, Zahlung, Wiederherstellung oder Testanlage auf dem Pixel. Die neue
Entwicklungsversion1.55.0/99 ist von Original98 und der beobachteten Installation
getrennt. Ein künftig signierter Prüfbau ist keine vollständige Revier-Abnahme.

### Quellen- und Arbeitsabgleich

- Remote-main beim Einstieg `e499e745832449ce0202d04d9903495248e8b034`;
  aktueller Quell-/Abschlussbranch `codex/function-sync-audit-20261022` bei
  `419d0b4bfcbb7108d00296619831a04ddc3273b0`, enthält saubere Original98-Appquelle
  `dbe9ed0b4d8a0a60aee4c4c0a578c3fe4f40ae4c`. Main ist dessen Vorfahr. Alle sechs
  vorhandenen Remote-Arbeitsbranches eingelesen, keine offenen PRs gemeldet.
- Ursprünglicher Working Tree sauber; keine ungesicherte Arbeit überschrieben.
  Genau ein begründeter neuer Branch `codex/revier-solo-20261023` auf dem neuesten
  gesicherten Stand, um vorhandene Arbeitsbranches nicht umzuwidmen. Keine
  automatische Übernahme nach main. Andere gehostete Cloud-Threads sind über die
  verfügbaren Werkzeuge nicht direkt inventarisierbar; deren Ausführung ist nicht
  als beendet attestiert. Vor weiteren Integrationen Remote erneut prüfen.
- Bestehende zentrale Regeln, Arbeitsübersicht, Projektzentrale und MCP-Dossier
  gelesen. Konzeptseite erreichbar, liefert Präsentationsmetadaten statt Folien.
  Vollständiger Nutzerauftrag ist maßgeblich und in bestehendem Auftragsdokument
  nach Anforderungen, Fachpfaden, RS-Fällen, Kosten und Grenzen konsolidiert.
- Erneute reine MCP-Lesung im Task bestätigt App1.54.9/98, Paket
  `at.hausgarten.service`, DB4, MCP9/117Tools, QuellSHA dbe9ed0… und Fingerprint
  `fd0979de4da7c090beef01ec6e584ddfa5a999d79f138c870d40956e5aa4c1ae`,
  `source_dirty=false`, pixel_drive_v1/android_provider. Pflichtabschluss weiterhin
  `pixel_drive_online_compare_required`. Keine APK-/Zertifikatsprüfung des Pixels.
  Ältere96-Beobachtung in AKTUELLER-STAND.json historisch erhalten.

### Konkrete Implementierung

- Bestehende SQLite-Tabellen und DB-Version4 erhalten. WorkSessionStore speichert
  für neue unveränderliche Zeitereignisse reale Zeit, monotone Zeit, Bootkennung
  und Europe/Vienna. Nach Bootwechsel, negativem Intervall, >2min Uhrabweichung
  oder >24h ununterbrochenem Segment wird keine Dauer aus der Wanduhr geraten.
  Prüfbedarf bleibt als Ereignis gespeichert, auch wenn die Uhr zurückgestellt wird.
- Pausen neuer Ereignisse verwenden dieselbe Boot-/Zeitprüfung. Geschlossene alte
  Ereignisintervalle werden nicht umgeschrieben. Unklare offene Altpause benötigt
  Prüfung. Referenztag bleibt150min netto/15minPause, um10:30 sind135min erreicht.
- Zweiter Start führt zur vorhandenen Sitzung, ohne einen zweiten Timer zu erzeugen;
  direkte Startwiederholungen erhalten einen dauerhaften Ereignisbeleg. Anfrage-ID
  für andere Zeitaktionen wird zurückgewiesen. Bereits finalisierte Sitzungen
  können nicht nachträglich über den Prüfpfad geändert werden.
- Neue gemeinsame Aktion `review_work_session`: aktuelle stabile Revision,
  ganze Netto-/Pausenminuten, Grund, ausdrückliche Bestätigung und gegebenenfalls
  Übernachtbestätigung. Atomarer SQLite-Abschluss in STOPPED_PENDING_REVIEW;
  ursprünglicher Zustand/Ereignisse bleiben erhalten. Retry erzeugt keine zweite
  Korrektur. Keine erfundene Endzeit in Arbeitsnotizen. Kein automatischer Worklog,
  keine Abrechnung und kein Versand durch die Korrektur.
- Heute zeigt „Sitzung prüfen“. Eigener manueller Prüfdialog erhält Eingaben nach
  Konflikt; Öffnen fokussiert Überschrift statt Tastatur. UI und MCP verwenden
  denselben WorkSessionStore.118Tools/157semantische UI-Aktionen: alle bisherigen
 117Tools erhalten, genau eine fachliche Erweiterung. Original-P1-Baseline bleibt
  unverändert; enges versioniertes Vertragsdelta prüft ausschließlich diese
  Ergänzung und weist zusätzliche Altvertragsänderungen weiter ab.
- LocalMigrationInventory ist ein lesendes Inventar über effektive Geschäfts-
  und Archivbezüge: Counts, ID-Mengen-/Inhaltshashes, dezimale historische Stunden/
  Rechnungssummen, verwaiste Beziehungen, ausstehende Aktionen/Konflikte und lokale
  Originaldateien. Native Originalprüfung streamt32KiB-Blöcke, lädt nichts nach und
  ändert keine Quelldaten. Über vorhandenes run_self_test für UI/MCP erreichbar.
  **Kein vollständiges Geräteinventar, keine Sicherung und keine Aktivierung.**
  Einstellungen/Staging/gesamte SQLite-/Jobinventarisierung bleiben erforderlich.

### Tatsächlich ausgeführte Prüfungen und Grenzen

Cloud: vorhandenes JDK17/Android37.0/Gradle9.3.1, Python3.12, Chromium; ausschließlich
synthetische vorhandene Factory/Fixtures. Ergebnisse vor dem folgenden
Quellsicherungspunkt; exakten gebauten/signierten Commit erst beim Prüfbau festhalten.

| Prüfung / Eingabe | Erwartung | Ergebnis / Reichweite |
| --- | --- | --- |
| Unveränderter Einstieg, vorhandene Python-Suite | kein Ausgangsregressionsfehler |420Tests PASS; anfängliches Workspace-Gate separat unten |
| JVM-Ausgang | vorhandene Fachregeln |49Suiten PASS; zusätzlicher Repositorytest benötigte zunächst kompilierten Android-Code |
| Aktuelle JVM-Regeln/Repository, synthetisch | CAS, Rollback, Replay, Zeit-/Inventarregeln |52Suiten PASS, einschließlich14Prüfungen am tatsächlichen LocalOwnerRepository |
| Referenztag und Boot/Uhrsprung |150/135min; unsichere Dauer0 bis Prüfung |10neue reine Zeitregelprüfungen PASS; keine Geräteausführung daraus ableiten |
| Altformatinventar | exakte Dezimalwerte, fehlende Originale/Duplikate/Relationen sichtbar, keine Freigabe |16Inventarprüfungen PASS, synthetischer JVM-Speicher |
| Prüfdialog, Konflikt/Retry,320/360/426px,200% | Eingaben/ID erhalten,48px-Knöpfe, keine automatische Arbeitsbuchung |4Browserfälle PASS mit synthetischer Bridge |
| Aktuelle Python-Gesamtsuite | bestehende Regeln plus enger Vertragsnachweis |421Tests PASS |
| Android App-/Testcode und lintRelease | kompilierbar, keine Lintfehler |PASS;0Fehler/54Warnungen, keine Pixelabnahme |
| Breite Browserregression | bestehende Abläufe erhalten |56 Suiten PASS; Browser/Bridge, keine Pixelabnahme |
| Native SQLite/Boot/Prozess/Update/Restore | tatsächliche Android-Ausführung |Instrumentierung erweitert/kompiliert; Ausführung noch offen |
| Aktuelle Primärquellen Android/Tink/Open-Meteo | Inhalt am Ausführungstag verifiziert |NOT TESTABLE: konfigurierter Proxy verweigert CONNECT mit403; kein Bypass |

Eingangs-Workspace-Gate scheiterte an21ignorierten älteren Ausgabedateien (20nicht
klassifizierte Bilder, eine Datei mit Roh-ID-Muster). Nur diese betroffenen
outputs-Dateien unverändert mit SHA-Manifest nach
`/workspace/revier-preserved-workspace-20261023` außerhalb des Checkouts verschoben,
privat0700/0600; nichts gelöscht oder neu als sicher klassifiziert. Danach Source-
und Workspace-Gates PASS. Aktuelle Browservorlagen erzeugen zu synthetischen
Bildschirmdateien passende Hash-/Fixture-Nachweise. Frühe Prüffehler (fehlende
JSON-Testabhängigkeit/Proxykonfiguration/Acorn, fehlende Android-Klassen sowie
strengere Toolzahl) sind im Task nachvollzogen und für folgende Läufe behoben.

### Noch auszuführen – keine Vollabnahme behaupten

RS-01/02/03/04/06/07/08/09/10/11/13/14/15/16 sind für den **Gesamtumbau** offen;
RS-05/12 haben lediglich die oben genau benannten Teilnachweise. Insbesondere
fehlen lokaler autoritativer Commit mit versionierter Schreiberumstellung,
verschlüsselte vollständige SAF-/USB-Sicherung und atomarer geprüfter Ersatzrestore,
Objekt-/Anfrage-/Pflege-/Tourabläufe, Material-/Gerätebewegungen, lokaler Rechnungs-
und PDF-Jobpfad, Quellmengen-/Zahlungsledger sowie deren durchgängige UI-Piloten.
Bisherige Online-Rechnungssperre und bestehende Drive-/Kalenderverbindungen bleiben.
Keine Restaufwandsschätzung vor beiden beauftragten vollständigen Piloten.

Aktuelle gewerbliche Wetterlizenz, österreichische Rechts-/E-Rechnungsfragen,
Android-Verifizierung, physischer Pixel/USB, TalkBack, Geräteboot/Flugmodus und
vollständiger Update-/Restorelauf sind nicht durch Quellcode/JVM/Browser belegt.
Vor tatsächlicher Umstellung: vollständige extern rückgelesene Sicherung samt
Recoveryprobe, aktuelle Belege/Reservierungen/Nummern abgleichen, alle Altschreiber
belegt stilllegen, eine Autorität/Epoche bestätigen; erst dann explizite
Geräteentscheidung. Kein alter Checkout/Backup darf den neuen Schreiber reaktivieren.

## Abschluss98 – Original-APK und vollständige Quellen privat ausgeliefert

1.54.9/98 mit314 Profilen / 628 Offlinebildern fertiggestellt. Appquelle
dbe9ed0b4d8a0a60aee4c4c0a578c3fe4f40ae4c / `source/v1.54.9-build98`; QA-Quelledbe9ed0b4d8a0a60aee4c4c0a578c3fe4f40ae4c separat.
APKffa7525b85ac47a0ec78cdd352cb5fb327293bd1138a9cd9784fa23f3f0c9997; App-Fingerprintfd0979de4da7c090beef01ec6e584ddfa5a999d79f138c870d40956e5aa4c1ae.
420 Python/50 JVM/55 Browser/55 MCP-stdio, betroffene native Pflanzen-/UI-/Bootstrap-
Prüfungen und Schrift1/1.5/2 PASS. 9tatsächlich gesichtete Bildschirmbelege,
restaurierte OS-Werte und installierte APK byteidentisch. Vollständiger
ursprünglicher synthetischer97-Ledger auch nach endgültigem98 unverändert.
Eigener Original97Testausgang, kein direkter Test mit ausgeliefertem97APK.
Fehlgeschlagene Testannahmen/erste Renderbelege historisch erhalten und behoben.
Native API30 inset/dvh fallbacks, wrapped large titles and non-overlapping credit/help layout; valid isolated QA draft and verified restoration. Initial transient UI interruption retained with passed same-APK recheck.

Private APK: https://drive.google.com/file/d/1SXrVaoq_rsGIKo57XeDu0CQDJXB5bepl/view
Originalquellen/TXT: https://drive.google.com/drive/folders/1iOo71s1cmHcVFtBfa3rVOc2bAf-68zbW
QA: https://drive.google.com/file/d/1K2Uf6J_xT7bW9ozezC3Fwpp3Cd1M4NyQ/view
Original-ZIPcfaa05c384d2e40ada1de495add2500a1bc590e3fb38879851992a62432a196f; vollständige verlustfreie XZ-Verpackung
und Review-TXT-ZIP. Große Drive-Dateien vollständiger Stream/Größe, kleine echte
Byte-SHAs. Unveränderliches App-Tag, Abschlussdokumentation gesondert gesichert.
Alle älteren Schutzmengen erhalten, keine Löschung, kein Reset, kein Main-Merge.
Pixel nicht installiert; historische letzte Beobachtung 96 unangetastet.
Keine produktive Google-/Sync-/Queueaktion. Nächster Task kann allein aus GitHub-
Branch, App-Tag und privaten Originalarchiven exakt fortsetzen.

## Fortsetzung98 – Kalenderpfad grün, echte Großschriftkollisionen behoben

Mit gültiger QA-Entwurfsfixture und bestätigter Wiederherstellung bestehen der
echte allgemeine Android-WebView-/Kalender-/Verbindungsdialogpfad und Pflanzen-
UI-Prüfung; keinerlei Änderung an Kalender-/Geschäftslogik nötig. Konkrete
Diagnose leer, schmutziger eigener Titelentwurf fortsetzbar, Tag/Woche vorhanden.

Alle9echten679Bildschirmdateien tatsächlich angesehen. Dabei bei200Prozent
Titel unter Schließen sowie Gestenhilfe über Bildnachweis erkannt. Deshalb679
nicht ausliefern. Kanonisches CSS lässt lange Überschriften umbrechen, hält
Kopfknöpfe frei und stapelt Bildnachweis/Gestenhilfe im natürlichen Flexfluss.
Native Abnahme prüft jetzt lokale Titelbreite und echte Abstände der Fußtexte.
Browserregression ohne inset/dvh bei320/360px und100/200Prozent PASS. Spiegel
regulär erzeugt und ausschließlich zugehörigen CSS-Reviewhash aktualisiert.
Neue Original-APK wird aus diesem gesicherten Stand geprüft, Version bleibt98;
kein98Tag/Pixelupdate vor vollständigen abschließenden Bild-/Artefaktgates.

## Fortsetzung98 – eigene QA-Entwurfsfixture korrigiert

Konkrete Kalenderdiagnose: Unexpected token q in JSON at position0. Ursache war
der eigene ältere Platzhalter qa_plant98_preserved_draft, kein Kalender-/Datenfehler.
Pflanzenprobe verwendet jetzt einen gültigen synthetischen JSON-Entwurf, bewahrt
beide Planungsschlüssel und bestätigt ihre bytegenaue Wiederherstellung auch bei
Fehlern. Nur exakt diesen bekannten alten QA-Marker im eigenen Test-AVD reparieren;
unbekannte/produktive beschädigte Entwürfe werden nicht gelöscht oder umgedeutet.
App67965b6 bleibt unverändert. Deren3Schriftskalen/Geometrien und9Bildschirmdateien
sowie installierteAPK-Bytegleichheit und vollständiger ursprünglicher97Ledger PASS.
Allgemeiner UI-Pfad wird nach Fixturekorrektur abschließend erneut ausgeführt.
Original679Quellen in privatem Drive vollständig mit6Belegen gesichert.

## Fortsetzung98 – korrigierte native Bildfläche PASS

App67965b6b963f8653fdeaa8e6e1f2f7eeb71d1c86 originalsigniert, voller Build/Lint
und erneut55Browser-Suiten PASS.4native Pflanzenprüfungen mit echten Geometrien
PASS: Dialogy14/523px innerhalb568px, Vollbildbild tatsächlich sichtbar und
Zurück korrekt. Ersten korrigierten Vollbildbeleg selbst geöffnet: Foto, Navigation,
Motiv-/Autoren-/Lizenz-/Änderungstext sichtbar.628Bitmaps/314Profile und erneuter
Datenerhalt weiterhin PASS; verstärkte Schrift-/Bildprüfung läuft.

Allgemeiner UI-Prüfbericht zählte eine erfolgreiche Aufräum-Assertion mit und
zeigte dadurch nicht die ursprünglich fehlgeschlagene Bedingung. Bericht nutzt
nun den ursprünglichen Assertion-Text und rein technische Kalenderdiagnose.
Nur Testbericht korrigiert; bisher keine Kalender-/Geschäftslogik verändert.

## Fortsetzung98 – tatsächlichen alten WebView-Layoutfehler behoben

Geometriediagnose auf API30 bestätigt: bisheriges Vollbild außerhalb des sichtbaren
Bereichs (y1402 bei640px Höhe), Bild-/Stage-Fläche0×0 trotz dekodiertem Foto.
Älterer WebView ignoriert inset-Shorthand und dynamische dvh-Einheiten. Vorherige
Appedf5113 deshalb kein ausgelieferter98Kandidat und kein98Tag; Quellen bleiben
als Vorprüfungsstände erhalten. Kanonisches Garten-CSS erhält explizite vier
Kanten sowie vh-Fallbacks und feste sichtbare Dialogposition mit begrenzter
scrollbarer Höhe. Regulärer Spiegelgenerator verwendet; zugehöriger geprüfter
CSS-Quellhash aktualisiert,117Tools/156Aktionen unverändert.

Gezielte Browserregression entfernt inset/dvh tatsächlich aus geladenen Regeln
und prüft sichtbare Porträt-/Vollbild-/Zurück-Flächen bei320/360px. Native QA
verlangt Geometrie und wartet auf WebView-VisualState. Allgemeiner Kalenderpfad
liefert weiter einen alten UI-Prüffehler; neue rein technische Fehlerdiagnose
wird mitgebaut, keine Testschwächung oder Geschäfts-/Snapshotänderung.

## Fortsetzung98 – native Schrift-/Darstellungsdiagnose

Schrift1.0/1.5/2.0 gegen unveränderte Appedf5113 technisch PASS und OS-Werte
restauriert. Tatsächlich geöffnete Bildschirmdateien zeigen leeres Vollbild/
verzögerte Rückkehrdarstellung; deshalb noch keine visuelle Abnahme behauptet.
Neue QA-Prüfung wartet auf WebView-VisualState/Frames, protokolliert ausschließlich
öffentliche Layoutgeometrie und verlangt sichtbare Bild-/Porträtflächen.
Allgemeine alte UI-Bedingung12 erwartete zusätzlich einen inzwischen entfernten
Arbeitswochen-Link im Kalender. Prüfung nutzt nun die tatsächlichen bestehenden
Kalenderknöpfe planning-day/planning-week und explizit schmutzigen QA-Entwurf;
keine operative Oberfläche oder Geschäftslogik dafür geändert.

## Fortsetzung98 – endgültige Appedf5113, QA-Vorbedingung korrigiert

Endgültige originale98-APK ausedf511343571165987ff72bb8793f2f6222692cb:
SHA256ecc3d60efecda932baf03f27b38854dd800382db0c7c8af8a803743b1e71267b,
App-Quellfingerprint19e2efe61d74905485bcee6231c8300599b9679bfe3278aebacedb8628c05cb0.
4/4native Pflanzenprüfungen sowie leeres isoliertes Android-Datenlager PASS.
Endgültige98ersetzt den vorläufigen98im selben synthetischen97→98Testlauf;
vollständiger ursprünglicher97Ledger unverändert, kein Downgrade/Reset.

Die zusätzliche alte allgemeine UI-Prüfung scheiterte an Bedingung12: Ein
unveränderter neuer Termin wird korrekt nicht als schmutziger Entwurf angeboten.
Test legt nun explizit eine ungespeicherte synthetische Titeländerung an und prüft
Fortsetzen; beide lokalen Entwurfsschlüssel werden anschließend exakt restauriert.
Nur androidTest/Status geändert. Die bereits geprüfte Appedf5113 bleibt unverändert;
separater originalsignierter Teststand wird gegen genau diese APK ausgeführt.
App-Originalquellenedf5113 vollständig mit XZ-Roundtrip, Review-TXT-ZIP und
6privaten Drive-Belegen gesichert:5SHA256_BYTES und1vollständiger Stream/Größe.
Schrift-/Bildsichtung und abschließende APK-Bereitstellung noch offen.

## Fortsetzung98 – Original-APK und native Erstprüfung

Originalkandidat aus304d5ee erfolgreich signiert, Paket/Zertifikat, alle690Assets,
314Profile/628Bilder, eingebetteter Commit/Quellfingerprint und16KiB-Ausrichtung
PASS.420Python/50JVM/55Browser/55MCP-stdio-Szenarien und Releasebuild/Lint PASS.
Isolierte Original97-Testbasis0430eaa → erster98Kandidat: Lebenszyklusprüfung
und vollständiger Counts-/Hashvergleich PASS, alle Daten/Queue/Timer erhalten.
628Android-Bitmapdekodierungen und echter Pflanzen-WebView einschließlich
Suche/Offlinebildern/Vollbild/Zurück/Entwurfsschutz PASS. Ein neuer nativer
Katalogtest erwartete irrtümlich eine zusätzliche entry-Hülle; bestehende
Pflanzenschnittstelle liefert direkt den Datensatz. Nur Testannahme korrigiert.
Schriftprüfungen wiederholen die betroffene WebView-Prüfung ohne erneute628fach
identische Bitmapdekodierung; echte synthetische Bildschirmbelege vorbereitet.

Software-AVD ohneKVM zeigt langsame Paketinstallation/SystemUI-ANR. Timeout
nicht als Erfolg gewertet; nach tatsächlichem Paket-/Installationsbeleg geprüft.
Physischer Pixel/produktive Google-Verbindungen unverändert. Noch kein98Tag.
Originalquell-ZIP161241502Bytes übersteigt100MiB-Connectoruploadgrenze. Verlustfreie
XZ-Verpackung82322704Bytes, vollständiger Roundtrip byteidentisch und beide
Privacygates PASS. Vorprüfungsquelle304d5ee plus49TXT-Bytebelege privat gesichert;
dieser Stand bleibt erhalten, endgültige korrigierte Quelle wird separat exportiert.

## Fortsetzung98 – Implementierungsgates grün, Version1.54.9/98

Alle55Browser-Regressionssuiten PASS nach korrekter Verwendung des eigentlichen
Playwright-Moduls und installiertem acorn. Vorherige Adapter-/Abhängigkeitsfehler
sind keine Produkt-Fixes. Integrierte Android-Haupt-/Testquellen und Release-Lint
42Tasks PASS; ein geänderter geprüfter Hash betrifft ausschließlich den generierten
plant-catalog.js-Adapter,117Tools/156Aktionen unverändert. Gesamtkatalogstand
ceb7c2f66ad975775e0b7c52101e1730de9ce649 exakt auf GitHub bestätigt.

Source2196/Workspace2657Dateien PASS/0operative Seeds nach unveränderter externer
Aufbewahrung des vollständigen alten97Buildcaches. Keine Privacy-Ausnahme.
Version jetzt1.54.9/98; Originalbuild/Signierung erst aus sauber gesichertem Commit.
Native Bild-/WebView-/Schrift-/isolierter97→98Erhaltungsnachweis und finale
Artefakt-/Drive-Prüfungen noch offen. Eigene AVD nach Root-/Qemu-/Namenprüfung
mit temporärem ro.hw_timeout_multiplier10 ohne App-/Datenreset neu gestartet,
keine Änderung des physischen Pixels oder produktiver Google-Verbindungen.

## Fortsetzung98 – vollständiger Import und erneuter Bytevergleich PASS

314App-Profile/628Offlinebilder tatsächlich integriert,181Bestands-IDs erhalten,
133Ergänzungen.293Bestandsderivate/335Originaldownloads nach tatsächlichen
Motiv-/Rechteprüfungen.304frische botanische Seiten/4080Feldgruppen abgeglichen,
Rosen-Drainage im aktuellen RHS-Fließtext bestätigt.314Einzelberichte binden
jeweils die beiden gewählten Derivathashes; historische Metadaten-/Rechtsprüfung
bleibt erhalten. Kein unabhängiger botanischer Expertennachweis behauptet.

Vollständiger Import und erneuter Check identisch: Katalog72f0a25d49740e1093a37bc7c02c059350e0f973e257298fda3965ab6570d710.
Spiegel636Outputs PASS. Importer bewahrt historische Bildbytes aus eingefrorenem
Git auch bei gewechselten Ausgabeslots; identische Caption/Autor-Paare werden
nicht mehr irrtümlich an dasselbe Altbild gebunden.25Importer-/21Bibliotheks-/
2Parserfälle und Pflanzen-Browserabläufe einschließlich628Offline-Dekodierungen
PASS. Vollständige Regressionen/Native-Prüfungen laufen, noch keine98APK-Freigabe.

Git-Zugang mit vorhandenem gh-Credential-Helper wiederhergestellt;0430eaa tatsächlich
gepusht/zurückgelesen, source_preservation PASS. Drive lieferte Original97als
vollständige Streamreferenz, aber lokaler Transfer>32MiB nicht möglich; blockierten
Direktabruf nicht umgangen. Eigener originalsignierter97Testausgang aus0430eaa
für transparenten isolierten Updatetest erhalten, ausgeliefertes97Tag/APK unverändert.
Neue eigene AVD musste wegen langsamer Softwaredarstellung vor Installation
auf480×960/Dichte240 umgestellt werden; keine App-/Datenlöschung. Native Resultate
noch offen. Keine Pixel-/produktiven Syncaktionen.

## Fortsetzung98 – Bildsichtung und abgesicherte Ersatzquellen

Alle628Kandidaten auf79beschrifteten Bildbögen tatsächlich gesichtet.24ungeeignete
oder doppelte Motive mit Referenz-ID/Dateihashes/Einzelgrund ausgeschlossen;
Ersatzbilder auf6weiteren Bögen geprüft, letzte Detailersetzungen laufen.
Importer sperrt ausgeschlossene Referenzidentitäten auch bei neuer Auflösung
oder Kodierung. Zusätzliche individuelle Fotoquellen separat mit datiertem
responseSha256/JSON-Beleg gebunden, historische Bibliotheksrevision unverändert.
25Importertests einschließlich negativer Ersatzquellen-/Motiv-/MPO-Fälle PASS.
Keine unabhängige botanische Expertenbestätigung behauptet.

415Python/50JVM-Suiten samt unverändertem P1-Vertrag117Tools/156Aktionen PASS.
Android-Haupt-/Testquellenkompilierung und Release-Lint42Tasks PASS. Die fehlenden
Google-Abhängigkeiten über regulären Proxy und System-CA geladen; Offlinefehler
und PKIX-Erstversuch nicht als Erfolg umgedeutet. Eigene synthetische AVD mit
API30 wird vorbereitet; kein physisches Gerät angeschlossen oder ausgewählt.

Drive-Metadaten des ausgelieferten97Originals lesbar, dessen Rohdownload liefert
im Connector wiederholt Transport closed. Für den isolierten98-Updatetest deshalb
ein sauber gesicherter97Testausgang aus dem aktuellen unveränderten Appcode,
ohne Checkout-Rücksetzung. Dieser eigene Testausgang ersetzt weder das bestehende
97Quelltag noch dessen ausgelieferte APK; der Unterschied wird im Nachweis erhalten.
Bild-/Native-/Artefaktabnahme für98 weiterhin offen; produktive Daten unverändert.

## Fortsetzung98 – Netzwerk erreichbar, tatsächliche Bildvorbereitung läuft

Am23.10.2026 den vom Nutzer genannten gesicherten Stand bdae3646 auf demselben
Arbeitsbranch wiederaufgenommen; main e499e745 und Bibliotheksbranch7d499b2
verglichen. Kein Reset, keine ältere Appkopie, keine Pixelinstallation oder
produktive Syncaktion. Ausgewählte Cloudrichtlinie enthält jetzt die benötigten
Bildhosts; echte NCSU-/RHS-/S3-/Commons-API-Abrufe gelingen. Commons-Einzelabrufe
mit429 wurden nicht als Erfolg behandelt; reguläre gebündelte imageinfo-Abfragen
liefern die individuellen Rechtebelege. Kein Proxy-/TLS-Bypass.

Neues tools/prepare_plant_images.py lädt ausschließlich öffentliche Kandidaten
in einen externen Prüfbereich, bestätigt frische individuelle Credits, hasht
Original/Derivat und entfernt Bildmetadaten. Es erteilt keine Sichtfreigabe und
ändert keinen App-Katalog. Erster Durchgang286vollständige Paare/578Dateien;
28unvollständige Profile werden mit gebündelten Commons-Belegen nachgearbeitet.
20bestehende Importertests erneut PASS. Vollständige Sichtprüfung/Import/98Build
stehen noch aus; frühere HOLD-Nachweise bleiben historisch unverändert.

Die wiederhergestellte Umgebung enthielt21ignorierte Alt-Ausgabedateien ohne
ausreichenden Privacy-Beleg. Ausschließlich diese Dateien unverändert nach
/workspace/preserved-preexisting-output-20261023 verschoben, jede per SHA/Größe
nachgeprüft; keine Inhalte geöffnet, keine Datei gelöscht. Externer Beleg enthält
nur Pfad-/Dateihashes, Counts und Kategorien. Source1339/Workspace1624Dateien
danach PASS/0operative Seeds. Alte Build-/SDK-Caches benötigen Nachladen;
fehlende Cacheabhängigkeiten und Java-CA-Vertrauen werden technisch eingerichtet,
keine Signatur-/Fachregeln gelockert. Version bleibt97bis zu vollständigen Gates.

## Auftrag98 – gesicherter Zwischenstand, ausschließlich technische Bildserverfreigabe blockiert

Alle unabhängigen Arbeiten und der tatsächliche Bildsichtungsumfang sind in
[Arbeitsbericht98](docs/verification/plant-library-1.54.9-98.md) und dessen JSON
festgehalten.20Importer-/21Bibliotheks-/2Parserprüfungen,55Browser-Suiten/8neue
Fälle,22Lifecycleprüfungen und P1-/Spiegel-/Source-/Workspacegates PASS. Quellstand
noch Version97, Ziel1.54.9/98erst nach vollständiger628-Bild-/314-Profilabnahme.
Kein neuer Build, Signierung,98Tag, Pixel- oder Synceingriff. Original97bleibt
unverändert edd16a4/Tag97/APK7e394a76; tatsächliche Erweiterungen im App-Katalog0.

Aktuellste tatsächlich beobachtete Cloudrevision37/enforced/current enthält
weiterhin keinen der sieben benötigten Hosts. Nutzerauthorisierung liegt vor;
nur deren tatsächliche Speicherung/Anwendung in der Umgebungskonfiguration fehlt.
[Konkreter freigegebener Umfang](libraries/plants/network-access-request.json).
Keine weitere Erlaubnis angefordert, keine Policy-/Proxy-/TLS-Umgehung.

Dieser vollständige Arbeitsstand wird nach sauberem Commit/Push/Remote-Readback
im bestehenden privaten Entwicklungsbereich unter
[Arbeitsstand-Pflanzenintegration98](https://drive.google.com/drive/folders/1AQbc0HfEUU84zgrgSeDmCpzO3HIivIkx)
gesichert; Manifest/Original-ZIP binden den exakten Commit und Hash, externer
Transferbeleg dokumentiert tatsächliche Rücklesungen. Es handelt sich um ungebaute
Originalquellen, ausdrücklich keine neue APK oder vollständige Bibliotheksfreigabe.
Bei Fortsetzung neuesten Auditbranch erhalten, nicht durch das ältere97Tag ersetzen.

## Auftrag98 – Import und Oberfläche geprüft, tatsächlicher Bildnachweis noch blockiert

Eigenständiger deterministischer tools/import_plant_library.py mit Vorschau/Check und strikt vollständigem Schreibgate; separates integrationsgebundenes Manifest314HOLD. Identische Bibliotheks-/Profilhashes, genau2tatsächlich dekodierbare JPEGs, Original-/Derivathashes, Rechte/Länderlizenzen, datierte Sichtbelege, keine EXIF/XMP, keine umbenannten/doppelten IDs und erhaltene zusätzliche App-Einträge. Unbelegte Pflegetexte nicht neu übernehmen, USDA kennzeichnen, Warnungen/Synonyme erhalten, EU-Wirksamkeit und CH-Anhang trennen. Erneute Bildausschlüsse können durch alte Metadatenfreigaben nicht überschrieben werden. Schreiben mit allen314offenen Profilen tatsächlich abgewiesen, App-Katalog und362Originalbildbytes unverändert.

Builder/Auditor lesen historische Katalog-/Bild-/Vokabularbytes jetzt aus eingefrorenem Git e499e745, nicht aus künftigen App-Assets. Revisions2-Daten nach Wiederholungsbau bytegleich.21Bibliotheks-/20neue echte Importer-Unitprüfungen,2Parserprüfungen PASS.55vollständige Browser-Suiten/0FAIL, darin8neue Fälle mit synthetischen Erweiterungsmetadaten: beide botanischen Namen finden dieselbe ID; Warnungen/Gruppe/fehlende Pflege/datiertes EU-/CH-Recht; Credits samt Änderungen in Vorschau/Vollbild;360×640/150/200Prozent,48pxBedienelemente, Offline/0Geschäftsschreib-/Außenabrufe. Diese Browsernachweise sind keine native98-APK-Abnahme. P1 bleibt117Tools/156Aktionen/Hash7119a0e0; keine fachlichen Rechte-/Request-/Syncänderungen. Nur zwei geprüfte UI-Dateihashes aktualisiert, historische Baselines erhalten; Spiegel regulär generiert.

335wiederverwendbar vorgesehene Bestandsdateien auf42beschrifteten Bögen tatsächlich angesehen,6Detaildateien zusätzlich geöffnet. Neuer integration-legacy-review.json dokumentiert8Motivausschlüsse und4Taxon-Holds, getrennt vom historischen335erMetadatenbestand. Anfängliche Übersichtszweifel bei Dryopteris331/Calendula234 nach Detailansicht ausdrücklich korrigiert; keine ungeprüfte Fremdartbehauptung. Keine unabhängige botanische Expertenabnahme. Neue externe Originaldownloads/Sichtungen/Freigaben weiterhin0, deshalb0neue Profile in der App. Pflicht ist628geeignete lokale Bilder für alle314Profile, nicht1140bloße Referenzen.

Nutzer hat Netzwerkfreigabe ausdrücklich erteilt; keine erneute Zustimmungsanforderung. Technisch beobachtete aktive Revision33 meldet weiterhin nurGeoSphere-/Paketmanagerziele, keine sieben Bild-/Quellhosts; konkrete direkte Proben schlagen fehl. Technische Speicherung dieser Hostliste in der Umgebungskonfiguration noch angefragt, keine Umgehung oder Politikdateiänderung. Source-/Workspace-Privacy nach Code/Review PASS1336/4387Dateien,0operative Seeds. Ziel98/1.54.9noch nicht angehoben, kein neuer Build/Originalsignierung/Tag oder Pixel-/Sync-/Queueeingriff.97-APK/tag edd16a4 bleibt unverändert verfügbar, weitere native-/Update-/APK-Gates folgen erst nach echter Bildfreigabe.

## Auftrag98 – neue APK mit erweiterter Pflanzenbibliothek, Umsetzung begonnen

Ausgang ist der vollständig gesicherte97-Abschluss6928ab55ea1a6dc953cb5b00150b3d495cd006ce mit unveränderlicher Original-APK/tag97. Frischen GitHub-main e499e745 und vorhandene Branches verglichen; neue Bibliothek aus7d499b225ed28f501333227e0ce92a54d4c5d542 ausschließlich unter libraries/plants übernommen, keine alte App-/Statuskopie. Kanonische181Profile/362Bildhashes vorab in eigenem technischen Tempnachweis festgehalten. Alle Heute-/MCP-/Synckorrekturen aus97 bleiben Grundlage. Ziel nach vollständiger Abnahme1.54.9/98; Version noch nicht angehoben, keine neue APK behauptet.

Bibliotheksrevision2:314ausgewählte Profile=181Bestand+133Ergänzungen,1140Fotoreferenzen,335eligible Bestandsderivate/27Ausschlüsse. Quellenmetadaten sind kein Bildnachweis; alle314historisch HOLD. Verbindliche Integrationsanweisung vollständig gelesen. Abnahme: eingefrorene historische Baseline e499e745, deterministischer manifestgebundener Import, stabile IDs, genau2belegte geeignete lokale Bilder pro übernommenem Profil samt SHA/Rechten/Sichtprüfung, datierte EU-/CH-Regeln, Warnungen/Synonyme/Quellenklima, Offline-/Such-/Vollbild-/Schrift-/Entwurfsprüfungen und Originalupdate mit Datenerhalt. Keine produktive Installation, Geschäftsdaten, request_sync oder Queueänderung.

Aktuelle Cloudrichtlinie30 ist enforced/observations_current, Originalsignierungsvariablen ready; Bild-/Quellhosts fehlen. Konkrete sieben Hosts zur Konfigurationsfreigabe angefragt, während unabhängige Adapter-/Prüfarbeit weiterläuft. Keine Umgehung, kein Metadaten-HOLD als Fotofreigabe. Aktuelles Source-/Workspace-Privacy vor Übernahme PASS/0operative Seeds;21historische Befunde des anderen Checkouts liegen hier nicht vor. APK-Auslieferung erst nach tatsächlichen vollständigen Gates; produktiver Pixel-/Drive-Sync bleibt gesondert offen.

## Auftrag97 – abgeschlossen: Original1.54.8/97, tatsächliche Nachweise und externe Grenze

Heute95 innerhalb eines Renderers wiederhergestellt, wichtige Aktionen unten,12 konkurrierende CSS-Orderregeln entfernt. Kompakter Timer, Terminzeit/Kunde bei360×616 auch mit gespeicherten Arbeiten sichtbar. Abgelaufene Auswahl, Scroll-/Rückgängigüberdeckung und enger MCP-Enumfehler korrigiert. Metadatenkatalog je Antwort einmal statt dreimal, Datumsformatierer wiederverwendet, kalte Kartendetails3→1/warm0. Dieser Header ist maßgeblich;9cc4e18war ein nicht ausgelieferter Zwischenkandidat.

Finaler gemeinsamer App-/Testquellcommit `edd16a44c13dc17a774cff26f91e760efc123848`, unveränderliches Tag `source/v1.54.8-build97`, Fingerprint `5bf13d01331b5492c0de05d691aa58e10c31fc744295683948d8adfc23a41595`. Original-APK-SHA256 `7e394a7603d79ffb264e1f113c122c1e1700453a82a3e9be521831a425df4e48`,36.217.051Bytes, Originalzertifikat erhalten. [APK](https://drive.google.com/file/d/19w2tUJRWlKr_0R37IU5jRkP34sCl6CTc/view), [QA](https://drive.google.com/file/d/1Z3pg6Be03ESDpc6iguHfQ7XrfIr09QvH/view), [Versionsnachweis](https://drive.google.com/file/d/1v22KPWGIMqNjtDkYFlvk9nZJn8feqx_r/view), [Bericht](docs/verification/today-mcp-1.54.8-97.md). [Originalquellen](https://drive.google.com/drive/folders/1dMX0byFIGCzOLSk3N3Cs6U4WVWyfZ3t8)1290Dateien/559UTF8/731öffentliche Binärressourcen, ZIP-SHA256 `eb2244f707fe620c32a6168a92f2c2bfb0f3f97f9d47bff5f1c54e25cb18da3d`.19Belege:18Byte-SHA+1ZIPStreamgröße, frischer Plan0Upload/19Reuse/0Duplikate. GroßeAPK ebenfalls vollständig gestreamt/Größe, kein Remote-SHA behauptet.

395 Python/0Skips/37 ausdrücklich ausgeschlossene Ersatzschlüssel-Altfälle,50 JVM/1050 Paritätsassertions,54 vollständige Browser-Suiten/13 Übersichtsfälle,55 synthetische stdio-,29 Kamera-/47 Cleanup-Hostfälle PASS. Finales APK:290 native+20 ergänzende Workflow-/Providerchecks/0FAIL; fresh_isolated/nativeUI und tatsächliche Fonts1.0/1.5/2.0 PASS.9Bilder angesehen, keine OS-Überdeckung. Originalfont1.0/480×960/Dichte240 restauriert, eigener AVD beendet. Nur dort reservierte QA-Namespaces bereinigt;0Reste, erneuter96→97-Master-/Pending-/Timer-/Relationsledger unverändert. Installierter APK-SHA exakt. P0 Source/Workspace/signedAPK PASS,0operative Seeds.

117 MCP-Tools über echte RPCs erreichbar,113 mit erfolgreichen Aufrufen,4 nur Guard-/Außenpfade;145 fachliche UI-Aktionen plus11 Plattform-/Darstellungsausnahmen. Keine vollständige Außenprovider-Abnahme behauptet. Letzter technischer Live-Read23.10.2026 06:45UTC: Pixel weiterhin96/998d71f/8a29c1b7; REMOTE_STATE_REFERENCE_MISSING, publication_chain,9Pending/0Review,cloud_confirmed=false. Kein produktiver Schreibtest, keine Pixelinstallation, kein request_sync/Queue-/Drive-Runtime-Eingriff. Produktive Pixel-/Drive-Abnahme bleibt offen und Aufgabe des Nutzers. Linkes Randmenü unverändert.

Finaldokumentation/QA-Stand wird in [Abschlussquellen97](https://drive.google.com/drive/folders/1rxp9cbTf2srnopH-X8p9Xar6ZGQViPAn) mit eigenem Commit/Manifest/ZIP vollständig gesichert; dies verändert weder APK noch97-Tag. Der externe Abschlussbeleg im Kandidatenordner bindet beide Quellgenerationen und die zurückgelesenen Wegweiser. Keine Altgeneration/Schutzmenge gelöscht, kein main-Merge/Force-Push.

## Auftrag97 – endgültiges Voraudit nach Sichtkorrektur grün

Finale gesamte54Browser-Suiten/0FAIL einschließlich13Übersichtsfällen und sichtbarer Terminzeit/Kunde mit aktivem Timer+gespeicherter Arbeit bei360×616. Alte P3-Präsentationserwartung für leeres Idle-Timerelement erlaubt jetzt gleiche y-Koordinate zum folgenden Inhalt; bei aktivem Timer strikte Reihenfolge zusätzlich geprüft, fachliche Tests unverändert. Native Java-/AndroidTest-/Lint-Vorprüfung42Tasks PASS;0Fehler/54bestehende Warnungen. Vertrags-/UI-/Source-/Workspace-Gates PASS; backendunverändert seit50JVM/395Python/55stdio-MCP/29Kamera-/47Cleanup-Prüfungen.13gezielte Fälle sowohl echte Layoutregression rot als auch korrigierte Fassung grün. Alle aktuellen Implementierungspunkte vor erneutem Originalbuild überprüft. Version1.54.8/97 bleibt unpublizierter Kandidat; keine97-Versionsmarke gesetzt. Endgültige App und Test-APK aus demselben neuen gesicherten Commit bauen, komplette native310Prüfungen/UI/Fonts/korrekten96→97-Vergleich und Byte-Provenienz wiederholen.9cc-Quellgeneration erst nach vollständigem Ersatz eindeutig als Zwischenstand kennzeichnen.

## Auftrag97 – Sichtprüfung deckt alte CSS-Reihenfolge auf

Alle3nativen Schriftgrößen1.0/1.5/2.0 mit tatsächlich fokussierter MainActivity PASS,9Bilder angesehen; SystemUI-ANR zuvor strikt zurückgewiesen und ausschließlich eigenen Software-AVD stabilisiert (temporärer Runtime-Timeout10, Testdisplay vorab eingerichtet, exaktes OS-Wait; keine Appdaten gelöscht). Font/physisches480×960-Display/Dichte240 anschließend tatsächlich restauriert. Keine Pixel-Performancebehauptung.

Bildprüfung fand trotz formaler Touch-/Overflow-Abnahme eine relevante Übersichtsschwäche: alte field-ui.css/workday-ui.css-Orderregeln priorisierten Zuletzt erfasst vor der Terminansicht; aktiver Timerblock zu hoch. Neues13. Browser-Szenario mit gespeicherter Arbeit/aktivem Timer/360×616px tatsächlich rot.12alte Home-order-Regeln entfernt, DOM-Reihenfolge allein maßgeblich; Timerstatus kompakt mit barrierefreiem48dp-Mehr-Menü.13Übersichtsfälle nach Korrektur PASS, native Prüfung ergänzt explizit sichtbare Terminzeit/Kunde oberhalb unterer Aktionen. Erneute54Browser-/native Compile-/Voraudit-Runde läuft. Noch kein finaler97-Tag/APK ausgeliefert;9cc4e18/91b50e7d ist dadurch ein nicht ausgelieferter Zwischenkandidat, seine Quellkopie bleibt bis vollständig gesichertem Ersatz erhalten. Nächster Originalbuild übernimmt auch alle QA-Fixes; App-/Testquellen wieder aus einem gemeinsamen gesicherten Commit.96-Vorledger unverändert; nachher mit explizitem --compare prüfen, keine Baseline ersetzen.

## Auftrag97 – native Fach-/MCP-Abnahme vollständig grün

Original97-App9cc4e18 mit separat originalsignierter QA-aa44c0b:290native Fach-/JSON-RPC-/SQLite-/SAF-Prüfungen/0FAIL, zusätzlich20gültige MCP-Workflows/SAF-Prüfungen/0FAIL.117Tools tatsächlich per Produktionsrouter erreichbar;113Toolnamen mit erfolgreichen Aufrufen,4nur geschützte Außenfreigabepfade (Legacy-Vollsicherung, Rechnungsabschluss, Mailvorbereitung/-bestätigung), deren negative Guards und isolierte Kerne separat geprüft sind. Alle neuen MCP-Fixtures ohne eigene DB-/Preferences-/Dateireste, einschließlich des zuvor asynchronen Kalenderfalls. Aktuelle Originalquellen19Dateien vollständig privat in1EBtXHhOQxqivTLu7L-DArRBBEwG-d94d gesichert:18Byte-SHA/1ZIPStreamgröße; frischer Plan0Upload/19Reuse/0Duplikate.

fresh_isolated PASS. Nativer UI-Lifecycle-Test stoppte an alter Erwartung Planungsbutton innerhalb planning-home; Button liegt wie beauftragt unten im today-action-dock. Genau diesen Präsentationsselektor aktualisiert, fachliche Assertions erhalten. Keine Produktionsänderung/kein anderer Appkandidat; UI-/Schrift-/Ledgerprüfung folgt mit erneuter originalsignierter QA-Quelle. Erneut lesend Pixel96 bestätigt: REMOTE_STATE_REFERENCE_MISSING,9Pending/0Review,cloud_confirmed=false; keine produktive Aktion.

## Auftrag97 – vollständiger Routerlauf und zusätzliche gültige MCP-Abläufe

Apporiginal9cc4e18220ff3d4eef8a56877658b397d63d2504, APK-SHA5087632af214da765fc9164681123bc23dbd2645eea612c1289d1e41606d7ce7, Produktionsfingerprint91b50e7d13644cf42565cdd92008be0acd82bdb3525bcc8c87b91fb70c02614d. Voller neuer nativer Lauf289PASS/1FAIL: ausschließlich noch QA-Teardown einer Kalender-Quota-Probe;117echte RPCs tatsächlich abgewickelt. Die eine Preferences-Datei entstand erst nach der Dateiliste aus asynchronem apply(). QA-Isolated erfasst jetzt jede geöffnete Preferences-Datei und wartet deren Writes vor Entfernen; keine fachliche Erwartung gelockert.

Separate zusätzliche QA-Quelle für gültige echte RPC-Abläufe: Auftrags-/Arbeitsänderungen samt Replay, vollständiger Timer/Pause/Fortsetzen/Abschluss, Verwerfen/Wiederherstellen, Leistungsbaustein-/Angebotsbearbeitung. Vorhandener isolierter SAF-Legacyabschluss ebenfalls über RPC. Neue Gruppe mcp_workflows. Noch keine erfolgreiche Abnahme behauptet. Appcode/APK bleiben unverändert9cc4e18, weitere QA-Commits/Test-APK werden separat im Abschlussquellstand gesichert; Original-Tag/ZIP wird den tatsächlich gebauten Appcommit bezeichnen.

## Auftrag97 – native Bereinigungsprobe zunächst rot

Originaler interner97-Kandidat4a8e554 aus sauber gesichertem Stand gebaut84Tasks, originalsigniert und gegen96-Paket/Zertifikat/424Assets/leerem Bootstrap/DEX-Commit/Fingerprint/Privacy geprüft. Nicht ausgeliefert oder getaggt. Native Ausführung deckte einen Fehler ausschließlich im neuen QA-Teardown auf: Android databaseList liefert DB und Journal/WAL-Sidecars; deleteDatabase entfernt diese gemeinsam, der folgende veraltete Listeneintrag liefertefalse. Bereinigung prüft nun erst Existenz, anschließend unverändert strikt keinen eigenen Datenbank-/Preferencesrest. Fachliche Assertionen nicht gelockert. Neuer vollständiger Quell-/Build-/Signier-/Emulatornachweis folgt; Fingerprint der unveränderten Produktionsdateien bleibt91b50e7d. Vorangegangene Host-Gates bleiben gültig, bisherige native Runde ist FAIL und keine Ersatzfreigabe.

## Auftrag97 – APK-Voraudit grün, Version1.54.8/97

Finale gesamte54Browsersuiten/0FAIL,12Übersichtsfälle/0FAIL einschließlich abgelaufener und vollständig leerer Terminauswahl. Finale native Java-/AndroidTest-/Lint-Vorprüfung42Tasks PASS,0Fehler/54bestehende Warnungen.395Python/50JVM/1050Paritätsassertions/55stdio-MCP/29Kamera-/47Cleanup-Hostfälle sowie Pflanzen-/Angebots-/Vertrags-/UI-/Source-/Workspace-Gates grün. Jeden aktuellen Auftragspunkt gegen Umsetzung/Tests/Grenzen im Voraudit geprüft. Versionsanhebung jetzt1.54.8/97, anschließend nur sauber gesicherten Commit bauen/originalsignieren. Native117RPC-/Fach-/Schrift-/96→97-Erhaltungs-/Artefakt-/Driveabnahme noch offen; keine produktive Installation oder Syncaktion.

## Auftrag97 – zusätzlicher Randfall und APK-Voraudit

Abgelaufene Karussellauswahl übersprang im roten Produktionstest den ersten weiterhin gültigen Termin. browseHome korrigiert; auch vollständig abgelaufene Liste entfernt ihre alte Karte.12gezielte Übersichtsfälle PASS. Beim Rückstellen der künstlichen Testuhr muss die Ansicht vor dem nächsten Klick aktualisiert werden; zuerst alter Fixture-Cleanup statt Produktionsassertion angepasst. Neue gesamte54Browserwiederholung läuft. Native Auditressourcen werden nun tatsächlich geschlossen und nach exaktem eigenen Präfix entfernt, Symlinks nicht verfolgt; sämtliche neuen Owner-Fixtures verwenden dieselbe Bereinigung. Native Audit zählt echte erfolgreiche/abgewiesene Aufrufe getrennt und erfasst Dauer ohne Argumente/Datensätze.

Alle395Python/50JVM/55stdio-MCP/29Kamera-/47Cleanup-Hostfälle PASS. Live-MCP rein technisch gelesen: Original96 installiert;117Tools/156UI-Aktionen. Aktueller produktiver Syncfehler REMOTE_STATE_REFERENCE_MISSING, publication_chain,9Pending/0Review/cloud_confirmed=false; keine Änderung/Sync ausgelöst. Fehlende unveränderliche Referenz darf nicht aus technischen Metadaten geraten werden. Bestehender exakter Name+SHA-Wiedergewinnungspfad und7JVM-Fälle geprüft. [Voraudit und Grenzen](docs/verification/today-mcp-1.54.8-97.md). Noch keine97-APK oder finale native Abnahme.

## Auftrag97 – Heute-Rückkehr und konkret reproduzierte Verbesserungen

Original95-Ansicht mit identischem synthetischem115-Termin-Fall tatsächlich im Browser angesehen: sichtbarer Heute-Titel/Datum, einzelne blätterbare Einsatzkarte, zwei Stundenkennzahlen; Logo war dort bereits nur im Menü sichtbar. Diese Struktur in demselben TodayUI-Renderer wiederhergestellt, statt alte konkurrierende Renderer zurückzubringen. Originale Start-/Pause-/Stop-/Foto-/Erfassen-/Planungskommandos in unterer fester Leiste über der Navigation. Dynamische Leistenhöhe und Scrollreserve; bei Tests gefundenes Verdecken von Inhalt und Rückgängig korrigiert. Fachliche Entwurfs-/Timer-/CAS-/Syncregeln und linke Randgeste erhalten. Neue gezielte11Übersichtsprüfungen einschließlich Karussellgrenzen, Hauptaktionen unten, Schrift150/200, Timer/Foto/Entwurf und begrenztem Bridgezugriff PASS. Erste54-Suiten-Regressionsrunde deckte mehrere alte Layout-Erwartungen und die echte Rückgängig-Überdeckung auf; korrigierte gezielte Wiederholung und finaler Gesamtlauf folgen.

Konkrete Leistungsmessung, Host/115synthetische Termine/25Wiederholungen:96-Heute median11ms/p95 26ms,95-Referenz3/4ms. Neuer Einzelkartenpfad und einmaliger Vienna-Datumsformatter2/3ms in erster Nachmessung; abschließende Vergleichsmessung folgt. Nur gewählte Termindetails über Android-Brücke, warme25Heute-Render ohne zusätzlichen get_appointment-Aufruf. Kein Pixel-/Netzleistungsversprechen. MCP-Metadaten bauten denselben117-Tool-Katalog dreimal je Antwort; lokale Wiederverwendung innerhalb derselben Antwort vermeidet diese Mehrarbeit, Folgeresultate bleiben frisch. Zusätzliche Produktionsprüfung für Hashkonsistenz, unveränderliche Folgeresultate und0Schreibzugriffe ergänzt.

MCP-Befund tatsächlich rot reproduziert: update_business_settings bietet invoice_numbering_authority="" an, minLength1 verwarf diesen eigenen Enumwert. Genau dieser Einzelwert nun minLength0; Schutz von request_id, record_hash, Rechte-/Journal-/Rechnungsnummernregeln unverändert. Eingefrorene P1-Baseline nicht überschrieben; gesonderter eng geprüfter Reparaturdelta mit Negativtests gegen weitergehende Schema-/Rechteänderungen.49JVM-Suiten mit1050Paritätsassertions und5gezielte Python-Vertragstests PASS; ursprünglicher Pythonlauf394PASS/0Skips. Vorher-/Nachher-Produktionsschema-Probe bestätigt Fehler/Korrektur und117/117gültige synthetische Aufrufargumente. Neue native echte JSON-RPC-/Negativpfadprüfung aller117Tools samt eigener Datenbank-/Datei-/Preferencesbereinigung vorbereitet; voller Funktions-/Provider-/Performance-/Artefaktnachweis noch offen. Version weiterhin96, kein neuer APK-Kandidat ausgeliefert.

## Auftrag97 – Heute aus95, Bedienung unten, Fehler-/Leistungs-/MCP-Prüfung (23.10.2026)

Neuer Nutzerauftrag: aktuellen96-Stand auf Fehler/Performance prüfen; Heute aus1.54.6/95 zurückbringen, wichtige Bedienung am unteren Rand. Ausgang App998d71f/Tag96, Dokumentatione6b67cb, sauberer bestehender Auditbranch. Frischen main e499e745 und alle Remote-Branches verglichen; AGENTS identisch zu frischem main. Kein main-Merge/Force-Push. Lesende Live-MCP-Metadaten bestätigen tatsächlich installiertes Original1.54.7/96 mit passendem Fingerprint8a29c1b7,117registrierten und117verbundenen Tools; vollständige156-Aktionsmatrix100+56/hashgebunden gelesen. Keine Geschäftsdatensätze/Testeinträge/Queue/Sync verändert.

Abnahme vor Umsetzung: Heute übernimmt95-Elemente(Arbeitstag/Logo, Terminblätterung, Heute-/Wochen-/Auftragskennzahlen, Arbeitsverlauf) in einem zuständigen Renderer. Arbeit erfassen/Planung und Timeraktionen unten oberhalb vorhandener Navigation,48dp, kein Überdecken des letzten Inhalts;360×640/616 sowie150/200Prozent prüfen. Linke Rand-/Zurückgeste, Daten-/Sync-/Request-/Entwurfsschutz und96-Fehlerkorrekturen erhalten. Performance vor/nach mit denselben synthetischen Daten und echten Hauptthread-/Bridgezählungen messen; keine Pixel-Leistungsbehauptung aus Host/Software-AVD. Alle117Werkzeuge und145fachlich abgedeckten Aktionen gegen tatsächliche Dispatcher/Workflows prüfen;11Plattform-/Darstellungsgrenzen gesondert ausweisen, Registrierung nicht als Funktionsbeweis ausgeben. Schreibprüfungen ausschließlich isoliert/synthetisch, keine produktive Installation oder automatischer Sync. Version bis grünen Implementierungsgates96; nächster Originalkandidat1.54.8/97, anschließend vollständige Regression, originalsigniertes Emulatorupdate/Datenerhalt, Quellen/Tag/Drive nach AGENTS. Kein Ersatzschlüssel/QA-Key.

## Umsetzung96 – AP1–AP7 abgeschlossen, Originalkandidat und finale Belege

Alle autorisierten Implementierungspunkte und sorgfältiges APK-Voraudit abgeschlossen. [Abschlussbericht je AP](docs/verification/implementation-1.54.7-96.md), [strukturierte Tests/Abweichungen/Grenzen](docs/verification/implementation-1.54.7-96.json).394Python/49JVM/54Browser/55lokale synthetische MCP/172native Prüfungen PASS;0native FAIL. Echte360×640dp-/WebView616px-Prüfung bei100/150/200Prozent,9unverdeckte Bilder visuell geprüft, Display/Font restauriert. Original95→96 vollständiger Ledger/fresh_isolated/nativeWebView PASS: Master/Pending/Relationen/RUNNING-Sitzung unverändert. Installierte APK byteidentisch. Source/Workspace/signierteAPK Privacy PASS,0operative Seeds. Linkes Rand-/Zurückmenü unverändert.

Endgültiger Originalcode `998d71f233f57b68264f771e64f8c576b214b957`, unveränderliches Tag `source/v1.54.7-build96`, Fingerprint `8a29c1b793014be4f4e6f53b8feb8ef2744e0d0727541bc374a26bdd54bae336`. APK1.54.7/96,36.217.051Bytes,SHA256 `e4cc718fc51ed7b8f47f089ea582da52961c09d413267c7fcf8df06e836a2a25`, Originalzertifikat identisch. [APK](https://drive.google.com/file/d/1YOT0voQQZ_ntiA_O9jBWVr3ifR8AnBQ8/view), [Versionsnachweis](https://drive.google.com/file/d/1fthY3Q6T0qLdb2sfi2GWrAAUHzaNZa8_/view), [QA](https://drive.google.com/file/d/1UZJh4BhsZGZWez1hiLRo2w_X9skKKZZf/view). [Originalquellen](https://drive.google.com/drive/folders/1PJqvAyhJlRAG-R2UpTtq4dA1wSjavwYr)1282Dateien/551UTF8/731öffentliche Binärressourcen; ZIP-SHA256 `ff8c5708a71a1566b89cc8e6dfe40434a6cf63c98463fb12f6be4df9e3ebfdfd`.19Transferbelege/19Reuse/0Upload/0Duplikate,18Byte-SHA+1ZIPStreamgröße. GroßeAPK ebenfalls vollständiger Stream/Größe, kein Remote-SHA behauptet. Finaldokumentation nachfolgend gesondert gesichert/exportiert, unveränderlicher Appcode bleibt998d71f. Keine Altartefakte/Schutzmengen entfernt, kein main-Merge/Force-Push.

**Offen ausschließlich externe Abnahme:** Pixel und produktives Google Drive ausdrücklich nicht aufgerufen; historische94-Beobachtung nicht aktualisiert. Nutzer übernimmt Sicherung/Installation/request_sync/Rücklesen. Emulatorerfolg beweist keinen produktiven Abgleich. Ältere folgende Chronik beschreibt Zwischenstände, nicht den endgültigen Kandidaten.

## Umsetzung96 – tatsächliche nutzbare Displayhöhe vor endgültigem APK-Build

Native Normalansicht mit korrekt datierten synthetischen Terminen reproduzierte einen knappen Überstand des zweiten Termins unter die Navigation: vom logischen360×640dp-Display bleiben durch die Android-Statusleiste616px WebViewhöhe. Timerstatus und Uhr teilen jetzt bei Normalschrift eine Zeile; Karteninnenabstand geringfügig kompakter, sämtliche Touchziele weiterhin mindestens48dp. Explizite Buttonabstände funktionieren auch im getesteten alten WebView83 ohne Flex-gap. Keine Timer-/Termin-/Speicherlogik geändert. Font-Fixture ergänzt langen Namen der tatsächlich laufenden synthetischen Sitzung und wartet zwei Renderframes vor der Messung.

Erneuter vollständiger Browserlauf mit diesen finalen Assets:54Suiten/0FAIL. Übersicht zusätzlich360×616px:10Fälle/0FAIL. Native Java-/Lint-Vorprüfung42Tasks PASS. Finale APK, tatsächliche native Schrift-/Sichtprüfung und erneute Fach-/Erhaltungsabnahme folgen; bisherige interne96-Artefakte und Quellkopie4625059 sind keine Auslieferung und erhalten keine endgültige Versionsmarke. Die nachfolgenden älteren Chronikeinträge bleiben historische Zwischenstände.

## Umsetzung96 – nachgewiesener Zeitzonenfehler im synthetischen Font-Fixture (22.10.2026)

Fensterwächter nach WindowManager jetzt tatsächlich PASS, freier Screenshot ohne Systemdialog. Fixture zeigte nach22UTC keinen heutigen Termin: Emulator ist Etc/UTC, Planung/Heute verwendenEurope/Vienna und lagen bereits am Folgetag. Die künstlichen Termine wurden fälschlich mit Geräte-localDate statt dem Tag der Planungsansicht erzeugt. Testdatum nun explizit über dieselbe Europe/Vienna-Tagesgrenze erzeugen; keine Produktionsdatum-/Datenaufrufe geändert. Produktionsfingerprint bleibt exaktf19e1919, originale APK/ad84ec8f aus4625059 unverändert. QA-/Testquellen werden separat sauber gesichert und exportiert, zugehörige Originalinstrumentierung neu gebaut. Versionsmarke bindet weiterhin exakt den tatsächlich eingebetteten App-Quellcommit4625059; abschließender Dokumentations-/QA-Commit wird gesondert angegeben. Kein APKwechsel allein wegen Fixture-/Dokumentationskorrektur, keine erfolgreiche Fontabnahme behauptet, bevor sie tatsächlich läuft.

## Umsetzung96 – finale Fachabnahme und präzisierter Fensterwächter (22.10.2026)

Originalkandidat3044ab5/Produktionsfingerprintf19e19197ec367d3a0b1bb9d8b478efc8943fe9ee4eebeec8fc4040e58679dad:172native Fachprüfungen/0FAIL,95→96-Vollledger, fresh_isolated und tatsächlicher WebView PASS. Alle Masterdaten, Beziehungen, eine offene Aktion und sämtliche persistierten Timerfelder unverändert. Schrift-/Displayrollback nach erster neuer Probe PASS.

Der zusätzliche Accessibility-Root-Wächter lieferte unmittelbar nach Activitystart null; dies ist kein Beleg für ein verdecktes Appfenster. Test liest stattdessen nach tatsächlicher WebView-Bereitschaft die fokussierte MainActivity aus Android WindowManager. Ein SystemUI-/ANR-Dialog besitzt diesen Fokus nicht und bleibt Abbruchgrund. Keine Produktionsquelle geändert; App-Fingerprint bleibt exaktf19e1919. Korrekte Testquelle wird erneut gesichert und ein dazu passendes Artefakt gebaut, bevor Tag/Auslieferung festgelegt werden.

## Umsetzung96 – abschließende kleine Bildschirm-/Sichtprüfung (22.10.2026)

Die ersten nativen Schriftproben lieferten zwar richtige OS-/TextZoom-/DOMwerte, die tatsächlich zurückgelesenen Screenshots zeigten jedoch einen verdeckenden Android-SystemUI-ANR-Dialog. Diese Bilder ausdrücklich nicht als visuelle Abnahme akzeptiert. Ausschließlich im bewachten eigenen AVD den exakt identifizierten Systemdialog über „Wait“ bestätigt; keine Appdaten/Queue geändert. Native Fontprüfung verlangt künftig zusätzlich fokussiertes Anwendungsfenster und prüft die tatsächliche Sichtbarkeit auf einem temporären360×640dp-Display, bei1.0/1.5/2.0; Font und Display werden zurückgestellt. Kein Schluss auf Pixelperformance aus dem nicht beschleunigten API30-Emulator.

Zwei weitere konkrete UI-Regressionsfälle erst rot, dann behoben: bei laufender Sitzung lag der zweite heutige Termin unter der Navigation; nach Kundenumbenennung blieben Namen in Today-Rendercaches alt. Timer-Jetzt nun kompakt mit Pause/Fortsetzen und Stoppen/Ergänzen, Foto/Nachtragen/Löschen über vorhandene Aktionen im ⋯-Zusatzmenü. Keine Timer-/Foto-/Speicherkontrakte geändert. Renderer-Schlüssel berücksichtigen tatsächlich angezeigte Kundennamen;1s-Takt ersetzt weiterhin nur die Uhr bei unverändertem Kontext. Zeitraum darf bei großer Schrift ganze Datumswörter in zusätzliche Zeile legen; Timer-/Startbeschriftungen werden nicht mitten im Wort getrennt.

Erneuter vollständiger54-Suiten-Browserlauf mit finalen operativen Assets:54PASS/0FAIL, keine zusammengesetzte Ersatzfreigabe mehr erforderlich. Neu10gezielte Übersichtsfälle einschließlich aktivem Timer+2Terminen, Kameraaufruf mit identischer Session/Kunde, Nachtragen ohne Timerneustart, umbenanntem Kunden, langen Namen und großen Idle-Startbeschriftungen.394Python erneut PASS/0Skips;49JVM-Suiten/1047Paritätsassertions sowie18Retentionfälle weiter grün. Finale native Java-/Lintprüfung und original96-Artefakt-/Emulatorwiederholung vor Auslieferung stehen noch an. Linke Rand-/Zurückgeste unverändert.

## Umsetzung96 – zusätzliche AP4-Referenzprüfung nach erstem Kandidaten (22.10.2026)

Erster originalsignierter interner96-Kandidat aus57e1b01:84Release-/AndroidTest-/Lint-Tasks PASS; Paket/Originalzertifikat/komplette Assets/leerem Bootstrap/Commit/Fingerprint geprüft.172native SQLite/MCP/Providerfälle PASS/0FAIL und tatsächlicher WebView PASS. Kein Tag/Drive-Upload/Pixelupdate dieses Zwischenkandidaten.

Zusätzliche Quittungsprüfung fand Serien-/Sammelaktionen mit bestehenden numerischen Unterkennungen request_0/request_1. Ein neuer Test reproduzierte tatsächliches vorzeitiges Freigeben der Elternquittung (ReceiptRetentionRules-Prüfung3 FAIL). Referenzschutz jetzt auf genau diese vorhandene numerische Konvention erweitert, auch für Tombstone-/Backupreferenzen; unspezifische Namen wie request_other/request_1_other zählen nicht.18gezielte Retentionfälle und erneut49JVM-Suiten/1047Paritätsassertions PASS. Native SQLite-Abnahme um diese offenen Unteraktionen/Tombstones erweitert; Java+AndroidTest33Tasks PASS. Aktueller interner Kandidat wird vor Auslieferung aus dem korrigierten Commit neu gebaut;96-Versionsmarke noch nicht gesetzt.

Erste native1.5-Schriftprobe scheiterte mit ungenauer Sammeldiagnose; kein Erfolg behauptet. Testadapter prüft nun die tatsächliche Activity-Konfiguration, gebundene native Lesemethoden und feste nummerierte Prüfphasen; Screenshots bei Fehler. OS-Schrift wurde auf effektiven Normalwert1.0 zurückgesetzt (Android initialisierte zuvor nicht gesetzten Default dabei als1.0; erste strikte Raw-String-Prüfung meldete deshalb fälschlich Restore-Fehler). Semantischen Originalwert künftig zusätzlich explizit vergleichen/protokollieren. Wiederholte native Fontabnahme und abschließender96-Kandidat weiterhin offen.

## Umsetzung96 – ausdrückliches APK-Voraudit bestanden (22.10.2026)

Jeden AP1–AP7-Unterpunkt vor APK-Erstellung nochmals gegen Quelle, Tests und Grenzen geprüft: [Abnahmeprotokoll](docs/verification/implementation-1.54.7-96.md).54aktuelle Browsersuiten grün(53Gesamtlauf plus korrigierte Planungsrechteck-Prüfung erneut; operative Assets nachweislich unverändert),394Python/0Skips/37explizite QA-Key-Altfallausnahmen,49JVM-Suiten/1047Paritätsassertions,114Syncfehlerprüfungen,55lokale stdio-MCP-,29Kamera-/47Cleanup-Hostfälle PASS. Native Java+AndroidTest+Lint42Tasks PASS,0Fehler/54Warnungen. Pflanzen-/Angebots-/UI-/Privacy-Gates grün. H3/H4kein Entwurfsverlust, H5gemessen/kein spekulativer Umbau. Synthetisches Emulator-Vorledger95 frisch erfasst:3Kunden/1Auftrag/2Arbeiten/1Pending/laufender Timer; nur Counts/Hashes gespeichert.

Implementierungsnacharbeiten abgeschlossen. Version darf jetzt1.54.7/96 werden; danach sauber gesicherten Commit bauen/originalsignieren. Native Ausführung der96-Tests, OS-Großschrift, Artefakt- und Driveabnahme sind weiterhin separate nachfolgende Gates. Keine vollständige96-Freigabe vor diesen Nachweisen; kein Pixel-/Produktivsync.

## Umsetzung96 – Regressionsprüfung und Großschriftkorrekturen (22.10.2026)

Vorhandene Positionstests bewusst auf die neue Heute-/Planungsstruktur umgestellt: echte Readiness statt entfernter Metrikkacheln, sichtbare aktive Terminkarte statt zusätzlichem versteckten Heute-Treffer, neues ⋯-Menü vor Zusatzaktionen öffnen. Fachliche CAS-/Request-/Timer-/Abrechnungs-/Kameraerwartungen nicht gelockert. Tatsächlich gefunden und behoben: Arbeitsdetail-Aktionsmenü war von einer entfernten Heute-Arbeitszeile abhängig; ruft jetzt denselben bestehenden Action-Sheet-Einstieg direkt mit Datensatz-ID auf. Offene Terminenden auf Heute wieder sichtbar. Persönliche bezahlt-Markierung einschließlich lokaler Persistenz/Fehlerfall über eingeklappten Arbeitsverlauf erhalten, Wochenstunden bleiben letzte Kennzahl.

Sichtprüfung100/200Prozent fand trotz rechnerisch korrekter Begrenzung zu schmale Kundenspalten. Heute und Planung geben dem vollständigen Kundennamen nun eine eigene Kartenzeile; Timeraktionen dürfen bei großer Schrift umbrechen. Geerbten zusätzlichen Pfeil im ⋯-Summary entfernt. Native Java/Main+AndroidTest erneut33Tasks PASS.49JVM-Suiten/1047Paritätsassertions PASS vor abschließender Netz-Backoff-Präzisierung; erneuter Gesamtlauf folgt. AP1 präzisiert: reine Netzfehler bleiben ohne Nutzeraktion im gedeckelten Backoff, auch ab Versuch8; Retryzähler bleibt begrenzt. Die vorhandene Grenze aller anderen Fehler unverändert. Diese bisherige Retrylimit-Verzweigung hätte sonst den ausdrücklich verlangten Netzwerkfall wieder zur Nutzeraktion gemacht.

Neue Diagnosedarstellungsfälle prüfen tatsächliches sync-dashboard und business-feedback: Klasse/Meldung und read_step in Diagnose, vier neue Codes mit verständlichen Haupttexten; gezielter Sync-trash-Lauf PASS. Neue H3/H4-Fokus-/Entwurfsproben sowie H5-Bridge-/Hauptthreadmessung laufen, keine vorweggenommene Erfolgsbehauptung. Eigener Emulator bewahrt95-Datenpartition; noch keine96-APK/Installation, kein produktiver Pixel-/Drive-Abgleich. Vollständige abschließende Browser-/Python-/Native-Abnahme und Quellen-/Artefaktbelege weiterhin offen.

## Umsetzung96 – AP5 und erster AP6/AP7-Browsernachweis (22.10.2026)

AP5: vorhandenen Leave-Guard erhalten, flushWorklogDetail ergänzt und Verwerfungsfrage auf eindeutigen Auftragstext gestellt. Tatsächlicher linker Menüpfad speichert gültige Änderung vor Wechsel; fehlender Kunde liefert false, Bestätigung erscheint und Abbrechen lässt Seite/Entwurf unverändert.10Worklog-Window-Browserfälle einschließlich2neuer Menüfälle PASS. Kein Warten auf einen Zeitgeber; keine pauschale zusätzliche Wrapperlage.

AP6: today-ui.js besitzt Heute-Struktur und ursprüngliche Timerkommandos; alte Heute-Renderer aus workday/design/planning/field entfernt, work-location behält Erfassung/Validierung und verändert die Heute-Struktur nicht mehr. Logo im vorhandenen Seitenmenü, Datum/Statuschip oben, Jetzt mit Start/Nachtragen oder laufender Arbeit/Stoppen, drei heutige Termine mit Alle anzeigen, bedarfsabhängige Offen-Chips und kleine Wochenkennzahl. Optionale bevorstehende Terminblätterung samt bisheriger Richtungs-/Klicksperre bleibt als Zusatz ohne heutige Termine. Versteckte Ordnerbuttons aus Markup entfernt; bestehende Ziele unter Mehr erhalten. Linke Rand-/Zurückgeste unverändert; nur das beauftragte Logo ergänzt.

Planung: drei Bedienzeilen(Zeitraum/Ansicht/Wochenstreifen), Terminliste unmittelbar danach, fester beschrifteter +Termin-Knopf. Zusatzwerkzeuge/Kundenfilter/Status/Einrichtung im ⋯-Menü, verbindungsbezogener Chip bei Problem; bestehende Request-/Editor-/Zeitkonvertierungs-/Kalenderentscheidungswege erhalten. Planning-Listenaufrufe und Pagination unverändert, Heute liest erforderliche Detailmetadaten über denselben vorhandenen get_appointment-Pfad.

AP7: neue feste Browser-Suite mit360×640 und100/150/200Prozent.7konkrete Fälle PASS: Start+2Termine initial sichtbar,3/Alle-Anzeige, erster Planungstermin und max3Bedienzeilen, mindestens48px-Touchziele, Timer-ID/Arbeitsdauer beim Hin-/Rückwechsel, erhaltener Planungsentwurf, horizontale Grenzen/umgebrochener Text und keine Browserfehler. Sichtprüfung der synthetischen100%-Heuteansicht erfolgt;150/200%- und Planungsbilder folgen. Neue Tests fanden44px-Alt-Summary, korrigiert; Timerstart bewusst über bestehende Kundenauswahl getestet, kein fiktiver Direktstart. Gesamter54-Suiten-Browserlauf läuft noch; alte Positionserwartung in visual-consistency meldet erwarteten Darstellungsdiff(Heute-Aktionen nun innerhalb Jetzt statt darunter), noch gezielt zu aktualisieren. Keine Baseline voreilig neu freigegeben.

AP1–AP4: korrigierte Android-Main- und AndroidTest-Javakompilierung tatsächlich PASS33Tasks. Native Ausführung weiterhin ausstehend. Eigene AVD mit bestehender95-Datenpartition gestartet, qemu/Name geprüft, root nur dort; Timeoutmultiplikator10 gesetzt/zurückgelesen und Framework ohne Datenreset neu gestartet. Native große Schrift und Datenerhalt noch offen. Version bleibt95, keine neue APK, keine Pixel-/Drive-Betriebsaktion.

## Umsetzung96 – AP1–AP4 Implementierungscheckpoint (22.10.2026)

AP1: gemeinsame reine SyncFailureRules,108gezielte Produktionsprüfungen PASS; Konfigurationsfehler Versuch1/Nutzeraktion ohne Planung, Netzfehler eigener Backoffcode. Bisherige Retryzählung anderer Fehler erhalten. Diagnose erhält Klasse/auf120Zeichen begrenzte datensparsame Meldung und read_step. Beliebige Provider-/Logikmeldungen können Geschäftstexte enthalten: ausschließlich exakt bekannte harmlose Plattformmeldungen übernehmen, sonst Inhalt ausblenden; keine scheinbar sichere Regex-Redaktion.

AP2: SyncCommitRules bestätigt accepted/applied/rejected_invoice/resolved gemeinsam mit Snapshot über bestehende SQLite-Transaktion. Späteres false oder Exception der Statuspersistenz wird STATUS_NOT_SAVED/Retry0 und kein erneuter Übertragungsfehlversuch.17gezielte JVM-Commit-/Reihenfolge-/Preferencesfehlerfälle PASS, native SQLite-Prüfung vorbereitet.

AP3: echte LocalOwnerRepository.runRequest-Methode im bestehenden JVM-Lauf frisch aus Produktionsquelle kompiliert und mit benanntem Transaktionsport ausgeführt:14Rollback-/Replay-/Mismatch-/Expiryprüfungen PASS. Keine Produktionsklasse als Testdouble. CleanupSyncStub implementiert schmalen I/O-Port, tatsächliche Fehlerklassifikation aus Produktionsklasse;47Hostfälle PASS.49JVM-Suiten/1047Paritätsassertions und bisheriger eingefrorener Vertrag PASS. Android-Main-Javakompilierung PASS; erste neue Testadapterkompilierungen fanden fehlende public-/throws-Deklarationen, korrigiert, erneute native Testkompilierung/Abnahme noch offen.

AP4: bestätigte/entfernte pending-Zeile plus entfernte Quittung im tatsächlichen Repository reproduziert: derselbe Request wirkt erneut; PK allein genügt nicht. Daher nur volle Ergebnisquittungen älter90Tage ohne offene Action/Backup/Tombstone entfernen, in derselben Transaktion dauerhaften minimalen Fingerprint-Sperrbeleg behalten. Gleiche abgelaufene Anfrage liefert REQUEST_EXPIRED statt zweiter Aktion; abweichender Fingerprint bleibt REPLAY_MISMATCH.13reine Alters-/Referenzprüfungen PASS. Alte Quittungen ohne verlässliches Datum bekommen erst ein Beobachtungsdatum; keine sofortige Altersvermutung. Automatische Bereinigung ausschließlich nach serververifizierter Publikation und erfolgreicher lokaler Statuspersistenz; Provider-Rücklesen genügt nicht. Protokoll nur Anzahl/Zeit. Tatsächliche native SQLite-/Providernegativfälle vorbereitet, noch nicht ausgeführt.

Version bleibt95 bis sämtliche Implementierungsgates und UI-Abnahme grün. Keine APK erstellt, kein produktiver Pixel-/Syncaufruf, keine Randmenüänderung. AP5–AP7 und Hypothesenprüfung folgen.

## Umsetzung96 – Auftrag und Abnahmekriterien festgelegt (22.10.2026)

Neuer Nutzerauftrag AP1–AP7: [CODEX-UMSETZUNG-1.54.7.md](CODEX-UMSETZUNG-1.54.7.md). Ausgang sauber d279945/Original95, Remote-Arbeitsbranch exakt identisch; frischer main e499e745 und alle bestehenden Branches verglichen, kein Reset/Merge. Aktuelle main-Regeln und Issue1 lesend über Connector geprüft; gh-GraphQL war gesperrt, kein fehlender Inhalt als gelesen behauptet. Cloudnetz eingeschränkt/enforced, erforderliche Originalsignierungsvariablen nur als bereit gemeldet, keine Werte ausgegeben. Noch keine Versionserhöhung, Codeänderung oder neue APK.

AP1/AP2 tatsächlich in Quelle bestätigt. AP5 enthält bereits einen Leave-Guard im mobile.html; tatsächlichen Menüpfad prüfen und gezielt ergänzen. AP4 muss auch Wiederholung nach bereits bestätigter/entfernter pending-Zeile schützen; Primary Key allein ist dann kein dauerhafter Replay-Beleg. Diese Randfälle vor Implementierung isoliert belegen. Sämtliche produktiven Pixel-/Sync-Aktionen ausgeschlossen, linkes Randmenü unverändert. Fortschritt und Schlussaudit je AP folgen; vorhandene95-Nachweise sind keine96-Abnahme.

## Abschluss B1–B4 – Optimierungen und Originalkandidat95 (22.10.2026)

**Umsetzung und Cloud-Abnahme abgeschlossen; physisches Pixelupdate und produktive Sync95-Bestätigung offen.** Alle sechs Auditoptimierungen sowie die beauftragten Sync-/Testreste-/Bedienkorrekturen sind implementiert. Vollständiger schichtenspezifischer Nachweis: [docs/verification/optimization-1.54.6-95.json](docs/verification/optimization-1.54.6-95.json). Die untenstehenden Zwischenstände und tatsächlich gefundenen Fehler bleiben als Historie erhalten.

- **B1 Sync:** präzise datensparsame Fehler/Unterphasen, korrekte nächste Queueaktion, getrennte Aktionszähler, wahrheitsgemäße Automatik-/Manuellflags, zusätzlicher plattformneutraler Kataloghash und begrenzter verifizierter Lesecache. Historische Geschäfts-/CAS-/Publikationshashes erhalten. Fehlende kurzlebige State-Provider-ID ausschließlich durch exakten unveränderlichen Dateinamen plus tatsächlich geprüfte SHA256-Bytes auflösen; existierende fehlerhafte IDs erhalten keinen Fallback. Publikationskettenprüfung und frische Schreibgrenzen unverändert geschützt.
- **B2 Testreste:** ausdrücklich benannter synthetischer Altkunde nach aktueller Vorschau/CAS endgültig entfernt; gezielte Löschsicherung862Bytes/SHA256264de056472ba07767a56cff24e935dc22ef0d61905db701a7f0b813ffd45c1d tatsächlich geprüft. Abschließende Live-Suche MCP0, [TEST]0, Kundenarchiv0. Keine echten Kunden/Arbeitsnachweise geändert. Neue95-Laufzeit kompaktiert bereits ausdrücklich purgierte lokale Testjournale transaktional nach Sicherungsbeleg und Referenzschutz; lokal-only-Tests nie hochladen, zuvor geteilte Tests über neue geschützte Archiv-/Purge-Aktionen entfernen. Minimale Tombstones und haltbare Standard-/Owner-/Batch-Idempotenzquittungen bleiben gegen Wiederanlage erhalten. Veränderte Originale und neuere Cloud-Löschbelege geschützt. Vollständiges Gerätebackup nicht nachgewiesen; unbestätigten ersten Vollbackupaufruf nicht als Erfolg gezählt.
- **B3 Bedienung:** lokale Listenaktualisierung ohne Drive-Abhängigkeit, Such-/Scrollpositionen erhalten, offene Entwürfe/Dialogs geschützt; vertikal begonnene Gesten bleiben vertikal, verschachteltes Scrollen löst kein Pull-Refresh aus. Planung mit beschriftetem +Termin und eingeklappten optionalen Werkzeugen; Heute priorisiert Arbeit, nächsten Einsatz, danach Summen. Keine geplanten Aufträge doppelt anzeigen.156semantische UI-Aktionen und alle117MCP-Werkzeuge erhalten.
- **B4 finale Prüfungen:**394Python PASS/0Skips (37alte QA-Schlüsselerzeugungsfälle ausdrücklich ausgeschlossen),45JVM-Suiten,1047UI–MCP-Assertions,53Browser-Suiten,55lokale synthetische stdio-MCP-Szenarien,29Kamera- und47Cleanup-Hostfälle PASS. Release/AndroidTest/lint84Tasks PASS,0Lintfehler/54Warnungen. **164 tatsächlich native SQLite/MCP/Provider-Prüfungen PASS/0FAIL**; Altquittungsfehler korrigiert und erneut mit unveränderter Erwartung geprüft. Original94→95→final95 ohne Wipe/Uninstall PASS, vollständiger Master/Pending/Relationen/Einstellungen/Tunnel und RUNNING-Timer unverändert. Fresh-isolated, tatsächlicher gebündelter WebView/Verbindungen und finaler Erhaltungsledger PASS.421Quellassets exakt plus2belegte generierte ART-Profile,181Pflanzen/362Bilder. Source/Workspace/tatsächliche signierte APK Privacy PASS,0operative Seeds.

**Unveränderliche Appquelle:** `6972780dc9adbe46e0a91f1f77aa990e303fdaf0`, Tag `source/v1.54.6-build95`, Fingerprint `dfc3cc87f18cb152081a56b25d94a682920d07a880800941db0212ba93544565`. Originalschlüssel/Paket erhalten; tatsächliche APK1.54.6/95,36.208.658Bytes,SHA256 `d54e6f50b8cab37088768c79caebf6cea0e5038959aeb85063c5b77b4a8db112`. Emulatorinstallationsbytes identisch. APK: https://drive.google.com/file/d/1mNMctVtasftStxWfwZboVv6chkQ_CbXv/view . Versionsnachweis/QA separat im privaten Kandidatenordner https://drive.google.com/drive/folders/1qV4B9lXj0P35DSsuIzTD9hIMWgx34bef . Appcode/Tag werden durch die nachfolgende Abschlussdokumentation nicht umgeschrieben.

**Originalquellsicherung:** GitHub-Branch/Tag exakt zurückgelesen; offizieller sauberer Export1262Dateien/531UTF8/731öffentliche Binärressourcen. Original-ZIP68.943.114Bytes,SHA256 `269877afd4fd6aefa4135b51318ef9f44e4465d75d49aac7166c736aa6563c2e`: https://drive.google.com/file/d/1Y8xLj_5d08RZjUeoqmM5f2rWY1D24QWc/view .19Quellartefakte tatsächlich zurückgelesen:18SHA256_BYTES +1ZIP COMPLETE_STREAM_SIZE_ONLY; Transferhelper19Reuse/0Upload/0Duplikate/0Unreferenziert. APK ebenfalls vollständiger authentifizierter Stream mit exakter Größe; wegen32MiB-Materialisierungsgrenze kein behaupteter Remote-SHA-Bytevergleich dieser beiden großen Dateien. Lokale Originalbytes/Hashes und Git-Quellen tatsächlich geprüft. Bestehende89/90/93/94-Originale/Schutzmengen erhalten. Abschließende Protokoll-/Registerdateien separat auf demselben Branch gesichert und offiziell exportiert; Manifest des Dokumentationsexports maßgeblich.

**Abnahmegrenzen:** API30-Software-AVD mit dokumentierten NetworkStack-/SystemUI-OS-Ausfällen und ausschließlich testlokaler Frameworkkonfiguration; keine Pixel-Leistungsmessung. Eindeutig identifizierten SystemUI-Wait-Dialog verlassen und finale Heute-/Timeransicht sichtbar geprüft; kein Appdatenreset. Physische API34+-Randgesten, echte Kamera-/Kontorechte und produktiver Google-Abgleich95 bleiben offen. Pixel meldet weiterhin Original1.54.5/94, Sync action_required/8pending/0review/provider_readback/cloud_confirmed=false. Die fehlende Provider-State-Referenz ist eine beleggestützte Reparaturhypothese, kein bereits bestätigter Live-Fix. Nach aktueller Sicherung und physischem Update95 den vom Auftrag autorisierten expliziten Sync mit echtem Readback prüfen; keine fachliche Queue löschen, keine blinden Alt94-Retries oder stillen Kontofreigaben. Kein main-Merge und keine automatische Pixelinstallation.

## Native Abnahme B4 – Altquittung korrigiert (22.10.2026)

Tatsächliches Original-In-place-Update94→085913e/95 PASS; vollständiger synthetischer Master, IDs/Relationen, Pending, Einstellungen/Tunnel und RUNNING-Timer per unverändertem Vor-/Nachledger erhalten. Native Gesamtprüfung:163PASS/1FAIL. Echter neuer Kompatibilitätsfehler im vorhandenen EstimateNativeAcceptance-Altquittungstest: Nach Test-GC fehlte bei einer alten MCP-Quittung ohne test_local_only ihr zuvor nur im Journal belegter Isolationsstatus. Datensätze blieben entfernt und es erfolgte kein Testupload; Status war falsch. Keine Anpassung dieser Abnahmeerwartung.

Korrektur: Vor dem Entfernen jedes eindeutig lokalen Testjournals passende alte Standard-/Ownerquittung in derselben Transaktion mit dauerhaftem Test-/Purge-Kennzeichen ergänzen. Request-ID, Fingerprint und fachliche Bezüge erhalten; keine neuen Quittungen erzeugen. Native Regression zusätzlich für alte Ownerquittung sowie veränderten Replay-Request/0verbleibende Fachjournale erweitert.085-Kandidat nicht ausgeliefert; neue Abnahme nach neuem gesicherten Build erforderlich.

Emulator nach AOSP-Ursachenprüfung stabilisiert: im ausschließlich eigenen synthetischen AVD DeviceConfig connectivity/always_ratelimit_networkstack_crash=true und min_uptime_before_crash=604800000 (vorher beide null) tatsächlich gesetzt/zurückgelesen. Diese Debug-OS-Konfiguration verhindert NetworkStack-bedingte Framework-Bootschleifen; daraus folgt keine Abnahme echter Google-/Netzverbindungen. Kein Produktivgerät/Systemkonto verändert und keine Appdaten gelöscht. Danach erstmals Vorupdateledger und echtes Update erfolgreich; vorherige Fehlversuche nicht als Abnahme gezählt.

## Datenintegrität B4 – Löschbelegpräzedenz korrigiert (22.10.2026)

Zusätzlicher Review fand einen Randfall im neu eingeführten Test-GC: ein alter lokaler Tombstone durfte den späteren geteilten Purge-Beleg nicht überschreiben. Neuer reiner Projektor bevorzugt existierende serverseitige Purge-Belege byte-/hashgleich; private Provenienzfelder bleiben außerhalb des projizierten Fachbelegs. Seit dem bestätigten Löschen geänderte Originale/Archive werden nicht durch den alten lokalen Beleg versteckt.16gezielte reine JVM-Prüfungen einschließlich unverändertem Eingabehash im Konfliktfall PASS;45JVM-Suiten insgesamt PASS. Native SQLite-Integration um tatsächlichen Belegvergleich nach Bestätigung ergänzt. Keine Geschäfts-/Publikationshashes geändert. Vorbereiteter6f4c4f6-Kandidat/Quellstand dadurch überholt und nicht ausgeliefert; aktueller Kandidat wird aus neu gesichertem Stand neu gebaut/signiert/exportiert.

Software-AVD: Emulatoroption ro.hw_timeout_multiplier wurde vom Emulator abgewiesen (nur qemu.* unterstützt), daher keine Wirksamkeit behauptet. Danach ausschließlich im nachgewiesenen dedizierten qemu-AVD adbd alsroot neu gestartet, ro.hw_timeout_multiplier=10 tatsächlich gesetzt/zurückgelesen und Android-Runtime ordentlich stop/start durchgeführt. Datenpartition/APKs unverändert erhalten; kein Wipe/Uninstall. Physischer Pixel blieb unberührt. Native Tests weiterhin erst nach tatsächlichem Erfolg PASS.

## Gesamtabnahme B4 – Zwischenstand (22.10.2026)

Release-/AndroidTest-Build und lintRelease PASS84Tasks aus gesichertem6b55163, noch unsignierter Testbuild.53Browser-Suiten ausgeführt;51PASS und2alte Präsentationserwartungen zunächst FAIL. Beide gezielt korrigiert und erneut tatsächlich PASS: ui-preview14Checks prüft reale Erreichbarkeit statt früherer fester unterer Bildschirmhälfte; p3-customer-hub wartet auf tatsächlich geladenes Field-Stylesheet und bestätigt neue Arbeit-vor-Summen-Reihenfolge. Weiterer vollständiger Schlusslauf folgt.44JVM-Suiten/1047Paritätsassertions,29Kamera-Hostfälle,47Cleanup-Koordinatorfälle und55echte lokale MCP-stdio-Szenarien PASS. Python-Gesamtprüfung noch im Gang.

Emulator-Umgebungsfehler vor Appupdate belegt: am_anr TelephonyDebugService, anschließend system_server IllegalStateException „Lost network stack“, Framework-Neustart. Zweite rein lesende Instrumentierungsprobe meldete Error type2. Keine Appregression daraus abgeleitet; kein Vorupdateledger erfunden. Bestehende AVD-Datenpartition bleibt erhalten, ausschließlich eigenen Emulator ordentlich beenden/neustarten und Softwareemulation stabilisieren. Native Abnahme/94→95-Update noch offen. Keine physische Pixelinstallation oder Kontoänderung.

## Umsetzung B2/B3 – ergänzter Schutz und fortgesetzte Abnahme (22.10.2026)

38gezielte Sync-/Automatik-/Queue-/Cache-/Aktive-Arbeit-Prüfungen und8State-Referenzfälle PASS. Aktive Timerreferenzen jetzt vor UI-/MCP-Archivierung, Einzelpurge und Sammelpurge gemeinsam geschützt. Bereits lokal purgierte Sammeltestaktionen werden ebenfalls nach geprüftem Backup kompaktiert; sofortige lokale Datei-GC ausschließlich für nie geteilte Tests, weiterhin keine geteilte Medienlöschung ohne normalen Syncbeleg. Neuer nativer Fall prüft geteilten Alt-Testkunden: exakt2frische CAS-/abhängigkeitsgeschützte Drive-Aufräumaktionen, keine lokale Wiederanlage. Voller nativer Nachweis noch offen.

Today-Darstellung priorisiert bewusst auch im Leerlauf Arbeit vor Termin/Summen. Alte Darstellungserwartung in p3-customer-hub entsprechend eng angepasst; laufende Arbeit bleibt weiterhin zuerst. Erster synthetischer Vorupdate-Ledger scheiterte an Instrumentierung nach langsamem Emulatorstart; kein Ledger überschrieben, keine Installation/kein Reset. Dienste danach erneut lesend geprüft. Browsergesamtlauf ansonsten abgeschlossen; erneuter gezielter Lauf der angepassten Präsentation folgt.

## Umsetzung B2/B3 – gesicherter Implementierungscheckpoint (22.10.2026)

**B2 Sync/Testreste:** Fehlende kurzlebige Provider-State-ID darf ausschließlich durch aktuelle Dateien mit exakt passendem unveränderlichem Namen und tatsächlich geprüften SHA256-Bytes aufgelöst werden. Existierende IDs mit Lese-/Berechtigungs-/Inhaltsfehlern erhalten keinen Fallback. Vollständige Publikations-/Kettenprüfung bleibt danach verpflichtend.8neue JVM-Fälle PASS, übrige44JVM-Suiten PASS. Tatsächliche Live-Fehlerursache bleibt ohne neue Pixel-Laufzeit unbestätigt.

Eindeutiger alter MCP-Testkunde per aktuellem Hash archiviert, erneute Purge-Vorschau zulässig, endgültig entfernt. Automatisch erzeugte gezielte Löschsicherung mit1Kunde/0Originaldateien tatsächlich inspiziert:862Bytes, SHA256264de056…c1d, verified_at_capture=true. Frische MCP-Suche nachMCP:0Treffer; zuvor separat gelesene [TEST]-Suche ebenfalls0 und Kundenarchiv0. Screenshotreste und tatsächlicher MCP-Lesestand unterscheiden sich; alte UI kann bei externen Änderungen bis zum nächsten Neuladen veraltet bleiben. Keine echten Kunden geändert. Keine Produktivdaten/IDs in Checkout oder Tests übernommen. Ein zunächst angeforderter Vollbackup-Aufruf hatte keine bestätigte Antwort; kein Vollbackup-Erfolg behauptet und keine blinde Wiederholung. Gezielte Löschsicherung unabhängig davon bestätigt.

Neue Laufzeit kompaktiert ausschließlich bereits ausdrücklich endgültig entfernte lokale Testjournale nach geprüftem Löschbackup, in derselben SQLite-Datenbank/Transaktion. Minimale Tombstones und Idempotenzquittungen bleiben. Lokal neu erzeugte Tests werden nie hochgeladen; bereits im geteilten Snapshot vorhandene entfernte Testoriginale bekommen separate versionsgeschützte Archiv-/Purge-Aktionen für Drive, keine Änderung alter Journal-IDs. Aktive Sitzung/Referenzen stoppen Bereinigung. Lokal-only-Testmedien erst nach geschütztem Tombstone und erneuter Referenzprüfung sammeln; geteilte Medien bleiben im normalen Belegpfad. Native Neustart-/Replay-/Datenerhaltfälle ergänzt, Ausführung noch ausstehend.

**B3 Bedienung:** Neue lokale Änderungsbenachrichtigung aktualisiert Listen ohne erfolgreichen Drive-Abgleich; offene Formulare/Dialogs bleiben geschützt. Filter und Hauptmenü-Scrollposition bleiben erhalten. Vertikal zuerst begonnene Bewegung bleibt Scrollen; verschachtelte gescrollte Listen lösen kein Pull-Refresh aus. Planung mit beschriftetem +Termin und optionaler Werkzeuggruppe; Heute priorisiert Arbeit, nächsten Einsatz, danach Summen, und vermeidet Doppelanzeige bereits terminierter Aufträge.8neue konkrete Browserinteraktionen PASS, bestehende Navigations-/Gesten-/Timer-/Formularsuiten PASS. Gezielt geänderte alte Darstellungserwartungen dokumentieren neuen Filtererhalt/benannte Primäraktion/eingeklappte Werkzeuge; unveränderte Editor-Speicher-/Retry-Funktionen weiterhin eingefroren geprüft. Kompletter53-Suiten-Browserlauf noch im Gang.

Android-Release-/AndroidTest-Javakompilierung PASS33Tasks. Vorheriger Versuch stoppte korrekt am Quellreview-Gate während laufender UI-Änderungen; nach Review erneut erfolgreich. Kandidatenversion1.54.6/95 reserviert, Original94 unverändert. Noch keine95-APK signiert/ausgeliefert/installiert. Weitere Gesamt-, Privacy- und native Abnahme folgen.

## Umsetzung B1 – sechs Auditoptimierungen implementiert (22.10.2026)

Queueaktion vor tatsächlicher Übergabe korrigiert; additive action_counts unterscheiden gespeicherte/lokale/Upload-/Prüfaktionen; native Statusflags und SyncPolicy berücksichtigen ausgeschaltete Automatik und explizite Anforderungen. Kataloge haben zusätzlich catalog-json-v1 mit unabhängiger JSON-Zeichenkodierung; alte Katalog-/Geschäftshashes sowie CATALOG_CHANGED bleiben erhalten. SAF erhält genaue Leseunterphasen, stabile datensparsame IO-Codes und einen4MiB-Cache verifizierter unveränderlicher Bytes/anfänglicher Inventarliste ausschließlich bis zur Schreibgrenze. Vor Journal-/Snapshotveröffentlichung Cache vollständig verwerfen; frisches Inventar und Originalvergleich bleiben Pflicht. Kein Wechsel des Kontos/Transports.

43JVM-Suiten einschließlich35neuer gezielter Prüfungen PASS;1047UI-MCP-Paritätsassertions PASS;7Python-Vertrags-/Baselineprüfungen PASS. Historische P1-Baseline unverändert. Explizite, eng begrenzte Funktionsevolution sync-optimization-contract-v1 bindet genau DriveJournalRules-Quellhash und qa_case_14-Ausgabehash; übrige Vertragsgruppen bleiben strikt eingefroren. Native Providerfälle ergänzt; deren tatsächliche Ausführung und vollständige Gesamtprüfung stehen noch aus. Keine Produktivdaten in Fixtures oder Checkout. Live-Sync weiterhin offen, bis neue Laufzeit tatsächlich installiert/geprüft ist.

## Umsetzung des Auditauftrags – Blöcke und Abnahme (22.10.2026)

Ausgang: unveränderte Original-App1.54.5/94, Auditbranch3355a497; main und bestehende Arbeitsbranches frisch verglichen. Nutzerauftrag autorisiert die sechs dokumentierten Optimierungen, gezielte vollständige operative Entfernung der ausdrücklich benannten MCP-Testkunden sowie UX-/Sync-Korrekturen. Keine produktive Testanlage. Historische Originale/Backups und minimale Lösch-/Idempotenzbelege bleiben geschützt.

1. **Sync und Verträge:** genaue nicht personenbezogene Leseunterphasen/IO-Codes; korrekte nächste Queueaktion; getrennte Roh-/Lokal-/Upload-/Prüfzähler; wahrheitsgemäßer Automatikstatus; zusätzlicher plattformneutraler Kataloghash; begrenzter laufbezogener Cache ausschließlich verifizierter unveränderlicher Inhalte. Alte Geschäfts-/CAS-/Publikationshashes und öffentliche Tool-/UI-Schemas bleiben erhalten. Vor Schreibvorgängen weiter frische Inventar-/Inhaltsprüfung. Abnahme: gezielte Fehler-, Paralleländerungs-, Hash-, Queue- und Statusfälle plus bestehende Vertrags-/Fachtests. Etwaige beauftragte additive Ausgabedeltas explizit gegen unveränderte historische Baseline dokumentieren.
2. **Testreste:** vorhandene Zielkunden und Verknüpfungen lesen, Sicherung verifizieren, aktuellen Hash und Lösch-/Purge-Vorschau anwenden; ausschließlich eindeutig synthetische abhängige Datensätze entfernen. Lokale Testjournale dürfen weder in Drive gelangen noch entfernte Testdaten wiederherstellen. Abnahme: Wiederholung, Wiederöffnung, laufende Sitzung und echte Referenzen geschützt; Live-Nachlesen getrennt vom Emulator belegen.
3. **Bedienung:** verlässliche Gestenrichtung, verschachteltes Scrollen, sichere Menüwechsel/Entwürfe; Planung und Heute mit klarer Priorität und weniger gleichzeitig sichtbaren Optionen. Funktionen, persönliche bezahlt-Markierung, Timer und156UI-Aktionen erhalten. Abnahme: echte Browserinteraktionen bei schmaler Ansicht/großer Schrift und gebündelter Emulator-WebView.
4. **Gesamtabnahme und Sicherung:** passende vollständige Regression, Privacy, nativer Erhaltungsledger und Originalkandidat aus gesichertem Quellstand; private Quellkopie und belegte Übergabe. Physische Pixelinstallation bleibt gemäß AGENTS.md bis Sicherung und ausdrücklicher Installationsfreigabe offen. Produktiver Sync gilt erst nach tatsächlich erfolgreichem Rücklesen als gelöst.

Status: Umsetzung begonnen; noch kein Block als PASS abgeschlossen, keine neue App installiert.

## Funktions-/Sync-Audit R3 – dauerhafte Sicherung und Abschluss (22.10.2026,17:37UTC)

Prüf-/Exportcheckpoint **49d112d7b77f32dae5b2c2a71215dd8b1cb405ff** auf codex/function-sync-audit-20261022 committed/gepusht und exakt von GitHub zurückgelesen. Offizieller source_lifecycle-Export PASS: **1249Originaldateien,518UTF8-Dateien vollständig rekonstruierbar,731öffentliche Binärressourcen**, gleicher Appfingerprint1d7e7d73…fab25. Original-ZIP **68.915.847Bytes**,SHA256 **7fc8569b1f5cde44f246c8b9ac6471488e3e3f87b5f6a7a5857774c9145b513f**, Drive-Datei **1iYeJfMDKhMZ-RJCJ22KGfGqG63doFweK** im vorhandenen privaten Entwicklungsbereich/[Auditordner](https://drive.google.com/drive/folders/1VKq658WXV87bP_LQkkT0MDl3ZWPALK5A). Keine neue App-/APK-Auslieferung und keine produktiven Daten exportiert.

Alle **19Quellenartefakte tatsächlich zurückgelesen**,18SHA256_BYTES über authentifizierte Referenz und echte materialisierte Bytes; großes ZIP1COMPLETE_STREAM_SIZE_ONLY wegen32MiB-Materialisierungsgrenze. Seine lokale SHA ist kein zweiter Remote-Bytehash. Frische vollständige Ordnerliste: exakte Namen/IDs/MIME/Größen,alle shared=false,0Duplikate. Offizieller drive_source_transfer-Abschluss mit tatsächlichen Readback-Belegen:19reuse,0upload,0verify_existing,0blocked_duplicates,0unreferenced,0deletions. Früheren guten Generationen/Original94 unverändert erhalten.

Erst danach vier vorhandene, zuvor gelesene und unmittelbar auf unveränderte Metadaten geprüfte KI-/Startwegweiser aktualisiert; bestehende Datei-IDs,Eltern und MIME erhalten. Alle vier erneut heruntergeladen und bytegenau identisch geprüft:4816Bytes,SHA256 **106b6c9369bc917f7b7526cfa4f4dd97cecfab294251bd4a5d1b7558b4bc92e1**. Original94-APK/Quelltag/-archiv bleiben getrennt verlinkt; neu beobachteter Pixel94,Auditbranch und offener Live-Syncfehler ausdrücklich genannt. Keine Freigaben/Konten/Betriebsordner geändert und keine Nachricht an Dritte versandt.

Abschließende echte Emulator-Sichtprüfung zeigt lesbare laufende synthetische Arbeit mit Pause/Beenden/Foto und drei Hauptzielen ohne OS-Dialog. Die zuvor tatsächlich beobachteten OS-ANRs bleiben als Umgebungsgrenze dokumentiert; keine uneingeschränkte Leistungsabnahme. Eigenen dedizierten AVD anschließend ordentlich beendet, Datenpartition erhalten; kein Reset/Uninstall. Der nachfolgende Sicherungs-/Übertragungsnachweis ist eine reine Dokumentationsergänzung nach dem unveränderlichen49d112d-Export. Original-Appcode bleibt bytegleich, GitHub enthält auch diese Ergänzung; keine erneute APK oder unnötige Exportgeneration erzeugt.

## Funktions-/Sync-Audit R2 – vollständiger Cloud-Prüflauf abgeschlossen, Live-Sync offen (22.10.2026)

**Ergebnis: automatisierter Cloud-Prüfumfang PASS, produktiver Drive-Abgleich FAIL/offen. Keine vollständige uneingeschränkte Geräte-/Google-Abnahme.** Vollständige Schichten-/Funktionsmatrix, sechs priorisierte Optimierungen, technische Beleg-SHA256 und Grenzen: [docs/verification/function-sync-audit-2026-10-22.json](docs/verification/function-sync-audit-2026-10-22.json). Appcode, Daten-/Journalformate, eingefrorene Domainregeln,117Toolverträge und156UI-Aktionen wurden in diesem Audit nicht geändert. Keine spekulative Reparatur, Hashumstellung, produktive Testanlage oder neue APK-Auslieferung.

**Tatsächlich ausgeführt und bestanden:**393Python/0Skips (37historische Schlüsselerzeugungstests ausdrücklich ausgeschlossen),42JVM-Suiten/1047Paritätsassertions,52Browser-Suiten,55synthetischeMCP-stdio-Szenarien,29Kamera-Hostfälle,47Cleanup-Koordinatorfälle und **159native Android-/SQLite-/MCP-Prüfungen,0Fehler**. Native Providerfälle schließen falschen Readback, Duplikatnamen, Schreibfehler/Retry,7-fache Konfliktwiederholung, Original-vor-Metadaten, Rename-/Readback-Unterbrechung und Foto-Lazy-Restore ein. Alle Fachgruppen laut Matrix geprüft: Kunde/Auftrag, Arbeit/Timer, persönliche Markierung/Zahlung, Foto/Datei, Rechnung/Angebot/KV, Archiv/Papierkorb/Backup, Planung/Kalender/Wetter, Pflanzen und Navigation.117Verträge sind vollständig geprüft; daraus folgt keine117-fache produktive Positivausführung.

**Zusätzliche aktuelle Emulatorabnahme PASS:** echter frischer leerer Appbestand sowie fresh_isolated ohne Credentials/automatischen Kontozugriff; synthetische Updatefixture; normale **94→94-In-place-Reinstallation**, kein Wipe/Uninstall; vollständiger Master/IDs/Pending/Settings/Tunnelkonfiguration/aktiver Timer erhalten; echter gebündelter WebView mit Navigation, Planung, Kundenakte, Papierkorb, Menü/Zurück und drei nativen Verbindungsmasken; abschließender erneuter vollständiger Verify PASS. Kein neu ausgeführtes93→94-Versionsupgrade behauptet. Vorbestehende historische Updatebelege bleiben getrennt erhalten.

**Umgebungsgrenze:** Software-AVD ohne KVM hatte echte OS-Dialoge „Process system isn't responding“ und anschließend „System UI isn't responding“. Beide tatsächlich visuell identifiziert und nur ihren Wait-Button gewählt; keine Appdaten/Installation gelöscht. UI-Endlauf meldet danach selbstständig PASS, finaler Datenerhalt-Verify ebenfalls PASS. Diese OS-ANRs bleiben eine Grenze für Geräte-/Leistungsbehauptungen. Kein App-/Pixel-ANR-Fix aus der Dialogbedienung abgeleitet. Native Lifecycle-Logs und deren SHA256 im Audit; drei synthetische Browseransichten zusätzlich visuell geprüft, OS-Grenze bei native Screenshots ausdrücklich dokumentiert.

**Artefakt-/Schutzabschluss PASS:** offizieller Originalsignierweg, ein unverändertes Originalzertifikat/v2/v3/16KiB,421Mainassets plus2exakt geprüfte Compiler-ART-Profile,31operative DEX-Klassen,181Pflanzenprofile/362Bilder und0operative Seeds. Source/gesamter Workspace/tatsächliche signierte PrüfAPK PASS; P1-Vertrag exakt899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813. Gradle84Tasks/Lint0Fehler/54vorbestehende Warnungen. Interne PrüfAPK b1eb35d0…e74eb aus7f734dd hat gleichen Appfingerprint wie Original94/429dfa11, ist aber ausdrücklich ein anderer Prüfbau und kein Ersatzdownload. Original94 und alle Quellen/Tags unverändert erhalten.

**Live-Endbefund:**8Uploads/0Reviews, rohe pending_actions58, TRANSFER_FAILED während reading, letzter erfolgreicher Abgleich15:24:09UTC, provider_readback und cloud_confirmed=false. Queue-JSON, gespeicherte Collection-Counts und Geschäftsintegritätshash gegenüber erster eigener Lesung identisch; dieser Hash schließt Pending-Overlay nicht ein. Kalender/primäre Belegung zuletzt geprüft; dauerhafte Google-Production-Freigabe, Kamera/GPS/Share/Mail, echte Serverbestätigung, physisches Update/Restore und Android34+-Gesten bleiben gesondert.49Backup-Metadaten sind kein49-facher Restorebeleg. Ursache des echten Transferfehlers bleibt unbestimmt; **kein request_sync und kein fachlicher/administrativer Pixel-Schreibaufruf in diesem Audit**.

Standregister führt nun die tatsächlich beobachtete Pixelversion94, ursprüngliche Appquelle und Auditbranch getrennt. Originalquellen dauerhaft im bestehenden privaten GitHub; nach diesem Prüfcheckpoint offizieller verlustfreier Export in den privat bestätigten Entwicklungsordner [Funktions-Sync-Audit](https://drive.google.com/drive/folders/1VKq658WXV87bP_LQkkT0MDl3ZWPALK5A). Erst erfolgreicher Upload/Readback zählt als Drive-Sicherung; Beleg dazu folgt getrennt, kein leerer Ordner als Sicherung ausgegeben.

## Funktions-/Sync-Audit R1 – reproduzierte Optimierungen und aktuelle Installation (22.10.2026)

Standregister aktualisiert: ursprüngliche94 ist jetzt auf dem echten Pixel beobachtet, zusätzlich vom Nutzer bestätigt. Das ist kein durch diesen Task ausgeführtes Pixelupdate und kein nachträglich erfundener Datenerhalt-/Kamera-/Share-Test. Zweite Live-Übersicht weiterhin TRANSFER_FAILED/reading/retry_wait,6Versuche,8Uploads/0Reviews; gespeicherter Geschäftshash und Collection-Counts gegenüber erster Lesung unverändert. Der in AGENTS verlinkte MCP-Testbericht wurde lesend berücksichtigt: unabhängiger früherer94-Lauf beschreibt denselben Transferfehler. Dessen produktive Tests/Schreibaufrufe sind keine eigenen Auditaktionen und werden nicht als eigene Ausführung gezählt.

Priorisierte Verbesserungen, **in diesem Prüfauftrag nicht als produktiver Fix umgesetzt oder abgenommen**:

1. **P1 – reale Syncursache eingrenzen:** reading in Ordnerinventar/Legacy-Gesamtstand/Veröffentlichungskette/Mobile-Journale unterteilen und einen nicht geheimen, stabilen Fehlergrund liefern. Aktuell maskiert TRANSFER_FAILED die konkrete I/O-Ursache. Gespeicherte Ordnerberechtigung beweist keine aktuelle Lesbarkeit beim Speicheranbieter. Kein Kontowechsel, Retry-Schreiben, Dateiersatz oder Queue-Cleanup aus diesem Befund ableiten. Direkter Drive-REST-Pfad ist bereits vorhanden, Original-Rechnungsabschluss benötigt ihn; eine Umstellung braucht den bestehenden Ordnervergleich und bewusste Gerätefreigabe.
2. **P2 – passende nächste Aktion:** synthetischer Original-JVM-Aufruf bestätigt bei archive und purge_v1 jeweils pending_upload=1/review=0/state=sync_pending, aber next_action=review_action. Vor tatsächlicher Übertragung ohne Konflikt sollte die Rückmeldung retry_sync empfehlen. Übertragene geschützte Altlöschungen und echte Konflikte weiterhin zurückhalten. Änderung der eingefrorenen Domainregel separat und ausdrücklich regressionsprüfen.
3. **P2 – Zähler klar trennen:** echter Readback pending_actions58 gegenüber queue.total8. Reine synthetische Gegenprobe2gespeicherte Aktionen, davon1_local_only → pending_actions2/queue1. Rohaktionen, lokale Testbelege, Uploads und Reviews explizit benennen; die alten Felder nicht inkompatibel umdeuten und keine Belege löschen.
4. **P2 – Automatikstatus wahrheitsgetreu:** SyncPolicy.describe mit konfiguriertem Drive, pending_upload1 und automatic_enabled=false verspricht weiterhin automatischen Abgleich. Aktueller nativer Status transportiert die beiden Automatik-/manuellen Anforderungsflags nicht. Flags rein lesend ergänzen, abgeschaltete Automatik und expliziten manuellen Auftrag getrennt anzeigen; Offline-Speicherung/Retry und Schalter unverändert erhalten.
5. **P2 – plattformfeste Katalogprüfung:** alle117Live-Werkzeugdefinitionen sind semantisch identisch, beide unterschiedlichen Kataloghashes durch `/`-Escaping exakt reproduziert. Diagnose/Buildtools um deklarierte Serialisierungsvariante bzw. zusätzlichen versionierten semantischen Kataloghash ergänzen. Geschäfts-, Request-, CAS- und Publikationshashes nicht still neu kanonisieren; CATALOG_CHANGED bleibt geschützt. Eine neue Operation/Schemaänderung darf durch den Vergleich nicht verschwinden.
6. **P3 – SAF-I/O messen und bündeln:** aktive Runtime verwendet android_provider, während vorhandene parallele Übertragung und starke Metadaten-/Immutable-Caches vor allem direct_drive optimieren. Vollständige Veröffentlichungsprüfung und frische Vergleiche vor Schreiben erhalten; wiederholte Ordner-/Commitlesungen pro Lauf instrumentieren und nur bereits hashgeprüfte unveränderliche Bytes begrenzt wiederverwenden. Kein unsicheres Überspringen anhand eines Dateinamens oder unzuverlässiger Änderungszeit und keine ungemessene Pixelbeschleunigung behaupten.

Weitere echte reine Leseprüfungen PASS: veralteter UI-Kataloghash liefert CATALOG_CHANGED; unbekannte synthetische Aktionskennung bleibt unknown/cloud_confirmed=false; gemeinsamer Angebotsvertrag erreichbar; Pflanzenkatalog181Einträge; Backupverzeichnis49Einträge. Letzteres ist keine49-fache Integritäts-/Restoreprüfung. Keine produktiven Kunden-/Arbeits-/Dokumentdaten für diese Prüfungen angelegt, geändert oder exportiert.

Interner Original94-Prüfbau aus7f734dd, gleicher Mainfingerprint1d7e7d73…fab25,421Assets bytegleich,0Seeds, Originalzertifikat a939a34e…f37bff8/v2/v3/16KiB PASS. SHA256 **b1eb35d05209d102f92c0d1ce6866a17a97a082974e90a09824255e1a92e74eb**, Runner **65a07eb4ba022de0eb73aa5dea362d9e433f356f0ebdbc48f83e25d61b567d65**. Diese Bytes ersetzen nicht die ausgelieferte94. Im dedizierten neuen AVD überschritt die erste35-MiB-Installation180sRückmeldefrist; anschließend tatsächliche installierteAPK vollständig per SHA256 gegen diese Bytes bestätigt, kein blindes Wiederholen. Nur fehlenden kleinen Runner danach installiert. Native Gesamtabnahme läuft noch; Host-Schlusslauf393/0Skips mit tatsächlich signiertem Prüfartefakt und allen drei Privacy-Gates PASS.

## Funktions-/Sync-Audit R0 – aktuelle Pixelbasis und Hostregression (22.10.2026)

Nutzerauftrag: alle erreichbaren Funktionen per Emulator und MCP prüfen und Verbesserungen an Funktionalität/Synchronisierung ermitteln. Abnahme: aktuelle Appquelle eindeutig zuordnen; vorhandene Fach-, Fehler-, Offline-/Retry-/Replay-/Neustartprüfungen ausführen; alle117 Werkzeugverträge und156 UI-Aktionen abgleichen; native SQLite/Provider/WebView tatsächlich ausführen; echte Pixel-/Google-Grenzen getrennt ausweisen. Keine produktiven Testschreibvorgänge oder neue APK-Auslieferung aus diesem Audit ableiten.

Frischer GitHub-main e499e745832449ce0202d04d9903495248e8b034 und alle vier vorhandenen Arbeitsbranches gelesen. Aktueller Pixel **1.54.5/94**, Original-Appquelle **429dfa11c622cd27228a5f572c9c916ccc137e82**, SOURCE_DIRTY=false; vom Nutzer zusätzlich bestätigt. Der zunächst wiederhergestellte91-Checkout war daher keine aktuelle Testbasis. Dessen abgeschlossene JVM-/stdio-Läufe bleiben getrennt; sämtliche berichteten Endläufe wurden anschließend auf **7f734dd1e6cde876f5268e125fb302abb75756a8** erneut ausgeführt. Genau ein eigener Auditbranch codex/function-sync-audit-20261022 aus diesem neuesten vorhandenen Arbeitsstand; keine fremde Arbeit überschrieben oder nach main übernommen. Main-/Gradle-Fingerprint **1d7e7d73b6801705ece1488f79f180c463ba0308083e695583181d9cbc8fab25**, identisch zur installierten94.

Tatsächlich abgeschlossen: **393 Python PASS/0Skips**,37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen; **42 JVM-Suiten/1047 UI-MCP-Paritätschecks,52 Browser-Suiten,55 synthetische MCP-stdio-Szenarien,29 Kamera-Hostfälle und47 Cleanup-Koordinatorprüfungen PASS**. Android Release/AndroidTest/Lint84Tasks PASS. Kein physischer Kamera-/Providerbeleg aus Host-Doubles abgeleitet. Source/Workspace/unsignedAPK-Privacy PASS, Bootstrap0Seeds,181 Pflanzenprofile/362 Offlinebilder und P1-Vertrag unverändert. Restore-Setup korrigiert: fehlendes acorn nur außerhalb Repository installiert;20 unversionierte alte Screenshots ohne Provenienz verlustfrei außerhalb Checkout verwahrt, keine falschen synthetischen Belege erzeugt. Anfangs fehlende Google-Auth-Buildabhängigkeit über denselben offiziellen Google-Mavenbestand auf erlaubtem dl.google.com mit vorhandener Proxy-/CA-Konfiguration bezogen; kein geänderter App-Buildvertrag.

Live-MCP ausschließlich lesend:117Tools=52Lesen/65Schreiben/17destruktiv annotiert;156 eindeutige UI-Aktionen vollständig100+56/hashgebunden,145 fachlich abgedeckt/11 Plattformgruppen. Alle117 vollständigen Werkzeugdefinitionen semantisch exakt gleich zur kompilierten Quelle. **Wichtiger Auditbefund:** JVM/Python-Kataloghash235f683d…14314 und Android8780ff5f…39aa unterscheiden sich ausschließlich durch Androids zusätzliches Escaping von `/`. Ersetzen dieser Escapevariante reproduziert den Livehash exakt; ebenso UI177e00aa…3670 → c9ac7931…62e3. Kein veralteter Connector aus diesem Hashunterschied ableiten; bestehende Record-/Request-/Publikationshashes keinesfalls still ändern.

Live-Selbsttest:7PASS,1FAIL(sync_state),2NOT TESTABLE. **Realer offener Befund:** android_provider, persisted_tree_permission=true, phase=reading, TRANSFER_FAILED, retry_wait/5Versuche,8Übertragungen ausstehend/0Reviews; letzter erfolgreicher Abgleich15:24:09Z. Kalender/primäre Belegung und Wetter zuletzt checked/verified. Keine Ursache wie OAuth-/Dateischaden/-dubletten aus dem generischen Transfercode behauptet; kein produktiver Retry oder Kontowechsel als Test. Rohdatensätze, Geschäftstexte und Queue-IDs werden nicht in den Audit übernommen.

Nächster Schritt: dedizierten leeren GartenpiratCloud-Softwareemulator fertig starten, gleiche Original94-Appquelle und bestehenden originalsignierten Runner nativ prüfen; danach priorisierte Optimierungsliste und tatsächliche Restgrenzen ergänzen. Der neu signierte interne Prüfbau hat denselben Appfingerprint, aber einen anderen Dokumentationscommit/APK-Hash als die ausgelieferte94; er ist kein neuer Download oder Pixelupdate. Betrieblichen Syncfehler erst nach echtem Readback als behoben bezeichnen.

## APK94 R-Abschluss – Original1.54.5/94 gebaut und nativ qualifiziert (22.10.2026)

Exakte unveränderte APK-Quelle **429dfa11c622cd27228a5f572c9c916ccc137e82**, unveränderliches **source/v1.54.5-build94**, Main-/Gradle-Fingerprint **1d7e7d73b6801705ece1488f79f180c463ba0308083e695583181d9cbc8fab25**. Regulärer Branch-/Tag-Push und exaktes GitHub-Readback PASS; finales Protokoll/Standregister in einem getrennten Dokumentationscommit mit demselben App-Fingerprint. Die frühere Nur-Quellcode-Grenze ist durch den ausdrücklichen APK-Folgeauftrag für diese94 aufgehoben. Original93 bleibt erhalten.

Tatsächlich ausgeführt: **393 Python PASS/0Skips** (37 QA-Schlüsselerzeugungstests ausgeschlossen), **42 JVM-Suiten,52/52 vollständige Browser-Suiten,55 synthetische MCP-Szenarien,1047 UI/MCP-Paritätschecks PASS**. Gradle Release/AndroidTest/Lint84Tasks PASS,0Fehler/54Warnungen. Die initiale Browserabweichung in Mehr war ein echter Initialisierungsfehler; vorhandene13-Menü-Baseline unverändert, gezielte Späteinfüge-Regression und anschließend vollständiger52er-Lauf bestanden. Erster unsignierter Zwischenbuild nie ausgeliefert.

**Originalsignierung und tatsächliche APK:** ausschließlich offizieller Cloud-Signer, kein Ersatzschlüssel/CI. Paket at.hausgarten.service; ein bestehender Originalsigner, Zertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8; v2/v3,16KiB-Ausrichtung, tatsächliche DEX-Commit/Version/Fingerprint/DEBUG=false/UI_PREVIEW=false/SOURCE_DIRTY=false PASS.421 Originalassets bytegleich,2 erzeugte ART-Profile geprüft,181 Pflanzenprofile/362 Bilder,0 operative Seeds. Source/Workspace/tatsächlicheAPK-Privacy PASS.

**Dedizierter Cloud-Emulator API30:** qemu=1 und GartenpiratCloud vor Zugriff geprüft, Original93→94 ausschließlich install-r ohne Löschen/Reset/Deinstallation. Bestehende historische Baseline erhalten; zusätzlich neues lesendes93→94-Ledger vor Update/nach Update/nach allen Prüfungen: vollständiger Master, IDs/Beziehungen, Pendingqueue und persistierte laufende Sitzung hashgleich. Fresh-isolated leer/ohne Zugangsdaten,159 Android-SQLite/MCP-Tests ohne Fehler, echter gebündelter WebView mit neuen Anzeige-/Menü-/calendar_busy_*-Lesebefunden sowie finaler Snapshot PASS. Erster Coldboot verlor den Android-Systemdienst nach NetworkStack-Fehler bereits auf Original93; ordentlicher Emulatorneustart ohne Datenänderung mit3072MiB und vollständiger Framework-Readiness stellte die QA-Umgebung wieder her. Kein Appfehler oder Datenreset als Lösung behauptet.

**Grenzen:** Pixel unverändert Original1.54.4/93; frischer technischer MCP-Read bestätigt verbundenen Kalender/checked/Fehler leer. Keine produktiven Schreib-/Synchronisierungs-/Termintests. API34+ EDGE_LEFT-/physische Android37-Gesten nicht mit API30 emulierbar, API29–33 weiterhin OS-Grenze200dp; Kontomasken bleiben separate Aktivitäten. Nur Hauptkalender-Belegung mit ausdrücklicher und aktueller Freigabe. Google-Projekt External/Testing, private Homepage-/Datenschutzentwürfe unveröffentlicht; dauerhafte Google-Freigabe nicht abgenommen. Live-Open-Meteo-Abruf aus dieser Egress-Policy nicht ausgeführt; vorhandene synthetische T+14-Nativregression erneut bestanden.

**Private Bereitstellung:** APK **1pwo3kz2shKIyPnXYV0KbL9Szyvya5OS8**,36200466Bytes,SHA256 **975ce32ca7e52338182c0f743044afbd57edeeaa9d376bd31292b1060809cc3c**. Original-ZIP **189LoXjhKV9a0GhsnCYpHfsL_60e5wBpL**,68897322Bytes,SHA256 **d1d580773d470273ea803f9f7a6461ed305c3882c136f3ef60587458c1940dde**,1247Originaldateien,516UTF8 vollständig rekonstruierbar.19Quellenartefakte tatsächlich zurückgelesen:18SHA256_BYTES+1ZIP COMPLETE_STREAM_SIZE_ONLY;0offen/dupliziert. Auch APK vollständig authentifiziert gestreamt/Größe bestätigt;32MiB-Materialisierungsgrenze bedeutet ausdrücklich keinen Remote-SHA-Bytevergleich. Ordner **1Gq89Ru3bCL-FNw60WAHEPLzsoUTsJnnR** privat/Eigentümer, keine Freigabeänderung. Versionsnachweis `docs/verification/original-apk-1.54.5-94.json` und privater Drive-Beleg **1QKvtz_IY9N-vg3WYoOAmdSqpjy20X1lY**, tatsächlicher SHA256-Bytevergleich PASS. Nach gesichertem Dokumentationscommit offizieller neuer Gesamtexport in **1lGyhXJtB_QuCex_R8ObLDxphhaq8SkC-**, alle Payloads/Indizes zurücklesen, erst dann vorhandene KI-Wegweiser aktualisieren; frühere gute Generationen und Originale erhalten. Keine Bereinigung oder Pixelinstallation.

## APK94 R2 – Initialisierungsrennen in Mehr reproduziert und behoben (22.10.2026)

Der erste reine Build68138ef war erfolgreich, blieb unsigniert und wurde nicht bereitgestellt. Der vollständige Browserlauf fand unter paralleler Cloud-Last eine echte Reihenfolgeabweichung: der später fertiggestellte Angebotsbaustein wurde nach Abrechnung/Rechnungen angehängt. Die bestehende eingefrorene Menü-Baseline bleibt unverändert. organizeMenu sortiert nun die vorhandenen Originalknöpfe stabil in der geprüften Reihenfolge, bewahrt Listener/Ziele und bewegt nur tatsächlich falsch stehende Elemente, damit der MutationObserver zur Ruhe kommt. Die vorhandene Regression reproduziert zusätzlich die späte Angebotseinfügung und verlangt danach genau dieselben13 Ziele/Knöpfe ohne Duplikat. Keine neue Funktion, Berechtigung oder Fachaktion. Neuer gesicherter Quellcommit und vollständiger Rebuild vor Originalsignierung erforderlich; Versionscode94 bleibt für den noch nicht ausgelieferten Kandidaten reserviert.

## APK94 R1 – konkrete native Anzeigeabnahme vorbereitet (22.10.2026)

Zusätzlich zur Versionsanhebung erhält die vorhandene native UI-Probe lesende Prüfungen im echten gebündelten WebView: entfernte Hauptseiten-Wischeinstellung/Statuszeile fehlen, Gartenpirat-Option bleibt, Randmenü öffnet und Zurück schließt genau das Menü, Heute-Karte bleibt vorhanden und technische calendar_busy_*-Felder kommen über den echten nativen Owner-Lesepfad. Keine Geschäftsdaten für diese UI-Prüfung erzeugt oder verändert. Die bereits geänderte native Einrichtung wird mit vier sichtbaren Schaltern geprüft. Source-/Host-/Emulator-Abnahme folgt aus genau dem gesicherten Commit; kein physischer Pixelzugriff.

## APK94 R0 – Originalupdate aus dem geprüften Planungsstand beauftragt (22.10.2026)

Der neue ausdrückliche Nutzerauftrag **Erstelle Apk laut Protokoll** autorisiert jetzt APK-Build, Versionsanhebung und Originalsignierung; die frühere Nur-Quellcode-Grenze des UI-Auftrags bleibt historisch und sperrt diesen Folgeauftrag nicht. Basis **7d1af30a024c5b07d9d64342e99eb55a254ab504** auf dem vorhandenen Branch codex/calendar-weather-20261022, frischer main **e499e745832449ce0202d04d9903495248e8b034** und vier Arbeitsbranches verglichen; keine fremden Änderungen/Resets. Original93 und sämtliche Quellen erhalten. Live-Pixel-MCP liest weiterhin Original1.54.4/93/b7d5fcd, source_dirty=false,117Tools. Keine produktive Geräteaktion.

Ziel **1.54.5/Build94**, Paket at.hausgarten.service, Originalschlüssel und bisherige Rechte/Scopes/DB4/Snapshot2/117Tools/156Aktionen erhalten. Abnahme: saubere exakt auf GitHub gesicherte Buildquelle und unveränderliches Tag; Source-/Workspace-/fertigeAPK-Privacy,0Seeds; tatsächliche Originalsignatur und Vergleich gegen Original93,16KiB-Ausrichtung, Assets und DEX-Commit/Fingerprint/Version prüfen. Aktuelle Hostregressionen und isolierte native Tests;93→94-Datenerhalt ausschließlich im dedizierten qemu=1/GartenpiratCloud mit neuem lesendem Hash-/Relations-/Sitzungsledger, bestehende Baselines nie ersetzen. Fresh-isolated/nativeUI und sichtbare Planungs-/Gestenänderungen soweit tatsächliches Emulator-API erlaubt; physische API37-Gesten separat kennzeichnen.

Originalsignierung ausschließlich tools/sign_original_apk.py; fünf konfigurierte Variablen laut frischer Environment-Readiness bereit, Werte nicht gelesen/ausgegeben und keine Ersatzschlüssel/CI. Nichtsignierende Prozesse erhalten diese Variablen nicht. Nach vollständiger Qualifikation private Drive-APK plus exakt passende Originalquellen/UTF8-Teile/Versionsnachweis, tatsächliches Rücklesen und danach KI-Wegweiser/Standregister aktualisieren. Große APK/ZIP-Streamgrößenbelege ausdrücklich von SHA256-Byteprüfung trennen. Keine automatische Pixelinstallation, Kontofreigabe oder produktiver Testtermin.

## Planung/Bedienung R-Abschluss – Quellumsetzung geprüft, ohne neue APK (22.10.2026)

P1–P3 sind im bestehenden Branch implementiert und dauerhaft gesichert. App-Quellcheckpoint **6079d94118e7b9ce4bda83fc036a06db650c98af**, Main-/Gradle-Fingerprint **03c45d8eff22a9f66962562de79994516f174f7ec221b14f292ea7e8f83c4f11**; der nachfolgende Dokumentations-/Testcommit erhält exakt denselben App-Fingerprint. Original APK93 bleibt unverändert **source/v1.54.4-build93 / b7d5fcd**. Entwicklungsquelle ist ausdrücklich keine neue93-APK und nicht der installierte Stand. Der Gesamt-/Kalender-UI-Auftrag schließt hier neue APK, Signierung und Versionsänderung aus; keine davon ausgeführt.

Tatsächliche Abnahme: **393 Python PASS,0 Skips** (37 ältere QA-Schlüsselerzeugungstests explizit ausgeschlossen), **42 JVM-Suiten PASS**, eingefrorener Vertrag unverändert **899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813**, **55 synthetische MCP-stdio-Szenarien PASS**, **117 Tools/156 UI-Aktionen**, abschließende1047 Paritätschecks PASS. Browser-Gesamtlauf52 Suiten:51 sofort PASS; UAT110 zählte noch den beauftragt entfernten Schalter. Erwartung auf vorhandene Fotowahl plus ausdrücklich fehlenden Schalter korrigiert, gezielte UAT-Wiederholung PASS. Alle anderen Suiten unverändert bestanden.

Aktuelle neue Carousel-Abnahme **10/10 PASS**: reale Chromium-Touchfolge blättert exakt einen Termin, erzeugt keinen versehentlichen Klick und vertikale Touchbewegung scrollt wirklich die Seite; Pfeile/Tastatur, Auswahl nach Refresh/Absage, leer/einzeln, schmale Ansichten/große Schrift, ungeprüfte externe Belegung und Gartenpirat-Scrollverhalten. Ein Folgecheck wartete zunächst nicht auf das asynchrone Gartenpirat-close-Ereignis und kollidierte mit dessen korrekter Fokusrückgabe; nun wird dieses Ereignis tatsächlich abgewartet. Keine unnötige Autofokus-Umgehung im App-Code. Gesten-/Kollisionen11/11, native Quellschutztests4/4 und unveränderte Speicherhandler/Preference-Fingerprints PASS. 320/360/426 sowie vergrößerte Schrift ohne horizontales Überlaufen; synthetische Browseraufnahmen gesichtet.

**Android-Kompilierung und Lint PASS:** stabile API37, JDK17/Gradle9.3.1,42 Tasks,0 Fehler/54 Warnungen; Main- und AndroidTest-Java kompiliert, keine APK erzeugt oder signiert. Die native UI-Prüfquelle erwartet nun die vier ausdrücklich sichtbaren Planungsschalter. Neue native Bildschirm-/API34/37-Gestenabnahme auf Emulator/Pixel **NOT EXECUTED**; Browser-Touch ist kein physischer Pixel-Test. API29–33 können nur200dp OS-Rand reservieren; eigene native Kontomasken bleiben separate Aktivitäten. Hauptkalender-Belegung muss aktuell und persönlich freigegeben sein; zusätzliche Google-Kalender werden nicht geprüft.

Source-/Workspace-Privacy erneut PASS,0 operative Seeds; keine Nutzerscreenshots, Kundendatensätze, Kontoadressen oder Geheimnisse ins Repository übernommen. Eigene Quellcommits regulär gepusht und exakt auf GitHub zurückgelesen, kein main-Push/Reset/History-Rewrite. Offizieller abschließender Export wird über striktes source_preservation aus dem sauberen exakten Remote-Commit in den neuen privaten Ordner **Planung-Bedienung-2026-10-22-Quellcode** (https://drive.google.com/drive/folders/1bh6Q4Uu2RgwYBFcB22JWs14X7qGp3IY0) erstellt. Manifest definiert den genauen finalen Commit; Wegweiser erst nach allen tatsächlichen Readbacks umstellen. Frühere gute Generationen und APK-Originale bleiben erhalten. Transferbelege außerhalb der Quellgeneration, SHA256-Byteprüfung und große ZIP-Streamgrößenprüfung getrennt; Abschlussbeleg im Chat.

Technischer Sammelnachweis: docs/verification/planning-gestures-2026-10-22.json. Google-Kalender ist live verbunden, aber weiterhin im externen Testbetrieb; Homepage-/Datenschutzentwürfe aus dem vorausgehenden Auftrag bleiben privat/unveröffentlicht. Keine permanente Google-Freigabe oder produktive Kollisions-/Installationsabnahme behauptet.

## Planung/Bedienung P1–P3 – Umsetzung und gezielte Reproduktion (22.10.2026)

**P1:** Die Liste unterscheidet jetzt überprüfte Hauptkalender-Belegung von ausgeschalteter, pausierter oder ungeprüfter Belegung. Die Warnung steht vor den Terminen; ein eigener Knopf öffnet die vorhandene Einrichtung. Native Belegtzeitoption und ausdrücklicher Google-Freigabeknopf stehen sichtbar im Kalenderbereich. Andere Google-Kalender werden ausdrücklich als nicht geprüft bezeichnet. Neue ausschließlich technische integration.calendar_busy_*-Felder liefern Freigabeschalter, Zustand und bestätigten Zeitraum ohne Konten/Kalender-IDs. Kein stilles Einschalten, keine neuen Scopes oder Requests. Tageskopf zeigt das Datum einmal, Diagnose folgt nach den eigentlichen Aufgaben. Originale Speicher-/CAS-/Kalenderwahl-Handler und Preference-Write-Fingerprints bleiben unverändert.

**P2:** Im vorhandenen Nächster-Einsatz-Fenster blättern beide Wischrichtungen, Pfeile oder fokussierte Tastaturpfeile über aktive bevorstehende App-Termine. Zähler, feste Grenzen und stabile Zeit-/ID-Sortierung; Auswahl über Refresh erhalten, abgesagte/entfernte Auswahl sicher ersetzt. Die Karte verwendet jetzt auch entry.collisions und availability; die bisher verworfenen externen Kollisionshinweise erscheinen. Tappen öffnet genau den angezeigten Termin. Vertikale Bewegung navigiert nicht; ein Wischende löst keinen versehentlichen Termin-Klick aus. Laufender Timer/Entwürfe und alle Schreibhandler erhalten.

**P3:** Globale Hauptseiten-Wischfunktion, Einstellungsfeld, Texte und Listener aus operativer App und historischem Webspiegel entfernt. Gartenpirat-Schalter und Lexikon-/Foto-/Verschiebefunktion bleiben. Scrollen setzt den Knopf nicht mehr immer auf die Ausgangsposition zurück; aktuelle Position wird begrenzt und bei Bedarf für sichtbare Bedienelemente geräumt. Linker Rand: Web-Menügeste ab äußerstem Rand, geringere eindeutige horizontale Schwelle; Formulare werden beim bloßen Menüöffnen nicht verlassen. API34+ differenziert BackEvent.EDGE_LEFT und öffnet das Menü über die volle native Randhöhe, während Hardware/rechte Zurück-Aktion die ursprüngliche Ein-Schritt-Navigation nutzt. API29–33 können nur200dp native Gesture-Exclusion erhalten; volle OS-Randhöhe dort nicht zugesagt. Native Kontomasken bleiben eigene Systemaktivitäten. Keine Manifestrechte oder globale Android-Zurück-Abschaltung.

Vor Umsetzung bestand die ursprüngliche Gesten-/Kollisionssuite11/11. Nach Umsetzung tatsächlich: neue Carousel-/Warnungs-/Gartenpirat-Suite8/8, geänderte Gesten-/Kollisionssuite11/11, bestehende Kalenderübersicht und Daily-Usability gezielt PASS. Die früheren Opt-in-Wischtests wurden durch den ausdrücklich beauftragten Nicht-mehr-vorhanden-/Alte-Preference-wirkungslos-Nachweis ersetzt. Native Quelltests4/4 PASS, einschließlich unveränderter sechs geschützter Handler und aller Preference-Schreibfingerprints. Source-/Workspace-Privacy PASS,0 operative Seeds; öffentliche Pflanzen-/Webspiegel über offiziellen Generator,156 UI-Aktionsmappings erhalten. Breiter Python-/JVM-/Browserlauf läuft; Android-Kompilierung/Lint folgt. Keine neue APK, Version oder Pixelinstallation; native API34/37-Gesten und neue native Bildschirmdarstellung noch NOT EXECUTED.

## Planung/Bedienung R0 – Nutzerauftrag vom 22.10.2026, Ausgang und Abnahme

Aktiver Ausgang: 24ebe65a09a81bb58b7b23ac33d741dd9560ff56 auf codex/calendar-weather-20261022; frischer main e499e745832449ce0202d04d9903495248e8b034 und alle vier Arbeitsbranches lesend verglichen. Neuere App-/Kalender-/Wetterfunktionen bleiben erhalten. AGENTS, Zentralauftrag, Status, Gesamt-/Kalender-UI-Auftrag, Reparatur-, Signier-, MCP-/Sync-Regeln und Issue1 gelesen. Kein Reset, neuer Branch oder produktiver Schreibtest. Die MD-Vorgabe für UI-Arbeit bleibt maßgeblich: Quellcode/Tests; kein neuer APK-Build, keine Signierung, Versionserhöhung oder Pixelinstallation in diesem UI-Schritt.

Lesender Pixel-MCP-Befund: Kalender verbunden/configured/checked, letzte erfolgreiche Prüfung14:43:56Z; für den im Nutzerbild gezeigten Tag genau ein App-Termin, availability.verified=false, external_checked=false,0 bekannte Konflikte. Die Bilder zeigen einen zusätzlichen externen Google-Termin. Dessen konkrete Überschneidung kann ohne verifizierte Belegtzeiten nicht erkannt werden. Ob die optionale Belegtzeitfreigabe aus oder der Cache unbrauchbar ist, exponiert Build93 nicht; keine Vermutung als Tatsache. Der Code liest bei freigegebenen Belegtzeiten ausschließlich den Hauptkalender. Die Heute-Karte verwirft zusätzlich bereits gelieferte externe Kollisionsdaten. Screenshots und Geschäftsdatensätze bleiben außerhalb des Checkouts; Tests verwenden qa_-Daten.

Abnahmekriterien / Blöcke:

1. **P1 Belegung und Planungsmenü:** Ungeprüfte externe Belegung deutlich über der Liste, eigener Einrichtungszugang, Grund/Prüfzeitraum ohne Kontodaten im MCP-Status; bekannte lokale/externe Kollisionen sichtbar, stale/disabled bleibt unbekannt. Belegtzeitoption in der nativen Einrichtung auffindbar, zusätzliche Google-Zustimmung ausdrücklich am Gerät. Keine neue Berechtigung, kein Scope-/Datenmodell-/Speicherhandlerwechsel. Tagesdatum ohne doppelten Bereich, Diagnose in nachrangigen Bereich; alle Fachaktionen weiter erreichbar.
2. **P2 Heute-Karte:** alle bevorstehenden aktiven App-Termine stabil nach Zeit/ID, im vorhandenen Fenster links/rechts wischbar, explizite Pfeile und Zähler als Alternative, Grenzen ohne Umlauf. Auswahl bei Neuladen erhalten; geloeschte/abgesagte Auswahl sicher ersetzen. Anzeigen/Blaettern/Scrollen schreibt nichts; Tippen öffnet exakt den sichtbaren Termin, Wetter/Kollisionen gehören dazu. Timer bleibt vorrangig.
3. **P3 Bedienung/Gesten:** roten globalen Hauptseiten-Wischbereich samt Preference/Listenern entfernen, Gartenpirat-Option und Funktion erhalten. Linker Rand öffnet Menü, niemals App-Zurück; API34+ unterscheidet native linke Geste und Hardware-/rechte Zurück-Aktion. Ältere Android-Versionen respektieren die OS-Grenze der Gesture-Exclusion. Horizontale Karten-/Tageswische und vertikales Scrollen konkurrieren nicht; Editoren/Entwürfe geschützt. Gartenpirat beim Scrollen nicht springend oder über Bedienknöpfen, Lexikon-/Bildfunktion erhalten.
4. **R-Abschluss:** gezielte Reproduktion vor/nach Änderung; Browser schmal/große Schrift und echte Touchfolge, Python/JVM/Android-Kompilierung/Lint, Privacy und unveränderter117/156-Vertrag; jeden Block commit/push/exakt zurücklesen. Abschließend offizieller privater Drive-Quellcodeexport für KI-Prüfung und erst danach Wegweiser. Tatsächliche Pixel-/OS-Endabnahme separat, keine Erfolgserfindung.

## Kalender R7 – Verbindung funktioniert; Google-Produktion trotz bestätigtem Branding-Speichern gesperrt (22.10.2026)

Nach dem echten Pixel-Verbindungserfolg wurde die Produktionsreife gezielt neu geprüft (Run **a6744fe4-7e82-44b8-8bdc-9e40de634957**). Das Branding wurde tatsächlich gespeichert, nun mit der ausdrücklichen Google-Bestätigung **Änderungen gespeichert**; App-Name sowie vorhandene Support-/Entwicklerkontakte wurden gespeichert zurückgelesen. Ein unbestätigter Kontakt-Chip ist damit als konkrete Ursache nicht belegt. Kontoadressen bleiben außerhalb des technischen Nachweises. Keine öffentlichen URLs erfunden, Website veröffentlicht oder Verifizierungsprüfung eingereicht.

Die anschließend unabhängig neu gelesene Zielgruppe bleibt **Test**. **App veröffentlichen** bleibt gesperrt mit dem wörtlichen Google-Hinweis **Damit Sie Ihre Anwendung veröffentlichen können, müssen Sie die Konfiguration auf der Seite Branding abschließen.** Kein Veröffentlichungsklick oder Bestätigungsdialog ausgeführt, kein Produktionsstatus vorgetäuscht. Der genaue verbleibende Google-Konfigurationsmangel ist weiterhin nicht bezeichnet; aus dem allgemeinen Hinweis werden keine fehlenden Pflicht-URLs, Abrechnung oder zwingende Verifizierung für den persönlichen Gebrauch abgeleitet. Weitere blinde Speicher-/Veröffentlichungsversuche unterbleiben.

Die reale Kalenderverbindung ist dagegen **bestätigt**: Eigentümerfreigabe plus unabhängiges Pixel-MCP, verbunden/configured=true, Zustand checked, keine Fehler, erfolgreiche Kalenderprüfung13:18:02Z. Die neue Testnutzerzeile beseitigte die konkrete Google-403-Sperre. Solange Google den externen Testbetrieb führt, können Kalenderfreigaben nach sieben Tagen ablaufen; dauerhafte Erneuerung und Produktion sind **nicht abgenommen**. Keine produktiven Testtermine, Datenänderung durch Codex oder neue APK. Wetter war nicht Gegenstand dieses Nachlaufs.

R6 einschließlich des Live-Verbindungserfolgs ist unter **5cb2831ee7af1f71000055bdd83b376826873ccf** auf dem bestehenden Branch gepusht und exakt zurückgelesen; striktes source_preservation PASS,1245Quell-/3667Workspace-Dateien Privacy PASS. App-Main/Gradle und das unveränderliche APK-Original-b7d5fcd bleiben bytegleich. Der abschließende offizielle Export in den vorbereiteten privaten Ordner https://drive.google.com/drive/folders/1aBT1HBB4t5-EEZ08-sA8-B7HGwi0pIfB sichert den nachfolgenden Dokumentationscommit; Manifest maßgeblich, Wegweiser erst nach vollständigem tatsächlichem Readback umstellen. R5 und alle APK-Originale bleiben erhalten.

## Kalender R6 – Testnutzer ergänzt und echte Pixel-Kalenderverbindung bestätigt (22.10.2026)

Der neue Nutzerscreenshot zeigt nach einem echten Pixel-Anmeldeversuch **403: access_denied** mit der ausdrücklichen Google-Begründung, dass die App getestet wird und nur vom Entwickler genehmigte Tester Zugriff erhalten. Die zunächst tatsächlich gelesene Testnutzerliste im bestehenden Projekt **gartenpirat-drive** war leer. Die aktuelle Ablehnung ist damit auf die Testnutzerbeschränkung eingegrenzt; sie belegt keinen erneuten Paket-/Signaturfehler und keine zwingende öffentliche App-Verifizierung für den persönlichen Testzugang.

**Eigentümerkonto tatsächlich ergänzt und gespeichert:** Der erste Versuch zeigte nach erneutem Laden weiterhin keinen Eintrag und wurde ausdrücklich nicht als gespeichert gewertet (Run e464320a-5696-46be-81bc-22decd87ed9d). Beim gezielten Folgeschritt wurde die Eingabe als gültiger Chip bestätigt und der aktivierte Dialog-Speichern-Button betätigt. Nach Navigation zur Projektübersicht und zurück zur Zielgruppe war die tatsächliche Zeile des exakten Eigentümerkontos vorhanden; Anzahl **1 Testnutzer** (Run dcd0ca74-7e50-4285-abdb-ff38a675f664). Keine ausdrückliche Erfolgs-Toastmeldung erfasst; der Erfolg beruht auf dem unabhängig neu gelesenen Listeneintrag. Keine anderen Nutzer verändert. Kontoadresse und Original-Screenshot bleiben außerhalb von Repository, Protokollexport und Quell-ZIP.

**Live-Verbindung bestätigt:** Nach dem gespeicherten Testnutzer meldet der Nutzer **Kalender verbunden**. Die anschließende unabhängige Pixel-MCP-Lesung bestätigt connected=true, configured=true, state=checked, registration_required=false, leere Fehlerfelder und eine erfolgreiche Kalenderprüfung um **13:18:02Z** (15:18Uhr Österreich). Frischer Einrichtungsversuch13:16:55Z; alter Fehlerzeitpunkt und Fehlerfelder sind nun geleert. Damit ist der reale Verbindungserfolg belegt; keine produktive Termin-Schreibprobe oder Langzeit-/Neustartabnahme daraus abgeleitet.

**Offen:** Veröffentlichungsstatus beim letzten Cloud-Readback weiterhin **Test**. Der neue Testnutzer und die persönliche Freigabe beheben die konkrete Zugangsbeschränkung; dauerhafter Zugang ist weiterhin nicht belegt. Im externen Testbetrieb können Kalenderfreigaben nach sieben Tagen ablaufen. Die zuvor von Google gesperrte Produktionsumschaltung wegen Branding ist durch diesen Schritt nicht gelöst; kein fehlendes URL-Feld oder kostenpflichtiger Verifizierungsbedarf daraus abgeleitet. Der Nutzer hat die persönliche Freigabe erteilt und die Verbindung ist geprüft. Eine frische gezielte Produktions-/Brandingprüfung nach diesem tatsächlichen Erfolg folgt; keine produktiven Testtermine.

Vor der Korrektur zeigte die erste frische Pixel-MCP-Lesung **1.54.4/93**, Original-b7d5fcd, SOURCE_DIRTY=false, verbunden=false, einen neuen Einrichtungsversuch um **13:05:23Z**, jedoch noch den alten Fehlerzeitpunkt **08:24:56Z**. Die Ablehnung wurde deshalb aus dem Screenshot beurteilt. Die spätere oben dokumentierte Live-Verbindung löst diese Fehlerlage tatsächlich ab. Keine Änderung von App-Code, Gradle, APK, Kalender-Scopes oder Manifestrechten; für die Testnutzerkorrektur ist kein neuer Build erforderlich.

Die vorausgehende R5-Generation **28d83a08e09563116a74c91862619e3b4357f81e** ist vollständig gesichert:19Quellartefakte,18tatsächliche SHA256-Byte-Readbacks und1vollständiger ZIP-Streamgrößenbeleg,0offeneUploads/0Duplikate;5vorhandene KI-Wegweiser ebenfalls SHA256-bytegleich zurückgelesen. Diese Generation und die APK-Originalquelle **source/v1.54.4-build93 / b7d5fcd71fd0e376b8750cf9eaacd94e47d72883** bleiben unverändert. Der folgende offizielle Dokumentationsexport wird im privaten Ordner https://drive.google.com/drive/folders/1aBT1HBB4t5-EEZ08-sA8-B7HGwi0pIfB gesichert; Wegweiser erst nach dessen vollständigem Readback umstellen. Exakter Dokumentationscommit im Manifest, Transferbelege außerhalb der Exportgeneration. Kein automatisches Pixelupdate, keine Datenänderung, keine neue Abrechnung oder Verifizierungseinreichung.

## Kalender R5 – Cloud-Grundlage bestätigt, Produktionsumschaltung und Pixel-Freigabe offen (22.10.2026)

Im bestehenden Projekt **gartenpirat-drive** sind nun der Android-OAuth-Client für **at.hausgarten.service** mit der Original-SHA-1 und die aktivierte **Google Calendar API** tatsächlich bestätigt. Die abschließende direkte API-Statusprüfung zeigt wörtlich **API aktiviert**; dabei war kein erneuter Aktivierungsklick nötig. Die drei vorhandenen Kalender-Scope-Deklarationen app.created, calendarlist.readonly und optional freebusy wurden gelesen. Die App selbst fordert weiterhin nur die zwei bisherigen Basis-Scopes und freebusy ausschließlich bei eingeschalteter Belegtzeitprüfung an.

Die neu geladene Brandingseite zeigt **Haus & Garten** sowie vorhandene Support-/Kontaktangaben. Diese Kontaktwerte wurden ausschließlich innerhalb der Google-Konsole verwendet und nicht ins Repository oder die Exportberichte übernommen. Öffentliche Homepage-/Datenschutz-/Nutzungsbedingungen-URLs sind im tatsächlich geprüften Formular optional; sichtbare Validierungsfehler fehlen. Speichern wurde zuletzt tatsächlich angeklickt, eine ausdrückliche Erfolgsmeldung jedoch nicht erfasst. Aus sichtbaren Formularwerten allein wird deshalb kein vollständiger Einrichtungsabschluss abgeleitet; keine URL erfunden oder Website veröffentlicht.

**Nicht abgeschlossen:** Die Zielgruppe ist External, der zuletzt gelesene Veröffentlichungsstatus weiterhin **Test**. Der Nutzer meldet den Veröffentlichungsweg ebenfalls als fehlend/gesperrt. Die neueste direkte Prüfung bestätigt einen gesperrten Button mit Googles Hinweis **Damit Sie Ihre Anwendung veröffentlichen können, müssen Sie die Konfiguration auf der Seite Branding abschließen.** Eine Produktionsumschaltung ist nicht belegt. Der tatsächliche Abschlussstatus der Google-Auth-Platform-Einrichtung wird weiter geprüft; fehlende öffentliche URLs oder eine zwingende Verifizierung sind damit noch nicht bewiesen. Solange Google den externen Testbetrieb führt, ist ein dauerhaft nutzbarer Grant ohne den möglichen Sieben-Tage-Ablauf nicht nachgewiesen. Anschließend ist die persönliche Kalenderfreigabe in der Pixel-App samt echtem Kalender-Readback erforderlich.

Die erneute Pixel-MCP-Lesung bestätigt **1.54.4/93/b7d5fcd**, SOURCE_DIRTY=false, Kalender verbunden=false. Der gespeicherte Fehler CALENDAR_APP_REGISTRATION_REQUIRED stammt weiterhin vom Versuch **08:24:55Z vor dieser Cloud-Einrichtung**; es wurde kein neuer erfolgloser Verbindungsversuch nach der Registrierung beobachtet. Die Cloud-Einrichtung darf deshalb weder als vollständiger Verbindungserfolg noch anhand dieser alten Meldung als erneut gescheitert bezeichnet werden. Kein produktiver Testtermin und kein automatisches Pixelupdate.

R4 ist unter **c9ceeb5085a3466de0487e8d1f34d1d74a703b85** auf dem vorhandenen Branch gepusht und exakt zurückgelesen: Source-Privacy1245Dateien/0Seeds, Workspace-Privacy3667Dateien inklusive ignorierter/unversionierter Dateien PASS, App-Main und Gradle gegenüber dem Original-b7d5fcd unverändert, striktes source_preservation PASS. Der vorhandene Drive-Versionsnachweis von Build93 wurde zusätzlich vollständig heruntergeladen und gegen die Repositorybytes geprüft:4031Bytes, SHA256 **d27473b38dda9aae4401bc1c470f6c9c53e658410636284893760adc3010cbfc**.

Der nachfolgende offizielle Export sichert diesen Dokumentationsstand im bereits privat geprüften Ordner https://drive.google.com/drive/folders/1ujSGAnpVH_nX4uMgYAVh5nkW1KdYAgCe . Sein Manifest definiert den exakten Dokumentationscommit; die unveränderliche APK-Originalquelle bleibt **source/v1.54.4-build93 / b7d5fcd71fd0e376b8750cf9eaacd94e47d72883**. Transferbelege außerhalb der Exportgeneration, tatsächliche Byteprüfung und reine ZIP-Streamgrößenprüfung unterscheiden; Wegweiser erst nach vollständigem Readback aktualisieren. Frühere Originale und Generationen bleiben erhalten. Keine neue APK oder Änderung des App-Funktionscodes für diese Google-Konfiguration.

## Kalender R4 – Google-Projekt gefunden und Android-Client registriert (22.10.2026)

Der Nutzer hat die Umsetzung der Google-Konfiguration und den TinyFish-Zugang ausdrücklich beauftragt. Nach persönlicher Google-Anmeldung ist der Zugriff auf die Cloud Console tatsächlich möglich. Das bestehende Projekt **gartenpirat-drive** wurde gelesen: zunächst keine OAuth-Clients, keine eingetragenen Kalender-Scopes und keine aktivierte Calendar API. Ein nicht sichtbares anderes Projekt wurde nicht als bewiesen angenommen; das vorhandene Gartenpirat-Projekt wird verwendet.

**Android-Registrierung tatsächlich erstellt und zurückgelesen:** Typ Android, Name Haus & Garten Android Original, Paket **at.hausgarten.service**, SHA-1 **16:FC:7A:13:7E:58:CA:31:C7:1D:70:6D:47:2F:29:FB:9F:55:8A:5A**, öffentliche Client-ID **123680727453-1d53mksthignnhiairm2tjuteijavivd.apps.googleusercontent.com**. Ein erster umfassender Konsolenlauf verlor den Browserkontext; der gezielte neue Lauf über das direkte Erstellungsformular war erfolgreich. Keine vorhandenen Clients gelöscht und kein Web-/Server-Client angelegt.

**Noch laufend:** Die Calendar-API-Aktivierung wurde ausgelöst, ihre endgültige Freischaltung ist noch nicht zurückgelesen. Der nachfolgende Lauf bearbeitet die zwei bisherigen Basis-Scopes sowie optional freebusy und den Wechsel vom externen Testbetrieb in Produktion; der gespeicherte Endzustand ist noch nicht bestätigt. Kein dauerhafter Kalenderzugang oder echter Google-Grant behauptet.

Die erneute lesende Pixel-MCP-Prüfung bestätigt inzwischen **1.54.4/93**, Quellcommit **b7d5fcd71fd0e376b8750cf9eaacd94e47d72883**, Main-/Gradle-Fingerprint **47e84a4a64d29117d828e49c26f4f700f0f4b75861274c5e15b7d75dbd1014c5**, SOURCE_DIRTY=false. Die tatsächliche Installation ist damit beobachtet; Codex hat keine Installation ausgeführt und keinen produktiven Datenerhaltstest durchgeführt. Kalender weiterhin verbunden=false, Stufe grant, CALENDAR_APP_REGISTRATION_REQUIRED aus dem gespeicherten Fehlversuch. Wetterkonfiguration tatsächlich open_meteo_non_commercial/non_commercial mit angeforderten16Tagen; dies allein ist kein Live-Prognosenachweis.

Technischer Nachweis ohne Kontoadressen oder Geheimwerte: docs/verification/google-calendar-cloud-2026-10-22.json. Neue private Dokumentationsgeneration vorbereitet unter https://drive.google.com/drive/folders/1ujSGAnpVH_nX4uMgYAVh5nkW1KdYAgCe ; Veröffentlichung der Wegweiser erst nach vollständigem Export und Readback. Bisherige Originale und Exportgenerationen bleiben erhalten. App-Code, APK, Datenmodell, Scopes der App-Anfrage und Original-Tag bleiben unverändert. Keine produktiven Kalender-Testeinträge, Kontoeinwilligung oder zusätzliche Abrechnung eingerichtet.

## Kalender/Wetter R3 – geprüfter Originalkandidat 1.54.4 / Build 93 (22.10.2026)

**App-Umsetzung, Originalsignierung und Cloud-Abnahme abgeschlossen. Google-Projektfreischaltung und physische Pixel-Abnahme bleiben offen.** Der neue Nutzerauftrag erlaubt Kalender-/Wetterfunktionen und den Entwicklungsbuild. Main und produktive Daten wurden unverändert erhalten.

Die App nutzt den kostenlosen öffentlichen Open-Meteo-Zugang für die vom Nutzer erklärte nichtkommerzielle Verwendung: ohne Konto, API-Schlüssel oder Abo, mit bis zu 16 Tagen Vorschau. Vollständige Arbeitsstunden eines Termins 14 Tage voraus sind im nativen App-/MCP-Pfad nachgewiesen. Tatsächliches Datenende, Aktualität, fehlende Werte und kurzfristiger GeoSphere-Ersatz werden offengelegt. Klare Fernprognosen erscheinen als unsicherer, neutral dargestellter Wettertrend. GeoSphere-Warnungen und Nowcast ergänzen die Hauptprognose. St./Sankt-Ortsvarianten werden erkannt; Originaladressen und Kundenbeziehungen bleiben erhalten.

Die Kalendereinrichtung unterscheidet Kontoauswahl, beobachtete Google-Freigabe und bestätigten Kalender. Die Registrierungshilfe nennt ausschließlich öffentliche Paket-/Zertifikatsdaten. Vorhandene minimale Scopes, Google-Tokenverwaltung, 401-Behandlung, Wiederaufnahme, Kalender-Readback, ETags und Idempotenz bleiben erhalten. Alle sechs geschützten nativen Handler und Preference-Write-Fingerprints sind unverändert. Keine neuen Manifestrechte, SDKs oder MCP-Werkzeuge.

| Tatsächlich ausgeführte Prüfung | Ergebnis |
|---|---|
| Python | 393 PASS, 0 Skips; 37 alte QA-Schlüsselerzeugungstests ausgeschlossen |
| JVM / vollständiger Browserlauf / MCP | 42 Suiten / 51 Suiten / 55 Szenarien PASS |
| Release, AndroidTest und Lint | 84 Tasks PASS; 0 Fehler, 53 Warnungen |
| Android SQLite/MCP | 159 PASS, 0 FAIL; einschließlich T+14-Coverage und Cache nach Wiederöffnung |
| Cloud-Updates | 92→erster93 und erster93→finaler93 PASS; vollständiger Snapshot, IDs, Queue, Einstellungen, Tunnel und aktiver Timer erhalten |
| Frisch isoliert / native UI / letzter Snapshot | PASS; UI und Snapshot nach System-UI-Wait zusätzlich wiederholt |
| Original-APK | Originalzertifikat, ein Signer, v2/v3, 16KiB, Assets, leere Seeds, Privacy und DEX-Provenienz PASS |

Der dedizierte Softwareemulator GartenpiratCloud / emulator-5554 wurde vor jedem Gerätezugriff mit qemu=1 geprüft. Die Sichtprüfung fand einen System-UI-ANR; die ursprüngliche Aufnahme blieb erhalten. Ausschließlich der exakt identifizierte Wait-Knopf wurde betätigt. Danach bestanden native UI und vollständiger Snapshot erneut. Eine unmittelbar beim App-Start erstellte Ladeaufnahme wurde separat erhalten und die Ansicht nach dem Laden erneut aufgenommen. Keine App-Datenlöschung, kein behaupteter App-ANR-Fix oder physischer Pixel-Leistungsnachweis.

Die abschließende Workspace-Prüfung hielt zunächst drei neue Emulatoraufnahmen als UNCLASSIFIED_IMAGE zurück; der Exporter stoppte deshalb vor der Archivveröffentlichung. Nach tatsächlicher Sichtprüfung und belegter Grenze GartenpiratCloud/emulator-5554/qemu=1 wurden SHA256-gebundene SYNTHETIC_TEST-Herkunftsbelege ergänzt. Danach Source-, Workspace- und tatsächliche Original-APK-Privacy vollständig PASS. Bilder und Gates wurden unverändert erhalten; der gestoppte Export blieb außerhalb des Checkouts und wird durch eine neue vollständige Generation ersetzt.

117 MCP-Werkzeuge, 156 UI-Aktionen, DB4/Snapshot2 und Vertrag 899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813 bleiben erhalten. 62 UI-Quellen wurden geprüft; fachliche Aktionsmappings sind bytegleich zur Ausgangsbasis. Abschließend 1047 UI-/MCP-Paritätsprüfungen PASS. Die letzte Änderung gegenüber 1cd2222 betrifft ausschließlich Android-Testanbieter und Protokoll; der Main-/Gradle-Fingerprint ist identisch. Build und Originalsignierung wurden dennoch mit dem neuen sauberen Commit wiederholt.

Originalquelle: **b7d5fcd71fd0e376b8750cf9eaacd94e47d72883**, unveränderliches Tag **source/v1.54.4-build93**, Branch codex/calendar-weather-20261022. Commit und Tag regulär gepusht und exakt auf GitHub zurückgelesen. Striktes source_preservation vor Build, Signierung und Export PASS. Main-/Gradle-SHA256 **47e84a4a64d29117d828e49c26f4f700f0f4b75861274c5e15b7d75dbd1014c5** für 544 Dateien; tatsächliche DEX bestätigt denselben Hash, Commit, Version 1.54.4/93, SOURCE_DIRTY=false, DEBUG=false und UI_PREVIEW=false sowie beide neuen Klassen. 421 Mainassets entsprechen exakt den Quellbytes; zwei Compilerprofile ebenfalls geprüft. 181 Pflanzenprofile und 362 Bilder. Null Betriebsseedzeilen; Source-/Workspace-/fertige APK-Privacy PASS.

APK **Haus-Garten-1.54.4-93-original-update.apk**, **36196370 Bytes**, SHA256 **16c7ed9dcbcc801002055d9646238398551ec69d8c797a039aa52eea50faa99d**. Originalzertifikat **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**. Original-ZIP **Haus-Garten-Quellcode-b7d5fcd71fd0.zip**, SHA256 **e4b80e526d57aace9193c20fa16d7e0c8b87c7abf54dc2f80a850091e2246383**, 1243 Originaldateien und 512 verlustlos rekonstruierte UTF-8-Dateien. Vollständiger maschinenlesbarer Nachweis: docs/verification/original-apk-1.54.4-93.json.

Privater Ordner **1.54.4-build93-Kalender-Wetter-b7d5fcd**: https://drive.google.com/drive/folders/1RAFAWqhKgzzd633GTJ1FaEPWgh0B7xbS . APK: https://drive.google.com/file/d/17Pts6yNjNHiKyRqhJHaqAGarWJPZBnfA/view . Original-ZIP: https://drive.google.com/file/d/1lB4r1dg6zrB8pvTSXcIJ7s1HDecda3Bd/view . 19 tatsächliche Quell-Readbacks: 18 SHA256-Bytevergleiche und ein vollständig authentifizierter ZIP-Stream mit exakter Größe. APK ebenso vollständig gestreamt und mit frischen privaten Metadaten geprüft. Für APK/ZIP wegen der 32MiB-Materialisierungsgrenze ausdrücklich kein Remote-SHA256-Bytevergleich; lokale Originalhashes vollständig geprüft. Offizieller Transferplan: 0 Uploads, 19 Reuse, 0 Duplikate. Payloads vor Indizes, START zuletzt, höchstens drei unabhängige Ketten.

Nach diesem Dokumentationscommit wird dessen vollständiger aktueller Quellstand mit dem offiziellen Exporter im privaten Unterordner https://drive.google.com/drive/folders/1kE3pqZns1XOrPSM7SZ2V7sMVAEIdFOjv gesichert. Manifest und START benennen den separaten exakten Dokumentationscommit; b7d5fcd bleibt die unveränderliche APK-Originalquelle. Readback-Belege liegen außerhalb der Exporte. Frühere Originale, tatsächlich installierter Stand 92 und Zwischenstand 1cd2222 bleiben erhalten. Keine Löschung, Retentionsverkürzung oder Mainübernahme.

**Offene externe Abnahme:** Die aktuelle lesende Pixelprüfung zeigt 1.54.3/92/aff54db, CALENDAR_APP_REGISTRATION_REQUIRED, Stufe grant, verbunden=false. Für das bestehende Google-Projekt fehlen hier Verwaltungszugang und eine belegte Projekt-ID. Calendar API, Android-OAuth-Client für at.hausgarten.service mit Original-SHA1 und geeigneter Veröffentlichungs-/Verifizierungsstatus müssen stimmen; anschließend einmal ausdrücklich am Pixel Google-Zugriff gewähren und Kalender zurücklesen. Keine Projektkonfiguration oder Einwilligung vorgetäuscht. Physische Installation/Update92→93 und Live-Open-Meteo-Probe sind nicht ausgeführt; der Executor-Zielkatalog enthält api.open-meteo.com nicht. Dieser Kandidat ist kein vollständig live abgenommener Produktivrelease. Keine echten Kunden-/Adressdaten in Quellen, Fixtures oder Reports und keine produktiven Kalender-/Drive-Schreibtests.

## Kalender/Wetter R2 – Originalkandidat und Android-Rückmeldung (22.10.2026)

Gesicherter Implementierungscommit1cd2222b66546edecccab387394400b60a4988ab:393Python/42JVM/55MCP und nach isolierter Wiederholung vollständige51/51Browser PASS. Erster kompletter Browserlauf50/51: unveränderter Menü-Reihenfolgetest fluktuierte; unmittelbare isolierte und vollständige Wiederholung PASS, keine Assertions oder Menüs geändert. Der abschließende kleine Anzeigeattribut-Fix für neutrale Trends wurde vor dem vollständigen Wiederholungslauf geprüft.

Originalsignierung tatsächlich möglich: Alle fünf Laufzeitbindungen ready; offizieller Signer und striktes source_preservation PASS.1.54.4/93 aus sauberem1cd2222, Main/Gradle47e84a4a64d29117d828e49c26f4f700f0f4b75861274c5e15b7d75dbd1014c5.84Release-/AndroidTest-/Linttasks PASS,0Lintfehler/53Warnungen. Originalzertifikat,16KiB, tatsächlicheAPK-Privacy, leereSeeds undDEX BuildConfig PASS. Noch nicht ausgeliefertes erstes Prüfartefakt bleibt außerhalb des Repos erhalten. Originalquellkopie1cd2222 mit1243Originaldateien/512verlustlosenUTF8-Dateien erzeugt;19privateDrive-Rücklesebelege (18SHA256_BYTES,1ZIPvollständigeStreamgröße), offiziellerTransferplan0Uploads/19Reuse/0Duplikate. Dieser Zwischenstand ist kein abgenommener Release.

Dedizierter GartenpiratCloud-Emulator: ausschließlich explizites emulator-5554/qemu=1. Alte verwaiste Lockdateien nach Prüfung fehlender lebender Emulatorprozesse erhalten/verschoben; keine userdata-/App-Daten entfernt. Ein erster Archivierungsversuch per Rename über Dateisystemgrenze wurde abgewiesen; danach innerhalb desselben Dateisystems außerhalb des AVD gesichert. Vollstart ca254Sekunden. Vorheriger92-Snapshot, In-place92→93 samtIDs/Queue/Einstellungen/Tunnel/Timer und frisch isolierterLeerzustand PASS. Android-Laufzeittest der neuen vollständigenT+14-Vorschau einschließlich wahrheitsgemäßemMCP-Coverage/Unknown PASS.

Erster Gesamt-Androidlauf158PASS/1FAIL: bestehender Same-Site-Cachetest lieferte ausschließlich GeoSphere-JSON an den neuen Open-Meteo-Hauptadapter und erwartete dessen alte vier HTTP-Abfragen. Testanbieter auf korrektes synthetisches Open-Meteo-Stunden-/Einheitenformat umgestellt; erwartete drei echten Requests sind Prognose+Lizenz+Warnungen. Cache nachWiederöffnung, Abbruch/entfernte/fehlendeOrte ohne zusätzlicheRequests und unveränderteGeschäftsdaten bleiben vollständig geprüft; neue Assertions für schlüssellosesGET und tatsächlichenProvider hinzugefügt. App-Funktionscode und Main/Gradle-Fingerprint unverändert. Testkorrektur erfordert neuen sauberen Quellcommit, erneutenBuild/Originalsignierung und vollständigeAndroid-Abnahme; erfolgt vor Auslieferung.

Echter Pixel weiterhin lesend1.54.3/92, CALENDAR_APP_REGISTRATION_REQUIRED/Stufegrant, keinKalenderverbund. Drive-Suche nach administrativenOAuth-/GoogleCloud-Dokumentnamen ohne passendenProjektbeleg. KeinGoogleCloud-Verwaltungstool/keineOutboundIdentity verfügbar. KeineKontofreigabe, produktiverKalendereintrag, Pixelinstallation oderOpenMeteo-Live-Netzprobe ausgeführt.

## Kalender/Wetter R1 – Implementierung (22.10.2026)

Entwicklung 1.54.4/93 auf codex/calendar-weather-20261022, aus dem belegten Original92-Quellstand, keine neue Auslieferung. R0-Sicherung bfda9d56945d7fe330a60bcec7b41b039e76113b auf GitHub exakt zurückgelesen. Neue native OpenMeteoWeatherSource: öffentlicher nichtkommerzieller Schlüssel-freier16Tage-Endpunkt, validierte UTC-Stunden/metrische Einheiten/Koordinaten, atomare Cache-Veröffentlichung, echte Datenabdeckung statt pauschaler14Tage. Vorhandene GeoSphere-Kurzfristquelle und amtliche Warnungen erhalten; zusätzliche Warn-/Nowcastfehler kennzeichnen den Teilstatus. Drosselung/Retry-After bleiben getrennt. Kein Abo/Secret/SDK/Manifestrecht hinzugefügt.

Ortsauflösung St./Sankt ergänzt, Resolver-Version3 für erneute negative Cacheprüfung; Adressen und Kundenbeziehungen bleiben unverändert. Frühe klare Wettertrends erscheinen neutral und unsicher; Detailansicht nennt tatsächliche Quelle, Datenende und kurzfristigen Ersatz. KalenderSetupRules zeigt Konto, beobachtete Berechtigung und bestätigten Kalender getrennt, Registrierungshilfe nennt ausschließlich öffentliche Paket-/Zertifikatsdaten. Sechs geschützte native Kalenderhandlers und alle gesicherten Preference-Writes unverändert; Minimal-Scopes, Token-Erneuerung/401, Restart, IDs/ETag/Readback unverändert.

Fokussiert:42JVM-Suiten inkl. neuer Kalender- und16Tage-Quelle PASS;66Orts-/Wetterworkflowprüfungen PASS;4native UI-Schutzprüfungen PASS;4neue Browser-Prognoseszenarien PASS, einschließlich Tag14-Anzeige,320/360/426px und16/24px-Schrift, Quelle/Ende/Ersatz/Unknown/Escaping/keine Schreibaktionen. Ein neuer Browserlauf hatte zunächst einen ungenauen Testselektor für zwei identische Home-/Planungsbuttons; auf die sichtbare Planung eingeschränkt, keine Assertion entfernt. Ein neuer nativer Laufzeittest für tatsächliche MCP-Coverage angelegt, Ausführung mangels Originalsignierung NICHT erfolgt. Vollständige Regression, Release-Kompilierung/Lint und Artefakt-Privacy folgen.

117MCP-Tools/156UI-Aktionen und eingefrorener Vertrag erhalten.62UI-Quellen geprüft und Fingerprints erneuert, keine fachlichen Aktionsmappings verändert. User-Auftrag erlaubt neue Funktionen und den Entwicklungsbuild; ursprüngliche Layout-/Daten-/Installationsschutzregeln bleiben. Aktualisierte Umgebungsbeobachtung nach der fokussierten Phase: spec_revision32/observations_current meldet fünf HG_ORIGINAL-Laufzeitvariablen ready. Die frühere Schlussfolgerung aus secrets=[] allein war unvollständig; deshalb Originalsignierung und isolierte native Cloud-Abnahme jetzt möglich und als nächste Phase vorgesehen. Keine Variablenwerte gelesen/geloggt oder an Tests/Build weitergegeben. Google-Projektzugriff, tatsächliche Gerätefreigabe und Live-Open-Meteo-Probe weiterhin offen, kein entsprechender Erfolg behauptet.

## Kalenderzugang und nichtkommerzielle 16-Tage-Wettervorschau – 22.10.2026 / R0

Aktueller ausdrücklicher Nutzerauftrag: neues Kalenderkonzept umsetzen, Wetter für die vom Nutzer erklärte nichtkommerzielle Nutzung umstellen, Ausführung nach Projektprotokoll. Dieser Folgeauftrag autorisiert die dafür nötigen Kalender-/Wetter-Funktionsänderungen; die Layoutaufträge 1.54.2 bleiben als erledigte historische Abnahme erhalten. Datenschutz-, Originalsignierungs-, Installations-, Konto- und Scope-Schutz gelten weiter. Keine neue kostenpflichtige Nutzung, keine produktiven Testtermine.

Ausgang: GitHub-main e499e745832449ce0202d04d9903495248e8b034, APK92-Branch e09b60b6f84ffbb03a8d56ae9180c8fdafa7457c, unveränderliche Original-APK-Quelle aff54dbd7cd64437645bba71242688ec5da4c30b. Neues einziges Arbeitsbranch codex/calendar-weather-20261022 im vorhandenen Git-Worktree; ältere Branches/Tags/Quellen erhalten. Aktuelle AGENTS.md vom GitHub-main gelesen; keine main-Übernahme. Technische Pixel-MCP-Lesung bestätigt tatsächlich 1.54.3/92, denselben aff54db-Quellfingerprint, MCP9 und117Werkzeuge; keine Betriebsinhalte übernommen. Kalender weiterhin CALENDAR_APP_REGISTRATION_REQUIRED, verbunden=false.

Baseline vor App-Funktionsänderungen:393Python/0Skips (37alte QA-Schlüsselerzeugungstests ausgeschlossen),40JVM-Suiten,55MCP-Szenarien, Vertrags-/UI-Review-/Pflanzengates PASS. Zwei vollständige unveränderte Browserläufe jeweils49/50PASS: gartenpirat-photo wartete beim Wiederwählen auf die bereits bekannte Bildbreite des alten Bildes, bevor die asynchrone Dekodierung das neue Blobbild eingesetzt hatte. Originalfehlberichte außerhalb des Checkouts erhalten. Ausschließlich Test-Synchronisation korrigiert: Beim Wiederwählen wird zusätzlich die neue Bild-URL abgewartet. Alle bestehenden Assertions, Bildidentitätswechsel, Abbruch-/Entwurf-/Ressourcenschutz und App-Code erhalten. Danach gezielte Fotosuite und vollständiger50/50Browserlauf PASS. Keine Assertions/Gates entfernt oder Qualitätsausnahme eingeführt.

Messbare Abnahme für die folgende Umsetzung:
1. Native kostenlose Open-Meteo-Abfrage ohne Konto/API-Schlüssel, forecast_days=16, HTTPS/UTC/metrische Einheiten; gültige stündliche Werte decken einen vollständigen synthetischen Termin mindestens14Tage voraus. Keine Statistik als Modellprognose, Null-/veraltete/fehlende Werte bleiben unbekannt.
2. Provider, tatsächlicher Datenzeitraum und Aktualität im vorhandenen Wetter-/MCP-Pfad korrekt; keine pauschale14Tage-Erfolgsangabe ohne Datennachweis. GeoSphere-Warnungen/Nowcast ergänzend, deren Ausfall darf eine vorhandene gültige Hauptprognose nicht vernichten.
3. St./Sankt-Ortsvarianten und eindeutige Ortsauflösung synthetisch prüfen; mehrdeutige Orte ablehnen, Originaladressen/Termine/Koordinaten nicht überschreiben.
4. Kalender-Kontoauswahl, tatsächliche Google-Freigabe und bestätigte Kalenderbindung wahrheitsgemäß unterscheiden; vorhandenen AuthorizationClient-/Readback-/Idempotenz-/ETag-/401-/Neustartschutz erhalten. Registrierungshinweise ohne Tokens, Konten oder neue Scopes.
5.117MCP-Werkzeuge/156UI-Aktionen, DB4/Snapshot2, IDs/Relationen/Journalformate, Originalpaket und Manifest-Berechtigungen erhalten. Relevante neue positive/negative Tests sowie vollständige Regression/Release/Lint/Privacy und verifizierte Quellsicherung.
6. Kein Pixelupdate, keine Konten-/Google-Projektänderung oder produktive Schreibprobe ohne die dafür erforderliche reale Freigabe. Aktuelle Umgebung meldet keine bereiten Originalsignierungsbindungen und keinen Google-Cloud-Verwaltungszugang; Open-Meteo ist nicht in ihrer ausgegebenen HTTP-Zielliste. Diese externen Grenzen offen führen, keinen Ersatzschlüssel/Netzwerk-Bypass verwenden.

Status: R0 abgeschlossen; eigentliche App-Funktionsumsetzung folgt.

## Original-APK 1.54.3 / Build 92 – Cloud-Abnahme (22.10.2026)

**Beauftragte APK erstellt, originalsigniert und als privater Update-Kandidat geprüft. Keine Pixelinstallation oder produktive Google-Freigabe.** Der neue APK-Auftrag ersetzt für diesen Build das frühere Verbot von APK/Signierung/Versionserhöhung im abgeschlossenen UI-Auftrag; Daten-/Konto-/Installationsschutz bleibt. Paket `at.hausgarten.service`, Version **1.54.3 / 92**. Gegenüber dem vollständig gesicherten UI-Stand c693b673c42556c87e6c72ee3f9318790d9de825 änderte sich ausschließlich die Versionsangabe in android/app/build.gradle. Frühere lokale/UI-Historie und Original91 unverändert erhalten; isolierter Git-Worktree desselben Repositorys, kein zweites Repository.

- Branch `codex/apk-1.54.3-build92-20261022`, exakter APK-Quellcommit **`aff54dbd7cd64437645bba71242688ec5da4c30b`**, unveränderliches Tag **`source/v1.54.3-build92`** regulär gepusht und zurückgelesen. source_preservation PASS am exakten sauberen Commit; kein Skip/Dirty/Offline-Weg. Main bleibt unberührt.
- APK **Haus-Garten-1.54.3-92-original-update.apk**, **36192274 Bytes**, SHA256 **`4f08b012bfd2c38259974c94b3071e218962d330f70ccb332725c77d25ec2814`**. Originalzertifikat tatsächlich geprüft: `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`; genau ein Signer, v2/v3 und16KiB-Ausrichtung PASS. Signierung ausschließlich über tools/sign_original_apk.py in persönlicher Cloud; Originalwerte weder geloggt noch nach CI/Git übertragen, kein Ersatzschlüssel.
- Main-/Gradle-SHA256 **`17d8be0d257733544896546bf22086d33d157011dfd0ab14db7664d42486ed34`**,542Dateien,421Mainassets plus zwei exakt bytegleiche Compiler-ART-Profile.181Pflanzenprofile/362Bilder. DEX bestätigt aff54db, denselben Quellhash,1.54.3/92, SOURCE_DIRTY=false, DEBUG=false, UI_PREVIEW=false und die neuen nativen UI-Klassen. Null operative Seedzeilen; Source-/Workspace-/fertige signierte APK-Privacy PASS.

| Tatsächlich ausgeführte Prüfung | Ergebnis |
|---|---|
| Python | **393PASS,0Skips**;37alte schlüsselerzeugende QA-Tests ausdrücklich ausgeschlossen |
| JVM | **40Suiten PASS** |
| Browser | **50/50Suiten im vollständigen Lauf PASS**, einschließlich Foto/Galerie/Kalender/Einstellungen |
| MCP-stdio, synthetischer Temp-Drive | **55/55Szenarien PASS** |
| Release-/Instrumentierungs-Build und Lint | **84Tasks PASS;0Fehler/56Warnungen** (bekannte Übersetzungshinweise) |
| Vertrag/UI-Review/Pflanzen | PASS; **117Tools/156Aktionen**, Vertrag899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813 unverändert |
| Native Android-SQLite/MCP | **158PASS,0FAIL**; isolierte synthetische Stores, Hauptsnapshot unverändert |
| Cloud-In-place-Update91→92 | **PASS**: vollständiger Snapshot, IDs, ausstehende Aktionen, Einstellungen, Tunnelkonfiguration und laufender Timer erhalten; keine Deinstallation/Datenlöschung |
| Fresh-isolated / Native WebView und Verbindungsscreens / abschließender Snapshot | **PASS/PASS/PASS**; UI und Snapshot zusätzlich nach System-UI-Wait wiederholt |
| Fertige APK | Signatur/Original91-Zertifikatsvergleich/Version/Assets/Seed/Privacy/16KiB/DEX-Provenienz **PASS** |

Nur der dedizierte `GartenpiratCloud`-Emulator mit qemu=1 und expliziter Seriennummer emulator-5554 wurde benutzt. Beim Start wurden nach Nachweis fehlender Emulatorprozesse ausschließlich verwaiste OS-Lockdateien erhalten/verschoben; keine AVD-/App-Daten entfernt. Die Sichtprüfung fand einen tatsächlichen **System UI isn't responding**-Dialog des Software-AVD. Ausschließlich dessen exakt identifizierter **Wait**-Button wurde betätigt; danach native UI und kompletter Snapshot erneut PASS. Finale synthetische Aufnahme outputs/apk92-native-ui.png tatsächlich ohne OS-Dialog gesichtet; ursprüngliches ANR-Bild samt Privacy-Beleg erhalten. Kein App-ANR-Fix oder Pixel-Leistungsnachweis daraus abgeleitet.

Infrastrukturvorläufe getrennt: Im frischen Worktree fehlte der Ausgabepfad des ersten Pythonreports; dessen393Assertions waren grün, nach Einrichtung nochmals kompletter393er-Lauf mit Bericht/Privacy PASS. Der externe DEX-Textleser traf auf MUTF-8 außerhalb BuildConfig; Decoder korrigiert und sämtliche ausgewählten unveränderten ASCII-Provenienzwerte exakt geprüft. Kurz nach Emulatorstart waren Android-Systemdienste noch nicht bereit; bestehender91-Snapshot anschließend lesend erfolgreich verifiziert. Keine Testassertion oder Qualitäts-/Signier-/Privacy-/Vertragsprüfung abgeschwächt, keine App-Funktionsänderung.

Originalquellen: **Haus-Garten-Quellcode-aff54dbd7cd6.zip**, SHA256 **`17af4375bdcf14352b93fdfcc7a6093ced4b5f7464422cfe9c45533d2dc45040`**,1237Originaldateien;506UTF-8-Dateien verlustlos rekonstruiert. Offizieller source_lifecycle-Exporter mit strikter Quellsicherung/Privacy benutzt. Privater Buildordner **1.54.3-build92-Original-APK-und-Quellcode-aff54db**: https://drive.google.com/drive/folders/1pzAVZQcxcvYEqbEYYzZXKg7cNm0qllw_ . APK: https://drive.google.com/file/d/1UHmeEkSO6k0-zU3i6DM53gjgyHDL-fwk/view . Original-ZIP: https://drive.google.com/file/d/10o0nWq_EK2qAF4zQHj_U2jev_4s6C5qT/view .19Quell-Transferbelege:18Dateien tatsächlich heruntergeladen und SHA256-bytegleich, ZIP vollständig authentifiziert gestreamt mit exakter Größe;0offeneÜbertragungen/0Duplikate. APK ebenfalls vollständiger36.192.274-Byte-Streamingbeleg und private Metadaten; wegen32MiB-Materialisierungsgrenze **kein Remote-SHA256-Bytevergleich für APK/ZIP behauptet**. Lokale Original-APK-/ZIP-Hashes separat vollständig geprüft. Payloads vor Manifest/Prüfsummen, Startdatei zuletzt; bestehende gute Generationen erhalten.

Abschluss-Entwicklungsquellen nach dem Dokumentationscommit zusätzlich über denselben offiziellen Exporter im privaten Unterordner https://drive.google.com/drive/folders/12LQGZIMfClGhdKPOikgbU3L_e5pg8rPT ; dessen Manifest/00-START nennen den tatsächlichen separaten Dokumentationscommit. Dieser aktuelle Entwicklungsstand ist kein anderer APK-Quellcommit: aff54db bleibt unverändert und über das Originaltag abrufbar. Nur Protokoll-/Entwicklungs-/Kandidatenmetadaten werden nach der APK-Erstellung ergänzt; Artefaktbytes und App-/Gradle-Fingerprint bleiben unverändert. Geänderte Metadaten: CODEX-STATUS.md, AKTUELLER-STAND.json, source-lifecycle.json, source-releases.json und docs/verification/original-apk-1.54.3-92.json. Verifizierte Auslieferung90, vorheriger Kandidat91 und zuletzt beobachteter Pixel90 bleiben separat erhalten; keine Retentions-/Löschfreigabe abgeleitet.

**NOT EXECUTED:** physische Pixelinstallation, echtes Update90→92 auf dem Pixel, Kamera-/Konten-/Live-Google-Freigabe und Google-Cloud-Consent-Konfiguration. Cloud91→92 ersetzt diese Abnahmen nicht. Keine automatische Installation, kein produktiver Testtermin, keine Konten-/Scope-/Daten-/Syncänderung, kein Push/Merge auf main, keine neue Freigabe oder Löschung in Drive. Der Kandidat ist kein behaupteter vollständig live abgenommener Produktivrelease.

## Kalender-Übersicht K1–K9 (Basis 1.54.2/91)

**Gesamtauftrag umgesetzt; Quellcode-/Hostabnahme PASS mit getrenntem Galerie-Nachlauf. Keine neue APK oder Gerätefreigabe.** Maßgeblich ist `CODEX-GESAMTAUFTRAG-1.54.2.md` (SHA256 `f408ab0d9875931d033de8d6f46d1b3cc4ddab4ce094f374a8a1562da8e5d783`); dessen äußere Reihenfolge wurde eingehalten: Ausgangslauf → A → C → G1 → G3 → G4 → G5 → G6 → K1–K9 → Abschluss. G2 wird durch K8 ersetzt, B entfällt. Abweichende ältere eingebettete Versions-/Reihenfolgeangaben wurden nicht als neuer Auftrag behandelt.

Arbeitsbranch: [codex/calendar-ui-overview-20261021](https://github.com/mihalhafner-collab/Gartenpirat-/tree/codex/calendar-ui-overview-20261021), Basis `d45c72544533fbb4420513d454b03a9043cac99f`. Dauerhaft bestätigter vollständiger Implementierungsstand: [97efbbbf9a2abbf7760f5beebbf7ce838291ed00](https://github.com/mihalhafner-collab/Gartenpirat-/commit/97efbbbf9a2abbf7760f5beebbf7ce838291ed00), Tree `aebf6ec2602d60f6aaacefcdc8a4a3e131a95aa6`. Die bestätigten Testkorrekturen liegen auf [dda5de58172fa48589f47801fa979f7126a17d58](https://github.com/mihalhafner-collab/Gartenpirat-/commit/dda5de58172fa48589f47801fa979f7126a17d58), Tree `dd550c783c82b7858df966d50d86cf0b4cef8fa1`; der App-Fingerprint bleibt identisch. Die abschließende Galerie-Fixturekorrektur liegt auf [220f452914b50b6f0de9292eb984be4aabd41224](https://github.com/mihalhafner-collab/Gartenpirat-/commit/220f452914b50b6f0de9292eb984be4aabd41224), Tree `c49e8bb8bd35a937bb9909ad34092b86a473f9c3`; weiterhin identische Appbytes. Weitere Abschlussänderungen betreffen ausschließlich diesen Bericht und Entwicklungsmetadaten. Der unten folgende historische native91-PASS gehört zum ursprünglichen Kandidaten, **nicht** zu diesem neuen UI-Stand.

### Umsetzung und Dateien pro Paket

| Paket | Umsetzung | GitHub-Commit |
|---|---|---|
| A | Klartext ausschließlich für exakt 127.0.0.1, localhost und 10.0.2.2; allgemeines Manifest-Attribut entfernt. | [1123ac6c](https://github.com/mihalhafner-collab/Gartenpirat-/commit/1123ac6c05a1382124b340231b54178e1da105ba) |
| C | WebView-Dateizugriffe gesperrt; CSP vor Skripten. connect/object/frame/base/form gesperrt, Android-Asset-Origin und Foto-Provider erhalten. | [87a533ac](https://github.com/mihalhafner-collab/Gartenpirat-/commit/87a533ac62db0e77425878cc031e366bc4a36760) |
| G1 | Gemeinsame tokens.css, lokale Schriftfamilien, Token-Aliase und Preview-CSS-Inventar. | [4fec5113](https://github.com/mihalhafner-collab/Gartenpirat-/commit/4fec511366dddc64a8282333154cfc7e6472dc66) |
| G3 | Heute-Aktionen im Fluss direkt nach Timer; Wetterursachen aus bestehenden Daten. | [e4db3e2d](https://github.com/mihalhafner-collab/Gartenpirat-/commit/e4db3e2d553fec2ee20cde1eab7f8c17397ccc5e) |
| G4 | Ein Zurück-Bedienelement je Unterseite; Hauptreiter ohne Zurück, originale Gesten/Entwürfe erhalten. | [3dae5839](https://github.com/mihalhafner-collab/Gartenpirat-/commit/3dae58391bdd587abc4b94bb0a9297525c5ae89b) |
| G5 | Punkt-/Text-Chips; Kalenderfehler, Wetter und Pixel-Meldungen getrennt. | [a3193f8d](https://github.com/mihalhafner-collab/Gartenpirat-/commit/a3193f8d0f3808bda8888d438389d7bc11932be3) |
| G6 | 24px-Linienicons im Mehr-Menü und Suche in Feldoptik; sämtliche13 Ziele/Reihenfolge/Handler erhalten. | [e4d5f7ed](https://github.com/mihalhafner-collab/Gartenpirat-/commit/e4d5f7ed10e294793346f90ba3e86c52f081d018) |
| K1 | Heute-Sprung, sieben gezählte Tage, chronologische Tagesköpfe; Wochen-/Tagesladen mit expliziter Teilmengenanzeige. | [a4eff80e](https://github.com/mihalhafner-collab/Gartenpirat-/commit/a4eff80e80ed38d992bffc7246d6c30fac028f10) |
| K2 | Zwei Textzeilen je Terminkarte plus umbrechende Warnchips; Originalklassifizierungen und Warntexte erhalten. | [4575dd99](https://github.com/mihalhafner-collab/Gartenpirat-/commit/4575dd99deeabd63f88fc33ffb2eb71051e36536) |
| K3 | Nur Tag/Woche als Segmente; Ist-Stunden, Kundenfilter und originale Kopfaktionen separat. | [b8091090](https://github.com/mihalhafner-collab/Gartenpirat-/commit/b809109084b82e82e9099cc44b9402fd923e052e) |
| K4 | 56px-Termin-FAB über Navigation; Freie Zeit finden im Fluss, letzte Karte/Weitere-Button scrollbar erreichbar. | [0f2f7439](https://github.com/mihalhafner-collab/Gartenpirat-/commit/0f2f7439231745e68790dc469d131048b3952f75) |
| K5 | Genau ein priorisierter Verbindungschip, ursprünglicher Einrichten-Handler; Diagnose bleibt geschlossen. | [46463da7](https://github.com/mihalhafner-collab/Gartenpirat-/commit/46463da78324acdee6656c512610e29098da393a) |
| K6 | Kurzformular und Mehr Optionen aus Originalfeldern; 1/2/4-Stunden-/Ende-offen-Chips, identische Speicherargumente. | [c6e1e79f](https://github.com/mihalhafner-collab/Gartenpirat-/commit/c6e1e79f0e624d240537f8a5247dc43763c861e9) |
| K7 | App-/Google-Vergleich mit markierten Unterschieden, bis360px gestapelt; Auswahlwerte und Bestätigungen unverändert. | [6763d29e](https://github.com/mihalhafner-collab/Gartenpirat-/commit/6763d29ecf359cfe0018f8b80e2b731b7f1cb43e) |
| K8 | NativeUi für drei Verbindungsseiten; ein Kalenderstatus, eine konkrete Fehlerbox, geschlossene technische Details, Gefahrstil für Trennen. | [d6c71ef6](https://github.com/mihalhafner-collab/Gartenpirat-/commit/d6c71ef622b821136caa45ced1088f490b4c785e) |
| K9 | Ein Diagnose-Aufklapper mit allen ursprünglichen Inhalten, neutraler Sync-Chip in Drive, Versionsfußzeile und deutliche Wiederherstellung. | [97efbbbf](https://github.com/mihalhafner-collab/Gartenpirat-/commit/97efbbbf9a2abbf7760f5beebbf7ce838291ed00) |

In jedem Paket außerdem `CODEX-STATUS.md` und `AKTUELLER-STAND.json`; bei UI-Quelländerungen wurde `android/app/src/main/assets/ui-mcp-actions.json` erst nach Quellreview neu erfasst. Weitere konkrete Dateien:

- **A:** `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/xml/network_security_config.xml`, `tests/test_network_security_policy.py`.
- **C:** `.github/workflows/qa-apk.yml`, `android/app/src/main/assets/mobile.html`, `android/app/src/main/java/at/hausgarten/service/MainActivity.java`, `tests/run_browser_regressions.py`, `tests/ui-fixture-source.cjs`, `tests/webview-hardening.test.cjs`, `tools/build_ui_preview_assets.py`.
- **G1:** `android/app/build.gradle`, `android/app/src/main/assets/design-concept.css`, `android/app/src/main/assets/field-ui.css`, `android/app/src/main/assets/mobile.html`, `android/app/src/main/assets/tokens.css`, `android/app/src/main/assets/workday-ui.css`, `tests/run_browser_regressions.py`, `tests/visual-consistency.test.cjs`.
- **G3:** `android/app/src/main/assets/field-ui.css`, `android/app/src/main/assets/field-ui.js`, `android/app/src/main/assets/planning-ui.js`, `android/app/src/main/assets/workday-ui.js`, `tests/field-ui.test.cjs`, `tests/visual-consistency.test.cjs`.
- **G4:** `android/app/src/main/assets/field-ui.css`, `android/app/src/main/assets/workday-ui.js`, `tests/navigation-calendar.test.cjs`, `tests/visual-consistency.test.cjs`.
- **G5:** `android/app/src/main/assets/field-ui.css`, `android/app/src/main/assets/navigation-status.js`, `tests/planning-ui.test.cjs`, `tests/status-collisions-gestures.test.cjs`, `tests/visual-consistency.test.cjs`.
- **G6:** `android/app/src/main/assets/field-ui.css`, `android/app/src/main/assets/workflow-ui.js`, `tests/fixtures/menu-presentation-baseline.json`, `tests/visual-consistency.test.cjs`.
- **K1:** `android/app/src/main/assets/planning-ui.css`, `android/app/src/main/assets/planning-ui.js`, `tests/planning-overview.test.cjs`, `tests/planning-ui.test.cjs`, `tests/run_browser_regressions.py`.
- **K2:** `android/app/src/main/assets/planning-ui.css`, `android/app/src/main/assets/planning-ui.js`, `tests/planning-overview.test.cjs`, `tests/planning-ui.test.cjs`.
- **K3:** `android/app/src/main/assets/planning-ui.css`, `android/app/src/main/assets/planning-ui.js`, `tests/planning-overview.test.cjs`.
- **K4:** `android/app/src/main/assets/planning-ui.css`, `android/app/src/main/assets/planning-ui.js`, `tests/navigation-calendar.test.cjs`, `tests/planning-overview.test.cjs`.
- **K5:** `android/app/src/main/assets/planning-ui.css`, `android/app/src/main/assets/planning-ui.js`, `tests/planning-overview.test.cjs`.
- **K6:** `android/app/src/main/assets/planning-ui.css`, `android/app/src/main/assets/planning-ui.js`, `tests/fixtures/planning-editor-baseline.json`, `tests/p3-search-planning.test.cjs`, `tests/planning-open-end.test.cjs`, `tests/planning-overview.test.cjs`, `tests/planning-ui.test.cjs`.
- **K7:** `android/app/src/main/assets/planning-ui.css`, `android/app/src/main/assets/planning-ui.js`, `tests/fixtures/planning-calendar-choice-baseline.json`, `tests/planning-overview.test.cjs`.
- **K8:** `android/app/src/androidTest/java/at/hausgarten/service/ReleaseLifecycleProbe.java`, `android/app/src/main/java/at/hausgarten/service/DriveConnectionActivity.java`, `android/app/src/main/java/at/hausgarten/service/NativeUi.java`, `android/app/src/main/java/at/hausgarten/service/PixelTunnelActivity.java`, `android/app/src/main/java/at/hausgarten/service/PlanningConnectionActivity.java`, `tests/fixtures/native-calendar-presentation-baseline.json`, `tests/test_native_calendar_ui.py`, `tests/test_native_ui_consistency.py`, `tools/verify_ui_mcp_inventory.py`.
- **K9:** `android/app/src/main/assets/business-actions-ui.js`, `android/app/src/main/assets/daily-usability.css`, `android/app/src/main/assets/daily-usability.js`, `android/app/src/main/assets/field-ui.css`, `android/app/src/main/assets/field-ui.js`, `android/app/src/main/assets/mobile.html`, `tests/fixtures/settings-presentation-baseline.json`, `tests/run_browser_regressions.py`, `tests/settings-cleanup.test.cjs`.

Die beiden hochgeladenen Aufträge liegen unverändert als `CODEX-GESAMTAUFTRAG-1.54.2.md` und `CODEX-AUFTRAG-KALENDER-UI-1.54.2.md` vor. Browser- und Python-Suiten sind im gemeinsamen Runner bzw. per unittest-Discovery registriert; der ausschließlich manuelle `.github/workflows/qa-apk.yml` verwendet dieselben Runner. Workflow nicht gestartet; keine neue App-Abhängigkeit.

### Tatsächlich ausgeführte Prüfungen

| Prüfung | Ausgangslauf | Endnachweis |
|---|---|---|
| Python, ohne historische QA-Schlüsselgenerierung |383PASS,0Skips;37 ausdrücklich ausgeschlossen|**393PASS,0Skips; dieselben37 ausgeschlossen**|
| JVM |40Suiten PASS|**40Suiten PASS**, einschließlich Kalender/Autorisierung/Recovery/Planung/Wetter und Vertrag|
| Vollständiger Browserrunner |45/46Suiten PASS; Foto8/9Checks|**49/50Suiten PASS im vollständigen Nachlauf; Galerie anschließend14/14Checks separat PASS. Damit PASS-Nachweise für alle50Suiten; kein weiterer50/50-Voll-Lauf behauptet**|
| Neue Browserchecks |nicht vorhanden|WebView6/6, Visual9/9, Planung19/19, Einstellungen6/6 PASS|
| Neue statische Pythonchecks |nicht vorhanden|Netzwerk3/3 und native Darstellung7/7 PASS; im393er-Lauf enthalten|
| MCP-stdio |historische55 nicht als neuer Ausgangslauf ausgegeben|**55/55Szenarien PASS**, isolierter synthetischer Temp-Drive, kein Pixel-Gerät|
| Release-Java + Instrumentierungs-Java + Lint |0Fehler/50Warnungen|**42Gradle-Tasks PASS;0Fehler/56Warnungen**; nur Compile/Lint, kein assemble/package/sign|
| Vertrag / UI-Review |117Tools/156Aktionen, eingefrorener Vertrag|PASS; **sämtliche156 Action-Objekte inhaltlich identisch**|
| Pflanzenmirror |370öffentliche Assets PASS|370PASS; Katalog/Bilder unverändert|
| Source-/Workspace-Privacy |PASS|PASS, einschließlich ignorierter/unversionierter Dateien; kein neuer APK-Privacy-PASS behauptet|
| Versions-/Berechtigungs-/Quellvergleich |1.54.2/91, bestehende Berechtigungen|PASS; Application-ID, Version, Berechtigungen und geschützte Geschäftsdateien unverändert|
| Vorhandene APKs |zwei im Checkout und originalsignierter91 außerhalb|SHA256/Bytezahlen unverändert; keine weitere APK/AAB entstanden|
| Preview/Release-Artefakt |Original91 nur lesend gegen Originalquelle geprüft|Preview-Quell-/Browserprüfung PASS; neues UI-Artefakt **NOT TESTABLE**, keines erzeugt|

Der unveränderte Foto-Test besteht im letzten vollständigen Lauf. Der intermittierende Ausgangsfehler wurde nicht repariert; frühere Fehlversuche bleiben als solche dokumentiert.

Erster vollständiger End-Browserlauf: 47/50Suiten PASS, FAIL: `status-collisions-gestures.test.cjs`, `settings-cleanup.test.cjs`, `gartenpirat-photo.test.cjs`. Der neue Einstellungs-Test wartete zunächst nicht auf das asynchrone toggle-Ereignis; er wartet jetzt ausdrücklich auf den unveränderten Diagnoseinhalt und behält seine Assertion. Nach22:00UTC lag Wien bereits im nächsten Tag: der vorhandene Kollisions-Test kombinierte eine Wiener Tages-Fixture mit UTC-Daten. Ausschließlich seine Testvorbereitung erhält jetzt eine feste Uhr `2026-10-21T10:00:00Z`; sämtliche11 ursprünglichen Schutzprüfungen bestehen. Der anschließende vollständige50er-Browserlauf erreichte49/50; dessen einzige fehlgeschlagene Suite war sync-customer-gallery. Die Galerie-Testbrücke lieferte beim einsekündigen Statuspoll keinen Upload, obwohl der Test direkt einen Upload-Fortschritt in der UI setzte. Ihre synthetische Statusantwort liefert jetzt denselben kontrollierten Fortschritt; alle14ursprünglichen Assertions bestehen im gezielten Nachlauf. Das ist ein separater PASS-Nachweis, kein50/50-Gesamtlauf. Die ersten und zweiten vollständigen FAIL-Nachweise bleiben erhalten. Keine Kalender-Appfunktion dafür geändert. Zusätzliche Lint-Warnungen betreffen SetTextI18n bei neuen nativen Anzeigetexten; keine neue Fehler-, Cleartext- oder Sicherheitswarnung.

Reproduzierbare Befehle: `python tests/run_quality_suite.py --exclude-qa-signing`, `python tests/run_browser_regressions.py`, `python tools/run-followup-java.py`, `python tests/mcp_stdio_e2e.py`, `python tools/check_refactoring_contract.py`, `python tools/verify_ui_mcp_inventory.py`, `python tools/sync_plant_assets.py`, `python tests/apk_privacy_guard.py --prebuild`; im Android-Verzeichnis `gradle --no-daemon --no-configuration-cache --no-build-cache :app:compileReleaseJavaWithJavac :app:compileReleaseAndroidTestJavaWithJavac :app:lintRelease`. Browserumgebung verwendet vorhandenes Playwright/Acorn und Chromium mit synthetischer Asset-Route. `HG_ORIGINAL_*` aus allen Prüfsubprozessen entfernt, keine Werte gelesen oder protokolliert.

Cloudlokale Nachweise: `outputs/QA-Python-results.json`, `outputs/integration-ui/results.json` plus Suite-Logs, `outputs/integration-java/results.json`, `outputs/QA-MCP-E2E.json`; erste End-Browserlogs separat `/tmp/calendar-ui-final-initial-browser/`, vollständige Abschlusslogs `/tmp/calendar-ui-final-*.log` und `/tmp/calendar-ui-final-browser-repeat.log`; zweite vollständige Suite-Logs separat `/tmp/calendar-ui-final-repeat-browser/`, Galerie-Nachlauf `/tmp/calendar-ui-final-gallery-corrected.log` und `outputs/sync-customer-gallery-results.json`. Screenshots/Testartefakte bleiben synthetisch mit SHA-gebundenen Privacy-Sidecars; keine Ablage in Drive, Space oder einem neuen Dienst.

### Bewusste Anpassungen und erhaltene Schutzassertions

- G3/G4: Heute-Aktionen auf Scroll-Erreichbarkeit statt feste Bildschirm-Y-Koordinate geprüft; Unterseiten verwenden den tatsächlichen Kopf-Zurück. Entwurfs-, Back-/Gesten-, Speicher-, Timer- und Fotoassertions erhalten; echte Modal-/Seiten-Rückkehr zusätzlich geprüft.
- G5: Zwei genaue Wettertext-Erwartungen an „Wetterwarnungen: EIN/AUS“ angepasst; Zustände und Daten gleich.
- K1: Wochen-Metadaten berücksichtigen115 geladene Testtermine; ursprünglicher30→60-Nachladecheck zusätzlich unverändert im Tagesmodus. Neuer Schutz für höchstens vier100er-Wochenaufrufe und400/501-Teilmenge.
- K2: Mindestbreite>180 am vollständigen Kartenknopf statt an der nun geteilten Kundenzeile gemessen; ursprüngliche Höhenobergrenze erhalten und echte Einzeiligkeit ergänzt.
- K4: Zwei Geometriemessungen prüfen denselben Abstand an `#planning-new` statt am entfallenen festen Leistenlayout; DOM-Klasse und Handler bleiben.
- K6: In bestehenden planning-ui/planning-open-end/p3-search-planning-Tests nur „Mehr Optionen“ vor verschobenen Notizen/Serien/Verwerfen geöffnet. Sämtliche Ende-offen-/Dauer-/Werte-/Serien-/Entwurfsassertions bleiben; insbesondere bleibt das ursprüngliche Dauerfeld bei Ende offen verborgen/deaktiviert. `capture`, `save`, `stable`, `editorTimes` sowie collisionReport/overlapKind exakt wie vor Änderung; vollständige tatsächliche Golden-Argumentobjekte für Einzel- und Serienspeicherung identisch.
- K7/K9: Vollständige ursprüngliche Kalender-Auswahl-/Restore-Handler und Sync-Klassifikation per SHA geschützt; Abbruch, CAS-/Konflikthashes, stabile Request-ID, Entwurfssperre und HTML-Maskierung zusätzlich geprüft. Neue Tests korrigierten nur ihre eigenen Mess-/Aufklapper-/Fixture-Vorbereitungen; ursprüngliche Fehlversuche nicht als PASS umetikettiert.
- K8: Native Instrumentierung zählt Framework-Schalter über CompoundButton und öffnet den neuen technischen Aufklapper. Vorhandene Diagnose-/Verborgenheits-/Konto-/SHA1-Assertions erhalten und erweitert; ausschließlich kompiliert. `calendarPrimary`, `openCalendarApp`, `notificationSettings`, `chooseAccount`, `requestGoogleConsent`, `onActivityResult` bytegleich zur Basis. Sämtliche Preference-Schreibaufrufe der drei Activities unverändert.
- Abschluss: feste Uhr nur im bestehenden Kollisions-Test, explizites Warten auf das Diagnose-toggle im neuen Einstellungs-Test und konsistente Fortschrittsdaten in der synthetischen sync-customer-gallery-Brücke, wie oben begründet. Die drei Abschluss-Testdateien sind tests/status-collisions-gestures.test.cjs, tests/settings-cleanup.test.cjs und tests/sync-customer-gallery.test.cjs; ihre bisherigen Assertions bleiben unverändert. Der Foto-Test bleibt vollständig unverändert. **Keine Schutzassertion und kein Privacy-/Signier-/Fingerprint-/Vertragsgate entfernt oder abgeschwächt.** NativeUi als zusätzliche Quelle in den verpflichtenden Review aufgenommen.

### K1-Datenmenge, Fingerprints und Quellschutz

Das unveränderte Tool-Schema erlaubt `limit`1–100. Woche lädt100 pro Aufruf und höchstens vier Seiten je Ladevorgang; weitere100 bleiben ausdrücklich abrufbar. Zähler/Tagesköpfe nennen bei Teilmengen „geladen“, damit400/501 nicht als vollständig erscheinen. Tag behält30/60/Weitere30. Geplante Stunden verwenden die bestehende Minuten-minus-Pause-Formel nur bei bestimmten Enden; keine falschen0h bei offenem Ende. Wiener Kalendertage und UTC-Mittagsarithmetik schützen Datum/Heute am Tageswechsel.

- Main-/Gradle-Dateien: **540→542**, Mainassets **420→421** (neue tokens.css). Die417-Angabe im Auftragsmuster war für diese Basis veraltet; Gates enumerieren weiterhin sämtliche Pfade und Bytes exakt.
- Ursprünglicher Quell-SHA256: `747762839020b4ffb0491ac360c3cc054c455245eb4126b0c1e6d4e8882b0c3c`.
- Neuer Main-/Gradle-SHA256: **`ca1123916fb41090faa6d3c9a6c87a72cebdf8ea3d057f70f57d4e724b7250d9`**.
- UI-Review: **61Quellen**, SHA256 der kanonisch nach Schlüsseln sortierten reviewed_sources-JSON ohne Leerzeichen: `727d1d0689a78a9aa48ac1105ad2b14bf5007ea74f0df006418beee028ebe94d`. Katalogdatei-SHA256: `f6a0974fd8c420c73b2aa4c6e7f81ceeb10738738726dea75a6ff83046269a04`.
- Vertrag unverändert **`899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`**,117Tools/156Aktionen. Katalog-/Schema-/Storage-/Scopes-/ETag-/CAS-/Dedup-/Retry-/Recovery-Code unverändert.
- Sicherheits-Musterdiff gezielt ergänzt: CSP enthält `self` für das vorhandene Cloud-Asset-Testmodell und ausschließlich den eigenen Thumbnail-Provider in img-src; Preview schreibt nur dessen Paketkennung um. Externe Requests bleiben gesperrt, connect-src bleibt none. Keine Origin-/localStorage-Migration und kein erneutes AllowFileAccess(true).

### Sichtprüfung und Grenzen

Vorher: `outputs/grafik-vorher-{heute,mehr,status,kunden,arbeit}-360.png` (tatsächlich vor G1 aufgenommen) und `outputs/kalender-vorher-{woche,tag,leer,formular,abgleich,verbindung}-360.png` aus dem gesicherten G6-Stand vor K1.

Nachher: `outputs/grafik-{heute,mehr,status,kunden,arbeit,erfassung}-{320,360,426}-{16,24}.png`, außerdem `grafik-heute-G3-360.png`, `grafik-status-freigabe-360.png`, `grafik-mehr-G6-360.png`; Kalender `kalender-{woche,tag,leer,karten,steuerung,fab,verbindungschip,formular,abgleich}-360.png`; Einstellungen `einstellungen-sync-360.png`, `einstellungen-sicherung-360.png`. Tatsächlich gesichtet: alle sechs Web-Hauptansichten bei360/100%, Vorherbilder, Tag/Woche/Leer, Karten, FAB, Verbindungsstatus, fertiges Formular, Vergleich und beide Einstellungsansichten. Automatische Geometrie-/Overflow-Proben zusätzlich bei320/360/426 und150% Schrift. Letzte Karte/Nachladebutton über FAB/Tab-Leiste erreichbar; Formular-Pflichtfelder und Speichern bei360×800 ohne Scrollen sichtbar.

Selbstkritik: Mehrere wichtige Warnchips brauchen zusätzliche Zeilen; damit bleibt der Hinweisbereich länger als die zwei normalen Karten-Textzeilen. Im geöffneten Wetter-/Kalendervergleich bleibt durch vorhandene Support-/Wetterinformationen mehr Scrollweg. Native Kalender-/Zeitpicker und Systemschrift können anders aussehen als Chromium. Native Stile sind nur statisch und durch Kompilierung bestätigt; ihre tatsächliche Darstellung, Tastatur-/Systemleistenabstände und Touchbedienung auf dem Pixel wurden nicht geprüft. Die neuen nativen Texte erzeugen zusätzliche Übersetzungswarnungen. Keine Behauptung, dass das UI-Paket eine reale Google-Cloud-Konfiguration repariert oder eine dauerhafte Live-Verbindung nachweist.

**NOT EXECUTED:** Pixel-/WebView-/Kamera-Abnahme, native Instrumentierung158/native_ui auf Gerät, echte Google-Freigabe, Google-Cloud-Projekt-/Consent-Konfiguration, Update90→91 und neue APK-/Preview-Artefaktabnahme. Für diesen Auftrag keine Installation, kein Emulatorstart und keine Konto-/Kalender-/Token-/Betriebsdatenzugriffe.

### Dauerhafte Sicherung und unveränderte Auslieferung

Jedes der16Pakete hat einen eigenen lokalen Commit und einen bestätigten kanonischen Commit im bestehenden privaten GitHub-Repository. Nach fehlender CLI-Git-Anmeldung wurde der vollständige identische Git-Tree über den vorhandenen GitHub-Connector gesichert; Branch-Updates nur mit erwartetem vorherigem SHA und ohne Force, danach genauer Ref-Readback. Lokale Prüfcheckpoints und kanonische Remote-Historie bleiben beide erhalten; kein Reset/Rebase/History-Rewrite. Der lokale K9-Prüfcheckpoint `ee0ab3317ec729ad17788094f3919809a7de0161` und kanonische K9-Commit `97efbbbf9a2abbf7760f5beebbf7ce838291ed00` haben denselben vollständigen Tree. Daraus wird kein bestandener Freigabegate für spätere Signierung/Originalexport abgeleitet; dessen vorhandene strikte Prüfungen bleiben erforderlich.

`main` unverändert auf `e499e745832449ce0202d04d9903495248e8b034` zurückgelesen. **Keine APK/Signierung/Versionserhöhung, keine Berechtigungs-/Daten-/Scope-/Sync-Änderung, keine Kontodaten in Testartefakten, kein Push/Merge auf main, kein neues Repository und keine Drive-/Space-Ablage.** `server.py`, `static/`, `db.py`, `mcp_integration/` und Lifecycle-/Release-Register unangetastet. Gradle-Änderung ausschließlich Preview-CSS-Inputs, keine Abhängigkeiten. Keine neuen App-Schriften/Bilder/Netz-APIs.

Original91 bleibt separat: unverändertes Tag `source/v1.54.2-build91` auf `50b518a1af9cee59763c4103d847f5016fb84fd9`, APK-SHA256 `874908a246f75a3c91f7399cf2e314364b9469f80843644ea340cf8c543b80c1`. Das vorhandene Original91-APK enthält diese neuen UI-Änderungen nicht. Verifizierte Auslieferung90, ursprünglicher Kandidat91 und zuletzt beobachtete Installation90 werden in `AKTUELLER-STAND.json` getrennt von dieser neuen Quellcodeentwicklung geführt.


## Kalenderauftrag – App-seitig abgeschlossen, Originalkandidat91 (21.10.2026, 19:21 UTC)

- App-Reparatur im bestehenden Produktivcode vollständig umgesetzt: Google-Rückgabe über offizielles SDK auswerten, technische Unterbrechung von fehlender Freigabe trennen, kein dauerhaftes Aufgeben nach acht Kalenderfehlern, keine Selbstunterbrechung laufender Einmaljobs, Wiederaufnahme bei Netzrückkehr und unabhängige tatsächliche Kalender-Erfolgszeitpunkte. Keine neuen Berechtigungen, Server, Tokenablage, IDs, Datenmigration oder produktiven Termine.
- **Finale Abnahme PASS:**383Python-Tests/0Skips,40JVM-Suiten,46Browser-Suiten,55synthetische MCP-Szenarien;158native SQLite/MCP-Prüfungen/0Fehler am tatsächlichen App-APK;fresh_isolated, native WebView-/Navigations-/Verbindungsdialogabnahme und erneutes vollständiges Snapshot-Verify nach UI PASS.37alte schlüsselerzeugende QA-Tests ausdrücklich ausgeschlossen. P1 unverändert117Tools/156UI-Aktionen/Contract899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813. Signatur v2/v3,16KiB,DEX-Provenienz,420Mainassets plus zwei exakt bestätigte Compilerprofile und Source/Workspace/Actual-APK-Privacy PASS. Screenshot mit klarer Heute-/Timer-/Drei-Reiter-Oberfläche ohne System-UI-Dialog angesehen; keine Pixel-Leistungsbehauptung.
- Unverändertes originales App-APK **1.54.2/91**,36184019Bytes, SHA256 **874908a246f75a3c91f7399cf2e314364b9469f80843644ea340cf8c543b80c1**, Originalzertifikat **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**. Exakter sauberer und zuvor gepushter APK-Quellcommit **50b518a1af9cee59763c4103d847f5016fb84fd9**, Main-/Gradle-Fingerprint **747762839020b4ffb0491ac360c3cc054c455245eb4126b0c1e6d4e8882b0c3c**. Unveränderliches Originaltag **source/v1.54.2-build91** gepusht und remote auf genau50b518a zurückgelesen. Spätere native UI-Prüfer-/Dokumentationsänderungen ändern keine App-/Gradle-Bytes. Native UI-Instrumentierung aus sauberem, remote bestätigtem71b7f007fda34dcec0707d6fed224f56b2c92bfb, SHA256ca16aac7882dd76a4ea45912c85beea82b93a023a070fb1034a57ea4e00998c4; ausschließlich ursprünglicher Cloud-Signierweg/Originalzertifikat.
- Private Kandidaten-APK: https://drive.google.com/file/d/1aqJIqC4oZXAym0QRYkE9pDFHeA3Yy2Kr/view . Originalquellarchiv: https://drive.google.com/file/d/1GZMxQpfGHwOF385_I5yyzyN0z5NQqFXh/view ,68760143Bytes, SHA256 **99fba5a127d16211cbb7b62ffdac229321f35c21883aaec1dd63cf008e3b5e18**,1220Originaldateien/489verlustlos rekonstruierbare UTF-8-Dateien. Vollständige private Generation19Dateien unter https://drive.google.com/drive/folders/1HFf0WpI0BGYL60jMEbYc9BfnvOVlYxKf :18kleine Dateien tatsächlich heruntergeladen/SHA256-bytegleich;ZIP vollständig authentifiziert gestreamt mit exakter Größe, kein zusätzlicher Remote-SHA behauptet. APK ebenfalls vollständiger Streaming-/Größennachweis und shared=false. Payloads vor Indizes, Startdatei zuletzt, anschließend vollständige frische Liste und19verifizierte Transferbelege/0offeneUploads. Versionsnachweis: https://drive.google.com/file/d/1YwVTmqP-HFSSU3JYXp8e1h9woNCAzb9s/view . Vorherige Originale und Freigaben erhalten; keine Löschung.
- Geprüfte Quellübernahme über PR5 im selben privaten Repository; die App-Änderung ist von den nachfolgenden reinen Abnahme-/Ablagenachweisen getrennt. Aktuelle Entwicklung und APK-Originaltag bleiben für einen neuen Task ohne vorherige VM abrufbar. Vollständiger aktueller Entwicklungs-/TXT-Export nach endgültigem Commit/Merge über vorhandenen Lifecycle-Weg; dessen tatsächliche Generation separat im privaten Register/Startwegweiser führen, nicht als APK-Quellcommit50b518a ausgeben.
- **Offen/NOT TESTABLE:** tatsächliche Google-Cloud-Projekt-ID/API-/OAuth-/Consent-Konfiguration und echte Gerätefreigabe. Autonome Suche in Repository/MD, Drive-Metadaten und engen Google-Benachrichtigungen ohne belastbaren Projekttreffer; kein GCP-Adminzugang/Browsersteuerung. Paket/SHA1 beweisen keine Projekt-ID, keine neue Zuordnung erfunden. Letzter lesender Pixelstand bleibt1.54.1/90; keine automatische Installation, Kontoänderung oder produktive Testtermine.90→91-In-place-Test wegen32MiB-Dateitransferlimit und bestehender Proxy-403 NOT TESTABLE; synthetisches91→91-Update/Neustart separat PASS. Bisherige Auslieferung90 und Rückfall89 bleiben geschützt;91 ist ein Cloud-geprüfter Kandidat und keine behauptete dauerhafte Live-Google-Verbindung.
- Erneuter kurzer Exec-Transportausfall gegen19:18UTC hat den finalen UI-Lauf nicht beendet; Zugriff wiederhergestellt, echte Endlogs und Exit0 gelesen. Kein erfundener OOM-/Google-/Appfehler als Ursache des Transportausfalls. System-UI-Wait und veraltete native Testannahmen oben getrennt dokumentiert.

## Kalenderauftrag – native Endabnahme und UI-Prüfer (21.10.2026, 19:12 UTC)

- Tatsächlicher finaler91-Kandidat: **158 native SQLite/MCP-Prüfungen PASS, acceptance failures=0**. Alle neuen Kalenderproben und sämtliche alten Kalender-/Provider-/Journal-/Foto-/Rechnungsproben bestanden; synthetischer primärer Snapshot unverändert. **fresh_isolated PASS** und **verify nach Neustart PASS**;90→91 weiterhin ausdrücklich NOT TESTABLE.
- Native UI-Abnahme meldete condition3 FAIL: vorbestehender Prüfer erwartete vier Hauptreiter, aktuelle gesicherte Oberfläche hat seit Build90 drei (Heute/Kunden & Gärten/Mehr). Derselbe Prüfer erwartete den Papierkorb noch im Mehr-Menü statt in Einstellungen. Ausschließlich diese Testannahmen an den bereits durch Browsertests bestätigten Vertrag angepasst; exakte drei Ziele plus Planungseinstieg und tatsächlichen Einstellungsweg weiterhin geprüft, keine Assertions gestrichen. Warten auf vollständig initialisierte bestehende fieldReady-Oberfläche ergänzt. Produktive App und finale APK bleiben bytegleich aus50b518a; neu originalsignierte Instrumentierung separat nachweisen.
- Sichtprüfung fand außerdem im Software-AVD einen echten **System UI isn't responding**-Dialog. Dieser Betriebssystemdialog wird über seinen geprüften Wait-Button verlassen; keine App-/Datenlöschung und kein behaupteter Pixel- oder App-ANR-Fix. Native UI-Endergebnis erst nach erneutem tatsächlichem Lauf als PASS führen.
- Finale APK zusätzlich privat im bestehenden Testartefaktordner erhalten: `1aqJIqC4oZXAym0QRYkE9pDFHeA3Yy2Kr`,36184019Bytes vollständig authentifiziert gestreamt, shared=false. Noch keine Auslieferungs-/Installationsbehauptung aus der Zwischenablage.
- UI-Nachlauf nach erster Prüferkorrektur erreichte sämtliche WebView-/Navigations-/Entwurfsproben und die nativen Verbindungen; scheiterte dann an einem weiteren alten Prüfbezeichner: Kalenderdialog hat getrennte „Arbeitszeiten und Wettergrenzen“/„Konto und Freigaben“ statt „Erweitert / Diagnose“. Logcat belegt AssertionError aus ReleaseLifecycleProbe, keinen produktiven Appfehler. Prüfer verwendet jetzt die tatsächlichen Disclosures, prüft zusätzlich verborgene/sichtbare Original-Paket-/SHA1-Diagnose ohne Kontoaktion; Assertions vom UI-Thread werden als Testfehler an den Runner weitergereicht. App-APK weiterhin unverändert; erneuter originalsignierter UI-Lauf nötig.

## Kalenderauftrag – Wiederaufnahme nach Cloud-Ausfall (21.10.2026, 19:08 UTC)

- Cloud-Ausführung wieder erreichbar; sämtliche finalen APK-/Instrumentierungs-/Originalquellbytes erhalten und erneut per SHA-256 geprüft. Gepushte Zwischenstände ohne Reset per Fast-forward in denselben Checkout übernommen. Ein zusätzlich konservierter Stash enthält ausschließlich den bereits remote gesicherten Export-Metadaten-Einzeiler; kein offener Produktivcode.
- Finale Python-/Privacy-Abnahme am tatsächlichen originalsignierten91-APK und aktuellen gesicherten Werkzeugstand: **383 Tests PASS, 0 Skips**,37 historische schlüsselerzeugende QA-Tests ausdrücklich ausgeschlossen. Kein erneuter Build und keine geänderten Appbytes.
- Derselbe dedizierte GartenpiratCloud-AVD wieder gestartet, ohne Wipe/Deinstallation/Datenlöschung. Ausschließlich verwaiste Emulator-Lockdateien des nachweislich beendeten Prozesses außerhalb AVD erhalten; synthetische Geräte-Datenpartition unverändert. Erneute Snapshot-Verify-/native Endabnahme läuft; noch kein finaler PASS behauptet.
- Exakte Originalquelle50b518a separat zusätzlich im privaten Entwicklungsordner gesichert: Ordner `1HFf0WpI0BGYL60jMEbYc9BfnvOVlYxKf`, Original-ZIP `1GZMxQpfGHwOF385_I5yyzyN0z5NQqFXh`. Authentifizierter vollständiger Streaming-Readback68.760.143Bytes, private Metadaten/Größe bestätigt; kein zusätzlicher Drive-SHA-Bytevergleich behauptet.14verlustlose TXT-Quelldateien und Manifest einzeln heruntergeladen und SHA-256-bytegleich bestätigt. Start-/Versionsnachweis und vollständige Generation werden erst nach den restlichen Prüfungen veröffentlicht. Vorherige Generationen unverändert erhalten.
- Physischer Pixel, produktive Google-Freigabe und Google-Cloud-Konfiguration weiterhin unverändert/offen. Original90→91-Geräteupdate bleibt mangels lokal materialisierbarer Original90-APK NOT TESTABLE; bereits belegter interner91→91-Test wird davon getrennt geführt.

## Kalenderauftrag – gesicherter Zwischenstand nach Cloud-Ausfall (21.10.2026, 18:54 UTC)

- Umsetzung und finales APK vollständig im bestehenden Cloud-Repository erarbeitet; Original-Appquellstand ist `50b518a1af9cee59763c4103d847f5016fb84fd9` auf `codex/calendar-permanent-20261021`. Kein Reset, Force-Push, weiteres Repository, Laptop-/Desktop-Pfad oder produktiver Schreibtest. Der installierte Pixel wurde zuletzt als1.54.1/90 ausschließlich lesend bestätigt.
- Finaler interner Kandidat1.54.2/91 tatsächlich gebaut und originalsigniert: APK-SHA256 `874908a246f75a3c91f7399cf2e314364b9469f80843644ea340cf8c543b80c1`, Originalzertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`, Quellfingerprint `747762839020b4ffb0491ac360c3cc054c455245eb4126b0c1e6d4e8882b0c3c`. DEX-BuildConfig bestätigt den genannten Commit, SOURCE_DIRTY=false, UI_PREVIEW=false, DEBUG=false und91/1.54.2.420Mainassets unverändert, nur zwei zusätzliche ART-Profile nach Format und exakter Compiler-Bytegleichheit akzeptiert. Source-/Workspace-/tatsächliches APK-Privacy und Signatur/16KiB PASS. Original-Instrumentierung SHA `c93761a988ac69dd4c7b2c35468fdfbccf8e7a34e1488d3381e893c921348c94`.
- Tatsächlich ausgeführte Regression:383Python-Tests/0Skips;40JVM-Suiten einschließlich2025neuer Kalenderprüfungen;46Browser-Suiten;55MCP-Szenarien PASS.37historische schlüsselerzeugende QA-Tests ausgeschlossen. P1-Vertrag `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`,117Tools/156Aktionen unverändert. Die neue reine Export-Metadatenpräzisierung wurde zusätzlich mit22bestehenden Lifecycle-Tests geprüft. Vollständiger Pythonlauf betrifft denselben Maincode; letzter Metadaten-Einzeiler separat geprüft.
- Erste tatsächliche native Gesamtrunde:157PASS/1FAIL. Sämtliche neuen Kalender-Recovery-/Unterbrechungs-/Rechte-/Zeitpunktproben und bestehenden Kalender-ACK-/Konflikt-/410-Proben PASS. Ein vorbestehender Test rief Papierkorbleerung mit falschem Hashfeld auf; ausschließlich den Test auf den unveränderten expected_preview_hash/preview_hash-Vertrag korrigiert, sämtliche Schutzassertions erhalten. Produktiven Papierkorbcode nicht geändert.
- Nach Neubuild aus50b518a: finales Original91 per install-r über ersten internen91 installiert, vollständiger synthetischer Snapshot-/ID-/Queue-/Relations-/Settings-/Tunnel-/Timervergleich PASS. Dies ist91→91 mit gleicher Maincodebasis;90→91 bleibt NOT TESTABLE. Original90 aus Drive identifiziert und als vollständige36.171.731-Byte-Streamreferenz gelesen, lokale Materialisierung über32MiB abgewiesen; Dateihost durch vorhandene Proxyregel403 gesperrt. Kein Umgehen oder neue Egressfreigabe.
- Vollständiger nativer Nachlauf auf finalem Artefakt gestartet. Letzter unmittelbar gelesener Stand: laufende Owner-/Backup-/Versionsproben PASS; der Endabschluss wurde wegen Cloud-Verbindungsverlust NICHT gelesen. Native Gesamtabnahme deshalb OFFEN, kein erfundenes PASS. Fresh-isolated-/WebView-Nachlauf noch nicht ausgeführt. Cloud environment_status bestätigt connectivity=offline bei observed_phase=running; Ursache unbekannt. wait_for_environment meldete ready, tatsächlicher exec-server blieb unerreichbar. Keine automatische Pixelinstallation.
- Finales Originalquellarchiv lokal erzeugt: `Haus-Garten-Quellcode-50b518a1af9c.zip`, SHA `99fba5a127d16211cbb7b62ffdac229321f35c21883aaec1dd63cf008e3b5e18`,1220Originaldateien,489UTF-8-Dateien vollständig rekonstruierbar. Artefakte lagen außerhalb Checkout unter /tmp/hausgarten-calendar91-final und /tmp/hausgarten-source-exports/calendar-build91-final-50b518a. Deren Fortbestand nach Cloud-Ausfall ist nicht bestätigt. Noch KEIN Drive-Upload, KEINE Auslieferung, KEIN Release-Tag91 oder Mergefreigabe. Exakte Originalquellen auf GitHub sind unabhängig davon gesichert und neu exportierbar.
- Externe Einrichtung autonom gesucht: Repository/MD, Drive-Metadatensuche und eng begrenzte Google-OAuth-/Cloud-Benachrichtigungssuche ohne App-Projekt-ID-Treffer. Keine GCP-Administrationsidentität oder Browsersteuerung verfügbar. Tatsächliche Calendar-API-/Android-OAuth-/Consent-/Organisationskonfiguration und echte Google-Freigabe bleiben NOT TESTABLE; Paket/SHA-1 erlauben keine eindeutige Ableitung einer Projekt-ID. Keine neue Projektzuordnung, Kontoänderung oder produktive Testtermine erzeugt.
- Weiterarbeit: denselben Branch abrufen und ungesicherte lokale Abweichungen zunächst erhalten; Cloud-Erreichbarkeit wiederherstellen und native Endlogs lesen/gegebenenfalls denselben finalen Quellenstand erneut bauen. Native Endabnahme, isolierter Erststart/WebView,90→91 soweit Originalbytes erreichbar, letzte Privacy-/Signatur-/Provenienzprüfung, immutable Originaltag91, geprüfte PR-Übernahme und regelkonformer privater Drive-Export erst mit tatsächlichen Nachweisen abschließen. Keine bestandene Google-/Pixel-Endabnahme oder Auslieferung aus diesem Zwischenstand ableiten.


## Dauerhafte Google-Kalender-Anbindung – Umsetzung (21.10.2026)

- Ausdrücklicher Folgeauftrag: das im Chat erarbeitete Kalenderkonzept autonom umsetzen und zuvor die MD-Regeln beachten. Frischer main `a112765844ee5a41408dc0538236d0eca52eacbf`, ursprünglicher Checkout sauber und ohne Reset per Fast-forward übernommen; ein Arbeitsbranch `codex/calendar-permanent-20261021`. AGENTS, vollständiger Auftrag, aktueller Status, Reparatur-/Signier-/MCP-/Sync-Nachweise und Issue1 gelesen. Kein zweites Repository, kein Desktop-/Laptop-Pfad.
- Abnahme vor Implementierung: Google-Rückgabe vor pauschalem Abbruch auswerten; fehlende Antwort/echten Abbruch/Registrierung/Netz unterscheiden und ausschließlich bereinigte Diagnose mit Zeitpunkt/Schritt speichern. Android-Unterbrechung darf keine Freigabesperre erzeugen. Kalender muss auch nach mehr als acht temporären Fehlern mit begrenzter Frequenz wieder anlaufen; echte fehlende Rechte bleiben gesperrt. Konto-/Kalenderbindung, Scopes, IDs, ETags, CAS, Dublettenschutz und lokale Änderungen erhalten. Erfolgszeitpunkt nur nach tatsächlich erfolgreichem Kalenderlauf, getrennt vom allgemeinen Wetter-/Planungscheck.
- Geplante Nachweise: synthetische JVM-/Browser-/native Regressionsfälle, unveränderte117MCP-/156UI-Verträge, Python-/Privacy-Gates, neuer Originalkandidat mit höherer Version. Produktive Google-Projektkonfiguration und echte Freigabe nur bei tatsächlich vorhandenem Zugang prüfen; aus fehlendem Zugang keinen Erfolg ableiten. Keine produktiven Testtermine, Datenmigration oder automatische Pixelinstallation.
- Ausgangsbefund: Gerätesnapshot1.54.1/90 mit CALENDAR_CONSENT_CANCELLED, calendar_connected/configured/enabled=false. Zum Fingerprint passende historische Originalquelle5bf4e52 separat erhalten; neuere Entwicklung nicht als bereits installiert ausgeben. Baselineprüfung läuft; externer Google-Projektzugang noch nicht vorhanden.
- Phase1 umgesetzt: Google-Intent auch bei nicht-OK-Ergebnis zuerst durch das offizielle SDK auswerten; fehlende Antwort separat, Abbruchtext ohne unbewiesene Nutzerzuschreibung. Technische Unterbrechung eigener wiederholbarer Zustand ohne Rechteverlust. Kalenderfehlerzähler bei8 sättigen und stündlich weiter versuchen, Retry-After beachten; echte Autorisierungssperren bleiben bestehen. Alte fälschliche Abbruchsperre wird einmal still geprüft. Jobfortsetzungen ersetzen keinen laufenden Einmaljob; periodische/Einmal-Futures getrennt. Netzrückkehr plant Kalenderarbeit, ohne neue Dienste/Rechte. Unabhängige Kalender-Erfolgs-/Versuchszeitpunkte; nur bereinigte Einrichtungsschritte/-zeiten/-Rückgabecodes, öffentlicher tatsächlicher Signatur-SHA1 ausschließlich unter Kontodetails. Keine Tokenpersistenz.
- Phase1 tatsächlich PASS:382Python-Tests/0Skips (37 historische schlüsselerzeugende QA-Tests ausgeschlossen),40JVM-Suiten einschließlich2025neuer Wiederanlauf-/Zeit-/Abbruchprüfungen; unveränderter P1-Vertrag899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813,117Tools/156Aktionen. Zehn gezielte Browserfälle PASS, Android-Release- und Instrumentierungs-Java sowie Lint BUILD SUCCESSFUL/42Tasks. Vollständiger Browserlauf und neue native Proben noch laufend/nicht ausgeführt. Keine APK/Signierung/Installation aus diesen Teilnachweisen ableiten.
- Baselinegrenze korrigiert: alter externer run-java.sh war unvollständig; aktueller Repositoryrunner mit vorhandener verifizierter JSON-Abhängigkeit verwendet. Privacy fand21 bereits vorhandene ignorierte Alt-Ausgaben ohne ausreichende Klassifikation (20Bilder/einJSON); sämtliche Originalbytes mit SHA-Rücklesen reversibel außerhalb Checkout isoliert,1081899Bytes erhalten, keine Löschung/Produktivmigration und kein Privacy-Gate abgeschwächt. Source1220/Workspace2289Dateien/0Seeds danach PASS. Technische Logs ausschließlich unter /tmp/calendar-* beziehungsweise bestehendem outputs-Testbereich.
- Google-Projekt autonom gesucht: Repository-/MD-/öffentliche Konfigurationsangaben und gezielte Drive-Metadatensuche ohne Projekt-ID-Treffer. Keine verfügbare Browser-/Google-Cloud-Administrationsverbindung oder konfigurierte GCP-Identität; API-/OAuth-Projektänderung und reale Google-Freigabe weiterhin NOT TESTABLE. Letzter lesender Gerätesnapshot bestätigt weiterhin1.54.1/90; keine Betriebsaktion ausgeführt.
- Phase2 tatsächlich PASS:46/46Browser-Suiten und55synthetische MCP-Szenarien. Kandidat1.54.2/91 aus sauberem, gepushtem Quellcommit21045bd8aca84795a44eaa8aa8ab8948160c479e; Main-/Gradle-SHA747762839020b4ffb0491ac360c3cc054c455245eb4126b0c1e6d4e8882b0c3c. Release/AndroidTest/Lint84Tasks BUILD SUCCESSFUL,0Lintfehler/51Warnungen. Originalsignierung über vorhandenes Werkzeug/Originalschlüssel; APK-SHAaadf07d335fb96a390d9a06bb184c61b6fd09ac0b835d176f8f93e1f875ebfda, Zertifikata939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8, v2/v3/16KiB geprüft. Native Instrumentierung ebenfalls originalsigniert, kein Ersatzschlüssel. Source-Preservation vor beiden Signierungen PASS. Noch keine Pixelinstallation oder Release-/Google-Endabnahme.
- Artefaktprüfer präzisiert: Google-Identity-Abhängigkeiten erzeugen zwei zusätzliche ART-Profile. Alte pauschale Assetprüfung meldete deshalb FAIL trotz420bytegleicher Mainassets. Nur die exakt zwei bekannten Profilpfade werden nach Formatprüfung UND Bytegleichheit zu den tatsächlichen Compiler-Ausgaben akzeptiert; unbekannte Zusätze, geänderte/fehlende Quelldateien und abweichende Profile bleiben FAIL. Sechs synthetische Prüferszenarien PASS; tatsächlicher91-Kandidat mit420Assets/181Profilen/362Bildern und genau zwei bestätigten Compilerprofilen PASS. Keine Privacy-/Signaturprüfung abgeschwächt.
- Native Abnahme auf neuem dediziertem Cloud-AVD GartenpiratCloud/emulator-5582/API30 gestartet; QEMU/AVD/Boot geprüft, nur Originalpaket/Originalinstrumentierung installiert. Bestehender physischer Pixel unberührt. Original90-APK über vorhandenen privaten Drivepfad gefunden und Streaming-Readback36.171.731Bytes erhalten; lokale Materialisierung am32-MiB-Transferlimit abgewiesen, direkter Dateihost durch bestehende Proxyregel403 gesperrt. Kein Umgehen/keine neue Egressregel. Ein tatsächlicher90→91-In-place-Test bleibt ohne Originalbytes vorerst NOT TESTABLE; Kandidaten-Native-/Fresh-/Wiederanlaufprüfung getrennt ausweisen.
- Native Zwischenprüfung: sämtliche fünf neuen Kalender-Recovery-/Fehlerrückgabe-/Rechte-/Zeitpunktproben PASS, bestehende Kalender-ACK-/Konflikt-/410-/Reopen-Proben ebenfalls PASS. Ein vorhandener, bislang nicht tatsächlich ausgeführter Nach-Build90-Test verwendete beim Papierkorbleeren irrtümlich expected_record_hash statt des bestehenden expected_preview_hash/preview_hash-Vertrags und brach mit JSONException ab. Nur diesen Testaufruf an den unveränderten tatsächlichen MCP-Vertrag angepasst; alle Assertions zu keiner Drive-Übertragung, lokalem Original und leerem Archiv erhalten. Kein produktiver Papierkorbcode geändert. Vollständiger Nachlauf mit finaler Instrumentierung erforderlich; erster91-Kandidat bleibt intern und wird nicht ausgeliefert.

## Einheitliche GitHub–Cloud–Drive-Ablage und Aufbewahrung (21.10.2026)

Nutzerauftrag: das zuvor erarbeitete Konzept vollständig und autonom umsetzen. Umfang: geprüfte PR-Übernahme, Originalquellensicherung, administrative Drive-Ordner/Exporte, nachvollziehbare endgültige Altartefaktbereinigung und dauerhafte Zeitpläne. Keine APK-Erstellung/Signierung/Installation und keine operative Daten-/Journal-/Foto-/Rechnungsänderung.

- Verbindliche private Ablage: https://drive.google.com/drive/folders/1-fLRbUEbGK4jQqRyHN07sd26zG-dQ_P0 ; Ordner-IDs/Schutzbereiche/Fristen in `source-lifecycle.json`. 00-START, 10-QUELLCODE (Entwicklung/Ausgeliefert/KI-EXPORT/Alt-Exporte), 20-APK (Aktuell/Rückfallversion/Test/Alt-Versionen), 30-BETRIEB als reiner Ordnerlink und 90-PRÜFPROTOKOLLE. Bestehende Perplexity-/Gemini-/Build-Ordner-IDs und APK-Datei-IDs erhalten. 57 vorhandene APKs eingeordnet: 90 aktuell, 89 geschützt, sechs Testdateien mit 14-Tage-Frist, 49 ersetzte reguläre Alt-APKs. Keine Betriebsdatei verschoben.
- Originalquellen vollständig wiedergewonnen und dauerhaft mit Git-Tags gesichert: `source/v1.54.0-build89` → `68a19c5d49ddc77e94d299eee2cadf1652f9c92a` (1.189 Dateien, Main-/Gradle-Hash `b05723a29302dfc98e8ac6a3aefcbaffd6cb5c0b1c5632fd6889df5761774aa1`); `source/v1.54.1-build90` → `5bf4e521bd1faab7a0095360faafaf7255d0d4d2` (1.203 Dateien, Hash `42c1fd1a43c1e8331b7a34f54cbf7cbb91e0b615d16c64729e62427d689b1052`). Alle Archivdateien gegen Git-Blobs geprüft, Tags unverändert auf GitHub zurückgelesen; tatsächliche vorhandene APK-SHA256 und eingebettete DEX-Quellfingerprints passen. Vollständige Wiederherstellung per `git archive`/CRC/Privacy/Dateizählung/Quellhash tatsächlich PASS für beide Versionen. Kein neuer Build und keine behauptete Pixel-Installation.
- Original89-Quellarchiv zusätzlich privat nach Drive hochgeladen: Datei `14PwvVo4DGTa9r7zh-OYxKucKzjuWxqsh`, 68.542.885 Bytes, lokaler SHA256 `0215daf6bf2ed5a61c1e7b3d5c83758542284982e2680a27faa4f033001413a1`; Original90-ZIP mit bestehender Datei-ID `1C945yeV6Z0SgQQMBqK-GFtJS2a0zrIFW` samt komplettem eingefrorenem 22-Dateien-Export unverändert nach Ausgeliefert90 verschoben. Große Drive-ZIPs: vollständige Streamgröße und Metadaten bestätigt, unabhängiger Remote-SHA256 wegen 32-MiB-Materialisierungslimit NICHT ausgeführt; vollständige Originalquellbytes dagegen über die bestätigten Git-Trees gesichert. Diese Nachweise nicht gleichsetzen.
- `tools/source_lifecycle.py`: aus bestätigtem sauberem Git-Commit exportieren, gemeinsame UTF-8-Moduldateien inklusive BOM/CRLF/Leerdateien exakt rekonstruieren, Originalbinärressourcen inventarisieren, unveränderliche Generationen erst nach Prüfung veröffentlichen. Bereinigungsplan nur bei vollständigem Inventar, explizitem Register, unveränderten Metadaten, bestätigtem Ersatz, abgelaufener Frist und erneutem Einzelabgleich; unbekannte/aktive/geschützte Dateien sowie Betriebsordner blockieren. Lokale Bereinigung nur registrierte, hashgleiche öffentliche Altartefakte bzw. abgeschlossene synthetische Ausgaben in expliziten Wurzeln; Pfad-/Symlinkschutz und Privacy-Gate. Externe Quellkopien nur im verwalteten `hausgarten-source-exports`-Cache. SDK-/Abhängigkeitscaches erhalten.
- Alte getrackte Build79-APK aus `downloads/` entfernt; Datei bleibt in geschützter Git-Historie wiederherstellbar. Aktive Downloadübersicht und Prüfsummen zeigen ausschließlich89/90. Regeln auf begrenzte Drive-Aufbewahrung plus dauerhafte Original-Git-Tags präzisiert; kein History-Rewrite. `AKTUELLER-STAND.json` trennt bestätigte historische Originalquelle90, beobachtete Installation89 und neueren, weiterhin ungepackten Entwicklungsstand (Main-/Gradle unverändert `aa2981fe1e6cd251eb9fb5e590c5292b0841d7f0037b46ac594ed6bdc46d6c09`).
- **Tatsächliche Endprüfung:** vollständiger finaler Python-Lauf **382 PASS / 0 Skips**, 37 historische schlüsselerzeugende QA-Tests ausdrücklich ausgeschlossen. Darin 22 gezielte Lifecycle-/Sicherheitsprüfungen. Vorherige Zwischenläufe 376 und381 PASS; Source-/Workspace-Privacy im Endlauf 1.218 Source-Dateien / 4.818 Workspace-Dateien PASS. UI-/MCP-Inventar und Vertragsgate unverändert PASS (117 Tools, 156 Aktionen, `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`). Keine neuen JVM-/Browser-/Android-Buildläufe behauptet; Main-/Gradle-Dateien unverändert. Zusätzlich Originalquelle88 aus dem damaligen Original-ZIP als `source/v1.53.1-build88` mit Commit `dc394b5796fbcb268533a8d8951fe05a829956df` gesichert (1.176 Dateien, Hash `1dec4e6146dbb5decdba3d8775664b777f1cbe7cd27f44964aae4de4824f935f`). Wiederherstellung **aller drei** ursprünglichen Quellstände88/89/90 tatsächlich PASS.
- **Endgültige administrative Bereinigung tatsächlich ausgeführt:** 49 reguläre alte Drive-APKs nach vollständigem Inventar, Register/Policy-Plan und jeweils unmittelbarer erneuter Einzelmetadatenprüfung dauerhaft per files.delete entfernt; **646.182.805 Bytes**. 49 erfolgreiche Antworten plus vollständige leere Abschlussliste des Alt-APK-Ordners bestätigt. Leeren alten APK-Backups-Ordner anschließend ebenfalls entfernt; produktiver Datenordner selbst, Journale, Fotos und Sicherungen bleiben unverändert. Zusätzliche fünf lokale öffentliche Alt-APKs aus outputs nach tatsächlicher aapt-Paket-/Buildprüfung, SHA256, Aufbewahrung und Artifact-Privacy entfernt; 90/89 und Test88 erhalten. Kein globales Papierkorbleeren und keine Löschung unbekannter Dateien.
- **Cloud-Cache geordnet:** 52 bekannte öffentliche temporäre Exportdateien (531.574.770 Bytes) nach Artifact-Privacy und anschließendem Bytevergleich in den explizit verwalteten externen Cache `/tmp/hausgarten-source-exports/legacy-20261021` verschoben. 7-Tage-Frist beginnt mit dieser belegten Ersetzung; Original-ZIPs89/90 zusätzlich geschützt. Keine pauschale /tmp-Bereinigung, kein Verlust ungesicherter Quellen. Lokale und Remote-Register enthalten ausschließlich technische Metadaten/Hashes; operative Daten bleiben außerhalb dieses Ablaufs.
- **Dauerhafte Scheduler tatsächlich angelegt und aktiviert zurückgelesen:** Europe/Vienna, flexible Zeitfenster: täglich ab22.10.2026 morgens08:00 Quellstand/Aktualität (`6ad8f3798fc081919a261c5eb3981ad1`); montags ab26.10.2026 um10:00 begrenzte endgültige Bereinigung (`6ad8f37b548c8191b18d43b443aae6d3`); monatlich am1. ab01.11.2026 mittags12:00 Wiederherstellungsprobe (`6ad8f37da1d88191b7aa7fbf09473760`). Erfolgreiche App-Leseproben vor Einrichtung; zukünftige zeitgesteuerte Ausführung noch NOT EXECUTED. Die manuelle Bestands-, Plan-, Lösch- und Wiederherstellungsprobe ist dagegen tatsächlich ausgeführt. Fehlende Cloud-/App-Verfügbarkeit bleibt blockierend sichtbar, kein erfundener Dauerbetrieb einer VM.
- **Publikationsverfahren ohne Selbstreferenz:** Nach der geprüften PR-Übernahme wird der genaue abschließende main-Commit mit `tools/source_lifecycle.py export` eingefroren. Der tatsächlich veröffentlichte Commit, sämtliche Upload-/Rücklesebelege und der gemeinsame Gemini-/Perplexity-Ordnerlink stehen anschließend im technischen Drive-Register `1lxLiMzK4NT7-ueLJvpsDU1gwbaxx610C` und im Generationsmanifest. Diese commitabhängigen Belege werden außerhalb des eingefrorenen Quellarchives nachgeführt; nach der abschließenden Publikation keine weitere Quelländerung ohne neue Sicherung/Generation. Wegweiser erst nach vollständiger Verifikation wechseln. Große ZIPs bleiben ausdrücklich STREAM_SIZE_ONLY statt erfundener Remote-SHA256-Prüfung.
- **Evidenz:** `outputs/qa-organization-original-sources.json`, `qa-organization-restore.json`, `qa-organization-cleanup.json`, `qa-organization-local-cleanup.json`, `qa-organization-local-export-cache.json`, abschließende Export-/Drive-/PR-/Schedulerbelege und `outputs/QA-Python-results.json`. Source-/Workspace-/Artifact-/UI-/Vertragsgates erhalten. Keine neue APK/Version/Berechtigung, Originalsignierung oder Installation. Pixel-Abnahme und fachlicher Live-Sync NOT EXECUTED.

## Cloud-Quellstand gesichert, GitHub-Regeln abgeglichen, Journalpaar geprüft (21.10.2026)

Nutzerauftrag nach der reinen Bestandsanalyse: aktuellen Cloud-Quellstand dauerhaft sichern, neue GitHub-Regeln abgleichen und die Journal-Namenskollision prüfen. Kriterien: vollständige bytegleiche Sicherung der bisherigen Originalarbeit im bestehenden privaten Repository, tatsächlicher Remote-Readback, keine verlorenen Optimierungen oder schwächeren Gates, keine Betriebsdaten in Git und keine produktive Drive-Schreibaktion. Keine APK, Signierung, Versionserhöhung oder Installation.

- **Originalarbeit zuerst gesichert:** alle 133 bisherigen Änderungen (87 geänderte und 46 neue Quelldateien) einzeln anhand Pfad, Größe und SHA256 geprüft und explizit aufgenommen; kein pauschales `git add .`. Sicherungscommit `74c7af719fcd5d3203bcaee77b0496bfcf0920fa` auf `backup/cloud-source-20261021` ins bestehende private Repository gepusht. Exakter Commit per `git ls-remote` und unabhängigem GitHub-Commit-Readback bestätigt: https://github.com/mihalhafner-collab/Gartenpirat-/commit/74c7af719fcd5d3203bcaee77b0496bfcf0920fa . Alle 133 Originaldateien anschließend gegen die Commitbytes verglichen. Source-Privacy 1.211 Dateien / Workspace-Privacy 4.805 Dateien PASS, 0 Betriebsdatenseeds. Keine Git-Historie umgeschrieben und kein Push auf main.
- **Regeln vollständig übernommen:** tatsächlichen GitHub-main `e13ba07bf990fea7e712b888a5893b83fdfc36b5` nach vorheriger Sicherung in denselben Arbeitsbranch integriert. Veralteter lokaler origin/main-Verweis durch Fetch aktualisiert. Neue AGENTS-Sicherungsregeln, Zentralauftrag/README, `tools/source_preservation.py`, Signier-CLI-Sperre und synthetische Prüfungen erhalten. Konflikte ausschließlich in `CODEX-STATUS.md` und `tests/package_source_copy.py`: beide Statushistorien bewahrt; Exporter verbindet die neue Clean-/Remote-Prüfung und gepinnte Git-Originalbytes mit bisherigen deterministischen ZIPs, komprimierten Originalbildern, Modus-/CRC-/Byte-/Privacy-Prüfungen und explizitem `--reuse-existing`. Committete Symlinks/Submodule abweisen; Git-Unterprozesse erhalten keine Originalsignierungsvariablen. Kein Dirty-/Offline-/Skip-Ausweg ergänzt.
- **Eindeutiger Einstieg:** `AKTUELLER-STAND.json` verweist für laufende Entwicklung auf diesen Sicherungsbranch und den bestätigten Originalcheckpoint; Main-/Gradle-Fingerprint unverändert `aa2981fe1e6cd251eb9fb5e590c5292b0841d7f0037b46ac594ed6bdc46d6c09` (539 Dateien). Der Entwicklungsstand enthält Nach-Build90-Änderungen; keine APK dafür gebaut. `verified_release_source` bleibt null, zuletzt beobachtete Installation89 und deren separate Provenienz bleiben ausdrücklich unbestätigt. Keine falsche Release-Versionsmarke erzeugt.
- **Journalpaar tatsächlich lesend verglichen:** neuere Datei 612.709 Bytes / 469 eindeutige Aktionen, ältere 26.521 Bytes / 33 eindeutige Aktionen. Alle 33 älteren Aktionen nach stabiler JSON-/Zahlennormalisierung byteinhaltlich identisch im neueren Journal enthalten; 436 ausschließlich neue Aktionen, 0 ausschließlich alte Aktionen, 0 kollidierende Aktionskennungen, 0 ungültige Zeilen oder interne Wiederholungen. Metadaten vor/nach der Lesung unverändert. Der bestehende Union-Pfad dedupliziert diese identischen Kennungen; dieses Paar liefert keinen `ACTION_ID_CONFLICT`. Keine Löschung, Zusammenführung oder Quittierung auf Drive ausgeführt. Server-Veröffentlichungsquittungen und Pixel-Laufzeit nicht gelesen; keine vollständige Abgleich- oder Löschfreigabe daraus abgeleitet.
- **Privatsphäre:** Journal-Rohbytes ausschließlich in temporären Downloads außerhalb des Checkouts gelesen, anschließend beide eigenen temporären Kopien entfernt. Im Repository ausschließlich Zählwerte und SHA256 in `docs/verification/journal-collision-audit-2026-10-21.json`; keine Kunden, Tätigkeiten, Preise, Aktionskennungen oder Rohdatensätze übernommen. Bekannte sensible Altobjekte in Git-Historie bleiben ein eigener Sicherheitsauftrag; kein automatisches Rewrite.
- **Abnahme tatsächlich ausgeführt:** 87 gezielte synthetische Tests PASS: 13 Quellcodesicherung, 43 Originalsignier-CLI, 18 Exportübertragung und 13 Quellprivacy. Anschließend vollständiger Python-Endlauf **360 PASS / 0 Skips**, 37 historische schlüsselerzeugende QA-Tests ausdrücklich ausgeschlossen und erhalten. Source-/Workspace-Privacy vor und nach dem Gesamtlauf PASS; keine neue APK, daher neue APK-Abnahme NOT TESTABLE. Zwei zusätzliche Integrationsproben sichern Wiederverwendung desselben gepinnten Archivs samt ausführbarem Git-Dateimodus und Ablehnung committeter Symlinks; bestehende Probe mit nachträglich gelöschter/veränderter Arbeitskopie erhalten. Mock der Artefaktprüfung auf den tatsächlichen Aufrufort im gemeinsamen ZIP-Helfer angepasst; keine Schutzassertion entfernt. UI-Review-/MCP-Inventar PASS (117 Tools, 52 lesend / 65 schreibend, 156 UI-Aktionen), Vertrag `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813` unverändert; 370 generierte Pflanzenressourcen PASS. Kein erneuter JVM-/Browser-/Android-Lauf behauptet: alle 539 Main-/Gradle-Dateien bytegleich zum gesicherten, zuvor getesteten Entwicklungsstand.
- **Integrierten Quellstand bestätigt:** Mergecommit `6aee3aba3b5e560830587654a394a6c6ecd217d9` mit beiden Eltern (Original-Cloud-Checkpoint und neuer GitHub-main) auf denselben Sicherungsbranch gepusht. Exakter Remote-Ref und `tools/source_preservation.py` auf sauberem Checkout **PASS**; kompletter Source-Privacy-Nachlauf auf bereinigtem Git-Index und beide technischen Zusatzberichte ebenfalls PASS. Abschlussdokumentation separat versioniert; der jeweils letzte Branchkopf und sein exakter Clean-/Remote-Nachweis stehen in `outputs/qa-cloud-preservation-final.json`. Unveränderter main, kein Force-Push/History-Rewrite, keine Drive- oder Pixel-Schreibaktion.
- **Belege:** `outputs/qa-cloud-preservation-python.json`, `outputs/QA-Python-results.json`, `outputs/qa-cloud-preservation-merge.json`, `outputs/qa-cloud-preservation-final.json` und der versionierte, ausschließlich technische Journalbefund. Original89-/90-APKs unverändert; physischer Pixel, reale Google-Freigabe, Installation und vollständiger produktiver Abgleich **NOT EXECUTED**.

## Mobilfunkdurchsatz und unmittelbarer Abgleich (21.10.2026)

**Auftrag / vorab festgelegte Kriterien:** Bestehende Mobilfunkverbindung auf nutzbaren Durchsatz, kurze Latenz, Parallelität, zeitnahen Änderungsabgleich und Zuverlässigkeit optimieren. **Datenvolumen ist ausdrücklich kein Optimierungsziel.** Bis zu drei unabhängige Dateiübertragungen gleichzeitig; vollständiger Readback jeder Datei, begrenzter RAM-/HTTP-Druck, unveränderte fachliche Journal-/Nummern-/Veröffentlichungs-/Löschreihenfolge. Künstliche 500-/1500-ms-Verzögerungen entfernen, Anforderungen nicht immer wieder nach hinten schieben, neue Änderungen nach laufendem Abgleich sofort weiterverarbeiten. Undo-Fristen, Serverwartezeiten, Freigaben/Opt-in und Android-Systemgrenzen erhalten. Nur synthetische App-Tests; keine APK/Signierung/Installation oder Betriebsdatenaktion.

### Implementierte Transport- und Ablaufänderungen

- **Dateien parallel, fachliche Änderungen seriell:** `DriveTransferPool.java` und `DriveMediaBatch.java` übertragen bis zu drei unabhängige Originale gleichzeitig. Jede Datei nutzt ihre eigene fortsetzbare Session; Blöcke **innerhalb** einer Session bleiben geordnet. Ordneranlage erfolgt einmal vor den Workern; Originale/Metadaten und Vorschaudateien werden über alle Referenzen dedupliziert. Erst nach vollständigen Original-Rücklesungen folgen Vorschaudateien. Der Koordinator allein verarbeitet danach Journale, Nummern-CAS, Veröffentlichung, SQLite-Commit, Quittierung und Bereinigung. Keine Geschäftsänderung im Worker, keine vorgezogene Bestätigung.
- **Größere Blöcke / begrenzter Druck:** `DriveRestClient.java` nutzt unter 1 MiB weiterhin 256-KiB-Blöcke, ab 1 MiB 1 MiB, ab 8 MiB **4 MiB**. Im 8-MiB-Test **8 → 2 Daten-PUTs** gegenüber dem unmittelbar vorherigen Stand, weiterhin vollständiger Readback; ältere 256-KiB-Fortsetzungspunkte bleiben gültig. Vollständig gelesene und geschlossene Antwortstreams werden für die Wiederverwendung durch Androids HTTP-/TLS-Verbindungspool freigegeben; `disconnect()` bleibt für Fehler und Abbrüche. HTTPS-Zielprüfung/Redirects/Timeouts unverändert; synthetischer Connection-Opener prüft Ressourcen ohne globale URL-Hooks oder echten Netzverkehr. Appweit höchstens sechs parallele Drive-HTTP-Anfragen, faire/cancelbare Warteschlange, Freigabe der Slots auch bei Fehlern. 429/5xx-Wartezeiten sperren neue Anfragen der verbundenen Workergruppe bereits vor Freigabe des HTTP-Slots; keine blinden automatischen POST-/CAS-Retries.
- **Abbruch / Lebensdauer:** Beim Abbruch Futures stoppen, ausschließlich die eigenen aktiven HTTP-Verbindungen trennen und sämtliche Worker beenden lassen, bevor Aufrufer/SQLite-Store geschlossen werden. Unterbrechungsflag und Sessions bleiben erhalten. Fehlgeschlagene Rücklesung bestätigt die betroffene Datei nicht; unabhängige korrekte Dateien bleiben verwendbar. Maximal drei Worker-Inventare gleichzeitig; Cache-Zugriff für parallele Leser synchronisiert, RAM-Cache weiter 4 MiB / 256 Einträge / 512 KiB pro Datei.
- **Kalte Veröffentlichungsketten parallel lesen:** `DrivePrimaryState.Storage.readCommits` erlaubt unabhängige Downloads in Wellen von drei kleinen Belegen. `DriveRestClient.readCommitFiles` beschränkt die jeweilige kleine Antwort auf die beobachtete Größe (höchstens 512 KiB je Datei); große/unbekannte Größen einzeln im bisherigen Maximalumfang. Sämtliche Namen/Hashes/Formate/Belege und die vollständige Herausgeber-/Sequenz-/Elternkette werden weiterhin geprüft; Besucher/Validierung und Anwendung laufen auf dem ursprünglichen Aufrufer in der ursprünglichen Reihenfolge. Der Provider- und synthetische Storage-Pfad bleiben seriell. Keine Kette verkürzt und keine Herkunftsprüfung ersetzt.
- **Lokale Änderungen sofort:** `SyncWakeup.Window`, `MainActivity`, `DriveSyncCoordinator` und `DriveSyncJob` entfernen künstliche 500-/1500-ms-Mindestwartezeiten. Die früheste Anforderung gewinnt; spätere Speicherereignisse verschieben sie nicht immer wieder. Undo-Frist und bestehendes `drive_next_retry` bleiben zeitliche Untergrenzen. Dauerhafter Job als Prozessabbruch-Fallback, Android darf ihn weiterhin systembedingt verzögern. Neue Anforderungen während eines laufenden Abgleichs werden über Generation und bestehenden Queue-Vergleich weitergeführt; sofortiger Folgeabschnitt statt zusätzlicher 1,5 Sekunden Pause. Android-Einmaljobs werden während ihrer Laufzeit nicht durch neue Anforderungen ersetzt/abgebrochen; deren frühester Folgeauftrag wird nach Jobende weitergegeben, auch wenn ein wartender Future vor Ausführung beendet wird. Periodische und Einmaljobs haben eigene Future-Zuordnungen. Interne Fortsetzungen zählen nicht als neue Benutzereingaben; noch laufende Undo-Fristen werden nach Zwischenabgleichen erneut eingeplant, ohne schnelle Abgleichschleife.
- **Metadatenprüfung bei aktiver Nutzung alle 15 Sekunden:** Hauptaktivität und der **bereits vom Nutzer aktivierte** MCP-Vordergrunddienst starten eine kleine Versions-/ID-/Namens-/MIME-/MD5-/Größenprüfung. Unveränderte Metadaten erzeugen keine neue Cloudquittung und keinen künstlich frischen Erfolgszeitpunkt. Lokale offene Änderungen, manuelle Anforderung, fehlende Medienbestätigung, Fehler oder geänderte/fehlende Versionsmetadaten erzwingen den vollständigen Abgleich; spätestens nach 15 Minuten bleibt die vollständige Prüfung fällig. Der gemerkte Fingerprint stammt **vor** dem Lauf, damit zwischenzeitliche Fremdänderungen nicht durch spätes Speichern eines bereits veränderten Inventars verdeckt werden. Eigene Veröffentlichungen können deshalb eine zusätzliche vollständige Folgerunde auslösen; Sicherheit vor falschem No-op.
- **Netzrückkehr / Offline:** Bestehende `ACCESS_NETWORK_STATE`-Berechtigung verwenden, keine neue Berechtigung. Nach bestätigter Internetverfügbarkeit/Netzwechsel sofort anfordern, keine wiederholten Vollabgleiche bei jeder Bandbreitenmeldung. Ohne validiertes Internet werden automatische Versuche nicht ausgeführt und verbrauchen das App-Retry-Budget nicht; der Systemjob bleibt wiederholbar. Probe-Fehler verwenden dieselbe persistierte Fehler-/Retry-After-Policy wie vollständige Läufe. Auth-/Review-/Retry-Grenzen bleiben erhalten. Keine neue permanente Dienstaktivierung, kein Wakelock, keine VPN-/DNS-/Geräte-/Provideränderung.
- **Bedienzugriffe neben Hintergrund-I/O:** Direkte hashgeprüfte Foto-/Dokument-/Vorschaulesungen warten auf keine vollständige Hintergrund-Netzwerksperre. Beim lokalen Speichern eines nachgeladenen Fotos unter SQLite-Schreibtransaktion erneut prüfen, dass eine aktive/archivierte oder ausstehende Referenz besteht: keine Wiederanlage nach gleichzeitig abgeschlossenem Purge. Provider-Rename-/Löschpfade bleiben unter der bisherigen Sperre. Hash-/Byte-/Dateitypprüfungen und Regeln zu gemeinsam verwendeten Dateien erhalten.
- **Faire Medienwahl / kurze Abschnitte bei Fachänderungen:** `SyncMediaPlan` verarbeitet im Bulk bis zu zwölf unterschiedliche Originale innerhalb eines Fensters von 32 MiB Originalbytes je Abschnitt (Readback-/HTTP-Kopien und übriger Appheap kommen hinzu). Bei gleichzeitig offenen Kunden-/Arbeits-/anderen Fachänderungen höchstens eine kurze Welle mit drei Dateien. Zurückgestellte Dateien erhalten in der nächsten Runde Vorrang, auch wenn ein großes Original zuvor wegen des RAM-Fensters übersprungen wurde. Technischer Cursor wird nach abgeschlossener Workerbarriere weitergesetzt; er ist keine Quittung. Fehlerhafte Originale können spätere intakte Dateien nicht dauerhaft besetzen. Diese Grenzen begrenzen Speicher und Antwortzeit, **nicht** verbrauchbares Datenvolumen; kein Tages-/Mobilfunklimit und keine neue Bildkompression.
- **Codex-Dateiexport:** `tools/drive_source_transfer.py` und dauerhafte Anweisung in `AGENTS.md` liefern bis zu drei überlappende Artefaktketten (einmaliger Upload → tatsächlicher Readback → Beleg), jeweils sofort nach freiem Platz. Innerhalb der Payload-Phase größte Dateien zuerst, damit das große ZIP nicht hinter vielen kleinen Einzelaufrufen wartet. Inventar/Prüfsummen erst nach verifizierten Payloads, Startdatei zuletzt; Abschluss-/autorisierte Bereinigungsbarriere bleibt. Unklare Uploadantwort zuerst über frische vollständige Ordnerliste abgleichen, doppelte Namen sperren. Keine Credentials/Netz-API im lokalen Helfer und keine Behauptung, den externen Google-/MCP-Server verändert zu haben. Keine tatsächlichen neuen Drive-Uploads/Löschungen in diesem Auftrag.

### Tatsächliche Prüfungen und Grenzen

| Prüfung | Ergebnis |
|---|---|
| Unveränderter Ausgangslauf | **38/38 JVM-Suiten PASS**, Main-/Gradle-Fingerprint `24109ed5f22b241053034ae3e43867e706e13fb01b35ba3b2d86e0f43d2c8b99` |
| Abschließende JVM-Suiten | **39/39 PASS**, einschließlich **17 neuer `DriveThroughputTests`** und 13 erhaltener Performance-/Integritätsprüfungen |
| Konkret nachgewiesene Parallelität | Drei echte Aufrufe des produktiven Medien-Batchpfads über synthetische HTTPS-Grenze überlappen; sechs separate Leseanfragen erreichen/überschreiten das globale Limit korrekt. Drei kalte Belegdownloads überlappen; identische vollständige Kette ergibt denselben Kopf, geänderter Beleg weiterhin abgewiesen |
| Fehler-/Abbruch-/Reihenfolgefälle | Einzelner korrupter Readback; gemeinsame Originale/Thumbnails; alle Referenzen; kein Vorschaustart vor Originalbarriere; laufende und wartende Uploads abgebrochen, drei Sessions erhalten, keine Worker nach Rückkehr; Slots freigegeben; Retry-After stoppt weitere Workerrequests; Keep-alive-/Payload-Limit-/Streamschluss und HTTPS-Zielschutz, RAM, Undo, faire Auswahl auch bei zwei großen Dateien, Nutzeränderungspriorität und keine erfundene Cloudbestätigung: **PASS** |
| Python-Gesamtlauf | **347/347 PASS**, 0 Skips, 37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen/erhalten; vorheriger Zwischenlauf 346/346 PASS |
| Exportwerkzeug | **18/18 PASS** im Gesamtlauf: zusätzliche Pipeline-/Barrieren-/Largest-first-Prüfungen; bestehende Metadaten-/Hash-/Privacy-/ZIP-/No-op-/Unklarheitsprüfungen erhalten |
| Browser/JS | **9/9 PASS**: sync-responsiveness, sync-customer-gallery, sync-trash, customer-photos, work-entry-photo-failure, field-ui, status-collisions-gestures, runtime-compat, webview-syntax; zusätzlich **ui-preview PASS** (insgesamt 10 Suiten) |
| Android-Kompilierung / Instrumentierungskompilierung / Lint | **PASS nach letzter Javaänderung**, 42 Tasks / 1 min 45 s; zwei generierte Release-BuildConfig-Fingerprints stimmen mit dem endgültigen Maincode überein. Keine APK-Aufgabe. Vorherige Zwischenläufe bestanden. Lint **0 Fehler / 51 Warnungen**; eine zusätzliche HardwareIds-Warnung durch Nutzung der unveränderten bestehenden Gerätekennung im Poll, keine neue Kennung/Berechtigung oder unterdrückte Regel |
| MCP-/UI-Vertrag, Pflanzenassets | **PASS**: unverändert **117 Tools / 156 Aktionen / `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`**; 420 Main-Assets |
| Source-/Workspace-/Artifact-Privacy | Python-Endlauf **PASS**: 1.211 Source-Dateien / 4.833 Workspace-Dateien einschließlich ignored/untracked, 0 Betriebsdatenseeds; bestehende APK90 unverändert privacygeprüft. Finaler verpflichtender Native-Prebuild: Source **1.211** und Workspace **4.836** Dateien PASS; aktuelle Hash-/Count-Evidenz im Ergebnisbericht |
| Physische Mobilfunk-/Pixelmessung, echte Google-Upload-/Netzwechsel-/Doze-/Geräteabnahme | **NOT EXECUTED**; keine erfundenen Mbit/s, Laufzeitgewinne oder Sofort-Push-Garantie |

**Bewusst angepasste Erwartungen:** `DrivePerformanceTests` erwartet beim 8-MiB-Fall zwei statt acht PUTs; vollständige Rücklesung/Originalbytegleichheit, kleine 600-kB-Datei, Unterbrechung und alte Checkpoints bleiben geprüft. `SyncDeletionTests` erwartet zwölf statt vier 2-MiB-Dateien bzw. sieben statt einer bei einem 20-MiB-Original im neuen 32-MiB-Fenster; Deduplizierung, Größen-/Grenz- und sämtliche fachlichen Lösch-/Schutzprüfungen bleiben erhalten. Keine Schutzassertion entfernt. Der erste neue JVM-Lauf stoppte korrekt am noch alten UI-Quellreview-Fingerprint; nach tatsächlicher Review der reinen MainActivity-Scheduler-/Netzcallbackänderung **nur dessen SHA-Feld** in `ui-mcp-actions.json` aktualisiert. Alle 156 semantischen Aktionsdatensätze und MCP-Zuordnungen unverändert; Gate unverändert streng. Neuer Datei-SHA des Reviewkatalogs: `3ae96ae9fb6dfa2f1de2bb975121bec5166ad47b6615762f8bc098e31b293f66`.

**Geänderte Appdateien:** `DriveRestClient`, `DriveDirectFiles`, `DrivePrimaryState`, `DriveSyncCoordinator`, `DriveSyncJob`, `MainActivity`, `PixelTunnelService`, `SyncMediaPlan`; neu `DriveTransferPool`, `DriveMediaBatch`, `SyncWakeup`; ausschließlich Reviewfingerprint in `assets/ui-mcp-actions.json`. Test-/Werkzeugänderungen: neue `DriveThroughputTests`, bestehende `DrivePerformanceTests` und `SyncDeletionTests`, Java-Runner, Exporthelfer/-tests, `AGENTS.md` und dieser Status. Sämtliche bereits vorhandenen uncommitteten Arbeiten erhalten.

**Auslieferungsstand:** Weiterhin Version **1.54.1/90**, Paket `at.hausgarten.service`, Berechtigungen, DB-/Snapshot-/Drive-/Journalformate und IDs/Relationen unverändert. 538 Main-Dateien plus Gradle (539 Fingerprint-Dateien); Main-Assets unverändert 420. **Keine neue APK, keine Versionserhöhung, Signierung, Installation, Konten-/Freigabe-/Betriebsdatenänderung, Commit oder Push.** Die vorhandenen Original-APKs89/90 bleiben bytegleich. Optimierungen sind gesonderte Nach-Build90-Quelländerungen und weder im signierten APK90 noch im exakt eingefrorenen Perplexity-Build90-Export enthalten; keine bereits aktive Pixeloptimierung behauptet. Ohne aktive App bzw. bereits aktivierten MCP-Dienst gelten weiterhin Androids Hintergrundgrenzen und der bestehende Systemjob; ein garantierter sofortiger externer Drive-Push würde zusätzliche Infrastruktur benötigen und wurde nicht erfunden.

**Evidenz:** `outputs/qa-throughput-before.json`, `qa-throughput.json`, `qa-throughput-python.json`, `qa-throughput-browser.json`, `qa-throughput-contract.json`, `outputs/integration-java/results.json` und `DriveThroughputTests.log` / `DrivePerformanceTests.log`; Ausgang `/tmp/throughput-baseline-jvm.log`, Endläufe `/tmp/throughput-jvm.log`, `/tmp/throughput-python-final.log`, `/tmp/throughput-android-final.log`, `/tmp/throughput-browser.log`, `/tmp/throughput-preview.log`. Endgültiger Main-/Gradle-Quellfingerprint **`aa2981fe1e6cd251eb9fb5e590c5292b0841d7f0037b46ac594ed6bdc46d6c09`**, identisch mit beiden tatsächlich generierten Release-BuildConfigs; finale Privacyzählungen im Ergebnisbericht. Schema-/Manifest-/Gradle-/PixelLocalStore-Einzelhashes bleiben gegenüber dem Ausgang identisch.

## Drive-Geschwindigkeit: App und Codex-Dateiexport (21.10.2026)

**Auftrag und Abnahmekriterien vor Umsetzung:** Nutzer bestätigt beide Wege: Pixel-App ↔ Drive sowie Codex ↔ Drive-Dateiexport. Synthetischer 8-MiB-Upload höchstens 8 statt 32 Daten-PUTs; Resume/Unterbrechung/Readback unverändert geschützt. Hashadressierte Veröffentlichungen nur bei unveränderten frisch gelesenen Metadaten wiederverwenden, Cache begrenzen. Codex überspringt nur durch vorhandene tatsächliche Prüfbelege und frische vollständige Ordner-Metadaten zugeordnete identische Exporte; unklare Uploadantwort vor Wiederholung abgleichen. Deterministische, bytegenaue Original-ZIPs. Keine Änderung an Vertrag/Formaten/IDs/Berechtigungen/Version, keine APK/Signierung/Installation, ausschließlich synthetische App-Tests.

**Implementiert:**

- `DriveRestClient.java`: ab 1 MiB Uploadgröße 1-MiB-Blöcke statt 256 KiB; kleine Dateien bleiben im bestehenden Raster. Upload-Session/Schlüssel unverändert, auch ältere 256-KiB-Zwischenstände fortsetzbar. Abschluss weiterhin erst nach vollständigem frischem Server-Rücklesen; kein Weglassen von SHA-/Bytevergleich, ETag-/CAS-, Abbruch-, Auth-/429-/Retry- oder Löschschutz. Token weiterhin pro HTTP-Anfrage aus AccountManager, keine neue Speicherung.
- `DriveRestClient.java` / `DriveDirectFiles.java`: Wiederverwendung ausschließlich hashadressierter Commit-/State-/Mobile-JSON-Dateien innerhalb derselben Client-/Sync-Instanz. Erst SHA256 gegen Dateiname prüfen; Schlüssel bindet Datei-ID, Name, Version, MD5 und Größe. Nach `refresh()` stets neue vollständige Ordnerliste samt neuem ID-Index; veränderte/unvollständige Metadaten führen zu neuem Download. Cache nur RAM, LRU höchstens 4 MiB / 256 Einträge / 512 KiB je Datei, defensive Bytekopien und weiterhin Größenlimit. Veränderlicher Legacy-Gesamtstand, Nummern/CAS, Fotos/PDFs, finale Uploadbestätigung und Löschprüfungen bleiben frisch. Keine persistierten Geschäftsdateikopien oder übersprungenen Ketten-/Herausgeber-/Forkprüfungen.
- Neu `tools/drive_source_transfer.py`: auf ZIP-/UTF-8-TXT-/JSON-Exporte anwendbarer inkrementeller Plan, frische vollständige Ordnerliste statt Metadaten-Einzelabfragen, Wiederverwendung anhand tatsächlicher vorheriger Readback-Belege und identischer beobachteter Metadaten. Version/MD5 einbeziehen, soweit geliefert; der Connector liefert hier nur ID/Name/MIME/Größe/Änderungszeit/Freigabe. Deshalb ausdrücklich **Wiederverwendung früherer Prüfung mit unveränderten Metadaten, kein neuer Remote-Bytehash**. Unbekannte/veränderte Dateien erst prüfen, doppelte Namen sperren, unklare POST-Antwort erst über neue Ordnerliste abgleichen. Unvollständige Inventare fail-closed. Keine automatische Löschung, Freigabeänderung, Kontozugriffe oder Credentialverwaltung im Werkzeug.
- `tests/package_source_copy.py`: bestehender Originalquellcode-Export nutzt denselben Helfer; deterministische ZIP-Reihenfolge/Zeit/Modi, bereits komprimierte öffentliche Bilder ohne erneute Deflate-Kompression. Optional `--reuse-existing` nur nach exakter Inventar-/Originalbyte-/Modus-/CRC-/Privacy-Prüfung. Source-/Workspace-/Artifact-Gates bleiben verpflichtend. ZIP-Rücklesen prüft die CRC mit; kein doppelter `testzip()`-Durchlauf. Schutz gegen Pfad-/Symlink-Ausbruch erhalten/ergänzt.
- `AGENTS.md`: dieser Ablauf ist für künftig ausdrücklich beauftragte Quellcode-Transfers dokumentiert. Prüfbelege bleiben außerhalb des Exportordners; kein neuer operativer Server, Desktop-Pfad, SDK, Schlüssel oder Cloud-Backend behauptet.

**Gemessen:** Im synthetischen 8-MiB-Test **32 → 8 Daten-PUTs (75 % weniger)**, weiterhin ein vollständiges Server-Rücklesen. Zwei Lesungen einer unveränderten Veröffentlichung über zwei frische Inventare benötigen **2 → 1 Body-GET**. Echte Perplexity-Ordnerliste einmal über den Google-Drive-Skill gelesen und mit den tatsächlichen vorherigen Exportbelegen abgeglichen: **22/22 bestehende Dateien wiederverwendbar, 0 Uploads / 0 Uploadbytes / 0 Remoteänderungen**. 22 Einzel-Metadatenanfragen entfallen zugunsten einer Ordnerabfrage. Vorherige Prüfqualität ausdrücklich erhalten: 21 TXT/JSON byteweise SHA256-geprüft; großes ZIP nur bestätigte vollständige Streamgröße, kein neuer/erfundener Remote-SHA. Keine gemessene Pixel-/Google-Laufzeitbeschleunigung behauptet.

| Tatsächlich ausgeführt | Ergebnis |
|---|---|
| JVM-Ausgangslauf, unveränderter Code | **37/37 Suiten PASS** |
| JVM-Endlauf, automatische Aufnahme der neuen `DrivePerformanceTests.java` | **38/38 Suiten PASS**, davon 13 neue Performance-/Schutzprüfungen: große Uploads, Prozessverlust, alter Zwischenstand, fehlerhafte Range/Readback, Metadatenänderung, Cachegrenzen, gelöschte Dateien, veränderlicher Zustand, frischer Fork |
| Neue `tests/test_drive_source_transfer.py`, im gemeinsamen Python-Runner automatisch entdeckt | **16/16 PASS**: Änderung/No-op, unbekannte Datei, Metadaten-/ID-Wechsel, unvollständiges Inventar, doppelte Namen, unklare Uploadantwort, Hashfehler, Streamgrenze, Pfade, Unicode/BOM/CRLF/Leerdatei, deterministische ZIPs, öffentliche Originalbilder |
| Vollständiger Python-Runner, erster und abschließender Lauf | **343/343 → 345/345 PASS**, 0 Skips; 37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen/erhalten |
| `sync-responsiveness`, `sync-customer-gallery`, `sync-trash`, `runtime-compat`, `webview-syntax` | **5/5 Browser-/JS-Suiten PASS** |
| `:app:compileReleaseJavaWithJavac`, `:app:compileReleaseAndroidTestJavaWithJavac`, `:app:lintRelease` | **PASS**, 42 Tasks; 0 Lintfehler / 50 bestehende Warnungen; keine Paketierungs-/Signieraufgabe ausgeführt |
| Vertrag, UI/MCP-Inventar/Review-Fingerprints, Pflanzenasset-Spiegel | **PASS**, unverändert 117 Tools / 156 Aktionen / `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813` |
| Source-/Workspace-/Artifact-Privacy vor/nach Tests, neuer vollständiger Source-ZIP-Export | **PASS**, keine Betriebsdatenseeds; Originalbyte-/CRC-/Inventar-/Modusprüfung aller **1.206 Dateien** im Quell-ZIP |
| Bestehende Original-APKs89/90 | Beide SHA256 **unverändert** |
| Physischer Pixel, reale Google-Synchronisierung/-Übertragungsdauer und Geräte-Update | **NOT EXECUTED** |

**Quellstand und Schutz:** Vorher-Fingerprint `42c1fd1a43c1e8331b7a34f54cbf7cbb91e0b615d16c64729e62427d689b1052`, neuer Main-/Gradle-Fingerprint **`24109ed5f22b241053034ae3e43867e706e13fb01b35ba3b2d86e0f43d2c8b99`**. Einzeldateivergleich aller 536 Main-/Gradle-Dateien: ausschließlich `DriveRestClient.java` und `DriveDirectFiles.java` geändert; Main-Assets weiterhin 420. Paket, Version **1.54.1/90**, Manifest/Berechtigungen, Daten-/Journal-/Driveformate, IDs/Relationen, MCP-Vertrag, UI-Quellreview und bestehende Test-Schutzassertions unverändert. Neue Tests ergänzen vorhandene; keine Erwartung abgeschwächt. Keine neue APK, Signierung, Installation, Geschäfts-/Konten-/Drive-Datenänderung, kein Commit/Push; vorhandene uncommittete Arbeiten erhalten.

**Wichtig für die Zuordnung:** Die Optimierungen sind gesonderte **Nach-Build90-Quellcodeänderungen**, noch nicht im originalsignierten APK90 und nicht im zuvor exakt gesicherten Build90-Export enthalten. Der bestehende Perplexity-Ordner wurde in diesem Auftrag nur gelesen; sein unveränderlicher Build90-Export bleibt korrekt beschriftet. Das vollständige technische Prüf-ZIP `/tmp/qa-hg-drive-performance-source.zip` mit 1.206 Dateien, 68.703.608 Bytes, SHA256 `06cf0ba0de0a6d3fa104476ee8434fb906b9dc0ab2220a8283caf1d3d7cd2ffb` erfasst den Code vor diesem abschließenden Statusabschnitt; nicht nach Drive hochgeladen. Keine Behauptung, der neue Code sei auf dem Pixel aktiv.

**Nachweise:** `outputs/qa-drive-performance.json`, `qa-drive-performance-before.json`, `qa-drive-performance-python-first.json`, `qa-drive-performance-python-final.json`, `qa-drive-performance-browser.json`, `qa-drive-performance-inventory.json`, `qa-drive-performance-verified-state.json`, `qa-drive-performance-export-noop.json`, `outputs/integration-java/results.json` und `DrivePerformanceTests.log`. Unveränderte Ausgangsergebnisse zusätzlich `/tmp/drive-perf-baseline-jvm.log`; aktuelle Android-/Python-/Paketprüfungen `/tmp/drive-perf-android.log`, `/tmp/drive-perf-python-final.log`, `/tmp/drive-perf-source-package.log`. Restgrenze: Netzwerk-/Anbieterlatenz und schwache Mobilfunkverbindungen brauchen eine spätere reale Pixel-Messung; der begrenzte RAM-Cache ersetzt keine frische Bestands-/Konfliktprüfung.

## Perplexity-Quellcode 1.54.1 / Build 90 aktualisiert; alte Exporte dauerhaft entfernt (21.10.2026)

- Nutzerauftrag: aktuellen Haus-&-Garten-Quellcode im bestehenden Perplexity-Drive-Ordner aktualisieren. Der anschließende ausdrückliche Auftrag **„Entferne alte versionen restlos“** ersetzt für diesen Ordner die vorherige Vorgabe, den Original89-Export aufzubewahren. Umfang sind die Quellcode-Exporte in diesem bestätigten Perplexity-Ordner; keine produktiven App-/Drive-Betriebsdaten oder Git-Historie bearbeitet. Google-Drive-Skill für Upload, Readback, Dateilöschung und Ordnerbenennung verwendet.
- Aktueller Ordner: **Haus & Garten – Perplexity – 1.54.1 – aktueller Quellcode**, unveränderte Ordner-ID `1BNJ3Zh3OjmBn-qySvu9gQzHB0Z1HgQZd`, https://drive.google.com/drive/folders/1BNJ3Zh3OjmBn-qySvu9gQzHB0Z1HgQZd . Neue Startdatei: https://drive.google.com/file/d/115Jbx6p4OUKw-vAybvSZUyUcwygxQLqe/view?usp=drivesdk . Keine Freigabe erweitert; sämtliche verbleibenden Dateien `shared=false`.
- Vollständiger aktueller Snapshot aus dem vorhandenen Cloud-Arbeitsordner: **1.203 Projektdateien**, davon **472 UTF-8-Texte und 731 öffentliche Binärressourcen**. 1.204 vom Source-Gate erfasste Dateien abzüglich einer vorhandenen historischen APK; APKs sind kein Quellcode und nicht exportiert. Alle übrigen Git-erfassten bzw. nicht ignorierten öffentlichen Projektdateien einschließlich uncommitteter Änderungen, Regeln, Buildkonfiguration, Java, UI, Python/MCP, Werkzeuge, synthetischer Tests und öffentlicher Katalog-/Markenressourcen aufgenommen. Keine Anonymisierung oder alternative Laufzeitcodebasis.
- Exakte Übereinstimmung zum tatsächlich gebauten Original90: alle **535 Main-Dateien plus android/app/build.gradle** ergeben **42c1fd1a43c1e8331b7a34f54cbf7cbb91e0b615d16c64729e62427d689b1052**, im bestehenden signierten APK90 nachgewiesen. Kein rekonstruierter historischer Stand. Quell-HEAD `72f96919d6a42ef8a2759c14c6da1df7359726d6`, vorhandener dirty-Arbeitsstand ausdrücklich offengelegt; kein Commit/Push. Snapshot-Dokumentation erfasst den Stand vor diesem abschließenden Exportbericht, der außerhalb des eingefrorenen Archivs nachgeführt wird.
- Originaldatei-ZIP: https://drive.google.com/file/d/1C945yeV6Z0SgQQMBqK-GFtJS2a0zrIFW/view?usp=drivesdk , `Haus-Garten-1.54.1-Build90-QUELLCODE.zip`, **68.594.134 Bytes**, lokaler SHA256 **a66f521e07e2cd243ac469d17ef0c307b82e1efec0e095a1a81fea5183977981**. Alle 1.203 Pfade eindeutig und sicher, ZIP-CRC und alle Originalbytes geprüft; Originalressourcen vollständig enthalten. Keine Datenbanken, Betriebsdaten, privaten Einstellungen, Backups, Kontozugangsdaten, Schlüssel, Tokens, Keystores, APK/AABs, Buildausgaben, SDK-/Git-/Cachedateien exportiert. Leerer Bootstrap unverändert.
- Insgesamt **22 Exportdateien**: ZIP; modulare UTF-8-TXT-Dateien für Architektur/Regeln/Build, Android-Java, mobile Oberfläche, Kataloge/Verträge, Python/MCP, synthetische Tests und Werkzeuge; Binärinventar, Vollständigkeits-/Herkunftsnachweis in TXT und JSON, Versionsnachweis, Exportprüfung, SHA256-Prüfsummen und Startdatei. TXT-Dateien mit Pfad, Bytezahl, Datei-/Abschnitts-SHA256 und Offsets; alle **472 Textdateien unabhängig aus den serialisierten Abschnitten wieder zusammengesetzt und bytegenau mit dem ZIP verglichen**, einschließlich Leerdateien, Zeilenenden und UTF-8-Grenzen.
- Tatsächliche Exportprüfung **PASS**: Source-Privacy 1.204 Dateien / 0 Betriebsdatenseeds; Workspace-Privacy 4.727 Dateien einschließlich ignored/untracked zum Beginn; alle 22 lokalen Exportdateien mit Artifact-Privacy geprüft. Keine neue Fach-/UI-Codeänderung, Versionserhöhung, APK, Signierung oder Geräteaktion. Keine erneute funktionale 329er-/JVM-/Browserabnahme behauptet: bestehender Maincode bleibt exakt Build90; für diesen Auftrag wurden Export-, Integritäts-, Hash- und Privacy-Prüfungen ausgeführt. G1–G6 weiterhin nicht umgesetzt; Pixel-/Google-/Update-Abnahme **NOT EXECUTED**.
- Drive-Readback vor Löschung: alle **22 neuen Dateien** anhand Name, MIME, Größe, Zielordner und `shared=false` überprüft und vollständige authentifizierte Streamreferenzen gelesen. **21 TXT-/JSON-Dateien vollständig materialisiert und SHA256-bytegleich** zum lokalen Export bestätigt. Beim großen ZIP vollständige Streamgröße 68.594.134 Bytes bestätigt; unabhängiger Downloadbyte-Hash wegen 32-MiB-Limit des Materialisierungstools **NOT EXECUTED**, kein solcher Hash-PASS behauptet. Lokale ZIP-Originalbytes/CRC/SHA256 vollständig geprüft.
- Erst danach auf ausdrücklichen Folgeauftrag **alle 26 vorherigen Dateien mit Drive `files.delete` dauerhaft gelöscht**, einschließlich Original-Build89-ZIP, separatem Nach-Build89-WIP-ZIP/TXT, bisherigen Moduldateien, früherer Start-/Versions-/Prüfsummendateien und alter Exportanweisung. Nicht nur in den Papierkorb verschoben. Neue Start-/Versionsdateien haben neue Datei-IDs; keine in-place ersetzten Altexporte mit früheren Dateirevisionen zurückgelassen. Historische Sicherungslinks in älteren Statusabschnitten sind damit überholt; die aktuelle Sicherung ist der oben verlinkte Build90-Export.
- Zwischenzeitliche MCP-Fehler `-32603 Internal error` bei einzelnen Lösch-/Metadaten-/Ordneraufrufen wurden nach Bestandsprüfung durch identische autorisierte Wiederholungen behoben. Keine Fehler als Erfolg gewertet. Abschließende erfolgreiche Ordner-Fetch-Lesung bestätigt **genau 22 eindeutige aktuelle Datei-IDs und Dateinamen, 0 der 26 alten IDs**, sämtliche erwarteten Namen/MIME/Größen/private Freigabe unverändert und korrekte neue Ordnerbezeichnung. 26 erfolgreiche permanente Löschantworten protokolliert; keine Bereinigungsrestarbeit in diesem Ordner offen.
- Konsolidierter Nachweis `outputs/qa-source90-drive-export.json`; lokale Vollständigkeit `qa-source90-local-export.json`, Uploadbelege `qa-source90-drive-receipts.json`, Byte-Readback `qa-source90-readback.json`, dauerhafte Löschungen `qa-source90-permanent-deletions.json`, finaler Ordnerbestand `qa-source90-final-folder.json`. Keine Source-/Workspace-/Privacy-/Inventar-/Vertragsgates abgeschwächt und keine produktiven Appdaten gelöscht.

## Produktive Update-APK 1.54.1 / Build 90 (21.10.2026)

- Folgeauftrag „Erstelle apk wie gewohnt“ autorisiert wieder Build, notwendige Versionserhöhung und bestehende Originalsignierung. Keine neue Grafik umgesetzt: G1–G6 bleiben wegen der im vorigen Auftrag ausdrücklich verlangten, noch fehlenden Sicherheitsanweisungen offen. Die APK enthält den bereits vorliegenden Produktivcode einschließlich Stundeneingabe/Ende offen, Kontextaktionen, Löschsicherungen und Such-/Speicherkorrekturen.
- Ausschließlich vorhandener Cloud-Checkout und geschützte persönliche Originalsignierung gemäß `ORIGINAL-SIGNIERUNG-CLOUD.md`. Aktuelle Umgebungsbeobachtung mit allen fünf Signierungsbindungen `ready`; keine Werte ausgegeben, kein neuer Schlüssel, keine CI-Signierung. Gradle und andere Unterwerkzeuge erhalten eine von den Originalsignierungsvariablen bereinigte Umgebung. Temporärer Originalkeystore ausschließlich außerhalb des Checkouts im privaten OS-Temp; Cleanup nach Signierung geprüft: 0 Signiertempordner.
- Neue Version **1.54.1 / 90**, Paket **at.hausgarten.service**. Einzelhashvergleich gegen Ausgang des neuen APK-Auftrags: alle **535 Main-Dateien unverändert**, ausschließlich `android/app/build.gradle` mit Versionsmetadaten geändert. Alle bestehenden fachlichen Änderungen und uncommitteten Arbeiten erhalten. Keine zusätzlichen Rechte, IDs, Beziehungen, Schemata, Drive-Formate oder MCP-Werkzeuge. 117 Tools (52 lesend / 65 schreibend), 156 UI-Aktionen, Vertrag `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813` und UI-Review-Gate weiterhin PASS.
- Ausgangsfehler des gesonderten Release-Browsers gezielt aufgeklärt und ausschließlich im Testaufbau korrigiert: `tests/release_qa_server.py` verwendet jetzt durchgängig dieselben `qa_`-IDs wie der Test. `tests/release-browser.test.js` verwendet passende Arbeits-/Rechnungs-IDs, räumt die tatsächlich erstellten Queue-Testeinträge auf und kennzeichnet das simulierte Touch-Ereignis ausdrücklich als `isPrimary:true, button:0`. Der Produktivguard gegen zusätzliche Finger bleibt unverändert; keine Schutzassertion entfernt oder abgeschwächt. Erneuter isolierter Release-Browserlauf: **20 von 20 PASS**, zuvor 11 PASS / 9 FAIL. Keine Produktivdaten und keine produktiven Web-/Desktop-Dienste verwendet.

| Tatsächlich ausgeführt für diesen Build | Ergebnis |
| --- | --- |
| Offline `:app:assembleRelease :app:compileReleaseAndroidTestJavaWithJavac :app:lintRelease` | **PASS**, 66 Tasks, 2 min 42 s; 0 Lintfehler / 50 bestehende Warnungen |
| Release-Browser mit zwei isolierten synthetischen HTTP-Fixtures | **20 PASS**, 0 FAIL |
| Python-Gesamtsuite gegen die tatsächliche signierte APK90 | **329 PASS**, 0 Skips; 37 historische schlüsselerzeugende QA-Tests ausdrücklich ausgeschlossen |
| Source-/Workspace-/APK90-Privacy der Python-Endprüfung | **PASS**, 1204 Source-Dateien / 4719 Workspace-Dateien einschließlich ignored und untracked; 0 Betriebsdatenseeds |
| UI-Inventar, Refactoring-Vertrag und Quellfingerprint | **PASS**, 117 Tools / 156 UI-Aktionen, unveränderter Vertrag |
| Strikte unsignierte und signierte Artefaktprüfung | **PASS**, 420 Main-Assets bytegleich; zwei zusätzliche SDK-ART-Profile mit Magic und Hash gegen die aktuellen Gradle-Kompilate geprüft; exakte Assetmenge / ZIP-CRC / keine doppelten Pfade |
| Originalsignierung und Vergleich mit erhaltener Original89-APK | **PASS**, gleiche Paketkennung und exakt dasselbe Originalzertifikat, versionCode 90 > 89, ein Signer, v2/v3, 16-KiB-Ausrichtung |
| Private Drive-Bereitstellung und Metadaten-/Stream-Readback | **PASS** für Name, MIME, Größe, Zielordner, shared=false und vollständige authentifizierte Dateireferenz; unabhängiger Downloadbyte-Hash **NOT EXECUTED** |

- Wiederverwendete, unmittelbar vorangegangene Funktionsabnahme ausdrücklich getrennt: **37 JVM-Suiten und 46 Browser-Suiten PASS** aus dem grafischen Ausgangslauf. Kein neuer JVM-/46er-Browsergesamtlauf für diesen Build behauptet. Der Einzelhashvergleich sämtlicher Main-Dateien bestätigt denselben getesteten Fach-/UI-Code; die anschließende reine Versionsänderung ist separat gebaut und geprüft. Aktuelle Python- und 20er-Release-Browserläufe sind dagegen tatsächlich erneut ausgeführt.
- Artefakt: `outputs/Haus-Garten-1.54.1-90-original-update.apk`, **36.171.731 Bytes**, SHA256 **5c767f48884d02df463aec7a08a2094383e5dda023077268b5cc2ad46329f178**. Zertifikat SHA256 **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**. Nicht debuggable, UI_PREVIEW=false, minSdk 26 / targetSdk 37, Backup aus, keine zusätzliche Instrumentierung/sharedUserId, drei produktive Provider und vorhandene Live-Komponenten erhalten. Google AuthorizationClient sowie neue Löschsicherungs-/Termin-/Wetterklassen im tatsächlichen DEX geprüft.
- Neuer Main-/Gradle-Fingerprint **42c1fd1a43c1e8331b7a34f54cbf7cbb91e0b615d16c64729e62427d689b1052**, im kompilierten BuildConfig und tatsächlichen DEX enthalten; Quell-HEAD `72f96919d6a42ef8a2759c14c6da1df7359726d6`, Arbeitsstand weiterhin als dirty gekennzeichnet. Keine falsche Zuordnung zum gesicherten Originalquellstand89. Unsigniertes APK **36.163.063 Bytes**; vor Originalschlüsselverwendung eigenständig geprüft.
- Im anhand der bisherigen APK89-Metadaten bestätigten privaten **Codex-Cloud-Builds**-Ordner neu erstellt: https://drive.google.com/file/d/1yp-i450bx9MIrn_t6-O1Srhwe7yGlFSg/view?usp=drivesdk . Upload und anschließende unabhängige Metadaten-/Fetch-Lesung bestätigen Dateiname, APK-MIME, exakte 36.171.731 Bytes, Zielordner `1K4WB9L6ZKlW-33o8uPK3zTfqgAOVOE8u` und `shared=false`. Keine Freigabeänderung. Vollständige authentifizierte Streamreferenz zurückgelesen; der 36-MB-Download überschreitet das 32-MiB-Limit des verfügbaren Materialisierungstools, daher keine unabhängige Downloadbyte-SHA behauptet. Google-Drive-Skill für Dateilebenszyklus und Readback verwendet; keine Betriebsdatei geöffnet oder verändert.
- **Keine Installation, Deinstallation, App-Datenlöschung, produktive MCP-Schreibaktion, Konten-/Freigabeänderung oder Commit/Push.** Pixel-WebView, Kamera, Benachrichtigungen/Doze, reale Google-Freigabe sowie Fresh-Install-/In-place-Update-/Datenerhaltprüfung **NOT EXECUTED**. Original89-APK bytegleich SHA256 `8464cb9e314e76979edbee6b6bc5ddde8ef8fa5a453c2cec7bd12baadcf8adea`; bisherige Drive-APKs und unveränderliche Build89-Quellcode-Sicherung erhalten.
- Nachweise: `outputs/qa-update90-release.json`, `qa-update90-unsigned.json`, `qa-update90-signed.json`, `qa-update90-signing.json`, `qa-update90-drive.json`, `qa-build90-before.json`, `QA-Python-results.json` und `QA-Browser-results.json`. Tatsächliche Prüfungen sind technische Cloud-Abnahme, keine Freigabe aus einer behaupteten Geräteinstallation.

## Grafische Vereinheitlichung G1–G6 (Basis 1.54.0/89)

Stand 21.10.2026: **Ausgangslauf ausgeführt; Umsetzung wegen fehlender ausdrücklich vorausgesetzter Sicherheitsanweisungen OFFEN.** Keine grafische Abnahme und kein abgeschlossener G1–G6-Commit behauptet.

- Maßgeblich ist der neue reine Oberflächenauftrag: keine APK, Signierung, Versionserhöhung, Installation, Konten-/Drive-/Schlüsselzugriffe oder fachlichen Änderungen. Vorhandene uncommittete Folgearbeiten bleiben erhalten. Lokaler Ausgangspunkt Branch `work`, HEAD `72f96919d6a42ef8a2759c14c6da1df7359726d6`; historischer lokaler `origin/main`-Verweis zusätzlich geprüft, kein Remote-/Kontenzugriff. Keine Behauptung, der aktuelle Folgecode sei bytegleich mit dem gesicherten Original-Build89.
- Verbindliche Voraussetzung aus Nutzerauftrag Abschnitt4: **„Sicherheit A → C zuerst, danach dieser Auftrag.“** `CODEX-AUFTRAG-SICHERHEIT-1.54.md` und `tests/webview-hardening.test.cjs` sind im Checkout und den verfügbaren Anhängen nicht vorhanden. `mobile.html` enthält noch keine CSP; `network_security_config.xml` hat weiterhin `cleartextTrafficPermitted="true"`. Der Sicherheitsumfang A–C wird nicht erfunden. Fehlende Anweisungen bzw. eine ausdrücklich abweichende Reihenfolge wurden per Rückfrage angefordert; bis dahin keine Appcodeänderung.
- Abnahme nach Klärung: unveränderte IDs/Selektoren, Beziehungen, Daten-/Drive-Verträge und Berechtigungen; eine Farb-/Schriftquelle, gleiche native/WebView-Darstellung, keine überdeckten Schaltflächen, mindestens48px-Ziele, Hauptreiter ohne Zurück und Unterseiten mit genau einem sichtbaren Rückweg, verständliche getrennte Statusmeldungen. Vollständige Regression nach jedem Paket, eigener Branch/Commit je Paket, kein Push auf main. Sicherheits-/Privacy-/Inventar-/Fingerprint-Gates bleiben verpflichtend.

| Paket | Umsetzung / neue oder geänderte Appdateien | Branch / Commit |
| --- | --- | --- |
| G1 Tokens / Gates | NOT STARTED; keine Appdatei geändert | Noch nicht angelegt |
| G2 Native Darstellung | NOT STARTED; keine Appdatei geändert | Noch nicht angelegt |
| G3 Heute / Leisten | NOT STARTED; keine Appdatei geändert | Noch nicht angelegt |
| G4 Kopfzeilen / Navigation | NOT STARTED; keine Appdatei geändert | Noch nicht angelegt |
| G5 Statusdarstellung | NOT STARTED; keine Appdatei geändert | Noch nicht angelegt |
| G6 Mehr-Menü, optional | NOT STARTED; keine Appdatei geändert | Noch nicht angelegt |

Tatsächlich ausgeführter **unveränderter Ausgangslauf**, ausschließlich synthetische Fixtures:

| Prüfung | Anzahl / Ergebnis | Nachweis / Einschränkung |
| --- | --- | --- |
| Python `tests/run_quality_suite.py --exclude-qa-signing --artifact …original-update.apk` | **329 PASS**, 0 Skips | `outputs/QA-Python-results.json`; einschließlich Release-Quality und Source-Privacy;37 historische schlüsselerzeugende Tests ausdrücklich ausgeschlossen |
| JVM `tools/run-followup-java.py` | **37 Suiten PASS** | `outputs/integration-java/results.json`; einschließlich CalendarSetup, CalendarAuthorization, Planning, UiMcpParity, RefactoringCharacterization und OpenEndPlanning |
| Gemeinsamer Browserrunner | **46 Suiten PASS** | `outputs/integration-ui/results.json`; enthält sämtliche in Abschnitt3.1 genannten Browserregressionen außer dem gesonderten Release-Browserlauf |
| `tests/release-browser.test.js`, zwei isolierte synthetische HTTP-Fixtures | **11 PASS / 9 FAIL**,20 Prüfungen | `outputs/QA-Browser-results.json`; bereits vor Änderungen rot, siehe Einordnung unten |
| UI-Inventar / Refactoring-Vertrag / Pflanzenspiegel | **3 Werkzeuge PASS** |117Tools (52read/65write),156UI-Aktionen,181Pflanzen/362Bilder/370generierte Spiegel geprüft |
| `tools/check_release_baseline.py` | **PASS** | Version1.54.0/89; aktueller Quellfingerprint unten |
| Source-/Workspace-/bestehendes Original89-APK-Privacy | **PASS** | Source1204Dateien,0Betriebsdatenseeds; Abschluss des Pythonlaufs Workspace4697Dateien einschließlich ignorierter/untracked Dateien; vorhandene APK nur lesend geprüft |
| `tools/verify_ui_preview_apk.py` gegen vorhandene Test-APK88 | **FAIL** beim exakten Asset-Vergleich | Alte APK enthält417Assets; aktueller Preview-Quellstand erwartet422 einschließlich2Preview-Assets. Vorherige Paket-/Manifest-/Isolationsassertions bestanden; kein Gesamt-PASS behauptet |
| `:app:compileReleaseJavaWithJavac :app:lintRelease`, offline | **PASS**,33Tasks/1m33s |0Lintfehler,50bestehendeWarnungen; kein assemble/sign/install |
| Chromium-Vorherbilder / Geometrie | **15 Aufnahmen**,0Laufzeitfehler,0horizontale Überläufe,0sichtbareButtons unter48px |5WebView-Seiten ×320/360/426px,150%Schrift; dies allein beweist keine Freiheit von verdeckten Inhalten |

Einordnung der bereits vorhandenen Ausgangsfehler:

- Der historische Release-HTTP-Fixture legt `cus_qa`/`ord_qa` an, während der Browserablauf `qa_cus_qa`/`qa_ord_qa` voraussetzt. Darauf folgen Pflichtfeld-/Beziehungs- und Queuefehler. Eine weitere fehlgeschlagene Prüfung erwartet beim alten mobilen HTTP-Fixture das Kontextdialog-Öffnen nach langem Tippen; dieser Befund ist noch nicht isoliert aufgeklärt. Daher nicht pauschal alle neun Fehlschläge als reinen Fixturefehler abgetan und keine Schutzassertion entfernt. Vor Umsetzung erneut gezielt abgrenzen.
- Die vorhandene Preview-APK stammt aus einem älteren Quellstand: fünf heutige Hauptassets fehlen (`data-order.js`, `field-ui.css/.js`, `record-actions.css/.js`); zwei Preview-Overlays sind erwartungsgemäß zusätzlich enthalten. Keine neue Preview-APK gebaut und keinen exakten aktuellen APK-Nachweis vorgetäuscht. Nach grafischen Änderungen bleibt eine tatsächliche neu verpackte APK-Verifikation unter dem ausdrücklichen APK-Verbot **NOT EXECUTED**; Generator-/Browser-/Manifestprüfungen bleiben separat möglich.
- Tatsächliche Main-Asset-Anzahl vor diesem Auftrag **420**, nicht417. Die aktuelle Original89-APK enthält417Main-Assets plus2Dexopt-Dateien; drei spätere Main-Assets erklären420im Arbeitsordner. `tokens.css` würde hier **420→421** ergeben (Preview-Gesamtbestand422→423). Keine Gate-Erwartung verändert oder entfernt. Abweichung zur angenommenen417→418-Basis offen dokumentiert.

Vorher-Screenshots: `outputs/grafik-{heute,mehr,status,kunden,arbeiten}-vorher-{320,360,426}-150.png`, jeweils mit bytegebundener synthetischer Privacy-Sidecar-Datei. Alle fünf Seiten bei360px/150%als geöffnete Bilder angesehen: Heute-Dock verdeckt Karteninhalt, weißer breiter Metriktrenner, Mehr und Kunden mit überflüssigem Zurück-Pfeil, Status mit zusätzlicher unterer Zurück-Leiste; lange Überschriften wirken gedrängt. Keine horizontalen Überläufe in den15Geometrieproben. Die nativen Kalenderseiten lassen sich durch Chromium-WebView-Aufnahmen nicht abnehmen; keine solche Darstellung erfunden. Die im neuen Auftrag beschriebenen fünf Pixel-Anhänge06:57/06:58 wurden nicht zusätzlich bereitgestellt; vorhandene ältere Gesprächsbilder und der schriftliche Befund ersetzen keine aktuelle Pixel-Abnahme. **Nachherbilder fehlen**, weil G1–G6 noch nicht umgesetzt sind. Selbstkritik: sämtliche beschriebenen optischen Uneinheitlichkeiten bestehen weiterhin.

Nicht ausgeführt / offen: neue Suiten `visual-consistency.test.cjs` und `test_native_ui_consistency.py` noch nicht erstellt; Sicherheits-A–C-/Hardening-Abnahme mangels Anweisungen/Test **NOT EXECUTED**. Pixel-WebView, Kamera, native Activities auf Gerät, reale Google-Freigabe und Installation **NOT EXECUTED**. Kein Endlauf nach Grafikänderungen, weil keine Grafikänderungen vorliegen. Bestehende Test-Erwartungen unverändert; **keine Schutzassertion entfernt**.

Aktueller Quellfingerprint unverändert `f25f7379bf7e33ddac66b6e816a10a6266968f2ceb8cfa8bf7707e92d83970fc`; UI-Review-Dateihashes unverändert und Gate PASS. Vertrag unverändert `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`. Einzelhashvergleich aller536Dateien (535Main-Dateien plus Gradle) vor/nach Ausgangslauf:0Änderungen/0Neuzugänge. Original89-APK unverändert SHA256 `8464cb9e314e76979edbee6b6bc5ddde8ef8fa5a453c2cec7bd12baadcf8adea`. **Keine APK, Version, Berechtigung, Geschäftslogik, Daten, IDs oder Schlüssel geändert; kein Commit, Branchwechsel oder Push auf main.** Nur dieser Statusabschnitt und synthetische QA-Nachweise ergänzt. Zusammenfassung: `outputs/qa-grafik-baseline.json`; Einzelhashes `outputs/qa-grafik-baseline-source.json`, Geometrie `outputs/qa-grafik-baseline-layout.json`.

## Termindauer in Stunden und Ende offen (21.10.2026)

- Nutzerauftrag anhand Planungs-Screenshot: Dauer in Stunden eingeben; alternativ feste Startzeit bei noch offenem Zeitaufwand. Produktivquellcode erweitern, keine neue APK oder produktiven Daten-/Kalenderaktionen. Bestehende Änderungen und unveränderlichen Build89-Drive-Export erhalten.
- Abnahme vor Umsetzung:1,5Stunden ergeben90Minuten einschließlich Pause; vorhandene Dauern und Entwürfe bleiben erhalten. Ende offen ohne erfundenen Endzeitpunkt/Nullstunden speichern und lesbar anzeigen; später explizit konkretisierbar. UI, native Validierung, Drive-Replay, Wiederholungen, Google-Abgleich, Wetter und Kundenübersicht müssen offene Enden ohne Fehler behandeln. Sichere bekannte Überschneidungen bleiben gesperrt, nur mögliche Kollisionen verständlich als ungeklärt melden; keine ungesicherte freie Zeit behaupten. Aktuelle Hash-/Request-/Beziehungs-/Timerregeln erhalten.320/360/426px/150%Schrift und48px-Bedienflächen, ausschließlich synthetische Java-/Browser-/Python-/Androidprüfungen.
- Umgesetzt: Dauer in Stunden einschließlich Pause (z.B.1,5→90Minuten). Deutlicher48px-bediendbarer Schalter „Ende offen“ unter dem Beginn; bei unklarer Dauer keine erfundene Zweistundenvorgabe. Eingabe der Stunden erscheint beim Ausschalten, zuvor eingegebene Stunden/Notizen bleiben beim Umschalten und in wieder geöffneten Entwürfen erhalten. Zeitfenstersuche fragt eine benötigte Stundendauer nur auf ausdrücklichen Suchwunsch; Vorschläge werden erst nach Bestätigung in den Entwurf übernommen. Vorhandene Zeitpunkte bleiben bei reiner Textbearbeitung exakt erhalten.
- Fachmodell ohne zusätzliche Felder/Schema-/ID-/Tooländerung: vorhandenes Pflichttextfeld ends_at darf ausdrücklich die leere Zeichenfolge enthalten. Fehlendes/null/ungültiges Ende bleibt abgewiesen; bekannte Dauern weiter5Minuten bis24Stunden, Pausen- und Beziehungs-/Versionsprüfungen bleiben erhalten. Unbekannte geplante Stunden sind null statt0, tatsächliche Arbeitszeit bleibt getrennt. Einzeltermine, Wiederholungen mit Ortszeit-/Sommerzeitbezug und idempotenter Drive-Replay behandeln offene Enden. Übersicht, Heute und Kundenakte zeigen „Ende offen“; später über dieselbe Termin-ID konkretisieren oder wieder öffnen.
- Kollisionen: bekannte Arbeits-/Anfahrtsüberschneidungen bleiben echte Sperren. Bei unbekanntem Ende spätere mögliche Überschneidungen ausdrücklich gelb mit „Mögliche Terminkollision“/„Belegung noch ungeklärt“ anzeigen; keine sichere freie Zeit behaupten und keine Endzeit erfinden. Zeitfenstersuche gibt nach offenen Enden keine ungeprüften freien Vorschläge aus. Externe Google-Belegung bei offenem Ende nicht als vollständig geprüft kennzeichnen.
- Google-Projektion nutzt endTimeUnspecified=true und einen API-Kompatibilitätsendpunkt eine Minute nach Beginn; dieser technische Marker ist KEINE gespeicherte/geplante Arbeitsdauer. Beschreibung nennt Ende offen. Rückvergleich übernimmt bei gesetztem Flag wieder ends_at leer; beim Festlegen einer Dauer Flag ausdrücklichfalse senden. Bestehende ETags, Identitäts-/Paralleländerungs-/Antwortverlustschutz und Kontofreigaben erhalten. Mock-Transport samt tatsächlichem CalendarClient-Speichern/Rücklesen geprüft; reale Google-Darstellung/-Verbindung ausdrücklich NICHT AUSGEFÜHRT.
- Wetter/Pixel: offene aktive Termine bleiben prüfbar und für Warnungen erreichbar. Prognose/Warnhinweise beziehen sich bei offenem Ende ausdrücklich auf Beginn bzw. aktuellen Zeitpunkt; keine Aussage über eine unbekannte Gesamtdauer. Beendete/abgesagte Termine weiter inaktiv. Einrichtung und automatische Standort-/Wetterprüfung brechen nicht an leeren Endzeiten ab.
- Abnahme PASS:329Python-Tests/0Skips (37historische QA-Schlüsselerzeugungstests ausgeschlossen),37JVM-Suiten einschließlich49neuer Prüfungen in OpenEndPlanningTests. Acht betroffene Browser-Suiten PASS; elf neue UI-Fälle prüfen offene Speicherung,90Minuten aus1,5Stunden,35-Minuten-Bestand ohne Zeitänderung, spätere Konkretisierung, Entwurf/Retry/Wiederholung, Kollision und schmale/vergrößerte Darstellung. Erste neue JVM-Testfassung verwendete irrtümlich eine falsche CalendarClient-Testsignatur; nur Testcode korrigiert, anschließender vollständiger37er-Lauf PASS. Screenshots der echten Assets mit synthetischer Bridge visuell geprüft. Neue Tests im vorhandenen Browserrunner registriert.
- Finale Release-/Instrumentierungs-Java-Kompilierung und Lint PASS:42Tasks/1m41s,0Lintfehler/50bestehendeWarnungen. Source-/Workspace-/bestehendes Original89-APK-Privacy PASS:1204Quellen einschließlich vorhandener APK,4659Workspace-Dateien,0Betriebsdatenseeds. P1 unverändert117Tools(52/65),156UI-Aktionen,Contract899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813. Keine neue APK, Originalsignierung, Installation, Produktiv-/Kalender-/Drive-Schreibaktion oder neue Berechtigung. Physische Pixel-/Google-/Update-Abnahme weiterhin offen.14Main-Dateien geändert; diese Folgearbeit gehört ausdrücklich NICHT zum gesicherten Originalquellstand von Build89. Nachweis outputs/qa-open-end-planning.json; Original-Build89-Drive-Export unverändert.

## Quellcode 1.54.0 / Build 89 für Perplexity gesichert (21.10.2026)

- Nutzerauftrag: Originalquellen aus diesem Arbeitsordner im vorbereiteten privaten Drive-Ordner sichern, gegliederte verlustfreie UTF-8-TXT-Dateien und Originaldatei-ZIP; spätere Änderungen getrennt. Google-Drive-Skill angewendet. Keine APK erstellt, keine Installation, kein Commit/Push und keine produktiven Daten-/Kontenaktionen.
- Vorbereiteter Ordner anhand ID/Metadaten/Anleitung bestätigt, bestehende Freigaben unverändert: https://drive.google.com/drive/folders/1BNJ3Zh3OjmBn-qySvu9gQzHB0Z1HgQZd . Bestehende Startdatei und Versionsnachweis aktualisiert, Exportauftrag erhalten. Ordnerstatus von „ausstehend“ auf „gesichert“ geändert.
- Original-ZIP: https://drive.google.com/file/d/1l0zPHaEXQl-_rjRL5Px8JdE79WnewLsv/view?usp=drivesdk . 1189 Projektdateien, davon458 UTF-8-Texte und731 öffentliche Binärressourcen. Die historische Source-Gate-Anzahl1190 enthielt zusätzlich eine vorhandene APK; diese ist kein Quellcode und ausgeschlossen. Originaldateien einschließlich Regeln, Python/MCP, Java, UI, Tests, synthetischen Fixtures, Katalogen und öffentlichen Bildern/Fonts enthalten. Keine Betriebsdaten, privaten Einstellungen, Schlüssel, Tokens, Keystores, Datenbanken, Buildausgaben oder Git-Historie.
- Exakte historische Main-Provenienz: alle531 Main-Dateien plus android/app/build.gradle ergeben erneut b05723a29302dfc98e8ac6a3aefcbaffd6cb5c0b1c5632fd6889df5761774aa1.517 Main-Dateien unverändert im Workspace;14 inzwischen geänderte Dateien ausschließlich anhand gespeicherter Original-Einzelhashes wiedergewonnen:6 bytegleiche APK-Assets,7 unveränderte Git-Quelldateien,1 Datei durch Entfernen ausschließlich späterer Ergänzungen. Kein geschätzter Maincode. Unterstützende Projekt-/Test-/Nachweisdateien außerhalb dieses historischen Fingerprints mit offengelegter Herkunft; bekannte spätere Ergänzungen entfernt. Kein historischer Gesamthash für diese unterstützenden Dateien behauptet.
- 24 Exportdateien im Ordner: Original-ZIP, modular gegliederte TXT-Dateien mit Dateipfad/Bytezahl/SHA256/Abschnittsoffsets, Binärinventar, maschinenlesbarer Herkunfts-/Vollständigkeitsnachweis, Prüfsummen und getrennte spätere WIP-Quellen. Alle458 Textdateien aus den TXT-Dateien wieder zusammengesetzt und bytegenau gegen ZIP-Originale geprüft; alle ZIP-Einträge CRC-/Pfad-/Anzahl-/Bytevergleich PASS. Original-Bootstrap unverändert leer. Source-/Workspace-/sämtliche Artifact-Privacy-Gates PASS.
- Spätere Arbeiten separat:29 neue/geänderte Dateien, davon10 neu, in ausdrücklich als NACH-BUILD89/WIP/NICHT ABGENOMMEN gekennzeichnetem ZIP und90-TXT-Dateien. Keine Vermischung mit ausgelieferter APK89. Der Sicherungszeitpunkt liegt vor der weiteren Abnahme dieses Folgeauftrags.
- Drive-Abgleich: alle24 Exportdateien anhand Name/Größe/Zielordner/shared=false verifiziert;23 Dateien einschließlich sämtlicher TXT/JSON, Prüfsummen und WIP-ZIP vollständig zurückgelesen und SHA256-bytegleich. Original-ZIP lokal68.542.885Bytes mit vollständiger CRC-/Originalbyte-/SHA256-Prüfung; authentifizierter vollständiger Streaming-Readback ebenfalls68.542.885Bytes. Unabhängige Remote-SHA des großen ZIP nicht verfügbar: Inline-Readback überschritt Transportlimit; keine solche Hashprüfung behauptet. Aktualisierter VERSIONSNACHWEIS.json separat SHA256-bytegleich zurückgelesen. Ordnerbestand26 Dateien einschließlich beider ursprünglicher Nachweise/Anleitung und aller Exporte vollständig geprüft. Nachweis outputs/qa-source89-drive-export.json.

## Gezielte Lösch- und Bereinigungsprüfung (21.10.2026)

- Erweiterter Folgeauftrag während der Abnahme: fehlende Löschwege ermitteln; einheitliche Kontextaktionen per langem Tippen für Einträge/Bilder/Dokumente; wichtige Daten automatisch vor endgültiger Entfernung sichern. Suche/Speicherung appweit prüfen, Kundenkonten ausdrücklich bestätigt, alphabetische Einordnung auch nach Neuanlage. Abnahme: alle acht vorhandenen Geschäftsentitäten erreichbar, keine Verwechslung bei Filtern/Pagination, Scrollen/Pinch/Textbearbeitung löst kein Kontextmenü aus, sichtbare/tastaturbedienbare Alternative; Löschsicherung vor irreversiblen Änderungen, aktuelle Vergleichsstände und Schutz ausgestellter Belege erhalten. Einheitliche deutsche Suche mit Mehrwort-/Umlaut-/Telefon-/Dokumentnummern, stabile Sortierung und sofortige Aktualisierung nach Speichern; keine automatische Wiederholung von Schreibfehlern, keine verlorenen Entwürfe. Nur synthetische Tests, keine produktiven Geräteaktionen oder neue APK.

- Folgeauftrag im Quellcode umgesetzt: ein gemeinsamer Gestenbesitzer für alle acht Geschäftsentitäten, Fotos/Dokumente, globale Suchtreffer und Papierkorb. Langes Tippen/ContextMenu/Shift-F10 und sichtbare48px-Aktionstaste führen zu derselben exakten Datensatz-ID; Scrollbewegung über10px, zweite Berührung, Texteingaben und Systemzurück bleiben geschützt. Vorhandene Bearbeitungs-/Ansichts-/Wiederherstellungswege erhalten, ausgestellte Rechnungen bleiben geschützt. Neue Dateien record-actions.js/.css; keine zusätzlichen MCP-Werkzeuge, öffentlichen Schemas oder Rechte.
- Wichtige Daten automatisch erkannt und vor Einzel-/Sammelpurge als gezielte unveränderliche Löschsicherung im bestehenden privaten NoBackup-Bereich gesichert: Archivdatensätze, finanzielle Dokumente, Arbeitsnachweise, Kundenkonten, Notizen und zugehörige Originalfotos/PDFs; geteilte Dateien dedupliziert. Jede Originalgröße/Prüfsumme, ZIP-Manifest und gespeicherte ZIP-Bytes vor dem Einreihen der Löschung geprüft. Fehler oder fehlende Originale brechen ohne Löschauftrag ab. Bestehende SQLite-Transaktion und mcp_uploads-Metadaten verwenden, kein neues Datenbankschema; große ZIPs werden als Dateien und in begrenzten Chunks gelesen. Sicherungen über vorhandene Backup-/Dateiwerkzeuge auffindbar/prüfbar/übertragbar. UI unterscheidet Löschsicherung von vollständiger Geschäftssicherung. Keine automatische Wiederanlage endgültig gelöschter IDs; gezielte Wiederherstellung muss separat geprüft werden. Löschsicherungen bleiben auf diesem Gerät und ersetzen keine externe Gerätesicherung.
- Suche/Sortierung: gemeinsame deutsche Umlaut-/ß-/Mehrwort-/Telefonregeln im mobilen UI sowie in nativen MCP-Lesewegen und Python-/MCP-/Kundenlisten. Kundenkonten aktiv zuerst und alphabetisch mit natürlichen Zahlen2vor10/stabiler ID; Bausteine nach Kategorie/Titel, chronologische Arbeits-/Fotolisten bleiben passend. Globale Suche ergänzt Dateinamen, Dokumentnummern, Bausteine und explizite Kundenzuordnungen; geöffnete Treffer aktualisieren sich nach bestätigtem Lesen. Kein Ändern gespeicherter Namen, Beziehungen, IDs oder Journal-/Requesthashes durch Suchnormalisierung. Kundenformular übernimmt bestätigte gespeicherte IDs/Baselines vor optionalem Foto, damit ein Fotofehler keinen zweiten Kunden erzeugt. Geschäftsdaten-/Statuslesefehler getrennt; letzte bestätigte Ansicht bleibt erhalten. Dynamisch geladene Fotooriginale im Viewer auf zwei begrenzt und bei Schließen freigegeben, eingebettete Originale unverändert.
- Abschließende Host-Abnahme PASS:329Python-Tests/0Skips,37historische Tests mit QA-Schlüsselerzeugung ausdrücklich ausgeschlossen;36JVM-Suiten, neue Löschsicherung57Checks, Suche19Checks,47zusätzliche Cleanup-Portchecks.45Browser-Suiten einschließlich15neuer Kontext-/Such-/Speicherfälle. Echte Nebenwirkung des neuen generischen Wrappers (doppelte persönliche Bezahlt-Taste) korrigiert und ursprüngliche Elternstruktur erhalten. Testharness-Probleme getrennt: Acorn war installiert, NODE_PATH fehlte; Kalenderselektor auf aktive Planung begrenzt, damit derselbe bevorstehende Termin auf Heute nicht die Prüfung mehrdeutig macht; persönlicher Bezahlt-Test wartet auf fertige Oberfläche und Foto-Cachetest auf asynchrones close-Ereignis. Keine Produktprüfung durch abgeschwächte Löschguards ersetzt.
- Finale offizielle Release-/Instrumentierungs-Java-Kompilierung und Release-Lint PASS,42Tasks/1m31s,0Fehler/50bestehendeWarnungen. Keine assemble-/Signier-/Installationsaktion. Native Geräteproben weiterhin NICHT AUSGEFÜHRT: kein Cloud-Emulator/physischer Pixel. Bestehende Original89 bytegleich SHA2568464cb9e314e76979edbee6b6bc5ddde8ef8fa5a453c2cec7bd12baadcf8adea. P1 weiterhin117Tools(52/65),156UI-Aktionen,Contract899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813. Finale Source-/Workspace-/Original-APK-Privacy PASS,1202Quellen einschließlich vorhandener APK/4639Workspace-Dateien/0Seeds. Nachweise outputs/qa-context-search-deletion.json, outputs/qa-context-browser-final.json und outputs/QA-Python-results.json. Aktualisierter separater Folgeexport wird aus37geänderten/neuen Dateien (12neu) erstellt; Original-Build89-ZIP bleibt unverändert.

- Auftrag: Lösch-/Papierkorb-/Bereinigungsverhalten gezielt testen. Ausschließlich isolierte synthetische Zustände; keine produktiven MCP-Schreibaktionen, keine Installation und keine neue APK. Bestehende Änderungen im Checkout erhalten.
- Abnahmekriterien: Archivieren/Wiederherstellen erhält IDs und unabhängige Datensätze; endgültiges Löschen erfordert aktuelle Vorschau und Bestätigung. Geschützte Belege/aktive Verknüpfungen bleiben erhalten. Sammellöschung und Einzelpurge müssen im lokalen Pending-Overlay und beim Drive-Replay dieselbe bestätigte Menge behandeln; lokale Testaktionen verlassen das Gerät nicht. Konflikte, Retry und Wiederholung dürfen keine Daten überschreiben oder doppelt löschen. Medienbereinigung erst nach Veröffentlichung/Commit, gemeinsame Dateien/noch ausstehende Referenzen schützen und Fehler wiederholen.
- Ausgangsprüfung: vorhandene JVM-Verträge sowie Papierkorb-Browserszenarien ausgeführt;55gezielte Python-Tests PASS nach Korrektur des Testmodulpfads im Aufruf. Erster Python-Aufruf ohne tests/-Modulpfad hatte ausschließlich Importfehler und ist kein Produktfehler. Vertiefung: letzte reale Sammellöschung meldete Versionskonflikte, Einzelpurges funktionierten; mögliche Abweichung durch rein lokales Test-Pending-Overlay gezielt synthetisch reproduzieren.
- Tatsächlicher Fehler synthetisch nachgestellt: regulären Papierkorbeintrag plus ausschließlich lokal vorgemerkten Testkunden anlegen; Sammellöschung verschwindet lokal, während derselbe unveränderte Auftrag ohne den privaten Testkunden beim Drive-Replay mit WRITE_CONFLICT scheitert. Der neue Test schlug vor Korrektur genau hier fehl. Ursache ist der gemeinsame Vorschauguard über alle Geschäftscollections bei absichtlich unterschiedlichen Pending-Ansichten.
- Kleine Quellkorrektur in SyncArchiveApplicationService/TrashPurgeBatch: Im bestehenden Eigentümer-Transaktionspfad die für Drive zulässigen Vorgänger separat projizieren. Nur bei exakt gleicher freigegebener Löschmenge und identischen Archivversionen einen zusätzlichen internen _drive_preview_hash binden. Lokaler Originalguard bleibt erhalten; Replay akzeptiert ausschließlich einen dieser vorab geprüften vollständigen Collection-/Archivguards und prüft sämtliche aktuellen Verknüpfungen, Belege und exakten Einträge erneut. Kein erzwungener Konfliktabschluss, keine Journalumschreibung, keine zusätzlichen MCP-Rechte/Tools, IDs, Datenbank- oder Snapshot-Schemaänderungen. Alte Aufträge ohne Zusatzguard behalten ihren bisherigen Prüfpfad; bereits konflikthafte Altaufträge werden nicht automatisch verändert. Rein lokale Testvorgänger werden nicht an Drive gebunden, gemischte Test-/Betriebs-Löschmengen bleiben gesperrt.
- Endgültige gezielte Abnahme PASS:100Python-Tests (55fachliche Lösch-/Datei-/Syncfälle,45Architektur-/Datenschutzfälle),34JVM-Suiten. Neue TrashBoundaryTests:97Checks einschließlich vollständiger Archivieren→Wiederherstellen→erneut Archivieren→Einzelpurge→Wiederanlage-Sperre für alle acht Entitätstypen. Shared-/lokale Projektion, unveränderte aktive Daten, private Testkunden, echte Paralleländerungen, unterschiedliche Löschmengen, Bestätigung, veränderte Einträge, lokale Testmischung und idempotenter Replay geprüft. Normale Legacy-Guards bleiben ohne Zusatzfeld.
- Tatsächlichen unveränderten TrashMediaCleanup-Koordinator zusätzlich in gesondertem OS-Temp-Classpath mit ausdrücklich synthetischen Store-/Datei-Ports ausgeführt:47Checks PASS. Original/Thumbnail/PDF, aktive/archivierte/verschachtelt ausstehende gemeinsame Referenzen, andere lokale Attachment-Referenzen,403-/Versionskonflikt-Simulation, Lesefehler, lokaler Commitfehler nach Remote-Löschung, Wiederholung und4er-Batches mit9Dateien geprüft. Diese Porttests ersetzen keine Ausführung der echten SQLite-Sperren oder Android-/Google-Provider. Wiederholbarer Einstieg tests/run_trash_cleanup_tests.py nach tools/run-followup-java.py; Nachweis outputs/qa-trash-cleanup.json.
- Sechs gezielte Browser-Suiten PASS:26Papierkorb-/Syncszenarien und fünf angrenzende Suiten für Mehrfachauswahl, Kunden/Fotos, Sync-Reaktionszeit, Geschäftsaktionen und Eigentümer-Client. Neu: Änderung während Bestätigung führt zu sicherer Ablehnung; vollständig geschützter Papierkorb sendet keinen Löschauftrag; Teilleerung nennt geschützte Reste und erhält deren Wiederherstellung.320/360/426px und150%Schrift im vorhandenen Papierkorbtest bestanden.
- Offizielle Android-Release- und Instrumentierungsquellen offline kompiliert,Release-Lint PASS:42Tasks,0Fehler/50bestehendeWarnungen,1m38s. Neue native SQLite-/MCP-Regressionsprobe für privaten Testkunden plus reguläre Sammellöschung kompiliert, mangels Cloud-Emulator/physischer Geräteausführung NICHT AUSGEFÜHRT. Keine APK erzeugt, signiert oder installiert; vorhandene Original89 bytegleich zum veröffentlichten SHA2568464cb9e314e76979edbee6b6bc5ddde8ef8fa5a453c2cec7bd12baadcf8adea. Die Korrektur liegt zunächst nur im Quellcode.
- P1 unverändert PASS:117Tools(52lesend/65schreibend),156UI-Aktionen,Contract899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813. Source-/Workspace-Privacy-Gates einschließlich ignorierter/untracked Dateien PASS,0Betriebsdatenseeds. Zusammengefasster technischer Nachweis outputs/qa-deletion-behavior.json; keine produktiven App-/Drive-Schreibaufrufe oder Rohdatenübernahme in diesem Auftrag.

> Diese Datei ist der fortlaufende technische Arbeitsnachweis für Codex Cloud.  
> Regeln: `AGENTS.md`  
> Vollständiger Auftrag: `CODEX-AUFTRAG-GARTENPIRAT.md`

## Produktive Update-APK 1.54.0 / 89 (20.10.2026)

- Nutzerauftrag: APK wie gewohnt erstellen; vorherige Beschränkung auf reine Codearbeit ausdrücklich aufgehoben. Aktuellen Produktivcode mit neuer Drei-Tab-Oberfläche, kompakter Erfassung und Kalender-Autorisierung bauen. Bestehender Checkout/Remote-main72f9691 bestätigt, alle531Main-Quelldateien vor Beginn gehasht; ausschließlich Versionsmetadaten auf1.54.0/89 erhöhen. Produktivpaket at.hausgarten.service und Originalzertifikat erhalten, keine Preview-APK und keine Geräteinstallation.
- Abnahme: Release-Build und Lint, Originalsignierung ausschließlich im geschützten persönlichen Cloud-Pfad über tools/sign_original_apk.py; Source-/Workspace-/tatsächliches Artifact-Privacy-Gate. Finale Paket-/Versions-/Zertifikats-/16KiB-Ausrichtungs-/ZIP-Prüfung, alle Mainassets bytegleich, UI_PREVIEW=false und erforderliche nativen Klassen/Provider prüfen. Vergleich mit Original88, keine zusätzliche Berechtigung oder Daten-/Schemaänderung. Vorhandene neun APKs erhalten; lediglich gewöhnlicher unsignierter Gradle-Ausgabepfad wird neu erzeugt, Vorgänger extern gesichert.
- Vorangegangene aktuelle Funktionsabnahme:326Python-Tests,33JVM-Suiten,44Browser-Suiten,55synthetische MCP-Szenarien PASS;37historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen. Diese tatsächlichen Prüfungen beziehen sich auf denselben Maincode vor der reinen Versionserhöhung, kein erneuter Gesamtlauf ohne Anlass behaupten. Aktuelles SDK/Build-/Originalsignier-/Lint-/Artefaktprüfergebnis folgt. Physischer Pixel-/Update-/Google-End-to-End-Nachweis separat offen.
- Bereitstellung als neue Datei im durch Metadaten bestätigten bisherigen privaten Codex-Cloud-Builds-Ordner. Original88 und Test88 bleiben erhalten; keine Freigabeänderung, kein Commit/Push, keine Geschäftsdaten-/Kontenaktion. Google-Drive-Skill für Dateilebenszyklus/Metadaten und abschließenden Readback angewendet.
- Erster Offline-Build stoppt vor APK/Signierung wegen fehlender Runtime-JARs der bereits eingeführten Google-Anmeldungsbibliothek (u.a. concurrent-futures/listenablefuture). Nachladen ausschließlich über vorhandenen Paketmanager-Proxy/CA-Trust und freigegebene offizielle Maven-Repositories; keine neue Domain-/Kontofreigabe und keine Codeänderung. Fehlerlog /tmp/qa-update89-build.log erhalten.43synthetische Originalsignier-Unitprüfungen erneut PASS.
- Release-Build, vollständiges Release-Lint und Kompilierung vorhandener Instrumentierungsquellen PASS:66Tasks/2m40s,0Lintfehler/50Warnungen. Keine native Geräteausführung. Beide fehlenden Runtime-JARs über vorhandenen freigegebenen Maven-Pfad nachgeladen. Tatsächliches unsigniertes APK36.147.448Bytes, SHA2560ae51a30e9e69e94e7ee02032eab08885f9c78274d5c1e266e3a3267bc3082e2. Quellfingerprint b05723a29302dfc98e8ac6a3aefcbaffd6cb5c0b1c5632fd6889df5761774aa1,alle531Main-Dateien gegenüber bestandener Funktionsabnahme unverändert.
- Unsigned-Artefaktprüfung PASS:Produktivpaket/1.54.0/89,min26/target37,nicht debuggable,UI_PREVIEW=false,Backup aus,keine Instrumentierung/sharedUserId,3produktive Provider,0zusätzliche Berechtigungen und alle Live-Komponenten weiterhin nutzbar.417Mainassets bytegleich,181Pflanzen/362Bilder,neue UI und Google AuthorizationClient im tatsächlichen APK. Zwei zusätzliche öffentliche SDK-ART-Profile strikt gegen jeweilige Gradle-Kompilata/magic/hash geprüft; erste temporäre reine Assetanzahlprüfung erkannte diese erwarteten SDK-Dateien, anschließend deren Herkunft bestätigt, keine Privacy-Gates abgeschwächt. Source-/Workspace-/Unsigned-Artifact-Gates PASS. Nachweis outputs/qa-update89-unsigned.json.
- Tatsächliche Originalsignierung und unabhängige Nachprüfung PASS:outputs/Haus-Garten-1.54.0-89-original-update.apk,36.155.140Bytes,SHA2568464cb9e314e76979edbee6b6bc5ddde8ef8fa5a453c2cec7bd12baadcf8adea. Genau ein Signierer,v2/v3,Originalzertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8 auch gegen erhaltene Original88 verglichen;16KiB-Ausrichtung/ZIP/417Mainassets/2SDK-Profile/BuildConfig-Fingerprint erneut PASS.0verbliebene private Signierordner. Alle acht übrigen vorhandenen APKs bytegleich, Vorgänger des regulär neu erzeugten unsigned-Ausgabepfads extern per Hash erhalten. Nachweis outputs/qa-update89-signed.json. Keine Geräteinstallation oder Update-/Datenerhalt-Abnahme daraus ableiten.
- Abschluss Privacy PASS am signierten APK:1190Quellen/0Betriebsdatenseeds,4603Workspace-Dateien einschließlich ignored/untracked,tatsächliches Artifact-SHA8464cb9e314e76979edbee6b6bc5ddde8ef8fa5a453c2cec7bd12baadcf8adea/0Seeds. Signier- und unabhängiger Nachprüfpfad erfolgreich,keine Schlüsselreste. Vorbereitender Offlinefehler und temporärer SDK-Profil-Abgleich separat erhalten, erfolgreicher finaler Build/Signatur-/Datenschutzbeleg maßgeblich.
- Privat bereitgestellt und zurückgelesen: https://drive.google.com/file/d/1hHgCQSG64R3jYN3TsdZlbMa0ibxI9OgQ/view?usp=drivesdk . Tatsächliche Uploadantwort und anschließende Metadaten bestätigen Name/MIME/36.155.140Bytes/bisherigen Codex-Cloud-Builds-Ordner/shared=false; authentifizierter vollständiger Streaming-Readback exakt36.155.140Bytes. Keine unabhängig lokal materialisierte Remote-SHA behauptet; lokale APK-SHA vor/nach Upload unverändert. Ausschließlich dauerhafte Drive-Datei-URL in Nachweisen, keine temporären Bearer-/Download-URLs oder Binärdaten gespeichert. Downloadindex auf Produktiv89 aktualisiert, Original88 und separate Test88 erhalten. Keine neue Freigabe, kein Commit/Push, keine Installation/Deinstallation/Datenlöschung oder betriebliche Konten-/Datenaktion. Pixel-/Update-/Datenerhalt-/echte Google-End-to-End-Abnahme weiter NOT EXECUTED.

## Einfache Oberfläche im Produktivcode, ohne APK (20.10.2026)

- Nutzerauftrag: Drei-Tab-Testkonzept erneut prüfen, verbessert in die bestehende Produktiv-App übernehmen. MCP erledigt die meisten Eingaben; lesende Übersicht zuerst, manuelle Erfassung nur ausdrücklich. Keine APK, Signierung, Installation oder Veröffentlichung. Bestehende neun APKs und Buildkonfiguration per Hash gesichert; tatsächlicher Remote-main und Checkoutbasis72f9691 verglichen, vorhandene Änderungen erhalten.
- Abnahme: genau drei Hauptbereiche Heute / Kunden & Gärten / Mehr; Planung und gespeicherte Arbeiten vollständig erreichbar. Laufende Arbeit vorrangig, kompakte Erfassung mit Originalformular und Originalvalidierung, eindeutige Rückkehr ohne Entwurfverlust; Foto-Recovery, Rechnungsempfänger und Timerabschluss erhalten. Keine Schreib-/Verbindungsaufrufe allein durch Navigation. Grün für Arbeits-/Speicheraktionen, Blau für Planung, Gelb für Prüfbedarf, Rot für Fehler/Löschen, immer zusätzlich Text. Keine technischen Rohdaten in Arbeitsübersichten. Mindestens48px Touchflächen,320/360/426px und150%Schrift, sichtbare untere Aktionen ohne Überdeckung. Alle117MCP-Werkzeuge und156UI-Aktionen sowie Datenverträge unverändert. Tatsächliche Geräteabnahme getrennt ausweisen.
- Umgesetzt: genau drei untere Hauptbereiche; Planung und gespeicherte Arbeiten als vollständige Unteransichten mit Rückkehr zum tatsächlichen Ursprung. More-Menü einheitlich gruppiert, Status/Einstellungen zuerst, Suche zentral; sichtbarer 48px-Seitenmenüknopf und geschützte Innenrandgeste bleiben erhalten. Optionale Hauptseitenwischgeste folgt denselben drei Bereichen, Datum/Kundenakte besitzen weiterhin ihre eigenen Gesten.
- Heute zeigt Datum, zwei Stundenübersichten, nächsten Einsatz und gespeicherte Arbeit; laufende/pausierte/offene Zeiterfassung bleibt vorrangig. Technischer Status unter Einstellungen bzw. Status. Doppelte Planungsschaltfläche entfernt, Planung und manuelle Erfassung in unterer Daumenzone. Gartenpirat standardmäßig über Menü, schwebender Schnellzugriff ausdrücklich einschaltbar und gerätelokal gespeichert; Pflanzen-, Foto-, Bewegungs- und Offline-Funktionen erhalten. Persönliche Markierung auf Heute heißt bezahlt?, markiert ✓ bezahlt; b.b. bleibt Geräteübersicht gespeicherter Arbeiten, keine Rechnungs-/billed-Verknüpfung.
- Erfassung als gemeinsamer unterer Dialog: genau das Originalformular, Zeit/Leistung/Notiz sofort, Foto/Sprache/Material/Abrechnung/Auftrag/Ort bei Bedarf. Abweichender Rechnungsempfänger bleibt bereits bei geschlossenen Extras lesbar. Untere Speichern-/Später-Aktionen, keine automatische Tastaturöffnung; Zurück schließt zuerst verschachtelte Auswahl und erhält danach Entwurf und Timer. Laufender Timer und gestoppter Abschluss verwenden unveränderte Original-IDs, Pausenwerte und Speicherpfade. Freigaben/Drive/Kalender/ChatGPT im Produktivcode bleiben nutzbar. Preview enthält nur noch ihre bisherigen Isolationstexte/-Sperren; das gemeinsame Design liegt vollständig unter main/assets, kein zweiter UI-Fachpfad.
- Korrigierte echte Fehler: Foto-Wiederholungsleiste wurde durch den ersten .actions-Treffer aus dem versteckten Recoverybereich gezogen. Haupt-, Verwerfen- und Timerabschlussleisten jetzt ausdrücklich am Submitbereich befestigt; Retry bleibt am Fotozustand. Wiederholtes Verschieben des Formulars beim Feldwechsel konnte Klicks verschlucken; identische Eltern werden nicht erneut beschrieben. Reentrante blur-/change-Ereignisse beim Dialogwechsel verursachten NotFoundError; Verschiebung nun geschützt. Bereits geöffnete Auswahldialoge werden bei später UI-Initialisierung nicht überlagert. Fehlerläufe und erster überholter Browserdurchlauf unter /tmp/qa-field-* erhalten; bestehende fachliche Prüfketten auf neue sichtbare Menüwege angepasst, keine Schutzasserts entfernt.
- Farben mit Text: Arbeitsstart/Speichern grün, Planung blau, Pause/Prüfbedarf/Kollision gelb, Fehler und destruktive Aktionen rot. Normales Beenden einer Zeitnahme neutral. Ungeprüfte/veraltete Verbindungen werden nicht als bestätigt grün angezeigt. Kontrastprüfungen der grünen/blauen/gelben/roten Kombinationen mindestens4,5:1; tatsächliche synthetische Screenshots von Heute, Menü und Erfassung geprüft.320/360/426px,150%Schrift, mindestens48px untere Schaltflächen ohne horizontales Überlaufen PASS.
- Abschluss tatsächlich PASS:326Python-Tests;37historische Tests mit echten QA-Schlüsselerzeugungen ausdrücklich ausgeschlossen.33JVM-Suiten, Cross-Language-Vertragsha899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813,117MCP-Werkzeuge/156UI-Aktionen unverändert. Alle44Browser-Suiten ausgeführt; nach Fehlerkorrekturen bzw. expliziten neuen Menüwegen erforderliche Nachläufe PASS, zusätzlich18gezielte Produktiv-UI-Proben.55synthetische MCP-stdio-Szenarien PASS. Android ausschließlich :app:compileReleaseJavaWithJavac,13Tasks/1m14s BUILD SUCCESSFUL; kein assemble/bundle/sign. UI-Quellreview,181Pflanzen/362Bilder, Vertrags-/Fixturegates PASS.
- Source-/vollständiges Workspace-Privacy-Gate PASS:1190Quelldateien,0Betriebsdatenseeds,4302Workspace-Dateien einschließlich ignoriert/untracked vor Kompilierung; abschließender Nachlauf nach Tests/Abschlussbericht ebenfalls PASS mit4285Dateien und unverändert1190Quellen/0Seeds. Neun bestehende APKs vollständig SHA256-bytegleich,0neueAPKs; Buildkonfiguration, Paket/Version und Previewmanifest/-Native-Isolation unverändert. Lediglich zwei bisher duplizierte Preview-UI-Overlays durch dünnen gemeinsamen Anschluss ersetzt. Technischer Nachweis outputs/qa-field-ui-production-source.json; Browserprotokolle outputs/integration-ui/, JVM outputs/integration-java/. Kein Commit/Push, Drive-Upload, produktiver Daten-/Kontozugriff oder Geräteaktion. Neue APK-/Releaseabnahme absichtlich NOT TESTABLE; tatsächliche Pixel-WebView-/Tastatur-/Kamera-/Benachrichtigungs-/Live-Kalender-/Drive-Abnahme NOT EXECUTED.

## Kalender-Autorisierung im Produktivcode, ausdrücklich ohne APK (20.10.2026)

- Neuer Nutzerauftrag: ausschließlich Produktivcode bearbeiten, noch keine APK. Screenshot zeigt CALENDAR_GOOGLE_AUTH_ERROR im Schritt Google-Freigabe; keine Aussage über konkrete Google-Ursache oder erfolgreiche Kalender-/API-Verbindung ableiten. Bild außerhalb Checkout belassen. Vorhandene APKs/Previewquellen per Hash gesichert; keine Preview-UI-Änderung, keine Paket-/Versionsänderung, assemble/bundle/Signierung/Installation/Drive-Veröffentlichung ausgeschlossen.
- Eingrenzung: Fehler entsteht im AccountManager-Zugang vor erster Kalenderabfrage. Ausnahmen werden bisher weitgehend generisch behandelt; nach erfolgreicher interaktiver Tokenanforderung wird der erhaltene Token verworfen und unmittelbar erneut ohne UI angefordert. Keine produktiven Daten oder Konten zur Diagnose abgefragt. Aktueller Remote-main unverändert72f9691.
- Abnahme: offizieller Google-AuthorizationClient statt generischer AccountManager-Kalenderanmeldung; Konto/angeforderte Rechte beim Fortsetzen unverwechselbar, vollständige Freigabe vor Kalenderzugriff prüfen, erhaltenen Zugang in derselben Operation verwenden und nur nach401 erneuern. Zusätzliche Busy-Freigabe fehlt: klare Abhilfe ohne stilles Abschalten oder lokale Verbindungsänderung. Abbruch/Rotation/Ablehnung/Netz/Google-App-Registrierung/fehlende Google-Dienste getrennt und redigiert behandeln. Hintergrund darf keine Zustimmung öffnen. Keine Token in Preferences, Snapshots, Entwürfen, Logs oder gesichertem Activity-Zustand. Bestehende Scopes, Kalender-/Termin-IDs, Marker/Pending-/Doppelanlageschutz und CAS erhalten.
- Referenzen: https://developer.android.com/identity/authorization und https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient . Aktuelle offizielle Anleitung und Google-Maven-Metadaten bestätigen play-services-auth22.0.0; kein neuer Server, Web-Login, Clientsecret oder zusätzlicher OAuth-Scope. Externe Android-OAuth-Zuordnung aus Paket und tatsächlichem Signatur-SHA1 kann durch Code nicht erteilt werden. Gezielte synthetische JVM-/UI-Prüfungen und reine Java-/Ressourcenkompilierung erlaubt; erfolgreiche Pixel-Freigabe ohne Gerät nicht behaupten.
- Umgesetzt: CalendarAuthorization als Google-Identity-Adapter mit expliziter IntentSender-Fortsetzung und geprüften grantedScopes. Setup verwendet das tatsächlich bestätigte Zugangsresultat; CalendarCredential hält es ausschließlich im Speicher derselben Konto-/Scope-Operation und erneuert erst nach401. Hintergrund liefert bei nötiger Zustimmung einen redigierten AUTH_REQUIRED-Fehler und öffnet keine UI. Google-Dienste/Konto/Registrierung/Netz/Abbruch/ungültige Rechte getrennt klassifiziert, rohe SDK-Texte verworfen. Bisherige OAuth-Scopes exakt erhalten; AndroidX wegen offizieller SDK-Abhängigkeit aktiviert, Manifestvergleich bestätigt0zusätzliche Berechtigungen.
- Vollständige Basisfreigabe vor Kalenderzugriff; fehlende optionale Busy-Freigabe meldet eigene Abhilfe und bietet bewusst Nur App-Kalender verbinden an. Kein stilles Deaktivieren oder vorzeitiges Speichern. Eigene Konto-/Scope-Snapshots, Activity-Generationen, gespeicherte Fortsetzungsmetadaten ohne Token und zusätzliche private Binding-Hash-Prüfung schützen Rückkehr/Neuerstellung und parallelen Kontowechsel; lokale Connect-/Disconnect-Abschlüsse gemeinsam serialisiert. Bestehende Kalender-/Termin-ID-, Marker-, SQL-/Versions- und Readback-Schutzpfade erhalten. Fehlende Busy-Freigabe nach explizit abgewiesenem401 blockiert keine spätere Kalenderanlage; unbekannter Antwortverlust bleibt gesperrt.
- Fehler nun verständlich direkt in der nativen Kalenderkarte sowie im gemeinsamen Status sichtbar, technische Kennung nur unter Details. Gespeicherte Setup-Fehler werden neben bestehender Kalenderbindung separat und redigiert gelesen. Native Google-Zustimmung und Gerätelebenszyklus nicht tatsächlich auf einem Gerät ausgeführt; dazu nur Implementierung/Kompilierung und synthetische Kernprüfungen nachgewiesen.
- Abschließend PASS:33JVM-Suiten einschließlich54neuer Scope-/Teilfeigabe-/Credential-/401-/Binding-/Retry-Prüfungen sowie unveränderter117Tools/P1-Hash899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813;156UI-Aktionen. Vier gezielte Browser-Suiten mit25Planungs-,elfStatus/Gesten-,achtNavigations- und sieben neuen Autorisierungsstatusprüfungen, insgesamt51PASS. Bestehende umfassende Python-/Browserläufe aus voriger Phase nicht als erneut ausgeführt zählen. Reine :app:compileReleaseJavaWithJavac-Kompilierung einschließlich Ressourcen PASS; keine assemble/bundle/package/sign-Aufträge. Source1187Dateien/0Seeds und ganzer Workspace4276Dateien einschließlich ignored/untracked PASS; neues Artefakt ausdrücklich nicht beauftragt, kein neuer Artefakt-/Release-PASS behauptet.
- Hashvergleich bestätigt alle neun vorhandenen APKs, drei Preview-Quelldateien und Preview-Generator unverändert;0neueAPKs. Produktivpaket/1.53.1/88 unverändert, keine Installation, Konten-/Google-Projekt-/Drive-Änderung, kein Commit/Push. Technischer Nachweis outputs/qa-calendar-authorization-source.json; Logs /tmp/qa-calendar-authorization-*. Erste Prüfläufe mit noch nicht aktualisiertem UI-Review scheiterten am korrekten Fingerprint-Gate, nach tatsächlicher Quellreview nachgeführt und vollständiger33er-Nachlauf PASS; keine Tests/Schutzgates abgeschwächt. Java-Dependencyzugriff über vorhandenen Proxy/CA-Trust, keine Egressänderung. Für externe Google-Projektprüfung aus bereits vorhandenem Original88 nur öffentlichen Zertifikatsfingerprint gelesen: Paket at.hausgarten.service, SHA1 16fc7a137e58ca31c71d706d472f29fb9f558a5a. Keinen Signierschlüssel gelesen/verwendet, kein Clientsecret erzeugt. Echte Google-Freigabe/Projektkonfiguration und Pixel-Endabnahme weiterhin NOT EXECUTED.

## Ausdrücklich beauftragte separate UI-Test-App (20.10.2026)

- Neuer Nutzerauftrag autorisiert ausdrücklich eine isolierte Side-by-Side-Testvariante und drei Haupttabs. Diese konkrete Ausnahme hat Vorrang vor bisherigen Regeln gegen eine zweite App; keine zweite operative Datenquelle und kein neues Repository. Produktivpaket/Installation und Original88-APK erhalten. Reiner Cloud-Workflow, keine automatische Installation.
- Abnahme: separate Paketkennung at.hausgarten.service.preview und sichtbarer Name Haus & Garten Test, keine Kollision mit bestehender androidTest-Paketkennung .test. Eigene Sandbox/Provider, leerer unveränderter Bootstrap, INTERNET in Preview entfernt, Live-Verbindungsaktivitäten und Hintergrunddienste deaktiviert, native Verbindungs-/Sync-Einstiege abgefangen. Keine Anbindung produktiver Konten/Ordner. Geschäfts-/SQLite-/MCP-Klassen bytegleich zur gesicherten Arbeitsbaseline außer ausdrücklich zusätzlicher UI-/Lifecycle-Isolation in MainActivity;117Tools/P1-Vertrag erhalten.
- Preview-Assets separat generiert: originale mobile.html/design-concept/daily-usability bleiben in main unangetastet. Nur Preview lädt drei Tabs Heute/Kunden & Gärten/Mehr-Einstellungen, untere Schnellaktionen und modale kompakte Zeit-/Leistungs-/Notizerfassung. Vorhandenes Originalformular und alle Saver/Validierungen/Entwürfe/Fotos weiterverwenden; keine zweite Erfassungslogik. Paketgebundene Foto-Allowlisten nur in generiertem Preview-Overlay anpassen. Status/technische Daten in Einstellungen, kontextuelle Warnungen erhalten.
- Prüfen: Initialisierung ohne Geschäftsschreiben, genau ein aktiver Tab, alle bisherigen Ziele weiterhin erreichbar, eine Formularinstanz/eine Speicherung, Fehler bleiben editierbar, Zurück/verschachtelte Auswahl/Entwurf und Timerabschluss,320/360/426px/150%Schrift/48pxTouch/untere Erreichbarkeit. Alle vorhandenen Python-/JVM-/Browser-/MCP-Szenarien sowie Preview-Build/Lint und fertiges signiertes Artefakt prüfen.37 historische QA-Schlüsselerzeugungstests weiterhin ausdrücklich ausgeschlossen; keinen Ersatzschlüssel erzeugen. Aktueller persönlicher Cloud-Signierpfad mit explizit auf .preview beschränkter Erweiterung, keine allgemeine Paketfreigabe. Physische Side-by-Side-Installation/Geräteabläufe ohne Gerät nicht behaupten.
- Umsetzung/Abnahme PASS:326Python-Tests/0Skips,41bestehende Browser-/JS-Suiten plus separate Preview-Suite mit14Szenarien;32JVM-Suiten und55synthetische MCP-stdio-Szenarien. Preview-Suite im gemeinsamen Browserrunner ergänzt;41Bestandssuiten und neue Suite getrennt ausgeführt, kein gemeinsamer42er-Lauf behauptet.43Original-Signier-Unitchecks einschließlich strikt begrenzter Preview-Freigabe PASS; reine Mock-Werkzeuge/keine Schlüssel erzeugt.37historische Schlüsselerzeugungstests ausdrücklich ausgeschlossen. Python3.12.14/32Pakete unterstützt/pip check PASS. Python-Gesamtlauf enthält Source1183Dateien/0Seeds, gesamten Workspace4262Dateien einschließlich ignored/untracked sowie tatsächliches Preview-APK-Privacy PASS.
- Preview-Szenarien bestätigen Originalformular genau einmal, keine Initialschreibvorgänge, gleicher Native-Saver/ein Schreibvorgang nach Fehler/Retry, Timerabschluss mit gemessenen Stunden und Sitzungs-ID, Entwurf/Notizen beim Zurück und erneuten Öffnen, verschachtelte Picker auch bei abweichender DOM-Reihenfolge, Inline-Kundenanlage zurück in denselben Entwurf sowie schmale/vergrößerte Ansichten. Vorhandene Vor-Ort-Funktionen über Mehr weiter erreichbar. Primäre technische Sync-Pille nach Einstellungen verschoben; laufende Zeiterfassung und fachliche Warnungen bleiben erhalten. Dekorativer Floating-Launcher nur in Preview ausgeblendet, Pflanzenmenü bleibt erreichbar.
- Echte Sichtprüfung fand aus verborgenem Foto-Recovery herausverschobene Werkzeugleiste der gemeinsamen Altansicht; Preview ordnet dieselben Schaltflächen wieder ihrem echten Recoverycontainer zu und respektiert hidden. Abrechnung/Kundenneuanlage in ausdrücklich geöffnetem Zusatzbereich, Notiz direkt neben Leistung. Keine Änderung der Produktiv-UI-Dateien. Früher Browserfehlstart war doppelte Cloud-URL-Umschreibung; korrekter nativer Testassetadapter verwendet. Erster Gradlelauf scheiterte an verbotenem Provider im SourceSet; auf konkreten generierten Pfad und bestehende Preview-Prebuild-Abhängigkeit korrigiert, keine AGP-Schutzoption deaktiviert. Fehlerlogs unter /tmp/qa-ui-preview-* erhalten.
- Finaler unsignierter Preview-Build/Lint PASS,0Lintfehler/49Warnungen; Instrumentierungsquellen für die bestehende Releasevariante nur kompiliert. Tatsächliches APK: Paket/Name/88/1.53.1-preview1, keine INTERNET-/sharedUserId-/Instrumentation-Angabe, alle sieben Live-Komponenten deaktiviert und alle drei Provider nur unter .preview.417Assets bytegleich zu den jeweils vorgesehenen Original-/Overlaydateien; keine fehlenden Katalogbilder/Module. Ausgangs529Dateihashes verglichen: ausschließlich MainActivity-UI-Isolation und UI-Katalogreview geändert; übrige Mainquellen einschließlich Geschäfts-/SQLite-/MCP-Klassen sowie Original88-/Releaseunsigned-APK bytegleich erhalten. Nachweis outputs/qa-ui-preview-unsigned.json. Keine Geräteaktion.
- Fertiges Artefakt: outputs/Haus-Garten-Test-1.53.1-preview1-88.apk,34.570.771Bytes,SHA256e38a325b0d6d534dfc68681645efb02cb7d295182b7ccc10e4276b5a9f2cdb24. Ausschließlich vorhandener Originalschlüssel über geschützten Cloud-Signierpfad mit --ui-preview; Zertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8,ein Signierer/v2-v3/16KiB-Ausrichtung/ZIP-Integrität/Artefaktprivacy PASS,0Signiertempordner. Kein Debug-/Ersatzschlüssel. Paket-/Provider-/Sandbox-/Live-Sperren und417Assets am signierten APK erneut geprüft; Original88 weiterhin SHAa37fb5bbb9972c2f6afd5eb0b4fade01841d034cbc2358aee9d2473606420238. Main-/Buildfingerprint d615902746ed492068a73dd58870bfeb3fc926bc1007cc128827593875ef6a98,Preview-Quellfingerprint efbb0587d1a3f6029bd214885e0782cf696ae133134c128e8bc3d5a54dfd6c5b;BuildConfig stimmt überein. Signierter Nachweis outputs/qa-ui-preview-signed.json.
- Zusätzlich privat im bestehenden Codex-Cloud-Builds-Ordner bereitgestellt: https://drive.google.com/file/d/1htZ2Hn1Lxq-o1AuacJsA7veVsq4TB1Ck/view . Name/MIME/Größe/Ordner/shared=false nach Upload bestätigt; vollständige authentifizierte Streamreferenz mit exakt34.570.771Bytes zurückgelesen. Keine unabhängige Downloadbyte-SHA behauptet. Downloadliste trennt separate Test-App und unveränderten Produktivkandidaten. Keine Installation/Deinstallation, Datenlöschung, Änderung produktiver Konten/Ordner, Commit/Push oder Sharingänderung. Tatsächliche Installation nebeneinander, Android-WebView-/Kamera-/GPS-/Benachrichtigungsverhalten auf physischem Pixel bleiben NOT EXECUTED; Live-Sync/Kalender/Tunnel sind in der isolierten UI-Testvariante bewusst gesperrt.

## Quellcode-ZIP für Gemini im vorhandenen Drive-Ordner (20.10.2026)

- Nutzerauftrag: aktuellen vollständigen Quellcode als ZIP im bestehenden Gemini-Drive-Ordner bereitstellen. Ordner über Drive-Suche und Metadaten eindeutig bestätigt. Die Suche lieferte zunächst nur Anleitung und Textkopie vom12.10.; direkte vollständige Ordnerlesung zeigte zusätzlich das ältere ZIP und dessen SOURCE-MANIFEST.json. Neuer Stand als eindeutig datiertes/versioniertes ZIP; sämtliche historischen Dateien bleiben erhalten. Keine Freigabeänderung oder zweite betriebliche Datenquelle.
- Exportierter App-Stand:1.53.1/88, Basiscommit72f96919d6a42ef8a2759c14c6da1df7359726d6 einschließlich aller aktuellen geänderten und neuen Quell-/Testdateien. Erwarteter App-Quellhash1dec4e6146dbb5decdba3d8775664b777f1cbe7cd27f44964aae4de4824f935f. Bestehenden Original-Exporthelfer verwenden; keine alternative Runtime und keine Veränderung des bereits leeren Bootstrap.
- Vor Upload verpflichtend: Source-, vollständiges Workspace- und tatsächliches ZIP-Artifact-Privacy-Gate; ZIP-Integrität und bytegleiche Vollständigkeit sämtlicher ausgewählter Quelldateien einschließlich neuer Status-/Gesten-/Kalender-/Wetterdateien prüfen. Build-Ausgaben, Git-Historie, Caches, lokale SDK-Konfiguration, Betriebsdaten und Schlüssel nicht exportieren.
- Vorhandene Gemini-Anleitung auf den aktuellen ZIP-Stand und die maßgeblichen Regeln/Statusdatei verweisen lassen; alte Textkopie, altes ZIP und dessen Manifest ausdrücklich als historischen Stand kennzeichnen. Keine neue Testausführung oder erfolgreiche physische Pixel-/Google-Abnahme durch den Export behaupten. Nachweisgrenzen und tatsächlich ausgeführte Tests stehen im folgenden Update88-Eintrag. Abschließender Upload-/Prüfnachweis wird im lokalen Status nach Export ergänzt.
- Aktuelles Ziel im selben privaten Ordner: https://drive.google.com/file/d/1Ewa2fhsDPZAzGEFG8hTZawcI_RsGjeJy/view . Erster vollständiger Upload/Streaming-Readback bestätigt; nach Korrektur des unvollständigen Suchbefunds werden ausschließlich die Archivdokumentation berichtigt und ZIP/Anleitung unter denselben aktuellen IDs ersetzt. App-Quellhash bleibt unverändert; abschließender Byte-/Privacy-Nachweis vor Ersatz erneut erforderlich.
- Abschließend PASS: Original-Exporthelfer mit Source-/vollständigem Workspace-/tatsächlichem ZIP-Artifact-Gate erneut durchlaufen;1176 Quelldateien einschließlich528 App-Dateien vollständig und bytegleich ausgewählt, sichere eindeutige ZIP-Pfade/CRC geprüft,0 Betriebsdatenseeds. Finales ZIP68505092Bytes, SHA2563342188e78ae8b70ef4d12a7c4a974f2017673a3f169aa55b151f374e053ff30; App-Quellhash unverändert1dec4e6146dbb5decdba3d8775664b777f1cbe7cd27f44964aae4de4824f935f. ZIP unter derselben aktuellen ID ersetzt; Drive-Metadaten bestätigen Größe/Ordner/privaten Status, abschließender vollständiger Streaming-Readback exakt68505092Bytes. Kein unabhängig lokal heruntergeladener Remote-SHA behauptet. Anleitung unter bestehender ID aktualisiert und aktueller Link/Hash/Größe sowie Kennzeichnung des Altmanifests zurückgelesen. Frühere Dateien unverändert erhalten. Archiv enthält den Status unmittelbar vor diesem nachträglichen Zustellbeleg; App-Quellen unverändert. Keine neue APK, keine Installation, keine Geschäftsdaten-/Kalender-/Sync-Aktion oder Freigabeänderung.

## Status, Wischmenü, Kollisionen und Papierkorb – Update 1.53.1/88 (20.10.2026)

- Während laufender Kalenderkorrektur zusätzlich beauftragt: verständliche mögliche Terminkollisionen, Statusmeldungen in eigenem Menüpunkt bündeln, linkes Wischmenü ohne Konflikt mit Android-Zurück und sinnvolle horizontale Navigation. Kalenderkorrektur bleibt Bestandteil derselben Umsetzung.
- Messbare Abnahme vor Umsetzung: lokale Zeit-/Anfahrtskollision und aktuell bestätigte externe Belegung getrennt anzeigen; Zeitraum und betroffenen Einsatz lesbar nennen. Keine zusätzliche Reservierung/Terminänderung, bestehende transaktionale Ablehnung beim Speichern bleibt maßgeblich. Veraltete/nicht verbundene/nicht freigegebene Google-Belegung niemals als frei anzeigen. Zeit ändern bzw. vorhandene Zeitfenstersuche ausdrücklich öffnen, Entwürfe erhalten.
- Status sammelt kurze lesende Zusammenfassungen und passende bestehende Einstellungswege; keine automatische Synchronisierung oder Freigabe beim Öffnen. Kritische Terminwarnungen und Fehlerrückmeldungen bleiben zusätzlich im fachlichen Kontext. Allgemeine Verbindungs-/Abgleichdetails auf Status bündeln, Hauptnavigation erhalten.
- Wischmenü mit sichtbarer Menüalternative, Rückkehr einen Schritt und Entwurferhalt. Äußerste System-Zurückzone ausdrücklich nicht übernehmen, keine Android-Gestenausschlussrechte. Öffnung aus innerem linken Randbereich; Formulare, Dialoge, Bild-/Scrollbereiche und vertikale Bewegungen schützen. Menü schließen per Zurück/Schließen/Linkswischen. Horizontale Tagsicht und vorhandene optionale Hauptnavigation mit eindeutiger Zuständigkeit, keine doppelten Wischaktionen.320/360/426px und150%Schrift, mindestens48px Touchflächen und keine Überdeckung prüfen.

- Neuer anschließender Nutzerauftrag: Papierkorb/Löschen und Sync prüfen sowie Datenlöschung in der gesamten App erleichtern. Umsetzung ausschließlich an Oberfläche/isolierten Tests; kein Auftrag zum Löschen eines konkreten produktiven Bestands. Abnahme: Papierkorb direkt erreichbar, nach Datenart filtern und neu lesen; Gesamtleerung unmissverständlich als gesamter Papierkorb mit vorhandener vollständiger Vorschau/Bestätigung kennzeichnen. Einzelwiederherstellung, aktuelle Versionshashes, Abhängigkeiten, geschützte Belege, stabile Retry-IDs und Drive-/Cloud-Belegtrennung erhalten. Keine automatische Kaskade, keine stillen Massenlöschungen oder Queue-Bereinigung. Vorhandene Mehrfachauswahl für Arbeiten leichter benennen/erreichen; übrige Datentypen über vorhandene Übersichten und Schutzregeln.

- Umgesetzt: Status als eigener Menüpunkt mit kurzen Drive-/Kalender-/Wetter-/Pixel-/ChatGPT-Zuständen, vorhandenen Einstellungswegen und letzter Sitzungsrückmeldung. Allgemeine Kalender-/Wetterzustände aus Heute/Planung dorthin gebündelt; einsatzbezogene Wetter-/Kollisionswarnungen bleiben sichtbar. Öffnen/Neu lesen startet keinen Sync oder Schreibaufruf. Bestehender Heute-Status öffnet die gemeinsame Übersicht.
- Gemeinsamer Gestenbesitzer: äußerste mindestens32CSS-Pixel bzw. tatsächliche Android-Systemgestenzone plus8dp bleiben frei; Menüöffnung erst im folgenden44px Innenrandbereich. Kein setSystemGestureExclusionRects. Modaler Drawer mit unteren Schließen-Knopf, expliziter Menüalternative, Zurück schließt zunächst nur Drawer. Planung besitzt die Datumswischgeste; Kundenakte wechselt Übersicht/Arbeiten/Fotos/Dokumente. Optionale Hauptseitenwischgeste gilt für Heute/Kunden/Arbeit, Formulare/Bild-/Eintragsaktionen bleiben geschützt. Keine doppelte Datums-/Hauptnavigation.
- Kollisionen: neue lesende gemeinsame Projektion für lokale Termine/Anfahrten und frische externe Busy-Zeitintervalle. Bei fehlender Verbindung, Pause, fehlender Busy-Freigabe, fehlerhaftem/zu altem/zu kurzem Cache bleibt Google-Belegung ungeprüft. Agenda-Badge und lesende Terminübersicht mit Zeitraum, eindeutigem Kunden/Einsatz, separatem Google-Hinweis und Aktionen Zeit ändern / Freie Zeit suchen; Editieren weiterhin ausdrücklich. Eingaben aktualisieren Warnung ohne Schreiben. Native Speicher-/CAS-/Overlap-Prüfung bleibt maßgeblich; pausierter Kalender nutzt alte Busy-Caches auch nicht mehr zum Blockieren oder für Vorschläge.
- Papierkorb/Sync: direkter Menü-/Drawer-Einstieg, Filter für alle unterstützten Datenarten einschließlich Termine, sichere Neu-laden-Aktion, deutlicher Gesamtscope vor Leerung. Lesefehler lässt Originalsätze erhalten und bietet Retry. Bestehende transaktionale Gesamtvorschau/Bestätigung, Hash-/Beziehungsprüfung, Wiederherstellung, geschützte Belege und unveränderte Retry-IDs erhalten. Mehrfachauswahl für Arbeiten heißt jetzt Mehrere in Papierkorb. Keine produktive Datenaktion.
- Bereits tatsächlich PASS:32JVM-Suiten, darin16Verfügbarkeits-,43Google-Zugriffs- und54Kalenderanlagechecks;25Planungs-,achtNavigations- undelfStatus-/Wisch-/Kollisionschecks sowie23Papierkorb-/Sync-Browserprüfungen. Zwei neue native Pausen-/Busy-/Quota-Proben ergänzt, nur Kompilierung hier möglich. Neuer UI-Test fand einen echten Renderabbruch bei noch nicht initialisierter lokaler Editorzeit; lesende Belegungsprüfung fängt unvollständige Eingabe nun ab und verwendet gültige gespeicherte Zeit als Ausgang. Ursprünglicher Fehlerlauf /tmp/qa-calendar-repair-planning.log erhalten; kompletter gezielter25er-Nachlauf PASS. Bestehende Tests auf verlegten Status und neue exakte Navigation angepasst, fachliche Assertketten erhalten. Finaler Gesamtlauf und APK-Nachweis folgen.

## Kalenderverbindung nach Pixel-Rückmeldung – Korrektur in 1.53.1/88 (20.10.2026)

- Der Screenshot der neuen Kalenderansicht zeigt den allgemeinen Verbindungsfehler, keinen tatsächlichen Google-Fehlercode. Keine Konten, Kundendaten oder produktiven Kalender gelesen/geändert. Bild außerhalb des Checkouts belassen. Regeln/Zentralauftrag/Status und Signierpfad aus derselben Sitzung berücksichtigt; Remote-main weiterhin72f9691, keine weiteren Branches.87 und sämtliche bisherigen Änderungen erhalten.
- Abnahme vor Eingriff: bekannte Google-Ablehnung, abgelaufene Freigabe, Kontingent/Netzfehler, abgebrochene Zustimmung und unsichere Kalenderanlage getrennt und ohne rohe Provider-/Kontotexte erklären. Ein eindeutig abgewiesener Anlageversuch darf einen späteren Versuch nicht dauerhaft blockieren; bei Antwortverlust weiterhin keine zweite Anlage.401 einmal mit neuem Token wiederholen,403-Kontingent mit bestehendem Backoff behandeln. Konto-/Kalender-/Terminzuordnungen und bewusste EIN/AUS-Zustände erhalten. Keine zusätzlichen Berechtigungen/Scopes oder automatische Kontoumstellung.
- Zunächst nachgewiesen: alle403 werden als Freigabefehler gewertet; abgewiesene POST-Antworten lassen pending=true ohne ID zurück. Fehleranzeige verdeckt die eigentliche Phase/Ursache. App-Kalender darf laut offizieller API weiter mit vorhandenem calendar.app.created angelegt werden; kein Anlass für pauschalen Kalender-Vollzugriff. Referenzen: https://developers.google.com/workspace/calendar/api/v3/reference/calendars/insert und https://developers.google.com/workspace/calendar/api/guides/errors. Fehlende externe Google-Projekt-/Kontofreigabe kann nicht durch Code vorgetäuscht werden. Optional nach beobachtetem Freigabedialog gefragt; Implementierungsarbeit läuft unabhängig weiter.

- Nutzerantwort: Google-Freigabedialog erscheint; Fehler kommt danach. Einrichtung zeigt nun den tatsächlich erreichten Schritt und redigierte Abhilfe, ausgewähltes Konto bleibt von erfolgreicher Verbindung getrennt. Sichere Fehlerkennung für OAuth-Registrierung/API deaktiviert, verweigerte Rechte, Kontingent, Netz und unbestätigte Anlage; maschinenlesbarer Code über vorhandene Integration lesbar, keine rohen Google-Texte/IDs/Tokens.401 mit einmaliger echter Tokenerneuerung,403-Kontingent mit Retry-After/Backoff. Eindeutig abgewiesene neue Anlage darf erneut versucht werden; Antwortverlust und ungeklärte Altversuche bleiben gegen Doppelanlage gesperrt. Bestehende Scopes/Berechtigungen unverändert. Erfolgreiche echte Pixel-/Google-Verbindung weiterhin nicht nachgewiesen.
- Finale Cloud-Nachweise bisher:325Python-Tests ohne Skips (37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen),32JVM-Suiten,55MCP-stdio-Szenarien und vollständiger41er-Browserlauf PASS. Nach abschließender Vereinheitlichung der optionalen Hauptseitenwischgeste gezielter Alltagstest und elf Status-/Wisch-/Kollisionsprüfungen erneut PASS. Alte Alltagsfixture bekam ausschließlich für den zusätzlichen Planungs-Lesetest einen temporären lesenden Adapter; ursprüngliche Kunden-/Arbeits-/Legacy-Schreibkanäle danach wiederhergestellt. Vollständige Erstlauf-Logs unter outputs/integration-ui; gezielte Nachläufe /tmp/qa-calendar-repair-daily-final2.log und /tmp/qa-calendar-repair-status-gestures-artifact.log. Kein erneuter vollständiger41er-Lauf nach dieser letzten gezielten Änderung behauptet.
- Finale Version1.53.1/88: Release, Android-Testquellen-Kompilierung und Lint PASS,0Lintfehler/49Warnungen. Aktuelle Quelle stimmt mit BuildConfig überein:1dec4e6146dbb5decdba3d8775664b777f1cbe7cd27f44964aae4de4824f935f, Basis72f96919d6a42ef8a2759c14c6da1df7359726d6,source_dirty=true.117Tools/156Aktionen und P1-SHA899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813 erhalten. Synthetische Status-/Drawer-/Kollisionsbilder visuell geprüft. Originalsignatur- und Artefaktabschluss unten dokumentiert; keine produktiven Daten-/Pixel-/Kontoaktionen, keine automatische Installation/Commits/Pushes.

- Originalsignierte88 neu privat bereitgestellt: [Haus-Garten-1.53.1-88-original-update.apk](https://drive.google.com/file/d/1fNqMArnpB0zmqnlrFjNZTj_X-KFAzZb-/view?usp=drivesdk),34.566.542Bytes,SHA256 a37fb5bbb9972c2f6afd5eb0b4fade01841d034cbc2358aee9d2473606420238. Genau ein vorhandener Originalsigner a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8. Paket/Zertifikat/höherer Code gegen87, ZIP-Integrität und16-KiB-Ausrichtung PASS;415Assets bytegleich,181Pflanzen/362Bilder,elfKalender-/Wetterklassen und eingebundener navigation-status.js-Einstieg im tatsächlichen APK geprüft. Source-/gesamter Workspace-/Signed-Artifact-Privacy PASS,0Betriebs-Seedzeilen;0private Signiertempordner. Nachweise outputs/qa-control-centre-original-apk.json und outputs/qa-control-centre-validation.json.
- Privater bestehender Codex-Cloud-Builds-Ordner; neue Datei,87/86/85/84 und Sharingrechte unverändert. Name/MIME/Ordner/34.566.542Bytes/shared=false nach Upload zurückgelesen; vollständige authentifizierte Streamreferenz gleicher Größe erhalten. Keine Binärdaten/Referenztoken gespeichert oder unabhängige Remote-Byte-SHA behauptet. downloads/README.md aktualisiert. Keine produktive Löschung, kein nativer Pixelzugriff oder Installation. Tatsächliche Ursache des Pixel-Googlefehlers, erfolgreiche Google-Verbindung, physische Wisch-/Back-Abnahme und Update-Datenerhalt weiterhin offen; keine Gerätefreigabe aus Cloudtests abgeleitet.

- Abschließendes Gate nach Bereitstellung/Dokumentation erneut PASS: Source1.177Dateien/0Betriebs-Seedzeilen, gesamter Workspace2.967Dateien einschließlich ignored/untracked, Signed-Artifact-SHA unverändert a37fb5bbb9972c2f6afd5eb0b4fade01841d034cbc2358aee9d2473606420238/0Betriebs-Seedzeilen. Quellfingerprint unverändert, Git-Whitespaceprüfung bestanden,0private Signiertempordner. Umsetzung/privater Kandidat abgeschlossen; physische Pixel-/Google-/Update-Abnahme bleibt getrennt offen.

## Kalender und einheitliche Menüs – Update 1.53.0/87 bereitgestellt (20.10.2026)

- Neuer Nutzerauftrag: Kalendereinbindung, einfache einheitliche Menüstruktur, kleine Hände und erreichbare Bedienelemente. AGENTS/Zentralauftrag/Status sowie Cloud-Regeln berücksichtigt; bestehende86-Änderungen/Original-APK bleiben erhalten. Tatsächliche Architektur: bestehende HTML-/JS-WebView, native Android-Adapter und gemeinsame Owner-/SQLite-/Drive-Geschäftsschicht. Keine neue App, UI-Bibliothek oder parallele Datenhaltung.
- Entscheidung/Abnahme vor Umsetzung: fünf direkte untere Ziele Heute/Plan/Kunden/Arbeit/Menü; damit vorhandene Arbeits-/Kundenwege erhalten und Planung ohne Umweg. Eine zentrale Zuständigkeit für aktive Navigation/Zuordnung, einheitliche große Listenzeilen, verständlich gruppiertes Menü. Daten/Verbindung/Sicherung/Papierkorb unter Einstellungen erreichbar. Seltene Detailfelder bleiben geschlossen. Häufige Planungsaktionen und Zurück nahe am unteren Rand, mindestens48px Touchflächen, keine Überschneidung mit System-/App-Navigation,320/360/426px und150%Schrift ohne horizontalen Überlauf. Bestehende Geschäftsfunktionen, IDs, Offline-/Sync-/Entwurfs-/CAS-Schutz und117Tools/156Aktionen erhalten.
- Kalender-Abnahme: sichtbarer Verbindungszustand und eindeutiger Einstieg ohne versteckte Kontoauswahl; bestehende Google-Gerätefreigabe beibehalten. Kalender EIN/AUS sofort und versionsgesichert speichern; Pause entfernt keine Termine oder Verbindung. Fehlende/abgebrochene Google-Freigabe nicht als Verbindung melden. Eigener App-Kalender, keine Einladungen, keine automatischen Terminänderungen, CAS/ETag/Antwortverlustschutz erhalten. Native echte Kontozustimmung nur am Gerät; keine fremden Kalenderdaten oder Secrets lesen/ausgeben. Gefundener Fehler: bisherige AUS-Checkbox speichert erst über den allgemeinen Speichern-Knopf.

- Implementiert: bestehendes workday-ui bündelt fünf Hauptziele und aktive Navigation; Planung ist unten direkt erreichbar, Kalender-/Wetterstatus plus gemeinsamer Setup-Einstieg. Das Menü nutzt einheitliche Zeilen in Aufträge & Stunden / Angebote & Rechnungen / Fotos & Pflanzen / Einstellungen; doppelte Sync-Menüaktion entfernt, Synchronisierung über vorhandene Einstellungen erhalten, Papierkorb dorthin verschoben. Bestehende Eventhandler der übrigen Ziele erhalten. Getrennte Haupt-/Detailnavigation, Zurück am unteren Rand in Einstellungen/Listen, vorhandene Auftragssuche mit erreichbarer Zurück-Aktion. Tatsächliche Navigationshöhe über ResizeObserver statt starrem Offset; bei offener Terminerfassung Hauptleiste ausgeblendet und Speichern/Zurück unten erreichbar.
- Entwurfs-/Bedienkorrektur: Android-Zurück aus Terminübersicht/-editor führt zuerst zur Planung; Eingaben vor Verlassen behalten. Menüwechsel öffnet beim Wiederkehren die Übersicht mit expliziter Entwurfsfortsetzung. Eine sichtbare Kalendereinrichtung pro Planungsansicht, keine doppelte Setup-Aktion. Seitliches Wischen bleibt optional und folgt der neuen Hauptreihenfolge; bestehende Randgesten-/Formularschutzregeln bleiben aktiv.
- Native Kalender-/Wettereinrichtung neu in lesbare Bereiche gegliedert: Kalenderzustand und Google-Konto, eigener Kalender und Einladungsverhalten sichtbar; Wetter separat, Konto-/Belegtzeit- und Schwellenfelder nur auf Wunsch. Verbindungs-/Abgleichaktion sowie Zurück im unteren festen Bereich. Calendar-EIN/AUS speichert sofort mit vorhandener CAS; AUS pausiert ohne Verlust von Konto, Kalenderzuordnung oder Terminen. Kontoauswahlabbruch stellt den bisherigen Zustand wieder her. Kalender-App kann über den Standard-Android-Intent geöffnet werden; keine READ/WRITE_CALENDAR-Berechtigung hinzugefügt.
- Zusätzlicher Kalenderfehler behoben: Google-Kalender-ID wird nach erfolgreicher Anlage vor dem ersten Rücklesen gesichert. Bei Rückleseverlust und verzögerter Kalenderliste nach Neustart denselben eigenen Kalender nachladen statt dauerhaft ohne ID festzuhängen oder erneut anzulegen. Neuer synthetischer Providerfehlerfall; CAS/ETag/Marker/keine-Einladungen-Regeln unverändert.
- Gezielt bereits bestanden:25Planungsprüfungen, bestehende einfache Designabläufe, acht neue Navigations-/Erreichbarkeitsprüfungen (320/360/426px,150%Schrift, alle Ziele mindestens48px, feste Planungsaktionen ohne Überdeckung, Modal-Zurück und Entwurferhalt ohne Schreibaufrufe). Native Quellkompilierung bestanden; zwei weitere isolierte Pausen-/Resume-/CAS-/fehlende-Zustimmung-Proben ergänzt. Vollständige finale Regression/Release läuft. Bestehende Navigationstests bewusst auf die neue exakte Fünferstruktur umgestellt, fachliche Tests/Schutzasserts erhalten; keine pauschale Lockerung.
- Visuelle Prüfung fand eine spätere alte Viererspalten-CSS-Regel: das fünfte Ziel brach zunächst um und überdeckte den Kundenfußbereich. Zwei bestehende Browserabläufe meldeten deshalb korrekt einen nicht anklickbaren Bearbeiten-Knopf. Zentralen Fünferspalten-Selektor präzisiert, Auftragstoolbar auf drei tatsächliche Bedienelemente angepasst; neue Erreichbarkeitsprüfung verlangt nun zusätzlich alle fünf Ziele in derselben Zeile. Gezielte Navigation, Kundenprofil/Bearbeiten und vollständiger UAT-Durchlauf danach PASS. Erste Gesamtlauflogs unverändert unter /tmp/qa-calendar-menu-browser-first-pass erhalten; finaler Gesamtlauf separat.
- Weitere tatsächlich abgeschlossene Cloud-Prüfungen:325Python-Tests ohne Skips (37 historische Schlüsselerzeugungstests ausdrücklich ausgeschlossen),30JVM-Suiten einschließlich52Kalenderanlage- und54Wetterablauffällen,55synthetische MCP-stdio-Szenarien.117Tools/156Aktionen und gefrorener P1-Vertrag bestanden. Google-Kontofreigabe, eigener echter Kalender, nativer Pixel und Update-Datenerhalt hier weiterhin nicht ausgeführt; die neuen nativen Proben sind Kompilierungsnachweise.
- Finaler erneuter Gesamtlauf: alle40Browser-/JS-Suiten vollständig PASS, einschließlich der acht neuen Navigationsprüfungen,25Planungsprüfungen, Kundenprofil/Bearbeiten, UAT, Offline-/Sync-/Papierkorb-/Rechnungsabläufen. Finale synthetische Menü-/Planungsscreenshots visuell geprüft. Release1.53.0/87, Android-Instrumentierungsquellen und Release-Lint erfolgreich;0Lintfehler/47Warnungen. Native Layout-/Geräteausführung bleibt unbewiesen. UI-Quellreview und Katalog181Profile/362Bilder bestanden; P1 unverändert899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813. BuildConfig stimmt mit aktuellem Quellhash62399cc8a66a52d670b6a1cf84524b6a13bd2c14e418ac2cd30b6efdcaadba8c überein, Basis72f96919d6a42ef8a2759c14c6da1df7359726d6/source_dirty=true. Keine automatischen Commits/Pushes oder Geräte-/Kontofreigaben.

- Original-APK87 tatsächlich über tools/sign_original_apk.py signiert und privat bereitgestellt: [Haus-Garten-1.53.0-87-original-update.apk](https://drive.google.com/file/d/1j07rXSIega7rvaWfQdYEv077gqV03Sm_/view?usp=drivesdk),34.554.181Bytes,SHA256 93cbaf8f21e3d4e3bd7e8f3311f880ce86f00043dc266ef8b03c03384cdb9aa1. Genau ein vorhandener Originalsigner a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8; Paket/Zertifikat und höherer Code gegenüber86 bestanden. ZIP-Integrität,16-KiB-Ausrichtung,414Quellassets bytegleich,181Pflanzen/362Bilder und neun Kalender-/Wetterklassen im DEX geprüft. Source-/gesamter Workspace-/Signed-Artifact-Privacy PASS,0 operative Seedzeilen;0 private Signiertempordner. Nachweise outputs/qa-calendar-menu-original-apk.json und outputs/qa-calendar-menu-validation.json.
- Bereitstellung als neue Datei im bestehenden privaten Codex-Cloud-Builds-Ordner;86/85/84 und Sharingrechte unverändert. Name/MIME/Ordner/34.554.181Bytes/shared=false nach Upload zurückgelesen; vollständige authentifizierte Streamreferenz gleicher Größe erhalten, keine Binärdaten/Referenztoken in Reports und keine unabhängige Downloadbyte-SHA behauptet. downloads/README.md aktualisiert. Kein physischer Pixelzugriff, keine produktive Konto-/Datenänderung oder Installation; Google-/Geräte-/Update-Datenerhaltabnahme bleibt offen. Kalenderpfad am Gerät: Plan → Einrichten → Google Kalender verbinden; danach eigene Google-Zustimmung erteilen. Keine neue Android-Kalenderberechtigung.

- Abschließendes Gate nach Dokumentation/Bereitstellung erneut PASS: Source1.171Dateien/0Betriebs-Seedzeilen, Workspace2.930Dateien einschließlich ignored/untracked, signiertes Artefakt unveränderteSHA93cbaf8f21e3d4e3bd7e8f3311f880ce86f00043dc266ef8b03c03384cdb9aa1/0Betriebs-Seedzeilen. Quellfingerprint unverändert, Git-Whitespaceprüfung bestanden, Signiertempordner0. Keine physische Installationsfreigabe aus diesen Cloud-Nachweisen abgeleitet.

## Adresssuche nach Pixel-Rückmeldung – 1.52.4/86 (20.10.2026)

- Nutzerbilder zeigen: Einsatzadresse ist vorhanden, Ortsprüfung meldet dennoch nicht gefunden. Bilder und reale Adress-/Kundentexte ausschließlich außerhalb des Checkouts belassen, keine produktiven Daten gelesen/geschrieben oder als Fixtures verwendet. Bestehende85-Änderungen und APK erhalten; AGENTS/Zentralauftrag/Status und Cloud-Regeln erneut berücksichtigt.
- Messbare Abnahme vor Eingriff: getrennte/zusammengeschriebene Straßensuffixe gleichwertig erkennen, Hausnummer/PLZ weiterhin exakt prüfen. Ist nur die Hausadresse nicht auffindbar, separat eindeutig angegebenen Ort samt PLZ für eine sichtbar als solche bezeichnete Ortsvorhersage suchen. Kein fremder Kunden-/Auftragsort und keine geschätzten Grundstückskoordinaten. Regionale Genauigkeit in App und Meldung erklären; unbekannte/mehrdeutige Orte bleiben unbekannt. Alte fehlgeschlagene Suchen nach Update neu versuchen; Jetzt prüfen wiederholt bewusst eine fehlgeschlagene Suche ohne Stundenwartezeit. Positive Koordinaten und Betriebsdaten unverändert.
- Bestehende Originalsignatur/Paketkennung,117Werkzeuge/156Aktionen/P1-Vertrag und Privacy-Gates erhalten. Keine neue Berechtigung, Kosten oder zusätzlicher Dienst; Tests mit synthetischen Varianten und simulierten Android-Antworten. Reale Android-Geocoderantworten und Pixel-Systemprüfung nur bei tatsächlich vorhandenem Zugriff als bestätigt melden.

- Konkrete Ursachen im Quellcode reproduziert: Straßenwort und Suffix wurden wortweise verglichen; getrenntes „QA Muster Straße“ wurde gegenüber „QA Musterstraße“ abgelehnt. Negative Ergebnisse blieben eine Stunde gültig; manuelles Jetzt prüfen löste zuvor nur unavailable, nicht not_found/ambiguous. Außerdem öffnete Jetzt prüfen aus der gespeicherten Übersicht den Editor. Alle drei Fälle mit synthetischen Regressionen adressiert.
- Umsetzung1.52.4/86: Suffix-Schreibweisen vereinheitlicht, Hausnummer/PLZ nicht aufgeweicht. Zweite gezielte Suche nur für ausdrücklich geschriebene PLZ+Ort; nummerierte Hauskandidaten und mehrdeutige/fremde Orte abweisen. Ortsvorhersage als locality_resolved mit eigener Gebietsanzeige und Genauigkeitshinweis, auch in Übersicht/Liste/Pixel-Meldung. Fachliche Einsatzadresse und GPS-/Kundenzuordnung bleiben identisch. Insgesamt weiterhin höchstens drei Android-Suchen/24Sekunden pro Durchlauf; Budget-Fortsetzung als pending ohne Stundenblock. Erkennungsregelversion2 lässt alte negative85-Caches einmal neu suchen, positive Punkte bleiben erhalten. Jetzt prüfen setzt fehlgeschlagene Suchen bewusst auf ausstehend; keine automatische Formularöffnung.
- Erste Prüfungen: 30 JVM-Suiten bestanden; Wetterablauftests von32 auf54 erweitert, insbesondere Suffixe, getrennte Ortsabfrage, fremde PLZ/Ort, Mehrdeutigkeit, Hausnummer, Budget, Cache und sichtbare Pixel-Genauigkeit. 24 Planungsprüfungen am ersten Lauf bestanden, einschließlich Ortsvorhersage ohne Änderung des Einsatzortes bei320/360/426px und150%Schrift. Zusätzliche25.Prüfung schützt Übersicht vor Jetzt-prüfen-Editoröffnung; vollständige Endregression läuft. Zwei weitere native Cache-/Regelversions-/Orts-/Retry-Proben erstellt und kompiliert; hier kein Emulator/Pixel-Geocoder verfügbar, deshalb keine echte Adressantwort als PASS behauptet.

- Finale gezielte Prüfungen bestanden: 30 JVM-Suiten, 54 Wetterablauffälle sowie25Planungs-Browserprüfungen einschließlich Jetzt-prüfen-Übersichtsschutz. Finaler Python-Lauf325PASS, keine Skips; historische37QA-Schlüsselerzeugungstests weiterhin ausdrücklich ausgeschlossen. 55MCP-stdio-Szenarien bestanden. Operative Verträge unverändert117Tools/156Aktionen/P1-SHA899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813. Gesamt-Browserlauf und finaler Original-APK-Nachweis folgen; keine echten Kundentexte/Bildbytes in Tests/Repository aufgenommen.

- Vollständige finale Regression bestanden:325Python,30JVM-Suiten mit54Wetterablauffällen,39Browser-/JS-Suiten einschließlich25Planungsprüfungen,55MCP-Szenarien. Android-Release gebaut, Instrumentierungsquellen kompiliert, Lint0Fehler/46Warnungen. 181Pflanzenprofile/362Bilder und414Quellassets erhalten; UI-Review und Quell-/Workspace-/Artefaktgates bestanden. Quelle72f96919d6a42ef8a2759c14c6da1df7359726d6, Quell-SHA635d67e7dcd2ae74285f6ee1d537d9ee02eb13f5714f5880d11d67beafb39b65, source_dirty=true. Kein automatischer Commit/Push. Technischer Nachweis outputs/qa-weather-location-validation.json.
- Grenzen weiter ausdrücklich offen: tatsächliche Android-Geocoderantwort am Pixel, physische Systembenachrichtigungen/Doze,86Installation und Datenerhalt. Die Screenshots belegen den85-Fehler, keine86-Geräteabnahme. Nur synthetische Adressvarianten/Antworten getestet; echte Einsatzadresse und Screenshotbytes nicht ins Repository, Testfixtures, Logs oder APK übernommen. Keine Änderung an produktivem Kunden/Termin oder dessen Koordinaten, keine neue Kontofreigabe/Berechtigung oder Gerätesteuerung.

- Originalsignierter Kandidat86 erstellt und [privat bereitgestellt](https://drive.google.com/file/d/10FTnNzcVQzGIHCgw5eerdBI5WtW8DXDy/view?usp=drivesdk): outputs/Haus-Garten-1.52.4-86-original-update.apk,34.554.181Bytes,SHA256f6fd6f92e00e56ada2b5331706d4a4245fe8c9aa9bc171a1d6483e27277a51e5. Paket at.hausgarten.service, Originalzertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8. Paket/Signatur/höherer Code gegen erhaltenen85 bestanden; ZIP-Integrität,16-KiB-Ausrichtung,414Assets bytegleich und Wetterklassen im DEX geprüft. Signiert ausschließlich tools/sign_original_apk.py, geschützte Variablen nur auf Bereitschaft geprüft;0verbleibende Signiertempordner. Kein Ersatzschlüssel/CI-/Gerätezugriff.
- Neue Datei im bestehenden privaten Codex-Cloud-Builds-Ordner,85und84 erhalten, keine Sharingänderung. Name/MIME/Größe/Ordner und shared=false nach Upload zurückgelesen; vollständige authentifizierte Streamreferenz mit identischen34.554.181Bytes vorhanden. Keine unabhängige SHA der Downloadbytes behauptet. downloads/README.md aktualisiert; nach Update einmal bewusst Jetzt prüfen, ohne Geräteinstallation auszulösen. Kandidat ohne reale86-Geocoder-/Benachrichtigungs-/Datenerhaltabnahme.

- Abschließendes Gate am signierten86-APK erneut PASS: Source1.170Dateien/0Betriebsseed, gesamter Workspace2.940Dateien einschließlich ignored/untracked, signiertes Artefakt identische SHA und0Betriebsseed. Unveränderter Quellfingerprint separat abgeglichen, Whitespaceprüfung bestanden; keine Geräteinstallation.

## Wetterwarnungen, Einsatzgebiet und Pixel-Meldungen – 1.52.3/85 (20.10.2026)

- Regeln/Anweisungen vor Umsetzung gelesen; vorhandene 84-Änderungen und Original-APK erhalten. Neuer Auftrag: einfache Aktivierung, klar erkennbare Wetterwarnungen und Pixel-Benachrichtigungen. Bestehender Pixel/Drive-/MCP-Pfad, kein zusätzlicher Wetterdienst, keine Kosten oder neue Berechtigung.
- Öffentliche GeoSphere-Lizenz, NWP-/INCA-Metadaten, Punktprognosen und Warnantworten mit synthetischem Ort 48.2/16.3 in dieser freigegebenen Cloud tatsächlich HTTP 200 gelesen. Reproduzierter Fehler: NWP bietet `2t,tp,10fg` und Intervallniederschlag statt der bisher abgefragten `t2m,rain_acc,ugust,vgust`; reale Temperatureinheiten ebenfalls bisher nicht unterstützt. Alte Aufgabe bleibt als historischer nicht abgenommener Stand erkennbar.
- Abnahme vor Änderung: aktuelle NWP-/INCA-Parameter und Einheiten korrekt normalisieren; Testdaten/alte Cacheformate kompatibel, fehlende/veraltete Daten niemals als Entwarnung. Einfache Wetter-Einrichtung ohne Google-Konto/API-Schlüssel, ausdrückliches AUS erhalten; Pixel-Berechtigung/App-/Kanal-Sperre klar zeigen; Testmeldung ausdrücklich erreichbar. Warnungen nennen Risiko, Kunde und Einsatzzeit, öffnen genau den Einsatz, keine automatische Terminänderung. Keine verlorene Meldung bei verweigerter Berechtigung, keine Wiederholungsflut, Ruhezeit und bestehende Kenntnisnahme erhalten. Bestehende117MCP-Werkzeuge/156UI-Aktionen und Privacy-Verträge erhalten.

- Zusätzliche Nutzersteuerung: Wetter erkennt das Einsatzgebiet nicht. Abnahme ergänzt: Terminort → zugehöriger Auftragsort → eigene Kundenadresse; vorhandene bestätigte Koordinaten bevorzugen. Gleichwertige Schreibweisen der eigenen Adresse erkennen, Koordinaten nie von einem anderen Grundstück übernehmen. Adressauflösung nur im Hintergrund über den vorhandenen Android-Geocoder, ohne GPS-Zugriff oder neue Berechtigung; nicht eindeutige Treffer bleiben offen. Adresse/Kunde bleiben unverändert, Gerätecache getrennt vom fachlichen Journal. Ergebnisse mit Einsatzgebiet und verständlichem Fehler anzeigen.
- Umsetzung: aktuelle NWP-Intervallsummen/Skalarböen und reale Einheiten unterstützt; Altformat kompatibel. Automatische Adressauflösung mit höchstens drei neuen Suchen je Durchlauf, acht Sekunden je Suche, Fehlerpause und bewusstem Neuversuch; sichere Ortsreihenfolge und adressabhängiger Cache. Wetterstatus/Pixel-Freigabe direkt in Heute/Planung, unbekanntes Wetter sichtbar; Eingabefelder bleiben geschlossen. Wetter-/Pixel-Schalter speichern sofort mit bestehendem CAS, Systemfreigabe bei bewusster Aktivierung, Testmeldung und Systemeinstellungen erreichbar. Einzelmeldungen nennen Risiko/Kunde/Zeit; Einsatz, Alternativsuche und exakte Kenntnisnahme erhalten. Kanal-/App-/Berechtigungssperre verbraucht keinen neuen Hinweisbeleg; Ruhezeit und Versionsvergleich erhalten.
- Erster gezielter Nachweis: 30 JVM-Suiten bestanden, darunter 31 neue Orts-/Warnablauffälle und 43 statt 37 Wetteradapterfälle; 22 Planungs-Browserprüfungen bestanden, einschließlich sichtbarer mehrdeutiger Adresse und Schutz gegen fremde Grundstückskoordinaten. Android-/Gesamtregression läuft; native Geocoder-/Pixel-Systemtests mangels Emulator/physischem Gerät noch nicht ausführbar. Quellstand auf 1.52.3/85 erhöht, bisherige Original-APK 84 separat erhalten.

- Gesamtregression bestanden: 325 Python-Tests (37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen), 30 JVM-Suiten mit 43 Quellen- und zuletzt 32 Wetterablauffällen, 39 Browser-/JS-Suiten sowie 55 MCP-stdio-Szenarien. Nach dem letzten gezielten Anzeige-Fix zusätzlich alle 23 Planungstests und ES2020 für alle 36 operativen Skripte erneut bestanden. Status-/Wetteranzeige wird bei Rückkehr aus Einrichtung bzw. fertiger Hintergrundprüfung aktualisiert; offene Editoren bleiben unberührt.
- Im ersten Python-Gesamtlauf trat eine reale Konkurrenzlücke auf: zwischen Replayprüfung und Duplikatsuche konnte dieselbe Anfrage bereits gespeichert sein und dann fälschlich POSSIBLE_DUPLICATE melden. Vorhandenen reentranten Schreiblock nun um Kanalrücklesen, Duplikatsuche und Append in beiden bestehenden Host-Erfassungsfunktionen erweitert; Originaltest zusätzlich auf zwei erfolgreiche Ergebnisse verschärft. Alle 325 danach bestanden; kein Test entfernt/gelockert und kein Hostpfad als operative Voraussetzung eingeführt.
- Öffentliche Quellen im tatsächlichen Java-Adapter erfolgreich verarbeitet: Lizenz, beide Metadaten, richtige Parameterabfragen, Punktgeometrie, Zeitachsen und Einheiten; 57 Stunden- und 11 Viertelstundenwerte, leere amtliche Warnantwort, vollständige synthetische Einsatzbewertung. Ursprüngliche öffentliche Antwortbytes außerhalb des Checkouts unter /tmp/qa_weather_live; Hashnachweise im technischen QA-Artefakt outputs/qa-weather-validation.json. Nicht als Live-Abnahme einer nicht leeren amtlichen Warnung oder echten Kundenadresse ausgegeben.
- Pixel-Kanal wird bei neuer Anlage mit hoher Wichtigkeit erstellt; bestehende Android-Kanaleinstellungen bleiben unter Systemkontrolle. Nach Sperre/AUS werden unveränderte, noch nicht quittierte Risiken bei Freigabe erneut zustellbar; Ruhezeiten verlieren dabei keinen offenen Beleg. Kein Push-Server, kein Schlüssel und keine neue Manifestberechtigung. Meldungskopf nennt das Wetterrisiko, Details Kunde/Einsatzzeit; Sperrbildschirm-Fassung ohne Kundennamen. Keine automatische Terminverschiebung.
- Grenzen: Android-Geocoder auf echtem Pixel, reale Benachrichtigungsleiste/Systemfreigaben, Ton/Doze/Akku, echte Google-Konten, frische Installation und In-place-Update/Datenerhalt nicht ausgeführt. Zwei zusätzliche persistente native Orts-/Retry-Proben erstellt; native Instrumentierung hier nur kompiliert, mangels Emulator/KVM nicht gestartet. Quell-/Vertrags- und Cloud-Abnahme ersetzen diese Geräteprüfung nicht. Keine produktiven Daten gelesen, geändert oder in Tests verwendet; kein automatischer Commit/Push oder Gerätezugriff.

- Finaler Cloud-Build einschließlich Instrumentierungs-Kompilierung und Release-Lint bestanden (0 Fehler / 46 Warnungen; zusätzliche Hinweise betreffen bestehende deutsche UI-Texte). Gleiche Paketkennung, DB4/Snapshot2/MCP9, 414 Assets, 181 Pflanzenprofile/362 Bilder. Unveränderter P1-Vertrag 899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813, 117 Werkzeuge (52/65) und 156 Aktionen (145/11). Build-Fingerprint: Basis 72f96919d6a42ef8a2759c14c6da1df7359726d6, Quell-SHA256 71261476b0e346ab157e66d3e716e44a45b3780d5599105f6e68495594e4ccb9, source_dirty=true. Alle Quelländerungen im bestehenden Arbeitsbaum, kein automatischer Commit/Push.

- Originalsignierter Update-Kandidat privat bereitgestellt: [Haus-Garten-1.52.3-85-original-update.apk](https://drive.google.com/file/d/1vtMCxjHjlQ2Jf_GACAUa86iS-ZY9l7S0/view?usp=drivesdk), 34.550.085 Bytes, SHA256 c8702ec656bce4b33f3f72af1a0fe301987a6f4f436c72550d9ec649de1d4cf0. Genau ein vorhandener Originalsigner a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8, Paket/Zertifikat und höherer Code gegenüber84 bestanden; v2/v3, ZIP-Integrität, 16-KiB-Ausrichtung und alle414 Assets bytegleich. Alle neun Wetter-/Planungs-Klassen in DEX geprüft. Ausschließlich tools/sign_original_apk.py und fünf geschützte Variablen, nur Bereitschaft ausgegeben; Signiertempordner danach0. Nachweis outputs/qa-weather-original-apk.json, weitere Zusammenfassung outputs/qa-weather-validation.json.
- Neue Drive-Datei im bestehenden privaten Codex-Cloud-Builds-Ordner, alte84 nicht ersetzt und keinerlei Sharingrechte geändert. Metadaten nach Upload zurückgelesen: Name/MIME/Ordner korrekt,34.550.085Bytes,shared=false; anschließend vollständige authentifizierte Streamreferenz mit identischer Größe erhalten. Keine Downloadbytes-SHA behauptet, keine produktive Installation oder Geräte-/Update-Abnahme. downloads/README.md auf85 aktualisiert, vorherige Kandidaten erhalten.

- Abschließendes Gate am tatsächlich signierten APK nach Bereitstellung erneut PASS: Source 1.170 Dateien / 0 Betriebs-Seedzeilen, gesamter Workspace 2.942 Dateien einschließlich ignored/untracked, signed Artifact identische SHA / 0 Betriebs-Seedzeilen. Signiertempordner weiterhin0, Git-Whitespaceprüfung bestanden. Weiterhin Kandidat ohne physische Pixel-/Datenerhaltfreigabe.

## Update-APK 1.52.2/84 erstellt und privat bereitgestellt (20.10.2026)

- Ausdrücklicher Folgeauftrag „Erstelle Update Apk“. Der unveränderte geprüfte 84-Build wurde nach erneutem UI-/Pflanzen-/Angebotsvertragsabgleich und identischer unsignierter SHA über `tools/sign_original_apk.py` mit dem vorhandenen Originalschlüssel signiert. Alle fünf geschützten Laufzeitvariablen aktuell `ready`; keine Geheimwerte ausgegeben, kein Ersatzschlüssel, keine CI-/Geräteaktion. Cleanup unabhängig geprüft: 0 verbleibende private Signierverzeichnisse.
- Datei `outputs/Haus-Garten-1.52.2-84-original-update.apk`, Paket `at.hausgarten.service`, Version `1.52.2`, versionCode `84`, 34.541.893 Bytes. SHA-256 `961b2939f5dfedeca7e91bf96e5ab73dd62a6eabd34e149ba3f4fce14e476388`; einzelnes Originalzertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`, gültige Signatur und 16-KiB-Ausrichtung geprüft. Paket/Zertifikat und höherer versionCode gegen den erhaltenen signierten Referenzbuild 79 bestanden. Alle 414 Assets bytegleich,181 Pflanzenprofile/362 Bilder und operative DEX-Klassen vorhanden; Source-/Workspace-/Signed-Artifact-Privacy PASS,0 operative Seedzeilen.
- [Privater Drive-Download](https://drive.google.com/file/d/1ax6MiOp7yKy9qw5Q2R6vzY-_0E8t1ltY/view?usp=drivesdk) im vorhandenen privaten Ordner Codex-Cloud-Builds. Neue Datei, keine alte APK ersetzt oder Sharingrechte geändert. Metadaten nach Upload zurückgelesen: korrekter Name/MIME/Ordner,34.541.893Bytes,`shared=false`. Vollständige authentifizierte Downloadreferenz mit derselben Größe erhalten; keine zusätzliche unabhängige SHA-Prüfung der Downloadbytes behauptet.
- Vorherige 325 Python-/29 JVM-/39 Browser-Suiten sowie 55 MCP-Szenarien bleiben Nachweise desselben unveränderten Quellstands. Nachweis der tatsächlichen signierten APK: `outputs/qa-overview-original-apk.json`. Keine automatische Installation. Fresh-Install, echtes 83→84-Update mit Datenerhalt, Pixel-/Google-Abnahme und bestehende Wettergrenzen bleiben offen; Signaturvergleich ist keine Geräteabnahme. APK-Fingerprint: Basiscommit `72f96919d6a42ef8a2759c14c6da1df7359726d6`, Quellhash `436b9937cf2f61b74fa49845e7ee425b5a2e0e2fcb16b0c24865ec101bf1a8fc`, `source_dirty=true`. Quelländerungen weiterhin im bestehenden Arbeitsbaum, kein automatischer Commit/Push.

## Übersicht und MCP-Testbefund – Cloud-Implementierung 1.52.2/84 (20.10.2026)

- Grundlage: zuerst das MCP-Testdossier vom 20.10.2026 gelesen (ChatGPT Page `page_7f18b1e34e808191b3851b6c15ebf582`), anschließend zentrale Regeln und bestehende Projektanweisungen. Neuer ausdrücklicher Auftrag: Übersicht priorisieren, manuelle Eingaben nur bei Bedarf, bestehende Funktionen erhalten und Fehler autonom korrigieren.
- Ausgang: sauberer Checkout `72f96919d6a42ef8a2759c14c6da1df7359726d6`, Remote-main mit `git ls-remote` identisch; keine weiteren Remote-Branches. Lokaler veralteter origin/main-Ref nicht als maßgeblicher Stand verwendet. Issue-API-Lesen durch Netzwerkpolicy abgewiesen; keine Umgehung. Keine produktiven Geschäftsdaten gelesen oder verändert.
- Messbare Abnahme: gespeicherten Termin aus Liste/Heute/Suche zuerst ohne Formular anzeigen; Bearbeiten öffnet denselben bestehenden Editor ausdrücklich. Start aus bekanntem Kundenkontext zeigt Kunde und Startaktion, Kundensuche erst über „Kunde ändern“. Kundenübersicht zeigt bekannte Garten-/Zugangsinformationen ohne Editor. Kein automatischer Schreibaufruf, keine automatische Tastatur, unveränderte Entwürfe und Vergleichsstände, 320/360/426 px und 150 % Schrift lesbar.
- Sync-Abnahme: lokale Testaktionen melden `test_local_only=true`, `status=local_saved`, `sync_state=not_applicable`, keine Drive-/Cloud-Bestätigung; alte Belege anhand ihrer exakten lokalen Aktion lesend erkennen. Dauerhafte Request-Belege und lokale Journale erhalten, keinerlei Queue- oder Betriebsdatenbereinigung. Produktive Aktionen bleiben unverändert wartend, bis ein echter Beleg vorliegt. Teststatus nach Purge/Neustart mit isoliertem Store prüfen.
- **Umgesetzt:** Heute zeigt ohne aktive Sitzung zuerst Stundenübersicht und nächsten Einsatz, dann Erfassungsaktionen. RUNNING/PAUSED/STOPPED_PENDING_REVIEW behalten Vorrang. Gespeicherte Termine aus Liste/Heute/Kundenakte/Suche/Benachrichtigung öffnen eine lesende Übersicht mit Wetterhinweis; bestehender Editor und geschützte Aktionen bleiben ausdrücklich erreichbar. Start aus bekannter Kundenakte zeigt den bestätigten Kunden; Suche/Auswahl erst über „Kunde ändern“, kein automatischer Tastaturfokus. Kundenakte zeigt vorhandene Zusatzfelder einschließlich Zugang, Zahlen und Ja/Nein als aufklappbare Lesedaten, ohne Garten-/Flächenumdeutung.
- **Sync-Ursache und Fix:** `pendingActionsForDrive()` schließt lokale Testjournale bereits aus; die bisherige Statusabbildung behandelte sie dennoch als wartend. MCP-Belege speichern nun Testprovenienz. Alte Belege erkennen exakt ihre unveränderte `_local_only`-Aktion; ohne belastbare Provenienz keine Umdeutung. Lokale Testbelege melden `local_saved/not_applicable`, niemals Drive-/Cloud-Bestätigung. Timerabschluss bewahrt dieselbe lokale Testprovenienz. Keine Aktion gelöscht, kein Produktivabgleich ausgelöst und keine nachträgliche Migration. Der Dossierbefund ist damit im Quellcode behoben; der installierte Pixel ist nicht aktualisiert oder nachgeprüft.
- **Zusätzlicher reproduzierter UI-Fehler behoben:** verspätet initialisierte Foto-/Unternehmenseinstellungen konnten im Mehr-Menü bleiben. Die vorhandenen Steuerelemente werden nun unabhängig von der Timerreihenfolge in Einstellungen einsortiert; gezielter Test mit verzögertem Einfügen bestanden. Testparser Acorn 8.15.0 außerhalb des Repositorys ergänzt; keine ES2020-Prüfung gelockert.
- **PASS:** 325 Python-Tests, 0 Skips (37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen); 29 JVM-Suiten einschließlich zwei neuen regressiven Statusfällen; 55 MCP-stdio-Szenarien. 39 Browser-/JS-Suiten im finalen Gesamtstand bestanden: ursprüngliche Fehlläufe für fehlenden Parser und Einstellungsrennen erhalten, beide gezielt erfolgreich wiederholt. Betroffene Heute-/Kunden-/Such-/UAT-Abläufe danach erneut bestanden. 320/360/426 px und 150 % Schrift, kein horizontaler Überlauf; Terminübersicht ohne Eingabefelder, Formular erst nach Bearbeiten; Entwürfe und CAS-Basis erhalten. Visuelle Cloud-Screenshots geprüft, keine Pixel-Messung.
- **Verträge PASS:** 117 Werkzeuge (52 lesend / 65 schreibend), 156 UI-Aktionen (145 fachlich zugeordnet / 11 Plattformgruppen); P1-Gesamtvertrag unverändert `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`. DB4/Snapshot2/MCP9 und Paketkennung erhalten; UI-Quellreview aktualisiert. Keine bestehenden Aktionen oder Tests entfernt.
- **Build PASS:** Version `1.52.2/84`, `at.hausgarten.service`; Release gebaut, Android-Instrumentierungsquellen kompiliert, Release-Lint 0 Fehler / 37 Warnungen. Finale unsignierte Prüf-APK SHA-256 `e1c57ba730791d4f9fd78248db2cd530cbc43e4214b3a8ccb56af8a3c60edf0f`, ZIP-Integrität und alle 414 Quellassets bytegleich. Source-/gesamter Workspace-/Artifact-Privacy-Gates PASS, 0 operative Seedzeilen. 21 vorbestehende unklassifizierte Ausgaben zu Beginn unverändert außerhalb des Checkouts erhalten (`/tmp/qa_preserved_previous_outputs_1_e5b1ct`), nichts gelöscht oder als neue Testprovenienz ausgegeben.
- **Grenzen:** neue native Sequenz Kunde/Änderung/2,5-h-Arbeit/Purge/Replay/Neustart kompiliert, hier nicht ausgeführt (kein Emulator/KVM vorhanden). Physischer Pixel, echter Drive-/Google-/Wetterpfad und In-place-Update/Datenerhalt bleiben offen. Keine Originalsignierung, APK-Veröffentlichung, Installation, Kontofreigabe oder produktive Datenaktion; APK ist ausschließlich ein Cloud-Prüfartefakt. Keine automatische Commit-/PR-/Push-Aktion. Historisches MCP-Testdossier unverändert erhalten.
- Nachweise: `outputs/qa-overview-browser-final.json`, `outputs/QA-Python-results.json`, `outputs/integration-java/results.json`, `outputs/QA-MCP-E2E.json`, synthetische Übersichtsscreenshots und Gradle-Berichte. Dauerhafte Anforderungen unter AGENTS/Alltagstauglichkeit fortgeschrieben. Nächster Geräte-Schritt benötigt die bestehende Sicherungs-/Originalsignatur-/Freigabekette; keine Geräteabnahme aus Hosttests ableiten.

## Dauerhafte Cloud-Quellcodesicherung (21.10.2026)

Nutzerauftrag: Den gültigen Originalcode dauerhaft außerhalb einzelner Cloud-Tasks
abrufbar halten und ungesicherte Auslieferungen verhindern. Abnahme: bestätigter
GitHub-Commit, Originalexport aus genau diesem Commit, Abbruch bei Dirty/Unpushed/
falschem oder unerreichbarem Remote sowie nachträglicher Checkoutänderung; keine
Schlüssel-, Geräte- oder Betriebsdatenaktion.

- Verbindliche Regel in AGENTS.md, Querverweis im Zentralauftrag und README.
  Nach Änderungsschritten/Unterbrechung/Abschluss sichern und extern bestätigen;
  ursprüngliche Arbeit vor Taskwechsel/Übernahme erhalten. Versionsmarke,
  Quell-/APK-/Archivhash, Drive-Kopie und frischer Task-Readback vor Auslieferung.
- AKTUELLER-STAND.json dient als Einstieg. Der zuletzt beobachtete installierte
  Stand1.54.0/89 meldet source_dirty=true; sein gemeldeter Basiscommit ersetzt
  keinen bestätigten Originalquellstand. verified_release_source bleibt null.
  Diese Regeländerung gewinnt den fremden Build-89-Arbeitsstand nicht wieder.
- tools/source_preservation.py prüft lesend den sauberen Checkout einschließlich
  unversionierter Android-Eingaben und einen Branch/Tag des bestehenden GitHub-
  Repositorys auf genau diesem Commit. Fehler/Netzausfall bleiben blockierend;
  Git erhält keine HG_ORIGINAL_*-Variablen. Kein Push, Reset oder Schlüsselzugriff
  durch das Prüfwerkzeug. Es bestätigt nicht selbst die APK-Provenienz.
- Offizieller Originalsignier-CLI und Quellcodeexport erzwingen die Prüfung. Der
  Exporter liest Dateien aus den gepinnten Git-Objekten statt veränderlichen
  Arbeitsdateien; Ziel perplexity ergänzt. Privacy-Gates unverändert erhalten.
- Gezielte Abnahme: 11 neue isolierte Git-/Signier-CLI-/Exportproben PASS,
  42 vorhandene synthetische Originalsigniertests PASS, 13 Quellprivacy-Regeltests
  PASS. Temporäre lokale Git-Remotes, synthetische Nicht-Schlüsselbytes und
  gemockte Androidwerkzeuge; keine echten Konten, Signierwerkzeuge oder Geräte.
  Source-Gate PASS, Bootstrap0; Abbruch am uncommitteten echten Workspace bestätigt.
  Keine Android-/UI-Regression behauptet: keine operative App-Funktion geändert.
- Globale Kontoeinstellungen und bereits laufende andere Tasks werden nicht
  automatisch umgestellt. Dort vor weiterer Auslieferung die aktuelle AGENTS.md
  lesen und vorhandene Originalarbeit ohne Reset sichern. Separate Cloud-only-,
  Originalsignatur-, Privacy- und Installationsfreigaben bleiben verbindlich.

## Übergabe an einen neuen Cloud-Task (20.10.2026)

**Gesicherter Zwischenstand. GeoSphere-Funktion: NICHT TESTABLE / nicht abgenommen.** Auf Nutzeranweisung keine weiteren GeoSphere-Livezugriffe oder daraus abgeleiteten Implementierungen in dieser alten Task-Umgebung. Neue Netzwerkfreigaben erst im nächsten Task verwenden.

- **Exakter Code-/Testcommit:** `35d7f7787a731ef6d8d1a663bf39947bbbf0b557`, main; Ausgang `6d8f0476426a31a8112cb702365edc7c72e6d081`. Alle Codeänderungen committed; der folgende Übergabecommit ergänzt nur zentrale Dokumentation. Keine neue Branch, kein Reset/History-Rewrite.
- **Erledigt:** versteckte Vollbackupprüfung beim Start, globale Neuladungen während Sync, konkurrierende Abgleiche und Statusarbeit auf dem UI-Looper repariert; ES2020-WebView-Modulstart abgesichert. Wetter-/Kalenderpfade vorbereitet, bewusstes AUS erhalten, Kalenderidentität/ETag-Konflikte sowie begrenzte Wetterfortsetzungen abgesichert. Wetter bleibt ohne echte Quellenprüfung unvollständig abgenommen.
- **Hosttests bestanden:** 325 Python, 29 JVM-Suiten, 39 Browser-/JS-Suiten, 55 MCP-stdio-Szenarien. 37 alte QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen; keine Ersatzschlüssel oder gelockerten Prüfungen. 37 Wetteradapter- und 43 Kalenderanlagechecks ausschließlich synthetisch, kein GeoSphere-/Google-Livenachweis. P1-Vertrag unverändert; MCP 9 / 117 Quelltools / 156 UI-Aktionen.
- **Native Grenze:** 141 isolierte Android-Prüfungen, echter WebView, fresh_isolated und vollständiger synthetischer Updateledger 81→82→interner83 am Stand b04798f bestanden. Die zwei späteren 35d-Wetterkorrekturen sind kompiliert, zusätzlich nativ **NICHT TESTABLE**: Schlusslauf beim Original82-Installationsschritt nach 180 Sekunden abgebrochen. Keine native143-Ausführung oder finalen35d-Updateledger behauptet. Alter AVD scheitert separat im Android-Systemdienst mit `Lost network stack`; beide Bestände erhalten, keine Resets/Timeoutverlängerung.
- **Datenschutz:** Source-/Workspace-/Artifact-Gates am tatsächlichen internen APK bestanden, 0 Betriebs-Seedzeilen, ignored/untracked eingeschlossen. Ausschließlich synthetische Testdaten; keine Geheimwerte oder produktiven Rohdaten ins Repository geschrieben. Produktiver Pixel nur gelesen: weiterhin82/89281da, Geschäftshash/Counts unverändert, aktive Queue/Review/Journal 0/0/0, nur provider_readback/cloud_confirmed=false. Kein produktiver Schreibtest oder Pixelupdate.
- **Nur interner Build:** 1.52.1/83, Originalpaket/-zertifikat, Quelle35d7f77, source_dirty=false. APK-SHA `f8dfbaa107bf89cfe7232d8cacde865e02f01b05cb3c30a82d0bf29b5b10adbd`; v2/v3, 16-KiB-ZIP-Ausrichtung und Signier-Cleanup geprüft. Nicht als fertig abgenommen veröffentlicht oder zu Drive hochgeladen; nach Live-Abnahme neu bauen/prüfen.
- **Offen / NICHT TESTABLE:** tatsächliche GeoSphere-Lizenz-/Metadaten-/Forecast-/INCA-/Warnantworten; echte Google-OAuth-/Cloud-Projektfreigabe, Kalender-Readback, Gerätebenachrichtigung/Doze und physischer Pixel. Issue3 bleibt offen. Connector zuletzt111 callable Tools gegenüber117 Runtime-Deskriptoren; nach späterem Geräteupdate erneut prüfen. Keine dieser Livefunktionen als PASS markieren.
- **Nächster Einstieg:** neuen Task in der neu veröffentlichten Umgebung starten; aktuellen main und AGENTS/Zentralauftrag/Status lesen. Zuerst öffentliche GeoSphere-Antworten mit synthetischen Koordinaten live prüfen und Parameter, Einheiten, Geometrie, Zeitachsen/Horizonte gegen FreeWeatherSource/WeatherRiskRules abgleichen. Erst dort vervollständigen; die zwei letzten nativen35d-Proben und den finalen82→35d83-Updateledger ohne Löschen/Abschwächen nachholen, Gesamtregression wiederholen. Google-/Connector-Endtest nur bei tatsächlicher Freigabe; produktive Installation weiterhin separat nach Sicherung/Hashes und ausdrücklicher Zustimmung.

## Reparatur 1.52.1/83 – Phase 0: Schutzbaseline (20.10.2026)

- Ausgang: sauberer main bb07820, frisch ohne Überschreiben auf tatsächlichen origin/main **6d8f0476426a31a8112cb702365edc7c72e6d081** vorgezogen. AGENTS, Zentralauftrag/Status, Signier-/Reparatur-/Owner-/Sync-Anweisungen vollständig gelesen; Issues1/3 und gesamte Historie e8a1d09→89281da→6d8f047 gelesen. Zehn spätere Wettercommits sind nicht Bestandteil der installierten82; nicht als deren Fehlerursache behandeln. Keine Branchanlage/Historyänderung.
- Pixel ausschließlich MCP-lesend bestätigt: **1.52.0/82**, Paket at.hausgarten.service, Quelle89281daf5d5cfb48a1e08fac931f60deb451ddc7, Quellhash16d1e8aa7b83d82c1077d445e31e86f0cb609b61ae345fabc3feafed35ed5b20, release/source_dirty=false, DB4/Protokoll9/117 Laufzeittools/Write EIN. Gemeldeter nativer Toolhash8780ff5fa46d421c4a1ea858db30b232e0272303065d80f28f5f43e242a139aa; Darstellungsunterschied zum kanonischen JVM-Hash nicht als Fehler behauptet. Hier aktuell **111 callable Connector-Werkzeuge**, sechs neuere Diagnose-/Offer-Operationen nicht veröffentlicht; keine117-fache Liveausführung behaupten. UI-Katalog vollständig100+56=156, einheitlicher live Hash28fa4b1d9aed8bbee531288fa4479f408c99d89c5cb798a7fb1e9742ce6ccc8f.
- Geschäftshash **2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932**, Counts Kunden58/Aufträge3/Arbeiten46/Rechnungen95/Fotos31/Bausteine33/Angebote0/Termine1. Aktive Queue/Review/Journalfehler **0/0/0**, separater Roh-Pendingzähler1. Drive während Lesung reading/syncing, keine Transferfehler, persistierte Ordnerfreigabe; provider_readback, cloud_confirmed=false. Kein Sync oder Geschäftsschreibtest angefordert.
- Vorhandene aktuelle Geschäftssicherung erneut inspiziert:33524508Bytes, SHA d965237fc10717e58e0897ebf14b13bddfcb18ff298a5139ca955750f252d780, dieselben Collection-Counts. Keine neue Sicherung nötig für reine Entwicklung; keine Rohdateien heruntergeladen. Scope bestehender Geschäftsbackupweg, kein vollständiges Gerätebackup. Kalender/Wetter AUS/nicht eingerichtet, OS-Benachrichtigungsrecht vorhanden. Produktive Installation bleibt separat freigabepflichtig.
- P0 Source/Workspace/Original82-Artifact **PASS**,0Seeds, ignored/untracked eingeschlossen. Ausgangs-JVM **FAIL bereits vor Codeänderung**: FreeWeatherSource fehlt im Compile-Runner, obwohl PlanningTests sie referenziert. Browser-Gesamtlauf noch in Arbeit. Keine Tests/Baselines gelockert. Nutzer konkretisiert Fehler: **Sync verlangsamt Appprozesse**; Diagnose deshalb Synchronisationskonkurrenz, UI-/Bridgekosten und P3-Startarbeit prüfen, keine ungeprüfte Datenverlustbehauptung.
- Messbare Abnahmen: keine verborgene Vollbackup-Inspektion beim Appstart; UI-/Speichervorgänge während verzögertem synthetischem Provider; identischer Geschäfts-/Queue-/Sitzungsledger beim normalen81→82 und82→83; Wetterintervalle exakt nach Auftrag, AUS respektiert, fehlende Daten unbekannt; Kalender CRUD/Retry/ETag ohne Duplikate; alle bestehenden117 Verträge/156 Aktionen und P0/P1 erhalten. Physische Google-/Pixel-Endabnahme ausdrücklich offen.
- Netzwerk: verwaltete Cloud restricted/package_managers, keine GeoSphere-/Google-API-Egressfreigabe und kein VPN. Offizielle GeoSphere-Dokumentation über unterstützte Webrecherche lesbar (CC BY4.0/gewerblich, keine Authentifizierung,60h NWP/3h INCA); tatsächlicher API-Datenread bisher nicht erreichbar. Keine Umgehung. Nächster Schritt Phase1: reproduzierbare Last-/Startcharakterisierung, minimaler Fix vor Wetter/Kalender.

### Phase 1 – Sync-Verlangsamung: reproduzierte Ursachen und begrenzte Reparatur

- **Reproduziert:** P3 startet180ms nach Laden eine synchrone vollständige Backup-Inspektion samt Dateihashprüfung aus verborgenem Einstellungsbereich. Synthetischer33-MiB-Metadatenfall zeigt inspect_backup/get_file_metadata während Heute und Sync; neuer Regressionstest vorher FAIL, nach Fix PASS. Keine Größen-/Laufzeitmessung am Pixel behauptet. Vollständige Inspektion jetzt nur ausdrücklich „Sicherung prüfen“, Erstellen oder Restore; Öffnen der Einstellungen liest lediglich Metadaten. Restore-/SHA-/CAS-Schutz bleibt erhalten.
- Transportphasenwechsel lösten alle5s vollständiges Datenlesen/Rendern aus. Neuer Charakterisierungstest erhält Suchtext und weist null zusätzliche getState-Aufrufe bei reading/media/journal/publishing nach; neue bestätigte Datenrevisionen laden weiterhin neu. Teure Start-/Endstatusberechnung des manuellen Syncs vom Android-UI-Looper auf vorhandenen Hintergrundlauf verlegt. Konkurrierende Activity-/MCP-/Job-Auslöser werden vor dem Netzwerklock zusammengeführt, ohne Beleg zu erfinden; während des Netzwerks neu gespeicherte Aktionen erhalten einen Folgelauf. Manueller Versuchshaushalt bleibt über begrenzte Retry-/Medienabschnitte erhalten.
- **Separat reproduzierte WebView-Regression:** vier logische Zuweisungen im neuen business-actions-ui.js sind keine ES2020-Syntax; alter unterstützter WebView kann deshalb das gesamte U01/U02/U03-Modul verlieren. Neuer Acorn-Gesamttest vorher SyntaxError, nach kompatibler Schreibweise36operative externe/inlineSkripte PASS. Bestehender API-Polyfilltest deckte Syntax nicht ab. Native Modulstartprüfung verschärft; deren tatsächliche Ausführung steht noch aus. Dies ist nicht ungeprüft die Ursache der vom Nutzer gemeldeten Pixel-Sync-Latenz.
- Geändert: business-actions-ui.js, daily-usability.js, MainActivity, DriveSyncCoordinator; neue sync-responsiveness-/webview-syntax-Tests, Native-Provider-/Lifecycle-Proben, Runner und geprüfte UI-Quellreviewprovenienz. Defekten aktuellen JVM-Runner um vorhandene FreeWeatherSource ergänzt. Syntaxparser Acorn8.15.0 ausschließlich unter Cloud-Tooling installiert; Runner löst ihn über HG_ACORN_MODULE oder vorhandenen Playwright-Toolpfad auf, fehlender Parser ist FAIL.
- Hostabnahme **PASS**:325Python/0Skips,27JVM/frozen P1 identisch899906c2…aa813,39Browser-/JS-Suiten einschließlich drei UAT-Failpfade, neuen Last-/Syntaxproben und bisherigem115-Aufgaben-Walkthrough; Android Java/Instrumentation kompiliert. Keine Tests/Baselines abgeschwächt. Native neue Parallelitätsprobe kompiliert, noch NOT TESTABLE bis Ausführung.
- Alter synthetischer82-AVD vor Beenden lesend geprüft: vollständiger Master-/Pending-/Relations-/persistierter Sitzungshash exakt gleich zur81-Baseline. Alte Lifecycle-Baseline unverändert. AVD normal beendet und erhalten. Neuer dedizierter Stock-Datenbereich außerhalb Checkout für echten81→82→83-Pfad; kein vorhandener Store kopiert/gelöscht. Zwei frühe Emulatorversuche vor vollständigem Boot abgewiesen, keine Installation behauptet; Emulator danach QEMU/AVD/Boot bereit. Original81-Fresh-Abnahme läuft. **Phase2/native Update-/UAT-Gesamtabnahme noch offen**, keine APK veröffentlicht oder produktiver Pixel verändert.

### Phase 2 – bisheriger Updatepfad und Gesamtregression

- Dedizierter stock-basierter Cloud-Emulator5584: Original81 frisch geprüft, ausschließlich QA-Fixture vorbereitet, Original82 mit normalem install -r aktualisiert. Native Lifecycle-Abnahme **PASS**: vollständiger Snapshot, IDs, Pending, Einstellungen, Tunnelkonfiguration und laufender Timerempfänger erhalten. Zusätzlich unabhängiger read-only Ledger81→82 **PASS**; neuer separater unveränderlicher82→83-Ausgangsledger erstellt. Alte Baseline und alter AVD bleiben erhalten. Keine Deinstallation, Wipe, Clear-data oder produktive Datenkopie.
- Neue Ledger-Parameter erlauben ausdrücklich benannte Vorwärtsupdates; Standard81→82 bleibt identisch. Rohcaptures ausschließlich RAM, Ausgaben nur QA-Counts/Hashes. Emulatorbaseline Master9436aa2d…4287, Relationshasheacbcd80…f6299a,3Kunden/1Auftrag/2Masterarbeiten/1Pending/RUNNING.
- Gesamtregression läuft erneut nach Wetter/Kalender:325Python/0Skips/P0Source-Workspace-Original82-Artifact PASS,29JVM-Suiten einschließlich neuer Quellentests und Kalenderanlage PASS, P1 weiterhin exakt899906c2…aa813;55stdio-Szenarien PASS. Vollständige Browser-/Python-/native Schlussabnahme wird separat nachgetragen. Zwei Zwischenbuilds wurden korrekt durch fehlende erneute UI-Quellreview bzw. Android-JSONObject.keySet-Inkompatibilität gestoppt; Iteratoren korrigiert. Keine Gate-Abschwächung.

### Phase 3 – kostenfreie Wetterkopplung (Implementierung; externe Abnahme offen)

- Ausschließlich öffentliche schlüsselfreie GeoSphere-Quellen; keine ECMWF-Webseiten-als-Prognose-Behauptung/kein bezahlter Ersatz. Offizielle Primärquellen geprüft: data.hub.geosphere.at/en/dataset/nwp-v2-1h-1km (60h), /nowcast-v1-15min-1km (3h), /warnungen-v1; dataset.api.hub.geosphere.at/v1/docs/index.html sowie user-guide/authentication.html und request-limit.html (CC BY4.0/gewerbliche öffentliche Nutzung,5/s und240/h). Laufzeitprüfung des öffentlichen Lizenzhinweises, unbekannte Lizenz/Einheit/Geometrie/Zeitskala fail-closed. Kein unveränderliches Lizenzversprechen.
- Exakte Auftragsintervalle24h/12h/6h/3h/1h, vorT-14 null Abfragen; tatsächlicher60h-Horizont bleibt unbekannt statt grüner14-Tage-Prognose. Standort-Caches, gemeinsame öffentliche Metadaten/Lizenzprüfung, beständige180/h-Reserve unter öffentlicher Quote, begrenzte12-Request-/45s-Abschnitte, Retry-After und gespeicherte Teilergebnisse. INCA nur letzte3h bei relevantem/unbekanntem Risiko, zusätzliche Risikobewertung ohne fehlende Stunden zu erfinden.
- Temperatur-/Niederschlags-/Wind-Einheiten streng geprüft; kumulative Mengen differenziert, erster/mangelnder/Reset-Wert unbekannt. Regen nicht durch fehlende/niedrige Wahrscheinlichkeit weginterpretiert. Verfügbare Schnee-/Winddaten berücksichtigt; amtliche Warnung getrennt, Risiken/Zeitraum/Standort/Profil und grobe Gefahrenbänder dedupliziert. Ruhezeit21–06 außer relevanter kritischer amtlicher Warnung, verweigerte OS-Rechte respektiert; keine automatische Termin-/Kundenaktion.
- Erste Termin-/Serienanlage setzt nur noch ungesetzte sichere Wetterdefaults innerhalb derselben SQLite-Anfrage. Explizites AUS bleibt AUS, Retry aktiviert es nicht wieder. Geänderter Einsatzort ohne neue Koordinaten invalidiert alte Zuordnung. Nur exakte eigene Kundenadresse darf vorhandene Kundenkoordinaten wiederverwenden; nichts geocodiert/geraten. Bestehendes hedge-Profil ausdrücklich auch für Bäume/Arbeiten in Höhe beschriftet; kein neues Enum/Tool/Datenformat nötig. Quelle im Termin attribuiert.
- Neue34 synthetische JVM-Checks für Quelle/Intervalle/Lizenz/Einheiten/Lücken/429/Horizont PASS; native AUS-/Replay-Charakterisierung ergänzt, Ausführung noch offen. Reale Cloud-Datenabfrage weiterhin **NOT TESTABLE** wegen Egress-Allowlist. Keine echte Quelle, Prognose/INCA oder amtliche Warnung als live erfolgreich behauptet.

### Phase 4 – automatischer eigener Google-Kalender (Implementierung; Gerätefreigabe offen)

- Normaler Schalter Google Kalender EIN startet bestehende Kontowahl/Google-Gerätefreigabe und Einrichtung; keine Calendar-ID/Scopes im Normalbereich. Dauerhafter Marker und vorab persistierte Anlageabsicht, eindeutige Eigentümerkalenderliste und zusätzlicher Provider-Readback; verlorene Antwort führt zu Wiederfinden oder sicherem Warten statt zweiter Anlage. Verbindungswechsel setzt ausschließlich private Transportzuordnungen nach Settings-CAS zurück.
- Bestehende asynchrone Job-/Owner-/Journalpfade weiterverwendet. Neue/Serien-/geänderte/abgesagte Termine planen Abgleich; anfänglich nur aktive zukünftige Termine. Frühere Jobfrist wird nicht durch spätere Wetterfrist verdrängt. Eigenständige Kalendernachprüfung, keine Einladungen; Titel aus eindeutigem Kundenname und Tätigkeit, Ort/Vienna und betriebliche Notiz, nur private ExtendedProperties zur stabilen Zuordnung.
- Extern geänderte/entfernte Google-Termine werden weder still importiert noch bei lokaler Absage blind gelöscht. Vorhandener Konfliktvergleich und ausdrückliches use_google/keep_app bleiben erforderlich; ETag/If-Match, deterministische Event-ID, Readback, Sync-Token und idempotente Journalanfrage erhalten. 401 verwirft abgelehnten Cachetoken,403 keine Umgehung;429/5xx begrenzt mit Retry-After. Lokaler Termin bleibt gespeichert.
- Neue39 synthetische Kalenderanlage-/Antwortverlust-/Dubletten-/Readback-/Rechte-/Titelchecks PASS. Native Google-Importprobe verschärft: vor ausdrücklicher Entscheidung keine lokale Änderung/weitere Journalaktion; danach derselbe geschützte Import. Isolierte Testcontexts dürfen keine echte JobScheduler-Arbeit auslösen. Reales Google-Konto/OAuth/Provider und produktiver Pixel weiterhin **NOT TESTABLE**, keine Rechte als erteilt behauptet.

### Phase 5 – native Abnahme und begrenzte Fortsetzung großer Wetterbestände

- Erster interner83-Prüfstand ausb04798f originalsigniert; synthetisches normales82→83 **PASS**, gesamter Master/Pending/Relations-/persistierter Sitzungshash unverändert. Nicht veröffentlicht, kein Pixelupdate. Zusätzliche Quotenprüfung entdeckt: geplante Wetter-Batch-/Stundenquota-Fortsetzungen durften den Acht-Fehlversuche-Haushalt nicht erschöpfen. Fortsetzungen erhalten jetzt getrennt ihren nächsten Zeitpunkt, ohne einen Netzfehler zu erfinden; echte Netz-/Rechtefehler bleiben begrenzt. Neue native12-Fortsetzungen-Probe ergänzt. Daher wird erst ein erneut geprüfter finaler Kandidat bereitgestellt, keine interne Zwischen-APK.
- Alle115 bisherigen UAT-Zuordnungen erneut gelesen. Aktuelle Abweichungen ausdrücklich:58 Google-EIN führt direkt zur Gerätefreigabe;59 keine privaten Wetterkeys/ECMWF-Webseitenprognose mehr;106 Einstellungen lesen kleine Sicherungsmetadaten und vollständige Dateiprüfung nur ausdrücklich „Sicherung prüfen“. Restore-/Hashschutz unverändert. Endgültige115-Aufgaben-Softwareabnahme erst nach nativer WebView-/Provider-/Neustartprüfung.
- Produktiver MCP am Ende erneut ausschließlich lesend: weiterhin82/Quelle89281da, DB4/Protokoll9/117Runtime/WriteAdmin EIN. Geschäftsstatehash2b8310a5…07932 und Counts58/3/46/95/31/33/0/1 unverändert; aktive Queue/Review/Journal0/0/0, Roh-Pending1, kein Drivefehler, nurprovider_readback/cloud_confirmed=false. Kalender/Wetter weiter AUS/unverbunden. Kein Produktivtest, keine Konto-/Installationsfreigabe fingiert.

## Aktueller P2-Konsolidierungsauftrag

**Sichere technische Konsolidierung bestanden; vollständige P2-Abnahme noch offen.** Ausgang `348b40a`, Wiederaufnahme ab `93ced84`, App unverändert 1.51.2/81. Drei integrierte Remote-Branches, elf alte APKs und vier temporäre Archive entfernt; drei Berichte konsolidiert. Neue Nachprüfung: alle 53 beobachteten Drive-Fotodateien aktiv/archiviert referenziert, 158 Dateien geschützt, nur drei weiterhin unbekannt. Keine Releases; sieben historische Actions-Artefakte mit automatischem Ablauf am 23./25.10.2026 inventarisiert. Alle sechs Altbackup-SHAs stimmen; bei einer alten Sicherung ist das fehlende additive Feld appointments als Ursache des inspect_backup-Fehlers belegt. Vollständige Restore-/globale Referenzabnahme bleibt offen. Vorübergehender Connector-Abbruch beendet; finaler Live-Readback bestätigt unveränderte Geschäftsdaten/139Archivversionen/sieben Backups, aktive Queue/Review/Journalfehler0. 322 Python-Tests,27 JVM-/30 Browser-Suiten/55 MCP-Szenarien und alle P0-Gates PASS, P1-Vertrag unverändert. Keine produktive Löschung. Aktuelle Tests und Grenzen in P2-7 bis P2-9; ältere Abschnitte sind zeitgebundene Nachweise.

## Bestandener P1-Refactoringauftrag

**P1: PASS, alle 12 Abnahmekriterien im definierten Cloud-Umfang erfüllt.** Ausgang `1abf049c1e27167bc16259d8717513aeb5ffe670`, Version 1.51.1/80; operativer Abschluss- und APK-Quellcommit `e8a1d09ba7bb93438fb5aaff1e6008a670339764`, Version 1.51.2/81. R0–R7 bestanden, Außenverträge unverändert: MCP 9 / 117 Tools / 156 UI-Aktionen. Alle drei P0-Gates PASS. Keine produktiven Daten für P1-Tests verwendet. Der physische Pixel wurde anschließend außerhalb dieser Cloud-Installation auf 81 aktualisiert und bei P2 lesend bestätigt; der P1-Schlussabschnitt beschreibt seine damaligen Prüfgrenzen.

## Bestandener P0-Datenschutzauftrag

Ausgang `aa04cbd04d2035a877acd243af612a2fc97f5519`, sauberer aktueller main; Implementierungscommit `7725f425e57d8a40733b0e798def1e279c8488ec`, anschließender Abnahme-/Härtungscommit über `git log -1`. **P0 für aktuellen Checkout und zukünftige Builds: PASS,14/14 Kriterien.**297 Python-Tests/24 JVM-/29 Browser-Suiten/55 MCP-E2E-Szenarien/134 native Android-Proben sowie Emulatorupdate77→80, WebView und isolierter Erststart bestanden. Alle drei Privacy-Gates PASS. Keine produktiven Daten kopiert oder geändert. Historische Git-Befunde und physischer Pixel-/echter Provider-/Connector-Endtest separat offen. Vorherige Zwischenstände/NOT-TESTABLE-Startversuche unten sind historische Phasen.

## Historischer Ausgangsstand

- Auftrag: **P0 Pixel/Drive Cloud-only Sync abschließen**
- Operativer Zielpfad: Pixel ↔ Google Drive direkt
- Entwicklungsweg: ausschließlich Codex Cloud
- Repository-Version: **1.49.5 /74**, Originalpaket; Mehrfacharchivierung, Sync-Normalisierung, WebView-Kompatibilität und Entwurfsreparatur. Cloud-Emulatorabnahme PASS, Pixel-/Google-Abnahme offen.
- Live-Pixel bei neuer Bestandsprüfung: **1.49.4 / 73**, Protokoll 5, 87 Laufzeitwerkzeuge, Owner-Write aktiv (nur gelesen).
- Live `offers_sync_ready`: true
- Live `service_catalog_sync_ready`: true
- Live `cloud_confirmed`: false
- Live Queue vor der aktuellen MCP-Testphase: 8 Review-Aktionen, 0 Uploads; 1 archivierte Prüfung. Sieben historische Fälle unverändert, zusätzlich ein älterer fehlgeschlagener Archivierungsauftrag. Aktuelle synthetische Testaktionen werden gesondert bilanziert.
- Historischer Zwischenstand nach früheren MCP-Tests: **38 = 8 bestehende Prüfungen + 30 synthetische Test-/Archivaktionen**. Kein neuer Drive-Beleg; lokaler Cleanup ist noch nicht synchronisiert.
- Aktuelle Live-Queue: **183 = 175 Übertragungen + 8 Prüfungen**, 1 historische Entscheidung. Neue Benutzeraktionen seit der Stichtagsbereinigung vorhanden; in diesem Prüfauftrag keine produktiven Schreibtests. Drive-Abschluss wegen LEGACY_SOURCE_CHANGED weiterhin offen.
- `get_business_settings`: lesbar; nur bekannte historische Ausstellerfelder, Steuer-/Nummernfreigaben nicht bestätigt.
- Quelle: bestehende operative App vollständig erhalten; 74 ergänzt Mehrfacharchivierung und behebt nativ reproduzierte Fehler. Pixel weiterhin73, reale Queue183 und LEGACY_SOURCE_CHANGED zuletzt nur gelesen. Falschpositiver Normalisierungsfall im Quellcode behoben, tatsächlich abweichende Geschäftsdaten weiter geschützt. Keine Pixel-Installation, keine produktive Sync-/Konfliktentscheidung. Stichtagsbereinigung aus Einträgen16/17 erhalten. **Gesamtauftrag wegen ausstehender Pixel-/Google-Abnahme NICHT vollständig freigegeben.**

## Arbeitsstatus

| Phase | Status | Ergebnis |
|---|---|---|
| A – Bestand verifizieren | PASS | Regeln, Issue #1, Historien und Live-MCP geprüft; keine Betriebsdatenänderung |
| B – produktive Laptop-Abhängigkeit entfernen | PASS (Analyse/Zielpfad) | Abhängigkeiten ermittelt; native Ablösung in C in Arbeit, noch keine operative Abnahme |
| C – Pixel/Drive-Sync vervollständigen | PASS Host / NOT TESTABLE Gerät | Native Migration, Gesamtstände, Handler, Belege, Finanzabschluss; Geräte-/Google-Abnahme offen |
| D – 2 technische Testfehler bereinigen | Neue Live-Beobachtung / Belegabnahme offen | Beide früheren technischen IDs jetzt nicht mehr aktiv, 1 Konfliktprüfung archiviert; Übergang vor aktueller Testphase, kein Eingriff durch diese Tests |
| E – 7 Legacy-Konflikte kontrolliert schließen | Vorschau PASS / Ausführung gesperrt | Genau 7 bestätigt; direkte Drive-Abnahme und neue vollständige Sicherung fehlen |
| F – End-to-End-Abnahme | Host PASS / Live-MCP TEILWEISE, P0/P1 FAIL | 83 Werkzeuge angesprochen; 50/51 lokale Vergleiche PASS, Fotorolle FAIL, Drive-Rundlauf FAIL; Details Eintrag 8 |
| G – neuer Release-Build + Originalsignierung | PASS Kandidat / Gerätefreigabe offen | Originalsignierter 1.49.3/72 geprüft; keine Geräte-/Update-/Datenerhaltabnahme |
| H – Sync vereinfachen / Papierkorb | PASS Cloud / NOT TESTABLE live | Gemeinsame Zustände, Diagnose/Retry, begrenzte Medienabschnitte, sieben Löschbereiche, Restore und kontrollierte Rücknahme; Einträge 9–12 |

## Bekannte technische Queue-Einträge

### Historische 7
- `id_sha256_ca46d340bc52b9e93e578b4a47c1afa08b11c87a013e5c9dee7cd7624bc12824`
- `id_sha256_57c833b527f0ca0f4da04e79ff2ca787c57ab9a891833becee4c3e4faccf3914`
- `id_sha256_81dda83b42bd64510013bec7217cae7c4e9b01537341db98abc6f91ed52ca52a`
- `id_sha256_c09057d51603a99907adeef4bb7e9c332c97e4ddd2fbdf687ccfb45e83d35264`
- `id_sha256_2a69731a26c376695ce26e7d1788647b6ac11aa887024a8b3c981d84b2a5e2dd`
- `id_sha256_c28c6391b7e050a97a7034e9ae7622d4f3c9bf73c4d9e5e893fde925e9abe1bd`
- `id_sha256_04ecd644998e4d3962e834a23db5feeb936ea7d85d055acc300c448fd4ea7899`

### Technische 2 (historischer Stand vor der aktuellen MCP-Testphase)
- `mcp_owner_b6f1c56ba6534a250f6f5057175f365623155aa656d55fd0342f2517b4a539da` → `SYNC_COMPONENT_UPDATE_REQUIRED`
- `mcp_owner_43e89c1a01b88694f43a944e5c4109cb1a7e1a8c15378ee0c1116779bb1ce54c` → `ACTION_ID_CONFLICT`

Diese beiden IDs sind bei der aktuellen Live-Prüfung nicht mehr aktiv. Der achte bestehende Prüfauftrag ist jetzt `mcp_owner_739bc9fcba337de095f4dbb8beccf5e44f336e735468bd7c2c07399c98252a0e` (`archive`, `INVALID_ACTION`, verknüpfte Daten). In der aktuellen Testphase unverändert erhalten.

## Letzter geprüfter Sicherungsstand

Pixel-Geschäftsbackup:
- Backup-ID: `backup_4ef99a6925a1c66e0af5c6d10721fd5993cd706bfacef5aa7b1e33d2052cae69`
- SHA-256: `4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b`
- State Hash: `02c7a22f709b59765e96453acc778f7fae84217bc2772ae4a49ee4e6ee84cd5f`

Drive-Ausgangssicherung:
- `HausGarten-Laptop-before-v1.49-sync-2026-10-18.json`
- SHA-256: `8556108cafc9ad47a6ee82fa3ce49d01d7a057b42407c8098539dc3825f18cc3`

## Fortlaufender Arbeitsnachweis

### Eintrag 9 – Sync vereinfachen / Phase A Bestandsprüfung

- Auftrag: Sync überarbeiten und vereinfachen, einfache Löschfunktion mit selbstständiger Verarbeitung nach definierten Regeln. Ausgang 426159c, sauberer Arbeitsbaum, vorgesehene Cloud-Branch, Pflichtvorfahr vorhanden; Remote bestätigt, main unverändert. Live ausschließlich gelesen: Build 71 / Protokoll 4, Geschäftsbestand hashgleich, Queue weiterhin 38 (30 Übertragungen, 8 Prüfungen).
- Status: PASS Analyse; keine produktive Mutation. Aktuelle Regeln, Auftrag, Reparaturnachweis, zentrale Sync-/Owner-Dokumentation und Issue #1 gelesen. Keine zweite Runtime oder Datenbank.
- Abnahme: einheitliche Sync-Zustände für UI/MCP/Jobs; expliziter Retry bleibt auch nach pausierten automatischen Versuchen möglich; Transportfehler mit konkretem nächsten Schritt; fachliche Prüfungen blockieren unabhängige Änderungen nicht; Fotojournal/Hash/Vorschau erhalten dieselbe Rolle; vollständiges Capabilities-Schema. Neue Löschanforderungen transaktional, idempotent, versionsgeschützt in bestehenden wiederherstellbaren Archivstrukturen verarbeiten; keine Kaskade und keine physische Medienlöschung. Geschützte Belege und verknüpfte Datensätze bleiben erhalten. Rückgängig erzeugt eine geprüfte Wiederherstellung, löscht keine schon übertragbare Aktion. Auch Angebotsentwürfe und inaktive Leistungsbausteine im gemeinsamen Papierkorb. Alte Löschaktionen bleiben gesondert geschützt.
- Tests ausschließlich synthetisch: Offline → Retry → Provider-Readback, Queue/Receipts nach Wiederholung und Neustart, parallele Änderung, Beziehungs-/Finanzschutz, Foto-Rollen- und Schemaregression, Papierkorb/Restore, kleine Bildschirmbreiten. Native Instrumentierung nur ausführen, wenn eine zulässige isolierte Geräteumgebung verfügbar ist; kein operativer Pixelzugriff außer MCP-Lesen, keine Installation.
- Nächster Schritt: gemeinsame Sync-Steuerung/Diagnose und unveränderte Fotojournale, danach Papierkorb und UI.

### Eintrag 10 – Sync vereinfachen / Phase B gemeinsame Steuerung

- Status: PASS synthetische JVM und Build, Gerät/echter Drive noch NOT TESTABLE. SyncPolicy führt dieselben Zustände für UI, MCP und Jobsteuerung. Versionierte Diagnose enthält Fehlercode, Phase, Versuchszähler und nächsten Schritt. Expliziter Retry setzt einen pausierten Versuchshaushalt einmal pro Request zurück; Replay erfindet keinen Erfolg. Automatische Auslöser beachten Backoff/Rechte und maximal acht Fehlversuche.
- Fachliche Prüfungen und erfolgreicher Transport werden getrennt ausgewiesen. Fotoübertragung pro Lauf auf vier eindeutige Inhalte/etwa 8 MiB begrenzt (ein einzelnes größeres Original darf fortschreiten); erfolgreiche Dateien werden wiederverwendet, weitere Abschnitte nach Fortschritt geplant. Foto-Metadatenjournale behalten Rolle und Hash; Capabilities behalten das vollständige chunk_base64-Schema.
- Tests: 16 JVM-Suiten einschließlich 69 neuer Sync-/Löschprüfungen bestanden; Release-Build, Android-Testquellen-Kompilierung und Lint bestanden. Native Foto-/Undo-Abnahme ergänzt, nur kompiliert. Vorläufiger Build 1.49.3/72 unsigned, noch keine Signatur/Installation. UI-/Gesamtregression und Detailprüfung der neuen Löschpfade folgen.

### Eintrag 11 – Sync vereinfachen / Phase C Papierkorb und automatische Verarbeitung

- Status: PASS synthetische Regeln/erste Browserprüfung; keine produktive Löschung. Gemeinsame DeletionRules für UI/MCP/Journal: sieben Bereiche, Vorschau und CAS vor Queue-Eintrag, Prüfung nochmals beim Abgleich. Neue MCP-Werkzeuge preview_delete/delete_entity, vorhandene Namen erhalten (additiv 85, Protokoll weiterhin kompatibel 4). list_archive nach Entität/ID filterbar, stabil paginiert. Gemeinsamer Papierkorb in Mehr/Sync, Wiederherstellen, Löschknöpfe auch bei Angebotsentwürfen und Leistungsbausteinen; Kunden/Fotos nutzen denselben Weg.
- Regeln: zulässige Löschung im bestehenden owner_archive, keine Kaskade, Originaldateien erhalten; Kunden/Bausteine deaktiviert. Festgeschriebene/nummerierte Rechnungen und finale/angenommene Angebote geschützt, abhängige Aufträge/Arbeiten blockiert. Rückgängig hängt eine neue Restore-Aktion an; Originalaktionen und Hashes bleiben erhalten. Eine gescheiterte Archivierung kann nach erneutem Hashvergleich ausdrücklich mit keep_current zurückgenommen werden; nur diese sichere Variante erlaubt, Verlauf und Daten bleiben erhalten. Frühere Löschaufträge werden nicht automatisch ausgeführt oder quittiert.
- Tests bisher: 88 neue JVM-Vergleiche bestanden; 11 neue UI-Prüfungen mit 65 synthetischen Papierkorbeinträgen bestanden (weitere Schutzprüfung ergänzt). 266 Python-Regressionen bestanden. Ein bestehender UI-Test fand den vorübergehend ausgeblendeten bisherigen Sync-Knopf; Bedienung auf den einen bestehenden Knopf zurückgeführt, gezielte Wiederholung bestanden. Kein App-/Datenbankneubau, keine Betriebsdatenmutation. Endgültige Regression und Kandidatenprüfung folgen.

### Eintrag 12 – Sync vereinfachen / Phase D Regression und Original-Kandidat 72

- Ausgang 426159c auf der vorhandenen Cloud-Branch, main weiterhin a38765d. Implementierung und Kandidat werden zusammen mit diesem Eintrag in derselben Branch gesichert. Keine Desktopübernahme oder zweite Runtime. Geändert: gemeinsame native Sync-/Löschregeln, Coordinator/Jobs/Store/Owner-Adapter, Sync-/Papierkorb-/Angebots-/Fotooberfläche, Regressionen/Runner sowie bestehende zentrale Dokumentation und Downloadliste.
- Ergebnis **PASS Cloud, NOT TESTABLE neue Live-Abnahme**. 266 Python-Tests (37 ältere Schlüssel erzeugende QA-Signierungstests ausdrücklich ausgeschlossen); 16 JVM-Suiten/731 Vergleiche, davon 88 neue Sync-/Löschprüfungen. 25 Browser-Suiten final bestanden; die korrigierte Erwartung „ein vorhandener Sync-Knopf“ im neuen Papierkorbtest separat wiederholt: 12/12 PASS. Runner nimmt alle vier neueren UI-Suiten künftig mit. Echte native Foto-/Undo-/Provider-Instrumentierung ergänzt und kompiliert, nicht ausgeführt. Release-Build/Lint PASS: 0 Fehler, 15 Warnungen, darunter synchrone technische SharedPreferences-Statusschreibvorgänge und bestehende Plattformhinweise.
- Verhalten: ein zentraler Abgleichknopf, Übertragungen und Prüfungen getrennt; gleicher Zustandsautomat für UI/MCP/Jobs; wiederholte Requests erhalten Belege. Automatische Versuche halten Backoff ein, höchstens acht Fehlversuche je explizit gestartetem Budget, Retry-After begrenzt; ein neuer ausdrücklicher Versuch startet erneut, ein identischer Replay setzt das Budget nicht fortlaufend zurück. Fehlende dauerhafte Android-Ordnerrechte führen zur erneuten Auswahl am Gerät; keine Freigabe durch Code erteilt. Technische Details nennen Fehlercode, Phase und nächsten Schritt; erfolgreiche Provider-Rücklesung bleibt von Cloud-Abschluss getrennt.
- Medien: je Durchgang höchstens vier eindeutige Foto-Inhalte/etwa 8 MiB, ein einzelnes größeres Original darf übertragen werden. Gleicher Hash innerhalb eines Durchgangs nur einmal versucht, bestätigte Inhalte wiederverwendet, separate Vorschaubilder erhalten. Weitere Abschnitte nach Fortschritt; einzelne Medienfehler erhalten die betroffenen Aktionen und blockieren unabhängige Geschäftsdaten nicht. Keine Original-Base64-Daten in Geschäftssnapshots. Canonical Foto-Metadatenrolle wird nicht aus dem älteren Dateicache überschrieben. Schemafeld chunk_base64 bleibt vollständig registriert.
- Löschregeln: Kunden, Aufträge, Arbeiten, Fotos, lokale Rechnungsentwürfe, Angebotsentwürfe und Leistungsbausteine. Löschanforderung wird als geprüfte Archivaktion automatisch im bestehenden Journal verarbeitet; UI-Vorschau plus atomarer Hash-/Beziehungscheck, erneute Prüfung beim Sync. Kunden und Bausteine werden deaktiviert. Verknüpfte Aufträge/Arbeiten sowie gestellte/nummerierte Rechnungen und finale Angebote bleiben geschützt. Keine Kaskade, keine automatische physische Original-/Dateilöschung. Papierkorb/Restore benutzen owner_archive; Rollen, IDs und Originale bleiben erhalten. Gescheiterte Löschanforderung nur ausdrücklich hashgebunden mit keep_current zurücknehmen, Verlauf in owner_conflict_history; niemals Infrastruktur- oder ACTION_ID_CONFLICT dadurch übergehen.
- Reale Schlusslesung über MCP: weiterhin **1.49.2/71, 83 Werkzeuge**, 38 Aktionen = 30 frühere synthetische Test-/Archivaktionen + 8 bisherige Prüfungen. Gespeicherte Geschäftssammlungen unverändert, Hash `0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b`; Counts 51/2/173/95/12/32/1. In dieser Entwicklungsphase ausschließlich gelesen, keine zusätzlichen Testaktionen und kein Cleanup an produktiven Queues. Neue Quelle registriert additiv 85 Werkzeuge (preview_delete/delete_entity), Protokoll 4 bleibt kompatibel; neue Werkzeuge noch **nicht** im installierten Connector bestätigt.
- Öffentlich geprüfter Kandidat: `downloads/Haus-Garten-1.49.3-72-original-update-kandidat.apk`, **at.hausgarten.service, 1.49.3/72, 34.410.051 Bytes**, minSdk 26, target/compile 37. Höher als der tatsächlich gelesene installierte Code 71. Unsigned SHA256 `df27ef215ddc210e3a923e184dde14bb4cbc405af5e3f42fbc658688139ac9df`; signiert SHA256 **`158fc4cf676ae0e42ef45d661d5575fec2ababd1911177db5a4dc259457d18a8`**.
- Signierung: vorher ausschließlich Vorhandensein der fünf HG_ORIGINAL_*-Variablen geprüft. Genau ein Originalsignierlauf über tools/sign_original_apk.py; keine Werte ausgegeben/gespeichert und kein Ersatzschlüssel. Zertifikat SHA256 **`a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`** stimmt mit Originalvorgabe überein; apksigner verifiziert v2/v3, genau einen Signer; zipalign 16 KiB PASS. Temp-Verzeichnisse danach 0, private Schlüsseldateien im Repository 0. 403 APK-Assets bytegleich mit der Quelle, Privacy PASS, operative Seedzeilen 0. Keine Installation und kein Vergleich mit dem Zertifikat der installierten APK.
- Bereitstellung im privaten Repository und [privater Drive-Download](https://drive.google.com/file/d/1zS-mRZZV5V20mq0404CbqMvfcovXbO6B/view), keine öffentliche Freigabe. Connector liefert nach erneutem vollständigem Dateiabruf eine herunterladbare Dateireferenz und 34.410.051 Bytes. Kein unabhängiger Vergleich der erneut heruntergeladenen Drive-Dateibytes behauptet.
- Offen: tatsächlicher Drive-Transportfehler des installierten 71 bleibt ohne neuen Diagnoselauf unbestimmt; nach vollständiger Sicherung und ausdrücklicher Installationsfreigabe 72 abnehmen. Danach die bestehenden 30 Testaktionen samt möglichem alten Fotorollen-/Archivvergleich prüfen, nicht umschreiben oder löschen; acht Altprüfungen bleiben geschützt. Alte sieben ausschließlich im bestehenden Backup-/Preview-/keep_current-Workflow mit gesonderter Bestätigung. Nach freigegebenem Update private MCP-Verbindung neu starten und tools/list/get_capabilities auf 85 Namen/aktuelle Schemata prüfen; nötigenfalls ChatGPT-Verbindung neu verbinden. Kein administrierbares Connector-Manifest im Repository. Kamera, Hintergrundwechsel, echte Medien-Recovery, native Transaktionen sowie Update/Datenerhalt bleiben separat nachzuweisen. Originalsignierung allein ist kein Update- oder Datenerhalt-Test.

### Eintrag 8 – echte MCP-Abnahme auf dem installierten Pixel (18.10.2026)

- Auftrag: „Teste App vollständig per mcp Schnittstelle“. Cloud-Ausgangspunkt `9509df6919c65ed913e1864965e087d8dc814dee`, unverändert auf `codex/original-signing-cloud-setup-20261018`; Pflichtvorfahr vorhanden. main unverändert (`a38765d586c3ea02b999e770e5ecb22b34642962`). Nur diese zentrale Statusdatei geändert; kein neuer Build, keine Installation, kein ADB, keine Schlüsselverwendung.
- **Gesamtergebnis: TEILWEISE BESTANDEN, KEINE vollständige Abnahme.** 83/83 Werkzeugnamen real angesprochen; bei 67 mindestens eine erfolgreiche Antwort, 16 nur Schutz-/Fehlerfälle. Eine erfolgreiche Antwort allein gilt nicht als erfolgreicher Fachprozess. 51 explizite fachliche Vergleiche: 50 PASS, 1 FAIL (Fotorolle). Zusätzlich Drive-Rundlauf FAIL und Capabilities-Schema inkonsistent.
- Tatsächlich installiert laut erneutem `get_capabilities` und `integration_health`: at.hausgarten.service, 1.49.2/71, DB 4, Snapshot 2, Protokoll 4, `pixel_drive_v1`, ready/pixel_business_write/write_enabled=true. Owner-Werkzeuge sowie Rechnungs-/Angebotswerkzeuge im Connector sichtbar. Kataloghash `ad4c1af1cd1f5b58db4ba18563d1165748c132b91099a929bac6a0980a5db8c6`; Hashabweichung allein nicht als Ursache eines Refresh-Problems interpretiert.
- Live-Start: 8 aktive Prüfungen, 0 Uploads, 1 archivierte Konfliktprüfung. Sieben historische Fälle und ein früherer `INVALID_ACTION`-Archivierungsauftrag; keine Auflösung ausgeführt. Der Übergang von früher 9 auf 8 fand vor dieser Testphase statt.
- Neue lokale Geschäftssicherung vor Teständerungen: `backup_9778e41a06e30d409bb8281417410ca5344f07aeae60367e1709bea5275b75f6`, 372514 Bytes, SHA-256 `3a4d578b87ec40d3a4ce83e5e277664888aecbe75b4a3b6f92ff8925609eb56b`. `inspect_backup`, Dateimetadaten, Liste und idempotente Wiederholung geprüft. Scope Geschäftssnapshot, offene Aktionen und lokal vorhandene Medien/Dokumente; **keine vollständige Gerätesicherung und keine neue vollständige Drive-Sicherung**.
- Testphase: 18:45–19:05 UTC; rein synthetische Datensätze, Namespace `mcpE2E_20261018_mve6e3g8`, sowie vorgesehener technischer Testkunde. Keine echten Kunden, Arbeitsleistungen, Preise oder Geschäftsrechnungen verändert. Zwei anfänglich unvollständige Test-Aufrufe auf Service-Wrapper wurden mit korrektem `entry.id` wiederholt; diese Parameterfehler sind kein App-Befund. Zu große Listenlimits wurden abgewiesen und anschließend mit ≤100 gelesen. Keine Produktionsinhalte als Fixtures/Logs gespeichert.

#### Abgeschlossene lokale Testphase

- Kunde neu/lesen/gezielt ändern, Zusatzfelder zusammenführen, Replay, stale Hash und Archiv/Wiederherstellung mit Original-ID bestanden.
- Auftrag neu/lesen/ändern, Replay, Request-ID mit abweichendem Inhalt (`REPLAY_MISMATCH`), stale Hash und lokales Archiv bestanden.
- Arbeitsaufzeichnung: 0 Stunden in beiden Create-Wegen abgewiesen, 0,01 h gespeichert, 0,02 h gezielt geändert, stale Hash blockiert. Manuelle Abrechnungsmarkierung an/aus getrennt von Rechnung und b.b.; identische Einsatz-/Empfängerzuordnung geprüft. Kein Wechsel zu einem fremden Kunden getestet.
- Timer: IDLE → RUNNING → PAUSED → RUNNING → STOPPED_PENDING_REVIEW → RUNNING → STOPPED_PENDING_REVIEW → CANCELLED → IDLE; Wiederherstellung derselben Testsitzung als PAUSED, erneut verworfen, abschließend IDLE. Keine zusätzliche Timer-Arbeitsaufzeichnung. Übernahme einer unbekannten Sitzung mit SESSION_CONFLICT blockiert; erfolgreicher `complete_work_session` nicht ausgeführt.
- Rechnungsentwurf neu/lesen/ändern (Zahlungsziel 7 Tage), Replay gleiche ID, stale Hash abgewiesen. Rein synthetische Null-Euro-Testposition; keine reale Preisbehauptung. Status bleibt Entwurf; Steuerstatus bleibt unconfigured. `confirmed=false` für Abschluss am Connector blockiert. Entwurf ohne ausgestelltes Original-PDF nicht zum Teilen freigegeben; Entwurf weder als bezahlt markierbar noch stornierbar. Keine Nummer reserviert, Rechnung ausgestellt oder versendet.
- Angebotsentwurf neu/lesen/ändern/Replay/stale Hash und alle vier Dokumentarten gespeichert. Validator meldet fehlenden Preis/Preisquelle, Aussteller-, Steuer- und Empfängerpflichtangaben; Entwurf bleibt unvollständig. Unbestätigter Abschluss/Umwandlung am Connector blockiert; Teilen eines Entwurfs abgewiesen. Kein gültiges finales Angebot, PDF, erfolgreicher Duplikat- oder Auftrag-Erfolgsweg getestet.
- Pflanzen-/Leistungskatalog und Vorlagen tatsächlich gelesen. Ein eigener Baustein mit bewusst fehlendem Preis erstellt/gezielt geändert, stale Hash blockiert, dauerhaft inaktiv. Keine bestehenden Preise geändert.
- Foto-MCP: zwei synthetische JPEGs hochgeladen, Datei/SHA wieder gelesen, Änderung der Beschriftung und stale Hash geprüft. Datei-Replay und Foto-Replay erzeugen dieselben IDs. Kleines Bild 96×64/524 Bytes bytegleich. Großes Bild 4200×2800, EXIF 6: nach Speicherung 2048×3072, 69.360 → 37.625 Bytes. Zurückgelieferte Datei in Cloud unabhängig dekodiert; SHA-256 `e99b1c7bf705b301dc6c6b64b2c4f534046b23188a8df3794b643bc087251f44`, Orientierungstag entfernt. **Rollenänderung fehlgeschlagen**, siehe Befunde.
- Geräte-/Unternehmenseinstellungen ausschließlich gelesen; absichtlich falsche Versionshashes bei Update und Restore abgewiesen. Reale Wiederherstellung, globale Settingsänderung und Entzug echter Berechtigungen nicht ausgeführt.

#### Abgeschlossene Sync-/Integritätsprüfung und Befunde

1. **P0 – Drive-Rundlauf live fehlgeschlagen.** Drei `request_sync`-Anforderungen wurden angenommen; kein neuer Lauf/Provider-Readback nachweisbar. Letzter gemeldeter Versuch 18:41:58.596338Z, letzter Sync 18:41:43.930867Z. Nutzer meldet nach manuellem Versuch „Lokal gespeichert · Abgleich fehlgeschlagen“. `android_provider`, `provider_readback`, `cloud_confirmed=false`. Kein direkter Drive-Serverbeleg, kein Medien-Retry-/Neustart-/Server-Roundtrip-Erfolgsnachweis. Die genaue Ursache ist über die vorhandene MCP-Antwort nicht feststellbar; fehlende Konto-/Ordnerrechte, Netzfehler oder ein Versionskonflikt werden nicht ungeprüft behauptet.
2. **P1 – Fotorolle wird lokal überdeckt.** `update_photo` mit neuer Beschriftung und `role=before` quittiert Erfolg; `get_photo` liefert die neue Beschriftung, aber weiterhin `role=documentation`. Gleicher Wert im lokalen Archiv. Quellursache: `MainActivity.LocalStore.pendingActions(false)` überschreibt in jedem Fotojournal die Nutzlastfelder mit älteren `photo_attachments`-Metadaten einschließlich `role` (um Zeile 1084); `pendingActions(true)` macht dies nicht. Daraus entsteht zusätzlich ein Risiko unterschiedlicher Aktions-/Vergleichshashes zwischen Vorschau und Drive. Der spätere Drive-Fehler dieses Testpfades ist **nicht tatsächlich ausgeführt**; eventuelle neue Konflikte müssen nach funktionierendem Sync hashgebunden geprüft werden. Keine unveränderliche Aktion nachträglich geändert.
3. **P2 – Capabilities-Schema unvollständig.** `write_file_chunk.inputSchema.required` nennt `chunk_base64`, in `properties` fehlt das Feld. `PixelMcpReadTools.withoutBinary` entfernt rekursiv alle gleichnamigen Schlüssel und trifft auch Schemadefinitionen. Connector und tatsächlicher Upload können das Feld korrekt nutzen; Werkzeugnamen stimmen 83/83 überein. Kein HTTP-401-Umgehen und keine erfundene Connector-Freigabe.
4. **Diagnosegrenze:** UI `syncStatus()` enthält `last_error`, `user_action_required`, `retry_count`, `next_retry`; `get_sync_overview` exponiert diese Transportdiagnose nicht. Deshalb kann MCP die genaue Ursache und notwendige erneute Geräte-/Kontofreigabe aktuell nicht zuverlässig bestimmen. Quellcode zeigt, dass sowohl REST als auch ein gespeicherter Android-Dokumentbaum grundsätzlich automatisch vorgesehen sind; ein Provider-Verbot wird daraus nicht abgeleitet.
5. **Funktionsgrenzen:** `list_photos` hat kein offset/cursor und höchstens 100 Ergebnisse; eine vollständige MCP-Blätterfunktion für größere Kundenbibliotheken fehlt. `archive_entity` unterstützt keine offers; synthetische Angebotsentwürfe können über MCP nicht ins Archiv verschoben werden. Diese Grenzen wurden am Schema statisch festgestellt, kein Last-/Pagination-Erfolgsnachweis daraus abgeleitet.

#### Nachbereitung und verbleibende Warteschlange

- Geschäftssammlungen im gespeicherten Ausgangsstand bleiben hashgleich: `0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b`. Rohbestand vorher/nachher: Kunden 51, Aufträge 2, Arbeiten 173, Rechnungen 95, Fotos 12, Leistungsbausteine 32, Angebote 1. Diese Werte schließen die ausstehenden Testprojektionen nicht ein.
- Bestehender technischer Testkunde, seine ursprüngliche Foto-Liste sowie Unternehmens-/Geräteeinstellungen unverändert. Alle 8 ursprünglichen Prüfungen mit identischen client_ids, action_hashes und current_hashes; Legacy-Vorschau weiterhin 7 und Hash `68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7`. Keine Löschung/Altentscheidung, kein Beleg erfunden, keine Queue gelöscht.
- Sechs eigene Einträge lokal im Archiv: ein neuer synthetischer Kunde, Auftrag, Arbeitsaufzeichnung, Rechnungsentwurf und zwei Fotos. Originalbytes nicht physisch gelöscht. Testbaustein inaktiv. Angebot bleibt klar markierter Entwurf ohne Auftragsverknüpfung, da kein Angebotsarchiv-MCP existiert. Timer IDLE; b.b.-Testmarkierung entfernt.
- **Aktive Queue danach: 38 = 8 vorherige Prüfungen + 30 neue synthetische Testaktionen (einschließlich Archivierungen).** Neue Aktionen vollständig den acht eigenen Testentitäten zugeordnet; keine zusätzliche persistierte Review-Aktion. Lokales Archivieren ist noch kein synchronisierter Cleanup. Die 30 dürfen weder gelöscht noch als bestätigt ausgegeben werden; nach Reparatur des Syncs normale hashgebundene Verarbeitung/Belegprüfung, beim Fotorollenpfad gegebenenfalls kontrollierte Konfliktprüfung. Keine weiteren Massen-Schreibtests bis dahin.
- Synthetische IDs: Kunde `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e`; Auftrag `id_sha256_2248b4da5ada771c10deeb5c060b378603875e3389c0524478469d14844ca9f9`; Arbeit `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f`; Entwurf `id_sha256_abbdad18e36cf5365aa24e00762ad87fcb9f14c656603af9a9b1f1e185d63217`; Fotos `id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc`, `id_sha256_06e7df1f19442bfb007e6d46dc69a5dc3f771862668078e6906f50bfd14071af`; Angebot `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5`; Baustein `id_sha256_494ef829768228263de344ca1e8feb695eddc5c41ec3398da289b1674618728f`.

<details>
<summary>30 neue synthetische Journalaktionen für die spätere Belegprüfung</summary>

| client_id | Art | Synthetische Entität |
|---|---|---|
| `mcp_pixel_3dbdcb71bcfeeacc77ef16acd4f56ea3409bf6ac` | order | `id_sha256_2248b4da5ada771c10deeb5c060b378603875e3389c0524478469d14844ca9f9` |
| `mcp_pixel_865559d81eba43a3e55fbf1c46d7144c38291f05` | order | `id_sha256_2248b4da5ada771c10deeb5c060b378603875e3389c0524478469d14844ca9f9` |
| `mcp_pixel_f5d406529401d97a4a3e69e3610d3615df0e3007` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_pixel_be8230f383d7a760522af8eb5a9c2d4072376c6d` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `photo_change_b173108b-6808-4768-9ac5-a88809254202` | photo | `id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc` |
| `mcp_owner_e6009ed5da9f9b59ee0052564b7e412fd2c989710652cb8aa5b4e7fcfb0d47a0` | photo | `id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc` |
| `mcp_pixel_5fad9aac1d3c265ba4e34dbdc549fad70deb3b54` | invoice | `id_sha256_abbdad18e36cf5365aa24e00762ad87fcb9f14c656603af9a9b1f1e185d63217` |
| `mcp_pixel_2fc08b3849ed3422c465b7548755d452c0a14914` | invoice | `id_sha256_abbdad18e36cf5365aa24e00762ad87fcb9f14c656603af9a9b1f1e185d63217` |
| `mcp_owner_109b45a637af58056e51bd1400494fcaeef8640554ac8d8ecf347205c47a1de9` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_owner_98964f7240f95746b88b46f34461bbf40fb049acd9f108f275f554db76517f38` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `photo_change_d82ee904-3a41-4512-b955-ae039e57dfab` | photo | `id_sha256_06e7df1f19442bfb007e6d46dc69a5dc3f771862668078e6906f50bfd14071af` |
| `mcp_owner_3ba2b1aea9e8eb1833fac42036a37ad554fb18b3e1992ed5f0fca918240ed8a4` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_owner_9ee3eea31c7922722870a45e5668d178be6a67df88b5c2e59c933cd1d522fa79` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_owner_4c20f8c5fcf095a01628ed1eb9cc666fdcfeb26dc0bd24aa76a4471cbb839d33` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_pixel_d5c69e42db79333ee3f84ca62124788c12b26c3c` | customer | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_pixel_189665c3d9988867a93bbfc5d74ebeefd0c10056` | customer | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_owner_e334a11ea03e913c49e81ff3f5108661f8918a6fb39fd97d17f523e99a23226a` | service_template | `id_sha256_494ef829768228263de344ca1e8feb695eddc5c41ec3398da289b1674618728f` |
| `mcp_owner_d5cbd686add476c13461db3d6dd2c21bec87fe96d6f1d0fba414dd9069baf0cb` | archive | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_owner_c058585c0525d592e4274c404d0028504213cba4805e35eb9866592d13e053c1` | service_template | `id_sha256_494ef829768228263de344ca1e8feb695eddc5c41ec3398da289b1674618728f` |
| `mcp_owner_f2d9a9e105654f267f0dfe424d30686a8748a947d956bf466edd55fecf92ef5d` | restore | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_owner_c79539cc896f180248ec536553c7e195b98dc04610af8203054c6f535a6c1b74` | archive | `id_sha256_04d3738af3f78ea5d75c30d1acbd9986f25f427865a39a8f0fcffdab2eab9a6e` |
| `mcp_owner_b3dc470efa9ee77d5a65df1d865f2ff265eb7accc12811df63b006509f4b78f7` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_owner_74d17eed7fa434c230c4e3078b976ae42bda726509a737a79c13199072bacea7` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_owner_545331ff3cc5b786d96f025d96ca7698585aaa1805570f6fcb4c93f1ff8c9a64` | worklog | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_owner_cfd7d3d752798b94e0a077ac414ba80325124dd06c2f6e1bc1db6fb9052d7e31` | archive | `id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc` |
| `mcp_owner_a4d7bcf72f83992e29dd876e789bdf588e79599311b0df55e68f109027b962f2` | archive | `id_sha256_06e7df1f19442bfb007e6d46dc69a5dc3f771862668078e6906f50bfd14071af` |
| `mcp_owner_f17a6bee01bd9064f92e786b59092327e91f18e82958fe49cf430ba9d99c5cfe` | archive | `id_sha256_abbdad18e36cf5365aa24e00762ad87fcb9f14c656603af9a9b1f1e185d63217` |
| `mcp_owner_4707ed8c6bbeb5809f441731647f00a2d17b46630d4faa05ecc6f0d7b538cc36` | archive | `id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f` |
| `mcp_owner_1011d4301e40f240268203f722c479d9dddae9fc832bdef89c3ee322efa3b667` | offer | `id_sha256_7556f393bca85a490d4665531a06d7f7f612a1568554e9894911b74e313f87c5` |
| `mcp_owner_55d3c61c4271a2a7d775274bb1449c7d0199a7364c711c71c1b10de011b153b5` | archive | `id_sha256_2248b4da5ada771c10deeb5c060b378603875e3389c0524478469d14844ca9f9` |

</details>

#### Tatsächliche Funktions- und Berechtigungsmatrix

„Bestanden“ bedeutet in dieser Tabelle den angegebenen lokalen MCP-Teilablauf, keine vollständige Geräte- oder Sync-Abnahme. Für 16 Werkzeuge gab es ausschließlich Negativprüfungen; bei `confirmed=false` blockiert bereits das Connector-Schema, nicht erst der native Ablauf.

| App-Bereich | Tatsächlich angesprochene MCP-Werkzeuge | Fachlicher Umfang | Erforderliche Rechte/Prüfungen | Tatsächlicher Teststatus / Barriere |
|---|---|---|---|---|
| Verbindung/Version | `integration_health`, `get_capabilities`, `get_business_summary` | Lesen | Privater Tunnel | Live lesbar; 83 Namen, Schemaabweichung bei chunk_base64. |
| Kunden/Suche | `search_customers`, `get_customer`, `create_customer`, `update_customer`, `search_business_data` | Lesen, Anlegen, gezielte Änderungen | Owner/Write, Hash bei Updates | Synthetischer Kunde: CRUD, Zusatzfelder, Replay/CAS bestanden. |
| Aufträge | `search_orders`, `get_order`, `get_open_orders`, `create_order`, `update_order` | Lesen, Anlegen, Ändern | Owner/Write, Hash | Testauftrag bestanden; Archiv als lokale Projektion geprüft. |
| Arbeitsaufzeichnungen | `list_work_records`, `get_work_record`, `get_customer_work_records`, `get_work_record_summary`, `create_work_record`, `create_customer_work_record`, `update_work_record`, `set_work_record_billed`, `reassign_work_record` | Lesen, Anlegen, Ändern, manuelle Abrechnungsmarkierung, Zuordnung | Owner/Write, Hash/Begründung | 0-Stunden-Negativtest beider Create-Wege; 0,01→0,02 h über Kundenweg; Markierung an/aus; identische Zuordnung bestätigt. Wechsel zu anderem Kunden nicht getestet. |
| Timer/Pausen | `get_active_work_session`, `start_work`, `start_break`, `end_break`, `stop_work`, `continue_work`, `discard_work_session`, `restore_work_session`, `complete_work_session` | Lesen, Start/Pause/Fortsetzen/Stop/Verwerfen/Wiederherstellen/Übernahme | Owner/Write, eigene Session-ID | Start bis Verwerfen/Wiederherstellen bestanden, am Ende IDLE. complete nur falsche Session abgewiesen; kein Timer-Worklog erzeugt. |
| Persönliche Markierung | `set_personal_paid` | b.b. an/aus | Owner/Write; gerätelokal | Bestanden; billed und Rechnung unverändert. |
| Fotos/Dateitransfer | `list_photos`, `get_photo`, `begin_file_upload`, `write_file_chunk`, `create_photo`, `update_photo`, `get_file_metadata`, `read_file_chunk` | Lesen, begrenzter Upload, Anlegen, Metadatenänderung | Owner/Write; Datei-ID/SHA; Hash bei Update | Kleine/große synthetische Datei und Leseschutz bestanden. Beschriftung geändert; Rollenwechsel FAIL. Keine MCP-Fotopagination (limit≤100, kein offset). |
| Rechnungsentwürfe | `list_invoices`, `get_invoice`, `create_invoice_draft`, `update_invoice_draft` | Lesen, Entwurf erstellen/ändern | Owner/Write, CAS/Request-ID | Null-Euro-Testentwurf erstellt, gelesen, geändert; Replay und stale Hash bestanden. Nicht gestellt/bezahlt/versendet. |
| Rechnungsabschluss/-korrektur | `request_invoice_issue`, `prepare_invoice_share`, `create_invoice_correction`, `update_invoice_payment` | Bestätigter Abschluss, PDF-Übergabe, Korrektur, Zahlung | Explizite Nutzeranweisung; Aussteller/Nummernfreigabe; Online-CAS; Original-PDF | Nur Schutzprüfungen: confirmed=false am Connector, Entwurf ohne Original-PDF, Zahlung/Korrektur auf Entwurf blockiert. Erfolgswege nicht ausgeführt. |
| Angebote | `list_offers`, `get_offer`, `get_offer_context`, `create_offer_draft`, `update_offer_draft`, `validate_offer` | Lesen, Kontext, Entwurf erstellen/ändern/prüfen | Owner/Write, Hash/Request-ID | Vier Dokumentarten, fehlender Preis/Pflichtangaben, Replay/CAS geprüft. Kein gültiges finales Dokument ohne Aussteller-/Steuerfreigabe. |
| Angebotsabschluss/Weitergabe | `finalize_offer`, `duplicate_offer`, `prepare_offer_share`, `convert_offer_to_order` | Bestätigter Abschluss, Duplikat, PDF-Übergabe, Auftrag | Owner/Write; aktuelle Hashes; ausdrückliche Bestätigung | Nur Negativtests: unbestätigt, Entwurf, unbekannte ID. Kein PDF-/Konvertierungs-Erfolgsnachweis. Kein Angebotsarchiv-Werkzeug vorhanden. |
| Pflanzen-/Leistungskatalog | `search_catalog`, `get_catalog_entry`, `search_offer_templates`, `get_offer_template`, `create_catalog_entry`, `update_catalog_entry` | Lesen, Leistungsbaustein erstellen/ändern/deaktivieren | Owner/Write für Bausteine, Hash | Pflanze und bestehende Bausteine gelesen; eigener preisloser Baustein erstellt/geändert, stale Hash blockiert, bleibt inaktiv. Keine Pflege echter Preise. |
| Archiv | `list_archive`, `archive_entity`, `restore_entity` | Lesen, reversibel archivieren/wiederherstellen | Owner/Write, Entity-/Archivhash, Beziehungsschutz | Eigener Kunde archiviert/wiederhergestellt/erneut archiviert; sechs eigene Einträge lokal archiviert. Drive-Übernahme ausstehend. |
| Sync/Konflikte | `get_sync_overview`, `get_sync_status`, `get_sync_conflicts`, `request_sync`, `preview_legacy_conflict_resolution`, `resolve_sync_conflict`, `prepare_legacy_conflict_backup`, `close_legacy_conflicts` | Lesen/Anfordern; hashgebundene Entscheidung | Owner/Write; Backup+Bestätigung für Altabschluss; direkte Drive-Freigabe | Lesen bestanden. request_sync nur angenommen, kein neuer Lauf sichtbar; Nutzer meldet Fehler. 8 Altprüfungen unverändert. Nur unbekannter Konflikt/unbestätigter Abschluss/fehlender Drive-Beleg negativ getestet. |
| Sicherung | `list_backups`, `inspect_backup`, `create_backup`, `restore_backup` | Lesen, Geschäftssicherung, geschützte additive Wiederherstellung | Owner/Write; State-Hash, leere Queue/IDLE für Restore | Backup erstellt/geprüft/Replay; veralteter Restore-Hash abgewiesen. Keine tatsächliche Wiederherstellung des produktiven Stores. |
| Einstellungen/Aussteller | `get_settings`, `get_business_settings`, `update_settings`, `update_business_settings` | Lesen, versioniert ändern | Owner/Write, Hash; keine OAuth-/Tunnelrechte durch Code | Lesen und stale-Hash-Abweisung bestanden, bestehende Einstellungen unverändert. Keine positive Änderung produktiver Konfiguration. |

#### Explizite Live-Vergleiche

| Prüfung | Ergebnis |
|---|---|
| App/Protokoll/Owner-Freigabe | PASS |
| Werkzeugnamen: Connector/Pixel 83/83 | PASS |
| Kunde Zusatzfelder gezielt zusammengeführt | PASS |
| Kunde Request-Replay gleiche ID | PASS |
| Kunde veralteter Hash abgewiesen | PASS |
| Kunde Archiv/Wiederherstellung Original-ID | PASS |
| Auftrag Update gelesen | PASS |
| Auftrag Replay gleiche ID | PASS |
| Request-ID mit anderem Inhalt abgewiesen | PASS |
| Auftrag veralteter Hash abgewiesen | PASS |
| Nullstunden abgewiesen | PASS |
| Arbeitsaufzeichnung erstellt/gezielt geändert | PASS |
| Arbeitsaufzeichnung veralteter Hash abgewiesen | PASS |
| b.b. ändert Arbeits- und Rechnungsstand nicht | PASS |
| Manuelle Abrechnungsmarkierung getrennt | PASS |
| Arbeitszuordnung identischer Ziel-IDs | PASS |
| Timer Pause/Fortsetzen/Stop | PASS |
| Timer Verwerfen/Wiederherstellen/IDLE | PASS |
| Timer falsche Sitzung abgewiesen | PASS |
| Foto Upload/Erstellen/Ändern/Lesen | FAIL – Rolle bleibt documentation statt before; Beschriftung geändert |
| Foto veralteter Hash abgewiesen | PASS |
| Datei Versionsschutz | PASS |
| Kleines Bild bytegleich | PASS |
| Großes Bild verkleinert/EXIF gedreht | PASS |
| Gespeicherte Bildbytes unabhängig dekodiert/Hash | PASS |
| Foto/Chunk Replay ohne Duplikat | PASS |
| Rechnungsentwurf erstellt/geändert/gelesen | PASS |
| Rechnungsentwurf Replay gleiche ID | PASS |
| Rechnungsentwurf veralteter Hash abgewiesen | PASS |
| Abschluss ohne Bestätigung im Connector abgewiesen | PASS |
| Entwurf ohne PDF nicht teilbar | PASS |
| Entwurf nicht bezahlbar/stornierbar | PASS |
| Angebot erstellt/geändert/Replay | PASS |
| Angebot veralteter Hash abgewiesen | PASS |
| Fehlender Preis/Pflichtangaben blockieren Angebot | PASS |
| Vier Angebotsarten gespeichert | PASS |
| Angebotsabschluss/Umwandlung ohne Bestätigung abgewiesen | PASS |
| Entwurf nicht zum Teilen freigegeben | PASS |
| Katalogbaustein erzeugt/geändert/inaktiv | PASS |
| Katalog veralteter Hash abgewiesen | PASS |
| Backup erstellt/geprüft/Replay | PASS |
| Settings/Backup veraltete Hashes blockiert | PASS |
| Kein unbekannter Konflikt aufgelöst | PASS |
| Altabschluss ohne Bestätigung abgewiesen | PASS |
| Legacy-Sicherung ohne direkten Drive-Beleg blockiert | PASS |
| Originale Geschäftssammlungen unverändert | PASS |
| Altaktionen/Hashes unverändert | PASS |
| Keine zusätzliche Konfliktprüfung | PASS |
| Aussteller-/Geräteeinstellungen unverändert | PASS |
| Bestehender Testkunde/Foto-Liste unverändert | PASS |
| Sechs eigene Einträge archiviert | PASS |

#### Nicht als getestet oder freigegeben ausgeben

- Erfolgreicher Drive-/Medien-Server-Rundlauf, Netzabbruch/Offline-Wechsel, wiederaufgenommener Upload nach Prozessabsturz, echte App-Neustart-/Snapshot-Migration: nicht live nachgewiesen.
- Mehr als 10 bzw. 100 Kundenfotos, komplette Fotopagination, reale Kamera, Wischgesten, Bildschirmdarstellung und Pixel-Leistung: in dieser MCP-Phase nicht getestet. Zwei neue synthetische Originale; frühere Hosttests mit 105 Metadatensätzen/21 Importdateien sind davon strikt getrennt. Wegen defektem Sync keine weiteren Massenaktionen erzeugt.
- Tatsächlicher Entzug/Wiedererteilung von Tunnel-, Android-, Drive- oder Organisationsrechten nicht getestet; laufende produktive Verbindung nicht absichtlich unterbrochen. Authentifizierter privater Tunnel und Write-Gate funktionieren für die angegebenen synthetischen Tests. Direkte Drive-Freigabe/Serverbeleg fehlt.
- Echtes Angebot finalisieren/PDF/Annahme→Auftrag, tatsächlicher zentraler Rechnungsabschluss/Nummernreservierung/PDF/Versand/Zahlung/Korrektur, vollständige Restore-Abnahme und Legacy-Abschluss nicht durchgeführt. Business Settings enthalten noch keine bestätigte Steuer-/Nummernkonfiguration.
- Installations-, Zertifikatsprüfung der installierten APK und Datenerhalt bei Update nicht durch MCP nachweisbar. Es wurde weder installiert noch deinstalliert, keine App-Daten gelöscht und keine neue APK gebaut. Die Originalsignaturprüfung des zuvor bereitgestellten Cloud-Kandidaten bleibt ein gesonderter früherer Nachweis.
- Nächster Schritt: tatsächlichen Drive-Transportfehler diagnostizierbar machen und beheben; Fotorollen-/Journalprojektion sowie Capabilities-Schema korrigieren, gezielt regressieren; danach dieselben synthetischen Aktionen mit echten Belegen abnehmen. Aktueller Build bleibt ohne vollständige P0/P1-Abnahme.


### Eintrag 0 – Initialisierung

Status: OFFEN

Nächster Schritt:
1. `AGENTS.md` lesen
2. `CODEX-AUFTRAG-GARTENPIRAT.md` vollständig lesen
3. aktuellen `main`-Head und Cloud-Branches prüfen
4. Phase A durchführen
5. diesen Status aktualisieren

---

## Vorlage für weitere Einträge

### Eintrag N – <Phase / Thema>

- Datum:
- Commit vorher:
- Commit nachher:
- Status: PASS / FAIL / NOT TESTABLE
- Geänderte Dateien:
- Ausgeführte Tests:
- Ergebnis:
- Offene Probleme:
- Nächster Schritt:

### Eintrag 1 – Phase A / verifizierter Cloud-Ausgangspunkt

- Datum: 2026-10-18 (UTC)
- Commit vorher: `354cb496264d09ed92957aeea09b1b71ad932389` (sauber).
- Commit nachher / Implementierungsbasis: `a38765d586c3ea02b999e770e5ecb22b34642962`. Bestehende Cloud-Branch ausschließlich per Fast-forward vorgezogen; main unverändert. GitHub bestätigt main sowie Reparatur-Branch `90bfc1660e0345d19a461f12f74b7e163492532a`; beide enthalten die bisherige Cloud-Implementierung und den Pflichtvorfahren `05fdcbe`. Veralteter origin/Cloud-Trackingref `36e5567` nicht verwendet.
- Geänderte Dateien: CODEX-STATUS.md (Arbeitsnachweis); alle neuen Vorgaben gelesen, einschließlich Originalsignierung, Reparaturhinweis und Issue #1.
- Ausgeführte Prüfungen: Git-Status/Ancestry, Repository-/Remotevergleich; live ausschließlich get_capabilities, integration_health, get_sync_overview.
- Ergebnis PASS: Repo 1.49.1/70; installierter Pixel 1.49.0/69, Paket at.hausgarten.service, Datenbank 4, Protokoll 3, 83 Werkzeuge, Kataloghash `0741dc4553e5e43baf537d996cd4efbb79eaa6f4fb0bcf6c543f3ea412e11a55`; ready/write_enabled. Snapshot meldet Schema 1. Drive transport android_provider, offers/service_catalog false, cloud_confirmed=false, provider_readback. Queue unverändert 9 (6 Löschprüfungen, 1 Kundenkonflikt, 2 technische Aktionen); archiviert 0. Keine Queue-Aktion geändert.
- Offene Probleme: Produktiver Import/Nummernpfad setzt noch externen Prozessor voraus; Originalsignatur der installierten APK und native Kamera-/Updateabnahme nicht aus MCP prüfbar. Installation ohne neue Sicherung und ausdrückliche Freigabe gesperrt.
- Abnahmekriterien B/C: validierte native Handler und dauerhafte Aktionshash-Belege; versionierter Pixel-Gesamtstand nach Neustart wieder einlesen; alte Originaldatei erhalten; unbekannte Settings/Nummernstände fail-closed; echte Geschäftsdatensätze bleiben unverändert.
- Nächster Schritt: B/C – Pixel/Drive-native Verarbeitung und isolierte Regressionen.

### Eintrag 2 – Phase B / Abhängigkeiten und nativer Zielpfad

- Datum: 2026-10-18 (UTC); Basis weiterhin a38765d.
- Status: PASS für Abhängigkeitsanalyse; Umsetzung/Abnahme in C offen.
- Befunde: DriveSyncCoordinator las immer die alte HausGarten-Laptop.json. SAF publish war leer, direkte REST-Veröffentlichungen wurden beim nächsten Lauf nicht als Ausgangsstand eingelesen. Settings und Compare-Freigaben erwarteten Python-exportierte Capabilities; Abschluss/Korrektur warteten auf invoicing.py.
- Entscheidung: unveränderliche Gesamtstände und verknüpfte Commit-Belege im vorhandenen Drive-Ordner lesen/schreiben; Altdatei unverändert erhalten. Native Handler migrieren das additive Schema und ihre eigenen Capabilities. Settings übernehmen nur nachgewiesene Felder, fehlende Werte bleiben unbekannt. Finanzabschluss benötigt online geschützte, explizit bestätigte Pixel-Nummernhoheit; SAF allein bietet keinen atomaren Nummernvergleich.
- Geänderte Dateien bisher: PixelDriveState.java, DrivePrimaryState.java, DriveSyncCoordinator.java, DriveDirectFiles.java, DriveRestClient.java, DrivePublication.java, DriveJournalRules.java, OwnerJournalRules.java, PixelOwnerAccess.java, MainActivity.java, tools/run-followup-java.py; noch in Arbeit, keine Freigabe.
- Tests: statische Abhängigkeitsprüfung; automatisierte Qualifikation folgt nach vollständiger Integration in C. Keine produktive Aktion. Legacy-Python-/Web-Code und Tests bleiben als Kompatibilitäts-/Testreferenz erhalten.
- Nächster Schritt: C, atomare native Regeln, Wiederanlauf und synthetische Tests.

### Eintrag 3 – Phase C / native Implementierung und isolierte Qualifikation

- Datum: 2026-10-18 18:30 UTC; Ausgangscommit a38765d; Änderungen noch auf derselben Cloud-Branch.
- Status: PASS (statisch/automatisiert), NOT TESTABLE (Gerät/echtes Google).
- Dateien: neue Produktionsklassen PixelDriveState, DrivePrimaryState, DriveNumbering, InvoiceIssueRules; bestehende DriveSyncCoordinator/DirectFiles/RestClient/Publication/JournalRules, OwnerJournalRules, MainActivity, PixelOwnerAccess und MCP-Adapter aktualisiert. Bestehende UI owner-ui, invoice-draft, mobile, sync-dashboard; bestehende native Abnahmen und JVM-/Browser-Tests erweitert. Neue PixelDriveStateTests; Java-Runner ergänzt. Version auf 1.49.2/71 erhöht.
- Ergebnis: Pixel verarbeitet die vorhandenen Entitäten, Settings, Archiv-/Konfliktaktionen und Korrekturentwürfe nativ. Neue Gesamtstände/Commitketten werden im selben Drive-Ordner unveränderlich veröffentlicht und beim nächsten Lauf wieder gelesen; alte HausGarten-Laptop.json wird nicht überschrieben. Parallel geänderte Altquelle, fremder primärer Pixel, Lücken/Forks und andere Aktionshashes stoppen. JSON bleibt frei von Originalmedien. Fehlende Settings bleiben unbekannt, bekannte/zusätzliche Felder bleiben erhalten. CAS auch für Settings/Fotos/Entwürfe und lokale Offline-Vorgängerketten. Native Leseansichten projizieren nur validierte Änderungen; eine verspätete 3-Stunden-Variante kann eine ausgestellte 2-Stunden-Leistung auch in der Ansicht nicht überschreiben. Vorschauen erzeugen keine Belege.
- Neuer Finanzjournaltyp pixel_invoice_issue_v1 wird von alten Prozessoren nicht finalisiert. Internes MCP-Protokoll 4, weiterhin 83 Werkzeugnamen und kompatible MCP-RPC-Verhandlung. Expliziter Abschluss bindet Entwurf, Arbeitsstände, Aussteller und Empfänger; bestätigte alleinige Pixel-Nummernhoheit, aktueller Ausgangszähler und Online-CAS erforderlich. Negative CAS-Probe ausschließlich auf technischer Probe-Datei, nie auf dem echten Zähler. Fehlender ETag oder nicht abgewiesener Vergleich blockiert. Persistente Reservierung bei verlorener Antwort wiederverwenden; PDF separat hashgebunden, Geschäftsmutation/Beleg im gemeinsamen Gesamtstand. Kein automatischer Versand/Bezahltstatus; b.b. bleibt getrennt.
- get_sync_overview ergänzt technische Bestandszahlen und Sammlungshashes ohne Betriebsinhalte; native Kamera-/Bitmap-/PDF-/SAF-Tests nur vorbereitet/kompiliert.
- Tests PASS: 15 JVM-Suiten / 637 Fälle (davon 73 neue Pixel-/Drive-Fälle); 266 Python-Tests ohne schlüsselerzeugende QA-Suite; 21 bestehende Browser-Suiten plus access-integrity (8), sync-customer-gallery (14), offers-photo-library (14). Synthetische Daten/Brücken, keine Betriebsdaten. Pflanzenquelle 181 Profile/362 Bilder unverändert, Spiegel geschrieben/geprüft.
- Offene Probleme: reale Google-CAS-Unterstützung, Android-Speicher-/Providerablauf und Update/Datenerhalt nicht bestätigt. Native Testquellen sind kein Ausführungsnachweis.

### Eintrag 4 – Phase D / technische Aktionen geprüft, Live-Ausführung blockiert

- Datum: 2026-10-18; Basis a38765d plus qualifizierte Cloud-Änderungen.
- Status: PASS synthetisch / NOT TESTABLE live.
- Die Angebotsaktion b6f1…539da bleibt live SYNC_COMPONENT_UPDATE_REQUIRED; der ursprüngliche Aktionshash lautet e1df44d3146a8d0e4ce1dd901f45332093aab65e3e0b0ae4e4f0986ffb6c40c0. Die spätere Kontrollaktion 43e8…e54c bleibt ACTION_ID_CONFLICT (51562d617c3418e3538a009a5b3b0ac149122c2920e8064b2666056be8046208). Vollständige IDs oben erhalten.
- Native Tests übernehmen ein synthetisches Angebot genau einmal und archivieren die überholte Kontrollaktion nur nach geprüftem Originalhash als superseded/no_business_data_changed. Manipulation blockiert; keine blinde Kontrollentscheidung.
- Live läuft weiterhin 69. Keine alte Aktion umgeschrieben, erneut erzeugt, gelöscht oder manuell aufgelöst. Neuer Handler benötigt freigegebenes Update und direkten Drive-Abgleich.

### Eintrag 5 – Phase E / Legacy-Vorschau geprüft, keine Auflösung

- Datum: 2026-10-18; Status Vorschau PASS / Ausführung NOT TESTABLE.
- Live preview_legacy_conflict_resolution bestätigt genau die sieben oben dokumentierten IDs, keep_current, no_business_data_changed. Preview-Hash 68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7. Sechs DELETE_REVIEW_REQUIRED, ein WRITE_CONFLICT; keine Löschung ausgeführt.
- Vorhandenes Geschäftsbackup nur inspiziert: SHA256 unverändert 4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b; historische Sicherungszahlen 51 Kunden/1 Auftrag/173 Arbeiten/95 Rechnungen/12 Fotos/32 Bausteine/0 Angebote. Das sind Backup-Zahlen, kein neuer vollständiger Geräte-Bestandstest. Alte Live-Leseübersicht: 50 Kunden/1 Auftrag/171 Arbeiten; alte Löschvorschläge verstecken Einträge in der Leseüberlagerung. Neuer Code erhält deren Sichtbarkeit und ändert dadurch keine Datensätze.
- Geprüfter aktueller State-Hash bei inspect_backup: eb8b38a8f16ae473086a2667875081db6c8c0b64205a17cae283ae127dfdb6ea (einschließlich flüchtiger Sync-Metadaten, kein reiner Geschäftshash). Kein vollständiger Vorher/Nachher-Geschäftshash über Build 69 verfügbar.
- Neue Backup-Funktion erfordert erst direkte Drive-Verbindung mit Serverbeleg und ausschließlich sieben historische Restfälle. Sichert SQLite, lokale Medien, aktuelle Pixel-Veröffentlichung samt Commit und erhaltene Altdatei. Backup-/Vorschau-/Aktions-/Datensatzhash werden erneut geprüft. keep_current darf keine Foto-Originale nachträglich bereinigen.
- Es wurde kein neues Backup als vollständige Gerätesicherung ausgegeben und keine Sammelentscheidung gesendet. Queue bleibt 9, historische Archivierungen 0.

### Eintrag 6 – Phase F / Testgrenzen und verbleibende Abnahme

- Datum: 2026-10-18; Status Host PASS / Pixel-End-to-End NOT TESTABLE.
- Isoliert geprüft: Migration ohne Settings-Defaults, unbekannte Felder, Angebot/Katalog/Settings und Offline-Entwurfsänderungen, Hash-Konflikte, Requests/Replays, Infrastruktur-Replay und Supersession, 7 keep_current-Fälle ohne Geschäftsänderung, Crash vor Commit/Antwortverlust, Zähler-Rennen/fehlender Versionsschutz, Rechnungsrennen, Archiv/Beziehungen, Rendering und Medien-Metadaten. PDF-Validierung Python tatsächlich ausgeführt; neue native PDF-Erzeugung nur kompiliert.
- Foto-Browser: 105 synthetische Metadaten, 30/60 Vorschauen, 21 sequenzielle Importe plus fehlerhafte Datei; keine native Abnahme von 100 Originalfotos. Bestehende Komprimierung 3072 px/JPEG 84, kleine Bilder erhalten, getrennte Originale/Vorschaudateien unverändert. Kamera am Pixel NOT TESTABLE.
- Live lesend: 83 Werkzeuge einschließlich aller 11 Angebots-/Kontextwerkzeuge und create_invoice_draft/update_invoice_draft/request_invoice_issue/prepare_invoice_share. Sichtbarkeit beweist keine Schreibfunktion. get_business_settings auf 69 bestätigt SETTINGS_UNAVAILABLE; offers/service_catalog bleiben false. Keine Live-Schreibtests, Timeraktion oder Geschäftsdatenmutation durchgeführt.
- P0 offen: neue App am Pixel abnehmen und direkte Drive-Veröffentlichung nachweisen; technische 2 plus historische 7 sicher abschließen; gesamter MCP-Testplan mit isoliertem Testkunden nach Update. P1 offen: Google-Konto/OAuth-/Ordnerrechte, echte CAS-Negativprobe, vollständige Gerätesicherung und ausdrückliche Installationsfreigabe, nativer Kamera-/In-place-Update-/Datenerhalt-Test. Keine ununterbrochene Verfügbarkeit zugesichert.
- Nächster sicherer Schritt: Originalsignierten Cloud-Prüfkandidaten konkret bereitstellen. Danach vollständige Gerätesicherung und ausdrückliche Updatefreigabe; kein automatisches Installieren.

### Eintrag 7 – Phase G / geprüfter Original-Kandidat und erneute Live-Lesung

- Datum: 2026-10-18 18:38 UTC. Ausgangscommit a38765d586c3ea02b999e770e5ecb22b34642962; Implementierungs-Abschlusscommit **545bd683380244d1e10ca45f75d66a652173eb45**. Artefakt/Status werden anschließend separat auf derselben Branch gesichert. main unverändert, kein Force-Push/History-Rewrite.
- Status: Build/Originalsignierung PASS; produktive Freigabe und Gesamtauftrag ausdrücklich NICHT abgeschlossen.
- Build: :app:assembleRelease, :app:compileReleaseAndroidTestJavaWithJavac, :app:lintRelease offline in Codex Cloud PASS; 0 Lint-Fehler/12 Warnungen. Plant-Sync --write/check, Privacy-Gate, Artefakt-/Assetvergleich PASS. Native Abnahmen kompiliert, nicht ausgeführt.
- APK: downloads/Haus-Garten-1.49.2-71-original-update-kandidat.apk, Paket at.hausgarten.service, Version 1.49.2, versionCode 71 (> Repo 70 und live 69); minSdk 26, target/compile 37, nicht debuggable. Größe **34.401.795 Bytes**. Unsignierte SHA256 5f7e7b0ead99ecd5763981a9bcb33ccbf9b1d8759d19b40b170d77ea9b8e563c. Finale SHA256 **a57973c9268b15e50facbb0f0fd5ccde91e98ec6f7a15dbb2ad9213f54c9e1fa**.
- Vor Signierung ausschließlich das Vorhandensein der fünf dokumentierten HG_ORIGINAL_*-Variablen geprüft, vollständig. Ein tatsächlicher Signierlauf ausschließlich tools/sign_original_apk.py; keine Ersatzschlüssel oder QA-/Debug-/Actions-Signierung. Vorhandenes Keystore-Zertifikat mit konfiguriertem Original abgeglichen; normaler einzelner APK-Signer erneut unabhängig geprüft. Zertifikat-SHA256 **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**. Zusätzlich Übereinstimmung mit erhaltenem Cloud-Kandidaten 69 bestätigt; das ist kein Zugriff auf die installierte Signatur.
- apksigner verify PASS: ein Signer, v2/v3 true; v1/v3.1/v3.2/v4 false. zipalign -c -P 16 4 PASS. Alle 402 Assets bytegleich; neue operative Klassen vorhanden; null operative Seedzeilen, private_markers_checked=0. Cleanup PASS: null hg-original-signing-Tempordner und null Schlüsseldateien nach geprüften Endungen im Repository. Keine Geheimwerte geloggt oder dauerhaft gespeichert.
- Privater [Drive-Download](https://drive.google.com/file/d/1sSRrb97nKbYlh_SEKetxAUkGMLIeiCqF/view) als neue APK-Datei, keine vorhandene Datei verändert; Eigentümerrecht bestätigt, keine öffentliche Freigabe hinzugefügt. Raw-Connector-Rückabruf lieferte vollständige Dateireferenz und dieselbe Größe (34.401.795); lokale APK-SHA geprüft, kein unabhängiger Remote-Bytehash behauptet.
- Quellkatalog: 83 Werkzeuge, interne Version 4; erwarteter tool_catalog_hash **584505048c5d7cc6a3baec85428a0a4633b8254d9f78068b74e82207acd30e7a**. Nach freigegebenem Update initialize/tools/list derselben privaten Verbindung aktualisieren, tatsächliche Version/Hash und Gates erneut lesen.
- Live nach Build weiterhin 1.49.0/69, Protokoll 3, Kataloghash 0741dc4553e5e43baf537d996cd4efbb79eaa6f4fb0bcf6c543f3ea412e11a55. Tatsächlich angebotene Angebotswerkzeuge: list_offers, get_offer, validate_offer, search_offer_templates, get_offer_template, get_offer_context, create_offer_draft, update_offer_draft, finalize_offer, duplicate_offer, prepare_offer_share, convert_offer_to_order. Rechnungswerkzeuge: list_invoices, get_invoice, create_invoice_draft, update_invoice_draft, request_invoice_issue, prepare_invoice_share, create_invoice_correction, update_invoice_payment. **Sichtbar, neue Schreibimplementierung live nicht getestet.**
- Live-Vorher/Nachher: Queue **9 → 9**, archiviert **0 → 0**, sechs DELETE_REVIEW_REQUIRED + ein WRITE_CONFLICT + je eine SYNC_COMPONENT_UPDATE_REQUIRED/ACTION_ID_CONFLICT unverändert. Legacy-Preview-Hash unverändert 68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7; damit Auswahl, Aktionshashes und sieben geprüfte aktuelle Datensatzhashes unverändert. Live-Lesezahlen unverändert 50 Kunden/1 Auftrag/171 Arbeiten, 1.001,75 Stunden/170 als unabgerechnet markiert. Kein vollständiger Geschäftshash über alten Connector vorhanden; keine weitergehende Integritätsbehauptung.
- Kein Produktivschreiben über Pixel-MCP, keine Installation, kein ADB, kein Kamera-/In-place-Update-/Datenerhalt-Test. Erfolgreiche Signierung ist kein Update- oder Datenerhalt-Nachweis.
- Offene P0/P1 aus Eintrag 6 bleiben bestehen. Nächste Geräteaktion benötigt vollständige Sicherung und ausdrückliche Freigabe gemäß AGENTS.md: „keine automatische Installation ohne Sicherung und Freigabe“. Danach direkte Google-Verbindung/Versionsschutz mit synthetischen Daten abnehmen; erst dann D, anschließend neues Backup und bestätigte E-Auflösung und der vollständige F-Plan. Keine Laptop-Handlung erforderlich.


### Technische Hashliste der nur gelesenen Ausgangsfälle

Kein Betriebsinhalt, keine angewendete Entscheidung. Hashes aus get_sync_conflicts; Legacy-Vorschau vor/nach Arbeit identisch.

| client_id | Fehler | Action-Hash | Current-Hash |
| --- | --- | --- | --- |
| `id_sha256_ca46d340bc52b9e93e578b4a47c1afa08b11c87a013e5c9dee7cd7624bc12824` | DELETE_REVIEW_REQUIRED | `d4ad7e0bb45f60ed925cd3fa294343aa44efe84ed0ca2627ac9c63bd74db74e8` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `id_sha256_57c833b527f0ca0f4da04e79ff2ca787c57ab9a891833becee4c3e4faccf3914` | DELETE_REVIEW_REQUIRED | `bb84d7424474d267d75981f29cc69914ff4bc7054e82732feae089b46d170364` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `id_sha256_81dda83b42bd64510013bec7217cae7c4e9b01537341db98abc6f91ed52ca52a` | DELETE_REVIEW_REQUIRED | `81fdd1bac9a83b6f81502513dacd59e58eac4e1ff10b7889b657372478d519ce` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `id_sha256_c09057d51603a99907adeef4bb7e9c332c97e4ddd2fbdf687ccfb45e83d35264` | DELETE_REVIEW_REQUIRED | `06a95cf238bd35098f5b3dd9147b16b26eaf063e4fe504f154c22859ded65c7b` | `78957fa4902bb23535b4e994935a1d9794680abc2f6f1be7ba43f3fb5dbf1123` |
| `id_sha256_2a69731a26c376695ce26e7d1788647b6ac11aa887024a8b3c981d84b2a5e2dd` | DELETE_REVIEW_REQUIRED | `2b45a83fc17f6eb72163f0a58b3779f7904259518b22dece9a7e88120f54bb41` | `d205eab650bc4f727a52ddfa2f29cfcaa7e7d3697820d28076aa01b845313d0e` |
| `id_sha256_c28c6391b7e050a97a7034e9ae7622d4f3c9bf73c4d9e5e893fde925e9abe1bd` | WRITE_CONFLICT | `258d26b477b41ec0a3f097d893217e8c22c988071eb11a98d6a03d89d9ea0701` | `30599c5c7807db1f9ccc8ceb0381df8c914ba43b38f3f7494b49b08d3324ae6f` |
| `id_sha256_04ecd644998e4d3962e834a23db5feeb936ea7d85d055acc300c448fd4ea7899` | DELETE_REVIEW_REQUIRED | `d5a7f9020c75ec994d0d8b09aee5a94cc2b53648bce8bf6f4cc81f9f8c237a8a` | `e3c8da0a874c1b44a7a1d27cdbe63ff1b06ccbe20fd77508a7d56be87561d7e6` |
| `mcp_owner_43e89c1a01b88694f43a944e5c4109cb1a7e1a8c15378ee0c1116779bb1ce54c` | ACTION_ID_CONFLICT | `51562d617c3418e3538a009a5b3b0ac149122c2920e8064b2666056be8046208` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |
| `mcp_owner_b6f1c56ba6534a250f6f5057175f365623155aa656d55fd0342f2517b4a539da` | SYNC_COMPONENT_UPDATE_REQUIRED | `e1df44d3146a8d0e4ce1dd901f45332093aab65e3e0b0ae4e4f0986ffb6c40c0` | `74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b` |

### Bereitstellung bestätigt

- GitHub-Commit und normaler Push bestätigt: **8e9a33af6552199d3f8b3b0a521d1b7554f09584** auf codex/original-signing-cloud-setup-20261018. Enthält Implementierungscommit 545bd68 und geprüfte APK. main weiterhin a38765d586c3ea02b999e770e5ecb22b34642962.
- Der folgende reine Statuscommit ergänzt nur diese Hashliste und die Remote-Bestätigung. Code/APK bleiben bytegleich; Gesamtauftrag bleibt wegen der dokumentierten P0/P1-Gates offen.


### Eintrag 13 – Papierkorb/Bedienung, Phase A: geprüfter Ausgang

- 2026-10-18, Ausgang 16854f285991c6a406dee1eb23c45e1ccea4ff32, sauber auf vorgesehener Cloud-Branch; Remote identisch, main a38765d unverändert, Pflichtancestor vorhanden. Keine Übernahme oder Datenmutation.
- Live ausschließlich lesend: Pixel 1.49.3/72, Protokoll 4, 85 Werkzeuge. Queue 51 (43 Übertragungen / 8 Prüfungen), Historie 1; gespeicherter Geschäftshash weiterhin 0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b. 13 zusätzliche Fotoaktionen seit letzter Phase vom Benutzer; nicht als Testdaten benutzt.
- Konkrete Sync-Barriere jetzt sichtbar: ursprünglicher Drive-Gesamtstand parallel verändert; persistierte Ordnerrechte vorhanden. Keine automatische Quellstand-Ersetzung oder Konfliktauflösung.
- Abnahmekriterien: verknüpfte Fotos verhindern Papierkorb für Arbeit/Auftrag nicht; Originale erhalten, nur betroffene Foto-Zuordnungen kontrolliert lösen. Nutzerpräzisierung: Kundenverknüpfung ausschließlich beim Verschieben in den Papierkorb lösen; kein eigener Entkopplungs-Workflow und keine globale Migration. Endgültige Entfernung nur aus Papierkorb mit ausdrücklicher Bestätigung, aktuellem Archivhash, idempotenter Aktion, minimalem Löschbeleg und Schutz gegen Wiederbelebung. Geschützte Rechnungen/finanzielle Beziehungen bleiben geschützt. Dateibereinigung erst nach dauerhaft bestätigter Löschaktion, keine fremden/gemeinsam verwendeten Originale entfernen; historische Sicherungen bleiben gesondert.
- UI: Hauptaktionen sichtbar, Zusatzaktionen gebündelt, Filter bei Bedarf; keine gleichzeitigen Lösch-/Restore-Vorgänge, Fokus/Entwürfe/Zurück erhalten. 320/360/426 px und große Schrift prüfen. Bestehende Architektur und alle Funktionen erhalten.
- Nächster Schritt: gemeinsame Journalregeln und Papierkorb, anschließend kompakte Bedienung und isolierte Regressionen. Keine produktiven Löschtests, Installation oder Legacy-Auflösung.


### Eintrag 14 – Papierkorb/Bedienung, Phase B/C: Implementierung

- Gemeinsame Regeln: Fotozuordnungen atomar innerhalb derselben Archivaktion mit Vergleichshash lösen. Nur betroffene Felder; andere Kunden/Fotos bleiben unverändert. Restore verbindet ausschließlich unveränderte Fotos. Altes Journal bleibt unverändert, alte Kundenarchivierung behält ihre damalige Semantik; sechs historische Löschaktionen weiterhin nicht ausgeführt.
- Endgültige Einzelentfernung über preview_purge/purge_entity und neuen fail-closed Journaltyp purge_v1. Aktueller Archivhash, ausdrückliche Bestätigung, dauerhafte Request-ID/Quittung. Minimaler Tombstone im bestehenden owner_archive, keine zweite Datenbank. Geschützte Belege sowie aktuelle/archivierte Abhängigkeiten blockieren Kaskaden. Alte Formulare/Journale/Backup-Übernahme dürfen purgierte IDs nicht neu anlegen.
- Dateibereinigung nach überprüfter Veröffentlichung und lokaler Snapshot-Übernahme, eigene wiederholbare Schritte/Belege. Referenzprüfung einschließlich aktivem Bestand, Papierkorb und ausstehenden Aktionen; gemeinsam verwendete Originale bleiben erhalten. Native Hash-/Dateinamenprüfung, Drive-REST zusätzlich ETag/If-Match; fehlender Versionsschutz bleibt blockiert. Sicherungen und historische unveränderliche Journale werden nicht überschrieben; keine Zusage einer forensischen Datenvernichtung. Provider-Löschung separat nach Rücklesen prüfen.
- Nutzerantwort verbindlich umgesetzt: keine manuelle Kunden-Entkopplungsfunktion. Fotos werden nur im Papierkorbablauf gelöst. Allgemeine Fotobibliothek unter Mehr hält erhaltene Bilder erreichbar (30 pro Abschnitt, lazy Thumbnails).
- UI: Mehr enthält Navigation; Unternehmens-/Fotospeichereinstellungen auf vorhandener Einstellungsseite. Kundenkontakt/Zusatzaktionen und zusätzliche Fotofilter aufklappbar, Fotoaktion in einem Mehr-Menü. Ein gleichzeitiger Papierkorb-Schreibvorgang; Bestätigung/Fehler lassen ursprüngliche Daten und Wiederherstellungszugang erhalten. Keine Funktion entfernt.
- Bisher PASS: 17 JVM-Suiten (neue TrashWorkflowTests zunächst 67 Prüfungen), neue UI-Prüfungen inkl. 105 unzugeordneter Fotometadaten, nativer Release-/Testquellen-Compile. Native Neustart-/Datei-Cleanup-Abnahme vorbereitet und kompiliert, nicht ausgeführt. Bestehende Browser-Tests werden auf neue sichtbare Navigation angepasst; Gesamt-Regression und finale APK noch ausstehend.
- Neuer Kandidat 1.49.4/73, Paket unverändert, Protokoll 5 mit zwei neuen Tools (87 Quellwerkzeuge). Installierter Pixel bleibt 72/Protokoll 4; neue Methoden dort nicht getestet. Konkrete Drive-Parallelstand-Barriere bleibt geschützt offen.


### Eintrag 15 – Papierkorb/Bedienung, Phase D: Qualifikation und Original-Kandidat

- Cloud-Quellbasis 16854f285991c6a406dee1eb23c45e1ccea4ff32. Vorgesehene Branch unverändert; keine Desktopübernahme, keine Historienänderung oder main-Mutation. Abschluss der Implementierung/Qualifikation: PASS; produktive Geräte-/Drive-Freigabe weiterhin NICHT erteilt.
- Zusätzliche Integritätskorrektur: bereits im Papierkorb befindliche Fotos werden beim Entfernen ihres Kunden/Auftrags/Arbeitseintrags ebenfalls versionsgeschützt entkoppelt. So entsteht keine wechselseitige Foto-/Kunden-Löschsperre. Erhaltenes Foto lässt sich nach endgültigem Entfernen eines unverknüpften Kunden ohne diesen Kunden wiederherstellen. Finanzielle Abhängigkeiten bleiben geschützt. Neuere Fotoentscheidungen blockieren nicht die Wiederherstellung des Elternsatzes und werden nicht überschrieben.
- Fehlgeschlagener Purge besitzt einen sicheren Ausstieg: nur keep_current, Originalaktion plus aktueller Archivhash geprüft; keine Anwendung eines veralteten Löschvorschlags. Queue-Quittung und Konflikthistorie bleiben erhalten. Keine Queue wird blind bereinigt.
- Automatisiert PASS: **266 Python-Tests**, **17 JVM-Suiten / 823 Prüfungen**, darunter **86 neue TrashWorkflow-Prüfungen**; **25 Browser-Suiten**, davon sync-trash mit 18 Prüfungen. Altes Kontakt-/Foto-/Einstellungs-Testing auf den realen neuen Menüweg angepasst; frühe Fehlversuche dokumentierten verborgene Knöpfe, Schlusslauf vollständig PASS. Kein Test mit Betriebsdaten. QA-Signierungstests mit echter Schlüsselerzeugung ausgeschlossen, Regressionstests erhalten.
- Browser: 105 synthetische unzugeordnete Fotometadaten, Begrenzung 30/60, lazy Thumbnails; zusätzlich vorhandene 21 sequenzielle Importe mit fehlerhafter Einzeldatei und 105 Kundenfotos getestet. Bestätigung/Abbruch, Stale Hash, stabiler Request beim Fehler/Retry, erhaltene Restore-Schaltfläche, verknüpftes Foto im Papierkorb, Settings-Navigation, Kontaktmenü, 320/360/426 px und 150%-Schrift bestanden. Screenshot-Kontrolle in Cloud Chromium; keine Aussage über Pixel-Leistung oder reale 100 Originaldateien.
- JVM: Zuordnung/Restore aktiver und archivierter Fotos, keine fremden Kundenänderungen, CAS-Rennen, geschützte Belege, minimaler Tombstone, Wiederholungen und Unterbinden gleicher purgierter IDs, geteilte Medien und Pending-Referenzen, Konfliktrücknahme, Drive-DELETE mit ETag/If-Match, fehlendem ETag/412/403 synthetisch bestanden.
- Android: assembleRelease, compileReleaseAndroidTestJavaWithJavac, lintRelease offline PASS; **0 Fehler / 17 Warnungen**. Native SQLite-/Original-/Purge-Replay-/Neustart-/Cleanup-Abnahme vorbereitet und kompiliert, ausdrücklich NICHT ausgeführt. Echte SAF-/Drive-Dateilöschung, Kamera, Hintergrundbetrieb und Crash-/Updateabnahme am Gerät nicht getestet.
- APK: downloads/Haus-Garten-1.49.4-73-original-update-kandidat.apk; **at.hausgarten.service**, **1.49.4 / 73** (> live 72 und >66), minSdk 26, target/compile 37, nicht debuggable. **34.418.378 Bytes**, SHA256 **131287f91d05d33988393ab3f444f9987f9a7bf0329b51a717dd225a5c901eab**. Unsignierter SHA256 a0e57be35c081e1c835ced6cfce04e1f0bb3e73c02904a3a744ead7011f56ded (34.411.290 Bytes).
- Originalsignierung: ausschließlich Vorhandensein aller fünf dokumentierten Variablen geprüft; ein tatsächlicher Lauf ausschließlich tools/sign_original_apk.py. Keinen Schlüssel erzeugt, keinen Fallback verwendet. Tatsächlich verifiziertes Originalzertifikat **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**. Genau ein Signer, v2/v3 gültig, v1/v3.1/v3.2/v4 false; zipalign -c -P 16 4 PASS. 405 Assets bytegleich und neue operative Klassen enthalten, Source-/APK-Privacy PASS, null operative Seedzeilen/private_markers_checked=0. Cleanup PASS: null private Signierungs-Tempordner und null Schlüsseldateien nach geprüften Endungen. Keine Geheimwerte gespeichert oder geloggt.
- Neue private APK-Drive-Datei: https://drive.google.com/file/d/1F1Dijrkqx9Rf7RoO3bGN8bU-Km6d_YNm/view . Connector-Rückabruf liefert vollständige Dateireferenz und exakt 34.418.378 Bytes. Keine vorhandene Datei oder Freigabe geändert. Lokaler SHA256 geprüft; kein unabhängiger Remote-Bytehash behauptet.
- Live-Abschluss nur lesend: Build **72**, Protokoll **4**, **85** tatsächlich angebotene Werkzeuge, Owner-Write aktiv. Neue Quellversion: Protokoll **5**, **87** Werkzeuge, Hash **0cd1b37e492eb9370c481a636f45f81c129694d60cd2d6bd25e0517a110eaab0**; zwei neue Werkzeuge noch NICHT im installierten Connector angeboten/getestet. Nach gesichertem, ausdrücklich freigegebenem Update Verbindung neu initialisieren und Werkzeugkatalog/Get-Capabilities real erneut prüfen.
- Live-Vorher/Nachher unverändert: gespeicherte Counts 51 Kunden /2 Aufträge /173 Arbeiten /95 Rechnungen /12 Fotos /32 Bausteine /1 Angebot; Geschäftshash **0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b**. Queue **51 →51** (43 Übertragungen /8 Prüfungen), Historie **1 →1**. Sieben alte Prüfungen nicht angewendet/geschlossen. Zusätzliche 13 Fotoaktionen stammen aus Benutzernutzung vor dieser Arbeit, keine erzeugten MCP-Tests.
- P0 offen: bestehender Drive-Gesamtstand parallel verändert (LEGACY_SOURCE_CHANGED, Build72 zeigt den Text noch ohne Fehlercode). Beide Stände und alle 51 Vorgänge erhalten; keine automatische Auswahl/Ersetzung. Keine Garantie, dass die bestehende Warteschlange mit diesem UI-Auftrag schon aufgelöst wurde. Datei-Cleanup wartet bis zur geprüften Veröffentlichung/Übernahme, kann bei fehlenden Provider-/Lösch-/CAS-Rechten ausstehend bleiben; Geschäftserfassung wird davon nicht blockiert. Historische Backups und unveränderliche Journale bleiben getrennt erhalten; endgültig aus der App ist keine forensische Datenvernichtung.
- Keine Installation, kein ADB/direkter Pixel-Zugriff, keine Betriebsdatenänderung; keine tatsächliche Löschung oder Konfliktentscheidung über MCP ausgeführt. Originalsignierung ist kein Update-/Datenerhalt-Test. Installation nach AGENTS.md erst nach vollständiger Sicherung und ausdrücklicher Freigabe.

- Implementierungs-Abschlusscommit: **da039031671c91d438c19f12c5a65414cdc74d9b**. Anschließender Bereitstellungscommit enthält ausschließlich die verifizierte APK, Prüfsumme und aktualisierte zentrale Download-/Abschlussangaben.


### Eintrag 16 – Stichtagsbereinigung / Phase A: Auswahl und Sicherung

- Neuer ausdrücklicher Betriebsauftrag: aktive Arbeitsaufzeichnungen mit Arbeitsdatum bis einschließlich 2026-07-31 entfernen; ab 2026-08-01 vollständig erhalten. Standard ist der wiederherstellbare Papierkorb, keine endgültige Vernichtung und keine Änderung an Kunden, Aufträgen, Fotos oder Rechnungen.
- Ausgang 6f2ae1530e4c065cdba839684069ef87a97840f1, sauber auf vorgesehener Branch, Pflichtancestor vorhanden, Remote identisch, main a38765d unverändert. Pixel 1.49.3/72, Protokoll 4, 85 Werkzeuge, Write aktiv. 73 nicht installiert.
- Vollständige Auswahl durch zwei nicht abgeschnittene Zeitfenster: bis 2026-03-31 49 Einträge; 2026-04-01 bis 2026-07-31 79 Einträge. Zusammen 128 eindeutige IDs, gültige Datumswerte, keine Fotoverknüpfung und keine manuelle billed-Markierung. Neuere sichtbare Einträge: 46, im Arbeitsspeicher für vollständigen Vorher-/Nachher-Vergleich festgehalten; keine Betriebsinhalte in Dateien/Logs gespeichert. Rechnungs-/Versionsschutz wird vor jeder Änderung durch denselben nativen Owner-Pfad geprüft.
- Neue private App-Geschäftssicherung erstellt und inspect_backup bestätigt: backup_7fd0a667d4708f0678e3f3c19e49c9feca3fcbea36b7e9c306cb2dea93b3ea28, 22.711.249 Bytes, SHA256 776685e56ded05e41ae9363dfb9861dffbe83a34862845a57dbd62958c89e19e, mit Snapshot/Pending/lokalen Originalen und Dokumenten, ohne Zugangsdaten. Ausdrücklich keine vollständige Gerätesicherung.
- Ausgangssync 51 (43 Übertragungen /8 Prüfungen), Historie 1, gespeicherter Geschäftshash 0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b. LEGACY_SOURCE_CHANGED weiter offen; keine Drive-Datei/alte Aktion überschreiben, keine Cloud-Bestätigung behaupten.
- AGENTS.md ergänzt: aktuelles Design nicht permanent; eigenständige praktische Design-/Navigationsverbesserungen erwünscht. Neue operative Arbeitsaufzeichnungen künftig über bestehende App; keine selbstständige Anlage per MCP/Import. Bestehende Funktionen und Tests erhalten.
- Nächster Schritt: Datensatzhashes lesen, dann versionsgeschützte Einzelarchivierungen mit stabilen Request-IDs. Ausnahmen nicht übergehen; anschließende vollständige Prüfung des erhaltenen Bestands.


### Eintrag 17 – Stichtagsbereinigung / Phase B/C: tatsächlich ausgeführt und rückgelesen

- Auf ausdrücklichen Nutzerauftrag **128 aktive Arbeitsaufzeichnungen mit work_date <= 2026-07-31** per bestehendem Pixel-MCP in den wiederherstellbaren Papierkorb verschoben. Keine endgültige Löschung, kein Datei-Purge, keine Änderung an Rechnungen, Fotos, Kunden oder Aufträgen und keine neue Arbeitsaufzeichnung erzeugt. Zeitraum bezieht sich auf das Arbeitsdatum, nicht auf Import-/Erstellungszeit.
- Vollständige Auswahl 49 +79 eindeutige Datensätze aus zwei nicht abgeschnittenen Lesefenstern. Neun native entity_hash-Werte mit dem unveränderten nativen Worklog-Guard/Kanonisierungsweg abgeglichen; übrige Vergleichshashes ausschließlich im Arbeitsspeicher aus dem gelesenen Stand berechnet. **Jede einzelne Änderung vom nativen Transaktions-/Hash-/Referenzschutz geprüft**, stabile request_id pro ID/Stand, begrenzte Wiederholung bei Netzfehlern; keine blinde Hashaktualisierung bei geänderten Fachfeldern. Keine Betriebsinhalte in Shell, Quellcode, Fixtures oder Repository geschrieben.
- Ergebnis: **128 Speicherbestätigungen /0 Fehler**. Aktive Liste bis einschließlich 31.07.2026 nach vollständigem erneuten MCP-Abruf **0**. Aktive Liste ab 01.08.2026 weiterhin **46**, sämtliche zurückgelieferten Datensatzfelder exakt gleich zum Ausgang; SHA256 der kanonisch sortierten Vergleichsliste vorher/nachher **8d2383e0f290fc814b700693dba4577bfde2779c125364d9c56db901a87b4007**. Keine fremden oder neueren IDs entfernt.
- Papierkorb anschließend vollständig über zwei Seiten gelesen: **134 =6 vorherige +128 neue Einträge**. Für alle 128 IDs Arbeitsdatum und sämtliche Worklog-Guard-Felder gegen die Ausgangslesung geprüft, 0 Abweichungen; die sechs vorherigen Archive vollständig unverändert. Alle entfernten Arbeitsaufzeichnungen bleiben mit Original-ID und ursprünglichen Fachwerten wiederherstellbar.
- Ebenfalls exakt gleich zum Ausgang: **95 Rechnungen** und **26 aktive Fotometadatensätze**, nicht nur die Anzahlen. Keine Fotoverknüpfung unter den 128 entfernten Arbeiten. Keine Rechnung finalisiert, bezahlt markiert oder verändert; persönliche b.b.-Markierungen und Timer nicht bearbeitet.
- Neue Geschäftssicherung aus Eintrag 16 vor jeder Archivierung bereits erstellt und per inspect_backup zurückgelesen. Keine vollständige Gerätesicherung behauptet; keine Wiederherstellung ausgeführt.
- Alle **51 vorherigen Queue-Einträge** nach erneutem MCP-Abruf vollständig unverändert (Inhalt und technische Zustände), keine alte Aktion umgeschrieben oder entfernt.
- Synchronisation danach **179 =51 bestehende Vorgänge +128 neue Archivierungen**, davon **171 Übertragungen /8 bestehende Prüfungen**, Konflikthistorie **1**. Quelle bleibt der lokal gespeicherte Pixel-Stand mit ausstehenden Journalaktionen; Rohsnapshot-Geschäftshash weiterhin 0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b. Rohsnapshot wird erst nach geprüftem Drive-Abgleich fortgeschrieben; das ist vom tatsächlich geprüften effektiven App-Bestand getrennt.
- **cloud_confirmed=false**, confirmation_scope=provider_readback. Bestehender Parallelstandfehler weiterhin offen. Kein Sync-Erfolg erfunden, keine Drive-Datei überschrieben, keine alten Löschaufträge angewendet oder Konflikte geschlossen. Die autorisierten Archivierungen sind lokal wirksam, ihr Drive-Abschluss bleibt ausstehend.
- AGENTS.md ergänzt: Design ist veränderbar und darf selbstständig sinnvoll optimiert werden; alle Funktionen, Datenintegrität und Entwürfe schützen. Neue operative Arbeitsaufzeichnungen künftig über bestehende App; keine eigenständige Anlage durch MCP/Import ohne späteren abweichenden Auftrag. Bestehende MCP-Funktionen und synthetische Tests erhalten.
- Validierung: echter MCP-Vorher-/Nachher-Vergleich und Archiv-Rücklesen PASS; git diff --check PASS. Nur Regel-/Statusdateien im Quellrepo geändert; kein neuer App-Build/Signierlauf oder irrelevanter Regressionstestlauf erforderlich. Keine Installation, kein ADB/direkter Gerätezugriff. main und Historie unverändert.

### Eintrag 18 – Gesamttest / Phase A: Bestandsprüfung und Abnahmeplan

- Ausgang **fb8f1635a5facee60073d1773030dc5552fde6b5**, sauber, vorgesehene Branch, Pflichtancestor vorhanden. main a38765d unverändert. Remote-Ref zeigt nach fetch noch 36e5567; lokale neuere operative Historie bleibt erhalten, kein Reset/Import. Regeln, Zentralauftrag/-status, Reparatur-/Signierungs-/Sync-/Owner-Dokumentation und Issue #1 gelesen.
- Nur lesend real geprüft: Pixel 1.49.4/73, Protokoll 5, 87 Tools, Write aktiv, DB4, pixel_drive_v1, Angebote/Katalog bereit. Queue 183 (175 Übertragungen/8 Prüfungen), Historie1, Provider-Freigabe vorhanden. LEGACY_SOURCE_CHANGED schützt weiterhin zwei unterschiedliche Gesamtstände, cloud_confirmed=false. Keine Testaktion am Pixel, keine alten Konflikte gelöst.
- Abnahme dieses Auftrags: vorhandene Python-/JVM-/Browser-Regressionssuiten; echte native SQLite-/MCP-/Medien-/Drive-Provider-Instrumentierung auf isoliertem Cloud-Emulator; operative WebView und Navigation/Entwürfe, schmale Ansichten/große Schrift prüfen. Alle Tests synthetisch; keinerlei produktive Datenübernahme. Pixel-/Google-End-to-End-Nachweise separat ausweisen.
- Verbesserungsziel: mehrere ausgewählte Arbeitsaufzeichnungen in einem geprüften lokalen Vorgang in den wiederherstellbaren Papierkorb verschieben; stabile Request-ID, pro Datensatz Version/Fotobeziehungen und Finanzschutz, atomarer Abbruch bei Änderungen. Keine automatische endgültige Massenlöschung. Aufwand/Fehler vorher/nachher messen; alle bestehenden Funktionen/Tests erhalten.
- Emulator: SDK/ADB vorhanden, aber kein /dev/kvm; Softwareemulation wird mit offiziellen, größen-/prüfsummengeprüften Google-Paketen vorbereitet. Keine Verbindung zum Pixel, keine zweite operative App. Erste konkrete statische Fehlfunktion: Instrumentierungsmanifest verweist noch auf alte .qa-Paketkennung statt auf at.hausgarten.service; vor nativem Test korrigieren.
- Status PASS Bestandsprüfung; Tests/Emulator/Fehlerbehebung in Arbeit. Nach jeder abgeschlossenen Phase diesen zentralen Nachweis aktualisieren.

### Eintrag 19 – Gesamttest / Phase B: Ausgangsregression und reproduzierte Fehler

- PASS: 266 Python-Tests (QA-Schlüsselerzeugung ausgeschlossen), 17 JVM-Suiten /823 Prüfungen. Browser zunächst 24/25; Sync-Detailprüfung einmal FAIL (aufgeklappte Prüfung während Status-Neurendering), isolierte Wiederholung PASS. Keine pauschale Erfolgsaussage für den ersten Lauf.
- Fehlerkorrekturen: Instrumentierungsmanifest auf Originalpaket umgestellt. Bestehender androidTest-Runner darf gezielt mit demselben Originalschlüssel über tools/sign_original_apk.py --instrumentation signiert werden; nur feste .test-Kennung, genau ein Original-Runner/Zielpaket, keine Activities/Services/Provider/Receiver, ausdrücklicher Modus, unveränderter Normalmodus und Cleanup. 41 synthetische Signierungsprüfungen PASS, kein Schlüssel erzeugt.
- Zwei weitere konkrete Befunde: reine Papierkorb-Vorschau legte über related/purged ein leeres Archivobjekt an; diese Lesewege verändern ihre Eingabe jetzt nicht mehr. Sync-Zentrale ersetzte unveränderte Queue-DOM bei Statusänderung und verlor dadurch Fokus/Bedienzustand; gleiche Queue wird jetzt stehen gelassen. Native get_capabilities berechnet dieselbe effektive Geschäftssicht einmal statt mehrfach; Löschvorschau ermittelt Fotobeziehungen nur einmal.
- Neue atomare Mehrfacharchivierung bis 200 ausdrücklich ausgewählte Arbeiten: vorhandene einzelne archive-Journale, keine neue Löschpipeline. Vorschauhash bindet IDs, Datensatz-/Fotovergleich und Finanzschutz; ein geänderter/geschützter Eintrag stoppt den ganzen lokalen Vorgang. Dauerhafte Request-Quittung und SQLite-Transaktion; keine endgültige Massenlöschung. UI: Auswählen → angezeigte/Einzelarbeiten markieren → ein bestätigter Papierkorbvorgang. Auswahl/Fehler bleibt sichtbar, b.b./Menüs während Auswahl verborgen, Funktionen nach Abbruch wieder vorhanden.
- Neue JVM-Auswahl-/CAS-/Fotobeziehungs-/Finanzschutz-/Replay-Regression: 21 Prüfungen PASS, 128 synthetische Journalaktionen in 63 ms vorbereitet (Host-JVM, keine Pixelmessung). Native Transaktionsabbruch-/Neustart-/Retry-Prüfung ergänzt; tatsächliche Ausführung noch ausstehend.
- Offizielle Emulatorpakete SHA1/Größe gegen Google-Manifest geprüft; Cloud-Android30 x86_64 läuft ohne KVM, erster Systemstart dauert erheblich länger. Frühe Installation vor fertigem PackageManager wurde vom Emulator mit StorageManager-Startfehler abgelehnt; kein App-Fehler, Installation erst nach Systembereitschaft wiederholen. Kein Pixel-/Betriebsdatenzugriff.

### Eintrag 20 – Gesamttest / Phase C: echter Android-Lauf und ursächliche Reparaturen

- Cloud-Emulator Android11/API30, x86_64, WebView83, ausschließlich dedizierte AVD GartenpiratCloud / emulator-5580. Originalsignierte operative App 73 frisch installiert; vorhandene Instrumentierung mit demselben Originalzertifikat, keine QA-App und kein neuer Schlüssel. Softwareemulation ohne KVM: langsamer Erststart und SystemUI-ANR; keine Pixel-Leistungswerte daraus ableiten.
- PASS echte Fresh-Install-Prüfung: leerer Geschäftsbestand, keine Zugangsdaten/automatische Cloudverbindung. PASS echtes In-place-Update 73→interner 74: vollständiger synthetischer Snapshot/IDs/Pending/Einstellungen/Tunnelkonfiguration und laufender Timer unverändert; keine Deinstallation/Datenlöschung. Dieser Emulatornachweis ersetzt keine Pixel-Sicherung/-Freigabe/-Updateabnahme.
- Erster vollständiger nativer Funktionslauf: **90 PASS /10 FAIL**. Archiv-/Undo-Tests erwarteten noch CANCELLED statt erhaltenem Archivauftrag plus Restore; Capabilities erwarteten Protokoll4 statt5; Rechnungstest erwartete Abschlussvormerkung ohne bestätigten Aussteller und Sync ohne eingerichteten Zugang; zwei PDF-Negativtests erwarteten IllegalArgumentException statt korrekt zurückgewiesener IOException. Erwartungen an aktuelle fachliche Regeln angepasst, Schutzmechanismen beibehalten. Unvollständige alte Basisfixture um leere Fotosammlung ergänzt.
- Echte UI-Fehlfunktion im Emulator: WebView83 konnte vier ||= Stellen nicht parsen, wodurch Navigation/mehrere Funktionsmodule ausfielen. Syntax auf ES2020 zurückgeführt; alle operativen Skripte mit Acorn auf ES2020 geparst. Kleines zuerst geladenes Kompatibilitätsmodul für Array.at, Object.hasOwn, replaceAll und sichere UUIDv4 mit crypto.getRandomValues; kein unsicherer UUID-Fallback. Test mit entfernten modernen APIs und 1000 sicheren IDs/fehlender Entropie PASS. Native WebView-Neuprüfung vorbereitet.
- Echte Sync-Ursache aus drei nativen Wiederholungs-/Neustarttests: run normalisierte fehlende offers/service_catalog, publish/readPrimary verglichen teils den Rohstand. Konsistente additive Normalisierung jetzt direkt in DrivePrimaryState.recover auf einer Kopie. Exakt nachgewiesener alter Rohhash bleibt kompatibel; abweichende Geschäftsdaten weiterhin LEGACY_SOURCE_CHANGED. Neuer Hosttest 9 Prüfungen PASS; Originaleingabe unverändert. Keine produktive Drive-Datei ersetzt, kein Konflikt als gelöst behauptet.
- Weiterer echter Neustartfehler behoben: Bestätigungs-IDs für fehlende alte Fotoanhänge wurden noch aus veralteten Preferences statt dem maßgeblichen SQLite-Snapshot gelesen. Jetzt snapshotText inklusive Legacy-Fallback; Regression mit echtem commitSnapshot, verlorenem Ack und erneut fehlendem Original ergänzt. Unbestätigtes Original bleibt als fehlend sichtbar und blockiert unabhängige Aktionen nicht.
- Mehrfacharchivierung nativ bereits PASS: künstlicher SQLite-Abbruch mitten im Vorgang hinterlässt null Teilaktionen; gleicher Request danach erfolgreich, Replay und Store-Neustart erhalten 128 Archivaufträge. Ein lokaler Commit 9.270 ms im unbeschleunigten Emulator (keine Pixelmessung). Hostvorbereitung 61 ms. UI mit alten WebView-APIs, 128 synthetischen Arbeiten, Auswahl/Abbruch/CAS/Retry und 320/360/426px/großer Schrift PASS.
- Code vereinfacht: archive_entity/delete_entity teilen einen nativen Archivhandler; keine zweite Löschpipeline, keine Funktion entfernt. Additive MCP-Erweiterung auf 89 Quellwerkzeuge bei weiterhin kompatiblem Protokoll5; installierter Pixel meldete zuletzt87, neue Methoden dort nicht behauptet.
- Reale MCP-Lesetests: 14 sinnvolle Leseaufrufe erfolgreich (Gesundheit, Timer, Einstellungen, Business Settings, Angebote, Vorlagen, Pflanzen, Konflikte, Archiv, Kunden mit Suchbegriff, Aufträge, Arbeiten, Rechnungen, Fotos). Leere Kundensuche als ungültige Pflichtangabe zurückgewiesen, danach gültige Suche PASS. Keine produktiven Schreibtests, keine alten Prüfungen geschlossen.
- Aktueller Stand: 267 Python-Tests PASS, 26 Browser-Suiten PASS; zusätzliche Kompatibilitätssuite PASS. Zweiter vollständiger nativer Lauf und finale Artefaktprüfung folgen. P0 Live-Drive-Abnahme und echte Pixel-/Kamera-/Kontofreigaben bleiben offen.


### Eintrag 21 – Gesamttest / Phase D: Qualifikation

- PASS **270 Python-Tests**, **20 JVM-Suiten /864 Prüfungen**, **100 native Android-SQLite/MCP/Provider-Prüfungen /0 Fehler** im isolierten Cloud-Emulator. Frühere native Läufe 90/10 und99/1 bleiben oben nachvollziehbar; nach Ursachen-/Erwartungskorrektur kein fehlgeschlagener Prüffall im abgeschlossenen nativen Schlusslauf.
- PASS tatsächliche native WebView83: Start aller Module, sichere API-Fallbacks, Navigation, Arbeits-Mehrfachauswahl/Abbruch, Kunden-Gartenansicht und Papierkorb. Erstes WebView-Fail deckte zusätzlich fehlendes replaceChildren auf; entsprechender kleiner Element-/Fragment-Fallback plus Browserregression mit entfernten APIs ergänzt. Synthetische UID/Datensätze, keine Pixel- oder Google-Kontotests.
- Rechnungsentwurf bei fehlendem oder leerem zentralen Steuermodus repariert: bleibt ausdrücklich unconfigured/editierbar, kein Steuermodus erfunden. Endgültige Ausstellung weiter blockiert; sechs neue Hostprüfungen plus native Entwurfs-/Abschluss-Trennung PASS. Weitere alte native Assertions an aktuellen Bestätigungs-/Queue-Ablauf angepasst; bestätigte Create-Aktion nicht mehr als Pending gezählt.
- Syntax-/API-Fallback und Status-DOM, gemeinsame Archivhandler und native Snapshot-Belegquelle geprüft. Auswahl ignoriert reine Transport-/Anzeigemetadaten; echte Feld-/Version-/Fotobeziehungsänderungen bleiben geschützt. Mehrfacharchivierung bis200, keine automatische endgültige Sammellöschung. Lokaler Abbruch/Replay/Neustart real geprüft: 128 Aktionen, letzter nativer Commit6.806ms im Softwareemulator; keine Pixel-Leistungsbehauptung.
- Sichtprüfung: Browser320/360/426px plus150%-Schrift und echte Android-WebView. Native Aufnahme zeigte schwebenden Gartenpirat über Stop; anschließend aktiven/pausierten/noch abzuschließenden Timer von Überlagerung befreit. Gartenpirat/alle Pflanzenfunktionen über Mehr weiter erreichbar, beweglicher Knopf im Ruhezustand erhalten. Neun gezielte Browserprüfungen einschließlich altem WebView-API-Profil, Transportwechsel, Stop-Zugriff und Pflanzenmenü PASS; erneute native Bedienungs-/Sichtabnahme dieses letzten UI-Schritts PASS; Stop frei und Pflanzenmenü erreichbar.
- UI-Schlussänderung ändert keine native App-Bytecode-Datei: classes.dex SHA256 cce57dfe7ae9d57da6de664ef8b6eb0e038e80b3b34d1e94dd70c0cdd5860bb6 ist bytegleich zum 100/0 geprüften nativen Stand. Native Instrumentierung nur erweitert; keine Geschäftslogik nach erfolgreichem Lauf geändert.
- Live-Abschluss ausschließlich lesend: Pixel73/Protokoll5/87Tools/Write aktiv. Queue183 (175 Übertragungen/8 Prüfungen) samt jedem Eintrag exakt gleich zur Bestandsprüfung; gespeicherter Geschäftshash unverändert0934de6f74b8280781fd12ed018de00a529b6388e94397ca716014768d4fa52b. cloud_confirmed=false, provider_readback, LEGACY_SOURCE_CHANGED weiter gemeldet. Die Quellkorrektur ist dort nicht installiert; keine Aussage über erfolgreiche Übernahme in echtes Drive, keine Konfliktentscheidung ausgeführt.

| Funktionsbereich | Tatsächliche Prüfung | Reale Grenze |
|---|---|---|
| Kunden/Garten/Aufträge | native Create/Update/Dubletten/Hash/Offline-Beziehungen, Browserformulare; Pixel-MCP lesend | kein produktives Testschreiben |
| Arbeitsaufzeichnungen/b.b. | native Speicherung/Version/Finanzschutz/Archiv/Restore; Browserfilter/Jahreswechsel/3000 Datensätze; Mehrfachauswahl | neue operative Arbeiten weiterhin nur über App |
| Timer/Pausen | native Start/Pause/Fortsetzen/Stop/Abschluss/Replay/Neustart; Browserbedienung | echtes Pixel-Hintergrund-/Akkuverhalten offen |
| Fotos/Dateien | native Chunk-Upload, Hash, Original, Thumbnail, Rotation/Optimierung,21 Fotos, Zuordnung/Neustart; Browser105 Metadaten/Pagination | keine100 Originaldateien/physische Kamera oder reale Gerätemedienabnahme |
| Rechnungen | native Entwurf/Änderung/PDF/Korrektur/Zahlung/Share-Handoff/Abschlusssperre; JVM Nummern-CAS/Transaktion | keine echte Rechnung ausgestellt/versendet/bezahlt; Nummernservice im echten Drive nicht abgenommen |
| Angebote/Leistungskatalog | native Create/Replay/Validierung/Finalisieren/PDF getrennt von Rechnung; JVM vier Arten/Katalog/Kontext; Browser Auftragübernahme | keine produktiven Angebote geändert |
| Pflanzen/Neophyten/Bilder |181 Profile/362 Bilder, kanonische Spiegel; Browser Suche/Wischen/Offline/Piratenknopf | keine vollständige botanische Neuprüfung |
| Sync/Konflikte/Medien | echter Android-DocumentsProvider mit synthetischem Speicher,7Wiederholungen,Abbruch/Readback/Originale/Folgestand | kein echter Google-Drive-Serverbeleg;183Produktivaktionen erhalten |
| Sicherung/Restore/Migration | native Geschäftssicherung/Inspektion/identischer Restore/Stale-Abweisung, SQLite-/Altbildmigration | keine Pixel-Gerätesicherung/Wiederherstellung |
| MCP/Rechte/Tunnel | native Dispatch/Schreibpfade, JVM Schema/entzogene Rechte/Dateiblöcke,14reale Leseaufrufe | zwei neue Quelltools erst nach Update/Connector-Refresh; externe Kontorechte separat |
| Oberfläche |27 Browser-Suiten, native WebView-Start/Navigationsprüfung; Timer-Overlay-Abnahme PASS | Softwareemulator ohneKVM ist keine Pixel-Leistungsmessung |


### Eintrag 22 – Gesamttest / Phase E: geprüftes Artefakt und Übergabe (19.10.2026)

- Cloud-Qualifikation abgeschlossen: 270 Python-Tests,20 JVM-Suiten/864 Prüfungen,27 Browser-Suiten und100 native Android-Prüfungen PASS. Zusätzlicher nativer WebView83-UI-Lauf inklusive Mehrfachauswahl, aktivem Timer ohne verdeckten Stop, Pflanzenzugang über Mehr, Garten und Papierkorb PASS. Letzte Timer-UI-Änderung danach vollständig im Browser und auf echter Emulator-WebView nachgeprüft; nativer Bytecode identisch zum100/0-Lauf. Screenshots mit synthetischen Daten visuell geprüft. Kein Pixel-Leistungs-/Google-End-to-End-Nachweis.
- Release-Build und AndroidTest-Build PASS; Release-Lint0 Fehler/17 Warnungen. Kanonischer Pflanzencheck181 Profile/362 Bilder/370 Spiegel PASS; notwendige gartenpirat.js/sw.js-Spiegel ausschließlich über tools/sync_plant_assets.py --write erzeugt. Kein Desktopstand übernommen, kein Feature/Regressionstest gelöscht.
- Originalkandidat downloads/Haus-Garten-1.49.5-74-original-update-kandidat.apk: **at.hausgarten.service,1.49.5/74**, min26/target37, nicht debuggable,34.422.544 Bytes. SHA256 **48cdd6d96f7c5eb91bf64e79f8981823b18453fe7e9fe4f7d567d0dbfea6c527**. Alle406 Assets bytegleich zur Quelle; classes.dex SHA256 cce57dfe7ae9d57da6de664ef8b6eb0e038e80b3b34d1e94dd70c0cdd5860bb6.
- Signierung sämtlicher interner Emulator-/Instrumentierungskandidaten ausschließlich tools/sign_original_apk.py mit fünf nur auf Vorhandensein geprüften Variablen, vorhandenem Originalschlüssel und anschließendem Cleanup. Keine Ersatz-/Debug-/QA-Schlüsselerzeugung/Actions-Signierung. Finale APK unabhängig erneut verifiziert: genau ein Originalsigner **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**, v2/v3 gültig, v1/v3.1/v3.2/v4 false,16KiB-zipalign PASS. Source-/APK-Privacy PASS,null operative Seedzeilen. Cleanupnull private Signiertempordner und null Keystore-/Privatschlüsseldateien nach geprüften Endungen im Repository. Test-APK nur bestehende Instrumentierung für Originalpaket, nicht bereitgestellt und keine zweite operative App.
- Version74 ist höher als tatsächlich gelesener Pixel73; Zertifikat mit erhaltenem Cloud-Kandidaten73 identisch. Tatsächliche Fresh-Install-/73→74-In-place-Update-Abnahme im isolierten Emulator einschließlich synthetischem Datenerhalt PASS. Installierte Pixel-Signatur nicht direkt abgefragt; kein Pixel-Update/Datenerhalt behauptet. Kein direkter Pixel-/ADB-Zugriff, keine produktive Installation oder Datenänderung.
- Neuer privater Drive-Download über vorhandene Google-Drive-Anbindung: https://drive.google.com/file/d/11ASToMZ3qHlpmgq7dkPoKl7-A28klzm-/view?usp=drivesdk . Upload, Metadaten und tatsächlicher Rohdatei-Referenz-Rückabruf mit34.422.544 Bytes PASS. Nur neue APK hochgeladen, keine Betriebsdatei/Freigabe verändert. Lokaler SHA256 geprüft, kein unabhängiger Remote-Bytehash behauptet.
- Reale Betriebsdaten und jeder der183 Queue-Einträge exakt unverändert gegenüber Eintrag18. Acht bestehende Prüfungen und die128 Stichtagsarchivierungen nicht erneut angewendet/gelöscht/aufgelöst. Drive meldet auf installiertem73 weiter LEGACY_SOURCE_CHANGED und nur provider_readback. Quellkorrektur behebt nachgewiesene falsche Normalisierungskonflikte; echte Paralleländerungen bleiben geschützt und dürfen nicht automatisch ausgewählt werden.
- **Offen/P0-P1:** neuer Pixelstand/Connector-Refresh auf 89 Tools und echter Google-Drive-Rundlauf mit diesen 183 Aktionen; notwendige Geräte-/Kontofreigaben, physische Kamera/Dateiauswahl/Share-App, Hintergrund-/Akkuverhalten und Pixel-Update-/Datenerhaltabnahme. Installation am Pixel weiterhin erst nach vollständiger Sicherung und ausdrücklicher Freigabe. Kein Vollzugriff-/Dauerverfügbarkeitsversprechen, keine produktive Alt-Konfliktlösung. Die Cloud-Umsetzung ist geprüft, der Gesamt-P0-Auftrag nicht als produktiv abgenommen markieren.

- Implementierungs-/Qualifikationscommit: **55e16b3a9c20240c67e05bcdce8c7498f67baf23**. Folgender Bereitstellungscommit ergänzt ausschließlich geprüfte APK/Prüfsumme, zentrale Downloadangaben und diesen Abschlussverweis. main unverändert; kein History-Rewrite/Force-Push.

- GitHub-Bereitstellung: Code/Qualifikation55e16b3 und Downloadstatus476a056 normal auf vorgesehene Branch gepusht. Allgemeiner *.apk-Schutz schloss zunächst die neue Datei aus; ausschließlich dieser geprüfte Kandidat erhält eine explizite Ignore-Ausnahme und einen separaten normalen Artefaktcommit. Keine Veröffentlichung ungeprüfter Builds.

### Eintrag 23 – Bereinigung / Phase A: Bestand und Sicherung (19.10.2026)

- Nutzerauftrag: App und Google Drive bereinigen. Ausgang **32dddee50bb0f7c521d29141a05b0ccbe80c560d**, sauber auf der vorgegebenen Cloud-Branch; Remote-Branch per ls-remote identisch, main unverändert a38765d. Pflichtancestor vorhanden. Keine Installation, kein direkter Pixel-/ADB-Zugriff, keine Code-/APK-Änderung.
- Jetzt tatsächlich installiert: **at.hausgarten.service,1.49.5/74,DB4,Protokoll5**. get_capabilities liefert89 Werkzeuge; hier tatsächlich aufrufbarer Connector weiterhin83. Es fehlen preview_delete/delete_entity,preview_purge/purge_entity sowie preview_archive_work_records/archive_work_records; archive_entity-Schema ebenfalls ohne expected_photo_links_hash. Keine Umgehung durch zusätzliche, nicht registrierte Parameter. App-/Tunnel-Katalog und ChatGPT-Registrierung sind weiterhin getrennt zu aktualisieren.
- Ausgang184 Aktionen =176 Übertragungen/8 Prüfungen; Papierkorb134; ursprüngliche sieben Legacy-Hashes unverändert. Android-Provider mit persistierter Ordnerfreigabe, provider_readback/cloud_confirmed=false, letzter alter Fehler LEGACY_SOURCE_CHANGED. Rohsammlungen weiterhin alter gespeicherter Stand; für Bereinigungsentscheidungen zusätzlich effektive Datensätze und Verknüpfungen gelesen.
- Vor erster App-Archivanforderung neue Geschäftssicherung **backup_3fa4460d4db94ee5f9b0e5fd8f124e7d539bfc997f99948c0f9acafbe5f685b5**,22.987.905 Bytes,SHA256 **58e99ac2e57d4215106391daea6d4ebe25a8c4a96998e73050b6f8aec15f6045**; inspect_backup bestätigt. Nach regulärer Übernahme zusätzliche Sicherung **backup_275dabc15814269805c1f1fee46aeb6c79287aa14a323cebbd244b7287b4de85**,22.796.693 Bytes,SHA256 **3c6e59ef604c25919f0f10cfdf223eacb72c9555899c9b8c2e966e1d78ebfc01**. Scope Geschäftssnapshot/offene Aktionen/lokal vorhandene Medien und Dokumente, keine vollständigen Gerätesicherungen. Vorhandene Sicherungen erhalten.

### Eintrag 24 – Bereinigung / Phase B: ausgeführt und rückgelesen (19.10.2026)

- **Drive geordnet:** vier frühere Original-APK-Kandidaten69/71/72/73 aus My Drive in vorhandenen Ordner Haus & Garten App → APK-Backups → Codex-Cloud-Builds verschoben. Ausschließlich überprüfte Ausgangseltern entfernt; Ziel vorab gelesen. Jede Datei anschließend einzeln per ID/Name/Größe/Eltern rückgelesen. Zusammen137.619.731 Bytes weiterhin vorhanden; keine Speicherfreigabe behauptet. IDs und bisherige Download-Links bleiben erhalten, keine Inhalts-/Freigabeänderung. Aktueller Kandidat74 separat per Metadaten unverändert bestätigt. Keine Betriebs-JSONs, Commit-/State-Ketten, Originalfotos, Dokumente oder Sicherungen aus Drive entfernt. Drive-Suche zeigte nicht alle bekannten APK-/JSON-Dateien; Suchleere ausdrücklich kein Nachweis eines leeren Ordners oder verwaister Dateien.
- **Realer Pixel-/Drive-Lauf:** einmalige explizite request_sync-Anforderung. Installierter74 verarbeitete die vorhandenen176 Übertragungen im regulären geschützten Journal-/Medienweg; alter LEGACY_SOURCE_CHANGED verschwand. Zwischenstände185→20→16→13 wurden tatsächlich gelesen. Keine Queue gelöscht und kein Beleg erfunden; Medien liefen in weiteren automatischen Abschnitten weiter. Nach technischer Konfliktnachbereitung und abschließender Fotoarchivierung: **Upload0,Prüfung7,gesamt7**,phase idle,kein Transportfehler/Retry/Medien-Cleanup offen. Letzter gelesener Provider-Abgleich **2026-10-19T04:35:04.039474Z**; **provider_readback,cloud_confirmed=false,last_full_sync leer**. Kein unabhängiger Google-Serverbeleg behauptet.
- **Testreste:** sechs fehlgeschlagene Archivaufträge ausschließlich für dokumentierte synthetische IDs über resolve_sync_conflict mit jeweils frisch gelesenen expected_action_hash/expected_current_hash und keep_current zurückgenommen. Originalaktionen und Entscheidungen erhalten; überholte Kontrollaktionen vom vorhandenen Prozessor als superseded quittiert. Konflikthistorie zuletzt12 Einträge einschließlich solcher Kontrollbelege; das sind nicht12 historische Geschäftskonflikte. Sieben geschützte Alt-Fälle unverändert.
- Erste neue Testfoto-Archivanfrage wurde hinter einem alten fehlgeschlagenen Archivvorgänger mit DEPENDENCY_BLOCKED angehalten; sie war zunächst nur lokal vorgemerkt, nicht erfolgreich archiviert. Diese Anforderung ebenfalls hashgebunden zurückgenommen. Nach Vorgängerprüfung erneute aktuelle Anfrage erfolgreich verarbeitet. Dokumentierte synthetische Arbeit **id_sha256_9f5265b31b9a97be269bf5faf93bfe87adb4f7d01d62bf5c0023f92fee03c92f** und Foto **id_sha256_8cbd663a374da3be09cd94c037a3025e77341079fc33f85d08a54ba4e9933abc** jetzt im bestehenden Papierkorb tatsächlich rückgelesen; Originale erhalten, keine physische Löschung. Papierkorb **136**. Alle vorherigen134 Archiv-IDs und die128 Stichtags-Arbeitsarchive unverändert erhalten.
- Zwei verbleibende technische Testaufträge haben noch aktive oder bereits archivierte Fotobeziehungen. Ein aktueller Archivversuch für id_sha256_c6ccbf58d78cbaaeeec27aa7d60d0d30639ddee2faeaba44a74c4fefe8224705 wurde vor Mutation korrekt mit **PREVIEW_REQUIRED** blockiert, obwohl aktive Foto-/Arbeitslisten leer waren: archivierte Fotozuordnungen zählen ebenfalls. Ohne exponierte preview_delete/expected_photo_links_hash keine Umgehung, kein Löschen von Beziehungen und kein weiterer blind wiederholter Auftrag.
- **Integritätsnachweis:** alle45 nicht synthetischen aktiven Arbeitsaufzeichnungen ab01.08.2026 feldweise mit kanonischer Schlüsselreihenfolge unverändert gegenüber der Eingangsliste; ausschließlich die eindeutig dokumentierte zusätzliche synthetische Testarbeit wurde archiviert. Rechnungssammlung einschließlich aller95 Datensätze hat unveränderten Sammlungshash. Ursprüngliche sieben client_ids/action_hashes/current_hashes weiterhin exakt gleich; Legacy-Vorschau7,keep_current,no_business_data_changed,Hash68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7. Gespeicherte Sammlungen jetzt52 Kunden/3 Aufträge/45 Arbeiten/95 Rechnungen/25 Fotos/33 Bausteine/0 Angebote; der Unterschied zum alten Rohsnapshot folgt der regulären Übernahme bestehender Aktionen und den genannten technischen Bereinigungen, nicht einer pauschalen Datenlöschung.
- **Legacy-Abschluss weiterhin gesperrt:** prepare_legacy_conflict_backup tatsächlich versucht, ohne Änderung abgewiesen: „Zuerst direkten Pixel-/Drive-Abgleich mit Serverbeleg prüfen. Keine Altentscheidung ausgeführt.“ Der vorhandene Sicherungs-/Abschlussweg verlangt einen direkten Drive-Serverbeleg; Provider-Rücklesen genügt nicht. Keine sieben Altentscheidungen erzeugt. Nächste notwendige Gerätehandlung: Sync-Zentrale → Drive-Verbindung einrichten, vorhandenes Google-Konto und denselben Ordner bewusst bestätigen; tatsächliche OAuth-/Projekt-/Kontorechte und Serverbeleg anschließend prüfen. Danach erst frische vollständige Backup-/Hash-Vorschau und die vom Nutzer verlangte einmalige ausdrückliche Bestätigung. Keine Geheimnisse anfordern, kein Rechte-Bypass.
- **Weiter offen:** denselben ChatGPT-MCP-Zugang neu initialisieren/Toolkatalog aktualisieren bzw. neu verbinden;89 tatsächlich aufrufbare Werkzeuge und aktuelle Archivschemata nachweisen. Zwei technische Testaufträge erst mit korrekter Fotovergleichsvorschau archivieren. Endgültige Papierkorbleerung/physische Medienlöschung nicht durchgeführt. Keine neuen Kosten/Freigaben, kein neuer Build, keine Installation. Diese Phase prüft echte autorisierte Bereinigung per MCP und Drive-Metadatenrücklesen; keine neuen automatisierten Tests erforderlich, da ausschließlich diese zentrale Statusdatei und bestehende operative Bereinigungswege verwendet wurden. Vorherige automatisierte Qualifikation bleibt unverändert; keine Vollabnahme des restlichen Funktionsumfangs behauptet.

### Eintrag 25 – sieben Altprüfungen ausdrücklich freigegeben, technisch blockiert (19.10.2026)

- Nutzer: „Entferne die 7 bestehenden synceinträge“. **Ausdrückliche Freigabe für die bereits konkret bezeichneten sieben historischen Fälle liegt jetzt vor**: keep_current, aktueller Geschäftsdatenstand bleibt erhalten, keine ursprüngliche Löschaktion anwenden; Originalaktionen und Entscheidungshistorie erhalten. Keine erneute Nutzerbestätigung für genau diese unveränderten sieben Fälle erforderlich. Technische Sicherungs-/Hashprüfungen bleiben Voraussetzung.
- Ausgang7c653e97f46fa411c0a6917dd541e1c0cbf3f7c2 auf vorgegebener Branch sauber; Remote identisch, main a38765d unverändert, Pflichtancestor vorhanden. Live74/Protokoll5/Write aktiv. Erneute Vorschau exakt7,keep_current,no_business_data_changed,Hash68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7; sämtliche sieben Aktions-/Datensatzhashes entsprechen Eintrag24.
- Vor Abschluss **prepare_legacy_conflict_backup** mit stabiler Request-ID close7_20261019_owner_authorized_backup_v1 tatsächlich erneut ausgeführt. App weist vor Änderung zurück: „Zuerst direkten Pixel-/Drive-Abgleich mit Serverbeleg prüfen. Keine Altentscheidung ausgeführt.“ Laufzeit weiterhin android_provider/provider_readback/cloud_confirmed=false. LegacyConflictBackup.requireReady und derselbe Guard im Sammelabschluss verlangen direkte Drive-Konfiguration samt drive_server_manifest. Keine Umgehung über sieben Einzelentscheidungen, keine erfundene Backup-ID/Cloud-Bestätigung, kein Entfernen der Queue.
- Nachprüfung: weiterhin **0 Uploads/7 Prüfungen**,phase idle,kein Transportfehler. Geschäftssammlungen hashgleich **0535a37257a7f47758f5f3bf0afba0a691d70de237f03d0847c9922dd54dc6a9**. Keine Geschäfts-, Archiv-, Medien- oder Konflikthistorienänderung durch diesen Auftrag. Kein Build/Signieren/Installieren und keine Quellcodeänderung; ausschließlich dieser zentrale Status ergänzt.
- Nächste notwendige Gerätehandlung: Sync-Zentrale → **Drive-Verbindung einrichten**, vorhandenes Google-Konto und denselben Ordner bestätigen; direkte Rechte/OAuth-Einrichtung und tatsächlichen Serverbeleg prüfen. Anschließend frische vollständige Legacy-Sicherung und unveränderte Hashvorschau, dann bereits autorisierten keep_current-Abschluss ausführen. Falls sich Auswahl/Aktions- oder Datensatzhash ändern: neu prüfen statt Freigabe auf andere Inhalte zu übertragen. Der aktuelle Auftrag ist wegen dieser technischen Sperre **nicht ausgeführt**, keine Erfolgsmeldung über entfernte Einträge.

### Eintrag 26 – Altfall-/Papierkorb-/Menüreparatur, Phase A (19.10.2026)

- Ausgang8b27060516aeb3f947706e2d693b94f08b7500e4, sauber auf vorgegebener Branch; Remote identisch/main a38765d unverändert/Pflichtancestor vorhanden. Nutzer autorisiert Altfallabschluss und endgültige Papierkorbbereinigung sowie deren Bedienung; Freigabe aus Eintrag25 bleibt gültig. Keine erneute Rückfrage für die Implementierung nötig. Keine automatische Pixel-Installation.
- Live ausschließlich erneut gelesen:74/Protokoll5/89 Runtime-Werkzeuge,0 Uploads/7 historische Prüfungen,136 Papierkorbeinträge. Geschäftshash0535a37257a7f47758f5f3bf0afba0a691d70de237f03d0847c9922dd54dc6a9 unverändert; Android-Provider-Rücklesen,kein Transportfehler,keine Cloud-Serverbestätigung. Keine Geschäftsdaten für Tests verwenden.
- Ursachen: Legacy-Sicherung verlangt pauschal REST/Servermanifest vor ihrer bereits vorhandenen inhaltlichen Snapshot-/Commit-Prüfung; funktionierender SAF-Zugang wird ausgeschlossen. Papierkorb bietet nur Einzel-Purge und kann interne Archivbeziehungen nicht als gemeinsame Löschmenge behandeln. Mehr-Menü wächst durch unabhängig eingefügte Links ohne gemeinsame Gruppierung.
- Abnahme: Altfallabschluss auch nach vollständig überprüfter Provider-Sicherung möglich, ohne cloud_confirmed zu erfinden; aktuelle Daten und unveränderliche Belege behalten. Papierkorb leeren mit einer vollständigen Hashvorschau, einer Bestätigung, dauerhafter Request-ID, atomarer lokaler und Drive-Verarbeitung; interne Archivbeziehungen blockieren sich nicht, aktive/finanzielle Referenzen bleiben geschützt. Alle Menüzugänge bleiben vorhanden, gleiche Aktionen gebündelt und320/360/426px/große Schrift geprüft. Isolierte Host-/Browser-/native Regressionen vor Originalkandidat75; tatsächliche Live-Ausführung erst über angebotene installierte Funktionen.

### Eintrag 27 – Altfall-/Papierkorb-/Menüreparatur, Phase B: Implementierung

- LegacyBackupPolicy trennt tatsächliche Ordnerrechte/abgeschlossenen Abgleich von Transportwahl. Vollständige Sicherung liest und verifiziert aktuelle State-/Commit-Kette auch über SAF, liest dieselbe Veröffentlichung nach Medienprüfung erneut und hält bei Änderungen an. Fehlende aktive oder archivierte Originale/PDFs werden ausschließlich hashgeprüft aus demselben Drive-Ordner nachgelesen; fehlende Dateien stoppen weiterhin. Snapshot, SQLite, Queue und lokale Dateien werden privat gesichert. confirmation_scope bleibt provider_readback oder drive_server_manifest, cloud_confirmed wird nicht erfunden. Fachlich keine alte Löschung; keep_current und Historie bleiben bestehen. UI verwendet stabile Backup-/Abschluss-Request-IDs je Vorschau statt erneuter Zufallsanfragen bei Fehlern.
- TrashPurgeBatch: vollständige Archivmenge plus aktive Datensatzversionen bilden preview_hash. Gemeinsamer Fixpunkt schützt aktive Referenzen/finale Belege und deren abhängige Archive, erlaubt aber Beziehungen/Zyklen innerhalb der ausdrücklich bestätigten Löschmenge. preview_empty_trash/empty_trash, eine atomare purge_trash_v1-Journalaktion, eindeutige Tombstones je Original, dauerhafte Request-Belege; bei Stale Hash keinerlei Teiländerung. Gescheiterte Sammelaktion hat hashgebundenen keep_current-Ausgang. Nur erfolgreich projizierte Vorgänger werden als Abhängigkeit gebunden; unabhängige sieben Altprüfungen blockieren die Papierkorbbereinigung nicht.
- Originaldateien werden erst nach überprüfter Veröffentlichung/lokalem Commit und erneuter Referenzprüfung bereinigt. Vorhandener Foto-Cleanup ergänzt um unreferenzierte Entwurfs-PDFs, jeweils Hashprüfung/Provider-Löschrücklesen bzw. REST-ETag. Gemeinsam verwendete Dateien, Original-Sicherungen und unveränderliche Journal-/State-Historie bleiben erhalten. Keine Zusage forensischer Datenvernichtung.
- Mehr: vorhandene Zugänge in vier sichtbaren Gruppen Arbeit & Planung, Angebote & Rechnungen, Fotos & Pflanzen, Daten & App. Bestehende Handler/Formulare bleiben erhalten, kein neuer Navigationszwischenraum. Papierkorb dort zentral, doppelter Link auf Sync-Seite entfernt. Mehr-Markierung auch in Papierkorb/Fotobibliothek/Abrechnung erhalten. Papierkorb leeren prüft alle Seiten und nennt geschützte Restfälle vor einer einzigen Bestätigung.
- Neuer Quellstand **1.49.6/75,Protokoll6,91 Werkzeuge**, neue trash_empty_v1-Capability; alte Handler bleiben kompatibel, ältere Verarbeiter dürfen neue Sammelaktion nicht ausführen. 270 Python-Tests,21 JVM-Suiten einschließlich163 neuer Sammellösch-/Providerpolicy-Prüfungen sowie gezielte Browser-Suite zunächst PASS. Release-/Instrumentierungsbuild und Lint zunächst PASS; zusätzliche native Provider-/SQLite-Abnahme und gesamter Browserlauf laufen noch. Keine produktive Queue-/Papierkorbänderung durch diese Entwicklungsphase und keine Pixel-Installation.

### Eintrag 28 – Altfall-/Papierkorb-/Menüreparatur, Phase C: Qualifikation und Kandidat

- PASS **273 Python-Tests**,**21 JVM-Suiten** (davon163 neue Sammellösch-/Providerpolicy-Prüfungen),**27 Browser-Suiten**,zusätzlicher gezielter Browserlauf21 Prüfungen. 136 synthetische Papierkorbeinträge statt nur erster30er-Seite, Abbruch/Fehler/identische Retry-ID, keine aktive Datenänderung; interner Kundenzyklus und aktive Rechnungsreferenzen geprüft. Mehr-Menü320/360/426px,150%-Schrift: alle Zugänge/Handler erhalten, lesbare Textbreiten und Zeilenhöhen sowie Überlauf geprüft; Screenshot visuell kontrolliert.
- PASS **103 native Android-SQLite/MCP/Provider-Prüfungen,0 Fehler**, vollständiger Schlussbericht vorhanden. Ein einziger Journalauftrag für136 Einträge; injizierter SQLite-Abbruch lässt null Teilaktionen/Quittungen, Replay/Neustart erhalten Resultat und aktive Arbeit. Provider-PDF-Test entfernt ausschließlich unreferenzierten Entwurfsinhalt nach Veröffentlichung und lokalem Commit; gemeinsam mit aktivem Beleg verwendete Datei bleibt lokal/remote erhalten. Wiederholung führt keine doppelte Dateilöschung aus.
- Sieben synthetische Altfälle über sieben normale Wiederholungen geschützt; anschließend vollständige private Provider-Sicherung mit geprüfter State-/Commit-Kette, fehlendes lokales Original tatsächlich hashgeprüft nachgeladen, sieben keep_current-Entscheidungen durch denselben nativen Owner-/Journalweg verarbeitet. Aktive Geschäftshashes unverändert,0 Restaktionen,7 Historieneinträge; Originaljournale/Belege erhalten,cloud_confirmed=false. Dies ist ein isolierter Android-DocumentsProvider, kein echter Google-/Pixel-Abschluss. Tatsächlicher Bulk-Backup-/Entscheidungsaufruf auf produktivem74 weiterhin nicht als erfolgreich ausgegeben.
- Native Original-Update-/Datenerhaltprüfung und tatsächliche WebView83-Navigation PASS; Mehr mit vier Gruppen/einem Papierkorbzugang, vorhandene Pflanzen-/Kunden-/Auswahl-/Timerwege und Papierkorb-leeren-Schaltfläche vorhanden. Erste Updateprüfung erkannte neue additive Laufzeit-Capability trash_empty_v1 im alten Gesamthash; ausschließlich dieses neue Metadatum ist nun explizit vom historischen Baselinevergleich ausgenommen. Alle vorherigen Capabilities, Geschäftsfelder und Pending-Bytes weiter vollständig verglichen. Ein neuer Provider-Test verwendete zunächst einen alten Publikationsvorgänger und wurde korrekt abgewiesen; Fixture liest jetzt wie der echte Sync den aktuellen Primärstand. Keine Schutzregel entfernt.
- Ein Emulatorlauf wurde durch irrtümlich überlappenden UI-Start unterbrochen. Hoststarter konnte zuvor Teil-PASS-Zeilen trotz Process crashed akzeptieren. Behoben: exklusives flock je expliziter Emulator-ID über Installation/Lauf und verpflichtender vollständiger Abschlussbericht; sechs Guardtests einschließlich Parallelstart/Teilbericht/Crash PASS. Unterbrochener Lauf gilt nicht als Erfolg; abschließender vollständiger103/0-Lauf nach der Korrektur tatsächlich ausgeführt. Erstlauf102 PASS/1 Fixturefehler und Unterbrechung bleiben in lokalen synthetischen Logs nachvollziehbar. Keine produktiven Daten verwendet.
- Release-/Instrumentierungsbuild PASS,Lint **0 Fehler/17 Warnungen**,Pflanzenspiegel181 Profile/362 Bilder/370 Ausgaben geprüft. Fertige APK406 Assets bytegleich zur Quelle, geforderte operative Klassen einschließlich LegacyBackupPolicy/TrashPurgeBatch vorhanden, null operative Seedzeilen/Privacy PASS. Keine parallele App/Datenbank und kein Test-/Featureabbau.
- Kandidat **downloads/Haus-Garten-1.49.6-75-original-update-kandidat.apk**,at.hausgarten.service,1.49.6/75,min26/target37,nicht debuggable,**34.426.640 Bytes**. SHA256 **db9035109b9206fbfe0cf621ad0198bd14f0d5baf45e5812553d4396f27f69a0**. DEX-SHA2567958fa5537cea3601eccf01d06bb239f57b9f9cd777412d18bd24707efe6f52c. Version75 über tatsächlich gelesenem Pixel74; Paket/Signatur auch gegen erhaltenen74-Kandidaten verglichen, kein direkter Zugriff auf installierte Pixel-Signatur.
- Vor jeder tatsächlichen Signierung ausschließlich Vorhandensein der fünf HG_ORIGINAL_*-Variablen geprüft; vollständig. Ausschließlich tools/sign_original_apk.py, vorhandener Originalschlüssel; interne Instrumentierung ebenfalls nur mit dokumentiertem --instrumentation-Modus, nicht bereitgestellt. Kein neuer Debug-/QA-/Ersatzschlüssel. Finale unabhängige Prüfung: genau ein Signer **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**,v2/v3 gültig,v1/v3.1/v3.2/v4 false,16-KiB-zipalign PASS. Private Signierungstempordner0,Schlüsseldateien nach geprüften Endungen im Repository0. Originalsignierung und Emulatorupdate sind keine Pixel-Installations-/Datenerhaltfreigabe.
- Privater Drive-Download tatsächlich hochgeladen: **https://drive.google.com/file/d/1Q8Ttp8Bm1xlwIeW3e_AXY2B11a0a6HHX/view?usp=drivesdk**. Vorhandener Ordner APK-Backups/Codex-Cloud-Builds,keine neue Ordner-/Freigabestruktur. ID/Name/MIME/Eltern/34.426.640 Bytes und ausschließlich Owner-Freigabe rückgelesen; Rohdatei-Referenz mit vollständiger Größe erneut erhalten. Keine Betriebs-/Sicherungsdateien ersetzt/gelöscht und kein unabhängiger Remote-Bytehash behauptet.
- **Produktive Schlusslesung:** weiterhin74/Protokoll5/89 Runtime-Tools,hier83 registrierte Tools. **0 Uploads/7 Prüfungen,136 Papierkorbeinträge**,Legacy-Previewhash unverändert68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7,Geschäftshash unverändert0535a37257a7f47758f5f3bf0afba0a691d70de237f03d0847c9922dd54dc6a9. Die produktive Entfernung wurde in dieser Entwicklung **nicht ausgeführt**: alter installierter Backup-Guard und fehlende Purge-/Sammeltools. Keine direkte Pixel-/ADB-Aktion oder Installation. Nach vollständiger Gerätesicherung und ausdrücklicher Updatefreigabe75 verwenden; dann Backup/Hashprüfung und bereits autorisierten unveränderten sieben keep_current-Abschluss ausführen, Papierkorb vollständig vorprüfen/leeren und tatsächliche Drive-Übernahme nachweisen. Neue MCP-Registrierung91/aktuelle Schemata separat refreshen. Keine erneute fachliche Freigabe für dieselben sieben Fälle verlangen. Gesamt-P0 bleibt bis realer Geräte-/Google-Abnahme offen.

### Eintrag 29 – Termin-/Kalender-/Wetterkonzept: abgeschlossene Planungsphase (19.10.2026)

- Auftrag ausdrücklich zuerst planen. Ausgang **8b23ab8151131303e35fa6203a9f771dc94dcc8d**, sauber auf vorgesehener Cloud-Branch, Pflichtancestor vorhanden; Remote-Branch identisch/main a38765d unverändert. Quelle1.49.6/75, letzter belegter Pixel74 in dieser Phase nicht erneut abgefragt. AGENTS/Auftrag/Status, Reparatur-/Signier-/zentrale Sync-/MCP-Dokumentation und Issue1 gelesen; historische Versionsstände nicht als aktuell übernommen.
- PASS statische Bestandsprüfung: OfferTools/OfferJournalRules mit bereits vorhandener ausdrücklich bestätigter Auftragsübernahme; scheduled_date noch ohne strukturierte Mehrfachtermine; bestehende Navigation und Android-JobScheduler. Neue Termin- und Wetterwerkzeuge nur geplant, keine Capability aktiviert und kein Funktionsnachweis behauptet.
- Konzept in **docs/SYNC-DRIVE-KUNDENAKTE-1.48.md**, Abschnitt „Konzept: Aufträge, Termine, Google Kalender und Wetter“ ergänzt; kein neues Dossier. Kunde→Angebot→Auftrag→mehrere Termine→tatsächliche App-Arbeit, ein Editor/Tag-Woche-Stunden, eigene Google-Kalenderprojektion mit minimalen echten OAuth-Rechten/ETags/Request-IDs und bestehendem Drive-Journal. Gemeinsame manuelle/MCP-Regeln, Wochenwetter14Tage, tätigkeitsbezogene Risiken, alternative Zeitfenster und deduplizierte Benachrichtigungen; keine automatische Kundenabsage/Rechnung/Ist-Zeit.
- PASS Recherche offizieller Google-Calendar-/Android-/Open-Meteo-/GeoSphere-Dokumentation. Calendar-Connector vorhanden, dessen Schemakatalog gelesen; weder Kontoinhalte noch App-OAuth-Rechte verifiziert. Open-Meteo-Produktivnutzung benötigt zulässige gewerbliche Lizenz; kein Vertrag/Schlüssel angefordert. Android-Laufzeitfreigabe und Hintergrundbeschränkungen bleiben reale Abnahmebedingungen.
- Geändert ausschließlich diese Statusdatei und vorhandene zentrale Konzeptdokumentation. Keine Quellcode-/Versions-/APK-Änderung, keine neuen Tests notwendig für reine Dokumentation; neue Funktionsabnahmen **NOT TESTED / geplant**. Keine Geschäfts-/Drive-/Kalenderdaten gelesen oder verändert, keine Pixel-/ADB-Aktion, Signierung oder Installation. Nächster Schritt erst nach Umsetzungsauftrag: Grundlage/Termindatenmodell und isolierte Akzeptanztests, dann Kalender/Wetter/MCP in beschriebenen Phasen. Frühere produktive Altfall-/Papierkorbblockaden bleiben unberührt.


### Eintrag 30 – Termine / Phase A: Bestand und Abnahmen (19.10.2026)

- Ausgang90f2eff3756e1f035e5dfebba09fac5ad228fae7, sauber auf vorgesehener Cloud-Branch, Remote identisch/main a38765d unverändert/Pflichtancestor vorhanden. Umsetzungsauftrag für das dokumentierte Konzept; externe Konto-/Kostenfreigaben und Installation separat.
- Reale MCP-Lesung: jetzt ebenfalls **1.49.6/75, DB4, Protokoll6,91 Runtime-Werkzeuge**, appointments noch nicht vorhanden. Keine produktiven Schreibtests oder Kalender-/Kundendatenänderung.
- Abnahmen: eine bestätigte Angebotsübernahme/ein Auftrag bei Replay; mehrere Einsätze ohne Ist-Stunden/Rechnung; gemeinsame UI/MCP-/Drive-Versionsprüfung und atomare lokale Request-Belege; keine Kalenderdubletten nach Antwortverlust; externe ETag-/Terminüberschneidung sichtbar;14Tage Wetter mit Alter/fehlenden Werten und tätigkeitsbezogenen Hinweisen, keine automatische Absage; unabhängige Drive-/Kalender-/Wetterfehler; schmale UI/Entwürfe/Timer erhalten. Ausschließlich synthetische Regressionen und dedizierter Cloud-Emulator.


### Eintrag 31 – Termine / Phase B: gemeinsame Geschäfts- und MCP-Pfade

- Implementiert im Originalpaket: additive appointments-Sammlung in bestehender DB4/Snapshot2/Drive-Verarbeitung, alte Snapshots ohne Termine bleiben lesbar. Gemeinsame Termin-/Zeitzonen-/Beziehungs-/Überschneidungsregeln, voller Record-CAS auch bei zwischenzeitlich neu hinzugefügten Feldern, dauerhafte Request-Belege und transaktionale begrenzte Wochenserien. Keine Ist-Zeiten oder Rechnungen durch Terminplanung.
- Angebotsübernahme behält genau einen Auftrag bei neuen Wiederholungs-Request-IDs; angenommene Nachträge mit Versionsreferenz am vorhandenen Auftrag, Hauptreferenz erhalten. Terminarchiv/Restore und Purge-Verknüpfungsschutz im vorhandenen Papierkorb. Neue13 MCP-Werkzeuge (Quelle104, internes Protokoll7), keine Behauptung einer bereits aktualisierten ChatGPT-Registrierung.
- PASS erste22 JVM-Suiten einschließlich73 neuer Termin-/DST-/Wetter-/Calendar-CAS-/Antwortverlustprüfungen; Bestands-Python273 PASS. Zusätzliche Gesamt-/native Abnahme läuft. Ein früherer Test deckte unvollständigen Vergleich gegen nachträglich hinzugefügte Terminfelder auf; vollständiger Appointment-CAS ergänzt, konkurrierende Variante bleibt erhalten. Alte Katalogzählungen in Tests additiv angepasst, vorhandene Werkzeuge weiter geprüft.

### Eintrag 32 – Termine / Phase C: Oberfläche und Geräteadapter

- Implementiert Tag/Woche/Ist-Stunden,30er Terminlisten, Kunden-/Auftrags-/Angebotszugang, persistenter Editor mit unveränderlicher Ausgangsversion und Retry-ID, Wettergründe/Alternativen/Kenntnisnahme sowie geschützte Timerübergabe. Früher Browserlauf deckte einen außerhalb seines bisherigen Moduls nicht verfügbaren Auftragshelper auf; auf gemeinsamen orders-Zugriff korrigiert. Danach17 gezielte Browserprüfungen PASS, einschließlich115 Metadaten, Stale-/Retry-/Entwurferhalt und320/360/426px mit150%-Schrift. Nachfolgende Schutz-/Menüänderungen werden erneut geprüft.
- Nativer Google-Calendar-Adapter mit expliziter Konto-/Scope-Einrichtung, App-Kalender, stabiler Event-ID, ETag, inkrementellem Token/410, dauerhaftem Restabgleich und getrennten Konfliktentscheidungen; kein externer Schreibaufruf in SQLite-Transaktion. Daten bleiben auf Pixel/Drive maßgeblich. Kalender-/Wetter-Wiederholungen getrennt begrenzt, eigenes JobScheduler-Jobpaar.
- Open-Meteo-Kundenzugang nur nach Geräte-Einrichtung/gewerblicher Freigabe, AES-GCM-geschützter Geräteschlüssel außerhalb von MCP/Snapshot,14Tage stündliche Werte und tätigkeitsbezogene Bewertung; ergänzende GeoSphere-Warnungen mit explizitem unbekanntem Zustand bei nicht bestätigtem Abruf/Format. Echte API-/OAuth-/Wetterrechte hier nicht aktiviert. Android-Benachrichtigungen dedupliziert, Ruhezeit21–6Uhr, keine automatische Terminänderung/Kundeneinladung.
- Quellstand1.50.0/76. Release-/Instrumentierungsbuild und Lint bisher erfolgreich; gesamte Qualifikation und neue native SQLite/Provider-Proben folgen. Keine Produktivdatenaktion, keine Installation am Pixel, kein kostenpflichtiger Dienst abgeschlossen.


### Eintrag 33 – Termine / Phase D: abgeschlossene Cloud-Qualifikation und Originalkandidat

- **PASS273 Python-Tests,22 JVM-Suiten**, einschließlich75 neuer Termin-/DST-/Wetter-/Calendar-CAS-/Antwortverlustprüfungen. **PASS28 Browser-Suiten**, darin19 gezielte Planungsprüfungen mit115 Metadatensätzen,30er Pagination, getrennten Entwürfen, versionsfesten Wiederholungen, Timer-Schutz, Alternativvorschau und320/360/426px/150%-Schrift. Screenshots der echten operativen Assets visuell geprüft; zu große Datumsnavigation vereinfacht, lange Entwurfsschaltflächen umbrechen. Alle bisherigen Pflanzen-/Foto-/Rechnungs-/b.b.-/Timer-/Privacy-Regressionen erhalten.
- **PASS120 native Android-/SQLite-/MCP-/Providerprüfungen,0 Fehler** im vorhandenen isolierten Cloud-Emulator: nun17 neue native Proben zusätzlich zur bestehenden Abnahme, einschließlich Angebotsübernahme mit anderer Request-ID ohne zweiten Auftrag, Nachtrag/umgekehrter Journalreihenfolge, atomarer Serie und injiziertem SQLite-Abbruch, Neustart/Idempotenz, echter App-Timer→Arbeitsnachweis mit Terminreferenz, Kalender-Ack-Verlust ohne doppelte Anlage, Paralleländerungen/ETag-Auflösung/410/entzogene Rechte und erhaltener Queue. Android-Benachrichtigung mit drei Aktionen tatsächlich erzeugt, unveränderte Warnung nicht erneut gepostet, Kenntnisnahme ohne Terminänderung geprüft. Provider/Geschäftsprüfungen synthetisch, kein echtes Google-Konto verwendet.
- Erster nativer Lauf116 PASS/1 FAIL erkannte eine veraltete Protokoll6-Testannahme. Auf tatsächlichen Runtime-Protokollwert umgestellt, ohne Zugriffs-/Datenprüfungen zu entfernen. Vollständiger abschließender120/0-Lauf tatsächlich ausgeführt. Frühe Browserfehler an Auftragshelper/Entwurfsresume und vollständigem Versionsvergleich dokumentiert/korrigiert; keine Teil-PASS-Logs als Erfolg akzeptiert.
- Zusätzlicher frisch erstellter Cloud-Android-Datenbereich aus SDK-Stockimage; vorhandene Emulator-/App-Daten erhalten, kein Wipe/Uninstall/Downgrade. SDK-AVD-Manager meldete fehlende Paketregistrierung des bereits vorhandenen Emulatorprogramms; neue AVD ausschließlich aus bekanntem Cloud-Hardwareprofil und unverändertem SDK-Stockuserdata angelegt. Zuerst Original75/Fresh-PASS, synthetische Upgrade-Baseline, danach **tatsächliches In-place-Update75→76 PASS** mit allen vorigen Geschäftsfeldern/IDs/Pending-Bytes/Einstellungen/Tunnelkonfiguration/laufendem Timer. Nur ab Baseline neu eingeführte leere appointments-Sammlung/appointments_v1-Capability dürfen fehlen; alle vorherigen Capabilities bleiben verglichen. Tatsächliche Android-WebView83: bestehende Navigation/Timer/Garten/Papierkorb und neuer Planungseditor mit korrektem Kunden/Auftrag sowie Entwurferhalt PASS. Zusätzlich **Fresh76-Appdaten in eigenem Android-Testprofil PASS**, ursprünglicher Benutzer-Datenbereich unverändert. Keine Pixel-Installation oder Pixel-Leistungsmessung.
- Release-/Instrumentierungsbuild PASS, Lint **0 Fehler/27 Warnungen**; kanonische Pflanzenspiegel181 Profile/362 Bilder/370 Ausgaben unverändert geprüft. Finale APK408 Assets bytegleich zur Quelle, alle elf neuen operativen Planungsklassen vorhanden. DEX-SHA256 **0ef95be1fe59e62bf68a413f9a40c21a555937ff4aaf0c5732de703e1b49f36f**. Source-/APK-Privacy PASS,null operative Seedzeilen, keine reale Kundendatei als Fixture oder in Git. Keine zweite operative App/Datenbank oder Desktopübernahme.
- Originalkandidat **downloads/Haus-Garten-1.50.0-76-original-update-kandidat.apk**, **at.hausgarten.service,1.50.0/76**, min26/target37,nicht debuggable, **34.475.927 Bytes**, SHA256 **3ae076024cec4ae38b51ea93f23a91fbbfce3582c7d169e80282423248f0ea42**. Interne erste Revision vor den letzten Nachbesserungen nicht veröffentlicht; ausschließlich abschließend120/0 geprüfte zweite Revision als Download kopiert, Hash erneut verglichen. Instrumentierungs-APK nur vorhandener Testweg, nicht bereitgestellt.
- Vor jeder echten Signierung ausschließlich Vorhandensein der fünf HG_ORIGINAL_*-Variablen geprüft; vollständig. Ausschließlich tools/sign_original_apk.py, vorhandener Originalschlüssel; Instrumentierung nur dokumentierter Modus. Kein Ersatz-/QA-/Debug-Schlüssel erzeugt und keine Actions-Signierung. Finale unabhängige Prüfung plus Vergleich zu75: genau ein Originalsigner **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**, v2/v3 gültig,16-KiB-zipalign PASS. Cleanup unabhängig bestätigt: private Signiertempordner0,Keystore-/Privatschlüsseldateien nach geprüften Endungen im Repository0. Zertifikatsvergleich gegen Cloud75, kein direkter Signaturzugriff auf den Pixel.
- Privater Download **https://drive.google.com/file/d/1HkgAS3mxW4qaT7m6n6qgEh-m8Cn5WPIJ/view?usp=drivesdk** tatsächlich in vorhandenen APK-Backups/Codex-Cloud-Builds hochgeladen. ID/Name/MIME/Eltern/34.475.927 Bytes, Owner-only/shared=false und vollständige Rohdateireferenz mit derselben Größe rückgelesen. Keine Freigabe geändert, keine Betriebs-/Sicherungsdatei ersetzt oder gelöscht; kein unabhängiger Remote-Bytehash behauptet.
- **Reale Schlusslesung ausschließlich MCP-Metadaten:** Pixel weiterhin **1.49.6/75,Protokoll6,91 Runtime-Werkzeuge**, tatsächlich hier aufrufbarer Connector83. Neue13 Werkzeuge daher **nicht im realen Connector nachgewiesen**. Cloud-Kandidat enthält104/Protokoll7. Kalender-/Wetterkonto nicht verbunden, kein API-Vertrag/Schlüssel angefordert, kein produktiver Termin/Kalendereintrag/Arbeitsnachweis/Snapshot verändert. Früherer Altfall-/Papierkorbauftrag durch diesen Entwicklungsauftrag nicht ausgeführt.
- **Verbleibende reale Abnahme:** vollständige Gerätesicherung und ausdrückliche Pixel-Updatefreigabe; Calendar API/OAuth-/Originalzertifikats-/Scope-/Organisationsfreigabe und tatsächlicher Google-Kalender-/Wetter-Rundlauf; zulässiger gewerblicher Wetterzugang, Android-Benachrichtigungs-/Hintergrundverhalten; verwaltete ChatGPT-Verbindung/Toolregistrierung refreshen und104 Tools real prüfen. Google-Serien/ganztägige Umformungen oder extern entfernte Events bleiben prüfpflichtig; App-Wiederherstellung ist keine behauptete Google-Event-Wiederherstellung. Unbekannte/veraltete Wetterdaten bleiben unbekannt, keine automatische Kundenabsage oder Ausweichbuchung. Gesamt-Pixel-/Providerfreigabe weiterhin offen; Cloud-Umsetzung und isolierte Qualifikation abgeschlossen.

### Eintrag 34 – Termine / Phase E: Bereitstellung abgeschlossen

- Implementierung, Regressionen, Original-APK und Downloadangaben im Commit **38e99294c5e84a10bcd20f85dfc0d198d9da8b58** normal auf **codex/original-signing-cloud-setup-20261018** gepusht; Remote-Ref identisch geprüft. main unverändert **a38765d586c3ea02b999e770e5ecb22b34642962**, kein Force/Rewrite. Folgender Statuscommit ändert ausschließlich diesen Veröffentlichungsnachweis.
- Downloaddatei76 vorhanden,34.475.927 Bytes/SHA256 gemäß Eintrag33, privater Drive-Link tatsächlich rückgelesen. Bestehende Kandidaten75/74 und sämtliche Betriebsdateien/Sicherungen erhalten. Kein weiterer Bericht/PDF oder paralleles Projekt erzeugt; vorhandene zentrale Dokumentation aktualisiert.
- Cloud-Arbeit abgeschlossen. Pixel bleibt bei letzter realer Metadatenprüfung75/91 Tools, ChatGPT-Katalog83; Konto-/Provider-/Pixelabnahmen aus Eintrag33 bleiben offen. Keine produktive Datenänderung oder Geräteinstallation aus dieser Bereitstellung ableiten.


### Eintrag 35 – MCP-Parität / Phase A: Ausgang und Abnahme

- Cloud-Branch sauber, Ausgang **db5fcd3d0035df4d8ed01c48da0648da690c19b3**, erforderlicher05fdcbec enthalten; Remote identisch, main unverändert a38765d. Live MCP: **1.50.0/76, Protokoll7,104 Werkzeuge**, Hash **9eade6bb724836aee976f9a912c80a0d40d8e0cc1dead509a55f8f7069fc3df2**, Schreiben aktiv. DB4/Snapshot2.
- Sieben Reviews, null ausstehende Uploads; Vorschau68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7 unverändert. Drive action_required/LEGACY_SOURCE_CHANGED, Provider-Rücklesen, cloud_confirmed=false. Keine Produktivmutation.
- Abnahme: dedizierte vollständige Sicherung bei festem Vorschauhash und ruhendem Sync trotz exakt dieses Fehlers; laufende Jobs, andere Fehler, geänderte Hashes oder unvollständige Medien bleiben gesperrt. Leere additive Schemafelder dürfen keinen falschen Legacy-Konflikt auslösen; echte Fremdänderungen bleiben sichtbar. Sieben synthetische keep_current-Entscheidungen müssen Geschäftshashes erhalten und die Queue nach echtem Provider-Rundlauf leeren.
- UI-/Bridge-Inventar gegen tatsächliche MCP-Dispatchpfade, zusätzliche Rechte-/CAS-/Replay-Negativtests. Quellkatalog, installierter Katalog und ChatGPT-Registrierung getrennt nachweisen. Kein Pixel-Update ohne Sicherung und ausdrückliche Freigabe.


### Eintrag 36 – MCP-Parität / Phase B: Implementierung und Hostprüfungen

- Drei zyklische Sperren getrennt: dedizierte Sicherung akzeptiert bei ruhendem Sync ausschließlich LEGACY_SOURCE_CHANGED plus unveränderte Siebener-Vorschau, bestehende RW-Freigabe und geprüften Providerstand. Sicherung liest die unveränderliche Veröffentlichungskette auch als reine Evidenz abweichender Legacy-Quelle; beide Snapshots bleiben im geprüften privaten ZIP. Keine Freigabe echter Fremdänderungen für den normalen Sync. ZIP-Einträge per CRC, Archiv-SHA und Geschäftshash vor Abschluss geprüft. Lokaler atomarer keep_current-Sammelabschluss verlangt diese Sicherung, keine erfolgreiche Netzwerkoperation.
- Normaler Drive-Leser akzeptiert exakt passende historische Hashes bei ausschließlich fehlenden/leeren additiven Sammlungen offers/service_catalog/appointments. Nichtleere Sammlungen, unbekannte Felder und echte Datensatzänderungen bleiben geschützt. Regression bildet75→76-Normalisierung und echten Fremdstand getrennt ab.
- Quellstand **1.50.1/77, Protokoll8,111 Werkzeuge**: zusätzlich get_ui_action_catalog, inspect_order_review, save_reviewed_order, get_planning_settings, update_planning_settings, request_weather_refresh, finish_work. Vorhandene Werkzeuge erhalten; start_work unterstützt Terminreferenz; Listen erhalten Versatz, Fotos zusätzlich Rolle/Sitzung/Arbeit/Zeitraum. Planungseinstellungen verwenden denselben SQLite-Eigentümerspeicher und native Prüfschicht; vorhandene externe Kontorechte/Schlüssel werden dadurch nicht erteilt. finish_work stoppt und speichert tatsächliche Timerarbeit in einer Transaktion; ungültige Eingaben rollen auch den Stopp zurück. Der aktuelle ausdrückliche Sprach-/MCP-Auftrag autorisiert diesen realen Timerablauf; keine erfundenen produktiven Arbeitsnachweise.
- Versioniertes Laufzeitinventar ui-mcp-actions.json:156 semantische Aktionsgruppen,145 fachliche MCP-Pfade,11 ausdrücklich erklärte Plattform-/Darstellungsgruppen;31 vollständige UI-/Native-Quellen versionsgebunden. Das sind fachliche Aktionen, keine Zählung duplizierter HTML-Schaltflächen. Statischer Pfad-/Schema-/Rechte-/Katalog-/Listenvertrag mit990 Prüfungen PASS; alle23 JVM-Suiten PASS,273 Python-Tests PASS. Android-Build/Lint PASS; vollständige Browser- und native Qualifikation folgen.
- Echte bestehende Verbindung104/104 Namen identisch; Health und Capabilities melden Hash9eade6bb724836aee976f9a912c80a0d40d8e0cc1dead509a55f8f7069fc3df2. Sicher scheiternde Live-Schreibpfade: unbekannter Rechnungsentwurf REFERENCE_NOT_FOUND; unmöglicher Legacy-Abschluss weiterhin alte Sync-Sperre. Keine Geschäftsdaten-/Queueänderung. Quellkatalog noch nicht auf Pixel installiert.

- Ergänzende Prüffunde in Phase C: natives Planungsformular verwendete beim Speichern eine neue Basis unter alten Eingaben. Öffnungs-Hash bleibt jetzt bis zum erfolgreichen atomaren Speichern fest; gemeinsamer CAS-Saver für UI/MCP, Eingaben bei Konflikt erhalten. Zusätzliche native Regression. Wetterwahrscheinlichkeit wird auch im nativen gemeinsamen Validator auf0–100 begrenzt. Erster interner77-Kandidat noch nicht veröffentlicht; abschließende Revision erst nach diesen Prüffunden und nativer Abnahme.

- Erster vollständiger nativer Lauf124/0 PASS. Abschließender Lauf123/1: der neu ergänzte Drift-Negativtest wurde von der echten Sicherungsroutine korrekt mit IOException abgewiesen; die Hilfsfunktion rejected akzeptierte nur IllegalArgumentException. Erwarteten konkreten IOException-Pfad explizit geprüft, ohne Produktionsschutz zu lockern. Abschließender vollständiger Lauf mit korrigierter Instrumentierung folgt; kein Teil-PASS als Endabnahme.


### Eintrag 37 – MCP-Parität / Phase C: abschließende Qualifikation

- Quellcommit **90b550e897aada6434fdf8a0ea81558720ff02d3**, normal auf vorgesehene Branch gepusht und Remote-Ref geprüft. main unverändert a38765d. Alle31 geprüften UI-/Native-Quelldateien entsprechen dem Inventar;156 semantische Gruppen,145 fachliche Werkzeugpfade,11 erklärte Plattform-/Darstellungsgruppen. Keine Behauptung, jede einzelne Schaltfläche auf dem Pixel betätigt zu haben.
- **PASS273 Python-Tests,23 JVM-Suiten mit993 gezielten Paritätsprüfungen,28 Browser-Suiten.** Darin14 Legacy-Hashprüfungen,168 Papierkorb-/Backup-Policyprüfungen, echte Fremdänderung weiterhin gesperrt, fehlende Rechte/aktive Jobs/stale Vorschau gesperrt;125 synthetische Foto-Metadatensätze vollständig über Seiten lesbar. Bestehende Rechnungs-/Angebots-/Pflanzen-/b.b.-/Foto-/Timer-/Privacy-Regressionen erhalten.
- **Final PASS124 native Android-/SQLite-/MCP-/Providerprüfungen,0 Fehler** auf der endgültigen operativen APK und korrigierter Instrumentierung. Siebener-Providerfall bildet75er Veröffentlichung vor appointments-Normalisierung ab; sieben alte Aktionen behalten Originale bei wiederholtem Sync. Bei action_required/LEGACY_SOURCE_CHANGED feste Vorschau geprüft, Sicherung mit Originalnachladen und SHA/CRC erstellt, Quelländerung während Sicherung verworfen, false-Bestätigung abgewiesen, Sammelabschluss samt Replay geprüft. Danach aktive Queue0, sieben Entscheidungen in Historie, originale Journale erhalten, Geschäftshash gleich, nächster Provider-Sync ebenfalls PASS. Provider_readback/cloud_confirmed=false korrekt erhalten.
- Native zusätzliche Abläufe: Planungseinstellungen CAS/Replay, offenes natives Formular gegen neueren MCP-Stand gesperrt, fehlende Kalender-/Wetterrechte abgewiesen; finish_work speichert tatsächliche Sitzung genau einmal und rollt Stopp bei ungültiger Tätigkeit zurück; gemeinsame Auftragsprüfung mit Token/Replay; Katalog-Pagination. Die native Abnahme blieb vollständig in isolierten Datenbanken/Dateien/Preferences des Cloud-Emulators, Ziel-Geschäftssnapshot unverändert.
- **Fresh77 PASS** in neuem Android-Benutzer10 des separaten Cloud-Emulators5582, bestehender Benutzer erhalten; keine Geschäftsdaten/Zugangsdaten/automatische Verbindung. **Update-/Datenerhalt PASS** auf vorherigem synthetischen76er Emulatorstand, vorhandene Upgrade-Baseline mit Snapshot/IDs/Queue/Einstellungen/Tunnelkonfiguration/laufendem Timer unverändert. Tatsächliche Android-WebView-Navigation PASS auch mit endgültigem Kandidaten. Kein Pixel-ADB, keine Pixel-Installation, keine Google-Konto-/Wetter-End-to-End-Abnahme.
- Release/Instrumentierung/Lint PASS, **0 Fehler/29 Warnungen**. Finale operative APK: **at.hausgarten.service,1.50.1/77**, **34.488.287 Bytes**, SHA256 **0024d12ca3137f326f61e1c454f062855357c73e385a179e34c20bb1230b818f**, DEX **65048dfcede2126776181baa6be004e0f51ddd1649149dd656757c830776c41a**. Alle409 Assets bytegleich zur Quelle;181 Pflanzenprofile/362 Bilder unverändert geprüft. Source-/APK-Privacy PASS,null operative Seeds. Nur endgültige operative Revision wird veröffentlicht; frühere interne Revision bleibt unverteilt.
- Originalsignierung ausschließlich tools/sign_original_apk.py, fünf Variablen ausschließlich auf Vorhandensein geprüft. Vorhandener Originalsigner **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**, v2/v3 und16-KiB-Ausrichtung unabhängig geprüft. Cleanup0 Signiertempordner/0 private Schlüsseldateien im Repository. Ein erster Instrumentierungsaufruf verwendete einen nicht vorhandenen unsigned-Dateinamen und stoppte vor Ausgabe; anschließend korrekten vorhandenen Android-Testpfad signiert. Keine neuen Signierschlüssel/QA-App/CI-Signierung. Instrumentierung nicht bereitgestellt. Original-Cloud-Update ist kein Pixel-Datenerhalt-Nachweis.
- **Realer Connector:**104/104 Namen und oberste Parameternamen/Pflichtfelder der tatsächlich veröffentlichten Deklarationen stimmen mit Pixel76 überein. Hash9eade6bb724836aee976f9a912c80a0d40d8e0cc1dead509a55f8f7069fc3df2 zwischen Health/Capabilities identisch; eigenständiger vollständiger ChatGPT-Schemahash nicht abrufbar. App-Berechtigungsverwaltung ausdrücklich geprüft: eigener Override **Allow all actions** aktiv, globale Low-risk-Voreinstellung übersteuert. Keine Berechtigungseinstellung geändert. Pixel write_enabled=true. Neue Quellfassung111/Protokoll8, Hash **392fdfacf116c49e13306fcfc8c84ab49ee0ce20b35ca62d250fa069ecd1f2ff**, noch nicht live installiert/registriert.
- **Tatsächlicher letzter Drive-Rundlauf auf Pixel76:** request_sync ausgelöst, danach abgeschlossen zurückgelesen: action_required/LEGACY_SOURCE_CHANGED, Uploads0, Reviews7, Historie12, provider_readback, cloud_confirmed=false. Geschäftshash **4fd509c09e7a65d0aaa87af7c533d19a66229e7b208a5a0a365bd9ba684ab91a** unverändert; Counts52 Kunden/3 Aufträge/45 Arbeiten/95 Rechnungen/25 Fotos/33 Bausteine/0 Angebote/0 Termine. Keine alte Löschung und keine Produktivbereinigung ausgeführt.
- **Offen:** Gerätesicherung und ausdrückliche Pixel-Updatefreigabe; Original77 installieren, vorhandenen Tunnel/ChatGPT-Katalog neu verbinden und111 Tools/Hash/Parameter tatsächlich nachweisen. Danach frische Siebener-Vorschau, vollständige geprüfte Konfliktsicherung und autorisierten keep_current-Abschluss real ausführen, Drive-Endstatus erneut prüfen. Bei tatsächlich inhaltlich fremdveränderter Legacy-Datei bleiben beide Stände erhalten und eine gesonderte fachliche Prüfung notwendig; kein automatisches Wegquittieren eines realen Fremdstands. Google-/Android-/Wetter-Erstfreigaben bleiben Geräte-/Kontohandlungen. Cloud-Code und isolierte Qualifikation abgeschlossen, reale P0-Abnahme noch offen.


### Eintrag 38 – MCP-Parität / Phase D: geprüfte Bereitstellung

- Ausschließlich endgültige operative Revision als **downloads/Haus-Garten-1.50.1-77-original-update-kandidat.apk** aufgenommen; SHA2560024d12ca3137f326f61e1c454f062855357c73e385a179e34c20bb1230b818f erneut nach Kopie geprüft. Kandidaten76/75 und frühere Dateien bleiben erhalten. README/SHA256SUMS/gezielte Gitignore-Ausnahme aktualisiert.
- Privater Drive-Link **https://drive.google.com/file/d/1s4GCUx3YcqtRE8elKCLuCXLjruuHW35i/view?usp=drivesdk**, tatsächlich in bestehendem Codex-Cloud-Builds hochgeladen. Metadaten34488287 Bytes, Name/MIME/Eltern und Owner-only/shared=false rückgelesen; vollständige Rohdateireferenz mit derselben Größe verfügbar. Kein unabhängiger Remote-Bytehash behauptet. Keine Freigaben geändert und keine Betriebs-/Sicherungsdateien ersetzt oder gelöscht.
- Codeänderungen im Implementierungscommit90b550e: LegacyBackupPolicy/LegacyConflictBackup, DrivePrimaryState/Coordinator, PixelMcpReadTools/OwnerTools/SessionTools/PixelOwnerAccess, MainActivity, AppointmentTools/Runtime/PlanningJob/PlanningConnectionActivity, Legacy-UI, versioniertes UI-MCP-Inventar und Versionsnummer. Tests: DriveProviderAcceptance, PlanningNativeAcceptance, DriveLegacyNormalizationTests, TrashPurgeBatchTests, UiMcpParityTests, bestehende Daily-/Write-Verträge angepasst. AGENTS präzisiert die ausdrückliche aktuelle MCP-Erfassungsautorisierung, zentrale Eigentümerdokumentation und CODEX-STATUS aktualisiert; keine zusätzliche operative Datenbank/App oder weiteres Dossier.
- Cloud-Code, Kandidat und isolierte Qualifikation fertig. Reale Pixel-P0-Abnahme aus Eintrag37 bleibt ausdrücklich offen. Kein neuer produktiver Konfliktabschluss und keine Installationsfreigabe durch APK-Bereitstellung behauptet.


### Eintrag39 – Auftrag main b2c37a2 / Phase A: Bestand und Kriterien

- Aktuelles main **b2c37a2f909cc8ac6defabe68dbdd3ce6a42db14**, zentralen Auftrag1343 Zeilen vollständig gelesen. main enthielt nur ältere operative Basis70 plus Preisquellenfix; vorhandenen veröffentlichten77-Stand5fbe0a3 auf lokalem main verlustfrei normal zusammengeführt. Keine neue Entwicklungsbranch, kein Reset/Force/Desktopimport. Aktueller main-Auftrag bleibt erhalten.
- Reale lesende MCP-Prüfung: **1.50.1/77,111 Werkzeuge,Protokoll8**, Hashae7cb69f7994ae8141d2d8c925594982f40236921d65abb3739eee270b5b312f. Schreiben und Angebote/Katalog bereit. Queue7 Reviews/0 Uploads,review_required,kein technischer Syncfehler,provider_readback/cloud_confirmed=false. Nur Counts/Hashes ausgegeben; kein Angebot angelegt oder versendet.
- Abnahmekriterien: gemeinsamer Vertrag für beide KV-Typen, explizite Bindungsbestätigung, ein UI/MCP/PDF-/Journalpfad, getrennte Validierung/Finalisierung/Versandbelege; alle156 Inventargruppen deterministisch paginiert. Testbelege nur isoliert, kein produktiver Testexport/Versand. Enum-/Preisquellenvertrag und alte Snapshots prüfen. Neuer Build>77 mit Git-/Source-Fingerprint und Originalsignatur; physische Pixel-/Connectorabnahme nur bei tatsächlichem Zugriff behaupten.
- Gefunden: main-Preisfix enthielt wörtliches Backslash-n im Java-Code; vorhandene KV-Typen bislang nur Label/Defaulttext; fehlende persistierte Validierungs-/Versandzustände und zentrale Enumverträge. Arbeitsstand verwendet die vorhandenen Angebote weiter, kein zweites Datenmodell.

## Eintrag 40 – gemeinsame Dokumentregeln und MCP-Verträge implementiert (19.10.2026)

- Bestehende `offers`-Entität für alle vier Dokumentarten beibehalten; kanonischer `offer-contract.json` mit generierten Java-/Browser-Adaptern. `price_source=user_confirmed`, Preisgrundlagen, Dokument-/Steuer-/Kundenarten und Vertragswege gemeinsam definiert. Verbindliche/unverbindliche Kostenvoranschläge speichern Bindungsmodus, explizite Bestätigung und editierbaren Hinweis. Keine Annahme einer Bindungsart und keine neue Estimate-Toolfamilie.
- Validierter Entwurf, unveränderlicher finaler Datensatz samt eingefrorenem Aussteller/Empfänger und SHA-geprüftem PDF, getrennte Share-/Versandstatus; `sent` ausschließlich nach Gmail-SENT-Readback mit identischem Anhang, Aussteller und bestätigtem Empfänger. Pixel benötigt hierfür zusätzlich seine eigene ausdrücklich freigegebene Gmail-Leseberechtigung. Noch kein realer Versand getestet oder ausgelöst.
- Neue atomare Create+Validate-Operation, Schema-/Domain-Grenzen, read-only Self-Test, Build-Fingerprint und versionCode-Baseline-Gate. Quellkatalog jetzt 117 Werkzeuge / Protokoll 9; physischer Pixel unverändert 111 / Protokoll 8.
- UI-Katalog vollständig über 100+56 Einträge, Hashbindung und `next_cursor`; 156 semantische Aktionen, 145 fachliche MCP-Pfade, 11 begründete Plattform-/Darstellungsgruppen. Build stoppt bei nicht geprüften UI-Quelländerungen/fehlenden Zuordnungen.
- Testdokumente erfordern klar markierte Testkunden und Testempfänger, bleiben ausschließlich lokal und können nicht extern versendet oder produktiv in Aufträge übernommen werden. Keine produktiven Testdaten erzeugt; bestehendes reales Angebot unangetastet.
- Zwischenprüfung: Release/Instrumentation/Lint gebaut; 273 isolierte Python-Tests bestanden, Java-Suite einschließlich 96 neuer Domänen-/Providerbeleg-Prüfungen bestanden. Browser-/native Abnahme läuft. Diese Angaben sind kein physischer Pixel-/Update-Nachweis.

## Eintrag 41 – native Kostenvoranschlagsabnahme und Isolation repariert (19.10.2026)

- Die neuen echten SQLite-/PDF-/Chunk-/Neustartprüfungen deckten eine Folgeaktionslücke auf: `offer_validate` erbte beim neu lokal angelegten Testangebot noch nicht dessen Lokal-Provenienz. Korrigiert durch gemeinsame Zuordnung der Offer-/Invoice-Folgeaktionen zum Ursprungsdatensatz; keine falsche Drive-Vormerkung. Isolierter erneuter Android-Lauf: alle vier Dokumentarten einschließlich PDF/SHA/Readback, Share ohne Versand, direkte Journal-Wiederholung, Neustart und Ausschluss vom Drive-Export bestanden (8 Proben, 0 Fehler).
- Vollständiger vorheriger Android-Lauf: 127 bestanden, genau diese vier neuen Isolationstests fehlgeschlagen; dieser interne Kandidat wurde nicht freigegeben. Nach Reparatur gezielt bestanden; abschließender vollständiger Lauf folgt auf dem endgültigen Build. Ein älterer interner Lauf wurde wegen unnötiger globaler Journalprojektion bei Batch-Provenienzprüfungen abgebrochen; diese Prüfung wurde auf Metadaten begrenzt. 128er-Batch-Abnahme besteht wieder.
- 24 JVM-Suiten, 273 Python-Tests und 28 Browser-Suiten bestanden. Zusätzlich 130 synthetische Angebote paginiert/gefiltert und 105 Foto-Metadaten mit Lazy Loading geprüft. Visuelle Kontrolle fand zusammengequetschte Angebotstitel bei 320px/150%; gemeinsame Dokumentzeilen jetzt untereinander, mit eigener Mindestbreiten-/Höhenprüfung bei 320/360/426px.
- Rechtsquellen am 19.10.2026 tatsächlich gelesen: RIS ABGB §1170a und KSchG §5. Keine automatische Annahme der Verbindlichkeit; bestehende Standardtexte zentralisiert, editierbar und vor Finalisierung ausdrücklich bestätigt. Gesetzes-/Regelversion und Quellen im gemeinsamen Dokumentvertrag.
- Physischer Pixel bleibt 1.50.1/77, 111 Tools/Protokoll8. Katalog live vollständig 100+56, sieben Keep-current-Vorschläge erneut gelesen, keine produktive Testschreiboperation, kein realer Mailversand, kein Geräteupdate.

## Eintrag 42 – vollständige Abnahme und reale Backup-Barriere (19.10.2026)

- Commit `1ff18d1d8ddb1c0b23f3eac2108fe08152c02917`: 24 JVM-Suiten, 273 Python-Tests, 28 Browser-Suiten sowie **131 Android-SQLite/MCP-Proben, 0 Fehler** auf dem originalsignierten Cloud-Emulator-Build. Release/Instrumentation/Lint und APK-Privacy-Gate bestanden. Alle vier Dokumentarten, synthetische Provider-/Gmail-Verträge, Journal-/Neustartpfade und lokale Testdaten-Grenzen geprüft; keine Aussage über ein physisches Pixel-Update.
- Reale, ausdrücklich autorisierte Legacy-Sicherung mit frischem unverändertem Preview `68c60929c2477b987524f578cade273d041a74380863b5dc6b2af146bab9f5c7` versucht. Installierter Build 77 verweigert sie mit `READ_ERROR: Ungültige Foto-Dateireferenz.` Kein vollständiger Beleg, deshalb **keine** Altentscheidung. Sieben Reviews bleiben erhalten. Aktive Zeiterfassung ist IDLE.
- Nur lesende Metadatenanalyse: 25 aktive Fotos, davon zwölf ältere Snapshot-Zeilen ohne heutige SHA-/Storage-Metadaten. Daraus folgt nicht automatisch, dass die Originaldateien verloren sind. Bestehender Backup-Code verlangt aber auch für solche Altzeilen den neuen Content-addressed-Dateinamen.
- Ergänzter Sicherungsweg liest ausschließlich den exakt referenzierten sicheren Bilddateinamen im bereits freigegebenen `HausGarten-Fotos`-Ordner. Zwei identische begrenzte Provider-Lesevorgänge, Bilddekodierung, beobachtete SHA/Größe und Herkunftsindex im selben privaten Backup; kein erfundener historischer Hash und keine Geschäfts-/Foto-Migration. Fehlende, doppelte, veränderte oder unsichere Originale blockieren die Sicherung weiterhin. Remote-Snapshot/Publikation werden auch nach der Medienprüfung erneut verglichen.
- Erweiterter vorhandener Android-Provider-Regressionsfall prüft Legacy-Originalbytes im ZIP, fehlende Datei, Pfad/URL-Abweisung, geänderten Readback und danach unveränderte Keep-current-Auflösung. Abschließende Abnahme dieses ergänzten Weges läuft; real erst nach Geräteupdate prüfbar. Kein Backup-Schutz entfernt.

## Eintrag 43 – Legacy-Originale und vollständiger Katalogvergleich (19.10.2026)

- Erweiterter Android-Provider-Lauf: **12 Proben, 0 Fehler**. Enthält vollständige Sicherung eines alten Foto-Dateinamens ohne SHA-Snapshotmetadaten, exakte Originalbytes/Herkunftsindex im ZIP, fehlende Datei, unsichere Namen/URLs und veränderten zweiten Readback; danach sieben synthetische Keep-current-Entscheidungen, unveränderte Geschäftshashes, Queue 0 und Historie 7. Zwei Fehler im neuen synthetischen Fixture (Data-URI-Dekodierung und Legacy-Basishash) korrigiert; keine produktiven Daten für die Reparatur verwendet.
- Tatsächlichen vollständigen Pixel-Katalog (111 Toolobjekte mit Schema/Annotationen) gegen die neue Quelle verglichen: **kein Werkzeug fehlt**, sechs neue Werkzeuge, 14 beabsichtigte Schema-/Beschreibungsänderungen. Damit werden die vorhandenen operativen MCP-Funktionen beibehalten, statt einen älteren Funktionsstand einzusetzen.
- Zusätzlicher Browserfall deckt neue Leistungsbausteine ab: Auswahl `unit`/`text` aus demselben Vertrag; leerer Preis bleibt `null`, keine freie Enum-Eingabe. Dadurch auch echten Rücksprungfehler im neuen paginierten Katalog gefunden und behoben: Klickereignis darf nicht als `more=true` behandelt werden. Neue schmale Dokumentzeilen und 130er-Pagination bestehen im gezielten Browserlauf.
- Test-Isolation auch auf Papierkorb-Folgeaktionen erweitert: Archiv-/Purge-Provenienz aus dem Original erhalten; reiner Test-Papierkorb bleibt lokal. Gemischte Test-/Drive-Sammellöschung wird vor Speicherung abgewiesen; einzelne versionsgeschützte Test-Purges bleiben als auflösbarer Pfad erhalten. JVM-Verträge bestanden, neue native Archiv-/Empty-trash-Probe Teil des letzten Gesamtlaufs.
- Reales Legacy-Backup im unveränderten installierten Build 77 bleibt mangels dortiger Legacy-Foto-Unterstützung unvollständig. Keine sieben Altaktionen geschlossen, keine alte Löschung ausgeführt. Neues Gerät-/Connector-Endtestfenster erst nach autorisiertem Update.

## Eintrag 44 – kontrollierte echte Testentwurfs-Bereinigung (19.10.2026)

- Nach erfolgreichem vollständigem Android-Lauf mit 132 Proben/0 Fehlern ausschließlich den ausdrücklich freigegebenen falschen 1-EUR-Entwurf `id_sha256_760622aaec5679a80d4929b9852789a5354a8823cbbef7fb23defb5d49a08b45` frisch gelesen, exakten Titel/Entwurfsstatus/Betrag und Hash `acfa1c3c0e366e7d3f9556e1f032f08116e6da395417c1f2332342d1d6d0ead6` geprüft. Löschvorschau erlaubt, keine Fotoverknüpfungen. Reversibel archiviert, keine physische Löschung; Request-Beleg `mcp_owner_f762da2ce0789749b5969bf83bccfa3d86f2ae6d5cad0ec709e2ef7aa171e06e`.
- Frischer Vorher-/Nachher-Integritätsvergleich: ausschließlich Angebotsgruppe geändert, aktive Angebote 2→1. Unverändert 53 Kunden, 3 Aufträge, 45 Arbeitsaufzeichnungen, 95 Rechnungen, 25 Fotos, 33 Bausteine, 0 Termine; ihre jeweiligen Gruppenhashes identisch. Kein echtes Angebot neu erstellt, finalisiert oder versendet. Auto-Drive-Abgleich lieferte `transferred`/`uploaded_to_drive=true`, Gesamtstand weiterhin Provider-Readback ohne Cloud-Behauptung. Sieben Legacy-Reviews, keine ausstehenden Uploads.
- Reale Statusinkonsistenz des installierten Builds 77 reproduziert: Owner-Aktionsbeleg zeigte trotz Übertragung `confirmation_scope=local_only`. Quellfix verwendet tatsächliche `drive_delivered_ids` für Provider-Belege, unterscheidet davon `applied_on_pixel` und erhält Original-Owner-Entitätsangaben. Nur lokales Anwenden behauptet keine Übertragung; unabhängige Serverbelege bleiben erforderlich. JVM-Regressionsprüfung und Build/Lint bestanden; eigener Android-Provider-Positiv-/Negativfall im abschließenden Lauf.
- Keine sieben Altentscheidungen ausgeführt, da die vollständige reale Sicherung im installierten alten Build weiterhin an Legacy-Fotoreferenzen scheitert. Neues Originalupdate und realer Connector-/Legacy-Backup-Endtest bleiben offen.

## Eintrag 45 – finale native Abnahme und unabhängige Live-Änderung (19.10.2026)

- Originalsignierter Stand mit kompatiblen alten Transferbelegen: **133 native Android-/SQLite-/MCP-/Providerproben, 0 Fehler**, anschließend vorhandener synthetischer Updatebestand vollständig erhalten, echte WebView-Navigation und frischer getrennter Android-Datenbereich bestanden. Keine Pixel-Installation und kein Pixel-ADB.
- Letzter realer Readback änderte den Ausgang unerwartet von sieben auf **sechs aktive Legacy-Reviews**. Historie 12→15. Für `id_sha256_ca46d340bc52b9e93e578b4a47c1afa08b11c87a013e5c9dee7cd7624bc12824` enthält der Dienst `apply_proposed`, `resolved_at=2026-10-19T10:33:04.591674Z`, Resolution-Aktion `mcp_owner_ad17a4d7e84b3a8e5bcad2b615ea55ec5e80b83ec552f940b78df49036755ffc`. Diese Aktion wurde in diesem Auftrag nicht aufgerufen; hier wurde ausschließlich der bekannte Testentwurf archiviert. Keine Zuschreibung an eine Person und keine Umschreibung dieser Historie.
- Neuer Preview: `e6752c0dc5a04bc3f729caccdbc3187d87327f6adc0792ccc7f7f722b0a51c8b`, Count 6. Die ursprüngliche Siebener-Vorschau ist ungültig; **keine Sammelentscheidung und kein weiterer Legacy-Schreibaufruf**. Geschäftsgruppenhashes außerhalb der erlaubten Angebotsbereinigung bleiben im Vergleich identisch. Kein Nachweis unabhängiger Google-Cloud-Bestätigung.
- Damit eine künftig ausdrücklich neu bestätigte Sechser-Auswahl nicht an einer künstlichen Siebener-Konstante hängen bleibt, Backup-Policy auf 1–100 vollständig historische aktive Einträge mit exakt passendem Queue-Umfang begrenzt. Für eine von sieben abweichende Anzahl zwingend aktueller Previewhash; alte Siebener-Hashes und zusätzliche technische/neue Aktionen bleiben gesperrt. Frischer Preview, Backup, Hashprüfung und `confirmed=true` für den Abschluss bleiben unverändert Pflicht. Host-Regressionsfall: 7→6 mit altem Hash FAIL, ohne frischen Hash FAIL, mit neuem Hash PASS, zusätzliche Aktion FAIL.
- Diese Policy-Änderung führt keine tatsächliche Entscheidung aus und autorisiert keine automatische Anpassung der alten Sammelfreigabe. Realer Backup-/Cleanup-Endtest nach gesichertem Geräteupdate weiterhin offen.

## Eintrag 46 – finaler Build 79 und unveränderte Installationssperre (19.10.2026)

- Finaler Release erhält **1.51.0 / 79**. Build 78 bleibt interner, nicht freigegebener Prüfkandidat; nach der zusätzlich notwendigen dynamischen Legacy-Auswahl wird dessen Versionsnummer nicht für die endgültige Auslieferung wiederverwendet. Originalpaket und Signer unverändert. Aktuell tatsächlich verbundener Pixel bleibt 1.50.1/77, Protokoll8/111 Tools.
- Für einen weiteren frischen Abnahmedatenbereich wird ein neuer isolierter Context des Originalcodes benutzt; kein vorhandenes Android-Testprofil oder App-Datenbestand wird gelöscht. Bereits zwei neu angelegte Betriebssystem-Testprofile sowie vorhandene Upgrade-Daten bleiben erhalten. Kein neues APK-Paket und keine zweite App-Codebasis.
- Die erstmals beobachtete, nicht durch diesen Auftrag aufgerufene Apply-proposed-Entscheidung betraf einen schon in der ursprünglichen Vorschau nicht vorhandenen Datensatz (`current_hash=SHA256(null)`). Die bestehenden Geschäftsgruppen außer dem ausdrücklich archivierten Fehl-Testentwurf blieben hashgleich. Die Audit-Historie wird nicht umgeschrieben. Sechs Reviews sind der neue beobachtete Zustand; keine automatische Übernahme der alten Siebener-Freigabe.
- Geschütztes reales Update und unabhängige Sicherung/Originalfoto-Prüfung bleiben Voraussetzung für den echten Connector-/Cleanup-Endtest. Keine Installation auf dem physischen Pixel erfolgt.

## Eintrag 47 – endgültige Original79 qualifiziert und Auslieferung konsolidiert (19.10.2026)

- Finaler APK-Quellcommit `5ae296394b5a9103f94421ea04ba01e55d06f8f0`, **1.51.0/79**, Paket `at.hausgarten.service`, Quellhash `b8b68448d1b6971c8cde6ebd7a00c7ccd88ecb6cf15c9a7b0065a9962c05f4da`, source_dirty=false, release. Späterer Liefercommit ergänzt ausschließlich APK und zentrale Dokumentation; dessen SHA aus `git log -1`, nicht mit eingebettetem APK-Commit verwechseln.
- Endgültige79: **133 native Android-/SQLite-/MCP-/Providerproben,0 Fehler**, anschließend In-place-Update mit erhaltenen synthetischen IDs/Beziehungen/Queue/Einstellungen/Tunnel/aktivem Timer, native WebView und frischer isolierter Start bestanden. Erneut24 JVM-Suiten/273 Python-Tests erfolgreich;28 Browser-Suiten der unveränderten betroffenen UI-Implementierung erfolgreich. Neue Estimate-Verträge121, Paritätsprüfungen1032, Papierkorb-/Backup-Policy173. Gradle Release/Instrumentation/Lint erfolgreich,33 nichtfatale Hinweise ausdrücklich verbleibend.
- APK **34.504.813Bytes**, SHA256 `b06798341ac929d00145f566db26d15377c3472b676e4d96b275a677291cc891`; bestehendes Zertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`, ein Signer, v2/v3,16-KiB-Ausrichtung und411 bytegleiche Assets geprüft. Privacy Source/Artefakt PASS, keine Betriebs-Seeds. Ausschließlich Originalsignierwerkzeug, keine neue Signatur. Signier-Tempverzeichnisse0/getrackte Schlüssel0.27 eigene alte interne78-APKs entfernt; alte Originaldownloads, automatisierte Tests und vorhandene Emulatorprofile erhalten.
- Finalen privaten Download im vorhandenen Buildordner unter derselben eigenen Kandidaten-ID `14t3aDwlKqPZkPWNH2qYRg8pXbdlr0H3v` auf79 aktualisiert. ID/Name/MIME/Eltern/Größe/Owner-only und vollständige Rohdateireferenz34.504.813Bytes rückgelesen; keine öffentliche Freigabe/Inline-Binärdaten und kein unabhängiger Remote-SHA behauptet. Downloads/Prüfsumme und bestehende zentrale Dokumentation aktualisiert; sämtliche Review-Fälle in Abschnitt31 des Auftrags eindeutig eingeordnet.
- Quellkatalog **117/Protokoll9**, Hash `235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314`. UI **156**, davon145 fachliche MCP-Pfade und elf explizite Plattform-/Darstellungsgruppen, vollständig100+56, Hash `34c9cdd7494658bb1a3ec0a71db267e1ffa1044fd31e6a735c5be2ce0c8a0d91`. Neuer Katalog/Fingerprint im Originalcode geprüft, nicht auf dem unveränderten physischen Pixel behauptet.
- Reales abschließendes Readback19.10.2026 **11:03:55UTC**: Pixel **1.50.1/77**,111Tools/Protokoll8, ready/write_enabled=true, persistierte Drive-Berechtigung, kein technischer Syncfehler, **0 Uploads/6 Reviews/15 Historie**, Provider-Readback/cloud_confirmed=false. Vorschau weiterhin sechs/e6752c0d… . Kalender nicht verbunden/Wetter nicht eingerichtet. Einzige reale Änderung dieses Auftrags bleibt die ausdrücklich erlaubte reversible Archivierung des falschen1-EUR-Testentwurfs; außer Angebotsgruppe alle Geschäftsgruppenhashes unverändert. Keine Legacy-Abschlussoperation durch diesen Auftrag, keine echte Mail, kein neues/finalisiertes/erneut versendetes echtes Angebot.
- **Gesamtauftrag nicht vollständig abgenommen:** unabhängige Gerätesicherung und ausdrückliche Installationsfreigabe, physischer Pixel-Update-/Datenerhalt-/Kamera-/Connector-Test, tatsächlicher117er-Katalog nach Refresh, reale Legacy-Originalsicherung und frisch bestätigter Sechser-Keep-current-Abschluss, echte Gmail-Konto-/API-Freigabe/Versandreadback sowie Kalender-/Wetter-/Hintergrundverhalten bleiben offen. Die ursprüngliche Siebener-Freigabe gilt nicht automatisch für veränderte Hashes. Keine Sicherungs-/Konfliktschutzprüfung entfernt und kein Cloud-Abschluss erfunden.


## Eintrag 48 – P0-Datentrennung / Bestandsprüfung (19.10.2026)

- main per Fast-forward auf aa04cbd aktualisiert; keine vorhandenen Änderungen, neue Branch oder Historienänderung. Regeln/Auftrag/Status gelesen.
- Reproduziert: export_mobile_snapshot schreibt Betriebsdaten ins Android-Bootstrap und outputs; db.py fällt auf Repository-data zurück; Geräte-Backup-/Update- und Audithelfer schreiben Rohdaten oder rohe Beziehungen in den Checkout. Vorhandenes Privacy-Gate liest optional eine produktive DB und prüft ignorierte Dateien nicht.
- Read-only Workspaceinventar ohne Werteausgabe: keine Geschäfts-SQLite mit betrieblichen Tabellen gefunden; ältere ausschließlich synthetische Browserprofile enthalten allgemeine Chromium-DBs. Ein synthetisches Kunden-/Auftragsfixture vorhanden. Kein Produktivbestand kopiert, migriert oder verändert.
- Abnahme: fehlender/interner/symlink-umgeleiteter Datenroot blockiert vor Dateizugriff; Bootstrap exakt leer; Exporte/Backups nur extern; zentrale synthetische Profile und temporäre Teststores; drei verpflichtende Gates einschließlich ignored/untracked und Archivprüfung; technische Reports ohne Geschäftsinhalte. P0 bleibt bis zum vollständigen Schlusslauf offen.


## Eintrag 49 – P0-Datentrennung / Pfade, Fixtures und Gates implementiert (19.10.2026)

- Host-Pfade zentral über lazy runtime_paths; fehlender/interner/relativer/Symlink-Root und QA-Zugriff auf fremde/private Stores fail-closed. Tests registrieren ausschließlich neu angelegte eigene Tempstores, unbekannte nichtleere Ordner werden nicht umgewidmet. Original-Pixel-/Drive-Adapter und IDs/Formate unverändert. Kein privater Root für reale Daten konfiguriert oder produktiver Bestand migriert.
- Snapshotexport read-only nur extern/exklusiv, kein db.init/Thumbnail-/Assetschreiben. Leerer Bootstrap mit eigenem konstanten Generator/Vertrag. DB, Bilder, Drive-/MCP-Hostkanäle, historische Importe, Backup-/Update-/Audithelfer auf externe Pfadprüfung umgestellt; technische Audit-/Vergleichsberichte nur Counts/Hashes/Schema/Relationsinformation. Reale Aussteller-Testwerte und personenbezogener Angebotsname aus aktuellen Quellen entfernt, historische Angaben als technische Nachweise erhalten.
- Zentrale JSON-Factory minimal/edge_cases/scale, Python-Tempadapter, Browseradapter und generierte JVM-/AndroidTest-Adapter. Test-Eingabe-IDs in QA-Namespace überführt, example.invalid und künstliche Aussteller-/Adressdaten. Fachliche Legacyformat-/IDgenerator-Proben bleiben künstliche isolierte Vertragsfälle; keine produktiven IDs als Fixtures. 25 nachweislich lokale synthetische Browserprofile und38 entbehrliche alte QA-Screenshots entfernt; keine Originalmedien oder Betriebsdateien angefasst.
- Erster Source-/Workspace-/Artifact-Schlusslauf PASS: gesamter Checkout einschließlich ignorierter/untracked Dateien, umbenannte SQLite-Dateien, Archive und bestehende79-APK (0 operative Seeds). Dokumentierte synthetische Bildnachweise erhalten Bytehash/Fixture-Herkunft; unbekannte Bilder bleiben gesperrt. Gates zusätzlich vor/nach Quality-Suite und im Releaseprozess verankert.
- Neue negative Privacy-/Pfad-/Export-/Reportregressionen;288 Python-Tests zuletzt PASS vor abschließender Gate-Verschärfung. JVM-Migrationsläufe fanden nur inkonsistente alte Fixture-Referenzen/Enum- bzw. HTML-ID-Ersetzungen; zurück auf unveränderte fachliche/DOM-Verträge korrigiert. Letzter kompletter24-JVM-Lauf PASS. Browser-Gesamtregression läuft erneut; ein alter GPS-Test musste nach Adress-Anonymisierung konsistent auf dieselbe künstliche Straße umgestellt werden. Kein App-Fehler daraus behauptet. P0 bleibt bis finalen vollständigen Tests/Gates OFFEN.

## Eintrag 50 – P0-Regressionsphase und native Umgebungsgrenze (19.10.2026)

- Nach Fixture-Migration alle24 JVM-Suiten und alle29 Browser-Suiten PASS.18 gezielte Privacy-/Pfadfälle einschließlich unbekanntem nichtleerem Store, Rootrechten, umbenannter SQLite/JSON, Archivfoto, Reportdaten und Source-Writer-Prüfung Bestandteil der abschließenden Python-Abnahme.
- Zusätzliche Pfadnachprüfung: historischer physischer Native-Diagnosehelfer schreibt ausschließlich unter privaten externen Root; alter verify.py verwendet jetzt denselben read-only Hash-/Counts-Audit statt Initialisierung und festen Umsatz-/Importwerten. Source-Pakethelfer übernimmt geprüften Originalcode ohne anonymisierte Ersatzruntime; alle drei Gates vor Freigabe der tatsächlichen ZIP-Bytes.
- Native Android-Abnahme derzeit NOT TESTABLE: /dev/kvm fehlt, Emulator meldet fehlende CPU-Beschleunigung. Begrenzter Softwarestartversuch läuft; kein physischer Pixel berührt, keine App-Daten migriert. Bisherige133 Android-Proben gehören ausdrücklich zum historischen79-Nachweis, nicht zur aktuellen P0-Abnahme.
- Historienprüfung rein lesend:8 unterschiedliche bekannte Bootstrap-/Ausstellerobjekte geprüft,4 sensible frühere Aussteller-Fixture-Befunde. Kein befülltes historisches Bootstrap in diesem begrenzten Prüfumfang. Klassifikation: produktive sensible Daten in Historie gefunden; keine vollständige forensische Historienprüfung behauptet und keine Historie umgeschrieben. Separate berechtigte Historienbereinigung bleibt offen.

## Eintrag 51 – P0-Artefaktbefunde überprüft (19.10.2026)

- Release-Gate stoppte fünf ignorierte alte AndroidTest-APKs: deren DEX enthielt frühere feste synthetische Tunnel-/Organisations-IDs. Vor Entfernung jeweils tatsächlich Paketkennung at.hausgarten.service.test und Übereinstimmung sämtlicher Treffer mit alten Testquell-Literalen geprüft; untracked, ausschließlich abgeleitete Testartefakte entfernt. Operative APK-Downloads, Kundenmedien und Emulator-Datenbereiche erhalten. Neue Testverbindungen werden erst zur Laufzeit deterministisch erzeugt und nicht als opake Werte eingebettet.
- Öffentliche Bildausnahmen auf SHA-geprüfte Markenbilder und explizite lizenzierte Pflanzenkataloge begrenzt; ein beliebiges neu eingelegtes Bild erhält keine Freigabe durch den Ordnernamen. Unklassifizierte Bilder und Archivmedien bleiben FAIL.
- Software-Emulator ohne Beschleunigung startet den Android-Boot in dieser Sitzung nach begrenztem Versuch nicht vollständig; eigener Prozess beendet, vorhandenes AVD nicht gelöscht. Aktuelle native Abnahme weiter NOT TESTABLE. Erste Python-Gesamtprobe fand eine noch alte künstliche Rechnungsnummernerwartung und eine übergreifend gemockte Git-Toolfunktion im Signer-Unit-Test; Testgrenzen/erwartete synthetische Nummer korrigiert, fachliche Rechnungs-/Signaturlogik unverändert. Abschließende Regression erneut gestartet.


## Eintrag 52 – P0-Build-/Hostabnahme (19.10.2026)

- Release-APK, Release-AndroidTest, AAB und lintRelease tatsächlich erfolgreich gebaut (94 Tasks, Gradle5m03s). Source-/Workspace-/APK-/AAB-Gates PASS;0 operative APK-Seeds.19 gezielte Privacy-Fälle PASS, zuletzt291 Python-Tests vor zusätzlichem verschachteltem Drive-Symlink-Fall PASS; abschließender292er-Quality-Lauf folgt.24 JVM-/29 Browser-Suiten und55 isolierte MCP-stdio-E2E-Szenarien PASS. Keine QA-Ersatzsignierung ausgeführt.
- Externe Pfadprüfung auch für verschachtelte Medien-/Dokument-/Thumbnail-/Tempdateien und SQLite-Readbacks; Repository-Symlink vor Export blockiert. Neue Regression PASS. Bootstrap-Writer-Gate umfasst direkte/aliasierte Writes, Copy/Move/Replace und bekannte Shell-/JS-/PowerShell-Schreibpfade. Leere ältere APK-Bootstraps bleiben kompatibel, unbekannte Felder/Versionswerte sind blockiert.
- Kandidat1.51.1/80, unveränderte App-ID at.hausgarten.service, MCP9/117. APK-Fingerprint kennzeichnet den tatsächlichen Arbeitsstand aa04cbd/source_dirty=true; kein behaupteter sauberer Releasecommit und keine Auslieferungs-/Installationsfreigabe. Der bisher qualifizierte Download79 bleibt unverändert. Originalsignaturprüfung dieses P0-Kandidaten läuft ausschließlich über tools/sign_original_apk.py.
- Vollständige P0-Abnahme weiter OFFEN: Vertrags-/Schema-/Relations-/Buildprüfungen erfolgreich, neue native Android-Laufzeit-/Update-Abnahme dieser migrierten Tests wegen fehlender Cloud-Beschleunigung NOT TESTABLE. Physischer Pixel und produktiver Drive unberührt. Historische sensible Git-Objekte separat offen; keine Historie umgeschrieben.


## Eintrag 53 – P0-Schlusslauf ohne produktive Daten (19.10.2026)

- Quality-Suite tatsächlich294 Tests PASS,0 Fehler/Skips, darunter21 Datenschutz-/Pfadfälle.37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen, damit keine Ersatz-/QA-Schlüssel entstehen; Originalsignierungsregressionen enthalten. Vorher-/Nachher-Source und vollständiger Workspace PASS (1126 Quellen,3011 Workspace-Dateien einschließlich ignored/untracked), tatsächliche originalsignierte APK PASS,0 operative Seeds. Einzelne JSON-Datensätze und bekannte private Literale auch in DEX/Klassen/Ressourcen werden geprüft.
- Source-Review-ZIP aus1114 aktuellen Originaldateien tatsächlich erzeugt und byteweise Artifact-Gate PASS, SHA256 fec1b8728934d30277450f19ac5bc1d959164a46f6b6dc61c1e6fe8cf4fcfc11. Nur vorübergehendes QA-Artefakt außerhalb des Checkouts; keine Ersatzruntime und kein zusätzliches Dossier. APK/AAB und Sourcepaket-Gates damit jeweils tatsächlich ausgeführt.
- Originalkandidat1.51.1/80, APK-SHA256 d8208fd3c5b42e52688778195ce7d276cde09ddaf2416c783003de6b1bf133aa; Zertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8 identisch mit bestehender79. Signatur v2/v3 und16-KiB-Alignment überprüft,411 aktuelle Assets bytegleich, Signier-Tempverzeichnisse0. Originalsignierwerkzeug allein verwendet. Keine Pixel-Installation oder Produktivdatenaktion.
- Zweiter begrenzter Software-Emulatorversuch: system_server und Bootanimation laufen, bislang0 beobachtete Java-/native Crashmarker; Bootabschluss noch nicht bestätigt. Native Abnahme weiter offen, keine alte133er-Probe als P0-Nachweis wiederverwendet. Temporärer bestehender AndroidTest-Runner wird für einen möglichen reinen Cloud-Emulatorlauf ausschließlich originalsigniert; keine neue operative App oder QA-Signatur.


## Eintrag 54 – P0 echtes Cloud-Emulatorupdate, Datenvergleich nur technisch (19.10.2026)

- Software-Emulator schließlich vollständig gestartet; frühere NOT-TESTABLE-Startbefunde durch diesen tatsächlichen Nachweis überholt. QEMU=1, exakt AVD GartenpiratCloud, expliziter emulator-5582 und exklusives Installations-/Testlock geprüft. Kein physisches Gerät.
- Vorhandene operative QA-App tatsächlich1.50.1/77, nicht ungeprüft79 angenommen. Vor Update ausschließlich eindeutig künstliche QA-Kunden im vorhandenen Emulatorstore geprüft; Original-AndroidTest-Runner mit Originalzertifikat installiert. Danach ausschließlich normales Originalpaket-Update -r auf1.51.1/80, keine Deinstallation/Datenspeicherlöschung und kein neues operatives Paket.
- Business-Readbacks vor/nach Update nur im Prozessspeicher ausgewertet, keine Base64-/Rohdaten gespeichert oder ausgegeben. Geschäftsmaster ohne technische capabilities und gesamte Pending-Queue kanonisch SHA256 be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af unverändert.3 künstliche Kunden/1 Auftrag/2 Arbeitsaufzeichnungen/1 Pending-Aktion erhalten,0 Fotos/Rechnungen/Angebote;32 öffentliche Bausteine und0 Termine unverändert. IDs/Beziehungen/sonstige Masterfelder durch identischen Ledgerhash mitgeschützt.
- Vollständige native Android-SQLite-/MCP-/Provider-Abnahme jetzt auf diesem originalsignierten80er-Build gestartet; Ergebnis folgt separat. Keine physische Pixel-Kompatibilität oder echte Google-Providerfreigabe behauptet.


## Eintrag 55 – P0 native Fixture-Abnahme nach echter Ausführung korrigiert (19.10.2026)

- Erster neuer vollständiger Android-Lauf:128 PASS/5 FAIL. Ursache ausschließlich migrierter Testprovider: Datei-IDs wurden zu qa_docN geändert, drei Referenzen blieben doc1; drei Schreibmodusprüfungen wurden versehentlich von w auf qa_w umbenannt. Dadurch falsch simulierte Providerfehler und fehlende Testdatei. Alle Referenzen konsistent, echte Android-Dateimodi wieder unverändert; keine operative Drive-Logik verändert.
- Native WebView-Probe sucht eindeutig benannte QA-Einsatzort-/Pflege-Datensätze statt eine Änderung erhaltener älterer synthetischer IDs zu verlangen. Keine bestehenden Datensätze für einen passenden Test überschrieben. AndroidTest-Neubuild mit Pflichtgates erfolgreich; erneute Originalsignierung/native Gesamtabnahme läuft.
- Zusätzliche Workspace-Regressionsprüfung: nur die tatsächliche .git-Historie des Checkoutroots ist separat; verschachtelte Ordner mit Namen .git können keine SQLite-Dateien verstecken.22 gezielte Privacy-Fälle PASS, neuer Python-Gesamtlauf295 Tests PASS vor abschließendem Nachher-Gate; endgültiger Gatebeleg folgt.


## Eintrag 56 – P0 native133 bestanden, vollständige QA-Backupisolation nachgeschärft (19.10.2026)

- Wiederholung nach konsistenten synthetischen Dokumentreferenzen und unveränderten echten Dateimodi:133 native Android-SQLite-/MCP-/Providerproben PASS,0 Fehler. Root-Drive-Snapshot vor/nach Proben unverändert. Enthalten Originalfoto/Rotation/21 Uploads, Offers/Estimates/PDF/Restart/Journal, Provider-Retry/Readback und sieben vollständig synthetische Legacyfälle; keine tatsächlichen Pixel-/Drive-Altaktionen.
- Zusätzliche Isolationanalyse: der vorhandene native QA-Kontext überschreibt Dateien/Cache/Prefs/DB, leitete getNoBackupFilesDir aber noch an den Basiscontext weiter. Nun eigener QA-Backupordner und neue native Pfadprüfung für alle vier Speicherarten. Operative App-Backupimplementierung unverändert. Letzter AndroidTest-Neubuild/134er-Lauf folgt; bis dahin kein P0-Abschluss.
- Originalsigner-Privacyfehler darf nur seine eigene unveränderte Ausgabedatei entfernen. Ein nachträglich fremd ersetztes Ziel bleibt erhalten; tatsächlicher Artefakt-SHA muss zum signierten Resultat passen.42 synthetische Signer-Tests PASS, darunter Gate-Rejection ohne Fremdverlust. Keine zusätzliche Schlüsselidentität erzeugt.
- Letzter vollständiger296er-Quality-Lauf mit22 Privacy-/Pfadfällen läuft. Native WebView-/Fresh-Proben ebenfalls gestartet. Frühere133er-PASS-Angabe ist tatsächlicher neuer P0-Lauf; die zusätzliche134. Speicherisolation wird ausdrücklich noch nicht als bestanden behauptet.


## Eintrag 57 – P0 WebView/Fresh und Bootstrap-Erhaltsschutz (19.10.2026)

- Tatsächliche native WebView-Probe auf80 PASS: kompatibles JavaScript, Navigation, Auswahl/Abbruch, Kunden-Garten, Papierkorb und auftragsbezogener Terminpfad. Eindeutige QA-Datensätze dynamisch gefunden, ursprüngliche IDs erhalten. Anschließend fresh_isolated PASS:0 operative Startdaten, keine Credentials, kein automatischer Drive-/Tunnelzugriff.
- Backupisolations-Erweiterung kompiliert und ausschließlich originalsigniert, zusätzliche134. Pfadprobe im nun gestarteten Gesamtlauf. Produktiver App-Code und80er-APK gegenüber der erfolgreichen133er-/Update-/UI-Probe unverändert.
- Konstanter Bootstrapgenerator verweigert jetzt zusätzlich Symlinks und vorhandenen nichtleeren/unbekannten Inhalt. Er kann einen versehentlich befüllten Seed nicht still durch Leerinhalt ersetzen; Bestand bleibt zur Prüfung erhalten.23 gezielte Privacy-Tests PASS,42 Signer-Tests PASS, abschließender297er-Gesamtlauf gestartet.


## Eintrag 58 – P0 vollständig im festgelegten Abnahmeumfang bestanden (19.10.2026)

- P0.13-Tabelle vollständig14/14 PASS.297 Python-Tests,0 Fehler/Skips, darunter23 Privacy-/Pfad-/Bootstrapfälle und42 Originalsignierungsregressionen;37 historische QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen.24 JVM- und29 Browser-Suiten sowie55 synthetische MCP-stdio-E2E-Szenarien bestanden. Keine Testdaten von realen Kunden verwendet.
- Endgültiger originalsignierter80er-Stand:134 Android-SQLite-/MCP-/Providerproben,0 Fehler. Neue Speicherprobe bestätigt eigene QA-Pfade für Datenbank, Medien, Cache und NoBackup. Danach erneut native WebView-Navigation und fresh_isolated PASS. Vollständiger Schlussreadback nur im RAM: Geschäftsmaster ohne capabilities plus gesamte Pending-Queue weiterhin SHA256 be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af. Alle synthetischen IDs/Relationen des geprüften Geschäftsmaster und1 ausstehende QA-Aktion nach Update und sämtlichen Proben erhalten; keine Rohdatensätze/Base64 gespeichert.
- Source und Workspace vor/nach letzter Quality-Suite PASS; Workspace3032 Dateien einschließlich ignored/untracked (Dateizahl variiert durch ausschließlich geprüfte Build-/Testartefakte). Source1126 Dateien; untergeordnete .git-Namensverzeichnisse eingeschlossen, tatsächliche Git-Objekte separat. Tatsächliche originalsignierte APK, AAB und Source-ZIP jeweils Artifact-Gate PASS; operative APK-Seeds exakt0. Pflichtgates im normalen Quality-/Build-/Signier-/Sourcepaketweg.
- Release/AndroidTest/AAB/Lint gebaut;0 Lintfehler/33 bestehende Hinweise. App1.51.1/80, Paket at.hausgarten.service, MCP9/117 unverändert. Produktions-App-Quellhash d329f9714af8ab6370e76a5a0d358cff79c0f7618feb5a8635a826f1af626ebd passt zum endgültigen App-Quellinhalt; APK-Fingerprint dokumentiert korrekt den Build aus aa04cbd mit damaligem source_dirty=true. Kein behaupteter anderer eingebetteter Commit. Bisheriger qualifizierter Download79 bleibt unverändert; neuer80er-P0-Kandidat ist lokal qualifiziert, nicht auf Pixel installiert/veröffentlicht.
- Original80-APK-SHA d8208fd3c5b42e52688778195ce7d276cde09ddaf2416c783003de6b1bf133aa; Originalzertifikat a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8 identisch mit79, ein Signer/v2/v3/16-KiB-Alignment,411 Assets bytegleich. Sole Signierweg tools/sign_original_apk.py, kein Ersatz-/QA-Schlüssel. Vorhandene nichtleere Bootstrapinhalte und fremd ersetzte Signerausgaben bleiben erhalten; Gatefehler blockieren statt blind zu löschen.
- Historienprüfung nach Implementierungscommit erneut nur lesend:12 unterschiedliche bekannte Bootstrap-/Ausstellerobjekte,4 sensible ältere Aussteller-Fixture-Befunde,0 nichtleere historische Bootstraps in diesem begrenzten Umfang. Klassifikation produktive sensible Daten in Historie gefunden. Kein vollständiger forensischer Audit und kein History-Rewrite; separater Sicherheitsauftrag bleibt erforderlich. Das ist ausdrücklich getrennt vom bestandenen aktuellen Checkout-/Build-P0 gemäß P0.11.
- Physischer Pixel, produktiver Drive, echte Kalender-/Gmail-/Tunnelkonten und tatsächlicher ChatGPT-Connector wurden in diesem P0-Auftrag nicht verwendet oder aktualisiert. Reale Legacyaktionen unverändert. Der Emulatornachweis ersetzt keinen physischen Update-/Datenerhalt-/Provider-Test; frühere offene Funktionsabnahmen bleiben bestehen. Keine produktive Datenmigration durchgeführt; HG_PRIVATE_DATA_ROOT für echte Hostdaten absichtlich nicht eingerichtet.


## Eintrag 59 – P0 Cleanup und Übergabe (19.10.2026)

- Eigenen Software-Emulator nach134er-/WebView-/Fresh- und erneut hashgleichem Ledger-Schlussreadback normal beendet; vorhandenes AVD samt synthetischen Daten nicht gelöscht. Eigenes temporäres Source-ZIP und drei eigene signierte Zwischen-Test-APKs nur nach Übereinstimmung ihrer bekannten SHA entfernt; bestehende operative Downloads und automatisierte Tests erhalten.
- Signier-Tempverzeichnisse0, getrackte Keystore-/Privatschlüsseldateien0. Kein privater Root eingerichtet, keine produktiven Dateien migriert, kopiert oder gelöscht. Aktuelle Betriebssystem-/Build-/Testartefakte im Checkout durch Workspace-Gate geprüft; reine Code-/synthetische Artefakte zulässig. Letzter Checkout-Gatelauf bestätigt Source/Workspace/Signed-APK PASS; abschließender normaler Abnahmecommit und Remote-Abgleich folgen ohne Historyänderung.

## P1 – R0 Baseline und Charakterisierung (19.10.2026)

- Ausgang main `1abf049c1e27167bc16259d8717513aeb5ffe670`, sauber per Fast-forward; nur neuer P1-Auftrag seit P0. Quelle1.51.1/80, App-ID unverändert, MCP9. Keine Betriebsdaten und kein privater Hostroot verwendet.
- Analyse: MainActivity→LocalStore→PixelOwnerAccess→MainActivity.LocalStore ist eine unerwünschte konkrete Kopplung; PixelOwnerAccess mischt SQL-Dateitransfer, Use Cases, Provider und Transportbelege. MCP-Reader mischt Filter/Projektion mit RPC; OwnerTools mischt Schema/Registrierung und Konfliktabfragen. UI-Module duplizieren synchrone ownerCall-/Fehleradapter. Domainregeln und ursprüngliche Journale bleiben maßgeblich, keine neue Datenquelle.
- Ausgangsmetriken (statische Quellmessung, keine Laufzeitabdeckung): OwnerAccess3 Switches/58Cases/38 Projektabhängigkeiten/8SQL-Aufrufe; ReadTools2/32/14/0; OwnerTools2/66/7/0; MainActivity2/10/43/30,117635Bytes. Größter Hotspot MainActivity1255Zeilen. Nicht nur Zeilenzahlen: Ziel sind getrennte Abhängigkeitsrichtungen und atomare bestehende Storegrenzen.
- Neue Charakterisierung:23 eingefrorene synthetische Lese-/Fehler-/Write-off-Antworten, unveränderte Eingabe. Alle bisherigen CAS/Replay/Referenz-/Provider-/Isolationstests bleiben erhalten. Maschinenlesbare unveränderliche Baseline `tests/fixtures/refactoring-baseline.json`:117Tools,52Read/65Write, vollständige Schemas/Annotationen,156UI-Aktionen,12 Domain-/Formatquellhashes, Bootstrap/Offervertrag und Antworten. Gesamtvertrag SHA256 `70130d3fb75eda3d425d9d5a4f982e61e47b3cbcff421723b243baaa959f1820`. Java-Runner erzwingt nach jedem Lauf den Vergleich.
- Bisher PASS:24 bestehende JVM-Suiten plus neue Charakterisierung,29Browser-Suiten,55MCP-stdio-Szenarien. Vollständige Python-/Privacy-Baseline läuft noch; **R0 noch nicht abgeschlossen, kein struktureller Umbau begonnen**.

### R0 abgeschlossen

- PASS297Python/0Skips,25JVM einschließlich Charakterisierung,29Browser,55MCP; zusätzlich3Negativtests des neuen Vertragsvergleichers PASS.37 Schlüssel erzeugende QA-Tests wie P0 ausdrücklich ausgeschlossen. Source/Workspace vor und nach Quality sowie tatsächliche Original80-APK Artifact PASS,0Seeds.
- P0-Gate wies zunächst zwei eigene neue QA-Antwortdateien ohne durchgängige Testmarkierungen ab. Diese ausschließlich synthetischen Nachweise auf kanonische Antwort-Hashes umgestellt, keine Gateausnahme. Der erneute komplette Lauf bestand. Noch keine strukturelle Änderung.
- Endgültige Vorher-Baseline zusätzlich57öffentliche JavaScript-Bridge-Signaturen und5SQLite-DDL-Anweisungen, Gesamtvertrag SHA256 `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`. Die zuvor genannten Gesamt-Hashes sind historische R0-Zwischenerfassungen, keine aktuelle Referenz. Baseline wird nach Beginn des Refactorings nicht neu aufgenommen.
- Nächste Phase R1: reine Snapshotabfragen, Owner-Schemabuilder und zentrale kompatible Fehlerabbildung. Original80 bleibt native Vorher-APK; Software-Emulatorstart läuft noch.

## P1 – R1 Contracts/Routing abgeschlossen (19.10.2026)

- Reine Snapshot-Auswahl/Binärredaktion in SnapshotReadSupport; fünf domänenspezifische Queryhandler in BusinessQueries, kein Android/SQL/Provider. Reader koordiniert weiterhin Validierung, Autorisierung und unveränderte Antwortdekoration. OwnerToolSchemas verwendet registrierte Feldbuilder statt globalem Feldswitch; McpFields ist gemeinsame schemaabhängigkeitsfreie Quelle für Owner/Offer/Planning/Entity-Tools. Keine Enum-/Schemaänderung. McpErrors vereinheitlicht intern die Abbildung, bewahrt bestehende unterschiedliche RPC-/Bridgecodes und begrenzte Provider-Retry-Metadaten.
- PASS26JVM-Suiten (neue14Fehler-/Redaktionsvergleiche),55MCP-Szenarien, unveränderter Gesamtvertrag `899906c2…aa813`. Source/Workspace und bestehende Original80-APK erneut PASS,0Seeds. Native Ausgangsabnahme noch offen: Software-Android meldete Bootproperty vor erreichbarem ActivityManager; erster Aufruf ausdrücklich fehlgeschlagen, kein Teilerfolg behauptet. Kein physisches Gerät.
- Messung: Reader2→1Switches,32→14Cases,31279→20263Bytes; OwnerTools2→1Switches,66→28Cases,25283→14781Bytes. Fachliche Queryzweige existieren einmal je Domäne, Schemafelder einmal je Builder; keine parallele Runtime. UI- und Domain-/Datenformatverträge unverändert.
- Nächster Schritt R2: Use Cases nacheinander hinter einen Android-/SQL-freien Repositoryvertrag stellen; nach jeder Domäne vollständige JVM-/Vertragsregression.

- R2 Teilabnahme Offer: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Work: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Photo: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme SyncArchive: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Settings: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Invoice: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

- R2 Teilabnahme Planning: vollständige26JVM-Suiten und eingefrorener P1-Vertragsvergleich PASS; Originalalgorithmen hinter OwnerRepository, keine Betriebsdaten.

## P1 – R2 Application Services abgeschlossen (19.10.2026)

- Alle sieben Ownerdomänen einzeln herausgelöst, jeweils komplette26JVM-/Vertragsregression danach PASS (Teilabnahmen oben). EntityApplicationService/EntityContract trennen zusätzlich Kunden-/Auftrags-/Rechnungsentwurfsabläufe von MCP-Schemata; nach dieser Domäne ebenfalls vollständige Regression PASS. Gemeinsame OwnerCommand-/OwnerRepository-Ports enthalten keine Android-/SQL-/UI-Typen. Router hängt von den Ports ab; Services hängen nicht vom Router ab. Atomare SQLite-/Replay-Grenze unverändert in nativer Implementierung.
- Schlussprüfung PASS27JVM-Suiten einschließlich79 direkter Service-/Port-/CAS-/Referenz-/Datei-/Konto-/Backup-/Seiteneffektprüfungen,55MCP-E2E; Vertrag weiterhin exakt `899906c2…aa813`. Erster neuer Service-Test übergab berechnete/stored Felder als MCP-Eingabe und wurde korrekt abgewiesen; Fixture auf erlaubte Eingabefelder begrenzt, kein Schema oder Schutz geändert.
- Gradle Release/AndroidTest PASS; Source/Workspace und tatsächliche neue unsigned Zwischen-APK Artifact PASS,0Seeds. Nicht signiert/ausgeliefert/installiert. Bestehende Original80 unverändert. OwnerAccess61731→34720Bytes,58→21Cases,38→29Projektabhängigkeiten; fachliche Kommandos hinter testbarem Effektport statt im nativen Dispatch.
- Zusätzliche native Vorher-Abnahme jetzt tatsächlich134/0 PASS auf unveränderter Original80. Eingangsledger nur im RAM verarbeitet: SHA256 `be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af`,3QA-Kunden/1Auftrag/2Arbeiten/1Pending,0Fotos/Rechnungen/Angebote,32öffentlicheBausteine/0Termine. Frühere Boot-/Timeoutversuche keine Erfolge; nach vollständiger Systembereitschaft gelaufen. Dies ist die Vorher-Abnahme, noch kein nativer Nachher-Nachweis des Refactorings.
- Nächster Schritt R3: vorhandenen SQLite-Store aus Activity-Lifecycle lösen, native Effekte/Receipts hinter LocalOwnerRepository und Snapshotprojektion gemeinsam halten. Keine Datenformat-/Pfad-/IDmigration.

## P1 – R3 Storage-Grenzen abgeschlossen (19.10.2026)

- PixelLocalStore ersetzt die bisherige verschachtelte MainActivity.LocalStore-Klasse vollständig, keine Kopie/zweiter Store. Gleiche SQLite-Datei/Version4, Tabellen/DDL, IDs, Snapshots, Journale, Migrationen und Preferences. Zwei bisherige MainActivity-/Tunnel-Instanzprüfungen durch expliziten McpSyncHost-Callback genau dieser beiden Klassen ersetzt; bestehender Vordergrund-/500ms-Abgleichweg erhalten.
- LocalOwnerRepository besitzt native SQL-/Datei-/Original-/Backup-/Request-Belegarbeit. PixelOwnerAccess validiert/routet und plant erst nach Commit; keine SQL-Aufrufe, kein fachlicher Dispatch-Switch mehr. NativeOwnerQueries getrennte native Querygrenze ohne SQL. SnapshotProjection ist die bisherige unveränderte Projektion, jetzt transportunabhängig; kein neuer Datastore, keine automatische Migration.
- PASS27JVM-Suiten einschließlich79Servicetests und vollständigem Vorher-/Nachher-Vertrag `899906c2…aa813`; vier Architekturtests sichern Android-/SQL-freie Use Cases, SQL-freie MCP-Router, entkoppelten Einzelstore und reine Projektion. Release und AndroidTest gebaut, Source/Workspace/aktuelles APK-Artifact-Gate PASS,0Seeds. Beide nativen Quellsets gegen den neuen Store kompiliert; Ausführung auf refaktorierter APK erst R7.
- Messung: OwnerAccess3→0Switches,58→0Cases,61731→2183Bytes,38→11Projektabhängigkeiten,8→0SQL. MainActivity117635→51587Bytes,43→31Abhängigkeiten,30→3SQL; die verbleibenden lokalen Bridge-/Altmethoden werden in R4/R6 behandelt. UI-Aktionen156/57Bridge-Signaturen/5DDL-Anweisungen unverändert. UI-Quellreview ergänzt zwei tatsächliche Adapterdateien, nur Review-Fingerprint geändert.
- Nativer Vorher-Schlussreadback nach134Proben erneut exakt gleicher QA-Ledger `be1523c9…407af`,alleCounts/Pending/IDs/Relationen erhalten. Keine Rohdaten gespeichert. Nächste Phase R4: BusinessDataBridge getrennt von Plattform-/Lifecycleaktionen.

## P1 – R4 Android Bridge abgeschlossen (19.10.2026)

- 35 fachliche Methoden in öffentliche BusinessDataBridge ausgelagert. Bestehende DataBridge erbt diese annotierten Methoden; Kamera/Picker, Share, Google-/Android-Consent, Standort, Navigation und Activity-Lifecycle bleiben native Plattformadapter. Kein neuer Bridge-Endpunkt, gleiche57öffentliche Signaturen/156Aktionen. Persönliche Preference-Migration mit unveränderter Transaktion im vorhandenen Storageadapter; gemeinsame kompatible McpErrors-Bridgeabbildung verwendet.
- PASS27JVM-/29Browser-/5Architektursuiten bzw. Checks, eingefrorener Gesamtvertrag exakt unverändert. Release/AndroidTest mit Source/Workspace/Artifact-Gates PASS,0Seeds. Neue zusätzliche native Probe für öffentliche Vererbung (52DataBridge-/35Businessmethoden), Query-/Fehlerpfad, geschützte b.b.-Migration und isoliertes UI-Speichern vorbereitet/kompiliert; endgültige Ausführung als135.Probe in R7, noch nicht als bestanden zählen.
- Zusätzlicher echter Host-Kameralauf29Fälle PASS, einschließlich CameraChooser/Provider/Dateicleanup. Der bislang separat nicht gestartete Runner hatte zwei P0-Fixtureverdrahtungsfehler: Rootimportpfad fehlte und zentrale SyntheticBusinessData wurde nicht kompiliert. Suchpfad sowie vorhandene gepinnte JSON-Abhängigkeit/echte zentrale Factory eingebunden; keine Prüfung entfernt. Erfolgreich aus fremdem cwd ausgeführt. Dies ist weiterhin keine physische Kameraabnahme.
- MainActivity jetzt40192Bytes/23Projektabhängigkeiten gegenüber117635/43vorher. Die Zwischenmetrik direct_sql enthielt auch zwei Kamera-File.delete-Aufrufe; im Abschluss getrennt von tatsächlichem SQL ausweisen. Der verbleibende echte Main-SQL-Lesepfad ist ein unbenutzter privater Altbeleghelper, Prüfung/Entfernung in R6. Nächster Schritt R5: bestehende UI-Owner-Adapter zuerst charakterisieren, dann stateless Request-/Error-/Clone-Helfer vereinheitlichen.

## P1 – R5 JavaScript/UI abgeschlossen (19.10.2026)

- Vor Änderung30Beobachtungen der fünf bestehenden Owner-Call-Adapter eingefroren; danach130Äquivalenzprüfungen für dieselben Requests/Hashes/Antworten/Fehler und unabhängige Entwurfskopien PASS. owner-client.js ist ein zustandsloser nativer Request-/Error-/Clone-Adapter, keine Datenquelle, kein neuer Request-ID-Generator. Fünf duplizierte native Calladapter konsolidiert, Offer-/Planning-Kopierhilfe gemeinsam. Bestehende Workflow-Entwürfe, Retry-IDs und LocalStorage-Schlüssel unverändert; abweichende Spezialadapter nicht blind umgebaut.
- PASS27JVM-Suiten,30Browser-/JavaScript-Suiten einschließlich neuer130Vergleiche,5Architekturchecks; eingefrorener Hauptvertrag `899906c2…aa813` identisch,156Aktionen/57Bridge-Signaturen erhalten. Keine Design-/Navigation-/Frameworkänderung. Source/Workspace und vorhandene letzte R4-Zwischen-APK Artifact PASS,0Seeds. Aktuelle UI kommt erst mit dem R7-Neubuild in eine APK; alter Zwischenbuild wird nicht als neuer UI-Nachweis ausgegeben.
- Nächster Schritt R6: nachgewiesene unbenutzte private Methoden samt ausschließlich internem Helferpfad entfernen; notwendige historische Regression/Migration unverändert erhalten.

## P1 – R6 nachgewiesene Altpfade abgeschlossen (19.10.2026)

- Vier private, nicht annotierte Methoden entfernt: assertEditableInvoice ohne Aufrufer, dessen ausschließlich dort verwendete readAsset→readStream-Kette und unbenutztes isoNow. Referenzen vor Entfernung exakt1/2/2/1 (einschließlich Definition), keine Reflexions-/JS-/Runtimepfade. Fachlicher Rechnungs-/CAS-/Nummernschutz bleibt in unveränderten zentralen Regeln. No-op-Schemabuilder durch identischen vorhandenen leeren Default ersetzt; ausschließlich nachgewiesen unbenutzte Imports entfernt. Foto-Use-Case nutzt vorhandenen reinen Transfer-SHA statt HTTP-Providerklasse.
- Historische db.py/server.py/mcp_integration und Migrationstests bleiben notwendig für Python-, Cross-Language- und Formatregressionen; nicht als tot gelöscht. Keine Regressionstests/operativen Funktionen entfernt und keine zweite Runtime aufgebaut.
- PASS27JVM-Suiten,6Architekturchecks und vollständiger Vertrag `899906c2…aa813`. Release/AndroidTest/Lint mit Source/Workspace/Artifact-Gates PASS,0Seeds,0Lintfehler/33bestehendeHinweise. Finale native Ausführung folgt in R7.
- Reproduzierbare Abschlussmetrik korrigiert frühere manuelle Zwischenzählungen: MainActivity117635→38136Bytes,43→23Projekt-/23→3fachliche Abhängigkeiten,54→13direkteStoreaufrufe (verbleibend native Share/Medien),28→0SQL-Query/Insert/Delete-Aufrufe. Zwei Kamera-Dateilöschungen werden nicht mehr fälschlich als SQL gezählt. OwnerAccess61731→2183Bytes,39→11Projektabhängigkeiten,42→0Storeaufrufe,8→0SQL,3/58→0/0Switches/Cases. Reader31279→16768Bytes und2/32→1/14; OwnerTools25283→14781Bytes und2/66→1/28 (übriger Switch beschreibt nur Tools). Zusätzliche kleine Helperabhängigkeiten sind bewusst sichtbar, kein pauschaler Rückgang aller Einzelmetriken behauptet.
- MCP117 (52Read/65Write), Protokoll9, Toolhash `235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314` unverändert. UI156/145fachlich/11Plattformgruppen; jetziger quellengebundener UI-Kataloghash `028c2151903c9cc07d7ed3761102002b79fd5d8bd900810ed3bf5094a7a6c11a` ändert ausschließlich Reviewprovenienz, Aktionsvertragsvergleich bleibt identisch. Nächster Schritt R7: abschließende Gesamtregression und neuer81erOriginalkandidat mit synthetischem Update.

## P1 – R7 Gesamtabnahme gestartet (19.10.2026)

- Remote-main erneut unverändert1abf049, alle bisherigen Phasen als normale Commits auf main. Neuer1.51.2/81-Quellstand für nachvollziehbaren sauberen Buildcommit, kein Protokoll-/Datenformatwechsel. Abschließende Python-/JVM-/Browser-/MCP-/Release-/Lint-/Privacyläufe und Original-/Emulatorupdate folgen. Noch keine Abschlussbehauptung.


## P1 – R7 Abschluss und konsolidierte Cloud-Abnahme (19.10.2026)

**PASS: alle 12 Kriterien aus P1.13 im definierten Cloud-Abnahmeumfang.** Ausgang `main` mit Commit `1abf049c1e27167bc16259d8717513aeb5ffe670`, Version 1.51.1/80. Operativer Abschluss-/APK-Quellcommit `e8a1d09ba7bb93438fb5aaff1e6008a670339764`, Version **1.51.2/81**, `source_dirty=false`, Quellhash `1a4d752fce32b09b08972fdabb4349917d8123f2c770986db78b894a327dcc54`. Der anschließende normale Abnahmecommit ändert ausschließlich zentrale Dokumentation; dessen SHA ist über `git log -1` verfügbar und nicht der eingebettete APK-Commit.

### Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Python Quality | 306 Tests PASS, 0 Skips; 37 historische Tests mit QA-Schlüsselerzeugung ausdrücklich ausgeschlossen, Originalsignier- und Privacy-Tests enthalten |
| JVM | 27 Suiten PASS, einschließlich 23 eingefrorener Antworten/Fehler, 14 Fehleradapter- und 79 direkter Application-Service-Prüfungen |
| Android-unabhängige Services | Die 79 direkten Serviceprüfungen zusätzlich ohne Android-SDK im Runtime-Classpath PASS |
| Browser / JavaScript | 30 Suiten PASS; gemeinsamer Ownerclient mit 130 Äquivalenzvergleichen gegen 30 Vorher-Beobachtungen |
| MCP stdio E2E | 55 synthetische Szenarien PASS |
| Kamera-/Provider-Hosttests | 29 Fälle PASS; vorhandenen Runner an Projektimport und zentrale synthetische Factory angeschlossen, keine Tests entfernt oder abgeschwächt |
| Native Android / SQLite / MCP / Provider | 135 Proben, 0 Fehler auf der endgültigen Original-81-APK; öffentliche Bridgevererbung und isolierter UI-Schreibpfad enthalten |
| WebView | Native Navigation, Arbeitsauswahl/Abbruch, Kunden-Gartenansicht und Papierkorb PASS |
| Isolierter Erststart | `fresh_isolated` PASS: neuer privater Testkontext ohne Geschäftsdaten/Zugangsdaten/automatische Providerverbindung; kein Löschen bestehender Profile |
| Release / AndroidTest / AAB / Lint | PASS; 0 Lintfehler, 33 bestehende Warnungen |
| Datenschutz | Source-, Workspace- und Artifact-Gates PASS; Workspace einschließlich ignorierter/untracked Dateien, tatsächliche APK und AAB geprüft, 0 operative Seed-Datensätze |

Keine prozentuale Zeilenabdeckung oder physische Kameraprüfung behauptet. Frühere Boot-/Timeoutversuche waren keine erfolgreichen Abnahmen; die endgültigen vollständigen Durchläufe sind oben ausgewiesen.

### Unveränderte Verträge und synthetisches Update

Der vor R1 eingefrorene Gesamtvertrag bleibt exakt **`899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`**: alle 117 Toolnamen/Schemas/Annotationen und ihre Reihenfolge, 156 semantische UI-Aktionen, 57 Bridge-Signaturen, fünf DDL-Anweisungen, zwölf Domain-/Formatquellhashes, Offer-/Bootstrap-Vertrag sowie 23 synthetische Antwort-/Fehlerhashes. Die Baseline wurde nach Refactoringbeginn nicht neu aufgenommen.

MCP bleibt **Protokoll 9, 117 Tools (52 Read / 65 Write, 17 destruktiv annotiert)**, Toolhash `235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314`. Der vollständig paginierte UI-Katalog behält 145 fachliche MCP-Pfade und elf Plattformgruppen. Nur die aktualisierte Reviewprovenienz der jetzt 51 geprüften Quelldateien ändert seinen Kataloghash auf `028c2151903c9cc07d7ed3761102002b79fd5d8bd900810ed3bf5094a7a6c11a`; Aktionen und Bedienverhalten bleiben gleich. DB 4, Snapshot 2 und Drive-/Journalformate unverändert.

Tatsächliches normales Emulatorupdate **80 → 81 PASS**, ohne Deinstallation oder Datenlöschung. Vor Update, danach und nach allen nativen Tests identischer Hash des Geschäftsmasters ohne technische `capabilities` einschließlich vollständiger Pending-Queue: `be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af`. Unverändert: drei QA-Kunden, ein Auftrag, zwei Arbeitsaufzeichnungen, eine ausstehende Aktion, 32 öffentliche Katalogeinträge; keine Fotos/Rechnungen/Angebote/Termine. Laufende Zeiterfassung bleibt `RUNNING`, stabiler Sitzungshash `78070c6fcd158b6e8e24d651716c5277905bf57bed3fa8386628e5a3514925b1`; nur berechnete `worked_ms`, `hours`, `break_minutes` aus diesem Vergleich ausgeschlossen. Rohzustände ausschließlich im RAM verarbeitet, Berichte enthalten nur Counts/Hashes. Kein produktiver Hostroot eingerichtet oder geöffnet, keine Betriebsdaten importiert, kopiert oder verändert.

### Architektur und messbare Entkopplung

MCP-Verträge, Schemafelder und Fehlerabbildung führen zu reinen Queries und domänenspezifischen Application Services. Diese verwenden `OwnerRepository`/`OwnerCommand` als Ports zum bestehenden nativen `LocalOwnerRepository`, `PixelLocalStore` und Drive-/Dateispeicher. `MainActivity` hält Lifecycle, Navigation und Android-Integration; `BusinessDataBridge` übersetzt bestehende UI-Aufrufe. Der gemeinsame JavaScript-Ownerclient ist zustandslos; Entwürfe, Requests und ihre Speicherschlüssel bleiben in den vorhandenen Workflowmodulen. Keine zweite Runtime, DB oder Dokumentlogik.

| Hotspot | Vorher → nachher | Aussage |
| --- | --- | --- |
| PixelOwnerAccess | 61.731 → 2.183 Bytes; 39 → 11 Projektabhängigkeiten; 3 Switches / 58 Cases → 0; 42 → 0 Storeaufrufe; 8 → 0 SQL-Aufrufe | Native Grenze enthält keine Fach- oder Persistenzimplementierung mehr |
| MainActivity | 117.635 → 38.136 Bytes; 23 → 3 fachliche Abhängigkeiten; 54 → 13 Storeaufrufe; 28 → 0 SQL-Aufrufe | Verbleibende Storeaufrufe betreffen native Share-/Medienadapter; SQL-Metrik zählt kein `File.delete` |
| PixelMcpReadTools | 31.279 → 16.768 Bytes; 2 Switches / 32 Cases → 1 / 14; 13 → 15 Projektabhängigkeiten | Abfragen und Projektion getrennt; zusätzliche kleine Helperabhängigkeiten ausdrücklich sichtbar |
| PixelMcpOwnerTools | 25.283 → 14.781 Bytes; 2 Switches / 66 Cases → 1 / 28; 7 → 8 Projektabhängigkeiten | Übriger Switch beschreibt Tools; Felddefinitionen in 37 registrierten Schemabuildern |
| Größte Klassen | Vorher MainActivity 117.635 / PixelOwnerAccess 61.731 Bytes; nachher PixelLocalStore 64.506 / DriveSyncCoordinator 46.596 Bytes | Keine pauschale Gesamtschrumpfung oder willkürliche Zeilengrenze behauptet |
| UI-Helfer | Fünf gleiche Native-Calladapter → einer; zwei gleiche Clonehilfen → eine | Gleiche Rückgaben/Fehler, keine zusätzlichen globalen Geschäftsdaten |

Direkte Referenzen auf `DriveSyncCoordinator`/`DriveDirectFiles`/`DriveRestClient` sinken im Reader von sechs auf null, in `PixelOwnerAccess` von sechs auf zwei und in `MainActivity` von zwölf auf zehn. Die verbleibenden Aufrufe betreffen native Sync-Steuerung und Dokumentbereitstellung; dies sind statische Referenzzahlen, keine gemessenen Netzaufrufe. Die ursprüngliche konkrete Rückkopplung über `MainActivity.LocalStore` entfällt; der Store kennt Activity und Tunnelservice nicht mehr und verwendet für die bisherige Sync-Benachrichtigung allein `McpSyncHost`.

Sieben Schema-Primitiven bleiben einmal definiert, jetzt unabhängig von der Toolregistrierung in `McpFields`. `EntityContract` bündelt drei Entity-Feldverträge, Record-Hashregeln und Positionsprüfung. `OfferContract` bleibt unveränderte zentrale Definition. Sechs Architekturtests sichern die beschriebenen Grenzen, darunter keine Android-Imports, direkten SQL-Aufrufe oder direkten `DriveRestClient`-Zugriffe in Services und Ports; native Tests prüfen die tatsächliche Persistenz zusätzlich zu Porttests.

Bewusst verbleibende Schulden: `PixelLocalStore` orchestriert weiterhin bestehende atomare Saver-, Timer- und Receipt-Abläufe. Ältere Domainhilfen verwenden kompatible MCP-Hash-/Fehlerfassaden; die reine Adressprüfung verwendet weiterhin den statischen Helfer `OfferDeliveryClient.oneAddress`, tatsächlicher Mailzugriff erfolgt über den Repository-Port. Bestehende Render-Hooks, Workflow-Spezialadapter und unterschiedliche historische Validator-/Fehlerstring-Verträge bleiben zur Wahrung des Außenverhaltens erhalten. Historische Python-/Web-Komponenten werden für Regression und Migration benötigt. Weitere Entkopplung nur inkrementell unter denselben Gates.

Geändert wurden MCP-/Query-/Schema-/Fehleradapter, Application Services/Ports/EntityContract, nativer Store/Repository/Bridge und typgleiche Aufrufer, fünf UI-Module mit gemeinsamem Ownerclient und Quellreview, Charakterisierungs-/Service-/Architektur-/UI-/Native-Tests und Runner sowie die App-Version. Vollständige Liste der 75 Dateien bis zum APK-Quellcommit: `git diff --name-only 1abf049..e8a1d09`; abschließende zentrale Dokumentationsänderungen kommen hinzu. Keine bestehenden Regressionstests entfernt oder abgeschwächt.

### Originalartefakt und Grenzen

- APK: `outputs/Haus-Garten-1.51.2-81-original-p1-kandidat.apk`, 34.513.073 Bytes, SHA-256 `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`.
- Paket `at.hausgarten.service`, Originalzertifikat `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8` identisch zu Original 80. Ein Signer, v2/v3 gültig, 16-KiB-Ausrichtung PASS. Alle 412 APK-Assets stimmen bytegenau mit dem aktuellen Quellstand überein; neue operative Klassen vorhanden, die alte verschachtelte `MainActivity$LocalStore`-Klasse nicht mehr im DEX.
- Ausschließlich `tools/sign_original_apk.py`; vorher nur das Vorhandensein der fünf dokumentierten Variablen geprüft. Instrumentierung ebenfalls mit bestehender Originalsignierung, kein neuer Schlüssel. Cleanup bestätigt: keine privaten Signiertempordner oder Schlüsseldateien im Repository. Vorhandene Downloadartefakte bleiben unverändert; keine neue APK in Drive/GitHub-Downloads veröffentlicht.

**Extern NOT TESTABLE / offen:** physischer Pixel nicht aktualisiert; realer ChatGPT-Connector und echte Google-Drive-/Kalender-/Gmail-/Kamera-/Hintergrund-Endtests nicht ausgeführt. Produktive Legacyaktionen und Warteschlangen nicht angefasst oder als geschlossen ausgegeben. Vor einer ausdrücklich freigegebenen Pixelinstallation bleiben Sicherung und Geräte-/Kontofreigaben erforderlich. Originalsignierung und synthetisches Emulatorupdate sind keine physische Update- oder Datenerhaltfreigabe. Diese externen Grenzen ändern nicht den bestandenen P1-Cloud-Vertrags- und Refactoringumfang.


## Original-APK 81 über Drive bereitgestellt (20.10.2026)

- Auf ausdrücklichen Nutzerauftrag die bereits geprüfte Original-APK 1.51.2 / Build 81 unverändert im bestehenden privaten Ordner `APK-Backups / Codex-Cloud-Builds` ergänzt. Kein neuer Build und keine erneute Signierung.
- [Privater Drive-Download](https://drive.google.com/file/d/1eVAH39SCVFCNCHJHh-ey5C5AR3s6W70K/view?usp=drivesdk). Name, MIME, Zielordner, 34.513.073 Bytes sowie ausschließlich Owner-Freigabe / shared=false per Metadaten rückgelesen. Vollständiger Rohdatei-Rückabruf liefert eine Dateireferenz mit derselben Größe. Kein unabhängiger Remote-Bytehash behauptet.
- Lokale APK vor Upload erneut SHA-256-geprüft: `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`. Keine ältere APK, Betriebsdatei, Sicherung oder Freigabe verändert. Die frühere Aussage „nicht in Drive veröffentlicht“ beschreibt den Zeitpunkt der P1-Abnahme und ist durch diese spätere Bereitstellung überholt.
- Physischer Pixel und realer Connector weiterhin nicht aktualisiert oder nach einem Update getestet; keine Installation durchgeführt oder freigegeben.


## P2 – sichere Konsolidierung gestartet (20.10.2026)

- P1 auf dem realen Pixel gelesen: 1.51.2 / Build 81, MCP9/117, write_enabled=true. UI-Katalog 156/145 fachlich/11 Plattformgruppen; Queue0, Review0, Journalfehler0. Drive aktuell provider_readback/idle ohne Transferfehler; cloud_confirmed=false bleibt externe Grenze.
- Startinventar Geschäftsdaten:58 Kunden,3 Aufträge,46 Arbeitsaufzeichnungen,95 Rechnungen,31 Fotos,33 Leistungskatalogeinträge,0 aktive Angebote,1 Termin. Keine Schreib-/Löschaktion durch P2-Start.
- Archiv read-only:2 Angebote,3 Kunden,129 Worklogs,3 Fotos,2 Rechnungen,0 Aufträge. Sechs vorhandene Backups gelistet. Noch keine Archiv-/Backup-/Dateilöschung freigegeben.
- GitHub read-only: main plus drei alte codex/*-Branches. Alle drei waren beim letzten Vergleich vollständig in main enthalten; Löschung erst nach frischem Vergleich im P2-Schritt. downloads/ enthält zwölf alte APK-Kandidaten Build66–79 plus README/SHA256SUMS; P1/81 liegt privat in Drive und nicht im GitHub-downloads-Ordner.
- Neuer verbindlicher P2-Block in CODEX-AUFTRAG-GARTENPIRAT.md: GitHub-/Codex-Konsolidierung, sichere Drive-/Archiv-/Backupklassifikation, read-only Orphan-Audit vor jeder Dateilöschung, kein History-Rewrite und keine pauschale Löschung geschäfts-/steuerrelevanter Unterlagen.
- Noch keine destruktive P2-Aktion ausgeführt. Nächster Schritt: frischer Mergevergleich der drei alten Branches und Klassifikation der APK-/Dokumentationskandidaten.


## P2-1 – frisches read-only Gesamtinventar (20.10.2026)

- Ausgang `348b40abcb7be78e6444407de58d96c6f07dd080`, sauber auf aktuellem `main`; nur zentrale P2-Anweisungen per Fast-forward übernommen. Keine Geschäftsdaten geschrieben, keine Datei/Branch gelöscht. P0 Source-/Workspace-/Original-81-Artifact-Gates PASS; P1-Gesamtvertrag unverändert `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, 117 Tools / 156 UI-Aktionen.
- Branches erneut direkt vom Remote gelesen: `codex/original-signing-cloud-setup-20261018` bei `5fbe0a3a8c1f9073dc83f83b37b741a59e935ee4`, `codex/preserve-cloud-28504c9-20261018` bei `28504c97278a7deebdfdbf0d9d5eeb57c5c319e4`, `codex/v1.46-functional-optimization-20261016` bei `49d8cb965844a32c708dde5b52867d7c0ff85769`. Alle echte Vorfahren von main, jeweils 0 eigene / 32, 79, 82 zusätzliche Main-Commits. Veralteten lokalen Trackingref nicht als Nachweis verwendet.
- Zwölf getrackte Original-APKs 66–79 inventarisiert und gegen SHA256SUMS verglichen. Zielklassifikation: 81 privat in Drive als aktueller freigegebener Referenzbuild, 79 als letzter GitHub-Referenzbuild erhalten; ältere elf Binaries sind überholte Entwicklungsartefakte, keine Appdaten. Keine Behauptung einer Android-Downgradefreigabe. 30 Markdown-Dateien erfasst, Referenzen geprüft; technische Legacy-Komponenten weiterhin tatsächlich von Tests/Migration benötigt.
- Pixel frisch gelesen: Originalpaket, 1.51.2/81, MCP9/117, Write aktiv, eingebetteter Quellcommit e8a1d09. Aktive Queue/Upload/Review 0/0/0, keine Sync-/Journalfehler; Provider-Readback, cloud_confirmed=false. Rohzähler `pending_actions=1` ist vom gefilterten Queue-Status zu unterscheiden; kein Eintrag entfernt oder quittiert. Geschäftsgruppenhash `2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932`, Counts 58/3/46/95/31/33/0/1. Live-Toolhash `8780ff5fa46d421c4a1ea858db30b232e0272303065d80f28f5f43e242a139aa` ausdrücklich vom JVM-Quellhash unterscheiden; kein Vertragsumbau in P2.
- Archiv vollständig 100+39 paginiert: 139 Einträge, davon 129 historische Arbeiten, zwei geschützte Rechnungen und acht unklare Einträge (zwei Angebote, drei Kunden, drei Fotos); keine sicher ausdrücklich markierten Testdatensätze. **Alle KEEP**, keine Purge-Freigabe daraus ableiten.
- Sechs Backups vollständig gelistet, alle sechs inspect_backup-Aufrufe READ_ERROR. Vier umfangreichere/ältere Referenzen und zwei bytegleiche kleine Altbackups bleiben geschützt; Metadaten allein sind kein aktueller vollständiger Restore-Nachweis. Kein unterstützter Backup-Löschweg gefunden. Aktuelle prüfbare Sicherung vor Löschgruppen noch offen.
- Drive ausschließlich Metadaten: Hauptordner 116 Einträge, Fotoordner 53, APK-Backups 34, Codex-Cloud-Builds 9, geschützter Sicherungsordner 6; zusammen 218 Einträge einschließlich vier Ordnern. 99 State-/Commit-/Mobile-/Legacy-Dateien geschützt, 47 Releaseartefakte, 53 Fotos, 15 sonstige/unklare Dateien. 44 Foto-/Dokumentdateien lassen sich schon aktiven/archivierten Storage-Referenzen zuordnen. Unvollständige Backup-/Historienreferenzen und nicht zugesicherte globale Connector-Vollständigkeit verhindern jeden sicheren Orphan-Löschnachweis. Keine JSON-/Foto-/PDF-/Backup-Rohdatei in den Workspace kopiert.
- Status: Inventar PASS soweit durch vorhandene Lesewege erreichbar, vollständige Sicherungs-/Orphan-Abnahme OFFEN. Nächster Schritt: bestehende lokale Backupfunktion einmal mit stabiler Request-ID verwenden und prüfen; dann ausschließlich nachgewiesen freigegebene technische Bereinigungen.


## P2-2 – Sicherung vor Bereinigungen bestätigt (20.10.2026)

- Bestehenden autorisierten App-Weg einmal mit stabiler Request-ID `p2_20261020_pre_cleanup_current_backup_v1` verwendet. Neue private Geschäftssicherung `backup_240b8a9bedea701bd45a7a92536916328f4cb6319e5dfaee8eff900e1ea0b0be`, 33.524.508 Bytes, SHA-256 `d965237fc10717e58e0897ebf14b13bddfcb18ff298a5139ca955750f252d780`. inspect_backup PASS; Umfang Snapshot/Pending/lokale Originale und Dokumente, keine Zugangsdaten und kein vollständiges Gerätebackup. Counts 58/3/46/95/31/33/0/1. Keine Rohbytes heruntergeladen oder im Repository gespeichert.
- Geschäftsdatenhash vor/nach Sicherung identisch `2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932`; aktive Queue/Review/Journalfehler weiterhin 0. Alle sechs früheren Backups unverändert erhalten. Deren READ_ERROR-Ursache nicht ungeprüft als beschädigte Datei ausgegeben; Quellcode setzt bei Inspektion alle heutigen Collections voraus, ältere Sicherungen können darunter scheitern.
- PASS: Voraussetzung einer aktuellen prüfbaren Geschäftssicherung für die nachfolgenden rein technischen Löschgruppen erfüllt. Kein Restore, kein Purge, kein Drive-Datei-Cleanup und keine Installation.


## P2-3 – integrierte Branches und Releaseartefakte konsolidiert (20.10.2026)

- Frischer Remote-/Ancestryvergleich unmittelbar vor normalem atomarem Branch-Delete; alle drei dokumentierten SHA-Spitzen unverändert und vollständig in main enthalten. Drei alte codex/*-Branches entfernt, Remote-Readback bestätigt nur main. Main dabei unverändert 348b40a, keine Commit-/Historylöschung, kein Force-Push.
- Zwölf APK-Prüfsummen automatisiert gegen vorhandenes Manifest verifiziert. 81 bleibt bewusst privat in Drive; letzter GitHub-Originalbuild 79 bleibt als Referenz mit unverändertem Hash erhalten. Elf ältere APKs 66–77 aus dem aktuellen downloads-Baum entfernt; Prüfsummen/Historienzuordnung in downloads/README.md, Wiederherstellung aus Commit 348b40a möglich. Dies verkleinert den aktuellen Baum, ausdrücklich nicht die Git-Historie. Keine Android-Downgradefreigabe. Original-80-/81-Cloud-Prüfkandidaten bleiben erhalten.
- .gitignore-Ausnahmen auf den tatsächlich aufbewahrten Originalbuild79 reduziert; keine erneute Signierung und keine operative Quelländerung. Neue Geschäftssicherung aus P2-2 ist geprüft, aktive Queue0; keine App-/Drive-Datei gelöscht.


## P2-4 – Dokumentation und Legacy-Nutzung (20.10.2026)

- Alle 30 Markdown-Dateien mit Referenzsuche klassifiziert. KEEP: AGENTS, Auftrag, Status, Originalsignierung, README, downloads/README, Integrationsnachweis, Reparaturnachweis, MCP-Eigentümernachweis, beide Sync-/Abnahmedokumente, Android-Build-/Emulatoranleitung, QA-Signierung (weiter von qa_signing.py referenziert), österreichische Rechnungsquellen und übrige historische Architektur-/Pflanzen-/Kamera-/Foto-/MCP-/Timer-/Pixel-first-Berichte. Deren exklusive historische Nachweise oder Test-/Migrationsbezüge bleiben erhalten; alte Runtime-Anweisungen gelten nicht für den aktuellen Betrieb.
- MERGE → REMOVE: RECHNUNGS-WORKFLOW-v1.24.md, RECHNUNGS-OBERFLAECHE-v1.25.md, DESIGN-v1.35.md. Vorher keine eingehenden Referenzen im aktuellen Baum, vollständig gelesen. Dauerhafte UI-/Dokumentschutzregeln in README und bestehendem MCP-Eigentümernachweis konsolidiert, heutige Regeln aus AGENTS/Auftrag bleiben vorrangig. Historische Details/Prüfungen unverändert aus Commit348b40a lesbar. Keine aus alten Dokumenten kopierten Kunden-/Bankdaten. 30 → 27 Markdown-Dateien, downloads/README und zentrale README verkürzt und auf den tatsächlichen Stand aktualisiert.
- Legacy KEEP bestätigt: db.py/server.py/mcp_integration sowie Rechnungs-/Angebots-/Drive-Migrationsmodule werden tatsächlich von Python-/MCP-/HTTP-/Relations-/Sync-/Backup-Regressionen importiert bzw. aufgerufen. Keine nachweislich vollständig unbenutzte zusätzliche Legacy-Komponente gefunden; keine Tests, Migrationen oder Produktivfunktionen entfernt. Keine operative Java-/JS-/APK-Änderung.


## P2-5 – Cloud-Artefakte, Archiv/Backups und read-only Medienaudit (20.10.2026)

- Nur regenerierbare Gradle-APK/AAB-Ausgaben und den anhand seines P1-SHA belegten eigenen Instrumentierungsrunner aus dem Workspace entfernt; Original-80/81, getrackter Referenzbuild79 und technische Abnahmeberichte bleiben erhalten. Keine Emulatorprofile, fremden Tasks oder veröffentlichte private Cloud-Umgebung entfernt. Keine administrierbare Codex-Task-Archivierungsschnittstelle vorhanden; keine Aufgaben als gelöscht behauptet.
- Archiv139 bleibt vollständig unverändert. Sechs alte Backups bleiben geschützt; zwei gleiche SHA-Werte allein sind keine Löschfreigabe. Neue siebte Sicherung zusätzlich über get_file_metadata vollständig auf Bytezahl/SHA geprüft, unverändert d965237f…2d780. Kein unterstützter Backup-Purge exponiert, kein manueller Drive-Ersatz. Steuer-/Geschäftsunterlagen und Journale unverändert erhalten.
- Ergänzt: tools/audit_media_inventory.py, ausschließlich normalisierte Metadaten im RAM oder ausdrücklich extern unter HG_PRIVATE_DATA_ROOT; kein produktiver Root-Fallback, kein Download-/Löschmodus. Bericht enthält nur Typ-/Klassenanzahlen, Bytes, Referenz-/Inventarhash und Coverage. Bei fehlender Inventar-/Referenz-/Backup-/Historienvollständigkeit, offener Queue, Versionsrennen, Metadatenkollision oder fehlendem referenziertem Objekt kein Orphan-Löschnachweis. Auch vollständiger Audit autorisiert niemals eine Löschung.
- Zehn synthetische Audit-Tests PASS: alle Klassen, aktive/archivierte/finale/pending/Backup-/Historienreferenzen, deterministisches Replay, fehlende Coverage, Rennen, fehlende Objekte, widersprüchliche Metadaten, unbekannte Größen, ungültige Schemawerte, keine Rohdaten in Ausgabe, kein Schreiben/Repository-Fallback. Keine App-/MCP-/UI-/DB-/Snapshotänderung.
- Live-Metadaten-Audit auf allen 214 vom Connector beobachteten Dateien: 44 referenced / 24.605.212 Bytes, 151 protected / 529.373.512 Bytes, 19 unknown / 5.761.826 Bytes, 0 nachgewiesene orphaned/temporary. Zwei vollständige Ordner-Metadatenreads identisch. Inventarhash 55569b9974b53d57194fb3bfc744528b628f9b140e5c5c344dd2cb11d2caa78d; Referenzmengensha 18224433603a496c77c6f19b24696726dd11fd9b84b83d58c9ba6fde97a4f6aa. Status ausdrücklich INCOMPLETE: globale Connector-Abdeckung und vollständige Backup-/Historien-/Dokumentreferenzen nicht belegt. Zero Orphans bedeutet hier keinen Nachweis, dass keine existieren. **Keine Drive-/Medien-/Dokumentlöschung.**

- Ergänzende externe Grenze: kein Remote-Git-Tag vorhanden; gesonderte GitHub-Release-/Asset-Liste über vorhandenes gh-CLI tatsächlich versucht, direkte api.github.com-Anfrage mit `Forbidden` blockiert. Kein Umgehen der Cloud-Netzwerkpolitik und keine Aussage, dass es deshalb keine Releases/Drafts gebe. Keine Release-/Actions-Artefakte remote gelöscht.
- Dokumentdateien bleiben im neuen Audit auch ohne aktuelle Referenz geschützt; finanzielle Aufbewahrung wird nicht aus einem fehlenden Link abgeleitet.

### P2-5 Vorher-/Nachher-Nachweis temporärer Archive

Vier nachweislich regenerierbare Dateien entfernt, zusammen 69.151.796 Bytes; alle vier Pfade danach abwesend. Keine Profil-/Geschäftsdaten betroffen.

| Datei | SHA-256 vor Entfernung |
| --- | --- |
| `outputs/qa-p1-original-instrumentation.apk` | `a60d1250ea9c82eb494c6d848332a46c97c859118fecd1fb25257ec6df250da7` |
| `android/app/build/outputs/apk/release/app-release-unsigned.apk` | `09eaaabb295b07d9a891949ee10b35cf9b04aa5371d44dd4883f99f063320cd8` |
| `android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk` | `9e4b3dc647274594aefe002ac0c418ef110e87a5b5b22154cbbbc8b2620de700` |
| `android/app/build/outputs/bundle/release/app-release.aab` | `186d117bca2ac301dc9db2270063ebd990365e6ab85be3900e4073334ee86b8b` |

Zusammen mit elf alten APKs 447.526.641 Bytes Dateiinhalte aus dem aktuellen Workspace entfernt, kein behaupteter Größenabbau der Git-Historie. README 171→67 und downloads/README 321→56 Zeilen; drei historische Einzelberichte zusammengeführt, keine operative Funktion entfernt.


## P2-6 – Regression und finaler Live-Readback (20.10.2026)

- PASS **316 Python-Tests, 0 Skips**, einschließlich zehn neuer read-only Audit-Tests; 37 historische Tests mit QA-Schlüsselerzeugung unverändert ausdrücklich ausgeschlossen. **27 JVM-Suiten, 30 Browser-/JS-Suiten, 55 isolierte MCP-stdio-Szenarien PASS**. Keine Regressionstests entfernt/abgeschwächt, ausschließlich synthetische Stores. Source/Workspace vor und nach Quality sowie Original-81-Artifact PASS, ignoriert/untracked eingeschlossen; operative Seeds0.
- P1-Gesamtvertrag weiterhin exakt 899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813, 117 Tools / MCP9 / 156 UI-Aktionen. Zusätzlich echte get_capabilities-Tooldefinitionen des Pixels vollständig gegen Baseline verglichen: alle Namen, Schemas, Annotationen und Reihenfolge semantisch identisch; kanonischer Hash235f683d…14314 auf beiden Seiten. Der nativ gemeldete Hash8780ff5f…139aa wird nicht mit diesem kanonischen Vergleich gleichgesetzt; unterschiedliche Darstellung allein ist kein nachgewiesener Vertragsfehler. Keine Katalog-/Runtimeänderung in P2.
- Finaler echter MCP-Readback: Geschäftsdatenhash vor/nach allen Schritten identisch 2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932, Counts58/3/46/95/31/33/0/1. Alle139 Archivschlüssel/Record-Hashes identisch. Sechs alte Backup-IDs/Metadaten identisch, neue geprüfte Sicherung zusätzlich:6→7. Aktive Queue/Review/Journalfehler0/0/0; provider_readback/cloud_confirmed=false. Keinen neuen Abgleich angefordert, keine Quittung erfunden.
- Neue native/Gradle-/Pixel-Installationsprüfung nicht ausgeführt: operative Java-/JS-/Assets/Gradle unverändert, kein neuer APK-Build erforderlich. Vorhandene P1-Nativabnahme ist ein historischer Nachweis, keine neue P2-Geräteausführung. Reale MCP-Leseprüfung tatsächlich durchgeführt; keine produktiven Schreibtests; einzige speichernde Betriebsaktion war die ausdrücklich notwendige neue private Geschäftssicherung.
- **Noch offen:** globale Vollständigkeit der Drive-/Dokument-/Backup-/Journalreferenzen; 19 beobachtete unbekannte Dateien bleiben KEEP. Alle sechs historischen inspect_backup-Aufrufe READ_ERROR trotz lesbarer Metadaten, Wiederherstellungswert daher nicht vollständig prüfbar. Direkte GitHub-Release-/Asset-Liste Forbidden; keine Aussage über abwesende Releases/Drafts. Fremde Codex-Tasks nicht administrierbar. Kein Bypass, keine manuelle Daten-/Backup-/Medienlöschung. P2 nicht pauschal als vollständig abgenommen markieren.

### Vollständige Dokumentklassifikation

| Datei | Entscheidung |
| --- | --- |
| `ABSCHLUSSBERICHT-v1.46.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `AGENTS.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `ARCHITEKTUR-PRUEFBERICHT-v1.34.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `CHATGPT-INTEGRATION.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `CODEX-AUFTRAG-GARTENPIRAT.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `CODEX-STATUS.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `DESIGN-v1.32.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `DESIGN-v1.35.md` | MERGE → REMOVE; gültige Regeln in zentralem MCP-Nachweis/README erhalten |
| `FOTODOKUMENTATION-v1.47-PLAN.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `GARTENPIRAT-PFLANZEN-v1.46.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `INTEGRATIONSNACHWEIS-2026-10-18.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `KAMERA-KORREKTUR-v1.46.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `MCP-ALLTAG-v1.36.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `ORIGINAL-SIGNIERUNG-CLOUD.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `PHASE-0-PIXEL-FIRST-BASELINE.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `PHASE-1-BACKUP-UND-ARBEITSSITZUNG.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `PLUGIN-SCHREIBRECHTE-v1.31.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `QA-SIGNIERUNG.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `README.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `RECHNUNGEN-OESTERREICH.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `RECHNUNGS-OBERFLAECHE-v1.25.md` | MERGE → REMOVE; gültige Regeln in zentralem MCP-Nachweis/README erhalten |
| `RECHNUNGS-WORKFLOW-v1.24.md` | MERGE → REMOVE; gültige Regeln in zentralem MCP-Nachweis/README erhalten |
| `REPAIR-v1.49.1-2026-10-18.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `TIMER-STANDORT-v1.33.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `android/ANDROID-17-BUILD.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `docs/MCP-EIGENTUEMERZUGRIFF-2026-10-18.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `docs/SYNC-DRIVE-KUNDENAKTE-1.48.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `docs/SYNC-KUNDENAKTE-ABNAHME.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `downloads/README.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |
| `tests/Android-Emulator.md` | KEEP; zentrale Anleitung oder historischer/technischer Nachweis |

### Vollständiges Altbackup-Metadateninventar

Alle sechs bleiben KEEP; Scope jeweils Geschäftssnapshot/Pending/lokale Fotos und Dokumente, keine Zugangsdaten. Die native Inspektion scheitert jeweils mit READ_ERROR, daher keine vollständige Wiederherstellbarkeitsbehauptung. Kleine Dateigröße allein beweist weder Vollständigkeit noch Unbrauchbarkeit; gleiche SHA allein autorisiert keine Löschung.

| Technische Referenz (gekürzt) | Erstellt UTC | Bytes | Metadaten-SHA-256 |
| --- | --- | ---: | --- |
| `backup_275dabc158142698…` | 2026-10-19T04:32:46.944085Z | 22796693 | `3c6e59ef604c25919f0f10cfdf223eacb72c9555899c9b8c2e966e1d78ebfc01` |
| `backup_3fa4460d4db94ee5…` | 2026-10-19T04:28:58.537608Z | 22987905 | `58e99ac2e57d4215106391daea6d4ebe25a8c4a96998e73050b6f8aec15f6045` |
| `backup_7fd0a667d4708f06…` | 2026-10-18T20:23:14.763926Z | 22711249 | `776685e56ded05e41ae9363dfb9861dffbe83a34862845a57dbd62958c89e19e` |
| `backup_9778e41a06e30d40…` | 2026-10-18T18:45:27.892581Z | 372514 | `3a4d578b87ec40d3a4ce83e5e277664888aecbe75b4a3b6f92ff8925609eb56b` |
| `backup_4ef99a6925a1c66e…` | 2026-10-18T17:34:49.618231Z | 362292 | `4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b` |
| `backup_ed1e21387e525d05…` | 2026-10-18T17:30:05.649173Z | 362292 | `4683945a1d959e34aeea818768eb8fc618ef2a9cc424e76054c05072e278cb6b` |


## P2-7 – offene Zugangs- und Backupfragen erneut geprüft (20.10.2026)

- Ausgang main `93ced845b7718b0a7d824220e5508057f1a97306`, sauber, Remote identisch. Frisches ls-remote bestätigt ausschließlich main. Den ebenfalls vollständig integrierten lokalen codex/original-signing-cloud-setup-20261018-Zeiger und drei nicht mehr existierende Remote-Trackingrefs entfernt; Commitobjekte unverändert erhalten. Lokaler work-Zeiger ist ein vollständig integrierter Umgebungscheckpoint, keine zweite operative Remote-Branch. Keine Historyänderung/Force-Push.
- Die bisherige direkte API-Sperre ist weiterhin gültig. Über den vorhandenen unterstützten GitHub-GET-Connector ist die Release-Liste aber erreichbar: 0 Releases. Alle 14 Actions-Läufe und jeweils deren vollständige Artefaktliste gelesen: sieben unexpired Artefakte, 240.642.864 Bytes. Vier UNSIGNED-NOT-RELEASED- und drei QA-Entwicklungsartefakte, sämtliche Quellcommits echte Vorfahren von main. Keine Behauptung einer APK-/Signatur-Inhaltsprüfung dieser Archive. Kein verfügbarer Actions-Artefakt-DELETE im Connector; kein Umgehen der Cloud-Netzwerkpolitik. KEEP bis automatischem Ablauf oder unterstützter Actions-Schreibfreigabe. Retention laut Provider: QA am 23.10.2026, unsigned am 25.10.2026; tatsächlich erfolgter Ablauf noch nicht geprüft.

| Actions-Run | Artefakt-ID | Bytes | Ablauf UTC |
| --- | --- | ---: | --- |
| 39150055162 | 12145390411 | 34298321 | 2026-10-25T17:35:48Z |
| 39149764763 | 12145100469 | 34298320 | 2026-10-25T17:31:38Z |
| 39149646442 | 12145310027 | 34298320 | 2026-10-25T17:30:18Z |
| 39149370858 | 12144637627 | 34298216 | 2026-10-25T17:26:43Z |
| 38958704554 | 12071757106 | 34484052 | 2026-10-23T19:59:37Z |
| 38956328082 | 12071085369 | 34483229 | 2026-10-23T19:39:44Z |
| 38953870720 | 12070645377 | 34482406 | 2026-10-23T19:19:53Z |

- Pixel zuerst lesend erreichbar: 1.51.2/81, Protokoll9/117, Counts58/3/46/95/31/33/0/1 und Geschäftshash `2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932` identisch zur P2-Abnahme; aktive Queue/Review/Journalfehler0, provider_readback, cloud_confirmed=false. Keine speichernde Betriebsaktion in dieser Nachprüfung.
- Alle sechs alten Backups erneut über get_file_metadata vollständig nach Bytezahl/SHA geprüft: 6/6 stimmen mit dem bisherigen Inventar überein. Beim kleinsten Altbackup `backup_ed1e21387e525d058319b103fa645fd221f93cbed44b294117ba5b09c220a6ea` wurde der State ausschließlich temporär im Connector-Arbeitsspeicher untersucht, ohne Rohdaten in Workspace/Logs: alte Collections vorhanden, nur appointments fehlt. Counts51/1/173/95/12/32/0, Pending9. LocalOwnerRepository.backupCounts verlangt dagegen getJSONArray("appointments"); dieser tatsächliche Formatunterschied erklärt den READ_ERROR dieser Datei. Keine Beschädigung aus dem Fehler ableiten. Gleicher SHA beim zweiten kleinen Backup ist ein Bytegleichheitsnachweis, keine Löschfreigabe; keine separate Restaurationsprobe.
- Umfangreichere Inhaltsprüfung anderer Altbackups lieferte keine verwertbare Antwort und wurde abgebrochen; keine unbeschränkten Wiederholungen. Anschließendes get_sync_overview meldet McpServerError: Connection failed. Ursache des vorübergehenden Verbindungsabbruchs nicht bewiesen. Der spätere erneute Health-/Sync-/Archiv-/Backup-Readback in P2-9 war erfolgreich; kein echter Restoretest daraus ableiten.
- Funktionaler Altbackup-Inspektionsfix bleibt OFFEN: P2 erlaubt ausdrücklich nur Konsolidierung, keine operative Weiterentwicklung. Fachlich begrenzte spätere Korrektur: ein in alten Sicherungen fehlendes additives appointments-Feld lesend als leer behandeln; vorhandene falsche Typen weiterhin ablehnen, Backupbytes/Restore-/DB-Pfad unverändert lassen und synthetisch absichern. In dieser P2-Nachprüfung keine Android-/MCP-/Restoreänderung und keine neue APK.

## P2-8 – Foto-/Dokumentreferenzen präzisiert (20.10.2026)

- Bestehenden read-only Audit um resolve_photo_references erweitert: ausschließlich explizite canonical storage_name/thumbnail_storage_name oder sichere Legacy-filename-Referenz ohne storage_name. Entspricht der bestehenden PhotoAttachmentStore-Dateinamenregel; keine URL-, Basename- oder unscharfe Zuordnung. Ein neuer Storage-Verweis wird niemals durch einen alten Anzeigenamen ersetzt.
- Doppelte Namen schützen alle Treffer und verhindern Vollständigkeitsbehauptung; widersprüchliche Objektidentitäten, fehlende/ungültige Namen ebenso. Adapterdaten bleiben privat im RAM; öffentliche Ausgabe nur Counts/Hashes. Kein zusätzlicher produktiver Store und kein Löschmodus. Sechs neue synthetische Regressionen, insgesamt16 Audit-Tests PASS; bestehende zehn Tests unverändert erhalten.
- Tatsächliche Metadatenabgleichung:31 aktive Fotozeilen (19 modern,12 legacy) referenzieren47 Drive-Dateien, sechs weitere Dateien sind bereits über Archivfotos referenziert. Alle53 beobachteten Fotodateien damit referenziert, keine fehlende aktive Storage-Datei und keine Namensmehrdeutigkeit im beobachteten Fotoordner. Drei Legacy-Zeilen teilen bereits referenzierte Dateien; Zeilen- und Dateizahl nicht gleichsetzen. Keine Originalbilder heruntergeladen, verändert oder gelöscht.
- Elf native/Text-/Markdown-Dokumente gemäß vorhandener Schutzregel als document klassifiziert; die explizite vorhandene Journaldatei ebenfalls geschützt. Übrige drei JSON-Dateien ohne vollständigen Verwendungsnachweis bleiben unknown/KEEP, keine Klassifikation nach geratenen Inhalten.
- Erneuter Audit auf214 beobachteten Dateien:53 referenced /30.299.197 Bytes;158 protected /529.414.214 Bytes;3 unknown /27.139 Bytes. 0 nachgewiesene orphaned/temporary; Gesamtbytes559.740.550 unverändert. Referenzhash `0a735eb6a3f9477fec39fc8a9df23f4ec6616991785d82d6ea12bfac188e03ad`, Inventarhash `d46975dfd5542eecf194c27344ca6e0a3502756294772b9aeb99a0016afdfecb`. Lokale Auditausführung nur über eine technisch SHA-pseudonymisierte ID-/Dateinamenprojektion, keine Rohdatensätze im Workspace. Deren eigene Audit-Hashes unterscheiden sich bewusst von den hier ausgewiesenen ursprünglichen RAM-Referenzhashes.
- Status weiterhin INCOMPLETE: keine zugesicherte globale Connector-Inventarvollständigkeit, nicht sämtliche Backup-/Historien-/Dokumentreferenzen verifiziert. Nach vorübergehendem Verbindungsabbruch finaler Geschäftshash erneut identisch gelesen: before_revision/after_revision entsprechen diesem tatsächlichen Geschäftshash, stable_revision=true und queue_empty-Coverage=true. Alle fünf beobachteten Drive-Ordner zusätzlich unverändert zurückgelesen. Snapshot-Stabilität wird nicht aus Git-HEAD oder identischen Dateizahlen abgeleitet. Referenzsicherheit der beobachteten Fotos ist kein globaler Orphan-Nachweis. Keine Drive-/Archiv-/Backup-/Medienlöschung.


## P2-9 – erneute Regression und finaler tatsächlicher Readback (20.10.2026)

- PASS322 Python-Tests/0 Skips,27 JVM-Suiten,30 Browser-/JS-Suiten und55isolierte MCP-stdio-Szenarien. Die37 historischen Tests mit QA-Schlüsselerzeugung unverändert ausdrücklich ausgeschlossen; kein neuer Signaturschlüssel erzeugt. Sechs neue Auditregressionen, insgesamt16 Audit-Tests. Keine produktiven Daten für Schreibtests und keine Abschwächung bestehender Tests.
- P0 Source/Workspace/Original-81-Artifact vor/nach Qualität PASS, ignoriert/untracked eingeschlossen; operative Seedzeilen0. Original-81-APK bytegleich SHA `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`. P1-Gesamtvertrag unverändert `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`,117 Tools/Protokoll9/156 UI-Aktionen. Keine Android-/JS-/MCP-/DB-/Snapshot-/Drive-Vertragsänderung, daher keine neue APK/Signierung/Installation und kein neuer nativer Emulatorlauf. Historische native Abnahmen nicht als neue P2-Ausführung ausgeben.
- Nach dem vorübergehenden Verbindungsfehler begrenzte erneute Healthabfrage erfolgreich:ready, danach get_sync_overview erfolgreich. Geschäftshash weiterhin `2b8310a538134a49966b736ae660758e2863a1d9fe62e973c11cc1f4ddb07932`, sämtliche Collection-Counts58/3/46/95/31/33/0/1 unverändert. Aktive Queue/Upload/Review/Journalfehler0/0/0/0; provider_readback, cloud_confirmed=false. Kein neuen Sync angefordert, keine Cloud-Bestätigung erfunden.
- Archiv erneut vollständig100+39 paginiert; vollständige139Einträge einschließlich Record-Hashes gegen bisheriges P2-Inventar identisch. Kanonischer Vollinventarhash vorher/nachher `d33a72b59135ad2928d01b3d6919537848aef35544000bfa8342e17061cdea50`. Alle sieben Backups weiter gelistet, sämtliche sechs alten IDs/Metadaten unverändert; neue geprüfte Sicherung ebenfalls erhalten. Keine neue Sicherung/Restore/Purge oder andere speichernde Betriebsaktion in dieser Nachprüfung.
- Alle fünf bekannten Drive-Ordner erneut vollständig im angebotenen top_k1000-Leseumfang abgefragt:218beobachtete Einträge inklusive vier Ordnern/214Dateien. Vergleich von IDs, Namen, MIME, Größe, Änderungsdatum und Parent-IDs vor/nach identisch; Metadatenhash `b302e09dd8dcb5550c07ff9d372f48a99b8d2edfb80d8599ba984912f7e06877`. Keine globale Vollständigkeitsgarantie aus einer begrenzten Connector-Liste ableiten. Keine Medienoriginale heruntergeladen und keine Drive-Datei geändert/gelöscht.
- Ergebnis: technisch sichere P2-Nachprüfung PASS; volle P2-Abnahme bleibt OFFEN. Noch offen: native Altbackup-Inspektion/ungeprüfter Restorewert, drei unbekannte JSON-Dateien und globale Backup-/Historien-/Dokumentreferenzen, tatsächlicher Ablauf der sieben alten Actions-Artefakte. Keine unterstützte Actions-Artefakt-DELETE-/Backup-Purge-/Codex-Task-Archivierungsschnittstelle vorhanden; kein Netzwerk- oder Berechtigungsbypass. Historische sensible Git-Objekte weiterhin separater Sicherheitsauftrag, kein Rewrite.
- Geändert ausschließlich tools/audit_media_inventory.py, tests/test_media_inventory_audit.py und die beiden zentralen CODEX-Dateien. Keine neue Projektdokumentation oder parallele Architektur.


## UAT und Black-Box-Usability – Analyseauftrag vom 20.10.2026

### Phase A – geprüfte Basis und Testmaßstab

- Ausgang/Remote-main identisch `d8960c46d42b5f6c99d8b6c2f3217686a6e4cf13`, sauber; operative Quelle/Originalartefakt1.51.2/81, Paket at.hausgarten.service, MCP9/117,156 UI-Aktionen. AGENTS, vollständiger Zentralauftrag und vollständiger bisheriger Status gelesen. Dieser neue Nutzerauftrag beschränkt die Arbeit auf Analyse/Test und diesen abgegrenzten Statusabschnitt; keine Reparatur, Design-/Architektur-/Vertrags-/Versionsänderung und keine produktiven Daten/Konten verwenden.
- Methode: nachvollziehbarer kognitiver Persona-Walkthrough plus reale Browserbedienung derselben operativen Assets mit bestehenden synthetischen Bridges, erhaltene Regressionen und dedizierter GartenpiratCloud-Emulator5582. Ein Agentenwalkthrough ist keine Studie mit echten Erstbenutzern; Auffindbarkeit/Verständlichkeit sind begründete Heuristik. Browserdoubles nicht als native Persistenz oder echten Google-/Kamera-/Versandnachweis ausgeben. Externe Freigaben ohne QA-Konto bleiben NOT TESTABLE.
- Abnahmekriterien: alle115 nummerierten Aufgaben einzeln erfassen, sämtliche sichtbaren Einstellungen inventarisieren, Technikexposition im normalen/Fehler-/Diagnosemodus unterscheiden, tatsächlich beobachtete Aktionszahlen von vorgeschlagenen Zielzahlen trennen. Keine Benutzerzeiten oder Fehlerquoten erfinden. Sicherheits-/Privacyschutz gilt auch dann als notwendig, wenn seine technische Darstellung UX-Reibung erzeugt.
- Zählregel: ein Tippen/Klicken, Feldbefüllen, Auswählen, Scrollen oder Zurück jeweils eine Benutzeraktion; programmgesteuerte Zustandsabfragen und Testdatenvorbereitung zählen nicht. Navigation am jeweiligen Ausgangspunkt ausdrücklich ausweisen; keine Summe über115 Aufgaben als Zahl unabhängiger Gesamtworkflows ausgeben. Unnötige Aktionen sind eine Persona-Bewertung, keine Erlaubnis zum Entfernen von Bestätigung/CAS/Backup.
- Emulator ohne KVM aus vorhandenem erhaltenem AVD gestartet, keine Profile/Stores gelöscht und keine Pixelverbindung. Testausführung, tatsächlicher Bootabschluss und abschließende Privacy-/P1-Vertragsnachweise noch offen. Der Auftrag endet mit einem priorisierten Bericht, nicht mit Umsetzung.

### Phase B – synthetische Funktions- und Bildschirmprüfung

- Erneut ausgeführt: 322 Python-Prüfungen PASS (0 Skips; 37 ausdrücklich ausgeschlossene historische QA-Schlüssel-/Parallelpaket-Prüfungen), 27 JVM-Suiten PASS, alle 30 Einträge des Browser-/JS-Regressionsrunners PASS und 55 Szenarien des isolierten MCP-stdio-Rundlaufs PASS. Dieser stdio-Rundlauf prüft den vorhandenen eingeschränkten Kanaladapter und ist kein Nachweis eines aktuell verbundenen Pixel-Connectors; dessen 117 Werkzeuge werden zusätzlich durch JVM-/native Contracts geprüft.
- Ergänzender kognitiver Durchlauf auf unveränderten operativen Assets bei 360 × 900: Kunde mit Name/Adresse anlegen, Kontakt nachtragen; Arbeitsdetail und alle fünf Zusatzgruppen öffnen; Kundenaktion → Angebotsentwurf → Prüfung; Planungsformular; Offene Leistungen → Rechnungserstellung; Timer bei verweigertem Standort → manuelle Auswahl → Pause → Fortsetzen → Ende → Speichern. Sechs Durchläufe PASS auf der bestehenden synthetischen Bridge. Anfängliche Selektor-/Fixture-Extraktionsfehler des temporären Auswertungsskripts wurden dort korrigiert, ohne App- oder Testcode zu ändern; sie sind keine Appfehler.
- Sichtprüfung tatsächlicher synthetischer Screenshots für Heute, Einstellungen, Kundenakte, Angebots- und Terminformular. Keine Produktivnamen, Originalfotos oder Kontozugänge verwendet. Browser-Fiktivversionen stammen aus bestehenden Fixtures und ersetzen nicht die nativ geprüfte Version 1.51.2/81.
- Der ausschließlich ausgewählte Cloud-Emulator emulator-5582 wurde mit QEMU-, AVD-Namens- und Bootprüfung bestätigt; vorhandene operative App 1.51.2/81 und Instrumentierung gefunden. Zu diesem Phasenzeitpunkt liefen die nativen SQLite-/MCP-/Medien-/Provider-Tests noch; Endergebnis siehe Phase D. physischer Pixel, echter Google-Account, echter Drive und externer Versand wurden nicht berührt.

### Phase C – Ergebnis des User-Acceptance- und Black-Box-Usability-Tests

#### Prüfgrenzen, Nachweise und Zählweise

Dies ist ein durch automatisierte Regressionen gestützter kognitiver Experten-Durchlauf mit der vorgegebenen Betreiberpersona, keine beobachtete Studie mit einer technisch uninteressierten Erstperson. Auffindbarkeit und unnötige Schritte sind deshalb begründete Usability-Bewertungen, keine gemessenen Erfolgsquoten oder Bearbeitungszeiten realer Benutzer. Es wurde kein Ein-Minuten-Versprechen bestätigt. Die unten vorgeschlagenen Abläufe sind ausschließlich Empfehlungen, nicht implementiert.

Nachweise: **B** = tatsächlich ausgeführtes Chromium-UI auf operativen Assets mit vorhandener synthetischer Android-Bridge; **N** = vorhandene Android-Instrumentierung auf dem dedizierten Cloud-Emulator; **J** = JVM-/Contract-Test; **P** = Python-/isolierter Kanaltest; **S** = Quellcode-/Bedienelementprüfung. B bestätigt keine Android-Berechtigung, keine Kameraaufnahme und keinen echten Google-Upload. N mit synthetischem Provider bestätigt keine Erreichbarkeit eines echten Google-Kontos. Ein Katalog-Mapping bestätigt keine tatsächlich sichtbare Schaltfläche.

**P = PASS**, **R = PASS MIT REIBUNG**, **F = FAIL des beschriebenen Benutzerziels**. Die angegebenen N-Nachweise sind die erfolgreichen isolierten Fach-/Neustarttests, keine pauschale Freigabe der nativen Sichtprüfung oder des zusätzlich fehlgeschlagenen alten Update-Verifiers (Phase D). **NT = NOT TESTABLE** bezeichnet den ausdrücklich nicht ausgeführten externen Teil, auch wenn Teilprüfungen PASS sind. Ein R kann technisch korrekt und trotzdem unnötig kompliziert sein. Kein F wird allein aus einer unvollständigen Browser-Bridge abgeleitet. Keine aktuelle Produktivwarteschlange oder historische Originalkonflikte wurden geprüft oder verändert.

**A/U** zählt Benutzeraktionen/als vermeidbar bewertete Aktionen vom jeweils genannten Ausgangspunkt: Tap/Klick, Auswahl, Feldeingabe, Bestätigung je eine Aktion; zusammengesetzter App-Auswahldialog üblicherweise zwei. Betrachtung ohne Eingabe und automatisch wiederhergestellter Stand: null. **≥** ist die im ausgeführten Weg belegte Untergrenze; **~** eine aus dem geprüften Bedienpfad abgeleitete Spanne, ausdrücklich keine vollständig gemessene Bedienfolge. Die Browserautomation scrollt Bedienelemente teilweise automatisch in Sicht; manuelles Scrollen, Android-Dateiauswahl, Tastatur, Kontofreigabe und OS-Dialoge erhöhen die tatsächlichen Zahlen. Für nicht ausführbare Ziele ist „—“ ehrlicher als eine erfundene Klickzahl. U ist eine Entwurfsbewertung und keine Aufforderung, Bestätigung, Preisprüfung oder Rechtsentscheidung wegzulassen. Zusammenhängende Teilaufgaben nicht addieren, wenn dieselbe Navigation mehrfach enthalten ist.

Die Profilspalte legt für **jede zugeordnete Zeile** Vorwissen, Auffindbarkeit, Verständlichkeit, Fehlerrisiko, Risiko unbeabsichtigter Änderung und Selbstkorrektur fest; abweichende Ursachen stehen in der Zeile. **V/N/A/E** nennt je Zeile: technische Informationen sichtbar / für das Benutzerziel notwendig / sicher automatisch erledigbar / fachliche Entscheidung auf EIN/AUS reduzierbar (J/N). „Automatisch“ bei einer Suche oder Dokumentprüfung meint nur Kontext-/Standardübernahme bzw. Prüfung, niemals erfundene Preise, rechtliche Bindung, Versand, endgültiges Löschen oder Konfliktüberschreiben. Profile:

| Profil | Vorwissen | Auffindbarkeit | Verständlichkeit | Fehlerrisiko | unbeabsichtigte Änderung | Selbstkorrektur |
|---|---|---|---|---|---|---|
| R – Lesen | keines | gut | gut | niedrig | niedrig | Zurück/Filter zurücksetzen |
| E – Eingabe | eigene Betriebsangaben | gut | überwiegend gut | mittel bei Auswahl | niedrig durch explizites Speichern | Bearbeiten/Abbrechen/Entwurf |
| X – versteckter Weg | Menü-/Bereichskenntnis | eingeschränkt | mittel | mittel | niedrig | Zurück/Entwurf; Ziel zunächst finden |
| D – Dokument | Preise, Empfänger, Bindung/Steuer fachlich kennen | mittel | erklärungsbedürftig | mittel bis hoch fachlich | begrenzt durch Prüfung/Bestätigung | Entwurf editieren; nach Abschluss Korrekturweg |
| T – Technik | derzeit Verbindungs-/Übertragungswissen | schlecht für Persona | unzureichend | mittel | niedrig bei Lesen; Fehlentscheidung möglich | Wiederholen/Einrichtung, teilweise Hilfe nötig |
| G – Entfernung | Papierkorb/Endgültigkeit unterscheiden | mittel | überwiegend gut | hoch bei endgültigem Löschen | durch Vorschau/Bestätigung begrenzt | Undo/Wiederherstellen; nach Purge nicht in App |
| A – Hintergrund | keines | keine Bedienung erforderlich | gut bei wahrer Rückmeldung | niedrig | niedrig | Entwurf/Sitzung wieder aufnehmen |
| L – fehlender Bedienpfad | derzeit MCP-/Hilfswissen nötig | nicht auffindbar | Ziel nicht ausführbar | mittel bei Workaround | durch Umwege erhöht | nur vorhandener alternativer Fachweg |
| O – externe Grenze | Geräte-/Kontofreigabe | nur teilweise geprüft | teilweise technisch | nicht vollständig bewertbar | nicht vollständig bewertbar | neu freigeben; kein Erfolg behauptet |

Severity bewertet den Befund, nicht den maximal denkbaren Schaden jeder Rechnung: **S0** = nachgewiesenes Daten-/Sicherheitsrisiko, **S1** = Benutzerziel blockiert, **S2** = deutlich unnötig kompliziert, **S3** = leichte Reibung, **S4** = kosmetisch; **–** = kein Befund. Es wurde kein neuer S0-Datenverlust nachgewiesen.

#### Aufgabenmatrix A–N: alle 115 Aufgaben

| Nr. | Aufgabe und Ausgangspunkt / Befund | Urteil; Nachweis | A/U | Profil | V/N/A/E | Severity |
|---:|---|---|---|---|---|---|
| 1 | Erster Start: leerer isolierter Bestand; keine Produktivdaten | P; N/B | ~1/0 | R | N/N/J/N | – |
| 2 | Heute: Tages-/Wochensumme, nächster Einsatz, letzter Eintrag | P; B/N | 0/0 | R | N/N/J/N | – |
| 3 | Heute: große Schaltfläche Arbeit starten; Leertext nennt abweichend „Arbeit erfassen“ | R; B/N | 0/0 | R | N/N/N/N | S3 |
| 4 | Heute: laufende/pausierte Sitzung und Abschluss sind sichtbar | P; B/N | 0/0 | R | N/N/J/N | – |
| 5 | Heute: lokalen Speicherzustand von erstem Drive-Abgleich unterscheiden | R; B/S | ~0–1/0 | T | J/N/J/N | S2 |
| 6 | Startbelastung: Drive-Warnpille ohne fachlichen nächsten Schritt; sonst kompakt | R; B | 0/0 | T | J/N/J/N | S3 |
| 7 | Heute → Kunden → Neu → Name/Straße/PLZ/Ort → speichern | P; B/N | ≥7/0 | E | N/N/N/N | – |
| 8 | Name und Adresse genügen; Telefon/E-Mail optional | P; B/N | 0/0 | E | N/N/N/N | – |
| 9 | Kundenliste → Akte → bearbeiten → Telefon → speichern | P; B | ≥4/0 | E | N/N/N/N | – |
| 10 | Geöffneter Kundeneditor → E-Mail ergänzen → speichern | P; B | ≥2/0 | E | N/N/N/N | – |
| 11 | Kundenliste: Suchfeld unten, ein Suchbegriff | P; B | ≥1/0 | R | N/N/N/N | – |
| 12 | Kundenliste: Treffer öffnet Kundenakte | P; B/N | ≥1/0 | R | N/N/N/N | – |
| 13 | Akte: letzte Arbeiten sichtbar; ältere über vollständige Arbeitsliste/Verlauf | P; B | ~0–2/0 | R | N/N/J/N | – |
| 14 | Akte → Fotos & Dateien; 30 Vorschauen, weitere laden | P; B | ≥1/0 | R | N/N/J/N | – |
| 15 | Akte → alle Arbeiten; fremde Zeit-/Suchfilter werden zurückgesetzt | P; B | ~1–2/0 | R | N/N/J/N | – |
| 16 | Rechnungen in Übersicht, Angebote unter Fotos & Dateien, Termine als Kopfaktion | R; B/S | ~1–3/~1 | X | N/N/J/N | S2 |
| 17 | Akte → Kundendaten bearbeiten → Änderung → speichern | P; B | ≥3/0 | E | N/N/N/N | – |
| 18 | Kundeneditor: Kunde aktiv AUS; Entfernung separat über Papierkorb | R; B/S/N | ~4–6/~1 | X | N/N/N/J | S2 |
| 19 | Inaktive Kunden bleiben suchbar; Papierkorb hat Wiederherstellen | P; B/N | ~2–4/0 | G | N/N/N/N | – |
| 20 | Heute → Start → Kundenauswahl → Start; kein Start allein durch Ortsermittlung | P; B/N | ≥3/0 | E | N/N/N/N | – |
| 21 | Startdialog/Kundenauswahl; Auftrag optional, ggf. separater Zusatzbereich | R; B/S | ~2–4/~1 | X | N/N/J/N | S3 |
| 22 | Laufende Sitzung → Pause | P; B/N | ≥1/0 | E | N/N/N/N | – |
| 23 | Pausierte Sitzung → Fortsetzen | P; B/N | ≥1/0 | E | N/N/N/N | – |
| 24 | Laufende Sitzung → Ende; Arbeitsaufzeichnung erst nach ergänzen/speichern | P; B/N | ≥1/0 | E | N/N/N/N | – |
| 25 | Abschluss → Tätigkeit → speichern; Timer-Nettozeit wird übernommen | P; B/N | ≥2/0 | E | N/N/J/N | – |
| 26 | Abschluss → Material und Kilometer → Material | P; B | ≥2/0 | E | N/N/N/N | – |
| 27 | Offene Materialgruppe → Kilometer eintragen | P; B | ≥1/0 | E | N/N/N/N | – |
| 28 | Arbeit → Fotos und Notizen → Datei; Kamera-/Pickergrenze separat | R; B/N/S; OS NT | ~3–5/0 | O | N/N/J/N | S3 |
| 29 | Arbeit → Fotos/Notizen → Notiz; zusätzlicher Nachtrag im Detail getrennt | R; B | ~2–3/~1 | E | N/N/N/N | S3 |
| 30 | Arbeiten → Eintrag → Bearbeiten → Korrektur → Speichern | P; B/N | ≥4/0 | E | N/N/N/N | – |
| 31 | Editor → Uhrzeiten/Zeit und Pause → Beginn/Ende; Dauer und Uhrzeiten erklärt | R; B/J | ~4–6/~1 | E | N/N/J/N | S3 |
| 32 | Editor → Zeit und Pause → Pause → speichern; Nettozeit nicht doppelt reduzieren | P; B/N | ~3–4/0 | E | N/N/J/N | – |
| 33 | Editor → Tätigkeit → speichern | P; B/N | ≥2/0 | E | N/N/N/N | – |
| 34 | Editor → Kunde/Auftrag; Auftrag wird nur passend übernommen | P; B/N | ~3–6/0 | E | N/N/J/N | – |
| 35 | Persönliches b.b. unabhängig von Arbeits-/Rechnungsstatus; Abkürzung verlangt Vorwissen | R; B/J/N | ~1–2/0 | X | N/N/N/J | S3 |
| 36 | b.b., App: verrechnet und Rechnung bezahlt sind fachlich getrennt, aber erklärungsbedürftig | R; B/J/N | 0/0 | D | N/N/N/N | S2 |
| 37 | Aktive Arbeit → Foto; Sitzung wird nach Abschluss mit Arbeit verbunden | R; B/N/S; Kamera NT | ~3–5/0 | O | N/N/J/N | S3 |
| 38 | Kundenakte → + Foto → Galerie/Kamera; Kunde vorgegeben | P; B/N; Kamera NT | ≥2/0 | E | N/N/J/N | – |
| 39 | Foto hinzufügen → Aufnahmephase wählen; Vorher/Während/Nachher | P; B | ~1–2/0 | E | N/N/N/N | – |
| 40 | Beim Import Beschreibung eingebbar; vorhandenes Foto: kein Bearbeiten im Betrachter | R; B/S | ≥1/0; später — | X | N/N/N/N | S2 |
| 41 | Vorhandenes Foto später einer Arbeit zuordnen: kein sichtbarer UI-Weg; MCP update_photo vorhanden | F; B/S/J | —/— | L | N/N/N/N | S1 |
| 42 | Akte/Fotobibliothek → Filter/Vorschau → Vollbild, Blättern und Zoomen | P; B | ~2–4/0 | R | N/N/J/N | – |
| 43 | Foto → Mehr → Papierkorb → Bestätigung; danach Undo/Wiederherstellen | P; B/N | ~3–4/0 | G | N/N/J/N | – |
| 44 | Kamera-/Dateiberechtigung: Speicherfehler/Entwurf getestet; echter OS-Entzug nicht durchgeklickt | NT; B/S Teilprüfung | —/— | O | J/N/N/N | offen |
| 45 | Bildbetrachter braucht weder Hash noch Pfad; Fallback kann Dateiname/Drive-Ladehinweis zeigen | R; B/S | 0/0 | R | J/N/J/N | S3 |
| 46 | Mehr → Projekte & Aufträge → Neu → Angaben → speichern | R; B/S | ~6–9/~1 | X | N/N/J/N | S2 |
| 47 | Auftrag aus Kundenakte übernimmt Kunden; globaler Dialog erfordert Auswahl | P; B/S/N | ~0–2/0 | E | N/N/J/N | – |
| 48 | Arbeit → optional Auftrag; Auswahl auf Kunden eingeschränkt | P; B/J | ~2–3/0 | E | N/N/J/N | – |
| 49 | Auftrag → Überblick → Bearbeiten → Änderung → speichern | R; B/S | ~4–5/~1 | X | N/N/N/N | S3 |
| 50 | Auftragsstatus Erledigt; Archivierung/Entfernung extra und wiederherstellbar | R; S/N/B | ~3–5/~1 | X | N/N/N/N | S2 |
| 51 | „Projekte & Aufträge“ ist eine gemeinsame Entität, kein zweites Projektsystem | R; B/S | 0/0 | X | N/N/J/N | S3 |
| 52 | Mehr → Planung → Neu → Kunde/Tätigkeit/Beginn/Dauer → speichern | R; B/N | ~8–11/~1 | E | J/N/J/N | S2 |
| 53 | Termin aus Kunde/Auftrag übernimmt Kontext; global bewusst auswählen | P; B/N | ~0–2/0 | E | N/N/J/N | – |
| 54 | Termin → Beginn/Dauer → speichern; Alternative bleibt zunächst Entwurf | P; B/N | ~3–5/0 | E | N/N/N/N | – |
| 55 | Termin absagen mit Bestätigung; Auftrag/Arbeit/Rechnung bleiben erhalten | P; B/N | ~2–3/0 | G | N/N/N/N | – |
| 56 | Wiederholung unter „Ort, Anfahrt, Pause und Wiederholung“ mit Intervall/Anzahl | R; B/S/N | ~4–6/~1 | X | N/N/N/N | S2 |
| 57 | Kundenakte → Termine → Neu, Kundenkontext vorgegeben | P; B/S | ~2–3/0 | E | N/N/J/N | – |
| 58 | Kalender: native Einrichtung/Konto/OAuth; tägliche Freigabe könnte EIN/AUS sein | R; B/S/N; Google NT | ~2 bis Einrichtung/~1 | O | J/N/J/J | S2 |
| 59 | Wetter: Funktion/Benachrichtigung EIN/AUS sinnvoll; Lizenz und Schlüssel bleiben Erstfreigaben | R; S/N; Anbieter NT | ~2 bis Einrichtung/~1 | O | J/N/J/J | S2 |
| 60 | Akte → Kontakt/weitere Aktionen → Angebot → umfangreiches Formular | R; B/N | ≥3/~1 | X | N/N/J/N | S2 |
| 61 | Dokumentart unverbindlicher Kostenvoranschlag + verständliche Bindungsbestätigung | P; B/N/J | ~2–3/0 zusätzlich | D | N/N/N/N | – |
| 62 | Dokumentart verbindlicher Kostenvoranschlag + erneute Bestätigung nach Wechsel | P; B/N/J | ~2–3/0 zusätzlich | D | N/N/N/N | – |
| 63 | Dokumentart Fixpreis; keine automatische Bindungsannahme | P; B/N/J | ~1–2/0 zusätzlich | D | N/N/N/N | – |
| 64 | Dokumentart Regie; Mengen als geplante Stunden und Preisgrundlage | P; B/N/J | ~1–2/0 zusätzlich | D | N/N/N/N | – |
| 65 | Baustein wählen → Menge; bestätigter Preis wird übernommen | P; B/N/J | ≥2/0 | E | N/N/J/N | – |
| 66 | Eigene Position: Titel/Beschreibung/Einheit/Menge/Preis/Steuer/Grundlage | R; B/S/N | ~5–9/~2 | D | N/N/J/N | S2 |
| 67 | Positionspreis ändern; Katalogbezug wird korrekt gelöst; Preisgrundlage wirkt technisch | R; B/J/N | ~1–3/~1 | D | N/N/N/N | S3 |
| 68 | Texte und Hinweise → editierbarer Text oder Baustein | P; B | ~2–3/0 | E | N/N/N/N | – |
| 69 | Prüfen und Vorschau; fehlende Angaben inline, keine erfundene Summe | P; B/N | ≥1/0 | D | N/N/J/N | – |
| 70 | Finalisieren → Prüfung → Vorschau/Bestätigung; keine automatische Rechnung | P; B/N/J | ~2–3/0 | D | N/N/N/N | – |
| 71 | Entwurf/finalisiert/geteilt/gesendet im Modell getrennt; Button „Finalisieren“ erklärungsbedürftig | R; B/S/N | 0/0 | D | N/N/J/N | S3 |
| 72 | Teilen-App ist kein Versandbeweis; Text sagt ausdrücklich „nicht bestätigt“ | P; S/N/J; realer Versand NT | ~1–2/0 | D | N/N/J/N | – |
| 73 | Offene Leistungen hat keine Rechnung-CTA; über Rechnungen → Neu → Leistungen → Arbeiten übernehmen | R; B/S/J/N | ~9–13/~3 | X | N/N/J/N | S2 |
| 74 | Rechnung: Kunde und Rechnungsadresse kontrollieren, Einsatzort getrennt erhalten | P; B/J/N | ~0–2/0 | D | N/N/J/N | – |
| 75 | Leistungen → Arbeitsaufzeichnungen übernehmen → Auswahl → übernehmen | R; B/S/J | ~3–5/~1 | X | N/N/J/N | S2 |
| 76 | Positionen/Mengen/Preis kontrollieren; keine erfundenen Preise | P; B/S/J/N | ~0–3/0 | D | N/N/J/N | – |
| 77 | Zahlungsziel zentral vorbelegt, im Entwurf prüfbar | P; B/J | ~0–1/0 | E | N/N/J/N | – |
| 78 | Abschließen nur nach Nummern-/Ausstellerfreigabe; lokaler Test prüft Blockade und Abschluss | R; N/J/S; produktiver Dienst NT | ~2–4/0 | D | J/N/J/N | S2 |
| 79 | Mehr → Rechnungen → Suchfeld/Statusfilter | P; B/S | ~3–4/0 | R | N/N/N/N | – |
| 80 | Entwurf/Gestellt/Bezahlt/Storniert getrennt; Versand nicht automatisch behauptet | P; B/J/N | 0/0 | D | N/N/J/N | – |
| 81 | Zahlung per MCP fachlich getestet; sichtbare Rechnungsaktionen bieten keine Zahlungsschaltfläche | F UI; S/N/J | —/— | L | N/N/N/N | S1 |
| 82 | Entwurf zeigt Steuerart, Empfängertyp, UID und Inlandsfall in normalem Formular | R; B/S | 0/0 | D | N/N/J/N | S2 |
| 83 | Kunden: ein lokales Suchfeld, sichtbare Treffer | P; B | ≥1/0 ab Liste | R | N/N/N/N | – |
| 84 | Arbeiten: Suche plus Zeitraum; fremde Filter zurücksetzbar | P; B | ~1–3/0 ab Liste | R | N/N/N/N | – |
| 85 | Fotos: Akte/Phase/Zeitraum; globale Bibliothek nur alle/ohne Kunde, keine freie Suche | R; B/S | ~3–6/~2 | X | N/N/J/N | S2 |
| 86 | Rechnungen: Textsuche und Statusfilter | P; B/S | ~1–2/0 ab Liste | R | N/N/N/N | – |
| 87 | Termine: Zeit-/Kundenkontext und Liste; übergreifender Suchpfad fehlt | R; B/S | ~3–5/~1 | X | N/N/J/N | S2 |
| 88 | Fünf getrennte Wiederfindewege; globale Suche nur als belegter Kandidat empfehlen | R; B/S/P | —/— | X | N/N/J/N | S2 |
| 89 | Archivieren führt intern in vorhandenen Wiederherstellungsbestand; Wörter variieren | R; S/N/B | ~2–4/~1 | G | N/N/N/N | S2 |
| 90 | Entfernen/Papierkorb direkt möglich, auch mit Fotos; Originale geschützt | P; B/N | ~1–3/0 | G | N/N/J/N | – |
| 91 | Mehr → Papierkorb → Wiederherstellen; bestehende Verknüpfungen werden geprüft | P; B/N | ~3–4/0 | G | N/N/J/N | – |
| 92 | Einzel-Purge oder Papierkorb leeren → eine mengen-/versionsgebundene Bestätigung | P; B/N/J | ~2–4/0 | G | N/N/N/N | – |
| 93 | Kunde aktiv AUS, Auftrag Erledigt, Archiv, Papierkorb nicht selbsterklärend zusammenhängend | R; B/S | 0/0 | X | N/N/N/N | S2 |
| 94 | Endgültiges Löschen geschützt; aktive Referenzen und gestellte Belege bleiben erhalten | P; B/N/J | ~1 Bestätigung/0 | G | N/N/J/N | – |
| 95 | Hash/Preview/CAS im normalen Papierkorb unsichtbar; Konflikt zeigt nur fachliche Entscheidung | P; B/N/J | 0/0 | G | N/N/J/N | – |
| 96 | Offline fachlich arbeiten; lokale Speicherung unabhängig von Netz | P; N/B/P | 0/0 zusätzlich | A | N/N/J/N | – |
| 97 | Offline Arbeit speichern; Wiederholung keine Doppelbuchung | P; N/B/P | ~1/0 zusätzlich | E | N/N/J/N | – |
| 98 | Offline Kunde speichern und später ändern; Vorgängerkette erhalten | P; N/B/P | ~1/0 zusätzlich | E | N/N/J/N | – |
| 99 | Offline Foto sichern; getrennte Medienwarteschlange, Fehler bewahrt Entwurf | P; N/B | ~1–3/0 zusätzlich | E | N/N/J/N | – |
| 100 | App-/Store-Neustart nach synthetischen Änderungen | P; N/B | ~1–2/0 | A | N/N/J/N | – |
| 101 | Testdaten, IDs, Beziehungen nach Wiederöffnung erhalten | P; N/B/P | 0/0 | A | N/N/J/N | – |
| 102 | Netz/Provider wieder verfügbar: synthetischer Retry, kein echtes Google-Konto | P Teilstrecke; N/P; Google NT | 0/0 | A | N/N/J/N | – |
| 103 | Drive auslösen: UI jetzt abgleichen; Provider- und Direkt-HTTP-Test doubles getrennt | P Teilstrecke; N/P; Drive live NT | ~1–3/~1 | T | J/N/J/N | S2 |
| 104 | Unterbrechung/401/429/Schreibfehler im isolierten Provider, alte Daten/Queue bleiben | P; N/P/J/B | 0/0 für Fehlerauslösung | A | N/N/J/N | – |
| 105 | Fehlerstatus unterscheidet Konflikt und Netz; rohe Fehlermeldung kann ins normale UI gelangen | R; B/S/N | ~0–2/~1 | T | J/N/J/N | S2 |
| 106 | Keine klare alltägliche Aussage „letzte geprüfte Sicherung“; Sync ist kein Vollbackupnachweis | F UX; B/S/N | —/— | L | J/N/J/N | S1 |
| 107 | Automatische Geschäftssicherung prinzipiell sinnvoll; umfassender automatischer Backupzyklus nicht nachgewiesen | R; S/N | 0/0 | A | N/N/J/J | S2 |
| 108 | EIN/AUS kann Sicherungsabsicht abbilden, nicht Freigabe/Erfolg/Restore ersetzen | R; B/S | 0/0 | T | J/N/J/J | S2 |
| 109 | Sync-/Verbindungs-/Historienflächen sichtbar; keine eigenständige verständliche Backupverwaltung gefunden | R; B/S | ~2–4/~1 | T | J/N/J/N | S2 |
| 110 | Alle gefundenen Einstellungen einzeln bewertet, siehe Inventar unten | R; B/S/N/J | unterschiedlich; unten | T | J/N/J/N | S2 |
| 111 | Bestehenden synthetischen Bestand schließen; keine Deinstallation/kein Löschen | P; N/B | ~1–2/0 | A | N/N/J/N | – |
| 112 | Operative App wieder öffnen, gleicher Testbestand | P; N/B | ~1/0 | A | N/N/J/N | – |
| 113 | Arbeits-/Angebots-/Termin-Entwürfe und private Dateien bleiben im geprüften Scope erhalten | P; N/B | 0/0 | A | N/N/J/N | – |
| 114 | Laufende/pausierte Sitzung bzw. offener Abschluss rekonstruierbar, kein zweiter Timer | P; N/B/J | ~0–1/0 | A | N/N/J/N | – |
| 115 | Normaler Neustart ohne technischen Wiederherstellungsdialog; Foto-Fehler fordert nur gezielte Aktion | P; B/N/S | ~0–1/0 | A | N/N/J/N | – |

Bei 28/37/38 bedeutet das P/R ausschließlich den tatsächlich geprüften Import-/Zuordnungs- und Speicherpfad. Ein Kameragerät wurde nicht vorgetäuscht. Bei 70/72/78 wurden synthetische Dokumente lokal erzeugt bzw. native Grenzen geprüft; keine echte Nachricht versendet und keine produktive Nummer vergeben. 78 bleibt für echte Konten ein offener Endtest. Bei 111–114 belegen native Store-Neustarts und der WebView-Lifecycle den jeweiligen Scope, keinen Boot-/Akku-/Speicherstress über mehrere Tage.

Zusätzlich zum nummerierten Alltagsszenario: Pflanzenbibliothek/Neophyten, Pflanzenbild-Blättern, Gartenpirat-Fotooptionen, Leistungskatalog, b.b.-Persistenz, Batch-Papierkorb und schmale Ansichten wurden über ihre vorhandenen Regressionssuiten erneut geprüft. Rollen-/MCP-Katalog, Berechtigungen, erlaubte Felder und Rückgaben bleiben durch P1-Contract/JVM/native Tests geschützt. Die 156 semantischen Katalogaktionen verteilen sich auf Kunden 13, Aufträge 12, Arbeiten 14, Zeiterfassung 10, Rechnungen 13, Angebote 13, Fotos 12, Sicherung 6, Synchronisierung 10, Papierkorb 6, Einstellungen 7, Termine 20, Leistungskatalog 5, Pflanzen 4 und Plattform/Darstellung 11. Das ist eine Vertragsinventur, keine Behauptung von 156 erfolgreich durchgeklickten Benutzerabläufen. Insbesondere Backup, Fotoänderung und Zahlung illustrieren die Differenz zwischen vorhandenem MCP-Fachweg und auffindbarer Oberfläche.
#### Einstellungen: vollständiges gefundenes Bedieninventar zu Aufgabe 110

56 einzelne Einstellungs-/Einrichtungsentscheidungen wurden erfasst: 2 allgemeine, 25 Unternehmens-/Rechnungsfelder, 21 Planungs-/Kalender-/Wetterfelder, 3 Drive-/Mailfreigaben, 4 ChatGPT-Verbindungsfelder und 1 Gartenpirat-Präferenz. Kontoauswahl und Freigabe sind Einrichtungsschritte, keine gewöhnlichen dauerhaften Formularfelder. Filter, Kunde aktiv, Foto-Aufnahmephase, Dokumentart und Vertragsweg sind Fachentscheidungen im jeweiligen Datensatz und bereits in der Aufgabenmatrix bewertet. Geheimnisfelder wurden ausschließlich anhand ihrer Definition bewertet; keine gespeicherten Werte gelesen.

Nachweis W/B/G = B/S, PL/D/T = S plus passende native Grenz-/Berechtigungstests; echte Konto-/Schlüssel-Freigaben NT. „Entfernen“ bedeutet hier nur aus täglicher Darstellung entfernen, niemals Werte, Rechte oder Datenmodell löschen. Alle Zeilen haben bei derzeitiger Darstellung mindestens leichte Reibung, soweit „Erweitert“ empfohlen wird; notwendige Fachangaben sind kein UX-Fehler. Standard „beibehalten“ darf keinen unbekannten Wert erfinden.

| ID | Einzelne Einstellung | Muss Eigentümer entscheiden? | Sicherer Standard / Automatik | EIN/AUS ausreichend? | Empfohlener Ort / technische Notwendigkeit |
|---|---|---|---|---|---|
| W1 | Seitlich zwischen Hauptseiten wischen | optional | bisher AUS behalten; explizites Opt-in | ja | Bedienung; keine technische Kenntnis nötig |
| W2 | Fotoqualität | optional | speicheroptimiert, kleine Bilder schonen | nein, 3 sinnvolle Profile | Fotos/Erweitert; keine Pixel-/Qualitätszahlen im Alltag |
| B1 | Geschäftsname | ja, einmalig | vorhandenen geprüften Wert übernehmen | nein | Unternehmen; fachlich nötig |
| B2 | Rechtlicher Ausstellername | ja, einmalig | nie erfinden | nein | Unternehmen/Ersteinrichtung |
| B3 | Geschäftsstraße | ja, einmalig | geprüften Bestand übernehmen | nein | Unternehmen/Ersteinrichtung |
| B4 | Geschäfts-PLZ | ja, einmalig | geprüften Bestand übernehmen | nein | Unternehmen/Ersteinrichtung |
| B5 | Geschäftsort | ja, einmalig | geprüften Bestand übernehmen | nein | Unternehmen/Ersteinrichtung |
| B6 | Geschäfts-E-Mail | ja | vorhandenen Wert übernehmen | nein | Unternehmen/Kontakt |
| B7 | Geschäftstelefon | ja | vorhandenen Wert übernehmen | nein | Unternehmen/Kontakt |
| B8 | Bankname | ja, falls verwendet | vorhandenen Wert übernehmen | nein | Unternehmen/Zahlung, nicht täglicher Editor |
| B9 | IBAN | ja | nie ableiten/erfinden | nein | Unternehmen/Zahlung; Prüfung erhalten |
| B10 | BIC | falls fachlich benötigt | leer erlaubt, wenn nicht erforderlich | nein | Zahlungsdetails/Erweitert |
| B11 | UID des Unternehmens | abhängig vom bestätigten Steuerfall | keine fiktive UID | nein | Unternehmen/Steuer |
| B12 | Steuerbefreiungshinweis | ja/fachlich prüfen | bestätigten Text wiederverwenden | nein | Unternehmen/Steuer, nicht jedes Dokument neu |
| B13 | Standard-Zahlungsziel in Tagen | ja, einmalig | bestätigten Standard übernehmen | nein | Zahlung; sicher vorbelegen |
| B14 | Standard-Zahlungsbedingungen als Text | ja, einmalig | aus bewusst gepflegtem Standard, Konsistenz prüfen | nein | Zahlung; keine zweite tägliche Abfrage desselben Ziels |
| B15 | Hinweis verbindlicher Kostenvoranschlag | ja, bei Einrichtung/Änderung | bestätigten Text wiederverwenden | nein | Dokumenttexte/Erweitert; Bindung weiter bewusst wählen |
| B16 | Hinweis unverbindlicher Kostenvoranschlag | ja, bei Einrichtung/Änderung | bestätigten Text wiederverwenden | nein | Dokumenttexte/Erweitert |
| B17 | Hinweis Regieangebot | ja, bei Einrichtung/Änderung | bestätigten Text wiederverwenden | nein | Dokumenttexte/Erweitert |
| B18 | Rechnungspräfix | ja, Nummernkreis festlegen | bestehenden Kreis erhalten | nein | geschützte Rechnungs-Ersteinrichtung |
| B19 | Zuletzt vergebene Rechnungsnummer/Zähler | ja, geprüft übernehmen | nie blind hoch-/zurücksetzen | nein | geschützte Einrichtung/Diagnose, nicht tägliche Einstellung |
| B20 | Weitere Unternehmensangaben | falls nötig | vorhandenen Text übernehmen | nein | Unternehmen/Erweitert |
| B21 | Steuermodus | ausdrücklich ja | ungeklärt bleibt ungeklärt | nein | Unternehmen/Steuer; niemals automatisch Kleinunternehmer annehmen |
| B22 | Bestehender Nummernkreis: eigen/sevDesk | ausdrücklich ja | bestehende Autorität erhalten | nein | geführte einmalige Einrichtung |
| B23 | Ausstellerdaten geprüft | ausdrücklich ja | keine automatische Zustimmung | ja als Bestätigung, kein beliebiger Alltagsschalter | Ersteinrichtung; bei relevanter Änderung erneut prüfen |
| B24 | Nummernkreis/Überschneidungen geprüft | ausdrücklich ja | keine automatische Zustimmung | ja als Bestätigung | Ersteinrichtung, fachlich sicherheitsrelevant |
| B25 | Pixel führt allein Rechnungsnummernkreis | ausdrücklich ja | kein automatischer Autoritätswechsel | nein, fachliche Einrichtung mit Voraussetzungen | zusammen mit B22/B24; nicht technische Providerwahl verlangen |
| PL1 | Kalender-Google-Konto | ja, bei Verbindung | vorhandenes autorisiertes Konto vorschlagen | nein | Ersteinrichtung; danach Status und EIN/AUS |
| PL2 | Hauptkalender-Belegtzeiten lesen | optional bewusst | AUS bis Freigabe | ja | Kalender/Erweitert; Zusatzrecht klar benennen |
| PL3 | Montag Arbeitstag | ja, falls Standard abweichend | bestehenden Wochenplan verwenden | ja | eine gemeinsame Wochenplanauswahl |
| PL4 | Dienstag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL5 | Mittwoch Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL6 | Donnerstag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL7 | Freitag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL8 | Samstag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL9 | Sonntag Arbeitstag | wie PL3 | wie PL3 | ja | Wochenplan |
| PL10 | Vorschlags-Arbeitsbeginn | optional | vorhandenen validierten Wert, sonst 08:00 | nein | Wochenplan, nicht jedes Mal abfragen |
| PL11 | Vorschlags-Arbeitsende | optional | vorhandenen validierten Wert, sonst 17:00 | nein | Wochenplan |
| PL12 | Anfahrtpuffer | optional | vorhandenen Wert verwenden, keine erfundene Fahrzeit | nein | Planung/Erweitert |
| PL13 | Wetterprüfung aktivieren | ja | AUS bis echter Zugang/Lizenz bestätigt | ja | normale Einstellung „Wetterhinweise“ |
| PL14 | Gewerblicher Wetterzugang freigegeben | ausdrücklich ja | niemals automatisch bejahen | nein als täglicher Schalter | einmalige Zugangseinrichtung |
| PL15 | Wetter-API-Schlüssel | Kontoinhaber einmalig am Gerät | privat lassen, vorhandenen Schlüssel behalten | nein | geschützte Einrichtung; nie Chat/Diagnoseausgabe |
| PL16 | Wetterrisiko-Benachrichtigungen | ja | respektiert vorhandene Präferenz und OS-Recht/Ruhezeit | ja | normale Einstellung, kein technisches Wissen nötig |
| PL17 | Regen mm/h | nur fortgeschrittene fachliche Anpassung | geprüfter Ausgangswert 1, nicht Sicherheitsgarantie | nein | Wetter/Erweitert; im Alltag verbergen |
| PL18 | Regenwahrscheinlichkeit % | wie PL17 | Ausgangswert 60 | nein | Wetter/Erweitert |
| PL19 | Böen km/h | wie PL17 | Ausgangswert 40 | nein | Wetter/Erweitert |
| PL20 | Frost °C | wie PL17 | Ausgangswert 0 | nein | Wetter/Erweitert |
| PL21 | Hitze °C | wie PL17 | Ausgangswert 30 | nein | Wetter/Erweitert |
| D1 | Drive-Google-Konto | ja, einmalig | autorisierte Verbindung erhalten | nein | Sicherung/Verbindung einrichten; danach EIN/AUS der Absicht |
| D2 | Link/ID des bestehenden Drive-Ordners | einmalig fachlichen Zielordner wählen, keine ID verstehen | bestehende geprüfte Zuordnung weiterverwenden | nein | Einrichtung mit Ordnerauswahl; rohe ID danach Diagnose |
| D3 | Gmail-Versandbelege nur lesen | optional ausdrücklich | AUS bis Freigabe; kein Versandrecht implizieren | ja für aktivieren/deaktivieren | Versand/Erweitert; keine tägliche Kontowahl |
| T1 | Pixel-Tunnel-ID | Administrator bei Einrichtung | bestehende bestätigte Verbindung erhalten | nein | ausschließlich Erweitert/Verbindung |
| T2 | OpenAI-Organisation | Administrator bei Einrichtung | verifizierte Zuordnung erhalten | nein | ausschließlich Erweitert/Verbindung |
| T3 | Runtime-Schlüssel | Kontoinhaber sicher am Gerät | vorhandenen verschlüsselten Schlüssel behalten | nein | geschützte Einrichtung, nicht normale Bedienung |
| T4 | ChatGPT darf Eigentümerfunktionen lesen/schreiben | Eigentümer ausdrücklich | vorhandene bestätigte Freigabe erhalten | ja | „ChatGPT-Steuerung“; Kontofreigabe bleibt zusätzlich erforderlich |
| G1 | Eigenes Gartenpirat-Bild automatisch anzeigen | optional | vorhandene Auswahl erhalten, nicht mit Geschäftsfotos vermischen | ja | Bildoptionen; kein normaler Betriebsdialog nötig |

Weitere gespeicherte Zustände sind **keine zusätzlichen Benutzerentscheidungen**: Launcherposition, Such-/Zeitraumfilter, Formularentwürfe, stabile Request-IDs, Vergleichshashes, Vorgängerkette, Wiederholungszähler und Bestätigungsbelege. Diese gehören automatisch verwaltet. `default_activity` und `default_location` sind über vorhandene Owner-Einstellungen verfügbar und werden im Formular übernommen; kein separater sichtbarer Einstellungseditor dafür wurde gefunden. Das Inventar erfindet deshalb keine zwei weiteren UI-Schalter. Kamera, Mikrofon, Benachrichtigungen, Dateien und Akku/Hintergrundbetrieb bleiben Android-Rechte; nachgewiesene Freigabe darf nicht durch ein dekoratives App-EIN ersetzt werden.

#### 1. Gesamturteil zur Bedienbarkeit

**Für die tägliche Kernarbeit brauchbar, das geforderte Black-Box-Prinzip jedoch nur teilweise erfüllt.** Vier Hauptziele, große Start-/Pause-/Ende-Aktionen, Kundenkontext, optionale Kontaktfelder, Entwurfserhalt, Foto-Pagination und echte Schutzgrenzen sind tragfähig. Die stärkste Belastung liegt in Einrichtung/Sync, Dokumenterstellung und uneinheitlich platzierten Folgeaktionen. „Die Technik interessiert mich nicht“ funktioniert beim Timer überwiegend, bei Sicherung und Rechnungsabschluss noch nicht zuverlässig aus Sicht dieser Persona. Erfolgreiche Regressionen sind kein Beweis guter Auffindbarkeit. Die visuelle Lesbarkeit bei 320/360/426 px und vergrößerter Schrift ist in den geprüften Chromium-Fällen gut; die native Sichtprüfung bleibt durch den in Phase D beschriebenen System-UI-Fehler eingeschränkt; der frühere extrem schmale b.b.-Zeilenfehler wurde hier nicht wieder als aktueller Fehler behauptet.

#### 2. Blockierende UX-Probleme

- **U01 / S1:** Vorhandenes Foto später einer Arbeit zuordnen bzw. Beschreibung korrigieren ist im Betrachter nicht bedienbar. `update_photo` existiert, aber der sichtbare Weg endet bei Blättern/Zoom/Papierkorb. Ein erneuter Import wäre ein schlechter, duplizierender Workaround.
- **U02 / S1:** Rechnungszahlung hat einen getesteten MCP-Fachweg, aber im untersuchten Rechnungsdetail/-aktionsbereich keinen gleichwertig auffindbaren UI-Weg. b.b. darf diesen Weg nicht ersetzen.
- **U03 / S1:** Die Oberfläche liefert keinen klaren allgemeinen Sicherungs-/Wiederherstellungszugang mit letzter geprüfter Geschäftssicherung. Die sechs Katalogeinträge unter Sicherung und vorhandene Owner-Tools machen diesen Ablauf nicht automatisch für einen manuellen Benutzer auffindbar. „Drive eingerichtet“ beweist keine vollständige Sicherung.
- **U04 / S2, Einrichtung kann S1 erreichen:** Rechnungsabschluss hängt von bewusst bestätigtem Aussteller/Nummernkreis und geeignetem Drive-Zugang ab. Die Schutzbedingung ist richtig; wechselnde Texte über Nummernservice, Pixel, Rücklesen und erneutes Synchronisieren liefern keinen einheitlichen nächsten fachlichen Schritt. Ein aktueller technischer Deadlock wurde in den synthetischen Tests nicht reproduziert.

Kein nachgewiesener neuer S0. Wiederherstellbare Entfernung, unveränderliche Originalrechnungen, bewusste Bindungswahl, ausstehender Versandnachweis und versionsgebundene Löschbestätigung bleiben positiv und dürfen nicht einer kürzeren Bedienung geopfert werden.

#### 3. Top 10 unnötig komplizierte bestehende Funktionen

| Rang | Befund | Weshalb unnötig kompliziert? |
|---:|---|---|
| 1 | U05 Sync-/Speicherstatus | Nutzer muss lokal, Übergabe, Rücklesen und Cloud selbst gedanklich zusammensetzen |
| 2 | U06 Rechnungsabschluss-Einrichtung | gemeinsames fachliches Ziel verteilt auf Unternehmensdaten, Nummernkreis und technische Verbindung |
| 3 | U07 Rechnung aus mehreren offenen Arbeiten | Abrechnungsübersicht zeigt Arbeiten, aber startet keine gemeinsame Rechnung |
| 4 | U08 Angebotsformular | vier Dokumentarten, Kundentyp, Vertragsweg, Preisgrundlage und viele Folgebuttons gleichzeitig |
| 5 | U09 Angebot in Kundenakte finden | Erstellung unter Kontakt, Bestand unter Fotos & Dateien |
| 6 | U10 Planung/Wiederholung | Serie steckt gemeinsam mit Ort, Anfahrt und Pause in einem langen Zusatzbereich |
| 7 | U11 Drei Bezahl-/Abrechnungsbegriffe | b.b., App: verrechnet und Rechnungszahlung fachlich verschieden, optisch ähnlich |
| 8 | U12 Archiv/Entfernen/Deaktivieren | ein Wiederherstellungsbestand und mehrere Lebenszyklusbegriffe ohne gemeinsamen Einstieg |
| 9 | U13 Historische Konfliktbedienung | auch ohne aktive Fälle sichtbare Wartungsaufgabe im normalen Sync-Bereich |
| 10 | U14 Verstreutes Wiederfinden | Kunde, Arbeit, Foto, Rechnung und Termin benötigen unterschiedliche Bereiche/Filter |

#### 4. Top 10 zu vereinfachende Funktionen/Workflows – konkrete Vorschläge

Alle Nachher-Zahlen sind **Zielwerte**, keine gemessenen Verbesserungen. Aufwand klein/mittel/groß relativ zur bestehenden App, kein Terminversprechen. Zeitnutzen ist ohne Benutzerstudie nicht in Sekunden belegt. Nummern-/Bindungs-/Löschbestätigungen und ungeklärte Konflikte werden nicht automatisch erledigt.

| ID / Priorität | Problem / aktueller Ablauf | Vereinfachter Ablauf, nur Vorschlag | Aktionen vorher → nachher | Nutzen | Aufwand | Regressionsrisiko |
|---|---|---|---|---|---|---|
| U05 / P1 | Heute/Settings → Drive → Übertragungszustände deuten | überall ein wahrer Zustand: „Auf Gerät gespeichert“, „Mit Drive gesichert“ nur mit passendem Nachweis, sonst klare nächste Aktion; Details erweitert | ~2–4 → 0 im Normalfall, 1 bei Fehler | täglich weniger Prüfbedarf, keine falsche Sicherheit | mittel | mittel: Nachweisstufen weiter intern getrennt |
| U06 / P1 | Unternehmen prüfen → Nummernkreis prüfen → technische Drive-Verbindung → zurück → Abschluss wiederholen | einmalige geführte fachliche Einrichtung, danach „Rechnung abschließen“ mit einer Zusammenfassung | ~8–15 Einrichtung → ~4–7; normal ~2–4 → 2 | weniger Abbrüche, eindeutiger nächster Schritt | mittel | hoch: Autorität/Parallelkreis unverändert schützen |
| U07 / P1 | Offene Leistungen lesen → Rechnungen → Neu → Kunde → Leistungen → Arbeiten auswählen → übernehmen | bei Empfänger „Rechnung vorbereiten“ → vorgeschlagene offene Arbeiten/Preise prüfen → Entwurf | ~9–13 → ~4–6 | wiederkehrende Abrechnung deutlich kürzer | mittel | hoch: Empfänger, bekannte Preise, Doppelabrechnung |
| U08 / P1 | Alle Angebotsfelder und Prüfen/Finalisieren gleichzeitig | Kunde → bewusste Dokumentart → Leistungen → Prüfen; geprüfte Stammdaten nur bei Abweichung; eine nächste Hauptaktion | ~12–18 → ~8–11 für Katalogfall | weniger Such-/Scrollschritte, Rechtswahl bleibt sichtbar | mittel | hoch: Typ-/Preis-/B2C-Validierung |
| U09 / P1 | Akte → Kontakt → neues Angebot; Akte → Fotos & Dateien → vorhandenes Angebot | Akte → Dokumente mit Angebot/Kostenvoranschlag/Rechnung und „Neu“ | ~2–3 → 1–2 | tägliches Wiederfinden ohne Umweg | klein–mittel | mittel: Kontext/Rückkehr/Entwürfe |
| U10 / P2 | Mehr → Planung → Termin → verstreute Zusatzfelder/Serie | bestehender Kunden-/Auftragskontext → Datum/Dauer → optionale klar benannte Wiederholung | ~8–11 → ~5–7; Serie ~4–6 zusätzlich → ~2–3 | Termine schneller, Serien leichter auffindbar | mittel | mittel: Zeitzone, Wiederholung, Kalender |
| U11 / P1 | b.b.-Knopf, App-Verrechnet-Flag und Rechnung separat erklären | „Persönlich erledigt/bezahlt“ eindeutig von „Rechnung offen/bezahlt“ unterscheiden, bestehende Bedeutung beibehalten | ~1–2 → 1, Verständnisaufwand sinkt | weniger falsche Zahlungsannahmen | klein–mittel | hoch bei fachlicher Vermischung; keine Datenkonvertierung |
| U12 / P1 | Aktiv-Checkbox/Erledigt/Aktionen/Archiv/Papierkorb getrennt suchen | klare Datensatzaktionen „Nicht mehr aktiv“ oder „In Papierkorb“; Wiederherstellen an einem Ort | ~3–6 → ~2–3; Purge bleibt bestätigt | weniger versehentliche falsche Aktion | mittel | hoch: Beziehungen, Belegschutz, Restore |
| U13 / P2 | Drive öffnen → Alt-Konfliktfunktionen trotz 0 Vorgängen | nur „Handlung nötig“ bei tatsächlichem Fall; Historie in Erweitert | ~1–3 Prüfaktionen → 0 ohne Fall | weniger unnötige Wartungsentscheidungen | klein | mittel: aktive Konflikte dürfen nicht verschwinden |
| U14 / P2 | Zielbereich erraten → lokaler Filter → zurück → anderer Bereich | übergreifender Lesezugang zu vorhandener Suche, gruppierte Treffer; keine neue Datenhaltung | ~3–6 → ~2–3 | weniger Fehlwege bei alten Einträgen | mittel | mittel: Sichtbarkeit/Archiv/Entwurfsschutz |

Weitere konkret belegte Ergänzungen: **U01** Foto → Mehr → Bearbeiten → Beschreibung/Arbeit → speichern statt derzeit nicht ausführbar (Ziel ~4–6, P1, mittel, mittleres Relationsrisiko); **U02** Rechnung → Zahlung erfassen → bestätigen statt derzeit nicht ausführbar (Ziel ~2–3, P1, klein–mittel, hohes Risiko bei b.b.-Verwechslung); **U03** Einstellungen → Sicherung zeigt geprüften Zeitpunkt und „Wiederherstellen“ führt über bestehende Prüfung statt technischem Suchweg (Normalstatus Ziel 0–1, Restore nicht verkürzen, P1, mittel, hohes Restore-/Nachweisrisiko). **U15** bestehendes Arbeitsdetail → Bearbeiten → Zurück speichert, Auftrag/Kunde dagegen haben Verwerfen-Dialoge: konsistente sichtbare Rückmeldung/Begriffe vorschlagen, ohne das bestehende Speicherverhalten zu ändern (Schrittzahl ~2–4 → ~2–3, P1, mittel, hohes Entwurfsrisiko). **U16** gleiche große Startaktion mit zwei ähnlichen Textzeilen und abweichendem Leertexthinweis: Begriffe vereinheitlichen (0 Aktionen Unterschied, P3, klein, niedrig). **U17** Wetter-/Kalendereinrichtung unter einer Betriebsoption anbieten, technische Felder erweitert (Einrichtungszahl von Freigaben abhängig; keine pauschale Klickzahl, P2, mittel, mittleres Rechte-/Kostenrisiko).

#### 5. Funktionen, die EIN/AUS werden sollten

- **Google Drive-Verbindung/Sicherungsabsicht:** EIN/AUS nach verifizierter Einrichtung; Erfolg weiterhin separat mit verständlichem Datum/Fehler anzeigen. AUS löscht keine Daten und beendet nicht stillschweigend erforderliche lokale Sicherung. Synchronisation ersetzt keine versionierte Vollsicherung.
- **ChatGPT-Steuerung:** EIN/AUS als Betriebsfreigabe, vorhandene Owner-Autorisierung und Client-/Kontorechte behalten. Tunnel-ID/Organisation/Schlüssel kein tägliches Bedienmodell.
- **Kalenderabgleich:** EIN/AUS, bestehende Kalendertermine beim Trennen erhalten; zusätzliche Hauptkalender-Lesefreigabe getrennt optional.
- **Wetterhinweise / Benachrichtigungen:** jeweils EIN/AUS; keine automatische Lizenzzustimmung, keine versteckten Kosten und keine vorgetäuschte OS-Freigabe.
- Bereits angemessen: Seitenwischen und eigenes Gartenpirat-Bild automatisch anzeigen. Fotoqualität bleibt bewusst dreistufig; Dokumentart, Steuermodus, Nummernkreis und rechtliche Bindung sind **keine** sinnvollen EIN/AUS-Entscheidungen.

#### 6. Funktionen, die automatisch laufen sollten

Vorhandene Wiederholung/Idempotenz/Hashprüfung/Medienreihenfolge, sichere lokale Speicherung, Entwurfserhalt, Fotooptimierung, Vorschauladen und Kunden-/Auftragsübernahme ohne Konflikt automatisch weiterführen. Ohne Benutzerentscheidung veröffentlichen, was bereits fachlich bestätigt wurde; nicht allein wegen einer ausstehenden unabhängigen Medienübertragung andere Arbeitsdaten blockieren. Automatische Hintergrundwiederholung bei Netzfehlern ist bereits vorhanden, nicht als neue Funktion verkaufen. Sichtbare neue Meldung nur bei tatsächlichem Handlungsbedarf oder Speicherfehler.

Automatischer Sicherungsplan wäre sinnvoll, ist aber erst nach geprüfter Vollständigkeit, Aufbewahrung und Wiederherstellbarkeit als aktiv darstellbar. Niemals ungeklärte Konflikte, alte Löschaufträge, Preise, Rechtsart, Nummernautorität, Kalender-Verschiebung oder Versandempfänger automatisch entscheiden. Der Nutzer entscheidet den Betrieb; die App prüft und transportiert im Hintergrund.

#### 7. Aus normalem UI nach Erweitert/Diagnose verschieben

Übertragungs-/Warteschlangendetail, Provider-/Transportnachweise, technische Dateinamen, Alt-Konflikt-Wartung bei null aktiven Fällen, Konflikt-ID-/Hashhistorie, Tunnel-/Organisation-/Runtime-Einrichtung, Akku-/Hintergrunddiagnose, Drive-Ordner-ID, Wetter-API-/Schwellenkonfiguration und Rechnungsnummern-Zähler. Bei echtem Fehler darf die normale UI verständlich sagen, was zu tun ist; technische Diagnose ist zusätzlich aufklappbar.

Schon richtig gelöst und beibehalten: `#sync-technical` ist eingeklappt; `#sync-reviews` ist grundsätzlich eingeklappt; Rohdaten/Hashes sind im Papierkorb nicht Voraussetzung. Diese Bereiche nicht fälschlich als ständig offene Technikflächen zählen. Der normale Settings-Absatz und die historische Wartung außerhalb dieser Details bleiben trotzdem sichtbar.

#### 8. Doppelte oder überlappende Funktionen

Keine zweite operative Datenbank, kein zweites Projektsystem und kein zweites Angebotsmodell gefunden bzw. vorgeschlagen. Überlappungen betreffen **Zugänge und Begriffe**: Sync über Statuspille, Settings und Mehr; Angebot-Neuanlage unter Kontakt und Bestand unter Fotodokumenten; Fotos im Kundenkonto und globale Bibliothek; Rechnung aus Einzeleintrag und separater Rechnungseditor; Gartenpirat über schwebenden Knopf und Mehr; „Projekt/Auftrag“ als Doppelname. Mehrere kontextuelle Einstiege sind nützlich, wenn sie in denselben Workflow führen. Nicht einfach entfernen. b.b. und Rechnungszahlung sind absichtlich unterschiedliche Fachziele, keine Daten-Dubletten.

#### 9. Einstellungen, die entfernt werden können

Aus der täglichen Seite entfernbar: Warteschlangenzählung, Übertragungsstufe und technische Erklärung sowie technische Verbindungs-/Schwellenfelder. Aus der Anwendung fachlich **nicht** ersatzlos entfernen: geprüfte Aussteller-/Steuer-/Nummernkreisangaben, Datenschutz-/Kontofreigaben, Originalqualität-Option, Benachrichtigungspräferenz. Kein belegter Grund, die 56 Entscheidungen aus dem Datenmodell zu löschen. Gemeinsam geführte Einrichtung kann B22/B24/B25 bündeln, muss aber alle drei Schutzvoraussetzungen weiter prüfen. B13/B14 gemeinsam pflegen und Widersprüche anzeigen; freie Zahlungsbedingungen nicht kommentarlos überschreiben.

#### 10. Workflows mit zu vielen Schritten

Größte belegte Wegelängen: Mehr → Offene Leistungen → anderer Bereich → Rechnung → Arbeiten übernehmen (~9–13); globaler Termin (~8–11); Angebot mit Katalog (~12–18 einschließlich fachlicher Entscheidungen); Deaktivieren/Archivieren bei unklarer Wortwahl (~3–6). Dokumentprüfung und endgültige Bestätigung sind keine unnötigen Schritte. Timerpause/-fortsetzen sind bereits je eine Aktion und sollten keine neue Zwischenbestätigung erhalten. Kundenanlage bleibt mit ≥7 Aktionen im geprüften Name-/Adressfall vertretbar; keine zusätzlichen Pflichtfelder hinzufügen. Eine Zeitersparnis in Minuten wurde nicht gemessen.

#### 11. Top 10 Kandidaten für neue Funktionen mit belegtem Alltagsbezug

Es sind **zehn geprüfte Kandidaten**, keine künstliche Empfehlung zehn neuer Produktfunktionen. Nur fehlende Bedienwege und wiederkehrende nachgewiesene Such-/Abrechnungsarbeit begründen Erweiterungen. Häufigkeit ist eine Persona-Annahme; Zeitnutzen nur als Aktions-/Fehlerreduktion, nicht als erfundene Sekundenmessung. Bewertung N=Nutzen/H=Häufigkeit, Aufwand/UI/Wartung/Risiko niedrig/mittel/hoch.

| Rang | Kandidat / konkrete Evidenz | N / H | Zeit-/Fehlernutzen | Aufwand / zusätzliche UI / Wartung / Risiko | Empfehlung |
|---:|---|---|---|---|---|
| 1 | Vorhandenes Foto bearbeiten/Arbeit zuordnen; Aufgabe 41 ohne UI-Weg | hoch / regelmäßig | derzeit unmögliche Korrektur wird ~4–6 Aktionen, kein Reimport-Duplikat | mittel / niedrig im Mehr-Menü / niedrig / mittel | P1: bestehenden update_photo-Fachweg bedienbar machen |
| 2 | Rechnungszahlung im Detail; Aufgabe 81 ohne UI-Weg | hoch / regelmäßig | ~2–3 statt Hilfsweg; b.b.-Fehlersatz vermeiden | klein–mittel / niedrig / niedrig / hoch fachlich | P1: vorhandenen Zahlungsfachweg exponieren |
| 3 | Sicherungsübersicht/Wiederherstellungs-Einstieg; Aufgabe 106 | hoch / seltene Aktion, tägliche Sicherheit | Normalprüfung 0–1 statt Techniklesen; weniger falsche Sicherheit | mittel / niedrig / mittel / hoch | P1: vorhandene Backupdienste nutzen, keinen zweiten Speicher |
| 4 | Rechnung aus Empfängergruppe; Aufgabe 73 | hoch / regelmäßig | ~3–7 Aktionen weniger, weniger falsche Empfänger | mittel / niedrig / mittel / hoch | P1: Kontextübergabe, keine neue Rechnungslogik |
| 5 | Ein gemeinsamer Dokumente-Einstieg in Kundenakte; Aufgabe 16 | hoch / regelmäßig | ~1–2 Suchaktionen weniger | klein–mittel / sinkend / niedrig / mittel | P1: Navigation konsolidieren, keine neue Entität |
| 6 | Übergreifende Suche; Aufgaben 83–88 | mittel–hoch / regelmäßig | ~1–3 Bereichswechsel weniger | mittel / niedrig / mittel / mittel | P2: vorhandene Suchdaten nutzen; Nutzertest vor Ausbau |
| 7 | Guided Setup für Rechnungsbereitschaft; Aufgabe 78 | hoch / selten | weniger Fehlversuche; keine feste Sekundenangabe | mittel / sinkend / mittel / hoch | P1: bestehende Einrichtung bündeln |
| 8 | Hintergrund-Sicherungsplan mit verifiziertem Restore; Aufgaben 106–109 | hoch / automatisch | entlastet tägliche Kontrolle; keine falschen Erfolgsanzeigen | mittel–hoch / niedrig / hoch / hoch | P2 nach eigenem Nachweis; nicht blind „Backup EIN“ ergänzen |
| 9 | Natürlichsprachlicher Sammelassistent für jeden Screen | Nutzen nicht zusätzlich belegt; MCP/Sprachfelder existieren | derzeit keine gemessene Mehrersparnis | hoch / hoch / hoch / hoch | NICHT neu ergänzen; vorhandene Wege konsistent halten |
| 10 | Automatische Wetter-Umbuchung mit Kundenversand | bisher nur Wetterhinweisbedarf belegt | vermeintliche Einsparung, hohes Fehlplanungsrisiko | hoch / mittel / hoch / hoch | NICHT ergänzen; Vorschlag und bewusste Terminentscheidung behalten |

Ränge 4/5/7 sind bewusst Verbesserungen vorhandener Workflows, keine neuen Subsysteme. Mehr neue Funktionen als die belegten Lücken wären diesem Auftrag und der Persona abträglich.

#### 12. Ausdrücklich nicht ergänzen

Kein Technikdashboard im Heute-Bildschirm, kein zweiter Sync-Modus für alltägliche Wahl, kein zweiter Backup-/Fotospeicher, keine automatische Alt-Konflikt- oder Löschbereinigung, kein automatischer Versand allein durch „Teilen“, keine automatische Rechnungsnummer oder Steuerannahme, kein neuer b.b.-Abrechnungsstatus, keine automatische Wetterabsage/-umbuchung, kein weiterer schwebender Betriebsassistent und kein zusätzlicher Haupttab pro Modul. Kein Build, keine neue APK und keine Änderung von MCP-Verträgen für diesen Analyseauftrag.

#### 13. Vorgeschlagene ideale Hauptnavigation

Die bestehenden vier Ziele **Heute – Kunden – Arbeiten – Mehr** behalten. Unter Mehr fachlich gruppiert: **Planung**, **Dokumente** (Angebote/Kostenvoranschläge/Rechnungen/offene Leistungen), **Aufträge**, **Fotos & Pflanzen**, **Einstellungen**. Das ist ein Navigationsvorschlag, keine Zusammenlegung von Datenmodellen. Papierkorb über Datenverwaltung und kontextuelle Rückgängig-Aktion erreichbar halten. Diagnose nur unter Einstellungen/Erweitert. Kontextuelle Direktwege aus Kunde, Auftrag und Arbeit bleiben erhalten.

#### 14. Vorgeschlagener idealer Heute-Bildschirm

Oben laufende Arbeit mit Kunde, Dauer und genau den passenden Aktionen Pause/Fortsetzen/Beenden; ohne Sitzung eine dominante Aktion **Arbeit starten**. Danach nächster Termin, heutige Arbeiten/Summe, **Arbeit nachtragen**. Kleiner wahrer Speicherhinweis **Auf diesem Gerät gespeichert**. Nur bei nötiger Handlung eine verständliche Problemmeldung; keine Queue-/Providerzählung. Die bestehende Startansicht erfüllt einen großen Teil bereits. Gartenpirat bleibt erreichbar und darf Timeraktionen nicht überdecken; vorhandene native UI-Prüfung behalten.

#### 15. Vorgeschlagene ideale Kundenakte

Kopf: Name/Adresse, Kontakt, **Arbeit starten/erfassen**, **Termin**, optional Menü. Danach **Übersicht – Arbeiten – Fotos – Dokumente** als verständliche Ziele; Garteninformationen auf Übersicht/Details, ohne sie als neue technische Entität zu behandeln. Übersicht: letzter Einsatz, offene Aufträge, Gartenfläche, nächste Termine. Dokumente enthalten Angebote und Rechnungen, Fotoansicht ausschließlich Bilder/zugehörige Dateien. Kontakt/Neues Angebot nicht unter demselben unbestimmten Sammelpunkt verstecken. Kunde und Einsatzort bleiben auch bei abweichendem Rechnungsempfänger eindeutig.

#### 16. Vorgeschlagener idealer Arbeitszeit-Workflow

**Start → Kunde bestätigen (Auftrag optional) → Pause/Fortsetzen → Ende → Tätigkeit prüfen → Speichern.** Zeit/Netto-Pause automatisch, Material/Kilometer/Fotos/Notiz als optionale direkte Ergänzungen. Kein Zwang zu GPS oder vorhandenem Auftrag. „Später ergänzen“ bewahrt den offenen Abschluss, benennt ihn aber nicht als fertige Arbeitsaufzeichnung. Korrektur aus Arbeitsdetail in genau einen Editor mit eindeutiger Speicher-/Abbrechen-Rückmeldung. b.b. bleibt persönliche Markierung; „App: verrechnet“ bleibt kein Beweis eines ausgestellten Dokuments. Bestehende Versionsprüfung im Hintergrund.

#### 17. Vorgeschlagener idealer Dokument-Workflow

**Kunde/Kontext → bewusst Dokumentart wählen → bekannte Leistungen/Preise übernehmen → fehlende Angaben ergänzen → Vorschau/Prüfung → ausdrücklich abschließen → optional Teilen/Versenden → bestätigten Status sehen.** Unterschiedliche Fachlogiken nicht vermischen: Angebot/Kostenvoranschlag haben bewusste Bindungsart; Rechnung hat zentrale Nummernautorität und unveränderliches Original. B2B/B2C und Vertragsweg nur bei fehlender/abweichender Datenlage nachfragen, keine stillen Rechtsannahmen. Ausgabe „Zum Teilen bereit“ ist nicht „Gesendet“; „Ausgestellt“ ist nicht „Bezahlt“. Nach Abschluss Korrektur-/Stornoweg statt freier Bearbeitung. Material-/Stunden-/Preisprüfung bleibt erforderlich.

#### 18. Technische Interna: vollständiges gefundenes Expositionsinventar

Die folgende Liste umfasst die bei dieser Prüfung gefundenen UI-Stellen und Fehlerrückgabefamilien; eine Garantie über jede zukünftig vom Provider gelieferte Fehlermeldung wäre unredlich. Nichts enthält echte Kontowerte, IDs, Pfade oder Hashes aus Betriebsdaten.

| Stelle / Quelle | Sichtbarkeit heute | Konkrete technische Information | Bewertung |
|---|---|---|---|
| Heute-Status / mobile.html + daily-usability.js | normal | erster Drive-Abgleich, Übertragungs-/Prüfzustand | besser fachlicher Speicherstatus/Handlungsbedarf |
| Einstellungen / daily-usability.js:15–18 | normal | Warteschlange, Übertragungsstand, Übergabe an Drive-App, Rücklesen vs Online-Upload | deutlich zu viel Erklärung im Alltag |
| Einstellungen Fotos / photo-settings.js:8–9 | normal | lokale Bilddateien, MB/GB frei, Drive noch nicht vollständig bestätigt | Kapazität bei Bedarf sinnvoll; Transportbegründung erweitert |
| Sync-Grundtext / mobile.html | normal | HausGarten-ChatGPT.json, Übernahmeweg, „kein dauerhafter Fernzugriff“ | historischer Kanaltext passt nicht als alleinige Beschreibung des heutigen Owner-Zugangs |
| Pixel-Verbindungskarte / pixel-tunnel-ui.js:8–12 | normal auf Sync-Seite | Pixel, letzter gespeicherter Drive-Stand, eingeschränkte Liste alter Schreibfunktionen | unvollständige Erklärung gegenüber aktuellen 117 Tools |
| Sync-Liste / sync-dashboard.js:9–17 | normal beim Öffnen der Seite | Übertragungen/Prüfungen, Cloud-Abschluss offen, nächste Abgleichphase/Serverprüfung | nur offene Handlungsfälle normal zeigen |
| Sync-Verbindungsdetails / sync-dashboard.js:17–20 | ausdrücklich aufgeklappt | Android-Speicheranbieter/direct_drive, Fehlercode, Phasen, Retry-Anzahl | angemessener Diagnoseort; nicht als dauernd offen gewertet |
| Sync-Fehler / mobile.html:191 + sync-dashboard.js | Fehler, normales UI | ungefiltertes result.error/last_error kann technische Codes/Providertext enthalten | verständliche Primärmeldung fehlt für alle Fehlerfamilien |
| Historische Prüfungen / legacy-conflicts-ui.js:5 | normal, auch bei 0 Fällen | unverifizierbare Alt-Konflikte/Wartungsfunktion | unnötige ständige Wartungssicht |
| Historienliste / legacy-conflicts-ui.js:5 | nach „Konflikthistorie anzeigen“ | client_id, keep_current, reason, action_hash als Rohtext | nach bewusster Aktion, aber noch kein klarer Diagnosebereich |
| Alt-Konflikt-Detail / legacy-conflicts-ui.js:8 | aufgeklappte Details nach Vorschau | geprüfte IDs und Action-/Record-Hashes | Diagnose nützlich, für Bestätigung nicht nötig |
| Alt-Konflikt-Bestätigung / legacy-conflicts-ui.js:9 | kritische Bestätigung | Warteschlange, Drive-Snapshot, Speicheranbieter-Readback, Cloud-Bestätigung | Datenbestand/Effekt/Sicherungsstatus verständlich zusammenfassen |
| Konfliktvergleich / sync-dashboard.js:24–29 | echter Entscheidungsfall | unbekannte Feldnamen fallen auf Rohschlüssel zurück; Beziehungen können IDs, Objekte JSON zeigen | normale Fachwerte statt Rohobjekte; beide Varianten erhalten |
| ChatGPT-Einrichtung / PixelTunnelActivity.java:40–60 | native Einrichtung | Tunnel-ID, Organisation, Runtime-Schlüssel, Akku-Ausnahme, Hintergrunddaten, Tunnelverwaltung | sicher geschützte Experteneinrichtung, kein normales Betriebsmodell |
| Drive-Einrichtung / DriveConnectionActivity.java:17–38 | native Einrichtung/Fehler | gespeicherte Ordner-ID im Linkfeld, Konto-/App-/Ordnerrechte, Providerfehlermeldung | Konto/Ordner fachlich wählen; Details erweitert |
| Planung / planning-ui.js:43 | normales Terminformular | Europe/Vienna, optionale Koordinaten/UTC-Offset in Zusatzgruppe | Ortszeit genügt täglich; DST-Ausnahme nur bei echter Mehrdeutigkeit |
| Planungs-Zugang / PlanningConnectionActivity.java:20–46 | native Einrichtung | API-Schlüssel, Nutzungsfreigabe, 5 Schwellen, OAuth-/Calendar-API-/Organisationsfehler | Betriebsoptionen normal; Zugang/Diagnose getrennt |
| Unternehmenseditor / owner-ui.js:22–32 | normales langes Editorformular | Nummernkreiszähler, Pixel als Nummernautorität, direkte Drive-Verbindung; Fehler „Vergleichsstand“ | fachliche Einrichtung bündeln; Versionstechnik aus Text entfernen |
| Rechnungsabschluss / invoice-draft.js:98–101 | normale Bestätigung/Fehler | Nummernservice, Pixel-/Drive-Abgleich, nach Service erneut synchronisieren, sevDesk-Parallelkreis | zentrale Schutzregel bewahren, nächste Aktion fachlich erklären |
| Rechnung fehlt lokal / invoice-draft.js:66–67 | Daten-/Dateifehler | Original-PDF nicht lokal, Datenstand abgleichen | kein Fake-PDF ist richtig; „Original wird geladen/derzeit nicht verfügbar“ verständlicher |
| Angebotsstatus / offers-ui.js:30,43 | normal nach Speichern/Abschluss | privater Dokumentspeicher, Drive-Abgleich folgt, keine Rechnungsnummer vergeben | „Gespeichert/Abgeschlossen“ genügt; kein Versand vorgeben |
| Foto-Ladestatus / design-concept.js:129–147 | Foto öffnen/Fehler | Original nach Drive-Abgleich, ggf. Dateiname statt Beschreibung | fachlicher Lade-/Fehlertext, kein Dateipfad nötig |
| Foto-Fehlerwiederholung / mobile.html:178–187 | echter Speicherfehler/Neustart | Wiederholauftrag, Datenstand, dauerhafte Wiederherstellung nicht verfügbar | Handlung erforderlich; Arbeit und Foto getrennt verständlich benennen |
| Papierkorb / trash-ui.js:14,24,58 | Bestätigung bzw. erklärende Details | Dateibereinigung nach geprüftem Abgleich, historische Sicherungen separat | sachlich notwendige Reichweite, keine Hash-/CAS-Bedienung; Text kürzen möglich |
| Owner-Client-Fehler / owner-client.js und Konsumenten | Fehler | serverseitige Fehlermeldungen werden in Status/Toast durchgereicht | gemeinsame fachliche Fehlerübersetzung als spätere Empfehlung |
| Versions-/Buildinfo / daily-usability.js | ausdrücklich Settings | Version/Build/Fingerprint | sinnvoll in Info/Diagnose, keine Betriebsentscheidung |

Die Herkunftsnachweise bei Pflanzen/Neophyten und Hinweise zur bewussten Dokumentbindung sind fachliche Informationen, keine sinnlos sichtbare Technik. Fehlende Kundenzusage, ungeklärte Preise oder fehlende Rechte dürfen verständlich sichtbar bleiben. Ein echtes Problem darf nicht zugunsten einer grünen EIN-Pille versteckt werden.

#### 19. Welche Informationen vollständig verborgen werden können

Im normalen Betrieb vollständig verborgen: Request-IDs, Record-/Action-/Preview-Hashes, CAS, interne Queue-/Journal-/Snapshot-Bezeichnungen, JSON-Dateiname, Transportklasse, Provider-Readback als Fachbegriff, interne Phasen und Wiederholungszähler, rohe Feldschlüssel/UUIDs und Dateipfade. Die internen Nachweise bleiben unverändert gespeichert und diagnostizierbar.

Ersetzen durch Antworten auf vier Fragen: **Ist gespeichert? Ist Sicherung/Verbindung eingerichtet? Ist der Vorgang erledigt? Muss ich etwas tun?** Beispiel für Netzfehler: **„Synchronisierung derzeit nicht möglich. Deine Daten sind auf diesem Gerät gespeichert. Wir versuchen es später erneut.“** Nur sagen, wenn die lokale Speicherung tatsächlich bestätigt ist und Wiederholung tatsächlich geplant ist. Bei vollem Speicher stattdessen **„Nicht gespeichert. Eingaben bleiben hier erhalten. Bitte Speicher freigeben und erneut speichern.“** Bei Kontoentzug **„Google Drive braucht erneut deine Freigabe. Auf diesem Gerät gespeicherte Daten bleiben erhalten. Verbindung prüfen.“** Kein automatischer Retry gegen dauerhaftes 401, keine Umgehung von Rechten.

Sichtbar bleiben müssen: fehlende echte Sicherung, nicht gespeicherte Fotos, erforderliche Kontofreigabe, aktueller Konflikt mit fachlichen Alternativen, bewusste Dokumentart/Bindung, fällige Rechnung/fehlende Nummernfreigabe, endgültiger Löschumfang, tatsächlicher Versand-/Zahlungsstatus. Sicher automatisch lösbare Fälle brauchen keine Rückfrage; konkurrierende fachliche Varianten bleiben ausdrücklich Entscheidungssache.

#### 20. Priorisierung und Abnahmeentscheidung

| Priorität | Befunde / vorgeschlagene nächste Arbeit | Abnahmekriterium für einen späteren, separat autorisierten UX-Auftrag |
|---|---|---|
| P0 UX-Sicherheitsprüfung | U15 uneinheitliches Zurück/Speichern; U11 b.b./Rechnung nicht verwechseln; U03 keine falsche Sicherungsbestätigung; U06 Nummernautorität erhalten | Keine unbemerkte Fachänderung, kein falsches Bezahlt-/Gesichert-/Gesendet; Charakterisierungstests bleiben PASS. Kein neuer S0 wurde nachgewiesen. |
| P1 hoher Alltagsnutzen | U01 Fotoänderung, U02 Zahlung, U03 Sicherungszugang, U05 verständlicher Speicher-/Syncstatus, U06 Rechnungsbereitschaft, U07 offene Leistungen → Entwurf, U08 Dokumentführung, U09 Kunden-Dokumente, U12 Löschbegriffe | Ziele ohne Technikvorwissen ausführbar; Kontext korrekt; keine zusätzlichen Pflichtdaten; Schutzparameter und Originale erhalten |
| P2 sinnvoll | U10 Wiederholung auffindbar, U13 konfliktfreie Oberfläche ruhig, U14 Suche, U17 Kalender/Wetter-Ersteinrichtung; verifizierbare automatische Sicherung konzipieren | weniger Navigation, kein zweites Subsystem; Rechte/Kosten/Zusagen weiter explizit |
| P3 optional | U16 konsistente Kurztexte, Speicher-MB-Rundung, Feinordnung der Mehr-Gruppen | messbar klarer, ohne neue UI-Komplexität oder Regressionsrisiko |

**Dieser Auftrag endet mit dieser Analyse.** Es wurden weder die genannten Bedienlücken noch UX-Reibungen behoben. Die bestehenden fachlichen Regeln, Datenmodelle, App-ID, Signatur, 117 MCP-Verträge/Protokoll 9 und 156 UI-Katalogaktionen bleiben unverändert. Der nächste Schritt wäre eine ausdrücklich beauftragte Prioritätsauswahl oder ein darauf begrenzter UX-Umsetzungsauftrag, keine automatische Fortsetzung in Produktcode.

### Phase D – abschließende Nachweise, Abweichungen und Grenzen

**Ausgeführte Funktionsprüfungen dieses Auftrags:**

| Prüfung | Tatsächliches Ergebnis / Grenze |
|---|---|
| Python-Qualitätsrunner | 322 PASS, 0 Skips; 37 historische QA-Signing-/Parallelpaket-Prüfungen durch den vorhandenen expliziten Schalter ausgeschlossen, nicht geändert oder als PASS gezählt |
| JVM-Runner | 27 Suiten PASS; darin UI-/MCP-Parität 1.036 Prüfungen und eingefrorener P1-Vergleich |
| Browser-/JS-Runner | alle 30 Runner-Einträge Exit 0; enthält Chromium- und reine JS-Prüfungen, deshalb nicht „30 native E2Es“ |
| Zusätzlicher kognitiver Chromium-Durchlauf | 6/6 beschriebene Pfade abgeschlossen; operative Assets und bestehende synthetische Bridges, keine neuen Geschäftsregeln im Test simuliert |
| MCP-stdio E2E | 55/55 synthetische Szenarien PASS; eingeschränkter vorhandener Kanaladapter, kein realer ChatGPT-Pixel-Connector |
| Cloud-Android-Instrumentierung acceptance | **135 PASS; acceptance failures=0**, eigener QA-Namensraum; SQLite, Timer, Versionen, Rechte, Papierkorb/136 Einträge, Fotooptimierung/-rotation, Medienretry/Readback, PDF, vier Dokumentarten, Kalender/Wetter/Testprovider, sieben synthetische Legacy-Fälle |
| Cloud-Android fresh_isolated | PASS: leerer isolierter Erststart, keine Credentials und kein automatischer Drive-/Tunnelzugriff; bestehender AVD-Bestand nicht gelöscht |
| Cloud-Android ui | Assertion-Runner PASS: echter WebView, vier Tabs, Mehr-Gruppen, Auswahl/Abbrechen, Garten, Papierkorb, Planungsentwurf, aktiver Timer und verdeckter Launcher |
| Native Screenshot-Sichtprüfung | **EINGESCHRÄNKT / weitere Prüfung nötig:** Screenshot enthält Systemdialog „System UI isn't responding“. Events-Log belegt ANR von `com.android.systemui/.keyguard.KeyguardService`. Programmgesteuerte WebView-Assertions erkennen diese OS-Überlagerung nicht. Deshalb keine uneingeschränkte manuelle native UI-Abnahme behaupten. |
| Zusätzlicher historischer release_lifecycle verify | **FAIL:** `AssertionError`, nur Sammelstelle ReleaseLifecycleProbe.require:33 gemeldet. Keine Neuinstallation/kein Update dieses Auftrags. Der bestehende alte Verifier schließt technische capabilities und weitere vorbereitete Präferenzen ein; die konkret gescheiterte Bedingung ist mit dieser Ausgabe nicht lokalisiert. Keine Baseline neu geschrieben, kein Test abgeschwächt, keine Ursache als bewiesen ausgegeben. |
| Lesender Abschlussvergleich des bestehenden synthetischen AVD-Bestands | PASS: vollständiger Geschäftsmaster ohne technische capabilities **einschließlich ganzer Pending-Queue** weiterhin SHA-256 `be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af`, identisch zum dokumentierten P1-Ledger. Nur im RAM über vorhandene Business-Capture-Instrumentierung verarbeitet; keine Rohzeilen/Base64 gespeichert oder ausgegeben. 3 QA-Kunden, 1 Auftrag, 2 Arbeitsaufzeichnungen, 1 Pending-Aktion, 32 öffentliche Katalogbausteine, 0 Fotos/Rechnungen/Angebote/Termine. IDs der Geschäftsentitäten ausschließlich synthetisch. Der fehlgeschlagene alte Verifier ist damit kein nachgewiesener Geschäftsdatenschaden, bleibt aber als fehlgeschlagene Prüfung offen. |
| P1-Vertragsvergleich | PASS, `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, 117 Tools = 52 Lesen/65 Schreiben, Protokoll 9, 156 semantische UI-Aktionen; keine Baselineaktualisierung |

**UAT-Zusammenfassung:** 115/115 Aufgaben bewertet; 69 PASS + 2 PASS auf ausdrücklich begrenzter synthetischer Sync-Teilstrecke, 40 PASS MIT REIBUNG, 3 FAIL des manuellen Benutzerziels (Foto nachträglich zuordnen, Rechnungszahlung manuell, Sicherungsstatus/-zugang) und 1 ausdrücklich NOT TESTABLE für echten Kamera-/Dateirechteentzug. Weitere externe Teilgrenzen stehen in den einzelnen Zeilen; diese Zahlen sind keine 114/115-Geräteabnahme. 56 Einstellungsentscheidungen einzeln inventarisiert, alle 20 verlangten Berichtsbereiche enthalten. Zählspannen/Verbesserungsziele nicht als empirisch gemessene Klickzeiten ausgeben.

**Privacy und Unverändertheit:** Source PASS (1.148 Dateien, 0 operative Seedzeilen), Workspace PASS (3.139 Dateien einschließlich ignorierter/untracked Inhalte), vorhandenes Original-81-Artefakt PASS (0 operative Seedzeilen), vor den Tests sowie nach den Tests geprüft. APK weiterhin bytegleich SHA-256 `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`. Keine neue APK, keine Signierung, keine Installation. Auf main d8960c46d42b5f6c99d8b6c2f3217686a6e4cf13 ausschließlich diesen neuen UAT-Abschnitt in CODEX-STATUS.md ergänzt; kein Produkt-/Test-/Architekturcode geändert, bestehende Statusabschnitte erhalten. Keine produktiven Daten, realen Drive-Dateien, historischen Originalkonflikte oder Konten gelesen/geschrieben. Standardisierte synthetische QA-Ausgaben des vorhandenen Runners bleiben in den vorhandenen Outputpfaden; keine zusätzliche Projektdokumentation erzeugt.

**Offen:** physischer Pixel und aktueller ChatGPT-Connector vollständig unberührt; echte Kamera/Dateifreigaben, Google-Konto/OAuth, Kalender-/Wetteranbieter, Drive-Cloudrundlauf, echte Zustellung und Akku-/Langzeitbetrieb nicht verifiziert. Der Cloud-Emulator läuft ohne KVM; ANR/Interaktionslatenz sind nicht auf Pixel-Alltagsleistung übertragbar. Native Sichtprüfung nach System-UI-Stabilisierung sowie genaue Diagnose des historischen Verifier-FAIL bleiben erforderlich. Keine Umgehung von Rechten, kein Reset/AVD-Wipe, keine produktive Reparatur. Die empfohlenen UX-Änderungen bleiben vollständig unimplementiert.


## P3 – Black-Box-UX und Workflow-Vereinfachung angelegt (20.10.2026)

- Grundlage: vollständiger Codex-UAT mit 115/115 bewerteten Aufgaben sowie unabhängiger Gemini-Produktbericht. Der UAT bestätigte 3 konkrete FAILs: U01 Foto nachträglich bearbeiten/zuordnen, U02 Rechnungszahlung im UI, U03 verständlicher Sicherungs-/Wiederherstellungszugang. Zusätzlich 40 PASS MIT REIBUNG.
- Neuer verbindlicher P3-Block in CODEX-AUFTRAG-GARTENPIRAT.md. Produktprämisse: normaler Benutzer bedient den Betrieb, nicht die Technik; technische Interna nur unter Erweitert/Diagnose. Normale UI beantwortet: gespeichert? Sicherung/Verbindung eingerichtet? Vorgang erledigt? Handlung nötig?
- P3 umfasst sieben begrenzte Pakete: Black-Box-Status, Heute/Arbeitsworkflow, Kundenakte, drei UAT-FAILs, Dokumente/Abrechnung, Begriffe/Lebenszyklus sowie Suche/Wiederholung/Einstellungen.
- Geminis Drei-Tab-Vorschlag wird nicht blind übernommen. Für die erste P3-Umsetzung bleibt der im UAT empfohlene Hauptzugang Heute – Kunden – Arbeiten – Mehr, bis ein erneuter UAT einen belastbaren Vorteil der Entfernung von Arbeiten zeigt.
- Bestehende Terminserien, Foto-Rollen, GPS, Wetter, Kalender und Spracheingabe gelten nicht als neue Funktionen; P3 verbessert primär Auffindbarkeit und Bedienung. Arbeitsvorlagen, Pflege-Turnusse, Material-Schnellwahl, digitale Unterschrift, Umsatzmonitor, Tourenoptimierung und neue Karten/KAGIS-Funktionen sind nicht Teil der ersten P3-Umsetzung.
- Außenverträge bleiben geschützt: Paket-ID, Originalsignatur, DB/Snapshot/Drive/Journal, IDs/Relationen, MCP9/117 Tools, P1-Vertragsbaseline und P0-Privacy-Gates.
- P3 ist mit diesem Eintrag **nur definiert, noch nicht implementiert**. Keine App-/Android-/JS-/MCP-/DB-/Drive-Funktion wurde durch das Anlegen des P3-Plans geändert.
- Ziel nach vollständiger Abnahme bei unverändertem Versionsverlauf: 1.52.0 / Build 82, originalsignierter Kandidat erst nach kompletter Regression, erneutem 115-Aufgaben-UAT und synthetischem In-place-Update 81→82. Physische Pixelinstallation bleibt separat freigabepflichtig.


## P3-R0 – Baseline und Charakterisierung (20.10.2026)

- Aktueller `main` vor P3-Implementierung: `d1c57034f7c1e74152f96d62a3cecaaaaccf511e`, sauber per Fast-forward übernommen. Operativer Quellstand unverändert 1.51.2 / 81, Paket `at.hausgarten.service`, MCP9 / 117 Werkzeuge, 156 semantische UI-Aktionen. P0/P1 bleiben maßgeblich; P2-Restgrenzen werden nicht als erledigt umgedeutet.
- Unveränderliche synthetische Vorher-Fixture `tests/fixtures/p3-ux-baseline.json`: alle 115 bisherigen UAT-Aufgaben, U01–U17 und Quellhashes vor der ersten Produktänderung. Drei neue Charakterisierungsprüfungen sichern die vollständige Aufgabenmenge, die ursprünglichen FAILs 41/81/106, die Herkunft der Quellhashes und die unveränderte P1-Fixture. Keine Betriebsdatensätze enthalten.
- Neu ausgeführt: Python-Qualitätssuite 322 PASS (bestehende QA-Schlüsseltests ausdrücklich ausgeschlossen), 27 JVM-Suiten PASS, 30 Browser-/JavaScript-Testläufe PASS, 3 P3-Baselineprüfungen PASS. UI-MCP-Inventarprüfung PASS. P1-Vertragsvergleich unverändert PASS: `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`.
- Source-, Workspace- (einschließlich ignoriert/untracked) und Artifact-Privacy-Gate PASS. Geprüftes bestehendes Originalartefakt 81: SHA-256 `7e569519f8f3328335499a84181f6eb6746c860e4325e80d67f0f44ba59e865e`, leerer operativer Bootstrap. Kein neuer Kandidat erstellt.
- Abnahmeziele der folgenden Phasen: verständliche belegbare Statusaussagen, technische Details nur explizit erweitert; drei UAT-FAILs über bestehende fachliche Funktionen schließen; alle 156 Aktionen und Außenverträge erhalten; schmale Ansichten, Entwürfe und Fehlerpfade prüfen.
- Offen unverändert: früherer synthetischer Emulator-Lifecycle-Nachweis mit nicht lokalisiertem Verify-FAIL sowie SystemUI-ANR bei visueller Abnahme; erneute native Abnahme erforderlich. Kein physischer Pixel-, Konto-, Drive- oder Versandtest durchgeführt. Die historische UAT-Bewertung ist keine erneute P3-Abnahme.


## P3-R1 – Verständlicher Betriebsstatus und ausdrücklich geöffnete Diagnose (20.10.2026)

- Kleine gemeinsame Präsentationsschicht `business-feedback.js` übersetzt Speicher-/Versions-/Freigabe-/Netzfehler und Sync-Nachweise. Keine Änderung an MCP-Antworten, fachlichen Validatoren oder Datenformaten. Rohfehler bleiben begrenzt im Sitzungsspeicher für ausdrücklich geöffnete Diagnose erhalten; keine Fehlerlogs mit Betriebsdaten angelegt.
- Normale Statusanzeige trennt lokale Speicherung, geprüfte Übertragung und bestätigten Online-Abgleich. Keine automatische Vollsicherungs-/Cloud-Behauptung. Ausstehende Benutzerentscheidung bleibt sichtbar; fehlende Freigabe erhält einen verständlichen nächsten Schritt. Warteschlangen-, Provider-, Retry- und Hashdetails sind nur erweitert sichtbar. Historische Wartung ist ohne aktive Fälle eingeklappt.
- Bestehende native Verbindungen erhalten verständliche EIN/AUS-Betriebsoptionen; Konto-/Schlüssel-/Schwellen-/Akku-Einrichtung bleibt unter Erweitert / Diagnose. Drive-AUS pausiert ausschließlich die Automatik, erhält alle Daten und Konto-/Ordnerfreigaben; ausdrücklich angeforderter manueller Abgleich bleibt möglich. Standardverhalten bestehender Installationen bleibt EIN. ChatGPT verwendet unverändert den vorhandenen Vertrauensanker/Schreibschalter; kein stilles Hochstufen von Rechten. Kalender/Wetter/Benachrichtigungen verwenden bestehende versionsgeschützte Planungseinstellungen.
- Nachgewiesen: 325 Python-Prüfungen PASS, 27 JVM-Suiten PASS, 55 isolierte MCP-stdio-Szenarien PASS; Android-Release-Java-Kompilierung PASS. Browserregressionen einschließlich gezielter Wiederholungen für absichtlich geänderte Statustexte PASS; neue Präsentationstests schützen fehlende Freigaben, Versionskonflikte und fehlende Online-Bestätigung. Diagnose muss ausdrücklich geöffnet werden und bleibt lesbar. Vorhandene Sicherheits-/Entwurfsprüfungen nicht entfernt.
- P1-Vertragsbaseline weiterhin exakt PASS `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`; MCP9/117 und 156 Aktionen unverändert. UI-Quellreview-Fingerprints nach Prüfung aktualisiert, ursprüngliche P1-/P3-Vorher-Fixtures nicht verändert. Source-, Workspace- und bestehendes Artifact-Privacy-Gate PASS.
- Native Isolationsprüfung für Automatik-AUS/manuelle Anforderung ergänzt; Ausführung des neuen Android-Codes auf originalsigniertem Kandidaten folgt in R7. Kompilierung ist kein Funktionsnachweis am physischen Pixel. Für den stdio-Lauf musste die bereits vorhandene Cloud-venv verwendet werden (Host-MCP-Paket bietet den benötigten Import nicht); kein Produktcode-Fallback eingeführt. P2-Restgrenzen bleiben offen.


## P3-R2 – Drei bestätigte UAT-FAILs: Bedienpfade ergänzt (20.10.2026)

- U01: Foto → Mehr → Bearbeiten öffnet Beschreibung, Rolle und passende Auftrags-/Arbeitszuordnung desselben Kunden. Bestehendes `get_photo`/`update_photo`, Öffnungs-Hash, gezielte Änderungen und bei unverändertem Inhalt stabile Request-ID; kein Dateiimport und keine neue Foto-ID. Unbekannte historische Zuordnungen werden erhalten. Entwürfe überleben Fehler; ein ausdrücklich bestätigter Vergleich mit dem aktuellen Stand übernimmt nur bewusst geänderte Entwurfsfelder. Keine stille Aktualisierung der Vergleichsbasis. Überlagerung der beiden Foto-Menüaktionen im realen Browsertest gefunden und korrigiert.
- U02: Ausgestellte gültige Rechnung → Zahlung erfassen/korrigieren → ausdrückliche Bestätigung. Vorhandenes `update_invoice_payment` mit `entity_hash`, keine Änderung an b.b., Arbeitsstatus, Rechnungstext oder Original-PDF. Bestehenden Invoice-Read über die lokale Bridge zugänglich gemacht; keine neue öffentliche JavaScript-Methode und kein neues MCP-Werkzeug.
- U03: Einstellungen → Sicherung zeigt nur tatsächlich inspizierte, gegen Dateigröße/SHA geprüfte Geschäftssicherungen, Erstellungszeit und ausdrücklich lokalen Speicherort. Jetzt sichern benutzt vorhandene idempotente Sicherung; Wiederherstellen wählt Sicherung, inspiziert sie und bindet die explizite Bestätigung an den aktuellen State-Hash. Beschädigte/unlesbare Altsicherungen verhindern keinen neuen Sicherungsversuch; bestehende Restore-Schutzregeln unverändert. Keine automatische Vollgeräte-/Cloud-Sicherungsbehauptung. P2-Altsicherungsgrenzen bleiben erhalten.
- Abnahme dieser UI-Pfade: gezielter Chromium-Test PASS für Foto-CAS/Retry, unveränderte Datei, korrekte Relationsauswahl, expliziten Versionsvergleich, Rechnungsbestätigung/Hash/b.b.-Trennung, Sicherungsprüfung, Restore-Abbruch/Bestätigung sowie beschädigte Sicherung; 320/360/426 px und vergrößerte Schrift geprüft. Gesamter Browser-/JS-Runner: 32/32 PASS; 27 JVM-Suiten PASS, 55 synthetische MCP-stdio-Szenarien PASS und 3 unveränderte P3-Baselineprüfungen PASS. Release-Java und androidTest-Java kompilieren erfolgreich.
- Source-/Workspace-/bestehendes Artifact-Privacy-Gate PASS. P1-Vertrag weiterhin exakt `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, 117 Tools / 156 semantische UI-Aktionen. Keine Geschäftsregel, DB-Struktur oder externen Daten geändert. Neue native Bridge-/Zahlungsprüfung kompiliert; tatsächliche Ausführung mit dem späteren Originalkandidaten in R7 noch offen. Browser-Doubles sind kein physischer Foto-/Pixel-/Drive-Nachweis und keine vollständige erneute 115-Aufgaben-Abnahme.


## P3-R3 – Heute und Arbeitsworkflow (20.10.2026)

- Heute zeigt die laufende Sitzung vor Kennzahlen, mit Kundenname, Netto-Laufzeit und passenden Pause/Fortsetzen/Beenden-Aktionen. Anschließend nächster Einsatz, Tagesübersicht und Arbeit nachtragen. Die vier Hauptziele Heute/Kunden/Arbeiten/Mehr bleiben erhalten. Leere Texte und Hauptaktion heißen konsistent Arbeit starten; Nachtragen bleibt als separate fachliche Alternative erreichbar.
- Arbeitsstart fragt zuerst den Kunden. Auftrag/Arbeitsort sind optional; ein Standortvorschlag wird ausschließlich auf ausdrücklichen Wunsch angefordert. Keine zusätzliche Berechtigung oder GPS-Pflicht; bestehende präzise/ungenaue/abgelehnte Standortprüfungen und Schutz gegen falsche Kundenzuordnung erhalten. Der Start überträgt die gewählte stabile Auftrags-ID über dieselbe vorhandene Timerfunktion.
- Sekündliche Laufzeitanzeige ersetzt nicht mehr die Aktionsschaltflächen und erhält damit den Fokus. Keine unnötige Wiederplatzierung des Timer-DOM bei unveränderter Reihenfolge. Korrektur bleibt im bestehenden einzigen Arbeitseditor; im Editor nur Speichern/Abbrechen im Fußbereich, Zurück bleibt mit ausdrücklich benannter Speicherwirkung verfügbar. Keine zusätzliche Speicherung oder Änderung der bestehenden Zeit-/Pausen-/CAS-Regeln.
- b.b. erhält eine zugängliche Erklärung und eindeutige Rückmeldung: persönliche Geräteübersicht, keine Rechnungszahlung und kein Abrechnungsnachweis. Heute zeigt weiterhin bezahlt. Rechnungs-, billed- und persönliche Markierungen bleiben getrennt.
- Prüfungen: Browser-/JS-Regressionen einschließlich gezielter Wiederholung PASS. Neue Prüfung bestätigt manuellen Start ohne Standortanforderung, optionalen Auftrag, Timerposition und Fokus nach Laufzeitaktualisierung. Vorhandener Timerabschluss mit Pause, später ergänzen, Fortsetzen, Entwurf und Undo weiter PASS. 27 JVM-Suiten, 55 MCP-stdio-Szenarien und 3 Baselineprüfungen PASS; P1-Vertrag unverändert PASS. Source-/Workspace-/bestehendes Artifact-Privacy-Gate PASS.
- Zusätzlich ausschließlich lesender Nachweis des erhaltenen synthetischen Cloud-AVD: 3 Kunden, 1 Auftrag, 2 Snapshot-Arbeiten, 1 ausstehende Aktion; 0 Rechnungen/Angebote/Fotos/Termine. Geschäftshash ohne rein technische Capabilities, einschließlich unveränderter Pending-Aktionen: `be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af`, identisch zum vorherigen UAT. Sämtliche Geschäfts-IDs synthetisch. Dies ist kein Zugriff auf den physischen Pixel. Native P3-Abnahme und 115-Aufgaben-Gesamtdurchlauf bleiben R7.


## P3-R4 – Kundenakte als zentrale Betriebsansicht (20.10.2026)

- Vier Hauptbereiche der Akte: Übersicht – Arbeiten – Fotos – Dokumente. Keine fünfte Hauptnavigation. Angebote/Kostenvoranschläge und Rechnungen sind in Dokumente gemeinsam, eindeutig beschriftet und auf 30 sichtbare Einträge mit Nachladen begrenzt. Die Fotoansicht lädt ausschließlich Fotos. Kein neuer Speicher oder neues Dokumentmodell.
- Kopfaktionen Arbeit starten, Termin und Foto übernehmen den exakten Kundenkontext. Arbeit startet erst nach Bestätigung, verhindert parallele aktive Sitzungen und erfordert keinen Standort. Termin öffnet den bestehenden Editor mit gewähltem Kunden. Nachtragen bleibt unter Kontakt & weitere Aktionen. Keine automatische Geschäftsdatenerstellung.
- Übersicht enthält Fläche, Pflegehinweis, nächsten Termin, offene Aufträge und letzte Arbeiten. Vollständige Auftrags-/Arbeitsgeschichte und bisherige Filter/Batchgrenzen bleiben unter Arbeiten erhalten. Garteninformationen bleiben von Übersicht aus bearbeitbar. Gartenentwürfe werden zusätzlich im flüchtigen Ansichtsspeicher gehalten, damit auch bei verweigertem localStorage ein Tabwechsel den aktuellen Entwurf nicht verwirft; Neustartpersistenz wird bei einem Speicherfehler weiterhin nicht behauptet. Öffnungsbasis/CAS bleiben unverändert.
- Neuer gezielter Kundenakten-Test PASS: vier Bereiche, Kundenkontext ohne Schreiben, Dokumente außerhalb der Fotos, vollständige Arbeiten, Gartenentwurf nach Tabwechsel und Lesbarkeit bei 320/360/426 px mit vergrößerter Schrift. Korrigierter Nachweis: Der R4-Regressionslauf war noch nicht vollständig PASS: Ein Folgetest suchte nach dem Dokumentaufruf die Arbeitszeile im Dokumentbereich statt unter Arbeiten (Selektor-/Navigations-FAIL). Die zunächst eingetragene pauschale PASS-Aussage war zu früh. Gezielte Korrektur und erneuter vollständiger Nachweis folgen in R5; vollständige Aufträge/Rechnungen und Schutz begonnener Arbeitsangaben bleiben Testanforderungen. 27 JVM-Suiten, 55 MCP-stdio-Szenarien und 3 P3-Baselineprüfungen PASS.
- P0 Source/Workspace/Artifact PASS; P1-Vertrag unverändert PASS, MCP9/117 und 156 semantische UI-Aktionen erhalten. Kein Pixel-/Google-/Versandnachweis und keine Änderung produktiver Daten; R7-Gesamtabnahme bleibt offen.


## P3-R5 – Dokumente, Abrechnung und geführte Einrichtung (20.10.2026)

- Offene Leistungen → Rechnung vorbereiten zeigt Rechnungsempfänger und auswählbare Arbeiten. Erneute Prüfung vor Übergabe verhindert eine veraltete Auswahl; bereits in ausgestellten Rechnungen oder bestehenden Entwürfen zugeordnete Arbeiten sind ausgeschlossen. Bestehende passende Entwürfe bleiben direkt erreichbar. Einsatzkunde und Rechnungsempfänger werden nicht zusammengelegt. Erst ausdrückliches Speichern legt einen Entwurf an; kein Ausstellen, Nummerieren, Bezahlen oder billed-Setzen durch die Vorbereitung.
- Zwei in diesem Pfad gefundene Clientfehler behoben: unbekannte Preise wurden bisher als 0 vorbelegt; Material-/Kilometerpositionen übernahmen mehrfach dieselbe Arbeitsreferenz, obwohl die unveränderte zentrale Rechnungsvalidierung doppelte Zuordnung ablehnt. Unbekannte Preise und Materialmengen bleiben jetzt in der Eingabe leer; Pflichtprüfung blockiert die Speicherung. Die Arbeitsreferenz steht einmal auf der Arbeitsposition, die gesamte Rechnung behält dieselben worklog_ids; ergänzende Material-/Fahrtpositionen erhalten keine doppelte Arbeitsreferenz. Bestehende gespeicherte Datensätze werden nicht migriert oder geändert. Bewusst eingegebene 0 bleibt zulässig.
- Freigegebene Preisbausteine können ausdrücklich ausgewählt werden; keine Preisschätzung. Gemeinsamer Rechnungseditor und seine bestehenden Webspiegel sind bytegleich; keine zweite Kalkulationslogik. Rechnungsabschluss verwendet unverändert Nummernautorität, Bestätigung und unveränderliches Original. Verständliche Rückmeldung unterscheidet angeforderten Abschluss von ausgestellter Rechnung und Versand.
- Rechnungsbereitschaft zeigt die tatsächlich fehlenden Einrichtungsbereiche. Bestehende Unternehmensmaske in drei nachvollziehbare Gruppen gegliedert: Unternehmen, Steuer/Zahlungsbedingungen, bestehender Nummernkreis. Keine Rechts-/Steuerentscheidung automatisiert. Angebote/Kostenvoranschläge verwenden denselben Editor mit drei Präsentationsschritten; bewusste Bindungsart und Bestätigung bleiben unverändert. Bekannte Werte werden weiterverwendet; Validierungsfehler öffnen den betreffenden Schritt. Abgeschlossene Dokumentangaben sind kompakt einsehbar. Statuslabels folgen dem zentralen Vertrag, Teilen wird nur angefordert und niemals als gesendet behauptet.
- Zusätzlich aufgedeckte grafische Regression behoben: Der frei positionierbare Gartenpirat konnte b.b. verdecken. Seine bevorzugte Position bleibt gespeichert; bei Überschneidung mit Betriebsaktionen weicht er aus. In seinem eigenen Dialog bleibt der eindeutige Schließen-Weg frei, bei aktiver Arbeit bleibt er verborgen. Drag-/Tastatur-/Resize-, Offline-Pflanzen- und Foto-Funktionen bleiben getestet. Test prüft gespeicherte Positionspräferenz und freien Zugriff auf Betriebsaktionen statt eine absichtlich kollisionsbehaftete Pixelposition zu verlangen.
- Korrektur zum R4-Protokoll oben eingetragen: Der noch offene Navigationsselektor wurde auf den tatsächlich sichtbaren Bereich Arbeiten umgestellt; Sicherheits-/Historienassertionen bleiben erhalten. Anschließend vollständiger Runner **34/34 Browser-/JS-Suiten PASS**, einschließlich R4, drei UAT-FAILs, Preis-/Auswahl-Negativtests, Angebote beider Bindungsarten und bestehender Regressionsszenarien. Neuer Dokumenttest bestätigt getrennte Empfänger, keine doppelte Arbeitszuordnung, Material/Kilometer, abgewiesene fehlende Preise, bestätigten Katalogpreis, veraltete Auswahl und ausschließlich Entwurfsanlage.
- Weitere Nachweise: 325 Python-Prüfungen PASS, 27 JVM-Suiten PASS, 55 MCP-stdio-Szenarien PASS. Alle operativen JavaScript-Dateien separat geparst. P0 Source/Workspace/Artifact PASS; P1-Vertrag unverändert `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`; MCP9/117, 156 UI-Aktionen, DB-/Snapshot-/Journalformate unverändert. Keine produktiven Daten, realer Versand oder physischer Pixel benutzt. Vollständiger 115-Aufgaben-P3-UAT, native Kandidaten- und Updateprüfung weiterhin R7.


## P3-R6 – Auffindbarkeit, Suche, Wiederholung und ruhige Einstellungen (20.10.2026)

- Überall suchen ist auf Heute und unter Mehr erreichbar. Reine Lesesuche über die bestehenden Kunden-, Arbeits-, Foto-, Rechnungs-, Angebots-, Termin- und Auftragsbestände; keine zweite Datenhaltung, keine Geschäfts-Schreiboperation. Index aus vorhandenen Projektionen je Datenrevision, gebündelte Eingabe und höchstens 50 sichtbare Treffer mit Nachladen. Nur Fotometadaten, keine Originalbilder oder Base64-Nutzlasten. Treffer öffnen die vorhandenen Fachansichten mit exakter ID, ohne Kundennamen zu erraten.
- Bestehende Terminwiederholung unmittelbar bei Beginn/Dauer auffindbar; Anzahl und Wochenabstand verwenden unverändert create_appointment_series und dessen Schutzregeln. Konto-/Wettertransportdetails bleiben ausdrücklich erweitert; abgeschaltete, nicht eingerichtete Integrationen erzeugen keine routinemäßige Warnung. Tatsächliche Freigabe-/Konfliktfälle und relevante Wetterrisiken bleiben sichtbar.
- Begriffe für Auftrag und Nicht mehr aktiv vereinheitlicht. Alte falsche Hinweise, Fotos würden grundsätzlich das Verschieben in den Papierkorb blockieren, korrigiert; geschützte Belegbezüge bleiben geschützt. Der bestehende Foto-Kontextdialog verwendet denselben R2-Editor. Konfliktvergleich zeigt fachliche Feldnamen/Zuordnungen, technische Schlüssel/Hashes nur im ausdrücklich geöffneten vollständigen Vergleich, ohne Bild-/PDF-Binärdaten im Diagnose-DOM.
- Zusätzlich gefunden und repariert: ausgestellte Rechnungen in der allgemeinen Rechnungsliste öffneten beim Antippen keine Details. Der vorhandene geschützte Detail-/Zahlungsweg ist jetzt auch von dort erreichbar. Regression prüft den tatsächlichen Listen-Tap und anschließende Zahlungsbestätigung.
- Neue Suche-/Planungsprüfung PASS: 111 synthetische Treffer über sieben bestehende Bereiche, 50/100/111 Pagination, unveränderte Daten, Index nach neuem Datenstand, erhaltener Suchtext, bestehender Serienauftrag und versteckte Leerlaufdiagnose. 320/360/426 px und vergrößerte Schrift geprüft. Vollständiger Browser-/JS-Runner **35/35 PASS**, 27 JVM-Suiten, 55 isolierte MCP-stdio-Szenarien und 3 P3-Baselineprüfungen PASS. Gartenpirat-Kollisionen einschließlich Positionspräferenz, Tastatur und Größenwechsel nochmals geprüft.
- Source-/Workspace-/bestehendes Artifact-Privacy-Gate PASS; P1-Vertrag exakt unverändert PASS, MCP9/117 und 156 semantische UI-Aktionen. Keine produktiven Daten, keine Pixel-Installation, keine tatsächliche Kontofreigabe/Cloud-/Versandbehauptung. R0–R6 sind implementiert; P3 bleibt bis R7 mit vollständigem erneutem 115-Aufgaben-UAT, nativer Regression und Originalkandidaten-/Updateprüfung **nicht abgenommen**.


## P3-R7 – Gesamtprüfung, Softwarestand vor nativer Kandidatenabnahme (20.10.2026)

- Erneute vollständige Software-Regression: **37/37 Browser-/JS-Suiten, 325 Python-Prüfungen ohne Skips, 27 JVM-Suiten und 55 synthetische MCP-stdio-Szenarien PASS**. 37 alte QA-Schlüsselerzeugungstests ausdrücklich ausgeschlossen, nicht abgeschwächt oder als PASS gezählt. Zusätzlich sechs kognitive Chromium-Durchläufe über bestehende isolierte Fixtures: Kunde/Kontakt/inaktiv, Timer/Material/Kilometer/Notiz, Kundenakte, Auftrag neu/korrigieren/abschließen, alle vier bewussten Dokumentarten einschließlich Eigenposition und Preisänderung, Einstellungen. Kein realer Kunde oder externer Versand. Vollständige 115-Aufgaben-Zuordnung und native Nachweise folgen unten nach tatsächlicher Ausführung.
- Vergleich mit unveränderlichem Vor-P3-main d1c5703 in identischem Chromium/360 px: Angebotsstart **21 → 5 gleichzeitig sichtbare Eingaben**; technische Syncdetails zuvor sichtbar, jetzt ausdrücklich erweitert; Kundenakte zuvor Übersicht/Garten/Fotos & Dateien/Verlauf, jetzt Übersicht/Arbeiten/Fotos/Dokumente. Öffentliche alte Assets ausschließlich im privaten OS-Testtemp, danach entfernt. Keine Baseline neu erzeugt und keine menschlichen Klickzeiten vorgetäuscht.
- Beim erweiterten UAT gefundenes Auftragsrennen behoben: früherer verzögerter Neu-Handler setzte nach 50 ms bereits eingegebene Angaben zurück, obwohl der aktuelle Auftragsdialog geöffnet war. Ist der vorhandene Auftragsdialog geladen, übernimmt nur dessen synchroner Neu-Pfad. Regression wartet nach Eingabe ausdrücklich über die alte Verzögerung hinaus, speichert, korrigiert denselben Auftrag und schließt ihn ab. Keine zweite Auftragslogik.
- Letzte Statuskorrekturen: bestätigte Arbeitsspeicherung verspricht keinen automatischen Drive-Abgleich bei ausgeschalteter Automatik; Foto-Speicherübersicht behauptet keine Medien-/Cloudbestätigung aus allgemeinem Syncstatus; unbekannte technische Fehlercodes/Hashes werden im Normalbetrieb übersetzt und nur flüchtig in Diagnose erhalten. Historische Konfliktbestätigung nennt Effekt und geprüfte Sicherung ohne Queue-/Providerbegriffe oder falsche automatische Erfolgszusage. Drive EIN/AUS ist aus Einstellungen direkt über den bestehenden nativen Zugang erreichbar; kein vorausgesetzter früherer Syncseiten-Aufruf.
- Source-/Workspace-/Original-81-Artifact-Gates PASS, einschließlich ignorierter/untracked Inhalte. P1-Gesamtvertrag weiter exakt **899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813**; 117 Tools, MCP9, 156 semantische UI-Aktionen unverändert. Generierte Pflanzen-/Angebotsverträge und UI-Reviewprovenienz geprüft. Neue Tests: p3-uat-walkthrough.test.cjs, p3-ux-comparison.test.cjs, p3_update_ledger.py; nativer UI-Test auf vier Aktenbereiche und drei erweiterbare Verbindungsmasken aktualisiert, keine Schutzassertion entfernt.
- Vor internem Update den tatsächlichen originalsignierten Cloud-AVD-Stand 81 ausschließlich lesend erfasst: 3 synthetische Kunden, 1 Auftrag, 2 Snapshot-Arbeiten, 1 vollständige Pending-Aktion, keine Fotos/Rechnungen/Angebote/Termine. Geschäft+Pending **be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af**, stabiler Sitzungshash **78070c6fcd158b6e8e24d651716c5277905bf57bed3fa8386628e5a3514925b1**, Sitzung RUNNING. Rohdaten nur RAM, ausschließlich Counts/Hashes/Schemabezug gespeichert. Historische Lifecycle-Baseline nicht zurückgesetzt.
- Quellversion für den internen Original-Prüfbuild **1.52.0/82** vorbereitet. Ausschließlich Vorhandensein der fünf dokumentierten HG_ORIGINAL_*-Variablen geprüft; vollständig vorhanden, keine Werte ausgegeben/gespeichert. **P3 noch nicht endgültig abgenommen:** native Ausführung, Artefaktprüfung und Updatevergleich stehen aus. Keine Bereitstellung/Installation auf dem physischen Pixel; P2-Restgrenzen bleiben offen.

- R7-Härtung nach dem internen ersten 82-Prüfbuild: Auch unbekannte kleingeschriebene technische Feldbezeichner (z. B. request_id/price_source/client_id) werden jetzt zentral aus Normalfehlermeldungen entfernt; der Originalfehler bleibt in der begrenzten flüchtigen Diagnose. Zusätzliche Negativprüfungen hierfür. Die bekannten Softwarepfade bleiben unverändert; der endgültige Kandidat wird aus diesem strengeren Fehlertextstand erneut gebaut und geprüft. Der erste interne 82-Build wird nicht bereitgestellt.


### P3-R7 – erneuter vollständiger 115-Aufgaben-UAT und endgültige Cloud-Abnahme

**Methode und Grenze:** Alle 115 ursprünglichen Aufgaben erneut abgeglichen, anhand der unten zugeordneten erneut ausgeführten Browser-/Native-/JVM-Szenarien und zusätzlicher kognitiver Chromium-Durchläufe bewertet. **96 PASS, 18 PASS MIT REIBUNG, 0 FAIL, 1 NOT TESTABLE** (Aufgabe 44: echter Android-Kamera-/Dateirechteentzug). Keine 115-fache physische Pixel-Abnahme und keine Studie mit einer tatsächlichen Erstbenutzer-Testperson. Native operative App im dedizierten synthetischen Cloud-AVD, API30, 720×1280/320dpi; Chromium zusätzlich 320/360/426px und vergrößerte Schrift. Reale Kamera-/Google-/Kalender-/Wetter-/Gmail-/Share-Freigaben bleiben externe Teilgrenzen. Die Softwarepfade dieser Aufgaben sind geprüft; Anbieterfunktionen wurden nicht produktiv aufgerufen.

**Tatsächlich beobachtet** sind die getesteten Pfade, Ergebniszustände, Versionsabwehr, Pagination, sichtbaren Eingaben und Layoutgrenzen. **Kognitive Bewertung/Empfehlung** sind Auffindbarkeit, Verständlichkeit, unnötige Orientierung und die mit ~ bzw. Spannen bezeichneten Schrittzahlen; keine gemessenen Erstbenutzer-Klickzeiten. Aktionen zählen Tap/Auswahl/Eingabe/Bestätigung je Feld, Startpunkt wie in der Aufgabe; bekannte Stammdaten werden nicht neu erfasst. Zahlen sind deshalb keine vergleichbaren Zeitbenchmarks.

Tabellenprofil **K**: normal auffindbar und fachlich verständlich, kein Technikvorwissen erforderlich, fachliche Eingabe-/Auswahlfehler bleiben möglich, gezielte Korrektur/Abbrechen/Versionenschutz getestet. **E**: fachliches Einrichtungs-/Preis-/Steuer-/Rechtswissen erforderlich; keine stille Automatik, Schutzbestätigung bzw. fehlende Freigabe ausdrücklich erhalten. Kein neuer S0/S1 im geprüften Softwareumfang; restliche Reibung S3, vollständiger automatischer Vollsicherungsplan S2 als spätere Umfangsentscheidung. Flagfolge **Technik sichtbar / im Alltag notwendig / automatisierbar / EIN-AUS-Potenzial**; bei Aufgabe110 bedeutet sichtbar ausschließlich bewusst geöffnetes Erweitert. Automatisierung bezieht sich nur auf sichere technische Schritte, niemals auf Preise, Rechtswahl, Lösch-/Abschlussentscheidung oder fingierte Bestätigungen.

**Ausgeführte Nachweise:** T1 daily-usability/design-concept/work-location/business-feedback und unveränderlicher Vorher-/Nachher-Vergleich; T2 Kundenakten-/Kunden-Duplikat-/Walkthrough-Tests; T3 worklog-window/design-v139/access-integrity/work-location/timer-completion/personal-payment plus native Timer/SQLite; T4 customer-photos/offers-photo-library/p3-business-actions/work-entry-photo-failure plus native Dateien/Optimierung/Rotation; T5 neuer Auftragsdurchlauf und bestehende Auftrags-/Relationsprüfungen; T6 planning-ui/p3-search-planning plus PlanningNativeAcceptance; T7 offers-photo-library/Walkthrough/EstimateNativeAcceptance/Offer-JVM und native PDF-/Delivery-Negativpfade; T8 p3-documents/invoice-flow/invoice-editor/p3-business-actions plus native Entwurf/Nummern/PDF/Zahlung; T9 p3-search-planning/daily-usability/access-integrity; T10 sync-trash/worklog-batch und native/JVM Papierkorb/Relations-/Purgeprüfungen; T11 NativeDriveAcceptance/DriveProviderAcceptance/SQLite/Browser-Fehlerpfade; T12 p3-business-actions/Walkthrough/business-feedback/native Backup/Restore und Drive-Automatikprobe; T13 Original-Updateledger/native fresh_isolated/ui/SQLite-Neuöffnung und Browser-Entwurf/Reload. Die Prüfungen sind zusammengesetzte Nachweise über die tatsächlichen Schichten; ein Bridge-Double wird nicht als realer Anbieterabschluss ausgegeben.

| Nr. | Erneut geprüfte Aufgabe / aktueller Weg | Ergebnis | Schritte/unnötig vorher → nachher (~) | Profil; Technik/Notwendig/Auto/EIN-AUS | Severity | Beobachtungsnachweis / Grenze |
|---|---|---|---|---|---|---|
| 001 | Erster Start: leerer isolierter Bestand; keine Produktivdaten | PASS | ~1/0 → ~1/0 | K; N/N/N/N | – | T1; N: leerer eigener Namensraum |
| 002 | Heute: Tages-/Wochensumme, nächster Einsatz, letzter Eintrag | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T1; B: operative Assets; T1-Nachweis |
| 003 | Heute: einheitlich Arbeit starten | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T1; B: operative Assets; T1-Nachweis |
| 004 | Heute: laufende/pausierte Sitzung und Abschluss sind sichtbar | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T1; B: operative Assets; T1-Nachweis |
| 005 | Heute: bestätigtes Speichern und Drive-Status verständlich getrennt | PASS | ~0–1/0 → 0/0 | K; N/N/J/N | – | T1; B: operative Assets; T1-Nachweis |
| 006 | Start ohne technische Drive-Zähler oder Wartungsaufforderung ohne Fall | PASS | 0/0 → 0/0 | K; J/N/J/N | S4 | T1; Versionsnummer bleibt reine Info im Markenkopf, kein erforderliches Vorwissen |
| 007 | Heute → Kunden → Neu → Name/Straße/PLZ/Ort → speichern | PASS | ≥7/0 → ≥7/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 008 | Name und Adresse genügen; Telefon/E-Mail optional | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 009 | Kundenliste → Akte → bearbeiten → Telefon → speichern | PASS | ≥4/0 → ≥4/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 010 | Geöffneter Kundeneditor → E-Mail ergänzen → speichern | PASS | ≥2/0 → ≥2/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 011 | Kundenliste: Suchfeld unten, ein Suchbegriff | PASS | ≥1/0 → ≥1/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 012 | Kundenliste: Treffer öffnet Kundenakte | PASS | ≥1/0 → ≥1/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 013 | Akte: letzte Arbeiten sichtbar; ältere über vollständige Arbeitsliste/Verlauf | PASS | ~0–2/0 → ~0–2/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 014 | Kundenakte → Fotos mit begrenzten Vorschauen | PASS | ≥1/0 → ≥1/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 015 | Akte → alle Arbeiten; fremde Zeit-/Suchfilter werden zurückgesetzt | PASS | ~1–2/0 → ~1–2/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 016 | Kundenakte: Dokumente bündelt Angebote/Kostenvoranschläge/Rechnungen; Termin im Kopf | PASS | ~1–3/~1 → 1–2/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 017 | Akte → Kundendaten bearbeiten → Änderung → speichern | PASS | ≥3/0 → ≥3/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 018 | Kundeneditor: Kunde aktiv AUS; Entfernung separat über Papierkorb | PASS | ~4–6/~1 → 4–5/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 019 | Inaktive Kunden bleiben suchbar; Papierkorb hat Wiederherstellen | PASS | ~2–4/0 → ~2–4/0 | K; N/N/N/N | – | T2; B: operative Assets; T2-Nachweis |
| 020 | Heute → Start → Kundenauswahl → Start; kein Start allein durch Ortsermittlung | PASS | ≥3/0 → 3/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 021 | Start: Kunde bestätigen, Auftrag/Ort optional, GPS nur ausdrücklich | PASS | ~2–4/~1 → 1–3/0 optional | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 022 | Laufende Sitzung → Pause | PASS | ≥1/0 → ≥1/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 023 | Pausierte Sitzung → Fortsetzen | PASS | ≥1/0 → ≥1/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 024 | Laufende Sitzung → Ende; Arbeitsaufzeichnung erst nach ergänzen/speichern | PASS | ≥1/0 → ≥1/0 | K; N/N/J/N | – | T3; B: operative Assets; T3-Nachweis |
| 025 | Abschluss → Tätigkeit → speichern; Timer-Nettozeit wird übernommen | PASS | ≥2/0 → ≥2/0 | K; N/N/J/N | – | T3; B: operative Assets; T3-Nachweis |
| 026 | Abschluss → Material und Kilometer → Material | PASS | ≥2/0 → ≥2/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 027 | Offene Materialgruppe → Kilometer eintragen | PASS | ≥1/0 → ≥1/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 028 | Arbeit → Fotos und Notizen → Datei; Kamera-/Pickergrenze separat | PASS MIT REIBUNG | ~3–5/0 → 3–5/0; OS offen | K; N/N/N/N | S3 | T3; B/N: Picker-/Fehlerpfad; Kamera extern |
| 029 | Notiz im selben Abschluss; Nachtrag wird ausdrücklich sofort gespeichert | PASS | ~2–3/~1 → 2–3/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 030 | Arbeiten → Eintrag → Bearbeiten → Korrektur → Speichern | PASS | ≥4/0 → ≥4/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 031 | Editor → Uhrzeiten/Zeit und Pause → Beginn/Ende; Dauer und Uhrzeiten erklärt | PASS MIT REIBUNG | ~4–6/~1 → ~4–6/~1 | K; N/N/J/N | S3 | T3; B: operative Assets; T3-Nachweis |
| 032 | Editor → Zeit und Pause → Pause → speichern; Nettozeit nicht doppelt reduzieren | PASS | ~3–4/0 → ~3–4/0 | K; N/N/J/N | – | T3; B: operative Assets; T3-Nachweis |
| 033 | Editor → Tätigkeit → speichern | PASS | ≥2/0 → ≥2/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 034 | Editor → Kunde/Auftrag; Auftrag wird nur passend übernommen | PASS | ~3–6/0 → ~3–6/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 035 | b.b. als persönliche Markierung mit Erklärung und Bestätigung im Toast | PASS | ~1–2/0 → 1/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 036 | Persönliches b.b., App-Verrechnet und Rechnungszahlung bleiben getrennt | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T3; B: operative Assets; T3-Nachweis |
| 037 | Aktive Arbeit → Foto; Sitzung wird nach Abschluss mit Arbeit verbunden | PASS MIT REIBUNG | ~3–5/0 → 3–5/0; Kamera offen | K; N/N/N/N | S3 | T4; B/N: exakte Sitzung/Arbeitszuordnung; Kamera extern |
| 038 | Kundenakte → + Foto → Galerie/Kamera; Kunde vorgegeben | PASS | ≥2/0 → ≥2/0 | K; N/N/N/N | – | T4; B: operative Assets; T4-Nachweis |
| 039 | Foto hinzufügen → Aufnahmephase wählen; Vorher/Während/Nachher | PASS | ~1–2/0 → ~1–2/0 | K; N/N/N/N | – | T4; B: operative Assets; T4-Nachweis |
| 040 | Bildbeschreibung beim Import und über Foto → Mehr → Bearbeiten | PASS | ≥1/0; später — → 4–6/0 nachträglich | K; N/N/N/N | – | T4; B: operative Assets; T4-Nachweis |
| 041 | Vorhandenes Foto → Mehr → Bearbeiten → Arbeit/Auftrag → Speichern | PASS | —/— → 5–7/0 inkl. Auswahl | K; N/N/N/N | – | T4; B: operative Assets; T4-Nachweis |
| 042 | Akte/Fotobibliothek → Filter/Vorschau → Vollbild, Blättern und Zoomen | PASS | ~2–4/0 → ~2–4/0 | K; N/N/N/N | – | T4; B: operative Assets; T4-Nachweis |
| 043 | Foto → Mehr → Papierkorb → Bestätigung; danach Undo/Wiederherstellen | PASS | ~3–4/0 → ~3–4/0 | K; N/N/N/N | – | T4; B: operative Assets; T4-Nachweis |
| 044 | Kamera-/Dateiberechtigung: Speicherfehler/Entwurf getestet; echter OS-Entzug nicht durchgeklickt | NOT TESTABLE | —/— → —/— | K; N/N/N/N | – | T4; B/N: Speicher-/Entwurfsfehler geprüft; echter OS-Entzug nicht ausführbar |
| 045 | Vollbild ohne Hash/Pfad; fehlendes Original verständlich benannt | PASS MIT REIBUNG | 0/0 → 0/0 | K; N/N/N/N | S3 | T4; B: operative Assets; T4-Nachweis |
| 046 | Mehr → Aufträge → Neu → Kunde/Titel → Speichern | PASS MIT REIBUNG | ~6–9/~1 → 6–8/0 Pflichtangaben | K; N/N/N/N | S3 | T5; B: operative Assets; T5-Nachweis |
| 047 | Auftrag aus Kundenakte übernimmt Kunden; globaler Dialog erfordert Auswahl | PASS | ~0–2/0 → ~0–2/0 | K; N/N/N/N | – | T5; B: operative Assets; T5-Nachweis |
| 048 | Arbeit → optional Auftrag; Auswahl auf Kunden eingeschränkt | PASS | ~2–3/0 → ~2–3/0 | K; N/N/N/N | – | T5; B: operative Assets; T5-Nachweis |
| 049 | Auftrag → Überblick → Bearbeiten → Änderung → speichern | PASS MIT REIBUNG | ~4–5/~1 → ~4–5/~1 | K; N/N/N/N | S3 | T5; B: operative Assets; T5-Nachweis |
| 050 | Auftragsstatus Erledigt; Archivierung/Entfernung extra und wiederherstellbar | PASS MIT REIBUNG | ~3–5/~1 → ~3–5/~1 | K; N/N/N/N | S3 | T5; B: operative Assets; T5-Nachweis |
| 051 | Aufträge: ein gemeinsamer fachlicher Bereich, kein zweites Projektsystem | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T5; B: operative Assets; T5-Nachweis |
| 052 | Mehr → Planung → Neu → Kunde/Tätigkeit/Beginn/Dauer → speichern | PASS MIT REIBUNG | ~8–11/~1 → ~8–11/~1 | K; N/N/N/N | S3 | T6; B: operative Assets; T6-Nachweis |
| 053 | Termin aus Kunde/Auftrag übernimmt Kontext; global bewusst auswählen | PASS | ~0–2/0 → ~0–2/0 | K; N/N/N/N | – | T6; B: operative Assets; T6-Nachweis |
| 054 | Termin → Beginn/Dauer → speichern; Alternative bleibt zunächst Entwurf | PASS | ~3–5/0 → ~3–5/0 | K; N/N/N/N | – | T6; B: operative Assets; T6-Nachweis |
| 055 | Termin absagen mit Bestätigung; Auftrag/Arbeit/Rechnung bleiben erhalten | PASS | ~2–3/0 → ~2–3/0 | K; N/N/N/N | – | T6; B: operative Assets; T6-Nachweis |
| 056 | Termin: eigene aufklappbare Wiederholung bei Beginn/Dauer | PASS | ~4–6/~1 → 3/0 zusätzlich | K; N/N/N/N | – | T6; B: operative Assets; T6-Nachweis |
| 057 | Kundenakte → Termin, Kunde bereits gesetzt | PASS | ~2–3/0 → 1/0 ab Akte | K; N/N/N/N | – | T6; B: operative Assets; T6-Nachweis |
| 058 | Kalenderabgleich EIN/AUS; Konto-/OAuth-Einrichtung erweitert | PASS MIT REIBUNG | ~2 bis Einrichtung/~1 → 1–2/0; Erstfreigabe extern | E; N/N/N/J | S3 | T6; B/N: native Oberfläche/Schutz geprüft; echtes Google-Konto offen |
| 059 | Wetterhinweise/Benachrichtigung EIN/AUS; Lizenz/Schlüssel erweitert | PASS MIT REIBUNG | ~2 bis Einrichtung/~1 → 1–2/0; Erstfreigabe extern | E; N/N/N/J | S3 | T6; B/N: native Oberfläche/Risiko-Provider geprüft; echter Wetterzugang offen |
| 060 | Kundenakte → Angebot → Kontext/Art → Leistungen → fehlende Angaben | PASS | ≥3/~1 → 3–5/0 bis Leistung | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 061 | Dokumentart unverbindlicher Kostenvoranschlag + verständliche Bindungsbestätigung | PASS | ~2–3/0 zusätzlich → ~2–3/0 zusätzlich | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 062 | Dokumentart verbindlicher Kostenvoranschlag + erneute Bestätigung nach Wechsel | PASS | ~2–3/0 zusätzlich → ~2–3/0 zusätzlich | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 063 | Dokumentart Fixpreis; keine automatische Bindungsannahme | PASS | ~1–2/0 zusätzlich → ~1–2/0 zusätzlich | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 064 | Dokumentart Regie; Mengen als geplante Stunden und Preisgrundlage | PASS | ~1–2/0 zusätzlich → ~1–2/0 zusätzlich | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 065 | Baustein wählen → Menge; bestätigter Preis wird übernommen | PASS | ≥2/0 → ≥2/0 | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 066 | Eigene Angebotsposition im Leistungsschritt | PASS MIT REIBUNG | ~5–9/~2 → 6–9/0 fachliche Angaben | E; N/N/N/N | S3 | T7; B: operative Assets; T7-Nachweis |
| 067 | Bewusst geänderter Preis löst Katalogbezug, behält dokumentierte Grundlage | PASS MIT REIBUNG | ~1–3/~1 → 1–3/0 | E; N/N/N/N | S3 | T7; B: operative Assets; T7-Nachweis |
| 068 | Texte und Hinweise → editierbarer Text oder Baustein | PASS | ~2–3/0 → ~2–3/0 | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 069 | Prüfen und Vorschau; fehlende Angaben inline, keine erfundene Summe | PASS | ≥1/0 → ≥1/0 | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 070 | Finalisieren → Prüfung → Vorschau/Bestätigung; keine automatische Rechnung | PASS | ~2–3/0 → ~2–3/0 | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 071 | Dokumentstatus fachlich getrennt und lesbar, kein Versand durch Finalisieren | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T7; B: operative Assets; T7-Nachweis |
| 072 | Teilen-App ist kein Versandbeweis; Text sagt ausdrücklich „nicht bestätigt“ | PASS | ~1–2/0 → ~1–2/0 | K; N/N/N/N | – | T7; B/N/J: keine falsche Versandbestätigung; echtes Teilen/Versand offen |
| 073 | Rechnung vorbereiten aus offenen Leistungen, keine ausgestellten/reservierten Arbeiten | PASS | ~9–13/~3 → 4–7/0 plus echte Preise | K; N/N/N/N | – | T8; B: operative Assets; T8-Nachweis |
| 074 | Rechnung: Kunde und Rechnungsadresse kontrollieren, Einsatzort getrennt erhalten | PASS | ~0–2/0 → ~0–2/0 | K; N/N/N/N | – | T8; B: operative Assets; T8-Nachweis |
| 075 | Vorgeschlagene offene Arbeiten prüfen; Auswahl vor Entwurf erneut validiert | PASS | ~3–5/~1 → 1–2/0 | K; N/N/N/N | – | T8; B: operative Assets; T8-Nachweis |
| 076 | Positionen/Mengen/Preis kontrollieren; keine erfundenen Preise | PASS | ~0–3/0 → ~0–3/0 | K; N/N/N/N | – | T8; B: operative Assets; T8-Nachweis |
| 077 | Zahlungsziel zentral vorbelegt, im Entwurf prüfbar | PASS | ~0–1/0 → ~0–1/0 | K; N/N/N/N | – | T8; B: operative Assets; T8-Nachweis |
| 078 | Rechnung abschließen: fehlende Einrichtung geführt; zentrale Autorität/Bestätigung bleibt | PASS MIT REIBUNG | ~2–4/0 → 2–4/0; Einrichtung separat | E; N/N/N/N | S3 | T8; B/N/J: lokale Nummern-/PDF-Pipeline; echte produktive Autorität nicht betätigt |
| 079 | Mehr → Rechnungen → Suchfeld/Statusfilter | PASS | ~3–4/0 → ~3–4/0 | K; N/N/N/N | – | T8; B: operative Assets; T8-Nachweis |
| 080 | Entwurf/Gestellt/Bezahlt/Storniert getrennt; Versand nicht automatisch behauptet | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T8; B: operative Assets; T8-Nachweis |
| 081 | Rechnungsliste → Detail → Zahlung erfassen → bestätigen | PASS | —/— → 2/0 ab Liste | K; N/N/N/N | – | T8; B: operative Assets; T8-Nachweis |
| 082 | Entwurf zeigt Steuerart, Empfängertyp, UID und Inlandsfall in normalem Formular | PASS MIT REIBUNG | 0/0 → 0/0 | E; N/N/N/N | S3 | T8; B: operative Assets; T8-Nachweis |
| 083 | Kunden: ein lokales Suchfeld, sichtbare Treffer | PASS | ≥1/0 ab Liste → ≥1/0 ab Liste | K; N/N/N/N | – | T9; B: operative Assets; T9-Nachweis |
| 084 | Arbeiten: Suche plus Zeitraum; fremde Filter zurücksetzbar | PASS | ~1–3/0 ab Liste → ~1–3/0 ab Liste | K; N/N/N/N | – | T9; B: operative Assets; T9-Nachweis |
| 085 | Fotos über Kundenakte/Filter oder übergreifende Metadatensuche | PASS | ~3–6/~2 → 2–3/0 | K; N/N/N/N | – | T9; B: operative Assets; T9-Nachweis |
| 086 | Rechnungen: Textsuche und Statusfilter | PASS | ~1–2/0 ab Liste → ~1–2/0 ab Liste | K; N/N/N/N | – | T9; B: operative Assets; T9-Nachweis |
| 087 | Termine über Planung oder übergreifende Lesesuche | PASS | ~3–5/~1 → 2–3/0 | K; N/N/N/N | – | T9; B: operative Assets; T9-Nachweis |
| 088 | Ein übergreifender Lesezugang zu sieben bestehenden Bereichen | PASS | —/— → 2–3/0 | K; N/N/N/N | – | T9; B: operative Assets; T9-Nachweis |
| 089 | Nicht mehr aktiv / In Papierkorb / Wiederherstellen sprachlich getrennt | PASS MIT REIBUNG | ~2–4/~1 → 2–4/0 | K; N/N/N/N | S3 | T10; B: operative Assets; T10-Nachweis |
| 090 | Entfernen/Papierkorb direkt möglich, auch mit Fotos; Originale geschützt | PASS | ~1–3/0 → ~1–3/0 | K; N/N/N/N | – | T10; B: operative Assets; T10-Nachweis |
| 091 | Mehr → Papierkorb → Wiederherstellen; bestehende Verknüpfungen werden geprüft | PASS | ~3–4/0 → ~3–4/0 | K; N/N/N/N | – | T10; B: operative Assets; T10-Nachweis |
| 092 | Einzel-Purge oder Papierkorb leeren → eine mengen-/versionsgebundene Bestätigung | PASS | ~2–4/0 → ~2–4/0 | K; N/N/N/N | – | T10; B: operative Assets; T10-Nachweis |
| 093 | Inaktiv, erledigt und Papierkorb verständlich benannt; unterschiedliche Wirkungen erhalten | PASS MIT REIBUNG | 0/0 → 0/0 | K; N/N/N/N | S3 | T10; B: operative Assets; T10-Nachweis |
| 094 | Endgültiges Löschen geschützt; aktive Referenzen und gestellte Belege bleiben erhalten | PASS | ~1 Bestätigung/0 → ~1 Bestätigung/0 | K; N/N/N/N | – | T10; B: operative Assets; T10-Nachweis |
| 095 | Hash/Preview/CAS im normalen Papierkorb unsichtbar; Konflikt zeigt nur fachliche Entscheidung | PASS | 0/0 → 0/0 | K; N/N/N/N | – | T10; B: operative Assets; T10-Nachweis |
| 096 | Offline fachlich arbeiten; lokale Speicherung unabhängig von Netz | PASS | 0/0 zusätzlich → 0/0 zusätzlich | K; N/N/J/N | – | T11; B/N: synthetisch offline |
| 097 | Offline Arbeit speichern; Wiederholung keine Doppelbuchung | PASS | ~1/0 zusätzlich → ~1/0 zusätzlich | K; N/N/N/N | – | T11; N: echte isolierte SQLite-Persistenz/Retry |
| 098 | Offline Kunde speichern und später ändern; Vorgängerkette erhalten | PASS | ~1/0 zusätzlich → ~1/0 zusätzlich | K; N/N/N/N | – | T11; B/N: Kunden-ID/Vorgängerkette |
| 099 | Offline Foto sichern; getrennte Medienwarteschlange, Fehler bewahrt Entwurf | PASS | ~1–3/0 zusätzlich → ~1–3/0 zusätzlich | K; N/N/N/N | – | T11; B/N: lokale Originaldatei/Entwurf getrennt |
| 100 | App-/Store-Neustart nach synthetischen Änderungen | PASS | ~1–2/0 → ~1–2/0 | K; N/N/J/N | – | T11; B/N: Store-Neuöffnung und App-Update |
| 101 | Testdaten, IDs, Beziehungen nach Wiederöffnung erhalten | PASS | 0/0 → 0/0 | K; N/N/J/N | – | T11; N: Counts, Hashes und Relationen |
| 102 | Netz/Provider wieder verfügbar: synthetischer Retry, kein echtes Google-Konto | PASS | 0/0 → 0/0 | K; N/N/J/N | – | T11; N: isolierter Provider wieder verfügbar; kein Google-Konto |
| 103 | Drive auslösen: UI jetzt abgleichen; Provider- und Direkt-HTTP-Test doubles getrennt | PASS | ~1–3/~1 → ~1–3/~1 | K; N/N/J/N | – | T11; B/N: Auslösung/Providerstrecke; kein realer Drive-Rundlauf |
| 104 | Unterbrechung/401/429/Schreibfehler im isolierten Provider, alte Daten/Queue bleiben | PASS | 0/0 für Fehlerauslösung → 0/0 für Fehlerauslösung | K; N/N/J/N | – | T11; N/J: Abbruch, 401/429, Retry und Erhalt |
| 105 | Verständlicher Fehlertext; technische Rohdaten nur ausdrücklich Diagnose | PASS | ~0–2/~1 → 0–1/0 | K; N/N/J/N | – | T11; B: operative Assets; T11-Nachweis |
| 106 | Einstellungen → Sicherung: zuletzt tatsächlich geprüfte Sicherung mit Zeitpunkt | PASS | —/— → 1–2/0 ab Heute | K; N/N/N/N | – | T12; B/N: lokale Sicherung/Dateihash; kein Vollgerätebackup |
| 107 | Automatik nur für Drive-Abgleich; keine automatische vollständige Sicherung erfunden | PASS MIT REIBUNG | 0/0 → 0/0; kein neuer Automatismus | E; N/N/N/N | S2 | T12; S: bewusst begrenzter Umfang; Vollsicherungsplan nicht neu beauftragt |
| 108 | Drive-Automatik EIN/AUS, manueller Abgleich und lokale Sicherung bleiben getrennt | PASS | 0/0 → 2–3/0 ab Einstellungen | K; N/N/N/J | – | T12; B/N: echter privater Automatikschalter, keine Erfolgsgarantie |
| 109 | Einstellungen → Sicherung → Jetzt sichern / Wiederherstellen mit Prüfung | PASS | ~2–4/~1 → 2–4/0 plus Bestätigung | K; N/N/N/N | – | T12; B: operative Assets; T12-Nachweis |
| 110 | Alle Einstellungsentscheidungen erneut auf Normalbetrieb/Erweitert geprüft | PASS MIT REIBUNG | unterschiedlich; unten → je Einstellung; kein Doppelpfad | E; J/N/N/N | S3 | T12; B/N/S: alle 56 bisherigen Entscheidungen plus Drive-Automatik; Details erweitert |
| 111 | Bestehenden synthetischen Bestand schließen; keine Deinstallation/kein Löschen | PASS | ~1–2/0 → ~1–2/0 | K; N/N/J/N | – | T13; N: kein Löschen/Deinstallieren |
| 112 | Operative App wieder öffnen, gleicher Testbestand | PASS | ~1/0 → ~1/0 | K; N/N/J/N | – | T13; N: Original-In-place-Update und WebView |
| 113 | Arbeits-/Angebots-/Termin-Entwürfe und private Dateien bleiben im geprüften Scope erhalten | PASS | 0/0 → 0/0 | K; N/N/J/N | – | T13; B/N: Entwürfe, Medien und Bezüge isoliert |
| 114 | Laufende/pausierte Sitzung bzw. offener Abschluss rekonstruierbar, kein zweiter Timer | PASS | ~0–1/0 → ~0–1/0 | K; N/N/J/N | – | T13; N: stabile laufende Sitzung ohne Doppelstart |
| 115 | Normaler Neustart ohne technischen Wiederherstellungsdialog; Foto-Fehler fordert nur gezielte Aktion | PASS | ~0–1/0 → ~0–1/0 | K; N/N/J/N | – | T13; B/N: kein technischer Wiederherstellungsdialog |

#### Befunde U01–U17: erneuter Abgleich und weniger Reibung

| Befund | Tatsächlicher Stand nach P3 / Abnahmeevidenz |
|---|---|
| U01 | PASS: vorhandenes Foto bearbeiten/zuordnen, Öffnungshash, gezielte Änderungen, Retry und expliziter Vergleich; Datei/Foto-ID unverändert, kein Reimport |
| U02 | PASS: sichtbare Zahlungsaktion auch nach tatsächlichem Tap in der Rechnungsliste; Bestätigung und entity_hash, idempotent; b.b./Original/PDF unverändert |
| U03 | PASS im vorhandenen lokalen Geschäftssicherungsumfang: Liste, tatsächlich geprüfte Größe/SHA, Zeit, Jetzt sichern, inspektions-/state_hash-geschützter Restore; beschädigte Sicherung kein falsches Erfolgslabel |
| U04/U06 | Weniger Reibung: eine Bereitschaftsanzeige mit nächstem Einrichtungsschritt und drei Gruppen; bestätigte bestehende Werte wiederverwenden. Nummernautorität und Aussteller-/Steuer-/Parallelkreisschutz bleiben, reale Konto-/Nummernfreigabe nicht automatisiert |
| U05 | Weniger Reibung: Alltag enthält fachlichen Speicher-/Handlungsstatus; technische lokale/Übertragungszähler im identischen Vorher-/Nachher-Test sichtbar → verborgen. Unbekannte obere/untere technische Codes/Feldschlüssel nur Diagnose; keine falschen Sicherungsbehauptungen |
| U07 | Weniger Reibung: offener Empfänger/Leistungen → Rechnung vorbereiten → Auswahl/Preise → Entwurf, ungefähr 9–13 → 4–7 Bedienhandlungen plus tatsächlich fehlende Preise. Vorhandene Entwürfe/gestellte Arbeiten und veraltete Auswahl geschützt |
| U08 | Weniger Reibung: Angebotsstart 21 → 5 gleichzeitig sichtbare Eingaben bei gleicher Umgebung/Fixture; drei Präsentationsschritte, Bindungsart weiterhin bewusst. Eigenposition/Preisänderung/Notizen und Pflichtprüfung erhalten; keine behauptete menschliche Zeitmessung |
| U09 | Weniger Reibung: Akte → Dokumente (1 Bereichswahl), statt Angebot zwischen Fotodateien zu suchen; 30 begrenzt, Rechnungen/Angebote/Kostenvoranschläge getrennt benannt |
| U10 | Wiederholung bei Beginn/Dauer in eigenem klaren Abschnitt; vorhandenes Serienwerkzeug, kein neues Seriendatenmodell |
| U11 | Weniger Verständnisreibung: persönliche Erklärung/zugängliche Beschriftung und Rückmeldung; sichtbare echte Rechnungszahlung getrennt. Kein b.b.-Datenumbau; Heute bleibt bezahlt |
| U12 | Weniger Begriffsreibung: Aufträge, Nicht mehr aktiv, In Papierkorb, Wiederherstellen und Endgültig löschen konsistent. Unterschiedliche Lebenszyklen bleiben sichtbar, gefährliche Bestätigung bleibt eine fachliche Entscheidung |
| U13 | Bei 0 Fällen keine normale Wartungsaufforderung; Historie/technische Einträge ausdrücklich erweitert. Aktive Konflikte bleiben sichtbar, keine automatischen Altentscheidungen |
| U14 | Überall suchen über sieben bestehende Projektionen, reine Metadaten, 50/100/111 Pagination, Index je Revision, Entwurf/Fokus erhalten |
| U15 | Weniger Aktionsreibung: im Arbeitseditor Speichern/Abbrechen statt redundanter dritter Fußaktion; Zurück-Wirkung explizit. Kunde/Auftrag behalten Verwerfen-Bestätigung. Keine unvermerkte Änderung des bewährten Speicherverhaltens |
| U16 | Arbeit starten/Arbeit nachtragen im heutigen Ablauf konsistent; Timer oben, kein GPS-Zwang, optionaler Auftrag |
| U17 | Normale Betriebsfreigaben EIN/AUS; Konten/Schlüssel/Lizenz/Schwellen ausdrücklich erweitert. Native echte View-Prüfung der drei Verbindungsmasken, ohne Kontofreigabe oder Schlüsseländerung |

**Verbleibende Empfehlung, kein offener P3-Funktions-FAIL:** Rechts-/Steuer-/Preisentscheidungen bleiben fachliche Einrichtung. Uhrzeiten/Dauer und unterschiedliche Lebenszyklen verlangen etwas Betriebswissen. Ein vollständiger automatischer Vollsicherungs-/Aufbewahrungsplan ist nicht durch den Drive-Schalter implementiert und wird nicht als aktiv behauptet. Kamera-/Kontorechte benötigen echte Gerätefreigaben. Die reine Versionsinfo im Markenkopf bleibt kosmetisch sichtbar; sie verlangt keine technische Bedienentscheidung. Keine weiteren Funktionen, kein Lager, keine Signatur-/Touren-/KI-Ersatzarchitektur ergänzt.

#### P3.13: Abnahme im ausdrücklich definierten Cloud-Prüfumfang

| Nr. | Kriterium | Ergebnis |
|---|---|---|
| 1 | U01/U02/U03 | PASS über UI + vorhandene native Anwendungs-/Persistenzpfade; keine physische Anbieterbehauptung |
| 2 | Kein neuer UAT-FAIL | PASS: 0 FAIL; alle 115 Aufgaben erneut bewertet, echter OS-Rechteentzug ausdrücklich NOT TESTABLE |
| 3 | U05/U06/U07/U08/U09/U11/U12/U15 weniger Reibung | PASS: obiger Vorher-/Nachher-Abgleich, sichtbare Entscheidungen/Pfade/Begriffe; keine erfundenen Zeitgewinne |
| 4 | Keine Request-/Hash-/CAS-/Queue-/Journal-/Provider-/Transportbedienung notwendig | PASS: normale Fachtexte, vollständige Diagnose nur ausdrücklich erweitert |
| 5 | Kein falsches gesichert/gesendet/bezahlt/ausgestellt | PASS: Zustands-/Fehler-/Pending-/Readback-/Share-/Zahlungsprüfungen, native Nummernautorität unverändert |
| 6 | Heute/Arbeit ohne Technik | PASS: manueller Kunde, Auftrag optional, GPS nur ausdrücklich, Timer/Nettozeit und Fokus erhalten |
| 7 | Kundenakte zentral | PASS: vier Bereiche und exakte Arbeits-/Termin-/Dokumentkontexte |
| 8 | Offene Leistungen → Entwurf | PASS: keine doppelte Reservierung, kein unbekannter Nullpreis, keine automatische Ausstellung/Abrechnung |
| 9 | Foto nachträglich ohne Duplikat | PASS: gezielte Metadatenänderung, gleiche Datei/ID, Versionsschutz |
| 10 | Rechnungszahlung sichtbar/getrennt | PASS: Zahlung bestätigt/hashgeschützt; b.b. und Original unverändert |
| 11 | Sicherung/Restore verständlich/geschützt | PASS im vorhandenen Geschäftsbackupumfang, Grenzen unten ausdrücklich erhalten |
| 12 | P0 Source/Workspace/Artifact | PASS für tatsächlichen finalen 82-Kandidaten; ignorierte/untracked Dateien eingeschlossen |
| 13 | P1 eingefrorener Vertrag | PASS exakt 899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813; keine Baseline neu geschrieben |
| 14 | App-ID/Formate/IDs/Autorität/Originalsignatur | PASS: Originalzertifikat gleich81; DB4/Snapshot2, vollständiger Updateledger und zentrale Regeln unverändert |
| 15 | Keine produktiven Testdaten | PASS: qa_/test_-Fixtures und eigene Stores, reine Metadaten-/Hashberichte; kein physischer Pixel-ADB |
| 16 | P2-Grenzen bleiben offen | PASS: keine produktive Löschung/Konfliktauflösung/Sicherungsmigration oder historische Freigabe erfunden |

**Status:** P3-R0–R7 sind implementiert und im erlaubten Cloud-Prüfumfang abgenommen. Dies ist ausdrücklich **keine uneingeschränkte 115-Aufgaben-Geräte-/Anbieterabnahme** und keine Installationsfreigabe. Die in P3.12 erlaubten externen Grenzen bleiben NOT TESTABLE/offen. Der Originalkandidat wird für die nach Sicherung gesondert freizugebende physische Abnahme bereitgestellt.

#### Technischer Abschlussnachweis und Kandidat

- Ausgang: main **d1c57034f7c1e74152f96d62a3cecaaaaccf511e**, Originalquelle **1.51.2/81**. R0-Baseline unverändert; kleine normale Phasencommits, keine Branchanlage/Force-Push/Historyänderung. Endgültiger APK-Quellcommit **89281daf5d5cfb48a1e08fac931f60deb451ddc7**; Release-Fingerprint **16d1e8aa7b83d82c1077d445e31e86f0cb609b61ae345fabc3feafed35ed5b20**, Buildtyp release, source_dirty=false. Beide Fingerprints zusätzlich in den tatsächlichen signierten DEX-Strings nachgewiesen. Abschlussdokumentation separat versioniert.
- Tests des endgültigen Stands: **325 Python PASS, 0 Skips** (37 alte QA-Schlüsselerzeugungstests explizit ausgeschlossen/erhalten), **27 JVM-Suiten PASS**, **37 Browser-/JS-Suiten PASS**, **55 synthetische MCP-stdio-Szenarien PASS**. Alle 117 registrierten MCP-Verträge, UI-Pagination 100+56/einheitlicher Hash, Schema-/Enum-/Berechtigungs-/Negativpfade und read-only Self-Test durch bestehende Contract-/Nativeprüfungen. Stdio ist ein Kanaladaptertest, kein realer Pixel-Connector.
- Original Android-Release und bestehender originalsignierter androidTest-Runner: **137 native Prüfungen PASS, acceptance failures=0**, danach **ui PASS**, **fresh_isolated PASS**. Echter WebView/SQLite, drei native Verbindungsmasken: 1/1/3 normale Schalter, keine technischen Eingabefelder sichtbar bis Erweitert, öffnen/schließen ohne Speichern/Kontoverbindung. Native Abnahme nochmals am endgültigen 82-Kandidaten ausgeführt, keine Deinstallation oder Datenlöschung.
- Screenshot-Sichtprüfung des endgültigen Builds: klar lesbare aktive Arbeit/Pause/Beenden/Foto und vier Hauptziele, kein verdeckter Gartenpirat, **kein System-UI-ANR-Dialog**. Der zuvor tatsächlich vorhandene com.android.systemui-ANR wurde im dedizierten AVD durch Auswahl des eindeutig geprüften OS-Buttons Wait verlassen, nicht durch einen App-/Datenreset; dies ist kein behaupteter App-Fix für einen OS-/Pixel-ANR. Emulator API30/Softwaredarstellung ist keine Pixel-Leistungsmessung.
- Updatekette **Original81 → interner82-Prüfbuild → endgültiger82-Kandidat**, jeweils normales install -r im exklusiv geprüften GartenpiratCloud/emulator-5582, ohne Wipe/Uninstall. Der erste interne82-Build wurde nie bereitgestellt und nach SHA-Prüfung als eigenes überholtes Testartefakt entfernt. Original81 bleibt erhalten. Neuer Ledger wurde vor dem ersten Update aus dem tatsächlich installierten81 ausschließlich lesend erfasst; alte historische Lifecycle-Baseline weder ersetzt noch passend gemacht.
- Final weiterhin identisch: vollständiger Master inkl. Capabilities **9436aa2dd614cd1512672b8bed6fa0478c3f188858d9f1fb38613da1e3aa4287**; Geschäft+vollständige Pending-Liste **be1523c98d2fced2b5b446a9191b7d37be4dc98c56a169d2659cc3047fe407af**; Pending **24bb047d5676a9e0e9cae4928e0e9d87e28a5e3c6892ea6d2a93ada956755087**; Beziehungen **eacbcd804eb160d0f022c32200d3004329764f23a5a6190b15560605d7f6299a**; persistierte Sitzungsfelder **78070c6fcd158b6e8e24d651716c5277905bf57bed3fa8386628e5a3514925b1**, RUNNING. Abgeleitete laufende Sekunden/Stunden/Pausenminuten wachsen naturgemäß und sind kein gespeicherter Versionswechsel. 3 Kunden,1 Auftrag,2 Snapshot-Arbeiten,1 Pending-Aktion;0 Fotos/Rechnungen/Angebote/Termine. Queue bewusst erhalten. Keine Rohdatensätze/Base64 gespeichert.
- Protokoll **9**, **117 Tools = 52 Lesen/65 Schreiben**, unveränderter Tool-Katalog-Hash **235f683d99d5170801d1e34c19ad386d9c4acf68b60a6992f72a3e5d2ea14314**. **156 semantische UI-Aktionen = 145 MCP-abgedeckt + 11 bereits ausgewiesene Plattform-/Präsentationsgruppen**; nur geprüfte Quellreviewprovenienz ändert den UI-Katalog-Hash auf **214355fb438d7c71ab0e7d143ca779f1378c28305ce4c101ffe1c52950fefb92**. Native Fenster/OS-Einwilligungen bleiben in den bestehenden Plattformgruppen; kein neuer öffentlicher Vertrag.
- Endgültiger Kandidat **Haus-Garten-1.52.0-82-original-p3-kandidat.apk**, **at.hausgarten.service**, **1.52.0/82**, **34.529.605 Bytes**, SHA-256 **693a3b7efcaad1df0e4bfe10581ab8ab7fcad1b76294f301f44ba5a38b0fa59a**. Ausschließlich tools/sign_original_apk.py; Paket/Version vor/nach Signieren identisch, apksigner-Verifikation und zipalign -c -P16 PASS, keine eigenen .so. Original81/82-Zertifikat identisch **a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8**. Fünf vorhandene HG_ORIGINAL_*-Variablen nur auf Vorhandensein geprüft, Werte nicht ausgegeben/gespeichert; eigentliche Schlüsselnutzung nur im erlaubten Signierwerkzeug. Signiertemp außerhalb Checkout vollständig entfernt, keine Ersatz-/Debug-/QA-Schlüssel.
- Über den bestehenden Google-Drive-Skill in den vorhandenen privaten Codex-Cloud-Buildordner hochgeladen. Metadaten zurückgelesen: exakter Name/MIME/Dateigröße/Elternordner, shared=false, ausschließlich Eigentümerrecht. Vollständige authentifizierte Downloadreferenz mit **34.529.605 Bytes** geliefert, keine Base64 im Chat/Repository. Der Connector exponiert hier kein SHA-Prüfsummenfeld: angegebene SHA ist die tatsächliche lokale Artefaktprüfung; kein zweiter byteweiser Drive-SHA-Vergleich behauptet. Nur die APK übertragen, keine Betriebsdaten, Sicherungen oder Schlüssel. Bestehende Artefakte/Freigaben unverändert; aktueller privater Download in downloads/README.md aktualisiert. Ein APK-Upload ist kein App-/Geschäftsdaten-Syncbeleg.

**Geänderte Bereiche:** bestehende Android-Verbindungsaktivitäten/Sync-Automatik/BusinessDataBridge; mobile.html, gemeinsame Fachtexte/-aktionen, Heute/Arbeitsstart, Kundenakte, Dokument-/Rechnungseditor, Angebotsführung, Suche/Planung, Gartenpirat-Kollisionsschutz und vorhandene generierte Webspiegel; gezielte Regressions-/Charakterisierungs-/UAT-/Updateledger-Tests, Version und zentrale Status-/Auftrags-/Downloadmetadaten. Keine frozen Domainregel, ID-/DB-/Snapshot-/Journalmigration, MCP-Neuveröffentlichung oder zweite operative App. Vollständige Dateiliste/kleine Phasenänderungen in normaler Git-Historie ab d1c5703.

**Weiter offen und nicht umgedeutet:** physische Pixelinstallation nach aktueller Sicherung und ausdrücklicher Freigabe; danach Live-Connector/117 Tools/Fingerprint und echte Kamera-/Datei-/Google-/Kalender-/Wetter-/Gmail-/Share-/Hintergrund-/Kontorechte. Kein produktiver Kunde/Foto/Drive-Journal/Backup geändert oder gelöscht. Keine sieben Original-Altaktionen aufgelöst. Alle P2-Restgrenzen bleiben erhalten, insbesondere große/alte Sicherungen, historische Artefakte/History und fehlende unabhängige Cloudbestätigung. Der historische nicht lokalisierte Lifecycle-verify-FAIL bleibt als solcher dokumentiert; die neue explizite81→82-Ledgerprüfung ist ein eigener nachvollziehbarer Nachweis, keine Behauptung über dessen unbekannte Ursache oder frühere Präferenzen. Erfolgreiche Signierung und Cloud-Updateprüfung ersetzen keinen physischen Datenerhalt-/Restoretest.
