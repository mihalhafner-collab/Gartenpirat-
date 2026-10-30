package at.hausgarten.service

import org.json.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.io.IOException

/** Bounded Calendar API transport. Caller persists intent before any network request. */
internal class CalendarClient(@JvmField val token: Token, @JvmField val wire: HttpTransport.Wire) {
    fun interface Token {
        @Throws(Exception::class) fun get(): String
        @Throws(Exception::class) fun invalidate(rejected: String) {}
    }
    interface CalendarSetup {
        fun pending(): Boolean
        @Throws(Exception::class) fun pending(value: Boolean)
        fun confirmedId(): String = ""
        @Throws(Exception::class) fun confirmedId(id: String) {}
        fun phase(value: String) {}
    }
    @Throws(Exception::class) fun send(method: String, path: String, body: JSONObject?, etag: String?): HttpTransport.Reply {
        val headers = hashMapOf("Accept" to "application/json")
        if (body != null) headers["Content-Type"] = "application/json; charset=UTF-8"
        if (etag != null) {
            require(etag.isNotEmpty() && etag != "*" && !etag.contains("\r") && !etag.contains("\n")) { "Google liefert keinen sicheren Vergleichsstand." }
            headers["If-Match"] = etag
        }
        var reply: HttpTransport.Reply? = null
        for (attempt in 0 until 2) {
            val credential = token.get(); headers["Authorization"] = "Bearer " + credential
            reply = wire.send(method, API + path, headers, body?.toString()?.toByteArray(StandardCharsets.UTF_8), 3 * 1024 * 1024)
            if (reply.status != 401) break
            token.invalidate(credential) // A 401 rejects the request; one fresh-token retry is safe.
        }
        val result = reply!!
        CalendarAccessRules.failure(result)?.let { throw it }
        if (result.status == 412) throw PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT", "Google-Termin parallel verändert. Beide Varianten bleiben erhalten.")
        return result
    }
    @Throws(Exception::class) fun calendars(): JSONArray {
        val all = JSONArray(); var page = ""; val seen = HashSet<String>()
        do {
            val reply = send("GET", "users/me/calendarList?maxResults=250" + (if (page.isEmpty()) "" else "&pageToken=" + enc(page)), null, null)
            if (reply.status != 200) throw IOException("Kalenderliste nicht bestätigt.")
            val data = reply.json(); val rows = data.optJSONArray("items")
            for (i in 0 until (rows?.length() ?: 0)) {
                require(all.length() < 2000) { "Kalenderliste zu groß." }; all.put(rows!!.getJSONObject(i))
            }
            page = data.optString("nextPageToken")
            if (page.isNotEmpty() && !seen.add(page)) throw IOException("Wiederholte Kalenderseite.")
        } while (page.isNotEmpty())
        return all
    }
    @Throws(Exception::class) fun createCalendar(marker: String): JSONObject {
        val reply = send("POST", "calendars", JSONObject().put("summary", "Haus & Garten – Einsätze").put("description", marker).put("timeZone", "Europe/Vienna"), null)
        if (reply.status in 400..499 && reply.status != 408) throw HttpTransport.Failure("CALENDAR_REQUEST_REJECTED", CalendarAccessRules.message("CALENDAR_REQUEST_REJECTED"), 0, true)
        if (reply.status != 200 && reply.status != 201) throw IOException("Kalenderanlage unbestätigt. Vor Wiederholung vorhandene Kalender prüfen.")
        return reply.json()
    }
    @Throws(Exception::class) fun ensureOwnCalendar(marker: String?, state: CalendarSetup): JSONObject = synchronized(CalendarClient::class.java) {
        require(!marker.isNullOrEmpty()) { "Kalenderzuordnung fehlt." }
        state.phase("calendar_lookup"); val rows = calendars(); var found: JSONObject? = null
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            if (marker == row.optString("description")) {
                if (found != null) throw HttpTransport.Failure("CALENDAR_DUPLICATE", CalendarAccessRules.message("CALENDAR_DUPLICATE"), 0, true)
                if (row.optString("accessRole") != "owner") throw HttpTransport.Failure("CALENDAR_OWNER_REQUIRED", CalendarAccessRules.message("CALENDAR_OWNER_REQUIRED"), 0, true)
                found = row
            }
        }
        if (found == null && state.confirmedId().isNotEmpty()) {
            state.phase("calendar_readback")
            val known = send("GET", "calendars/" + enc(state.confirmedId()), null, null)
            if (known.status != 200 || marker != known.json().optString("description")) throw HttpTransport.Failure("CALENDAR_READBACK_PENDING", CalendarAccessRules.message("CALENDAR_READBACK_PENDING"), 30000, false)
            found = known.json()
        }
        if (found == null) {
            if (state.pending()) throw HttpTransport.Failure("CALENDAR_CREATE_PENDING", CalendarAccessRules.message("CALENDAR_CREATE_PENDING"), 0, true)
            state.phase("calendar_create"); state.pending(true)
            found = try { createCalendar(marker) } catch (rejected: HttpTransport.Failure) {
                if (CalendarAccessRules.rejectedCreation(rejected)) state.pending(false); throw rejected
            }
            state.confirmedId(found.getString("id"))
        }
        state.phase("calendar_readback")
        val id = found.getString("id"); val read = send("GET", "calendars/" + enc(id), null, null)
        if (read.status != 200 || id != read.json().optString("id") || marker != read.json().optString("description")) throw HttpTransport.Failure("CALENDAR_READBACK_PENDING", CalendarAccessRules.message("CALENDAR_READBACK_PENDING"), 30000, false)
        state.confirmedId(id); state.pending(false); found
    }
    @Throws(Exception::class) fun path(calendar: String, event: String): String = "calendars/" + enc(calendar) + "/events" + (if (event.isEmpty()) "" else "/" + enc(event))
    @Throws(Exception::class) fun get(calendar: String, id: String): JSONObject? {
        val reply = send("GET", path(calendar, id), null, null)
        if (reply.status == 404 || reply.status == 410) return null
        if (reply.status != 200) throw IOException("Kalendereintrag nicht lesbar.")
        return reply.json()
    }
    @Throws(Exception::class) fun save(calendar: String, a: JSONObject, remote: JSONObject?): JSONObject = save(calendar, a, remote, "")
    @Throws(Exception::class) fun save(calendar: String, a: JSONObject, remote: JSONObject?, customer: String): JSONObject {
        val body = CalendarRules.event(a, customer); val id = body.getString("id")
        if (remote != null) {
            if (remote.has("recurrence")) body.put("recurrence", JSONObject.NULL)
            for (edge in arrayOf("start", "end")) if (remote.optJSONObject(edge)?.has("date") == true) body.getJSONObject(edge).put("date", JSONObject.NULL)
            CalendarRules.requireIdentity(remote, a.getString("id"))
        }
        val reply = send(if (remote == null) "POST" else "PATCH", path(calendar, if (remote == null) "" else id) + "?sendUpdates=none", body, remote?.getString("etag"))
        if (reply.status == 409) {
            val prior = get(calendar, id) ?: throw IOException("Google-ID noch nicht lesbar. Dieselbe Anfrage wiederholen.")
            CalendarRules.requireIdentity(prior, a.getString("id")); return prior
        }
        if (reply.status != 200 && reply.status != 201) throw IOException("Google-Kalenderspeicherung nicht bestätigt.")
        return get(calendar, id) ?: throw IOException("Google-Kalender konnte nicht zurückgelesen werden.")
    }
    @Throws(Exception::class) fun remove(calendar: String, id: String, remote: JSONObject?) {
        if (remote == null || remote.optString("status") == "cancelled") return
        val reply = send("DELETE", path(calendar, id) + "?sendUpdates=none", null, remote.getString("etag"))
        if (reply.status !in listOf(204, 404, 410)) throw IOException("Kalenderabsage noch nicht bestätigt.")
        val after = get(calendar, id)
        if (after != null && after.optString("status") != "cancelled") throw IOException("Kalenderabsage nicht zurückgelesen.")
    }
    @Throws(Exception::class) fun changes(calendar: String, syncToken: String): JSONObject {
        val all = JSONArray(); var page = ""; var next = ""; val seen = HashSet<String>(); var pages = 0
        do {
            if (++pages > 40) throw IOException("Kalenderabgleich zu groß; bestehender Stand bleibt erhalten.")
            val reply = send("GET", path(calendar, "") + "?maxResults=250&showDeleted=true" + (if (syncToken.isEmpty()) "" else "&syncToken=" + enc(syncToken)) + (if (page.isEmpty()) "" else "&pageToken=" + enc(page)), null, null)
            if (reply.status == 410) return JSONObject().put("reset_required", true)
            if (reply.status != 200) throw IOException("Kalenderänderungen nicht bestätigt.")
            val data = reply.json(); val rows = data.optJSONArray("items")
            for (i in 0 until (rows?.length() ?: 0)) all.put(rows!!.getJSONObject(i))
            next = data.optString("nextSyncToken"); page = data.optString("nextPageToken")
            if (page.isNotEmpty() && !seen.add(page)) throw IOException("Wiederholte Kalenderseite.")
        } while (page.isNotEmpty())
        if (next.isEmpty()) throw IOException("Google-Synctoken fehlt.")
        return JSONObject().put("events", all).put("next_sync_token", next)
    }
    @Throws(Exception::class) fun busy(ids: JSONArray, from: String, to: String): JSONObject {
        val items = JSONArray(); for (i in 0 until ids.length()) items.put(JSONObject().put("id", ids.getString(i)))
        val reply = send("POST", "freeBusy", JSONObject().put("timeMin", from).put("timeMax", to).put("timeZone", "Europe/Vienna").put("items", items), null)
        if (reply.status != 200) throw IOException("Belegtzeiten nicht bestätigt.")
        val calendars = reply.json().optJSONObject("calendars") ?: throw IOException("Belegtzeiten fehlen.")
        val result = JSONArray()
        for (i in 0 until ids.length()) {
            val calendar = calendars.optJSONObject(ids.getString(i))
            if (calendar == null || calendar.has("errors")) throw HttpTransport.Failure("CALENDAR_BUSY_PERMISSION_REQUIRED", "Belegtzeiten eines gewählten Kalenders nicht lesbar.", 0, true)
            val busy = calendar.getJSONArray("busy"); for (j in 0 until busy.length()) result.put(busy.getJSONObject(j))
        }
        return JSONObject().put("busy", result).put("from", from).put("to", to).put("checked_at", java.time.Instant.now().toString()).put("verified", true)
    }
    companion object {
        const val API = "https://www.googleapis.com/calendar/v3/"
        @JvmStatic @Throws(Exception::class) fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
