package at.hausgarten.service

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp

internal data class NativeDetailAction(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val run: () -> Unit,
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun NativeDetailHeading(title: String, actions: List<NativeDetailAction> = emptyList()) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        if (actions.isNotEmpty())
            Box {
                IconButton(
                    { menu = true },
                    Modifier.semantics {
                        contentDescription = "Weitere Aktionen"
                        testTag = UiTags.DETAIL_MENU
                    },
                ) {
                    Text("⋮", style = MaterialTheme.typography.headlineSmall)
                }
                DropdownMenu(
                    menu,
                    { menu = false },
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                ) {
                    for (action in actions) DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            menu = false
                            action.run()
                        },
                        enabled = action.enabled,
                        modifier = Modifier.semantics { testTag = UiTags.detailAction(action.id) },
                    )
                }
            }
    }
}

@Composable
internal fun NativeAddDetails(missing: List<String>, enabled: Boolean = true, onEdit: () -> Unit) {
    if (missing.isNotEmpty())
        TextButton(onEdit, Modifier.semantics { testTag = UiTags.DETAIL_ADD }, enabled = enabled) {
            Text("+ ergänzen")
        }
}

@Composable
internal fun NativeDetailDelete(enabled: Boolean = true, onDelete: () -> Unit) {
    HorizontalDivider(Modifier.padding(top = 16.dp))
    TextButton(
        onDelete,
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = UiTags.DETAIL_DELETE },
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        NativeTrashIcon(
            if (enabled) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text("Löschen")
    }
}

internal fun nativeDetailStatus(value: String, kind: String = "", recordKind: String = ""): String =
    when (value) {
        "draft" -> "Entwurf"
        "prepared" -> "Vorbereitet"
        "inspection_planned" -> "Besichtigung geplant"
        "offered" -> "Angebot erstellt"
        "archived" -> "Archiviert"
        "planned" -> "Geplant"
        "tentative" -> "Vorgemerkt"
        "confirmed" -> "Bestätigt"
        "completed" -> "Erledigt"
        "cancelled" ->
            if (kind == "invoice" || kind == "settlement" && recordKind == "invoice") "Storniert"
            else if (kind == "settlement") "Aufgehoben" else "Abgesagt"
        "superseded" -> "Ersetzt"
        "partial" -> "Teilweise erledigt"
        "active" -> "Aktiv"
        "inactive" -> "Inaktiv"
        "new" -> "Neu"
        "accepted" -> "Angenommen"
        "issued" -> "Ausgestellt"
        "sent" -> "Versendet"
        "paid" -> "Bezahlt"
        "abandoned" -> "Aufgehoben"
        "declined",
        "rejected" -> "Abgelehnt"
        "expired" -> "Abgelaufen"
        "open" -> "Offen"
        "closed" -> "Abgeschlossen"
        "" -> ""
        "in_progress" -> "In Arbeit"
        else ->
            if (
                value in
                    setOf(
                        "Entwurf",
                        "Offen",
                        "Bezahlt",
                        "Ausgestellt",
                        "Storniert",
                        "Erledigt",
                        "Geplant",
                        "Archiviert",
                        "Abgerechnet",
                        "In Arbeit",
                        "Versendet",
                        "Angenommen",
                    )
            )
                value
            else "Stand prüfen"
    }
