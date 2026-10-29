# Synthetische Leistungsprüfung der aktuellen Quellkorrekturen

Prüfdatum: 29. Oktober 2026. Öffentliche Repositoryquellen und synthetische Daten.
Keine Produktivdatensätze als Entwicklungsinput; keine Installation oder Migration.
Der aktuelle Auftrags-/Freigabestand steht ausschließlich in `AKTUELLER-STAND.json`.

## Quellbindung

Ausgangsstand: GitHub main `ce9b677e50e37cae463b08eac63e9af904be2870`, Build114,
Appfingerprint `699e7b6bddd10143658780efaec60ecf13314185d7c8d6e8eea2e7a1cd89f133`.
Geprüfter Kandidat: Build115 / 1.55.10, Fingerprint `4f1f66a811dd7c796e3b6cab8b395b14d1aa03cf6ed79cde0cdabb6d9ee6c49d`.
Die Prüfkopie enthält sämtliche Appquellen und Assets. Wiederverwendete Dateien
wurden gegen die GitHub-Blob-SHAs gebunden. Historische Root-Bestände und nicht
benötigte redaktionelle Pflanzenunterlagen wurden nicht materialisiert. Der lokale
Hilfscommit der Prüfkopie ist kein GitHub-Quellcommit.

## Bedingungen und Messwerte

Gleicher `startup-ui.test.cjs`-Leistungslauf und gleiche Cloud-Werkzeuge:
Chromium153, Playwright1.64.1, Acorn8.19.0. Je Bedienweg 40 Wiederholungen,
360 × 840 CSS-Pixel, 300 synthetische Kunden, 300 Aufträge, 5 000 Arbeiten,
3 000 Fotometadaten und 500 Rechnungen. Neue Seite pro Stichprobe, warmer
Browser-Assetcache, keine Warmups. Start bis `hg:startup-ready`, Aktionen bis
zum zweiten Animationsframe, Suche einschließlich 130-ms-Debouncewartezeit.
Langsamste 5 %: Mittel der zwei langsamsten Werte. Alle Zeiten in Millisekunden.
Vorher wurde im Build114-Audit gemessen; Hostlast und Gerätezustand sind nicht
kontrolliert. Keine isolierte Kausalitätsbehauptung aus Laufzeitunterschieden.

| Bedienweg | Mittel vorher | Mittel nachher | P95 vorher / nachher | Langsamste 5 % vorher / nachher |
| --- | ---: | ---: | ---: | ---: |
| App-Start | 900.2 | 822.3 | 989.9 / 902.9 | 1050.5 / 932.7 |
| Heute → Büro | 98.9 | 86.2 | 111.9 / 104.3 | 123.2 / 114.0 |
| Büro → Heute | 107.0 | 93.3 | 130.6 / 111.4 | 140.3 / 120.1 |
| Kunden öffnen | 189.3 | 172.4 | 225.6 / 201.4 | 234.0 / 207.0 |
| Suche | 149.6 | 147.1 | 156.9 / 154.9 | 170.3 / 155.6 |
| Speichern | 427.1 | 289.5 | 488.7 / 332.1 | 496.9 / 362.8 |

Der deterministische Unterschied beim Speichern ohne Foto ist durch den Browsertest
belegt: genau ein Geschäftsschreibaufruf, 1 statt 2 vollständige `getState`-Abrufe,
kein verbleibender Kundenentwurf. Bei 40 Start-/Speicherzyklen sinkt `getState`
von 120 auf 80 Aufrufe (Start und Speichern zusammen). Eine Antwort umfasst rund
1,595 MB; damit entfällt pro Speichern ohne Foto ein solcher Vollsnapshot.
Die durchschnittliche simulierte `getState`-Dauer beträgt vorher 4,30 ms und
nachher 4,28 ms; der Nutzen entsteht aus dem entfallenden Aufruf samt Verarbeitung.
Alle 40 Speicher-Readbacks erfolgreich; keine Browser-Seitenfehler.

## Regressionen

- Fünf aktuelle Browser-Suiten: 63 Prüfgruppen PASS.
- Profilbilder: eingebettet und geprüfte native Thumbnail-URI, explizite Rolle,
  Ablehnung fremder URIs und Vorrang einer expliziten Dokumentationsrolle: 6 Fälle PASS.
- 17 Hauptseiten × 320/360 Pixel × 100/200 % Schrift: 68 Layoutfälle PASS.
  Die zuvor überlaufende Rechnungsbereitschaftskarte bricht lange Wörter um.
- Vier JVM-Suiten: 318 Checks PASS (102 Tabellen, 91 Wartung, 54 Gerätefotos,
  71 ZIP/Status/Finanzdiagnose). Statusfälle prüfen Reihenfolge, Pagination,
  Abbruch/Workerstatus, fehlende Nachweise, Zeitfehler und erhaltene frühere Belege.
- `check_current.py`: 62 Pythonprüfungen und aktuelle Quellen-/Vertrags-/Privacy-/
  Fixture-/Pflanzenspiegelprüfungen PASS. Privacy gilt für materialisierte Quellen.

## Grenzen

ADB und physischer Pixel-Zugriff fehlen. Native Worker-/Activity-Lebenszyklen,
Android-Fotoprovider, SAF-Dateiwahl, vollständiges Backup/Restore, RAM/CPU und
Framezeiten wurden nicht getestet. Die Nummernkollision im produktiven Bestand
wurde nicht beseitigt; konkrete Unterkategorien werden erst durch den korrigierten
Appstand sichtbar. Keine Integritätsprüfung wurde abgeschaltet.

Gradle und der vollständige Google-/AndroidX-Abhängigkeitscache fehlen. Ein begrenzter
Javac-Kompilierungscheck umfasst 200 Original-Produktionsquellen mit Android37;
CalendarAuthorization, eine AndroidX-Heading-Methode und BuildConfig wurden
nur für diesen Check durch nicht ausführbare Hilfsdefinitionen ersetzt. Dies ist
kein Gradle-, DEX-, Signatur- oder APK-Nachweis. Keine neue APK erstellt.

Der App-interne Paritätstest prüft keinen in ChatGPT gespeicherten Werkzeugkatalog.
Connector-Abgleich und Gerätnachtest bleiben getrennte Nachweise; der aktuelle
Connector- und Prüfstand steht ausschließlich in `AKTUELLER-STAND.json`.
