package dev.sevendigits

/**
 * Immutable, axis-aligned rectangle in the original input bitmap's pixel coordinates.
 *
 * The origin is its top-left corner; x increases rightward and y downward.
 * Coordinates are finite and nonnegative, with positive area. Right and bottom are exclusive.
 * Boxes in a [RecognitionResult] are clipped to that result's original image dimensions.
 *
 * @property left Left edge, inclusive.
 * @property top Top edge, inclusive.
 * @property right Right edge, exclusive.
 * @property bottom Bottom edge, exclusive.
 */
public data class BoundingBox(
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "Bounding box coordinates must be finite."
        }
        require(left >= 0f && top >= 0f && right > left && bottom > top) {
            "Bounding boxes must have nonnegative coordinates and positive area."
        }
    }

    /** Width in original-image pixels. */
    public val width: Float get() = right - left

    /** Height in original-image pixels. */
    public val height: Float get() = bottom - top
}
