package at.hausgarten.service

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

private class BoardEntry(val data: JSONObject) {
    val kind = data.getString("kind")
    val record = data.getJSONObject("record")
    val id = record.getString("id")
    val key = "$kind:$id"
    val title = data.getString("title")
    val subtitle = data.optString("subtitle")
    val property = data.getJSONObject("property")
    val weather = data.getJSONObject("weather")
    val start = PlanningBoardRules.start(kind, record)
    val day = PlanningBoardRules.day(kind, record)
    val minute = start?.toLocalTime()?.toSecondOfDay()?.div(60)
    val duration = PlanningBoardRules.duration(kind, record)
    val point = PlanningBoardRules.coordinates(if (kind == "visit") property else record)
    val address =
        if (kind == "visit") property.optString("address") else record.optString("location")
    val warning = weather.optString("severity") in setOf("critical", "difficult")
    val closed = record.optString("status") in setOf("completed", "cancelled", "superseded")
    val timeLabel: String
        get() {
            val begin =
                start?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: return "Uhrzeit offen"
            val end =
                if (kind == "visit") record.optString("window_end")
                else
                    record
                        .optString("ends_at")
                        .takeIf(String::isNotBlank)
                        ?.let {
                            Instant.parse(it)
                                .atZone(PlanningBoardRules.zone)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                        }
                        .orEmpty()
            return begin + if (end.isBlank()) " · Ende offen" else "–$end"
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativePlanningBoardScreen(
    controller: NativeAppController,
    weatherDetails: Boolean,
    onWeather: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onNew: (Boolean, String, String) -> Unit,
) {
    var anchor by rememberSaveable {
        mutableStateOf(
            controller.route.args.optString(
                "date",
                controller.route.args.optString(
                    "date_from",
                    LocalDate.now(PlanningBoardRules.zone).toString(),
                ),
            )
        )
    }
    var mode by rememberSaveable { mutableStateOf("day") }
    var query by rememberSaveable { mutableStateOf("") }
    var customer by rememberSaveable {
        mutableStateOf(controller.route.args.optString("customer_id"))
    }
    var customerTitle by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var chooseCustomer by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var board by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val day = LocalDate.parse(anchor)
    val from = if (mode == "week") day.minusDays((day.dayOfWeek.value - 1).toLong()) else day
    val to = if (mode == "week") from.plusDays(6) else from
    val changes by
        remember(controller) { controller.store.nativeData().watchCommittedChanges() }
            .collectAsStateWithLifecycle(-1L)
    val request =
        JSONObject()
            .put("date_from", from.toString())
            .put("date_to", to.toString())
            .put("query", query)
            .put("status", status)
            .put("offset", offset)
            .put("limit", 50)
            .apply {
                if (customer.isNotBlank()) put("customer_id", customer)
                controller.route.args.optString("order_id").takeIf(String::isNotBlank)?.let {
                    put("order_id", it)
                }
            }
    LaunchedEffect(request.toString(), changes, attempt) {
        board = null
        error = ""
        try {
            board = controller.query("get_planning_board", request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = nativeActionError(failure, "Die Planung konnte nicht geladen werden.")
        }
    }
    val entries =
        remember(board?.toString()) {
            SettlementRules.rows(board?.optJSONArray("items")).map(::BoardEntry)
        }
    fun open(entry: BoardEntry) {
        if (entry.kind == "appointment") onSelect(entry.id)
        else controller.navigate("visits", entry.id, "detail")
    }
    fun move(entry: BoardEntry, date: LocalDate, minute: Int) {
        if (moving.isNotBlank()) return
        moving = entry.key
        scope.launch {
            try {
                controller.moveBoardEntry(
                    entry.kind,
                    entry.record,
                    entry.data.getString("record_hash"),
                    date,
                    minute,
                )
                error = ""
                attempt++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error =
                    nativeActionError(
                        failure,
                        "Die Verschiebung konnte noch nicht bestätigt werden.",
                    )
            } finally {
                moving = ""
            }
        }
    }
    val ime = WindowInsets.isImeVisible
    Box(Modifier.fillMaxSize()) {
        NativeSearchList(
            "Suche",
            query,
            {
                query = it
                offset = 0
            },
            if (board == null) "Wird geladen …" else "${board?.optInt("total")} Einsätze",
            onFilters = { filters = !filters },
            onFocus = { filters = false },
        ) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!ime)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NativeDateField(
                                "Datum",
                                anchor,
                                {
                                    anchor = it
                                    offset = 0
                                },
                                required = true,
                            )
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(
                                    {
                                        anchor =
                                            day.minusDays(if (mode == "week") 7 else 1).toString()
                                        offset = 0
                                    },
                                    Modifier.semantics { testTag = UiTags.PLANNING_PREVIOUS },
                                ) {
                                    Text("‹")
                                }
                                for ((value, label) in
                                    listOf(
                                        "day" to "Tag",
                                        "week" to "Woche",
                                        "map" to "Karte",
                                    )) FilterChip(
                                    mode == value,
                                    {
                                        mode = value
                                        offset = 0
                                    },
                                    label = { Text(label) },
                                    modifier =
                                        Modifier.semantics { testTag = UiTags.planningView(value) },
                                )
                                TextButton(
                                    {
                                        anchor =
                                            day.plusDays(if (mode == "week") 7 else 1).toString()
                                        offset = 0
                                    },
                                    Modifier.semantics { testTag = UiTags.PLANNING_NEXT },
                                ) {
                                    Text("›")
                                }
                            }
                            NativeActions {
                                TextButton(
                                    {
                                        anchor = LocalDate.now(PlanningBoardRules.zone).toString()
                                        offset = 0
                                    },
                                    Modifier.semantics { testTag = UiTags.PLANNING_TODAY },
                                ) {
                                    Text("Heute")
                                }
                                TextButton(
                                    {
                                        controller.navigate(
                                            "tour",
                                            args = JSONObject().put("date", anchor),
                                        )
                                    },
                                    Modifier.semantics { testTag = UiTags.PLANNING_TOUR },
                                ) {
                                    Text("Tour")
                                }
                            }
                        }
                    }
                if (filters)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                { chooseCustomer = true },
                                Modifier.semantics { testTag = UiTags.PLANNING_CUSTOMER },
                            ) {
                                Text(
                                    customerTitle.ifBlank {
                                        if (customer.isBlank()) "Alle Kunden" else "Gewählter Kunde"
                                    }
                                )
                            }
                            if (customer.isNotBlank())
                                TextButton(
                                    {
                                        customer = ""
                                        customerTitle = ""
                                        offset = 0
                                    },
                                    Modifier.semantics { testTag = UiTags.PLANNING_CUSTOMER_CLEAR },
                                ) {
                                    Text("Alle Kunden anzeigen")
                                }
                            NativeActions {
                                for ((value, label) in
                                    listOf(
                                        "" to "Alle",
                                        "open" to "Offen",
                                        "completed" to "Erledigt",
                                        "cancelled" to "Abgesagt",
                                    )) FilterChip(
                                    status == value,
                                    {
                                        status = value
                                        offset = 0
                                    },
                                    label = { Text(label) },
                                    modifier =
                                        Modifier.semantics {
                                            testTag = UiTags.planningStatus(value)
                                        },
                                )
                            }
                            FilterChip(
                                weatherDetails,
                                { onWeather(!weatherDetails) },
                                label = { Text("Wetterdetails") },
                                modifier =
                                    Modifier.semantics { testTag = UiTags.PLANNING_WEATHER_DETAILS },
                            )
                            TextButton(
                                { onNew(true, anchor, customer) },
                                Modifier.semantics { testTag = UiTags.PLANNING_REPEAT },
                            ) {
                                Text("Wiederholungen")
                            }
                            TextButton(
                                { controller.open(PlanningConnectionActivity::class.java) },
                                Modifier.semantics { testTag = UiTags.PLANNING_SETTINGS },
                            ) {
                                Text("Kalender & Wetter")
                            }
                            TextButton(
                                {
                                    controller.navigate(
                                        "visits",
                                        args =
                                            JSONObject()
                                                .put("date_from", from.toString())
                                                .put("date_to", to.toString())
                                                .put("customer_id", customer),
                                    )
                                },
                                Modifier.semantics { testTag = UiTags.PLANNING_ALL_VISITS },
                            ) {
                                Text("Einsatzliste")
                            }
                            TextButton(
                                {
                                    checking = true
                                    scope.launch {
                                        try {
                                            val result =
                                                controller.query(
                                                    "request_calendar_sync",
                                                    JSONObject()
                                                        .put(
                                                            "request_id",
                                                            UUID.randomUUID().toString(),
                                                        ),
                                                )
                                            controller.message(
                                                if (
                                                    result.optString("state") ==
                                                        "configuration_required"
                                                )
                                                    "Kalender zuerst einrichten."
                                                else "Kalenderprüfung angefordert."
                                            )
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            error =
                                                nativeActionError(
                                                    failure,
                                                    "Der Kalender konnte nicht geprüft werden.",
                                                )
                                        } finally {
                                            checking = false
                                        }
                                    }
                                },
                                Modifier.semantics { testTag = UiTags.PLANNING_SYNC },
                                enabled = !checking,
                            ) {
                                Text("Kalender prüfen")
                            }
                        }
                    }
                if (error.isNotBlank())
                    item {
                        NativeStatus(error, NativeStatusTone.Failed)
                        TextButton(
                            { attempt++ },
                            Modifier.semantics { testTag = UiTags.PLANNING_RELOAD },
                        ) {
                            Text("Ansicht neu laden")
                        }
                    }
                if (board == null && error.isBlank())
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (moving.isNotBlank())
                    item {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().semantics { testTag = UiTags.PLANNING_MOVING }
                        )
                    }
                if (board != null && entries.isEmpty())
                    item {
                        Text(
                            if (query.isBlank() && status.isBlank())
                                "Hier planst du deine Einsätze für Tag und Woche."
                            else "Keine passenden Einsätze. Suche oder Filter ändern."
                        )
                    }
                if (entries.isNotEmpty())
                    item {
                        when {
                            ime || query.isNotBlank() ->
                                PlanningBoardList(controller, entries, weatherDetails, ::open)
                            mode == "map" -> PlanningBoardMap(controller, entries, ::open)
                            else ->
                                PlanningBoardGrid(
                                    entries,
                                    from,
                                    to,
                                    day,
                                    weatherDetails,
                                    moving.isBlank(),
                                    ::open,
                                    ::move,
                                )
                        }
                    }
                if (board?.optInt("total", 0) ?: 0 > 50)
                    item {
                        NativeActions {
                            TextButton(
                                { offset = (offset - 50).coerceAtLeast(0) },
                                Modifier.semantics { testTag = UiTags.PLANNING_PAGE_PREVIOUS },
                                enabled = offset > 0,
                            ) {
                                Text("Vorige Seite")
                            }
                            Text(
                                "${offset + 1}–${offset + entries.size} von ${board?.optInt("total")}"
                            )
                            TextButton(
                                { offset += 50 },
                                Modifier.semantics { testTag = UiTags.PLANNING_PAGE_NEXT },
                                enabled = board?.optBoolean("has_more") == true,
                            ) {
                                Text("Weitere")
                            }
                        }
                    }
            }
            if (!ime) NativeActionBar("Einsatz planen", { onNew(false, anchor, customer) })
        }
        if (!ime)
            NativeCreateButton(
                controller,
                Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 72.dp),
            )
    }
    NativeRecordDialog(
        chooseCustomer,
        controller,
        "customers",
        "Kunde auswählen",
        onDismiss = { chooseCustomer = false },
        onChoose = {
            customer = it?.id.orEmpty()
            customerTitle = it?.title.orEmpty()
            offset = 0
        },
    )
}

/** Persist before writing; an uncertain result only inspects the exact original request. */
private suspend fun NativeAppController.moveBoardEntry(
    kind: String,
    record: JSONObject,
    hash: String,
    day: LocalDate,
    minute: Int,
) {
    val key = "planning-move:$kind:" + record.getString("id")
    val saved = draft(key)
    val previous = saved?.optJSONObject("original_args")
    if (previous != null) {
        val proof =
            query(
                "inspect_edit_action",
                JSONObject()
                    .put("original_tool", saved.getString("original_tool"))
                    .put("original_args", previous),
            )
        require(proof.optString("state") in setOf("confirmed", "content_removed")) {
            "Die frühere Verschiebung ist noch offen. Bitte später erneut versuchen."
        }
        removeDraft(key)
        message("Verschiebung bestätigt.")
        return
    }
    val (tool, args) = PlanningBoardRules.move(kind, record, day, minute, hash)
    args.put("request_id", UUID.randomUUID().toString())
    saveDraft(key, JSONObject().put("original_tool", tool).put("original_args", args))
    flushDrafts()
    try {
        query(tool, args)
        removeDraft(key)
        message("Einsatz verschoben.")
    } catch (failure: NativeCallFailure) {
        if (failure.detail.optString("commit_state") == "not_committed") removeDraft(key)
        throw failure
    }
}

@Composable
private fun PlanningBoardList(
    controller: NativeAppController,
    entries: List<BoardEntry>,
    weatherDetails: Boolean,
    onOpen: (BoardEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (entry in
            entries.sortedWith(
                compareBy<BoardEntry> { it.day }.thenBy { it.minute ?: 1440 }
            )) NativeSwipeRow(
            controller,
            entry.kind,
            entry.id,
            positive = if (entry.closed) "" else "Erledigt",
        ) {
            NativeInfoRow(
                entry.title,
                nativeDisplayDate(entry.day.toString()) +
                    " · " +
                    entry.timeLabel +
                    " · " +
                    nativeDisplayText(entry.subtitle),
                { onOpen(entry) },
                Modifier.semantics { testTag = UiTags.planningEntry(entry.kind, entry.id) },
            )
            if (entry.warning || weatherDetails)
                Text(
                    entry.weather.optString("label"),
                    Modifier.padding(horizontal = 12.dp).semantics {
                        testTag = UiTags.planningWeather(entry.kind, entry.id)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun PlanningBoardGrid(
    entries: List<BoardEntry>,
    from: LocalDate,
    to: LocalDate,
    focusDay: LocalDate,
    weatherDetails: Boolean,
    enabled: Boolean,
    onOpen: (BoardEntry) -> Unit,
    onMove: (BoardEntry, LocalDate, Int) -> Unit,
) {
    val density = LocalDensity.current
    val font = density.fontScale.coerceAtLeast(1f)
    val hourHeight = (60 * font).dp
    val cardMinimum = (156 * font).dp
    val laneWidth = (168 * font.coerceAtMost(1.5f)).dp
    val minutePx = with(density) { hourHeight.toPx() / 60 }
    val lanePx = with(density) { laneWidth.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val days =
        (0..Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays().toInt()).map {
            from.plusDays(it.toLong())
        }
    val placed = mutableMapOf<String, Int>()
    val lanes =
        days.map { date ->
            val ends = mutableListOf<Float>()
            for (entry in
                entries.filter { it.day == date && it.minute != null }.sortedBy { it.minute }) {
                val start = requireNotNull(entry.minute).toFloat()
                val index = ends.indexOfFirst { it <= start }.let { if (it < 0) ends.size else it }
                val end =
                    start +
                        maxOf(
                            (entry.duration ?: 30).toFloat(),
                            with(density) { cardMinimum.toPx() } / minutePx,
                        )
                if (index == ends.size) ends.add(end) else ends[index] = end
                placed[entry.key] = index
            }
            maxOf(1, ends.size)
        }
    val dayStarts = days.indices.map { index -> lanes.take(index).sum() * lanePx }
    val totalWidth = laneWidth * lanes.sum()
    val horizontal = rememberScrollState()
    LaunchedEffect(from, to, focusDay) {
        val index = days.indexOf(focusDay)
        if (index >= 0) horizontal.scrollTo(dayStarts[index].roundToInt())
    }
    val scroll = rememberScrollState(initial = with(density) { (hourHeight * 8).roundToPx() })
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Zum Verschieben lang drücken und ziehen.", style = MaterialTheme.typography.bodySmall)
        Column(
            Modifier.fillMaxWidth().horizontalScroll(horizontal).semantics {
                testTag = UiTags.PLANNING_GRID
            }
        ) {
            Row {
                Spacer(Modifier.width(56.dp))
                for ((index, date) in days.withIndex()) Text(
                    date.format(DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)),
                    Modifier.width(laneWidth * lanes[index]).padding(4.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Box(Modifier.width(totalWidth + 56.dp).height(480.dp).verticalScroll(scroll)) {
                Box(Modifier.width(totalWidth + 56.dp).height(hourHeight * 24 + cardMinimum)) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (hour in 0..24) {
                            val y = hour * minutePx * 60
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                        }
                    }
                    for (hour in 0..23) Text(
                        "%02d:00".format(Locale.ROOT, hour),
                        Modifier.offset(y = hourHeight * hour).width(56.dp).padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    for (entry in entries.filter { it.minute != null }) {
                        val dayIndex = days.indexOf(entry.day)
                        if (dayIndex < 0) continue
                        var drag by
                            remember(entry.key, entry.data.getString("record_hash")) {
                                mutableStateOf(Offset.Zero)
                            }
                        var dragging by remember(entry.key) { mutableStateOf(false) }
                        val startX = dayStarts[dayIndex] + (placed[entry.key] ?: 0) * lanePx
                        val baseY = requireNotNull(entry.minute) * minutePx
                        val canDrag = enabled && !entry.closed
                        val height =
                            maxOf(
                                (entry.duration ?: 30).toFloat() * minutePx,
                                with(density) { cardMinimum.toPx() },
                            )
                        Card(
                            Modifier.offset {
                                    IntOffset(
                                        with(density) { 56.dp.roundToPx() } +
                                            (startX + drag.x).roundToInt(),
                                        (baseY + drag.y).roundToInt(),
                                    )
                                }
                                .width(laneWidth - 8.dp)
                                .height(with(density) { height.toDp() })
                                .zIndex(if (dragging) 1f else 0f)
                                .semantics {
                                    testTag = UiTags.planningEntry(entry.kind, entry.id)
                                    stateDescription =
                                        if (entry.closed) "Abgeschlossen" else entry.timeLabel
                                }
                                .pointerInput(
                                    entry.key,
                                    entry.data.getString("record_hash"),
                                    canDrag,
                                    minutePx,
                                    lanePx,
                                ) {
                                    if (canDrag)
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                drag = Offset.Zero
                                                dragging = true
                                            },
                                            onDragCancel = {
                                                drag = Offset.Zero
                                                dragging = false
                                            },
                                            onDragEnd = {
                                                val moved = drag.getDistance() >= touchSlop
                                                val x = startX + lanePx / 2 + drag.x
                                                val target =
                                                    days.indices.firstOrNull {
                                                        x >= dayStarts[it] &&
                                                            x < dayStarts[it] + lanes[it] * lanePx
                                                    }
                                                val minute =
                                                    PlanningBoardRules.snapMinute(
                                                        requireNotNull(entry.minute) +
                                                            drag.y / minutePx
                                                    )
                                                drag = Offset.Zero
                                                dragging = false
                                                if (
                                                    moved &&
                                                        target != null &&
                                                        (days[target] != entry.day ||
                                                            minute != entry.minute)
                                                )
                                                    onMove(entry, days[target], minute)
                                            },
                                        ) { change, amount ->
                                            change.consume()
                                            drag += amount
                                        }
                                }
                                .clickable { onOpen(entry) },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (entry.warning)
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainer
                                ),
                        ) {
                            Column(
                                Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(entry.timeLabel, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    entry.property.optString("name").ifBlank {
                                        nativeDisplayText(entry.subtitle)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.warning || weatherDetails)
                                    Text(
                                        entry.weather.optString("label"),
                                        Modifier.semantics {
                                            testTag = UiTags.planningWeather(entry.kind, entry.id)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                            }
                        }
                    }
                }
            }
        }
        val withoutTime = entries.filter { it.minute == null }
        if (withoutTime.isNotEmpty()) {
            Text("Uhrzeit offen", style = MaterialTheme.typography.titleMedium)
            for (entry in withoutTime) NativeInfoRow(
                entry.title,
                nativeDisplayDate(entry.day.toString()),
                { onOpen(entry) },
                Modifier.semantics { testTag = UiTags.planningEntry(entry.kind, entry.id) },
            )
        }
    }
}

@Composable
private fun PlanningBoardMap(
    controller: NativeAppController,
    entries: List<BoardEntry>,
    onOpen: (BoardEntry) -> Unit,
) {
    val known = entries.filter { it.point != null }
    val groups = known.groupBy { requireNotNull(it.point) }
    var selectedPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (known.isNotEmpty())
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(300.dp).semantics { testTag = UiTags.PLANNING_MAP }
            ) {
                val minLat = known.minOf { requireNotNull(it.point).first }
                val maxLat = known.maxOf { requireNotNull(it.point).first }
                val minLon = known.minOf { requireNotNull(it.point).second }
                val maxLon = known.maxOf { requireNotNull(it.point).second }
                Canvas(Modifier.fillMaxSize()) {
                    for (i in 1..3) {
                        drawLine(
                            gridColor,
                            Offset(size.width * i / 4, 0f),
                            Offset(size.width * i / 4, size.height),
                        )
                        drawLine(
                            gridColor,
                            Offset(0f, size.height * i / 4),
                            Offset(size.width, size.height * i / 4),
                        )
                    }
                }
                for ((index, location) in groups.entries.withIndex()) {
                    val point = location.key
                    val entry = location.value.first()
                    val x =
                        if (maxLon == minLon) 0.5 else (point.second - minLon) / (maxLon - minLon)
                    val y =
                        if (maxLat == minLat) 0.5
                        else 1 - (point.first - minLat) / (maxLat - minLat)
                    FilledTonalButton(
                        { selectedPoint = point },
                        Modifier.offset(
                                x = (maxWidth - 56.dp) * x.toFloat(),
                                y = (maxHeight - 56.dp) * y.toFloat(),
                            )
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                testTag = UiTags.planningPin(entry.kind, entry.id)
                                contentDescription = location.value.joinToString(" · ") { it.title }
                            },
                        contentPadding = PaddingValues(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(contentColor = primary),
                    ) {
                        Text(
                            if (location.value.size > 1) "${index + 1} · ${location.value.size}"
                            else (index + 1).toString()
                        )
                    }
                }
            }
        else Text("Mit einem Standort am Objekt erscheinen deine Einsätze auf der Karte.")
        if (selectedPoint != null)
            TextButton(
                { selectedPoint = null },
                Modifier.semantics { testTag = UiTags.PLANNING_MAP_ALL },
            ) {
                Text("Alle Einsätze anzeigen")
            }
        for (entry in entries.filter { selectedPoint == null || it.point == selectedPoint }) {
            NativeInfoRow(
                entry.title,
                entry.timeLabel + " · " + entry.address,
                { onOpen(entry) },
                Modifier.semantics { testTag = UiTags.planningEntry(entry.kind, entry.id) },
            )
            if (entry.address.isNotBlank() || entry.point != null)
                TextButton(
                    {
                        val target =
                            entry.point?.let { "${it.first},${it.second}" } ?: entry.address
                        try {
                            controller.context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("geo:0,0?q=" + Uri.encode(target)),
                                )
                            )
                        } catch (_: ActivityNotFoundException) {
                            controller.message("Keine Karten-App verfügbar.")
                        }
                    },
                    Modifier.semantics { testTag = UiTags.planningRoute(entry.kind, entry.id) },
                ) {
                    Text("Route")
                }
        }
    }
}
