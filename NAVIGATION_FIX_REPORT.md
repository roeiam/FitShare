# Navigation fix — and the symptom that is still open

Written 8 August 2026, after the report of a sign-in form rendering inside the add-workout screen.

**Read the summary line first: one confirmed defect was fixed and verified; the reported symptom was
never reproduced and is not closed.** These are two different things and this document keeps them
apart on purpose.

---

## 1. The reported symptom — OPEN

On the add-workout form: pick a photo, tap **שינוי תמונה**, pick a second one. The new photo did not
load, and the login screen's **email and password fields appeared inside the add-workout screen,
below the title** — two destinations rendered at once. Reported as happening **every time**, with a
signed-in session.

It then stopped reproducing, for Roei and for me, **with no code change in between**. Nothing fixed
it. It simply stopped appearing.

### What was tried, and what it ruled out

Every attempt below ran against the **unfixed** build. That matters: the defect in section 2 was
present throughout, and none of these forced it to misbehave.

| forced condition | result |
|---|---|
| Activity recreation, **Don't keep activities** on, add form on screen | form restored intact, no login fields |
| process killed (`am kill`) while backgrounded, returned via recents | form restored, typed title preserved, no login fields |
| logcat throughout | no crash, no `FragmentManager` warning, no `Navigation` warning |

The reported flow itself **could not be driven from adb**. DocumentsUI refused synthetic selection on
this device that day — grid view and list view, `tap` and press-and-hold, integer coordinates, bounds
read from the hierarchy — even though taps registered normally on its own menus and the same approach
had selected a photo the day before. The second pick was never reached by automation, so the symptom
was never observed first-hand.

### Working theory, assessed

The theory was: returning from the picker recreates the Activity or restores the process, and
`MainActivity` recomputes the start destination from `FirebaseAuth.currentUser` and pushes login into
the NavHost while the add form is still on the stack.

- **The mechanism it names is real** and is fixed in section 2.
- **The trigger it names was not sufficient.** Recreation and process death were both forced, on the
  unfixed build, and both restored correctly.
- **One sub-claim is wrong.** The picker launchers are `registerForActivityResult` *field
  initializers* on the fragment (`AddWorkoutFragment.kt:56, 77, 96`) — the recommended pattern. They
  re-register on every recreation and cannot deliver to a stale instance.

---

## 2. The defect that was found and fixed — CLOSED

`MainActivity.setUpNavigation` ran from `onCreate` with no `savedInstanceState` guard, on every
creation — rotation, return from the picker, process restored from saved state:

```kotlin
val isSignedIn = ServiceLocator.authRepository.isSignedIn   // FirebaseAuth.currentUser, synchronous
val graph = navController.navInflater.inflate(R.navigation.nav_graph)
graph.setStartDestination(if (isSignedIn) R.id.feedFragment else R.id.loginFragment)
navController.graph = graph
```

Two faults that compound:

1. **It discards restored state.** A recreation already carries a `NavController` that has restored
   its own back stack. Assigning a freshly inflated graph over it throws that away and can navigate
   somewhere the user never asked to go.
2. **It re-decides from a value that can lie.** `currentUser` is read synchronously; a restoring
   process can answer null for a user who *is* signed in. The recomputation could therefore resolve
   to `loginFragment` while the user's real screen was still on the stack.

That is the exact shape of the reported symptom — which is why it was worth fixing on its own merits,
independently of whether it caused what was seen.

### The fix

- The graph is built **once per task**, from the first `onCreate` only.
- A recreation re-attaches listeners and lets the restored `NavController` state stand.
- The resolved start destination is carried across recreations in `onSaveInstanceState`, so the one
  remaining rebuild path — a defensive branch for a restore that produced no destination at all —
  reuses the answer the task started with instead of asking `FirebaseAuth` again.

Files: `app/src/main/java/com/roeiamor/fitshare/MainActivity.kt`.

---

## 3. Verification after the fix

On the physical Samsung **SM-A305F, Android 11**, unless noted.

| flow | result |
|---|---|
| cold start, signed in | **feed** |
| cold start, signed out (emulator, app data cleared, no session) | **login** |
| rotation mid-form, portrait → landscape | form kept, typed title intact, no login fields |
| rotation mid-form, landscape → portrait | form kept, typed title intact, no login fields |
| process killed **while the photo picker was foreground**, then Back | form restored, typed title intact, no login fields |
| 55 unit tests + 4 instrumented tests (on the device) | pass |

The picker row is the closest deterministic stand-in for the reported flow that can be produced on
demand, and it is the case the fix targets.

**Not verified:** the literal two-pick flow, for the automation reason in section 1. Roei has retested
it by hand since and it behaves correctly.

**A note on method.** `uiautomator dump` misled twice during this work and both times a screenshot
settled it: it omits off-screen nodes, and it can return stale content. Any conclusion drawn from a
dump alone in this codebase should be confirmed against a screenshot.

---

## 4. What is still unexplained

- Why **two destinations rendered simultaneously** rather than one replacing the other. The
  `FragmentNavigator` replaces; it does not stack. Nothing found so far accounts for both being
  visible.
- Why the **new photo failed to load** in the same moment.
- Why it reproduced **every time** and then stopped, with no code change.

### If it comes back

Capture before touching anything:

1. `adb logcat -d > bug.txt` immediately — a stack trace or `FragmentManager` warning would settle
   the mechanism.
2. Whether the add form is still **underneath** the login fields, or has been **replaced** by them.
3. Whether the app had been backgrounded for a long time first, and whether the device was under
   memory pressure.
