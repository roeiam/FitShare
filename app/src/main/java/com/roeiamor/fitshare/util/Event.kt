package com.roeiamor.fitshare.util

/**
 * A value that should be consumed exactly once.
 *
 * `LiveData` re-delivers its current value to every new observer, which is right for state and
 * wrong for actions. Without this wrapper, rotating the screen after a successful login would
 * re-deliver "navigate to feed" and navigate a second time, and a snackbar would pop up again.
 *
 * The ViewModel exposes `LiveData<Event<T>>`; the Fragment reads it with [getContentIfNotHandled],
 * which returns the value the first time and null on every later read.
 */
class Event<out T>(private val content: T) {

    /** True once the content has been handed out. Exposed for tests and for peeking. */
    var hasBeenHandled = false
        private set

    /** Returns the content the first time it is called, and null every time after that. */
    fun getContentIfNotHandled(): T? =
        if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
}
