package com.roeiamor.fitshare.ui.addworkout

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.roeiamor.fitshare.util.ImageCompressor
import java.io.File

/**
 * The temporary file a camera photo is written into, and the `content://` Uri that lets the camera
 * app write to it.
 *
 * Kept apart from the Fragment so the path, the FileProvider authority and the cleanup live in one
 * place rather than being spread across callbacks.
 */
object CameraPhotoFile {

    /**
     * Creates an empty file under the cache directory and returns a Uri the camera can write to.
     *
     * The directory is the same one [ImageCompressor] writes to, and the same one
     * `res/xml/file_paths.xml` exposes - three places that have to agree, so the name comes from one
     * constant rather than being typed out again here.
     *
     * The authority is derived from `applicationId`, matching the `${applicationId}.fileprovider`
     * placeholder in the manifest, so a build with a different id cannot silently mismatch.
     */
    fun create(context: Context): Uri {
        val directory = File(context.cacheDir, ImageCompressor.CACHE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "camera_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /**
     * Deletes a file created by [create].
     *
     * Called when the user backs out of the camera: `TakePicture` has already created a zero-byte
     * file by then, and leaving it behind would slowly fill the cache with empty JPEGs.
     */
    fun delete(context: Context, uri: Uri) {
        val name = uri.lastPathSegment ?: return
        File(File(context.cacheDir, ImageCompressor.CACHE_DIRECTORY), name).delete()
    }

    private fun authority(context: Context): String = "${context.packageName}.fileprovider"
}
