package at.hausgarten.service

import org.json.*

/** One existing catalog bootstrap rule, shared by live state and captured-state readers. */
class ServiceCatalogBootstrap {
    fun interface Source {
        @Throws(Exception::class) fun read(): String
    }

    private constructor() {}

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun seed(snapshot: JSONObject, source: Source) {
            if (
                ((!snapshot.optBoolean("service_catalog_initialized") &&
                    (snapshot.getJSONArray("service_catalog").length() == 0)))
            ) {
                snapshot.put("service_catalog", JSONArray(source.read()))
            }
            snapshot.put("service_catalog_initialized", true)
        }
    }
}
