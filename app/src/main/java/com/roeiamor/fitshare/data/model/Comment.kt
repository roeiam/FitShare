package com.roeiamor.fitshare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * One comment on a workout, mirroring `workouts/{workoutId}/comments/{commentId}`
 * (SPEC section 3).
 *
 * Every property has a default so the class keeps a no-argument constructor for Firestore's
 * `toObject()`.
 *
 * The author's name and photo are **denormalized**, for the same reason they are on a workout: the
 * comment list renders from one query, and looking each author up would turn a twenty-comment thread
 * into twenty-one reads.
 *
 * @property id the document id, mirrored into the document so a row knows its own id.
 * @property authorId uid of whoever wrote it; compared against the current user to decide whether
 *   deleting is offered.
 * @property text 1-300 characters.
 * @property createdAt written by the server, so ordering does not depend on device clocks.
 */
data class Comment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
