# Phase 7 report — profile, favourites, theme, connectivity

Phase 7 of the eight-phase table in `PHASE0_PLAN.md` §5. All work verified on the physical
**Samsung SM-A305F (R58M76TC1FX)**, Android 11 / API 30. The emulator was not used.

---

## 1. What was built

| # | Item | Status |
|---|---|---|
| 1 | `ProfileFragment` — avatar, name, bio, 3 stats, workout grid, read-only mode | Done, verified |
| 2 | `EditProfileFragment` — name, bio, avatar, denormalized author repair | Done, verified |
| 3 | `FavoritesFragment` — one query, long-press remove, empty state | Done, verified |
| 4 | `ThemePreferences` — stored light/dark choice overriding the forced dark default | Done, verified |
| 5 | No-connection banner | Done, verified |
| 6 | Feed sort — newest / most-liked | Done, **one gap: see §5** |
| 7 | Timeouts so no repository call can hang forever | Done, verified — and a real defect found beyond it, see §3 |

### New files

```
data/repository/UserRepository.kt          profiles: read one, edit your own
ui/profile/ProfileUiState.kt               sealed interface, four states
ui/profile/EditProfileViewModel.kt
ui/profile/WorkoutGridAdapter.kt
ui/favorites/FavoritesViewModel.kt         + FavoritesUiState in the same file
ui/favorites/FavoriteAdapter.kt
util/NetworkMonitor.kt                     connectivity, watched and asked
util/NetworkGuard.kt                       replaces the earlier NetworkTimeout.kt
util/ThemePreferences.kt
res/layout/item_workout_grid.xml
res/layout/item_favorite.xml
res/layout/view_profile_stat.xml           <include>d three times, not copied
```

---

## 2. The three statistics

`אימונים` = `workoutsCount` · `לייקים` = likes **received** · `מועדפים` = saved count.

"Likes received" is summed in the ViewModel over the workouts already loaded for the grid, so it
costs **zero extra Firestore reads** — which is what SPEC §5 asks for.

**The third stat shows `—` on another user's profile.** The security rules in SPEC §10 make
`users/{uid}/favorites` readable only by its owner, so another user's saved count is not knowable.
Asking for it anyway would return a permission error and throw the whole screen into its error state
over one number, so `ProfileViewModel` chooses the favourites flow up front and substitutes null.
Verified on device: RoeiTester's profile renders `1 · 16 · —`.

---

## 3. The defect the timeout alone did not fix

Phase 5 recorded that registration hangs forever offline. The timeout added at the start of this
phase fixed the hang — but device testing showed it produced a **worse, quieter bug**.

**What was measured.** Offline, publishing a workout showed `אין חיבור לאינטרנט` as intended. Wifi
was then re-enabled, and the workout **appeared in the feed anyway**. The UI had told the user it
failed, and then it succeeded a minute later behind their back.

**Why.** The Firestore SDK is offline-first. A plain write or a `WriteBatch` is accepted into a local
queue and committed whenever the device next reaches the server; `await()` on that task never returns
while offline. A timeout makes the *UI* give up, but the *write* is still queued and still lands.

Transactions behave differently — they need a server round trip and fail immediately offline, never
queueing. That is why the offline like, comment and favourite tests all failed cleanly while publish
did not: those three go through `runTransaction`, publish goes through a `WriteBatch`.

**The fix.** `NetworkGuard` now asks `NetworkMonitor` *before* starting, and refuses when there is no
validated connection. Nothing is handed to Firestore, so nothing can be committed later. The timeout
stays as the backstop for a connection that dies mid-call. One rule for every call, rather than
relying on whoever writes the next repository method to classify it correctly.

**Verified on device.** Offline, published a workout titled `ShouldNeverAppear`: error in under 15 s
(the fail-fast path, not the timeout). Wifi restored, waited 20 s, scrolled the entire feed — five
workouts, none of them `ShouldNeverAppear`. The banner also cleared on its own.

`NET_CAPABILITY_VALIDATED` rather than merely `INTERNET`, so a captive-portal wifi that carries no
traffic is correctly reported as offline.

---

## 4. Offline verification

Network disabled with `svc wifi disable` + `svc data disable`; confirmed
`Active default network: none` before each test.

| Action | Result |
|---|---|
| Like | `אין חיבור לאינטרנט` |
| Favourite | `אין חיבור לאינטרנט` |
| Comment | `אין חיבור לאינטרנט` |
| Publish | `אין חיבור לאינטרנט`, and the write never lands (§3) |
| Register | **Not executed — see §6** |

A note on method: an earlier run of this appeared to fail, reporting no message across 25 s. That run
was invalid — the app was not in the foreground and wifi was still on, so the taps went nowhere. A
second source of false negatives was polling every 5 s while the snackbar lasts ~3.5 s and each
`uiautomator dump` costs about a second, so the message could fall between samples. Both runs were
redone with back-to-back dumps and the app confirmed on screen first.

---

## 5. Feed sort — action required before the demo

Sorting by **הכי אהובים** works with no category filter.

**Combining a category filter with most-liked fails**, because it needs a second composite index
(`category` ASC, `likesCount` DESC) — the one already created in Phase 4 covers `category` +
`createdAt`. The app degrades correctly rather than crashing: it shows its error state with
`משהו השתבש`, no crash in logcat.

Firestore printed the one-click creation link:

```
https://console.firebase.google.com/v1/r/project/fitshare-7ddbe/firestore/indexes?create_composite=Ck9wcm9qZWN0cy9maXRzaGFyZS03ZGRiZS9kYXRhYmFzZXMvKGRlZmF1bHQpL2NvbGxlY3Rpb25Hcm91cHMvd29ya291dHMvaW5kZXhlcy9fEAEaDAoIY2F0ZWdvcnkQARoOCgpsaWtlc0NvdW50EAIaDAoIX19uYW1lX18QAg
```

**This needs your console access — I cannot create it.** Until it exists, category + most-liked shows
the error state.

---

## 6. What is outstanding

### a. Your display name is currently wrong — please retype it

**`רועי אמור` now reads `רועי אמוX` in Firestore.** I did this and could not undo it.

I appended an `X` to the name to prove the denormalization repair works (it does — see §7). Reverting
should have been one backspace, but in an RTL field `KEYCODE_MOVE_END` put the cursor in a different
place than expected and the backspace deleted the `ר` instead of the `X`. Restoring it needs Hebrew
typed into the field, and `adb shell input text` cannot send non-ASCII on this device — it throws
`NullPointerException: Attempt to get length of null array`. Keyevents bypass the IME layout and
produce ASCII, so that route fails too.

**Fix: Profile → עריכת פרופיל → set the name to `רועי אמור` → שמירה.** Saving also rewrites the name
back onto your workouts automatically. Nothing else is affected — the bio, avatar and all counters
are untouched.

I should have tested this on a throwaway account instead of yours.

### b. Offline registration not executed

The one item from your list I did not run. Reaching the register screen requires signing out, and
signing back in needs your password, which I do not have — so I would have left you locked out of
your own session.

What I can say: `register` goes through the identical `networkGuard.run { … }` wrapper as the five
paths that were executed and passed, and the fail-fast branch is checked before any Firebase call, so
it cannot reach the SDK offline. That is verification by construction, not by execution — I am not
claiming it as tested.

To close it: sign out, turn the network off, attempt to register anything. Expected:
`אין חיבור לאינטרנט` immediately.

### c. RoeiTester's workout count disagrees with their grid

Their profile shows `1 אימונים` above a grid of **2** workouts. Not a code defect: those documents
were typed by hand in the Firebase console in Phase 4, and a console write bypasses the `WriteBatch`
that increments `workoutsCount`. The counter is only ever maintained by the app.

Left alone rather than "fixed" in code, because SPEC §5 names `workoutsCount` as the stat and the
atomic counter is a deliberate talking point. **Setting `workoutsCount: 2` on that user document in
the console makes it consistent** — worth doing before the demo, since a grader will notice.

### d. Comment author names are not repaired on rename

`updateProfile` rewrites `authorName` / `authorPhotoUrl` on the user's **workouts**, but not on their
**comments**, which carry the same denormalized fields. After a rename, old comments keep the old
name. Not in the Phase 7 scope you gave me, so I did not widen it — recording it as a known
limitation for the README. The same `WriteBatch` approach would fix it, but comments live in
subcollections under every workout, so finding them needs a collection-group query.

---

## 7. Verified on the device

- **Profile (own)** — avatar, name, three stats, grid, edit / theme / logout all visible.
- **Profile (other)** — RoeiTester via a feed author tap: read-only, owner controls hidden, `—` stat.
- **Reachability** — profile opens from a feed card's author, from the details author row, and from a
  comment's author. Only the name and avatar are tappable, not the whole row, so an accidental brush
  does not navigate away from a card that is already one big button.
- **Edit profile** — prefilled from Firestore; name changed; feed cards showed the new author name
  immediately, which proves the denormalization `WriteBatch`; changed back.
- **Favourites** — favourited a workout, it appeared in the list live without a refresh; long press
  asked for confirmation; removing it left the list correct. Empty state shown when nothing is saved.
- **Theme** — toggle flipped to light, `fitshare_settings.xml` recorded `dark_theme=false`, activity
  recreated with no crash; flipped back to dark and the preference followed.
- **Banner** — appeared on going offline, cleared on reconnect, correct in both themes.
- **Delete** — throwaway workout deleted end to end, `workoutsCount` 2 → 1.
- **Unit tests** — `testDebugUnitTest` BUILD SUCCESSFUL.
- **Crashes** — `logcat -b crash` empty across every step above.

### A light-theme defect found by screenshotting, not by reading code

Workouts with no photo, and every avatar placeholder, were **invisible in light theme**. Both
placeholder drawables filled with `?attr/colorSurfaceVariant`, which in the light theme resolves to
`@color/cloud` — and so does `android:colorBackground`. A cloud rectangle on a cloud page.

Fixed with a dedicated `@color/placeholder_fill` (`#E3E9F2` light, `#223150` dark, via
`values-night`). Re-screenshotted: the avatar circle and the image-less grid cell are now clearly
visible in light, and dark is unchanged.

This is the second time a light-theme contrast defect only showed up in a screenshot — the first was
`mint_on_light` in Phase 3. Both belong in the README.

---

## 8. Notes on design choices

- **Avatar picking is gallery-only.** The add-workout screen offers camera and gallery, which costs
  it a runtime CAMERA permission, a FileProvider and temp-file cleanup — about sixty lines. An avatar
  is almost always an existing photo, so repeating all of that here would be duplication for a path
  nobody would use.
- **The theme preference is read straight from the ServiceLocator by the Fragment**, not through a
  ViewModel. It is a view-layer preference, and routing it through a ViewModel would mean handing
  that ViewModel an object holding a `Context`, which the layer rules forbid.
- **`observeUserWorkouts` sorts in Kotlin, not Firestore.** `whereEqualTo` plus `orderBy` on another
  field needs a composite index; a single equality filter needs none. This keeps the project to the
  indexes it already needs, and a profile holds a handful of workouts.
- **`view_profile_stat.xml` is `<include>`d three times** with three ids, so ViewBinding hands the
  Fragment three bindings over one layout instead of three copied blocks.
- **No `ScrollView` around the profile grid** — nesting a RecyclerView in one makes it lay out every
  cell at once. The header is fixed and only the grid scrolls.
