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

private fun hueOf(argb: Int, default: Float): Float {
    if (argb == 0) return default
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0]
}

@Composable
fun FormScreen(vm: VaultVM, id: String?) {
    val ctx = LocalContext.current
    val existing = id?.let { i -> vm.docs.firstOrNull { it.id == i } }
    val shared = if (existing == null) vm.shareQueue.firstOrNull() else null
    val k = Pair(id, shared)

    var type by remember(k) { mutableStateOf(existing?.type ?: shared?.let { guessType(it.name) } ?: DocType.AADHAAR) }
    var customType by remember(k) { mutableStateOf(existing?.customType ?: "") }
    var title by remember(k) { mutableStateOf(existing?.title ?: type.label) }
    var number by remember(k) { mutableStateOf(existing?.number ?: "") }
    var password by remember(k) { mutableStateOf(existing?.password ?: "") }
    var showPw by remember(k) { mutableStateOf(false) }
    var gradient by remember(k) { mutableIntStateOf(existing?.gradient ?: type.defaultGradient) }
    var customOn by remember(k) { mutableStateOf((existing?.customA ?: 0) != 0) }
    var hA by remember(k) { mutableFloatStateOf(hueOf(existing?.customA ?: 0, 200f)) }
    var hB by remember(k) { mutableFloatStateOf(hueOf(existing?.customB ?: 0, 290f)) }
    var uri by remember(k) { mutableStateOf<Uri?>(shared?.uri) }
    var pickedName by remember(k) { mutableStateOf<String?>(shared?.name) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) { uri = u; pickedName = displayName(ctx, u) }
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val u = GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pdf?.uri
            if (u != null) vm.stage(u) { staged -> uri = staged; pickedName = "Scanned document.pdf" }
        }
    }
    fun startScan() {
        val act = ctx.findActivity() ?: return
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(6)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(opts).getStartScanIntent(act)
            .addOnSuccessListener { sender -> scanner.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { Toast.makeText(ctx, "Scanner isn't available on this device", Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) {
        if (vm.autoScan && existing == null) { vm.autoScan = false; startScan() }
    }

    val cA = Color.hsv(hA, 0.75f, 0.9f).toArgb()
    val cB = Color.hsv(hB, 0.75f, 0.7f).toArgb()
    val canSave = title.isNotBlank() && (existing != null || uri != null)
    val remaining = vm.shareQueue.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing != null) "Edit document"
                        else if (remaining > 1) "Add document ($remaining files left)" else "Add document"
                    )
                },
                navigationIcon = { BackIcon(vm) },
                colors = transparentBar()
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DocCard(
                Doc(
                    "preview", title.ifBlank { type.label }, type, customType, number, gradient,
                    if (customOn) cA else 0, if (customOn) cB else 0, ""
                ),
                Modifier.fillMaxWidth().height(CardH)
            )

            Text("Document type", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = {
                            if (existing == null) {
                                if (title.isBlank() || title == type.label) title = t.label
                                if (!customOn) gradient = t.defaultGradient
                            }
                            type = t
                        },
                        label = { Text(t.label) }
                    )
                }
            }
            if (type == DocType.OTHER) {
                OutlinedTextField(
                    value = customType, onValueChange = { customType = it }, singleLine = true,
                    label = { Text("Custom type name (e.g. Gas connection)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text("Card name (e.g. Aadhaar – Mother)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            OutlinedTextField(
                value = number, onValueChange = { number = it }, singleLine = true,
                label = { Text("ID number (optional)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, singleLine = true,
                label = { Text("PDF password (optional)") },
                supportingText = { Text("Saved only on this phone so locked PDFs open automatically. Aadhaar PDF: first 4 letters of your name in CAPITALS + birth year.") },
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ showPw = !showPw }) {
                        Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show password")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Card colour", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Gradients.size) { i ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(gradientBrush(i))
                            .border(if (!customOn && i == gradient) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            .clickable { customOn = false; gradient = i }
                    )
                }
                item {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                            .border(if (customOn) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            .clickable { customOn = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, "Custom colour", tint = Color.White) }
                }
            }
            if (customOn) {
                Text("Custom colour – start", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Slider(value = hA, onValueChange = { hA = it }, valueRange = 0f..360f)
                Text("Custom colour – end", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Slider(value = hB, onValueChange = { hB = it }, valueRange = 0f..360f)
            }

            Text("Document file (PDF or photo)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text(pickedName ?: if (existing == null) "Choose file" else "Replace file", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { startScan() }, modifier = Modifier.height(52.dp)) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }

            Button(
                onClick = {
                    vm.save(
                        existing, title.trim(), type, customType.trim(), number.trim(), gradient,
                        if (customOn) cA else 0, if (customOn) cB else 0, password, uri
                    )
                },
                enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
