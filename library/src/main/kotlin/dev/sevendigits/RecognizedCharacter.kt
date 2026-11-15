package dev.sevendigits

/**
 * One detected digit, numeric separator, or sign.
 *
 * @property value Detected character: a digit 0–9 or one of `.` `,` `:` `-` `/` `+`.
 * An individual model may support only a subset; consult [SevenDigitsRecognizer.supportedCharacters].
 * @property boundingBox Position in the original bitmap.
 * @property confidence Model score in 0..1, not a calibrated probability of correctness.
 */
public data class RecognizedCharacter(
    public val value: Char,
    public val boundingBox: BoundingBox,
    public val confidence: Float,
) {
    init {
        require(value in "0123456789.,:-/+") { "Unsupported numeric character: $value" }
        require(confidence.isFinite() && confidence in 0f..1f) {
            "confidence must be finite and in 0..1."
        }
    }
}
