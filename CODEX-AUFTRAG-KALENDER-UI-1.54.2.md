# CODEX-AUFTRAG: Kalender-Übersicht und einfache Bedienung (Basis 1.54.2 / Build 91)

Für Codex Cloud. **Prämisse: Übersicht und einfache Bedienung.** Reine Darstellungs- und Bedienungsänderung an der Kalenderseite („Planung"), an der nativen Seite „Kalender & Wetter" und an wenigen Stellen der Einstellungen. **Keine Änderung an Kalender-Logik, Google-Autorisierung, Datenmodell, Sync oder MCP.**

Dieser Auftrag baut auf drei vorhandenen Aufträgen auf:
- `CODEX-AUFTRAG-SICHERHEIT-1.54.md` (Paket A: Klartext-HTTP, Paket C: WebView/CSP)
- `CODEX-AUFTRAG-GRAFIK-1.54.md` (G1 Tokens, G3 Heute-Dock, G4 Kopfzeilen, G5 Status-Chips; **G2 wird hier durch K8 konkretisiert**)
- die Kalender-Wiederherstellung aus `CODEX-STATUS.md` (21.10.2026, Branch `codex/calendar-permanent-20261021`)

---

## 0. Rahmen (zuerst lesen)

1. **Basis:** Branch `codex/calendar-permanent-20261021` (enthält die Kalender-Wiederherstellung, Quellfingerprint `747762839020b4ffb0491ac360c3cc054c455245eb4126b0c1e6d4e8882b0c3c`). **Neuer Arbeitsbranch** `codex/calendar-ui-overview-20261021` davon abzweigen. Kein Push auf `main`, kein zweites Repository.
2. `AGENTS.md`, `CODEX-STATUS.md`, `AKTUELLER-STAND.json`, `source-lifecycle.json` vor Beginn lesen und deren Regeln einhalten. Statusdateien nur so nachführen, wie dort beschrieben.
3. **Nicht ändern:** Paket `at.hausgarten.service`, IDs/Relationen/Speichermodelle, Drive-Formate, Kalender-Scopes, Konto-/Kalenderbindung, ETags/CAS, Dublettenschutz, Wiederanlauf-/Retry-Logik, die **117 MCP-Tools / 156 UI-Aktionen**, den Vertragshash `899906c246f9cb8256666c32a89435aa5f792eede3d3407b1c846380900aa813`, Manifest-Berechtigungen.
4. **DOM-IDs, `data-*`-Attribute und Klassen, die Tests/JS nutzen, bleiben** (u. a. `#planning-body`, `#planning-day`, `#planning-week`, `#planning-prev`, `#planning-next`, `#planning-new`, `#planning-suggest`, `#planning-more`, `#planning-all`, `#planning-settings`, `#planning-refresh`, `[data-appointment-open]`, `[data-open="weeks"]`, `[data-calendar-choice]`, `.planning-row`, `.planning-weather[data-severity]`, `.planning-collision-badge[data-uncertain]`, `#appointment-form`, `.planning-save-actions`).
5. **Keine APK, keine Signierung, keine Versionserhöhung, keine Installation.** Pixel-Abnahme und echte Google-Freigabe bleiben **NOT EXECUTED** und werden so im Bericht geführt. Aus fehlender Abnahme keinen Erfolg ableiten.
6. Nur synthetische Daten. Keine Konten, keine Tokens, keine echten Termine. Kein Google-Konto/keine E-Mail-Adresse in Tests, Logs, Screenshots oder Berichten.
7. Reihenfolge: **Baseline-Lauf → Sicherheit A, C → Grafik G1 → K1–K9 → Grafik G3–G6 (soweit nicht durch K ersetzt) → Abschlusslauf.** Jedes Paket ein eigener Commit.

---

## 1. Ausgangsbefund (Build 91, aus Quellcode und Screenshot)

| # | Befund | Fundstelle |
|---|---|---|
| 1 | Kalender ist nur eine Kartenliste (Tag/Woche). Kein „Heute", keine Wochenleiste, keine Tagesköpfe. Datum + Wochentag steht in **jeder** Karte. | `planning-ui.js` `list()`, `rowHTML()`, Variable `view='week'` |
| 2 | Karte bis zu sechs Textzeilen (Zeit, Kunde, Titel, Status·Ort, Ortsvorhersage, Wetter, Kollision). | `rowHTML(entry)` |
| 3 | Segment „Tag \| Woche \| Ist-Stunden": Ist-Stunden ist eine andere Seite, keine Ansicht. | `.planning-tabs` (3 Spalten) |
| 4 | Feste Leiste (+ Termin / Zeitfenster finden) hängt über der Tab-Leiste und verdeckt Inhalt. | `planning-ui.css` `.planning-list-actions{position:fixed;bottom:var(--app-nav-height)…}` |
| 5 | Verbindungsproblem nur als nicht tippbarer Satz („… unter Einrichten prüfen"). | `list()` Variable `attention` |
| 6 | Terminformular: Kunde, Auftrag, Titel, Zweck, Ort, Status, Wetterprofil, Anfahrt, Pause, Wiederholung, „Ende offen" in einem langen Formular. | `planning-ui.js` Zeilen ca. 112–113 |
| 7 | Kalenderabgleich-Dialog nennt technisch: „App-Termin verwenden / Google-Zeit/Ort übernehmen / App-Termin ohne Kalender behalten". | `planning-ui.js` `data-calendar-choice` |
| 8 | Native Seite „Kalender & Wetter": graue Standard-Buttons/Checkboxen, Fehlertext mehrfach, Rohcode „Prüfschritt … Hinweiscode …" sichtbar, „Kalenderverbindung trennen" optisch gleich wie harmlose Buttons, Fußbutton „Google-Freigabe erneuern" auch ohne frühere Verbindung. | `PlanningConnectionActivity.java` Zeilen ca. 24–25, 40–44, 61, 76–77 |
| 9 | Einstellungen/Sicherung: **zwei** „Erweitert / Diagnose" untereinander, neutraler Sync-Chip gelb und in „App-Information", „Wiederherstellen" optisch gleich wie „Sicherung prüfen", doppelte Zurück-Leiste. | `daily-usability.js` Zeile ca. 28, `business-actions-ui.js` Zeile ca. 44, `business-feedback.js` Zeile ca. 17 |

---

## 2. Arbeitspakete

### K1 – Orientierung: Heute, Wochenleiste, Tagesköpfe

In `list()` (`planning-ui.js`):
- Zeile `‹ Datumsbereich ›` bleibt, dazu ein Button **„Heute"** (setzt `anchor=new Date()`, `offset=0`, ruft `list()`). IDs: `planning-prev`, `planning-next` bleiben; neu `planning-today`.
- Darunter eine **Wochenleiste** mit 7 Buttons `Mo … So` (`data-planning-day="YYYY-MM-DD"`, `aria-label="Mittwoch 21. Oktober, 2 Termine"`), heutiger Tag hervorgehoben, ausgewählter Tag markiert, Punkt unter Tagen mit Terminen. Tippen = `view='day'`, `anchor=<Tag>`, `list()`.
- Wochenansicht: Termine **nach Tag gruppiert** mit Tageskopf `<h3 class="planning-day-head">Mi 21.10. · 2 Termine</h3>` (Anzahl; Summe der geplanten Stunden nur, wenn alle Termine ein Ende haben, sonst weglassen). Tagesansicht: genau ein Tageskopf.
- Die Karten zeigen **kein Datum mehr** (nur Uhrzeit). Leere Tage in der Wochenansicht **nicht** einzeln auflisten; Leere Woche: bestehender Text „Noch keine Termine in diesem Zeitraum." bleibt.
- **Datenmenge:** `list_appointments` wird weiter mit dem vorhandenen Schema aufgerufen (kein neues Argument, kein Tool-Umbau). Für Punkte/Zähler die bereits geladenen `items` verwenden. Reicht `limit:30` für eine Woche nicht (`offset<total`), die Seiten bis `offset>=total` nachladen (maximal 4 Aufrufe), oder, wenn das Schema es zulässt, größeres `limit` – **im Bericht begründen**, welche Variante gewählt wurde. „Weitere30 anzeigen" bleibt für Tagesansicht erhalten.

### K2 – Karte ruhiger, Inhalte nach Wichtigkeit

`rowHTML(entry)` neu gliedern, Container bleibt `<button class="card planning-row" data-appointment-open="ID">`:

```html
<span class="planning-time">14:00–20:40</span>            <!-- Zeile 1, fett -->
<strong>Kundenname</strong>                                <!-- Zeile 1/2 -->
<span class="planning-what">Gartenpflege · Siedlerweg 33</span>  <!-- Zeile 2, gedämpft -->
<span class="planning-chips">                             <!-- Chips, umbrechend -->
  <span class="planning-chip" data-kind="status">Bestätigt</span>      <!-- nur wenn nicht Standard -->
  <span class="planning-weather" data-severity="difficult">☂ Arbeit erschwert</span>
  <span class="planning-collision-badge" data-uncertain="false">⚠ Terminkollision</span>
  <span class="planning-chip" data-kind="calendar">Kalender prüfen</span>
</span>
```

Regeln:
- **Maximal 2 Textzeilen + 1 Chipzeile**; Ort/Titel mit Auslassung (`text-overflow`, 1 Zeile), kein 6-Zeilen-Block.
- Texte, auf die Tests prüfen, bleiben wörtlich erhalten: „Ende offen", „Terminkollision", „Mögliche Terminkollision", „Kalenderänderung prüfen" (als Chip-Text), „Einsatzort offen". Ortsvorhersage („Ortsvorhersage · Einsatzgebiet") in den Wetter-Chip statt eigener Zeile.
- Statuschip nur bei Abweichung vom Normalzustand; Farben ausschließlich aus Tokens (Grafikauftrag G1), immer Icon/Text, nie nur Farbe.
- Mindesthöhe 56 px, ganze Karte tippbar, kein horizontales Scrollen bei 320 px und 150 % Schrift.

### K3 – Steuerzeile vereinfachen

- Segment nur **„Tag | Woche"** (zwei Spalten; `#planning-day`, `#planning-week`, `aria-pressed` bleiben).
- **„Ist-Stunden ›"** wird ein einzelner Textlink in der Kopfzeile bzw. direkt unter dem Segment (`data-open="weeks"` bleibt, damit Navigation/Tests unverändert greifen).
- „Alle Kunden" (`#planning-all`) bleibt als Chip „Nur: <Kunde> ✕" sichtbar, wenn ein Kundenfilter aktiv ist.

### K4 – Aktionsleiste ohne Überlappung

- `.planning-list-actions` nicht mehr `position:fixed` über der Tab-Leiste. **„+ Termin"** (`#planning-new`) wird eine runde Schaltfläche (FAB, 56 px) rechts unten **oberhalb** der Tab-Leiste: `position:fixed; right:16px; bottom:calc(var(--app-nav-height,80px) + 16px)`, mit Beschriftung `aria-label="Neuer Termin"`. Seite erhält `padding-bottom` ≥ FAB-Höhe + 24 px, damit nichts verdeckt wird.
- **„Zeitfenster finden"** (`#planning-suggest`) wandert als kleiner Textbutton **„Freie Zeit finden"** unter die Wochenleiste (nur Listenansicht). Funktion unverändert.
- `.planning-save-actions` (Speichern-Leiste im Formular) bleibt am unteren Rand, überlappt die Tab-Leiste nicht (in der Formularansicht ist die Tab-Leiste bereits ausgeblendet – prüfen, nicht annehmen).

### K5 – Verbindungsstatus als tippbarer Chip

In `list()` die Variable `attention` (bisher `<p role="alert">`) ersetzen durch **einen** Chip, der den gleichen Handler wie `#planning-settings` auslöst (öffnet „Kalender & Wetter"):
- Priorität: `authorization_required` → „Kalender: Freigabe nötig ›"; `retry_limit_reached` → „Kalender: Verbindung prüfen ›"; `review_required` → „Kalender: Änderung prüfen ›"; Wetterstatus analog „Wetter: …".
- Bei blockierenden Zuständen `role="alert"`, sonst `role="status"`. Es erscheint **nur ein** Chip (höchste Priorität).
- `<details class="planning-diagnostics">` („Erweitert / Diagnose") bleibt, standardmäßig **geschlossen**; Inhalt unverändert.

### K6 – Terminformular: Kurzform + „Mehr Optionen"

In `drawEditor()` (Zeilen ca. 112–113):
- **Immer sichtbar (oben):** Kunde, Datum, Startzeit, **Dauer-Chips** „1 Std | 2 Std | Halber Tag | Ende offen" (setzen die vorhandenen Felder `planning-duration` / `end_open`; Eingabe „1,5" bleibt als freies Feld), Titel.
- **Unter `<details class="planning-more-options">` (Standard geschlossen):** Auftrag (optional), Zweck, Status, Wetterprofil, Ort/Anfahrt/Pause/Standortübernahme, Wiederholung, Notizen.
- Das Panel öffnet **automatisch**, wenn (a) ein bestehender Termin Werte in diesen Feldern hat, (b) die Validierung einen Fehler in einem dieser Felder meldet, (c) der Entwurf (`localStorage`) solche Werte enthält.
- **Feldnamen, IDs, `capture()`, `values`, `create_appointment`, `create_appointment_series`, Wochenwiederholung und Entwurfs-/Archivlogik bleiben unverändert.** Nur die Anordnung ändert sich.
- Aus der Kundenakte („Einsatz planen") bleibt der Kunde vorbelegt (`customerFilter`), der Cursor springt auf das erste leere Pflichtfeld.

### K7 – Kalenderabgleich verständlich

Der Dialog „Kalenderänderung vergleichen" (`[data-calendar-choice]`, Zeilen ca. 126–134):
- **Zwei Spalten** „Deine App | Google Kalender" (bei ≤ 360 px untereinander), unterschiedliche Werte (Zeit, Ort, Titel) farbig/fett markiert, Zeitangaben in `stamp()`-Format.
- Buttons umbenennen, **Werte der `data-calendar-choice`-Attribute und die aufgerufenen Funktionen bleiben**:
  - „App-Termin verwenden" → **„Meine App-Version behalten"**
  - „Google-Zeit/Ort übernehmen" → **„Google-Version übernehmen"**
  - „App-Termin ohne Kalender behalten" → **„Nur in der App behalten"** (mit einer Zeile Erklärung „Der Termin wird nicht mehr mit Google abgeglichen.")
- Keine neue Auswahl, kein Standard, der Daten überschreibt. Primärstil nur für **„Meine App-Version behalten"** (reversibel); „Google-Version übernehmen" Sekundär mit Bestätigung, falls das bisher schon so ist (nicht abschwächen).

### K8 – Native Seite „Kalender & Wetter" (`PlanningConnectionActivity.java`, ersetzt Grafikpaket G2 für diese Seite)

Voraussetzung: `NativeUi.java` aus dem Grafikauftrag (Buttons, Schalter, Karten, Farben aus `tokens.css`). Wenn dort noch nicht umgesetzt: zuerst anlegen.
- **Kopfbereich:** ein Status-Chip (Nicht verbunden / Verbunden / Prüfung nötig) plus **eine** Handlungszeile. Die Fehlermeldung (z. B. „Die Google-Freigabe wurde abgebrochen …") erscheint **einmal** in einer Hinweisbox, nicht in Kopfzeile + Karte + Status.
- **Dreischritt-Anzeige** „1 Konto · 2 Freigabe · 3 Kalender" als reine Darstellung aus den **vorhandenen** Zuständen (Konto gewählt? Freigabe erteilt? Kalender angelegt?). Keine neue Zustandslogik, keine neuen Preferences.
- **Fußleiste:** „Zurück" (Sekundär) und Hauptbutton. Text: „**Verbinden**", solange noch nie verbunden wurde (kein `calendar_setup_code`, kein Konto), danach „**Erneut verbinden**" bzw. „Freigabe erneuern" nur bei vorhandener Freigabe. Methode `calendarPrimary()` und alle Intents **unverändert**.
- **„Kalenderverbindung trennen"** im Gefahrstil (rot umrandet); der bestehende Bestätigungsdialog bleibt wörtlich erhalten.
- **Rohcode ausblenden:** „Prüfschritt …" und „Hinweiscode: CALENDAR_…" (ausschließlich aus `CalendarAccessRules.safeCode`) in einen eingeklappten Bereich **„Technische Details"** (bestehender `disclosure(...)`-Mechanismus). Sie dürfen nicht entfallen (Support/Diagnose).
- Checkboxen → Framework-`Switch` ganze Zeile, Texte unverändert; „Auch Belegtzeiten meines Hauptkalenders prüfen" mit einer Erklärzeile statt zweizeiligem Titel.
- **Sichtbare Konto-Adresse** weiterhin nur dort, wo sie jetzt schon steht („Konto und Freigaben"); nicht in Chip/Kopfzeile aufnehmen, nicht in Logs/Tests.
- Auf `DriveConnectionActivity` und `PixelTunnelActivity` die gleichen `NativeUi`-Stile anwenden.
- **Nicht anfassen:** `CalendarAuthorization*`, `CalendarAccessRules`, `PlanningAccount`, Scopes, Wiederanlauf, Retry-Zähler.

### K9 – Einstellungen/Sicherung aufräumen (Screenshot Mehr → Sicherung)

- **Ein** Aufklapper „Erweitert / Diagnose": den in `business-actions-ui.js` (Sicherungskarte) und den in `daily-usability.js` (Zeile ca. 28, „· Fehlermeldungen") zusammenführen; `pre.settings-error-details` und die vorhandenen Inhalte bleiben, die Karte verweist nicht doppelt.
- **Neutraler Sync-Chip:** `tone:'busy'` (`business-feedback.js`) in Salbei/neutral statt Gelb, mit kleinem Fortschrittsindikator, **in der Sync-Karte** statt in „App-Information". Gelb bleibt Aktion/Prüfung nötig.
- **„App-Information"** als kleine Fußzeile (Version/Build) statt eigener Karte.
- **Sicherungsbereich:** „Jetzt sichern" Primär, „Sicherung prüfen" Sekundär, **„Wiederherstellen" im Gefahrstil**; vorhandene Bestätigungen **nicht** abschwächen. Der Absatz darf kürzer werden, **der Satz „Die Synchronisierung allein ersetzt keine Sicherung." bleibt sichtbar** (nicht hinter einen Aufklapper). Restlicher Erklärtext optional unter „Mehr erfahren".
- Doppelte untere „Zurück"-Leiste auf Unterseiten entfernen (Grafikpaket G4); `handleAndroidBack` bleibt.

---

## 3. Verknüpfung mit den vorhandenen Tests

**Ausgangslauf vor der ersten Änderung** (Ergebnis und Zahlen notieren): Python-Suite (383 Tests/0 Skips, 37 historische QA-Schlüsseltests ausgeschlossen), 40 JVM-Suiten, Browser-Suiten, Release-/Preview-Gates. Wenn der Ausgangslauf nicht grün ist: **zuerst melden, nicht anpassen.**

### 3.1 Bestehende Tests, die geschützt werden

| Suite | Schützt | Erwartete Anpassung |
|---|---|---|
| `tests/planning-ui.test.cjs` | Liste, Tag/Woche, Formular, Speichern, Serien, Entwurf, Kalenderabgleich | **K1–K7:** nur Selektoren/Texte, die bewusst wandern (Datum aus Karte, Ist-Stunden-Position, Button-Beschriftungen). Schutzassertions (gespeicherte Werte, Serie, Entwurf, Abgleich) **bleiben**. |
| `tests/planning-open-end.test.cjs` | „Ende offen" | **K6:** Dauer-Chip „Ende offen" setzt dasselbe Feld wie bisher; Assertions unverändert. |
| `tests/navigation-calendar.test.cjs` | Navigation Planung/Kalender, Zurück | **K3/K4:** Ist-Stunden-Link, FAB statt Leiste. |
| `tests/p3-search-planning.test.cjs` | Suche → Planung | unverändert PASS. |
| `tests/calendar-authorization-ui.test.cjs` | Status/Autorisierung in der UI | **K5:** Chip statt Absatz; Texte „Freigabe nötig" etc. prüfen. |
| `tests/status-collisions-gestures.test.cjs` | Kollisionsbadge, Wischgesten, Zurück-Reihenfolge | **K2/K9:** Badge-Texte und `data-uncertain` bleiben; Zurück-Verhalten (`handleAndroidBack`) unverändert. |
| `tests/field-ui.test.cjs`, `tests/daily-usability.test.cjs`, `tests/design-concept.test.cjs`, `tests/design-v139.test.cjs`, `tests/simple-design.test.cjs` | Heute-Dock, Einstellungen, Navigation, Überlauf 320/360/426 px | **K4/K9:** Dock/FAB-Platzierung; nur eine „Erweitert / Diagnose". |
| `tests/p3-business-actions.test.cjs`, `tests/p3-customer-hub.test.cjs`, `tests/p3-ux-comparison.test.cjs`, `tests/p3-uat-walkthrough.test.cjs`, `tests/release-browser.test.js`, `tests/ui-preview.test.cjs` | Sicherung/Wiederherstellung, Kundenakte → „Einsatz planen", Gesamtablauf, Preview | Sicherung: „Wiederherstellen" bleibt bestätigungspflichtig. Preview muss `tokens.css` mitnehmen (Grafikauftrag). |
| `tests/webview-syntax.test.cjs`, `tests/webview-hardening.test.cjs` (aus Sicherheitsauftrag), `tests/visual-consistency.test.cjs` (aus Grafikauftrag) | Syntax, CSP (keine Netz-APIs, keine externen Ressourcen), Tokens | Weiter PASS; keine externen Schriften/URLs. |
| JVM: `CalendarRecoveryTests`, `CalendarSetupTests`, `CalendarAuthorizationTests`, `CalendarAccessTests`, `PlanningTests`, `AppointmentAvailabilityTests`, `OpenEndPlanningTests`, `WeatherWorkflowTests`, `FreeWeatherSourceTests`, `UiMcpParityTests` | Wiederanlauf/Abbruch/Zeit, Setup, Scopes, Verfügbarkeit, 117 Tools/156 Aktionen | **Müssen unverändert bestehen.** Nur Textassertions auf **bewusst geänderte** native Beschriftungen („Verbinden") dürfen mit Begründung angepasst werden, nie Fehlerklassifizierung oder Zustandslogik. |
| Python: `tests/run_quality_suite.py`, `tests/test_release_quality.py`, `tests/test_source_privacy_rules.py`, `tests/test_native_ui_consistency.py` (Grafikauftrag) | Release-/Privacy-Gates, native Farben = Tokens | PASS; Gates nicht abschwächen. |
| Tools: `tools/verify_ui_mcp_inventory.py`, `tools/check_refactoring_contract.py`, `tools/verify_ui_preview_apk.py`, `tests/apk_privacy_guard.py` | Inventar, Vertrag, Preview, Privacy | UI-Quellreview-Fingerprint und Asset-Anzahl (tokens.css, ggf. neue CSS) korrekt nachführen. Danach Android-Release-/Instrumentierungs-Java kompilieren und Lint ohne neue Fehler. |
| Native Instrumentierung (158 Prüfungen, `native_ui`) | echte Geräteabläufe | **Nur kompilieren** (kein Gerät): als **NOT EXECUTED** führen. |

### 3.2 Neue Tests

1. **`tests/planning-overview.test.cjs`** (Chromium, synthetische `AndroidData`-Brücke, Muster `planning-ui.test.cjs`; je 320/360/426 px, 150 % Schrift):
   - Wochenleiste hat 7 Buttons; Tage mit Terminen tragen Punkt und `aria-label` mit Anzahl; Tippen wechselt in die Tagesansicht des Tages; „Heute" setzt zurück und markiert den heutigen Tag.
   - Wochenansicht gruppiert: Tagesköpfe in Reihenfolge, Anzahl stimmt; **kein Datum in der Karte**.
   - Karte: höchstens 2 Textzeilen (`getClientRects`/Zeilenhöhe), kein Überlauf (`scrollWidth ≤ clientWidth+1`), Chips umbrechen; Wetter-/Kollisions-/Kalenderchips erscheinen nur bei passenden Fixtures.
   - Segment hat genau zwei Buttons (`#planning-day`, `#planning-week`); `[data-open="weeks"]` ist weiterhin auffindbar.
   - **FAB überlappt die Tab-Leiste nicht** und verdeckt keinen Button/Karte (Bounding-Box-Vergleich, letzte Karte bis ans Ende scrollbar).
   - Verbindungschip: bei `authorization_required` genau ein Chip, Klick löst denselben Handler wie `#planning-settings` aus (Stub zählt Aufrufe).
   - Formular-Kurzform: nur Kunde/Datum/Zeit/Dauer-Chips/Titel sichtbar, `.planning-more-options` geschlossen; öffnet bei bestehendem Termin mit Auftrag/Ort/Wetterprofil und bei Validierungsfehler im Panel.
   - **Regression Speichern:** Das an `create_appointment`/`create_appointment_series` übergebene Argumentobjekt ist bei gleicher Eingabe **identisch** mit dem Stand vor der Änderung (Golden-Vergleich aus dem Ausgangslauf).
   - Konfliktansicht: zwei Spalten, Unterschiede markiert, `[data-calendar-choice]`-Werte unverändert und rufen dieselben Funktionen auf.
   - Screenshots `outputs/kalender-*.png` (Liste Tag/Woche, leere Woche, Formular, Abgleich, Verbindungschip) speichern und im Bericht beschreiben.
2. **`tests/settings-cleanup.test.cjs`:** Einstellungsseite enthält genau **eine** Zusammenfassung „Erweitert / Diagnose"; `pre.settings-error-details` vorhanden; Sync-Chip im Zustand `busy` ohne Warnfarbe; Satz „Die Synchronisierung allein ersetzt keine Sicherung." sichtbar (nicht im geschlossenen `<details>`); „Wiederherstellen" mit Gefahrklasse und vorhandener Bestätigung.
3. **`tests/test_native_calendar_ui.py`** (statisch, JVM-/geräte-frei):
   - `PlanningConnectionActivity.java` enthält keine nackten `new Button(`/`new CheckBox(` außerhalb der gestylten Helfer.
   - Hauptbutton-Beschriftung „Verbinden" und „Erneut verbinden" kommen vor, `calendarPrimary()` ist unverändert vorhanden.
   - „Technische Details" enthält die Ausgabe von `CalendarAccessRules.safeCode(...)` und „Prüfschritt"; beides erscheint **nicht** in Kopfzeile/Fehlerbox.
   - Keine E-Mail-Adresse, kein Token, kein `Authorization`-Header in den neuen Strings.
4. Neue Suiten in `tests/run_quality_suite.py` bzw. im gemeinsamen Browser-Runner und im manuellen Workflow `.github/workflows/qa-apk.yml` eintragen.

### 3.3 Abnahmekriterien (messbar)

- Planungsliste: Tag/Woche mit Heute-Sprung, Wochenleiste, Tagesköpfen; **eine** Primäraktion (FAB) ohne Überdeckung; Karten ≤ 2 Textzeilen + Chips.
- Terminformular: Kurzform ohne Scrollen bei 360×800 px für Kunde, Datum, Zeit, Dauer, Titel und Speichern; Felder unverändert gespeichert.
- „Kalender & Wetter": Fehlermeldung einmal; Rohcode nur unter „Technische Details"; richtiger Hauptbutton-Text; Trennen im Gefahrstil mit Bestätigung.
- Einstellungen: ein Diagnose-Aufklapper, neutraler Sync-Chip, Wiederherstellen als Gefahraktion.
- Alle Suiten aus 3.1 PASS (oder dokumentiert: bewusst geändert, Schutzassertions erhalten). **117 Tools / 156 Aktionen / Vertragshash unverändert.**

---

## 4. Pflicht-Abschlussbericht (`CODEX-STATUS.md`, Abschnitt „Kalender-Übersicht K1–K9 (Basis 1.54.2/91)")

1. Geänderte/neue Dateien je Paket (Liste) und Branchname.
2. **Tatsächlich ausgeführte** Tests (Anzahl, PASS/FAIL), getrennt von **NOT EXECUTED**: Pixel, echte Google-Freigabe, native Instrumentierung auf Gerät, Update 90→91.
3. Welche bestehenden Test-Erwartungen wegen bewusster Layout-/Textänderung angepasst wurden (mit Begründung); Bestätigung, dass keine Schutzassertion und kein Gate entfernt oder abgeschwächt wurde.
4. Wahl bei K1 (Datenmenge/`limit`) mit Begründung; Ausgangslauf vs. Endlauf; Asset-Anzahl, UI-Review-Fingerprint, Quell-SHA-256.
5. Vorher/Nachher-Screenshots (Pfade), kurze Selbstkritik, was weiterhin uneinheitlich ist.
6. Bestätigung: keine APK, keine Version, keine Berechtigungs-/Daten-/Scope-/Sync-Änderung, keine Kontodaten in Artefakten, kein Push auf `main`.

## 5. Verboten

- Keine Änderung an Google-Autorisierung, Scopes, Konto-/Kalenderbindung, Retry-/Wiederanlauflogik, Datenmodell, Sync, MCP.
- Keine Abschwächung von Privacy-/Signier-/Fingerprint-/Vertrags-Gates, keine entfernten Schutzassertions.
- Kein Dunkelmodus, keine neuen Schriften, Bilder oder Abhängigkeiten, keine Netz-APIs in der UI.
- Keine Rohcodes, E-Mail-Adressen oder Tokens im sichtbaren Hauptbereich oder in Testartefakten.
- Kein `setAllowFileAccess(true)` als „Notlösung" (siehe Sicherheitsauftrag).
