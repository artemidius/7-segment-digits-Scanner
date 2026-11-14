package dev.sevendigits.internal

import dev.sevendigits.BoundingBox
import kotlin.math.min
import kotlin.math.round

/** Pixel-edge transforms; grouping uses upright coordinates until the final result is built. */
internal class ImageTransform(
    val originalWidth: Int,
    val originalHeight: Int,
    val rotation: Int,
    val inputWidth: Int,
    val inputHeight: Int,
) {
    val uprightWidth = if (rotation == 90 || rotation == 270) originalHeight else originalWidth
    val uprightHeight = if (rotation == 90 || rotation == 270) originalWidth else originalHeight
    private val scale = min(inputWidth.toDouble() / uprightWidth, inputHeight.toDouble() / uprightHeight)
    val resizedWidth = round(uprightWidth * scale).toInt().coerceIn(1, inputWidth)
    val resizedHeight = round(uprightHeight * scale).toInt().coerceIn(1, inputHeight)
    val offsetX = (inputWidth - resizedWidth) / 2
    val offsetY = (inputHeight - resizedHeight) / 2
    val scaleX = resizedWidth.toFloat() / uprightWidth
    val scaleY = resizedHeight.toFloat() / uprightHeight

    fun fromModel(x: Float, y: Float, width: Float, height: Float): BoundingBox? {
        val left = ((x - width / 2 - offsetX) / scaleX).coerceIn(0f, uprightWidth.toFloat())
        val top = ((y - height / 2 - offsetY) / scaleY).coerceIn(0f, uprightHeight.toFloat())
        val right = ((x + width / 2 - offsetX) / scaleX).coerceIn(0f, uprightWidth.toFloat())
        val bottom = ((y + height / 2 - offsetY) / scaleY).coerceIn(0f, uprightHeight.toFloat())
        return if (right > left && bottom > top) BoundingBox(left, top, right, bottom) else null
    }

    fun toOriginal(box: BoundingBox): BoundingBox = when (rotation) {
        90 -> BoundingBox(box.top, originalHeight - box.right, box.bottom, originalHeight - box.left)
        180 -> BoundingBox(originalWidth - box.right, originalHeight - box.bottom,
            originalWidth - box.left, originalHeight - box.top)
        270 -> BoundingBox(originalWidth - box.bottom, box.left, originalWidth - box.top, box.right)
        else -> box
    }

    fun drawingMatrix(): FloatArray = when (rotation) {
        90 -> floatArrayOf(0f, -scaleX, offsetX + originalHeight * scaleX, scaleY, 0f, offsetY.toFloat(), 0f, 0f, 1f)
        180 -> floatArrayOf(-scaleX, 0f, offsetX + originalWidth * scaleX,
            0f, -scaleY, offsetY + originalHeight * scaleY, 0f, 0f, 1f)
        270 -> floatArrayOf(0f, scaleX, offsetX.toFloat(), -scaleY, 0f,
            offsetY + originalWidth * scaleY, 0f, 0f, 1f)
        else -> floatArrayOf(scaleX, 0f, offsetX.toFloat(), 0f, scaleY, offsetY.toFloat(), 0f, 0f, 1f)
    }
}
