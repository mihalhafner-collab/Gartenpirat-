package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.TreeSet

/** Pure review projection. Business caches, tokens and old action queues are never imported. */
object RestoredBrowserReviewRules {
    const val MIRROR = "hausgarten.fullbackup.formdrafts.v1"

    private fun invalid(): IOException = IOException("BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED")

    @JvmStatic
    @Throws(Exception::class)
    fun validate(source: JSONObject): JSONObject {
        if (source.opt("formatVersion") !is Int || source.getInt("formatVersion") != 1 ||
            source.optJSONObject("storage") == null || source.toString().toByteArray(StandardCharsets.UTF_8).size > 16 * 1024 * 1024) {
            throw invalid()
        }
        val storage = source.getJSONObject("storage")
        val storageKeys = storage.keys()
        while (storageKeys.hasNext()) if (storage.get(storageKeys.next()) !is String) throw invalid()
        if (source.has("drafts")) {
            val draft = source.optJSONObject("drafts")
            if (draft == null || draft.optInt("formatVersion") != 1) throw invalid()
            forms(draft)
        }
        if (storage.has(MIRROR)) {
            val mirror = JSONObject(storage.getString(MIRROR))
            if (mirror.optInt("formatVersion") != 1 || mirror.optJSONObject("pages") == null) throw invalid()
            val pages = mirror.getJSONObject("pages")
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: throw invalid()
                forms(page)
            }
        }
        return JSONObject(source.toString())
    }

    @Throws(Exception::class)
    private fun forms(page: JSONObject) {
        val forms = page.optJSONArray("forms")
        if (forms == null || forms.length() > 10000) throw invalid()
        if (page.has("page") && page.opt("page") !is String) throw invalid()
        for (i in 0 until forms.length()) {
            val form = forms.getJSONObject(i)
            val fields = form.getJSONArray("fields")
            if (fields.length() > 10000) throw invalid()
            for (j in 0 until fields.length()) {
                val field = fields.getJSONObject(j)
                if (field.opt("value") !is String) throw invalid()
                for (name in arrayOf("id", "name", "type", "tag", "label")) {
                    if (field.has(name) && field.opt(name) !is String) throw invalid()
                }
                if (field.has("checked") && field.opt("checked") !is Boolean) throw invalid()
                if (field.has("selected")) {
                    val selected = field.getJSONArray("selected")
                    for (k in 0 until selected.length()) if (selected.get(k) !is String) throw invalid()
                }
            }
        }
    }

    @JvmStatic
    fun selectable(key: String): Boolean = key in listOf(
        MIRROR, "hausgarten.form.sessions.v1", "hausgarten.entry.draft.v1", "hausgarten.offer.editor.v1",
        "hausgarten.offer.decision.editor.v1", "hausgarten.planning.draft.v1", "hausgarten.planning.draft.v1.others",
        "hausgarten.revier.editor.v1", "hausgarten.payment.editor.v1", "hausgarten.business.settings.draft"
    ) || key.matches(Regex("hausgarten\\.(?:garden\\.draft|photo\\.edit)\\.[A-Za-z0-9_-]{1,200}"))

    @JvmStatic
    fun label(key: String): String = when {
        key == "hausgarten.form.sessions.v1" -> "Fortsetzbare Bearbeitungen"
        key == MIRROR -> "Zusätzlich erfasste Formularwerte"
        key.startsWith("hausgarten.entry.") -> "Arbeitsaufzeichnung"
        key.startsWith("hausgarten.offer.decision.") -> "Angebotsentscheidung"
        key.startsWith("hausgarten.offer.") -> "Angebot"
        key.startsWith("hausgarten.planning.") -> "Terminplanung"
        key.startsWith("hausgarten.revier.") -> "Objekt, Anfrage oder Pflege"
        key.startsWith("hausgarten.payment.") -> "Zahlungserfassung"
        key.startsWith("hausgarten.business.settings.") -> "Firmenprofil"
        key.startsWith("hausgarten.garden.") -> "Gartenangaben"
        key.startsWith("hausgarten.photo.") -> "Fotobeschreibung"
        else -> "Weitere Browserwerte"
    }

    @JvmStatic
    @Throws(Exception::class)
    fun drafts(source: JSONObject): JSONObject = drafts(source, true, true)

    @Throws(Exception::class)
    private fun drafts(source: JSONObject, markRequests: Boolean, markPayment: Boolean): JSONObject {
        val checked = validate(source)
        val storage = checked.getJSONObject("storage")
        val result = JSONObject()
        val storageKeys = storage.keys()
        while (storageKeys.hasNext()) {
            val key = storageKeys.next()
            if (selectable(key)) result.put(key, storage.getString(key))
        }
        val removedSessions = HashSet<String>()
        if (markRequests && result.has("hausgarten.form.sessions.v1")) {
            try {
                val sessions = JSONObject(result.getString("hausgarten.form.sessions.v1"))
                val removed = sessions.optJSONObject("removed")
                val entries = sessions.optJSONObject("entries")
                removed?.keys()?.forEachRemaining { removedSessions.add(it) }
                if (entries != null) {
                    val ids = ArrayList<String>()
                    entries.keys().forEachRemaining { ids.add(it) }
                    for (id in ids) {
                        if (removedSessions.contains(id)) {
                            entries.remove(id)
                            continue
                        }
                        val edit = entries.optJSONObject(id)
                        if (edit != null && edit.optJSONObject("pending") != null) edit.put("restore_review_required", true)
                    }
                }
                result.put("hausgarten.form.sessions.v1", sessions.toString())
            } catch (malformed: JSONException) {
                // One unreadable draft is retained as evidence; no business action is inferred.
            }
        }
        if (markRequests) {
            for (key in arrayOf("hausgarten.revier.editor.v1", "hausgarten.offer.editor.v1", "hausgarten.planning.draft.v1", "hausgarten.payment.editor.v1")) {
                if (result.has(key) && (markPayment || key != "hausgarten.payment.editor.v1")) {
                    try {
                        val edit = JSONObject(result.getString(key))
                        val target = if (key == "hausgarten.offer.editor.v1") edit.optJSONObject("editor") else edit
                        if (target != null && (edit.optJSONObject("request") != null || target.optJSONObject("pending") != null ||
                                target.optJSONObject("request") != null || target.optJSONObject("frozen") != null)) {
                            target.put("restore_review_required", true)
                        }
                        result.put(key, edit.toString())
                    } catch (malformed: JSONException) {
                        // Preserve an unreadable individual legacy draft without interpreting it as an action.
                    }
                }
            }
        }
        if (markRequests && markPayment) {
            val keys = result.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "hausgarten.business.settings.draft" || key.startsWith("hausgarten.garden.draft.") || key.startsWith("hausgarten.photo.edit.")) {
                    try {
                        val edit = JSONObject(result.getString(key))
                        if (edit.has("request") && !edit.isNull("request") || edit.has("pending") && !edit.isNull("pending")) {
                            edit.put("restore_review_required", true)
                        }
                        result.put(key, edit.toString())
                    } catch (malformed: JSONException) {
                        // Preserve the individual source; never infer missing action arguments.
                    }
                }
            }
        }
        if (markRequests && result.has("hausgarten.planning.draft.v1.others")) {
            try {
                val others = JSONObject(result.getString("hausgarten.planning.draft.v1.others"))
                val ids = others.keys()
                while (ids.hasNext()) {
                    val edit = others.optJSONObject(ids.next())
                    if (edit != null && (edit.optJSONObject("pending") != null || edit.optJSONObject("request") != null)) {
                        edit.put("restore_review_required", true)
                    }
                }
                result.put("hausgarten.planning.draft.v1.others", others.toString())
            } catch (malformed: JSONException) {
            }
        }
        val visible = checked.optJSONObject("drafts")
        if (visible != null && visible.optString("page").isNotEmpty() && visible.getJSONArray("forms").length() > 0) {
            val mirror = if (result.has(MIRROR)) JSONObject(result.getString(MIRROR)) else
                JSONObject().put("formatVersion", 1).put("pages", JSONObject())
            mirror.getJSONObject("pages").put("restored_visible_forms", JSONObject().put("page", visible.getString("page")).put("forms", visible.getJSONArray("forms")))
            result.put(MIRROR, mirror.toString())
        }
        if (markRequests && removedSessions.isNotEmpty() && result.has(MIRROR)) {
            val mirror = JSONObject(result.getString(MIRROR))
            val pages = mirror.getJSONObject("pages")
            val ids = pages.keys()
            while (ids.hasNext()) {
                val page = pages.getJSONObject(ids.next())
                val current = page.getJSONArray("forms")
                val retained = JSONArray()
                for (i in 0 until current.length()) {
                    if (!removedSessions.contains(current.getJSONObject(i).optString("session_id"))) retained.put(current.get(i))
                }
                page.put("forms", retained)
            }
            result.put(MIRROR, mirror.toString())
        }
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun candidates(source: JSONObject, origin: String?): JSONArray {
        val draft = drafts(source)
        val rows = JSONArray()
        val keys = TreeSet<String>()
        draft.keys().forEachRemaining { keys.add(it) }
        for (key in keys) rows.put(JSONObject().put("source", origin).put("key", key).put("label", label(key)).put("value", draft.getString(key)))
        return rows
    }

    @JvmStatic
    @Throws(Exception::class)
    fun selection(restored: JSONObject, device: JSONObject, selected: JSONArray): JSONObject = selection(restored, device, selected, true, true)

    @Throws(Exception::class)
    private fun selection(restored: JSONObject, device: JSONObject, selected: JSONArray, markRequests: Boolean, markPayment: Boolean): JSONObject {
        val backup = drafts(restored, markRequests, markPayment)
        val current = drafts(device, markRequests, markPayment)
        val result = JSONObject()
        if (selected.length() > 10000) throw invalid()
        val seen = HashSet<String>()
        for (i in 0 until selected.length()) {
            val choice = selected.getJSONObject(i)
            val origin = choice.getString("source")
            val key = choice.getString("key")
            if (origin !in listOf("backup", "device") || !selectable(key) || !seen.add(key)) throw invalid()
            val source = if (origin == "backup") backup else current
            if (!source.has(key)) throw invalid()
            result.put(key, source.getString(key))
        }
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun validateSelection(restored: JSONObject, device: JSONObject, saved: JSONObject) {
        if (!saved.optString("request_id").matches(Regex("[A-Za-z0-9_-]{8,200}")) || saved.optJSONArray("selected") == null ||
            saved.optJSONObject("selected_storage") == null || saved.optJSONObject("clear_storage") == null) throw invalid()
        val projection = saved.optInt("projection_version", 1)
        if (projection < 1 || projection > 3) throw invalid()
        val expected = selection(restored, device, saved.getJSONArray("selected"), projection >= 2, projection >= 3)
        val hash = PixelMcpWriteTools.hash(expected)
        if (hash != saved.optString("selection_hash") || hash != PixelMcpWriteTools.hash(saved.getJSONObject("selected_storage")) ||
            PixelMcpWriteTools.hash(validate(device).getJSONObject("storage")) != PixelMcpWriteTools.hash(saved.getJSONObject("clear_storage"))) {
            throw invalid()
        }
    }
}
