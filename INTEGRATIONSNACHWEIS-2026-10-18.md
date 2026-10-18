# Zusammenführung der bestehenden Haus- und Garten-App

## Ausgangsvergleich

- Repository ausschließlich `mihalhafner-collab/Gartenpirat-`.
- Lokaler Ausgangspunkt `0426480`, identisch mit dem abgefragten Remote-`main`.
- Neuerer Entwicklungsbranch `codex/v1.46-functional-optimization-20261016`: `49d8cb9`.
- Gemeinsame Basis `8c72bcd`; `main` enthält zusätzlich nur den älteren manuellen QA-Workflow.
- Vor Beginn: sauberer Arbeitsbaum, keine Stashes, genau ein Worktree. Nicht zugängliche Arbeiten anderer Sitzungen sind damit nicht erfasst.
- Merge `40a5beb` erhält beide Historien. Einziger Konflikt: Workflow; die neuere Fassung wurde übernommen. Keine älteren App-Dateien über den Entwicklungsstand kopiert.

## Abnahmekriterien vor weiteren Änderungen

1. Datenintegrität: bestehende Python-/Java-Regressionen und Foto-/Sync-Fehlerfälle mit isolierten synthetischen Daten bestehen; keine Geräte- oder Drive-Schreibzugriffe.
2. Pflanzen: alle 181 Profile und 362 Bilddateien einschließlich Quellen und Lizenzmetadaten im APK bytegleich nachweisen. Bestehender Weg Heute → Gartenpirat → Pflanzenlexikon/Neophyten öffnet dieselben Daten. Deutsche/botanische Suche, Filter, leere Trefferliste, Bilder, Vollbild, Zoom und Zurück im Browser tatsächlich ausführen. Android-Assets ohne Netzwerk und Web-Service-Worker nach Installation offline prüfen.
3. Gemeinsame Datenquelle: vorhandene Katalogdaten erhalten; Ausgaben für Android und Web dürfen nicht unabhängig redigiert werden. Erst nach bestandenem Ausgangsbuild und gesicherter Historie bereinigen.
4. Bezahlübersicht: `b.b.` nur an gespeicherten Arbeitsaufzeichnungen; `bezahlt` unter Heute, nach Neuladen erhalten, rücknehmbar. Keine Änderung von `billed`, Rechnungen, Kunden oder Arbeitszeiten. Speicherfehler darf keinen erfolgreichen Status vortäuschen.
5. Bedienung: bestehende Kamera-/Foto-/Einstellungs-/Piratenabläufe regressionsprüfen. Seitennavigation schützt Entwürfe und räumt den Eingabefokus auf. Reale Android-Tastatur/Kamera gesondert offen ausweisen.
6. Build: operative Application ID `at.hausgarten.service` aus dem Repository erhalten, Kandidaten-`versionCode` größer als 65. Tatsächlich installierte Kennung, Originalzertifikat und installierter Versionscode bleiben ohne autorisierte Geräte-/Original-APK-Evidenz unbestätigt.
7. Update: kein Ersatzschlüssel, keine Installation, Deinstallation oder Datenlöschung. Ohne Originalsignatur und echten Update-/Datenerhaltnachweis keine Release-Freigabe.

## Übernommene Funktionen und Erreichbarkeit

| Erweiterung | Weg in der bestehenden App / tatsächlich erhaltener Umfang |
| --- | --- |
| Pflanzenbibliothek | Heute → Gartenpirat → Pflanzenlexikon; 181 Profile in 11 Kategorien, 362 lokale Bilder, Fachfelder, Quellen und Bildrechte. Kategorien, Lichtfilter, deutsche/botanische Suche und leere Trefferliste geprüft. |
| Neophyten | Heute → Gartenpirat → Neophyten in Österreich; 22 erfasste Neophyten, davon 21 österreichische Vorkommen der EU-Unionsliste. Statusfilter bleiben im selben Katalog. |
| Vollbild und Vergrößerung | Pflanzenporträt → Bild antippen → Vollbild, Bildwechsel, Zoom; Zurück führt ins gleiche Porträt und erhält die Suche. |
| Piratenknopf | Ein verschiebbarer Knopf auf Heute; Position gespeichert, eigene Fotos lokal öffnbar/speicherbar. In anderen Seiten und fremden Dialogen ausgeblendet. Keine Sprach-/Geschäftsdatenaktion durch den Knopf. |
| Kamerakorrekturen | Kamera am Arbeitsformular sowie in Kundenfotos; native URI-/Dateipfadkorrekturen, Berechtigungsdiagnosen, Callback- und temporäre Dateibereinigung aus `ce67213` übernommen. |
| Fotodokumentation | Kunden → Kundenkonto → Dokumentation/Fotos; Chronologie, Filter nach Fotos oder Auftrag, Vorschaubilder, Originalnachladen, Vollbild und Zoom; Profilbilder bleiben getrennt. |
| Fotozuordnung | Im Arbeitsformular erhält das Foto genau die erfolgreich gespeicherte Arbeits-ID. Speicherfehler behalten einen wiederholbaren Fotoauftrag auch über Neuladen, ohne eine zweite Arbeit anzulegen. Kundenkamera übernimmt die Kunden-ID; bei noch nicht gespeichertem Einsatz wird keine Arbeits-ID erraten. Der Timer-Kameraknopf öffnet die Kundendokumentation; eine dort gewünschte Arbeitszuordnung wird ausdrücklich ausgewählt. |
| Einstellungen | Heute → Zahnrad oder Mehr → Einstellungen; Bedienung, lokaler/offener Drive-Stand, Verbindung und App-Version. Vorhandene Schutzmechanismen für Entwürfe und Synchronisierung erhalten. |
| Tastatur/Fokus | Beim tatsächlichen Seitenwechsel wird der alte Eingabefokus freigegeben. Ein Arbeitsentwurf bleibt erhalten. Verweigerte Navigation verwirft weder Fokus noch Inhalt. Android-IME-Verhalten benötigt noch einen Gerätetest. |
| Persönliches `b.b.` | An gespeicherten Arbeiten in Arbeiten und Heute → Zuletzt erfasst. Schalter mit eindeutigem Status; unter Heute erscheint `bezahlt`. Nach Neuladen erhalten und rücknehmbar. Ausschließlich gerätelokale Übersicht in WebView-LocalStorage; keine Drive-Synchronisierung, keine Änderung von `billed`, Rechnungen, Arbeitszeiten oder Kundendaten. |
| Weitere erhaltene Schutzverbesserungen | Fotojournal und Wiederaufnahme, Schutz vor unpassenden Foto-/Kundenrelationen, explizite Auftragskonfliktprüfung und geschütztes Pull-to-refresh aus dem Entwicklungsbranch. |

Der kanonische Katalog bleibt unverändert aus dem Entwicklungsstand erhalten:
`android/app/src/main/assets/plant-catalog.json`, SHA-256
`46bec4dc380c6b938b368422efb236c831656fe30209bf9fc5998709331837fc`.
`tools/sync_plant_assets.py` erzeugt den Offline-JS-Adapter und die Webspiegel aus
dieser Quelle, einschließlich Bilder und gemeinsamer Gartenpirat-Komponenten.
370 generierte Ausgaben werden verglichen. Die Gradle-Vorprüfung bricht bei
Abweichungen ab. Es gibt keinen zweiten gepflegten Katalog oder Lexikoneinstieg.

## Tatsächlich ausgeführte Prüfungen

Maschinenlesbare Ergebnisse einschließlich einzelner Java-/UI-Suiten und
APK-Nachweis: [docs/verification/integration-2026-10-18.json](docs/verification/integration-2026-10-18.json).

| Prüfung | Ergebnis / Aussagegrenze |
| --- | --- |
| Ausgangsmerge | 217 Python-Tests erfolgreich, keine Skips; Source-Privacy-Gate erfolgreich. |
| Finale Python-Regression | `python3 tests/run_quality_suite.py`: 224 Tests erfolgreich, keine Skips; ausschließlich temporäre synthetische Daten. |
| Java-Verträge | `tools/run-followup-java.py`: 10 Suiten, 284 Fälle erfolgreich; einschließlich Foto-/Sync-Metadaten, Relationen, Timer und Android/Python-Konflikthash. Host-JVM, keine native Android-Speicherabnahme. |
| Kamera | 29 Hostfälle mit tatsächlicher Dateisystem-/Symlinklogik und extrahierten Originalmethoden; Android-Grenzen simuliert. Kamera-/Timer-Quellcodeguards ebenfalls erfolgreich. |
| MCP | `tests/mcp_stdio_e2e.py`: 55 Szenarien erfolgreich mit temporärem synthetischem Drive-Speicher. Kein produktiver Pixel-/Drive-Zugriff. |
| JS/UI | 56 JS-Dateien syntaktisch geprüft; 21 ausgewählte Suiten bestanden, nach gezielten Korrekturen und Nachläufen veralteter Testannahmen. Keine physischen Geräteskripte ausgeführt. |
| Pflanzen aus dem APK | 15 Prüfgruppen am aus dem finalen Kandidaten extrahierten Original-HTML und dessen Assets; tatsächlicher Chromium-Browser mit synthetischer Android-Brücke. Alle 362 Bilder bei abgeschaltetem Netzwerk dekodiert. Die lokale Assetroute modelliert gebündelte Android-Assets; sie ist kein installierter Android-WebView. |
| Bezahlübersicht aus dem APK | 4 Prüfgruppen: markieren, Anzeige auf Heute, Neuladen, Rücknahme, Speicherfehler, Entwurf/Fokus und unveränderte Geschäftsdatensätze. Auch 320-Pixel-Ansicht geprüft. |
| Web-Offline | 6 Prüfgruppen über den tatsächlichen vorhandenen HTTP-Server mit synthetischer Datenbank und echtem Service Worker. Nach Installation offline neu geladen; Porträts, Bilder und eigenes Foto weiter benutzbar. Nicht zwischengespeicherte Geschäfts-API-Aufrufe scheitern offline erwartungsgemäß; kein Online-Abgleich vorgetäuscht. |
| Android-Build | `:app:assembleRelease :app:lintRelease` erfolgreich; abschließender Release-Build nach Erhalt des lokalen Signierungsselektors ebenfalls erfolgreich. Lint: 0 Fehler, 9 Warnungen (u. a. vorhandene WebView-, Netzwerk-, SharedPreferences- und Gerätemerkmal-Hinweise). |
| APK-Inhalt | Application ID `at.hausgarten.service`, Version 1.46.1, Code 66; sämtliche 393 App-Assets bytegleich. Katalog, alle 362 Bilder, aufgerufene Komponenten und erforderliche operative Klassen im DEX vorhanden. |
| Datenschutz | Source- und finale APK-Prüfung bestanden; alle Geschäfts-Startkollektionen leer, 0 operative Seedzeilen. Kein produktiver Datenbestand zum Markerabgleich vorhanden (`private_markers_checked=0`); keine Behauptung einer Prüfung gegen aktuelle Kundendaten. |

Beim ersten UI-Lauf waren ältere Menü-/Selektorannahmen und der frühere
Windows-Browserpfad unpassend. Behoben wurden die vierte Menüoption
„Neophyten“, die Beschränkung des Piratenknopfs auf Heute, ein durch die neue
Dokumentationschronologie mehrdeutiger Testselektor und die Browserauswahl beim
Prozessneustart. Eine Java-Fotometadatenprobe benötigte das leere synthetische
`photos`-Array. Alle betroffenen Suiten wurden danach tatsächlich nachgeprüft.
Ein bei `b.b.` gefundener Konflikt mit dem bestehenden Aktionsknopf wurde durch
getrennten Platz für beide Schalter behoben und erneut mit echten Klicks geprüft.

## Fachliche Prüfung: Umfang ausdrücklich begrenzt

Für alle Profile wurden IDs, Struktur, vorhandene Quellen-/Lizenzmetadaten,
lokale Medien, deklarierte zulässige Lizenzklassen und fehlende EXIF-Segmente
automatisiert geprüft. Das ist keine unabhängige Bestätigung sämtlicher
gärtnerischer Angaben oder aller 362 ursprünglichen Lizenzzuordnungen.

Am 18. Oktober zusätzlich anhand öffentlicher Primärquellen abgeglichen:

- Die 21 im Katalog erfassten österreichischen Vorkommen stimmen mit der
  [amtlichen markierten Unionsliste](https://www.neobiota-austria.at/ias-unionsliste/neophyten) überein.
- [Sommerflieder](https://www.neobiota-austria.at/neobiota-national/neobiota-auswahl/sommerflieder): Ausbreitung, Verdrängung und Samenvermeidung; außerhalb dieser Unionsliste.
- [Lavendel](https://plants.ces.ncsu.edu/plants/lavandula-angustifolia/): Licht, Drainage und als gering deklarierte Giftigkeit.
- [Eibe](https://plants.ces.ncsu.edu/plants/taxus-baccata/): hohe Giftigkeit und Standortangaben.
- [Riesen-Bärenklau](https://www.neobiota-austria.at/heracleum-mantegazzianum): Größenordnung, österreichischer Status und phototoxischer Pflanzensaft.

Die übrigen Fachdetails sind hier nicht vollständig neu recherchiert worden;
die vorhandenen Quellen-/Prüfdaten bleiben erhalten. Insbesondere werden aus
fehlenden Fachwerten keine erfundenen Werte oder Ungiftigkeitsannahmen erzeugt.

## Bereinigung und Wiederherstellbarkeit

Erst nach erfolgreichem zusammengeführtem Ausgangsbuild (391 identische Assets)
und Merge-Commit `40a5beb` wurden folgende Buildteile geändert:

- `.github/workflows/qa-apk.yml`: früheren `.qa`-APK-/QA-Secret-Build durch einen
  manuell startbaren unsignierten Kandidaten der operativen App ersetzt. Der
  vorhandene Workflowpfad bleibt für GitHub sichtbar. Hier wurde kein Actions-Lauf gestartet.
- `android/app/build.gradle`: `.qa`-Suffix und QA-Signing-Konfiguration entfernt,
  Debugvarianten deaktiviert, Instrumentierung auf Release ausgerichtet. Der
  bisherige lokale Original-Schlüsselpfad und Alias bleiben als Selektor für
  `tests/sign-local-update.ps1` erhalten. Release wird damit nicht signiert und
  kein Schlüssel neu erzeugt. Das Desktop-Skript vergleicht den Schlüssel vor
  Signierung zwingend mit einer vorhandenen Original-APK.
- Doppelte manuelle Web-Katalogausgaben im Neophyten-Generator durch die gemeinsame
  Erzeugung ersetzt. Katalog- und Bilddateien bleiben vollständig erhalten.

Keine Tests nach Dateinamen gelöscht. `qa_signing.py` und seine 37 synthetischen
Sicherheitsregressionen bleiben als historische Diagnose erhalten; sie gehören
nicht zum aktiven Buildweg. Die vier im Entwicklungsbranch verlorenen
Source-Privacy-Regressionen und zugehörigen Exportprüfungen aus `main` wurden
wiederhergestellt, ebenso die Freigabesperren der fünf betroffenen Geräteskripte.
Auch die native Geräteabnahme verlangt nun ausdrücklich eine Bestätigung.

Keine weitere separat gepflegte App-Implementierung wurde im zugänglichen
Repository gefunden oder pauschal entfernt. Alte Cloud-Übergabedokumente
`CLOUD-START.txt` und `SOURCE-MANIFEST.json`, die bereits der integrierte Branch
entfernt hatte, sind unter `0426480` vollständig historisch wiederherstellbar.
Eine weitergehende Altcodebereinigung bleibt bis zu den offenen Geräte- und
Update-Nachweisen zurückgestellt.

## Kandidat, Originalsignatur und noch offene Abnahme

- Artefakt: `android/app/build/outputs/apk/release/app-release-unsigned.apk`.
- SHA-256: `6ffc9cbf2a3a2963c6bc254a07197fbeb7fb8bfa57a5746c01194b23bfce14d1`.
- **Unsigniert und nicht zur Installation freigegeben.** Code 66 ist höher als
  der verglichene Entwicklungsstand 65; ein Vergleich mit dem tatsächlich auf
  dem Pixel installierten Versionscode fehlt.
- Keine Original-APK und kein Originalschlüssel in dieser Cloud-Umgebung
  verfügbar. Nach Nutzerangabe wurde der ursprüngliche Vorgang von Codex Desktop
  durchgeführt; dessen lokales Dateisystem ist hier nicht eingebunden.
- Historischer Hinweis gefunden in `DESIGN-v1.32.md`: Paket
  `at.hausgarten.service`, Version 1.32.0 / Code 49, berichtetes Zertifikat
  `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`.
  Dieser frühere Bericht ist hier nicht reproduziert und bestätigt weder die
  aktuelle Pixel-Installation noch einen verfügbaren privaten Schlüssel.
- `tests/verify_app_candidate.py --baseline-apk ...` kann mit einer echten
  Original-APK beide Signaturen, gleiche Paketkennung und höheren Code prüfen.
  Ohne Baseline wird ausdrücklich kein Originalsignaturnachweis ausgegeben;
  auch eine passende Signatur allein erteilt keine Geräte-/Updatefreigabe.
- **Nicht ausgeführt:** physischer Pixel-/Android-WebView-Test, reale Kamera,
  Galerie, Android-Tastatur, Pinch-Gesten auf dem Gerät, echter Drive-Provider,
  Fresh-Install auf einem isolierten Gerät, In-place-Update und Datenerhalt,
  Vergleich mit installierter Signatur sowie produktive Sicherung.
- Keine App installiert/deinstalliert, keine App-Daten gelöscht, kein
  produktiver Pixel-/Drive-Zugriff und keine Remote-Branches überschrieben oder gepusht.

Vor einer Desktop-Fortsetzung dieselbe Repository-Historie mit dortigen lokalen
Änderungen vergleichen und diese sichern; den vorhandenen Original-APK-/Schlüssel-
Nachweis dort ermitteln. Erst nach Originalsignaturprüfung, isolierter
Geräteabnahme und nachgewiesenem Datenerhalt darf eine gesicherte operative
Pixel-Installation ausdrücklich zur Freigabe vorgelegt werden.
