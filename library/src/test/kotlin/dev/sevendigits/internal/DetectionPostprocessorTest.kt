package dev.sevendigits.internal

import dev.sevendigits.BoundingBox
import dev.sevendigits.RecognizedCharacter
import dev.sevendigits.TextRegion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DetectionPostprocessorTest {
    @Test
    fun removesOverlappingClassesButNotCharactersInsideIgnoredRegionClass() = runBlocking {
        val detections = decodeDetections(tensor(
            floatArrayOf(50f, 50f, 20f, 20f, .99f, 0f, 0f),
            floatArrayOf(46f, 50f, 12f, 20f, 0f, .9f, 0f),
            floatArrayOf(46f, 50f, 12f, 20f, 0f, 0f, .8f),
        ), 3, listOf(null, '8', '3'), ImageTransform(100, 100, 0, 100, 100), .25f)
        assertEquals(listOf('8'), detections.map { it.value })
    }

    @Test
    fun clipsBoxesDiscardsPaddingAndHonorsInclusiveThreshold() = runBlocking {
        val result = decodeDetections(tensor(
            floatArrayOf(50f, 50f, 20f, 30f, .25f),
            floatArrayOf(5f, 50f, 10f, 10f, .9f), // Entirely in letterbox padding.
            floatArrayOf(85f, 50f, 10f, 10f, .24f),
        ), 3, listOf('8'), ImageTransform(100, 200, 0, 100, 100), .25f)
        assertEquals(1, result.size)
        assertEquals(BoundingBox(30f, 70f, 70f, 130f), result.single().boundingBox)
    }

    @Test
    fun malformedScoresFailRatherThanProduceEmptySuccess() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                decodeDetections(tensor(floatArrayOf(5f, 5f, 2f, 2f, Float.NaN)),
                    1, listOf('1'), ImageTransform(10, 10, 0, 10, 10), .25f)
            }
        }
    }

    @Test
    fun groupsRowsFieldsAndSmallPunctuationWithoutInventingDigits() = runBlocking {
        val characters = listOf(
            character('1', 0f), character('2', 12f), character('0', 24f),
            character('8', 0f, 40f), character('0', 12f, 40f),
            character('6', 80f, 40f), character('5', 92f, 40f),
            character('1', 0f, 80f), character('1', 12f, 80f),
            character(':', 24f, 86f, 3f, 9f), character('0', 30f, 80f), character('5', 42f, 80f),
            character('0', 0f, 120f), character('.', 12f, 137f, 3f, 3f), character('8', 18f, 120f),
        )
        val lines = groupDetections(characters.reversed()).map { row -> row.map { TextRegion(it).text } }
        assertEquals(listOf(listOf("120"), listOf("80", "65"), listOf("11:05"), listOf("0.8")), lines)
        assertTrue(groupDetections(emptyList()).isEmpty())
    }

    @Test
    fun mapsAllFourRotationsBackToOriginalPixelEdges() {
        val box = BoundingBox(10f, 20f, 30f, 40f)
        assertEquals(box, ImageTransform(100, 200, 0, 100, 100).toOriginal(box))
        assertEquals(BoundingBox(20f, 170f, 40f, 190f), ImageTransform(100, 200, 90, 100, 100).toOriginal(box))
        assertEquals(BoundingBox(70f, 160f, 90f, 180f), ImageTransform(100, 200, 180, 100, 100).toOriginal(box))
        assertEquals(BoundingBox(60f, 10f, 80f, 30f), ImageTransform(100, 200, 270, 100, 100).toOriginal(box))
    }

    @Test
    fun usesActualRoundedResizeDimensionsForOddAspectRatios() {
        val transform = ImageTransform(101, 203, 0, 640, 640)
        val full = transform.fromModel(transform.offsetX + transform.resizedWidth / 2f,
            transform.offsetY + transform.resizedHeight / 2f, transform.resizedWidth.toFloat(),
            transform.resizedHeight.toFloat())!!
        assertEquals(101f, full.right, .0001f)
        assertEquals(203f, full.bottom, .0001f)
    }

    private fun character(value: Char, x: Float, y: Float = 0f, width: Float = 10f, height: Float = 20f) =
        RecognizedCharacter(value, BoundingBox(x, y, x + width, y + height), .9f)

    private fun tensor(vararg rows: FloatArray): FloatArray =
        FloatArray(rows.size * rows[0].size) { index -> rows[index % rows.size][index / rows.size] }
}
