package com.roeiamor.fitshare.ui.common

import java.text.NumberFormat

/**
 * Turns a count into the text shown beside it. The only place that happens.
 *
 * Five places used to write `count.toString()` by hand - the like button, both counters on a feed
 * card and two of the three profile statistics - which is five places to change if a count ever has
 * to be shortened or grouped differently, and five warnings from lint's `SetTextI18n` check.
 *
 * [NumberFormat] rather than `toString`, because `toString` always emits Western digits and a plain
 * decimal point. That happens to be right for Hebrew, but it is right by accident: the moment the
 * app runs in a locale with its own digits the number would be the one piece of text that did not
 * follow. Asking the platform costs nothing and cannot be wrong.
 *
 * On [Number] rather than on `Long`, because the counters that come from Firestore are `Long` while
 * the ones the app derives itself - the size of a list - are `Int`. One function for both beats an
 * overload per type or a `toLong()` at every call site.
 */
fun Number.asCountLabel(): String = NumberFormat.getIntegerInstance().format(this)
