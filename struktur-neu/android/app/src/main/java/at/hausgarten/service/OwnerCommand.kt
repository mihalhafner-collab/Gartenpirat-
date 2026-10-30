package at.hausgarten.service

import org.json.JSONObject

/** Application operation contract; services do not depend on their router. */
fun interface OwnerCommand {
    @Throws(Exception::class)
    fun perform(name: String, args: JSONObject, request: String): JSONObject
}
