package dev.sevendigits

import java.util.Collections

/**
 * Successful recognition output for one whole input bitmap.
 *
 * No detections is represented by an empty [regions] list. Processing failures are exceptions,
 * never an empty success. This constructor checks dimensions and bounds but does not sort regions.
 *
 * @property imageWidth Width of the original bitmap, before processing rotation or resize.
 * @property imageHeight Height of the original bitmap, before processing rotation or resize.
 */
public class RecognitionResult(
    public val imageWidth: Int,
    public val imageHeight: Int,
    regions: List<TextRegion>,
) {
    /** Immutable snapshot in upright reading order: rows top to bottom, fields left to right. */
    public val regions: List<TextRegion> = Collections.unmodifiableList(ArrayList(regions))

    init {
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive." }
        require(this.regions.all { it.boundingBox.right <= imageWidth && it.boundingBox.bottom <= imageHeight }) {
            "Every region must fit within the original image dimensions."
        }
    }
}
