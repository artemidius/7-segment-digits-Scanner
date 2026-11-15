package dev.sevendigits.testapp

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

class CaptureFileProvider : FileProvider()

internal data class CameraPhoto(val file: File, val uri: Uri) {
    fun captureIntent(): Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, uri)
        clipData = ClipData.newRawUri("Captured photo", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}

internal class CameraCapture(private val context: Context) {
    private val directory get() = File(context.cacheDir, "captures")
    private val authority get() = "${context.packageName}.captures"

    fun create(): CameraPhoto {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create the photo directory." }
        return photo(File.createTempFile("capture-", ".jpg", directory))
    }

    fun restore(filename: String): CameraPhoto? {
        // Store only the generated name in saved state; never accept paths outside this directory.
        if (!filename.startsWith("capture-") || !filename.endsWith(".jpg") || File(filename).name != filename) return null
        return File(directory, filename).takeIf { it.isFile }?.let(::photo)
    }

    fun revoke(photo: CameraPhoto) {
        context.revokeUriPermission(photo.uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    fun discard(photo: CameraPhoto) {
        revoke(photo)
        photo.file.delete()
    }

    /** Only delete our own previous capture, after its recognition coroutine has finished. */
    fun deleteIfOwned(uri: Uri) {
        if (uri.scheme == "content" && uri.authority == authority &&
            uri.pathSegments.size == 2 && uri.pathSegments.first() == "photos") {
            uri.lastPathSegment?.let(::restore)?.let(::discard)
        }
    }

    private fun photo(file: File) = CameraPhoto(file, FileProvider.getUriForFile(context, authority, file))
}
