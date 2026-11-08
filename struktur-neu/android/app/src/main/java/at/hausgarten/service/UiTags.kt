package at.hausgarten.service

/** Stable ids of controls that instrumentation tests use instead of visible text. */
internal object UiTags {
    const val BACK = "action:back"
    const val CREATE = "action:create"
    const val MORE = "action:more"
    const val PRIMARY = "action:primary"
    const val SECONDARY = "action:secondary"
    const val FILTER = "action:filter"
    const val VOICE = "action:voice"
    const val DETAIL_MENU = "action:detail-menu"
    const val PROGRESS = "billing:progress"
    const val TODAY_SEARCH = "action:today-search"
    const val SEARCH_RECENT = "search:recent"
    const val SEARCH_CALL = "search:call"
    const val SEARCH_ROUTE = "search:route"

    fun searchGroup(type: String) = "search-group:$type"

    fun area(section: String) = "area:$section"
}
