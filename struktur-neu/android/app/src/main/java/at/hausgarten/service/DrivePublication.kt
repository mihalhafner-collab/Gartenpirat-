package at.hausgarten.service

import java.nio.charset.StandardCharsets
import java.util.*
import org.json.*

internal class DrivePublication {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun manifest(
            snapshot: JSONObject,
            actions: JSONArray,
            stateId: String,
            sha: String,
        ): JSONObject {

            var applied = DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids"))
            applied.addAll(DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids")))
            var resolved = snapshot.optJSONObject("drive_resolved_actions")
            var receipts = JSONObject()
            var errors = HashMap<String, JSONObject>()
            for (field in arrayOf("sync_errors", "drive_journal_errors")) {
                var list = snapshot.optJSONArray(field)
                run {
                    var i: Int = 0
                    var firstIteration28 = true
                    while (true) {
                        if (firstIteration28) firstIteration28 = false
                        else {
                            i++
                        }
                        if (!(((list != null) && (i < list.length())))) break

                        var e = list.getJSONObject(i)
                        errors.put(e.optString("client_id"), e)
                    }
                }
            }
            var missing = HashSet<String>()
            var media = snapshot.optJSONArray("drive_media_errors")
            run {
                var i: Int = 0
                var firstIteration29 = true
                while (true) {
                    if (firstIteration29) firstIteration29 = false
                    else {
                        i++
                    }
                    if (!(((media != null) && (i < media.length())))) break

                    var m = media.getJSONObject(i)
                    missing.add(
                        ((m.optString("entity_type", "photo") + ":") +
                            m.optString("entity_id", m.optString("photo_id")))
                    )
                }
            }
            run {
                var i: Int = 0
                var firstIteration30 = true
                while (true) {
                    if (firstIteration30) firstIteration30 = false
                    else {
                        i++
                    }
                    if (!((i < actions.length()))) break

                    var a = actions.getJSONObject(i)
                    var id = a.getString("client_id")
                    var done: Boolean =
                        (applied.contains(id) || (((resolved != null) && resolved.has(id))))
                    var kind = a.optString("kind")
                    var entity =
                        (if (kind.startsWith("offer")) "offer"
                        else (if (kind.contains("invoice")) "invoice" else kind))
                    if (
                        missing.contains(
                            ((entity + ":") + a.getJSONObject("payload").optString("id"))
                        )
                    ) {
                        done = false
                    }
                    var e = errors.get(id)
                    receipts.put(
                        id,
                        JSONObject()
                            .put("action_sha256", PixelMcpWriteTools.hash(a))
                            .put("applied", (done && (e == null)))
                            .put(
                                "code",
                                (if ((e == null)) ((if (done) "" else "PROCESSOR_REQUIRED"))
                                else e.optString("code", "REVIEW_REQUIRED")),
                            ),
                    )
                }
            }
            return JSONObject()
                .put("format", "hausgarten.drive.commit.v1")
                .put("state_file_id", stateId)
                .put("state_sha256", sha)
                .put("receipts", receipts)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun verify(manifest: JSONObject, state: ByteArray, actions: JSONArray): Unit {

            if (
                (!(("hausgarten.drive.commit.v1" == manifest.optString("format"))) ||
                    !((HttpTransport.sha256(state) == manifest.optString("state_sha256"))))
            ) {
                throw IllegalArgumentException(
                    "Drive-Veröffentlichung ist unvollständig oder verändert."
                )
            }
            var receipts = manifest.getJSONObject("receipts")
            run {
                var i: Int = 0
                var firstIteration31 = true
                while (true) {
                    if (firstIteration31) firstIteration31 = false
                    else {
                        i++
                    }
                    if (!((i < actions.length()))) break

                    var action = actions.getJSONObject(i)
                    var r = receipts.optJSONObject(action.getString("client_id"))
                    if (
                        ((r == null) ||
                            !((PixelMcpWriteTools.hash(action) == r.optString("action_sha256"))))
                    ) {
                        throw IllegalArgumentException(
                            "Drive-Aktionsbeleg stimmt nicht mit der Änderung überein."
                        )
                    }
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun progress(manifest: JSONObject, publicationId: String): JSONObject {

            var out = JSONObject()
            var rs = manifest.getJSONObject("receipts")
            run {
                var keys = rs.keys()
                var firstIteration32 = true
                while (true) {
                    if (firstIteration32) firstIteration32 = false else {}
                    if (!(keys.hasNext())) break

                    var id = keys.next()
                    var r = JSONObject(rs.getJSONObject(id).toString())
                    r.put("cloud_confirmed", r.optBoolean("applied"))
                        .put("provider_readback", true)
                        .put("drive_manifest_readback", true)
                        .put("publication_id", publicationId)
                        .put("state_sha256", manifest.getString("state_sha256"))
                    out.put(id, r)
                }
            }
            return out
        }

        @JvmStatic
        @Throws(Exception::class)
        fun stateBytes(snapshot: JSONObject): ByteArray {

            var clean = JSONObject(snapshot.toString())
            for (key in
                arrayOf(
                    "drive_cloud_receipts",
                    "drive_last_publication",
                    "drive_parent_publication_id",
                    "drive_parent_state_sha256",
                    "drive_parent_sequence",
                )) {
                clean.remove(key)
            }
            return PixelMcpWriteTools.canonical(clean).toByteArray(StandardCharsets.UTF_8)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun evidence(result: JSONObject, state: JSONObject): JSONObject {

            var master = state.getJSONObject("master")
            var rs = master.optJSONObject("drive_cloud_receipts")
            var r = (if ((rs == null)) null else rs.optJSONObject(result.optString("client_id")))
            if (((r == null) || !(r.optBoolean("cloud_confirmed")))) {
                return result
            }
            var pending = state.optJSONArray("pending")
            run {
                var i: Int = 0
                var firstIteration33 = true
                while (true) {
                    if (firstIteration33) firstIteration33 = false
                    else {
                        i++
                    }
                    if (!(((pending != null) && (i < pending.length())))) break

                    var a = pending.getJSONObject(i)
                    if (
                        ((result.optString("client_id") == a.optString("client_id")) &&
                            !((r.optString("action_sha256") == PixelMcpWriteTools.hash(a))))
                    ) {
                        return result
                    }
                }
            }
            result
                .put("cloud_confirmed", true)
                .put("confirmation_scope", "drive_server_manifest")
                .put("publication_id", r.optString("publication_id"))
                .put("fully_synchronized", r.optBoolean("applied"))
            if (r.optBoolean("applied")) {
                result
                    .put("sync_state", "sync_confirmed")
                    .put("sync_message", "Änderung übernommen und mit Drive-Serverbeleg bestätigt.")
            }
            return result
        }

        @JvmStatic
        @Throws(Exception::class)
        fun head(summaries: JSONArray, publisher: String): JSONObject {

            var chain = TreeMap<Long, JSONObject>()
            run {
                var i: Int = 0
                var firstIteration34 = true
                while (true) {
                    if (firstIteration34) firstIteration34 = false
                    else {
                        i++
                    }
                    if (!((i < summaries.length()))) break

                    var s = summaries.getJSONObject(i)
                    if (!((publisher == s.getString("publisher")))) {
                        throw IllegalArgumentException(
                            "PRIMARY_DEVICE_CONFLICT: Mehrere primäre Pixel-Herausgeber."
                        )
                    }
                    var n: Long = s.getLong("sequence")
                    if ((n < 1)) {
                        throw IllegalArgumentException(
                            "PUBLICATION_INVALID: Ungültige Veröffentlichungsfolge."
                        )
                    }
                    var previous = chain.putIfAbsent(n, s)
                    if (
                        ((previous != null) && !((previous.getString("id") == s.getString("id"))))
                    ) {
                        throw IllegalArgumentException(
                            "PUBLICATION_CONFLICT: Zwei Varianten desselben Veröffentlichungsstands."
                        )
                    }
                }
            }
            var sequence: Long = 0
            var prior: JSONObject? = null
            for (row in chain.entries) {
                var s = row.value
                if (
                    ((row.key != ++sequence) ||
                        ((if ((prior == null)) !(s.optString("parent").isEmpty())
                        else
                            (!((prior.getString("id") == s.optString("parent"))) ||
                                !((prior.getString("sha") == s.optString("parent_sha")))))))
                ) {
                    throw IllegalArgumentException(
                        "PUBLICATION_GAP: Die Veröffentlichungskette ist unvollständig. Originale bleiben erhalten."
                    )
                }
                prior = s
            }
            return (if ((prior == null)) JSONObject() else prior)
        }
    }
}
