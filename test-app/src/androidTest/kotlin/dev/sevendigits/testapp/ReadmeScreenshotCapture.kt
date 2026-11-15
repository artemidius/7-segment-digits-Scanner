package dev.sevendigits.testapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in capture of real app views and unedited recognition output; see docs/images/README.md. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ReadmeScreenshotCapture {
    @Test
    @Suppress("DEPRECATION")
    fun captureGalleryResult() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureReadme") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "readme-fixture.jpg")
        assertTrue("Copy the reference photo into app cache first", fixture.isFile)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.onActivityResult(100, Activity.RESULT_OK, Intent().setData(Uri.fromFile(fixture)))
            }
            val deadline = System.currentTimeMillis() + 90_000
            var finished = false
            while (System.currentTimeMillis() < deadline && !finished) {
                scenario.onActivity {
                    finished = it.findViewById<View>(R.id.results).visibility == View.VISIBLE &&
                        it.findViewById<View>(R.id.progress).visibility == View.GONE
                }
                if (!finished) Thread.sleep(100)
            }
            assertTrue("Recognition did not complete", finished)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val text = activity.findViewById<TextView>(R.id.text_output)
                val lines = text.text.toString()
                assertTrue(lines.contains("2001"))
                assertTrue(lines.contains("SYS."))
                assertTrue(lines.contains("126"))
                assertTrue(lines.contains("DIA."))
                assertTrue(lines.contains("mmHg"))
                File(context.cacheDir, "readme-lines.txt").writeText(lines)
                File(context.cacheDir, "readme-visual.json").writeText(
                    activity.findViewById<TextView>(R.id.visual_output).text.toString(),
                )
                // A scrolling screenshot of the actual laid-out content, ending after Lines.
                // JSON stays available in the app and the sidecar file, outside this crop.
                val content = activity.findViewById<ScrollView>(R.id.page).getChildAt(0)
                val results = activity.findViewById<View>(R.id.results)
                val height = results.top + text.bottom + content.paddingBottom
                val screenshot = Bitmap.createBitmap(content.width, height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(screenshot)
                    val background = TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.windowBackground, background, true)
                    canvas.drawColor(background.data)
                    content.draw(canvas)
                    File(context.cacheDir, "readme-gallery.png").outputStream().use {
                        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { screenshot.recycle() }
            }
        }
    }
}
