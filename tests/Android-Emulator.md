# Androidtests in Codex Cloud

Nur die bestehende Cloudumgebung verwenden. Der operative Pixel ist kein
Testfixture; keine produktiven Daten oder Konten in Emulatoren übernehmen.
Keine Windows-Launcher oder Laptop-Abhängigkeit voraussetzen.

Der vorhandene Einstieg ist `tests/run_cloud_emulator_tests.py`; seine Optionen
vor einem tatsächlich benötigten nativen Lauf lesen. Er verlangt ein bereites
Androidsystem und prüft Installations-/Instrumentierungsergebnisse. Readiness-
Fehler nicht umgehen und kompilierte Proben nicht als ausgeführt ausgeben.

Für Build106 ist die native UI-/Updateabnahme offen: der vorherige Cloud-AVD
verlor den Paketmanager. Kein historischer Emulator-PASS ersetzt diesen fehlenden
Nachweis. Keine automatische Installation auf dem physischen Pixel.

Host-/Browserregressionen benutzen ausschließlich synthetische temporäre Stores;
sie beweisen keine native Android-/Kamera-/Hintergrund-/Datenerhaltabnahme.
Aktuelle Befehle und tatsächlich ausgeführte Prüfungen stehen in
`../README.md`, `../AGENTS.md` und `../CODEX-STATUS.md`.

Historische Windows-Anleitung: [unveränderter Git-Stand](https://github.com/mihalhafner-collab/Gartenpirat-/blob/a41fc67f9e2d74fb3912c688b0f57cf7476ec598/tests/Android-Emulator.md).
