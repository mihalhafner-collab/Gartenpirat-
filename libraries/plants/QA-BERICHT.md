# Erneute Gesamtprüfung der Pflanzenbibliothek

Stand: 23.10.2026, Revision 2. **Quellen- und Lizenzmetadatenprüfung abgeschlossen;
neue Bildoriginale weiterhin nicht gesichtet.** Ergebnis der vollständigen
Datenprüfung: `PASS_WITH_DOCUMENTED_HOLDS`. Keine App-Implementierungsfreigabe.

| Prüfung | Ergebnis |
| --- | ---: |
| Ausgewählte Profile, alle einzeln geprüft | 314 |
| Davon Bestand / Erweiterung | 181 / 133 |
| Individuell belegte Fotoreferenzen | 1140 |
| Profile mit mindestens zwei Fotoreferenzen | 314 von 314 |
| Profile mit strukturierten botanischen Standortfakten | 303 |
| Zusätzliche amtlich identifizierte Neophyten-Referenzen ohne Pflegeanleitung | 11 |
| Exakte GBIF-Abgleiche | 313 |
| Zusätzliche Kew-Namensklärungen | 2 |
| Datierte amtliche Rechtsabgleiche | 26 |
| Alte Bilddateien erneut per Hash geprüft | 362 |
| Erneut bestätigte alte Credits | 341 |
| Bestandsderivate für spätere Wiederverwendung zugelassen | 335 |
| Bestandsderivate ausdrücklich ausgeschlossen | 27 |
| Neu heruntergeladene / persönlich gesichtete Bildoriginale | 0 / 0 |
| Unabhängige botanische Expertenabnahmen | 0 |

Die 27 Ausschlüsse sind abschließende redaktionelle Entscheidungen für diese
Bibliothek. 21 Credits wurden nicht exakt erneut bestätigt; sechs weitere Bilder
haben bestätigte Credits, sind aber als Lebendpflanzenfoto ungeeignet. Die neun
bekannten Motivbeanstandungen sind darin enthalten. Dateien der bestehenden App
wurden weder verändert noch gelöscht. Ausschluss bedeutet keine Behauptung einer
Rechtsverletzung und keine nachträgliche Lizenzbestätigung.

## Abgeschlossene Korrekturen

Die ursprüngliche verkürzte Webseitenextraktion ließ Merkmalslisten und
Bildattribute aus. Die Prüfung verwendet nun den Original-DOM mit dt/dd-Feldern
und zusammengehörigen Attributen für Bild, Motiv, Urheber und individuelle Lizenz.
RHS-Moisture wird als Bodendurchlässigkeit erfasst; daraus entsteht keine erfundene
Gießmenge. RHS-Gift- und Kontaktwarnungen werden gesondert mit Quellen bewahrt.
Historische Warnungen werden durch fehlende neue Werte nicht aufgehoben.

Ein Parserfehler behandelte das Zeichen `<` in pH-Angaben als HTML-Anfang. Der
Parser ist korrigiert und synthetisch geprüft. Bereits beschädigte numerische
Suffixe wurden nicht rekonstruiert: Die belegte qualitative Angabe „Acid“ bleibt
als „Sauer“ erhalten. Quellenfehler und unbekannte Werte werden nicht zu
präziseren Empfehlungen umgedeutet.

Kew bestätigt Pennisetum alopecuroides als Synonym von Cenchrus alopecuroides und
Coreopsis verticillata als Synonym von Anacis verticillata. Historische
GBIF-Antworten und bestehende IDs bleiben nachvollziehbar. Gartengruppen und das
Brombeer-Aggregat sind als solche gekennzeichnet, ohne einzelne Sorten vorzutäuschen.

Commons-Credits wurden über individuelle imageinfo/extmetadata-Datensätze
abgeglichen. CC0-Schreibweisen sind vereinheitlicht; länderspezifische
Lizenzfassungen bleiben mit ihrer konkreten Versions-/Länderkennung erhalten.
NC, ND und pauschale Public-Domain-Behauptungen ohne ausreichenden Nachweis werden
nicht als neue Fotolizenz übernommen. Unterschiedliche nichtlateinische
Urhebernamen werden beim Vergleich nicht mehr auf denselben Leerwert reduziert.

Zusätzliche Fotoquellen schließen die früher fehlenden zweiten Referenzen.
Explizit ausgeschlossene Werke dürfen nicht über Ergänzungslisten zurückkehren.
Drei Ludwigia-Aufnahmen mit dem Quellenmerkmal „subsp. hexapetala“ sind als
Artbeleg für L. grandiflora ausgeschlossen: Kew führt sie unter L. hexapetala.
Eine weitere Ludwigia-Datei nennt selbst Bestimmungszweifel und ist ausgeschlossen.
Die maschinelle Quellenzuordnung ersetzt weiterhin keine Sichtbestimmung.

## Recht und Management

BfN-Listungsdaten wurden je Art einschließlich Wirksamkeitsdatum gelesen und
mit dem österreichischen Behördenkontext getrennt von Schweizer Recht erfasst.
Bei Celastrus orbiculatus greifen die EU-Listungsbeschränkungen erst am
**02.08.2027**. Das Schweizer Umgangsverbot gilt davon unabhängig.

Die FrSV-Ausgabe vom 01.09.2024 wurde zusammen mit der aktuellen BAFU-FAQ vom
18.03.2026 geprüft. Anhang 2.1 (Umgangsverbot mit gesetzlichen Ausnahmen) und
Anhang 2.2 (Inverkehrbringen/Einfuhr) bleiben getrennt. Für bestehende Pflanzen
unter Anhang 2.2 wird keine allgemeine Entfernungspflicht erfunden. Auch
Kirschlorbeer, Hanfpalme, Neubelgische Aster und Fünfblättrige Jungfernrebe erhalten
die belegten Schweizer Einschränkungen.

Die 22 historischen Neophytenprofile sind Identifikations- und
Präventionsreferenzen. Vermehrungsanleitungen werden dort nicht angeboten. Elf
haben keine zusätzliche strukturierte botanische Profilquelle; diese Lücke ist
als fehlende Pflegeinformation sichtbar und wird nicht durch erfundene Werte
ersetzt. Konkrete Bekämpfungs-, Transport- und Entsorgungsverfahren sind örtlich
abzustimmen. Keine vollständige Prüfung aller Landes-/Kantons-/Schutzgebietsregeln.
Botblockaden bei EUR-Lex und die Fedlex-JS-Hülle sind als gescheiterte Direktabrufe
dokumentiert; die amtlichen Ersatzbelege werden ausdrücklich benannt.

## Verwendete Quellen

- NC State Extension Plant Toolbox: individuelle Pflanzenmerkmale und Bild-Credits.
- Royal Horticultural Society: Standortmerkmale, Rosen-Gattungsratgeber und Warnungen.
- GBIF und Royal Botanic Gardens, Kew / Plants of the World Online: Namen und Synonyme.
- Wikimedia Commons: individuelle Dateimetadaten, Urheber und exakte Bildlizenzen.
- BfN, Umweltbundesamt Österreich, LfU Brandenburg: EU-Listung, Wirksamkeit und Prävention.
- BAFU und amtlicher FrSV-Text bei Info Flora: Schweizer Regelungen.

Feldbelege, Einzelentscheidungen und gescheiterte Abrufe stehen in `evidence/`.
Längere neue Beschreibungen werden nicht als eigene Texte kopiert; Bildwerke
bleiben unter ihrer jeweiligen Fremdlizenz. Keine temporären URL-Signaturen,
EXIF-/GPS-Daten oder Betriebsdaten in der Bibliothek.

## Erneut vollständig geprüft

Alle **394 Datensätze** einschließlich der 80 weiterhin nicht ausgewählten
Recherchekandidaten wurden zweimal in getrennten Auditorprozessen geprüft.
Beide Durchgänge haben identische Eingabehashes und keine Konsistenzfehler.
Drei Synonym-/Bestandsduplikate bleiben ausgeschlossen. Wiederholungsbau identisch.
19 Python-Prüfungen und zwei Original-DOM-Parserprüfungen bestanden. Der Auditor
verwirft unter anderem falsche Urheber, falsche Lizenz-URLs, erfundene Sicht- oder
Expertenfreigaben, verfrüht behauptete Rechtswirksamkeit und wiedereingeführte
Bildsperren. Der App-Abgleich besteht unverändert mit 181 Profilen/362 Bildern und
370 generierten Ausgaben.

## Noch blockiert

Die Cloud-Netzrichtlinie erlaubt die Bildserver weiterhin nicht. Die
Recherchewerkzeuge liefern hier Metadaten und Referenzen, keine tatsächlich
sichtbaren Originalbytes. Deshalb sind neue Downloads und persönliche
Fotosichtungen **nicht durchgeführt**. `pending-image-review.json` enthält den
konkreten Anschlussbestand; jedes ausgewählte Profil bleibt auf HOLD. Bei
Wiederaufnahme mindestens zwei geeignete Motive je Profil tatsächlich öffnen,
Taxon-/Sortenbezug prüfen, Dateibytes hashen und Nutzungsbedingungen erneut lesen.
Quellenbasiert geprüft bedeutet keine unabhängige artdiagnostische Expertenabnahme.

Source-Privacy PASS. Das allgemeine Workspace-Gate enthält weiterhin die
21 bereits vorgefundenen, auftragsfremden Befunde. Kein Gate wurde umgangen,
kein P0-Gesamtabschluss behauptet. Ein vollständiger App-Quellarchivexport bleibt
an diesem bestehenden Gate blockiert. Der Bibliotheksstand wird auf demselben
privaten GitHub-Arbeitsbranch gesichert. Ein separates Daten-ZIP enthält nur
Bibliotheksdaten und die 335 bestätigten, bei der Übersichtssichtung unauffälligen
Bestandsderivate; es ist kein vollständiger App-Quellcodeexport.
