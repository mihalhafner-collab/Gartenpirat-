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
