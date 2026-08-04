# SPEC.md — FitShare

**One line:** a Hebrew social app where people post the workout they just did, with a photo, and others browse, like, comment and save it.
**In-app tagline:** אימונים, השראה והתקדמות במקום אחד

---

## 1. Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | **XML layouts + Fragments + ViewBinding** (no Compose, no DataBinding) |
| Components | Material Components (`com.google.android.material`) |
| Min SDK / Target | 24 / latest stable |
| Navigation | Navigation Component, single Activity, `nav_graph.xml`, SafeArgs |
| Architecture | MVVM: Fragment → ViewModel (LiveData) → Repository → DataSource |
| DI | Hand-written `ServiceLocator` + `ViewModelFactory` (no Hilt) |
| Auth | Firebase Authentication (Email/Password) |
| Database | Cloud Firestore |
| Image upload | Cloudinary unsigned upload, Retrofit + OkHttp multipart |
| Image loading | Glide |
| Async | Kotlin Coroutines (`viewModelScope`, `callbackFlow`) |
| Lists | RecyclerView + `ListAdapter` + `DiffUtil` |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |

Use BOMs where available: `com.google.firebase:firebase-bom` (v34+, **no `-ktx` artifacts** — KTX was merged into the main modules).
Enable `buildFeatures { viewBinding = true; buildConfig = true }`.

### Cost model — the project must stay at zero, with no credit card
- **Firebase Auth**: free, unlimited.
- **Cloud Firestore on the Spark plan**: free — 50k reads / 20k writes / 20k deletes per day, 1 GiB stored. No billing account is linked, so billing is impossible.
- **Cloudinary Free tier**: free, no card required.
- **Firebase Storage is NOT used.** It now requires the paid Blaze plan; on Spark every bucket call fails with 402/403. That is the only reason images go to Cloudinary.

Image upload sits behind an abstraction so this stays a one-line swap:

```kotlin
interface ImageUploader {
    /** Uploads a local image and returns its public URL, or a failure. */
    suspend fun upload(uri: Uri): Result<String>
}
```
`CloudinaryImageUploader` is the only implementation, created in `ServiceLocator`. Document this decision in the README.

---

## 2. Server components (course requirement: 2+)

1. **Firebase Auth** — registration, login, password reset, persistent session.
2. **Cloud Firestore** — users, workouts, likes, comments, favorites, with real-time listeners.
3. **Cloudinary** — media upload.

---

## 3. Data model (Firestore)

### `users/{uid}`
| Field | Type | Notes |
|---|---|---|
| `uid` | String | == auth uid |
| `displayName` | String | |
| `email` | String | |
| `photoUrl` | String? | Cloudinary URL |
| `bio` | String | default "" |
| `workoutsCount` | Long | maintained with `FieldValue.increment` |
| `createdAt` | Timestamp | `@ServerTimestamp` |

### `workouts/{workoutId}`
| Field | Type | Notes |
|---|---|---|
| `id` | String | doc id mirrored into the document |
| `authorId` | String | uid |
| `authorName` | String | **denormalized** — the feed must not issue N extra reads |
| `authorPhotoUrl` | String? | denormalized |
| `title` | String | 3–60 chars |
| `description` | String | 0–600 chars |
| `category` | String | enum name, §4 |
| `durationMinutes` | Int | 1–600 |
| `difficulty` | String | `EASY` / `MEDIUM` / `HARD` |
| `imageUrl` | String? | Cloudinary URL, nullable |
| `likesCount` | Long | |
| `commentsCount` | Long | |
| `createdAt` | Timestamp | `@ServerTimestamp` |

All model classes need a **no-argument constructor with defaults** so Firestore's `toObject()` works.

### `workouts/{workoutId}/likes/{uid}`
`{ uid, createdAt }` — the existence of the document *is* the like.

### `workouts/{workoutId}/comments/{commentId}`
`{ id, authorId, authorName, authorPhotoUrl, text, createdAt }` — text 1–300 chars.

### `users/{uid}/favorites/{workoutId}`
`{ workoutId, savedAt }` plus a denormalized snapshot (`title`, `imageUrl`, `authorName`, `category`) so Favorites renders from a single query.

**Counter rule:** like/unlike and add/delete comment run inside `runTransaction` (or a `WriteBatch`) that writes the subcollection document **and** increments the counter together. Never update a counter alone. Be ready to explain why: two separate writes can leave the counter wrong if the second one fails.

---

## 4. Enums

```kotlin
enum class WorkoutCategory(@StringRes val labelRes: Int) {
    STRENGTH, RUNNING, HIIT, YOGA, CYCLING, SWIMMING, CROSSFIT, OTHER
}
enum class Difficulty(@StringRes val labelRes: Int) { EASY, MEDIUM, HARD }
```
Hebrew labels: כוח · ריצה · HIIT · יוגה · אופניים · שחייה · קרוספיט · אחר
Difficulty: קל · בינוני · מאתגר

---

## 5. Navigation & screens

`MainActivity` hosts a `FragmentContainerView` + `BottomNavigationView`.
On launch it checks `FirebaseAuth.currentUser` and sets the graph's start destination to `feedFragment` or `loginFragment`.
An `OnDestinationChangedListener` hides the bottom bar on auth destinations.
After login/register: `popUpTo` the auth graph with `inclusive = true` so Back does not return to the login screen.

**Bottom navigation, 4 items:** פיד · מועדפים · הוספה · פרופיל

| Fragment | Layout | Contents |
|---|---|---|
| `LoginFragment` | `fragment_login.xml` | email, password, login button, link to register, forgot-password link |
| `RegisterFragment` | `fragment_register.xml` | name, email, password, confirm, optional avatar |
| `ForgotPasswordFragment` | `fragment_forgot_password.xml` | email + send reset link |
| `FeedFragment` | `fragment_feed.xml` | RecyclerView of workouts, `ChipGroup` category filter, search field, `SwipeRefreshLayout`, FAB → add |
| `AddWorkoutFragment` | `fragment_add_workout.xml` | image picker (camera + gallery), title, description, category chips, duration, difficulty, publish button with progress |
| `WorkoutDetailsFragment` | `fragment_workout_details.xml` | hero image, title, tappable author row, meta chips, description, like, favorite, share, comments list + input; edit/delete for the owner |
| `FavoritesFragment` | `fragment_favorites.xml` | saved workouts, remove on long-press |
| `ProfileFragment` | `fragment_profile.xml` | avatar, name, bio, 3 stats, grid of own workouts, edit, theme toggle, logout |
| `UserProfileFragment` | reuses `fragment_profile.xml` | read-only view of another user (`userId` arg) |
| `EditProfileFragment` | `fragment_edit_profile.xml` | name, bio, avatar |

### Reusable layouts (`<include>` these, do not copy)
`layout_state_loading.xml` · `layout_state_empty.xml` · `layout_state_error.xml` · `item_workout.xml` · `item_comment.xml` · `item_workout_grid.xml`

### `item_workout.xml` anatomy
`MaterialCardView` (radius 16dp, no heavy elevation) containing:
avatar (circle 40dp) + name + relative time → image `ImageView` 16:9, `centerCrop`, rounded → title → `ChipGroup` with category / duration / difficulty → like button + count + comment count.
Whole card clickable → details.
Views bound through `ItemWorkoutBinding`; adapter is `WorkoutAdapter : ListAdapter<Workout, ...>` with a `DiffUtil.ItemCallback` comparing by `id` and by content.

---

## 6. Features

### MVP — all of these must work
1. Register with email/password + client-side validation with Hebrew errors
2. Login, persistent session, logout
3. Password reset email
4. Create a workout with a photo
5. Real-time feed of all workouts, newest first
6. Filter the feed by category
7. Search the feed by title/author
8. Like / unlike with a live counter
9. Comments: add, list, delete own
10. Save / unsave to favorites
11. Workout details screen
12. Own profile + edit (name, bio, avatar)
13. View another user's profile
14. Edit / delete own workout
15. Hebrew RTL throughout

### Extras — only after all of the above works, in this order
16. Light/dark theme toggle persisted in `SharedPreferences`
17. Share a workout as text via `Intent.ACTION_SEND`
18. "No connection" banner
19. Sort feed: newest / most liked
20. Pagination (`limit(20)` + load more on scroll)

Do not exceed this list. The course instructions say an overloaded feature set gets trimmed.

---

## 7. Design

Derived from the approved deck: deep navy + mint green.

```xml
<!-- res/values/colors.xml -->
<color name="mint">#FF5FE3A1</color>          <!-- primary, the logo green -->
<color name="mint_pressed">#FF3FC985</color>
<color name="mint_on_light">#FF12A063</color> <!-- accessible green on light surfaces -->
<color name="ink">#FF0E1726</color>           <!-- dark background -->
<color name="navy">#FF17233A</color>          <!-- dark surface -->
<color name="navy_elevated">#FF223150</color>
<color name="cloud">#FFF4F6FA</color>         <!-- light background -->
<color name="text_high">#FFF2F6FB</color>
<color name="text_muted">#FF97A6BF</color>
<color name="danger">#FFFF6B6B</color>
```

- Theme: `Theme.Material3.DayNight.NoActionBar`, with `values/themes.xml` + `values-night/themes.xml`. **Dark is the default look** (it matches the mockups); light must be fully usable.
- The rubric explicitly warns about bad color pairings — body text must be at least 4.5:1 contrast. No yellow on green, ever.
- Corner radii: cards 16dp, buttons 12dp, images 16dp, chips full-round.
- Spacing scale in `dimens.xml`: 4 / 8 / 12 / 16 / 24 / 32dp. Screen horizontal padding 16dp, gap between cards 12dp.
- Touch targets ≥ 48dp. Nothing overlapping, nothing clipped, no truncated Hebrew.
- Text appearances defined once in `styles.xml`: `TextAppearance.FitShare.Headline` (22sp), `.Title` (18sp), `.Body` (15sp), `.Label` (13sp), `.Caption` (12sp). Line spacing multiplier 1.3 for Hebrew.

### Typography
Bundle two Hebrew-designed fonts in `res/font/` and reference them via a `fontFamily` resource:
- **Rubik SemiBold** for headings — geometric, strong Hebrew letterforms, matches the logo wordmark
- **Heebo Regular / Medium** for body and UI — built for Hebrew, holds up at small sizes

Download the `.ttf` files from Google Fonts and commit them. Do not silently fall back to the system font.

### The one signature element
The **like button**: the logo's heart-and-dumbbell mark, animated on tap with a short scale bounce (`ScaleAnimation` or `ViewPropertyAnimator`, ~150ms) and a mint fill, with the counter fading between values. It is the app's identity applied to its core social action. Everything around it stays quiet.

---

## 8. Hebrew strings

All in `values/strings.xml`. Naming convention `screen_element`. Starting set — extend as needed:

```xml
<string name="app_name">FitShare</string>
<string name="app_tagline">אימונים, השראה והתקדמות במקום אחד</string>

<string name="login_title">ברוך שובך</string>
<string name="login_email">אימייל</string>
<string name="login_password">סיסמה</string>
<string name="login_submit">התחברות</string>
<string name="login_to_register">אין לך חשבון? הרשמה</string>
<string name="login_forgot">שכחת סיסמה?</string>

<string name="register_title">יוצרים חשבון</string>
<string name="register_name">שם מלא</string>
<string name="register_confirm">אימות סיסמה</string>
<string name="register_submit">הרשמה</string>
<string name="forgot_sent">שלחנו לך קישור לאיפוס סיסמה</string>

<string name="feed_title">פיד אימונים</string>
<string name="feed_search">חיפוש אימון או מתאמן</string>
<string name="feed_empty_title">עוד אין כאן אימונים</string>
<string name="feed_empty_body">העלה את האימון הראשון והתחל את הפיד</string>
<string name="feed_filter_all">הכל</string>

<string name="add_title">העלאת אימון</string>
<string name="add_photo">הוספת תמונה</string>
<string name="add_from_camera">צילום</string>
<string name="add_from_gallery">מהגלריה</string>
<string name="add_workout_name">שם האימון</string>
<string name="add_description">תיאור האימון</string>
<string name="add_duration">משך (בדקות)</string>
<string name="add_category">קטגוריה</string>
<string name="add_difficulty">רמת קושי</string>
<string name="add_publish">פרסום האימון</string>
<string name="add_uploading">מעלה את האימון…</string>

<string name="details_comments">תגובות</string>
<string name="details_comment_hint">כתוב תגובה…</string>
<string name="details_no_comments">אין עדיין תגובות. תהיה הראשון.</string>
<string name="details_save">שמירה למועדפים</string>
<string name="details_saved">נשמר במועדפים</string>
<string name="details_delete_confirm">למחוק את האימון? הפעולה אינה הפיכה.</string>

<string name="favorites_title">מועדפים</string>
<string name="favorites_empty_title">אין עדיין אימונים שמורים</string>
<string name="favorites_empty_body">שמור אימון מהפיד כדי לחזור אליו מתי שתרצה</string>

<string name="profile_title">הפרופיל שלי</string>
<string name="profile_workouts">אימונים</string>
<string name="profile_likes">לייקים</string>
<string name="profile_favorites">מועדפים</string>
<string name="profile_edit">עריכת פרופיל</string>
<string name="profile_logout">התנתקות</string>
<string name="profile_logout_confirm">להתנתק מהחשבון?</string>

<string name="action_retry">נסה שוב</string>
<string name="action_cancel">ביטול</string>
<string name="action_confirm">אישור</string>

<string name="error_generic">משהו השתבש. נסה שוב.</string>
<string name="error_no_network">אין חיבור לאינטרנט</string>
<string name="error_invalid_email">כתובת אימייל לא תקינה</string>
<string name="error_short_password">הסיסמה חייבת להכיל לפחות 6 תווים</string>
<string name="error_password_mismatch">הסיסמאות אינן תואמות</string>
<string name="error_wrong_credentials">אימייל או סיסמה שגויים</string>
<string name="error_email_in_use">כתובת האימייל כבר רשומה</string>
<string name="error_required_field">שדה חובה</string>
<string name="error_image_upload">העלאת התמונה נכשלה. נסה שוב.</string>
```

**Copy voice:** plain, active, second person, sentence case. An error says what happened and what to do — never a vague "אירעה שגיאה" when a specific message exists. An empty screen is an invitation to act.

Relative timestamps in Hebrew (`כרגע`, `לפני 5 דקות`, `לפני 3 שעות`, `אתמול`, then `d.M.yy`) live in `util/TimeFormatter.kt` with unit tests.

---

## 9. RTL

1. `AndroidManifest.xml`: `android:supportsRtl="true"`.
2. Force Hebrew regardless of device language: in `FitShareApp.onCreate`, `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("he"))`.
3. In every layout use `layout_marginStart` / `layout_marginEnd`, `paddingStart` / `paddingEnd`, `layout_constraintStart_*` / `layout_constraintEnd_*`. **Never** `left` / `right`.
4. `TextView` alignment: `android:textAlignment="viewStart"`, not `gravity="left"`.
5. Directional drawables (back arrow, chevrons) must mirror — `android:autoMirrored="true"` on the vector, or the `AutoMirrored` Material icon.
6. Mixed Hebrew + numbers/English (durations, "HIIT") must not scramble. Check visually.
7. Verify every screen in RTL before calling a phase done. Test with the device set to English too — the app must still render Hebrew RTL.

---

## 10. Firestore security rules

Replace test mode with this before submission. Keep it in `firestore.rules` in the repo **and** paste it into the console.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() { return request.auth != null; }
    function isOwner(uid) { return signedIn() && request.auth.uid == uid; }

    match /users/{uid} {
      allow read: if signedIn();
      allow create, update: if isOwner(uid);
      allow delete: if false;

      match /favorites/{workoutId} {
        allow read, write: if isOwner(uid);
      }
    }

    match /workouts/{workoutId} {
      allow read: if signedIn();
      allow create: if signedIn() && request.resource.data.authorId == request.auth.uid;
      allow update: if signedIn() && (
            resource.data.authorId == request.auth.uid ||
            request.resource.data.diff(resource.data).affectedKeys()
                   .hasOnly(['likesCount', 'commentsCount'])
      );
      allow delete: if signedIn() && resource.data.authorId == request.auth.uid;

      match /likes/{uid} {
        allow read: if signedIn();
        allow write: if isOwner(uid);
      }

      match /comments/{commentId} {
        allow read: if signedIn();
        allow create: if signedIn() && request.resource.data.authorId == request.auth.uid;
        allow delete: if signedIn() && resource.data.authorId == request.auth.uid;
      }
    }
  }
}
```

The "filter by category + order by createdAt desc" query needs a **composite index**. Commit `firestore.indexes.json`, and note in the README that Firestore prints a one-click index-creation link to Logcat the first time the query runs.

---

## 11. Cloudinary integration

- Endpoint: `POST https://api.cloudinary.com/v1_1/{cloudName}/image/upload`
- Multipart parts: `file` (the image), `upload_preset` (the unsigned preset name)
- Read `secure_url` from the response
- `cloudName` and `uploadPreset` come from `gradle.properties` → `buildConfigField`. Unsigned presets are public by design (they are embedded in every client app that uses them); state this in the README so nobody thinks a secret leaked.
- **Compress before uploading:** decode with `inSampleSize`, cap the longest edge at 1080px, JPEG quality 80, write to `cacheDir`. Never upload a raw camera file.
- Show progress (or at minimum an indeterminate indicator with the button disabled). On failure show `error_image_upload` and **keep the form filled** so nothing is lost.
- Image picking: `ActivityResultContracts.PickVisualMedia` for gallery, `TakePicture` + `FileProvider` for camera, with a runtime `CAMERA` permission request that degrades gracefully when denied.

---

## 12. Testing (small but present — it scores)

- Unit tests: `TimeFormatter`, input validators, `ErrorMapper`, and a repository against a fake data source.
- One instrumented test is enough: the feed adapter binds a workout and the like click reaches the ViewModel.
- Keep it to 6–10 tests. Enough to show competence, not enough to eat the schedule.

---

## 13. Definition of done, per screen

- [ ] Loading, empty, error and content states all implemented
- [ ] Correct in RTL, and correct with the device language set to English
- [ ] Works in both light and dark theme
- [ ] No hardcoded strings, colors or dimensions
- [ ] Rotation does not lose state or crash
- [ ] `_binding` nulled in `onDestroyView`; no leaks
- [ ] No crash on: no network, empty list, missing image, very long Hebrew text, back-press from any depth
- [ ] KDoc on the Fragment, the ViewModel and every public function
