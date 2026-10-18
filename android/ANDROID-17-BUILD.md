# Android 17 als Build- und Testziel

Vorgabe von Michael, geprüft am 10.10.2026: Den nächsten Android-Build auf
Android 17 / API 37 ausrichten. Die bestehende App weiterverwenden; Daten,
IDs, Signatur, Drive-Anbindung und MCP-Verbindungen erhalten.

## Tatsächlich vorhandener Stand am 10.10.2026

- `app/build.gradle`: compileSdk 37, targetSdk 37, minSdk 26.
- Neue APK 1.28.1 / Versionscode 43: Paket und vorhandene Signatur erhalten.
- Android Gradle Plugin 9.1.1, Gradle 9.3.1, JDK 17.
- Installierte stabile Plattform `platforms;android-37.0` (Revision 2)
  und Build-Tools `build-tools;37.0.0`; keine QPR-Beta.
- Gradle-Distribution anhand offizieller SHA256 geprüft.
- Clean Build und Lint erfolgreich; fünf bestehende Warnungen bleiben dokumentiert.
- API 33+ nutzt den nativen OnBackInvokedDispatcher und dieselbe bestehende
  JavaScript-Navigation. Der alte Callback bleibt ausschließlich für API 26–32;
  nur dessen irreführende Lintwarnung ist mit dieser Begründung lokal unterdrückt.
- Hauptansicht und geschützte Einrichtung berücksichtigen Systemleisten,
  Displayausschnitt und Bildschirmtastatur. Dunkle Systemleistensymbole auf hellem Grund.
- Native Netzwerkzugriffe gehen ausschließlich per HTTPS zu OpenAI. Drive wird
  über den bestehenden Android-Dateianbieter genutzt; externe LAN-Links öffnen
  den Browser. Keine neue LAN-Berechtigung eingeführt.
- Update auf realem Pixel 10 Pro mit Android 17 / API 37 installiert.
  Gesamte private App-Daten vor/nach Installation identisch, SQLite-Prüfung ok.
- Der vorhandene API-35-Emulator ersetzt keine Android-17-Gerätetests.

Build und Installation sind tatsächlich erfolgt. Die laufenden bzw. abgeschlossenen
USB-Oberflächentests sind getrennt im Pixel-Prüfbericht dokumentiert. Eine installierte
APK bestätigt noch keine live verbundene ChatGPT-App oder Tunnel-Dauerstabilität.

## Vor dem nächsten Android-17-Build

1. Die stabile API-37-Plattform und passende Build-Werkzeuge installieren.
   Keine QPR-Beta automatisch auswählen. Die zum Zeitpunkt des Builds gültige
   AGP-/Gradle-Kompatibilität anhand offizieller Unterlagen prüfen und nur
   notwendige Werkzeug-Upgrades vornehmen.
2. Compile-Ziel auf API 37 umstellen. Das Target-Ziel ebenfalls auf API 37
   umstellen, sobald die folgenden Verhaltensanpassungen umgesetzt sind.
   minSdk 26 nicht ohne fachlichen Grund anheben.
3. Die bestehende Zurück-Logik von `MainActivity.onBackPressed()` an die
   unterstützte Back-Dispatcher-Schnittstelle anbinden. Ab targetSdk 36 auf
   Android 16 oder neuer wird die alte Methode standardmäßig nicht mehr
   aufgerufen. Dieselbe bestehende `window.handleAndroidBack()`-Logik nutzen:
   jeweils genau einen Schritt zurück, einschließlich Speichern beim Schließen.
4. Systemleisten, Displayausschnitt und Tastatur prüfen. Für targetSdk 36 oder
   neuer ist das bisherige Edge-to-edge-Opt-out nicht mehr wirksam.
5. Lokale Netzwerkzugriffe prüfen: Bei targetSdk 37 gilt die neue
   `ACCESS_LOCAL_NETWORK`-Laufzeitberechtigung. Nur anfordern, wenn die App
   selbst tatsächlich ins LAN zugreift; externe Browseraufrufe und Drive-
   Dateiauswahl nicht unnötig mit einer neuen Berechtigung versehen.
6. Vordergrunddienst, Benachrichtigungen, Neustart, WLAN/Mobilfunk-Wechsel,
   Bildschirm aus, Energiesparen und Wiederverbindung auf Android 17 testen.
   Keine Garantie einer ununterbrochenen Verbindung allein aus dem Build ableiten.
7. Native Foto-/Dateiauswahl, Drive-Freigaben, Rechnungsfreigabe, Spracherkennung,
   Offline-Speicherung, Auto-Save und stabile IDs prüfen; ebenso die bereits
   vorhandenen automatisierten Regressionstests ausführen.
8. Clean Build und Lint durchführen. Vor Auslieferung Versionscode erhöhen,
   dieselbe Signatur und enthaltene Assets prüfen. Die veröffentlichte APK nicht
   allein wegen erfolgreicher Übersetzung als unter Android 17 getestet melden.

## Freigabenachweis

Build-Ziel, Target-Ziel, tatsächlich verwendete Android-Geräteversion,
ausgeführte Tests und offene Gerätetests im Prüfbericht getrennt ausweisen.
Ein Browser-/Host-Test ersetzt weder Android-17-Gesten noch Doze-/Diensttests.
Keine produktiven Daten zum Testen löschen oder neu initialisieren.

## Offizielle Grundlagen

- SDK-Einrichtung: https://developer.android.com/about/versions/17/setup-sdk
- Änderungen für alle Apps: https://developer.android.com/about/versions/17/behavior-changes-all
- Änderungen beim Target 37: https://developer.android.com/about/versions/17/behavior-changes-17
- Weiterhin relevante Back-/Edge-to-edge-Änderungen: https://developer.android.com/about/versions/16/behavior-changes-16
- Verbindliche API-/Werkzeug-Mindestversionen: https://developer.android.com/build/releases/about-agp
- AGP 9.1.1 unterstützt API 37.0: https://developer.android.com/build/releases/agp-9-1-0-release-notes

Die ältere SDK-Einrichtungsseite nennt noch AGP 8.9.0-rc01. Für diese stabile
Plattform wurde die aktuellere Kompatibilitätstabelle verwendet: mindestens AGP 9.1.1.
