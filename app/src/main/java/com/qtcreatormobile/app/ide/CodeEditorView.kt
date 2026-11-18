package com.qtcreatormobile.app.ide

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.qtcreatormobile.app.CrashLog
import com.qtcreatormobile.app.home.SoraTextMate
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File

/** Liga a UI Compose ao CodeEditor do Sora (um editor so, reaproveitado entre as abas). */
class EditorHandle {
    var editor: CodeEditor? = null

    /** Caminho do arquivo que o editor mostra agora; null enquanto carrega outro. */
    @Volatile var loadedPath: String? = null
    var loading = false

    fun currentText(path: String): String? = if (loadedPath == path) editor?.text?.toString() else null

    fun undo() { editor?.undo() }
    fun redo() { editor?.redo() }
    fun canUndo(): Boolean = editor?.canUndo() ?: false
    fun canRedo(): Boolean = editor?.canRedo() ?: false
    fun insert(text: String) { editor?.commitText(text) }

    private fun options(ignoreCase: Boolean, regex: Boolean) = EditorSearcher.SearchOptions(ignoreCase, regex)

    fun find(query: String, ignoreCase: Boolean = true, regex: Boolean = false) {
        val e = editor ?: return
        if (query.isEmpty()) {
            e.searcher.stopSearch()
            return
        }
        try {
            e.searcher.search(query, options(ignoreCase, regex))
        } catch (_: Exception) {
            // regex invalida enquanto o usuario digita
        }
    }

    fun findNext() { editor?.searcher?.takeIf { it.hasQuery() }?.gotoNext() }
    fun findPrevious() { editor?.searcher?.takeIf { it.hasQuery() }?.gotoPrevious() }
    fun replaceCurrent(text: String) { editor?.searcher?.takeIf { it.hasQuery() }?.replaceCurrentMatch(text) }
    fun replaceAll(text: String) { editor?.searcher?.takeIf { it.hasQuery() }?.replaceAll(text) }
    fun stopFind() { editor?.searcher?.stopSearch() }
}

private fun scopeFor(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
    "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "hxx" -> "source.cpp"
    "java" -> "source.java"
    "kt", "kts" -> "source.kotlin"
    "xml" -> "text.xml"
    "json" -> "source.json"
    else -> null
}

private val CPP_WORDS = arrayOf(
    "auto", "bool", "break", "case", "catch", "char", "class", "const", "constexpr", "continue", "default", "delete",
    "do", "double", "else", "enum", "explicit", "extern", "false", "float", "for", "if", "include", "inline", "int",
    "long", "namespace", "new", "nullptr", "operator", "private", "protected", "public", "return", "short", "signed",
    "sizeof", "static", "struct", "switch", "template", "this", "throw", "true", "try", "typedef", "typename",
    "union", "unsigned", "using", "virtual", "void", "volatile", "while", "std", "string", "vector", "size_t",
    "uint8_t", "uint32_t", "int32_t", "int64_t", "printf", "malloc", "free", "memcpy", "nullptr_t", "JNIEnv",
    "jobject", "jstring", "jint", "NewStringUTF", "ANativeWindow", "android_app", "ALOGI", "__android_log_print",
    "ANDROID_LOG_INFO",
)
private val JAVA_WORDS = arrayOf(
    "abstract", "boolean", "break", "byte", "case", "catch", "char", "class", "continue", "default", "do", "double",
    "else", "enum", "extends", "false", "final", "finally", "float", "for", "if", "implements", "import",
    "instanceof", "int", "interface", "long", "native", "new", "null", "package", "private", "protected", "public",
    "return", "short", "static", "super", "switch", "this", "throw", "throws", "true", "try", "void", "while",
    "String", "System", "Override", "Bundle", "Activity", "TextView", "Context", "onCreate", "setContentView",
    "loadLibrary", "android.os.Bundle", "android.app.Activity", "android.widget.TextView",
)
private val KOTLIN_WORDS = arrayOf(
    "abstract", "class", "companion", "const", "data", "else", "enum", "external", "false", "for", "fun", "if",
    "import", "in", "init", "interface", "internal", "is", "lateinit", "null", "object", "open", "override",
    "package", "private", "protected", "public", "return", "sealed", "super", "this", "true", "try", "val", "var",
    "when", "while", "String", "Int", "Boolean", "List", "Unit", "Bundle", "Activity", "TextView", "onCreate",
    "setContentView", "System.loadLibrary", "android.os.Bundle", "android.app.Activity", "android.widget.TextView",
)

private fun applyLanguage(editor: CodeEditor, fileName: String, highlight: Boolean, onError: (String) -> Unit) {
    val scope = if (highlight) scopeFor(fileName) else null
    try {
        if (scope == null) {
            editor.setEditorLanguage(null)
            return
        }
        val language = TextMateLanguage.create(scope, true)
        val words = when (scope) {
            "source.cpp" -> CPP_WORDS
            "source.java" -> JAVA_WORDS
            "source.kotlin" -> KOTLIN_WORDS
            else -> null
        }
        if (words != null) language.setCompleterKeywords(words)
        editor.setEditorLanguage(language)
    } catch (t: Throwable) {
        CrashLog.event(editor.context, "realce falhou para $fileName: ${t.javaClass.simpleName}: ${t.message}")
        try {
            editor.setEditorLanguage(null)
        } catch (_: Throwable) {
        }
        onError("Não foi possível ativar o realce de sintaxe (${t.javaClass.simpleName}). O arquivo abriu sem cores.")
    }
}

/** Esquema escuro simples, sem TextMate (modo seguro). */
private fun plainDarkScheme(): EditorColorScheme {
    val scheme = EditorColorScheme()
    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E1E.toInt())
    scheme.setColor(EditorColorScheme.TEXT_NORMAL, 0xFFD4D4D4.toInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER, 0xFF6E7681.toInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E1E.toInt())
    scheme.setColor(EditorColorScheme.CURRENT_LINE, 0xFF282828.toInt())
    return scheme
}

private fun applyColorScheme(editor: CodeEditor, highlight: Boolean, onError: (String) -> Unit) {
    try {
        if (highlight) {
            SoraTextMate.initialize(editor.context)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        } else {
            editor.colorScheme = plainDarkScheme()
        }
    } catch (t: Throwable) {
        CrashLog.event(editor.context, "tema do editor falhou: ${t.javaClass.simpleName}: ${t.message}")
        try {
            editor.colorScheme = plainDarkScheme()
        } catch (_: Throwable) {
        }
        onError("Não foi possível carregar o tema do editor (${t.javaClass.simpleName}). Usando cores simples.")
    }
}

private fun createEditor(context: Context, handle: EditorHandle, onChanged: () -> Unit): CodeEditor {
    val editor = CodeEditor(context)
    editor.typefaceText = Typeface.MONOSPACE
    editor.subscribeAlways(ContentChangeEvent::class.java) { _ ->
        if (!handle.loading) onChanged()
    }
    handle.editor = editor
    CrashLog.event(context, "editor criado")
    return editor
}

/** O editor propriamente dito: recarrega o texto quando [revision] muda e aplica as preferencias. */
@Composable
fun CodeEditorView(
    handle: EditorHandle,
    file: File,
    text: String,
    revision: Int,
    jumpLine: Int?,
    settings: EditorSettings,
    onChanged: () -> Unit,
    onJumped: () -> Unit,
    onError: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context -> createEditor(context, handle, onChanged) },
        onRelease = {
            handle.editor = null
            handle.loadedPath = null
            try {
                it.release()
            } catch (_: Throwable) {
            }
        },
    )

    // Tema (cores): refeito quando o modo seguro liga/desliga.
    LaunchedEffect(settings.syntaxHighlight) {
        val editor = handle.editor ?: return@LaunchedEffect
        applyColorScheme(editor, settings.syntaxHighlight, onError)
    }

    // Troca de arquivo / recarga: so quando a revisao muda (nunca a cada recomposicao).
    LaunchedEffect(revision, file.absolutePath, settings.syntaxHighlight) {
        val editor = handle.editor ?: return@LaunchedEffect
        handle.loading = true
        handle.loadedPath = null
        try {
            CrashLog.event(editor.context, "abrindo ${file.name}")
            applyLanguage(editor, file.name, settings.syntaxHighlight, onError)
            editor.setText(text)
            handle.loadedPath = file.absolutePath
        } catch (t: Throwable) {
            CrashLog.event(editor.context, "falha ao abrir ${file.name}: ${t.javaClass.simpleName}: ${t.message}")
            onError("Não foi possível mostrar ${file.name}: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            handle.loading = false
        }
    }

    LaunchedEffect(settings) {
        val editor = handle.editor ?: return@LaunchedEffect
        try {
            editor.setTextSize(settings.fontSizeSp)
            editor.isWordwrap = settings.wordWrap
            editor.isLineNumberEnabled = settings.lineNumbers
            editor.tabWidth = settings.tabSize
        } catch (t: Throwable) {
            onError("Falha ao aplicar as preferências do editor: ${t.message}")
        }
    }

    LaunchedEffect(jumpLine, revision) {
        val line = jumpLine ?: return@LaunchedEffect
        val editor = handle.editor ?: return@LaunchedEffect
        try {
            val target = (line - 1).coerceIn(0, maxOf(editor.text.lineCount - 1, 0))
            editor.jumpToLine(target)
        } catch (_: Throwable) {
        }
        onJumped()
    }
}
