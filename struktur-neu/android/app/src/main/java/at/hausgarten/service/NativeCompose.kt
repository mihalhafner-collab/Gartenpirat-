package at.hausgarten.service

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** System bars follow the same system setting as the Compose palette. */
internal fun android.view.Window.nativeLightSystemBars() {
    val dark =
        decorView.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    androidx.core.view.WindowCompat.getInsetsController(this, decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}

/** Six semantic roles, shared by every native screen. */
internal val NativeActionColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val NativeActionContainer: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
internal val NativeDone: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
internal val NativeDoneContainer: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer
internal val NativeMoney: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
internal val NativeMoneyContainer: Color
    @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
internal val NativeDue: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xffea8fde) else Color(0xff9a1b8d)
internal val NativeDueContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xff3a1736) else Color(0xfffbe7f8)
internal val NativeDelete: Color
    @Composable get() = MaterialTheme.colorScheme.error
internal val NativeDeleteContainer: Color
    @Composable get() = MaterialTheme.colorScheme.errorContainer
internal val NativeNeutral: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val NativeNeutralContainer: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
internal val NativeTransparent: Color
    get() = Color.Transparent
internal val NativeAttentionContainer: Color
    @Composable get() = NativeDueContainer
internal val NativeAttentionContent: Color
    @Composable get() = NativeDue

/** One system-aware Material 3 palette for main, connection and recovery activities. */
@Composable
fun HausGartenTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme =
            if (dark)
                darkColorScheme(
                    primary = Color(0xffa9aefc),
                    onPrimary = Color(0xff161b65),
                    primaryContainer = Color(0xff272d7d),
                    onPrimaryContainer = Color(0xffe7e8fb),
                    secondary = Color(0xff7cc98a),
                    onSecondary = Color(0xff102f16),
                    secondaryContainer = Color(0xff183d22),
                    onSecondaryContainer = Color(0xffe3f2e5),
                    tertiary = Color(0xffffa564),
                    onTertiary = Color(0xff452006),
                    tertiaryContainer = Color(0xff54280d),
                    onTertiaryContainer = Color(0xfffff0e3),
                    background = Color(0xff14151d),
                    onBackground = Color(0xfff3f4f8),
                    surface = Color(0xff1b1d27),
                    onSurface = Color(0xfff3f4f8),
                    surfaceVariant = Color(0xff30323f),
                    onSurfaceVariant = Color(0xffa3a6bd),
                    surfaceContainerLowest = Color(0xff101119),
                    surfaceContainerLow = Color(0xff20222d),
                    surfaceContainer = Color(0xff252734),
                    surfaceContainerHigh = Color(0xff30323f),
                    surfaceContainerHighest = Color(0xff3b3d4b),
                    surfaceTint = Color(0xffa9aefc),
                    outline = Color(0xffa3a6bd),
                    outlineVariant = Color(0xff424557),
                    error = Color(0xffff8a7a),
                    onError = Color(0xff4a110b),
                    errorContainer = Color(0xff571f18),
                    onErrorContainer = Color(0xfffdecea),
                    inverseSurface = Color(0xfff3f4f8),
                    inverseOnSurface = Color(0xff1b1d27),
                    inversePrimary = Color(0xff3138a0),
                )
            else
                lightColorScheme(
                    primary = Color(0xff3138a0),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xffe7e8fb),
                    onPrimaryContainer = Color(0xff3138a0),
                    secondary = Color(0xff2c7738),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xffe3f2e5),
                    onSecondaryContainer = Color(0xff2c7738),
                    tertiary = Color(0xffb4470b),
                    onTertiary = Color.White,
                    tertiaryContainer = Color(0xfffff0e3),
                    onTertiaryContainer = Color(0xffb4470b),
                    background = Color(0xfff3f4f8),
                    onBackground = Color(0xff20222e),
                    surface = Color.White,
                    onSurface = Color(0xff20222e),
                    surfaceVariant = Color(0xfff3f4f8),
                    onSurfaceVariant = Color(0xff5d6178),
                    surfaceContainerLowest = Color.White,
                    surfaceContainerLow = Color(0xfff3f4f8),
                    surfaceContainer = Color(0xfff3f4f8),
                    surfaceContainerHigh = Color(0xffe9eaf1),
                    surfaceContainerHighest = Color(0xffdfe1eb),
                    surfaceTint = Color(0xff3138a0),
                    outline = Color(0xff5d6178),
                    outlineVariant = Color(0xffd4d6e2),
                    error = Color(0xffb42318),
                    onError = Color.White,
                    errorContainer = Color(0xfffdecea),
                    onErrorContainer = Color(0xffb42318),
                    inverseSurface = Color(0xff20222e),
                    inverseOnSurface = Color(0xfff3f4f8),
                    inversePrimary = Color(0xffa9aefc),
                ),
        typography =
            Typography().let { base ->
                base.copy(
                    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    headlineSmall =
                        base.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    titleLarge =
                        base.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    titleMedium =
                        base.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    titleSmall =
                        base.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(10.dp),
                extraLarge = RoundedCornerShape(16.dp),
            ),
        content = {
            // Test tags become accessibility resource ids: tests find controls by id, not text.
            Box(Modifier.semantics { testTagsAsResourceId = true }) { content() }
        },
    )
}

/** Wrap actual actions instead of shrinking labels at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeActions(content: @Composable FlowRowScope.() -> Unit) {
    Surface(tonalElevation = 0.dp) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/** A short filter action cannot squeeze the result count into one-character lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeSearchSummary(count: String, onFilters: (() -> Unit)? = null) {
    val compact = LocalDensity.current.fontScale > 1.3f
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            if (compact) count.substringBefore(" ·") else count,
            Modifier.padding(vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        if (onFilters != null)
            NativeTextButton(
                modifier = Modifier.semantics { testTag = UiTags.FILTER },
                onClick = {
                    keyboard?.hide()
                    focus.clearFocus()
                    onFilters()
                },
            ) {
                Text("Filter")
            }
    }
}

/** Speech input for search fields; the recognized text replaces the query. */
@Composable
internal fun NativeVoiceButton(onClick: () -> Unit) {
    IconButton(
        onClick,
        Modifier.semantics {
            contentDescription = "Spracheingabe"
            testTag = UiTags.VOICE
        },
    ) {
        NativeMicIcon()
    }
}

/**
 * Design: every billing entry shows its way fertig → abgerechnet → bezahlt; an open payment is
 * amber.
 */
@Composable
internal fun NativeProgressSteps(stage: Int, paymentOpen: Boolean = false) {
    val labels = listOf("fertig", "abgerechnet", "bezahlt")
    Row(
        Modifier.fillMaxWidth().semantics {
            contentDescription =
                "Stand: " +
                    labels[(stage - 1).coerceIn(0, 2)] +
                    if (paymentOpen) ", Zahlung offen" else ""
            testTag = UiTags.PROGRESS
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (index in 0 until 3) {
            val color =
                when {
                    index < stage -> NativeDone
                    index == 2 && paymentOpen -> NativeDue
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            Surface(
                Modifier.weight(1f).height(6.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = color,
            ) {}
        }
    }
}

/** Delete symbol for the brick-red delete action. */
@Composable
internal fun NativeTrashIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val unit = size.minDimension / 24f
        drawLine(color, Offset(4 * unit, 6 * unit), Offset(20 * unit, 6 * unit), 2 * unit)
        drawLine(color, Offset(9 * unit, 6 * unit), Offset(10 * unit, 3 * unit), 2 * unit)
        drawLine(color, Offset(10 * unit, 3 * unit), Offset(14 * unit, 3 * unit), 2 * unit)
        drawLine(color, Offset(14 * unit, 3 * unit), Offset(15 * unit, 6 * unit), 2 * unit)
        drawRoundRect(
            color,
            Offset(6 * unit, 8 * unit),
            Size(12 * unit, 13 * unit),
            androidx.compose.ui.geometry.CornerRadius(2 * unit, 2 * unit),
            style = Stroke(2 * unit),
        )
    }
}

/** Fixed touch target leaves the full page title room at large font sizes. */
@Composable
internal fun NativeBackButton(onBack: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(
        onBack,
        Modifier.semantics {
            contentDescription = "Zurück"
            testTag = UiTags.BACK
        },
    ) {
        Canvas(Modifier.size(24.dp)) {
            val unit = size.minDimension / 24f
            drawLine(color, Offset(20 * unit, 12 * unit), Offset(4 * unit, 12 * unit), 2 * unit)
            drawLine(color, Offset(4 * unit, 12 * unit), Offset(11 * unit, 5 * unit), 2 * unit)
            drawLine(color, Offset(4 * unit, 12 * unit), Offset(11 * unit, 19 * unit), 2 * unit)
        }
    }
}

@Composable
internal fun NativeMicIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        val unit = size.minDimension / 24f
        drawRoundRect(
            color,
            Offset(9 * unit, 3 * unit),
            Size(6 * unit, 11 * unit),
            androidx.compose.ui.geometry.CornerRadius(3 * unit, 3 * unit),
        )
        drawArc(
            color,
            0f,
            180f,
            false,
            Offset(5 * unit, 6 * unit),
            Size(14 * unit, 12 * unit),
            style = Stroke(2 * unit),
        )
        drawLine(color, Offset(12 * unit, 18 * unit), Offset(12 * unit, 21 * unit), 2 * unit)
    }
}

/** Existing stroke symbols avoid adding an icon dependency. */
@Composable
internal fun NativeMoreIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        for (fraction in listOf(.25f, .5f, .75f)) drawCircle(
            color,
            size.minDimension / 14f,
            Offset(size.width / 2, size.height * fraction),
        )
    }
}

@Composable
internal fun NativeCloseIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val inset = size.minDimension * .2f
        drawLine(
            color,
            Offset(inset, inset),
            Offset(size.width - inset, size.height - inset),
            2.dp.toPx(),
        )
        drawLine(
            color,
            Offset(size.width - inset, inset),
            Offset(inset, size.height - inset),
            2.dp.toPx(),
        )
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

/** The same status semantics are used in every feature. */
internal enum class NativeStatusTone {
    Neutral,
    Running,
    Confirmed,
    Attention,
    Money,
    Due,
    Failed,
}

@Composable
internal fun NativeStatus(label: String, tone: NativeStatusTone = NativeStatusTone.Neutral) {
    if (label.isBlank()) return
    val labor = label == "Labor clandestino"
    val (background, foreground) =
        when (if (labor) NativeStatusTone.Neutral else tone) {
            NativeStatusTone.Running -> NativeActionContainer to NativeActionColor
            NativeStatusTone.Confirmed -> NativeDoneContainer to NativeDone
            NativeStatusTone.Attention,
            NativeStatusTone.Due -> NativeDueContainer to NativeDue
            NativeStatusTone.Money -> NativeMoneyContainer to NativeMoney
            NativeStatusTone.Failed -> NativeDeleteContainer to NativeDelete
            NativeStatusTone.Neutral -> NativeNeutralContainer to NativeNeutral
        }
    val borderColor = NativeNeutral
    val border =
        if (labor)
            Modifier.drawWithContent {
                drawContent()
                drawRoundRect(
                    borderColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style =
                        Stroke(
                            1.dp.toPx(),
                            pathEffect =
                                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                        ),
                )
            }
        else Modifier
    Surface(
        modifier = border,
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** The Material button owns its tag and click action; layout reserves the full touch target. */
@Composable
internal fun NativeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 48.dp) {
        Button(
            onClick,
            modifier
                .minimumInteractiveComponentSize()
                .wrapContentWidth(Alignment.End)
                .then(
                    if (LocalDensity.current.fontScale > 1.3f) Modifier.heightIn(min = 30.dp)
                    else Modifier.requiredHeight(30.dp)
                ),
            enabled,
            shape,
            colors,
            elevation,
            border,
            contentPadding,
            interactionSource,
            content,
        )
    }
}

@Composable
internal fun NativeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 48.dp) {
        OutlinedButton(
            onClick,
            modifier
                .minimumInteractiveComponentSize()
                .wrapContentWidth(Alignment.End)
                .then(
                    if (LocalDensity.current.fontScale > 1.3f) Modifier.heightIn(min = 30.dp)
                    else Modifier.requiredHeight(30.dp)
                ),
            enabled,
            shape,
            colors,
            elevation,
            border,
            contentPadding,
            interactionSource,
            content,
        )
    }
}

@Composable
internal fun NativeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 48.dp) {
        TextButton(
            onClick,
            modifier
                .minimumInteractiveComponentSize()
                .wrapContentWidth(Alignment.End)
                .then(
                    if (LocalDensity.current.fontScale > 1.3f) Modifier.heightIn(min = 26.dp)
                    else Modifier.requiredHeight(26.dp)
                ),
            enabled,
            shape,
            colors,
            elevation,
            border,
            contentPadding,
            interactionSource,
            content,
        )
    }
}

@Composable
internal fun NativeFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors =
        ButtonDefaults.filledTonalButtonColors(
            containerColor = NativeActionContainer,
            contentColor = NativeActionColor,
        ),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    NativeButton(
        onClick,
        modifier,
        enabled,
        shape,
        colors,
        elevation,
        border,
        contentPadding,
        interactionSource,
        content,
    )
}

/** Secondary left, primary right; large text wraps without hiding actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeActionBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    enabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    destructive: Boolean = false,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (secondaryLabel != null && onSecondary != null)
                    NativeOutlinedButton(
                        onSecondary,
                        Modifier.semantics { testTag = UiTags.SECONDARY },
                        enabled = secondaryEnabled,
                    ) {
                        Text(secondaryLabel)
                    }
                NativeButton(
                    onPrimary,
                    Modifier.semantics { testTag = UiTags.PRIMARY },
                    enabled = enabled,
                    colors =
                        if (destructive)
                            ButtonDefaults.buttonColors(
                                containerColor = NativeDelete,
                                contentColor = MaterialTheme.colorScheme.onError,
                            )
                        else ButtonDefaults.buttonColors(),
                ) {
                    Text(primaryLabel)
                }
            }
        }
    }
}

/** Compact rows share their surrounding group's surface and one divider. */
@Suppress("ModifierParameter")
@Composable
internal fun NativeInfoRow(
    title: String,
    detail: String = "",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val keepValues = Regex("[0-9].*(?:€|[0-9]:[0-9]| Std| Min| h\\b)")
    Column(
        modifier
            .fillMaxWidth()
            .background(
                if (isSelected) NativeActionContainer else MaterialTheme.colorScheme.surface
            )
            .then(
                if (onClick != null && onLongClick != null)
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = "Auswählen",
                    )
                else if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 42.dp)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (keepValues.containsMatchIn(title)) Int.MAX_VALUE else 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (detail.isNotBlank())
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = NativeNeutral,
                        maxLines = if (keepValues.containsMatchIn(detail)) Int.MAX_VALUE else 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
            }
            trailing?.invoke()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A titled group of compact rows, like a section of a delivery note. */
@Composable
internal fun NativeGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Text(
                title,
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

/**
 * Two-letter avatar; its theme container is fixed per name, so a customer is recognised at a
 * glance.
 */
@Composable
internal fun NativeInitials(name: String, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val words =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    val initials =
        (if (words.size > 1) "${words[0].first()}${words[1].first()}"
            else words.firstOrNull()?.take(2).orEmpty())
            .uppercase()
            .ifEmpty { "?" }
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
        )[Math.floorMod(name.trim().lowercase().hashCode(), 3)]
    Box(
        Modifier.size(size).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = content,
            style =
                if (size > 48.dp) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.titleSmall,
        )
    }
}

/** One tappable label/value row; an empty value invites to add it, no value marks an action. */
@Composable
internal fun NativeKeyValue(
    label: String,
    value: String?,
    money: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 42.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (value != null)
            Text(
                value.ifBlank { "Ergänzen" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    when {
                        value.isBlank() -> MaterialTheme.colorScheme.primary
                        money -> NativeMoney
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
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
        fun line(x1: Int, y1: Int, x2: Int, y2: Int) =
            drawLine(color, p(x1, y1), p(x2, y2), 1.8f * unit)
        when (section) {
            "today" -> {
                drawPath(
                    Path().apply {
                        moveTo(3 * unit, 11 * unit)
                        lineTo(12 * unit, 3 * unit)
                        lineTo(21 * unit, 11 * unit)
                    },
                    color,
                    style = Stroke(1.8f * unit),
                )
                line(6, 9, 6, 21)
                line(6, 21, 18, 21)
                line(18, 21, 18, 9)
                line(10, 21, 10, 14)
                line(10, 14, 14, 14)
                line(14, 14, 14, 21)
            }
            "worklogs" -> {
                drawRoundRect(
                    color,
                    p(4, 7),
                    Size(16 * unit, 14 * unit),
                    androidx.compose.ui.geometry.CornerRadius(2 * unit, 2 * unit),
                    style = Stroke(1.8f * unit),
                )
                line(8, 7, 8, 4)
                line(8, 4, 16, 4)
                line(16, 4, 16, 7)
                line(4, 12, 20, 12)
            }
            "planning" -> {
                drawRoundRect(
                    color,
                    p(3, 5),
                    Size(18 * unit, 16 * unit),
                    androidx.compose.ui.geometry.CornerRadius(2 * unit, 2 * unit),
                    style = Stroke(1.8f * unit),
                )
                line(3, 10, 21, 10)
                line(8, 3, 8, 7)
                line(16, 3, 16, 7)
                line(7, 14, 10, 14)
                line(13, 14, 17, 14)
                line(7, 17, 10, 17)
            }
            "accounting" -> {
                drawCircle(color, 9 * unit, p(12, 12), style = Stroke(1.8f * unit))
                drawArc(
                    color,
                    45f,
                    270f,
                    false,
                    p(8, 7),
                    Size(9 * unit, 10 * unit),
                    style = Stroke(1.8f * unit),
                )
                line(6, 11, 13, 11)
                line(6, 14, 13, 14)
            }
            "customers" -> {
                drawCircle(color, 3 * unit, p(9, 7), style = Stroke(1.8f * unit))
                drawArc(
                    color,
                    180f,
                    180f,
                    false,
                    p(2, 12),
                    Size(14 * unit, 14 * unit),
                    style = Stroke(1.8f * unit),
                )
                drawArc(
                    color,
                    -90f,
                    180f,
                    false,
                    p(15, 4),
                    Size(6 * unit, 6 * unit),
                    style = Stroke(1.8f * unit),
                )
                line(18, 13, 21, 18)
            }
            else -> {
                drawRoundRect(
                    color,
                    p(5, 3),
                    Size(14 * unit, 18 * unit),
                    androidx.compose.ui.geometry.CornerRadius(2 * unit, 2 * unit),
                    style = Stroke(1.8f * unit),
                )
                drawRect(color, p(8, 6), Size(8 * unit, 4 * unit), style = Stroke(1.4f * unit))
                for (x in listOf(8, 12, 16)) for (y in listOf(14, 18)) drawCircle(
                    color,
                    0.9f * unit,
                    p(x, y),
                )
            }
        }
    }
}

internal fun nativeDisplayDate(value: String): String {
    if (value.isBlank()) return value
    return try {
        if (value.length == 10)
            LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        else
            OffsetDateTime.parse(value)
                .atZoneSameInstant(ZoneId.of("Europe/Vienna"))
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
    } catch (_: Exception) {
        value
    }
}

internal fun nativeDisplayText(value: String): String {
    val dated =
        Regex("\\b\\d{4}-\\d{2}-\\d{2}(?:T[0-9:.]+(?:Z|[+-][0-9:]+))?").replace(value) {
            nativeDisplayDate(it.value)
        }
    return Regex("(\\d+)\\.(\\d+)(?= (?:h|min)\\b)").replace(dated) {
        it.groupValues[1] + "," + it.groupValues[2]
    }
}

internal fun nativeNumber(value: Any?): String =
    value
        ?.toString()
        ?.toBigDecimalOrNull()
        ?.stripTrailingZeros()
        ?.toPlainString()
        ?.replace('.', ',') ?: value?.toString().orEmpty()

/** Connection and recovery screens keep their own back guards and transactions. */
@OptIn(ExperimentalLayoutApi::class)
fun ComponentActivity.nativeContent(
    title: String,
    showBack: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    window.nativeLightSystemBars()
    setContent {
        HausGartenTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().imePadding()) {
                    if (!WindowInsets.isImeVisible) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showBack)
                                NativeBackButton { onBackPressedDispatcher.onBackPressed() }
                            Text(
                                title,
                                Modifier.weight(1f).padding(8.dp),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        HorizontalDivider()
                    }
                    Column(
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                    actions?.let { NativeActions { it() } }
                }
            }
        }
    }
}
