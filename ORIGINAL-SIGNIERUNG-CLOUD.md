# Originalsignierung im erhaltenen Cloud-Stand

Geltungsbereich: ausschließlich der vorhandene Checkout von
`mihalhafner-collab/Gartenpirat-` in der ausgewählten **persönlichen Gartenpirat-
Codex-Cloud-Umgebung**. Keine Desktop-Code-/App-Dateien oder Desktop-Commits
übernehmen. Die gesondert beauftragte Bereitstellung des vorhandenen
Originalschlüssels erfolgt ausschließlich über geschützte persönliche Secrets;
sie ist kein Codeimport und kein Auftrag für Desktop-Builds.
Die Zuordnung zum persönlichen Kontext erfolgt über die Bereitstellung der Werte
nur für diese Umgebung; der Befehl attestiert keine Vault-/Umgebungsidentität.
Er verweigert zusätzlich erkannte CI-Kontexte.

## Nicht geheime operative Baseline

`android/original-signing-baseline.json` enthält ausschließlich:

- Paket: `at.hausgarten.service`
- operative Ausgangsversion: `1.45.0`
- operativer Ausgangs-versionCode: `64`
- erwarteter Zertifikats-Variablenname: `HG_ORIGINAL_CERT_SHA256`

Kein Fingerprint wird neu hartcodiert. Frühere historische Signierungsberichte
ersetzen den verifizierten Wert in dieser Variablen nicht.

## Persönliche Konfiguration

Alle fünf Werte müssen in der persönlichen Cloud-Konfiguration für genau diese
Gartenpirat-Umgebung angefordert und bereitgestellt werden:

| Variablenname | Verwendung |
| --- | --- |
| `HG_ORIGINAL_KEYSTORE_BASE64` | Unveränderte Originaldatei, kanonisches Base64 in einer Zeile |
| `HG_ORIGINAL_STORE_PASSWORD` | Passwort des vorhandenen Keystores |
| `HG_ORIGINAL_KEY_ALIAS` | Alias des vorhandenen Originalschlüssels |
| `HG_ORIGINAL_KEY_PASSWORD` | Passwort dieses vorhandenen Schlüssels |
| `HG_ORIGINAL_CERT_SHA256` | Verifizierter Original-Zertifikat-Fingerprint, 64 Hexzeichen oder 32 durch Doppelpunkte getrennte Hexpaare |

Keine privaten Schlüssel-, Alias- oder Passwortwerte in Anweisungen,
Terminalausgaben, Reports, Git oder CI speichern. Der normalisierte öffentliche
Zertifikat-Fingerprint ist ausschließlich als geprüftes Ergebnis-Metadatum erlaubt.
Keystore-Dateien dürfen nur temporär im privaten OS-Temp dieser Umgebung genutzt
werden. Der Originalschlüssel wird nicht erzeugt, ersetzt oder verändert.
Passwörter und Base64 dürfen keine Prozessargumente sein.

Codex Personal Vault macht Werte **nicht automatisch in GitHub Actions verfügbar**.
Dieser Auftrag richtet keine GitHub Secrets, keinen Actions-Signierungsschritt
und keine andere Cloud-/CI-Umgebung ein. Bestehende QA-Werkzeuge sind weder
Fallback noch Bestandteil des neuen Befehls.

## Abnahmekriterien und Befehl

1. Fehlende, leere oder ungültige Konfiguration stoppt vor Werkzeugaufrufen und
   temporären Dateien. Base64 wird streng und kanonisch validiert.
2. Ein privater OS-Temp-Ordner außerhalb des Repositorys hat Modus `0700`, die
   temporäre Keystore-Datei `0600`. Ein ins Repository umgebogenes Temp-Ziel
   wird abgewiesen. Der Befehl benötigt die POSIX-Cloud-Umgebung.
3. Eine feste private Kopie des Kandidaten wird als ZIP/Android-Paket geprüft;
   Paket exakt `at.hausgarten.service`, versionCode strikt größer als `64`.
4. `keytool -exportcert` prüft Keystore/Alias/Store-Passwort. Ausschließlich der
   SHA-256-Fingerprint des ausgegebenen Zertifikats wird mit dem normalisierten
   Wert aus `HG_ORIGINAL_CERT_SHA256` verglichen. Bei Abweichung wird kein
   privater Signieraufruf ausgeführt.
5. `keytool -certreq` prüft die Entsperrbarkeit des bestehenden privaten
   Schlüssels; die öffentliche CSR wird verworfen. Es wird kein Schlüssel erzeugt.
   Passwörter werden über `-storepass:env` / `-keypass:env` übergeben.
6. Nach Ausrichtung und Signierung prüft `apksigner verify --verbose --print-certs`
   die kryptografische Gültigkeit, genau einen Signer und dessen exaktes
   Originalzertifikat. Paket, versionName und versionCode müssen erhalten bleiben.
   `zipalign` prüft außerdem die Ausrichtung. Andere Werkzeuge erhalten keine der
   fünf privaten Variablen; Signierwerkzeuge nur die nötigen Passwortvariablen.
7. Cleanup erfolgt im `finally`-Pfad bei Erfolg und Fehler: zunächst Entfernung
   der privaten Schlüsseldatei, danach Entfernung des gesamten privaten
   Arbeitsordners. Beide Entfernungen werden auch beim Fehler der jeweils anderen
   versucht. Jeder Cleanup-Fehler führt zum Abbruch mit festem, geheimwertfreiem
   Hinweis; keine signierte Ausgabedatei wird veröffentlicht.
8. Erst nach allen Prüfungen und erfolgreichem Cleanup wird die verifizierte APK
   an einem neuen Ziel mit exklusiver Dateierstellung ausgegeben. Bestehende
   Dateien/Symlinks bleiben erhalten. Fehlerhafte Teilausgaben werden entfernt.
9. Toolausgaben und Exceptions werden niemals ungefiltert weitergereicht.
   Erfolgs-JSON enthält nur Paket, versionName/versionCode, normalisierten
   Zertifikat-Fingerprint, Ergebnis und APK-SHA256. Keine Schlüsselwerte,
   Passwörter, Aliaswerte oder Pfade in Reports.

Beispiel **nach** persönlicher Konfiguration; die fünf Werte werden weder hier
eingetragen noch auf der Kommandozeile übergeben:

```sh
python3 tools/sign_original_apk.py \
  --apk android/app/build/outputs/apk/release/app-release-unsigned.apk \
  --output /tmp/gartenpirat-original-candidate.apk \
  --keytool "$JAVA_HOME/bin/keytool" \
  --aapt "$ANDROID_HOME/build-tools/37.0.0/aapt" \
  --zipalign "$ANDROID_HOME/build-tools/37.0.0/zipalign" \
  --apksigner "$ANDROID_HOME/build-tools/37.0.0/apksigner"
```

Das Ziel darf noch nicht existieren. Der Befehl führt keine Installation, keinen
Pixel-Zugriff und keinen Update-/Datenerhalt-Test aus. Auch bei erfolgreicher
Signierung bleibt die Installation bis zu Sicherung und ausdrücklicher Freigabe
gesperrt. Geräteabnahme und Update mit Datenerhalt müssen separat nachgewiesen
werden.

## Tests und Nachweis

`python3 -m unittest discover -s tests -p test_original_signing.py`

Diese Tests nutzen ausschließlich synthetische Nicht-Schlüsselbytes, ungültige
Android-ZIP-Platzhalter und gemockte JDK-/Android-Werkzeuge. Kein echtes APK,
kein echter Schlüssel, keine tatsächliche kryptografische Signierung und keine
produktiven Daten werden für diese Tests verwendet. Sie prüfen Konfiguration,
Fehlerpfade, Identitätsregeln, Geheimhaltung, Dateirechte und Cleanup.

Die ältere Suite `test_qa_signing.py` erzeugt echte temporäre Testschlüssel.
Sie bleibt unverändert erhalten und wird in diesem Auftrag gemäß der Vorgabe
„keine echten Schlüssel erzeugen“ nicht ausgeführt. Andere bestehende
Regressionen, Build und Artefakt-Datenschutzprüfung werden separat ausgeführt;
der Build bleibt ohne Originalwerte unsigniert.

### Tatsächlich ausgeführte Cloud-Prüfungen am 18.10.2026

- Ausgang: Branch `work`, HEAD `28504c97278a7deebdfdbf0d9d5eeb57c5c319e4`.
  Der direkt darauf angelegte Branch
  `codex/preserve-cloud-28504c9-20261018` wurde ohne Force gepusht und über
  Remote-Ref/Fetch geprüft; `40a5beb` und `28504c9` sind darüber erreichbar.
  Die Implementierung liegt auf `codex/original-signing-cloud-setup-20261018`.
- Neue Signierungssuite: 38 Tests erfolgreich. Gemeinsamer Python-Lauf:
  225 Tests erfolgreich, keine übersprungenen Tests. Die 37 Tests der alten
  `test_qa_signing.py` wurden vor diesem Lauf ausdrücklich ausgeschlossen, weil
  sie reale temporäre Testschlüssel erzeugen. Nach einer Präzisierung der neuen
  Testfälle wurde die neue Suite erneut erfolgreich ausgeführt.
- Bestehende Java-Regressionen: zehn Suiten, 284 Fälle erfolgreich.
- Offline-Release-Build und Release-Lint erfolgreich; null Lint-Fehler,
  neun bestehende Warnungen. Keine Änderung am Anwendungscode oder Katalog.
- Die tatsächlich gebaute **unsignierte** APK hat Paket `at.hausgarten.service`,
  versionName `1.46.1`, versionCode `66` und SHA-256
  `6ffc9cbf2a3a2963c6bc254a07197fbeb7fb8bfa57a5746c01194b23bfce14d1`.
  APK-Datenschutzprüfung erfolgreich; der Artefaktvergleich bestätigt die
  Übereinstimmung der geprüften Assets mit der gemeinsamen Quelle. Eine
  Geräteprüfung von Navigation, Bildern oder Offline-Verhalten ersetzt das nicht.
- Alle fünf Variablen waren nicht vorhanden. Der echte CLI-Aufruf mit fehlender
  Konfiguration wurde geprüft: Abbruch vor Werkzeugaufrufen. Eine echte
  Zertifikatsprüfung oder Originalsignierung wurde nicht ausgeführt.
- Dateiscan einschließlich Build-Artefakten: keine Keystore-/Privatschlüsseldatei,
  keine erkannten Privatschlüsselheader oder eingebetteten Schlüsseldateien;
  keine zurückgebliebenen temporären Signierungsordner. Da die fünf Variablen
  fehlen, ist ein Vergleich gegen deren tatsächliche Geheimwerte nicht möglich.
  Die synthetischen Tests prüfen die Unterdrückung von Geheimwerten in
  stdout, stderr, Exceptions und Reports.
- Keine Installation, kein Pixel-Zugriff, kein Update-/Datenerhalt-Test,
  keine Entfernung älterer Tests oder Parallelcode-Dateien und kein Merge in main.
