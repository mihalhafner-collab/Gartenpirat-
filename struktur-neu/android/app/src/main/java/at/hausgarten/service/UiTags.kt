package at.hausgarten.service

/** Stable ids of controls that instrumentation tests use instead of visible text. */
internal object UiTags {
    const val BACK = "action:back"
    const val CREATE = "action:create"
    const val MORE = "action:more"

    fun area(section: String) = "area:$section"
}
