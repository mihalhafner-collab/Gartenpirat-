package at.hausgarten.service

/** Stable ids of controls that instrumentation tests use instead of visible text. */
internal object UiTags {
    const val BACK = "action:back"
    const val CREATE = "action:create"
    const val MORE = "action:more"
    const val PRIMARY = "action:primary"
    const val SECONDARY = "action:secondary"
    const val FILTER = "action:filter"

    fun area(section: String) = "area:$section"
}
