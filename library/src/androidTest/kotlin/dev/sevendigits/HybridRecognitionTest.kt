package dev.sevendigits

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HybridRecognitionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun wholePhotosRetainReadingsAndLabels() = runBlocking {
        val recognizer = SevenDigitsRecognizer.create(context)
        for (name in listOf("126-81-81-a.jpg", "126-81-81-b.jpg")) {
            val bitmap = context.assets.open("fixtures/$name").use { requireNotNull(BitmapFactory.decodeStream(it)) }
            try {
                val result = recognizer.recognizeVisual(bitmap)
                Log.i("HybridRecognitionTest", "$name: ${result.toLines()}")
                assertTrue(result.regions.any { it.text == "126" })
                assertEquals(2, result.regions.count { it.text == "81" })
                assertTrue(result.regions.any { it.text.contains("SYS") })
                assertFalse(bitmap.isRecycled)
            } finally { bitmap.recycle() }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun referenceClockDigitsOnAndroid14AndLater() = runBlocking {
        // Exact clock recovery is verified on API 34. API 23 misses these clocks;
        // keep that documented limitation separate from the cross-version API tests.
        val recognizer = SevenDigitsRecognizer.create(context)
        for (name in listOf("126-81-81-a.jpg", "126-81-81-b.jpg")) {
            val bitmap = context.assets.open("fixtures/$name").use { requireNotNull(BitmapFactory.decodeStream(it)) }
            try {
                val result = recognizer.recognizeVisual(bitmap)
                val clockDigits = result.lines.first().joinToString("") { it.text }
                assertEquals(if (name.endsWith("a.jpg")) "2001" else "2000", clockDigits)
                assertEquals(4, result.lines.first().sumOf { it.characters.size })
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun printedInstrumentTextPreservesUnitsSignsLeadingZerosAndRotatedBoxes() = runBlocking {
        val recognizer = SevenDigitsRecognizer.create(context)
        val upright = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
        Canvas(upright).apply {
            drawText("TEMP -12.5 °C", 60f, 110f, paint)
            drawText("METER 001234 kWh", 60f, 240f, paint)
            drawText("SENSOR 48 %", 60f, 370f, paint)
        }
        try {
            val baseline = recognizer.recognizeVisual(upright)
            Log.i("HybridRecognitionTest", "printed: ${baseline.toLines()}")
            val text = baseline.toLines().flatten().joinToString(" ")
            for (expected in listOf("TEMP", "-12.5", "°C", "METER", "001234", "kWh", "SENSOR", "48", "%")) {
                assertTrue("Missing $expected in $text", text.contains(expected))
            }
            val rotation = 90
            val bitmap = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height,
                Matrix().apply { postRotate(-rotation.toFloat()) }, false)
            try {
                val result = recognizer.recognizeVisual(bitmap, RecognitionOptions(rotationDegrees = rotation))
                assertEquals(baseline.toLines(), result.toLines())
                baseline.regions.zip(result.regions).forEach { (a, b) ->
                    assertEquals(a.boundingBox.left, bitmap.height - b.boundingBox.bottom, 3f)
                    assertEquals(a.boundingBox.top, b.boundingBox.left, 3f)
                }
            } finally { bitmap.recycle() }
        } finally { upright.recycle() }
    }
}
