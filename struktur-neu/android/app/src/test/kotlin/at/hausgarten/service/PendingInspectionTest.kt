package at.hausgarten.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PendingInspectionTest {
    @Test
    fun confirmedCleanupSurvivesRestartingItsInspectionEffect() = runBlocking {
        val owner = Job()
        val ownerScope = CoroutineScope(coroutineContext + owner)
        val release = CompletableDeferred<Unit>()
        var started = false
        var finished = false
        var failureReported = false
        lateinit var acknowledgement: Job
        try {
            val inspection = launch {
                acknowledgement =
                    launchNativeAcknowledgement(ownerScope, { failureReported = true }) {
                        started = true
                        release.await()
                        finished = true
                    }
                cancel()
            }
            inspection.join()
            assertTrue(inspection.isCancelled)
            assertTrue(started)
            assertTrue(acknowledgement.isActive)
            assertFalse(finished)
            release.complete(Unit)
            acknowledgement.join()
            assertTrue(finished)
            assertFalse(failureReported)
        } finally {
            owner.cancelAndJoin()
        }
    }

    @Test
    fun confirmedCleanupStopsWhenItsOwningScreenCloses() = runBlocking {
        val owner = Job()
        val ownerScope = CoroutineScope(coroutineContext + owner)
        val release = CompletableDeferred<Unit>()
        var started = false
        var finished = false
        var failureReported = false
        val acknowledgement =
            launchNativeAcknowledgement(ownerScope, { failureReported = true }) {
                started = true
                release.await()
                finished = true
            }
        assertTrue(started)
        owner.cancelAndJoin()
        assertTrue(acknowledgement.isCancelled)
        assertFalse(finished)
        assertFalse(failureReported)
    }

    private fun original() =
        JSONObject()
            .put("request_id", "qa_background_original")
            .put("order_id", "qa_background_order")
            .put("expected_record_hash", "a".repeat(64))
            .put("updates", JSONObject().put("status", "Erledigt"))

    @Test
    fun lateReceiptIsReadWithTheExactOriginalRequest() = runBlocking {
        val args = original()
        val before = args.toString()
        val calls = mutableListOf<String>()
        val pauses = mutableListOf<Long>()
        val result =
            PendingInspectionRules.inspect(
                "update_order",
                args,
                { tool, request ->
                    assertEquals("inspect_edit_action", tool)
                    assertEquals("update_order", request.getString("original_tool"))
                    assertEquals(
                        PixelMcpWriteTools.hash(args),
                        PixelMcpWriteTools.hash(request.getJSONObject("original_args")),
                    )
                    calls += request.getJSONObject("original_args").getString("request_id")
                    // A reader cannot change the frozen request used by a later probe.
                    request
                        .getJSONObject("original_args")
                        .put("order_id", "qa_wrong_reader_mutation")
                    JSONObject()
                        .put("state", if (calls.size == 3) "confirmed" else "unknown")
                        .put("entity_id", "qa_background_order")
                        .put("business_write_performed", false)
                },
                { pauses += it },
            )
        assertEquals("confirmed", result.state)
        assertEquals(
            listOf("qa_background_original", "qa_background_original", "qa_background_original"),
            calls,
        )
        assertEquals(listOf(750L, 2500L), pauses)
        assertEquals(before, args.toString())
    }

    @Test
    fun unavailableReceiptRetainsTheRequestAndStopsBoundedProbes() = runBlocking {
        val args = original()
        val before = args.toString()
        var calls = 0
        val result =
            PendingInspectionRules.inspect(
                "update_order",
                args,
                { tool, _ ->
                    assertEquals("inspect_edit_action", tool)
                    calls++
                    throw IllegalStateException("QA transient read failure")
                },
                {},
            )
        assertEquals("waiting", result.state)
        assertEquals(4, calls)
        assertEquals(before, args.toString())
    }

    @Test
    fun genuineConflictAndRemovedContentAreTerminal() = runBlocking {
        for ((state, expected) in listOf("changed" to "conflict", "content_removed" to "removed")) {
            var calls = 0
            val result =
                PendingInspectionRules.inspect(
                    "update_order",
                    original(),
                    { _, _ ->
                        calls++
                        JSONObject().put("state", state).put("business_write_performed", false)
                    },
                    { fail("No retry after a terminal result") },
                )
            assertEquals(expected, result.state)
            assertEquals(1, calls)
        }
    }

    @Test
    fun removedReceiptAcknowledgesOnlyAnExplicitDeletion() = runBlocking {
        for (tool in
            listOf(
                "delete_entity",
                "purge_entity",
                "empty_trash",
                "delete_work_records",
                "saveWorklog",
                "update_photo",
            )) {
            var calls = 0
            val result =
                PendingInspectionRules.inspect(
                    tool,
                    original(),
                    { name, input ->
                        assertEquals("inspect_edit_action", name)
                        assertEquals(tool, input.getString("original_tool"))
                        calls++
                        JSONObject()
                            .put("state", "content_removed")
                            .put("business_write_performed", false)
                    },
                    { fail("A terminal deletion receipt must not repeat") },
                )
            assertEquals(
                if (tool.startsWith("delete_") || tool in setOf("purge_entity", "empty_trash"))
                    "confirmed"
                else "removed",
                result.state,
            )
            assertEquals(1, calls)
        }
    }

    @Test
    fun deletionAcknowledgementKeepsDifferentTargetsSeparate() {
        val work = JSONObject().put("work_record_ids", org.json.JSONArray().put("qa_work_one"))
        assertTrue(
            PendingInspectionRules.sameDeletionTarget(
                "delete_work_records",
                work,
                JSONObject(work.toString()).put("request_id", "qa_delete_one"),
            )
        )
        assertFalse(
            PendingInspectionRules.sameDeletionTarget(
                "delete_work_records",
                work,
                JSONObject().put("work_record_ids", org.json.JSONArray().put("qa_work_two")),
            )
        )
        assertFalse(
            PendingInspectionRules.sameDeletionTarget(
                "delete_work_records",
                JSONObject(),
                JSONObject(),
            )
        )
        val entity = JSONObject().put("entity", "appointment").put("entity_id", "qa_entity_one")
        for (tool in listOf("delete_entity", "purge_entity")) {
            assertTrue(
                PendingInspectionRules.sameDeletionTarget(
                    tool,
                    entity,
                    JSONObject(entity.toString()).put("request_id", "qa_delete_entity"),
                )
            )
            assertFalse(
                PendingInspectionRules.sameDeletionTarget(
                    tool,
                    entity,
                    JSONObject(entity.toString()).put("entity", "order"),
                )
            )
            assertFalse(
                PendingInspectionRules.sameDeletionTarget(
                    tool,
                    entity,
                    JSONObject(entity.toString()).put("entity_id", "qa_entity_two"),
                )
            )
        }
        assertFalse(PendingInspectionRules.sameDeletionTarget("saveWorklog", entity, entity))
    }

    @Test
    fun replayMismatchIsShownWithoutSubmittingAnything() = runBlocking {
        var calls = 0
        val result =
            PendingInspectionRules.inspect(
                "update_order",
                original(),
                { tool, _ ->
                    assertEquals("inspect_edit_action", tool)
                    calls++
                    throw PixelMcpWriteTools.Rejected("REPLAY_MISMATCH", "QA mismatch")
                },
                { fail("A replay conflict must not loop") },
            )
        assertEquals("conflict", result.state)
        assertEquals(1, calls)
    }

    @Test
    fun cancellationDoesNotAcknowledgeOrRetryThePendingRequest() = runBlocking {
        var calls = 0
        try {
            PendingInspectionRules.inspect(
                "update_order",
                original(),
                { _, _ ->
                    calls++
                    throw CancellationException("QA closed screen")
                },
                { fail("Cancelled checks must not retry") },
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
        }
    }

    @Test
    fun aReaderThatWritesCannotConfirmTheUiOperation() = runBlocking {
        for (tool in listOf("update_order", "delete_work_records")) for (state in
            listOf("confirmed", "content_removed", "unknown")) {
            var calls = 0
            val result =
                PendingInspectionRules.inspect(
                    tool,
                    original(),
                    { name, args ->
                        assertEquals("inspect_edit_action", name)
                        assertEquals(tool, args.getString("original_tool"))
                        calls++
                        JSONObject().put("state", state).put("business_write_performed", true)
                    },
                    { fail("Unexpected writer result must stop") },
                )
            assertEquals("conflict", result.state)
            assertEquals(1, calls)
        }
    }

    @Test
    fun recoveryHintRequiresAnActuallyActivatedRestore() {
        for (phase in
            listOf(
                "NONE",
                "REQUESTED",
                "ACTIVATING",
                "SWAPPED",
                "FAILED_HOLD",
                "CANCELLED",
                "ROLLED_BACK",
            )) assertEquals(
            "",
            PendingInspectionRules.recoveryKey(
                JSONObject().put("phase", phase).put("restore_id", "qa_restore")
            ),
        )
        assertEquals(
            "qa_restore:qa_preview",
            PendingInspectionRules.recoveryKey(
                JSONObject()
                    .put("phase", "ACTIVE")
                    .put("restore_id", "qa_restore")
                    .put("preview_hash", "qa_preview")
            ),
        )
        assertEquals("", PendingInspectionRules.recoveryKey(JSONObject().put("phase", "ACTIVE")))
    }
}
