@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.falah.pvault

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val CardH = 190.dp
private val CardStep = 72.dp

@Composable
fun DocCard(doc: Doc, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(gradientBrush(doc.gradient))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.07f), size.height * 0.8f, Offset(size.width * 0.95f, size.height * 0.9f))
            drawCircle(Color.White.copy(alpha = 0.06f), size.height * 0.5f, Offset(size.width * 0.85f, size.height * 0.75f))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                doc.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(doc.type.label, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val m = maskNumber(doc.number)
        if (m.isNotEmpty()) {
            Text(
                m, color = Color.White, fontSize = 20.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
            )
        }
        Icon(
            Icons.Default.PictureAsPdf, null, tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(24.dp)
        )
    }
}

@Composable
fun HomeScreen(vm: VaultVM) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Personal Vault", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (vm.docs.size > 1) IconButton({ vm.screen = Screen.Reorder }) {
                        Icon(Icons.Default.SwapVert, "Rearrange")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.screen = Screen.Form(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add document") }
            )
        }
    ) { pad ->
        if (vm.docs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No documents yet.\nTap “Add document” to store a PDF.", color = Color.Gray)
            }
        } else {
            val n = vm.docs.size
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(CardStep * (n - 1) + CardH)) {
                    vm.docs.forEachIndexed { i, d ->
                        key(d.id) {
                            DocCard(
                                d,
                                Modifier.offset(y = CardStep * i).fillMaxWidth().height(CardH),
                                onClick = { vm.screen = Screen.Detail(d.id) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = tint)
    }
}

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

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(doc.type.label) },
                navigationIcon = { IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 28.dp),
                pageSpacing = 12.dp
            ) { p ->
                DocCard(docs[p], Modifier.fillMaxWidth().height(CardH))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                repeat(docs.size) { i ->
                    Box(
                        Modifier.padding(3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) Color.White else Color.DarkGray)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Surface(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), color = Color(0xFF141414)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Card name", color = Color.Gray, fontSize = 12.sp)
                    Text(doc.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text("ID number", color = Color.Gray, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            doc.number.ifBlank { "Not added" },
                            fontSize = 17.sp, letterSpacing = if (doc.number.isBlank()) 0.sp else 1.sp,
                            color = if (doc.number.isBlank()) Color.Gray else Color.White,
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

            Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                ActionButton(Icons.AutoMirrored.Filled.OpenInNew, "Open in", Modifier.weight(1f)) {
                    openExternal(ctx, vm.fileOf(doc), doc.title)
                }
                ActionButton(Icons.Default.Share, "Share", Modifier.weight(1f)) {
                    shareFile(ctx, vm.fileOf(doc), doc.title)
                }
                ActionButton(Icons.Default.Edit, "Edit", Modifier.weight(1f)) { vm.screen = Screen.Form(doc.id) }
                ActionButton(Icons.Default.Delete, "Delete", Modifier.weight(1f), tint = Color(0xFFFF6B6B)) { confirm = true }
            }
            Spacer(Modifier.height(24.dp))
        }
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

@Composable
fun FormScreen(vm: VaultVM, id: String?) {
    val ctx = LocalContext.current
    val existing = id?.let { i -> vm.docs.firstOrNull { it.id == i } }
    var type by remember { mutableStateOf(existing?.type ?: DocType.AADHAAR) }
    var title by remember { mutableStateOf(existing?.title ?: type.label) }
    var number by remember { mutableStateOf(existing?.number ?: "") }
    var gradient by remember { mutableIntStateOf(existing?.gradient ?: type.defaultGradient) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) { uri = u; pickedName = displayName(ctx, u) }
    }
    val canSave = title.isNotBlank() && (existing != null || uri != null)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add document" else "Edit document") },
                navigationIcon = { IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DocCard(
                Doc("preview", title.ifBlank { type.label }, type, number, gradient, ""),
                Modifier.fillMaxWidth().height(CardH)
            )

            Text("Document type", color = Color.Gray, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = {
                            if (existing == null) {
                                if (title.isBlank() || title == type.label) title = t.label
                                gradient = t.defaultGradient
                            }
                            type = t
                        },
                        label = { Text(t.label) }
                    )
                }
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Card name (e.g. Aadhaar – Mother)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            OutlinedTextField(
                value = number, onValueChange = { number = it },
                label = { Text("ID number (optional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            )

            Text("Card colour", color = Color.Gray, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Gradients.size) { i ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(gradientBrush(i))
                            .border(if (i == gradient) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { gradient = i }
                    )
                }
            }

            OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    pickedName ?: if (existing == null) "Choose PDF" else "Replace PDF (optional)",
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = { vm.save(existing, title.trim(), type, number.trim(), gradient, uri) },
                enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

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
    val widthPx = ctx.resources.displayMetrics.widthPixels

    LaunchedEffect(doc.fileName) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(vm.fileOf(doc), ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(pfd).use { r ->
                    val out = mutableListOf<Bitmap>()
                    for (i in 0 until r.pageCount) {
                        r.openPage(i).use { p ->
                            val scale = widthPx * 1.5f / p.width
                            val bmp = Bitmap.createBitmap(
                                (p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(AColor.WHITE)
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            out.add(bmp)
                        }
                        pages = out.toList()
                    }
                }
            } catch (e: SecurityException) {
                error = "This PDF is password-protected. Use “Open in” to view it in another app."
            } catch (e: Exception) {
                error = "Couldn't display this PDF here. Use “Open in” to view it in another app."
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF1A1A1A),
        topBar = {
            TopAppBar(
                title = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton({ openExternal(ctx, vm.fileOf(doc), doc.title) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in") }
                    IconButton({ shareFile(ctx, vm.fileOf(doc), doc.title) }) { Icon(Icons.Default.Share, "Share") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            val err = error
            when {
                err != null -> Text(err, color = Color.LightGray, modifier = Modifier.padding(32.dp))
                pages.isEmpty() -> CircularProgressIndicator()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pages) { b ->
                        androidx.compose.foundation.Image(
                            b.asImageBitmap(), null,
                            Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReorderScreen(vm: VaultVM) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Rearrange cards") },
                navigationIcon = { IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton({ vm.back() }) { Text("Done") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(vm.docs, key = { _, d -> d.id }) { i, d ->
                Row(
                    Modifier.fillMaxWidth().animateItem().clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141414)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(gradientBrush(d.gradient)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(d.type.label, color = Color.Gray, fontSize = 12.sp)
                    }
                    IconButton({ vm.move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                    IconButton({ vm.move(i, i + 1) }, enabled = i < vm.docs.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                }
            }
        }
    }
}
