package com.falah.pvault

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class DocType(val label: String, val defaultGradient: Int) {
    AADHAAR("Aadhaar Card", 0),
    PAN("PAN Card", 1),
    VOTER("Voter ID", 2),
    DL("Driving Licence", 3),
    LL("Learner's Licence", 4),
    PASSPORT("Passport", 6),
    RATION("Ration Card", 8),
    RC("Vehicle RC", 10),
    INSURANCE("Insurance", 12),
    PASSBOOK("Bank Passbook", 14),
    CERT("Certificate", 16),
    OTHER("Other / Custom", 5)
}

data class Doc(
    val id: String,
    val title: String,
    val type: DocType,
    val customType: String = "",
    val number: String = "",
    val gradient: Int = 0,
    val customA: Int = 0,
    val customB: Int = 0,
    val fileName: String,
    val password: String = ""
) {
    val typeLabel: String get() = if (type == DocType.OTHER && customType.isNotBlank()) customType else type.label
}

private fun g(vararg c: Long): List<Color> = c.map { Color(0xFF000000L or it) }

val Gradients: List<List<Color>> = listOf(
    g(0xFF6A00, 0xEE8A4A, 0x1B9E3E), g(0x1E3AFF, 0x3B1F9E, 0xE91EE6), g(0x0F766E, 0x14B8A6), g(0xF28B6B, 0xE5546B),
    g(0x6A5ACD, 0x9D8CFF), g(0x232526, 0x414345), g(0x0F2027, 0x203A43, 0x2C5364), g(0x8A6A00, 0xE6B422),
    g(0x11998E, 0x38EF7D), g(0xC31432, 0x240B36), g(0x134E5E, 0x71B280), g(0xFF512F, 0xDD2476),
    g(0x4776E6, 0x8E54E9), g(0x00B4DB, 0x0083B0), g(0x41295A, 0x2F0743), g(0xFDC830, 0xF37335),
    g(0xD31027, 0xEA384D), g(0x1D976C, 0x93F9B9), g(0x667EEA, 0x764BA2), g(0xFF9966, 0xFF5E62),
    g(0x2193B0, 0x6DD5ED), g(0x373B44, 0x4286F4), g(0xB24592, 0xF15F79), g(0x0F0C29, 0x302B63, 0x24243E)
)

fun gradientBrush(i: Int): Brush = Brush.linearGradient(Gradients[i.mod(Gradients.size)])

fun docColors(d: Doc): List<Color> =
    if (d.customA != 0) listOf(Color(d.customA), Color(d.customB)) else Gradients[d.gradient.mod(Gradients.size)]

fun docBrush(d: Doc): Brush = Brush.linearGradient(docColors(d))

fun maskNumber(n: String): String {
    val c = n.filter { it.isLetterOrDigit() }
    return if (c.isEmpty()) "" else "•••• " + c.takeLast(4)
}

fun formatNumber(n: String): String {
    val t = n.trim()
    return if (t.length == 12 && t.all { it.isDigit() }) t.chunked(4).joinToString(" ") else t
}

fun matches(d: Doc, q: String): Boolean {
    val s = q.trim().lowercase()
    if (s.isEmpty()) return true
    val sq = s.replace(" ", "")
    return d.title.lowercase().contains(s) || d.typeLabel.lowercase().contains(s) ||
        d.number.lowercase().replace(" ", "").contains(sq)
}

fun guessType(name: String): DocType {
    val n = name.lowercase()
    return when {
        n.contains("aadhaar") || n.contains("aadhar") -> DocType.AADHAAR
        n.contains("passport") -> DocType.PASSPORT
        n.contains("voter") || Regex("(^|[^a-z])epic([^a-z]|$)").containsMatchIn(n) -> DocType.VOTER
        n.contains("learner") -> DocType.LL
        n.contains("licen") || Regex("(^|[^a-z])dl([^a-z]|$)").containsMatchIn(n) -> DocType.DL
        Regex("(^|[^a-z])pan([^a-z]|$)").containsMatchIn(n) -> DocType.PAN
        n.contains("ration") -> DocType.RATION
        n.contains("insurance") || n.contains("policy") -> DocType.INSURANCE
        else -> DocType.AADHAAR
    }
}

fun displayName(ctx: Context, uri: Uri): String =
    try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "selected file"
    } catch (e: Exception) { "selected file" }

data class Shared(val uri: Uri, val name: String)

class Repo(private val ctx: Context) {
    val dir = File(ctx.filesDir, "docs").apply { mkdirs() }
    private val index = File(ctx.filesDir, "docs.json")

    fun file(d: Doc) = File(dir, d.fileName)

    fun decode(text: String): List<Doc> {
        val a = JSONArray(text)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Doc(
                id = o.getString("id"), title = o.getString("title"),
                type = runCatching { DocType.valueOf(o.getString("type")) }.getOrDefault(DocType.OTHER),
                customType = o.optString("ctype", ""), number = o.optString("number", ""),
                gradient = o.optInt("gradient", 0), customA = o.optInt("ca", 0), customB = o.optInt("cb", 0),
                fileName = o.getString("file"), password = o.optString("pw", "")
            )
        }
    }

    fun encode(list: List<Doc>): String {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("title", it.title).put("type", it.type.name)
                    .put("ctype", it.customType).put("number", it.number).put("gradient", it.gradient)
                    .put("ca", it.customA).put("cb", it.customB).put("file", it.fileName).put("pw", it.password)
            )
        }
        return a.toString()
    }

    fun load(): List<Doc> = try {
        if (!index.exists()) emptyList() else decode(index.readText())
    } catch (e: Exception) { emptyList() }

    fun save(list: List<Doc>) { index.writeText(encode(list)) }

    /** Imports a PDF as-is, or converts a photo into a PDF. */
    fun importFile(uri: Uri): String {
        val name = UUID.randomUUID().toString() + ".pdf"
        val out = File(dir, name)
        val isPdf = ctx.contentResolver.openInputStream(uri)?.use { s ->
            val b = ByteArray(4)
            s.read(b) == 4 && String(b, Charsets.ISO_8859_1) == "%PDF"
        } ?: throw IllegalStateException("cannot read")
        if (isPdf) {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        } else {
            imageToPdf(ctx, uri, out)
        }
        return name
    }

    fun deleteFile(name: String) { File(dir, name).delete() }

    fun writeBackup(out: OutputStream, list: List<Doc>) {
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("docs.json"))
            z.write(encode(list).toByteArray(Charsets.UTF_8))
            z.closeEntry()
            list.forEach { d ->
                val f = file(d)
                if (f.exists()) {
                    z.putNextEntry(ZipEntry("files/" + d.fileName))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    fun readBackup(input: InputStream): List<Doc> {
        var json: String? = null
        val safe = Regex("[A-Za-z0-9._-]+\\.pdf")
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name.substringAfterLast('/')
                if (n == "docs.json") json = z.readBytes().toString(Charsets.UTF_8)
                else if (e.name.startsWith("files/") && safe.matches(n)) File(dir, n).outputStream().use { z.copyTo(it) }
                e = z.nextEntry
            }
        }
        return decode(json ?: throw IllegalStateException("not a backup"))
    }
}

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data class Form(val id: String?) : Screen
    data class Viewer(val id: String) : Screen
    data object Reorder : Screen
    data object Settings : Screen
}

class VaultVM(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val docs = mutableStateListOf<Doc>()
    val shareQueue = mutableStateListOf<Shared>()
    var screen by mutableStateOf<Screen>(Screen.Home)
    var autoScan by mutableStateOf(false)
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0))
        private set
    var blockShots by mutableStateOf(prefs.getBoolean("secure", false))
        private set

    init {
        docs.addAll(repo.load())
        val l = docs.toList()
        viewModelScope.launch(Dispatchers.IO) { publishShortcuts(app, l) }
    }

    private fun toast(s: String) = Toast.makeText(getApplication(), s, Toast.LENGTH_SHORT).show()

    fun fileOf(d: Doc): File = repo.file(d)

    fun updateTheme(m: Int) { themeMode = m; prefs.edit().putInt("theme", m).apply() }
    fun updateBlockShots(b: Boolean) { blockShots = b; prefs.edit().putBoolean("secure", b).apply() }

    private fun persist() {
        val l = docs.toList()
        viewModelScope.launch(Dispatchers.IO) {
            repo.save(l)
            publishShortcuts(getApplication(), l)
        }
    }

    fun back() {
        screen = when (val s = screen) {
            is Screen.Viewer -> Screen.Detail(s.id)
            is Screen.Form -> {
                shareQueue.clear(); autoScan = false
                s.id?.let { Screen.Detail(it) } ?: Screen.Home
            }
            else -> Screen.Home
        }
    }

    /** Copies an incoming uri into app cache so we don't depend on the sender's temporary permission. */
    private suspend fun stageUris(uris: List<Uri>): List<Shared> = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        uris.mapNotNull { u ->
            try {
                val name = displayName(app, u)
                val dir = File(app.cacheDir, "incoming").apply { mkdirs() }
                val f = File(dir, UUID.randomUUID().toString())
                val input = app.contentResolver.openInputStream(u) ?: return@mapNotNull null
                input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                Shared(Uri.fromFile(f), name)
            } catch (e: Exception) { null }
        }
    }

    fun receiveShared(uris: List<Uri>) {
        viewModelScope.launch {
            val staged = stageUris(uris)
            if (staged.isEmpty()) { toast("Couldn't read the shared file"); return@launch }
            shareQueue.clear(); shareQueue.addAll(staged)
            autoScan = false
            screen = Screen.Form(null)
        }
    }

    fun stage(uri: Uri, then: (Uri) -> Unit) {
        viewModelScope.launch {
            val s = stageUris(listOf(uri)).firstOrNull()
            if (s != null) then(s.uri) else toast("Couldn't read the scan")
        }
    }

    fun save(
        existing: Doc?, title: String, type: DocType, customType: String, number: String,
        gradient: Int, customA: Int, customB: Int, password: String, uri: Uri?
    ) {
        viewModelScope.launch {
            try {
                val newFile = if (uri != null) withContext(Dispatchers.IO) { repo.importFile(uri) } else null
                if (uri != null && uri.scheme == "file") withContext(Dispatchers.IO) { File(uri.path!!).delete() }
                if (shareQueue.firstOrNull()?.uri == uri && uri != null) shareQueue.removeAt(0)
                val savedId: String
                if (existing == null) {
                    val f = newFile ?: return@launch
                    val d = Doc(UUID.randomUUID().toString(), title, type, customType, number, gradient, customA, customB, f, password)
                    docs.add(d)
                    savedId = d.id
                } else {
                    val i = docs.indexOfFirst { it.id == existing.id }
                    if (i >= 0) {
                        docs[i] = existing.copy(
                            title = title, type = type, customType = customType, number = number,
                            gradient = gradient, customA = customA, customB = customB,
                            fileName = newFile ?: existing.fileName, password = password
                        )
                        if (newFile != null) withContext(Dispatchers.IO) { repo.deleteFile(existing.fileName) }
                    }
                    savedId = existing.id
                }
                persist()
                screen = if (existing == null && shareQueue.isNotEmpty()) Screen.Form(null) else Screen.Detail(savedId)
            } catch (e: Exception) {
                toast("Couldn't save the file")
            }
        }
    }

    fun setPassword(id: String, pw: String) {
        val i = docs.indexOfFirst { it.id == id }
        if (i >= 0) { docs[i] = docs[i].copy(password = pw); persist() }
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

    fun exportBackup(uri: Uri) {
        val l = docs.toList()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: return@withContext false
                    out.use { repo.writeBackup(it, l) }
                    true
                } catch (e: Exception) { false }
            }
            toast(if (ok) "Backup saved (${l.size} documents)" else "Backup failed")
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                try {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: return@withContext null
                    input.use { repo.readBackup(it) }
                } catch (e: Exception) { null }
            }
            if (restored == null) { toast("That isn't a valid backup file"); return@launch }
            val have = docs.map { it.id }.toSet()
            val add = restored.filter { it.id !in have && repo.file(it).exists() }
            docs.addAll(add)
            persist()
            toast(if (add.isEmpty()) "Nothing new to restore" else "Restored ${add.size} documents")
        }
    }
}
