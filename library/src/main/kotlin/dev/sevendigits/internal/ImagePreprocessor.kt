package dev.sevendigits.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun prepareImage(bitmap: Bitmap, transform: ImageTransform): ByteBuffer {
    currentCoroutineContext().ensureActive()
    var softwareCopy: Bitmap? = null
    val resized = Bitmap.createBitmap(transform.inputWidth, transform.inputHeight, Bitmap.Config.ARGB_8888)
    try {
        val source = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false).also { softwareCopy = it }
                ?: throw IllegalArgumentException("Cannot read hardware bitmap.")
        } else bitmap
        val canvas = Canvas(resized)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val matrix = Matrix().apply { setValues(transform.drawingMatrix()) }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val size = floatTensorBytes(transform.inputWidth, transform.inputHeight, 3)
        val input = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        val row = IntArray(transform.inputWidth)
        for (y in 0 until transform.inputHeight) {
            currentCoroutineContext().ensureActive()
            resized.getPixels(row, 0, row.size, 0, y, row.size, 1)
            for (pixel in row) {
                input.putFloat(Color.red(pixel) / 255f)
                input.putFloat(Color.green(pixel) / 255f)
                input.putFloat(Color.blue(pixel) / 255f)
            }
        }
        return input.apply { rewind() }
    } finally {
        resized.recycle()
        softwareCopy?.recycle()
    }
}
