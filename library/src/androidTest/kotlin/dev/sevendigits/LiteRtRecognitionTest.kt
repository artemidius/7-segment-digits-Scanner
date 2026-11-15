package dev.sevendigits

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sevendigits.internal.ImageTransform
import dev.sevendigits.internal.prepareImage
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiteRtRecognitionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    private val expected = listOf(listOf("126"), listOf("81"), listOf("81"))

    @Test
    fun wholePhotosProduceMatchingTextAndVisualResults() = runBlocking {
        val recognizer = SevenDigitsRecognizer.create(context)
        assertEquals(('0'..'9').toSet(), recognizer.supportedCharacters)
        for (name in listOf("126-81-81-a.jpg", "126-81-81-b.jpg")) {
            val bitmap = photo(name)
            try {
                val before = bitmap.getPixel(100, 100)
                val visual = recognizer.recognizeVisual(bitmap)
                assertEquals(name, expected, visual.toLines())
                assertEquals(visual.toLines(), recognizer.recognizeText(bitmap))
                assertEquals(7, visual.regions.sumOf { it.characters.size })
                assertEquals(bitmap.width, visual.imageWidth)
                assertEquals(bitmap.height, visual.imageHeight)
                assertFalse(bitmap.isRecycled)
                assertEquals(before, bitmap.getPixel(100, 100))
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun rotatedPhotosKeepReadingOrderAndMapBoxesToInput() = runBlocking {
        val recognizer = SevenDigitsRecognizer.create(context)
        val upright = photo("126-81-81-a.jpg")
        try {
            val baseline = recognizer.recognizeVisual(upright)
            for (rotation in listOf(90, 180, 270)) {
                val bitmap = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height,
                    Matrix().apply { postRotate(-rotation.toFloat()) }, false)
                try {
                    val visual = recognizer.recognizeVisual(bitmap, RecognitionOptions(rotationDegrees = rotation))
                    assertEquals("Rotation $rotation", expected, visual.toLines())
                    val transform = ImageTransform(bitmap.width, bitmap.height, rotation, 640, 640)
                    baseline.regions.zip(visual.regions).forEach { (base, actual) ->
                        val expectedBox = transform.toOriginal(base.boundingBox)
                        assertEquals(expectedBox.left, actual.boundingBox.left, 2f)
                        assertEquals(expectedBox.top, actual.boundingBox.top, 2f)
                        assertEquals(expectedBox.right, actual.boundingBox.right, 2f)
                        assertEquals(expectedBox.bottom, actual.boundingBox.bottom, 2f)
                    }
                } finally { bitmap.recycle() }
            }
        } finally { upright.recycle() }
    }

    @Test
    fun reusedRecognizerSupportsConcurrentCallsAndCancellation() = runBlocking {
        val recognizer = SevenDigitsRecognizer.create(context)
        val bitmap = photo("126-81-81-a.jpg")
        try {
            val cancelled = launch { recognizer.recognizeText(bitmap) }
            yield()
            cancelled.cancelAndJoin()
            val first = async { recognizer.recognizeText(bitmap) }
            val second = async { recognizer.recognizeVisual(bitmap).toLines() }
            assertEquals(expected, first.await())
            assertEquals(expected, second.await())
            assertFalse(bitmap.isRecycled)
        } finally { bitmap.recycle() }
    }

    @Test
    fun invalidAssetsAreErrorsRatherThanEmptyDetections() = runBlocking {
        for (metadata in listOf("missing.json", "fixtures/wrong-hash.json", "fixtures/wrong-units.json",
            "fixtures/wrong-label-count.json")) {
            try {
                SevenDigitsRecognizer.create(context, metadataAssetPath = metadata)
                fail("Expected failure for $metadata")
            } catch (expected: RecognitionException) { assertNotNull(expected.cause) }
        }
        try {
            SevenDigitsRecognizer.create(context, "fixtures/corrupt.tflite", "fixtures/unverified.json")
            fail("Expected corrupt-model failure")
        } catch (expected: RecognitionException) { assertNotNull(expected.cause) }
    }

    @Test
    fun blankAndRecycledImagesHaveDistinctOutcomes() = runBlocking {
        val recognizer = SevenDigitsRecognizer.create(context)
        val blank = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        assertTrue(recognizer.recognizeText(blank).isEmpty())
        blank.recycle()
        try { recognizer.recognizeText(blank); fail("Expected invalid input") }
        catch (_: IllegalArgumentException) { }
    }

    @Test
    fun preprocessingUsesRgbNormalizationGrayPaddingAndSupportsHardwareBitmaps() = runBlocking {
        val software = Bitmap.createBitmap(2, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(255, 128, 0)) }
        try {
            val hardware = if (Build.VERSION.SDK_INT >= 26) software.copy(Bitmap.Config.HARDWARE, false) else null
            try {
                for (bitmap in listOfNotNull(software, hardware)) {
                    val values = prepareImage(bitmap, ImageTransform(2, 4, 0, 4, 4)).asFloatBuffer()
                    assertEquals(114f / 255, values[0], .001f)
                    assertEquals(1f, values[3], .001f)
                    assertEquals(128f / 255, values[4], .001f)
                    assertEquals(0f, values[5], .001f)
                    assertFalse(bitmap.isRecycled)
                }
            } finally { hardware?.recycle() }
        } finally { software.recycle() }
    }

    private fun photo(name: String): Bitmap = context.assets.open("fixtures/$name").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
}
