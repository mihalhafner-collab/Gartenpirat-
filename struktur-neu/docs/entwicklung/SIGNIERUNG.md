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
