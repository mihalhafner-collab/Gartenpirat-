# Pflanzenbibliothek98 – originalsignierte APK fertiggestellt

**[Haus & Garten 1.54.9 / Build 98](https://drive.google.com/file/d/1SXrVaoq_rsGIKo57XeDu0CQDJXB5bepl/view)** mit 314 Pflanzenprofilen und 628
Offlinebildern. 181 bestehende IDs erhalten, 133 Profile ergänzt. Bestehende
Heute-/MCP-/Syncfunktionen erhalten; kein Reset und keine Pixelinstallation.

Originalquelle: `dbe9ed0b4d8a0a60aee4c4c0a578c3fe4f40ae4c`, unveränderliches Tag `source/v1.54.9-build98`.
App-Fingerprint: `fd0979de4da7c090beef01ec6e584ddfa5a999d79f138c870d40956e5aa4c1ae`.
APK-SHA256: `ffa7525b85ac47a0ec78cdd352cb5fb327293bd1138a9cd9784fa23f3f0c9997`.
Original-ZIP-SHA256: `cfaa05c384d2e40ada1de495add2500a1bc590e3fb38879851992a62432a196f`.
[Vollständige Originalquellen und verlustfreie TXT-Ableitungen](https://drive.google.com/drive/folders/1iOo71s1cmHcVFtBfa3rVOc2bAf-68zbW).
Das Original-ZIP liegt verlustfrei XZ-komprimiert vor; vollständiger lokaler
Roundtrip byteidentisch. Review-TXT-ZIP enthält alle Exporttexte und Manifest.
Großes Archiv/APK: vollständiger authentifizierter Stream und Größenvergleich;
kleine Belege/Review-ZIP: tatsächlicher SHA256-Bytevergleich. Kein behaupteter
Remote-SHA256 aus reinen Größen. [Tatsächlicher QA-Nachweis](https://drive.google.com/file/d/1K2Uf6J_xT7bW9ozezC3Fwpp3Cd1M4NyQ/view).

420 Python-Tests (37 Tests zur Erzeugung fremder QA-Signaturen ausdrücklich
ausgeschlossen), 50 JVM-Suiten, 55 Browser-Suiten und 55 MCP-stdio-Szenarien PASS.
P1 bleibt 117 Tools / 156 UI-Aktionen. Build/Lint, Asset-/Bootstrap-/Privacyprüfung,
Originalzertifikat, 16-KiB-Ausrichtung und eingebettete Provenienz PASS.
Native Prüfung: 314 Profile / 628 Bitmapdekodierungen, Katalogabfragen samt Synonymen
und EU-/CH-Trennung, echte WebView-Bedienung, leerer isolierter Android-Store,
allgemeine Navigation und OS-Schriftgrößen 1,0 / 1,5 / 2,0 PASS. 9 Bildschirmbelege tatsächlich
angesehen; Schrift/Anzeige restauriert. Installierte APK bytegleich.
Ein einmaliger Navigationsabbruch im Software-AVD bleibt samt bestandenem
Wiederholungslauf derselben APK ohne Code-/Geschäftsdatenänderung dokumentiert.

Updatenachweis mit eigenem originalsigniertem 97-Testausgang `0430eaa`:
97 → vorläufige 98-Kandidaten → endgültiger Build 98 ohne Datenreset/Downgrade. Vollständiger ursprünglicher
97-Ledger nach allen Prüfungen unverändert: Stammdaten, Pending, Beziehungen und
stabile Timerfelder. Dies ist kein direkter Updatenachweis mit der früher
ausgelieferten 97-Drive-APK und keine physische Pixel-Abnahme. App- und getrennte
QA-Quellcommits stehen im JSON; ältere fehlerhafte Testannahmen bleiben dort
nachvollziehbar. Keine neue produktive Google-Freigabe oder Syncaktion.

304 aktuelle botanische Seiten / 4080 Feldgruppen und individuelle Fotorechte geprüft.
Alle 628 gewählten Motive gesichtet; 25 ungewisse/ungeeignete Referenzen gesperrt.
Datierte Rechtsbelege, künftige EU-Wirksamkeit und CH-Anhänge getrennt erhalten.
Keine unabhängige botanische Expertenbestimmung behauptet.

## Historische Zwischenstände

# Pflanzenbibliothek98 – Integration abgeschlossen, APK-Abnahme läuft

Aktueller Stand der Fortsetzung vom23.10.2026: **314 Profile /628 Offlinebilder**
im kanonischen Katalog und den regulär generierten Webspiegeln. Alle181Bestands-IDs
erhalten,133neue Profile. Vollständiger Import und erneuter Check liefern denselben
Katalog-SHA256 `72f0a25d49740e1093a37bc7c02c059350e0f973e257298fda3965ab6570d710`.
Keine Pixelinstallation, Geschäfts-/Sync-/Queueänderung oder Zurücksetzung.

304aktuelle botanische Quellseiten /4080Feldgruppen abgeglichen; Rosen-Drainage
im tatsächlich gelesenen RHS-Leitfadentext bestätigt. Datierte amtliche Rechtsbelege
vom selben Tag erhalten; künftige EU-Wirksamkeit und Schweizer Anhänge getrennt.
293Bestandsderivate und335neue Originaldownloads gewählt, jedes App-Derivat tatsächlich
auf beschrifteten Bildbögen bzw. Ersatz-/Detailansichten angesehen.25 Ausschlüsse
werden einzeln und ohne Umdeutung früherer Sichtbefunde geführt. Verbindlich sind
`integration-photo-exclusions.json`,314Einzelberichte und das Integrationsmanifest.
Keine unabhängige botanische Expertenbestimmung behauptet.

25synthetische Importerprüfungen,21historische Bibliotheksprüfungen und2Parserfälle
PASS. Sämtliche628Bilder im tatsächlichen Chromium-Offlinepfad dekodiert; Suche,
Neophytenfilter, Vollbild und Arbeitsentwurfsschutz in der Pflanzen-Browsersuite
PASS. Vollständige Regressionen, native neue Bitmap-/WebView-Prüfungen und
Original98-APK-Abnahme laufen noch; Version bis zu den Implementierungsgates97.

Vorhandener CLI-Zugang über Git-Credential-Helper bestätigt, kein neuer Schlüssel
oder Login. Letzter gesicherter Zwischenstand0430eaa. Rohdownload des97-Drive-APKs
als vollständiger Stream geliefert, jedoch36MB>32MiB-Transfergrenze; direkter
Byteabruf durch die Cloudrichtlinie abgewiesen und nicht umgangen. Separater
originalsignierter97Testausgang aus sauber gesichertem0430eaa/aktueller Vorbereitung
gebaut (APK-SHA `cfe56ad4ee7d651e878562549ad58a5f82adf175cd778b8151420615db251f9e`).
Er ersetzt nicht den unveränderten ausgelieferten97Kandidaten/Tag. Native
Updateabnahme wird diesen transparent bezeichneten synthetischen Ausgang verwenden.

## Historischer Vorbereitungsbericht vor der Netzwerkänderung

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
