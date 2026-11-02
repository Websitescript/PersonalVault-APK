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

val CardH = 190.dp
val CardStep = 72.dp

/** Press-and-hold tilt: the card leans toward the finger. Doesn't consume touches, so swipes still work. */
fun Modifier.pressTilt(): Modifier = composed {
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun tilt(x: Float, y: Float) {
                    val nx = (x / size.width - 0.5f).coerceIn(-0.5f, 0.5f)
                    val ny = (y / size.height - 0.5f).coerceIn(-0.5f, 0.5f)
                    scope.launch { rx.snapTo(-ny * 22f); ry.snapTo(nx * 22f) }
                }
                tilt(down.position.x, down.position.y)
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull() ?: break
                    tilt(c.position.x, c.position.y)
                } while (ev.changes.any { it.pressed })
                scope.launch { rx.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                scope.launch { ry.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            }
        }
        .graphicsLayer {
            rotationX = rx.value
            rotationY = ry.value
            cameraDistance = 14f * density
        }
}

@Composable
fun DocCard(
    doc: Doc,
    modifier: Modifier = Modifier,
    reveal: Boolean = false,
    badge: Int? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(24.dp)
    val glow = docColors(doc).last()
    val ts = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 3f), 6f))
    val click = if (onClick != null || onLongClick != null)
        Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick) else Modifier
    Box(
        modifier
            .shadow(14.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(docBrush(doc))
            .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))), shape)
            .then(click)
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.07f), size.height * 0.8f, Offset(size.width * 0.95f, size.height * 0.9f))
            drawCircle(Color.White.copy(alpha = 0.06f), size.height * 0.5f, Offset(size.width * 0.85f, size.height * 0.75f))
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.20f)
                )
            )
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                doc.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = ts,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(if (badge > 0) Color.White else Color.Transparent)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (badge > 0) Text(badge.toString(), color = Color(0xFF111111), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else if (doc.title != doc.typeLabel) {
                Spacer(Modifier.width(8.dp))
                Text(doc.typeLabel, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = ts, maxLines = 1)
            }
        }
        val num = if (reveal) formatNumber(doc.number) else maskNumber(doc.number)
        if (num.isNotEmpty()) {
            Text(
                num, color = Color.White, fontSize = if (reveal) 26.sp else 20.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium, style = ts, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp, end = 56.dp)
            )
        }
        Icon(
            Icons.Default.PictureAsPdf, null, tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(24.dp)
        )
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, tint: Color? = null, onClick: () -> Unit) {
    val c = tint ?: LocalContentColor.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = c)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = c, maxLines = 1)
    }
}

@Composable
fun BackIcon(vm: VaultVM) {
    IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
}

@Composable
fun transparentBar() = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
