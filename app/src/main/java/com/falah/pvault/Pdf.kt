package com.falah.pvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File

sealed interface LoadResult {
    class Ok : LoadResult
    class NeedPw(val wrong: Boolean) : LoadResult
    class Fail(val msg: String) : LoadResult
}

/** Renders every page; throws SecurityException when the PDF needs a password. */
fun renderPdf(file: File, widthPx: Int, onPages: (List<Bitmap>) -> Unit) {
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    pfd.use {
        PdfRenderer(it).use { r ->
            val out = mutableListOf<Bitmap>()
            for (i in 0 until r.pageCount) {
                r.openPage(i).use { p ->
                    val scale = widthPx * 1.5f / p.width
                    val bmp = Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.add(bmp)
                }
                onPages(out.toList())
            }
        }
    }
}

/** 0 = ok, 1 = wrong password, 2 = error */
fun decryptPdf(ctx: Context, src: File, pw: String, out: File): Int {
    PDFBoxResourceLoader.init(ctx.applicationContext)
    return try {
        PDDocument.load(src, pw).use { d ->
            d.setAllSecurityToBeRemoved(true)
            d.save(out)
        }
        0
    } catch (e: InvalidPasswordException) {
        1
    } catch (e: Throwable) {
        2
    }
}

fun loadPdf(ctx: Context, id: String, src: File, widthPx: Int, pw: String?, onPages: (List<Bitmap>) -> Unit): LoadResult {
    val failMsg = "Couldn't display this PDF here. Use “Open in” to view it in another app."
    try {
        renderPdf(src, widthPx, onPages)
        return LoadResult.Ok()
    } catch (e: SecurityException) {
        if (pw.isNullOrEmpty()) return LoadResult.NeedPw(false)
        val tmp = File(ctx.cacheDir, "dec_$id.pdf")
        val code = decryptPdf(ctx, src, pw, tmp)
        if (code == 1) return LoadResult.NeedPw(true)
        if (code != 0) return LoadResult.Fail(failMsg)
        return try {
            renderPdf(tmp, widthPx, onPages)
            LoadResult.Ok()
        } catch (e2: Exception) {
            LoadResult.Fail(failMsg)
        } finally {
            tmp.delete()
        }
    } catch (e: Exception) {
        return LoadResult.Fail(failMsg)
    }
}

fun imageToPdf(ctx: Context, uri: Uri, out: File) {
    val cr = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
    val dec = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, dec) }
        ?: throw IllegalStateException("Not an image")
    val orientation = try {
        cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } ?: 1
    } catch (e: Exception) { 1 }
    val deg = when (orientation) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
    if (deg != 0f) {
        val m = Matrix().apply { postRotate(deg) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    val pageW = 595
    val pageH = (595f * bmp.height / bmp.width).toInt().coerceAtLeast(1)
    val pdf = PdfDocument()
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
    page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
    pdf.finishPage(page)
    out.outputStream().use { pdf.writeTo(it) }
    pdf.close()
}

/**
 * Merges PDFs (in the given order) into [out]. Every page is placed on an A4 sheet,
 * scaled to fit and centred, so all pages end up the same size. Returns null on success, or an error message.
 */
fun mergePdfs(ctx: Context, items: List<Triple<File, String, String>>, out: File): String? {
    PDFBoxResourceLoader.init(ctx.applicationContext)
    val opened = mutableListOf<PDDocument>()
    val dest = PDDocument()
    var current = ""
    try {
        val layer = LayerUtility(dest)
        val a4 = PDRectangle.A4
        for ((file, pw, title) in items) {
            current = title
            val d = PDDocument.load(file, pw)
            d.setAllSecurityToBeRemoved(true)
            opened.add(d)
            for (page in d.pages) {
                val form = layer.importPageAsForm(d, page)
                // size of the page as it will actually appear (after the form's own rotation matrix)
                val bb = form.bBox
                val m = form.matrix
                val xs = FloatArray(4)
                val ys = FloatArray(4)
                val cx = floatArrayOf(bb.lowerLeftX, bb.upperRightX, bb.lowerLeftX, bb.upperRightX)
                val cy = floatArrayOf(bb.lowerLeftY, bb.lowerLeftY, bb.upperRightY, bb.upperRightY)
                for (k in 0 until 4) {
                    xs[k] = m.getValue(0, 0) * cx[k] + m.getValue(1, 0) * cy[k] + m.getValue(2, 0)
                    ys[k] = m.getValue(0, 1) * cx[k] + m.getValue(1, 1) * cy[k] + m.getValue(2, 1)
                }
                val minX = xs.min(); val maxX = xs.max()
                val minY = ys.min(); val maxY = ys.max()
                val w = (maxX - minX).coerceAtLeast(1f)
                val h = (maxY - minY).coerceAtLeast(1f)
                val s = minOf(a4.width / w, a4.height / h)
                val tx = (a4.width - w * s) / 2f - minX * s
                val ty = (a4.height - h * s) / 2f - minY * s
                val np = PDPage(a4)
                dest.addPage(np)
                PDPageContentStream(dest, np).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(PdfMatrix(s, 0f, 0f, s, tx, ty))
                    cs.drawForm(form)
                    cs.restoreGraphicsState()
                }
            }
        }
        dest.save(out)
        return null
    } catch (e: InvalidPasswordException) {
        return "“$current” is password-protected. Open it once and enter its password, then try again."
    } catch (e: Throwable) {
        return "Couldn't merge these documents."
    } finally {
        opened.forEach { try { it.close() } catch (e: Exception) { } }
        try { dest.close() } catch (e: Exception) { }
    }
}
