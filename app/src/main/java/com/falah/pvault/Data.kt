package com.falah.pvault

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class DocType(val label: String, val defaultGradient: Int) {
    AADHAAR("Aadhaar Card", 0),
    PAN("PAN Card", 1),
    VOTER("Voter ID", 2),
    DL("Driving Licence", 3),
    LL("Learner's Licence", 4),
    OTHER("Other", 5)
}

data class Doc(
    val id: String,
    val title: String,
    val type: DocType,
    val number: String,
    val gradient: Int,
    val fileName: String
)

val Gradients: List<List<Color>> = listOf(
    listOf(Color(0xFFFF6A00), Color(0xFFEE8A4A), Color(0xFF1B9E3E)),
    listOf(Color(0xFF1E3AFF), Color(0xFF3B1F9E), Color(0xFFE91EE6)),
    listOf(Color(0xFF0F766E), Color(0xFF14B8A6)),
    listOf(Color(0xFFF28B6B), Color(0xFFE5546B)),
    listOf(Color(0xFF6A5ACD), Color(0xFF9D8CFF)),
    listOf(Color(0xFF232526), Color(0xFF414345)),
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    listOf(Color(0xFF8A6A00), Color(0xFFE6B422)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFC31432), Color(0xFF240B36))
)

fun gradientBrush(i: Int): Brush = Brush.linearGradient(Gradients[i.mod(Gradients.size)])

fun maskNumber(n: String): String {
    val c = n.filter { it.isLetterOrDigit() }
    return if (c.isEmpty()) "" else "•••• " + c.takeLast(4)
}

fun displayName(ctx: Context, uri: Uri): String =
    try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "selected.pdf"
    } catch (e: Exception) { "selected.pdf" }

class Repo(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "docs").apply { mkdirs() }
    private val index = File(ctx.filesDir, "docs.json")

    fun file(d: Doc) = File(dir, d.fileName)

    fun load(): List<Doc> = try {
        if (!index.exists()) emptyList() else {
            val a = JSONArray(index.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Doc(
                    o.getString("id"), o.getString("title"),
                    runCatching { DocType.valueOf(o.getString("type")) }.getOrDefault(DocType.OTHER),
                    o.optString("number", ""), o.optInt("gradient", 0), o.getString("file")
                )
            }
        }
    } catch (e: Exception) { emptyList() }

    fun save(list: List<Doc>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("type", it.type.name)
                .put("number", it.number).put("gradient", it.gradient).put("file", it.fileName))
        }
        index.writeText(a.toString())
    }

    fun importPdf(uri: Uri): String {
        val name = UUID.randomUUID().toString() + ".pdf"
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("no stream")
        input.use { i -> File(dir, name).outputStream().use { o -> i.copyTo(o) } }
        return name
    }

    fun deleteFile(name: String) { File(dir, name).delete() }
}

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data class Form(val id: String?) : Screen
    data class Viewer(val id: String) : Screen
    data object Reorder : Screen
}

class VaultVM(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    val docs = mutableStateListOf<Doc>()
    var screen by mutableStateOf<Screen>(Screen.Home)

    init { docs.addAll(repo.load()) }

    fun fileOf(d: Doc): File = repo.file(d)

    private fun persist() {
        val l = docs.toList()
        viewModelScope.launch(Dispatchers.IO) { repo.save(l) }
    }

    fun back() {
        screen = when (val s = screen) {
            is Screen.Viewer -> Screen.Detail(s.id)
            is Screen.Form -> s.id?.let { Screen.Detail(it) } ?: Screen.Home
            else -> Screen.Home
        }
    }

    fun save(existing: Doc?, title: String, type: DocType, number: String, gradient: Int, uri: Uri?) {
        viewModelScope.launch {
            try {
                val newFile = if (uri != null) withContext(Dispatchers.IO) { repo.importPdf(uri) } else null
                if (existing == null) {
                    val f = newFile ?: return@launch
                    val d = Doc(UUID.randomUUID().toString(), title, type, number, gradient, f)
                    docs.add(d)
                    screen = Screen.Detail(d.id)
                } else {
                    val i = docs.indexOfFirst { it.id == existing.id }
                    if (i >= 0) {
                        docs[i] = existing.copy(
                            title = title, type = type, number = number, gradient = gradient,
                            fileName = newFile ?: existing.fileName
                        )
                        if (newFile != null) withContext(Dispatchers.IO) { repo.deleteFile(existing.fileName) }
                    }
                    screen = Screen.Detail(existing.id)
                }
                persist()
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Couldn't save the file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun delete(d: Doc) {
        docs.removeAll { it.id == d.id }
        viewModelScope.launch(Dispatchers.IO) { repo.deleteFile(d.fileName) }
        persist()
        screen = Screen.Home
    }

    fun move(from: Int, to: Int) {
        if (from !in docs.indices || to !in docs.indices) return
        docs.add(to, docs.removeAt(from))
        persist()
    }
}
