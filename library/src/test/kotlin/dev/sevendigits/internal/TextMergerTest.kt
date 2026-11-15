package dev.sevendigits.internal

import dev.sevendigits.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TextMergerTest {
    private fun chars(text: String, x: Float = 0f, y: Float = 0f, source: RecognitionSource = RecognitionSource.SEVEN_SEGMENT) =
        text.mapIndexed { i, c -> RecognizedCharacter(c, BoundingBox(x + i * 12, y, x + i * 12 + 10, y + 20), .9f, source) }
    private fun word(text: String, x: Float = 0f, y: Float = 0f) = TextRegion(chars(text, x, y, RecognitionSource.GENERAL_TEXT))

    @Test fun combinesLabelsDigitsAndUnitsWithoutDuplicateReadings() = runBlocking {
        val result = mergeText(chars("126", 50f), listOf(word("SYS"), word("128", 50f), word("mmHg", 110f)))
        assertEquals(listOf(listOf("SYS", "126", "mmHg")), result.map { it.map { r -> r.text } })
        assertTrue(result.single()[1].characters.all { it.source == RecognitionSource.SEVEN_SEGMENT })
    }

    @Test fun preservesMissedDigitsAndPunctuationFromGeneralOcr() = runBlocking {
        val digits = listOf(chars("1")[0], chars("0", 36f)[0])
        val result = mergeText(digits, listOf(word("11:05")))
        assertEquals("11:05", result.single().single().text)
    }

    @Test fun neverTurnsPrintedLettersIntoDetectorDigits() = runBlocking {
        val result = mergeText(chars("5"), listOf(word("SYS")))
        assertEquals("SYS", result.single().single().text)
    }

    @Test fun isolatedLetterMisreadDoesNotEraseADigitInAnEstablishedNumericField() = runBlocking {
        val result = mergeText(chars("81"), listOf(word("B")))
        assertEquals("81", result.single().single().text)
    }

    @Test fun keepsSpatiallySeparateReadingsAndRowsWithoutMedicalInterpretation() = runBlocking {
        val digits = chars("20") + chars("01", 60f) + chars("008", 0f, 60f)
        val result = mergeText(digits, listOf(word("kWh", 60f, 60f)))
        assertEquals(listOf(listOf("20", "01"), listOf("008", "kWh")), result.map { it.map { r -> r.text } })
    }

    @Test fun keepsSeparatorsInsideACompleteDetectorField() = runBlocking {
        val result = mergeText(chars("125"), listOf(word(".", 22f)))
        assertEquals("12.5", result.single().single().text)
    }

    @Test fun punctuationDetectedByBothEnginesIsReturnedOnce() = runBlocking {
        val result = mergeText(chars("11:05"), listOf(word("11:05")))
        assertEquals("11:05", result.single().single().text)
    }

    @Test fun secondOcrViewCanCompleteAPartialWordWithoutRepeatingItsPrefix() = runBlocking {
        val primary = mutableListOf(word("20"))
        addUncoveredText(primary, listOf(word("2001")))
        assertEquals(listOf("20", "01"), primary.map { it.text })
        assertEquals("2001", mergeText(emptyList(), primary).single().single().text)
    }

    @Test fun windowsCoverOddSizeImagesAndRejectInternalTruncation() {
        val windows = detectionWindows(301, 501, true)
        assertEquals(10, windows.size)
        assertEquals(DetectionWindow(150, 250, 151, 251), windows.last())
        assertFalse(windows.last().containsComplete(BoundingBox(150f, 300f, 160f, 320f), 301, 501))
        assertTrue(windows.last().containsComplete(BoundingBox(280f, 480f, 301f, 501f), 301, 501))
        assertEquals(1, detectionWindows(301, 501, false).size)
    }

    @Test fun avoidsExtraInferenceWhenTheWholeImageAlreadyHasEnoughResolution() {
        assertFalse(needsZoomPasses(ImageTransform(640, 480, 0, 640, 640)))
        assertFalse(needsZoomPasses(ImageTransform(1000, 500, 0, 640, 640)))
        assertTrue(needsZoomPasses(ImageTransform(1500, 2000, 0, 640, 640)))
    }

    @Test fun cropCoordinatesAndRotationRoundTrip() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val transform = ImageTransform(1000, 1000, rotation, 640, 640, DetectionWindow(250, 250, 500, 500))
            val box = transform.fromModel(320f, 320f, 128f, 128f)!!
            assertEquals(BoundingBox(450f, 450f, 550f, 550f), box)
            assertEquals(box, transform.toUpright(transform.toOriginal(box)))
        }
    }
}
