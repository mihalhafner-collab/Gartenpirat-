# Arbeitsanweisung: neue Pflanzenbibliothek in Gartenpirat einbinden

Stand: 23.10.2026. Vorbereiteter Folgeauftrag für Codex Cloud.
Diese Anweisung beschreibt die spätere Umsetzung. Ihre Erstellung führt keine
App-Integration, Signierung oder Installation aus. Umsetzung erst bei einem
entsprechenden Folgeauftrag; danach die freigegebenen Schritte selbstständig
abarbeiten und nur tatsächlich fehlende Zugänge oder geschützte Geräteaktionen
zurückmelden.

## Ziel und verbindliche Ausgangsdaten

Integriere die neue Bibliothek in das vorhandene Gartenpirat-Pflanzenlexikon und
den bestehenden Neophytenbereich. Verwende ausschließlich das bestehende
Repository [mihalhafner-collab/Gartenpirat-](https://github.com/mihalhafner-collab/Gartenpirat-)
und Codex Cloud. Erhalte Navigation, vorhandene Pflanzen-IDs, persönliche Fotos,
betriebliche Daten und Pixel/Drive-Verträge.

| Grundlage | Verbindlicher Stand |
| --- | --- |
| Bibliotheksbranch | `codex/pflanzenbibliothek-20261023` |
| Geprüfter Bibliotheks-/Paketquellcommit | `78e0204cd7155799c61c4a88f98150baffeb9ffd` |
| Sicherungscommit einschließlich Paketnachweis | `59b22332b5e8cc4288f919370ea0001c081b4526` |
| Redaktionelle Eingabe | [plant-library.json](plant-library.json) |
| Auswahl | 314 Profile, davon 181 Bestand und 133 Ergänzungen |
| Nicht zur Auswahl gehörend | 80 Recherchekandidaten und drei Duplikate in [review-queue.json](review-queue.json) |
| Fotobestand | 1140 externe Referenzen; 335 bestätigte Bestandsderivate im Daten-ZIP |
| Aktueller Fotostatus | Neue Originaldownloads und persönliche Sichtprüfungen: jeweils 0; alle ausgewählten Profile auf HOLD |
| Daten-ZIP | `Pflanzenbibliothek-2026-10-23-r2.zip`, 30.626.529 Bytes |
| ZIP-SHA-256 | `412a67476b1188c6987821c2c34fb3fc5ef02147d6de4ef36cf0746ecb87edb7` |

GitHub ist die dauerhafte Quelle. Falls das ZIP in einer späteren Umgebung fehlt,
die Bibliotheksdaten aus dem angegebenen Commit und vorhandene Bilder aus dem
Repository lesen; eine fehlende Datei nicht als geprüft oder heruntergeladen
kennzeichnen. Das ZIP ist ein Bibliotheks-Datenpaket und kein App-Quellarchiv.

Vor Beginn [QA-BERICHT.md](QA-BERICHT.md), [entry-review.csv](entry-review.csv),
[photo-credits.csv](photo-credits.csv), [pending-image-review.json](pending-image-review.json)
und die jeweiligen Belege unter [evidence/](evidence/) lesen.

## 1. Aktuellen App-Stand erhalten

1. Aktuelle `AGENTS.md` von GitHub-main sowie `CODEX-AUFTRAG-GARTENPIRAT.md`,
   `CODEX-STATUS.md` und `AKTUELLER-STAND.json` lesen. Frischen main, vorhandene
   Arbeitsbranches und den tatsächlich ausgelieferten Stand vergleichen.
2. Bibliotheksdateien aus dem belegten Commit gezielt in den aktuellen,
   passenden Arbeitsstand übernehmen. Keine alte App-Gesamtkopie aus dem
   Bibliotheksbranch über neuere Funktionen legen. Kein Reset, Force-Push oder
   ungeprüftes Zusammenführen fremder Arbeitsbranches.
3. Versionierten Ausgangsstand mit Kataloghash, Pflanzen-IDs und Bildhashes
   erfassen. Unfertige eigene Änderungen nach den vorhandenen Privacy-Gates
   auf dem passenden bestehenden Branch sichern und den Remote-Commit zurücklesen.
4. Bestehende operative Paketkennung, Datenformate und Geschäfts-/Sync-Funktionen
   erhalten. Keine neue App, zweite betriebliche Datenbank oder Laptop-Abhängigkeit.

Abnahmekriterien vor Änderungen: stabile vorhandene IDs; ausschließlich
nachgewiesen freigegebene Ergänzungen; zwei geeignete Offlinefotos je übernommenem
Profil; vollständige Bild-Credits; passende Quellen und Warnungen; Suche und
Vollbild funktionieren; keinerlei betriebliche Schreibaktionen.

## 2. Fotoprüfung vor der Übernahme abschließen

Die 1140 Referenzen sind ein Kandidatenbestand, keine 1140 geprüften Bilddateien.
Für die erste Integration genau zwei geeignete Aufnahmen pro Profil auswählen;
weitere Referenzen bleiben redaktionell erhalten. Bei 314 vollständig
freigegebenen Profilen sind das 628 in der App referenzierte Bilder.

1. Cloud-Netzrichtlinie aktuell prüfen. Für Bilddownloads sind insbesondere
   `eit-planttoolbox-prod.s3.amazonaws.com`, `upload.wikimedia.org`,
   `thumb.wikimedia.org` und `commons.wikimedia.org` relevant. Direktabrufe der
   Quellseiten können zusätzliche ausdrücklich erlaubte Hosts benötigen.
   Gesperrte Ziele über den vorgesehenen Konfigurationsweg freigeben lassen;
   Proxy-/TLS-Schutz erhalten. Unabhängige Adapterarbeit darf weiterlaufen.
2. Frische Downloadlinks auf der jeweiligen `sourcePage` auflösen. Keine
   abgelaufenen Signaturen oder bloßen `originalLocator`-Referenzen in die App
   übernehmen. Lizenz, Version, Länderfassung und Urheber erneut bestätigen.
3. Jede gewählte Datei tatsächlich öffnen: Lebendpflanze, Art-/Gruppenbezug,
   erkennbare Bestimmungsmerkmale und gegebenenfalls Sortenbezug prüfen. Zwei
   unterschiedliche sinnvolle Motive bevorzugen, etwa Gesamtwuchs und Detail.
   Zweifelhaftes Motiv ersetzen; keine KI-generierten Bestimmungsfotos verwenden.
4. SHA-256, Dateityp, Abmessungen, Quelle, Lizenz und tatsächlichen Sichtbefund
   dokumentieren. Original und daraus erzeugtes App-Derivat getrennt hashen;
   Ausschnitt, Verkleinerung, Formatwechsel und Metadatenentfernung angeben.
   App-Derivate ohne EXIF/GPS/XMP bereitstellen.
5. Die 335 bestätigten Bestandsderivate dürfen nach erneuter Eignungsprüfung
   wiederverwendet werden. Deren Kontaktbogensichtung ist keine unabhängige
   Artbestimmung. Die 27 ausgeschlossenen Altderivate nicht erneut übernehmen.
   Zusätzlich [photo-exclusions.json](evidence/photo-exclusions.json) beachten,
   insbesondere die ausgeschlossenen Ludwigia-hexapetala-/Zweifelsmotive.
6. Titel/Motiv, Urheber, Quelle, exakte Lizenz und Lizenzlink am Vorschaubild und
   im Vollbild zugänglich halten; Änderungen auch im Vollbild verfügbar machen.
   CC-BY-SA-Bedingungen für das bearbeitete Bild beachten. NC/ND, unklare Rechte
   und bloße pauschale Public-Domain-Angaben nicht neu übernehmen.

Ein Profil ohne zwei tatsächlich geeignete, rechtlich belegte Dateien bleibt
außerhalb der neuen Übernahme. Bereits veröffentlichte fehlerhafte Motive gezielt
ersetzen; einen ungelösten Fehler nicht durch Erhalt des alten Bildes als
abgenommen behandeln. Blockierte Restarbeiten mit betroffenen IDs dokumentieren.

## 3. Historische Bibliotheksprüfung und Integration trennen

**Vor dem ersten Schreiben des App-Katalogs beachten:** `build_library.py` liest
gegenwärtig den App-Katalog als Altbestand. `audit_library.py` vergleicht den
aktuellen App-Katalog und dessen Bilddateien mit der ursprünglichen Baseline.
Nach einer Integration würden ungeänderte Skripte daher neue Profile als
Altbestand behandeln bzw. beabsichtigte Bild-/Katalogänderungen beanstanden.

Vor einer weiteren Bibliothekserzeugung diese Baseline-Leser auf den eingefrorenen,
hashgeprüften Ausgangsstand aus
`e499e745832449ce0202d04d9903495248e8b034` umstellen. Historische Bildbytes ebenfalls
aus dieser Baseline lesen, etwa aus den vorhandenen Git-Objekten. Die in
`plant-library.json` vermerkten Baselinehashes unverändert gegen diesen Stand
prüfen. Keine Hashes oder historischen Sichtbefunde passend zum neuen App-Stand
umschreiben. Den veröffentlichten Revision-2-Stand nachvollziehbar erhalten.

Ein **neu anzulegendes Integrationsmanifest** soll je Profil Quellrevision,
Entscheidung, fachlichen Prüfumfang und die zwei ausgewählten Dateien samt
Original-/Derivathash, Rechten, Sichtnachweis und Bearbeitungen enthalten.
Bei Freigabegründen auf konkrete Belege verweisen. Historisches HOLD oder
`NOT_PERFORMED` nicht pauschal in APPROVED umbenennen: Der bisherige Auditor
verwirft solche unbelegten Aussagen ausdrücklich.

Ein eigener Integrationstest muss den aktuellen App-Katalog gegen dieses Manifest
prüfen. Historische Bibliothekskonsistenz und aktuelle Implementierungsfreigabe
sind zwei verschiedene Nachweise. Das Manifest ist eine öffentliche
Build-/Redaktionsdatei, keine zweite operative Pflanzen- oder Geschäftsdatenbank.

## 4. Datenadapter für den bestehenden App-Vertrag erstellen

Einziger gepflegter App-Katalog bleibt
`android/app/src/main/assets/plant-catalog.json`. Die redaktionelle Datei hat ein
anderes Schema und darf diesen Katalog nicht direkt ersetzen.

Einen nachvollziehbaren, deterministischen Import mit lesendem Vorschau-/Diffmodus
erstellen. Nur im Integrationsmanifest freigegebene Profile verarbeiten. Nach
`id`/`legacyId` aktualisieren, neue IDs einmalig ergänzen und vorhandene zusätzliche
App-Einträge erhalten. Bei unveränderter 181er-Basis ergeben alle 133 freigegebenen
Ergänzungen insgesamt 314 Profile; nicht 314 an 181 anhängen. Duplikate auch anhand
belegter akzeptierter Namen/Synonyme prüfen, ohne alte IDs umzubenennen.

| Redaktioneller Inhalt | Übernahme in den App-Vertrag |
| --- | --- |
| `id`, `name`, `scientificName`, `category` | Bestehende gleichnamige Felder erhalten/gezielt aktualisieren |
| `traits.family` | `family` als belegte Familienangabe; fehlende Werte kenntlich machen |
| `traits.light` | `light`, mit konsistenten deutschen Filterwerten |
| `traits.soilTexture`, `traits.soilReaction` | `soil`, `ph` |
| `traits.soilDrainage` | Bestehendes `water` unter der bereits verwendeten Anzeige „Bodenfeuchte / Drainage“; keine gemessenen Gießmengen daraus ableiten |
| `traits.bloomSeason`, `traits.flowerColors` | `bloom`, `colors`; Quellenregion erhalten, keine erfundenen DACH-Blühmonate |
| `traits.lifeCycle`, `traits.foliage`, `traits.growthRate` | `life`, `foliage`, `growth` |
| `traits.propagation` | `propagation`, nur im fachlich zulässigen Anbauumfang |
| `traits.hardinessUSDA` | `hardiness` mit sichtbarer USDA-Kennung und Quellenklima; RHS-H-Klassen getrennt behandeln |
| `traits.dimensionsSource` | `dimensions`, Einheit und Richtwertcharakter erhalten; keine ungeprüfte Umrechnung |
| `traits.originSource` | Botanisches `origin`; das redaktionelle Top-Level-`origin` bezeichnet lediglich Bestand/Erweiterung |
| `safety` | `poison`/`contact` nur mit passender belegter Bedeutung; zusätzliche RHS-Warntexte und historische Warnungen sichtbar bewahren |
| `sources[].checkedAt` | App-Quellen mit `url`, nachvollziehbarem `title` und `checked`; Feldbelege/Prüfstand erhalten |
| Gewählte Bildreferenz | `images[]` mit lokalem `plant-images/...`-Pfad, `caption`, `author`, `license`, `licenseUrl`, `source`, `changes` |

Lizenzkennungen wie `CC-BY-SA-3.0-AT` einschließlich Länderfassung erhalten;
bei Bedarf zusätzlich den vorhandenen Anzeigenamen `CC BY-SA 3.0 AT` erzeugen.
`sourcePage` wird zur Bildquelle `source`. Remote-URL, signierter Link oder
Prüfstatus ist kein lokaler Bildpfad. Quell- und Bildlizenzen getrennt führen.

`care` und `planting` werden nicht durch die neuen Merkmalslisten geliefert.
Nur belegte vorhandene Texte erhalten bzw. quellenbelegte eigene Hinweise
ergänzen. Fehlende Pflegeinformationen verständlich ausweisen. Vorhandene Tests,
die für jede Art gefüllte Pflegetexte erzwingen, gezielt auf diesen ehrlichen
Vertrag umstellen; keine Fülltexte schreiben, um Tests zu erfüllen.

Synonyme und akzeptierte Namen für Anzeige/Suche ergänzen, insbesondere
Pennisetum/Cenchrus alopecuroides und Coreopsis/Anacis verticillata. Gattung,
Gartengruppe und Brombeer-Aggregat sichtbar unterscheiden. Unbekannte Giftigkeit
nicht als „ungiftig“ anzeigen; historische Warnungen nicht stillschweigend löschen.

## 5. Neophyten und Recht korrekt anzeigen

- [legal-review.json](evidence/legal-review.json) und die amtlichen Quellen zum
  Integrationszeitpunkt erneut prüfen. Land, Wirksamkeitsdatum und Belegdatum
  zugänglich machen; Listenmitgliedschaft und bereits geltende Beschränkung
  getrennt behandeln.
- Celastrus orbiculatus: EU-Wirksamkeit laut geprüftem Stand **02.08.2027**;
  Schweizer Umgangsverbot unabhängig davon. Ein pauschales aktuelles EU-Verbot
  vor diesem Termin darf nicht aus dem historischen Katalog übernommen werden.
- Schweiz: Anhang 2.1 (Umgangsverbot mit gesetzlichen Ausnahmen) und 2.2
  (Inverkehrbringen/Einfuhr) unterscheiden. Bei 2.2 keine allgemeine Pflicht
  zur Entfernung bestehender Pflanzen behaupten. Die zusätzlich belegten
  Schweizer Einschränkungen bei Gartenpflanzen ebenfalls anzeigen.
- Den bestehenden Österreichbereich fachlich erhalten. Schweizer Listung nicht
  als Nachweis österreichischer Invasivität oder einer EU-Listung verwenden.
  Regionale Regeln und örtlich abzustimmende Maßnahmen kenntlich machen.
- Neophyten-Referenzprofile ohne Anbauempfehlung übernehmen. Fehlende Pflege- oder
  Bekämpfungsangaben nicht erfinden. RHS-Warnungen einschließlich Kontakt- und
  Haustierhinweisen bewahren; Kategorien Kräuter/Obst/Gemüse sind keine Verzehrfreigabe.

## 6. App einbinden und Offline-Spiegel erzeugen

Freigegebene Daten und Dateien zuerst im kanonischen Android-Assetbestand
bereitstellen. Erforderliche Anzeige-/Suchanpassungen im bestehenden
`android/app/src/main/assets/gartenpirat.js` und vorhandenen Ansichten umsetzen.
Neue optionale Metadaten rückwärtskompatibel ergänzen; falls eine tatsächliche
Vertragsänderung nötig ist, Schema und alle Leser gemeinsam versionieren.
Keinen zweiten Laufzeitimport der redaktionellen JSON-Datei einführen.

Danach die vorhandenen Generatoren verwenden:

```sh
python3 tools/sync_plant_assets.py --write
python3 tools/sync_plant_assets.py
```

`plant-catalog.js`, die `static/`-Katalogspiegel, Bildspiegel und die
Service-Worker-Assetliste ausschließlich darüber erzeugen. Der Generator
aktualisiert den inhaltsabhängigen Pflanzencache. Er löscht keine Dateien:
gemeldete nicht mehr referenzierte Web-Bilder einzeln gegen den neuen Katalog
und ihre öffentlichen Hashes prüfen und nur diese ersetzten Spiegel bereinigen.
Keine persönliche Fotoablage oder betrieblichen Medien anfassen.

## 7. Abnahme und Abschluss

Die vollständige Integration prüfen, anschließend unmittelbar vor Abschluss
einen zweiten Gesamtdurchgang über sämtliche übernommenen Profile, Bilder,
Warnungen, Rechte und Spiegel ausführen. Ergebnisse mit finalem Quellstand und
Hash-/Zählbelegen festhalten. Ein erfolgreicher JSON-Import ist keine UI-Abnahme.

| Eingabe / Ablauf | Erwartetes Ergebnis |
| --- | --- |
| Import zweimal gegen denselben Stand | Identischer Output; keine doppelten oder umbenannten IDs |
| Nicht freigegebener oder zurückgestellter Kandidat | Keine neue Übernahme; konkrete offene Ursache im Bericht |
| Gesperrtes Bild, falscher Hash, unpassende Lizenz oder fehlender Sichtbeleg | Übernahmefehler; kein stiller Ersatz durch das alte ungeeignete Motiv |
| Jeder übernommene Eintrag | Zwei lesbare Offlinebilder, vollständige Credits, passende Quellen und Warnungen |
| Suche nach deutschem Namen, bisherigem und akzeptiertem botanischem Namen | Derselbe korrekte Eintrag; Filter kombinierbar, leere Trefferliste verständlich |
| Lexikon → Porträt → Vollbild → Zurück | Bildwechsel/Zoom/Credits funktionieren; Rückkehr zum selben Kontext |
| Neophytenfilter und datierte Rechtsanzeige | Richtige Region; künftige EU-Wirksamkeit getrennt von geltenden Regeln |
| Flugmodus und App-Neustart | Katalog und beide Bilder vollständig nutzbar, keine externen Bildabrufe erforderlich |
| Schmale Ansicht und große Schrift | Lesbare Fakten, bedienbare Bildsteuerung und erreichbare Quellen/Credits |
| Gartenpirat öffnen bei vorhandenen Arbeitsentwürfen | Keine Betriebsdatenänderung, kein Sync-/Schreibaufruf, Entwurf erhalten |

Vorhandene Prüfungen weiterverwenden:

```sh
python3 -m unittest discover -s libraries/plants -p 'test_*.py'
node --test libraries/plants/test_extract_profile_metadata.mjs
python3 libraries/plants/audit_library.py --report final
python3 tools/sync_plant_assets.py
node tests/gartenpirat-plants.test.cjs
```

Der Bibliotheksauditor darf nach Integration erst mit dem in Schritt 3
festgelegten historischen Baselinezugriff verwendet werden. Hinzu kommt die
neu zu implementierende Prüfung des aktuellen Integrationsmanifests. Die
Browserumgebung gemäß `tests/browser.cjs` in Codex Cloud konfigurieren; sie
ersetzt keine tatsächliche Android-WebView-/APK-Prüfung.

Bestehende Tests mit festem Quellenhostkatalog, exakt 21 Unionslistenfällen oder
Pflegepflicht für alle Neophyten fachlich aktualisieren. Neu verwendete amtliche
Quellen gezielt zulassen; Quellen-/Rechteprüfung, Mindestbildzahl, Datenerhalt und
Offlinegarantien nicht pauschal lockern. Statische, automatisierte, tatsächliche
UI-/APK- und nicht ausführbare Geräteprüfungen getrennt ausweisen.

Vor einer später beauftragten APK-Bereitstellung außerdem passende Android-/
Regressionsprüfungen und alle Source-/Workspace-/Artifact-Privacy-Gates bestehen.
Die zuletzt beobachteten 21 auftragsfremden Workspace-Befunde sind keine Ausnahme:
Bei fortbestehendem FAIL keine Releaseabnahme oder vollständigen Quellcodeexport
behaupten. Kein Skip-Schalter und keine fremden Dateien pauschal bereinigen.

Geprüfte eigene Änderungen committen, im bestehenden privaten Repository pushen
und exakten Commit zurücklesen; `tools/source_preservation.py` und den regulären
Quellarchivexport gemäß `AGENTS.md` nutzen. `CODEX-STATUS.md` mit Anzahl tatsächlich
integrierter Profile/Bilder, Restbestand und Prüfergebnissen aktualisieren.
Anschließend Repository-/Commitlink und die tatsächlich erzeugten Artefakte nennen.
Den vorhandenen Bibliotheks-Datenpaketnachweis nicht als Nachweis einer neuen APK
verwenden. Originalsignierung und Versionsanhebung erst im beauftragten
Releaseumfang; Pixel-Installation weiterhin erst nach Sicherung und ausdrücklicher
Gerätefreigabe. Die Erstellung dieser Anweisung verändert keine App-Version.
