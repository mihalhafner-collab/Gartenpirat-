package at.hausgarten.service

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** All light app windows, including full-screen selection and connection views. */
internal fun android.view.Window.nativeLightSystemBars() {
    androidx.core.view.WindowCompat.getInsetsController(this, decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}

/** One Material 3 palette for main, connection and recovery activities. */
@Composable
fun HausGartenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xff17634f), onPrimary = Color.White,
            primaryContainer = Color(0xffe2f0e8), onPrimaryContainer = Color(0xff12543f),
            secondary = Color(0xff52645d), onSecondary = Color.White,
            secondaryContainer = Color(0xffe2f0e8), onSecondaryContainer = Color(0xff12543f),
            tertiary = Color(0xff52645d), onTertiary = Color.White,
            tertiaryContainer = Color(0xffeaf0ec), onTertiaryContainer = Color(0xff1d302a),
            background = Color(0xfff4f7f4), onBackground = Color(0xff1d302a),
            surface = Color.White, onSurface = Color(0xff1d302a),
            surfaceVariant = Color(0xffeaf0ec), onSurfaceVariant = Color(0xff52645d),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xfff4f7f4),
            surfaceContainer = Color(0xfff4f7f4), surfaceContainerHigh = Color(0xffeaf0ec),
            surfaceContainerHighest = Color(0xffe2ebe5), surfaceTint = Color(0xff17634f),
            outline = Color(0xff77877e), outlineVariant = Color(0xffdce4de),
            error = Color(0xff963d36), onError = Color.White,
            errorContainer = Color(0xfff7e9e7), onErrorContainer = Color(0xff6d2420),
            inverseSurface = Color(0xff1d302a), inverseOnSurface = Color(0xfff4f7f4), inversePrimary = Color(0xffa8d9c0)
        ),
        typography = Typography().let { base -> base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
            bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        ) },
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content
    )
}

/** Wrap actual actions instead of shrinking labels at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeActions(content: @Composable FlowRowScope.() -> Unit) {
    Surface(tonalElevation = 0.dp) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/** A short filter action cannot squeeze the result count into one-character lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeSearchSummary(count: String, onFilters: (() -> Unit)? = null) {
    val compact = LocalDensity.current.fontScale > 1.3f
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(if (compact) count.substringBefore(" ·") else count, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge)
        if (onFilters != null) TextButton({ keyboard?.hide(); focus.clearFocus(); onFilters() }) { Text("Filter") }
    }
}

/** Fixed touch target leaves the full page title room at large font sizes. */
@Composable
internal fun NativeBackButton(onBack: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(onBack, Modifier.semantics { contentDescription = "‹ Zurück" }) {
        Canvas(Modifier.size(24.dp)) {
            val unit = size.minDimension / 24f
            drawLine(color, Offset(20*unit,12*unit), Offset(4*unit,12*unit), 2*unit)
            drawLine(color, Offset(4*unit,12*unit), Offset(11*unit,5*unit), 2*unit)
            drawLine(color, Offset(4*unit,12*unit), Offset(11*unit,19*unit), 2*unit)
        }
    }
}

/** Amber for money amounts; dark enough for text on white. */
internal val NativeMoney = Color(0xff8a5a12)

/** Attention colors shared by status chips and hint cards. */
internal val NativeAttentionContainer = Color(0xfffff1d0)
internal val NativeAttentionContent = Color(0xff79510b)

@Composable
internal fun NativeMicIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        val unit = size.minDimension / 24f
        drawRoundRect(color, Offset(9 * unit, 3 * unit), Size(6 * unit, 11 * unit), androidx.compose.ui.geometry.CornerRadius(3 * unit, 3 * unit))
        drawArc(color, 0f, 180f, false, Offset(5 * unit, 6 * unit), Size(14 * unit, 12 * unit), style = Stroke(2 * unit))
        drawLine(color, Offset(12 * unit, 18 * unit), Offset(12 * unit, 21 * unit), 2 * unit)
    }
}

/** Opens the search over all local records. */
@Composable
internal fun NativeSearchButton(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(onClick, Modifier.semantics { contentDescription = "Alles durchsuchen" }) {
        Canvas(Modifier.size(24.dp)) {
            val unit = size.minDimension / 24f
            drawCircle(color, 6.5f * unit, Offset(10 * unit, 10 * unit), style = Stroke(2 * unit))
            drawLine(color, Offset(15 * unit, 15 * unit), Offset(20 * unit, 20 * unit), 2 * unit)
        }
    }
}

/** The same explicit status semantics are used in every feature. */
internal enum class NativeStatusTone { Neutral, Running, Confirmed, Attention, Failed }

@Composable
internal fun NativeStatus(label: String, tone: NativeStatusTone = NativeStatusTone.Neutral) {
    if (label.isBlank()) return
    val (background, foreground, symbol) = when (tone) {
        NativeStatusTone.Running -> Triple(Color(0xffe2f0e8), Color(0xff12543f), "●")
        NativeStatusTone.Confirmed -> Triple(Color(0xffe2f0e8), Color(0xff12543f), "✓")
        NativeStatusTone.Attention -> Triple(NativeAttentionContainer, NativeAttentionContent, "!")
        NativeStatusTone.Failed -> Triple(Color(0xfff7e9e7), Color(0xff963d36), "×")
        NativeStatusTone.Neutral -> Triple(Color(0xffeaf0ec), Color(0xff52645d), "·")
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(50)) {
        Text("$symbol $label", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge)
    }
}

/** Secondary left, primary right; large text may stack without hiding actions. */
@Composable
internal fun NativeActionBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    enabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    destructive: Boolean = false
) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val primaryColors = if (destructive) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) else ButtonDefaults.buttonColors()
            if (stacked) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (secondaryLabel != null && onSecondary != null) {
                        OutlinedButton(onSecondary, Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            enabled = secondaryEnabled, shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text(secondaryLabel) }
                    }
                    Button(onPrimary, Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        enabled = enabled, colors = primaryColors, shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text(primaryLabel) }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (secondaryLabel != null && onSecondary != null) {
                        OutlinedButton(onSecondary, Modifier.weight(1f).heightIn(min = 56.dp),
                            enabled = secondaryEnabled, shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text(secondaryLabel) }
                    }
                    Button(onPrimary, Modifier.weight(if (secondaryLabel == null) 1f else 1.25f)
                        .heightIn(min = 56.dp), enabled = enabled, colors = primaryColors, shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(primaryLabel)
                    }
                }
            }
        }
    }
}

/** White outlined cards match the concept while retaining the existing row actions. */
@Composable
internal fun NativeInfoRow(
    title: String,
    detail: String = "",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false
) {
    Surface(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large, color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().then(
            if (onClick != null && onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Auswählen")
            else if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Text("›", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A titled group of compact rows, like a section of a delivery note. */
@Composable
internal fun NativeGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Text(title.uppercase(), Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** One tappable label/value row; an empty value invites to add it, no value marks an action. */
@Composable
internal fun NativeKeyValue(label: String, value: String?, money: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (value != null) Text(value.ifBlank { "+ ergänzen" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = when { value.isBlank() -> MaterialTheme.colorScheme.primary; money -> NativeMoney; else -> MaterialTheme.colorScheme.onSurface })
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Four consistent stroke icons; their adjacent navigation labels carry semantics. */
@Composable
internal fun NativeAreaIcon(section: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        fun p(x: Int, y: Int) = Offset(x * unit, y * unit)
        fun line(x1: Int, y1: Int, x2: Int, y2: Int) = drawLine(color, p(x1,y1), p(x2,y2), 1.8f * unit)
        when (section) {
            "today" -> {
                drawPath(Path().apply { moveTo(3*unit,11*unit); lineTo(12*unit,3*unit); lineTo(21*unit,11*unit) }, color, style = Stroke(1.8f*unit))
                line(6,9,6,21); line(6,21,18,21); line(18,21,18,9); line(10,21,10,14); line(10,14,14,14); line(14,14,14,21)
            }
            "worklogs" -> {
                drawRoundRect(color, p(4,7), Size(16*unit,14*unit), androidx.compose.ui.geometry.CornerRadius(2*unit,2*unit), style = Stroke(1.8f*unit))
                line(8,7,8,4); line(8,4,16,4); line(16,4,16,7); line(4,12,20,12)
            }
            "planning" -> {
                drawRoundRect(color, p(3,5), Size(18*unit,16*unit), androidx.compose.ui.geometry.CornerRadius(2*unit,2*unit), style = Stroke(1.8f*unit))
                line(3,10,21,10); line(8,3,8,7); line(16,3,16,7); line(7,14,10,14); line(13,14,17,14); line(7,17,10,17)
            }
            "accounting" -> {
                drawCircle(color, 9*unit, p(12,12), style = Stroke(1.8f*unit))
                drawArc(color, 45f, 270f, false, p(8,7), Size(9*unit,10*unit), style = Stroke(1.8f*unit))
                line(6,11,13,11); line(6,14,13,14)
            }
            "customers" -> {
                drawCircle(color, 3*unit, p(9,7), style = Stroke(1.8f*unit)); drawArc(color,180f,180f,false,p(2,12),Size(14*unit,14*unit),style=Stroke(1.8f*unit))
                drawArc(color,-90f,180f,false,p(15,4),Size(6*unit,6*unit),style=Stroke(1.8f*unit)); line(18,13,21,18)
            }
            else -> {
                drawRoundRect(color, p(5,3), Size(14*unit,18*unit), androidx.compose.ui.geometry.CornerRadius(2*unit,2*unit), style = Stroke(1.8f*unit))
                drawRect(color, p(8,6), Size(8*unit,4*unit), style = Stroke(1.4f*unit))
                for (x in listOf(8,12,16)) for (y in listOf(14,18)) drawCircle(color, 0.9f*unit, p(x,y))
            }
        }
    }
}

internal fun nativeDisplayDate(value: String): String {
    if (value.isBlank()) return value
    return try {
        if (value.length == 10) LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        else OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Europe/Vienna")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
    } catch (_: Exception) { value }
}
internal fun nativeDisplayText(value: String): String {
    val dated = Regex("\\b\\d{4}-\\d{2}-\\d{2}(?:T[0-9:.]+(?:Z|[+-][0-9:]+))?").replace(value) { nativeDisplayDate(it.value) }
    return Regex("(\\d+)\\.(\\d+)(?= (?:h|km|min)\\b)").replace(dated) { it.groupValues[1] + "," + it.groupValues[2] }
}
internal fun nativeNumber(value: Any?): String = value?.toString()?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: value?.toString().orEmpty()

/** Connection and recovery screens keep their own back guards and transactions. */
@OptIn(ExperimentalLayoutApi::class)
fun ComponentActivity.nativeContent(title: String, showBack: Boolean = true, actions: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    window.nativeLightSystemBars()
    setContent {
        HausGartenTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().imePadding()) {
                    if (!WindowInsets.isImeVisible) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (showBack) NativeBackButton { onBackPressedDispatcher.onBackPressed() }
                            Text(title, Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.titleLarge)
                        }
                        HorizontalDivider()
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    actions?.let { NativeActions { it() } }
                }
            }
        }
    }
}
