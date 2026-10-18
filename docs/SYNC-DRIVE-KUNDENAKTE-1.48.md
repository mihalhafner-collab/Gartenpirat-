# Drive-Abgleich und Kundenakte – 1.48.0 / 68

Umsetzung des freigegebenen Konzepts im vorhandenen Cloud-Repository und auf
`codex/original-signing-cloud-setup-20261018`, Ausgang `5e1ca93`. Vor Beginn war
der Arbeitsbaum sauber; der vorgeschriebene Vorfahr `05fdcbe` ist enthalten.
Keine Desktopübernahme, zweite operative Datenbank, Änderung an main,
Installation oder Betriebsdatenänderung.

## Ergebnis und Bedeutung der Zustände

Pixel bleibt die primäre App. Jede Änderung und ihr lokaler Auftrag werden in
der bestehenden SQLite-Datenhaltung gespeichert. Permanente Request-Belege und
inhaltliche Hashes verhindern doppelte fachliche Wirkung. Der UI-Stand zeigt
dieselbe Vorschau geschützter Eigentümeraktionen wie MCP; eine Vorschau ist kein
Empfangsbeleg. Offene Geschäftsaufträge werden getrennt von Übertragungen gezählt.

| Zustand | Tatsächlicher Nachweis |
| --- | --- |
| Lokal gespeichert | Dauerhafter Datensatz/Aktionsauftrag auf dem Pixel |
| Übertragung offen | Aktion bleibt erhalten; Ursache und nächste Handlung sichtbar |
| Provider zurückgelesen | Android-Dokumentanbieter hat Inhalt geliefert; keine unabhängige Cloud-Bestätigung |
| In Drive bestätigt | Direkter HTTPS-Serverinhalt und inhaltlich gebundener Veröffentlichungsbeleg geprüft |
| Vollständig abgeglichen | Fachliche Übernahme und notwendige Originale bestätigt; keine offene Prüfung |
| Prüfung erforderlich | Version, Berechtigung, Original, Herausgeber oder geschützter Abschluss blockiert |

Offlinebetrieb und echte Konflikte können keine ehrliche stets leere Warteschlange
garantieren. Die App löscht oder bestätigt solche Aktionen nicht zur kosmetischen
Bereinigung. Der zuletzt früher gelesene operative Bestand mit sieben offenen
Aktionen/einem Kundenkonflikt wurde in diesem Entwicklungsauftrag nicht verändert
und nicht erneut live geprüft.

## Neuer Drive-Transport

- `DriveRestClient`: HTTPS zu Google, explizite Authentifizierung pro Aufruf,
  keine Token in Logs, Repo oder dauerhaftem App-Speicher. Ordnerzugriff bleibt
  auf die konfigurierte Ordner-ID begrenzt. Abgelaufene Anmeldung wird nicht
  umgangen; 429/Serverfehler verwenden begrenzte Wiederholungen und Retry-After.
- Upload in 256-KiB-Abschnitten mit privatem, dauerhaftem Sitzungs-Checkpoint.
  Nach Abbruch wird die tatsächliche Serverposition geprüft. Ein wiederholter
  Upload prüft vorhandene Bytes; abweichende Dateien werden nicht überschrieben.
- Alte Austauschdateien bleiben erhalten. Neue mobile Pakete sind unveränderliche,
  inhaltsadressierte Journaldateien mit denselben Aktionskennungen. Eine Wiederholung
  desselben Pakets erzeugt keine weitere Variante.
- `DriveDirectFiles` veröffentlicht erst den geprüften Datenstand, anschließend
  einen unveränderlichen Commit mit Inhalts- und Aktionsbelegen. Ein abgebrochener
  Abschluss wird über die bestätigte Veröffentlichungsfolge wieder aufgenommen.
  Zwei primäre Herausgeber, Lücken oder gleiche Folgenummern mit verschiedenen
  Varianten halten den Abschluss zur Prüfung an. Drive erhält keine behauptete
  Transaktion über mehrere Dateien.
- Foto- und PDF-Originale liegen getrennt in `HausGarten-Fotos` beziehungsweise
  `HausGarten-Dokumente`. Der neue veröffentlichte Rechnungsstand enthält Dateiverweise
  statt eingebetteter Original-PDFs. Fehlende Originale schließen Vollständigkeit aus.
  Unveränderte, bereits geprüfte Fotos werden anhand erneut gelesener Google-
  Dateiversion/Prüfsumme bestätigt, ohne jedes Original ständig neu herunterzuladen.
- `DriveSyncJob`: Android-Systemplanung mit Netzbedingung und dauerhaften Jobs,
  zusätzlich Wiederaufnahme bei App-Nutzung. Nach acht fehlgeschlagenen Versuchen
  wird eine konkrete Benutzerhandlung angezeigt. Entzogene Rechte benötigen eine
  bewusste Neuverbindung. Android kann Hintergrundausführung verzögern.
- Die Fortschrittsanzeige unterscheidet hochgeladene Abschnitte, Serverprüfung
  und bestätigten Inhalt. Ein abgebrochener Job bestätigt keine Warteschlange.

Der bisherige Dokumentbaum bleibt gespeichert und funktionsfähig. Eine direkte
Verbindung wird nur nach bewusster Gerätehandlung und lesender Prüfung des
bestehenden Ordners aktiviert. Kein automatisches Umschalten in einen anderen Ordner.

`sync_protocol` ordnet Vorgänger und Beziehungen unabhängig von Geräteuhren.
Explizite manuelle Änderungen an unterschiedlichen Feldern werden innerhalb der
geschützten Verarbeitung zusammengeführt; widersprüchliche Werte desselben Feldes
bleiben Konflikte. Explizite MCP-Vergleichsbasen werden weiterhin streng geprüft.
Ein einzelner Kunden-/Foto-Konflikt hält unabhängige gültige Aktionen nicht an.

Neue UI-Entfernungen nutzen den bestehenden versionierten Archivablauf mit
Undo-Zeitfenster. Datensatz und Originaldateien bleiben wiederherstellbar.
Ältere Löschaufträge erhalten einen sichtbaren Prüfpfad: aktuellen Stand behalten
oder den tatsächlich geprüften Eintrag archivieren. Die Originalaktion bleibt in
der Entscheidungshistorie. Ausgestellte Rechnungen und Beziehungen bleiben geschützt.

Der ergänzende Web-/Drive-Importer liest die neue veröffentlichte Pixel-Sicht,
verarbeitet dieselben Vorgänger und Regeln und darf keinen unvollständigen
Gesamtstand zurückexportieren. Lokales Lesen eines gemounteten Drive-Ordners
behauptet ausdrücklich keine unabhängige Google-Cloud-Bestätigung.

## Kundenakte und Pflanzenbilder

Die Kundenakte besitzt vier Bereiche: Übersicht, Garten, Fotos/Dateien und Verlauf.
Kontakt und Fotoaufnahme sind direkt erreichbar. Aufträge, letzte Arbeiten,
Rechnungen und eine kleine Fotovorschau halten die Übersicht kompakt. Größere
Verläufe und Bildlisten werden portionsweise geladen.

Gartengröße nutzt das vorhandene Feld `garden_area`. Rasenfläche, Heckenlänge,
Bewässerung, Pflege- und Zugangshinweise ergänzen bestehende Kunden-Zusatzfelder.
Fremde Zusatzfelder bleiben erhalten. Unbekannte Flächen bleiben null; null und
explizit 0 sind unterschiedliche Vergleichsstände. Native/Python-Regeln prüfen
Zahlen und Beziehungen der Flächen. Formulare behalten ihre Ausgangsversion und
lokale Entwürfe auch nach fehlgeschlagenem Speichern und Neustart.

Kundenfotos behalten ihre Originalbytes, feste Kundenzuordnung und optionale
Arbeits-/Vorher-Nachher-Zuordnung. Kleine Vorschaubilder ersetzen kein Original.
Noch nicht gespeicherte Dateien werden im bestehenden privaten Medien-Entwurfsspeicher
gesichert. Nach Abbruch/Neustart ist Wiederaufnahme möglich; dauerhafte native
Foto-Request-Belege verhindern doppelte Datensätze nach einem verlorenen Rückruf.
Der Dateibereich bündelt Originalfotos und vorhandene Rechnungsbelege.

Pflanzenbilder wechseln durch horizontales Wischen bei 100 %. Vergrößerte Bilder
werden verschoben, zwei Finger zoomen. Kurze, vertikale oder abgebrochene Gesten
wechseln kein Bild. Pfeile, Bildzähler, Quellen und Zurück bleiben erhalten. Die
181 Profile/362 Offlinebilder sind unverändert; Webspiegel wurden aus der
verbindlichen gemeinsamen Quelle erzeugt.

## Tatsächliche Abnahme

| Prüfung | Ergebnis / Grenze |
| --- | --- |
| Python | 253 Tests bestanden; 37 ältere QA-Signierungstests mit Schlüsselerzeugung ausdrücklich ausgeschlossen |
| JVM | 13 Suiten, 460 Prüffälle bestanden; synthetische JSON-/HTTP-Gegenstellen |
| Browser | 17 ausgewählte Suiten bestanden; darunter 14 neue Prüfungen der Kundenakte, Fotoentwürfe, Konfliktentscheidung, Fortschritt und Bildgesten |
| Touch | Tatsächliche Chromium-Touch-Ereignisse zusätzlich zu gezielten Pointer-/Abbruchfällen geprüft; keine Pixel-Messung |
| Darstellung | 320/360/426 px und 150 % Schrift; Namen-/Titelhöhe zusätzlich zum Überlauf geprüft, Screenshot visuell kontrolliert |
| Rendervergleich | 3.000 synthetische Arbeiten, 50 sichtbare Zeilen, keine versteckten Abrechnungszeilen; 38 ms Median/4.929 DOM-Knoten im Cloud-Chromium |
| Android | Release-Build, Release-Lint und Android-Testquellen-Kompilierung bestanden; null Lint-Fehler, elf Warnungen |
| Native Abnahme | Neue isolierte Foto-Replay-/Archiv-Abnahmen vorbereitet und kompiliert, nicht auf Android ausgeführt |
| Originalsignierung | Paket/Zertifikat, v2/v3, Ausrichtung, Quellassets und Cleanup tatsächlich geprüft; maschinenlesbarer Nachweis unten |
| Google/Pixel | Neue Anmeldung, realer Google-Abgleich, OS-Hintergrundverhalten, Installation und Update/Datenerhalt nicht ausgeführt |

Maschinenlesbarer Nachweis: [sync-customer-gallery-1.48.json](verification/sync-customer-gallery-1.48.json).
Der APK-Kandidat ist nicht als Geräte-Release freigegeben.

## Externe Voraussetzungen und Einführungsreihenfolge

1. Die gemeinsame Web-/Drive-Verarbeitung aus dieser Branch bereitstellen und
   vorhandene fachliche Capabilities beibehalten. Rechnungsabschluss benötigt
   weiterhin den vorhandenen zentralen Nummernservice; eine Offline-Vormerkung
   ist keine ausgestellte Rechnung.
2. Bestehende Daten einschließlich Originalen/Warteschlangen vollständig sichern.
   Installierte Paketversion und Originalzertifikat prüfen. Code 68 liegt über
   dem vorherigen Cloud-Kandidaten 67; die installierte Version bleibt unbekannt.
   Installation benötigt weiterhin ausdrückliche Freigabe. Keine Deinstallation.
3. Der Google-Projektinhaber muss Drive API, Android-OAuth-Registrierung mit Paket
   `at.hausgarten.service` und Original-SHA1-Zertifikat sowie Konto-/Consent-Freigabe
   tatsächlich bereitstellen. Der geprüfte öffentliche SHA1 steht im JSON-Nachweis.
   Der Zugriff auf bereits bestehende Dateien benötigt den ausdrücklich am Gerät
   bestätigten Scope `https://www.googleapis.com/auth/drive`; eine reine
   `drive.file`-Freigabe deckt fremd erstellte Bestandsdateien nicht automatisch ab.
   Googles Prüfung eingeschränkter Scopes und Organisationsregeln können die
   Verbindung verhindern. Keine dieser externen Freigaben wurde hier erstellt.
4. Nach zulässiger Installation in Drive-Abgleich → Google Drive verbinden das
   bestehende Konto und denselben Ordner auswählen. Anmeldung/Ordnerinhalt werden
   zuerst gelesen und geprüft; erst anschließend wird die neue Verbindung aktiviert.
5. Erste echte Synchronisation und Medien prüfen; bestehende Konflikte einzeln
   anhand beider Versionen entscheiden. Eine Queue wird nicht pauschal geleert.
   Neue MCP-Gesundheit/Capabilities prüfen; bei Connector-Cache neu verbinden.
   Die 66 Werkzeugnamen bleiben erhalten. Private Tunnel-/Organisationsrechte
   bleiben eine eigenständige, extern zu bestätigende Autorisierungsgrenze.

Diese Cloud erlaubt derzeit keinen allgemeinen direkten Google-API-Egress.
Es wurden keine Netzwerkregeln umgangen und keine neue Google-Verbindung behauptet.
Die neue Android-Anbindung benutzt nach Gerätefreigabe dessen eigenen Netzwerkzugang.
Akku-/Hintergrundbeschränkungen und Netzverfügbarkeit begrenzen Abschlusszeiten.

Grenzen: 20 MiB je Foto/PDF, 40 MiB/zehn Fotos je Auswahl, 64 MiB je JSON-Datei,
20.000 Dateien je gelistetem Ordner, 256-KiB-Drive-Uploadabschnitte; MCP-Dateiblöcke
bleiben auf 48 KiB begrenzt. Überschreitungen, fehlende Originale und beschädigte
Historien werden sichtbar angehalten. Historienbereinigung und Anbieterrechte
werden nicht durch ungeprüfte Löschung ersetzt. Keine ununterbrochene
Verfügbarkeit oder erfolgreiche Geräteaktualisierung zugesichert.
