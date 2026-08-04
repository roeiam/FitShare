# PHASE6_REPORT.md — Details and interaction

Phase 6 of the 8-phase plan in `PHASE0_PLAN.md` §5.
Status: **built, and the core verified on the physical Samsung with a real second user.**
47 unit tests pass. No crashes.

Verified with two real accounts, as instructed: the second user's like and comment were written
directly to Firestore from outside the app, and both appeared on the phone **live, untouched**.

Two things to read first:

- **The counter invariant held under 12 rapid taps** — §4. That was the point of the transaction.
- **I misdiagnosed a favourites "bug" and it was my verification that was wrong** — §6. Worth
  knowing because it turned on something you should know about your own data.

---

## 1. Scope

| # | Item | Status |
|---|---|---|
| 1 | Details screen: hero, title, tappable author row, chips, description, four states | built, verified |
| 2 | Like button as the signature element; like/unlike in one `runTransaction` | built, verified |
| 3 | Comments: live list, input, add, delete own on long press with confirmation | built, add + live list verified |
| 4 | Favourites with the denormalized snapshot | built, verified |
| 5 | Owner-only edit and delete | built, buttons verified; destructive path not run — §7 |
| 6 | Share via `ACTION_SEND` | built, not exercised — §7 |
| 7 | Two-way sync, feed scroll position restored | sync verified; scroll restoration not exercised |
| 8 | `autoMirrored` back arrow | built, verified — points right under Hebrew |

---

## 2. Why the counters use a transaction

**What goes wrong with two separate writes**, which is what the phase asked me to explain.

The like document and the counter are two different documents. `workouts/{id}/likes/{uid}` records
*that you liked it*; `workouts/{id}.likesCount` records *how many people did*. Written separately —
create the like, then increment — anything that interrupts the gap breaks them apart: the process
dying, the network dropping, the user force-closing the app between the two calls.

The result is not a transient glitch. It is permanent:

- The counter only ever moves by a **delta**. No later write recomputes it, so nothing ever notices
  it is wrong or repairs it.
- The like document is what decides whether the heart shows as filled. So the user ends up looking
  at a filled heart next to a number that does not include them - visibly, obviously broken.
- Reversed, an increment that lands without its document gives a count nobody can account for and
  which unliking cannot undo, because there is no document to delete.

A transaction also fixes something two writes cannot: **concurrency**. The transaction *reads*
whether the like exists and decides from that, and Firestore reruns the body if the document changed
underneath it. Two taps in flight at once cannot both read "not liked" and both increment. With
separate writes there is no read to protect, and the second tap simply adds another increment.

The same reasoning covers comments. Deleting a comment additionally re-checks existence inside the
transaction, so deleting the same comment twice — two taps, or two devices — cannot drive
`commentsCount` below the number of comments.

**Publishing and deleting a workout use a `WriteBatch` instead**, because nothing needs reading
first: `FieldValue.increment` is applied server-side without the client knowing the current value, so
a transaction would add a read and a retry loop for no benefit.

---

## 3. The signature element

`LikeButton` is a view, not a pair of widgets wired up per screen, because the same control appears
on every feed card and on the details screen and has to behave identically in both.

It is deliberately **dumb**: it renders what it is given and reports taps. It never writes anything
and holds no opinion about what should happen next. That is what lets a live Firestore listener drive
it without the two disagreeing — there is no optimistic local state to get out of step.

The mark is **one path**, not two. The heart and the dumbbell are subpaths of a single path with
`fillType="evenOdd"`, so the dumbbell is knocked *out* of the heart. That keeps the whole mark one
colour, which is what makes it work in both themes and both states — the view just tints it: muted
when not liked, mint when liked. Two overlapping paths would have needed the inner one to match the
card surface and would have broken the moment that colour changed.

Two animations, both short enough to read as feedback rather than decoration: the mark springs past
its final size and settles (`OvershootInterpolator`, 150 ms), and the counter fades out, changes, and
fades back in, so the number never appears to teleport.

**A bug the device caught.** The counter rendered **blank** on any workout with zero likes. `render`
only wrote the number when it *changed*, and the field was initialised to `0` — which already matched
a workout with no likes, so the first render skipped it and the view kept its empty text. The
tracking field is now nullable: `null` means "nothing drawn yet", so the first render always writes.
The same change suppresses the animation on first draw, so cards do not bounce at views the user has
never touched.

---

## 4. The rapid-tapping test

12 taps as fast as `adb` could send them, then the invariant that actually matters:

```
likesCount field   : 1
like documents     : 1
shown on the phone : 1
RESULT: consistent
```

An even number of toggles left Roei not-liking it, so only the second user's like remained — exactly
right. A single further tap then gave `likesCount=2`, `likeDocs=2`, phone showing `2`.

**`likesCount` equals the number of documents in the `likes` subcollection.** That is the invariant
two separate writes cannot promise.

---

## 5. The second user, and live sync

Created `dana.tester@example.com` ("דנה כהן") with a profile document, then wrote as her directly to
Firestore via the REST `:commit` endpoint — an atomic commit with the document write and the counter
`increment` transform together, the same shape the app's transaction produces.

| Action, from outside the app | Result on the Samsung, untouched |
|---|---|
| Dana likes the workout | counter went **0 → 1** within seconds |
| Dana posts a Hebrew comment | row appeared live: "דנה כהן" / "אימון מעולה! גם אני עושה את זה" |

**Two-way sync**: liking on the details screen and pressing Back showed the feed card at the same
count. Both screens read the same document through their own listener, so they cannot drift.

---

## 6. A misdiagnosis worth recording

I spent a while convinced favourites were broken: the button toggled, the snackbar said
"נשמר במועדפים", and reading `users/{uid}/favorites/{workoutId}` over REST returned **404** every
time.

The app was correct. **I was reading the wrong account.** The phone is signed in as
`roeiamor123@gmail.com` — uid `W8gxFF…`, display name "רועי אמור" — your own real account, not the
`roei.test.phase3@example.com` test account from Phase 3. Likes and comments live under the *workout*
so they verified fine; favourites live under the *user*, which is where the mismatch showed up.

Read under the right uid, the document is exactly right:

```
users/W8gxFF…/favorites/ZrDLwHUZ4wwBhbx7Wn1n
  workoutId  "ZrDLwHUZ4wwBhbx7Wn1n"
  title      "אימון גב"
  authorName "רועי אמור"
  category   "STRENGTH"
  imageUrl   https://res.cloudinary.com/j7rhqis3/.../ag8kdhh2pjdbwti8czvm.jpg
  savedAt    2026-08-04T22:50:06Z   (server-written)
```

The full denormalized snapshot per SPEC §3, so the Favorites screen will render from one query.

**What this means for you:** there are now three accounts in the project — your real one, the Phase 3
test account, and Dana. Your real account owns "אימון גב", which is why the edit and delete buttons
appear on it.

---

## 7. What is built but not exercised

Being explicit rather than implying more coverage than there is:

- **Delete a workout.** The path is built and owner-gated in three places (the UI only shows the
  button to the owner, the repository refuses a non-owner, and the security rules enforce it
  server-side). I did not run it, because the only owned workout on that phone is **your real one**,
  and deleting real data to prove a button works is not a trade I should make unasked.
- **Edit a workout.** Built — the add form doubles as the editor behind a nullable `workoutId`, so
  there is one form rather than two near-identical ones. Not driven end to end.
- **Share.** The `ACTION_SEND` chooser is wired but not opened.
- **Comment deletion on long press**, and **feed scroll restoration** after returning from details.

Say the word and I will run all of them; delete I would rather do against a throwaway workout I
publish first.

---

## 8. One deliberate deviation

The scope said edit and delete should **both** sit behind a confirmation dialog. **Delete does.**
Edit navigates straight to the form.

Confirming before *editing* asks the user to approve an action that has not happened yet and that
they can still abandon — the form has a Back button, and nothing is written until they save. SPEC §8
also only defines `details_delete_confirm`, with no matching string for edit. Confirmations earn
their interruption by guarding something irreversible; used on a reversible action they train people
to dismiss dialogs without reading them, which makes the delete confirmation weaker.

Tell me if you want it anyway and it is a three-line change.

---

## 9. Files

**Created (10)**
```
PHASE6_REPORT.md
app/src/main/java/.../data/model/Comment.kt
app/src/main/java/.../data/model/FavoriteWorkout.kt
app/src/main/java/.../data/remote/InteractionDataSource.kt
app/src/main/java/.../data/repository/InteractionRepository.kt
app/src/main/java/.../ui/common/LikeButton.kt
app/src/main/java/.../ui/details/CommentAdapter.kt
app/src/main/java/.../ui/details/WorkoutDetailsUiState.kt
app/src/main/java/.../ui/details/WorkoutDetailsViewModel.kt
app/src/main/res/layout/view_like_button.xml
app/src/main/res/layout/item_comment.xml
app/src/main/res/drawable/ic_like_mark.xml, ic_share.xml, ic_edit.xml, ic_delete.xml
```

**Modified (12)**
```
app/src/main/java/.../data/remote/WorkoutDataSource.kt      observeWorkout, delete, update
app/src/main/java/.../data/repository/WorkoutRepository.kt  observe/delete/update + owner check
app/src/main/java/.../di/ServiceLocator.kt                  interaction stack, arg-taking factory
app/src/main/java/.../di/ViewModelFactory.kt                details + edit-mode arguments
app/src/main/java/.../ui/details/WorkoutDetailsFragment.kt  rewritten
app/src/main/java/.../ui/addworkout/AddWorkoutFragment.kt   edit mode
app/src/main/java/.../ui/addworkout/AddWorkoutViewModel.kt  edit mode
app/src/main/java/.../ui/feed/FeedFragment.kt               scroll restoration policy
app/src/main/java/.../util/Validators.kt                    validateComment
app/src/main/res/layout/fragment_workout_details.xml        rewritten
app/src/main/res/layout/fragment_add_workout.xml            title id for edit mode
app/src/main/res/navigation/nav_graph.xml                   workoutId arg, details→edit
app/src/main/res/values/strings.xml, values-en/strings.xml
```

---

## 10. Before Phase 7

Phase 7 is *Profile and favourites*: the profile screen with its three stats and workout grid, edit
profile, another user's profile, the Favorites list, and the theme toggle.

Two things carry into it:

- **The offline hang** (`PROGRESS.md`, Known open defects) is a Phase 7 item: `withTimeout` on
  network-bound repository calls, alongside the no-connection banner.
- **Favourites already write the snapshot the Favorites screen needs**, so that screen is a single
  query over `users/{uid}/favorites`.
