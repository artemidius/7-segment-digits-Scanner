package dev.sevendigits

import java.util.Collections

/**
 * A spatially grouped numeric field, such as `120`, `08`, `11.11.2026`, or `11:05`.
 *
 * [characters] must be nonempty and already in reading order in the upright image.
 * This constructor preserves that order; it does not perform detection, sorting, or grouping.
 * Text, bounds, and score are derived from the same character snapshot so they cannot disagree.
 * A region may still be incomplete if a character was missed by the recognizer.
 */
public class TextRegion(characters: List<RecognizedCharacter>) {
    /** Immutable snapshot in reading order, including any recognized punctuation. */
    public val characters: List<RecognizedCharacter> = Collections.unmodifiableList(ArrayList(characters))

    init {
        require(this.characters.isNotEmpty()) { "A text region must contain at least one character." }
    }

    /** Raw detected text. Leading zeros, separators, and signs are preserved. */
    public val text: String = this.characters.joinToString(separator = "") { it.value.toString() }

    /** Smallest axis-aligned rectangle enclosing every character, in original-image coordinates. */
    public val boundingBox: BoundingBox = BoundingBox(
        left = this.characters.minOf { it.boundingBox.left },
        top = this.characters.minOf { it.boundingBox.top },
        right = this.characters.maxOf { it.boundingBox.right },
        bottom = this.characters.maxOf { it.boundingBox.bottom },
    )

    /** Lowest character score in this region; does not establish completeness or field accuracy. */
    public val minimumCharacterConfidence: Float = this.characters.minOf { it.confidence }
}
