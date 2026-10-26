# Pflanzenbibliothek zur späteren Implementierung

Stand: 23.10.2026, Revision 2. Eigenständige redaktionelle Bibliothek;
**die App liest diese Dateien noch nicht ein.**

314 quellengeprüfte Profile = 181 Bestandsprofile + 133 Ergänzungen in
15 Kategorien. Alle haben mindestens zwei individuell lizenzbelegte
Fotoreferenzen, zusammen 1140. 80 zusätzliche Recherchekandidaten bleiben außerhalb
der Auswahl. Alle 394 Datensätze wurden vor Abschluss erneut vollständig geprüft.

**Neue Bildoriginale sind noch nicht heruntergeladen oder persönlich gesichtet.**
Die Bildserver werden durch die Cloud-Netzrichtlinie blockiert. Deshalb bleibt
die Implementierung gesperrt. Die abgeschlossene Quellenprüfung ist keine
unabhängige botanische Expertenabnahme oder vollständige Rechtsfreigabe.

| Datei | Inhalt |
| --- | --- |
| `PFLANZENVERZEICHNIS.md` | Pflanzen nach Kategorien |
| `plant-library.json` | Merkmale, Feldbelege, Taxonomie, Rechte und Prüfstatus |
| `entry-review.csv` | Einzelprüfung und verbleibende Sperren |
| `photo-credits.csv` | Motiv, Urheber, exakte Lizenz, Lizenzlink und Quelle |
| `pending-image-review.json` | Konkreter Bestand für Download und Sichtprüfung |
| `review-queue.json` | Nicht ausgewählte Kandidaten und Duplikate |
| `QA-BERICHT.md` | Korrekturen, Prüfumfang und Grenzen |
| `INTEGRATIONSANWEISUNG.md` | Ausführbarer Folgeauftrag für die spätere Einbindung in die bestehende App |
| `audit-initial.json`, `audit-final.json` | Beide vollständigen Prüfdurchgänge mit Eingabehashes |
| `evidence/` | Pflanzen-, Taxonomie-, Dateilizenz-, Rechts- und Ausschlussbelege |

303 Profile haben strukturierte botanische Quellenmerkmale; elf weitere sind
amtlich identifizierte Neophytenreferenzen ohne erfundene Pflegeangaben.
Neophytenprofile dienen der Identifikation und Prävention, mit datierten
EU-/CH-Einschränkungen. Regionale Einzelfallregeln und konkrete Bekämpfungsverfahren
werden nicht als vollständig geprüft ausgegeben.

Kew-Namensklärungen, RHS-Standortfelder und Warnungen sowie die Lizenzprüfung sind
in Revision 2 ergänzt. Fehlende Giftangaben bedeuten keine Unbedenklichkeit.
Gattung, Gartengruppe, Art und Aggregat bleiben unterschieden. Quellenklima,
USDA-Zonen und Bodendurchlässigkeit werden nicht als lokale Frostgarantie,
Blühmonatskalender oder gemessener Wasserbedarf ausgegeben.

## Bildrechte und Ausschlüsse

341 der 362 alten Credits sind exakt erneut bestätigt. 335 Bestandsderivate
kommen nach Metadaten- und grober Motivsichtung für spätere Wiederverwendung
infrage. 27 sind redaktionell ausgeschlossen: ungeklärte Credits oder ungeeignete
Motive. Die ursprünglichen App-Dateien bleiben erhalten. Ausgeschlossene Werke
dürfen nicht über Ergänzungslisten wieder in die Fotoauswahl gelangen.

Nur individuelle CC-BY-, CC-BY-SA- oder CC0-Lizenzen werden übernommen; NC, ND und
unbelegte Public-Domain-Angaben bleiben ausgeschlossen. Länderspezifische Fassungen
bleiben exakt erhalten. Die Lizenz eines Seitentextes gilt nicht automatisch für
seine Bilder. NCSU-Zuordnungen verwenden originale Bildattribute;
Commons-Zuordnungen verwenden individuelle imageinfo/extmetadata-Datensätze.

Bei der Nutzung Titel/Motiv, Urheber, Quellseite, exakten Lizenznamen und
anklickbaren Lizenzlink am Bild zugänglich halten. Änderungen wie Ausschnitt,
Verkleinerung und Formatwechsel angeben. Bei CC-BY-SA die für das bearbeitete Werk
geltenden Weitergabebedingungen beachten; keine pauschale Umlizenzierung der App.
CC0-Quellen werden redaktionell ebenfalls genannt. Verknüpfte Bilder bleiben
unter ihren individuellen Fremdlizenzen; dieses Verzeichnis erteilt keine
pauschale Lizenz an allen Werken und garantiert keine Rechtekette des Uploaders.

`originalLocator` enthält Objektpfade ohne kurzlebige Signaturparameter. Vor
Downloads frische Links an der Quelle auflösen, Originalbytes und Hash prüfen und
die Bildlizenz erneut lesen. Metadaten allein bestätigen weder Dateiintegrität
noch botanische Bestimmung oder Motivart.

## Nächster Umsetzungsschritt

Nach freiem Zugang zu den Bildservern mindestens zwei geeignete Aufnahmen je
Profil herunterladen und tatsächlich sichten, unpassende Motive ausscheiden,
Hash und Bearbeitungen dokumentieren. Erst anschließend geprüfte Einträge in
einem gesonderten Integrationsauftrag in den bestehenden kanonischen Katalog
übernehmen. IDs erhalten; Spiegel ausschließlich mit dem vorhandenen
`tools/sync_plant_assets.py --write` erzeugen. Bilder, Attribution, Suche und
Offline-Verhalten dann in der App prüfen.

Reproduzierbare Prüfung im bestehenden Checkout:

```sh
python3 libraries/plants/build_library.py
python3 -m unittest discover -s libraries/plants -p 'test_*.py'
node --test libraries/plants/test_extract_profile_metadata.mjs
python3 libraries/plants/audit_library.py --report initial
python3 libraries/plants/audit_library.py --report final
```

Die Skripte prüfen gespeicherte öffentliche Quellenbelege ohne Netzwerkzugang.
`PASS_WITH_DOCUMENTED_HOLDS` bestätigt Datenkonsistenz und hebt keine Fotosperre auf.
Die CSV-Spalte `selection` unterscheidet Auswahl und zurückgestellte Kandidaten;
keiner dieser Werte ist eine Implementierungsfreigabe. Die vollständige
Hashprüfung der Bestandsbilder benötigt den bestehenden Git-Checkout.
