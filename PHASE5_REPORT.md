# PHASE5_REPORT.md — Create a workout

Phase 5 of the 8-phase plan in `PHASE0_PLAN.md` §5.
Status: **complete and verified end to end** — a workout was published from the gallery with a photo,
and confirmed in Firebase Auth's Firestore **and** in Cloudinary. 47 unit tests pass. No crashes.

Three things worth reading first:

- **The offline-registration defect you flagged is recorded in `PROGRESS.md`, unfixed**, as
  instructed — §7.
- **Compression is verified by measurement, not assertion**: the uploaded JPEG is 1080×810 and
  120 KB, from a 1600×1200 / 223 KB source. §3.
- **Your phone was connected but locked.** I did not touch it. Everything was verified on the
  emulator. §6 has a note about it that is worth your attention.

---

## 1. Scope, in the order you asked for

| # | Item | Where |
|---|---|---|
| 1 | `ImageCompressor` — `inSampleSize`, 1080px cap, JPEG 80, cacheDir; sizing maths unit tested | `util/ImageCompressor.kt`, 13 tests |
| 2 | `CloudinaryApi` (Retrofit multipart), `CloudinaryUploadResponse`, `CloudinaryImageUploader` reading `secure_url`, preset from `BuildConfig` | `data/remote/` |
| 3 | Gallery via `PickVisualMedia` — **built and verified on the device first** | `AddWorkoutFragment` |
| 4 | Camera via `TakePicture` + FileProvider + runtime permission, degrading gracefully | `AddWorkoutFragment`, `CameraPhotoFile`, `ImageSourcePicker` |
| 5 | The form: validation, category and difficulty chips, upload progress, failure keeps the form filled | `fragment_add_workout.xml`, `AddWorkoutViewModel` |
| 6 | Workout document + `workoutsCount` in one batch; navigate to the feed | `WorkoutDataSource.createWorkout`, `WorkoutRepositoryImpl` |

**The order was followed literally.** The gallery path was built, installed, and a real workout was
published and confirmed in Firestore and Cloudinary *before* a single line of camera code existed.

---

## 2. Two decisions worth defending

**The image is uploaded before the document is written.** The other order would put a workout in
everyone's feed with no photo and then try to patch it — and a failure there leaves that hole
permanently. Uploading first means a failure costs only an orphaned image in Cloudinary, which
nobody sees. Proved in §5: two failed publishes left **zero** workout documents and `workoutsCount`
untouched.

**The workout and the counter go in one `WriteBatch`.** Either the workout appears and the counter
moves, or neither does. As two separate writes, a failure between them leaves the profile claiming a
number that does not match the feed, and nothing ever corrects it because the counter only moves by
a delta. A batch rather than a transaction because nothing needs reading first —
`FieldValue.increment` is applied server-side without the client knowing the current value, so a
transaction would add a read and a retry loop for no benefit.

---

## 3. Compression, measured

The pure sizing functions were extracted over an Android-free `ImageDimensions` so the arithmetic —
the part with the edge cases — is unit testable on the JVM. 13 tests cover the power-of-two
constraint, that sampling never overshoots below the cap, that a small image is not upscaled, and
that a 20000×5 panorama does not round its short side to zero and crash `createScaledBitmap`.

`inSampleSize` deliberately stops one step early and leaves the exact fit to a scale pass, because
overshooting throws away detail that cannot be recovered.

**Measured on the real upload:**

| | Source | Uploaded |
|---|---|---|
| Dimensions | 1600×1200 | **1080×810** |
| Size | 223,444 bytes | **120,264 bytes** |

OkHttp logged the request body at 120,649 bytes and Cloudinary returned `200 OK` in 4.5 s. The
compressed file is deleted in a `finally` block whether the upload succeeds or fails.

**EXIF orientation is handled.** A camera does not rotate its pixels; it writes a tag, and
`BitmapFactory` ignores it. Without this a photo taken upright uploads sideways and stays that way
forever, because the tag is lost on re-encode. This is why `androidx.exifinterface` was added — the
only new dependency, checked for `minCompileSdk` first as `CLAUDE.md` requires (it is 34).

---

## 4. The camera degrades gracefully

`CAMERA` is declared in the manifest, so `ACTION_IMAGE_CAPTURE` requires it to be granted. The
permission is requested when the user taps "צילום", not when the screen opens, so the prompt arrives
with an obvious reason attached.

**Denial is not an error.** It shows "בלי הרשאת מצלמה אפשר לבחור תמונה מהגלריה" and the form carries
on untouched. Verified by revoking the permission and denying the prompt: snackbar shown, no crash,
gallery still fully usable.

Backing out of the camera deletes the zero-byte file `TakePicture` has already created, so the cache
does not slowly fill with empty JPEGs.

`ImageSourcePicker` returns its answer through the Fragment Result API rather than a captured
lambda. A dialog fragment is destroyed and recreated on a configuration change, and a lambda held
from construction would not survive — the sheet would come back alive with a dead listener and the
buttons would silently do nothing.

---

## 5. Device verification

Emulator API 35, device language English, real Firebase and real Cloudinary.

### The happy path, confirmed on both servers

```
Firestore  workouts/yhgt5JyKjrjYFWipScOF
  title           "Gallery upload te"
  category        "STRENGTH"      difficulty  "MEDIUM"
  durationMinutes 35              likesCount  0        commentsCount 0
  authorId        JoXN6tV…        authorName  "RoeiTester"
  imageUrl        https://res.cloudinary.com/j7rhqis3/image/upload/v1785875270/giw2bwzqqfps0iek6xec.jpg
  createdAt       2026-08-04T20:27:52.543Z   (server-written)
  id              yhgt5JyKjrjYFWipScOF       (mirrored into the document)

Firestore  users/JoXN6tV…
  workoutsCount   0  →  1
```

The Cloudinary URL returns `200 OK` and the bytes are the compressed 1080×810 image. The workout
appeared **at the top of the feed** with "כרגע", carried there by the Phase 4 live listener.

### Failure paths

| Path | Result |
|---|---|
| Cancel the picker | returns to the form, previously chosen photo untouched, no crash |
| Deny the camera permission | Hebrew snackbar pointing at the gallery; form fully usable |
| **Kill the network mid-upload** | `ConnectException` after 5.9 s → "אין חיבור לאינטרנט"; **form kept every field, the photo, and both chips**; button re-enabled to retry |
| Publish while fully offline | `UnknownHostException` in 98 ms → same message, same preservation |
| Invalid form | title "ab" → "שם האימון חייב להכיל בין 3 ל־60 תווים"; duration 999 → "משך האימון חייב להיות בין 1 ל־600 דקות"; **publish disabled** |
| After two failed publishes | **0 new documents, `workoutsCount` still 1** — no partial state |
| Crashes / ANRs | **none** |

### A defect found and fixed

The bottom sheet's icons did not render: `MaterialButton` ignores `android:drawableStart` and needs
`app:icon`. Fixed and re-verified — the icons now sit on the start side, which is the right under
Hebrew, with no mirroring work needed.

---

## 6. Your phone, and why I left it alone

Partway through, the emulator was replaced by a connected physical device: **Samsung SM-A305F,
Android 11 (API 30)**, with FitShare already installed. It was **locked**, so I did not drive it —
automating a locked personal phone is not something to do without being asked.

Worth knowing, though: that device is a genuinely useful compatibility target, because API 30
exercises paths the API 35 emulator never touches.

- **The locale fix from Phase 2 takes a different route there.** Below API 33 there is no system
  `LocaleManager` — confirmed, `cmd locale` does not exist on it — so AppCompat persists the locale
  itself through the `AppLocalesMetadataHolderService` I added. That path has never been exercised.
- **`PickVisualMedia` falls back** to `ACTION_OPEN_DOCUMENT` below API 33 rather than using the
  system photo picker.

Both should work, but "should" is doing the work in that sentence. If you unlock the phone and leave
it on, I will verify both on it in a few minutes.

---

## 7. The offline-registration defect — recorded, not fixed

Added to `PROGRESS.md` under **Known open defects**, exactly as instructed, with:

- what happens (registration spins forever with no error, on a device with no internet);
- why (Firestore is offline-first — it queues the write locally and only resolves the `Task` once
  the server acknowledges, so `await()` never returns; Auth reports its network error correctly, it
  is the `users/{uid}` write that hangs);
- that it is **not specific to registration** — every Firestore-writing repository call can hang the
  same way: publishing, liking, commenting, favouriting, editing a profile;
- the Phase 7 fix: `withTimeout` on network-bound repository calls, alongside the no-connection
  banner, so a stalled call becomes a real failure that `ErrorMapper` turns into `error_no_network`.

**Not touched in Phase 5.**

Worth noting how it interacts with what was built here: publishing *with* a photo fails correctly
offline, because Cloudinary goes over OkHttp, which times out and throws a real `IOException`. It is
only the Firestore write that can hang — so a workout published **without** a photo while offline
would hit the same defect. That is another reason the Phase 7 fix belongs at the repository layer
rather than being patched into one screen.

---

## 8. Notes

**Test text was Latin, not Hebrew.** `adb shell input text` cannot send Hebrew, so the workout
titles typed during verification are English. Hebrew rendering is proven throughout the rest of the
app, including the seeded workouts in Phase 4. It also truncated the last two characters of each
field — an adb artifact, visible as "Gallery upload te", not an app bug.

**`ChipBuilder` was extracted.** The feed's category filter and the form's two chip rows are the same
job. The feed's version was refactored onto the shared helper, so the chips are generated from the
enums in one place and every label still comes from `ui/common/EnumLabels.kt`.

**The photo slot uses a fixed height, not a 16:9 constraint.** `layout_constraintDimensionRatio`
only works inside a `ConstraintLayout`, and the form is a vertical `LinearLayout`; the attribute
would have been silently ignored.

---

## 9. Files

**Created (10)**
```
PHASE5_REPORT.md
app/src/main/java/.../data/model/WorkoutDraft.kt
app/src/main/java/.../data/remote/CloudinaryApi.kt
app/src/main/java/.../data/remote/CloudinaryImageUploader.kt
app/src/main/java/.../data/remote/ImageUploader.kt
app/src/main/java/.../ui/addworkout/AddWorkoutViewModel.kt
app/src/main/java/.../ui/addworkout/CameraPhotoFile.kt
app/src/main/java/.../ui/addworkout/ImageSourcePicker.kt
app/src/main/java/.../ui/common/ChipBuilder.kt
app/src/main/java/.../util/ImageCompressor.kt
app/src/main/res/layout/bottom_sheet_image_source.xml
app/src/main/res/drawable/ic_camera.xml, ic_gallery.xml
app/src/test/java/.../util/ImageCompressorTest.kt
```

**Modified (11)**
```
gradle/libs.versions.toml, app/build.gradle.kts     exifinterface
app/src/main/java/.../data/remote/WorkoutDataSource.kt   createWorkout batch
app/src/main/java/.../data/repository/WorkoutRepository.kt   publish orchestration
app/src/main/java/.../di/ServiceLocator.kt          Retrofit, OkHttp, uploader
app/src/main/java/.../di/ViewModelFactory.kt        AddWorkoutViewModel
app/src/main/java/.../ui/addworkout/AddWorkoutFragment.kt
app/src/main/java/.../ui/feed/FeedFragment.kt       uses ChipBuilder
app/src/main/java/.../util/Validators.kt            workout form rules
app/src/main/res/layout/fragment_add_workout.xml
app/src/main/res/values/dimens.xml
PROGRESS.md
```

---

## 10. Ready for Phase 6

Phase 6 is *Details and interaction*: the hero image, the tappable author row, the like button as
the animated signature element with its `runTransaction`, comments, favourites, share, and the
owner's edit and delete.

`getWorkout(id)` is already implemented and finally gets its caller.

Test data in the project: `w_strength_01`, `w_running_02`, `w_yoga_03`, `w_hiit_04`, and the newly
published `yhgt5JyKjrjYFWipScOF`. Delete any of them whenever you like.
