package dev.sevendigits.internal

import dev.sevendigits.BoundingBox
import dev.sevendigits.RecognizedCharacter
import dev.sevendigits.TextRegion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun sameGlyph(a: BoundingBox, b: BoundingBox): Boolean {
    val overlap = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
        max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
    return intersectionOverUnion(a, b) >= .35f || overlap / min(a.width * a.height, b.width * b.height) >= .65f
}

/** Additional views may complete a partially read word; do not discard its uncovered suffix. */
internal suspend fun addUncoveredText(primary: MutableList<TextRegion>, extra: List<TextRegion>) {
    for (region in extra) {
        currentCoroutineContext().ensureActive()
        val uncovered = region.characters.filter { character ->
            primary.none { sameGlyph(it.boundingBox, character.boundingBox) }
        }
        if (uncovered.isNotEmpty()) primary.add(TextRegion(uncovered))
    }
}

/** Merge in upright coordinates. Never compare confidence across the two engines. */
internal suspend fun mergeText(
    segment: List<RecognizedCharacter>,
    general: List<TextRegion>,
): List<List<TextRegion>> {
    val words = general.filter { it.text.count(Char::isLetter) > it.text.count(Char::isDigit) }
    val unused = segment.filterNot { digit -> words.any { sameGlyph(it.boundingBox, digit.boundingBox) } }.toMutableList()
    val fields = groupDetections(unused).flatten().map { TextRegion(it) }
    val regions = ArrayList<TextRegion>()
    for (word in general) {
        currentCoroutineContext().ensureActive()
        if (word in words) {
            regions.add(word)
            continue
        }
        val covered = fields.filter { field ->
            field.characters.size >= word.characters.size && field.characters.size >= 2 &&
                sameGlyph(field.boundingBox, word.boundingBox)
        }
        if (covered.isNotEmpty()) {
            // OCR sometimes reads one fragment of a seven-segment field as an extra digit.
            // Keep the complete detector field and any independently observed separators.
            val marks = word.characters.filter { mark ->
                mark.value in ".,:;-/+°%" && covered.none { field -> field.characters.any {
                    it.value == mark.value && sameGlyph(it.boundingBox, mark.boundingBox)
                } }
            }
            if (marks.isNotEmpty()) regions.add(TextRegion(marks))
            continue
        }
        val overlapping = unused.filter { digit -> word.characters.any { sameGlyph(it.boundingBox, digit.boundingBox) } }
        val characters = word.characters.filter { character ->
            overlapping.none {
                sameGlyph(it.boundingBox, character.boundingBox) &&
                    (character.value !in ".,:;-/+°%" || it.value == character.value)
            }
        } + overlapping
        unused.removeAll(overlapping.toSet())
        regions.add(TextRegion(characters.sortedBy { it.boundingBox.left }))
    }
    regions.addAll(groupDetections(unused).flatten().map { TextRegion(it) })
    return groupTextRegions(regions)
}

/** Preserve OCR word boundaries; only reconnect adjacent numeric fragments and separators. */
private suspend fun groupTextRegions(regions: List<TextRegion>): List<List<TextRegion>> {
    val rows = ArrayList<MutableList<TextRegion>>()
    // Large regions establish rows before small units and punctuation are attached.
    for (region in regions.sortedByDescending { it.boundingBox.height }) {
        currentCoroutineContext().ensureActive()
        val box = region.boundingBox
        val row = rows.filter { row ->
            val anchor = row.first().boundingBox
            val overlap = min(box.bottom, anchor.bottom) - max(box.top, anchor.top)
            overlap > min(box.height, anchor.height) * .4f &&
                abs(centerY(box) - centerY(anchor)) < max(box.height, anchor.height) * .65f
        }.minByOrNull { abs(centerY(box) - centerY(it.first().boundingBox)) }
        if (row == null) rows.add(mutableListOf(region)) else row.add(region)
    }
    return rows.sortedBy { centerY(it.first().boundingBox) }.map { row ->
        val merged = ArrayList<TextRegion>()
        for (region in row.sortedBy { it.boundingBox.left }) {
            val previous = merged.lastOrNull()
            val gap = if (previous == null) Float.MAX_VALUE else region.boundingBox.left - previous.boundingBox.right
            if (previous != null && previous.text.all { it in "0123456789.,:-/+" } &&
                region.text.all { it in "0123456789.,:-/+" } &&
                gap < max(previous.boundingBox.height, region.boundingBox.height) * .35f) {
                merged[merged.lastIndex] = TextRegion((previous.characters + region.characters).sortedBy { it.boundingBox.left })
            } else merged.add(region)
        }
        merged
    }
}

private fun centerY(box: BoundingBox): Float = (box.top + box.bottom) / 2
