package dev.sevendigits

/**
 * One detected text character. Letters, digits, units, and punctuation are preserved.
 *
 * @property value Detected UTF-16 character. A multi-unit OCR symbol shares its box across its characters.
 * @property boundingBox Position in the original bitmap.
 * @property confidence Model score in 0..1, not a calibrated probability of correctness.
 * @property source Engine that supplied the character. Scores are not comparable across engines.
 */
public data class RecognizedCharacter(
    public val value: Char,
    public val boundingBox: BoundingBox,
    public val confidence: Float,
    public val source: RecognitionSource = RecognitionSource.SEVEN_SEGMENT,
) {
    init {
        require(!value.isWhitespace() && !value.isISOControl()) { "Expected a visible text character." }
        require(confidence.isFinite() && confidence in 0f..1f) {
            "confidence must be finite and in 0..1."
        }
    }
}
