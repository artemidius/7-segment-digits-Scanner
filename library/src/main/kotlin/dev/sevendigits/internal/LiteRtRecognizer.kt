package dev.sevendigits.internal

import android.content.res.AssetManager
import android.graphics.Bitmap
import dev.sevendigits.RecognitionException
import dev.sevendigits.RecognitionOptions
import dev.sevendigits.RecognitionResult
import dev.sevendigits.SevenDigitsRecognizer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal suspend fun createLiteRtRecognizer(
    assets: AssetManager,
    modelPath: String,
    metadataPath: String,
): SevenDigitsRecognizer {
    require(modelPath.isNotBlank() && metadataPath.isNotBlank()) { "Asset paths must not be blank." }
    return withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            val metadata = assets.open(metadataPath).bufferedReader().use { JSONObject(it.readText()) }
            require(metadata.getString("box_units") == "input_pixels") { "Expected pixel-space model boxes." }
            val names = metadata.getJSONObject("names")
            require(names.length() > 0) { "The model must declare its class labels." }
            val labels = List(names.length()) { index ->
                require(names.has(index.toString())) { "Class IDs must be contiguous starting at zero." }
                when (val name = names.getString(index.toString())) {
                    "dot" -> '.'
                    "comma" -> ','
                    "colon" -> ':'
                    "minus" -> '-'
                    "plus" -> '+'
                    else -> name.singleOrNull()?.takeIf { it in "0123456789.,:-/+" }
                }
            }
            require(labels.any { it != null }) { "No supported numeric characters in model metadata." }
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = assets.open(modelPath).use { input ->
                val accumulator = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(chunk)
                    if (count < 0) break
                    digest.update(chunk, 0, count)
                    accumulator.write(chunk, 0, count)
                }
                accumulator.toByteArray()
            }
            require(bytes.isNotEmpty()) { "Model file is empty." }
            if (metadata.has("model_sha256")) {
                val expected = metadata.getString("model_sha256")
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(actual.equals(expected, ignoreCase = true)) { "Model checksum does not match metadata." }
            }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            model.put(bytes).rewind()
            val immutableModel = model.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
            currentCoroutineContext().ensureActive()
            openLiteRtSession(immutableModel.duplicate().order(ByteOrder.nativeOrder()), labels.size).use { }
            currentCoroutineContext().ensureActive()
            LiteRtRecognizer(immutableModel, labels)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            throw RecognitionException("Cannot load the recognition model and metadata from assets.", failure)
        } catch (failure: LinkageError) {
            currentCoroutineContext().ensureActive()
            throw RecognitionException("LiteRT native runtime is unavailable.", failure)
        }
    }
}

private class LiteRtRecognizer(
    private val model: ByteBuffer,
    private val labels: List<Char?>,
) : SevenDigitsRecognizer {
    override val supportedCharacters: Set<Char> = Collections.unmodifiableSet(labels.filterNotNull().toSet())

    override suspend fun recognizeVisual(bitmap: Bitmap, options: RecognitionOptions): RecognitionResult {
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "Input bitmap is recycled or empty." }
        return withInferenceSession(
            open = { openLiteRtSession(model.duplicate().order(ByteOrder.nativeOrder()), labels.size) },
        ) { session ->
            val transform = ImageTransform(bitmap.width, bitmap.height, options.rotationDegrees,
                session.inputWidth, session.inputHeight)
            val characters = ArrayList<dev.sevendigits.RecognizedCharacter>()
            val outputSize = floatTensorBytes(4 + labels.size, session.candidateCount)
            val output = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder())
            val values = FloatArray(outputSize / 4)
            for (window in detectionWindows(transform.uprightWidth, transform.uprightHeight,
                options.detectSmallCharacters && needsZoomPasses(transform))) {
                currentCoroutineContext().ensureActive()
                val view = ImageTransform(bitmap.width, bitmap.height, options.rotationDegrees,
                    session.inputWidth, session.inputHeight, window)
                val input = prepareImage(bitmap, view)
                output.rewind()
                session.run(input, output)
                currentCoroutineContext().ensureActive()
                output.rewind()
                output.asFloatBuffer().get(values)
                val found = decodeDetections(values, session.candidateCount, labels, view,
                    options.minimumCharacterConfidence)
                    .filter { window.containsComplete(it.boundingBox, transform.uprightWidth, transform.uprightHeight) }
                // Preserve full-frame readings. Zoom passes only add non-overlapping detections.
                for (candidate in found.sortedByDescending { it.confidence }) {
                    if (characters.none { sameGlyph(it.boundingBox, candidate.boundingBox) }) characters.add(candidate)
                }
            }
            val lines = groupDetections(characters).map { row ->
                row.map { field ->
                    dev.sevendigits.TextRegion(field.map { it.copy(boundingBox = transform.toOriginal(it.boundingBox)) })
                }
            }
            RecognitionResult(bitmap.width, bitmap.height, lines)
        }
    }
}
