package com.glassfinder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val MAX_SIDE = 2400

/** Copies the picked/captured image to [dest] as an upright JPEG, downscaled so OCR stays fast. */
fun importImage(context: Context, uri: Uri, dest: File): Boolean {
    return try {
        val resolver = context.contentResolver

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        val upright = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

        dest.parentFile?.mkdirs()
        dest.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Exception) {
        false
    }
}

/** Small-memory bitmap for display; null if the file is missing or unreadable. */
fun loadBitmap(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Runs ML Kit text recognition on the image. Fully on-device, no internet needed. */
suspend fun recognizeText(file: File): String = suspendCancellableCoroutine { cont ->
    val bmp = BitmapFactory.decodeFile(file.absolutePath)
    if (bmp == null) {
        cont.resume("")
    } else {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resume("") }
    }
}

/** Turns OCR output (or hand-typed text) into one model name per entry. */
fun parseModels(text: String): List<String> =
    text.split('\n', ',', ';', '/', '\\', '|', '•', '·')
        .map { it.trim().trim('-', '.', ':', '*', '(', ')').replace(Regex("\\s+"), " ") }
        .filter { it.length >= 2 && it.any { c -> c.isLetterOrDigit() } }
        .distinct()
