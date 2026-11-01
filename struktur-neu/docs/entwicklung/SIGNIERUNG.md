# Originalidentität erhalten

Paketkennung: `at.hausgarten.service`. Der öffentliche Identitätsvertrag steht in
`android/original-signing-baseline.json`. Der Originalschlüssel bleibt in der
vorhandenen geschützten persönlichen Umgebung; kein Ersatzschlüssel und keine
Schlüssel- oder Passwortwerte im Repository.

`tools/sign_original_apk.py` benötigt die vorhandenen geschützten Variablen
`HG_ORIGINAL_KEYSTORE_BASE64`, `HG_ORIGINAL_STORE_PASSWORD`,
`HG_ORIGINAL_KEY_ALIAS`, `HG_ORIGINAL_KEY_PASSWORD` und `HG_ORIGINAL_CERT_SHA256`.
Werte nicht anzeigen, protokollieren oder als Prozessargumente übergeben.

Die Variablen gehören zur gewählten persönlichen Bauumgebung. Ein neuer
Projektchat übernimmt weder diese Variablen noch Android-Bauwerkzeuge automatisch.
Vor einem APK-Auftrag die tatsächliche Umgebung und die Verfügbarkeit prüfen;
bereits eingerichtete Umgebungen weiterverwenden statt Schlüssel zu übertragen.

Nach einem ausdrücklich beauftragten Bau:

1. Geprüften Quellstand committen, pushen und mit
   `python3 -B tools/source_preservation.py` exakt nachweisen.
2. `python3 -B tools/sign_original_apk.py --help` lesen und die tatsächlich gebaute
   unsignierte Release-APK mit den vorhandenen Android-Werkzeugen signieren.
3. Originalzertifikat, Paket, Version, Assets, leere Startdaten und APK-Prüfsumme
   prüfen. `tests/verify_app_candidate.py` ist dafür das bestehende Prüfwerkzeug.
4. Bereitstellung, Installation und Update mit Datenerhalt benötigen den jeweiligen
   ausdrücklichen Auftrag. Ein früherer Auslieferungsauftrag gilt nicht weiter.

Der Signierer prüft Originalidentität, Datenfreiheit und temporäre Dateien. Die
hier übernommenen Signiertests verwenden ausschließlich synthetische Platzhalter
und gemockte Werkzeuge. Sie signieren keine echte App und erzeugen keine Schlüssel.

## Ausdrücklich beauftragte Test-APK ohne Originalsignatur

Der vorhandene `uiPreview`-Buildtyp verwendet denselben nativen Quellcode mit
Paketkennung `at.hausgarten.service.preview`, Versionssuffix `-test` und dem
sichtbaren Namen „Haus & Garten Test“. Er ist separat installierbar und kann die
Original-App nicht aktualisieren. Der Android-Sandboxbereich ist getrennt;
Startkollektionen bleiben leer. Das kleine variantenspezifische Manifest entfernt
nur für die Test-App die INTERNET-Berechtigung. Externe Maps-Übergaben bleiben
möglich; App-Verbindungen benötigen die reguläre App. Nur synthetische Testdaten
verwenden.

`HG_TEST_KEYSTORE_PATH` wählt einen privaten Testschlüssel außerhalb des Projekts.
Die Gradle-Testsignierung verwendet den Android-Debug-Alias mit den üblichen
Testkennwörtern. Keine Original-Signiervariablen oder Originalschlüssel verwenden.
Den Build gezielt mit `gradle -p android :app:assembleUiPreview` anstoßen. Die
Release-Konfiguration behält ihre Originalkennung und bleibt unsigniert.

Danach `tests/verify_app_candidate.py --ui-preview` und den bestehenden
Artefakt-Datenschutzcheck auf die tatsächlich erzeugte Datei anwenden. Paketname,
Testkennzeichnung, gültige Testsignatur, native Klassen, identische Ressourcen
und leere Startdaten sind überprüfbar; eine Pixel-Abnahme folgt daraus nicht.
