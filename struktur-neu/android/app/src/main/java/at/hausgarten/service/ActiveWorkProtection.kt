package at.hausgarten.service

import java.util.*
import org.json.*

/** Local timer references cannot be orphaned by either UI or MCP removal. */
class ActiveWorkProtection {
    private constructor() {}

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun require(state: JSONObject, entity: String, id: String) {
            val session: JSONObject? = state.optJSONObject("work_session")
            if (
                ((session == null) ||
                    Arrays.asList("IDLE", "FINALIZED", "CANCELLED")
                        .contains(session!!.optString("state")))
            ) {
                return
            }
            if (
                (id.equals(session!!.optString("" + entity + "_id")) ||
                    (entity.equals("worklog") &&
                        id.equals(session!!.optString("finalized_worklog_id"))))
            ) {
                throw PixelMcpWriteTools.Rejected(
                    "ACTIVE_WORK_SESSION",
                    "Die zugehörige Zeiterfassung zuerst abschließen oder bewusst verwerfen. Eintrag bleibt erhalten.",
                )
            }
        }
    }
}
