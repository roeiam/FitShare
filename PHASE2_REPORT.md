# PHASE2_REPORT.md — Navigation and screen skeleton

Phase 2 of the 8-phase plan in `PHASE0_PLAN.md` §5.
Status: **complete and verified on a device** — built, installed and driven on a Pixel-class
emulator (API 35). Nine destinations reached, no crashes, no ANRs.

**One real bug was found and fixed during verification: the app was rendering entirely in English.**
Details in §2 — it is the most important thing in this report.

---

## 1. The Phase 1 correction: dark is now the default

Applied as instructed, in `FitShareApp.onCreate`:

```kotlin
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
```

You were right that this could not wait. Every screenshot below is dark because of this line, which
means Phases 2–6 are being tuned against the mode the mockups were drawn in rather than against
whatever the developer's device happened to be set to. The KDoc records that Phase 7's
`ThemePreferences` overrides this default rather than replacing the mechanism.

Two theme additions were needed to make the bottom bar look right: `colorSecondaryContainer` /
`colorOnSecondaryContainer` (the active tab pill) and `colorSurfaceContainer` (the bar background),
in both themes. Material3 derives those components from roles Phase 1 had not set.

---

## 2. The bug: the whole app came up in English

### What happened

The emulator's device language is `en-US`. On first launch every string rendered from
`values-en/strings.xml` — "Welcome back", "Forgot your password?" — instead of Hebrew. The app was
supposed to be locked to Hebrew regardless of device language (SPEC §9.2).

Querying the system directly:

```
$ adb shell cmd locale get-app-locales com.roeiamor.fitshare
Locales for com.roeiamor.fitshare for user 0 are []
```

Empty. The call had done nothing at all. No exception, no warning in logcat — the app simply
rendered in the device language.

### Root cause

`AppCompatDelegate.setApplicationLocales()` was being called from `FitShareApp.onCreate()`, which is
exactly where SPEC §9.2 says to put it.

From API 33, AppCompat does not apply the locale itself — it forwards the call to the system
`LocaleManager`, and it reaches that service **through an active Activity delegate**. Called from
`Application.onCreate()` no Activity exists yet, so there is no delegate, so there is nothing to
forward through. The call returns normally and does nothing.

That is a genuinely nasty failure mode: silent, invisible in logs, and it only shows up on a device
whose language is not Hebrew. On a Hebrew phone the app would have looked perfect.

### The fix

The call moved to `MainActivity.onCreate()`, where a delegate exists. This keeps the API that SPEC
§9.2 names — only the call site changed. Two supporting pieces were added at the same time:

- `res/xml/locales_config.xml` listing `he` and `en`, referenced by `android:localeConfig`. From
  API 33 the system wants this list to accept a per-app locale.
- `AppLocalesMetadataHolderService` in the manifest, which is how AppCompat persists and reapplies
  the locale below API 33 — our `minSdk` is 24, so that path matters for real devices.

### Verified

```
$ adb shell cmd locale get-app-locales com.roeiamor.fitshare
Locales for com.roeiamor.fitshare for user 0 are [he]

$ adb shell getprop ro.product.locale
en-US
```

Device in English, app in Hebrew. **Your third requirement is met, and it is met because it was
actually tested rather than assumed.**

I tried the metadata service on its own first — it did not fix it. Only moving the call site did.
Worth knowing which of the two changes is load-bearing.

---

## 3. What was built

### Navigation

**`res/navigation/nav_graph.xml`** — **nine** destinations, not the ten `PHASE0_PLAN.md` §5
predicted. That table was written before decision §4.D collapsed `UserProfileFragment` into
`ProfileFragment`. Nine is the correct count now.

`app:startDestination` is deliberately absent from both the graph and the `FragmentContainerView`.
`MainActivity` inflates the graph in code and sets the start destination from the session — feed if
signed in, login otherwise. Declaring it in XML as well would inflate the graph twice and flash the
wrong screen at launch.

The login→feed and register→feed actions carry `popUpTo="@id/loginFragment"` with
`popUpToInclusive="true"`, so the auth flow is cleared off the back stack on sign-in.

**`MainActivity`** — rebuilt from the template. It now: picks the start destination from
`FirebaseAuth.currentUser`, calls `setupWithNavController` on the bottom bar, hides that bar on the
three auth destinations, and applies window insets.

Because the menu item ids match the destination ids, `NavigationUI` handles tab taps, the selected
highlight and the per-tab back stack with **no click listeners of our own**.

### The reusable pieces

**`ui/common/BaseFragment.kt`** — owns the ViewBinding lifecycle for all nine fragments. Nulls
`_binding` in `onDestroyView`; `binding` throws a named error if touched outside the view lifecycle
rather than a bare NPE. This is the single defence against the leak CLAUDE.md calls out, written
once instead of nine times.

**`ui/common/StateRenderer.kt`** — shows exactly one of loading / empty / error / content. Every
screen hands it the three included state layouts and its own content view; no screen toggles a state
view's visibility itself.

**Three state layouts** — `layout_state_loading`, `_empty`, `_error`, `<include>`d rather than
copied. The empty state takes its title, body and optional action at runtime, so it holds no
screen-specific copy.

### Screens

Nine fragments and nine layouts. Three of them — feed, favorites, profile — already render the real
empty state with real Hebrew copy, which is what actually put the state layouts, the typography and
the RTL mirroring on screen where they could be checked.

The buttons on these screens are mostly **not throwaway**: the login screen's three buttons, the
profile's edit button and the feed's empty-state action are the final controls, using the final
strings and the final navigation actions. Later phases build the fields and lists *around* them.

### Drawables

Six vectors: four bottom-nav icons and two state illustrations. **Every one is symmetrical**, so
there is nothing to mirror in RTL. No directional icon exists in the project yet; when one arrives
(the back arrow in Phase 6) it will need `android:autoMirrored="true"`.

---

## 4. Three UI defects found on the device and fixed

Verification was worth doing — none of these are visible from the source.

**1. Only the selected tab showed its label.** With four items, `BottomNavigationView`'s default
label mode labels only the active tab, leaving three unlabelled icons. SPEC §5 names all four.
Fixed with `app:labelVisibilityMode="labeled"`.

**2. The bottom bar did not reach the bottom of the screen.** The window insets were padding the
root, which left a strip of page background below the bar. The bottom inset now goes on the bar
itself, so its surface colour runs to the screen edge while its items stay above the gesture handle.

**3. `editProfileFragment` was unreachable.** Nothing navigated to it — Phase 7 owns the profile UI.
Rather than add a throwaway button, I added the **real** "עריכת פרופיל" control, using the real
string and the real action, shown only when `userId == null`. Phase 7 moves it next to the avatar
and adds the theme toggle and logout beside it. This is the control arriving early, not scaffolding.

---

## 5. Verification performed

Built, installed, and driven on the `Medium_Phone` AVD (API 35), device language **English**.

| Check | Result |
|---|---|
| `.\gradlew.bat assembleDebug` | BUILD SUCCESSFUL |
| All 9 destinations reached | login, register, forgot, feed, favorites, add, details, profile, editProfile |
| Crashes / ANRs in our package | **none** — full logcat buffer swept for `FATAL EXCEPTION`, `AndroidRuntime:E`, `ANR in com.roeiamor` |
| Hebrew renders with device in English | yes, after the §2 fix — per-app locale `[he]`, device `en-US` |
| Bottom nav RTL order | פיד rightmost → מועדפים → הוספה → פרופיל leftmost. Correct for Hebrew |
| Directional icons | none exist; all six drawables are symmetrical |
| Bottom bar hidden on auth screens | yes, on all three |
| SafeArgs required argument | details opened with its `workoutId` argument, no bundle crash |
| Nullable argument | profile with `userId == null` selected the own-profile copy |
| Rotation | rotated to landscape on Edit Profile and back: destination preserved, no crash, RTL still correct |
| Back from a pushed screen | returns to the screen that opened it |
| Back eventually exits | yes, unwinds tab history then returns to the launcher |

Screenshots from the run are in the job scratch directory
(`~/.claude/jobs/1a4e3785/tmp/*.png`) — they are working artefacts, not committed.

**On back behaviour:** Navigation 2.4+ keeps a back stack *per tab*, so Back from a tab returns to
the previously selected tab before exiting, rather than jumping straight to the feed. That is the
library default and Google's documented behaviour, it never loops, and it always terminates at the
launcher. If you would rather Back always returned to the feed first and exited from there, say so
and I will change it in Phase 7 — it is a few lines, but it is a deliberate UX choice, not a defect.

**Emulator noise:** "Pixel Launcher isn't responding" and "System UI isn't responding" dialogs
appeared during the run. Those are the emulator itself under load — neither names our package, and
the crash sweep for `com.roeiamor.fitshare` is clean.

---

## 6. Deliberately not done

**`di/ViewModelFactory.kt` is deferred to Phase 3.** `PHASE0_PLAN.md` §5 lists it under Phase 2, but
there are no ViewModels yet — the class would be a factory whose `when` block has zero branches and
which nothing constructs. That is dead code, and CLAUDE.md treats dead code as a defect. It arrives
in Phase 3 alongside `LoginViewModel`, the first thing that needs it. Flagging the deviation rather
than quietly skipping it.

**Light theme is not yet verified.** SPEC §13 requires every screen to work in both themes, but the
app now forces `MODE_NIGHT_YES`, so light cannot be reached without changing code. The natural place
to verify it is Phase 7, when `ThemePreferences` makes the toggle reachable at runtime. The light
theme *is* fully defined and its contrast ratios were calculated in Phase 1 — it is untested, not
unwritten.

**Fonts:** your three `.ttf` files are in place and resolving. Nothing was downloaded or substituted.

---

## 7. Files

**Created (24)**
```
PHASE2_REPORT.md
app/src/main/res/navigation/nav_graph.xml
app/src/main/res/menu/menu_bottom_nav.xml
app/src/main/res/xml/locales_config.xml
app/src/main/res/layout/layout_state_loading.xml
app/src/main/res/layout/layout_state_empty.xml
app/src/main/res/layout/layout_state_error.xml
app/src/main/res/layout/fragment_{login,register,forgot_password,feed,favorites,
                                  add_workout,workout_details,profile,edit_profile}.xml
app/src/main/res/drawable/ic_{feed,favorites,add,profile,empty_state,error_state}.xml
app/src/main/java/.../ui/common/BaseFragment.kt
app/src/main/java/.../ui/common/StateRenderer.kt
app/src/main/java/.../ui/auth/{Login,Register,ForgotPassword}Fragment.kt
app/src/main/java/.../ui/feed/FeedFragment.kt
app/src/main/java/.../ui/favorites/FavoritesFragment.kt
app/src/main/java/.../ui/addworkout/AddWorkoutFragment.kt
app/src/main/java/.../ui/details/WorkoutDetailsFragment.kt
app/src/main/java/.../ui/profile/{Profile,EditProfile}Fragment.kt
```

**Modified (6)**
```
app/src/main/AndroidManifest.xml            localeConfig + AppLocales service
app/src/main/java/.../MainActivity.kt       rebuilt: nav host, bottom bar, insets, locale
app/src/main/java/.../FitShareApp.kt        dark default; locale call moved out
app/src/main/res/layout/activity_main.xml   nav host + bottom nav
app/src/main/res/values/themes.xml          bottom-nav colour roles
app/src/main/res/values-night/themes.xml    bottom-nav colour roles
PROGRESS.md
```

---

## 8. Ready for Phase 3

Phase 3 is *Authentication*: the `User` model, `AuthDataSource`, `AuthRepository`, `Validators`,
`ErrorMapper`, `ViewModelFactory`, and the three auth screens wired to real Firebase calls with
Hebrew validation messages.

Nothing is blocking it. The one open question you may want to answer first is the back-behaviour
preference in §5 — but it does not block Phase 3, and the current behaviour is defensible as is.
