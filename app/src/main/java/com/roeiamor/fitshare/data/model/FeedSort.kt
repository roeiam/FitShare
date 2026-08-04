package com.roeiamor.fitshare.data.model

/**
 * How the feed is ordered.
 *
 * [NEWEST] is the default and the only one the UI exposes today; sorting is SPEC feature 19, an
 * extra that comes after the MVP. The data layer already supports both so that feature is a UI
 * change rather than a query change.
 */
enum class FeedSort {
    /** Newest first, by the server-written `createdAt`. */
    NEWEST,

    /** Most liked first. */
    MOST_LIKED
}
