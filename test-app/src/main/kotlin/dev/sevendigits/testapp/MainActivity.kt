package dev.sevendigits.testapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import dev.sevendigits.SevenDigitsRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognitionJob: Job? = null
    private var recognizer: SevenDigitsRecognizer? = null
    private var selectedUri: Uri? = null
    private lateinit var captures: CameraCapture
    private var pendingPhoto: CameraPhoto? = null
    private lateinit var preview: RecognitionImageView
    private lateinit var status: TextView
    private lateinit var gallery: Button
    private lateinit var camera: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Keep controls clear of system bars, including enforced edge-to-edge on newer Android.
        findViewById<View>(R.id.page).setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        preview = findViewById(R.id.preview)
        status = findViewById(R.id.status)
        captures = CameraCapture(applicationContext)
        pendingPhoto = savedInstanceState?.getString("pendingPhoto")?.let(captures::restore)
        gallery = findViewById<Button>(R.id.gallery).apply { setOnClickListener { openGallery() } }
        camera = findViewById<Button>(R.id.camera).apply { setOnClickListener { openCamera() } }
        setInputsEnabled(pendingPhoto == null)
        findViewById<Switch>(R.id.show_boxes).apply {
            isChecked = savedInstanceState?.getBoolean("showBoxes", true) ?: true
            preview.showBoxes = isChecked
            setOnCheckedChangeListener { _, checked -> preview.showBoxes = checked }
        }
        savedInstanceState?.getString("selectedUri")?.let { scan(Uri.parse(it)) }
    }

    @Suppress("DEPRECATION")
    private fun openCamera() {
        if (pendingPhoto != null) return
        try {
            val photo = captures.create()
            pendingPhoto = photo
            val intent = photo.captureIntent()
            if (intent.resolveActivity(packageManager) == null) {
                abandonCapture(R.string.camera_unavailable)
                return
            }
            // ACTION_IMAGE_CAPTURE delegates permission prompts to the camera app.
            // Only this content URI is temporarily shared; no broad storage/camera grant.
            setInputsEnabled(false)
            startActivityForResult(intent, TAKE_PHOTO)
        } catch (_: ActivityNotFoundException) {
            abandonCapture(R.string.camera_unavailable)
        } catch (_: SecurityException) {
            abandonCapture(R.string.camera_blocked)
        } catch (_: java.io.IOException) {
            abandonCapture(R.string.camera_failed)
        } catch (_: IllegalStateException) {
            abandonCapture(R.string.camera_failed)
        }
    }

    private fun abandonCapture(message: Int) {
        pendingPhoto?.let(captures::discard)
        pendingPhoto = null
        setInputsEnabled(true)
        status.setText(message)
    }

    private fun setInputsEnabled(enabled: Boolean) {
        gallery.isEnabled = enabled
        camera.isEnabled = enabled
    }

    @Suppress("DEPRECATION")
    private fun openGallery() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, PICK_IMAGE)
        } catch (_: ActivityNotFoundException) {
            status.setText(R.string.picker_unavailable)
        }
    }

    @Deprecated("Uses the platform Activity result callback for this small sample.")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TAKE_PHOTO) {
            val photo = pendingPhoto
            pendingPhoto = null
            setInputsEnabled(true)
            if (photo == null) {
                status.setText(if (resultCode == RESULT_OK) R.string.camera_empty else R.string.camera_cancelled)
            } else if (resultCode == RESULT_OK && photo.file.length() > 0) {
                captures.revoke(photo)
                scan(photo.uri)
            } else {
                captures.discard(photo)
                status.setText(if (resultCode == RESULT_OK) R.string.camera_empty else R.string.camera_cancelled)
            }
            return
        }
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers only grant temporary access. It still works for this selection.
        }
        scan(uri)
    }

    private fun scan(uri: Uri) {
        val oldUri = selectedUri
        selectedUri = uri
        val previous = recognitionJob
        previous?.cancel()
        recognitionJob = scope.launch {
            previous?.join()
            oldUri?.takeIf { it != uri }?.let {
                captures.deleteIfOwned(it)
                try {
                    contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) { /* Camera files and temporary gallery grants are not persisted. */ }
            }
            setInputsEnabled(false)
            findViewById<View>(R.id.progress).visibility = View.VISIBLE
            findViewById<View>(R.id.results).visibility = View.GONE
            findViewById<View>(R.id.image_section).visibility = View.GONE
            preview.setImage(null)
            status.setText(R.string.loading)
            try {
                val bitmap = withContext(Dispatchers.IO) { decodePhoto(contentResolver, uri) }
                preview.setImage(bitmap)
                preview.contentDescription = getString(R.string.preview_loading)
                findViewById<View>(R.id.image_section).visibility = View.VISIBLE
                status.setText(R.string.recognizing)
                val engine = recognizer ?: SevenDigitsRecognizer.create(applicationContext).also { recognizer = it }
                // Exercise both public entry points independently in this test app.
                val visual = engine.recognizeVisual(bitmap)
                preview.setResult(visual)
                val lines = engine.recognizeText(bitmap)
                findViewById<TextView>(R.id.text_output).text = formatLines(lines)
                findViewById<TextView>(R.id.visual_output).text = formatVisual(visual)
                findViewById<View>(R.id.results).visibility = View.VISIBLE
                preview.contentDescription = getString(R.string.preview_description,
                    visual.lines.joinToString("; ") { row -> row.joinToString(" ") { it.text } })
                status.text = when {
                    lines != visual.toLines() -> getString(R.string.results_differ)
                    visual.regions.isEmpty() -> getString(R.string.no_detections)
                    else -> getString(R.string.complete, bitmap.width, bitmap.height, visual.regions.size)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                status.text = getString(R.string.recognition_failed, failure.message ?: failure.javaClass.simpleName)
            } finally {
                setInputsEnabled(pendingPhoto == null)
                findViewById<View>(R.id.progress).visibility = View.GONE
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selectedUri", selectedUri?.toString())
        outState.putString("pendingPhoto", pendingPhoto?.file?.name)
        outState.putBoolean("showBoxes", preview.showBoxes)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        scope.cancel()
        // Bitmaps are GC-owned: cancelled native inference may still be reading one.
        super.onDestroy()
    }

    private companion object {
        const val PICK_IMAGE = 100
        const val TAKE_PHOTO = 101
    }
}
