package at.hausgarten.service

/** One operation, one account/scope snapshot. Credentials are memory-only. */
internal class CalendarCredential(granted: String?, private val source: Source) :
    CalendarClient.Token {
    interface Source {
        @Throws(Exception::class) fun acquire(): String?

        @Throws(Exception::class) fun invalidate(rejected: String)
    }

    private var value: String? = granted ?: ""

    @Synchronized
    @Throws(Exception::class)
    override fun get(): String {
        if (value.isNullOrEmpty()) value = source.acquire()
        if (value.isNullOrEmpty()) {
            value = ""
            throw HttpTransport.Failure(
                "CALENDAR_AUTH_REQUIRED",
                CalendarAccessRules.message("CALENDAR_AUTH_REQUIRED"),
                0,
                true,
            )
        }
        return value!!
    }

    @Synchronized
    @Throws(Exception::class)
    override fun invalidate(rejected: String) {
        if (value == rejected) {
            value = ""
            source.invalidate(rejected)
        }
    }
}
