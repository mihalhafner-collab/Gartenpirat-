# Gartenpirat-Pflanzenlexikon – Stand 1.46

Prüfstand: 16. Oktober 2026

## Ergebnis und Zählung

- Ausgangsbestand: 160 Pflanzenprofile und 320 lokale Bilder.
- Neuer Bestand: 181 Pflanzenprofile und 362 lokale Bilder.
- Neu angelegt: 21 fachlich belegte Profile der in Österreich nachgewiesenen Pflanzenarten der EU-Unionsliste.
- Ergänzt: 42 lokal optimierte Bilder, genau zwei je neuem Profil (zusammen 4.234.523 Byte).
- Fachlich nachklassifiziert: 1 vorhandenes Profil, Sommerflieder (`Buddleja davidii`), als etablierter invasiver Neophyt außerhalb der EU-Unionsliste.
- Dubletten: 0 doppelte IDs und 0 doppelte wissenschaftliche Namen im Endbestand.
- Neue Kategorien: keine zweite Pflanzendatenbank und keine künstliche Pflanzenkategorie. Der vorhandene Katalog erhielt einen eigenen Neophytenbereich mit Statusfiltern.

Die 21 neuen Arten sind:

1. Götterbaum (`Ailanthus altissima`)
2. Gewöhnliche Seidenpflanze (`Asclepias syriaca`)
3. Papiermaulbeerbaum (`Broussonetia papyrifera`)
4. Rundblättriger Baumwürger (`Celastrus orbiculatus`)
5. Nadelkraut (`Crassula helmsii`)
6. Kap-Efeu (`Delairea odorata`)
7. Schmalblättrige Wasserpest (`Elodea nuttallii`)
8. Riesen-Bärenklau (`Heracleum mantegazzianum`)
9. Japanischer Hopfen (`Humulus scandens`)
10. Drüsiges Springkraut (`Impatiens glandulifera`)
11. Himalaya-Knöterich (`Koenigia polystachya`)
12. Wechselblatt-Wasserpest (`Lagarosiphon major`)
13. Großblütiges Heusenkraut (`Ludwigia grandiflora`)
14. Amerikanischer Stinktierkohl (`Lysichiton americanus`)
15. Brasilianisches Tausendblatt (`Myriophyllum aquaticum`)
16. Verschiedenblättriges Tausendblatt (`Myriophyllum heterophyllum`)
17. Wassersalat (`Pistia stratiotes`)
18. Japanischer Staudenknöterich (`Reynoutria japonica`)
19. Böhmischer Staudenknöterich (`Reynoutria × bohemica`)
20. Sachalin-Staudenknöterich (`Reynoutria sachalinensis`)
21. Riesen-Schwimmfarn (`Salvinia molesta`)

## Datenmodell und fachliche Trennung

`plant-catalog.json` wurde additiv von Schema 1 auf Schema 2 erweitert. Bestehende IDs, Felder und Profile bleiben erhalten. Das neue optionale Objekt `neophyte` enthält unter anderem:

- Neophyten-, Invasivitäts- und EU-Unionslistenstatus,
- österreichischen Vorkommens- und Etablierungsstatus,
- Erkennungsmerkmale, Ausbreitung und Auswirkungen,
- Kontrolle, Entsorgung und rechtliche Hinweise,
- Stichtag und nachvollziehbare Quellen.

Der UI-Filter unterscheidet jetzt:

- alle im Katalog erfassten Neophyten (22),
- etabliert oder lokal etabliert (13),
- invasiv eingestuft (22),
- EU-Unionsliste und damit unionsrechtlich reguliert (21).

„Neophyt“, „invasiv“ und „rechtlich reguliert“ werden ausdrücklich nicht gleichgesetzt. Das Lexikon ist weiterhin eine kuratierte Praxisauswahl und kein vollständiges Verzeichnis aller 1.615 in der österreichischen Checkliste erfassten Neophytentaxa.

## Fach- und Rechtsquellen

Für Status, Vorkommen, Erkennung und Auswirkungen wurden amtliche beziehungsweise primäre Quellen verwendet:

- Umweltbundesamt Österreich: [Neophyten der EU-Unionsliste](https://www.neobiota-austria.at/ias-unionsliste/neophyten) und die dort verlinkten 21 Artenprofile.
- Umweltbundesamt Österreich: [Maßnahmen gegen invasive gebietsfremde Arten](https://www.neobiota-austria.at/biologische-invasionen/ias-massnahmen).
- Umweltbundesamt Österreich: [Sommerflieder](https://www.neobiota-austria.at/neobiota-national/neobiota-auswahl/sommerflieder).
- Europäische Kommission: [Invasive alien species](https://environment.ec.europa.eu/topics/nature-and-biodiversity/invasive-alien-species_en).
- Europäische Union: [Durchführungsverordnung (EU) 2025/1422](https://eur-lex.europa.eu/eli/reg_impl/2025/1422/oj).
- Glaser et al. (2025): „Checklist of Austrian neophytes (2nd edition)“, Preslia 97: 413–539, als taxonomischer und fachlicher Hintergrund.

Jeder neue Datensatz speichert seine konkreten Quellen und den Prüfstand. Nicht zuverlässig belegte gärtnerische Einzelwerte – etwa Licht-, pH- oder Winterhärteangaben für einzelne Wasser- und Problempflanzen – bleiben leer und werden in der Oberfläche als nicht angegeben ausgewiesen.

Für alle 21 neuen Profile sind dagegen das amtlich beschriebene Wuchs-/Erkennungsbild und konkrete Kontroll- beziehungsweise Pflegehinweise befüllt. Soweit der amtliche Text es ausdrücklich trägt, werden außerdem Lebensdauer und Laubverhalten übernommen. Ein sichtbarer Hinweis „Datenumfang“ erklärt je Profil, warum nicht sicher übertragbare Standardfelder bewusst leer bleiben. Am 16. Oktober 2026 wurden die 21 UBA-Artenlinks sowie der Sommerflieder-Link erneut abgerufen; alle 22 lieferten HTTP 200. Die UBA-Übersicht wies weiterhin genau 21 in Österreich vorkommende Pflanzenarten der EU-Unionsliste aus.

Die Rechtsangaben sind Orientierung zum Prüfstand. Status und örtliche Entsorgungs- beziehungsweise Naturschutzvorgaben können sich ändern; vor Maßnahmen sind die verlinkten amtlichen Quellen und die zuständige Stelle zu prüfen.

## Bilder, Lizenzen und Offline-Speicherung

Die 42 neuen Bilder stammen von Wikimedia Commons. Die Build-Pipeline übernimmt nur Dateien mit CC0, Public-Domain-Markierung, CC BY oder CC BY-SA; NC- und ND-Lizenzen werden abgewiesen. Autor, Commons-Dateiseite, Lizenz und Lizenzlink stehen pro Bild im Katalog und in der Detailansicht.

Lizenzverteilung der 42 neuen Bilder:

| Lizenz | Anzahl |
| --- | ---: |
| CC BY 2.0 | 4 |
| CC BY 3.0 | 5 |
| CC BY 3.0 US | 6 |
| CC BY 4.0 | 2 |
| CC BY-SA 2.0 FR | 1 |
| CC BY-SA 2.5 | 1 |
| CC BY-SA 3.0 | 5 |
| CC BY-SA 4.0 | 12 |
| CC0 | 1 |
| Public domain | 5 |

Alle neuen Dateien wurden auf maximal 800 × 650 Pixel verkleinert, als progressive JPEGs gespeichert und ohne EXIF-/GPS-Metadaten ausgegeben. Die Bilder liegen lokal in Android- und Web-Assets; der Service Worker führt alle 362 Katalogbilder explizit im Offline-Cache. Android- und Web-Katalog sowie JavaScript/CSS-Spiegel sind bytegleich.

Die Anforderung „mehrere Beispielbilder“ ist mit zwei Bildern pro neuem Profil umgesetzt. Eine vollständige Bildserie aus Gesamtansicht, Blatt, Blüte, Frucht, Rinde und Schadbild für jede Art ist wegen gesicherter Artzuordnung, Lizenzlage und Offline-Größe nicht umgesetzt.

## Vollbildansicht

Pflanzenbilder öffnen aus demselben bestehenden Gartenpirat-Dialog in einer Vollbildansicht. Umgesetzt sind:

- Vor/zurück zwischen den zwei Bildern,
- Zoomtasten, Mausrad und Doppeltippen,
- Pinch-to-Zoom und Verschieben per Zeigerereignissen,
- Zoombegrenzung von 1× bis 5×,
- Bildtitel, Autor, Lizenz und Quelle,
- Android-Zurück beziehungsweise Schließen zurück zum gleichen Pflanzenporträt,
- umbrechende Symbolleiste ohne horizontalen Überlauf bei 320 Pixel Breite.

Es wurde keine zusätzliche Bild- oder Zoom-Bibliothek eingebaut.

## Build- und Spiegelhygiene

`tools/build_neophyte_catalog.py` baut die Erweiterung aus `tools/neophyte_profiles.json`, übernimmt lizenzierte Commons-Metadaten, optimiert neue Bilder und schreibt Android- und Web-Katalog aus demselben Speicherobjekt. Die Pipeline prüft eindeutige IDs/wissenschaftliche Namen, Bildanzahl, erlaubte Lizenzen, Offline-Dateien, die bestehende Nicht-Unionslisten-Klassifizierung und Spiegelgleichheit. Der Service-Worker-Cache wurde auf `haus-garten-v16-neophytes-viewer` angehoben.

Die Pipeline ist additiv und öffnet weder die produktive Datenbank noch Synchronisationswarteschlangen. Pflanzenlexikon, Bilder und UI bleiben vollständig offline nutzbar; Geschäfts- und Kundendaten sowie deren Synchronisierung wurden nicht verändert.

## Tests und Grenzen

Zieltests prüfen Katalogschema, Arten- und Bildzahlen, Quellen/Lizenzen, fehlende EXIF-Marker, Spiegelgleichheit, Service-Worker-Liste, Suche/Filter, Statusdifferenzierung, Vollbildnavigation, Zoom, Android-Zurück, 320-Pixel-Layout, Offline-Bilder und die Trennung von Geschäftsdaten. Zusätzlich prüft der HTTP/PWA-Test Dashboard-Sichtbarkeit, Service-Worker-Offline-Neustart und den Viewer.

Ausgeführte Prüfungen:

- `node tests/gartenpirat-plants.test.cjs`: vollständig bestanden.
- `node tests/gartenpirat-web.test.cjs` über einen lokalen HTTP-Server: vollständig bestanden; alle 362 Bilder wurden vom Service Worker installiert und offline geladen.
- Syntax- und Spiegelprüfung für JavaScript, Python, JSON/JS-Kataloge und CSS: bestanden; Android und Web sind bytegleich.
- `python tests/run_quality_suite.py`: 180 Tests ausgeführt, davon 179 bestanden. Ein fachfremder Rechnungstest (`test_invoice_http_confirmation_issue_and_pdf`) scheiterte reproduzierbar an seinem festen 5-Sekunden-HTTP-Timeout beim PDF-Erstellen (`ConnectionAbortedError` im Server). An Rechnungs- oder Servercode wurde in diesem Pflanzen-Scope nichts geändert.

Ein erfolgreicher Browser- oder Build-Test ersetzt keinen vollständigen Test auf einem realen Android-Gerät. Pinch-to-Zoom, Kamera-/Galerieintegration und Bedienung mit Android-Zurück sollen deshalb vor produktiver Freigabe zusätzlich auf dem Zielgerät geprüft werden. Der Katalog ist ein Arbeits- und Nachschlagewerk, keine sichere Bestimmungs-, Verzehr- oder Bekämpfungsfreigabe.
