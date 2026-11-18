package com.qtcreatormobile.app.ide

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferencias do editor. */
data class EditorSettings(
    val fontSizeSp: Float = 14f,
    val wordWrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val tabSize: Int = 4,
    val autoSave: Boolean = true,
    /** Desligar usa um editor sem realce de sintaxe (modo seguro). */
    val syntaxHighlight: Boolean = true,
)

/** Guarda as preferencias em SharedPreferences e expoe o valor atual como fluxo. */
class IdeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ide-settings", Context.MODE_PRIVATE)

    private val _editor = MutableStateFlow(loadEditor())
    val editor: StateFlow<EditorSettings> = _editor.asStateFlow()

    private val _appearance = MutableStateFlow(loadAppearance())
    val appearance: StateFlow<AppearanceSettings> = _appearance.asStateFlow()

    private fun loadEditor(): EditorSettings = EditorSettings(
        fontSizeSp = prefs.getFloat("font_size", 14f).coerceIn(10f, 24f),
        wordWrap = prefs.getBoolean("word_wrap", false),
        lineNumbers = prefs.getBoolean("line_numbers", true),
        tabSize = prefs.getInt("tab_size", 4).coerceIn(2, 8),
        autoSave = prefs.getBoolean("auto_save", true),
        syntaxHighlight = prefs.getBoolean("syntax_highlight", true),
    )

    private fun loadAppearance(): AppearanceSettings = AppearanceSettings(
        accent = prefs.getInt("accent", DefaultAccentArgb),
        amoled = prefs.getBoolean("amoled", false),
    )

    fun update(transform: (EditorSettings) -> EditorSettings) {
        val next = transform(_editor.value)
        _editor.value = next
        prefs.edit()
            .putFloat("font_size", next.fontSizeSp)
            .putBoolean("word_wrap", next.wordWrap)
            .putBoolean("line_numbers", next.lineNumbers)
            .putInt("tab_size", next.tabSize)
            .putBoolean("auto_save", next.autoSave)
            .putBoolean("syntax_highlight", next.syntaxHighlight)
            .apply()
    }

    fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        val next = transform(_appearance.value)
        _appearance.value = next
        prefs.edit().putInt("accent", next.accent).putBoolean("amoled", next.amoled).apply()
    }
}
