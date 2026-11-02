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
fun ReorderScreen(vm: VaultVM) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Rearrange cards") },
                navigationIcon = { BackIcon(vm) },
                actions = { TextButton({ vm.back() }) { Text("Done") } },
                colors = transparentBar()
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(vm.docs, key = { _, d -> d.id }) { i, d ->
                Row(
                    Modifier.fillMaxWidth().animateItem().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(docBrush(d)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(d.typeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    IconButton({ vm.move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                    IconButton({ vm.move(i, i + 1) }, enabled = i < vm.docs.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(s: String) {
    Text(s, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun SettingsScreen(vm: VaultVM) {
    val stamp = java.time.LocalDate.now().toString().replace("-", "")
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u: Uri? ->
        if (u != null) vm.exportBackup(u)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) vm.importBackup(u)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { BackIcon(vm) }, colors = transparentBar()) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System" to 0, "Light" to 1, "Dark" to 2).forEach { (label, m) ->
                    FilterChip(selected = vm.themeMode == m, onClick = { vm.updateTheme(m) }, label = { Text(label) })
                }
            }

            SectionTitle("Privacy")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Block screenshots", fontWeight = FontWeight.Medium)
                    Text("Hides the app in screenshots, screen recordings and the recent-apps view.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.blockShots, onCheckedChange = { vm.updateBlockShots(it) })
            }

            SectionTitle("Backup")
            Text(
                "A backup is one .zip file with all your documents and card details (including saved PDF passwords). Keep it somewhere safe.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { backup.launch("PersonalVault-backup-$stamp.zip") },
                enabled = vm.docs.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("Create backup") }
            OutlinedButton(
                onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restore from backup") }
            Text("Restoring adds missing documents; it never deletes or overwrites the ones you already have.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("About")
            Text("Personal Vault 1.2", fontWeight = FontWeight.Medium)
            Text(
                "Documents are stored only on this phone, in the app's private storage. Share a PDF or photo from WhatsApp or Files to this app to add it quickly.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
