# Haus & Garten App – dauerhafte Projektregeln

## Selbstständige Verbesserung

Setze bessere, einfachere und funktionellere Lösungen innerhalb des beauftragten
Bereichs selbstständig um. Bevorzuge kleine, reversible Änderungen an der
bestehenden Anwendung. Prüfe die Änderungen mit passenden Tests und dokumentiere
das Ergebnis. Keine Rückfrage für eindeutig sinnvolle, risikoarme technische
Details; keine zusätzlichen Funktionen ohne praktischen Nutzen.

Frage vorher bei Risiken für produktive Betriebsdaten, bestehende Schnittstellen
oder Kontoverbindungen, bei neuen Kosten, zusätzlichen Berechtigungen und
irreversiblen Änderungen. Diese Arbeitsregel ersetzt keine Sicherheitsfreigaben
und erweitert nicht den Umfang des jeweiligen Auftrags.

## Bestehende App erhalten

- Keine neue App und keine parallele betriebliche Datenbank aufbauen.
- Vor einer Änderung den betroffenen Ablauf, das Datenmodell und seine
  Schnittstellen verstehen; nicht jedes Mal unnötig das gesamte Projekt analysieren.
- Bestehende IDs, Beziehungen, Offline-Daten, Drive-Dateiformate und MCP-Werkzeuge
  erhalten. Erweiterungen rückwärtskompatibel ausführen.
- Pixel ist die primäre Arbeits-App; Drive bleibt der zentrale Datenabgleich.
  Die Web-App ist ergänzend. Keine alten Laptop-Tunnel wiederherstellen.
- Einsatzkunde und Rechnungsempfänger getrennt halten. Arbeitsaufzeichnungen
  ohne Auftrag bleiben gültig. Historische Zuordnungen nicht erraten.
- Keine produktiven Daten löschen, zusammenführen, überschreiben oder als
  Testdaten verwenden. Keine automatische Massenmigration oder ID-Neuvergabe.
- Keine Secrets, Signaturschlüssel, Betriebsdaten oder private Drive-Inhalte in
  Quellcode, Testfixtures, Logs, APKs oder Repository-Uploads einbetten.

## Alltagstauglichkeit

Die App bleibt vollständig ohne KI bedienbar. Prioritäten: Datenintegrität,
Zuverlässigkeit, Übersichtlichkeit und einfache, schnelle Bedienung.
Wichtige Aktionen für kleine Hände gut erreichbar machen, eindeutige Beschriftungen
verwenden und unnötige Navigation, Doppeleingaben und technische Hinweise vermeiden.
Zurück geht genau einen Schritt; Entwürfe und laufende Zeiterfassung schützen.
Keine versteckten automatischen Datenänderungen und keine dekorative Komplexität.

## Prüfung und Freigabe

- Vor jeder Umsetzung kurze, messbare Abnahmekriterien festlegen. Für jeden
  betroffenen Benutzerablauf konkrete Eingaben, erwartete Ergebnisse und
  Fehlerfälle definieren. Beispiel: Arbeitszeit erfassen, offline lokal erhalten,
  später synchronisieren, richtige Kunden-/Abrechnungszuordnung bestätigen und
  denselben Request wiederholen, ohne eine Dublette zu erzeugen.
- Die Arbeitspriorität verbindlich einhalten: zuerst Datenintegrität und
  Synchronisierung, danach Bedienbarkeit und Geschwindigkeit, erst dann
  Zusatzfunktionen. Eine kurze Liste offener Punkte nach dieser Reihenfolge
  führen; keine neue Funktion auf einer bekannten kritischen Datenlücke aufbauen.
- Vorhandene Tests weiterverwenden und bei konkreten Fehlern gezielt ergänzen.
  Tests mit isolierten, synthetischen Daten ausführen.
- Python-Regression: `python3 tests/run_quality_suite.py`; Python-Abhängigkeiten
  gemäß vorhandenen Requirements und Imports bereitstellen.
- Java-, UI-, MCP- und Android-Tests passend zur Änderung ergänzend ausführen;
  vorhandene Windows-Testskripte benötigen gegebenenfalls Werkzeugpfade.
- Zwischen statisch geprüft, automatisiert getestet, tatsächlich ausgeführt,
  End-to-End getestet und nicht testbar ausdrücklich unterscheiden.
- Lokale Speicherung, Drive-Übergabe und bestätigte Synchronisierung nicht
  gleichsetzen; Konflikte und ausstehende Aktionen sichtbar erhalten.
- Android-Releases: gleiche Application ID und vorhandenen Signing Key erhalten,
  versionCode erhöhen. Vor Freigabe echte Fresh-Install- und In-place-Update-Tests
  mit Datenerhalt sowie finalen APK-Privacy-Check nachweisen. Ein Build reicht nicht.
- Vor jeder APK-Bereitstellung zusätzlich die betroffenen Regressionstests,
  Offline-/Sync-Abläufe und das fertige Artefakt prüfen. Bei fehlender Geräte- oder
  Testumgebung den Release als nicht freigegeben kennzeichnen statt Tests zu
  überspringen oder ein erfolgreiches Build als Abnahme auszugeben.
- Änderungen, Tests, Ergebnisse und offene Einschränkungen verständlich berichten.
  Keine Erfolgsbehauptung ohne tatsächlich vorliegenden Nachweis.
