package com.roeiamor.fitshare.data.model

/**
 * How the feed is ordered.
 *
 * [NEWEST] is the default. Both constants are exposed by the UI as a row of chips built straight
 * from `entries` in `FeedFragment.setUpSortChips`, so adding a third ordering here puts a third chip
 * on screen with no change to the fragment.
 *
 * Sorting is SPEC feature 19, an extra beyond the MVP. It was built in the data layer first and
 * given its UI in Phase 7, which is why the query side carried both orderings before anything could
 * select between them.
 *
 * [MOST_LIKED] combined with the category filter needs its own composite index
 * (`category` ASC, `likesCount` DESC); both indexes are in `firestore.indexes.json`.
 */
enum class FeedSort {
    /** Newest first, by the server-written `createdAt`. */
    NEWEST,

    /** Most liked first. */
    MOST_LIKED
}
