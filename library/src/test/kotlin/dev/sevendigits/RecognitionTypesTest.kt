package dev.sevendigits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionTypesTest {
    @Test
    fun preservesWholeDisplayFieldsWithoutNumericConversion() {
        val expected = listOf("120", "80", "65", "11.11.2026", "11:05", "008", "-0,5", "+1/2")
        val result = RecognitionResult(200, 200, expected.mapIndexed { row, text -> listOf(region(text, row * 22f)) })

        assertEquals(expected, result.regions.map { it.text })
        assertEquals(expected.map { listOf(it) }, result.toLines())
    }

    @Test
    fun derivesBoundsAndMinimumScoreFromCharactersIncludingSmallPunctuation() {
        val result = TextRegion(listOf(
            RecognizedCharacter('0', BoundingBox(10f, 10f, 20f, 30f), .9f),
            RecognizedCharacter('.', BoundingBox(22f, 27f, 25f, 30f), .4f),
            RecognizedCharacter('5', BoundingBox(27f, 12f, 39f, 31f), .8f),
        ))

        assertEquals("0.5", result.text)
        assertEquals(BoundingBox(10f, 10f, 39f, 31f), result.boundingBox)
        assertEquals(.4f, result.minimumCharacterConfidence, 0f)
    }

    @Test
    fun preservesSuppliedReadingOrderWhenOriginalCoordinatesAreRotated() {
        // An upright left-to-right field can run bottom-to-top in the original bitmap.
        val text = TextRegion(listOf(
            RecognizedCharacter('1', BoundingBox(0f, 20f, 10f, 30f), .9f),
            RecognizedCharacter('2', BoundingBox(0f, 0f, 10f, 10f), .8f),
        ))
        assertEquals("12", text.text)
        assertEquals(BoundingBox(0f, 0f, 10f, 30f), text.boundingBox)
        val visual = RecognitionResult(100, 100, listOf(listOf(text, region("08"))))
        assertEquals(listOf(listOf("12", "08")), visual.toLines())
    }

    @Test
    fun takesDefensiveSnapshotsOfCallerLists() {
        val characters = mutableListOf(RecognizedCharacter('8', BoundingBox(0f, 0f, 10f, 20f), .9f))
        val text = TextRegion(characters)
        val regions = mutableListOf(text)
        val rows = mutableListOf<List<TextRegion>>(regions)
        val result = RecognitionResult(100, 100, rows)
        characters.clear()
        regions.clear()
        rows.clear()

        assertEquals("8", result.regions.single().text)
        assertEquals(1, text.characters.size)
        assertEquals(listOf(listOf("8")), result.toLines())
        assertThrows(UnsupportedOperationException::class.java) {
            (text.characters as MutableList).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.regions as MutableList).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.lines as MutableList).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.lines.single() as MutableList).clear()
        }
    }

    @Test
    fun distinguishesEmptyDetectionFromInvalidRegionOrImage() {
        assertTrue(RecognitionResult(640, 480, emptyList()).regions.isEmpty())
        assertTrue(RecognitionResult(640, 480, emptyList()).toLines().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { TextRegion(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { RecognitionResult(640, 480, listOf(emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { RecognitionResult(0, 480, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { RecognitionResult(640, -1, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { RecognitionResult(5, 20, listOf(listOf(region("8")))) }
        assertThrows(IllegalArgumentException::class.java) { RecognitionResult(100, 10, listOf(listOf(region("8")))) }
        // Right/bottom edges may coincide with the image edges.
        assertEquals("8", RecognitionResult(10, 20, listOf(listOf(region("8")))).regions.single().text)
    }

    @Test
    fun textViewKeepsMultipleFieldsOnOneRowAndSeparateRowsDistinct() {
        val visual = RecognitionResult(300, 200, listOf(
            listOf(region("120")),
            listOf(region("80", top = 30f), region("65", top = 30f, left = 80f)),
            listOf(region("11.11.2026", top = 60f), region("11:05", top = 60f, left = 150f)),
        ))

        val lines: Lines = visual.toLines()
        assertEquals(listOf(listOf("120"), listOf("80", "65"), listOf("11.11.2026", "11:05")), lines)
        assertEquals("65", lines[1][1])
        assertEquals(5, visual.regions.size)
    }

    @Test
    fun textViewIsImmutableAtBothListLevels() {
        val visual = RecognitionResult(100, 100, listOf(listOf(region("008"))))
        val lines = visual.toLines()

        assertThrows(UnsupportedOperationException::class.java) {
            (lines as MutableList).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (lines.single() as MutableList)[0] = "8"
        }
        assertEquals(listOf(listOf("008")), visual.toLines())
    }

    @Test
    fun rejectsNonFiniteNegativeAndEmptyBoundingBoxes() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { bad ->
            for (index in 0..3) {
                val edges = mutableListOf(0f, 0f, 10f, 20f)
                edges[index] = bad
                assertThrows(IllegalArgumentException::class.java) {
                    BoundingBox(edges[0], edges[1], edges[2], edges[3])
                }
            }
        }
        assertThrows(IllegalArgumentException::class.java) { BoundingBox(-1f, 0f, 10f, 20f) }
        assertThrows(IllegalArgumentException::class.java) { BoundingBox(0f, -1f, 10f, 20f) }
        assertThrows(IllegalArgumentException::class.java) { BoundingBox(10f, 0f, 10f, 20f) }
        assertThrows(IllegalArgumentException::class.java) { BoundingBox(0f, 20f, 10f, 10f) }
    }

    @Test
    fun validatesThresholdsScoresAndRotation() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f, 1.01f).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { RecognitionOptions(minimumCharacterConfidence = bad) }
            assertThrows(IllegalArgumentException::class.java) {
                RecognizedCharacter('1', BoundingBox(0f, 0f, 10f, 20f), bad)
            }
        }
        listOf(-90, 1, 45, 360).forEach { rotation ->
            assertThrows(IllegalArgumentException::class.java) { RecognitionOptions(rotationDegrees = rotation) }
        }
        listOf(0, 90, 180, 270).forEach { rotation ->
            assertEquals(rotation, RecognitionOptions(rotationDegrees = rotation).rotationDegrees)
        }
        assertEquals(0f, RecognitionOptions(minimumCharacterConfidence = 0f).minimumCharacterConfidence, 0f)
        assertEquals(1f, RecognitionOptions(minimumCharacterConfidence = 1f).minimumCharacterConfidence, 0f)
    }

    @Test
    fun rejectsNonNumericSymbols() {
        listOf('A', ' ', '\n').forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                RecognizedCharacter(value, BoundingBox(0f, 0f, 10f, 20f), .9f)
            }
        }
    }

    private fun region(text: String, top: Float = 0f, left: Float = 0f): TextRegion = TextRegion(
        text.mapIndexed { index, value ->
            RecognizedCharacter(value, BoundingBox(left + index * 12f, top, left + index * 12f + 10f, top + 20f), .9f)
        },
    )
}
