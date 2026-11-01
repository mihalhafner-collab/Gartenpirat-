package at.hausgarten.service

import org.json.*
import java.math.*
import java.nio.charset.StandardCharsets
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

/** Deterministic new-entity rules. No network, prices, times or historical links are guessed. */
class RevierRules {
    private constructor() {
    }
    companion object {
        @JvmField val ROOT: String = "revier:v1:"
        @JvmField val ROW_BYTES: Int = (192 * 1024)
        @Throws(Exception::class)
        @JvmStatic fun copy(value: JSONObject): JSONObject {
            return JSONObject(value.toString())
        }
        @JvmStatic fun error(code: String, text: String): PixelMcpWriteTools.Rejected {
            return PixelMcpWriteTools.Rejected(code, text)
        }
        @Throws(Exception::class)
        @JvmStatic fun text(row: JSONObject, key: String, max: Int, required: Boolean): String {
            var raw: Any? = row.opt(key)
            if ((((raw == null) || (raw == JSONObject.NULL)))) {
                if ((required)) {
                    throw error("INVALID_INPUT", ("" + ("Bitte " + key) + " angeben."))
                }
                return ""
            }
            if ((!(raw is String))) {
                throw error("INVALID_INPUT", ("" + key + " benötigt Text."))
            }
            var value: String = ((raw as String)).trim { it <= ' ' }
            if ((((value.length > max) || (required && value.isEmpty())))) {
                throw error("INVALID_INPUT", ("" + key + " ist leer oder zu lang."))
            }
            return value
        }
        @Throws(Exception::class)
        @JvmStatic fun id(row: JSONObject, key: String, required: Boolean): String {
            var value: String = text(row, key, 200, required)
            if (((!value.isEmpty() && !value.matches(Regex("[A-Za-z0-9_.:-]{1,200}"))))) {
                throw error("INVALID_INPUT", ("" + ("Ungültige Kennung für " + key) + "."))
            }
            return value
        }
        @Throws(Exception::class)
        @JvmStatic fun newId(kind: String, request: String): String {
            var prefix: String = (if (request.startsWith("qa_")) "qa_" else (if (request.startsWith("test_")) "test_" else ""))
            return (("" + (prefix + kind) + "_") + PixelMcpWriteTools.hash(request).substring(0, 28))
        }
        @Throws(Exception::class)
        @JvmStatic fun hash(row: JSONObject): String {
            var clean: JSONObject = copy(row)
            clean.remove("record_hash")
            return PixelMcpWriteTools.hash(clean)
        }
        @Throws(Exception::class)
        @JvmStatic fun view(row: JSONObject): JSONObject {
            return copy(row).put("record_hash", hash(row))
        }
        @Throws(Exception::class)
        @JvmStatic fun cas(old: JSONObject, expected: String?) {
            if (((((expected == null) || !expected!!.matches(Regex("[0-9a-f]{64}"))) || !hash(old).equals(expected)))) {
                throw error("WRITE_CONFLICT", "Der Eintrag wurde inzwischen geändert. Eingaben bleiben erhalten; aktuellen Stand prüfen.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun integer(row: JSONObject, key: String, min: Int, max: Int, fallback: Int): Int {
            if ((!row.has(key))) {
                return fallback
            }
            var raw: Any = row.get(key)
            var value: String = raw.toString()
            if ((!value.matches(Regex("-?(0|[1-9][0-9]*)")))) {
                throw error("INVALID_INPUT", ("" + key + " benötigt eine ganze Zahl."))
            }
            try {
                var n: Int = Integer.parseInt(value)
                if ((((n < min) || (n > max)))) {
                    throw NumberFormatException()
                }
                return n
            } catch (invalid: NumberFormatException) {
                throw error("INVALID_INPUT", ("" + key + " liegt außerhalb des gültigen Bereichs."))
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun bool(row: JSONObject, key: String, fallback: Boolean): Boolean {
            if ((!row.has(key))) {
                return fallback
            }
            if ((!(row.get(key) is Boolean))) {
                throw error("INVALID_INPUT", ("" + key + " benötigt Ja oder Nein."))
            }
            return row.getBoolean(key)
        }
        @Throws(Exception::class)
        @JvmStatic fun decimal(row: JSONObject, key: String, positive: Boolean, optional: Boolean): String {
            if (((optional && (((!row.has(key) || row.isNull(key)) || row.optString(key).trim { it <= ' ' }.isEmpty()))))) {
                return ""
            }
            var raw: Any? = row.opt(key)
            if (((!(raw is String) || !((raw as String)).matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?"))))) {
                throw error("INVALID_INPUT", ("" + key + " als Dezimalzahl mit Punkt und höchstens sechs Nachkommastellen angeben."))
            }
            var n: BigDecimal = BigDecimal((raw as String))
            if ((((n.precision() > 24) || (positive && (n.signum() < 0))))) {
                throw error("INVALID_INPUT", ("" + ("Ungültige Menge für " + key) + "."))
            }
            return n.stripTrailingZeros().toPlainString()
        }
        @Throws(Exception::class)
        @JvmStatic fun minor(row: JSONObject, key: String): Long {
            var value: String = row.get(key).toString()
            if ((!value.matches(Regex("0|[1-9][0-9]*")))) {
                throw error("INVALID_INPUT", ("" + key + " in kleinsten Währungseinheiten angeben."))
            }
            try {
                var result: Long = BigDecimal(value).longValueExact()
                if (((result > 9007199254740991L))) {
                    throw ArithmeticException()
                }
                return result
            } catch (invalid: ArithmeticException) {
                throw error("INVALID_INPUT", ("" + key + " ist zu groß für die gemeinsame JSON-Schnittstelle."))
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun date(value: String): LocalDate {
            try {
                return LocalDate.parse(value)
            } catch (invalid: Exception) {
                throw error("INVALID_INPUT", "Datum im Format JJJJ-MM-TT angeben.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun date(row: JSONObject, key: String, required: Boolean): String {
            var value: String = text(row, key, 10, required)
            if ((!value.isEmpty())) {
                date(value)
            }
            return value
        }
        @Throws(Exception::class)
        @JvmStatic fun time(row: JSONObject, key: String): String {
            var value: String = text(row, key, 5, false)
            if ((!value.isEmpty())) {
                try {
                    if ((!value.matches(Regex("[0-9]{2}:[0-9]{2}")))) {
                        throw IllegalArgumentException()
                    }
                    LocalTime.parse(value)
                } catch (invalid: Exception) {
                    throw error("INVALID_INPUT", "Zeitfenster als HH:mm angeben.")
                }
            }
            return value
        }
        @Throws(Exception::class)
        @JvmStatic fun instant(value: String): String {
            try {
                return OffsetDateTime.parse(value).toInstant().toString()
            } catch (invalid: Exception) {
                throw error("INVALID_INPUT", "Zeitpunkt mit Datum, Uhrzeit und Zeitzonenoffset angeben.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun fields(args: JSONObject, vararg allowed: String) {
            var names: MutableSet<String> = HashSet(Arrays.asList(*allowed))
            run {
                var it: MutableIterator<String> = args.keys()
                while (it.hasNext()) {
                    if ((!names.contains(it.next()))) {
                        throw error("INVALID_INPUT", "Unbekanntes Feld; Eingaben unverändert prüfen.")
                    }
                }
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun bounded(row: JSONObject) {
            if (((row.toString().toByteArray(StandardCharsets.UTF_8).size > ROW_BYTES))) {
                throw error("RECORD_TOO_LARGE", "Dieser technische Eintrag ist zu groß. Notizen kürzen oder mehrere getrennte Einträge verwenden.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun version(row: JSONObject) {
            var version: Any? = row.opt("format_version")
            var kind: String = row.optString("kind")
            var maintenanceKind: Boolean = (kind.equals("equipment") || kind.equals("equipment_maintenance"))
            if (((version is Number && (((version.toString().equals("1") || (version.toString().equals("2") && maintenanceKind)) || ((version.toString().equals("3") && kind.equals("equipment")) && (row.optJSONObject("profile_photo") != null))))))) {
                if (((version.toString().equals("1") && ((kind.equals("equipment_maintenance") || ((kind.equals("equipment") && (row.optJSONArray("maintenance_plans") != null)) && (row.optJSONArray("maintenance_plans").length() > 0))))))) {
                    throw error("REVIER_SCHEMA_UPGRADE_REQUIRED", "Wartungsdaten benötigen ihren geprüften Datensatzvertrag. Keine automatische Migration.")
                }
                if ((((kind.equals("equipment") && row.has("profile_photo")) && !version.toString().equals("3")))) {
                    throw error("REVIER_SCHEMA_UPGRADE_REQUIRED", "Gerätefotos benötigen ihren geprüften Datensatzvertrag.")
                }
                return
            }
            throw error("REVIER_SCHEMA_UPGRADE_REQUIRED", "Ein neues oder unbekanntes Datenformat benötigt eine passende Appversion. Keine Änderung vorgenommen.")
        }
        @JvmStatic fun activeVisit(row: JSONObject): Boolean {
            return Arrays.asList("planned", "in_progress", "partial").contains(row.optString("status"))
        }
        @Throws(Exception::class)
        @JvmStatic fun season(rule: JSONObject, date: LocalDate): Boolean {
            var day: MonthDay = MonthDay.from(date)
            var start: MonthDay = MonthDay.parse(("--" + rule.optString("season_start", "01-01")))
            var end: MonthDay = MonthDay.parse(("--" + rule.optString("season_end", "12-31")))
            return (if ((start.compareTo(end) <= 0)) ((day.compareTo(start) >= 0) && (day.compareTo(end) <= 0)) else ((day.compareTo(start) >= 0) || (day.compareTo(end) <= 0)))
        }
        @Throws(Exception::class)
        @JvmStatic fun occurs(rule: JSONObject, date: LocalDate): Boolean {
            var anchor: LocalDate = date(rule.getString("anchor_date"))
            return ((!date.isBefore(anchor) && ((ChronoUnit.DAYS.between(anchor, date) % rule.getInt("interval_days")) == 0L)) && season(rule, date))
        }
        @Throws(Exception::class)
        @JvmStatic fun seasonField(row: JSONObject, key: String) {
            var value: String = text(row, key, 5, false)
            if ((value.isEmpty())) {
                value = (if (key.equals("season_start")) "01-01" else "12-31")
            }
            try {
                MonthDay.parse(("--" + value))
            } catch (invalid: Exception) {
                throw error("INVALID_INPUT", "Saisonbeginn/-ende als MM-TT angeben.")
            }
            row.put(key, value)
        }
    }
}
