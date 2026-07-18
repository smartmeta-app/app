package id.teladanbarat.smartmeta.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Tombol satu-klik untuk geser tema: Sistem -> Terang -> Gelap -> Sistem.
 * Ikonnya berubah mengikuti mode aktif supaya user tahu mode apa yang
 * sedang jalan tanpa perlu buka menu terpisah.
 */
@Composable
fun ThemeToggleButton() {
    val mode by ThemeController.themeMode.collectAsState()
    IconButton(onClick = { ThemeController.toggle() }) {
        Icon(
            imageVector = when (mode) {
                ThemeMode.SYSTEM -> Icons.Default.Brightness4
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.DARK -> Icons.Default.DarkMode
            },
            contentDescription = when (mode) {
                ThemeMode.SYSTEM -> "Tema: ikut sistem"
                ThemeMode.LIGHT -> "Tema: terang"
                ThemeMode.DARK -> "Tema: gelap"
            }
        )
    }
}
