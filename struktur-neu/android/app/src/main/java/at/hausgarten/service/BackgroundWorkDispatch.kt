package at.hausgarten.service

/** Scheduling failures never roll back a committed business write. */
internal class BackgroundWorkDispatch private constructor() {
    interface Host {
        fun state(pending: Boolean, code: String)

        fun post(task: Runnable): Boolean

        @Throws(Exception::class) fun prepare()

        @Throws(Exception::class) fun localReady(): Boolean

        @Throws(Exception::class) fun local(delay: Long): Boolean
    }

    companion object {
        @JvmStatic
        fun request(delay: Long, host: Host) {
            host.state(true, "")
            val task = Runnable {
                try {
                    host.prepare()
                    if (!host.localReady()) {
                        host.state(true, "BACKUP_SETUP_REQUIRED")
                        return@Runnable
                    }
                    val accepted = host.local(maxOf(0L, delay))
                    host.state(!accepted, if (accepted) "" else "BACKGROUND_SCHEDULER_UNAVAILABLE")
                } catch (_: Exception) {
                    host.state(true, "BACKGROUND_SCHEDULING_FAILED")
                }
            }
            try {
                if (!host.post(task)) host.state(true, "BACKGROUND_HANDLER_UNAVAILABLE")
            } catch (_: RuntimeException) {
                host.state(true, "BACKGROUND_HANDLER_UNAVAILABLE")
            }
        }
    }
}
