package com.roeiamor.fitshare.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** A width and a height, with no Android types, so the sizing maths can be unit tested. */
data class ImageDimensions(val width: Int, val height: Int)

/**
 * Shrinks a photo before it is uploaded (SPEC section 11).
 *
 * A modern phone camera produces something like 4000x3000 and 4-8 MB. Uploading that would be slow
 * on mobile data, would burn Cloudinary's free quota, and would be pointless - the feed renders the
 * image into a card a few hundred pixels wide. So nothing is ever uploaded raw.
 *
 * Three steps, in this order, and the order matters:
 *  1. read the bounds only, then decode with `inSampleSize` so a huge image never has to fit in
 *     memory whole - decoding a 12-megapixel photo at full size is the classic OutOfMemoryError;
 *  2. scale the result so the longest edge is exactly [MAX_EDGE_PIXELS];
 *  3. apply the EXIF orientation, because a camera does not rotate its pixels - it writes a tag
 *     saying which way up the phone was, and BitmapFactory ignores it.
 *
 * @param context application context, used for the ContentResolver and the cache directory.
 */
class ImageCompressor(private val context: Context) {

    /**
     * Compresses the image at [uri] into a JPEG in the cache directory.
     *
     * Runs on [Dispatchers.IO]: decoding and writing a bitmap is slow and blocking, and doing it on
     * the main thread is exactly the jank the project's rules forbid.
     *
     * @return the compressed file, or a failure if the image cannot be read or decoded.
     */
    suspend fun compress(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        safeCall {
            val bounds = readBounds(uri)
            val decoded = decodeSampled(uri, bounds)
            val scaled = scaleToMaxEdge(decoded)
            val oriented = applyExifOrientation(uri, scaled)
            writeJpeg(oriented)
        }
    }

    /** Reads the image's size without allocating pixels, via `inJustDecodeBounds`. */
    private fun readBounds(uri: Uri): ImageDimensions {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Cannot open the selected image" }
            BitmapFactory.decodeStream(input, null, options)
        }
        check(options.outWidth > 0 && options.outHeight > 0) { "The selected file is not an image" }
        return ImageDimensions(options.outWidth, options.outHeight)
    }

    /** Decodes at the smallest power-of-two reduction that still covers the target size. */
    private fun decodeSampled(uri: Uri, bounds: ImageDimensions): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, MAX_EDGE_PIXELS)
        }
        return context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Cannot open the selected image" }
            checkNotNull(BitmapFactory.decodeStream(input, null, options)) {
                "The selected image could not be decoded"
            }
        }
    }

    /**
     * Scales to the exact target. `inSampleSize` only halves, so after decoding the longest edge is
     * somewhere between 1080 and 2160 pixels; this brings it to 1080 exactly.
     */
    private fun scaleToMaxEdge(source: Bitmap): Bitmap {
        val target = scaleToMaxEdge(ImageDimensions(source.width, source.height), MAX_EDGE_PIXELS)
        if (target.width == source.width && target.height == source.height) return source

        val scaled = source.scale(target.width, target.height)
        if (scaled != source) source.recycle()
        return scaled
    }

    /**
     * Rotates the pixels to match the camera's orientation tag.
     *
     * Without this, a photo taken holding the phone upright uploads on its side - and stays that way
     * for everyone who ever sees it, because the tag is lost when the bitmap is re-encoded.
     */
    private fun applyExifOrientation(uri: Uri, source: Bitmap): Bitmap {
        val degrees = readExifRotationDegrees(uri)
        if (degrees == 0f) return source

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) source.recycle()
        return rotated
    }

    /** Reads the orientation tag; 0 when there is none or it cannot be read. */
    private fun readExifRotationDegrees(uri: Uri): Float {
        val orientation = try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return 0f
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (error: Exception) {
            // A missing or malformed EXIF block is not a reason to fail the upload.
            return 0f
        }

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    /** Writes the bitmap into the cache directory as JPEG at [JPEG_QUALITY], and frees it. */
    private fun writeJpeg(bitmap: Bitmap): File {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "upload_${System.currentTimeMillis()}.jpg")

        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        bitmap.recycle()
        return file
    }

    companion object {

        /** The longest edge of an uploaded image, in pixels (SPEC section 11). */
        const val MAX_EDGE_PIXELS = 1080

        /** JPEG quality (SPEC section 11). */
        const val JPEG_QUALITY = 80

        /** Sits under cacheDir; the FileProvider also exposes it for the camera. */
        const val CACHE_DIRECTORY = "images"

        /**
         * The largest power of two by which the image can be reduced while still covering
         * [maxEdge] on its longest side.
         *
         * `inSampleSize` must be a power of two - BitmapFactory rounds anything else down to one -
         * so this deliberately stops one step early and leaves the exact fit to [scaleToMaxEdge].
         * Overshooting here would throw away detail that cannot be recovered.
         *
         * @return at least 1, meaning "do not reduce".
         */
        fun calculateInSampleSize(source: ImageDimensions, maxEdge: Int): Int {
            require(maxEdge > 0) { "maxEdge must be positive" }
            val longestEdge = maxOf(source.width, source.height)

            var sampleSize = 1
            while (longestEdge / (sampleSize * 2) >= maxEdge) {
                sampleSize *= 2
            }
            return sampleSize
        }

        /**
         * The size the image should end up, with its longest edge capped at [maxEdge] and its
         * aspect ratio preserved.
         *
         * An image already within the cap is returned untouched - upscaling a small photo would
         * only make it blurrier and bigger to upload.
         *
         * Each side is floored at 1 pixel: a very wide panorama would otherwise round its short
         * side to zero, and `createScaledBitmap` throws on a zero dimension.
         */
        fun scaleToMaxEdge(source: ImageDimensions, maxEdge: Int): ImageDimensions {
            require(maxEdge > 0) { "maxEdge must be positive" }
            val longestEdge = maxOf(source.width, source.height)
            if (longestEdge <= maxEdge) return source

            val ratio = maxEdge.toDouble() / longestEdge
            return ImageDimensions(
                width = maxOf(1, Math.round(source.width * ratio).toInt()),
                height = maxOf(1, Math.round(source.height * ratio).toInt())
            )
        }
    }
}
