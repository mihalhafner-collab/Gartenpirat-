package at.hausgarten.service

import java.time.*
import java.util.*
import org.json.*

/** The same deterministic planning rules apply to UI, MCP and Drive replay. */
class AppointmentRules {
    private constructor() {}

    companion object {
        @JvmField
        val FIELDS: Array<String> =
            arrayOf(
                "customer_id",
                "order_id",
                "title",
                "purpose",
                "starts_at",
                "ends_at",
                "timezone",
                "location",
                "latitude",
                "longitude",
                "weather_profile",
                "status",
                "notes",
                "travel_minutes",
                "break_minutes",
                "flexible_from",
                "flexible_until",
                "series_id",
                "scope_refs",
            )
        @JvmField
        val PROFILES: Array<String> =
            arrayOf("general", "lawn", "hedge", "planting", "inspection", "indoor")

        @Throws(Exception::class)
        @JvmStatic
        fun hash(row: JSONObject): String {
            return PixelMcpWriteTools.hash(LocalJournalRules.clean(row))
        }

        @JvmStatic
        fun active(row: JSONObject): Boolean {
            return !Arrays.asList("cancelled", "completed").contains(row.optString("status"))
        }

        @JvmStatic
        fun openEnd(row: JSONObject): Boolean {
            return ((row.has("ends_at") && !row.isNull("ends_at")) &&
                row.optString("ends_at").isEmpty())
        }

        @Throws(Exception::class)
        @JvmStatic
        fun pending(row: JSONObject, now: Instant): Boolean {
            return (if (openEnd(row)) active(row)
            else instant(row.getString("ends_at")).isAfter(now))
        }

        @Throws(Exception::class)
        @JvmStatic
        fun weatherStart(row: JSONObject, now: Instant): Instant {
            val start: Instant = instant(row.getString("starts_at"))
            return (if (openEnd(row) && start.isBefore(now)) now else start)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun weatherEnd(row: JSONObject, now: Instant): Instant {
            return (if (openEnd(row)) weatherStart(row, now).plusSeconds(1)
            else instant(row.getString("ends_at")))
        }

        @Throws(Exception::class)
        @JvmStatic
        fun overlapKind(left: JSONObject, right: JSONObject): Int {
            val a: Instant = instant(left.getString("starts_at"))
            val b: Instant = instant(right.getString("starts_at"))
            val x: Boolean = openEnd(left)
            val y: Boolean = openEnd(right)
            val start: Instant = a.minusSeconds(left.optInt("travel_minutes") * 60L)
            val other: Instant = b.minusSeconds(right.optInt("travel_minutes") * 60L)
            val end: Instant = (if (x) a else instant(left.getString("ends_at")))
            val until: Instant = (if (y) b else instant(right.getString("ends_at")))
            if (
                ((((start.isBefore(until) && end.isAfter(other)) ||
                    ((x && !a.isBefore(other)) && a.isBefore(until))) ||
                    ((y && !b.isBefore(start)) && b.isBefore(end))) || ((x && y) && a.equals(b)))
            ) {
                return 2
            }
            return (if (((x && (y || until.isAfter(start))) || (y && (x || end.isAfter(other))))) 1
            else 0)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun instant(value: String): Instant {
            try {
                return OffsetDateTime.parse(value).toInstant()
            } catch (bad: Exception) {
                throw IllegalArgumentException(
                    "Beginn und Ende benötigen Datum, Uhrzeit und Zeitzonenoffset."
                )
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun local(value: String, zone: String, offset: String): Instant {
            val time: LocalDateTime = LocalDateTime.parse(value)
            var id: ZoneId = ZoneId.of(zone)
            val valid: MutableList<ZoneOffset> = id.getRules().getValidOffsets(time)
            if (valid.isEmpty()) {
                throw IllegalArgumentException(
                    "Diese Uhrzeit existiert wegen der Zeitumstellung nicht."
                )
            }
            if ((valid.size > 1) && offset.isEmpty()) {
                throw IllegalArgumentException(
                    "Diese Uhrzeit gibt es zweimal. Bitte den Zeitzonenoffset auswählen."
                )
            }
            val chosen: ZoneOffset = (if (offset.isEmpty()) valid.get(0) else ZoneOffset.of(offset))
            if (!valid.contains(chosen)) {
                throw IllegalArgumentException("Zeitzonenoffset passt nicht zur Ortszeit.")
            }
            return time.toInstant(chosen)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun validate(row: JSONObject, old: JSONObject?, s: JSONObject) {
            var customer: JSONObject? =
                OwnerJournalRules.findOrNull(
                    s.getJSONArray("customers"),
                    row.optString("customer_id"),
                )
            if (((customer == null) || ((old == null) && (customer!!.optInt("active", 1) == 0)))) {
                throw IllegalArgumentException(
                    "Bitte einen vorhandenen aktiven Einsatzkunden wählen."
                )
            }
            val orderId: String = row.optString("order_id")
            if (!orderId.isEmpty()) {
                var order: JSONObject = PixelMcpWriteTools.find(s.getJSONArray("orders"), orderId)
                if (!row.getString("customer_id").equals(order.optString("customer_id"))) {
                    throw IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.")
                }
            }
            if (row.optString("title").trim().isEmpty()) {
                throw IllegalArgumentException("Bitte eine Tätigkeit eintragen.")
            }
            val zone: ZoneId = ZoneId.of(row.optString("timezone", "Europe/Vienna"))
            row.put("timezone", zone.getId())
            val endValue: String = row.getString("ends_at")
            val open: Boolean = endValue.isEmpty()
            val start: Instant = instant(row.getString("starts_at"))
            val end: Instant? = (if (open) null else instant(endValue))
            val minutes: Long = (if (open) 0 else Duration.between(start, end).toMinutes())
            if (!open && ((minutes < 5) || (minutes > (24 * 60)))) {
                throw IllegalArgumentException(
                    "Ein Einsatz dauert mindestens5Minuten und höchstens24Stunden. Mehrtägige Arbeit bitte auf Einsätze aufteilen."
                )
            }
            row.put("starts_at", start.toString())
                .put("ends_at", (if (open) "" else end!!.toString()))
            if (!row.has("status")) {
                row.put("status", "tentative")
            }
            if (
                (!Arrays.asList("draft", "tentative", "confirmed", "completed", "cancelled")
                    .contains(row.optString("status")))
            ) {
                throw IllegalArgumentException("Ungültiger Terminstatus.")
            }
            if (!row.has("purpose")) {
                row.put("purpose", "work")
            }
            if (!Arrays.asList("work", "inspection").contains(row.optString("purpose"))) {
                throw IllegalArgumentException("Ungültiger Terminzweck.")
            }
            if (!row.has("weather_profile")) {
                row.put("weather_profile", "general")
            }
            if (!Arrays.asList(*PROFILES).contains(row.optString("weather_profile"))) {
                throw IllegalArgumentException("Wetterprofil bitte bewusst wählen.")
            }
            if (row.has("scope_refs")) {
                val refs: JSONArray? = row.optJSONArray("scope_refs")
                if ((refs == null) || (refs!!.length() > 200)) {
                    throw IllegalArgumentException("Ungültige Leistungsreferenzen.")
                }
                val seen: MutableSet<String> = HashSet()
                run {
                    var i: Int = 0
                    while (i < refs!!.length()) {
                        val ref: String = refs!!.getString(i)
                        val split: Int = ref.lastIndexOf('#')
                        if ((split < 1) || !seen.add(ref)) {
                            throw IllegalArgumentException(
                                "Leistungsreferenz als Angebots-ID#Positionsindex angeben."
                            )
                        }
                        var offer: JSONObject =
                            PixelMcpWriteTools.find(
                                s.getJSONArray("offers"),
                                ref.substring(0, split),
                            )
                        val position: Int = Integer.parseInt(ref.substring(split + 1))
                        if (
                            (((((!offer.optString("status").equals("accepted") ||
                                !row.getString("customer_id")
                                    .equals(offer.optString("customer_id"))) ||
                                orderId.isEmpty()) ||
                                !orderId.equals(
                                    offer.optString(
                                        "converted_order_id",
                                        offer.optString("order_id"),
                                    )
                                )) || (position < 0)) ||
                                (position >= offer.getJSONArray("positions").length()))
                        ) {
                            throw IllegalArgumentException(
                                "Leistung gehört nicht zu einem angenommenen Angebot dieses Auftrags."
                            )
                        }
                        i++
                    }
                }
            }
            for (key in arrayOf("travel_minutes", "break_minutes")) {
                val n: Int = row.optInt(key, 0)
                if ((n < 0) || (n > 720)) {
                    throw IllegalArgumentException(
                        "Pause/Anfahrt muss zwischen0und720Minuten liegen."
                    )
                }
                row.put(key, n)
            }
            if (!open && (row.getInt("break_minutes") >= minutes)) {
                throw IllegalArgumentException("Die Pause muss kürzer als der Einsatz sein.")
            }
            val lat: Boolean = (row.has("latitude") && !row.isNull("latitude"))
            val lon: Boolean = (row.has("longitude") && !row.isNull("longitude"))
            if (lat != lon) {
                throw IllegalArgumentException("Breiten- und Längengrad gemeinsam angeben.")
            }
            if (
                (lat &&
                    (((!java.lang.Double.isFinite(row.getDouble("latitude")) ||
                        (Math.abs(row.getDouble("latitude")) > 90)) ||
                        !java.lang.Double.isFinite(row.getDouble("longitude"))) ||
                        (Math.abs(row.getDouble("longitude")) > 180)))
            ) {
                throw IllegalArgumentException("Ungültige Einsatzkoordinaten.")
            }
            for (field in arrayOf("flexible_from", "flexible_until")) {
                if (!row.optString(field).isEmpty()) {
                    LocalDate.parse(row.getString(field))
                }
            }
            if (
                (!row.optString("flexible_from").isEmpty() &&
                    start
                        .atZone(zone)
                        .toLocalDate()
                        .isBefore(LocalDate.parse(row.getString("flexible_from"))))
            ) {
                throw IllegalArgumentException(
                    "Termin liegt vor dem vereinbarten Ausführungsfenster."
                )
            }
            if (
                (!row.optString("flexible_until").isEmpty() &&
                    (if (open) start else end!!.minusMillis(1))
                        .atZone(zone)
                        .toLocalDate()
                        .isAfter(LocalDate.parse(row.getString("flexible_until"))))
            ) {
                throw IllegalArgumentException(
                    "Termin liegt nach dem vereinbarten Ausführungsfenster."
                )
            }
            if (
                ((old != null) &&
                    (!old!!.optString("customer_id").equals(row.optString("customer_id")) ||
                        !old!!.optString("order_id").equals(orderId)))
            ) {
                throw IllegalArgumentException(
                    "Die ursprüngliche Kundenzuordnung bleibt erhalten. Bei falschem Kontext Termin absagen und neu planen."
                )
            }
            if (
                (((old != null) && old!!.optString("status").equals("cancelled")) &&
                    !row.optString("status").equals("cancelled"))
            ) {
                throw IllegalArgumentException(
                    "Abgesagten Termin nicht still reaktivieren. Bitte einen neuen Termin planen."
                )
            }
            if (
                (((old != null) && !old!!.optString("status").equals("draft")) &&
                    row.optString("status").equals("draft"))
            ) {
                throw IllegalArgumentException(
                    "Geplanten Termin verschieben oder ausdrücklich absagen; nicht zum Entwurf zurückstufen."
                )
            }
            if (!active(row) || row.optString("status").equals("draft")) {
                return
            }
            var all: JSONArray? = s.optJSONArray("appointments")
            run {
                var i: Int = 0
                while ((all != null) && (i < all!!.length())) {
                    val other: JSONObject = all!!.getJSONObject(i)
                    if (
                        ((row.getString("id").equals(other.optString("id")) || !active(other)) ||
                            other.optString("status").equals("draft"))
                    ) {
                        i++
                        continue
                    }
                    if (overlapKind(row, other) == 2) {
                        throw PixelMcpWriteTools.Rejected(
                            "APPOINTMENT_OVERLAP",
                            "Zeit oder Anfahrt überschneidet sich mit einem vorhandenen Einsatz. Bitte eine freie Zeit wählen.",
                        )
                    }
                    i++
                }
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun prepare(
            name: String,
            a: JSONObject,
            s: JSONObject,
            request: String,
            stamp: String,
        ): JSONObject {
            val create: Boolean = name.equals("create_appointment")
            var old: JSONObject? =
                (if (create) null
                else
                    PixelMcpWriteTools.find(
                        s.getJSONArray("appointments"),
                        a.getString("appointment_id"),
                    ))
            if ((old != null) && !hash(old).equals(a.getString("expected_record_hash"))) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Termin wurde inzwischen geändert. Eingaben bleiben erhalten.",
                )
            }
            var row: JSONObject = (if (old == null) JSONObject() else LocalJournalRules.clean(old))
            val changes: JSONObject? = (if (create) a else a.optJSONObject("updates"))
            if (changes != null) {
                for (key in FIELDS) {
                    if (changes!!.has(key)) {
                        row.put(key, changes!!.get(key))
                    }
                }
            }
            if (create) {
                row.put("id", ("apt_" + PixelMcpWriteTools.hash(request).substring(0, 32)))
                    .put("created_at", stamp)
                    .put("source", "owner_app_mcp")
            }
            if (name.equals("cancel_appointment")) {
                row.put("status", "cancelled")
            }
            row.put("updated_at", stamp)
            validate(row, old, s)
            if (old == null) {
                row.put("_mcp_create", true)
            } else {
                row.put("_mcp_base", LocalJournalRules.guard(old, "appointment"))
            }
            return row
        }

        @Throws(Exception::class)
        @JvmStatic
        fun sorted(s: JSONObject): JSONArray {
            val rows: MutableList<JSONObject> = ArrayList()
            var all: JSONArray? = s.optJSONArray("appointments")
            run {
                var i: Int = 0
                while ((all != null) && (i < all!!.length())) {
                    rows.add(all!!.getJSONObject(i))
                    i++
                }
            }
            rows.sortWith(Comparator.comparing(lambda0@{ r -> r.optString("starts_at") }))
            return JSONArray(rows)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun protectParent(s: JSONObject, entity: String, id: String) {
            if (!entity.equals("customer") && !entity.equals("order")) {
                return
            }
            val rows: JSONArray? = s.optJSONArray("appointments")
            run {
                var i: Int = 0
                while ((rows != null) && (i < rows!!.length())) {
                    if (id.equals(rows!!.getJSONObject(i).optString("" + entity + "_id"))) {
                        throw IllegalArgumentException(
                            "Zugeordnete Termine zuerst in den Papierkorb verschieben. Terminverlauf und Kundenbeziehungen bleiben geschützt."
                        )
                    }
                    i++
                }
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun worklog(row: JSONObject, old: JSONObject?, snapshot: JSONObject) {
            var id: String = row.optString("appointment_id")
            if (
                (((old != null) && !old!!.optString("appointment_id").isEmpty()) &&
                    !old!!.optString("appointment_id").equals(id))
            ) {
                throw IllegalArgumentException(
                    "Die ursprüngliche Terminreferenz der Arbeit bleibt erhalten."
                )
            }
            if (id.isEmpty()) {
                return
            }
            val a: JSONObject = PixelMcpWriteTools.find(snapshot.getJSONArray("appointments"), id)
            if (
                (!a.getString("customer_id").equals(row.optString("customer_id")) ||
                    !a.optString("order_id").equals(row.optString("order_id")))
            ) {
                throw IllegalArgumentException(
                    "Arbeit und Termin gehören zu verschiedenen Kunden/Aufträgen."
                )
            }
        }
    }
}
