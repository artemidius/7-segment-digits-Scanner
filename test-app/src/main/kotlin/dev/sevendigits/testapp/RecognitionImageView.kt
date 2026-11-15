package dev.sevendigits.testapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import dev.sevendigits.BoundingBox
import dev.sevendigits.RecognitionResult
import kotlin.math.min

class RecognitionImageView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var bitmap: Bitmap? = null
    private var result: RecognitionResult? = null
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawingRect = RectF()
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    var showBoxes: Boolean = true
        set(value) { field = value; invalidate() }

    fun setImage(value: Bitmap?) {
        bitmap = value
        result = null
        invalidate()
    }

    fun setResult(value: RecognitionResult) {
        result = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        val scale = min(width.toFloat() / image.width, height.toFloat() / image.height)
        val left = (width - image.width * scale) / 2
        val top = (height - image.height * scale) / 2
        drawingRect.set(left, top, left + image.width * scale, top + image.height * scale)
        canvas.drawBitmap(image, null, drawingRect, imagePaint)
        if (!showBoxes) return
        fun rect(box: BoundingBox): RectF {
            drawingRect.set(left + box.left * scale, top + box.top * scale,
                left + box.right * scale, top + box.bottom * scale)
            return drawingRect
        }
        val density = resources.displayMetrics.density
        result?.regions?.forEach { region ->
            boxPaint.color = Color.rgb(215, 108, 0)
            boxPaint.strokeWidth = density
            region.characters.forEach { canvas.drawRect(rect(it.boundingBox), boxPaint) }
            boxPaint.color = Color.rgb(36, 87, 197)
            boxPaint.strokeWidth = 2 * density
            canvas.drawRect(rect(region.boundingBox), boxPaint)
            // Text is listed below the image; floating labels obscure dense displays.
        }
    }
}
