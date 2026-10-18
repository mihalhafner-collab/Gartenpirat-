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

## Noch erforderlicher produktiver Rollout

Der Laptop/Web-Prozessor muss auf denselben aktuellen Repository-Stand aktualisiert und neu gestartet werden. Vor dem ersten vollständigen Import ist der gesicherte Ausgangsstand beizubehalten. Der aktualisierte Prozessor exportiert anschließend `business_settings`, `offers`, `service_catalog` sowie die Capabilities `offers_v1`, `service_catalog_v1`, `owner_journal_v2`, `invoice_compare_and_save`, `photo_compare_and_save` und `durable_action_receipts`.

Erst danach Pixel-Abgleich erneut ausführen und prüfen:
- `offers_sync_ready=true`
- `service_catalog_sync_ready=true`
- `get_business_settings` erfolgreich
- Foto- und Rechnungsentwurf-Update ohne `REMOTE_GUARD_REQUIRED`
- keine `SYNC_COMPONENT_UPDATE_REQUIRED`-Einträge
- technische Test-Konfliktentscheidung verschwindet ohne Geschäftsdatenänderung
- anschließend Backup + Vorschau + kontrolliertes Schließen der sieben historischen Alt-Konflikte

Keine Installation oder produktive Konfliktschließung gilt allein durch einen erfolgreichen Build als freigegeben.
