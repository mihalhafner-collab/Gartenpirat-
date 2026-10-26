> Historisches Quell-/Abnahmeprotokoll früherer Versionen; keine heutige
> Arbeitsanweisung oder Freigabe. Damalige „aktuell“-/„verbindlich“-Angaben
> gelten ausschließlich für ihren datierten Abschnitt. Heutige Regeln:
> [AGENTS.md](../AGENTS.md); tatsächlicher Stand und offene Gates:
> [CODEX-STATUS.md](../CODEX-STATUS.md). Veraltete Drive-Geschäftswege
> und alte Auslieferungs-/Laptopabläufe daraus nicht reaktivieren.

# Abnahme: Drive-Abgleich, Kundenakte und Pflanzenbilder

Ausgang: `codex/original-signing-cloud-setup-20261018`, HEAD `5e1ca93`;
Arbeitsbaum vor Beginn sauber, vorgeschriebener Vorfahr `05fdcbe` enthalten.
Auftrag: Umsetzung des am 18.10.2026 besprochenen Konzepts. Keine operativen
Schreibtests, Installation, ADB-Verbindung oder Änderung externer Kontofreigaben.

## Verbindliche Kriterien vor der Umsetzung

1. Jede ausstehende Aktion hat einen nachvollziehbaren Zustand, Ursache und
   nächsten Schritt. Offener Geschäftsauftrag und Übertragungszustand sind getrennt.
   Gleiche Request-ID mit gleichem Inhalt wird genau einmal fachlich wirksam;
   abweichender Inhalt wird zurückgewiesen. Konflikte halten unabhängige Aktionen
   nicht auf; Warteschlangen und ursprüngliche Varianten bleiben erhalten.
2. Direkte Drive-Übertragung bestätigt Serverinhalt und Prüfsummen. Android-
   Provider-Rücklesen bleibt ausdrücklich ohne Cloud-Nachweis. Erst nach
   vollständigem Upload wird eine unveränderliche Veröffentlichung mit Belegen
   sichtbar. Fehlende Rechte, 401, 429, Abbrüche und unvollständige Medien erzeugen
   keinen falschen Erfolg. Wiederaufnahme bleibt dauerhaft nachvollziehbar.
3. Neue Kontoanbindung benötigt eine bewusste Gerätehandlung und tatsächlich
   vorhandene Google-Freigabe; bestehende Ordner/IDs/Journale werden nicht ersetzt.
   Hintergrundausführung überlebt Prozessende über Android-Systemplanung;
   fehlende System-/Netzfreigaben bleiben sichtbar. Keine Dauerverfügbarkeit zusagen.
4. Kundenakte mit Übersicht, Garten, Fotos/Dateien und Verlauf: Gartendaten sind
   optional und versioniert, unbekannte Flächen sind nicht 0. Fotos sind aus der
   Kundenakte erreichbar, Zuordnung ist fest; Originale und gescheiterte Entwürfe
   bleiben erhalten. Große Listen und Vorschauen sind begrenzt.
5. Pflanzen-Vollbild: links/rechts wechseln nur mit klarer horizontaler Geste bei
   100 %, Zoom/Pan/Pinch wechseln kein Bild. Abbruch/Vertikalbewegung/Einzelbild
   bleiben korrekt; Pfeile, Bildzähler, Quellen, Offlinebilder und Zurück erhalten.
6. Isolierte Python-/JVM-/Browserregressionen, Android-Build und Lint prüfen.
   Native Geräte-, reale Google- und Update-/Datenerhalt-Abnahme getrennt ausweisen.
   Ein APK-Kandidat behält Paket/Originalzertifikat und erhöht den versionCode;
   Signierung ausschließlich gemäß ORIGINAL-SIGNIERUNG-CLOUD.md.

## Arbeitsreihenfolge

1. Datenintegrität, Übertragungsbelege, Wiederaufnahme und Verbindung.
2. Übersicht und Kundeninformationen einschließlich Medien.
3. Pflanzenbildgesten, Regressionen, Artefakt und Auslieferungsnachweise.

Ergebnisse und verbleibende externe Voraussetzungen werden nach den tatsächlich
ausgeführten Prüfungen ergänzt. Diese Datei ist zunächst der Abnahmeplan.

## Ergebnis

Die Kriterien sind im Cloud-Quellcode umgesetzt. 253 Python-Tests, 460 JVM-Fälle
und 17 ausgewählte Browser-Suiten bestehen. Darunter 14 neue Browserprüfungen,
einschließlich erzeugter Chromium-Touch-Ereignisse. Release-Build, Lint und
Android-Testquellen-Kompilierung bestehen. Native Android-/Google-/Update-Abnahme
bleibt nicht ausgeführt; keine Betriebsdaten wurden getestet oder verändert.

Details, genaue Nachweisgrenzen und notwendige externe Freigaben:
[`SYNC-DRIVE-KUNDENAKTE-1.48.md`](SYNC-DRIVE-KUNDENAKTE-1.48.md).
