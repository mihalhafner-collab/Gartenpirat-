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

### Fortsetzung mit bereitgestellter Originalsignierung am 18.10.2026

Öffentlicher, maschinenlesbarer Nachweis:
[`docs/verification/original-signing-2026-10-18.json`](docs/verification/original-signing-2026-10-18.json).

- Vor Beginn: sauberer Arbeitsbaum auf
  `codex/original-signing-cloud-setup-20261018`, HEAD exakt
  `05fdcbec77e7c26a039945cbdfcd09c955859b54`. Alle fünf Variablen vorhanden;
  keine Werte zur Konfigurationsprüfung ausgegeben oder gespeichert.
- Die zunächst vorhandene APK war Version `1.42.0` / Code `61` und wurde wegen
  Unterschreitung der Baseline nicht signiert. Nach Erhalt einer Kopie dieser
  unsignierten APK außerhalb des Repositorys wurde ausschließlich aus dem
  aktuellen Cloud-Stand neu gebaut. Der unsignierte Release-Build und
  Release-Lint liefen offline erfolgreich; null Lint-Fehler, neun Warnungen.
- Der erste Originalsignierungsaufruf verweigerte die Veröffentlichung bei der
  finalen Zertifikatsauswertung. Build-Tools 37 bezeichnen den einzelnen
  v3-Signer als `V3.0 Signer:`; der vorbereitete Parser erkannte nur `Signer #1`.
  Der gemeinsame Parser für Signierung und Kandidatenprüfung unterstützt jetzt
  genau diese beiden Formate und verlangt genau einen Signer und genau eine
  Zertifikatszeile. Zusätzliche, doppelte, unbekannte oder fremde Zertifikate
  führen weiterhin zum Abbruch. Dieser Fehler wurde synthetisch reproduziert;
  der erste Versuch hinterließ weder eine Ausgabe-APK noch einen privaten
  Signierungsordner.
- Anschließend tatsächliche Signierung ausschließlich durch
  `tools/sign_original_apk.py`: Paket `at.hausgarten.service`, versionName
  `1.46.1`, versionCode `66` (größer als operative Baseline `64`), minSdk `26`,
  targetSdk und compileSdk `37`, nicht debuggable. SHA-256 der unsignierten APK:
  `6ffc9cbf2a3a2963c6bc254a07197fbeb7fb8bfa57a5746c01194b23bfce14d1`.
- `keytool -exportcert` bestätigte die Übereinstimmung des vorhandenen
  Keystore-Zertifikats mit dem konfigurierten Original-Fingerprint;
  `keytool -certreq` bestätigte die Entsperrbarkeit des vorhandenen Schlüssels.
  Der tatsächlich verifizierte öffentliche Zertifikat-SHA256 ist
  `a939a34e7c3a17a94c344f145583e2719d1e6b8c6ad43ce331f756363f37bff8`.
  Der Nachweis beruht auf der tatsächlichen Prüfung, nicht auf früheren Berichten.
- `apksigner verify --verbose --print-certs` sowohl vor Veröffentlichung als
  auch erneut an der ausgegebenen APK erfolgreich: genau ein Signer mit diesem
  Zertifikat, v2 und v3 gültig. Für v1, v3.1, v3.2 und v4 meldet die Prüfung
  jeweils `false`.
  Die abschließende `zipalign`-Prüfung mit 16-KiB-Seitenausrichtung bestand.
  SHA-256 der signierten APK:
  `353a9a0c44567e09f2436dcdacecd9b3c20ef6f0da86b96a87b32ce541655249`.
- Finale Artefaktprüfung: alle 393 App-Assets bytegleich zur Quelle, 181 Profile
  und 362 Pflanzenbilder vorhanden, erforderliche operative Klassen vorhanden.
  Source- und APK-Datenschutzprüfung bestanden, null operative Seedzeilen.
  Kein produktiver Datenbestand zum Markerabgleich vorhanden
  (`private_markers_checked=0`).
- Cleanup beider tatsächlichen Versuche erfolgreich. Die erfolgreiche Ausgabe
  erfolgte erst nach dem `finally`-Cleanup; die Ausgabe hat Modus `0600`.
  Unabhängige Nachprüfung: null verbliebene private Signierungsordner im
  OS-Temp und null Keystore-/Privatschlüsseldateien nach den geprüften
  Dateiendungen im Repository. Keine Geheimwerte im Nachweis.
- Nach der Parserkorrektur 229 Python-Tests erfolgreich, keine Skips; darin
  40 synthetische Originalsignierungs- und fünf Kandidatenidentitätstests.
  Die alte QA-Signierungssuite wurde nicht ausgeführt, da sie reale temporäre
  Testschlüssel erzeugt. Zusätzlich zehn Java-Suiten mit 284 Fällen erfolgreich;
  isolierte Host-Prüfungen einschließlich Sync-/Beziehungsregeln, keine
  Android-Speicher- oder Geräteabnahme.
- Keine Änderung am App-Code, kein Desktopimport, kein neuer Commit, kein
  Umschreiben der Historie und keine Änderung an `main`. HEAD bleibt der oben
  geprüfte Commit; die Parser-, Test- und Nachweisänderungen waren zum Abschluss
  dieser Signierungsprüfung uncommitted.
- Keine Installation, kein Pixel-Zugriff und keine Änderung operativer App-Daten.
  Eine Original-APK oder die installierte Pixel-Signatur wurde nicht verglichen.
  Fresh-Install, In-place-Update und Datenerhalt bleiben **nicht getestet**.
  Der Kandidat ist **nicht als Release freigegeben**; Installation bleibt bis
  Sicherung und ausdrücklicher Freigabe gesperrt.

### Beauftragte GitHub-Bereitstellung

Die anschließende Nutzeranweisung zur Bereitstellung einer updatefähigen APK auf
GitHub wird auf derselben Branch umgesetzt. Unter
[`downloads/`](downloads/README.md) liegen die bereits geprüfte, originalsignierte
APK `1.46.1` / Code `66`, ihre SHA256-Prüfsumme und der Verweis auf den öffentlichen
Signierungsnachweis. Die APK-Bytes werden bei der Bereitstellung nicht verändert.
Die zuvor geprüften Parser-, Test- und Dokumentationsänderungen werden zusammen
mit der Download-Datei auf dieser Branch gesichert; `main` bleibt unberührt.

Der direkte Release-API-Zugriff aus dieser Cloud ist gesperrt. Daher erfolgt die
Bereitstellung als Download-Datei auf der bestehenden Branch im vorhandenen
privaten Repository. Es werden keine Originalwerte nach GitHub übertragen und
keine Signierung oder Schlüsselbereitstellung in GitHub Actions eingerichtet.
Der vorhandene manuelle Workflow erzeugt weiterhin nur unsignierte Kandidaten.

Vor dem Upload wurden Datenschutz, Paket, Version, Originalzertifikat, gültige
Signatur und Quellasset-Übereinstimmung erneut an der endgültigen signierten APK
geprüft. Die Installationssperre und die offenen Geräte-/Datenerhalt-Tests bleiben
bestehen. Der ursprüngliche JSON-Nachweis beschreibt den abgeschlossenen
Signierungszeitpunkt vor diesem späteren Bereitstellungs-Commit.
