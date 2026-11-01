package com.falah.pvault

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
}

/** Copies the stored PDF to cache under a readable name and returns a shareable content Uri. */
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
