package com.roeiamor.fitshare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * A registered FitShare user, mirroring the `users/{uid}` document (SPEC section 3).
 *
 * Every property has a default so the class keeps a no-argument constructor. Firestore's
 * `toObject()` instantiates the class reflectively and then fills the fields, so without defaults
 * deserialisation fails at runtime rather than at compile time.
 *
 * No Android imports here by design - this is a plain data class, and the Firebase annotations are
 * the only concession, because Firestore needs them to map the document.
 *
 * @property uid the Firebase Auth uid; also the document id.
 * @property displayName the name shown on workouts and comments.
 * @property email the address the account was registered with.
 * @property photoUrl a Cloudinary URL, or null while the user has no avatar.
 * @property bio free text on the profile screen, empty until the user writes one.
 * @property workoutsCount maintained with FieldValue.increment when a workout is added or deleted.
 * @property createdAt written by the server, so it does not depend on the device clock. Null only
 *   in the brief window between a local write and the server acknowledging it.
 */
data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val bio: String = "",
    val workoutsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
