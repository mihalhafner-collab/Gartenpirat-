# Private ChatGPT-Verbindung für Hafner Grünraum Handwerk

## Was eingebaut wurde

Die vorhandene Haus-&-Garten-App besitzt jetzt einen privaten MCP-Dienst. Er verwendet keine zweite Datenbank und keine neue App-Logik:

- gelesen wird ausschließlich aus `HausGarten-Laptop.json`,
- neue und korrigierte Arbeitsaufzeichnungen werden ausschließlich als gültige Aktionen unter `actions` in `HausGarten-ChatGPT.json` ergänzt,
- erst der bereits vorhandene Drive-Abgleich der Laptop-App prüft und übernimmt diese Aktionen,
- bestehende IDs bleiben unverändert; Kunden- und Auftrags-IDs werden immer aus dem echten Datenbestand gelesen,
- es gibt kein Löschwerkzeug und keinen freien Datei- oder Datenbankzugriff.

ChatGPT kann Kunden, Projekte/Aufträge und Arbeitsaufzeichnungen suchen und anzeigen, offene Aufträge abfragen, Arbeitsaufzeichnungen anlegen oder kontrolliert korrigieren sowie Arbeitszeit, Pause, Tätigkeit, Material, Kilometer, Arbeitsort und Notizen verarbeiten.

## Datenweg

```text
ChatGPT auf Handy oder Computer
        ↓ privater OpenAI Secure MCP Tunnel
MCP-Dienst auf deinem Laptop
        ↓ validierte Aktion, atomar und wiederholsicher
Haus & Garten App/HausGarten-ChatGPT.json
        ↓ vorhandener App-Abgleich
Haus-&-Garten-Datenbank
        ↓ aktualisierter Snapshot
Haus & Garten App/HausGarten-Laptop.json
```

`queued` bedeutet: Die Aktion ist sicher im Drive-Kanal vorgemerkt, aber noch nicht von der App übernommen. `accepted` bedeutet: Die App hat die `client_id` verarbeitet und im neuen Laptop-Snapshot bestätigt.

## Einmalige Vorbereitung

1. Google Drive für Desktop muss auf dem Laptop laufen. In der Haus-&-Garten-App muss als Drive-Ordner weiterhin **Haus & Garten App** eingestellt sein.
2. In diesem Ordner müssen `HausGarten-Laptop.json` und `HausGarten-ChatGPT.json` vorhanden sein.
3. Python 3.11 wird verwendet. Die mitgelieferten Startdateien wählen diese getestete Version automatisch.
4. Der offizielle OpenAI `tunnel-client` v0.0.16 ist lokal installiert, per veröffentlichter SHA-256-Prüfsumme verifiziert und das Profil `haus-garten-private` ist für den Tunnel `<PRIVATE_TUNNEL_ID>` vorbereitet.
5. Unter https://platform.openai.com/settings/organization/api-keys einen **Runtime API Key** erzeugen. Der zugehörige Benutzer benötigt **Tunnels Read + Use**.
6. Im App-Ordner `ChatGPT-Verbindung-starten.cmd` doppelklicken. Den Runtime API Key in das Fenster einfügen. Die Eingabe bleibt unsichtbar, gilt nur für diesen laufenden Prozess und wird nicht gespeichert.
7. Während dieses Fenster läuft, in ChatGPT einen benutzerdefinierten MCP-Server über **Tunnel** hinzufügen und den Tunnel `<PRIVATE_TUNNEL_ID>` auswählen.

Für spätere Starts genügt wieder ein Doppelklick auf `ChatGPT-Verbindung-starten.cmd` und die verdeckte Eingabe des Runtime API Key.

### Automatischer Start beim Einschalten/Anmelden

Mit `Tunnel-Autostart-einrichten.cmd` kann der Tunnel einmalig für den aktuellen Windows-Benutzer eingerichtet werden. Dabei wird der Runtime-Schlüssel mit Windows-DPAPI verschlüsselt gespeichert; er steht weder im Projekttext noch in der Aufgabenplanung. Danach startet der Tunnel bei jeder Windows-Anmeldung automatisch und wird bei einem kurzen Abbruch automatisch neu gestartet.

Das ist bewusst ein Start **bei der Windows-Anmeldung**, nicht beim ausgeschalteten Laptop. Der Laptop muss eingeschaltet, angemeldet und online sein. Zum Entfernen des Autostarts in der Windows-Aufgabenplanung den Eintrag **Haus & Garten – ChatGPT Tunnel** deaktivieren oder löschen. Die ChatGPT-App und die Pixel-App müssen dafür nicht neu installiert werden.

Das Verbindungsfenster muss während der Nutzung geöffnet bleiben. Es wird kein öffentlicher Port auf dem Laptop geöffnet.

## Nutzung per Sprache

Beispiele:

- „Suche den Kunden Huber und zeige mir seine offenen Aufträge.“
- „Trage heute beim Auftrag Heckenpflege drei Stunden Heckenschnitt ein.“
- „Heute von 8:00 bis 12:30, 30 Minuten Pause, Thuja geschnitten, 14 Kilometer.“
- „Ergänze bei der Aufzeichnung zwei Säcke Lärchenmulch und die Notiz: tote Äste entfernt.“
- „Zeige mir die Arbeitsberichte für Kunde Krassnitzer im Oktober.“

Bei mehreren passenden Kunden oder Aufträgen muss ChatGPT nachfragen. Es darf keine ID raten. Neue Arbeitsaufzeichnungen benötigen einen vorhandenen Auftrag; der Kunde wird automatisch aus diesem Auftrag übernommen.

## Schutz der Daten

- Jede Aktion besitzt eine eindeutige, aus der `request_id` abgeleitete `client_id`.
- Derselbe Auftrag kann gefahrlos erneut gesendet werden; er wird nicht doppelt gebucht.
- Gleiche `request_id` mit verändertem Inhalt wird abgelehnt.
- Ähnliche vorhandene oder bereits vorgemerkte Arbeitsaufzeichnungen lösen eine Dublettenwarnung aus.
- Kunde und Auftrag werden vor dem Vormerken gegen den aktuellen Laptop-Snapshot geprüft.
- Beginn, Ende, Pause, Dauer, Kilometer und Pflichtfelder werden validiert.
- Der ChatGPT-Kanal wird atomar ersetzt und anschließend erneut gelesen. Vorher entsteht eine lokale Kopie unter `data/mcp-channel-backups`.
- Beschädigte JSON-Dateien, ungültige Beziehungen und gleichzeitige Änderungen stoppen den Schreibvorgang; es wird nicht blind überschrieben.
- Das technische Protokoll enthält nur Zeitpunkt, Werkzeug, Erfolg/Fehler und eine betroffene Kennung, aber keine Kundennamen, Tätigkeiten, Notizen oder Zugangsdaten.
- Texte aus Betriebsdaten sind als unvertrauenswürdige Nutzdaten markiert und werden nie als Anweisungen ausgeführt.

## Fehler erkennen

- `CUSTOMER_NOT_FOUND` / `ORDER_NOT_FOUND`: zuerst erneut suchen; keine ID raten.
- `RELATION_MISMATCH`: Kunde und Auftrag gehören nicht zusammen.
- `POSSIBLE_DUPLICATE`: ähnliche Aufzeichnung bereits vorhanden; vor einer neuen Buchung prüfen.
- `REQUEST_ID_REUSED`: dieselbe Kennung wurde mit verändertem Inhalt verwendet.
- `queued`: Drive-Aktion gespeichert, App-Abgleich steht noch aus.
- `CHANNEL_CONFLICT`: die Drive-Datei wurde gleichzeitig geändert; erneut versuchen.
- `INVALID_JSON` / `BROKEN_RELATION`: Abgleich anhalten und Dateien/Sicherung prüfen.
- Tunnel nicht erreichbar: Tunnel-Fenster prüfen und `tunnel-client doctor --profile haus-garten-private --explain` ausführen.

## Wiederherstellung und Deaktivierung

Vor jeder MCP-Änderung des Aktionskanals wird dessen vorherige Fassung unter `data/mcp-channel-backups` gesichert. Die bestehende App-Sicherung für Datenbank, Fotos und Kontrolllisten bleibt unverändert aktiv.

Zum Deaktivieren das Tunnel-Fenster schließen. Zusätzlich kann der private Tunnel im OpenAI-Konto deaktiviert und der Laufzeitschlüssel widerrufen werden. App und Daten bleiben davon unberührt.

## Smartphone und Kosten

Nach der einmaligen ChatGPT-Verknüpfung kann die normale ChatGPT-App auch über Mobilfunk verwendet werden. Der Laptop muss eingeschaltet sein, Google Drive für Desktop und der Tunnel müssen laufen, und der Laptop benötigt Internet. Ein gemeinsames WLAN mit dem Pixel ist nicht nötig. Der Autostart sorgt dafür, dass die Verbindung nach der Windows-Anmeldung automatisch bereitsteht.

Die Pixel-App selbst benötigt für die normale mobile Datenerfassung keine direkte MCP-Verbindung: Sie synchronisiert über den bereits eingerichteten Drive-Ordner. ChatGPT-Sprachbefehle über das Pixel verwenden dagegen den MCP-Tunnel und funktionieren nur, solange dessen Ausführungsgerät läuft. Wenn ChatGPT auch bei ausgeschaltetem Laptop verfügbar sein soll, muss der MCP-Dienst später auf einen dauerhaft laufenden Mini-PC, NAS oder Cloud-Server verschoben werden; eine APK-Änderung allein kann das nicht lösen.

Der lokale MCP-Dienst selbst hat kein eigenes Abo. Mögliche Kosten oder Nutzungslimits ergeben sich aus dem persönlichen ChatGPT-/OpenAI-Tarif, der Tunnel-Nutzung und dem Google-Speicher.

## Offizielle technische Grundlage

- [Custom MCP server](https://developers.openai.com/api/docs/guides/custom-mcp-server)
- [Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels)
- [MCP und Connector Tools](https://developers.openai.com/api/docs/guides/tools-connectors-mcp)
