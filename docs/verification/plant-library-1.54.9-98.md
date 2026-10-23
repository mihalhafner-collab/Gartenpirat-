# Pflanzenbibliothek und neue APK – Vorbereitung98, noch keine Auslieferung

Der angeforderte nächste Kandidat ist **1.54.9 / Build98**. Die Version wurde noch
nicht angehoben, keine98-APK gebaut/signiert und kein98-Tag gesetzt. Die bislang
ausgelieferte Original97-APK bleibt unverändert. Die neue Bibliothek enthält
314Profile (181Bestand +133Ergänzungen); aktuell liest die App weiterhin181Profile
mit362Bildern. Eine APK mit vollständiger Bibliothek ist noch nicht abgenommen.

## Tatsächlich umgesetzt und geprüft

- Revision2 samt Quellenbelegen gezielt aus dem Bibliotheksbranch übernommen;
  alle97-Heute-/MCP-/Synckorrekturen erhalten, keine alte Appkopie übernommen.
- Deterministischer Importer `tools/import_plant_library.py`: lesende Vorschau,
  separate Integrationsentscheidungen, stabile IDs und zusätzliche App-Einträge,
  genau2belegte Bilder, Original-/Derivathashes, individuelle Rechte, datierte
  Sichtbelege, dekodierte Maße und JPEG ohne EXIF/XMP. Ungeprüfte Profile bleiben
  HOLD. Der vollständige Schreibversuch wurde mit314offenen Profilen tatsächlich
  abgewiesen; App-Katalog/Originalbilder blieben bytegleich.
- Historischer Builder/Auditor lesen Katalog, Bilder und Vokabular aus dem
  eingefrorenen Gitstand `e499e745832449ce0202d04d9903495248e8b034`.
  Der Wiederholungsbau erhält die Revisions2-Daten bytegleich.
- Oberfläche für optionale Bibliotheksfelder vorbereitet: Synonyme/akzeptierte
  Namen, Arten-/Gartengruppen, fehlende Pflege, USDA-Kennung, zusätzliche Warnungen,
  Quellenklima sowie getrennte EU-/CH-Rechtsangaben. Motiv, Rechte und Änderungen
  sind in Vorschau/Vollbild zugänglich; relevante Ziele mindestens48px.
- 20 Importertests, 21 Bibliothekstests und 2 DOM-Parserprüfungen PASS.
  Alle55Browser-Suiten PASS, darunter8neue synthetische Fälle mit360×640,
  Schrift150/200Prozent, Suche/Zurück, Warnungen, Recht, Credits und Offlinebildern.
  Keine Geschäftsschreib- oder Außenabrufe in diesen neuen Browserfällen.
  P1 unverändert117Tools/156Aktionen/Vertrag7119a0e0; Source-/Workspace-Privacy PASS.

Diese Ergebnisse sind Quell-/Hostprüfungen, keine neue native APK-/Emulatorabnahme.
Die vollständigen Ergebnisse/Hashes stehen in der gleichnamigen JSON-Datei.

## Tatsächliche erneute Bildsichtung

Alle335für Wiederverwendung vorgesehenen Derivate auf42beschrifteten Bögen
angesehen,6Dateien zusätzlich einzeln geöffnet.8Motive für die neue Integration
ausgeschlossen,4wegen unklarer Artzuordnung zurückgestellt; Einzelpfade, Hashes,
Gründe und Prüfumfang in `libraries/plants/integration-legacy-review.json`.
Der historische335erMetadatenbestand wird dadurch nicht nachträglich umgeschrieben.
Anfängliche Übersichtszweifel bei Dryopteris/Calendula nach Detailansicht
ausdrücklich korrigiert. Keine unabhängige botanische Expertenbestätigung behauptet.

Neue externe Originaldownloads/Sichtungen/Freigaben:0. Deshalb bleiben die
314Integrationsentscheidungen offen und die tatsächlich neu integrierten Profile0.
Für die vollständige Integration sind628geeignete lokale Bilder erforderlich;
1140Fotoreferenzen ersetzen diese Dateien und Sichtprüfungen nicht.

## Noch fehlende technische Netzwerkänderung

Der Nutzer hat **„netzwerkfreigabe erteilt“**. Die Zustimmung wird nicht erneut
angefordert. Die tatsächlich aktive Cloudkonfiguration meldet jedoch weiterhin
eine eingeschränkte enforced-Liste ausschließlich mit vorhandenen GeoSphere-
Zielen und Paketmanager-Presets. Direkte Bild-/Quellabrufe scheitern weiterhin.
Eine Chatantwort verändert die technische Hostliste nicht. Die verfügbaren
Cloudwerkzeuge können die Konfiguration lesen, aber nicht selbst bearbeiten.

In der aktiven Cloud-Umgebung diese zusätzlichen erlaubten Hosts eintragen und
die Konfiguration speichern; vorhandene Hosts/Presets und Originalsecrets erhalten:

```text
plants.ces.ncsu.edu
eit-planttoolbox-prod.s3.amazonaws.com
commons.wikimedia.org
upload.wikimedia.org
thumb.wikimedia.org
creativecommons.org
www.rhs.org.uk
```

Maschinenlesbarer konkreter Änderungsumfang:
`libraries/plants/network-access-request.json`. Kein Bearbeiten der lokalen
Policydatei, keine Proxy-/TLS-Umgehung, keine Kontoberechtigung oder kostenpflichtige
API benötigt. Nach tatsächlicher Anwendung aktuelle enforced-Beobachtung und
wirkliche Dateiabrufe prüfen, dann selbstständig fortsetzen.

## Fortsetzung und Sicherungsorte

Aktueller Arbeitsbranch:`codex/function-sync-audit-20261022`.
Implementierungscheckpoint:`ad83d0711bf8db9d00c542456de2831ccba8241b`.
Bibliotheks-Datencommit:`78e0204cd7155799c61c4a88f98150baffeb9ffd`;
Branchkopf samt Integrationsanweisung:`7d499b225ed28f501333227e0ce92a54d4c5d542`.
Den neuesten Arbeitsstand erhalten; kein Reset auf das alte97-Tag.

Vollständiger Arbeitsstand einschließlich dieses Berichts wird unter
[Arbeitsstand-Pflanzenintegration98](https://drive.google.com/drive/folders/1AQbc0HfEUU84zgrgSeDmCpzO3HIivIkx)
gesichert. `00-START.txt`/`01-MANIFEST.json` binden exakten Commit, Original-ZIP und
Prüfsummen. Das ist ein ungebauter Arbeitsstand, kein98-APK-Nachweis. Der tatsächliche
Upload-/Readbackbeleg wird außerhalb des eingefrorenen Quellarchivs geführt.

Nach Netzwerkänderung: frische Bildlinks/Rechte prüfen, Dateien herunterladen,
ungeklärte Motive ersetzen, jede gewählte Datei tatsächlich ansehen, belegte
Integrationsentscheidungen setzen. Vollständigen Import/Spiegel/Suche/Recht/
Rechte/Offline/Entwürfe zweimal prüfen. Erst bei grünen Gates Version98anheben,
sauber gesicherten Code bauen/originalsignieren, native Schrift-/Update-/Datenerhalt-
und Artefaktprüfungen durchführen und mit Quelltag/Drive-Readback bereitstellen.

Keine Pixelinstallation, produktive Tests, Sync-/Warteschlangenänderung oder
Löschung von Betriebsdaten. Frühere Pixelbeobachtung vom97-Auftrag bleibt96 mit
`REMOTE_STATE_REFERENCE_MISSING`/9Pending; in98-Arbeit nicht neu abgefragt.
Emulatorerfolg wäre weiterhin kein Beweis eines produktiven Google-Drive-Abgleichs.
