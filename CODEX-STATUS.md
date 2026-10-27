# Gartenpirat Arbeitsstand

Stand: 27. Oktober 2026. Repository: `mihalhafner-collab/Gartenpirat-`.
Entwicklungsanweisungen und neue Sicherungen ausschließlich in GitHub nach
`AGENTS.md`. Vollständige frühere Berichte sind im festen
[Vorbereinigungsstand a41fc67](https://github.com/mihalhafner-collab/Gartenpirat-/blob/a41fc67f9e2d74fb3912c688b0f57cf7476ec598/CODEX-STATUS.md)
erhalten und gelten nicht als neue Arbeitsanweisung.

## Aktueller Auftrag

GitHub als einzige Entwicklungszentrale; keine Drive-Quellkopien oder externen
Regel-/Statusabrufe. Widersprüchliche aktuelle Anweisungen werden ersetzt,
überholte Dokumente/Windows-Launcher nach Abhängigkeitsprüfung aus dem aktiven
Baum entfernt. Aktuelle Quellen, Installation und offene Gates bleiben getrennt.

**Nutzerklärung27.10.:** APKs ab Revier Solo behalten. Grenze:1.55.0/Build99
(`00974518426e1a2bdfcb07c5eba1223071a49ca3`).16 ältere administrative APKs
(536.600.591Bytes) und elf Versions-Tags88–98 sind zur Entfernung klassifiziert.
Vor Taglöschung werden drei eigenständige Originalarchiv-Commits88–90 durch
einen reinen Historienmerge erhalten; der aktuelle Dateibaum bleibt unverändert.
APKs105/106 und ihre vorhandenen Teile, App-Backups sowie einzigartige
Pflanzenarbeit bleiben erhalten. Keine neue Release-/Migrationsfreigabe.

**Cloud-Starttext offen:** Nutzer hat die sichere ChatGPT-Anmeldung im Cloudbrowser
beauftragt; Anmeldeseite bereitgestellt, gespeichertes Profil noch ausstehend.
Der Umgebungs-Connector ist weiterhin nur lesend. Der fertige Starttext steht
in `AGENTS.md`; eine tatsächliche Veröffentlichung wird getrennt nachgewiesen.

## Quellkandidat und beobachtete Installation

| Zustand | Version | Originalquelle | Nachweisgrenze |
|---|---|---|---|
| Bereitgestellter originalsignierter Kandidat | 1.55.1 / 106 | `source/v1.55.1-build106`, `99ab63afbdfdb9fd32dac70134f9461b1ba15b17` | Hostprüfung bestanden; native UI-/physische Updateabnahme offen |
| Zuletzt vom Pixel gemeldet | 1.55.0 / 105 | `source/v1.55.0-build105`, `4087d8b5b83294d75dba6d9fc87e2d6472d3be28` | Lesender MCP-Readback vom 26.10.; keine Installation durch Codex |

106: DB6, MCP9, Speichervertrag3. Operativer Quellfingerprint
`6da86aad5019a93520c50794b72f53b45b5847b7dd30dfb650f290fe99e22928`.
Original-APK SHA256
`4b6bf5656dadfcb4a6b76baabe661c8d9f75f0a2d8c78431a4354f66a8192e3c`.
Originalquelle, Signierung und Downloadprovenienz bleiben in `source-releases.json`.
Keine neue APK für die aktuelle administrative Bereinigung.

## Tatsächlich geprüfte Software

Die folgenden Resultate gehören zu den genannten Appquellen, nicht zu einem neuen
Testlauf dieses Dokumentationsauftrags:

- 106/99ab63a: 79 JVM-Suiten, 441 Pythonprüfungen, zwei betroffene Backup-
  Browsersuiten, P1, Build/Lint/Privacy und Originalsignatur bestanden.
  166 Werkzeuge und 209 UI-Aktionen im Codevertrag. Lint: 0 Fehler/103 Warnungen.
- 106-Original-APK-Nutzlast aus drei tatsächlich zurückgelesenen Teilen erneut
  gehasht/signaturgeprüft. Die separate große rohe APK-ID war nur durch Metadaten
  und vollständige Streamgröße bestätigt; kein unabhängiger Bytehash dieser ID.
- Native106-UI-/Updateprobe nicht ausgeführt: Cloud-AVD verlor den Paketmanager.
  Kompilierte Tests sind kein nativer PASS. Keine produktive Pixelinstallation.
- 105/4087d8b: gemeinsame native Cloud-Abnahme 443 PASS/0 FAIL, fünf tatsächliche
  OS-Prozesspaare und fünf Flugmodus-UI-Fälle. Cloud-Suchlatenzziel RS-14 nicht
  erreicht; physische Suchleistung nicht gemessen. Kein produktiver Gesamt-PASS.
- 55 synthetische MCP-stdio-Szenarien sind historische Vorgängerevidenz und wurden
  nicht als neuer106-Lauf ausgegeben. Privacy- und Originalsigniergates bleiben.

## Offene produktive Transition

Die source-only Umsetzung106 entfernt produktive Drive-Geschäftstransporte,
Syncauslöser, Remote-Original-Fallbacks und alten Teilrestore; vorhandenes
verschlüsseltes Vollbackup bleibt. Historische lokale Evidenz und Finanzgates
bleiben separat erhalten. Keine neue Appfunktion in der aktuellen Bereinigung.

Der letzte vorherige105-Readback meldete `pixel_local_v2`, Vertrag3,
`backup_only` und `business_sync_allowed=false`. Gleichzeitig:130 ungeklärte
Beziehungsreferenzen,107 nicht lokal nachgewiesene Originale und vier historische
Konflikte. Der produktive Gesamtauftrag bleibt deshalb in der Integritätsklärung;
keine Migration, Queueleerung, Rechnungsfreigabe oder manuelle Freigabeflags.

App-/Serverkatalog damals166 Werkzeuge, gebundene ChatGPT-Verbindung117;
20 Lese-/29 Schreibwerkzeuge fehlen dort. Externer Connector-Refresh offen.
Kalender war verbunden, zeigte aber `CALENDAR_NETWORK`/Retry; kein heutiger
Live-Endnachweis. Aktuelle Bereinigung verwendet keine produktiven Tests.

## Backup und Gerät

106 vereinfacht die vorhandene Einrichtung: Konto/Ordner wählen → Notfallschlüssel
als Datei speichern, zurücklesen und getrennte Aufbewahrung bestätigen → bewusst
Backup einschalten. Keine manuelle Ordner-ID oder erneutes Abtippen nach Read-back.
Restore, Reparatur, USB und Historie bleiben zusätzliche geschützte Abläufe.

Letzte vorherige105-Gerätebeobachtung: Ziel, Schlüssel, Automatik und passender
Kontext noch nicht eingerichtet. Die privaten Sicherungsordner bleiben erhalten.
Der aktuelle Backupordner war beim letzten Bereinigungs-Readback leer. Ein
bereitgestelltes Installationspaket beweist kein vollständiges Betriebsdatenbackup.
Physische Einrichtung, echtes verschlüsseltes Backup/Remote-Hash und Datenerhalt
beim105→106-Update sind weiterhin auf dem Pixel zu prüfen, nicht automatisch ausführen.

## Nachgewiesene Drive-Bereinigung

Vorheriger ausdrücklich beauftragter Schritt:61 Verwaltungsordner und1216
Duplikatdateien mit2.256.778.662Bytes endgültig entfernt.1151 Textpakete,9 direkte
Git-Dateien und52 Teile von elf rekonstruierten vollständigen Quellarchiven
gegen Git geprüft. Vier weitere Review-ZIPs nach Erhalt ihrer eigenständigen
Versionshinweise entfernt.216 Originalobjekte unter denselben privaten IDs erhalten.

Alle Ziel-IDs/Ordner fehlten im Abschluss-Readback.15 unterbrochene Einzelantworten
über ursprüngliche IDs lesend geklärt, keine blinden Wiederholungen. Backupordner,
App/Installationspakete und Betriebsdaten unverändert.36 große Einzelarchive ohne
vollständigen Bytezugriff sowie drei Archive mit eigenständigen alten Quellen/
QA-/Bildnachweisen blieben erhalten. Kein Vollabschluss dieser Altarchive behauptet.

Vollständige IDs/Hashes/Lösch- und Verschiebebelege sowie die vier ursprünglichen
Versionshinweise liegen jetzt selbständig in GitHub:
`docs/verification/drive-cleanup-2026-10-26.json`. Das frühere Drive-Register ist
historische Evidenz und keine heutige Anweisungs-/Sicherungsquelle.

## Repositorystruktur und einzigartige Arbeit

Produktive Quellen, öffentliche Pflanzenassets, Host-Regressionsmodule, Tests und
Legacy-Fixtures bleiben erhalten. Es gibt keinen separaten Quellcodeordner pro
alter Appversion; diese Stände liegen in Git-Commits/Referenzen. Gespiegelte
Pflanzenassets sind benötigte Laufzeitpfade, keine entbehrlichen Altkopien.

Fünf ältere Arbeitsbranches sind nach frischem Remotevergleich vollständig in
`main` enthalten (jeweils null einzigartige Commits) und wurden atomar gelöscht.
Remote-Read-back: alle fünf Referenzen fehlen, `main`, aktueller Arbeitsbranch
und Pflanzenbranch unverändert. Namen/Original-SHAs waren vorher in GitHub-Commit
`3f9e41ceb99b44ee1cc8f1f1fd1cad79e6e232dc` gesichert; die vollständige Historie
bleibt erhalten. Beim damaligen Read-back waren alle13 Versions-Tags unverändert;
die jetzt geklärte Bereinigung steht oben. Branch `codex/pflanzenbibliothek-20261023` dagegen hat sieben
nicht integrierte Commits, letzter Stand
`7d499b225ed28f501333227e0ce92a54d4c5d542`. Öffentliche Pflanzenquellen und
redaktionelle Nachweise darin nicht als erledigte Kopie löschen oder ungeprüft
integrieren. Aktueller Arbeitsbranch bleibt `codex/revier-solo-20261023`.

## Validierung dieser Konsolidierung

48 Source-/Lifecycle-/Privacy-Regressionen,18 Transfer-Metadatenregressionen und
45 Originalsignier-/Installationsbaseline-Prüfungen bestanden:111 PASS/0 FAIL.
Source-Privacy:2325 Dateien/0 operative Seedzeilen PASS. Quellworktree-Privacy:
6002 Dateien einschließlich ignored/untracked PASS. Aktuelle Markdownlinks und
Installation-/Quelltrennung geprüft; alte CLI-Einstiege weisen vor Dateizugriff ab.

26 überholte Dokumente/Windows-Launcher aus dem aktiven Baum entfernt; Originale
mit Pfad/Hash/festem Git-Commit im Strukturprüfbeleg nachgewiesen. Die drei
Administrationsautomationen auf reine GitHub-Bootstraps umgestellt und zurückgelesen;
Zeitpläne/Aktivzustände unverändert (täglich pausiert, wöchentlich/monatlich aktiv).
Drei ältere technische Vertrags-/Abnahmeprotokolle und der Integrationsbericht
sind ausdrücklich historisch gekennzeichnet; die aktiven Signierschutzgates bleiben.

GitHub-Zwischensicherung `0050d1089af6866d12ec0fc8e89662a4929438a9`
exakt zurückgelesen. PR #7 ohne History-Rewrite nach `main` integriert:
`6092fa1d40b295453df28ef9e9300f94a60d7e33`, Remote-Read-back bestanden.
Die Android-Appdateien entsprechen unverändert dem Originalquelltag106.
Keine neue App-Abnahme oder veröffentlichte Umgebungskonfiguration daraus ableiten.

## Aufbewahrungsprüfung27.10.

51 Source-/Lifecycle-/Privacy-Regressionen und45 Originalsignierbaseline-Prüfungen
bestanden:96 PASS/0 FAIL. Neue synthetische Gegenproben: alte Quellen werden nicht
für aktuelle Quell-Wiederherstellung gewählt, Kandidaten dadurch nicht freigegeben,
fehlende Grenze wird abgewiesen; lokale APKs99 und107 bleiben auch bei registrierter
Retirement-Anfrage erhalten, synthetisches98 wird tatsächlich entfernt.

Quell-Wiederherstellung105/106 tatsächlich aus Git rekonstruiert:2338/2349 Dateien,
Privacy und jeweiliger Originalfingerprint PASS. Kein App-/Betriebsdatenrestore.
Der CLI-Pfad wählt jetzt die aufbewahrten Originalkandidaten statt stillgelegter
88–90-Tags. Keine Appcodeänderung und kein neuer APK-Bau.
Quellprivacy2326 Dateien/0 operative Seedzeilen und Worktree-Privacy6003 Dateien
einschließlich ignored/untracked PASS. Auf GitHub sind keine Releases/Release-Assets
vorhanden; ältere Installationsdateien werden im bestehenden privaten APK-Bereich
bereinigt. Rohbetriebsdaten wurden dafür weder gelesen noch geändert.
