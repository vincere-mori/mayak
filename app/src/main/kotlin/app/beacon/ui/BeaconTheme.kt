package app.beacon.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BeaconColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF141414),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD05A),
    onPrimaryContainer = Color(0xFF151515),
    secondary = Color(0xFF396A5C),
    onSecondary = Color.White,
    background = Color(0xFFF7F7F2),
    onBackground = Color(0xFF151515),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFE7E2D4),
    onSurfaceVariant = Color(0xFF4E4A40),
    error = Color(0xFFB3261E)
)

@Composable
fun BeaconTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BeaconColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
