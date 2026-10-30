package at.hausgarten.service

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared native presentation. Business state stays in the transactional repositories. */
@Composable
fun HausGartenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xff23744e), onPrimary = Color.White,
            primaryContainer = Color(0xffedf3ee), onPrimaryContainer = Color(0xff164d38),
            background = Color(0xfff5f7f5), surface = Color.White,
            onSurface = Color(0xff17241d), error = Color(0xff8a2e25)
        ), content = content
    )
}

/** Connection and recovery screens use the same accessible Compose layout. */
fun ComponentActivity.nativeContent(title: String, content: @Composable ColumnScope.() -> Unit) {
    setContent {
        HausGartenTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().imePadding()) {
                    Text(title, Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                    HorizontalDivider()
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content
                    )
                }
            }
        }
    }
}
