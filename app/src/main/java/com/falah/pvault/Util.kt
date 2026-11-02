package com.falah.pvault

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File

const val ACT_SCAN = "com.falah.pvault.SCAN"
const val ACT_ADD = "com.falah.pvault.ADD"
const val ACT_OPEN = "com.falah.pvault.OPEN"

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun exportUri(ctx: Context, src: File, title: String): Uri {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val clean = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "document" }
    val out = File(dir, "$clean.pdf")
    src.copyTo(out, overwrite = true)
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", out)
}

fun openExternal(ctx: Context, src: File, title: String) {
    try {
        val uri = exportUri(ctx, src, title)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("", uri)
        ctx.startActivity(Intent.createChooser(i, "Open with"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app found to open PDFs", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn't open the file", Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(ctx: Context, src: File, title: String) {
    try {
        val uri = exportUri(ctx, src, title)
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("", uri)
        ctx.startActivity(Intent.createChooser(i, "Share $title"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn't share the file", Toast.LENGTH_SHORT).show()
    }
}

private fun openIntent(ctx: Context, id: String) =
    Intent(ctx, MainActivity::class.java).setAction(ACT_OPEN).putExtra("id", id)

/** Long-press app-icon shortcuts for the first two cards. */
fun publishShortcuts(ctx: Context, docs: List<Doc>) {
    try {
        val list = docs.take(2).map { d ->
            ShortcutInfoCompat.Builder(ctx, "doc_" + d.id)
                .setShortLabel(d.title.take(24))
                .setLongLabel(d.title)
                .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_sc_card))
                .setIntent(openIntent(ctx, d.id))
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
    } catch (e: Exception) { }
}

/** Puts a coloured shortcut for one card on the home screen. */
fun pinShortcut(ctx: Context, d: Doc) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
        Toast.makeText(ctx, "Your launcher doesn't support pinned shortcuts", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            docColors(d).map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP
        )
        Canvas(bmp).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 44f, 44f, p)
        val info = ShortcutInfoCompat.Builder(ctx, "pin_" + d.id)
            .setShortLabel(d.title.take(24))
            .setLongLabel(d.title)
            .setIcon(IconCompat.createWithBitmap(bmp))
            .setIntent(openIntent(ctx, d.id))
            .build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn't create the shortcut", Toast.LENGTH_SHORT).show()
    }
}
