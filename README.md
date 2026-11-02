# Gartenpirat

Produktive Arbeitsbasis: `struktur-neu/`; Arbeitsregeln: `AGENTS.md`.
Vor Arbeiten die aktuelle Pixel-Identität mit dem Quellfingerabdruck abgleichen.
Der bestehende Arbeitsstand steht in `struktur-neu/AKTUELLER-STAND.json`.

Quellprüfung: `python3 -B struktur-neu/tools/check_current.py`.
Bau- und Fachanleitungen stehen unter `struktur-neu/docs/`.

Aktueller separater Testkandidat: **TEST 2.0.0 / Test-Build 2**,
Paket `at.hausgarten.service.fulltest`, Android-Code **118**.

- [Vollständiger, exakt zur APK passender Quellcommit c9d955b](https://github.com/mihalhafner-collab/Gartenpirat-/tree/c9d955bd5775ce300e91c4cea2122838c3fa8f10)
- [TEST-Branch test/2.0.0-build2](https://github.com/mihalhafner-collab/Gartenpirat-/tree/test/2.0.0-build2)
- [TEST-APK](https://drive.google.com/file/d/1AJVvySLiJ3bnm8Fdz7UY-hZlrITcuNhC/view)
- [Unverändertes Quellarchiv](https://drive.google.com/file/d/1Cc3zn2pkqqwBB0QMNBmjBJh4umEk188U/view)

Der TEST-Code liegt in diesem separaten Quellcommit unter `app/`,
die Testvariante unter `app/src/fullTest/`. Vor Arbeiten dessen `AGENTS.md` und
`app-versions.properties` lesen; nicht unter `main/struktur-neu/` nach der TEST-App suchen.
SOURCE_SHA256: `2e830ea9cfc4352471b495efd31523eaba818ee8653c174d5c96b47f298f38f4`.
Die bestehende APK enthält noch den damaligen Basiscommit `d721780` und
`SOURCE_DIRTY=true`; der oben verlinkte Commit sichert nachträglich den gesamten
identischen Archiv-Dateistand. Es wurde keine neue APK erzeugt.

Produktiv- und TEST-Quelle bleiben getrennt. Die Installation von TEST 2 am Pixel
und ein vollständiger Gerätefunktionstest sind nicht bestätigt.
Die maschinenlesbare Zuordnung steht in `current_test_candidate` im bestehenden
`struktur-neu/AKTUELLER-STAND.json`.
