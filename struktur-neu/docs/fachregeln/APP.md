# Eisbär: einzige Design- und Funktionsumfangsvorgabe

**Eisbär 1.0.1** (`HG-UMB-20261110-001`) ist auf ausdrücklichen Auftrag des
Eigentümers die alleinige Vorgabe für den vollständigen Umbau der
Haus-und-Garten-App. Das gilt für Oberfläche, Funktionen, Aktionswege,
Datenbeziehungen, Appcode und Abnahme.

| Maßgeblicher Bestandteil | Datei |
|---|---|
| Autorität, Version und SHA-256 | [EISBAER.json](eisbaer/EISBAER.json) |
| Vollständige Übersicht mit allen gültigen Illustrationen | [Eisbär-PDF](eisbaer/Eisbaer-Konzept-v1.0.1.pdf) |
| Durchsuchbare Anforderungen, Abläufe, IDs und Verträge | [Eisbär-Spezifikation](eisbaer/Eisbaer-Metadaten-v1.0.1.json) |
| Originalprüfsummen | [Prüfsummen](eisbaer/Eisbaer-Pruefsummen-v1.0.1.json) |
| Umsetzung und tatsächliche Abnahmen | [UMSETZUNG.json](eisbaer/UMSETZUNG.json) |

PDF und JSON gehören zu **derselben** Eisbär-Version und werden unverändert
aufbewahrt. Die JSON enthält zusätzlich den identischen, in der PDF eingebetteten
Datenstand. Menü-, Aktions-, Schritt-, Anforderungs- und Bildkennungen verbinden
beide Darstellungen. Die `page_map` nennt die physischen PDF-Seiten ab 1;
ursprüngliche `/workspace`-Pfade sind Herkunftsangaben, keine im Repository
benötigten Dateien. Alle gültigen Illustrationen stehen bereits in der PDF.
Drive, Chatverlauf und lokale Arbeitsdateien werden zum Lesen nicht benötigt.

## Was ersetzt ist

Das frühere Konzept „Gartenpirat Optik-Entwürfe“ / Claude vom 08.11.2026 und die
bisherigen widersprechenden Abschnitte dieser Datei und des aktuellen Stands sind
ersetzt. Historische Commits, alte Screenshots, vorhandenes Appverhalten,
Kommentare und bisherige Tests begründen keine zweite Designvorgabe. Tests für
ersetzte Abläufe werden bei der Umsetzung passend zu Eisbär aktualisiert;
echte Fehler werden nicht durch das Löschen von Prüfungen verdeckt.

Insbesondere sind Labor als neue Abrechnungsart, permanente Suchfelder oben,
Objektpflege samt Planungslinks und ein neues ZIP pro Sicherungslauf keine
aktuellen Anforderungen. Maßgeblich sind Eisbärs L.C.R.-Kennzeichnung am
Original, untere Lupe, gemeinsame Terminquelle und dieselbe ergänzte
Sicherungsdatei. Historische Belege und Zahlungen bleiben geschützt.

Diese Datei ist Einstieg und Autoritätsregel, keine konkurrierende gekürzte
Spezifikation. Technische Schutzregeln aus AGENTS.md sowie Bau- und
Signierungsregeln gelten weiter; daraus darf kein anderer Design- oder
Funktionsumfang abgeleitet werden. Neue Eigentümerentscheidungen werden als
versionierte Eisbär-Änderung mit aktualisierten Originalen, Prüfsummen und
Abnahmen eingetragen. Anforderungen werden nicht still ausgelassen, entfernt
oder als „nicht sinnvoll“ verworfen.

## Wann der Umbau abgeschlossen ist

**Erst wenn Eisbär vollständig umgesetzt und nachgewiesen ist.**

- Jede Anforderung, jeder Menüweg mit seinen Aktionen und Schritten, jeder
  Finanzablauf, Code-/Datenbereich und Migrationsschritt muss abgenommen sein.
- Die Bildtafeln und Detailzustände müssen mit der wirklichen nativen Oberfläche
  vereinbar sein. Ein HTML-Prototyp oder ein Konzeptbild ist kein Appnachweis.
- Alle vorgesehenen Tests und sämtliche MCP-Verträge einschließlich neuer
  Funktionen müssen tatsächlich geprüft sein. Entfernte Pflegewege erhalten
  ausdrücklich kompatible Übergangsantworten, keine heimliche Umdeutung.
- Voller grüner CI-Lauf ohne Teilprüfschalter und echte Pixel-/MCP-Abnahme müssen
  denselben Appkandidaten belegen. Kamera, Freigaben, Verbindungen und sichere
  Fortschreibung/Wiederherstellung der gewählten ZIP benötigen echte Nachweise.
- Keine offenen, fehlgeschlagenen, übersprungenen oder pauschal als nicht
  zutreffend abgehakten Abnahmen. Nachweise verwenden synthetische Daten und
  enthalten Paket, Appversion, Quellhash, Build-Commit und tatsächliche Ergebnisse.
  Beleg-/Betriebsdaten und Geheimwerte gehören nicht ins Repository.

Der Fortschritt wird ausschließlich in `UMSETZUNG.json` geführt. Die Originale
bleiben Konzeptoriginale; ihr damaliges `NOT_IMPLEMENTED` wird nicht nachträglich
umgeschrieben. Der Umsetzungsstand startet vollständig offen. Ein fertiger
Teil-PR, bestandene Dokumentprüfung oder bestehender Alt-Testlauf darf nicht als
abgeschlossener Eisbär-Umbau bezeichnet werden.

```sh
# Integrität, vollständige Abnahmeliste und ehrlichen Zwischenstand prüfen:
python3 -B struktur-neu/tools/check_current.py

# Abschlussnachweis; muss bis zur vollständigen Umsetzung fehlschlagen:
python3 -B struktur-neu/tools/check_eisbaer.py --completion
```

Bei einem Abschlussstatus aktiviert sich die strikte Prüfung auch ohne
`--completion`. Fehlende Nachweise blockieren den Abschluss. Der Prüfer
kontrolliert Konsistenz und Nachweisdateien; tatsächliche Laufzeittests und
Bedienprüfungen müssen ausgeführt werden und dürfen nicht durch PASS-Einträge
ersetzt werden. Installation und Veröffentlichung bleiben eigene Aufträge.

## Nachweise eintragen

`overall_status` lautet `NOT_STARTED`, `IN_PROGRESS` oder `COMPLETE`. Die
Abnahmemenge wird aus den Original-IDs abgeleitet; neue Pflichten aus einer
Eisbär-Revision werden übernommen, keine bisherigen Einträge weggelassen.
`PASS` benötigt Referenzen aus `evidence`; ohne Beleg bleibt die Abnahme offen.

Jeder Beleg ist ein lokaler JSON-Bericht relativ zum Eisbär-Verzeichnis,
üblicherweise unter `nachweise/`, mit SHA-256 im Nachweisregister. Der Bericht
enthält Art, Ergebnis, tatsächlichen UTC-Zeitpunkt, ausführenden Befehl,
`covers` mit genau den geprüften IDs und aussagekräftige Beobachtungen.
`candidate` enthält Paket, Versionsname/-code, Build-Git-Commit und App-Quellhash
und stimmt in allen Berichten mit dem abgenommenen Kandidaten überein. Der
Abschlussprüfer vergleicht dessen Appversion und Quellhash mit dem Checkout.
Ein späterer reiner Dokumentationscommit verändert nicht den geprüften Appcode.

Die Nachweisarten sind `runtime`, `ui`, `code`, `data`, `migration`, `mcp`, `ci`
und `pixel`. Die fünf Gesamtbedingungen verlangen jeweils ausdrücklich CI-,
Pixel-, MCP-, Datenbewahrungs- oder Pixel-Sicherungsnachweise. CI nennt den
tatsächlichen vollständigen Workflowlauf im Repository; Pixel-Berichte
kennzeichnen das echte Gerät. Eine Behauptung oder ein künstlich ausgefüllter
Bericht ersetzt niemals eine Ausführung. Die neuen synthetischen Guard-Tests
prüfen nur diese Abschlussregeln und sind keine Eisbär-Funktionsabnahme.
