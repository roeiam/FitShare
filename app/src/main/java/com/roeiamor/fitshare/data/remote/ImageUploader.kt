package com.roeiamor.fitshare.data.remote

import android.net.Uri

/**
 * Uploads images and returns public URLs (SPEC section 1).
 *
 * The abstraction exists so the storage provider stays a one-line swap in the ServiceLocator.
 * FitShare uses Cloudinary only because Firebase Storage now requires the paid Blaze plan; if that
 * ever changes, a `FirebaseImageUploader` replaces [CloudinaryImageUploader] and nothing else in the
 * app has to know.
 */
interface ImageUploader {

    /**
     * Uploads a local image and returns its public URL, or a failure.
     *
     * Implementations are expected to compress before uploading - callers hand over the raw picker
     * or camera Uri and do not think about size.
     */
    suspend fun upload(uri: Uri): Result<String>
}
