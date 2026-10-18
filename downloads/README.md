# Aktueller Original-Kandidat 1.48.0 / 68

[APK 1.48.0 herunterladen](Haus-Garten-1.48.0-68-original-update-kandidat.apk?raw=true)

Direkter Drive-Abgleich mit Serverbelegen und Wiederaufnahme, übersichtliche
Kundenakte mit Garteninformationen und erhaltenen Foto-Originalen sowie Wischen
zwischen Pflanzenbildern. Neue Entfernungen archivieren nachvollziehbar.

**Installation bleibt bis Sicherung und ausdrücklicher Freigabe gesperrt.**
Die neue Google-Verbindung benötigt die tatsächlich erteilte Konto-/Projektfreigabe
und Anmeldung am Gerät. Bestehende Betriebsdaten und Warteschlangen wurden nicht verändert.

- Paket `at.hausgarten.service`, Version `1.48.0`, Code `68`.
- Originalzertifikat mit vorhandenem Keystore und Cloud-Kandidat 67 verglichen;
  genau ein Signer, v2/v3 gültig, 16-KiB-Ausrichtung und Cleanup geprüft.
- 253 Python-Tests, 460 JVM-Fälle und 17 ausgewählte Browser-Suiten erfolgreich;
  darin 14 neue Browserprüfungen. Build/Lint bestanden: null Fehler, elf Warnungen.
- Alle 397 App-Assets stimmen mit der Quelle überein; unverändert 181 Pflanzenprofile
  und 362 Bilder. Datenschutzprüfung bestanden; keine operativen Seedzeilen.
- Installierte Pixel-Version/-Signatur, echte Google-Verbindung, native Android-
  Abläufe sowie Update/Datenerhalt sind noch nicht geprüft. Kein freigegebener Release.

APK-SHA256: `3230d5c0d1810718650f2ad31049626ed69f592128e3428a52ed54d8cbedec70`.

[Änderungen, Nachweise und notwendige Freigaben](../docs/SYNC-DRIVE-KUNDENAKTE-1.48.md) ·
[Maschinenlesbarer Prüfnachweis](../docs/verification/sync-customer-gallery-1.48.json) ·
[Prüfsummen](SHA256SUMS)

## Erhaltener vorheriger Stand 1.47.0 / 67

# Aktueller Original-Kandidat 1.47.0 / 67

[APK 1.47.0 herunterladen](Haus-Garten-1.47.0-67-original-update-kandidat.apk?raw=true)

**Nicht zur Installation freigegeben.** Sicherung und ausdrückliche Freigabe
bleiben erforderlich. Keine Deinstallation oder App-Datenlöschung. Zuerst den
kompatiblen Web-/Drive-Importer bereitstellen; neue Funktionen sind noch nicht
auf dem Pixel installiert und nicht gegen Betriebsdaten schreibend getestet.

| Eigenschaft | Tatsächlich geprüfter Wert |
| --- | --- |
| Paket | `at.hausgarten.service` |
| Version / Code | `1.47.0` / `67`, höher als bisheriger Cloud-Kandidat `66` und Baseline `64` |
| Android | minSdk `26`, targetSdk / compileSdk `37` |
| Build | Release, nicht debuggable |
| Originalzertifikat | Übereinstimmung mit konfiguriertem Keystore und vorherigem Cloud-Kandidaten geprüft |
| Signatur | Genau ein Signer; v2 und v3 gültig; Ausrichtung mit 16-KiB-Prüfung bestanden |
| Cleanup | Keine privaten Signierungsordner oder Schlüsseldateien nach den geprüften Dateiendungen zurückgeblieben |

APK-SHA256:
`a41d1a10dc98c652be8089eeca1c4048bf22f450785648ed0e7a467d7963015e`

Geprüfter öffentlicher Zertifikat-SHA256:
`a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`

Alle sieben beschriebenen Fehler/Optimierungen sind im Cloud-Quellcode umgesetzt.
Der private MCP-Umfang umfasst 66 Werkzeuge; externe Konto-/Gerätefreigaben
werden dadurch nicht ersetzt. 244 Python-Tests, 436 JVM-Prüffälle, elf bestehende
Browser-Suiten und acht neue Browserprüfungen erfolgreich. Release-Build und
Lint erfolgreich (null Fehler, neun bestehende Warnungen). Alle 394 App-Assets
stimmen mit der Quelle überein, inklusive unverändertem Katalog mit 181 Profilen
und 362 Bildern. Keine operativen Seedzeilen im Artefakt.

Native Android-Testquellen sind nur kompiliert. Fresh-Install, In-place-Update,
Datenerhalt sowie die derzeit installierte Pixel-Version/-Signatur wurden nicht
geprüft. Eine erfolgreiche Originalsignierung ist kein Update-/Datenerhalt-Test.

[Funktions-/Berechtigungsmatrix und Einführungsgrenzen](../docs/MCP-EIGENTUEMERZUGRIFF-2026-10-18.md) ·
[Öffentlicher Prüfnachweis](../docs/verification/mcp-owner-2026-10-18.json) ·
[Prüfsummen](SHA256SUMS)

## Erhaltener vorheriger Cloud-Kandidat

# Haus & Garten – originalsignierter Update-Kandidat

[APK herunterladen](Haus-Garten-1.46.1-66-original-update-kandidat.apk?raw=true)

| Eigenschaft | Geprüfter Wert |
| --- | --- |
| Vorhandene App / Paket | `at.hausgarten.service` |
| Version | `1.46.1` |
| versionCode | `66`, größer als operative Baseline `64` |
| Android | mindestens API `26`, Ziel API `37` |
| Signatur | konfiguriertes Originalzertifikat, genau ein Signer; v2 und v3 gültig |
| Build | Release, nicht debuggable |

**Noch nicht zur Installation freigegeben.** Zuerst die bestehende App sichern
und die Installation ausdrücklich freigeben. Keine Deinstallation und keine
App-Datenlöschung. Ein tatsächliches In-place-Update mit Datenerhalt und ein
Vergleich mit der derzeit installierten Pixel-Version und deren Zertifikat
wurden nicht ausgeführt. Diese Datei ist ein originalsignierter Update-Kandidat.

APK-SHA256:
`353a9a0c44567e09f2436dcdacecd9b3c20ef6f0da86b96a87b32ce541655249`

Geprüfter öffentlicher Zertifikat-SHA256:
`a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`

[Prüfsummendatei](SHA256SUMS) ·
[Vollständiger öffentlicher Prüfnachweis](../docs/verification/original-signing-2026-10-18.json) ·
[Signierungsdokumentation](../ORIGINAL-SIGNIERUNG-CLOUD.md)

229 Python-Tests und 284 Java-Prüffälle bestanden. Der unsignierte Cloud-Build,
Release-Lint, abschließende Signatur- und Ausrichtungsprüfung sowie die
Datenschutzprüfung der signierten APK waren erfolgreich. Alle 393 App-Assets
stimmen mit der bestehenden gemeinsamen Quelle überein. Diese Host- und
Artefaktprüfungen ersetzen keinen Geräte- oder Datenerhalt-Test.

Die APK wurde ausschließlich im bestehenden persönlichen Cloud-Checkout mit
`tools/sign_original_apk.py` signiert. Originalschlüssel und Zugangswerte werden
nicht nach GitHub oder GitHub Actions übertragen. Diese Bereitstellung übernimmt
die bereits geprüften APK-Bytes unverändert; es findet keine Signierung in CI statt.
