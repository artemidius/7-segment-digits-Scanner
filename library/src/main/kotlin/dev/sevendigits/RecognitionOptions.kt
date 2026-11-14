package dev.sevendigits

/**
 * Per-image recognition options.
 *
 * @property rotationDegrees Clockwise rotation needed to make the supplied bitmap upright:
 * 0, 90, 180, or 270. A decoded bitmap carries no EXIF orientation; apply EXIF when decoding
 * (including any mirroring), or provide the remaining rotation here. Do not rotate twice.
 * @property minimumCharacterConfidence Inclusive score threshold for retaining individual
 * characters, in 0..1. Raising it may remove digits from an otherwise detected reading.
 * Scores depend on the model and are not calibrated probabilities of correctness.
 */
public data class RecognitionOptions(
    public val rotationDegrees: Int = 0,
    public val minimumCharacterConfidence: Float = 0.25f,
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
