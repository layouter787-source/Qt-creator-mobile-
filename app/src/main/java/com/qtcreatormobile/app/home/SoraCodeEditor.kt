package com.qtcreatormobile.app.home

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme

/** TextMate grammars power incremental highlighting; Sora supplies mobile editing behavior. */
object SoraTextMate {
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(appContext.assets))
        val themePath = "textmate/themes/dark_vs.json"
        val themeInput = FileProviderRegistry.getInstance().tryGetInputStream(themePath)
            ?: error("Editor theme asset is missing: $themePath")
        ThemeRegistry.getInstance().loadTheme(
            ThemeModel(IThemeSource.fromInputStream(themeInput, themePath, null), "dark_vs").apply { setDark(true) }
        )
        ThemeRegistry.getInstance().setTheme("dark_vs")
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        initialized = true
    }
}

@Composable
fun SoraCodeEditor(
    fileName: String,
    initialText: String,
    modifier: Modifier = Modifier,
    onTextChanged: (String) -> Unit,
    onEditorReady: (CodeEditor) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext -> createEditor(viewContext, fileName, initialText, onTextChanged).also(onEditorReady) },
        update = { editor ->
            if (editor.text.toString() != initialText) editor.setText(initialText)
            onEditorReady(editor)
        },
        onRelease = { it.release() },
    )
}

private fun createEditor(context: Context, fileName: String, text: String, onTextChanged: (String) -> Unit): CodeEditor {
    SoraTextMate.initialize(context)
    val editor = CodeEditor(context)
    editor.typefaceText = Typeface.MONOSPACE
    editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    editor.setText(text)
    val languageName = when (fileName.substringAfterLast('.', "").lowercase()) {
        "c", "h" -> "c"
        "cc", "cpp", "cxx", "hpp", "hh", "hxx" -> "cpp"
        "java" -> "java"
        "kt", "kts" -> "kotlin"
        "xml" -> "xml"
        "json" -> "json"
        else -> null
    }
    val scopeName = when (languageName) {
        "c", "cpp" -> "source.cpp"
        "java" -> "source.java"
        "kotlin" -> "source.kotlin"
        "xml" -> "text.xml"
        "json" -> "source.json"
        else -> null
    }
    if (scopeName != null) editor.setEditorLanguage(TextMateLanguage.create(scopeName, true))
    editor.subscribeAlways(ContentChangeEvent::class.java) { _ ->
        onTextChanged(editor.text.toString())
    }
    return editor
}
