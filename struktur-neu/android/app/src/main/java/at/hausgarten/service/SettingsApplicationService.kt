package at.hausgarten.service

import org.json.*
import java.util.*

/** Settings use cases. Effects pass through the existing owner repository transaction. */
class SettingsApplicationService : OwnerCommand {
    private val repository: OwnerRepository
    constructor(repository: OwnerRepository) {
        this.repository = repository
    }
    @Throws(Exception::class)
    override fun perform(name: String, args: JSONObject, request: String): JSONObject {
        var all: JSONObject = repository.effective()
        var stamp: String = java.time.Instant.now().toString()
        when ((name)) {
            "update_business_settings" -> {
                run {
                    var p: JSONObject = JSONObject(args.toString())
                    p.remove("request_id")
                    OwnerJournalRules.apply(JSONObject(all.toString()), JSONObject().put("kind", "business_settings").put("payload", p), HashMap(), JSONObject())
                    return repository.enqueue("business_settings", p, request)
                }
            }
            "update_settings" -> {
                run {
                    var s: JSONObject = repository.settings()
                    if ((!PixelMcpWriteTools.hash(s).equals(args.getString("expected_record_hash")))) {
                        throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Einstellungen wurden geändert.")
                    }
                    var u: JSONObject = args.getJSONObject("updates")
                    run {
                        var keys: MutableIterator<String> = u.keys()
                        while (keys.hasNext()) {
                            var key: String = keys.next()
                            s.put(key, u.get(key))
                        }
                    }
                    repository.putOwnerValue("settings", s.toString())
                    return JSONObject().put("settings", s).put("record_hash", PixelMcpWriteTools.hash(s)).put("scope", "device_only")
                }
            }
            else -> {
                throw IllegalArgumentException("Eigentümerfunktion fehlt.")
            }
        }
    }
}
