package dev.sevendigits.testapp

import dev.sevendigits.BoundingBox
import dev.sevendigits.Lines
import dev.sevendigits.RecognitionResult
import org.json.JSONArray
import org.json.JSONObject

internal fun formatLines(lines: Lines): String = lines.joinToString(
    separator = ",\n  ", prefix = if (lines.isEmpty()) "[" else "[\n  ",
    postfix = if (lines.isEmpty()) "]" else "\n]",
) { row -> row.joinToString(", ", "[", "]") { JSONObject.quote(it) } }

internal fun formatVisual(result: RecognitionResult): String = JSONObject().apply {
    put("imageWidth", result.imageWidth)
    put("imageHeight", result.imageHeight)
    put("lines", JSONArray(result.lines.map { row -> JSONArray(row.map { region ->
        JSONObject().apply {
            put("text", region.text)
            put("boundingBox", region.boundingBox.json())
            put("minimumCharacterConfidence", region.minimumCharacterConfidence.toDouble())
            put("characters", JSONArray(region.characters.map { character -> JSONObject().apply {
                put("value", character.value.toString())
                put("source", character.source.name)
                put("boundingBox", character.boundingBox.json())
                put("confidence", character.confidence.toDouble())
            } }))
        }
    }) }))
}.toString(2)

private fun BoundingBox.json(): JSONObject = JSONObject().apply {
    put("left", left.toDouble())
    put("top", top.toDouble())
    put("right", right.toDouble())
    put("bottom", bottom.toDouble())
}
