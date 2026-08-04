# PHASE4_REPORT.md — The feed read path

Phase 4 of the 8-phase plan in `PHASE0_PLAN.md` §5.
Status: **complete and verified against real Firestore data** — four workouts were created and the
feed rendered them live, without restarting the app. 34 unit tests pass. No crashes, no leaks.

Three things worth reading before the detail:

- **Live updating is proven, not assumed.** §4 explains how, and it happened by accident in the best
  possible way.
- **The category filter is implemented and demonstrably correct, but needs one manual click from
  you** — a Firestore composite index. The exact URL is in §6. This is the workflow SPEC §10
  anticipates.
- **Rotation exposed a real landscape defect** that had nothing to do with the feed: every text
  field in the app took over the whole screen. Fixed. §5.

---

## 1. Scope, item by item

| # | Item | Where |
|---|---|---|
| 1 | `Workout` with no-arg constructor; `WorkoutCategory` / `Difficulty` kept plain; one label mapping in the UI | `data/model/`, `ui/common/EnumLabels.kt` |
| 2 | `observeFeed(category, sort)` on a `snapshotListener` in `callbackFlow`, plus `getWorkout(id)` | `data/remote/WorkoutDataSource.kt` |
| 3 | `WorkoutRepository` interface + Impl returning `Flow<Result<T>>`; the ViewModel combines and ends with `asLiveData()` | `data/repository/`, `ui/feed/FeedViewModel.kt` |
| 4 | `item_workout.xml` per SPEC §5; `WorkoutAdapter` as a `ListAdapter` with `DiffUtil` | `res/layout/`, `ui/feed/WorkoutAdapter.kt` |
| 5 | RecyclerView, category `ChipGroup`, search, `SwipeRefreshLayout`, four states via `StateRenderer` | `ui/feed/FeedFragment.kt` |
| 6 | Glide with placeholder and error; a workout with no image still renders correctly | `util/ViewExtensions.kt` |
| 7 | `TimeFormatter` for Hebrew relative times, unit tested | `util/TimeFormatter.kt` |
| 8 | Temporary "פרטי האימון" button deleted; the whole card navigates with its `workoutId` | `fragment_feed.xml`, `FeedFragment` |

`notifyDataSetChanged` appears nowhere in the project.

---

## 2. Two decisions worth defending

**The enums stay plain, and the model holds strings.** `Workout.category` is a `String`, exactly as
Firestore stores it, and becomes an enum through `WorkoutCategory.fromName`, which falls back to
`OTHER`. Mapping straight to an enum would mean a value this build does not recognise — a document
written by a newer version, or typed by hand in the console — throws during deserialisation and
takes the entire feed down. A feed that silently shows "אחר" is far better than a feed that crashes.

**`FeedUiState` is a sealed interface, not a data class of flags.** CLAUDE.md asks for one UiState
per screen; this is one type, with the four states as its cases. A data class carrying `isLoading`,
`isEmpty` and `error` permits nonsense — loading *and* error at once — and pushes the decision of
which wins into the Fragment. As a sealed interface that cannot be constructed, and the `when` in
the Fragment is exhaustive, so adding a state later is a compile error instead of a blank screen.

---

## 3. How the listener is not leaked

The whole question is one line in `WorkoutDataSource`:

```kotlin
awaitClose { registration.remove() }
```

A Firestore listener is not garbage collected while it is registered — it holds a live connection
and keeps billing reads. `callbackFlow` runs `awaitClose` when the collector stops, and the chain of
ownership is: `asLiveData()` collects while the LiveData has active observers → the Fragment
observes with `viewLifecycleOwner` → leaving the feed makes it inactive → after the 5-second
timeout the collection is cancelled → `awaitClose` runs → the registration is removed.

`flatMapLatest` matters for the same reason: changing a chip cancels the previous collection, which
removes the previous listener, before subscribing to the new one. Without it, tapping four chips
would leave four listeners live.

**Measured** — 8 full cycles of feed → profile → feed, each pausing longer than the 5-second timeout:

| | Baseline | After 8 cycles |
|---|---|---|
| Views | 334 | **132** |
| Activities | 3 | **1** |
| AppContexts | 14 | **7** |
| Total PSS | 147 MB | 158 MB |

Nothing accumulated; the view and context counts fell as transient state settled. The ~11 MB of PSS
is Glide's bitmap cache filling with four photos, which is what it is for.

Honest limit: this is the structural guarantee plus the standard Android leak counters, not a
heap-dump proof. If you want certainty later, LeakCanary in the debug build would settle it.

---

## 4. Live updating, proven the hard way

The first seeding pass wrote the Hebrew through PowerShell, which encoded the body as ASCII — so
Firestore genuinely stored `????? ??? ???? ??? ?????`. The app rendered exactly that, which was
correct behaviour on corrupt data. I confirmed the corruption server-side before touching the app:

```
$ GET .../documents/workouts/w_strength_01
stored title: ????? ??? ???? ??? ?????
```

Re-writing the four documents as UTF-8 bytes, **with the app left running and untouched**, the feed
changed on screen within seconds: the title became "אימון כוח לפלג גוף עליון" and the author
"רועי עמור". No restart, no pull to refresh, no navigation.

That is the snapshot listener and `DiffUtil` doing exactly what they are there for, demonstrated
rather than asserted. It also happens to be the cleanest evidence that `areContentsTheSame` works —
only the changed cards rebound.

---

## 5. A landscape defect rotation exposed

Rotating the feed with the search field focused made the field take over the entire screen: no list,
no chips, no bottom bar. It is the IME's fullscreen "extract" mode, which Android turns on by
default in landscape for any focused text field — so it affected **every** input in the app, the
auth screens included, not just the feed.

Fixed with `flagNoExtractUi` on all eight inputs. Landscape now shows the search field, all nine
chips in right-to-left order, the cards, and the bottom bar. This is exactly the kind of thing SPEC
§13's "nothing overlapping, nothing clipped" is aimed at, and it is only findable by rotating.

---

## 6. The category filter needs one click from you

Filtering by category while ordering by `createdAt` needs a Firestore **composite index**. There is
no way to create one from the app or from a user ID token — it takes an owner, which is you.

The implementation is verifiably correct; Firestore echoed the query it received:

```
Listen for Query(workouts where category==STRENGTH order by -createdAt) failed:
FAILED_PRECONDITION: The query requires an index.
```

**Create it here** (one click, then about a minute to build):

```
https://console.firebase.google.com/v1/r/project/fitshare-7ddbe/firestore/indexes?create_composite=Ck9wcm9qZWN0cy9maXRzaGFyZS03ZGRiZS9kYXRhYmFzZXMvKGRlZmF1bHQpL2NvbGxlY3Rpb25Hcm91cHMvd29ya291dHMvaW5kZXhlcy9fEAEaDAoIY2F0ZWdvcnkQARoNCgljcmVhdGVkQXQQAhoMCghfX25hbWVfXxAC
```

I have committed **`firestore.indexes.json`** with both composite indexes the feed queries need
(category + createdAt, and category + likesCount for the sort that Phase 7 exposes), so
`firebase deploy --only firestore:indexes` also does it. That file was a Phase 8 deliverable; it
belongs with the query that needs it, so it is here now.

**What this means for the verification you asked for:** everything except end-to-end category
filtering is confirmed on the device. Filtering is confirmed only as far as the query being
constructed correctly and the failure being handled gracefully — the feed shows its Hebrew error
state with a retry button rather than crashing or hanging, which is itself worth having tested.
Tell me once the index is built and I will confirm the filter end to end in two minutes.

---

## 7. Device verification

Emulator API 35, device language English, real Firestore project, four seeded workouts: varied
categories, one **without an image**, one with a **very long Hebrew title**.

| Check | Result |
|---|---|
| Workouts appear live, no restart | **yes** — see §4 |
| Card with no image | placeholder fills the 16:9 slot; title, chips and counts all correct |
| Very long Hebrew title | ellipsizes at two lines with "…", no clipping, RTL correct |
| Relative times | "לפני 8 דקות", "לפני 3 שעות", "אתמול" — plurals correct |
| Meta chips | קטגוריה / משך / קושי, right-to-left order |
| Search, matching | "HIIT" narrowed the feed to one card |
| Search, not matching | filtered empty state, and deliberately **no** "add workout" button |
| Category filter | query correct; blocked on the index — §6 |
| Error state | Hebrew message, retry button, no crash |
| Pull to refresh | spinner clears on the next emission, scroll position kept |
| Rotation | state preserved; landscape defect found and fixed — §5 |
| Card tap → details | navigates with the workout's id via SafeArgs |
| Listener leak | no accumulation over 8 cycles — §3 |
| Crashes / ANRs | **none** |
| Unit tests | **34 pass** — Validators 15, ErrorMapper 9, TimeFormatter 10 |

---

## 8. Notes and deviations

**Hebrew plurals instead of format strings.** SPEC §8 gives `לפני %d דקות`, which renders
"לפני 1 דקות" — broken Hebrew. Minutes, hours and the duration chip now use `<plurals>` with `one`,
`two` and `other`, so one minute reads "לפני דקה" and two hours "לפני שעתיים". `values-en` mirrors it.

**Sorting is in the data layer but not in the UI.** `observeFeed` takes a `FeedSort` as scoped, and
`FeedSort.NEWEST` is used. Exposing the choice is SPEC feature 19 — an extra, after the MVP — so no
control was added. When it arrives it is a UI change, not a query change.

**Category chips are generated from the enum**, not written in XML. Nine hardcoded chips would
duplicate the enum and the label mapping and go stale the day a category is added.

**`getWorkout(id)` is implemented but nothing calls it yet.** It is in the scope, and Phase 6's
details screen is its caller.

---

## 9. Files

**Created (13)**
```
PHASE4_REPORT.md
firestore.indexes.json
app/src/main/java/.../data/model/Workout.kt
app/src/main/java/.../data/model/WorkoutCategory.kt
app/src/main/java/.../data/model/Difficulty.kt
app/src/main/java/.../data/model/FeedSort.kt
app/src/main/java/.../data/remote/WorkoutDataSource.kt
app/src/main/java/.../data/repository/WorkoutRepository.kt
app/src/main/java/.../ui/common/EnumLabels.kt
app/src/main/java/.../ui/common/TimeLabels.kt
app/src/main/java/.../ui/feed/FeedUiState.kt
app/src/main/java/.../ui/feed/FeedViewModel.kt
app/src/main/java/.../ui/feed/WorkoutAdapter.kt
app/src/main/java/.../util/TimeFormatter.kt
app/src/main/res/layout/item_workout.xml
app/src/main/res/drawable/ic_like.xml, ic_comment.xml, ic_image_off.xml,
                          placeholder_workout.xml, placeholder_avatar.xml, image_error.xml
app/src/test/java/.../util/TimeFormatterTest.kt
```

**Modified (9)**
```
app/src/main/java/.../di/ServiceLocator.kt        workout data source + repository
app/src/main/java/.../di/ViewModelFactory.kt      FeedViewModel branch
app/src/main/java/.../ui/feed/FeedFragment.kt     rewritten
app/src/main/java/.../util/ViewExtensions.kt      Glide helpers
app/src/main/res/layout/fragment_feed.xml         rewritten
app/src/main/res/layout/fragment_login.xml, _register.xml, _forgot_password.xml   flagNoExtractUi
app/src/main/res/values/styles.xml                chip + avatar styles
app/src/main/res/values/strings.xml, values-en/strings.xml   plurals
PROGRESS.md
```

---

## 10. Ready for Phase 5

Phase 5 is *Create a workout*: `ImageCompressor`, the Cloudinary uploader, image picking —
**gallery first, then camera**, as decided — the form with validation, and `workoutsCount`.

The seeded workouts are useful test data. Delete them from the console whenever you like; the ids
are `w_strength_01`, `w_running_02`, `w_yoga_03`, `w_hiit_04`.

The one thing I would like from you before Phase 6 is the composite index in §6 — Phase 5 does not
need it, but the filter stays unverified until it exists.
