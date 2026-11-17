package com.qtcreatormobile.app.ide

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtcreatormobile.projectmodel.Language
import com.qtcreatormobile.projectmodel.ProjectFiles
import com.qtcreatormobile.projectmodel.ProjectModelLoader
import com.qtcreatormobile.projectmodel.ProjectNames
import com.qtcreatormobile.projectmodel.ProjectTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

data class ProjectEntry(val name: String, val dir: File, val modified: Long, val languages: String)

sealed interface BuildPanelState {
    data object Idle : BuildPanelState
    data class Running(val stage: String, val variant: BuildVariant) : BuildPanelState
    data class Done(val outcome: BuildOutcome, val variant: BuildVariant, val autoInstall: Boolean) : BuildPanelState
}

data class IdeUiState(
    val projects: List<ProjectEntry> = emptyList(),
    val project: File? = null,
    val tree: List<ProjectFiles.Entry> = emptyList(),
    val expanded: Set<String> = emptySet(),
    val tabs: List<File> = emptyList(),
    val activeIndex: Int = -1,
    val activeText: String = "",
    /** Muda sempre que o editor precisa ser recarregado (troca de aba, arquivo alterado por fora). */
    val editorRevision: Int = 0,
    val jumpLine: Int? = null,
    val dirty: Set<String> = emptySet(),
    val build: BuildPanelState = BuildPanelState.Idle,
    val lastApk: File? = null,
    val notice: String? = null,
    val findQuery: String = "",
    val findHits: List<ProjectFiles.SearchHit> = emptyList(),
    val fileIndex: List<File> = emptyList(),
) {
    val activeFile: File? get() = tabs.getOrNull(activeIndex)
}

/** Estado e acoes da IDE: projetos, abas, arquivos, busca e build. */
class IdeViewModel(application: Application) : AndroidViewModel(application) {
    private val projectsRoot = File(application.filesDir, "projects").apply { mkdirs() }
    private val builder = ProjectBuilder(application)
    val settings = IdeSettings(application)

    private val _state = MutableStateFlow(IdeUiState(projects = scanProjects()))
    val state: StateFlow<IdeUiState> = _state.asStateFlow()

    /** Texto de cada arquivo aberto (a versao mais recente conhecida). */
    private val buffers = HashMap<String, String>()
    private var textProvider: ((String) -> String?)? = null
    private var searchJob: Job? = null

    /** Preenchido pelo gerenciador de keystore: chave usada no build Release. */
    var signingProvider: (() -> SigningSpec?)? = null

    // ---------- projetos ----------

    private fun languageLabel(language: Language): String = when (language) {
        Language.C -> "C"
        Language.CPP -> "C++"
        Language.JAVA -> "Java"
        Language.KOTLIN -> "Kotlin"
    }

    private fun scanProjects(): List<ProjectEntry> =
        (projectsRoot.listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "qtcm.project.yaml").isFile }
            .map { dir ->
                val languages = runCatching {
                    ProjectModelLoader.loadFromFile(File(dir, "qtcm.project.yaml")).languages.joinToString(" · ") { languageLabel(it) }
                }.getOrDefault("")
                ProjectEntry(dir.name, dir, dir.lastModified(), languages)
            }
            .sortedByDescending { it.modified }

    fun refreshProjects() {
        _state.update { it.copy(projects = scanProjects()) }
    }

    /** Cria o projeto e o abre. Devolve a mensagem de erro, ou null se deu certo. */
    fun createProject(templateId: String, name: String, packageName: String): String? = try {
        val dir = ProjectTemplates.create(templateId, name.trim(), packageName.trim(), projectsRoot)
        refreshProjects()
        openProject(dir)
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "Dados inválidos."
    } catch (e: Exception) {
        "Falha ao criar o projeto: ${e.message}"
    }

    fun suggestPackage(name: String): String = ProjectNames.suggestPackage(name)

    fun deleteProject(entry: ProjectEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            entry.dir.deleteRecursively()
            _state.update { it.copy(projects = scanProjects()) }
        }
    }

    fun openProject(dir: File) {
        buffers.clear()
        dir.setLastModified(System.currentTimeMillis())
        val src = File(dir, "src")
        val expanded = if (src.isDirectory) {
            src.walkTopDown().onEnter { true }.filter { it.isDirectory }.take(200).map { it.absolutePath }.toSet()
        } else {
            emptySet()
        }
        _state.update {
            it.copy(
                project = dir, expanded = expanded, tabs = emptyList(), activeIndex = -1, activeText = "", dirty = emptySet(),
                build = BuildPanelState.Idle, findQuery = "", findHits = emptyList(), fileIndex = emptyList(),
                editorRevision = it.editorRevision + 1,
            )
        }
        refreshTree()
        val first = ProjectFiles.allTextFiles(dir).firstOrNull { it.path.contains("/src/") }
        if (first != null) openFile(first)
    }

    fun closeProject() {
        saveAll()
        buffers.clear()
        _state.update {
            it.copy(project = null, tree = emptyList(), tabs = emptyList(), activeIndex = -1, activeText = "", dirty = emptySet(), projects = scanProjects())
        }
    }

    // ---------- editor ----------

    fun attachEditor(provider: ((String) -> String?)?) {
        textProvider = provider
    }

    private fun activePath(): String? = _state.value.activeFile?.absolutePath

    /** Copia o texto do editor ativo para o buffer. */
    private fun syncActive() {
        val path = activePath() ?: return
        val text = textProvider?.invoke(path) ?: return
        buffers[path] = text
    }

    fun markDirty() {
        val path = activePath() ?: return
        if (path !in _state.value.dirty) _state.update { it.copy(dirty = it.dirty + path) }
    }

    private fun select(tabs: List<File>, index: Int, jump: Int? = null) {
        val file = tabs[index]
        val text = buffers.getOrPut(file.absolutePath) { runCatching { file.readText() }.getOrDefault("") }
        _state.update {
            it.copy(tabs = tabs, activeIndex = index, activeText = text, editorRevision = it.editorRevision + 1, jumpLine = jump)
        }
    }

    fun openFile(file: File, line: Int? = null) {
        syncActive()
        if (!ProjectFiles.isEditableText(file)) {
            notify("Este arquivo não pode ser editado (binário ou maior que 2 MB).")
            return
        }
        val path = file.absolutePath
        var tabs = _state.value.tabs
        var index = tabs.indexOfFirst { it.absolutePath == path }
        if (index < 0) {
            val text = try {
                file.readText()
            } catch (e: IOException) {
                notify("Não foi possível abrir o arquivo: ${e.message}")
                return
            }
            buffers[path] = text
            tabs = tabs + file
            index = tabs.lastIndex
        }
        select(tabs, index, line)
    }

    fun selectTab(index: Int) {
        syncActive()
        val tabs = _state.value.tabs
        if (index in tabs.indices) select(tabs, index)
    }

    fun consumeJump() {
        _state.update { it.copy(jumpLine = null) }
    }

    fun closeTab(index: Int) {
        syncActive()
        val s = _state.value
        val file = s.tabs.getOrNull(index) ?: return
        val path = file.absolutePath
        if (path in s.dirty) writeBuffer(path)
        buffers.remove(path)
        val tabs = s.tabs.filterIndexed { i, _ -> i != index }
        val dirty = s.dirty - path
        if (tabs.isEmpty()) {
            _state.update { it.copy(tabs = tabs, activeIndex = -1, activeText = "", dirty = dirty, editorRevision = it.editorRevision + 1) }
            return
        }
        val newActive = when {
            index < s.activeIndex -> s.activeIndex - 1
            index == s.activeIndex -> minOf(index, tabs.lastIndex)
            else -> s.activeIndex
        }
        _state.update { it.copy(dirty = dirty) }
        select(tabs, newActive)
    }

    private fun writeBuffer(path: String) {
        val text = buffers[path] ?: return
        try {
            File(path).writeText(text)
        } catch (e: IOException) {
            notify("Não foi possível salvar ${File(path).name}: ${e.message}")
        }
    }

    fun save() {
        syncActive()
        val path = activePath() ?: return
        writeBuffer(path)
        _state.update { it.copy(dirty = it.dirty - path) }
    }

    fun saveAll() {
        syncActive()
        val dirty = _state.value.dirty
        if (dirty.isEmpty()) return
        dirty.forEach { writeBuffer(it) }
        _state.update { it.copy(dirty = emptySet()) }
    }

    /** Recarrega do disco um arquivo alterado por fora (ex.: pela IA). */
    fun reloadFromDisk(file: File) {
        val path = file.absolutePath
        val s = _state.value
        val index = s.tabs.indexOfFirst { it.absolutePath == path }
        buffers.remove(path)
        refreshTree()
        if (index < 0) return
        _state.update { it.copy(dirty = it.dirty - path) }
        if (index == s.activeIndex) select(s.tabs, index) else buffers[path] = runCatching { file.readText() }.getOrDefault("")
    }

    /** Texto atual de um arquivo (do editor, se estiver aberto; senao do disco). */
    fun currentText(file: File): String {
        syncActive()
        return buffers[file.absolutePath] ?: runCatching { file.readText() }.getOrDefault("")
    }

    // ---------- arvore e arquivos ----------

    fun refreshTree() {
        _state.update { s ->
            val root = s.project ?: return@update s
            s.copy(tree = ProjectFiles.visibleTree(root, s.expanded))
        }
    }

    fun toggleFolder(dir: File) {
        val path = dir.absolutePath
        _state.update { it.copy(expanded = if (path in it.expanded) it.expanded - path else it.expanded + path) }
        refreshTree()
    }

    private fun expand(dir: File) {
        _state.update { it.copy(expanded = it.expanded + dir.absolutePath) }
    }

    fun createFile(parent: File, name: String): String? {
        val clean = name.trim()
        if (!ProjectFiles.isValidEntryName(clean)) return "Nome inválido."
        val target = File(parent, clean)
        if (target.exists()) return "Já existe um item com esse nome."
        return try {
            parent.mkdirs()
            target.createNewFile()
            expand(parent)
            refreshTree()
            openFile(target)
            null
        } catch (e: IOException) {
            e.message ?: "Falha ao criar o arquivo."
        }
    }

    fun createFolder(parent: File, name: String): String? {
        val clean = name.trim()
        if (!ProjectFiles.isValidEntryName(clean)) return "Nome inválido."
        val target = File(parent, clean)
        if (target.exists()) return "Já existe um item com esse nome."
        if (!target.mkdirs()) return "Não foi possível criar a pasta."
        expand(parent)
        expand(target)
        refreshTree()
        return null
    }

    private fun remap(path: String, from: String, to: String): String =
        if (path == from || path.startsWith(from + File.separator)) to + path.removePrefix(from) else path

    fun rename(file: File, newName: String): String? {
        val clean = newName.trim()
        if (!ProjectFiles.isValidEntryName(clean)) return "Nome inválido."
        val target = File(file.parentFile, clean)
        if (target.exists()) return "Já existe um item com esse nome."
        syncActive()
        saveAll()
        if (!file.renameTo(target)) return "Não foi possível renomear."
        val from = file.absolutePath
        val to = target.absolutePath
        val s = _state.value
        val activePathBefore = activePath()
        val tabs = s.tabs.map { File(remap(it.absolutePath, from, to)) }
        val moved = buffers.entries.associate { remap(it.key, from, to) to it.value }
        buffers.clear()
        buffers.putAll(moved)
        val expanded = s.expanded.map { remap(it, from, to) }.toSet()
        _state.update { it.copy(tabs = tabs, expanded = expanded, dirty = emptySet()) }
        refreshTree()
        if (activePathBefore != null && s.activeIndex in tabs.indices) select(tabs, s.activeIndex)
        return null
    }

    fun delete(file: File) {
        syncActive()
        val path = file.absolutePath
        val s = _state.value
        val doomed = s.tabs.filter { it.absolutePath == path || it.absolutePath.startsWith(path + File.separator) }
        doomed.forEach { buffers.remove(it.absolutePath) }
        val tabs = s.tabs - doomed.toSet()
        val activeFile = s.activeFile
        val keepActive = activeFile != null && activeFile !in doomed
        _state.update { st ->
            st.copy(
                tabs = tabs,
                dirty = st.dirty.filterNot { d -> doomed.any { it.absolutePath == d } }.toSet(),
                activeIndex = if (keepActive) tabs.indexOf(activeFile) else if (tabs.isEmpty()) -1 else 0,
                editorRevision = st.editorRevision + 1,
            )
        }
        if (tabs.isNotEmpty() && !keepActive) select(tabs, 0) else if (tabs.isEmpty()) _state.update { it.copy(activeText = "") }
        viewModelScope.launch(Dispatchers.IO) {
            file.deleteRecursively()
            refreshTree()
        }
    }

    // ---------- busca ----------

    fun loadFileIndex() {
        val root = _state.value.project ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val files = ProjectFiles.allTextFiles(root).sortedBy { it.path.lowercase() }
            _state.update { it.copy(fileIndex = files) }
        }
    }

    fun findInFiles(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(findQuery = query) }
        val root = _state.value.project ?: return
        if (query.length < 2) {
            _state.update { it.copy(findHits = emptyList()) }
            return
        }
        syncActive()
        saveAll()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val hits = ProjectFiles.search(root, query)
            _state.update { if (it.findQuery == query) it.copy(findHits = hits) else it }
        }
    }

    // ---------- build ----------

    fun build(variant: BuildVariant, installAfter: Boolean) {
        val project = _state.value.project ?: return
        if (_state.value.build is BuildPanelState.Running) return
        saveAll()
        _state.update { it.copy(build = BuildPanelState.Running("Preparando", variant)) }
        val signing = if (variant == BuildVariant.RELEASE) signingProvider?.invoke() else null
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = builder.build(project, variant, signing) { stage ->
                _state.update { s ->
                    val current = s.build
                    if (current is BuildPanelState.Running) s.copy(build = current.copy(stage = stage)) else s
                }
            }
            _state.update {
                it.copy(
                    build = BuildPanelState.Done(outcome, variant, installAfter && outcome.succeeded),
                    lastApk = outcome.apk ?: it.lastApk,
                )
            }
        }
    }

    fun consumeInstall() {
        _state.update {
            val current = it.build
            if (current is BuildPanelState.Done && current.autoInstall) it.copy(build = current.copy(autoInstall = false)) else it
        }
    }

    // ---------- avisos ----------

    fun notify(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }
}
