package dev.sevendigits

import java.util.Collections

/**
 * Detailed visual recognition output for one whole input bitmap.
 *
 * [lines] contains the detected row grouping. No detections is an empty outer list; each
 * detected row must contain at least one field. Processing failures are exceptions, never
 * an empty success. This constructor checks dimensions and bounds but does not group or sort.
 *
 * @property imageWidth Width of the original bitmap, before processing rotation or resize.
 * @property imageHeight Height of the original bitmap, before processing rotation or resize.
 */
public class RecognitionResult(
    public val imageWidth: Int,
    public val imageHeight: Int,
    lines: List<List<TextRegion>>,
) {
    /** Immutable rows, top to bottom, containing immutable lists of fields, left to right. */
    public val lines: List<List<TextRegion>> = Collections.unmodifiableList(
        lines.map { Collections.unmodifiableList(ArrayList(it)) },
    )

    /** All fields flattened in upright reading order, convenient for drawing overlays. */
    public val regions: List<TextRegion> = Collections.unmodifiableList(this.lines.flatten())

    init {
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive." }
        require(this.lines.all { it.isNotEmpty() }) { "Detected rows must contain at least one field." }
        require(this.regions.all { it.boundingBox.right <= imageWidth && it.boundingBox.bottom <= imageHeight }) {
            "Every region must fit within the original image dimensions."
        }
    }

    /**
     * Returns just the strings with the existing row/field grouping, without running inference
     * again. Both list levels are immutable. Coordinates and scores are omitted.
     */
    public fun toLines(): Lines = Collections.unmodifiableList(
        lines.map { row -> Collections.unmodifiableList(row.map { it.text }) },
    )
}
