package id.teladanbarat.smartmeta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SignalAmber,
    onPrimary = OnAmber,
    primaryContainer = SignalAmber.copy(alpha = 0.22f),
    onPrimaryContainer = SignalAmber,
    secondary = MelatiSage,
    onSecondary = OnSage,
    secondaryContainer = MelatiSage.copy(alpha = 0.2f),
    onSecondaryContainer = MelatiSage,
    tertiary = BestariDusty,
    onTertiary = OnDusty,
    tertiaryContainer = BestariDusty.copy(alpha = 0.2f),
    onTertiaryContainer = BestariDusty,
    background = TealDeep,
    onBackground = IvoryText,
    surface = TealPanel,
    onSurface = IvoryText,
    surfaceVariant = TealPanel,
    onSurfaceVariant = IvoryText.copy(alpha = 0.7f),
    outline = TealLine,
    error = DangerCoral,
    onError = Color.White,
    errorContainer = DangerCoral.copy(alpha = 0.2f),
    onErrorContainer = DangerCoral
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFB4791F),          // amber digelapkan supaya kontras cukup di atas terang
    onPrimary = Color.White,
    primaryContainer = SignalAmber.copy(alpha = 0.18f),
    onPrimaryContainer = Color(0xFF6B4712),
    secondary = Color(0xFF3F7A54),
    onSecondary = Color.White,
    secondaryContainer = MelatiSage.copy(alpha = 0.22f),
    onSecondaryContainer = Color(0xFF1F4A2C),
    tertiary = Color(0xFF3B6E96),
    onTertiary = Color.White,
    tertiaryContainer = BestariDusty.copy(alpha = 0.22f),
    onTertiaryContainer = Color(0xFF1B3A50),
    background = CreamBackground,
    onBackground = InkText,
    surface = CreamSurface,
    onSurface = InkText,
    surfaceVariant = Color(0xFFF1EDE2),
    onSurfaceVariant = InkText.copy(alpha = 0.65f),
    outline = LineLight,
    error = DangerCoral,
    onError = Color.White,
    errorContainer = DangerCoral.copy(alpha = 0.15f),
    onErrorContainer = Color(0xFF6E241E)
)

/**
 * Theme SMART META. Terang/gelap mengikuti pilihan manual user lewat
 * [ThemeController] (tombol toggle ada di setiap TopAppBar), default-nya
 * ikut pengaturan sistem HP kalau user belum pernah memilih.
 *
 * Sengaja TIDAK memakai dynamic color (Material You) supaya identitas warna
 * SMART META (teal + amber + sage + dusty blue) konsisten di semua HP,
 * senada dengan dashboard admin — bukan berubah-ubah ikut wallpaper user.
 */
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val mode by ThemeController.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
