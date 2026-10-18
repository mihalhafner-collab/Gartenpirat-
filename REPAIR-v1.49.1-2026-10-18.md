# Reparaturnachweis v1.49.1 – 18.10.2026

## Ausgangsfehler

Der produktive Pixel lief bereits auf 1.49.0/69, während der Drive-Gesamtstand `HausGarten-Laptop.json` noch das ältere Schema verwendete. Der gelesene Ausgangsstand hatte SHA-256 `8556108cafc9ad47a6ee82fa3ce49d01d7a057b42407c8098539dc3825f18cc3`, keine `business_settings`, keine Angebots-/Leistungskatalogsammlungen und nur die älteren Compare-Capabilities. Dadurch blieben `offers_sync_ready` und `service_catalog_sync_ready` falsch, Foto-/Rechnungsänderungen liefen in `REMOTE_GUARD_REQUIRED`, Angebote in `SYNC_COMPONENT_UPDATE_REQUIRED`.

Eine bytegleiche Drive-Sicherung des Ausgangsstands wurde vor weiteren produktiven Änderungen angelegt. Zusätzlich wurde über die Pixel-Eigentümerfunktion ein Geschäftsbackup erstellt. Keine Geschäftsdatensätze wurden für die Reparatur erfunden oder überschrieben.

## Korrekturen

- `SYNC_COMPONENT_UPDATE_REQUIRED` ist kein manueller Datenkonflikt mehr und kann nicht mehr über `resolve_sync_conflict` verworfen oder angewendet werden.
- Manuelle Konfliktauflösung ist auf tatsächlich auflösbare Geschäftsaktionen mit `WRITE_CONFLICT` oder `DELETE_REVIEW_REQUIRED` begrenzt. Kontroll-/Infrastrukturaktionen werden fail-closed zurückgewiesen.
- Android berücksichtigt bei einer bereits erzeugten Konfliktentscheidung den eingebetteten unveränderlichen Originalbeleg, wenn die ursprüngliche Journalzeile nicht mehr im selben Paket steht, aber der Fehler im Snapshot nachgewiesen ist.
- Wird die Zielaktion vor einer späteren Konfliktentscheidung bereits erfolgreich übernommen oder gelöst, wird die überholte Kontrollaktion ohne Geschäftsdateneffekt als erledigt quittiert. Das ist in Android und Python-Web/Drive-Verarbeitung gespiegelt.
- Queue-Hinweis für `SYNC_COMPONENT_UPDATE_REQUIRED` verweist ausdrücklich auf das Komponentenupdate statt auf einen Versionsvergleich.
- QA führt zusätzlich die vorhandenen Java-Sync-/Owner-Vertragstests aus.
- Reparierter APK-Kandidat: `1.49.1`, `versionCode 70`.

## Verbindlicher weiterer Rollout: Codex Cloud + Pixel/Drive

Der frühere Plan, einen Laptop-/Web-Prozessor zu aktualisieren und neu zu starten, ist **verworfen**. Ab 18. Oktober 2026 ist Laptop/Desktop weder Entwicklungsweg noch produktive Voraussetzung oder Fallback.

Alle noch fehlenden Sync-, Nummern-, Angebots-, Foto- und Rechnungsschutzfunktionen werden ausschließlich in **Codex Cloud** im bestehenden Repository weiterentwickelt und geprüft. Der operative Zielpfad ist **Pixel-App ↔ Google Drive direkt**. Historische Python-/Web-Komponenten dürfen nur noch als Legacy-/Testreferenz dienen; keine produktive Kernfunktion darf von ihrem Betrieb abhängen.

Für die ausstehende Reparatur bedeutet das:
- Pixel/Drive-direkte Verarbeitung muss `business_settings`, `offers` und `service_catalog` verlustfrei erhalten und synchronisieren.
- Die Capabilities `offers_v1`, `service_catalog_v1`, `owner_journal_v2`, `invoice_compare_and_save`, `photo_compare_and_save` und `durable_action_receipts` dürfen erst aktiv gemeldet werden, wenn die Pixel/Drive-Verarbeitung sie tatsächlich implementiert.
- Die bestehende Test-Angebotsaktion muss idempotent übernommen werden; keine doppelte Neuanlage.
- `SYNC_COMPONENT_UPDATE_REQUIRED` und die daraus entstandene technische Test-Konfliktentscheidung müssen ohne Änderung von Geschäftsdaten verschwinden.
- Danach Backup + Vorschau + kontrolliertes Schließen der sieben historischen Alt-Konflikte.
- Abschließend End-to-End-Test direkt über Pixel-MCP und Drive: Angebot, Leistungskatalog, Fotoänderung, Rechnungsentwurf, Einstellungen, Retry/Replays und Queue-Zustand.

Ein erfolgreicher Cloud-Build allein ist keine produktive Freigabe. Installation und Konfliktschließung bleiben bis Sicherung, Pixel-Abnahme und nachgewiesenem direkten Drive-Abgleich gesperrt.
