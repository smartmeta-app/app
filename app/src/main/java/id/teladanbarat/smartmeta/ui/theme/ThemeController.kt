package id.teladanbarat.smartmeta.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Menyimpan pilihan tema user (terang/gelap/ikuti sistem) supaya tetap
 * konsisten setiap kali app dibuka lagi — bukan cuma reset ke default tiap
 * kali app di-restart.
 */
object ThemeController {
    private const val PREFS_NAME = "smart_meta_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        _themeMode.value = runCatching { ThemeMode.valueOf(saved ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        initialized = true
    }

    /** Geser ke pilihan berikutnya: Sistem -> Terang -> Gelap -> Sistem -> ... */
    fun toggle() {
        val next = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        setMode(next)
    }

    fun setMode(mode: ThemeMode) {
        _themeMode.value = mode
        if (initialized) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
    }
}
