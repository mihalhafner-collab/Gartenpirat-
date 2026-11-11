# Gartenpirat

Einziger funktionaler Android-Quellstand: `struktur-neu/android/app/src/main/`
(Kotlin, Jetpack Compose, lokale SQLite-Datenbank). Es gibt genau ein Paket,
`at.hausgarten.service`; Tests laufen im Emulator oder direkt auf dem Pixel.

**Alleinige Design- und Funktionsvorgabe: [Eisbär](struktur-neu/docs/fachregeln/APP.md).**
Der Umbau gilt erst bei vollständiger Umsetzung und bestandener Eisbär-Abnahme
als abgeschlossen. Frühere widersprechende Konzepte sind ersetzt.

Maßgebliche Projektregeln: [AGENTS.md](AGENTS.md).
Aktueller Stand: [AKTUELLER-STAND.json](struktur-neu/AKTUELLER-STAND.json).
Vor jeder Entwicklung die installierte Pixel-App und ihren Quellfingerabdruck
prüfen. Eine Quelländerung ist nicht automatisch auf dem Pixel installiert.

Lokale Sicherungen entstehen ausschließlich per Android-Dateiauswahl auf dem
Pixel und werden auf Wunsch des Eigentümers außerhalb der App manuell kopiert.
Es gibt keine automatische Geschäftsdatensynchronisation und keine
Sicherungspflicht bei der Umstellung auf lokalen Betrieb.

Google Drive dient ausschließlich der ausdrücklich freigegebenen Verteilung
originalsignierter Installationspakete.
Keine automatische Installation.
