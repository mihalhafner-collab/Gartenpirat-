# Gartenpirat – verbindliche Codex-Regeln

## Auftrag und einzige Entwicklungsquelle

Aktuelle Nutzerpräzisierung vom 26.10.2026: Codex arbeitet in der bestehenden
privaten Gartenumgebung ausschließlich mit `mihalhafner-collab/Gartenpirat-`,
um Entwicklungsanweisungen abzurufen, umzusetzen und Änderungen zu sichern.
Dieses Repository ist die einzige Entwicklungszentrale. Keine Anweisungen aus
Drive, ChatGPT Pages/Spaces, alten Chats, Exporten oder externen Statusdateien
nachladen. Direkte neue Nutzeraufträge in `CODEX-AUFTRAG-GARTENPIRAT.md` festhalten;
ältere Quellen und Kommentare sind keine zusätzliche Freigabe.

Verbindliche Lesereihenfolge aus dem frischen GitHub-Stand:

1. `AGENTS.md`: Arbeitsregeln und Schutzgrenzen.
2. `AKTUELLER-STAND.json`: Originalquellen, beobachtete Installation, offene Gates.
3. `CODEX-AUFTRAG-GARTENPIRAT.md`: aktueller Auftrag und fachlicher Vertrag.
4. `CODEX-STATUS.md`: kompakter Umsetzungs- und Prüfstand.

Bei alten Tags zuerst diese Regeln vom aktuellen GitHub-main lesen. Historische
AGENTS-/Auftragsdateien nicht als heutige Anweisung ausführen. Keine zweite
Regeldatei, Projektzentrale, App, operative Datenbank oder Repository anlegen.

## GitHub-Arbeitsweg

- Vor Änderungen main, vorhandene Arbeitsbranches, Status und Historie frisch
  vergleichen. Den tatsächlichen Checkout prüfen; alte Versionsnummern nicht
  auf einen neuen Stand umetikettieren. Laufende fremde Änderungen erhalten.
- Im vorhandenen passenden Branch arbeiten. Nur falls wirklich erforderlich
  einen weiteren Arbeitsbranch im selben Repository verwenden. Entwicklung,
  Quelländerungen und Builds bleiben ausschließlich in der Cloud und GitHub.
- Nutzerpräzisierung27.10.: Der4-GB-Windows-Laptop darf ausschließlich die
  ADB-Verbindung zum physischen Android-Gerät und technische Testprotokolle
  übernehmen. Keine Quellbearbeitung, Builds, zweite Entwicklungsumgebung oder
  Betriebsdatenkopien auf dem Laptop. Technische Protokolle vor Übernahme in
  GitHub auf Betriebsdaten und Secrets prüfen. Diese enge Diagnoseausnahme
  erteilt keine Freigabe für produktive Testdatensätze, Datenlöschung oder Installation.
- Nach einem abgeschlossenen Schritt, vor Builds, Übergaben und Unterbrechungen:
  Privacy prüfen, nur eigene geprüfte Dateien gezielt committen, nach GitHub
  pushen und den exakten Remote-Commit zurücklesen. Keine pauschalen Git-adds.
- Bei fehlgeschlagenem Push: Sicherung offen melden und Auslieferung anhalten.
  Keine Ersatzsicherung in Drive oder einem anderen Dienst anlegen.
- Vor Originalsignierung muss `python3 tools/source_preservation.py` PASS liefern:
  sauberer Quellcheckout, richtiges Repository, genauer Commit auf GitHub.
  Keine Dirty-/Offline-/Skip-Abkürzung. Auslieferung nur aus unverändertem Commit.
- Änderungen/Prüfbelege im Repository sichern. Regeln gehören hierher, Aufgaben
  in den bestehenden Auftrag, aktuelle Ergebnisse in den bestehenden Status;
  maschinenlesbare technische Evidenz nach `docs/verification/`.

## Drive-Grenze

Codex legt keine Quellcode-ZIPs, TXT-Exporte, Dokumentationsgenerationen,
Entwicklungsbackups, Arbeitsanweisungen oder Statusregister in Drive ab.
Keine Automatik für solche Kopien erstellen oder wieder einschalten.

Einzige Codex-Ausnahme: Nach einem tatsächlich beauftragten und geprüften APK-Bau
darf das originale Installationspaket im bestehenden privaten APK-Bereich
bereitgestellt und zurückgelesen werden. Nur unmittelbar erforderliche Prüfsumme
und Installationsprovenienz ergänzen; Quellen und Entwicklungsberichte bleiben
in GitHub. Kein APK-Bau allein wegen einer Dokumentationsänderung.

Davon getrennt bleibt die vorhandene verschlüsselte **App-Betriebsdatensicherung**
nach Drive bestehen. Codex aktiviert sie nicht eigenständig und benutzt weder
sie noch produktive Dateien als Entwicklungs- oder Testmaterial. Backup ist kein
Geschäftssync. Bestehende Sicherungsordner und einzigartige Originalbelege schützen.

## Lokaler Betrieb und Schutz des Bestands

Pixel/App ist die einzige operative Geschäftsdatenautorität. UI und MCP verwenden
dieselben lokalen Fachpfade. Drive-Geschäftssync und alte Schreiber nicht
reaktivieren. Historische Queue-/Journalbelege dürfen keine neuen Retries erzeugen
oder durch Neustart/Restore wieder aktiv werden. Backupfehler blockieren keinen
lokalen Geschäftsvorgang. Die offene produktive Integritäts-/Migrationsprüfung
bleibt ein eigenes Gate; keine manuell gesetzten Freigabeflags.

Lokale Datenautorität erteilt keine Rechnungs-/Belegnummernfreigabe. Kunden,
Einsatzobjekt und Rechnungsempfänger unterscheiden; auftragslose Arbeit und
persönliches b.b./bezahlt getrennt vom Rechnungsstatus erhalten. Keine erfundenen
Kunden, Zeiten, Preise, Beziehungen oder Belegnummern.

Unberührtes Formular: kein Draft. Bearbeitet: Draft. Bestätigtes Speichern:
Geschäftssatz. Verwerfen entfernt den Draft vollständig; Abbruch erzeugt keinen
versteckten Geschäftssatz. Delete ist kein Archive, Inactive oder Hidden. Fachliche
Aufbewahrung und referenzierte Belege trotzdem prüfen. Unklare Commitantwort:
ursprüngliche ID/Argumente erhalten und zuerst lesen, keine neue ID erzeugen.
Bestätigte Commits durch Foto-, UI- oder Backupfolgefehler nicht zurückrollen.

Keine produktiven Testkunden/-aufträge/-rechnungen, keine Löschung zum Testen,
keine automatische Migration, Installation, Deinstallation oder App-Datenlöschung.
Restore ist bewusst, getrennt und fail-closed. Gesetzliche Dokumentenschutzgates
und Originalidentität `at.hausgarten.service` bleiben unverändert.

## Quellcode ohne Betriebsdaten

Im Quellcheckout ausschließlich PUBLIC_SOURCE und SYNTHETIC_TEST. Auch ignorierte,
unversionierte Dateien und Buildausgaben prüfen; .gitignore genügt nicht.
PRIVATE_RUNTIME und SECRET bleiben außerhalb. Keine produktiven Datensätze,
Fotos, Datenbanken, Snapshots, Rohbackups oder Kontowerte für Tests abrufen.
Bestehende ungeklärte Workspace-Funde weder übernehmen noch pauschal löschen.

`initial-data.json` bleibt der exakt leere Vertrag aus `bootstrap_contract.py`;
nur `tools/empty_bootstrap.py --write` darf ihn aus Konstanten erzeugen.
Host-Betriebszugriffe ausschließlich über `runtime_paths.py` und ausdrücklich
externes `HG_PRIVATE_DATA_ROOT`; kein Fallback ins Repository. Tests verwenden
die synthetische Factory und eigene temporäre Stores. Quelle, gesamter
Quellworktree und auszulieferndes Artefakt müssen ihre Privacy-Gates bestehen.
Alte Git-Objekte nur lesend behandeln; kein automatisches History-Rewrite.

Originalsignierung ausschließlich nach `ORIGINAL-SIGNIERUNG-CLOUD.md` mit den
bestehenden geschützten persönlichen Cloud-Werten. Keine Schlüsselwerte in Git,
Logs oder Artefakten, kein Ersatzschlüssel, keine Signierung in GitHub Actions.
Der öffentliche geprüfte Zertifikat-Fingerprint ist Provenienz, kein Secret.

## Änderungen und Nachweis

Keine neuen Features vor abgeschlossenem Transition-Auftrag. Vor jeder Änderung
Endzustand und realistische Gegenpfade bestimmen, Ursache im Code/Datenmodell
prüfen, minimal korrigieren und durch reproduzierbaren Test oder Read-back
gegenprüfen. UI/native/SQLite/Owner-Stores/MCP/Journale/Queue/Backup/Restore sowie
Drafts, Timer, Medien, Planung, Angebote, Rechnungen und Löschpfade einschließen,
soweit betroffen. Tests und Fixtures dürfen ausgemusterte Pfade nicht aktivieren.

Aktive Schutz-/Test-/Host-Legacykomponenten nicht nach Dateiname oder Alter löschen.
Pflanzendaten und Webspiegel weiter über `tools/sync_plant_assets.py` führen;
generierte Spiegel nicht unabhängig ändern. Bestehende Fachverträge erhalten.
Nur relevante bestehende Prüfungen ausführen; fehlende native/physische oder
externe Nachweise ausdrücklich offen lassen. Ein Build ist keine Geräteabnahme.

Bei uneindeutiger Datenintegrität, drohendem Verlust einzigartiger Arbeit,
unklarer Belegautorität oder notwendiger Gateumgehung den betroffenen Schritt
anhalten. Andere eindeutig ausführbare Schritte fortsetzen. Aktuelle und
beobachtete installierte Versionen nicht mit historischen Ständen verwechseln.
Versionsaufbewahrung nach Nutzerklärung vom27.10.: APKs ab Revier Solo
(1.55.0/Build99) behalten, ältere administrative APKs und Versionsreferenzen
entfernen. Die genaue Grenze und Nachweise stehen in `source-lifecycle.json`;
kein automatisches späteres Verschieben auf „nur zwei Versionen“. Git-Historie zur Nachvollziehbarkeit
erhalten; nie Force-Push oder unangekündigtes Umschreiben.

## Starttext für die bestehende Gartenumgebung

Dieser Bootstrap ist der zuletzt direkt kopierbar übergebene Start-Skill.
Der Nutzer hat seine manuelle Veröffentlichung am27.10.2026 bestätigt.
Veröffentlichungsbestätigung, technisches Rücklesen und Übernahme in eine neue
Laufzeit bleiben im aktuellen Status getrennt; eine Git-Änderung allein ändert
keine Cloud-Konfiguration. Die spätere ausdrückliche Nutzerpräzisierung zur
engen ADB-/Protokollausnahme für den Laptop gilt gemäß den obigen aktuellen Regeln;
der untenstehende Text bleibt der unveränderte zuletzt manuell veröffentlichte Stand.

```text
Arbeite ausschließlich im bestehenden privaten Repository mihalhafner-collab/Gartenpirat-.

Hole vor Aufgabenbeginn den aktuellen GitHub-main und die vorhandenen Arbeitsbranches.

Lies zuerst AGENTS.md vom aktuellen GitHub-main, danach AKTUELLER-STAND.json, CODEX-AUFTRAG-GARTENPIRAT.md und CODEX-STATUS.md aus dem passenden aktuellen Quellstand.

Anweisungen und Entwicklungsstände ausschließlich über dieses GitHub-Repository führen. Keine Regeln, Aufträge oder Arbeitsstände aus Drive, Pages/Spaces oder alten Chats nachladen.

Alle Änderungen nach Privacyprüfung committen, nach GitHub pushen und exakt zurücklesen.

Keine Drive-Quellsicherungen, Exporte oder Statuskopien. Ausnahme: fertige geprüfte APKs und unmittelbar erforderliche Installationsprüfsummen im bestehenden privaten APK-Bereich.

Verschlüsselte App-Backups getrennt erhalten. Keine Betriebsdaten für Entwicklung oder Tests verwenden.

Persönliche Signierwerte sowie Netzwerk- und Zugriffsrechte unverändert schützen.

Kein Laptop, keine automatische Installation, Migration oder Rechnungsfreigabe.

Bei fehlendem GitHub-Zugriff sicher anhalten. Keinen anderen Anweisungs- oder Sicherungsdienst verwenden.
```
