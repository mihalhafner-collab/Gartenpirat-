package at.hausgarten.service

import java.util.*
import org.json.*

internal class LocalWorkflowTools {
    constructor() {}

    companion object {
        @JvmField
        val BACKUP_READS: Array<String> = arrayOf("get_full_backup_status", "get_restore_status")
        @JvmField
        val BACKUP_WRITES: Array<String> = arrayOf("request_full_backup", "retry_full_backup")
        @JvmField
        val REVIER_READS: Array<String> =
            arrayOf(
                "get_revier_overview",
                "inspect_edit_action",
                "list_properties",
                "get_property",
                "get_property_timeline",
                "get_linked_context",
                "list_inquiries",
                "get_inquiry",
                "list_care_plans",
                "list_visits",
                "get_visit",
                "get_day_tour",
                "get_planning_board",
                "get_tour_navigation",
                "list_materials",
                "list_equipment",
                "search_local",
                "get_equipment_maintenance",
            )
        @JvmField
        val REVIER_WRITES: Array<String> =
            arrayOf(
                "create_property",
                "update_property",
                "create_inquiry",
                "update_inquiry",
                "accept_inquiry_direct",
                "purge_revier_entity",
                "plan_inquiry_inspection",
                "create_care_plan",
                "update_care_plan",
                "generate_care_visits",
                "plan_visit",
                "update_visit",
                "set_day_tour",
                "move_tour_stop",
                "save_material",
                "record_material_consumption",
                "record_material_movement",
                "save_equipment",
                "save_equipment_maintenance_plan",
                "record_equipment_maintenance",
            )
        @JvmField val OFFER_DECISION_READS: Array<String> = arrayOf("get_offer_decision_history")
        @JvmField
        val OFFER_DECISION_WRITES: Array<String> =
            arrayOf("record_offer_acceptance", "decline_offer")
        @JvmField
        val READS: Array<String> =
            concat(
                concat(concat(BACKUP_READS, REVIER_READS), OFFER_DECISION_READS),
                ListActionTools.READS,
            )
        @JvmField
        val WRITES: Array<String> =
            concat(
                concat(concat(BACKUP_WRITES, REVIER_WRITES), OFFER_DECISION_WRITES),
                ListActionTools.WRITES,
            )

        @JvmStatic
        fun concat(first: Array<String>, second: Array<String>): Array<String> {
            var all = ArrayList<String>(first.asList())
            all.addAll(second.asList())
            return all.toTypedArray()
        }

        @JvmStatic
        fun backup(name: String): Boolean {
            return (BACKUP_READS.asList().contains(name) || BACKUP_WRITES.asList().contains(name))
        }

        @JvmStatic
        fun revier(name: String): Boolean {
            return (REVIER_READS.asList().contains(name) || REVIER_WRITES.asList().contains(name))
        }

        @JvmStatic
        fun critical(name: String): Boolean =
            name in setOf("purge_revier_entity", "queue_list_action")

        @JvmStatic
        fun offerDecision(name: String): Boolean {
            return (OFFER_DECISION_READS.asList().contains(name) ||
                OFFER_DECISION_WRITES.asList().contains(name))
        }

        @JvmStatic
        fun handles(name: String): Boolean {
            return backup(name) ||
                revier(name) ||
                offerDecision(name) ||
                ListActionTools.handles(name)
        }

        @JvmStatic
        fun write(name: String): Boolean {
            return WRITES.asList().contains(name)
        }

        @JvmStatic
        fun description(name: String): String {
            if (
                name in
                    setOf(
                        "list_care_plans",
                        "create_care_plan",
                        "update_care_plan",
                        "generate_care_visits",
                    )
            )
                return "Entfernter Objektpflegeweg: antwortet kompatibel mit FUNCTION_REMOVED, ohne Datenänderung oder neue Termine. Historische Objekte, Fotos und Bezüge bleiben erhalten."

            if (name == "get_linked_context")
                return "Liest bekannte Elternbezüge für Kunden, Objekte, Anfragen, Angebote, Aufträge, Einsätze, Arbeiten und Termine. Berücksichtigt tatsächliche Rückverweise und bei Terminen gültige Angebotspositionen. Keine Zuordnung nur über denselben Kunden; widersprüchliche oder mehrdeutige Bezüge liefern context_conflict. Rein lesend, keine Änderung der ursprünglichen Zuordnungen."
            if (name == "get_property_timeline")
                return "Liest den historischen Objektverlauf von Anfrage bis Zahlung sowie Fotos und Geld. Objektpflege und zugehörige Planungslinks sind entfernt. Nur ausdrückliche Objektverknüpfungen und bekannte Elternbezüge, keine Zuordnung allein über denselben Kunden. Belegbeträge gelten für den ganzen Beleg, keine erfundene Objektsumme. Reine lokale Leseabfrage mit begrenzter Seite; Fotos ohne Binärdaten."
            if (name == "get_planning_board")
                return "Liest Einsätze und Termine gemeinsam für Tag oder Woche: begrenzte Seite mit aktuellem record_hash, echten Zeitfenstern, ausdrücklichen Objektbezügen und Wetterhinweisen aus dem vorhandenen Cache. Keine Netzabfrage, keine Umplanung und keine erfundenen Zeiten oder Koordinaten. Verschieben über update_visit bzw. update_appointment mit dem gelieferten Hash."
            if (name == "get_tour_navigation")
                return "Liefert die Übergabe an die Karten-App zum ersten gespeicherten Stopp der Tagestour. Benötigt aktuellen Tourhash; ungespeicherte oder prüfpflichtige Reihenfolge zuerst bestätigen. Keine Routenoptimierung, kein Kartendienst und keine Datenänderung. visit_ids bleiben in gespeicherter Reihenfolge."
            if (ListActionTools.handles(name)) return ListActionTools.description(name)
            if (name == "record_material_movement")
                return "Verweis für Version 1.71.0: record_material_consumption. Verbrauch an einer gespeicherten Arbeit."
            if (name == "record_material_consumption")
                return "Erfasst eine positive tatsächliche Materialmenge an einer Arbeit (worklog_id) oder deren laufender Sitzung (session_id); beim Arbeitsspeichern wird der Verbrauch atomar zugeordnet. Genau einen der beiden Bezüge und aktuellen Materialhash verwenden; gleicher request_id bucht höchstens einmal."
            if (name == "save_equipment") {
                return "Speichert ein lokales Gerät mit unveränderter Wartung. Optional profile_photo_upload_id aus begin_file_upload/write_file_chunk: geprüfte Fotozuordnung ausschließlich zu diesem Gerät, kein Kundenfoto. Bestehendes Gerät benötigt equipment_id und aktuellen expected_record_hash. list_equipment liefert finale profile_photo-Metadaten; read_file_chunk mit resource_type photo liest das aktuell zugeordnete Original. request_id bei unklarer Antwort erhalten und zuerst inspect_edit_action lesen."
            }
            if (name == "get_equipment_maintenance") {
                return "Liest eigene Wartungspläne und paginierten erledigten Wartungsverlauf genau eines lokalen Geräts, einschließlich nächster Fälligkeit und record_hash. Kein Schreiben, keine Gerätefreigabe, keine Herstellerintervalle oder automatische Erledigung."
            }
            if (name == "save_equipment_maintenance_plan") {
                return "Speichert einen ausdrücklich gewählten eigenen Wartungsplan am vorhandenen Gerät. configure benötigt Tätigkeit, Kalenderintervall und starts_on; Änderungen benötigen Grund und aktuellen Gerätehash. bring_forward zieht nur den nächsten Termin vor; shorten verkürzt das Intervall dauerhaft. Keine erledigte Wartung, Zustandsfreigabe, Herstellerempfehlung oder Drive-Arbeit. request_id bei unklarer Antwort erhalten und zuerst lesend prüfen."
            }
            if (name == "record_equipment_maintenance") {
                return "Erfasst eine tatsächlich erledigte Wartung am vorhandenen Plan mit explizitem performed_on, aktuellem Gerätehash und stabiler request_id. Kein Zukunftsdatum. Ein älterer Nachtrag verdrängt keine neuere Erledigung. Optionale kürzere Intervalle nur mit Grund, etwa Fehlschnitten oder erneutem Nachschärfen. Historie bleibt erhalten; Gerätezustand und Backup bleiben unabhängig."
            }
            if (name == "get_restore_status") {
                return "Liest ausschließlich den nicht geheimen Zustand einer am Gerät bestätigten vollständigen Wiederherstellung: Phase, Vorschauhash, Neustartbedarf und lokale Integritätsprüfung. Kein Recovery-Code, Dateipfad oder Inhalt und keine Aktivierung durch Lesen."
            }
            if (name == "get_offer_decision_history") {
                return "Liest Annahme-/Ablehnungshistorie der unveränderlichen Angebotsversion mit Nachweis und Zeitpunkt. Kein Versand-/Zustellbeleg aus bloßem Teilen."
            }
            if (name == "record_offer_acceptance") {
                return "Hält die ausdrücklich bestätigte Annahme genau dieser finalen Angebotsversion mit tatsächlichem Zeitpunkt und nachvollziehbarem Nachweis fest. record_hash prüfen; legt im selben Vorgang genau einen Auftrag an. request_id bei Retry unverändert lassen."
            }
            if (name == "decline_offer") {
                return "Hält die ausdrückliche Ablehnung genau dieser finalen Angebotsversion samt optionalem Grund nachvollziehbar fest. Dokument und Historie bleiben erhalten; kein Auftrag und keine Rechnung entstehen."
            }
            if (name == "get_full_backup_status") {
                return "Liest den lokalen Status unverschlüsselter ZIP-Sicherungen auf dem Pixel. Kein Drive, Konto, Schlüssel oder Hintergrundupload. Vollständige Dateiwahl und Wiederherstellung erfolgen bewusst am Gerät."
            }
            if (name == "request_full_backup") {
                return "Liefert den Hinweis, die lokale Sicherungsansicht und Android-Dateiwahl auf dem Pixel zu öffnen. Startet keinen Upload und erzeugt ohne Dateiwahl keine Sicherung. Zuerst get_full_backup_status lesen; request_id bei Wiederholung erhalten."
            }
            if (name == "retry_full_backup") {
                return "Liefert den Hinweis, einen bestehenden lokalen Speicherversuch in der Sicherungsansicht am Pixel fortzusetzen. Keine neue Sicherung oder Dateifreigabe durch MCP; kein Drive-Zugriff."
            }
            if (name == "inspect_edit_action") {
                return "Prüft ausschließlich lesend den vorhandenen Beleg einer ursprünglichen Editoraktion mit unveränderten Argumenten. state=confirmed, content_removed oder unknown; keine Wiederholung einer Geschäftsaktion und keine Freigabe allein anhand restaurierter Requests. Unbekanntes Ergebnis separat anhand aktueller Originale klären."
            }
            if (name == "purge_revier_entity") {
                return "Entfernt genau einen aktiven oder früher archivierten Revier-Eintrag endgültig nach aktueller Versions- und Abhängigkeitsprüfung. Die lokale Oberfläche führt die beschriftete Löschung direkt aus. Minimale Lösch-/Request-Belege verhindern Wiederbelebung; historische Vollsicherungen bleiben gesondert erhalten. Keine produktive Löschung durch Tests."
            }
            if (name == "accept_inquiry_direct") {
                return "Nimmt eine Anfrage ausdrücklich ohne vorheriges Angebot an: tatsächlichen Annahmezeitpunkt, Nachweis und gegebenenfalls getrennten Rechnungsempfänger angeben. Aktuellen record_hash prüfen. Derselbe request_id erzeugt höchstens einen verknüpften Auftrag; kein Rechnungsversand."
            }
            return (java.lang.String.valueOf(
                (if (write(name))
                    "Speichert den ausdrücklich beauftragten lokalen Revier-Vorgang mit request_id und Versionsschutz. "
                else
                    "Liest den vollständigen lokalen Revier-Bestand mit Typ-/Status-/Zeitraumfiltern und Pagination. ")
            ) +
                "Kunden, Objekte, Rechnungsempfänger und tatsächliche Arbeit bleiben getrennt. Historische Zuordnungen und Zeiten nicht erraten; keine operative Drive-Synchronisierung oder automatische Rechnung auslösen.")
        }

        private @JvmStatic @Throws(Exception::class) fun date(): JSONObject {
            return McpFields.text(10).put("pattern", "[0-9]{4}-[0-9]{2}-[0-9]{2}")
        }

        private @JvmStatic @Throws(Exception::class) fun clock(): JSONObject {
            return McpFields.text(5).put("pattern", "(?:[01][0-9]|2[0-3]):[0-5][0-9]")
        }

        private @JvmStatic @Throws(Exception::class) fun clearable(field: JSONObject): JSONObject {
            field.remove("minLength")
            if (field.has("pattern")) {
                field.put("pattern", (("(?:" + field.getString("pattern")) + ")?"))
            }
            return field
        }

        private @JvmStatic @Throws(Exception::class) fun instant(): JSONObject {
            return McpFields.text(80).put("minLength", 1)
        }

        private @JvmStatic @Throws(Exception::class) fun decimal(signed: Boolean): JSONObject {
            return McpFields.text(64)
                .put(
                    "pattern",
                    (java.lang.String.valueOf(if (signed) "-?" else "") +
                        "(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?"),
                )
        }

        /** Retained for material purchase/billing prices, not invoice payments. */
        private @JvmStatic fun minor(): JSONObject =
            JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 9000000000000000L)

        private @JvmStatic @Throws(Exception::class) fun array(
            item: JSONObject,
            max: Int,
        ): JSONObject {
            return JSONObject().put("type", "array").put("items", item).put("maxItems", max)
        }

        private @JvmStatic @Throws(Exception::class) fun materialNeeds(): JSONObject {
            return array(
                McpFields.`object`(
                    JSONObject()
                        .put("material_id", McpFields.id())
                        .put("quantity_decimal", decimal(false)),
                    "material_id",
                    "quantity_decimal",
                ),
                100,
            )
        }

        private @JvmStatic @Throws(Exception::class) fun list(p: JSONObject): Unit {
            p.put("offset", McpFields.number(0, 1000000))
                .put("limit", McpFields.number(1, 100))
                .put("query", McpFields.text(200))
                .put("status", McpFields.text(80))
                .put("customer_id", McpFields.id())
                .put("property_id", McpFields.id())
                .put("from_date", date())
                .put("to_date", date())
        }

        private @JvmStatic @Throws(Exception::class) fun propertyFields(
            create: Boolean
        ): JSONObject {
            var p =
                JSONObject()
                    .put("name", McpFields.text(500).put("minLength", 1))
                    .put("address", McpFields.text(4000))
                    .put("district", McpFields.text(500))
                    .put("access_notes", McpFields.text(10000))
                    .put("notes", McpFields.text(20000))
                    .put("area_decimal", decimal(false))
                    .put("area_unit", McpFields.text(100))
                    .put(
                        "latitude",
                        JSONObject().put("type", "number").put("minimum", -(90)).put("maximum", 90),
                    )
                    .put(
                        "longitude",
                        JSONObject()
                            .put("type", "number")
                            .put("minimum", -(180))
                            .put("maximum", 180),
                    )
                    .put("active", McpFields.bool())
            if (create) {
                p.put("customer_id", McpFields.id())
            } else {
                p.put("area_decimal", clearable(decimal(false)))
            }
            return p
        }

        private @JvmStatic @Throws(Exception::class) fun inquiryFields(
            create: Boolean
        ): JSONObject {
            var p =
                JSONObject()
                    .put("title", McpFields.text(500).put("minLength", 1))
                    .put("property_id", McpFields.id())
                    .put("concern", McpFields.text(20000))
                    .put("wish_from", date())
                    .put("wish_until", date())
                    .put(
                        "status",
                        McpFields.enumeration(
                            "new",
                            "inspection_planned",
                            "offered",
                            "accepted",
                            "declined",
                            "closed",
                        ),
                    )
            if (create) {
                p.put("customer_id", McpFields.id()).put("received_at", instant())
            } else {
                p.put("property_id", McpFields.text(500))
                    .put("wish_from", clearable(date()))
                    .put("wish_until", clearable(date()))
                    .put("inspection_notes", McpFields.text(10000))
                    .put(
                        "measurements",
                        array(
                            McpFields.`object`(
                                JSONObject()
                                    .put("name", McpFields.text(500).put("minLength", 1))
                                    .put("value_decimal", decimal(true))
                                    .put("unit", McpFields.text(100)),
                                "name",
                                "value_decimal",
                            ),
                            200,
                        ),
                    )
                    .put("photo_ids", array(McpFields.id(), 200))
                    .put("offer_id", McpFields.text(500))
                    .put("order_id", McpFields.text(500))
            }
            return p
        }

        private @JvmStatic @Throws(Exception::class) fun careFields(create: Boolean): JSONObject {
            var p =
                JSONObject()
                    .put("title", McpFields.text(500).put("minLength", 1))
                    .put("anchor_date", date())
                    .put("interval_days", McpFields.number(1, 365))
                    .put("planned_minutes", McpFields.number(1, 1440))
                    .put("season_start", McpFields.text(5).put("pattern", "[0-9]{2}-[0-9]{2}"))
                    .put("season_end", McpFields.text(5).put("pattern", "[0-9]{2}-[0-9]{2}"))
                    .put("material_needs", materialNeeds())
                    .put("equipment_ids", array(McpFields.id(), 100))
                    .put("active", McpFields.bool())
                    .put("notes", McpFields.text(20000))
            if (create) {
                p.put("property_id", McpFields.id())
            } else {
                p.put("season_start", McpFields.text(5).put("pattern", "(?:[0-9]{2}-[0-9]{2})?"))
                    .put("season_end", McpFields.text(5).put("pattern", "(?:[0-9]{2}-[0-9]{2})?"))
            }
            return p
        }

        private @JvmStatic @Throws(Exception::class) fun visitFields(create: Boolean): JSONObject {
            var p =
                JSONObject()
                    .put("date", date())
                    .put("title", McpFields.text(500).put("minLength", 1))
                    .put("planned_minutes", McpFields.number(1, 1440))
                    .put("window_start", clock())
                    .put("window_end", clock())
                    .put("notes", McpFields.text(20000))
                    .put("material_needs", materialNeeds())
                    .put("equipment_ids", array(McpFields.id(), 100))
            if (create) {
                p.put("property_id", McpFields.id())
                    .put("order_id", McpFields.id())
                    .put("parent_visit_id", McpFields.id())
            } else {
                p.put(
                        "status",
                        McpFields.enumeration(
                            "planned",
                            "in_progress",
                            "completed",
                            "partial",
                            "cancelled",
                        ),
                    )
                    .put("remaining_minutes", McpFields.number(0, 1440))
                    .put("planning_adopted", McpFields.bool())
                    .put("window_start", clearable(clock()))
                    .put("window_end", clearable(clock()))
            }
            return p
        }

        @JvmStatic
        @Throws(Exception::class)
        fun schema(name: String): JSONObject {
            if (name == "get_linked_context") return PropertyTimelineRules.contextSchema()
            if (name == "get_property_timeline") return PropertyTimelineRules.schema()
            if (name == "get_planning_board") return PlanningBoardRules.boardSchema()
            if (ListActionTools.handles(name)) return ListActionTools.schema(name)
            if (!(handles(name))) {
                throw IllegalArgumentException("Lokaler Werkzeugvertrag fehlt.")
            }
            var p = JSONObject()
            val required = ArrayList<String>()
            if (write(name)) {
                p.put("request_id", McpFields.text(128).put("minLength", 8))
                required.add("request_id")
            }
            when (name) {
                "get_restore_status" -> {}
                "get_offer_decision_history" -> {
                    p.put("offer_id", McpFields.id())
                        .put("offset", McpFields.number(0, 1000000))
                        .put("limit", McpFields.number(1, 100))
                    required.add("offer_id")
                }
                "record_offer_acceptance" -> {
                    p.put("offer_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("accepted_at", instant())
                        .put("evidence", McpFields.text(10000).put("minLength", 1))
                    required.addAll(
                        Arrays.asList("offer_id", "expected_record_hash", "accepted_at", "evidence")
                    )
                }
                "decline_offer" -> {
                    p.put("offer_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("reason", McpFields.text(4000))
                    required.addAll(Arrays.asList("offer_id", "expected_record_hash"))
                }
                "get_full_backup_status" -> {
                    p.put("offset", McpFields.number(0, 1000000))
                        .put("limit", McpFields.number(1, 100))
                        .put("backup_id", McpFields.id())
                }
                "request_full_backup" -> {
                    p.put("expected_settings_hash", McpFields.hash())
                    required.add("expected_settings_hash")
                }
                "retry_full_backup" -> {
                    p.put("backup_id", McpFields.id()).put("expected_record_hash", McpFields.hash())
                    required.addAll(Arrays.asList("backup_id", "expected_record_hash"))
                }
                "inspect_edit_action" -> {
                    p.put("original_tool", McpFields.text(100))
                        .put("original_args", JSONObject().put("type", "object"))
                    required.addAll(Arrays.asList("original_tool", "original_args"))
                }
                "get_revier_overview" -> {
                    p.put("date", date())
                }
                "list_properties",
                "list_inquiries",
                "list_care_plans",
                "list_visits",
                "list_materials",
                "list_equipment" -> {
                    list(p)
                    if (name == "list_care_plans") {
                        p.put("care_plan_id", McpFields.id())
                    }
                }
                "get_property" -> {
                    p.put("property_id", McpFields.id())
                    required.add("property_id")
                }
                "get_inquiry" -> {
                    p.put("inquiry_id", McpFields.id())
                    required.add("inquiry_id")
                }
                "get_visit" -> {
                    p.put("visit_id", McpFields.id())
                    required.add("visit_id")
                }
                "get_day_tour",
                "get_tour_navigation" -> {
                    p.put("date", date())
                    required.add("date")
                    if (name == "get_tour_navigation") {
                        p.put("expected_record_hash", McpFields.hash())
                        required.add("expected_record_hash")
                    }
                }
                "create_property" -> {
                    p = merge(p, propertyFields(true))
                    required.addAll(Arrays.asList("customer_id", "name"))
                }
                "update_property" -> {
                    p.put("property_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("updates", McpFields.`object`(propertyFields(false)))
                        .put("reason", McpFields.text(4000))
                    required.addAll(Arrays.asList("property_id", "expected_record_hash", "updates"))
                }
                "create_inquiry" -> {
                    p = merge(p, inquiryFields(true))
                    required.addAll(Arrays.asList("customer_id", "title"))
                }
                "update_inquiry" -> {
                    p.put("inquiry_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("updates", McpFields.`object`(inquiryFields(false)))
                        .put("reason", McpFields.text(4000))
                    required.addAll(Arrays.asList("inquiry_id", "expected_record_hash", "updates"))
                }
                "purge_revier_entity" -> {
                    p.put(
                            "entity",
                            McpFields.enumeration(
                                "property",
                                "inquiry",
                                "care_plan",
                                "visit",
                                "material",
                                "equipment",
                            ),
                        )
                        .put("entity_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("reason", McpFields.text(4000))
                        .put("confirmed", McpFields.bool())
                    required.addAll(
                        Arrays.asList("entity", "entity_id", "expected_record_hash", "confirmed")
                    )
                }
                "accept_inquiry_direct" -> {
                    p.put("inquiry_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("accepted_at", instant())
                        .put("evidence", McpFields.text(4000))
                        .put("billing_customer_id", McpFields.id())
                        .put("confirmed", McpFields.bool())
                    required.addAll(
                        Arrays.asList(
                            "inquiry_id",
                            "expected_record_hash",
                            "accepted_at",
                            "evidence",
                            "confirmed",
                        )
                    )
                }
                "plan_inquiry_inspection" -> {
                    p.put(
                            "mode",
                            McpFields.enumeration("first", "reschedule", "additional", "reopen"),
                        )
                        .put("expected_appointment_hash", McpFields.hash())
                        .put("inquiry_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("starts_at", instant())
                        .put("ends_at", instant())
                        .put("timezone", McpFields.text(100))
                        .put("notes", McpFields.text(4000))
                    required.addAll(
                        Arrays.asList("inquiry_id", "expected_record_hash", "starts_at", "ends_at")
                    )
                }
                "create_care_plan" -> {
                    p = merge(p, careFields(true))
                    required.addAll(
                        Arrays.asList(
                            "property_id",
                            "title",
                            "anchor_date",
                            "interval_days",
                            "planned_minutes",
                        )
                    )
                }
                "update_care_plan" -> {
                    p.put("care_plan_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("scope", McpFields.enumeration("this_and_future"))
                        .put("effective_from", date())
                        .put("updates", McpFields.`object`(careFields(false)))
                        .put("reason", McpFields.text(4000))
                    required.addAll(
                        Arrays.asList(
                            "care_plan_id",
                            "expected_record_hash",
                            "scope",
                            "effective_from",
                            "updates",
                        )
                    )
                }
                "generate_care_visits" -> {
                    p.put("care_plan_id", McpFields.id())
                        .put("from_date", date())
                        .put("to_date", date())
                    required.addAll(Arrays.asList("from_date", "to_date"))
                }
                "plan_visit" -> {
                    p = merge(p, visitFields(true))
                    required.addAll(
                        Arrays.asList("property_id", "date", "title", "planned_minutes")
                    )
                }
                "update_visit" -> {
                    p.put("visit_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("updates", McpFields.`object`(visitFields(false)))
                        .put("scope", McpFields.enumeration("only_this"))
                        .put("reason", McpFields.text(4000))
                    required.addAll(Arrays.asList("visit_id", "expected_record_hash", "updates"))
                }
                "set_day_tour" -> {
                    p.put("date", date())
                        .put("visit_ids", array(McpFields.id(), 100))
                        .put("expected_record_hash", McpFields.hash())
                        .put("district", McpFields.text(500))
                    required.addAll(Arrays.asList("date", "visit_ids", "expected_record_hash"))
                }
                "move_tour_stop" -> {
                    p.put("date", date())
                        .put("visit_id", McpFields.id())
                        .put("direction", McpFields.enumeration("up", "down"))
                        .put("expected_record_hash", McpFields.hash())
                    required.addAll(
                        Arrays.asList("date", "visit_id", "direction", "expected_record_hash")
                    )
                }
                "save_material" -> {
                    p.put("material_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("name", McpFields.text(500).put("minLength", 1))
                        .put("unit", McpFields.text(100).put("minLength", 1))
                        .put(
                            "purchase_price_minor",
                            minor().put("type", JSONArray().put("integer").put("null")),
                        )
                        .put(
                            "billing_price_minor",
                            minor().put("type", JSONArray().put("integer").put("null")),
                        )
                        .put("notes", McpFields.text(20000))
                        .put("active", McpFields.bool())
                }
                "record_material_consumption",
                "record_material_movement" -> {
                    p.put("material_id", McpFields.id())
                        .put("worklog_id", McpFields.id())
                        .put("session_id", McpFields.id())
                        .put("quantity_decimal", decimal(false))
                        .put("kind", McpFields.enumeration("consume"))
                        .put("reason", McpFields.text(4000))
                        .put("expected_record_hash", McpFields.hash())
                    required.addAll(
                        Arrays.asList("material_id", "quantity_decimal", "expected_record_hash")
                    )
                }
                "save_equipment" -> {
                    p.put(
                            "accessories",
                            array(
                                McpFields.`object`(
                                    JSONObject()
                                        .put("id", McpFields.id())
                                        .put("type", McpFields.enumeration("chain", "other"))
                                        .put("name", McpFields.text(240))
                                        .put("manufacturer", McpFields.text(240))
                                        .put("model", McpFields.text(240))
                                        .put("pitch", McpFields.text(80))
                                        .put("gauge", McpFields.text(80))
                                        .put("drive_links", McpFields.text(80))
                                        .put("article_number", McpFields.text(240))
                                        .put("tooth_type", McpFields.text(240))
                                        .put("file_size", McpFields.text(80))
                                        .put("notes", McpFields.text(10000))
                                        .put("photo_upload_id", McpFields.id()),
                                    "id",
                                    "type",
                                ),
                                100,
                            ),
                        )
                        .put("current_chain_id", McpFields.text(200))
                        .put("profile_photo_upload_id", McpFields.id())
                        .put("equipment_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("name", McpFields.text(500).put("minLength", 1))
                        .put(
                            "condition",
                            McpFields.enumeration("ready", "maintenance", "defective"),
                        )
                        .put("maintenance_due", clearable(date()))
                        .put("notes", McpFields.text(20000))
                        .put("active", McpFields.bool())
                }
                "get_equipment_maintenance" -> {
                    p.put("equipment_id", McpFields.id())
                        .put("offset", McpFields.number(0, 1000000))
                        .put("limit", McpFields.number(1, 100))
                    required.add("equipment_id")
                }
                "save_equipment_maintenance_plan" -> {
                    p.put("equipment_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("plan_id", McpFields.id())
                        .put(
                            "operation",
                            McpFields.enumeration("configure", "bring_forward", "shorten"),
                        )
                        .put("title", McpFields.text(240))
                        .put("interval_value", McpFields.number(1, 3650))
                        .put(
                            "interval_unit",
                            McpFields.enumeration("days", "weeks", "months", "years"),
                        )
                        .put("starts_on", date())
                        .put("next_due_on", date())
                        .put("reason", McpFields.text(2000))
                    required.addAll(
                        Arrays.asList("equipment_id", "expected_record_hash", "operation")
                    )
                }
                "record_equipment_maintenance" -> {
                    p.put("equipment_id", McpFields.id())
                        .put("expected_record_hash", McpFields.hash())
                        .put("plan_id", McpFields.id())
                        .put("performed_on", date())
                        .put("reason", McpFields.text(2000))
                        .put("notes", McpFields.text(4000))
                        .put("shorter_interval_value", McpFields.number(1, 3650))
                        .put(
                            "shorter_interval_unit",
                            McpFields.enumeration("days", "weeks", "months", "years"),
                        )
                    required.addAll(
                        Arrays.asList(
                            "equipment_id",
                            "expected_record_hash",
                            "plan_id",
                            "performed_on",
                        )
                    )
                }
                "search_local" -> {
                    list(p)
                    p.put(
                        "types",
                        array(
                            McpFields.enumeration(
                                "customer",
                                "property",
                                "order",
                                "appointment",
                                "visit",
                                "activity",
                                "note",
                                "offer",
                                "invoice",
                                "inquiry",
                                "care_plan",
                                "material",
                                "equipment",
                                "photo",
                                "settlement",
                            ),
                            15,
                        ),
                    )
                    p.put("billing_customer_id", McpFields.id())
                        .put("work_record_id", McpFields.id())
                        .put("city", McpFields.text(500))
                        .put("category", McpFields.text(500))
                        .put("amount_from", McpFields.text(100))
                        .put("amount_to", McpFields.text(100))
                        .put(
                            "date_basis",
                            McpFields.enumeration(
                                "default",
                                "work",
                                "document",
                                "payment",
                                "planned",
                                "created",
                                "updated",
                                "photo",
                            ),
                        )
                    for (field in
                        listOf(
                            "lcr",
                            "archived",
                            "without_customer",
                            "without_time",
                            "open_work",
                            "maintenance_due",
                        )) p.put(field, McpFields.bool())
                }
                else -> {
                    throw IllegalArgumentException("Lokaler Werkzeugvertrag fehlt.")
                }
            }
            return McpFields.`object`(p, *required.toTypedArray())
        }

        private @JvmStatic @Throws(Exception::class) fun merge(
            target: JSONObject,
            fields: JSONObject,
        ): JSONObject {
            run {
                val keys = fields.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    target.put(key, fields.get(key))
                }
            }
            return target
        }
    }
}
