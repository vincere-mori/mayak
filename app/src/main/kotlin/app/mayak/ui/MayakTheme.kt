package app.mayak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// палитра один в один с десктопом (MayakTheme.kt в desktop)
object MayakColors {
    val BgTop = Color(0xFF0F1535)
    val BgBot = Color(0xFF080C22)
    val Card = Color(0xFF162046)
    val Input = Color(0xFF0F1636)
    val InputHover = Color(0xFF17224C)
    val Border = Color(0xFF2A3A6E)
    val BorderSoft = Color(0xFF23305C)
    val Text = Color(0xFFE1E8FA)
    val TextDim = Color(0xFFB4C3E6)
    val Muted = Color(0xFF788CBE)
    val Accent = Color(0xFF3C6EE6)
    val AccentHover = Color(0xFF5082F5)
    val AccentLight = Color(0xFF82B4FF)
    val Success = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)
}

private val MayakScheme: ColorScheme = darkColorScheme(
    primary = MayakColors.Accent,
    onPrimary = Color.White,
    primaryContainer = MayakColors.Card,
    onPrimaryContainer = MayakColors.Text,
    secondary = MayakColors.AccentLight,
    onSecondary = Color(0xFF0B1024),
    background = MayakColors.BgBot,
    onBackground = MayakColors.Text,
    surface = MayakColors.BgBot,
    onSurface = MayakColors.Text,
    surfaceVariant = MayakColors.Input,
    onSurfaceVariant = MayakColors.Muted,
    // карточки M3 берут container-тона — держим их в navy, как на пк
    surfaceContainerLowest = MayakColors.BgBot,
    surfaceContainerLow = MayakColors.Card,
    surfaceContainer = MayakColors.Card,
    surfaceContainerHigh = MayakColors.Card,
    surfaceContainerHighest = MayakColors.Card,
    outline = MayakColors.Border,
    outlineVariant = MayakColors.BorderSoft,
    error = MayakColors.Danger,
    onError = Color.White
)

@Composable
fun MayakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MayakScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

fun Modifier.mayakBackground(): Modifier = this
    .fillMaxSize()
    .background(
        Brush.verticalGradient(
            colors = listOf(MayakColors.BgTop, MayakColors.BgBot)
        )
    )
