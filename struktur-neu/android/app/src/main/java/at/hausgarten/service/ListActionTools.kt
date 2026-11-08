package at.hausgarten.service

import java.time.Clock
import org.json.JSONArray
import org.json.JSONObject

/** The five-second window precedes the shared writer, so undo preserves files and relations. */
internal object ListActionTools {
    const val KEY = "list_actions:v1"
    const val UNDO_MS = 5000L
    val READS = arrayOf("get_list_actions")
    val WRITES = arrayOf("queue_list_action", "undo_list_action")
    val COMMANDS =
        setOf(
            "delete_entity",
            "delete_work_records",
            "purge_revier_entity",
            "update_order",
            "update_visit",
            "update_appointment",
            "add_settlement_payment",
            "delete_settlement_draft",
        )

    fun handles(name: String) = name in READS || name in WRITES

    /** Order point reads return the record itself; appointment reads return an envelope. */
    fun completionArgs(entity: String, id: String, current: JSONObject): JSONObject {
        require(entity in setOf("order", "appointment"))
        val hash =
            if (entity == "order") {
                require(current.getString("id") == id)
                PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("order", current))
            } else current.getString("record_hash")
        return JSONObject()
            .put("${entity}_id", id)
            .put("expected_record_hash", hash)
            .put(
                "updates",
                JSONObject().put("status", if (entity == "order") "Erledigt" else "completed"),
            )
    }

    fun schema(name: String): JSONObject {
        require(handles(name))
        val fields = JSONObject()
        val required = mutableListOf<String>()
        if (name in WRITES) {
            fields.put("request_id", McpFields.text(128).put("minLength", 8))
            required.add("request_id")
        }
        if (name == "queue_list_action") {
            fields
                .put("original_tool", McpFields.text(60).put("enum", JSONArray(COMMANDS.toList())))
                .put(
                    "original_args",
                    JSONObject()
                        .put("type", "object")
                        .put("maxProperties", 30)
                        .put("additionalProperties", true),
                )
            required.addAll(listOf("original_tool", "original_args"))
        }
        if (name == "undo_list_action") {
            fields.put("action_id", McpFields.id())
            required.add("action_id")
        }
        return JSONObject()
            .put("type", "object")
            .put("properties", fields)
            .put("required", JSONArray(required))
            .put("additionalProperties", false)
    }

    fun description(name: String) =
        when (name) {
            "queue_list_action" ->
                "Plant genau eine Listenaktion mit fünf Sekunden Rückgängig-Frist. Erst nach Fristablauf führt derselbe Fachschreiber die unveränderten, versionsgeschützten Angaben aus. Ausgestellte Rechnungen bleiben geschützt. original_args aus aktuellem Datensatz bzw. Löschvorschau übernehmen; keine Preise oder Zeiten erfinden. Bei unklarer Antwort dieselbe request_id wiederholen und get_list_actions lesen."
            "undo_list_action" ->
                "Nimmt eine noch nicht ausgeführte Listenaktion innerhalb ihrer fünf Sekunden zurück. Daten, Dateien und Beziehungen bleiben erhalten. Nach Fristablauf keine Rücknahme; ursprüngliche Vorgangskennung bei Wiederholung erhalten."
            else ->
                "Liest noch offene Listenaktionen und ihre Rückgängig-Frist sowie bestätigte Ergebnisse oder echte Konflikte. Keine Fachaktion wird durch diese Abfrage ausgelöst."
        }

    fun rows(repository: OwnerRepository): MutableList<JSONObject> =
        SettlementRules.rows(JSONObject(repository.ownerValue(KEY, "{}")).optJSONArray("actions"))
            .toMutableList()

    fun save(repository: OwnerRepository, rows: List<JSONObject>) {
        val active =
            rows.filter { it.optString("state") in setOf("queued", "applying", "uncertain") }
        val ended = rows.filterNot { it in active }.takeLast(20)
        repository.putOwnerValue(
            KEY,
            JSONObject().put("actions", JSONArray(ended + active)).toString(),
        )
    }

    fun publicRow(row: JSONObject) =
        JSONObject(row.toString()).apply {
            remove("original_args")
            remove("restore_epoch")
        }

    fun targets(row: JSONObject): Set<Pair<String, String>> {
        val entity = row.optString("entity").let { if (it == "worklog_batch") "worklog" else it }
        val ids = row.optJSONArray("entity_ids") ?: JSONArray().put(row.getString("entity_id"))
        return (0 until ids.length()).map { entity to ids.getString(it) }.toSet()
    }
}

/** Called inside the existing owner request transaction, with its durable replay receipt. */
internal class ListActionService(
    private val repository: OwnerRepository,
    private val clock: Clock = Clock.systemUTC(),
) : OwnerCommand {
    fun query(): JSONObject =
        JSONObject()
            .put(
                "actions",
                JSONArray(ListActionTools.rows(repository).map(ListActionTools::publicRow)),
            )

    override fun perform(name: String, args: JSONObject, request: String): JSONObject {
        McpSchema.validate(args, ListActionTools.schema(name))
        val rows = ListActionTools.rows(repository)
        if (name == "undo_list_action") {
            val row =
                rows.singleOrNull { it.optString("id") == args.getString("action_id") }
                    ?: throw IllegalArgumentException("Listenaktion fehlt.")
            require(
                row.optString("state") == "queued" && clock.millis() < row.getLong("undo_until")
            ) {
                "Die Rückgängig-Frist ist abgelaufen."
            }
            row.put("state", "undone").remove("original_args")
            ListActionTools.save(repository, rows)
            return ListActionTools.publicRow(row)
        }
        require(name == "queue_list_action")
        val tool = args.getString("original_tool")
        require(tool in ListActionTools.COMMANDS)
        val original = JSONObject(args.getJSONObject("original_args").toString())
        require(original.toString().length <= 20000) { "Listenaktion ist zu groß." }
        val id = "la_" + PixelMcpWriteTools.hash(request).take(32)
        original.put("request_id", original.optString("request_id").ifBlank { "list-action-$id" })
        McpSchema.validate(
            original,
            if (tool == "update_order") PixelMcpEntityWriteTools.schema(tool)
            else if (AppointmentTools.write(tool)) AppointmentTools.schema(tool)
            else PixelMcpOwnerTools.schema(tool),
        )
        val (entity, entityId) = target(tool, original)
        val metadata =
            JSONObject()
                .put("entity", entity)
                .put("entity_id", entityId)
                .put(
                    "entity_ids",
                    if (tool == "delete_work_records") original.getJSONArray("work_record_ids")
                    else JSONArray().put(entityId),
                )
        val targets = ListActionTools.targets(metadata)
        require(
            rows.none {
                it.optString("state") in setOf("queued", "applying", "uncertain") &&
                    ListActionTools.targets(it).any(targets::contains)
            }
        ) {
            "Für diesen Eintrag läuft bereits eine Listenaktion."
        }
        require(
            rows.count { it.optString("state") in setOf("queued", "applying", "uncertain") } < 50
        ) {
            "Die laufenden Listenaktionen zuerst abwarten."
        }
        if (tool == "delete_entity") {
            val preview = DeletionRules.preview(repository.effective(), entity, entityId)
            require(preview.optBoolean("allowed")) {
                preview.optString("message", "Dieser Eintrag ist geschützt.")
            }
            require(
                preview.getString("expected_record_hash") ==
                    original.getString("expected_record_hash")
            ) {
                "Der Eintrag wurde inzwischen geändert."
            }
            ActiveWorkProtection.require(repository.state(), entity, entityId)
        }
        if (tool == "delete_work_records") {
            WorklogArchiveBatch.actions(
                repository.effective(),
                original.getJSONArray("work_record_ids"),
                original.getString("expected_preview_hash"),
                original.getString("request_id"),
                clock.instant().toString(),
            )
            val state = repository.state()
            for ((type, targetId) in targets) ActiveWorkProtection.require(state, type, targetId)
        }
        if (tool == "add_settlement_payment") {
            val ledger = SettlementRules.ledger(repository)
            val record = SettlementRules.find(ledger, entityId)
            require(
                record.optString("kind") == "internal" && record.optString("status") == "confirmed"
            ) {
                "Ausgestellte Rechnungen lassen sich nicht wegwischen."
            }
            require(SettlementRules.minor(original.get("amount")) > 0) {
                "Nur einen offenen Betrag als bezahlt markieren."
            }
            require(
                SettlementRules.minor(original.get("amount")) ==
                    record.optLong("total_minor") - SettlementRules.paid(ledger, entityId)
            ) {
                "Der offene Betrag wurde inzwischen geändert."
            }
        }
        if (tool == "delete_settlement_draft") {
            val record = SettlementRules.find(SettlementRules.ledger(repository), entityId)
            require(record.optString("status") == "draft") {
                "Vorbereitete oder ausgestellte Abschlüsse sind geschützt."
            }
            require(SettlementRules.hash(record) == original.getString("expected_record_hash")) {
                "Der Entwurf wurde inzwischen geändert."
            }
        }
        val row =
            metadata
                .put("id", id)
                .put("state", "queued")
                .put("original_tool", tool)
                .put("original_args", original)
                .put("undo_until", clock.millis() + ListActionTools.UNDO_MS)
                .put("restore_epoch", repository.settlementRestoreEpoch())
        rows.add(row)
        ListActionTools.save(repository, rows)
        return ListActionTools.publicRow(row)
    }

    /** Claim under the same SQLite transaction as undo; the original request survives a crash. */
    fun claimDue(): JSONObject? {
        val rows = ListActionTools.rows(repository)
        val row =
            rows.firstOrNull {
                it.optString("state") in setOf("queued", "applying", "uncertain") &&
                    it.optLong("undo_until") <= clock.millis()
            } ?: return null
        if (row.optString("restore_epoch") != repository.settlementRestoreEpoch()) {
            row.put("state", "failed")
                .put(
                    "message",
                    "Wiederhergestellte Listenaktion prüfen und bei Bedarf neu ausführen.",
                )
                .remove("original_args")
            ListActionTools.save(repository, rows)
            return null
        }
        row.put("state", "applying")
        ListActionTools.save(repository, rows)
        return JSONObject(row.toString())
    }

    fun finish(id: String, failure: String = "", uncertain: Boolean = false) {
        val rows = ListActionTools.rows(repository)
        val row = rows.single { it.optString("id") == id }
        require(row.optString("state") == "applying")
        row.put(
            "state",
            if (uncertain) "uncertain" else if (failure.isEmpty()) "done" else "failed",
        )
        if (failure.isNotEmpty()) row.put("message", failure)
        if (!uncertain) row.remove("original_args")
        ListActionTools.save(repository, rows)
    }

    private fun target(tool: String, args: JSONObject): Pair<String, String> {
        if (tool == "delete_work_records")
            return "worklog_batch" to PixelMcpWriteTools.hash(args.getJSONArray("work_record_ids"))
        if (tool in setOf("delete_entity", "purge_revier_entity"))
            return args.getString("entity") to args.getString("entity_id")
        if (tool in setOf("add_settlement_payment", "delete_settlement_draft"))
            return "settlement" to args.getString("settlement_id")
        val kind = tool.removePrefix("update_")
        val updates = args.getJSONObject("updates")
        require(
            updates.length() == 1 &&
                updates.optString("status") == if (kind == "order") "Erledigt" else "completed"
        ) {
            "Nach rechts wischen markiert nur als erledigt."
        }
        return kind to args.getString("${kind}_id")
    }
}
