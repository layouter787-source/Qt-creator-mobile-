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
)

/** Guarda as preferencias em SharedPreferences e expoe o valor atual como fluxo. */
class IdeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ide-settings", Context.MODE_PRIVATE)

    private val _editor = MutableStateFlow(load())
    val editor: StateFlow<EditorSettings> = _editor.asStateFlow()

    private fun load(): EditorSettings = EditorSettings(
        fontSizeSp = prefs.getFloat("font_size", 14f).coerceIn(10f, 24f),
        wordWrap = prefs.getBoolean("word_wrap", false),
        lineNumbers = prefs.getBoolean("line_numbers", true),
        tabSize = prefs.getInt("tab_size", 4).coerceIn(2, 8),
        autoSave = prefs.getBoolean("auto_save", true),
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
            .apply()
    }
}
