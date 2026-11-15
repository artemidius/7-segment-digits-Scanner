package dev.sevendigits.internal

import dev.sevendigits.BoundingBox

internal data class DetectionWindow(val left: Int, val top: Int, val width: Int, val height: Int) {
    // Reject truncated detections at internal crop edges. Overlap supplies complete alternatives.
    fun containsComplete(box: BoundingBox, imageWidth: Int, imageHeight: Int): Boolean =
        (left == 0 || box.left > left + 2) && (top == 0 || box.top > top + 2) &&
            (left + width == imageWidth || box.right < left + width - 2) &&
            (top + height == imageHeight || box.bottom < top + height - 2)
}

/** Full frame followed by nine overlapping half-size views; no device-specific screen layout. */
internal fun detectionWindows(width: Int, height: Int, includeSmall: Boolean): List<DetectionWindow> {
    val full = DetectionWindow(0, 0, width, height)
    if (!includeSmall || width < 64 || height < 64) return listOf(full)
    val w = (width + 1) / 2
    val h = (height + 1) / 2
    return listOf(full) + listOf(0, (height - h) / 2, height - h).flatMap { y ->
        listOf(0, (width - w) / 2, width - w).map { x -> DetectionWindow(x, y, w, h) }
    }
}

/** Zoom only when full-frame resizing loses more than half of the source resolution. */
internal fun needsZoomPasses(transform: ImageTransform): Boolean =
    transform.scaleX < .5f || transform.scaleY < .5f
