# Gartenpirat

Einziger funktionaler Android-Quellstand: `struktur-neu/android/app/src/main/`
(Kotlin, Jetpack Compose, lokale SQLite-Datenbank). TEST und PRODUKTIV
entstehen aus demselben fachlichen Quellcode; keine getrennten Implementierungen.

Maßgebliche Projektregeln: [AGENTS.md](AGENTS.md).
Aktueller Stand: [AKTUELLER-STAND.json](struktur-neu/AKTUELLER-STAND.json).
Vor jeder Entwicklung die installierte Pixel-App und ihren Quellfingerabdruck
prüfen. Eine Quelländerung ist nicht automatisch auf dem Pixel installiert.

Lokale Sicherungen entstehen ausschließlich per Android-Dateiauswahl auf dem
Pixel und werden auf Wunsch des Eigentümers außerhalb der App manuell kopiert.
Es gibt keine automatische Geschäftsdatensynchronisation und keine
Sicherungspflicht bei der Umstellung auf lokalen Betrieb.

Die einzige für Google Drive vorgesehene Veröffentlichung ist die ausdrücklich
freigegebene, originalsignierte [Paketinstallation](https://drive.google.com/drive/folders/1ZO05p02n3V9bQABiDLH_bo-vQQAwMbua).
Aktuell installierte Version: 1.57.2 / Build 121. Keine automatische Installation.
