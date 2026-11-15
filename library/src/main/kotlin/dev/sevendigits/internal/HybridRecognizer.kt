package dev.sevendigits.internal

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.sevendigits.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class HybridRecognizer(private val segment: SevenDigitsRecognizer) : SevenDigitsRecognizer {
    override val supportedCharacters: Set<Char> get() = segment.supportedCharacters

    override suspend fun recognizeVisual(bitmap: Bitmap, options: RecognitionOptions): RecognitionResult {
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "Input bitmap is recycled or empty." }
        if (!options.recognizePrintedText) return segment.recognizeVisual(bitmap, options)
        return withContext(Dispatchers.Default) {
            var uprightCopy: Bitmap? = null
            var softwareCopy: Bitmap? = null
            try {
                currentCoroutineContext().ensureActive()
                val readable = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
                    requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)).also { softwareCopy = it }
                } else bitmap
                val upright = if (options.rotationDegrees != 0) {
                    Bitmap.createBitmap(readable, 0, 0, readable.width, readable.height,
                        Matrix().apply { postRotate(options.rotationDegrees.toFloat()) }, false).also { uprightCopy = it }
                } else readable
                val transform = ImageTransform(bitmap.width, bitmap.height, options.rotationDegrees, 640, 640)
                val uprightOptions = options.copy(rotationDegrees = 0)
                val general = recognizeGeneralText(upright, uprightOptions, transform)
                val numeric = segment.recognizeVisual(upright, uprightOptions).regions.flatMap { it.characters }
                val rows = mergeText(numeric, general)
                currentCoroutineContext().ensureActive()
                RecognitionResult(bitmap.width, bitmap.height, rows.map { row -> row.map { region ->
                    TextRegion(region.characters.map { it.copy(boundingBox = transform.toOriginal(it.boundingBox)) })
                } })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (failure is RecognitionException) throw failure
                throw RecognitionException("Text recognition failed.", failure)
            } catch (failure: LinkageError) {
                currentCoroutineContext().ensureActive()
                throw RecognitionException("Text recognition runtime is unavailable.", failure)
            } finally {
                uprightCopy?.recycle()
                softwareCopy?.recycle()
            }
        }
    }
}

private suspend fun recognizeGeneralText(
    bitmap: Bitmap,
    options: RecognitionOptions,
    transform: ImageTransform,
): List<TextRegion> {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    var softwareCopy: Bitmap? = null
    try {
        val source = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
            requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)).also { softwareCopy = it }
        } else bitmap
        currentCoroutineContext().ensureActive()
        val text = recognizer.process(InputImage.fromBitmap(source, options.rotationDegrees)).awaitCompletion()
        currentCoroutineContext().ensureActive()
        val regions = text.regions(transform).toMutableList()
        if (options.detectSmallCharacters) {
            // Colored backlights can hide low-contrast strokes in ordinary luminance conversion.
            // Max-channel grayscale preserves the bright channel on red, green, and blue displays.
            val enhanced = maxChannelImage(source)
            try {
                currentCoroutineContext().ensureActive()
                val extra = recognizer.process(InputImage.fromBitmap(enhanced, options.rotationDegrees)).awaitCompletion()
                currentCoroutineContext().ensureActive()
                addUncoveredText(regions, extra.regions(transform))
            } finally { enhanced.recycle() }
        }
        return regions
    } finally {
        recognizer.close()
        softwareCopy?.recycle()
    }
}

private fun Text.regions(transform: ImageTransform): List<TextRegion> =
    textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
        val characters = element.symbols.flatMap { it.characters(transform) }
        characters.takeIf { it.isNotEmpty() }?.let { TextRegion(it) }
    }

private suspend fun maxChannelImage(source: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    try {
        val row = IntArray(source.width)
        for (y in 0 until source.height) {
            currentCoroutineContext().ensureActive()
            source.getPixels(row, 0, row.size, 0, y, row.size, 1)
            for (x in row.indices) {
                val v = maxOf(Color.red(row[x]), Color.green(row[x]), Color.blue(row[x]))
                row[x] = Color.rgb(v, v, v)
            }
            result.setPixels(row, 0, row.size, 0, y, row.size, 1)
        }
        return result
    } catch (failure: Throwable) {
        result.recycle()
        throw failure
    }
}

private fun Text.Symbol.characters(transform: ImageTransform): List<RecognizedCharacter> {
    val bounds = boundingBox ?: return emptyList()
    val left = bounds.left.toFloat().coerceIn(0f, transform.uprightWidth.toFloat())
    val top = bounds.top.toFloat().coerceIn(0f, transform.uprightHeight.toFloat())
    val right = bounds.right.toFloat().coerceIn(0f, transform.uprightWidth.toFloat())
    val bottom = bounds.bottom.toFloat().coerceIn(0f, transform.uprightHeight.toFloat())
    if (right <= left || bottom <= top || !confidence.isFinite() || confidence !in 0f..1f) return emptyList()
    val box = BoundingBox(left, top, right, bottom)
    return text.filterNot { it.isWhitespace() || it.isISOControl() }.map {
        RecognizedCharacter(it, box, confidence, RecognitionSource.GENERAL_TEXT)
    }
}

/** ML Kit tasks cannot be cancelled. Wait for completion before closing or releasing the bitmap. */
internal suspend fun <T> Task<T>.awaitCompletion(): T = suspendCoroutine { continuation ->
    addOnCompleteListener(Executor { it.run() }) { completed ->
        when {
            completed.isCanceled -> continuation.resumeWithException(CancellationException("OCR task cancelled."))
            completed.isSuccessful -> continuation.resume(completed.result)
            else -> continuation.resumeWithException(completed.exception ?: IllegalStateException("OCR task failed."))
        }
    }
}
