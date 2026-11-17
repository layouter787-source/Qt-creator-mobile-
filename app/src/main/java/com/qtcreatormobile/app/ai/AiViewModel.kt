package com.qtcreatormobile.app.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtcreatormobile.aiclient.AiClient
import com.qtcreatormobile.aiclient.AiException
import com.qtcreatormobile.aiclient.ChatMessage
import com.qtcreatormobile.aiclient.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiMessage(val id: Long, val role: String, val text: String)

data class AiUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val activeId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
    val includeFile: Boolean = true,
    val models: List<String> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
) {
    val active: ProviderConfig? get() = providers.firstOrNull { it.id == activeId } ?: providers.firstOrNull()
}

private const val SYSTEM_PROMPT =
    "Você é um assistente de programação dentro de uma IDE Android (QT Creator Mobile). " +
        "O usuário desenvolve apps Android em C++, Java e Kotlin e compila no próprio aparelho " +
        "(clang/NDK, javac, kotlinc, d8, aapt2). Responda no idioma do usuário, de forma objetiva, " +
        "e coloque todo código em blocos cercados por ``` com a linguagem indicada."

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AiSettingsStore(application)
    private val _state = MutableStateFlow(
        store.load().let { list -> AiUiState(providers = list, activeId = store.activeId ?: list.firstOrNull()?.id) },
    )
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    @Volatile private var stopRequested = false
    private var nextId = 1L

    // ---------- provedores ----------

    fun saveProvider(config: ProviderConfig) {
        _state.update { s ->
            val exists = s.providers.any { it.id == config.id }
            val list = if (exists) s.providers.map { if (it.id == config.id) config else it } else s.providers + config
            store.save(list)
            val active = s.activeId ?: config.id
            store.activeId = active
            s.copy(providers = list, activeId = active)
        }
    }

    fun deleteProvider(id: String) {
        _state.update { s ->
            val list = s.providers.filterNot { it.id == id }
            store.save(list)
            val active = if (s.activeId == id) list.firstOrNull()?.id else s.activeId
            store.activeId = active
            s.copy(providers = list, activeId = active)
        }
    }

    fun setActive(id: String) {
        store.activeId = id
        _state.update { it.copy(activeId = id) }
    }

    fun fetchModels(config: ProviderConfig) {
        _state.update { it.copy(modelsLoading = true, modelsError = null, models = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val models = AiClient(config).listModels()
                _state.update { it.copy(models = models, modelsLoading = false, modelsError = if (models.isEmpty()) "O provedor não devolveu modelos." else null) }
            } catch (e: Exception) {
                _state.update { it.copy(modelsLoading = false, modelsError = e.message ?: "Falha ao buscar modelos.") }
            }
        }
    }

    fun clearModels() {
        _state.update { it.copy(models = emptyList(), modelsError = null, modelsLoading = false) }
    }

    // ---------- chat ----------

    fun setIncludeFile(value: Boolean) {
        _state.update { it.copy(includeFile = value) }
    }

    fun clearChat() {
        stopRequested = true
        _state.update { it.copy(messages = emptyList(), error = null, sending = false) }
    }

    fun stop() {
        stopRequested = true
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun send(text: String, fileName: String?, fileText: String?) {
        val snapshot = _state.value
        val provider = snapshot.active
        if (provider == null) {
            _state.update { it.copy(error = "Configure um provedor de IA primeiro.") }
            return
        }
        val clean = text.trim()
        if (clean.isEmpty() || snapshot.sending) return

        val history = snapshot.messages.filter { it.text.isNotBlank() }.takeLast(20).map { ChatMessage(it.role, it.text) }
        val prompt = if (snapshot.includeFile && fileName != null && fileText != null) {
            "Arquivo aberto no editor ($fileName):\n```\n${fileText.take(24_000)}\n```\n\n$clean"
        } else {
            clean
        }
        val userMessage = UiMessage(nextId++, ChatMessage.USER, clean)
        val replyId = nextId++
        stopRequested = false
        _state.update {
            it.copy(messages = it.messages + userMessage + UiMessage(replyId, ChatMessage.ASSISTANT, ""), sending = true, error = null)
        }

        viewModelScope.launch(Dispatchers.IO) {
            var failure: String? = null
            try {
                AiClient(provider).chat(
                    messages = listOf(ChatMessage(ChatMessage.SYSTEM, SYSTEM_PROMPT)) + history + ChatMessage(ChatMessage.USER, prompt),
                    shouldStop = { stopRequested },
                ) { delta ->
                    _state.update { s -> s.copy(messages = s.messages.map { if (it.id == replyId) it.copy(text = it.text + delta) else it }) }
                }
            } catch (e: AiException) {
                failure = e.message
            } catch (e: Exception) {
                failure = e.message ?: e.javaClass.simpleName
            }
            _state.update { s ->
                val reply = s.messages.firstOrNull { it.id == replyId }
                val messages = if (reply != null && reply.text.isEmpty()) s.messages.filterNot { it.id == replyId } else s.messages
                s.copy(messages = messages, sending = false, error = failure)
            }
        }
    }
}
