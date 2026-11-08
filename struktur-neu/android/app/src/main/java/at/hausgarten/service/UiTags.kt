package at.hausgarten.service

/** Stable ids of controls that instrumentation tests use instead of visible text. */
internal object UiTags {
    const val PLANNING_GRID = "planning:grid"
    const val PLANNING_MAP = "planning:map"
    const val PLANNING_MAP_ALL = "planning:map-all"
    const val PLANNING_PREVIOUS = "planning:previous"
    const val PLANNING_NEXT = "planning:next"
    const val PLANNING_TODAY = "planning:today"
    const val PLANNING_TOUR = "planning:tour"
    const val PLANNING_CUSTOMER = "planning:customer"
    const val PLANNING_CUSTOMER_CLEAR = "planning:customer-clear"
    const val PLANNING_WEATHER_DETAILS = "planning:weather-details"
    const val PLANNING_REPEAT = "planning:repeat"
    const val PLANNING_SETTINGS = "planning:settings"
    const val PLANNING_ALL_VISITS = "planning:all-visits"
    const val PLANNING_SYNC = "planning:sync"
    const val PLANNING_RELOAD = "planning:reload"
    const val PLANNING_MOVING = "planning:moving"
    const val PLANNING_PAGE_PREVIOUS = "planning:page-previous"
    const val PLANNING_PAGE_NEXT = "planning:page-next"
    const val TOUR_ORDER = "tour:order"
    const val TOUR_SAVE = "tour:save"
    const val TOUR_NAVIGATE = "tour:navigate"

    fun planningView(mode: String) = "planning:view:$mode"

    fun planningStatus(status: String) = "planning:status:$status"

    fun planningEntry(kind: String, id: String) = "planning:entry:$kind:$id"

    fun planningWeather(kind: String, id: String) = "planning:weather:$kind:$id"

    fun planningPin(kind: String, id: String) = "planning:pin:$kind:$id"

    fun planningRoute(kind: String, id: String) = "planning:route:$kind:$id"

    fun tourSelect(id: String) = "tour:select:$id"

    fun tourMove(id: String, direction: String) = "tour:move:$direction:$id"

    const val UNDO = "action:undo"
    const val DELETE_SELECTION = "action:delete-selection"

    fun swipe(entity: String, id: String) = "swipe:$entity:$id"

    fun billingTab(index: Int) = "billing-tab:$index"

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

    fun recordChoice(collection: String, id: String) = "record-choice:$collection:$id"

    fun area(section: String) = "area:$section"
}
