package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Small validation helpers for the existing manual offline write path. No storage/migration. */
class EntityProtectionRules {
    companion object {
        @JvmStatic fun text(row: JSONObject?, key: String): String {
            return (if (((row == null) || row!!.isNull(key))) "" else row!!.optString(key).trim { it <= ' ' })
        }
        private fun normalized(value: String): String {
            return value.trim { it <= ' ' }.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        }
        private fun digits(value: String): String {
            return value.replace(Regex("[^0-9]"), "")
        }
        private fun find(rows: JSONArray?, id: String): JSONObject? {
            run {
                var i: Int = 0
                while (((rows != null) && (i < rows!!.length()))) {
                    var row: JSONObject? = rows!!.optJSONObject(i)
                    if ((((row != null) && id.equals(text(row, "id"))))) {
                        return row
                    }
                    i++
                }
            }
            return null
        }
        @JvmStatic fun validateNewCustomer(payload: JSONObject, customers: JSONArray?) {
            if ((payload.optBoolean("allow_duplicate", false))) {
                return
            }
            var name: String = normalized(text(payload, "name"))
            var fullName: String = normalized((("" + text(payload, "first_name") + " ") + text(payload, "last_name")))
            var company: String = normalized(text(payload, "company"))
            var email: String = normalized(text(payload, "email"))
            var phone: String = digits(text(payload, "phone"))
            var source: String = text(payload, "sevdesk_contact_id")
            run {
                var i: Int = 0
                while (((customers != null) && (i < customers!!.length()))) {
                    var row: JSONObject? = customers!!.optJSONObject(i)
                    if ((((row == null) || text(payload, "id").equals(text(row, "id"))))) {
                        i++
                        continue
                    }
                    var full: String = normalized((("" + text(row, "first_name") + " ") + text(row, "last_name")))
                    var sameName: Boolean = ((!name.isEmpty() && (((name.equals(normalized(text(row, "name"))) || name.equals(full)) || name.equals(normalized(text(row, "company")))))) || (!fullName.isEmpty() && ((fullName.equals(full) || fullName.equals(normalized(text(row, "name")))))))
                    var sameCompany: Boolean = (!company.isEmpty() && company.equals(normalized(text(row, "company"))))
                    var sameContact: Boolean = ((((!email.isEmpty() && email.equals(normalized(text(row, "email"))))) || ((!phone.isEmpty() && phone.equals(digits(text(row, "phone")))))) || ((!source.isEmpty() && source.equals(text(row, "sevdesk_contact_id")))))
                    if ((((sameName || sameCompany) || sameContact))) {
                        throw IllegalArgumentException(("" + (("DUPLICATE_CUSTOMER: Ein ähnlicher Kunde existiert bereits: " + text(row, "name")) + ((if (text(row, "city").isEmpty()) "" else (" · " + text(row, "city"))))) + ". Bitte vorhandenen Kunden prüfen, auch archivierte Kunden."))
                    }
                    i++
                }
            }
        }
        @JvmStatic fun validateOrderChange(payload: JSONObject, previous: JSONObject?, worklogs: JSONArray?, invoices: JSONArray?, photos: JSONArray?) {
            if ((((previous == null) || text(previous, "customer_id").equals(text(payload, "customer_id"))))) {
                return
            }
            for (rows in arrayOf(worklogs, invoices, photos)) {
                run {
                    var i: Int = 0
                    while (((rows != null) && (i < rows!!.length()))) {
                        var row: JSONObject? = rows!!.optJSONObject(i)
                        if ((((row != null) && text(previous, "id").equals(text(row, "order_id"))))) {
                            throw IllegalArgumentException("Der Kunde kann nicht geändert werden: Dieser Auftrag besitzt bereits zugeordnete Arbeiten, Rechnungen oder Fotos.")
                        }
                        i++
                    }
                }
            }
        }
        @JvmStatic fun validatePhoto(payload: JSONObject, customers: JSONArray?, orders: JSONArray?, worklogs: JSONArray?) {
            var customer: String = text(payload, "customer_id")
            var orderId: String = text(payload, "order_id")
            var workId: String = text(payload, "worklog_id")
            if ((((((customer.isEmpty() && orderId.isEmpty()) && workId.isEmpty()) && text(payload, "session_id").isEmpty()) && !text(payload, "role").equals("profile")))) {
                return
            }
            if (((find(customers, customer) == null))) {
                throw IllegalArgumentException("Der ausgewählte Kunde existiert nicht.")
            }
            if ((!orderId.isEmpty())) {
                var order: JSONObject? = find(orders, orderId)
                if (((order == null))) {
                    throw IllegalArgumentException("Der ausgewählte Auftrag existiert nicht.")
                }
                if ((!customer.equals(text(order, "customer_id")))) {
                    throw IllegalArgumentException("Kunde und Auftrag des Fotos passen nicht zusammen.")
                }
            }
            if ((!workId.isEmpty())) {
                var work: JSONObject? = find(worklogs, workId)
                if (((work == null))) {
                    throw IllegalArgumentException("Die ausgewählte Arbeitsaufzeichnung existiert nicht.")
                }
                if ((!customer.equals(text(work, "customer_id")))) {
                    throw IllegalArgumentException("Kunde und Arbeitsaufzeichnung des Fotos passen nicht zusammen.")
                }
                if ((((!orderId.isEmpty() && !text(work, "order_id").isEmpty()) && !orderId.equals(text(work, "order_id"))))) {
                    throw IllegalArgumentException("Auftrag und Arbeitsaufzeichnung des Fotos passen nicht zusammen.")
                }
            }
        }
        @JvmStatic fun validateInvoiceDelete(row: JSONObject?) {
            if (((row == null))) {
                throw IllegalArgumentException("Die Rechnung wurde nicht gefunden. Bitte Ansicht aktualisieren.")
            }
            if (((!text(row, "invoice_number").isEmpty() && !text(row, "invoice_number").startsWith("Entwurf")))) {
                throw IllegalArgumentException("Eine vergebene Rechnungsnummer bleibt als Beleg erhalten. Nur einen lokalen Entwurf löschen.")
            }
            if ((((!text(row, "sevdesk_id").isEmpty() || !text(row, "issued_at").isEmpty()) || !java.util.Arrays.asList("Entwurf", "Storniert").contains(text(row, "status"))))) {
                throw IllegalArgumentException("Diese Rechnung ist geschützt und kann hier nicht gelöscht werden. Nur lokale, nicht ausgestellte Entwürfe dürfen entfernt werden.")
            }
        }
    }
}
