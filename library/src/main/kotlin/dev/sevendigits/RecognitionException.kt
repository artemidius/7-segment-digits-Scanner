package dev.sevendigits

/**
 * Operational recognition failure, such as unavailable model resources or failed inference.
 * Invalid caller arguments use [IllegalArgumentException] instead.
 * Coroutine cancellation must propagate unchanged and must never be wrapped in this exception.
 *
 * @param message Diagnostic description; not intended as user-facing or machine-parsed text.
 * @param cause Underlying failure, when available.
 */
public class RecognitionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
