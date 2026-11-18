package com.qtcreatormobile.app.ide

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtcreatormobile.app.ai.KeyCipher
import com.qtcreatormobile.buildengine.RealProcessRunner
import com.qtcreatormobile.buildengine.ToolchainLayout
import com.qtcreatormobile.projectmodel.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class KeystoreEntry(
    val id: String,
    val name: String,
    val file: File,
    val alias: String,
    val password: String,
)

/** Regras de validacao dos dados de um keystore (sem Android, facil de testar). */
object KeystoreRules {
    private val ALIAS = Regex("[A-Za-z0-9_.-]{1,40}")

    fun validateAlias(alias: String): String? =
        if (ALIAS.matches(alias)) null else "O alias deve ter de 1 a 40 letras, números, '.', '_' ou '-'."

    fun validatePassword(password: String): String? =
        if (password.length >= 6) null else "A senha precisa ter pelo menos 6 caracteres."

    /** Remove caracteres que quebram um Distinguished Name (X.500). */
    fun cleanDnValue(value: String): String = value.filterNot { it in ",=+<>#;\"\\" }.trim()

    fun distinguishedName(commonName: String, organization: String, country: String): String =
        buildList {
            add("CN=${cleanDnValue(commonName)}")
            cleanDnValue(organization).takeIf { it.isNotEmpty() }?.let { add("O=$it") }
            cleanDnValue(country).takeIf { it.length == 2 }?.let { add("C=${it.uppercase()}") }
        }.joinToString(", ")
}

/** Lista de keystores guardados no aparelho. As senhas ficam cifradas pelo Android Keystore. */
class KeystoreStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("keystores", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "keystores").apply { mkdirs() }

    fun fileFor(id: String) = File(dir, "$id.keystore")

    fun list(): List<KeystoreEntry> = try {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val id = o.getString("id")
            val file = fileFor(id)
            if (!file.isFile) null else KeystoreEntry(id, o.getString("name"), file, o.getString("alias"), KeyCipher.decrypt(o.optString("pw", "")))
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(items: List<KeystoreEntry>) {
        val array = JSONArray()
        items.forEach { e ->
            array.put(JSONObject().put("id", e.id).put("name", e.name).put("alias", e.alias).put("pw", KeyCipher.encrypt(e.password)))
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(value) { prefs.edit().putString("selected", value).apply() }

    fun add(entry: KeystoreEntry) {
        save(list() + entry)
        if (selectedId == null || list().none { it.id == selectedId }) selectedId = entry.id
    }

    fun remove(id: String) {
        fileFor(id).delete()
        save(list().filterNot { it.id == id })
        if (selectedId == id) selectedId = list().firstOrNull()?.id
    }

    /** Chave escolhida para o build Release (ou null se nao houver nenhuma). */
    fun selectedSpec(): SigningSpec? {
        val items = list()
        val entry = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull() ?: return null
        return SigningSpec(entry.file, entry.password, entry.alias, entry.password)
    }
}

data class KeystoreUiState(
    val items: List<KeystoreEntry> = emptyList(),
    val selectedId: String? = null,
    val busy: Boolean = false,
    val details: String? = null,
)

class KeystoreViewModel(application: Application) : AndroidViewModel(application) {
    private val store = KeystoreStore(application)
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<KeystoreUiState> = _state.asStateFlow()

    private fun snapshot(busy: Boolean = false, details: String? = null): KeystoreUiState {
        val items = store.list()
        return KeystoreUiState(items, store.selectedId?.takeIf { id -> items.any { it.id == id } } ?: items.firstOrNull()?.id, busy, details)
    }

    private fun keytool(): File =
        File(getApplication<Application>().filesDir, "toolchain/${ToolchainLayout.subdirFor(Language.JAVA)}/bin/keytool")

    private fun requireKeytool(): String? =
        if (keytool().isFile) null else "O JDK ainda não está instalado. Conclua a instalação do ambiente primeiro."

    fun select(id: String) {
        store.selectedId = id
        _state.value = snapshot()
    }

    fun delete(id: String) {
        store.remove(id)
        _state.value = snapshot()
    }

    fun dismissDetails() {
        _state.update { it.copy(details = null) }
    }

    /** Cria um keystore PKCS12 novo com o keytool do JDK instalado. [onResult] recebe a mensagem de erro, ou null. */
    fun create(
        name: String,
        alias: String,
        password: String,
        commonName: String,
        organization: String,
        country: String,
        years: Int,
        onResult: (String?) -> Unit,
    ) {
        val problem = when {
            name.isBlank() -> "Informe um nome para o keystore."
            commonName.isBlank() -> "Informe o nome do titular (CN)."
            else -> KeystoreRules.validateAlias(alias) ?: KeystoreRules.validatePassword(password) ?: requireKeytool()
        }
        if (problem != null) {
            onResult(problem)
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                val id = UUID.randomUUID().toString()
                val file = store.fileFor(id)
                try {
                    val command = listOf(
                        keytool().absolutePath, "-genkeypair",
                        "-keystore", file.absolutePath, "-storetype", "PKCS12",
                        "-alias", alias, "-keyalg", "RSA", "-keysize", "2048",
                        "-validity", (years.coerceIn(1, 100) * 365).toString(),
                        "-storepass", password,
                        "-dname", KeystoreRules.distinguishedName(commonName, organization, country),
                    )
                    val result = RealProcessRunner().run(command, file.parentFile!!)
                    if (!result.succeeded || !file.isFile) {
                        file.delete()
                        "Falha ao criar o keystore: ${(result.stderr + result.stdout).trim().take(400)}"
                    } else {
                        store.add(KeystoreEntry(id, name.trim(), file, alias, password))
                        null
                    }
                } catch (e: Exception) {
                    file.delete()
                    "Falha ao criar o keystore: ${e.message}"
                }
            }
            _state.value = snapshot()
            onResult(error)
        }
    }

    /** Importa um keystore existente (.jks, .keystore, .p12) e confere a senha e o alias. */
    fun import(uri: Uri, name: String, alias: String, password: String, onResult: (String?) -> Unit) {
        val problem = when {
            name.isBlank() -> "Informe um nome para o keystore."
            alias.isBlank() -> "Informe o alias da chave."
            password.isEmpty() -> "Informe a senha."
            else -> requireKeytool()
        }
        if (problem != null) {
            onResult(problem)
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                val id = UUID.randomUUID().toString()
                val file = store.fileFor(id)
                try {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: return@withContext "Não foi possível abrir o arquivo escolhido."
                    input.use { source -> file.outputStream().use { source.copyTo(it) } }
                    val check = RealProcessRunner().run(
                        listOf(keytool().absolutePath, "-list", "-keystore", file.absolutePath, "-storepass", password, "-alias", alias),
                        file.parentFile!!,
                    )
                    if (!check.succeeded) {
                        file.delete()
                        "Senha ou alias incorretos, ou arquivo inválido: ${(check.stderr + check.stdout).trim().take(300)}"
                    } else {
                        store.add(KeystoreEntry(id, name.trim(), file, alias, password))
                        null
                    }
                } catch (e: Exception) {
                    file.delete()
                    "Falha ao importar: ${e.message}"
                }
            }
            _state.value = snapshot()
            onResult(error)
        }
    }

    /** Mostra titular, validade e impressoes digitais (SHA-1/SHA-256) da chave. */
    fun describe(entry: KeystoreEntry) {
        val problem = requireKeytool()
        if (problem != null) {
            _state.update { it.copy(details = problem) }
            return
        }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                val result = RealProcessRunner().run(
                    listOf(keytool().absolutePath, "-list", "-v", "-keystore", entry.file.absolutePath, "-storepass", entry.password, "-alias", entry.alias),
                    entry.file.parentFile!!,
                )
                if (!result.succeeded) {
                    "Não foi possível ler: ${(result.stderr + result.stdout).trim().take(300)}"
                } else {
                    result.stdout.lines()
                        .map { it.trim() }
                        .filter { l -> listOf("Owner", "Dono", "Valid", "Válido", "SHA1", "SHA-1", "SHA256", "SHA-256", "Alias").any { l.startsWith(it) } }
                        .joinToString("\n")
                        .ifBlank { result.stdout.take(600) }
                }
            }
            _state.update { it.copy(details = text) }
        }
    }
}
