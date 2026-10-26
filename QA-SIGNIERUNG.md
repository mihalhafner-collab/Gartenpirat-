# Historischer QA-Signierungsweg – seit 18. Oktober 2026 deaktiviert

Der aktive Workflow baut den unsignierten Kandidaten der bestehenden operativen
App. Die `.qa`-Variante und die Übernahme von QA-Schlüsseln in Gradle sind entfernt;
Debug-Varianten sind deaktiviert. `qa_signing.py` und seine synthetischen
Sicherheitsregressionen bleiben als historische Diagnosewerkzeuge erhalten und
sind kein produktiver Signierungs- oder Updateweg. Die nachstehende Anleitung
beschreibt den früheren Stand und darf nicht für das operative Update verwendet
werden. [Heutiger Stand](CODEX-STATUS.md), [Originalsignierung](ORIGINAL-SIGNIERUNG-CLOUD.md).

# Dauerhafte Signierung der QA-App

## Umfang und Aktivierungsstand

Basis: `ce67213e4d5d4cac0fbb201f8a3b66591baa1a41`, bestehender Zweig
`codex/v1.46-functional-optimization-20261016`, Version 1.46.0 / Build 65.
Nur die separate QA-App `at.hausgarten.service.qa` ist betroffen. Die produktive
Application ID, ihre Signierung, lokale Debug-Schluessel, Versionsnummern,
Datenbanken, Kundendaten und Synchronisierung bleiben unveraendert.

Der Quellcode verwendet fuer den manuellen QA-Workflow jetzt einen festen,
extern gespeicherten Schluessel. Die fruehere Erzeugung eines neuen
`debug.keystore` in jedem Workflow-Lauf ist entfernt.

**Die einmalige Einrichtung der Repository-Secrets ist noch erforderlich.**
In dieser Aenderung wurde kein dauerhafter Benutzer-Schluessel erzeugt oder
hochgeladen. Der verwendete ChatGPT-GitHub-Connector kann Repository-Code
schreiben, aber keine Repository-Secrets verwalten. Kein neuer Build wurde
angestossen, keine APK erstellt und keine App auf dem Pixel veraendert.

## Abnahmekriterien

Zwei getrennte QA-Builds muessen denselben Schluessel und dasselbe
Signaturzertifikat verwenden. Fehlende/ungueltige Secrets, ein falsches Passwort
oder ein abweichender Zertifikatsfingerabdruck muessen den Build stoppen;
es darf keinen Rueckfall auf einen neu generierten Schluessel geben.
Die fertige APK muss kryptografisch verifiziert und ausschliesslich der
QA-Application-ID zugeordnet sein. Private Schluessel duerfen weder in Git,
APKs, Artefakten, Build-Caches noch in Protokollen landen. Ein praktischer
Update-Test mit Datenerhalt ist gesondert auf dem Pixel nachzuweisen.

## Einmalige Einrichtung auf dem eigenen Rechner

Voraussetzungen: aktuelle Arbeitskopie dieses bestehenden Entwicklungszweigs,
Python 3.9 oder neuer (unter Windows zum Beispiel Python 3.11), JDK 17 mit
`keytool` im PATH und GitHub CLI `gh`, angemeldet als berechtigter
Repository-Inhaber. Keine ungesicherten lokalen Codex-Aenderungen ueberschreiben
und keine neue Arbeitskopie oder Branch auf Verdacht anlegen.

Im aktualisierten Projektordner unter Windows:

```powershell
gh auth login --hostname github.com
py -3.11 qa_signing.py setup --publish
```

Bei bereits bestehender passender `gh`-Anmeldung ist der erste Befehl nicht
noetig. Unter Linux/macOS ersetzt `python3` das Windows-Praefix `py -3.11`.
Die Anmeldung erfolgt direkt bei GitHub, niemals durch Einfuegen eines Tokens
oder Passworts in den Chat.

Das Programm erzeugt EINMALIG einen eigenen QA-Schluessel (RSA 3072,
SHA256withRSA, PKCS12, Alias `gartenpirat-qa`, Gueltigkeit 10000 Tage)
und ein zufaelliges starkes Passwort. Es verwendet niemals den vorhandenen
produktiven Debug-/Release-Schluessel.

Dauerhafte lokale Ablage:

```text
Windows: %USERPROFILE%\.gartenpirat-signing\qa\
Linux/macOS: ~/.gartenpirat-signing/qa/

qa-signing.p12
qa-signing-password.txt
certificate-sha256.txt
```

Das Verzeichnis liegt ausserhalb des Repositories. Dateien werden unter POSIX
mit nur fuer den Benutzer geltenden Zugriffsrechten angelegt. Unter Windows
gelten zusaetzlich die Zugriffsrechte des Benutzerprofils. Keystore und Passwort
getrennt bzw. gemeinsam verschluesselt in einer verlaesslichen privaten
Sicherung aufbewahren. Nicht in Git, APKs, Testberichte oder Chats kopieren.
Der Zertifikatsfingerabdruck ist oeffentlich teilbar; der private Schluessel nicht.

Mit `--publish` werden ausschliesslich diese drei Secrets im bestehenden
Repository `mihalhafner-collab/Gartenpirat-` angelegt:

- `QA_KEYSTORE_BASE64`: Base64-kodierter privater PKCS12-Keystore.
- `QA_KEYSTORE_PASSWORD`: Passwort fuer Keystore und privaten Schluessel.
- `QA_CERT_SHA256`: SHA-256-Fingerabdruck des zugehoerigen Zertifikats.

GitHub CLI erhaelt Werte ueber STDIN und verschluesselt sie fuer GitHub.
Base64 allein ist KEINE Verschluesselung. Der lokale Helfer prueft vor dem
Upload, ob QA-Secrets existieren, und ueberschreibt sie nicht automatisch.
Eine bereits vollstaendige lokale Schluesselablage wird wiederverwendet,
niemals stillschweigend rotiert. Einrichtung nicht parallel starten.

## Verhalten bei abgebrochener Ersteinrichtung

Die drei GitHub-Schreiboperationen sind nicht atomar. Der Fingerabdruck wird
zuletzt hochgeladen, sodass eine unterbrochene Ersteinrichtung im QA-Build
geschlossen fehlschlaegt. Bei teilweise angelegten Secrets stoppt der Helfer,
bevor er weitere Werte ueberschreibt. Lokales Schluessel-Backup unbedingt erhalten.

Vor einer Reparatur feststellen, ob nur die Ersteinrichtung unterbrochen wurde
oder schon verwendete Signierdaten existieren. Nicht auf Verdacht neue Schluessel
erzeugen, vorhandene Secrets loeschen oder eine andere Identitaet einspielen.
Der Inhaber kann nach dieser Pruefung die drei Secretwerte aus demselben
verifizierten lokalen Backup ueber die GitHub-Secretverwaltung vervollstaendigen.
Eine teilweise lokale Ablage wird ebenfalls nicht automatisch ueberschrieben.

## Ablauf der folgenden QA-Builds

Der vorhandene Workflow bleibt ausschliesslich manuell ausloesbar und erhaelt
weiter nur `contents: read`. Vor teuren Build-Schritten werden die drei Secrets
auf Vorhandensein und Format geprueft. Bestehende Kamera- und Privacy-Pruefungen
bleiben erhalten; die Signierregressionen verwenden nur synthetische Testschluessel.

Unmittelbar vor dem Build wird der feste Keystore in ein eigenes Verzeichnis
unter `RUNNER_TEMP` geschrieben. `keytool` prueft Zertifikat und entsperrbaren
privaten Schluessel. Das Zertifikat muss zu `QA_CERT_SHA256` passen.
Gradle erhaelt den privaten Pfad und das Passwort nur in diesem Schritt ueber
die Umgebung und wird mit `-PqaPersistentSigning` ausgefuehrt. Ohne gueltige
Konfiguration stoppt Gradle. Der bisherige lokale Debug-Build ohne diesen
Parameter bleibt unveraendert; er ist nicht mit der stabilen CI-QA-Signierung
gleichzusetzen und darf nicht ungeprueft als deren Update verteilt werden.

Gradle-Cache-Uploads sowie Build-/Konfigurationscache sind im signierten
QA-Workflow deaktiviert. Nach dem Build verifiziert `apksigner` die APK und
das einzelne Signaturzertifikat; `aapt` prueft die QA-Paketkennung.
Anschliessend folgen APK-Privacy-Pruefung und Bereinigung des temporaeren
Keystores, auch bei Fehlern. Ausschliesslich `app-debug.apk` wird als Artefakt
hochgeladen, niemals das Signierverzeichnis. Kein automatisches Deployment.

## Bereits installierte alte Test-App

Ein neuer fester Schluessel stellt die verlorene Identitaet eines frueheren,
einmaligen CI-Schluessels nicht wieder her. Eine solche alte QA-Installation
laesst sich mit einem anderen Zertifikat nicht einfach als normales Update
erhalten. Vor jeder Installation Zertifikate vergleichen und pruefen, ob
lokale Testdaten gesichert werden muessen. Keine produktive App und keine
Test-App automatisiert deinstallieren, keine App-Daten loeschen.
Der erste Wechsel auf die stabile QA-Identitaet erfordert eine gesonderte,
bewusste Entscheidung; nachfolgende Updates muessen dieselbe Identitaet behalten.

## Tatsaechlich ausgefuehrte Pruefungen

```sh
python3 -m unittest discover -s tests -p 'test_qa_signing.py' -v
```

Lokal **37 Tests erfolgreich** auf Python 3.13 / OpenJDK 21. Die CI-Konfiguration
verwendet weiterhin JDK 17; ein CI-Lauf dieses Patches ist noch nicht ausgefuehrt.
Echte PKCS12-Erzeugung, Entsperrung, Zertifikatsvergleich und JAR-Signierung
wurden mit temporaeren synthetischen Schluesseln ausgefuehrt. Zwei getrennte
Wiederherstellungen lieferten identische Keystore-Bytes und Zertifikate.
Geprueft wurden ausserdem Fehlerfaelle, kein Ueberschreiben bestehender
Schluessel, Secret-Uebergabe via STDIN, partielle Einrichtung, eng begrenzte
Bereinigung sowie die Erweiterung des Privacy-Scanners fuer Signierdateien.

GitHub-Schreibzugriffe und `apksigner`-/`aapt`-Ausgaben sind in diesen Tests
explizit simuliert; dies ist kein echter GitHub-Upload oder APK-Signaturtest.
Die neue Workflow-YAML wurde lokal geparst und auf manuelle Ausloesung,
minimale Berechtigungen, begrenzte Secret-Sichtbarkeit und APK-only-Upload
geprueft. Durch Ruecknahme ausschliesslich der beabsichtigten Aenderungen
wurden die urspruenglichen Git-Blob-Hashes von Gradle-Datei, Privacy-Scanner
und `.gitignore` reproduziert; keine unbeabsichtigten Nebenbearbeitungen.

Nicht ausgefuehrt: vollstaendiger Android-/Gradle-Build, gesamte Projekt-Testsuite,
Android-Lint, echter APK-Privacy-Lauf, CI-Secrets-Einrichtung, Emulator-/Pixel-
Installation und In-place-Update mit Datenerhalt. Kein produktiver Release
freigegeben. Die Signaturkorrektur erweitert nicht die Fotozuordnung oder Kamera.

## Fachliche Referenzen

- Android App Signing: https://developer.android.com/studio/publish/app-signing
- GitHub Actions Secrets: https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets
- GitHub CLI Secret Set: https://cli.github.com/manual/gh_secret_set
