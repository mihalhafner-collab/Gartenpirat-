# Haus & Garten – aktuelle Downloads

Aktueller originalsignierter Kandidat: **[1.54.6 / Build95](https://drive.google.com/file/d/1mNMctVtasftStxWfwZboVv6chkQ_CbXv/view)**. Alle sechs Auditoptimierungen, sichere Testbereinigung, Scroll-/Menüwechsel sowie Planung/Heute umgesetzt. Original94→95 mit Datenerhalt und laufendem Timer,164native Androidprüfungen und echter WebView PASS. Physisches Pixelupdate und produktiver Live-Sync95 bleiben offen.

- [Versionsnachweis](https://drive.google.com/file/d/1XYk1lHZpQ2fyXLr0ZTxg7sidZyGEhzWU/view) und [vollständige QA-Abnahme](https://drive.google.com/file/d/1F_vDQOOnO_W84u879Penqi9VWzcmT_kp/view), tatsächlicher SHA256-Bytevergleich bestanden.
- [Exakt passende Originalquellen](https://drive.google.com/file/d/1Y8xLj_5d08RZjUeoqmM5f2rWY1D24QWc/view), unveränderliches Tag `source/v1.54.6-build95`, Commit `6972780dc9adbe46e0a91f1f77aa990e303fdaf0`.
- [Originalquellordner](https://drive.google.com/drive/folders/1TSWc869NZxFMfEuvrC-aZyGiK1vaGczw):1262Originaldateien,531UTF8/731öffentliche Binärressourcen;19Artefakte tatsächlich zurückgelesen,18SHA256_BYTES+1ZIP COMPLETE_STREAM_SIZE_ONLY.
- [Privater Kandidatenordner](https://drive.google.com/drive/folders/1qV4B9lXj0P35DSsuIzTD9hIMWgx34bef) mit APK und Nachweisen. Abschlussdokumentation separat im selben GitHub-Branch; Appcode/Tag unverändert.

APK-SHA256: `d54e6f50b8cab37088768c79caebf6cea0e5038959aeb85063c5b77b4a8db112`.
App-Fingerprint: `dfc3cc87f18cb152081a56b25d94a682920d07a880800941db0212ba93544565`.
Original-ZIP-SHA256: `269877afd4fd6aefa4135b51318ef9f44e4465d75d49aac7166c736aa6563c2e`.
APK/Original-ZIP vollständig authentifiziert gestreamt und Größe geprüft; wegen32MiB-Materialisierungsgrenze kein behaupteter Remote-SHA-Bytevergleich dieser großen Dateien. Tatsächliche lokale Originalhashes geprüft, Emulator-APK identisch.

Zuletzt per Pixel-MCP beobachtet: **1.54.5/94**, Sync action_required/8pending/0review/cloud_confirmed=false. Keine Installation durch Codex. Nach aktueller Sicherung und Geräteupdate95 expliziten Sync und tatsächlichen Readback prüfen; keine Queue löschen. MCP-Testkundensuche und Kundenarchiv jeweils0; minimale Lösch-/Idempotenzbelege und bestätigte gezielte Löschsicherung geschützt. Keine vollständige Gerätesicherung attestiert.

Vorherige Original94/93 und formale Referenzen89/90 bleiben erhalten; Schutzmengen in `source-lifecycle.json` und `source-releases.json`. Keine Android-Downgradefreigabe. Produktive Installation erst nach Sicherung und Freigabe; physische API34+-Gesten-/Kamera-/Google-Dauerfreigabe bleibt separat offen.

## Historischer Downloadstand94


Aktueller originalsignierter Kandidat: **[1.54.5 / Build 94](https://drive.google.com/file/d/1pwo3kz2shKIyPnXYV0KbL9Szyvya5OS8/view)**. Cloud-Update93→94, Datenerhalt,159 native Tests und echter WebView bestanden. Keine automatische Pixelinstallation; physische API34+/37-Gestenabnahme offen.

- [Versionsnachweis](https://drive.google.com/file/d/1QKvtz_IY9N-vg3WYoOAmdSqpjy20X1lY/view), tatsächlicher SHA256-Bytevergleich bestanden.
- [Exakt passende Originalquellen](https://drive.google.com/file/d/189LoXjhKV9a0GhsnCYpHfsL_60e5wBpL/view), unveränderliches Tag `source/v1.54.5-build94`, Commit `429dfa11c622cd27228a5f572c9c916ccc137e82`.
- [Privater Versionsordner](https://drive.google.com/drive/folders/1Gq89Ru3bCL-FNw60WAHEPLzsoUTsJnnR) mit APK, Original-ZIP und verlustlosen TXT-Teilen.
- [Aktuelle vollständige Dokumentations-/Entwicklungsquelle](https://drive.google.com/drive/folders/1lGyhXJtB_QuCex_R8ObLDxphhaq8SkC-): finaler gesonderter Commit im Manifest, gleicher App-Fingerprint.

APK-SHA256: `975ce32ca7e52338182c0f743044afbd57edeeaa9d376bd31292b1060809cc3c`.
App-Fingerprint: `1d7e7d73b6801705ece1488f79f180c463ba0308083e695583181d9cbc8fab25`.
APK/Original-ZIP vollständig authentifiziert gestreamt und Größe geprüft; wegen32MiB-Materialisierungsgrenze kein behaupteter Remote-SHA-Bytevergleich dieser großen Dateien.

Zuletzt per lesendem Pixel-MCP beobachtet: **1.54.4 / Build93**; keine Installation durch Codex. [Original93](https://drive.google.com/file/d/17Pts6yNjNHiKyRqhJHaqAGarWJPZBnfA/view) und [seine Quellen](https://drive.google.com/file/d/1lB4r1dg6zrB8pvTSXcIJ7s1HDecda3Bd/view) bleiben erhalten. Google-Konto verbunden; Produktion/Dauerfreigabe weiterhin offen.

Bisherige formale Referenzen: [1.54.1/90](https://drive.google.com/file/d/1yp-i450bx9MIrn_t6-O1Srhwe7yGlFSg/view) und [1.54.0/89](https://drive.google.com/file/d/1hHgCQSG64R3jYN3TsdZlbMa0ibxI9OgQ/view). Schutzmengen/Original-Tags stehen in `source-lifecycle.json` und `source-releases.json`; keine Android-Downgradefreigabe. Quell-/Versionsnachweis in `AKTUELLER-STAND.json`; produktive Installation erst nach eigener Sicherung und Freigabe.
