# Androidtests in Codex Cloud

Cloudtestung bleibt nach Nutzerwunsch bevorzugt, sobald eine belastbar stabile
Konfiguration nachgewiesen ist. Dazu gehören vollständiger Systemstart,
wiederholbare Kaltstarts und relevante tatsächliche UI-/Instrumentierungsproben.
Ein einzelner Boot-Erfolg beweist keine allgemeine Zuverlässigkeit.

Für Emulatorläufe die bestehende Cloudumgebung verwenden. Der operative Pixel
ist kein Testfixture; keine produktiven Daten oder Konten in Emulatoren übernehmen.
Nutzerpräzisierung27.10.: Der Laptop darf ausschließlich ADB zum physischen Gerät
und technische Protokolle übernehmen. Entwicklung und Builds bleiben in der Cloud.
Der Cloudrunner hier akzeptiert weiterhin nur den dedizierten Emulator; eine
physische Testfreigabe oder Erreichbarkeit folgt nicht aus der Laptopausnahme.

Der vorhandene Einstieg ist `tests/run_cloud_emulator_tests.py`; seine Optionen
vor einem tatsächlich benötigten nativen Lauf lesen. Er verlangt ein bereites
Androidsystem und prüft Installations-/Instrumentierungsergebnisse. Readiness-
Fehler nicht umgehen und kompilierte Proben nicht als ausgeführt ausgeben.

Der Bootstatus allein genügt nicht: Nach einem Systemabsturz kann
`sys.boot_completed=1` stehen bleiben, während Paketmanager/Aktivitätsdienst fehlen.
Der Runner verlangt deshalb beide Dienste und einen erfolgreichen lesenden
Paketmanager-Aufruf vor Installations-/Testaktionen. Ein später verschwundener
Kerndienst wird als Androidsystemfehler gemeldet; rohe Geräteausgaben werden
dabei nicht in den Prüfbeleg übernommen. Ergebnis-, Geräte- und Sperrgates bleiben.

Die Diagnose27.10. reproduziert unter API29/x86 mit Emulator37.2.12 und
Softwaregrafik einen nativen SIGSEGV im Androidsystem (`HostConnection::gl2Encoder`,
RenderThread von `system_server`), ohne eine APK zu installieren. Fehlendes KVM
und Softwareemulation sind zusätzliche Umgebungsgrenzen. Hohe RAM-Zähler allein
beweisen keine OOM-Ursache. Kein Crash-/Timeout-Unterdrücken als Reparatur ausgeben.
API30/x86_64 erreicht auch mit zwei CPUs/3GiB, frischem Profil und separatem
Emulator32.1.15 keinen stabilen Vollstart. Im32er-Lauf beendet Androids Watchdog
den Systemprozess während einer blockierten Rollenvergabe. Das ist ein anderer
belegter Fehlerpfad als der29er-Grafikabsturz; keine gemeinsame Ursache behaupten.
Verschlüsselungswarnungen erscheinen auch mit frischem Profil und beweisen keine
beschädigten Altdaten. Der Kreuzvergleich32.1.15 mit AOSP29/x86 läuft noch.
Aktuelle kontrollierte Gegenproben und Grenzen:
[technischer Prüfbeleg](../docs/verification/cloud-emulator-diagnosis-2026-10-27.json).

Für Build106 ist die native UI-/Updateabnahme offen: der vorherige Cloud-AVD
verlor den Paketmanager. Kein historischer Emulator-PASS ersetzt diesen fehlenden
Nachweis. Keine automatische Installation auf dem physischen Pixel.

Host-/Browserregressionen benutzen ausschließlich synthetische temporäre Stores;
sie beweisen keine native Android-/Kamera-/Hintergrund-/Datenerhaltabnahme.
Aktuelle Befehle und tatsächlich ausgeführte Prüfungen stehen in
`../README.md`, `../AGENTS.md` und `../CODEX-STATUS.md`.

Historische Windows-Anleitung: [unveränderter Git-Stand](https://github.com/mihalhafner-collab/Gartenpirat-/blob/a41fc67f9e2d74fb3912c688b0f57cf7476ec598/tests/Android-Emulator.md).
