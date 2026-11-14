package dev.sevendigits

import android.graphics.Bitmap

/**
 * Recognizes numeric text anywhere in a whole device photo.
 *
 * This interface defines the contract for the forthcoming recognition implementation.
 * Implementations must run expensive work off the calling thread, including when called
 * from the main thread. Calls are independent and may run concurrently.
 *
 * Each call owns its inference resources and child coroutines. Implementations must release
 * those resources in finally blocks on success, failure, or cancellation, and must not leave
 * detached work running after the call finishes. There is no manual resource lifecycle.
 * The caller owns each bitmap; implementations must not modify or recycle it.
 */
public interface SevenDigitsRecognizer {
    /**
     * Immutable set of characters the model can recognize. This describes the model's
     * vocabulary, not a guarantee that every occurrence of a character will be detected.
     */
    public val supportedCharacters: Set<Char>

    /**
     * Recognizes a whole image as rows of strings, without requiring the client to handle boxes
     * or scores. Each inner list holds the fields on one row in upright reading order.
     *
     * Uses the same options, bitmap ownership, cancellation, and failure contract as
     * [recognizeVisual]. The default implementation performs recognition once and projects
     * its existing row grouping into text. To obtain both views, call [recognizeVisual] once
     * and then [RecognitionResult.toLines].
     */
    public suspend fun recognizeText(
        bitmap: Bitmap,
        options: RecognitionOptions = RecognitionOptions(),
    ): Lines = recognizeVisual(bitmap, options).toLines()

    /**
     * Processes [bitmap] as a whole image and returns visual details, applying [options] internally.
     *
     * Do not modify or recycle [bitmap] until this call completes, including cancellation.
     * A successful result may contain partial text or no regions at all. An empty result means
     * no characters met the detection criteria; it does not prove that the photo contains none.
     *
     * Coordinates always refer to the supplied bitmap, before any processing rotation or resize.
     * [RecognitionResult.lines] groups fields into upright rows, top to bottom and left to right.
     * No medical interpretation, numeric conversion, or inferred missing characters is applied.
     *
     * Implementations must propagate coroutine cancellation, release per-call resources, and
     * never turn cancellation into [RecognitionException] or an empty result. The recognizer
     * remains usable after a cancelled call; a native invocation may need to finish first.
     *
     * @throws IllegalArgumentException if [bitmap] has been recycled or cannot be read.
     * @throws RecognitionException if recognition cannot complete because of an operational failure.
     */
    public suspend fun recognizeVisual(
        bitmap: Bitmap,
        options: RecognitionOptions = RecognitionOptions(),
    ): RecognitionResult
}
