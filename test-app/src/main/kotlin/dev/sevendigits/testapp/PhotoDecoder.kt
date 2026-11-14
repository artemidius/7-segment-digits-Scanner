package dev.sevendigits.testapp

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Decode a bounded preview and apply EXIF rotation/mirroring before either API sees it. */
internal suspend fun decodePhoto(resolver: ContentResolver, uri: Uri): Bitmap {
    fun stream() = resolver.openInputStream(uri) ?: error("The selected image could not be opened.")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    stream().use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a supported image." }
    currentCoroutineContext().ensureActive()
    var sample = 1
    while ((maxOf(bounds.outWidth, bounds.outHeight).toLong() + sample - 1) / sample > 2048) sample *= 2
    val orientation = stream().use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
    val bitmap = stream().use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } ?: error("The selected image could not be decoded.")
    try {
        currentCoroutineContext().ensureActive()
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (upright !== bitmap) bitmap.recycle()
        return upright
    } catch (failure: Throwable) {
        bitmap.recycle()
        throw failure
    }
}
