package at.hausgarten.service

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One Material 3 palette for main, connection and recovery activities. */
@Composable
fun HausGartenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xff1c6846), onPrimary = Color.White,
            primaryContainer = Color(0xffd3ebda), onPrimaryContainer = Color(0xff102d20),
            secondary = Color(0xff506354), onSecondary = Color.White,
            secondaryContainer = Color(0xffdce9dc), onSecondaryContainer = Color(0xff1b3325),
            tertiary = Color(0xff526246), onTertiary = Color.White,
            tertiaryContainer = Color(0xffe1e9ce), onTertiaryContainer = Color(0xff24321d),
            background = Color(0xfff6f8f4), onBackground = Color(0xff1a211c),
            surface = Color(0xfffcfdf9), onSurface = Color(0xff1a211c),
            surfaceVariant = Color(0xffe1e7dd), onSurfaceVariant = Color(0xff424c44),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xfff0f4ed),
            surfaceContainer = Color(0xffeaf0e7), surfaceContainerHigh = Color(0xffe4eae1),
            surfaceContainerHighest = Color(0xffdee5db), surfaceTint = Color(0xff1c6846),
            outline = Color(0xff707b71), outlineVariant = Color(0xffbcc7bc),
            error = Color(0xff9a2924), onError = Color.White,
            errorContainer = Color(0xffffdad5), onErrorContainer = Color(0xff410005),
            inverseSurface = Color(0xff2f3831), inverseOnSurface = Color(0xffedf3eb), inversePrimary = Color(0xff9ed5af)
        ),
        typography = Typography(),
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content
    )
}

/** Wrap actual actions instead of shrinking labels at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeActions(content: @Composable FlowRowScope.() -> Unit) {
    Surface(tonalElevation = 3.dp) {
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

/** Status remains readable without relying on color alone. */
@Composable
internal fun NativeStatus(label: String) {
    if (label.isBlank()) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
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
            "worklogs" -> { drawCircle(color, 9*unit, p(12,12), style = Stroke(1.8f*unit)); line(12,6,12,12); line(12,12,16,15) }
            "customers" -> {
                drawCircle(color, 3*unit, p(9,7), style = Stroke(1.8f*unit)); drawArc(color,180f,180f,false,p(2,12),Size(14*unit,14*unit),style=Stroke(1.8f*unit))
                drawArc(color,-90f,180f,false,p(15,4),Size(6*unit,6*unit),style=Stroke(1.8f*unit)); line(18,13,21,18)
            }
            else -> { drawRect(color,p(4,7),Size(16*unit,14*unit),style=Stroke(1.8f*unit)); line(8,7,8,3); line(8,3,16,3); line(16,3,16,7); line(4,12,20,12) }
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
internal fun nativeDisplayText(value: String): String = Regex("\\b\\d{4}-\\d{2}-\\d{2}(?:T[0-9:.]+(?:Z|[+-][0-9:]+))?").replace(value) { nativeDisplayDate(it.value) }
internal fun nativeNumber(value: Any?): String = value?.toString()?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: value?.toString().orEmpty()

/** Connection and recovery screens keep their own back guards and transactions. */
@OptIn(ExperimentalLayoutApi::class)
fun ComponentActivity.nativeContent(title: String, showBack: Boolean = true, actions: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
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
