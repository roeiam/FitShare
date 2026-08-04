# PHASE1_REPORT.md — Build and design foundation

Phase 1 of the 8-phase plan in `PHASE0_PLAN.md` §5.
Status: **complete and verified** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.

---

## 1. Decisions applied

All ten answers were mirrored into `SPEC.md` and `CLAUDE.md` and committed on their own, before
any code was touched (commit `5a5c290`, documents only, as requested).

| § | Decision | Where it now lives |
|---|---|---|
| A | Stay on `compileSdk 36`; pin `core-ktx` 1.18.0 and `glide` 5.0.7 | `CLAUDE.md` > *Pinned dependencies*, non-negotiable #9; `SPEC.md` §1 |
| B | Keep `values-en`, explain it in the README | `CLAUDE.md` rule 3; `SPEC.md` §9.2; the file's own header comment |
| C | Plain enums, one mapping function in the UI layer | `SPEC.md` §4 (rewritten); `CLAUDE.md` > *Layer rules* |
| D | One `ProfileFragment` with a nullable `userId` | `SPEC.md` §5 (table + explanation); `CLAUDE.md` > *Code style* |
| E | Drop feature #20, pagination | `SPEC.md` §6, struck through with the reason |
| F | "Likes" = likes received | `SPEC.md` §5 |
| G | Repository returns `Flow<Result<T>>`, ViewModel ends with `asLiveData()` | `CLAUDE.md` > *Layer rules*, replacing the old rule explicitly |
| H | Counter rules are unenforceable without Blaze | `SPEC.md` §10, marked as a README item |
| I | Fonts supplied by hand, never downloaded or substituted | `CLAUDE.md` non-negotiable #10; `SPEC.md` §7 |
| — | Gallery before camera in Phase 5 | `SPEC.md` §11 |

`PHASE0_PLAN.md` §4 now opens with a resolution table so it does not read as still-open, and
`CLAUDE.md` states that the `PHASE0_PLAN.md` §5 numbering is the only one we follow.

---

## 2. §4.A — the pinning research, and why these two libraries

The Android Studio template did not compile. Rather than guess at compatible versions, I read the
`minCompileSdk` field out of `META-INF/com/android/build/gradle/aar-metadata.properties` inside each
candidate `.aar`. That field is the exact thing the failing `checkDebugAarMetadata` task reads.

| Library | Version checked | `minCompileSdk` | Verdict |
|---|---|---|---|
| `androidx.core:core-ktx` | 1.19.0 | **37** | rejected |
| `androidx.core:core-ktx` | 1.18.0 | 36 | **pinned here** |
| `com.github.bumptech.glide:glide` | 5.0.9 | **37** | rejected |
| `com.github.bumptech.glide:glide` | 5.0.7 | 1 | **pinned here** |
| `com.google.android.material:material` | 1.14.0 | 1 | kept at latest |
| `androidx.activity:activity-ktx` | 1.13.0 | 36 | kept at latest |
| `androidx.recyclerview` 1.4.0, `swiperefreshlayout` 1.2.0 | — | 35 | kept at latest |
| `navigation` 2.9.8, `lifecycle` 2.11.0, `fragment-ktx` 1.8.9, `constraintlayout` 2.2.1 | — | 34 | kept at latest |

Two things worth knowing:

- **`material:1.14.0` did not need pinning.** It declares `minCompileSdk=1` and depends on
  `androidx.core:core` **1.16.0**, not 1.19.0, so nothing drags the resolved core version back up
  past 36. Only the explicit `core-ktx` declaration was the problem.
- **Glide was a surprise.** It was not part of the original failure — the original build failed
  before Glide existed in the project. Checking its metadata before adding it caught a second
  `minCompileSdk=37` that would otherwise have broken the build a moment later.

Only **two** of the twenty-odd libraries had to be held back. Everything else is on its latest
stable release, so this costs the project almost nothing.

`CLAUDE.md` now carries the rule: check `minCompileSdk` inside the `.aar` before adding any new
dependency.

---

## 3. What was built

### Build configuration

**`gradle/libs.versions.toml`** — rewritten as a full, grouped, commented catalog: build, AndroidX,
Firebase, async, networking/images, testing. Both pins carry an inline comment explaining *why*,
so nobody "helpfully" upgrades them later.

**`build.gradle.kts`** — added `google-services` 4.5.0 and `navigation.safeargs.kotlin` 2.9.8.

**`app/build.gradle.kts`** — plugins applied; `viewBinding` and `buildConfig` enabled; every
dependency from `PHASE0_PLAN.md` §3 wired in grouped blocks; `compileSdk` left at 36 with a comment
saying it is deliberate.

The Cloudinary settings become `BuildConfig` fields through a small helper:

```kotlin
fun requiredProperty(name: String): String =
    providers.gradleProperty(name).orNull
        ?: throw GradleException("Missing '$name' in gradle.properties. See README.")
```

If a machine is missing `CLOUDINARY_CLOUD_NAME`, the build fails at configuration time with a
sentence that says what to do — instead of compiling fine and then failing every upload at runtime
with an opaque Cloudinary error. This matters for the grader, who will build on a fresh machine.

**Confirmed: there is no Kotlin plugin, and none is needed.** AGP 9.2.1 ships built-in Kotlin
support. I verified this rather than assuming it — `.\gradlew.bat :app:tasks --all` lists
`compileDebugKotlin` with no Kotlin plugin applied anywhere. Worth knowing for the demo, because it
looks like an omission until you can explain it.

### Design tokens

**`values/colors.xml`** — the ten brand colours from SPEC §7 verbatim, plus six neutrals the deck
never specified but a light theme cannot exist without.

The contrast numbers were **calculated, not eyeballed**, because SPEC §7 sets a hard 4.5:1 floor and
the rubric explicitly warns about bad pairings:

| Pairing | Contrast | Verdict |
|---|---|---|
| `text_on_light_high` on `cloud` | 15.1:1 | body text, light |
| `text_on_light_muted` on `cloud` | 5.0:1 | secondary text, light |
| `text_high` on `ink` | 16.6:1 | body text, dark |
| `text_muted` on `ink` | 7.4:1 | secondary text, dark |
| `ink` on `mint` | 11.3:1 | primary button, dark |
| `ink` on `mint_on_light` | 5.4:1 | primary button, light |
| `danger_on_light` on `cloud` | 4.6:1 | errors, light |
| `danger` on `ink` | 6.6:1 | errors, dark |

**One thing I changed on purpose, and you should know about it:** `colorOnPrimary` is `@color/ink`,
not white. White on `mint_on_light` measures **3.4:1** and fails the 4.5:1 rule. Dark navy text on
mint green passes at 5.4:1 — and it is also the pairing in the logo, so it looks more on-brand, not
less. If a lecturer asks why the buttons are not white-on-green, that is the answer.

**`values/dimens.xml`** — the 4/8/12/16/24/32 scale, radii (card 16, button 12, image 16), avatar
sizes, `touch_target_min` 48dp, and the five text sizes. No layout will ever hardcode a dp.

**`values/styles.xml`** — the five `TextAppearance.FitShare.*` styles named exactly as SPEC §7
requires, plus two shape appearances.

There is a real trap here, and the file documents it. `android:lineSpacingMultiplier` — the 1.3 that
keeps Hebrew readable — is a `TextView` attribute, **not** a `TextAppearance` attribute. Written
`android:textAppearance="@style/TextAppearance.FitShare.Body"` it is *silently ignored*. Applied as
`style="@style/TextAppearance.FitShare.Body"` it works. The styles keep the SPEC names, and the file
header says in capitals which of the two to use. Getting this wrong would have produced cramped
Hebrew everywhere with no error message.

Colours are deliberately absent from the text styles — they resolve from the theme, so one style
serves both light and dark.

**`values/themes.xml` + `values-night/themes.xml`** — Material3 DayNight. Colour roles mapped in
both; typography, shape and everything structural defined once in the light file and inherited.

**Fonts** — `font_heading.xml` wraps Rubik SemiBold at weight 600; `font_body.xml` declares Heebo at
400 and 500 so `textStyle="bold"` picks real Medium instead of a synthesised fake bold. All three
`.ttf` files you supplied are present and non-empty (57 KB, 56 KB, 208 KB) and resolve at build
time. **I did not download or substitute anything.**

### Strings

**123 Hebrew strings** in `values/strings.xml`, grouped by screen, following SPEC §8's
`screen_element` convention and its voice rules. The SPEC starting set is included verbatim; the
rest are the strings the later phases will need — nav labels, category and difficulty labels,
relative-time formats, validation errors, and accessibility content descriptions.

**123 English strings** in `values-en/strings.xml`, same keys. Verified mechanically rather than by
eye: both files export exactly 123 keys, `comm` reports zero keys unique to either side, and there
are no duplicates.

One correction I made while writing them: SPEC's format strings used bare `%d`. I wrote them as
`%1$d`. Positional arguments are required once a string is translated, and Android lint flags the
bare form in a multi-locale project.

### Code

**`FitShareApp.kt`** — initialises the `ServiceLocator`, then locks the locale to Hebrew. The KDoc
explains *why* the app ignores the device language, since that is exactly the kind of line a
lecturer stops on.

**`di/ServiceLocator.kt`** — the skeleton. Holds the application context and the lazily-created
shared `FirebaseAuth` and `FirebaseFirestore` instances. `requireContext()` uses `check()` so
forgetting `init()` produces a sentence naming the fix, rather than an
`UninitializedPropertyAccessException`. Repositories join phase by phase.

**`AndroidManifest.xml`** — `INTERNET`, `ACCESS_NETWORK_STATE`, and `CAMERA` (runtime-requested);
`uses-feature camera required="false"` so the app still installs on a device without one; the
Application class; `supportsRtl`; `adjustResize` so the Hebrew keyboard does not cover input fields;
and the `FileProvider` for Phase 5, pointing at `xml/file_paths.xml`.

---

## 4. Verification

| Check | Result |
|---|---|
| `.\gradlew.bat assembleDebug` | **BUILD SUCCESSFUL** |
| Full dependency graph resolves on `compileSdk 36` | passes — `checkDebugAarMetadata` green |
| Kotlin compiles with no Kotlin plugin | confirmed via `compileDebugKotlin` |
| Fonts resolve | all three `.ttf` present, non-empty, referenced by two font families |
| Hebrew ↔ English string parity | 123 = 123, zero drift, zero duplicates |
| Contrast ratios | all eight pairings computed, all ≥ 4.5:1 |

One benign build warning, worth recognising so it does not look alarming later:
`Unable to strip ... libdatastore_shared_counter.so`. It comes from a Firebase native library, is
normal for debug builds, and does not affect the APK.

---

## 5. Deliberately not done in Phase 1

- **`MainActivity` and `activity_main.xml` are still the Android Studio template.** They compile and
  do nothing useful. Phase 2 replaces them with the NavHost and the bottom bar. I did not touch them
  because navigation is Phase 2 work.
- **Dark is not yet the default.** SPEC §7 says dark should be the default look; the theme currently
  follows the system setting. Making dark the default means writing `ThemePreferences`, which is a
  Phase 7 deliverable. Flagging it now so it is not forgotten — it is on the Phase 7 line and in
  `PROGRESS.md`.
- No models, repositories, data sources, fragments or navigation graph — all later phases.

---

## 6. Files touched

**Created (11)**
```
PHASE1_REPORT.md
PROGRESS.md
app/src/main/java/com/roeiamor/fitshare/FitShareApp.kt
app/src/main/java/com/roeiamor/fitshare/di/ServiceLocator.kt
app/src/main/res/font/font_body.xml
app/src/main/res/font/font_heading.xml
app/src/main/res/values/dimens.xml
app/src/main/res/values/styles.xml
app/src/main/res/values-en/strings.xml
app/src/main/res/xml/file_paths.xml
(+ the three .ttf files you supplied, now committed)
```

**Modified (7)**
```
build.gradle.kts
app/build.gradle.kts
gradle/libs.versions.toml
app/src/main/AndroidManifest.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/values-night/themes.xml
```

---

## 7. Nothing is blocking Phase 2

Phase 2 is *Navigation and screen skeleton*: `nav_graph.xml` with 10 destinations, `MainActivity`
rebuilt around a `NavHostFragment` and `BottomNavigationView` that hides itself on auth
destinations, the three reusable state layouts, `BaseFragment` and `StateRenderer`,
`ViewModelFactory`, and empty fragments for every screen.

Phase 2 will be the first point where the app is worth looking at on a device — and the first real
RTL check.
