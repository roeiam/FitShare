# PHASE0_PLAN.md — FitShare

Planning document produced before any application code was written.
Sources: `CLAUDE.md`, `SPEC.md`, and an inspection of the existing generated project.

A baseline build was run against the project as generated. **It fails.** See §4.A.

No application code was written for this document. The only file modified so far is `CLAUDE.md`,
which gained an *Environment* section recording the Windows/PowerShell build command.

---

## 1. What we are building

A Hebrew (RTL) social app for sharing workouts. A user registers, publishes a workout with a
photo, category, duration and difficulty — and others browse a live feed, filter it, search it,
like, comment and save to favorites.

Architecture: a single Activity, 10 Fragment screens using XML layouts + ViewBinding, MVVM with
`LiveData`, and hand-wired DI through a `ServiceLocator`. Three server components:
**Firebase Auth** (identity), **Cloud Firestore** (all data, with real-time listeners) and
**Cloudinary** (image upload — Firebase Storage requires the paid Blaze plan). The whole project
must stay at zero cost.

The real grading criterion is not "does it work" but **"can Roei explain every line"** — with no
crashes, clean layer separation and no duplication. That constraint drives every technical
decision below.

---

## 2. Planned file tree

### Code — `app/src/main/java/com/roeiamor/fitshare/`

```
FitShareApp.kt                       Application: ServiceLocator.init + forced Hebrew locale
MainActivity.kt                      NavHost + BottomNavigationView + session-based routing

data/model/                          plain data classes, no Android imports
├── User.kt
├── Workout.kt
├── Comment.kt
├── FavoriteWorkout.kt               the denormalized snapshot from users/{uid}/favorites
├── WorkoutCategory.kt               enum
└── Difficulty.kt                    enum

data/remote/                         the only place that knows Firebase / Retrofit exist
├── AuthDataSource.kt
├── UserDataSource.kt
├── WorkoutDataSource.kt             includes callbackFlow for listeners
├── CommentDataSource.kt
├── FavoriteDataSource.kt
├── ImageUploader.kt                 interface (per SPEC §1)
├── CloudinaryImageUploader.kt       the only implementation
├── CloudinaryApi.kt                 Retrofit interface, multipart
└── CloudinaryUploadResponse.kt      secure_url

data/repository/                     each one: interface + Impl in the same file
├── AuthRepository.kt
├── UserRepository.kt
├── WorkoutRepository.kt
└── FavoritesRepository.kt

di/
├── ServiceLocator.kt                one object, all wiring visible top to bottom
└── ViewModelFactory.kt              a single generic factory for every ViewModel

ui/auth/
├── LoginFragment.kt / LoginViewModel.kt
├── RegisterFragment.kt / RegisterViewModel.kt
└── ForgotPasswordFragment.kt / ForgotPasswordViewModel.kt

ui/feed/
├── FeedFragment.kt / FeedViewModel.kt / FeedUiState.kt
└── WorkoutAdapter.kt                ListAdapter + DiffUtil

ui/addworkout/
├── AddWorkoutFragment.kt / AddWorkoutViewModel.kt / AddWorkoutUiState.kt
└── ImageSourcePicker.kt             BottomSheet: camera / gallery

ui/details/
├── WorkoutDetailsFragment.kt / WorkoutDetailsViewModel.kt / DetailsUiState.kt
└── CommentAdapter.kt

ui/favorites/
└── FavoritesFragment.kt / FavoritesViewModel.kt

ui/profile/
├── ProfileFragment.kt / ProfileViewModel.kt / ProfileUiState.kt
├── EditProfileFragment.kt / EditProfileViewModel.kt
└── WorkoutGridAdapter.kt

ui/common/
├── BaseFragment.kt                  _binding lifecycle + rendering the four states
├── StateRenderer.kt                 Loading/Empty/Error/Content logic in one place
└── LikeButton.kt                    the signature custom view (SPEC §7)

util/
├── Event.kt                         one-shot events for navigation / snackbars
├── ErrorMapper.kt                   Exception → Hebrew message, in one place
├── TimeFormatter.kt                 "לפני 5 דקות" (with unit tests)
├── Validators.kt                    input validation (with unit tests)
├── ImageCompressor.kt               inSampleSize, longest edge 1080, JPEG 80
├── ThemePreferences.kt              SharedPreferences for the theme toggle
├── NetworkMonitor.kt                the "no connection" banner
└── ViewExtensions.kt                loadImage (Glide), showSnackbar, visibleIf
```

### `res/layout/`

```
activity_main.xml
fragment_login.xml            fragment_register.xml       fragment_forgot_password.xml
fragment_feed.xml             fragment_add_workout.xml    fragment_workout_details.xml
fragment_favorites.xml        fragment_profile.xml        fragment_edit_profile.xml

item_workout.xml              item_comment.xml            item_workout_grid.xml

layout_state_loading.xml      layout_state_empty.xml      layout_state_error.xml
layout_network_banner.xml     view_like_button.xml        bottom_sheet_image_source.xml
```

### The rest of `res/`

```
values/        strings.xml  colors.xml  dimens.xml  styles.xml  themes.xml
values-night/  themes.xml  colors.xml
values-en/     strings.xml
font/          rubik_semibold.ttf  heebo_regular.ttf  heebo_medium.ttf
               font_heading.xml  font_body.xml
navigation/    nav_graph.xml
menu/          menu_bottom_nav.xml  menu_workout_owner.xml
xml/           file_paths.xml       (FileProvider for the camera)
drawable/      ic_heart_dumbbell.xml  ic_comment.xml  ic_bookmark.xml  ic_share.xml
               ic_feed.xml  ic_favorites.xml  ic_add.xml  ic_profile.xml
               ic_camera.xml  ic_gallery.xml  ic_empty_state.xml  ic_error_state.xml
               bg_chip_selected.xml  placeholder_workout.xml  placeholder_avatar.xml
```

### Tests and project root

```
app/src/test/java/.../util/TimeFormatterTest.kt  ValidatorsTest.kt  ErrorMapperTest.kt
app/src/test/java/.../repository/WorkoutRepositoryTest.kt
app/src/test/java/.../fake/FakeWorkoutDataSource.kt
app/src/androidTest/java/.../ui/feed/WorkoutAdapterTest.kt

firestore.rules   firestore.indexes.json   PROGRESS.md   README.md
```

---

## 3. Dependencies for `gradle/libs.versions.toml`

The versions below were resolved from Google Maven during planning, not recalled from memory.
All are the latest **stable** releases.

| Dependency | Version | Why it is needed |
|---|---|---|
| `com.android.application` | 9.2.1 (present) | The build plugin. **Important:** AGP 9 has built-in Kotlin support — verified that `compileDebugKotlin` exists with no Kotlin plugin applied. There is no need to add `org.jetbrains.kotlin.android`. |
| `com.google.gms:google-services` | 4.5.0 | Reads `google-services.json` and turns it into the resources the Firebase SDK reads at runtime. Without it Firebase is never initialised. |
| `androidx.navigation.safeargs.kotlin` | 2.9.8 | Generates typed Args/Directions classes from `nav_graph`. Without it arguments travel as raw Bundle strings — a classic crash source. |
| `firebase-bom` | 34.17.0 | Aligns every Firebase version into one compatible set; the modules are then declared without versions. |
| `firebase-auth` | via the BOM | Registration, login, password reset, persistent session. Server component #1. |
| `firebase-firestore` | via the BOM | The whole data model: users, workouts, likes, comments, favorites — with real-time listeners and `runTransaction` for counters. Server component #2. |
| `kotlinx-coroutines-play-services` | 1.10.2 | Provides `await()`, which turns a Firebase `Task<T>` into a `suspend` call. Without it we fall back to nested callbacks. |
| `navigation-fragment-ktx` | 2.9.8 | The NavHost and the actual Fragment swapping — the single-Activity architecture. |
| `navigation-ui-ktx` | 2.9.8 | One line that wires `BottomNavigationView` to the NavController and keeps item selection in sync. |
| `lifecycle-viewmodel-ktx` | 2.11.0 | `viewModelScope` — coroutines cancelled automatically when the ViewModel dies. The main defence against rotation crashes. |
| `lifecycle-livedata-ktx` | 2.11.0 | `LiveData`, `switchMap`, and `asLiveData()` for converting the Firestore `callbackFlow`. |
| `lifecycle-runtime-ktx` | 2.11.0 | `repeatOnLifecycle` / `flowWithLifecycle` for safe collection inside a Fragment. |
| `fragment-ktx` | 1.8.9 | `by viewModels { factory }` and `registerForActivityResult` — the basis of every Fragment in the project. |
| `activity-ktx` | 1.13.0 (present) | `ActivityResult` contracts for picking an image and taking a photo. |
| `material` | 1.14.0 (present) | MaterialCardView, ChipGroup, TextInputLayout, FAB, BottomNavigationView, Snackbar, the Material3 DayNight theme. The backbone of the UI. |
| `constraintlayout` | 2.2.1 (present) | The layout of every screen; critical for RTL because `constraintStart/End` mirror automatically. |
| `recyclerview` | 1.4.0 | The feed, the comments, the profile grid. Declared explicitly rather than inherited by accident through material. |
| `swiperefreshlayout` | 1.2.0 | The pull-to-refresh in the feed, explicitly required by SPEC §5. |
| `glide` | 5.0.9 | Loads Cloudinary images with caching, placeholders and lifecycle awareness. Manual `ImageView` loading means leaks and main-thread stalls. |
| `retrofit` | 3.0.0 | The Cloudinary client as a typed interface instead of hand-rolled `HttpURLConnection`. |
| `retrofit-converter-gson` | 3.0.0 | Parses the Cloudinary JSON response so we can read `secure_url`. |
| `okhttp` | 5.4.0 | Retrofit's HTTP layer; declared explicitly so we control timeouts. |
| `okhttp-logging-interceptor` | 5.4.0 | Logs upload requests in debug builds only. Saves hours of multipart debugging. |
| `junit` | 4.13.2 (present) | The unit tests from SPEC §12. |
| `kotlinx-coroutines-test` | 1.10.2 | `runTest`, for testing the repository against a fake data source. |
| `androidx-junit` + `espresso-core` | present | The single instrumented test on the adapter. |

**Deliberately not added:** no Hilt, no Compose, no Room, no WorkManager, no Paging, no Coil
(Glide is enough), no Timber.

---

## 4. Contradictions and ambiguities

> **All decisions below are RESOLVED.** Roei's answers, recorded 2026-08-04:
>
> | § | Decision |
> |---|---|
> | A | **Option B** — stay on the installed `compileSdk 36`. Pin `core-ktx` → 1.18.0 and `glide` → 5.0.7. Do not install API 37. |
> | B | Keep `values-en`. Explain it in the README. |
> | C | Approved — plain enums, one mapping function in the UI layer. |
> | D | Approved — one `ProfileFragment` with a nullable `userId`. |
> | E | Approved — feature #20 dropped and struck from `SPEC.md`. |
> | F | Approved — "likes" = likes received (sum of `likesCount` over own workouts). |
> | G | Approved — repository returns `Flow<Result<T>>`, ViewModel ends with `asLiveData()`. Rule updated in `CLAUDE.md`. |
> | H | Accepted — documented in the README under "known limitations". |
> | I | Roei supplies the fonts by hand. Never download or substitute; stop and report a missing file. |
> | Camera | Keep both. In Phase 5 build gallery first and finish it, then add camera. |
>
> These are now mirrored into `SPEC.md` and `CLAUDE.md`, which are authoritative.
> The text below is preserved as the record of why each decision was needed.

### A. Blocker: the project as generated does not compile

```
Dependency 'androidx.core:core-ktx:1.19.0' requires ... version 37 or later
:app is currently compiled against android-36.1
```

Both `core-ktx:1.19.0` and `material:1.14.0` require `compileSdk 37`, but `app/build.gradle.kts`
is set to 36.1 — and **only** `android-36.1` is installed on this machine. This is a template
that was generated broken, not something caused by earlier work here.

- **Option A (recommended):** install API 37 via the SDK Manager and set `compileSdk = 37`.
  Consistent with SPEC's "target latest stable" and keeps every library current.
- **Option B:** stay on 36 and pin `core-ktx` and `material` back to compatible versions.

This decision is needed before Phase 1. **It will not be made unilaterally.**

### B. `values-en/strings.xml` is dead code

`CLAUDE.md` rule 3 requires English translations in `values-en`, but `SPEC §9.2` mandates
`setApplicationLocales("he")` unconditionally, and `§9.7` stresses that the app must render Hebrew
even on an English device. The consequence: `values-en` will never be loaded. Recommendation: keep
it anyway (it is cheap, and it shows the lecturer that strings are properly externalised) — just
be ready to explain this in class.

### C. The enums as written in SPEC do not compile

```kotlin
enum class WorkoutCategory(@StringRes val labelRes: Int) {
    STRENGTH, RUNNING, HIIT, ...   // ← no constructor argument
}
```

They need `STRENGTH(R.string.category_strength)` and so on. **However** — `CLAUDE.md` requires
`data/model/` to contain "no Android imports", and `@StringRes` and `R` are exactly that. A real
contradiction.

**Recommendation:** keep the enums completely plain and map category → string in a single function
in the UI layer. Keeps the model pure and honours the zero-duplication rule.

### D. `ProfileFragment` vs `UserProfileFragment`

SPEC defines two Fragments sharing the same layout. Two near-identical Fragments are precisely the
duplication that costs marks. **Recommendation:** one Fragment with a `userId: String?` argument —
`null` means my own profile (with edit/logout), otherwise a read-only view.

### E. Search + pagination + real-time do not coexist

Firestore has no substring search, so search must run client-side over whatever is loaded.
Feature #20 (pagination at 20 items) makes search partial and wrong — the user searches for a
workout that exists and does not find it. **Recommendation: drop #20.** It is last on the extras
list, and it is the one most likely to produce an embarrassing bug during the demo.

### F. The "likes" profile statistic is undefined

`§5` asks for three numbers: workouts · likes · favorites. It is unclear whether "likes" means
likes received or likes given. **Recommendation:** sum `likesCount` across my own workouts — they
are already loaded for the profile grid, so this costs zero additional reads.

### G. `LiveData<Result<T>>` makes the feed awkward

`CLAUDE.md` requires live streams to return `LiveData<Result<T>>`. But the feed must combine a live
stream with category filtering, search and sorting, and doing that in LiveData requires nested
`MediatorLiveData` — less readable, not more. **Recommendation:** the repository returns
`Flow<Result<T>>`, the ViewModel combines and finishes with `.asLiveData()`. The Fragment still
sees only `LiveData`, exactly as the rule demands.

### H. The security rules do not protect the counters

The rule lets any signed-in user update `likesCount`, including by 1000 at a time. This cannot be
enforced without Cloud Functions (which require Blaze). Acceptable for a course project — but worth
a sentence in the README, because a lecturer may well ask.

### I. Fonts

`§7` requires bundling Rubik and Heebo as `.ttf` files. That is the plain, correct approach, but it
requires downloading from Google Fonts. This will be handled in Phase 1, and any file that turns
out to be unavailable will be flagged rather than silently substituted.

---

## What is more complex than a course project requires

- **Feature #20, pagination** — drop it. See §4.E.
- **Camera capture** (`TakePicture` + `FileProvider` + the `CAMERA` runtime permission) — the
  largest crash surface in the project. Gallery-only would reduce risk substantially. SPEC requires
  both, so both will be built, but this is the area that will need the most manual testing.
- **Features 18–19** (no-connection banner, sorting) — good, but only real once 1–17 fully hold.
  Not before.
- **The instrumented test** needs a running emulator; run it once in Phase 8, not on every build.

Everything else — 10 screens, 17 features, four states per screen, RTL, light/dark — is a large
volume but legitimate for a final project, and will not be reduced unilaterally.

---

## On XML Views and hand-written DI

Asked directly whether this is a mistake: **it is the right decision, and would not be changed even
if that were an option.** The criterion here is explaining every line to a lecturer. A
`ServiceLocator` you can read top to bottom is fully defensible; Hilt generates code nobody in the
room has seen, and if asked "where did this repository come from", the answer "an annotation" is a
bad answer. Likewise, the Fragment → ViewModel → Repository → DataSource split is exactly the
"class separation" the rubric measures, and it is visible in the directory tree. Compose would blur
that and add state management that would itself need explaining. This stays as specified.

---

## 5. Work order — 8 phases

| # | Phase | Deliverable | Done when |
|---|---|---|---|
| **1** | **Build and design foundation** | The `compileSdk` decision, all dependencies in `libs.versions.toml`, the google-services + SafeArgs plugins, `viewBinding`+`buildConfig`, `buildConfigField` for Cloudinary, the Manifest (permissions, RTL, FileProvider), `FitShareApp` with the forced Hebrew locale, colors/dimens/styles/themes + night, fonts, the full `strings.xml`, a `ServiceLocator` skeleton | `assembleDebug` passes |
| **2** | **Navigation and screen skeleton** | `nav_graph.xml` with 10 destinations, `MainActivity` + BottomNav with hiding on auth destinations, the 3 state layouts, `BaseFragment` + `StateRenderer`, `ViewModelFactory`, empty Fragments | Full navigation between every screen, zero crashes |
| **3** | **Authentication** | `User`, `AuthDataSource`, `AuthRepository`, the 3 auth screens, `Validators`, `ErrorMapper`, session routing with `popUpTo` | Register → feed; logout and log back in; password reset email arrives |
| **4** | **Feed (read path)** | `Workout`, the enums, `WorkoutDataSource` with `callbackFlow`, `WorkoutRepository`, `item_workout`, `WorkoutAdapter`, category filtering, search, SwipeRefresh, the 4 states | The feed updates live from documents created by hand in Firestore |
| **5** | **Create a workout** | `ImageCompressor`, `CloudinaryApi` + `CloudinaryImageUploader`, camera/gallery picking with permission handling, the form with validation, publish + `workoutsCount` | A photo from the phone appears in the feed; a failed upload leaves the form filled |
| **6** | **Details and interaction** | Hero image, tappable author row, like via `runTransaction` + the animated `LikeButton`, comments (add/list/delete), favorites, share, owner edit/delete | Counters are always correct, verified on two devices at once |
| **7** | **Profile and favorites** | Own profile, the 3 statistics, the workout grid, edit profile with avatar, another user's profile, the favorites screen, logout, theme toggle | All 17 MVP features work |
| **8** | **Hardening, security and tests** | `firestore.rules` + `firestore.indexes.json` (and pasted into the console), 6–10 tests, a full pass over RTL / light-dark / rotation / no network / very long Hebrew text, README + PROGRESS + demo script | The SPEC §13 checklist is ticked for every screen |

Every phase ends with `.\gradlew.bat assembleDebug`, a `PROGRESS.md` update, and a conventional
commit.

---

## Open decisions

One question must be answered before Phase 1 — **§4.A**: install API 37 and move to
`compileSdk = 37`, or stay on 36 and pin `core-ktx`/`material` back. Option A is recommended.

The other four recommendations (D — a single profile Fragment, E — drop pagination, C — plain
enums, G — Flow inside the repository) are also open. If the instruction is simply "go ahead"
without detail, the recommendations will be followed and each one called out explicitly in the
phase where it enters the code.
