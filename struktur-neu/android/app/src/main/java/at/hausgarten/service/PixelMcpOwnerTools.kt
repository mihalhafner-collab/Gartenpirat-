package at.hausgarten.service

import java.util.*
import org.json.*

internal class PixelMcpOwnerTools {
    fun interface Access {
        @Throws(Exception::class) fun query(name: String, args: JSONObject): JSONObject?
    }

    companion object {
        @JvmField val CHUNK: Int = McpFields.CHUNK
        @JvmField val BUSINESS_KEYS: Array<String> = McpFields.BUSINESS_KEYS
        @JvmField
        val READS: Array<String> =
            LocalWorkflowTools.concat(
                arrayOf(
                    "run_self_test",
                    "get_ui_action_catalog",
                    "inspect_order_review",
                    "get_capabilities",
                    "preview_empty_trash",
                    "preview_archive_work_records",
                    "preview_purge",
                    "preview_delete",

                    "get_photo",
                    "get_settings",
                    "get_business_settings",
                    "search_catalog",
                    "get_catalog_entry",
                    "read_file_chunk",
                    "get_file_metadata",
                    "list_archive",
                    "list_backups",
                    "inspect_backup",
                ),
                LocalWorkflowTools.READS,
            )
        @JvmField
        val WRITES: Array<String> =
            LocalWorkflowTools.concat(
                arrayOf(
                    "save_reviewed_order",
                    "empty_trash",
                    "archive_work_records",

                    "set_work_record_billed",
                    "reassign_work_record",
                    "update_photo",
                    "begin_file_upload",
                    "write_file_chunk",
                    "create_photo",
                    "purge_entity",
                    "delete_entity",
                    "archive_entity",
                    "restore_entity",

                    "update_settings",
                    "update_business_settings",
                    "set_personal_paid",
                    "create_backup",
                    "restore_backup",

                ),
                LocalWorkflowTools.WRITES,
            )

        @JvmStatic
        fun read(n: String): Boolean {

            return READS.asList().contains(n)
        }

        @JvmStatic
        fun write(n: String): Boolean {

            return WRITES.asList().contains(n)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun schema(name: String): JSONObject {

            return OwnerToolSchemas.schema(name, write(name))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun tools(writable: Boolean): JSONArray {

            var out = JSONArray()
            var names = ArrayList<String>(READS.asList())
            if (writable) {
                names.addAll(WRITES.asList())
            }
            for (n in names) {
                out.put(
                    JSONObject()
                        .put("name", n)
                        .put("description", description(n))
                        .put("inputSchema", schema(n))
                        .put(
                            "annotations",
                            JSONObject()
                                .put("readOnlyHint", read(n))
                                .put(
                                    "destructiveHint",
                                    (Arrays.asList(
                                            "empty_trash",
                                            "archive_work_records",
                                            "set_work_record_billed",
                                            "reassign_work_record",
                                            "purge_entity",
                                            "delete_entity",
                                            "archive_entity",
                                            "restore_entity",
                                            "restore_backup",
                                        )
                                        .contains(n) || LocalWorkflowTools.critical(n)),
                                )
                                .put("idempotentHint", true)
                                .put("openWorldHint", false),
                        )
                )
            }
            return out
        }

        private @JvmStatic fun description(n: String): String {

            if (LocalWorkflowTools.handles(n)) {
                return LocalWorkflowTools.description(n)
            }
            var common =
                " Private Eigentümerfunktion. Vor Änderung Datensatz und Hash abrufen; request_id bei Wiederholung gleich lassen. Lokale Speicherung, Provider-Rücklesen und Cloud-Bestätigung unterscheiden."
            when (n) {
                "get_ui_action_catalog" -> {
                    return "Liest die versionierte UI-MCP-Funktionsmatrix mit Werkzeugpfaden und notwendigen Geräte-/Kontofreigaben, paginiert."
                }
                "save_reviewed_order" -> {
                    return "Übernimmt eine ausdrücklich bestätigte Auftragsprüfung mit aktuellem Token aus inspect_order_review. Keine blinde Konfliktüberschreibung, Originalaktionen bleiben dokumentiert."
                }
                "preview_archive_work_records" -> {
                    return "Prüft eine ausdrückliche Auswahl von höchstens 200 Arbeitsaufzeichnungen samt Versionen, Fotozuordnungen und Finanzschutz. Keine Änderung. Liefert preview_hash für archive_work_records."
                }
                "archive_work_records" -> {
                    return ("Verschiebt die ausdrücklich bestätigte Auswahl anhand des unveränderten preview_hash atomar in den wiederherstellbaren Papierkorb. Bei nur einem geänderten/geschützten Eintrag keine Änderung. Bestehende Einzeljournale, Originalfotos und stabile Request-Belege bleiben erhalten." +
                        common)
                }
                "get_capabilities" -> {
                    return "Liest App-/MCP-Version, tatsächlich unterstützte Funktionen, Grenzen und Berechtigungen."
                }
                "read_file_chunk" -> {
                    return "Liest höchstens 48 KiB eines authentifizierten Originals anhand einer fachlichen Ressourcen-ID und SHA256; keine freien Pfade oder URLs. Base64 nur in diesem begrenzten Transfer."
                }
                "begin_file_upload",
                "write_file_chunk" -> {
                    return ("Überträgt ein Foto in begrenzten geprüften Blöcken in privaten Zwischenspeicher. Kein Geschäftseintrag vor create_photo." +
                        common)
                }
                "create_photo" -> {
                    return ("Prüft vollständigen Upload, Prüfsumme, Bildtyp und Verknüpfungen; optimiert neue Fotos gemäß dem gespeicherten Qualitätsprofil über denselben Fotospeicher wie die App. Der gespeicherte SHA256 kann vom geprüften Uploadhash abweichen; get_photo liest die endgültigen Metadaten." +
                        common)
                }
                "preview_empty_trash" -> {
                    return "Prüft den gesamten Papierkorb einschließlich interner Beziehungen; liefert Löschmenge, geschützte Restfälle und preview_hash. Keine Änderung."
                }
                "empty_trash" -> {
                    return ("Entfernt die bestätigte freigegebene Papierkorbmenge atomar mit aktuellem preview_hash und dauerhafter Request-ID. Aktive Referenzen und geschützte Belege bleiben erhalten. Eine gemeinsame Journalaktion; Datei-Cleanup erst nach geprüfter Veröffentlichung." +
                        common)
                }
                "preview_purge" -> {
                    return "Prüft endgültige Entfernung eines Papierkorbeintrags ohne Änderung; zeigt Abhängigkeiten und aktuellen Archivhash."
                }
                "purge_entity" -> {
                    return ("Löscht ausschließlich einen ausdrücklich bestätigten Papierkorbeintrag mit aktuellem Archivhash endgültig aus dem aktiven App-/Archivbestand. Kein Restore; minimale Löschquittung verhindert Wiederbelebung. Ungeteilte Mediendateien erst nach dauerhafter Sync-Übernahme bereinigen. Historische Sicherungen/Journale werden nicht überschrieben. " +
                        common)
                }
                "preview_delete" -> {
                    return "Prüft ohne Änderung die Löschregeln, Verknüpfungen und den aktuellen Vergleichshash. Löschungen bleiben im Papierkorb wiederherstellbar."
                }
                "delete_entity" -> {
                    return ("Entfernt einen zulässigen Eintrag versionsgeschützt in den bestehenden lokalen Papierkorb. Keine operative Drive-Synchronisierung, keine physische Löschung und keine Kaskade. Bei betroffenen Fotos expected_photo_links_hash aus preview_delete erforderlich. " +
                        common)
                }
                "archive_entity" -> {
                    return ("Entfernt einen fachlich zulässigen Eintrag aus aktiven Ansichten und bewahrt das Original im Archiv. Betroffene Fotozuordnungen lösen; bei Fotos expected_photo_links_hash aus preview_delete erforderlich. Keine physische Dateilöschung." +
                        common)
                }
                "restore_entity" -> {
                    return ("Stellt einen archivierten Eintrag mit Original-ID wieder her; bestehende IDs und Beziehungen werden nicht überschrieben." +
                        common)
                }
                "create_backup" -> {
                    return ("Erstellt die bisherige begrenzte Geschäftssicherung im vorhandenen Altformat für Geschäftssnapshot, Journal und lokale Originale. Keine verschlüsselte Vollsicherung und kein vollständiger Restore aller neuen Revier-/Jobdaten. Vollbackup über request_full_backup beziehungsweise die geschützte Geräteoberfläche verwenden." +
                        common)
                }
                "restore_backup" -> {
                    return ("Stillgelegter Teilrestore: in jedem Autoritätsmodus mit RESTORE_FULL_REPLACEMENT_REQUIRED gesperrt. Vollständigen verschlüsselten Bestand am Gerät prüfen und ausdrücklich ersetzen; keine automatische Vermischung." +
                        common)
                }
                "set_work_record_billed" -> {
                    return ("Ändert ausdrücklich die manuelle Abrechnungsmarkierung mit get_work_record.record_hash und Begründung. Kein Nachweis tatsächlicher Rechnungsstellung. Ausgestellte Leistungsdaten bleiben geschützt; persönliche b.b.-Markierung bleibt separat." +
                        common)
                }
                "reassign_work_record" -> {
                    return ("Ordnet eine vorhandene Arbeit ausdrücklich einem echten Einsatzkunden und Rechnungsempfänger zu. get_work_record.record_hash, beide Ziel-IDs und Begründung erforderlich. Foto-/Rechnungsverknüpfungen dürfen nicht widersprüchlich werden; historische Zuordnungen niemals erraten." +
                        common)
                }
                "set_personal_paid" -> {
                    return ("Ändert ausschließlich die persönliche Geräteübersicht b.b.; keine Änderung an billed oder Rechnungen." +
                        common)
                }
                "update_business_settings" -> {
                    return ("Ändert ausschließlich nicht geheime Aussteller- und Rechnungskonfiguration anhand get_business_settings.record_hash. Nummernkreis/externen Nummernstand nur nach tatsächlicher Prüfung bestätigen. Keine Zugangsdaten oder externen Kontorechte." +
                        common)
                }
                "update_settings" -> {
                    return ("Ändert freigegebene fachliche Gerätestandards. Zugangsdaten, Konten, Tunnelrechte und rechtliche Aussteller-/Nummernfreigaben bleiben separate Einstellungen." +
                        common)
                }
                else -> {
                    return (java.lang.String.valueOf(
                        ((if (read(n)) "Liest vorhandene App-Daten ohne Geheimwerte."
                        else "Verwendet die bestehenden App-Speicher und fachlichen Prüfungen."))
                    ) + common)
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun validate(name: String, args: JSONObject): Unit {

            McpSchema.validate(args, schema(name))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun photoHash(row: JSONObject): JSONObject {

            return DriveJournalRules.guard(row, "photo")
        }

    }
}
