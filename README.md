# Gartenpirat

Einziger aktueller funktionaler Quellbestand: `struktur-neu/android/app/src/main/`
(Kotlin / Jetpack Compose). Das Originalpaket `at.hausgarten.service` bleibt
auf dem Pixel produktiv und unverändert, bis eine eigens freigegebene,
originalsignierte Update-APK die Produktivtests besteht.

Vor der Entwicklung: [AGENTS.md](AGENTS.md), den
[aktuellen Stand](struktur-neu/AKTUELLER-STAND.json) und die lebende
Pixel-MCP-Buildidentität prüfen. Quellprüfung:
`python3 -B struktur-neu/tools/check_current.py`.

**TEST und PRODUKTIV müssen aus dem gleichen Kotlin-Funktionscode gebaut
werden.** Eine neue isolierte TEST-Variante ist erst nach abgeschlossenem
Build, eigenständiger Signatur, Isolationsnachweis und Prüfung gültig.

Der frühere Kandidat TEST 2.0.0 / Test-Build 2 unter
[Commit c9d955b](https://github.com/mihalhafner-collab/Gartenpirat-/tree/c9d955bd5775ce300e91c4cea2122838c3fa8f10)
und Branch `test/2.0.0-build2` basiert auf Java/WebView und ist
**historisch, nicht mehr zur Weiterentwicklung oder Produktivübernahme
zugelassen**. Seine APK und sein Quellarchiv bleiben reine historische
Nachweise; keine behauptete Kotlin-Abnahme.

Der aktuelle Status enthält alle gültigen Kandidaten, offenen Prüfungen
und Grenzen. Keine parallelen Regel- oder Statusfassungen anlegen.
