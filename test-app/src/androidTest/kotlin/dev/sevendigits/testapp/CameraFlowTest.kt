package dev.sevendigits.testapp

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26) // ActivityMonitor interception; the app itself still supports API 23.
class CameraFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    @Suppress("DEPRECATION")
    fun fullPhotoFlowsThroughBothApisAndCancelOrEmptyCapturePreservesIt() {
        var outcome = Activity.RESULT_OK
        var writePhoto = true
        val captureUris = mutableListOf<Uri>()
        withCamera { intent ->
            val uri = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
            captureUris.add(uri)
            assertEquals("content", uri.scheme)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            assertEquals(grants, intent.flags and grants)
            if (writePhoto) {
                val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                bitmap.recycle()
            }
            // Real camera apps commonly return null data when EXTRA_OUTPUT is supplied.
            Instrumentation.ActivityResult(outcome, null)
        }.use {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.findViewById<Button>(R.id.camera).performClick() }
                waitForStatus(scenario, R.string.no_detections)
                scenario.onActivity {
                    assertEquals("[]", it.findViewById<TextView>(R.id.text_output).text.toString())
                    assertTrue(it.findViewById<TextView>(R.id.visual_output).text.contains("\"imageWidth\": 640"))
                }
                scenario.recreate()
                waitForStatus(scenario, R.string.no_detections)
                writePhoto = false
                outcome = Activity.RESULT_CANCELED
                scenario.onActivity { it.findViewById<Button>(R.id.camera).performClick() }
                waitForStatus(scenario, R.string.camera_cancelled)
                assertDeleted(captureUris.last())
                outcome = Activity.RESULT_OK
                scenario.onActivity { it.findViewById<Button>(R.id.camera).performClick() }
                waitForStatus(scenario, R.string.camera_empty)
                assertDeleted(captureUris.last())
                scenario.onActivity {
                    assertEquals("[]", it.findViewById<TextView>(R.id.text_output).text.toString())
                    assertTrue(it.findViewById<Button>(R.id.gallery).isEnabled)
                    assertTrue(it.findViewById<Button>(R.id.camera).isEnabled)
                }
            }
        }
        captureUris.forEach { CameraCapture(context).deleteIfOwned(it) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun blockedOrMissingCameraReleasesFileAndKeepsGalleryAvailable() {
        var blocked = true
        var output: Uri? = null
        withCamera { intent ->
            output = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
            if (blocked) throw SecurityException("Permission denied by test camera")
            else throw ActivityNotFoundException("Camera unavailable")
        }.use {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.findViewById<Button>(R.id.camera).performClick() }
                waitForStatus(scenario, R.string.camera_blocked)
                assertDeleted(output!!)
                blocked = false
                scenario.onActivity { it.findViewById<Button>(R.id.camera).performClick() }
                waitForStatus(scenario, R.string.camera_unavailable)
                assertDeleted(output!!)
                scenario.onActivity { assertTrue(it.findViewById<Button>(R.id.gallery).isEnabled) }
            }
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun providerIsPrivateNarrowlyScopedAndPendingFileCanBeRestored() {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        assertFalse(Manifest.permission.CAMERA in requested)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in requested)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in requested)
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.captures", 0)!!
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        val captures = CameraCapture(context)
        val photo = captures.create()
        try {
            val restored = CameraCapture(context).restore(photo.file.name)!!
            assertEquals(photo.uri, restored.uri)
            assertNull(captures.restore("../${photo.file.name}"))
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.captures", File(context.cacheDir, "private.txt"))
                fail("Files outside captures/ must not be shareable")
            } catch (_: IllegalArgumentException) { }
        } finally { captures.discard(photo) }
        assertDeleted(photo.uri)
    }

    private fun withCamera(respond: (Intent) -> Instrumentation.ActivityResult): AutoCloseable {
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action == MediaStore.ACTION_IMAGE_CAPTURE) respond(intent) else null
        }
        instrumentation.addMonitor(monitor)
        return AutoCloseable { instrumentation.removeMonitor(monitor) }
    }

    private fun assertDeleted(uri: Uri) {
        assertFalse(File(context.cacheDir, "captures/${uri.lastPathSegment}").exists())
    }

    private fun waitForStatus(scenario: ActivityScenario<MainActivity>, expected: Int) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            var done = false
            scenario.onActivity { done = it.findViewById<TextView>(R.id.status).text == it.getString(expected) }
            if (done) return
            Thread.sleep(100)
        }
        scenario.onActivity { fail("Unexpected status: ${it.findViewById<TextView>(R.id.status).text}") }
    }
}
