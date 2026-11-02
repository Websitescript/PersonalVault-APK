@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.falah.pvault

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlin.math.abs

@Composable
fun ViewerScreen(vm: VaultVM, id: String) {
    val ctx = LocalContext.current
    val doc = vm.docs.firstOrNull { it.id == id }
    if (doc == null) {
        LaunchedEffect(Unit) { vm.screen = Screen.Home }
        return
    }
    var pages by remember { mutableStateOf(listOf<Bitmap>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var needPw by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var tryPw by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var savePw by remember { mutableStateOf(true) }
    val widthPx = ctx.resources.displayMetrics.widthPixels
    val stored = doc.password

    LaunchedEffect(doc.fileName, attempt) {
        pages = emptyList(); error = null; needPw = false
        val typed = tryPw
        val pw = typed ?: stored.ifBlank { null }
        val res = withContext(Dispatchers.IO) {
            loadPdf(ctx, doc.id, vm.fileOf(doc), widthPx, pw) { pages = it }
        }
        when (res) {
            is LoadResult.Ok -> if (typed != null && savePw && typed != stored) vm.setPassword(doc.id, typed)
            is LoadResult.NeedPw -> { wrong = res.wrong; needPw = true }
            is LoadResult.Fail -> error = res.msg
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        topBar = {
            TopAppBar(
                title = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackIcon(vm) },
                actions = {
                    IconButton({ openExternal(ctx, vm.fileOf(doc), doc.title) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in") }
                    IconButton({ shareFile(ctx, vm.fileOf(doc), doc.title) }) { Icon(Icons.Default.Share, "Share") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            val err = error
            when {
                err != null -> Text(err, modifier = Modifier.padding(32.dp))
                needPw -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Lock, null, Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("This PDF is password-protected.")
                }
                pages.isEmpty() -> CircularProgressIndicator()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pages) { b ->
                        Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
    }

    if (needPw) {
        var input by remember { mutableStateOf("") }
        var show by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { needPw = false; error = "Password needed. Tap back, or use “Open in” to view in another app." },
            title = { Text("Enter PDF password") },
            text = {
                Column {
                    if (wrong) Text("That password didn't work. Try again.", color = Color(0xFFFF6B6B))
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton({ show = !show }) { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show") }
                        }
                    )
                    Text(
                        "Aadhaar PDF: first 4 letters of your name in CAPITALS + birth year (e.g. FALA1999).",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Checkbox(savePw, { savePw = it })
                        Text("Remember for this card")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { tryPw = input; attempt++ },
                    enabled = input.isNotEmpty()
                ) { Text("Unlock") }
            },
            dismissButton = {
                TextButton({ needPw = false; error = "Password needed. Tap back, or use “Open in” to view in another app." }) { Text("Cancel") }
            }
        )
    }
}
