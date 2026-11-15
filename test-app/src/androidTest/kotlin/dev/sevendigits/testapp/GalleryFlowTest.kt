package dev.sevendigits.testapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    @Suppress("DEPRECATION")
    fun selectionShowsBothOutputsAndSurvivesPickerCancellationAndRecreation() {
        val file = File(context.cacheDir, "blank-gallery-test.png")
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<Button>(R.id.camera).isEnabled)
                    activity.onActivityResult(100, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
                }
                waitForEmptyResult(scenario)
                scenario.onActivity { activity ->
                    assertEquals("[]", activity.findViewById<TextView>(R.id.text_output).text.toString())
                    val visual = activity.findViewById<TextView>(R.id.visual_output).text.toString()
                    assertTrue(visual.contains("\"imageWidth\": 320"))
                    assertTrue(visual.contains("\"lines\": []"))
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.image_section).visibility)
                    activity.onActivityResult(100, Activity.RESULT_CANCELED, null)
                    assertEquals("[]", activity.findViewById<TextView>(R.id.text_output).text.toString())
                }
                scenario.recreate()
                waitForEmptyResult(scenario)
            }
        } finally { file.delete() }
    }

    @Test
    fun decoderAppliesAllExifOrientationsIncludingMirrors() = runBlocking {
        val source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        Canvas(source).apply {
            colors.forEachIndexed { index, color ->
                val left = (index % 2) * 60f
                val top = (index / 2) * 40f
                drawRect(left, top, left + 60, top + 40, Paint().apply { this.color = color })
            }
        }
        val expected = listOf(
            listOf(0, 1, 2, 3), listOf(1, 0, 3, 2), listOf(3, 2, 1, 0), listOf(2, 3, 0, 1),
            listOf(0, 2, 1, 3), listOf(2, 0, 3, 1), listOf(3, 1, 2, 0), listOf(1, 3, 0, 2),
        )
        val file = File(context.cacheDir, "exif-gallery-test.jpg")
        try {
            for (orientation in 1..8) {
                file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                ExifInterface(file).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                val decoded = decodePhoto(context.contentResolver, Uri.fromFile(file))
                try {
                    assertEquals(if (orientation < 5) 120 else 80, decoded.width)
                    assertEquals(if (orientation < 5) 80 else 120, decoded.height)
                    expected[orientation - 1].forEachIndexed { index, colorIndex ->
                        val actual = decoded.getPixel(decoded.width * (1 + index % 2 * 2) / 4,
                            decoded.height * (1 + index / 2 * 2) / 4)
                        val color = colors[colorIndex]
                        assertTrue("Wrong orientation $orientation, corner $index",
                            kotlin.math.abs(Color.red(actual) - Color.red(color)) < 8 &&
                            kotlin.math.abs(Color.green(actual) - Color.green(color)) < 8 &&
                            kotlin.math.abs(Color.blue(actual) - Color.blue(color)) < 8)
                    }
                } finally { decoded.recycle() }
            }
        } finally { source.recycle(); file.delete() }
    }

    @Test
    fun decoderBoundsLargeImagesAndRejectsInvalidFiles() = runBlocking {
        val file = File(context.cacheDir, "large-gallery-test.jpg")
        try {
            val source = Bitmap.createBitmap(4100, 100, Bitmap.Config.ARGB_8888)
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            source.recycle()
            val decoded = decodePhoto(context.contentResolver, Uri.fromFile(file))
            assertTrue(decoded.width <= 2048 && decoded.height <= 2048)
            decoded.recycle()
            file.writeText("not an image")
            try {
                decodePhoto(context.contentResolver, Uri.fromFile(file))
                fail("Expected invalid image to fail")
            } catch (_: IllegalArgumentException) { }
        } finally { file.delete() }
    }

    private fun waitForEmptyResult(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            var done = false
            scenario.onActivity {
                done = it.findViewById<TextView>(R.id.status).text == it.getString(R.string.no_detections)
            }
            if (done) return
            Thread.sleep(100)
        }
        scenario.onActivity {
            fail("Recognition did not finish: ${it.findViewById<TextView>(R.id.status).text}")
        }
    }
}
