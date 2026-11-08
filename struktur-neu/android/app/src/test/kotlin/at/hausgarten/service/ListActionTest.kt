package at.hausgarten.service

import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ListActionTest {
    private class TestClock(var now: Long = 100000L) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    private class Sandbox {
        val values = linkedMapOf<String, String>()
        var master = SyntheticBusinessData.profile("minimal")
        var epoch = "qa_list_epoch_a"
        var beforeValues = mapOf<String, String>()
        var beforeMaster = ""
        var successful = false
        var writes = 0
        val clock = TestClock()
        val transactions =
            object : OwnerRequestTransactions.Store {
                override fun begin() {
                    beforeValues = values.toMap()
                    beforeMaster = master.toString()
                    successful = false
                }

                override fun read(key: String) = values[key].orEmpty()

                override fun write(key: String, value: String) {
                    values[key] = value
                }

                override fun successful() {
                    successful = true
                }

                override fun end() {
                    if (!successful) {
                        values.clear()
                        values.putAll(beforeValues)
                        master = JSONObject(beforeMaster)
                    }
                }
            }
        val repository =
            Proxy.newProxyInstance(
                OwnerRepository::class.java.classLoader,
                arrayOf(OwnerRepository::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "ownerValue" -> values[args!![0] as String] ?: args[1]
                    "putOwnerValue" -> {
                        values[args!![0] as String] = args[1] as String
                        Unit
                    }
                    "effective" -> JSONObject(master.toString())
                    "state" ->
                        JSONObject()
                            .put("master", JSONObject(master.toString()))
                            .put("pending", JSONArray())
                            .put("work_session", JSONObject().put("state", "IDLE"))
                    "settlementRestoreEpoch" -> epoch
                    "pendingActions" -> JSONArray()
                    "enqueue" -> {
                        writes++
                        val event =
                            JSONObject()
                                .put("kind", args!![0])
                                .put("payload", args[1])
                                .put("client_id", args[2])
                                .put("created_at", "2026-11-08T10:00:00Z")
                        OwnerJournalRules.apply(master, event, java.util.HashMap(), JSONObject())
                        JSONObject().put("client_id", args[2]).put("local_state", "local_saved")
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as OwnerRepository

        fun service() = ListActionService(repository, clock)

        fun command(tool: String, args: JSONObject): JSONObject =
            OwnerRequestTransactions.runRequest(
                transactions,
                args.getString("request_id"),
                PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", args)),
                { service().perform(tool, args, args.getString("request_id")) },
                clock.instant().toString(),
            )

        fun original(row: JSONObject): JSONObject {
            val tool = row.getString("original_tool")
            val args = row.getJSONObject("original_args")
            return OwnerRequestTransactions.runRequest(
                transactions,
                args.getString("request_id"),
                PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", args)),
                {
                    (if (tool == "add_settlement_payment") SettlementApplicationService(repository)
                        else ArchiveApplicationService(repository))
                        .perform(tool, args, args.getString("request_id"))
                },
                clock.instant().toString(),
            )
        }

        fun financial(tool: String, args: JSONObject): JSONObject {
            McpSchema.validate(args, SettlementTools.schema(tool))
            return OwnerRequestTransactions.runRequest(
                transactions,
                args.getString("request_id"),
                PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", args)),
                {
                    SettlementApplicationService(repository)
                        .perform(tool, args, args.getString("request_id"))
                },
                clock.instant().toString(),
            )
        }

        fun work(id: String): JSONObject {
            val row =
                JSONObject()
                    .put("id", id)
                    .put("customer_id", "qa_customer_1")
                    .put("activity", "[TEST] QA Wischaktion")
                    .put("date", "2026-11-08")
                    .put("hours", 1)
                    .put("billed", false)
                    .put("test_record", true)
            master.getJSONArray("worklogs").put(row)
            return row
        }

        fun queue(id: String, request: String): JSONObject {
            val row = OwnerJournalRules.findOrNull(master.getJSONArray("worklogs"), id)!!
            return JSONObject()
                .put("request_id", request)
                .put("original_tool", "delete_entity")
                .put(
                    "original_args",
                    JSONObject()
                        .put("entity", "worklog")
                        .put("entity_id", id)
                        .put("expected_record_hash", OwnerJournalRules.version(row)),
                )
        }
    }

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("Aktion muss abgewiesen werden")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun undoKeepsTheOriginalAndRepeatedRequestsDoNotQueueAgain() {
        val s = Sandbox()
        s.work("qa_list_undo")
        val original = s.master.toString()
        val args = s.queue("qa_list_undo", "qa_list_stage_undo")
        val staged = s.command("queue_list_action", args)
        assertEquals(staged.getString("id"), s.command("queue_list_action", args).getString("id"))
        assertEquals(1, ListActionTools.rows(s.repository).size)
        assertEquals(original, s.master.toString())
        s.clock.now += 4999
        val undo =
            JSONObject()
                .put("request_id", "qa_list_undo_request")
                .put("action_id", staged.getString("id"))
        assertEquals("undone", s.command("undo_list_action", undo).getString("state"))
        assertTrue(s.command("undo_list_action", undo).getBoolean("replayed"))
        s.clock.now += 10000
        assertNull(s.service().claimDue())
        assertEquals(original, s.master.toString())
        assertEquals(0, s.writes)
    }

    @Test
    fun expiredDeletionUsesTheActualWriterAndSurvivesACrashBeforeAcknowledgement() {
        val s = Sandbox()
        s.work("qa_list_delete")
        val staged =
            s.command("queue_list_action", s.queue("qa_list_delete", "qa_list_stage_delete"))
        assertNull(s.service().claimDue())
        s.clock.now += 5000
        rejected {
            s.command(
                "undo_list_action",
                JSONObject()
                    .put("request_id", "qa_list_too_late")
                    .put("action_id", staged.getString("id")),
            )
        }
        val first = s.service().claimDue()!!
        s.original(first)
        val writes = s.writes
        val afterRestart = s.service().claimDue()!!
        assertTrue(s.original(afterRestart).getBoolean("replayed"))
        assertEquals(writes, s.writes)
        s.service().finish(afterRestart.getString("id"))
        assertNull(
            OwnerJournalRules.findOrNull(s.master.getJSONArray("worklogs"), "qa_list_delete")
        )
        assertFalse(OwnerJournalRules.archives(s.master).has("worklog:qa_list_delete"))
        assertNull(s.service().claimDue())
        assertEquals(
            "done",
            s.service().query().getJSONArray("actions").getJSONObject(0).getString("state"),
        )
    }

    @Test
    fun restoredAndStaleActionsKeepTheirSource() {
        val restored = Sandbox()
        restored.work("qa_list_restore")
        restored.command(
            "queue_list_action",
            restored.queue("qa_list_restore", "qa_list_stage_restore"),
        )
        restored.clock.now += 5000
        restored.epoch = "qa_list_epoch_b"
        assertNull(restored.service().claimDue())
        assertNotNull(
            OwnerJournalRules.findOrNull(
                restored.master.getJSONArray("worklogs"),
                "qa_list_restore",
            )
        )
        assertEquals(
            "failed",
            restored.service().query().getJSONArray("actions").getJSONObject(0).getString("state"),
        )
        val stale = Sandbox()
        val row = stale.work("qa_list_stale")
        stale.command("queue_list_action", stale.queue("qa_list_stale", "qa_list_stage_stale"))
        row.put("activity", "[TEST] QA inzwischen geändert")
        stale.clock.now += 5000
        val pending = stale.service().claimDue()!!
        rejected { stale.original(pending) }
        stale.service().finish(pending.getString("id"), "Eintrag wurde geändert.")
        assertEquals(
            "[TEST] QA inzwischen geändert",
            OwnerJournalRules.findOrNull(stale.master.getJSONArray("worklogs"), "qa_list_stale")!!
                .getString("activity"),
        )
        assertFalse(OwnerJournalRules.archives(stale.master).has("worklog:qa_list_stale"))
    }

    @Test
    fun batchUndoKeepsEverySourceAndBlocksOverlappingActions() {
        val s = Sandbox()
        s.work("qa_list_batch_a")
        s.work("qa_list_batch_b")
        val original = s.master.toString()
        val ids = JSONArray().put("qa_list_batch_a").put("qa_list_batch_b")
        val staged =
            s.command(
                "queue_list_action",
                JSONObject()
                    .put("request_id", "qa_list_batch_undo")
                    .put("original_tool", "delete_work_records")
                    .put(
                        "original_args",
                        JSONObject()
                            .put("work_record_ids", ids)
                            .put(
                                "expected_preview_hash",
                                WorklogArchiveBatch.preview(s.master, ids).getString("preview_hash"),
                            )
                            .put("confirmed", true),
                    ),
            )
        assertEquals(2, ListActionTools.targets(staged).size)
        rejected {
            s.command("queue_list_action", s.queue("qa_list_batch_a", "qa_list_overlapping_single"))
        }
        s.clock.now += 4999
        s.command(
            "undo_list_action",
            JSONObject()
                .put("request_id", "qa_list_batch_cancel")
                .put("action_id", staged.getString("id")),
        )
        s.clock.now += 10000
        assertNull(s.service().claimDue())
        assertEquals(original, s.master.toString())
        assertEquals(0, s.writes)
    }

    @Test
    fun batchExpiryUsesOneDurableRequestForTheActualDeletion() {
        val s = Sandbox()
        s.work("qa_list_batch_remove_a")
        s.work("qa_list_batch_remove_b")
        val ids = JSONArray().put("qa_list_batch_remove_a").put("qa_list_batch_remove_b")
        s.command(
            "queue_list_action",
            JSONObject()
                .put("request_id", "qa_list_batch_delete")
                .put("original_tool", "delete_work_records")
                .put(
                    "original_args",
                    JSONObject()
                        .put("request_id", "qa_original_batch_writer")
                        .put("work_record_ids", ids)
                        .put(
                            "expected_preview_hash",
                            WorklogArchiveBatch.preview(s.master, ids).getString("preview_hash"),
                        )
                        .put("confirmed", true),
                ),
        )
        s.clock.now += 5000
        val pending = s.service().claimDue()!!
        assertEquals(
            "qa_original_batch_writer",
            pending.getJSONObject("original_args").getString("request_id"),
        )
        s.original(pending)
        assertTrue(s.original(s.service().claimDue()!!).getBoolean("replayed"))
        s.service().finish(pending.getString("id"))
        for (i in 0 until ids.length()) {
            assertNull(
                OwnerJournalRules.findOrNull(s.master.getJSONArray("worklogs"), ids.getString(i))
            )
            assertFalse(OwnerJournalRules.archives(s.master).has("worklog:" + ids.getString(i)))
        }
    }

    @Test
    fun orderCompletionUsesTheActualPointReadAndTheSharedUpdateGuard() {
        val s = Sandbox()
        LocalBusinessState.prepare(s.master)
        s.master
            .getJSONArray("customers")
            .getJSONObject(0)
            .put("name", "[TEST] QA Auftragsempfänger")
        val order =
            JSONObject()
                .put("id", "qa_list_completion_guard")
                .put("customer_id", "qa_customer_1")
                .put("title", "[TEST] QA Auftrag mit Änderungsstand")
                .put("test_record", true)
                .put("status", "Geplant")
                .put("notes", "[TEST] QA Notiz bleibt erhalten")
                .put("created_at", "2026-11-08T10:00:00Z")
                .put("updated_at", "2026-11-08T10:00:00Z")
        s.master.getJSONArray("orders").put(order)
        val source = PixelMcpReadTools(PixelMcpReadTools.Source { s.repository.state() })
        val current =
            source
                .call("get_order", JSONObject().put("order_id", order.getString("id")))
                .getJSONObject("data")
        val args =
            ListActionTools.completionArgs("order", order.getString("id"), current)
                .put("request_id", "qa_list_complete_guard")
        PixelMcpEntityWriteTools.validate("update_order", args)
        val prepared =
            EntityApplicationService.prepare(
                    "update_order",
                    args,
                    s.repository.state(),
                    args.getString("request_id"),
                )
                .getJSONObject("payload")
        assertEquals("Erledigt", prepared.getString("status"))
        assertEquals(order.getString("customer_id"), prepared.getString("customer_id"))
        assertEquals(1, args.getJSONObject("updates").length())
        ManualChangeRules.prepare("order", prepared, current, "2026-11-08T10:00:30Z")
        val saved = JSONObject(s.master.toString())
        val errors =
            LocalJournalRules.apply(
                saved,
                JSONArray()
                    .put(
                        JSONObject()
                            .put("kind", "order")
                            .put("client_id", args.getString("request_id"))
                            .put("created_at", "2026-11-08T10:00:30Z")
                            .put("payload", prepared)
                    ),
            )
        assertEquals(errors.toString(), 0, errors.length())
        val completed =
            SnapshotReadSupport.find(saved.getJSONArray("orders"), order.getString("id"))
        assertEquals("Erledigt", completed.getString("status"))
        assertEquals(order.getString("notes"), completed.getString("notes"))
        order.put("notes", "[TEST] QA inzwischen bearbeitet")
        order.put("updated_at", "2026-11-08T10:01:00Z")
        rejected {
            EntityApplicationService.prepare(
                "update_order",
                args,
                s.repository.state(),
                args.getString("request_id"),
            )
        }
        assertEquals("Geplant", order.getString("status"))
        assertEquals(0, s.writes)
    }

    @Test
    fun positiveOrderUndoPreservesTheOrderAndUnrelatedUpdatesAreRejected() {
        val s = Sandbox()
        val order =
            JSONObject()
                .put("id", "qa_list_positive_order")
                .put("customer_id", "qa_customer_1")
                .put("title", "[TEST] QA positiver Auftrag")
                .put("status", "Geplant")
                .put("notes", "[TEST] QA ursprüngliche Notiz")
        s.master.getJSONArray("orders").put(order)
        val before = s.master.toString()
        val args =
            JSONObject()
                .put("request_id", "qa_list_stage_order")
                .put("original_tool", "update_order")
                .put(
                    "original_args",
                    JSONObject()
                        .put("order_id", order.getString("id"))
                        .put("expected_record_hash", OwnerJournalRules.version(order))
                        .put("updates", JSONObject().put("status", "Erledigt")),
                )
        val staged = s.command("queue_list_action", args)
        s.clock.now += 4999
        s.command(
            "undo_list_action",
            JSONObject()
                .put("request_id", "qa_list_cancel_order")
                .put("action_id", staged.getString("id")),
        )
        assertEquals(before, s.master.toString())
        val changed = JSONObject(args.toString()).put("request_id", "qa_list_bad_order_fields")
        changed
            .getJSONObject("original_args")
            .getJSONObject("updates")
            .put("notes", "[TEST] QA unerlaubte Nebenänderung")
        rejected { s.command("queue_list_action", changed) }
        assertEquals(before, s.master.toString())
        assertEquals(0, s.writes)
    }

    @Test
    fun paymentUndoAndCrashReplayNeverAddASecondPayment() {
        val s = Sandbox()
        val id = "qa_list_internal_payment"
        val record =
            JSONObject()
                .put("id", id)
                .put("kind", "internal")
                .put("route", "labor")
                .put("status", "confirmed")
                .put("total_minor", 1000)
        s.values[SettlementRules.KEY] =
            JSONObject()
                .put("records", JSONArray().put(record))
                .put("payments", JSONArray())
                .toString()
        fun stage(request: String) =
            s.command(
                "queue_list_action",
                JSONObject()
                    .put("request_id", request)
                    .put("original_tool", "add_settlement_payment")
                    .put(
                        "original_args",
                        JSONObject()
                            .put("settlement_id", id)
                            .put("expected_record_hash", SettlementRules.hash(record))
                            .put("amount", "10,00")
                            .put("date", "2026-11-08")
                            .put("note", "[TEST] QA vollständig bezahlt"),
                    ),
            )
        val first = stage("qa_list_payment_undo")
        s.clock.now += 4999
        s.command(
            "undo_list_action",
            JSONObject()
                .put("request_id", "qa_list_payment_cancel")
                .put("action_id", first.getString("id")),
        )
        assertEquals(0, SettlementRules.ledger(s.repository).getJSONArray("payments").length())
        val second = stage("qa_list_payment_execute")
        s.clock.now += 5000
        val pending = s.service().claimDue()!!
        assertEquals(second.getString("id"), pending.getString("id"))
        s.original(pending)
        assertTrue(s.original(s.service().claimDue()!!).getBoolean("replayed"))
        s.service().finish(pending.getString("id"))
        val ledger = SettlementRules.ledger(s.repository)
        assertEquals(1, ledger.getJSONArray("payments").length())
        assertEquals(1000L, SettlementRules.paid(ledger, id))
    }

    @Test
    fun draftDeletionIsPermanentAndKeepsPreparedAndIssuedOriginals() {
        assertTrue(PixelMcpOwnerTools.write("delete_settlement_draft"))
        val s = Sandbox()
        val draft =
            JSONObject()
                .put("id", "qa_delete_draft")
                .put("kind", "internal")
                .put("route", "labor")
                .put("status", "draft")
                .put("title", "[TEST] QA zu löschender Entwurf")
                .put("positions", JSONArray())
        val prepared =
            JSONObject()
                .put("id", "qa_keep_prepared")
                .put("kind", "invoice")
                .put("route", "billed")
                .put("status", "prepared")
                .put("invoice_number", "QA-RES-1")
        val issued =
            JSONObject()
                .put("id", "qa_keep_issued")
                .put("kind", "invoice")
                .put("route", "billed")
                .put("status", "confirmed")
                .put("invoice_number", "QA-INV-1")
        s.values[SettlementRules.KEY] =
            JSONObject()
                .put("records", JSONArray().put(draft).put(prepared).put(issued))
                .put("payments", JSONArray())
                .toString()
        val cache =
            "native_form_draft:" + PixelMcpWriteTools.hash("settlement-editor:qa_delete_draft")
        s.values[cache] = JSONObject().put("values", draft).toString()
        fun deletion(record: JSONObject, request: String) =
            JSONObject()
                .put("request_id", request)
                .put("settlement_id", record.getString("id"))
                .put("expected_record_hash", SettlementRules.hash(record))
                .put("confirmed", true)
        val args = deletion(draft, "qa_delete_draft_request")
        assertTrue(s.financial("delete_settlement_draft", args).getBoolean("deleted"))
        assertTrue(s.financial("delete_settlement_draft", args).getBoolean("replayed"))
        assertEquals("", s.values[cache])
        val ledger = SettlementRules.ledger(s.repository)
        assertEquals(2, ledger.getJSONArray("records").length())
        assertEquals(
            prepared.toString(),
            SettlementRules.find(ledger, prepared.getString("id")).toString(),
        )
        assertEquals(
            issued.toString(),
            SettlementRules.find(ledger, issued.getString("id")).toString(),
        )
        rejected {
            s.financial("delete_settlement_draft", deletion(prepared, "qa_keep_prepared_request"))
        }
        rejected {
            s.financial("delete_settlement_draft", deletion(issued, "qa_keep_issued_request"))
        }
        rejected {
            s.financial(
                "save_settlement_draft",
                JSONObject()
                    .put("request_id", "qa_draft_no_resurrection")
                    .put("settlement_id", draft.getString("id"))
                    .put(
                        "draft",
                        JSONObject()
                            .put("route", "labor")
                            .put("title", "[TEST] QA kein Wiederanlegen")
                            .put("positions", JSONArray()),
                    ),
            )
        }
        assertEquals(2, SettlementRules.ledger(s.repository).getJSONArray("records").length())
    }

    @Test
    fun issuedInvoicesCannotBeSwipedPaid() {
        val s = Sandbox()
        val invoice =
            JSONObject()
                .put("id", "qa_list_invoice")
                .put("kind", "invoice")
                .put("status", "confirmed")
                .put("total_minor", 100)
        s.values[SettlementRules.KEY] =
            JSONObject().put("records", JSONArray().put(invoice)).toString()
        val args =
            JSONObject()
                .put("request_id", "qa_list_invoice_stage")
                .put("original_tool", "add_settlement_payment")
                .put(
                    "original_args",
                    JSONObject()
                        .put("settlement_id", "qa_list_invoice")
                        .put("expected_record_hash", SettlementRules.hash(invoice))
                        .put("amount", "1,00")
                        .put("date", "2026-11-08")
                        .put("note", "[TEST] QA"),
                )
        rejected { s.command("queue_list_action", args) }
        assertTrue(ListActionTools.rows(s.repository).isEmpty())
        assertEquals(0, s.writes)
    }
}
