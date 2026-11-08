package at.hausgarten.service

import org.json.*

/**
 * Actual request transaction algorithm; Android SQLite supplies only the transaction/storage port.
 */
class OwnerRequestTransactions {
    interface Store {
        fun begin()

        fun read(key: String): String

        fun write(key: String, value: String)

        fun successful()

        fun end()
    }

    fun interface Command {
        @Throws(Exception::class) fun run(): JSONObject
    }

    private constructor() {}

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun runRequest(
            store: Store,
            request: String,
            fingerprint: String,
            command: Command,
            now: String,
        ): JSONObject {
            store.begin()
            var result: JSONObject
            try {
                var old: String = store.read(("receipt:" + request))
                var guard: String = store.read(("receipt_guard:" + request))
                if ((!old.isEmpty())) {
                    var receipt: JSONObject = JSONObject(old)
                    requireFingerprint(receipt, fingerprint)
                    if ((receipt.getJSONObject("result").optBoolean("_result_content_removed"))) {
                        throw PixelMcpWriteTools.Rejected(
                            "REQUEST_EXPIRED",
                            "Dieser verarbeitete Vorgang wurde endgültig entfernt. Aktuellen Stand prüfen; keine erneute Ausführung.",
                        )
                    }
                    result = receipt.getJSONObject("result").put("replayed", true)
                } else {
                    if ((!guard.isEmpty())) {
                        requireFingerprint(JSONObject(guard), fingerprint)
                        throw PixelMcpWriteTools.Rejected(
                            "REQUEST_EXPIRED",
                            "Diese Anfrage wurde bereits verarbeitet. Der Detailbeleg ist abgelaufen. Aktuellen Eintrag prüfen; die Aktion wird nicht erneut ausgeführt.",
                        )
                    } else {
                        result = command.run()
                        store.write(
                            ("receipt:" + request),
                            JSONObject()
                                .put("fingerprint", fingerprint)
                                .put("created_at", now)
                                .put("result", result)
                                .toString(),
                        )
                    }
                }
                store.successful()
            } finally {
                store.end()
            }
            return result
        }

        @Throws(Exception::class)
        private fun requireFingerprint(receipt: JSONObject, fingerprint: String) {
            if ((!fingerprint.equals(receipt.getString("fingerprint")))) {
                throw PixelMcpWriteTools.Rejected(
                    "REPLAY_MISMATCH",
                    "Die Anfragekennung gehört zu anderen Angaben.",
                )
            }
        }
    }
}
