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
fun DetailScreen(vm: VaultVM, id: String) {
    val ctx = LocalContext.current
    val docs = vm.docs
    if (docs.isEmpty()) {
        LaunchedEffect(Unit) { vm.screen = Screen.Home }
        return
    }
    val pager = rememberPagerState(
        initialPage = docs.indexOfFirst { it.id == id }.coerceAtLeast(0),
        pageCount = { docs.size }
    )
    val doc = docs[pager.currentPage.coerceIn(0, docs.lastIndex)]
    var confirm by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true }
    val enter by animateFloatAsState(if (opened) 1f else 0f, tween(600, easing = FastOutSlowInEasing), label = "open")
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(doc.typeLabel) },
                navigationIcon = { BackIcon(vm) },
                colors = transparentBar()
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 28.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.graphicsLayer {
                    cameraDistance = 14f * density
                    rotationX = (1f - enter) * -55f
                    translationY = (1f - enter) * 160f
                    alpha = enter
                }
            ) { p ->
                val off = (pager.currentPage - p) + pager.currentPageOffsetFraction
                DocCard(
                    docs[p],
                    Modifier.fillMaxWidth().height(CardH)
                        .graphicsLayer {
                            cameraDistance = 14f * density
                            val d = abs(off).coerceIn(0f, 1f)
                            rotationY = off * -28f
                            scaleX = 1f - d * 0.12f
                            scaleY = 1f - d * 0.12f
                            alpha = 1f - d * 0.4f
                        }
                        .pressTilt(),
                    onClick = {},
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); reveal = true }
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                repeat(docs.size) { i ->
                    Box(
                        Modifier.padding(3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Surface(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Card name", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(doc.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text("ID number", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (doc.number.isBlank()) "Not added" else formatNumber(doc.number),
                            fontSize = 17.sp, letterSpacing = if (doc.number.isBlank()) 0.sp else 1.sp,
                            color = if (doc.number.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (doc.number.isNotBlank()) IconButton({ copyText(ctx, "ID number", doc.number) }) {
                            Icon(Icons.Default.ContentCopy, "Copy number")
                        }
                        IconButton({ vm.screen = Screen.Form(doc.id) }) { Icon(Icons.Default.Edit, "Edit number") }
                    }
                }
            }

            Button(
                onClick = { vm.screen = Screen.Viewer(doc.id) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, null)
                Spacer(Modifier.width(8.dp))
                Text("View PDF")
            }

            Row(Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
                ActionButton(Icons.AutoMirrored.Filled.OpenInNew, "Open in", Modifier.weight(1f)) {
                    openExternal(ctx, vm.fileOf(doc), doc.title)
                }
                ActionButton(Icons.Default.Share, "Share", Modifier.weight(1f)) { shareFile(ctx, vm.fileOf(doc), doc.title) }
                ActionButton(Icons.Default.Edit, "Edit", Modifier.weight(1f)) { vm.screen = Screen.Form(doc.id) }
                ActionButton(Icons.Default.PushPin, "Pin", Modifier.weight(1f)) { pinShortcut(ctx, doc) }
                ActionButton(Icons.Default.Delete, "Delete", Modifier.weight(1f), tint = Color(0xFFFF6B6B)) { confirm = true }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (reveal) {
        RevealDialog(
            doc, onDismiss = { reveal = false }, onDetails = { reveal = false },
            onView = { reveal = false; vm.screen = Screen.Viewer(doc.id) }
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete “${doc.title}”?") },
            text = { Text("The stored PDF will be removed from this app. This can't be undone.") },
            confirmButton = { TextButton({ confirm = false; vm.delete(doc) }) { Text("Delete", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } }
        )
    }
}
