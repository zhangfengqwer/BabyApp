package family.babyhome.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val WarmLightColors = lightColorScheme(
    primary = Color(0xFF776000),
    primaryContainer = Color(0xFFFFDF63),
    secondary = Color(0xFF6D7D61),
    surface = Color(0xFFFFFBF7),
    background = Color(0xFFFFFBF7),
)

private val WarmDarkColors = darkColorScheme(
    primary = Color(0xFFD7B9A8),
    secondary = Color(0xFFB6C7AA),
)

@Composable
fun BabyHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> WarmDarkColors
        else -> WarmLightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
