# CLAUDE.md — FitShare

This is a **final course project** for an Android development course, graded by a human lecturer on:
code quality, class separation, no duplication, correct library usage, UI/UX polish, and **no unexplained crashes**.

The developer (Roei) must be able to **explain every line in class**. Clever is worse than clear.
When you have a choice between a clever modern solution and the plain, standard, well-documented one — **choose the plain one**.

Read `SPEC.md` in full before doing anything. It is the single source of truth for stack, data model, screens, strings and design tokens.

---

## Non-negotiables

1. **XML Views, not Jetpack Compose.** This is deliberate. Every screen is a `Fragment` with an XML layout and **ViewBinding**. No Compose dependency anywhere in the project. No DataBinding, no Kotlin synthetics.

2. **No Hilt, no Dagger, no Koin.** Dependencies are wired by hand in `di/ServiceLocator.kt` and passed to ViewModels through a `ViewModelProvider.Factory`. It must be readable top to bottom in one sitting.

3. **The app UI is Hebrew (RTL).**
   - Hebrew strings live in `app/src/main/res/values/strings.xml` (the **default** folder). English goes in `values-en/strings.xml`.
   - **Zero hardcoded user-facing text** in Kotlin or XML. Always `@string/...` or `getString(R.string...)`.
   - Layouts use `start`/`end`, never `left`/`right`. Directional icons mirror.
   - Code, identifiers, comments, commit messages and filenames: **English**.

4. **Never invent scope.** If it is not in `SPEC.md`, ask before building it.

5. **Every screen handles four states: Loading, Empty, Error, Content.** No screen ships with only the happy path. Empty states carry a Hebrew message and a call to action.

6. **No crashes.** Every Firebase/network call is wrapped and returns `Result<T>`. No `!!`. No unchecked indexing. Nothing blocking on the main thread. Always null-check `_binding` in fragments and clear it in `onDestroyView`.

7. **Verify.** After every meaningful change run `./gradlew assembleDebug`. A phase is not done until it compiles.

8. **One phase at a time.** Never start work belonging to a later phase. End each phase with a summary and a `PROGRESS.md` update.

---

## Package structure

```
com.roeiamor.fitshare/
├── FitShareApp.kt              // Application — initialises ServiceLocator
├── MainActivity.kt             // the ONLY activity: NavHost + BottomNavigationView
├── data/
│   ├── model/                  // plain data classes, no Android imports
│   ├── remote/                 // AuthDataSource, WorkoutDataSource, CloudinaryApi
│   └── repository/             // AuthRepository, WorkoutRepository, UserRepository
├── di/
│   ├── ServiceLocator.kt
│   └── ViewModelFactory.kt
├── ui/
│   ├── auth/                   // LoginFragment + LoginViewModel + ...
│   ├── feed/                   // FeedFragment, FeedViewModel, WorkoutAdapter
│   ├── addworkout/
│   ├── details/
│   ├── favorites/
│   ├── profile/
│   └── common/                 // BaseFragment, shared adapters, state views
└── util/                       // Result helpers, ErrorMapper, TimeFormatter, ImageCompressor, extensions
```

## Layer rules

- **Fragment**: inflates the layout, observes `LiveData`, renders state, forwards user events to the ViewModel. **Zero Firebase calls, zero business logic in a Fragment.**
- **ViewModel**: exposes `LiveData<XUiState>` (one immutable data class per screen) plus `LiveData<Event<T>>` for one-shot navigation/snackbar events. Uses `viewModelScope`. Never touches `View`, `Context` or `Fragment`.
- **Repository**: an interface plus one implementation. ViewModels depend on the interface, never on `FirebaseFirestore` directly.
- **DataSource**: the only place that knows Firebase or Retrofit exists.
- `suspend` functions return `Result<T>`. Live streams return `LiveData<Result<T>>` (Firestore listeners wrapped with `callbackFlow { }.asLiveData()`).
- Exceptions are converted to Hebrew messages in **one** place — `util/ErrorMapper.kt`.

## Code style

- Kotlin official style; consistent formatting throughout.
- **KDoc on every class and every public function** — what it does and why, one or two lines. This is explicitly graded.
- Meaningful names. No `data1`, `temp`, `Helper2`, no leftover `TODO`.
- Fragments stay short. If a fragment passes ~200 lines, extract logic to the ViewModel or a helper.
- **Duplication is a graded defect.** Repeated layout blocks become `<include>`; repeated list rows become one adapter; repeated logic becomes a util function.
- All dimensions in `dimens.xml`, all colors in `colors.xml`, all text styles in `styles.xml`. **No raw hex or hardcoded dp inside a layout.**
- RecyclerView adapters always extend `ListAdapter` with a `DiffUtil.ItemCallback`. Never `notifyDataSetChanged()`.

## Git

- Commit at the end of every phase using conventional commits: `feat(feed): add workout feed with like button`.
- `google-services.json` **is** committed on purpose — the grader must be able to build the project.

## Progress tracking

Update `PROGRESS.md` at the end of each phase: what was built, which files, what is still open, known limitations. It feeds the final README and the demo video script.

## When stuck

Do not guess and do not silently stub things out. State the problem, the two options you see, and the tradeoff — then wait.
