package dev.sevendigits

/**
 * Per-image recognition options.
 *
 * @property rotationDegrees Clockwise rotation needed to make the supplied bitmap upright:
 * 0, 90, 180, or 270. A decoded bitmap carries no EXIF orientation; apply EXIF when decoding
 * (including any mirroring), or provide the remaining rotation here. Do not rotate twice.
 * @property minimumCharacterConfidence Inclusive score threshold for retaining individual
 * seven-segment detector characters, in 0..1. Printed OCR words retain all their symbols
 * to avoid silently deleting low-scoring characters. Raising the threshold may remove
 * digits from an otherwise detected reading.
 * Scores depend on the model and are not calibrated probabilities of correctness.
 * @property recognizePrintedText Include bundled Latin text OCR for letters, units, and punctuation.
 * @property detectSmallCharacters Run overlapping zoomed detector passes and a backlight-neutral OCR pass.
 * Zoom passes run when the full-frame resize loses more than half the source resolution.
 * This improves small-digit recall at the cost of additional inference time; it does not
 * guarantee completeness. Disable for a faster single detector pass.
 */
public data class RecognitionOptions(
    public val rotationDegrees: Int = 0,
    public val minimumCharacterConfidence: Float = 0.25f,
    public val recognizePrintedText: Boolean = true,
    public val detectSmallCharacters: Boolean = true,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotationDegrees must be 0, 90, 180, or 270."
        }
        require(minimumCharacterConfidence.isFinite() && minimumCharacterConfidence in 0f..1f) {
            "minimumCharacterConfidence must be finite and in 0..1."
        }
    }
}
