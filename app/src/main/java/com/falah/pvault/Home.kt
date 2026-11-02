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
private fun StackCard(d: Doc, i: Int, selected: String?, t: Float, onTap: () -> Unit, onLong: () -> Unit, badge: Int? = null) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enter by animateFloatAsState(
        if (appeared) 1f else 0f, tween(500, delayMillis = i * 70, easing = FastOutSlowInEasing), label = "enter"
    )
    val isSel = selected == d.id
    val stepPx = with(LocalDensity.current) { (CardStep * i).toPx() }
    val fallPx = with(LocalDensity.current) { 360.dp.toPx() }
    val riseIn = with(LocalDensity.current) { 120.dp.toPx() }
    DocCard(
        d,
        Modifier.offset(y = CardStep * i).fillMaxWidth().height(CardH)
            .zIndex(if (isSel) 100f else i.toFloat())
            .graphicsLayer {
                cameraDistance = 16f * density
                translationY = (1f - enter) * riseIn + if (isSel) -stepPx * t else fallPx * t
                val sc = if (isSel) 1f + 0.05f * t else 1f
                scaleX = sc; scaleY = sc
                rotationX = if (isSel) -8f * t else 0f
                alpha = enter * (if (isSel) 1f else 1f - t)
            },
        badge = badge,
        onClick = onTap,
        onLongClick = onLong
    )
}

@Composable
fun RevealDialog(doc: Doc, onDismiss: () -> Unit, onDetails: () -> Unit, onView: () -> Unit) {
    val ctx = LocalContext.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val sc by animateFloatAsState(if (appeared) 1f else 0.7f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "rv")
    val a by animateFloatAsState(if (appeared) 1f else 0f, tween(200), label = "rva")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f * a))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(20.dp).graphicsLayer { scaleX = sc; scaleY = sc; alpha = a },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DocCard(doc, Modifier.fillMaxWidth().height(240.dp).pressTilt(), reveal = true)
                Spacer(Modifier.height(20.dp))
                Text(doc.typeLabel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (doc.number.isNotBlank()) {
                    Text("ID: " + formatNumber(doc.number), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (doc.number.isNotBlank()) {
                        FilledTonalButton({ copyText(ctx, "ID number", doc.number) }) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy")
                        }
                    }
                    FilledTonalButton(onView) {
                        Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("View PDF")
                    }
                    FilledTonalButton(onDetails) { Text("Details") }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: VaultVM) {
    val haptic = LocalHapticFeedback.current
    var selected by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf<Doc?>(null) }
    val ctx = LocalContext.current
    var mergeMode by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<String>() }
    val focus = remember { FocusRequester() }
    val t by animateFloatAsState(if (selected != null) 1f else 0f, tween(380, easing = FastOutSlowInEasing), label = "select")
    LaunchedEffect(selected) {
        val s = selected
        if (s != null) { delay(380); vm.screen = Screen.Detail(s) }
    }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    BackHandler2(searching) { searching = false; query = "" }
    BackHandler2(mergeMode) { mergeMode = false; picked.clear() }

    val list = vm.docs.filter { matches(it, query) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Search name or ID number") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                    } else if (mergeMode) Text("Select documents", fontWeight = FontWeight.Bold)
                    else Text("Personal Vault", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (searching) IconButton({ searching = false; query = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
                    else if (mergeMode) IconButton({ mergeMode = false; picked.clear() }) { Icon(Icons.Default.Close, "Cancel") }
                },
                colors = transparentBar(),
                actions = {
                    if (searching) {
                        if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear") }
                    } else if (!mergeMode) {
                        if (vm.docs.size > 1) IconButton({ mergeMode = true; picked.clear() }) { Icon(Icons.Default.MergeType, "Merge and share") }
                        if (vm.docs.isNotEmpty()) IconButton({ searching = true }) { Icon(Icons.Default.Search, "Search") }
                        if (vm.docs.size > 1) IconButton({ vm.screen = Screen.Reorder }) { Icon(Icons.Default.SwapVert, "Rearrange") }
                        IconButton({ vm.screen = Screen.Settings }) { Icon(Icons.Default.Settings, "Settings") }
                    }
                }
            )
        },
        bottomBar = {
            if (mergeMode) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton({ mergeMode = false; picked.clear() }) { Text("Cancel") }
                        Button(
                            onClick = {
                                vm.mergeAndShare(picked.toList()) { f, name ->
                                    shareCacheFile(ctx, f, name)
                                    mergeMode = false
                                    picked.clear()
                                }
                            },
                            enabled = picked.size >= 2 && !vm.merging,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            if (vm.merging) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text(if (picked.size < 2) "Pick at least 2" else "Merge & share (${picked.size})")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!mergeMode) ExtendedFloatingActionButton(
                onClick = { vm.screen = Screen.Form(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add document") }
            )
        }
    ) { pad ->
        if (vm.docs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(
                    "No documents yet.\nTap “Add document”, or share a PDF to this app from WhatsApp or Files.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp)
                )
            }
        } else if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No matching documents", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(CardStep * (list.size - 1) + CardH)
                        .graphicsLayer {
                            cameraDistance = 20f * density
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            rotationX = vm.tiltDeg.toFloat()
                        }
                ) {
                    list.forEachIndexed { i, d ->
                        key(d.id) {
                            StackCard(
                                d, i, selected, t,
                                onTap = {
                                    if (mergeMode) {
                                        if (d.id in picked) picked.remove(d.id) else picked.add(d.id)
                                    } else if (selected == null) selected = d.id
                                },
                                onLong = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); reveal = d },
                                badge = if (mergeMode) picked.indexOf(d.id) + 1 else null
                            )
                        }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    reveal?.let { d ->
        RevealDialog(
            d, onDismiss = { reveal = null },
            onDetails = { reveal = null; vm.screen = Screen.Detail(d.id) },
            onView = { reveal = null; vm.screen = Screen.Viewer(d.id) }
        )
    }
}

@Composable
private fun BackHandler2(enabled: Boolean, onBack: () -> Unit) =
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
