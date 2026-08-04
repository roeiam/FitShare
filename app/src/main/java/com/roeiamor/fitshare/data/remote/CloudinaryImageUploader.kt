package com.roeiamor.fitshare.data.remote

import android.net.Uri
import com.roeiamor.fitshare.BuildConfig
import com.roeiamor.fitshare.util.ImageCompressor
import com.roeiamor.fitshare.util.safeCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * The only [ImageUploader] implementation: compress, then POST to Cloudinary.
 *
 * The cloud name and the unsigned preset come from `BuildConfig`, which the build fills from
 * `gradle.properties` - so a machine missing them fails at configuration time with a readable
 * message rather than at runtime with an opaque Cloudinary error.
 *
 * @param cloudinaryApi the Retrofit interface.
 * @param imageCompressor shrinks the photo first; nothing is ever uploaded raw (SPEC section 11).
 */
class CloudinaryImageUploader(
    private val cloudinaryApi: CloudinaryApi,
    private val imageCompressor: ImageCompressor
) : ImageUploader {

    /**
     * Compresses [uri] and uploads the result.
     *
     * The compressed file is deleted afterwards whatever happens. It lives in the cache directory,
     * so the system would eventually reclaim it, but a user who publishes ten workouts should not
     * leave ten megabytes of dead JPEGs behind in the meantime.
     *
     * @return the `secure_url` Cloudinary stored the image at.
     */
    override suspend fun upload(uri: Uri): Result<String> {
        val compressed = imageCompressor.compress(uri).getOrElse { return Result.failure(it) }

        return try {
            safeCall { postToCloudinary(compressed) }
        } finally {
            compressed.delete()
        }
    }

    /** Builds the multipart request and reads `secure_url` out of the response. */
    private suspend fun postToCloudinary(file: File): String {
        val filePart = MultipartBody.Part.createFormData(
            PART_FILE,
            file.name,
            file.asRequestBody(JPEG_MEDIA_TYPE.toMediaType())
        )
        val presetPart = BuildConfig.CLOUDINARY_UPLOAD_PRESET
            .toRequestBody(TEXT_MEDIA_TYPE.toMediaType())

        val response = cloudinaryApi.uploadImage(
            cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
            file = filePart,
            uploadPreset = presetPart
        )

        // A 200 with no secure_url means the preset is misconfigured. Failing here with a clear
        // message beats storing an empty imageUrl that renders as a broken card forever.
        return checkNotNull(response.secureUrl?.takeIf { it.isNotBlank() }) {
            "Cloudinary accepted the upload but returned no secure_url"
        }
    }

    private companion object {
        const val PART_FILE = "file"
        const val JPEG_MEDIA_TYPE = "image/jpeg"
        const val TEXT_MEDIA_TYPE = "text/plain"
    }
}
