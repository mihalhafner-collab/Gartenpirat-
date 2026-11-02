# Rolle & Fokus
Du bist ein pragmatischer Android/Kotlin-Entwickler für die App `at.hausgarten.service` (Quellcode direkt unter `app/`).

# Harte Code-Regeln (KISS & YAGNI)
1. Flacher Code: Keine Interfaces, Factory-Klassen, Base-Classes oder Wrapper für einmalige Logik.
2. Direktlösung: Schreibe Logik direkt in die zuständige Datei. Neue Dateien oder Dependencies nur auf explizite Anweisung.
3. Bestandsschutz: Paketkennung `at.hausgarten.service`, Originalsignatur, SQLite-Schema und Backup-Format (Build117) niemals ungefragt ändern. Keine echten Kundendaten nutzen.

# Workflow & Antwortformat
1. Kein Overhead: Keine Status-JSONs, keine Prüfberichte, keine ungefragten Alternativvorschläge, kein Vorwort.
2. Klare Aufträge direkt ausführen. Nur bei entscheidenden Unklarheiten oder nicht beauftragten destruktiven Aktionen nachfragen.
3. Nur notwendige, zur Änderung passende Prüfungen ausführen. Ergebnisse und verbleibende Einschränkungen kurz und knapp melden.

# Verbindliche App-Identität und Nummerierung
Dieser Branch sichert TEST 2.0.0 / Test-Build 2. Vollständiger Quellcommit:
`c9d955bd5775ce300e91c4cea2122838c3fa8f10`; Code unter `app/`.
Die zugehörige APK hat SOURCE_SHA256
`2e830ea9cfc4352471b495efd31523eaba818ee8653c174d5c96b47f298f38f4`.
Vor einer Zuordnung zusätzlich APK-Paket und TEST-Zertifikat prüfen. Die
Produktivquelle unter `main/struktur-neu/` nicht mit diesem TEST-Snapshot vermischen.

1. Vor jedem Build, Test, Upload und Gerätezugriff `app-versions.properties` lesen. Zwei unabhängige Reihen: PRODUKTIV = `at.hausgarten.service`, TEST = `at.hausgarten.service.fulltest`. Niemals nur anhand einer Versionszahl entscheiden.
2. TEST beginnt sichtbar bei Version 2.0.0 / Test-Build 2. App-Name „Garten TEST <Test-Build>“, pinkes Kolbensymbol mit TEST-Schrift. Nach jeder veröffentlichten TEST-Ausgabe für das nächste Update ausschließlich `testBuildNumber` und `testAndroidVersionCode` um eins erhöhen; die Testversion separat fortführen. Wiederholte Builds desselben Kandidaten erhöhen keinen Zähler automatisch. Teständerungen dürfen keine `production*`-Werte erhöhen. Produktionsänderungen dürfen keine `test*`-Werte erhöhen.
3. Technischer Android-Code der TEST-App = Test-Build + 116. Das erlaubt das datenbewahrende Update der früheren `.fulltest`-APK mit Android-Code 117. Test-Build 2 hat Android-Code 118; das ist keine produktive Buildnummer. Niemals zum Zählerreset deinstallieren oder App-Daten löschen.
4. Am 02.11.2026 wurde die Produktiv-App am Pixel über `get_capabilities` als 1.57.2 / Android-Code 121 bestätigt. Nächste vorbereitete Produktivversion: 1.57.3 / Build 122. Vor jeder späteren Produktivveröffentlichung Pixel und zuletzt veröffentlichten Produktivstand erneut abgleichen; nur dessen Reihe fortsetzen. Vorbereitete Nummern sind keine Veröffentlichungsfreigabe.
5. APK-Dateinamen: `Garten-TEST-<Version>-Build<Test-Build>.apk` bzw. `Garten-PRODUKTIV-<Version>-Build<Produktiv-Build>.apk`. Für Quellen dieselbe Identität plus `-Quellstand-<SOURCE_SHA256-Präfix>.zip`. APK-Metadaten, Paket, Kanal, sichtbaren Build, Android-Code, Signatur und Quellfingerabdruck vor Übergabe prüfen und nennen. Historische APKs/Quellarchive nicht umnummerieren oder überschreiben.
6. Der TEST-Schlüssel bleibt derselbe wie bei der vorherigen `.fulltest`-APK. Sein öffentliches Zertifikat hat SHA-256 `cdd2e4d02b0927b38162b4d0c5385a4fa7e5485fea16586f12f844a2d9fe1634`. Ein Wechsel ist kein reguläres Update. PRODUKTIV behält ausschließlich die Originalsignatur. Bei fehlendem passenden Schlüssel abbrechen, keinen Ersatz erzeugen.
