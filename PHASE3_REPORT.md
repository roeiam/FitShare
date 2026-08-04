# PHASE3_REPORT.md — Authentication

Phase 3 of the 8-phase plan in `PHASE0_PLAN.md` §5.
Status: **complete and verified against real Firebase** — a real account was registered, confirmed
in Firebase Auth *and* Cloud Firestore from outside the app, and every success and failure path was
exercised on an API 35 emulator. 24 unit tests pass. Zero crashes, zero ANRs.

Two things worth your attention before the detail:

- **The light-theme check you insisted on found a real accessibility defect.** The green used for
  links measured **3.1:1** against the light background — below the 4.5:1 floor SPEC §7 sets and
  the exact kind of pairing the rubric warns about. Fixed. §5.
- **Registration's orphan case is handled by rolling back the Auth account**, with a documented
  fallback when even the rollback fails. §3.

---

## 1. Scope, item by item

| # | Item | Where |
|---|---|---|
| 1 | `User` model with no-arg constructor and defaults | `data/model/User.kt` |
| 2 | `AuthDataSource` (signUp, signIn, signOut, reset, currentUser) and `UserDataSource` | `data/remote/` |
| 3 | `AuthRepository` interface + Impl, registration as one `Result<User>` | `data/repository/AuthRepository.kt` |
| 4 | `Validators` and `ErrorMapper`, both unit tested | `util/`, `app/src/test/` |
| 5 | `ViewModelFactory`, now with real branches | `di/ViewModelFactory.kt` |
| 6 | Three auth screens: `TextInputLayout` fields, inline Hebrew errors, submit disabled while invalid, progress indicator, one-shot events | `ui/auth/`, `util/Event.kt` |
| 7 | Session routing and logout clearing the back stack | `MainActivity`, `nav_graph.xml`, `ProfileViewModel` |

---

## 2. How the layers fit

```
Fragment  →  ViewModel  →  AuthRepository  →  AuthDataSource  →  FirebaseAuth
                                          →  UserDataSource  →  Firestore
```

Three decisions worth being able to defend:

**Errors are string resources, not strings.** `Validators` and `ErrorMapper` both return
`@StringRes Int`. That keeps them free of any `Context`, so ViewModels can call them and — more
importantly — so they are unit testable on the JVM with no emulator and no Robolectric. Only the
Fragment turns a resource id into text, through `TextInputLayout.setErrorRes`.

**`safeCall`, not `runCatching`.** Every data source call goes through `util/SafeCall.kt`, which
catches `Throwable` *except* `CancellationException`, which it rethrows. `runCatching` would swallow
it, and a ViewModel cleared mid-request would then treat a cancelled coroutine as a plain failure and
carry on touching state that no longer exists.

**`Event<T>` for anything that happens once.** `LiveData` re-delivers its current value to each new
observer, which is right for state and wrong for actions. Without the wrapper, rotating the screen
after a successful login would navigate a second time.

---

## 3. Registration: what happens when the second write fails

Registration is two writes that must both succeed — the Auth account, then `users/{uid}`. Firebase
has no transaction spanning Auth and Firestore, so the account can exist for a moment with no profile
document: the classic orphan.

Leaving it would be the worst option. The user would have an account they can sign into with no name,
no bio and no stats, and a second attempt to register would fail with "email already in use" — which
is baffling when you believe your registration just failed.

**The chosen behaviour: roll the Auth account back.**

1. `signUp` succeeds → `createUser` fails.
2. The just-created Auth account is **deleted** (Firebase allows this because the sign-in is recent).
3. The **original Firestore error** is returned, so the user is told what actually went wrong and the
   email address is free to try again.

**If the delete also fails** — offline is the likely cause — there is nothing more the function can
do. It signs out so the app is not left half-authenticated, and still reports the failure. The
account then exists without a profile, and **`login` repairs it**: if the document is missing after a
successful sign-in, it is recreated from the Auth account. So a user can never end up stuck in an
account with no profile.

This is recorded in `PROGRESS.md` as required.

---

## 4. Two failures the unit tests caught

Both were in the tests, not the app, but both would have blocked `testDebugUnitTest` for good.

**`ExceptionInInitializerError` in `ErrorMapper`.** The Firestore offline codes were collected into a
`private val` set. A property initialises when the object first loads, which forced the Firebase
`Code` enum to initialise too — and that fails outside Android, taking all nine tests with it. The
codes are now named inline inside the `when` branch, so they are only touched if that branch actually
runs. A subtle trap: the code was correct, the *initialisation timing* was not.

**`Method isEmpty in android.text.TextUtils not mocked`.** `ErrorMapperTest` constructs real Firebase
exceptions, and `FirebaseException`'s constructor calls `TextUtils.isEmpty` internally. Local unit
tests run against a stub `android.jar` whose methods throw. Fixed with the documented setting:

```kotlin
testOptions { unitTests { isReturnDefaultValues = true } }
```

It affects tests only; nothing in the app relies on it. The comment in `build.gradle.kts` says so.

**Result: 24 tests, all passing** — 15 for `Validators`, 9 for `ErrorMapper`.

---

## 5. The light-theme check — and what it found

You were right not to let this wait.

Flipping `setDefaultNightMode` to `MODE_NIGHT_NO` and looking at the three auth screens showed
everything legible and nothing invisible. But measuring rather than eyeballing found a real defect:

| Pairing | Contrast | Verdict |
|---|---|---|
| `mint_on_light` **as text** on `cloud` | **3.1:1** | **fails** the 4.5:1 body-text rule |
| `ink` on `mint_on_light` (filled button) | 5.4:1 | fine |

`mint_on_light` is fine as a *fill* behind ink text. As *text* on the light background — which is
what every text button and link uses, because Material3 colours `TextButton` with `colorPrimary` — it
is below the floor. SPEC §7 calls this out explicitly, and the rubric warns about exactly this.

**The fix**, kept as small and plain as possible:

- A new `@color/link_text`: `#0B7A4A` in `values/` (**5.0:1** on cloud), `#5FE3A1` in `values-night/`
  (11.3:1 on ink). The resource qualifier does the theming — no custom theme attribute needed.
- One style, `Widget.FitShare.Button.Text`, applied to every text button in the project.

The brand palette is untouched: `mint_on_light` is still `colorPrimary` and still fills buttons. Only
*text* on light surfaces got a darker green. Verified on the device in both themes, then the default
was flipped back to `MODE_NIGHT_YES`.

---

## 6. Device verification

Emulator, API 35, device language **English**, real Firebase project `fitshare-7ddbe`.

### Server-side, checked from outside the app

After registering through the UI, the account was confirmed with the Identity Toolkit and Firestore
REST APIs — not by trusting the app's own screen:

```
$ accounts:signInWithPassword
localId    : JoXN6tVRyzcdNWZfngo1Aqb8b9Q2
email      : roei.test.phase3@example.com
registered : True

$ GET .../documents/users/JoXN6tVRyzcdNWZfngo1Aqb8b9Q2
displayName   : "RoeiTester"
email         : "roei.test.phase3@example.com"
uid           : "JoXN6tVRyzcdNWZfngo1Aqb8b9Q2"
bio           : ""
workoutsCount : 0
photoUrl      : null
createdAt     : 2026-08-04T16:25:24.328Z
```

Both server components confirmed: the Auth user **and** the `users/{uid}` document, with
`@ServerTimestamp` populated by the server and every default correct.

### Flows

| Check | Result |
|---|---|
| Register a real account | account + profile document both created, routed to feed |
| Kill and reopen | cold start lands on the **feed** — session persists |
| Sign out | confirm dialog in Hebrew RTL → login screen |
| Back stack cleared after logout | Back from login exits to the launcher |
| Sign back in | succeeds, routed to feed |
| Password reset | log confirms `Password reset request roei.test.phase3@example.com`, snackbar "שלחנו לך קישור לאיפוס סיסמה" |
| **Wrong password** | "אימייל או סיסמה שגויים", form preserved and re-enabled |
| **Email already registered** | "כתובת האימייל כבר רשומה", form preserved |
| **No network** (wifi + data off) | "אין חיבור לאינטרנט" |
| Inline validation | "כתובת אימייל לא תקינה" under the field, submit disabled |
| Submit disabled while invalid | confirmed on both login and register |
| Progress indicator | fields lock, spinner shows, button disabled during the request |
| Light theme | all three auth screens legible, nothing invisible, contrast fixed |
| Crashes / ANRs | **none** across the whole session |

**On the password reset email:** what is verified is that Firebase accepted the request for the
correct address and returned success. Delivery itself was not confirmed — the test account uses
`@example.com`, a reserved domain with no inbox. Firebase returns success for unregistered addresses
too, by design, so that this screen cannot be used to discover which addresses have accounts.

**Test-harness artifacts, not app bugs.** Two things went wrong while driving the emulator and are
worth naming so they are not mistaken for defects: `adb input text` once dropped a leading character
(the log showed `oei.test...`), and a stray tap hit the floating IME panel. Both were re-run cleanly
and the correct results are the ones above.

---

## 7. Deviations and notes

**`ProfileViewModel` arrived early.** Logging out is repository work and a Fragment must never call a
repository directly, so a minimal `ProfileViewModel` exists now with only `onLogoutConfirmed()`.
Phase 7 grows it into the real profile screen.

**A logout button was added to the profile screen.** Needed to test session handling at all. Like the
edit button in Phase 2, it is the real control with the real string, shown only when `userId == null`.

**The forgot-password screen does not navigate away on success.** It was written to, and that was
wrong: navigating tears down the Fragment before its snackbar can appear, so the user would never see
the confirmation. It now stays put and the user returns with Back.

**Three ViewModels look structurally similar** (loading flag, message event, revalidate). That is
shape, not duplicated logic — the actual rules live in `Validators` and `ErrorMapper`, each written
once. A shared base class would hide more than it saved.

---

## 8. Files

**Created (16)**
```
PHASE3_REPORT.md
app/src/main/java/.../data/model/User.kt
app/src/main/java/.../data/remote/AuthDataSource.kt
app/src/main/java/.../data/remote/UserDataSource.kt
app/src/main/java/.../data/repository/AuthRepository.kt
app/src/main/java/.../di/ViewModelFactory.kt
app/src/main/java/.../ui/auth/LoginViewModel.kt
app/src/main/java/.../ui/auth/RegisterViewModel.kt
app/src/main/java/.../ui/auth/ForgotPasswordViewModel.kt
app/src/main/java/.../ui/profile/ProfileViewModel.kt
app/src/main/java/.../util/Event.kt
app/src/main/java/.../util/SafeCall.kt
app/src/main/java/.../util/Validators.kt
app/src/main/java/.../util/ErrorMapper.kt
app/src/main/java/.../util/ViewExtensions.kt
app/src/main/res/values-night/colors.xml
app/src/test/java/.../util/ValidatorsTest.kt
app/src/test/java/.../util/ErrorMapperTest.kt
```

**Modified (12)**
```
app/build.gradle.kts                     unit test returnDefaultValues
app/src/main/java/.../FitShareApp.kt     (light-theme check, reverted)
app/src/main/java/.../MainActivity.kt    session routing via AuthRepository
app/src/main/java/.../di/ServiceLocator.kt   data sources, repository, factory
app/src/main/java/.../ui/auth/*Fragment.kt   wired to their ViewModels
app/src/main/java/.../ui/profile/ProfileFragment.kt   logout
app/src/main/res/layout/fragment_login.xml, _register.xml, _forgot_password.xml
app/src/main/res/layout/fragment_profile.xml, fragment_feed.xml
app/src/main/res/navigation/nav_graph.xml    global logout action
app/src/main/res/values/colors.xml, styles.xml
app/src/main/res/values/strings.xml, values-en/strings.xml   (+4 keys each)
PROGRESS.md
```

---

## 9. Ready for Phase 4

Phase 4 is the *feed read path*: `Workout`, the enums, `WorkoutDataSource` with `callbackFlow`,
`WorkoutRepository`, `item_workout`, `WorkoutAdapter`, category filtering, search, pull to refresh
and the four states.

Nothing is blocking it. The account `roei.test.phase3@example.com` / `Test123456` exists in the
project and is useful test data — delete it from the Firebase console whenever you like; the app
recreates a profile document on next login if one is missing.

One housekeeping item for Phase 4: the feed's temporary "פרטי האימון" button gets deleted when
workout cards become tappable, as planned in Phase 2.
