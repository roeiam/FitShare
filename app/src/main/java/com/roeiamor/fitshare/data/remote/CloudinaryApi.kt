package com.roeiamor.fitshare.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * The part of Cloudinary's unsigned upload response the app uses (SPEC section 11).
 *
 * Cloudinary returns a large JSON object - dimensions, format, version, a signature and more - and
 * the app needs exactly one field. Declaring only that field is deliberate: Gson ignores the rest,
 * and the class does not pretend to model an API it does not use.
 *
 * @property secureUrl the `https` URL of the stored image. Nullable because a malformed response
 *   must become a handled failure, not a crash on a non-null assertion.
 */
data class CloudinaryUploadResponse(
    @SerializedName("secure_url") val secureUrl: String?
)

/**
 * Cloudinary's unsigned upload endpoint.
 *
 * Unsigned means no API secret is involved: the request carries an upload *preset* that the
 * Cloudinary account marks as public. That is what makes it safe to call straight from the app -
 * there is no credential here to leak, which is why the preset sits in `gradle.properties` and ends
 * up in `BuildConfig`. This is stated in the README so nobody mistakes it for a leaked secret.
 */
interface CloudinaryApi {

    /**
     * Uploads one image.
     *
     * @param cloudName the Cloudinary account name; part of the path, not a header.
     * @param file the multipart `file` part - the compressed JPEG.
     * @param uploadPreset the multipart `upload_preset` part naming the unsigned preset.
     */
    @Multipart
    @POST("v1_1/{cloudName}/image/upload")
    suspend fun uploadImage(
        @Path("cloudName") cloudName: String,
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody
    ): CloudinaryUploadResponse
}
