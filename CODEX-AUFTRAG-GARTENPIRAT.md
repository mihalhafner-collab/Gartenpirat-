# Aktueller Gartenpirat-Auftrag

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
