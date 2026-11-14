package dev.sevendigits.internal

import dev.sevendigits.BoundingBox
import dev.sevendigits.RecognizedCharacter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun decodeDetections(
    output: FloatArray,
    count: Int,
    labels: List<Char?>,
    transform: ImageTransform,
    threshold: Float,
): List<RecognizedCharacter> {
    require(output.size == (4 + labels.size) * count) { "Unexpected output tensor size." }
    val candidates = ArrayList<RecognizedCharacter>()
    for (index in 0 until count) {
        if (index % 128 == 0) currentCoroutineContext().ensureActive()
        var label = -1
        var score = -1f
        for (category in labels.indices) {
            val value = output[(4 + category) * count + index]
            require(value.isFinite() && value in 0f..1f) { "Model returned invalid class scores." }
            if (value > score) { score = value; label = category }
        }
        if (score < threshold) continue
        // Ignore row/display classes before NMS; their boxes can otherwise suppress digits.
        val character = labels[label] ?: continue
        val x = output[index]
        val y = output[count + index]
        val width = output[2 * count + index]
        val height = output[3 * count + index]
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite()) {
            "Model returned non-finite box coordinates."
        }
        if (width <= 0 || height <= 0) continue
        val box = transform.fromModel(x, y, width, height) ?: continue
        candidates.add(RecognizedCharacter(character, box, score))
    }
    val kept = ArrayList<RecognizedCharacter>()
    for (candidate in candidates.sortedByDescending { it.confidence }) {
        currentCoroutineContext().ensureActive()
        if (kept.none { intersectionOverUnion(candidate.boundingBox, it.boundingBox) >= .45f }) {
            kept.add(candidate)
        }
    }
    return kept
}

internal fun intersectionOverUnion(a: BoundingBox, b: BoundingBox): Float {
    val intersection = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
        max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
    return intersection / (a.width * a.height + b.width * b.height - intersection)
}

/** Rows -> fields -> characters, all still in upright coordinates. */
internal suspend fun groupDetections(characters: List<RecognizedCharacter>): List<List<List<RecognizedCharacter>>> {
    val rows = ArrayList<MutableList<RecognizedCharacter>>()
    for (character in characters.filter { it.value.isDigit() }.sortedBy { centerY(it) }) {
        currentCoroutineContext().ensureActive()
        val match = rows.filter { row ->
            abs(centerY(character) - median(row.map { centerY(it) })) <
                min(character.boundingBox.height, median(row.map { it.boundingBox.height })) * .5f
        }.minByOrNull { row -> abs(centerY(character) - median(row.map { centerY(it) })) }
        if (match == null) rows.add(mutableListOf(character)) else match.add(character)
    }
    val unmatched = ArrayList<MutableList<RecognizedCharacter>>()
    for (mark in characters.filterNot { it.value.isDigit() }) {
        currentCoroutineContext().ensureActive()
        val match = rows.filter { row ->
            val digits = row.filter { it.value.isDigit() }
            val top = median(digits.map { it.boundingBox.top })
            val bottom = median(digits.map { it.boundingBox.bottom })
            val height = bottom - top
            centerY(mark) in (top - height * .15f)..(bottom + height * .15f) &&
                horizontalDistance(mark, row) < height * .8f
        }.minByOrNull { horizontalDistance(mark, it) }
        if (match == null) unmatched.add(mutableListOf(mark)) else match.add(mark)
    }
    return (rows + unmatched).sortedWith(compareBy({ row -> median(row.map { centerY(it) }) },
        { row -> row.minOf { it.boundingBox.left } })).map { row ->
        val digits = row.filter { it.value.isDigit() }
        val height = median((digits.ifEmpty { row }).map { it.boundingBox.height })
        val fields = mutableListOf(mutableListOf<RecognizedCharacter>())
        for (character in row.sortedBy { it.boundingBox.left }) {
            currentCoroutineContext().ensureActive()
            val previous = fields.last().lastOrNull()
            if (previous != null && character.boundingBox.left - previous.boundingBox.right > height * .8f) {
                fields.add(mutableListOf())
            }
            fields.last().add(character)
        }
        fields
    }
}

private fun centerY(character: RecognizedCharacter): Float = (character.boundingBox.top + character.boundingBox.bottom) / 2
private fun median(values: List<Float>): Float {
    val sorted = values.sorted()
    return if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
}
private fun horizontalDistance(mark: RecognizedCharacter, row: List<RecognizedCharacter>): Float =
    row.minOf { max(0f, max(it.boundingBox.left - mark.boundingBox.right, mark.boundingBox.left - it.boundingBox.right)) }
