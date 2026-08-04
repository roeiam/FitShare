package com.roeiamor.fitshare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * One published workout, mirroring a `workouts/{workoutId}` document (SPEC section 3).
 *
 * Every property has a default so the class keeps a no-argument constructor, which Firestore's
 * `toObject()` needs to instantiate it reflectively before filling the fields.
 *
 * `category` and `difficulty` are held as **strings**, exactly as Firestore stores them, and are
 * turned into enums through [WorkoutCategory.fromName] and [Difficulty.fromName]. Mapping them here
 * would mean a value this version of the app does not recognise - a document written by a newer
 * build, or typed by hand in the console - would throw during deserialisation and take the whole
 * feed with it.
 *
 * The author's name and photo are **denormalized** on purpose: the feed renders from one query and
 * must not issue an extra read per card (SPEC section 3).
 *
 * @property id the document id, mirrored into the document so a card knows its own id.
 * @property authorId uid of whoever published it.
 * @property authorName denormalized display name.
 * @property authorPhotoUrl denormalized avatar URL, null when the author has none.
 * @property title 3-60 characters.
 * @property description 0-600 characters.
 * @property category a [WorkoutCategory] name.
 * @property durationMinutes 1-600.
 * @property difficulty a [Difficulty] name.
 * @property imageUrl Cloudinary URL, null when the workout has no photo - the card must still look
 *   right without one.
 * @property likesCount maintained in the same transaction as the like document.
 * @property commentsCount maintained in the same transaction as the comment document.
 * @property createdAt written by the server, so ordering does not depend on device clocks. Null
 *   only between a local write and the server acknowledging it.
 */
data class Workout(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val durationMinutes: Int = 0,
    val difficulty: String = "",
    val imageUrl: String? = null,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
