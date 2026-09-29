# Workout Tracker — Roadmap

A **local-only** workout logger. The product is one small app that does one thing
well: start a workout, log sets, see your history, keep your data.

The MVP is deliberately tiny. If something is not on the MVP list below, it is
either quality work that applies to every screen (see [Quality bar](#quality-bar-applies-throughout))
or [parked on purpose](#parked--deliberately-not-planned). Feature ids (`F#`,
`P#.#`, `R#.#`) are stable and are referenced from commit messages.

## The MVP

**Open the app → start a workout → log sets → finish → find it in history → export.**

Nothing else is required to call this app usable.

### Landed

A `◐` marks a row that works but still has an open follow-up, named in the row
itself. Everything unmarked here is done.

| # | Feature | Notes |
| --- | --- | --- |
| F1 | Backup / transfer opt-out | `allowBackup="false"` plus excludes for cloud backup, device-to-device transfer and iOS cross-platform transfer. Health data does not leave the device unless the user exports it. |
| F2 | Navigation | `navigation-compose` with type-safe routes; arguments read via `SavedStateHandle.toRoute()`. |
| F3 | Presentation layer | `@HiltViewModel` + `StateFlow<UiState>` + `collectAsStateWithLifecycle`, split into stateful `…Route` and stateless `…Screen`. |
| F4 | Dependency injection | Hilt via KSP. |
| F5 | Local persistence | Room, schema v3, committed [`app/schemas/`](app/schemas/), migrations validated by test, and **no** `fallbackToDestructiveMigration` — a forgotten migration fails loudly instead of wiping history. |
| F7 | Result / error model | `DataResult` + a `DataError` taxonomy; writes return a value instead of throwing. |
| F9 | `java.time` native | `minSdk` 26, so no core-library desugaring. |
| F10 | Release pipeline | GitHub Actions: unit tests, lint, detekt, debug and R8-minified release. |
| F12 | Lint / detekt gate | Warnings fail the build. The lint **baseline is deliberately not wired** — AGP auto-creates the file and would silently accept a warning on the next run; see the comment in [app/build.gradle.kts](app/build.gradle.kts). |
| F13 | Rest-alert permissions | Requested on the first logged set, never at launch; refusal is non-fatal and not re-prompted. See [Built, but optional](#built-but-optional). |
| F14 | Ship identity | `applicationId` = `io.github.volt997.workout`, a real adaptive icon (background + foreground + `monochrome` for themed icons), `versionCode`/`versionName` from a single [`version.properties`](version.properties), the launcher label **Workout Log**, and a 512 px listing icon at [`store/icon-512.png`](store/icon-512.png) rendered from the app's own vector by [`tools/MakeStoreIcon.java`](tools/MakeStoreIcon.java). |
| F15 | Seed delivery | Seeded on *every* open with `INSERT OR IGNORE`, so first launch and upgrades both work, the first read is never empty, and a user's delete survives a top-up. |
| F16 | Recomposition-safe clock | The 1-second tick no longer rebuilds the workout screen's exercise list. |
| F17 | Input validation | Weight parsing is bounded (no exponent/hex, capped); `logSet` fails unless the exercise is live and its session is open. |
| P1.1 | Exercise library | 30 seeded movements with primary/secondary muscles, equipment and pattern, persisted in Room. Search covers primary *and* secondary muscles on a locale-stable key, and the two empty states are distinct (P1.1a). |
| P1.2 | Start a workout | Atomic find-or-create, so two taps cannot open two sessions. |
| P1.3 | Log sets / reps / weight | Prefill (what you just did → last time → default), tap to edit, delete with undo. Numeric keyboards and −/+ steppers (P1.3a). |
| P1.4 | Rest timer | In-app countdown, +15s/−15s, skip. |
| P1.8 | Crash-safe session | The open session is a database row, not memory, so a kill or reboot resumes it. Verified on device. |
| P1.12 | Export / import | Whole database to a JSON file the user picks, through the Storage Access Framework — so no storage permission is needed. Import follows one rule — **bring back what is gone, never overwrite what is there**: missing rows are inserted, locally *deleted* rows are restored from the file (clearing `deletedAt`), and live rows are left alone. Idempotent, refuses a file from a newer schema version, carries soft-deleted rows, and rejects a malformed file before touching anything. |
| P1.6 | Workout history | Chronological list grouped by month, with duration, volume and set count per workout, plus a read-only detail view. Totals are aggregated in SQL, and only finished sessions appear. |
| P1.7 | Edit / delete | Correct or remove a past set, and delete a whole workout behind a confirmation. Makes `Finish` recoverable instead of a one-way door. |
| P1.16 | Resume affordance | The library button reads "Resume workout" with the elapsed time and exercise count when a session is open, so backing out no longer hides a running workout. The clock is a separate flow only the button reads (see F16). |
| P1.3a | Number entry | Numeric keyboards on both fields plus −/+ steppers wired to the already-written `Weight.step`. Typing stays unrestricted; validation happens on save. |
| P1.1a | Search and empty states | Search matches primary *and* secondary muscles, and both the display label and the locale-stable enum name, so it survives translation. An empty library and a search with no hits now say different things. |

74 JVM tests and 48 instrumented tests, all passing.

### Still to do

Nothing. Every MVP row is in *Landed* above, which is what makes the release
checklist below the next thing to work through.

### Recorded decision: the set row shape (P1.3a)

`reps × weight` is the v1 row shape, and **bodyweight is already expressible**: a
push-up is reps at 0 kg, which the parser accepts, which `Weight.step` clamps at,
and which a set-editor test asserts actually saves. Such a set contributes 0 to
volume — correct, because bodyweight is not external load.

**Duration and distance are deliberately out of scope for v1.** The reason this
decision was wanted before history accumulated is retrofit cost. That cost is
bounded here: the schema already carries hand-written migrations with exported
schemas and `MigrationTestHelper` coverage, so a `measure` column on `set_entries`
(defaulting to weight-and-reps, which is what every existing row is) arrives as
migration 3→4 and a routine test — not a rewrite. Deciding that they are not in v1
*is* the decision; adding them later is a migration, a path this app has walked
twice already.

## First release (when the MVP is done)

Not part of the MVP feature list — it is the step that turns the MVP into
something running on the phone. No Play Store involved: a signed APK, installed
by hand. The signing infrastructure is already in place; this is the checklist that
runs once the "Still to do" rows above are green.

| # | Step | State |
| --- | --- | --- |
| R1.1 | Keystore (`workout.jks`) and `keystore.properties` created, both gitignored | ✅ done |
| R1.2 | [`tools/build-apk.sh`](tools/build-apk.sh) builds a **signed** release APK and refuses an unsigned one | ✅ done |
| R1.3 | App label is **Workout Log** — verified in the built APK for every locale and on the launcher un-ellipsized | ✅ done |
| R1.4 | Signed release installed on the phone and a workout logged end to end. **Reported from the device, not reproduced here** — I cannot see the phone — but it is the first time the R8-minified build and its Hilt/Compose shrinking, the adaptive icon under a real launcher, and the notification/exact-alarm prompts ran on real hardware rather than an emulator. | ✅ done |
| R1.5 | Upgrade test — `versionCode` 1 → 2 (v1.1), installed **over** the running app without uninstalling; **workout history survived**, confirmed on the device. The decisive property was checked mechanically first: `apksigner` reports the same certificate SHA-256 for both builds (`dd27ec9f…`), which is what Android requires to accept an update rather than demand an uninstall. | ✅ done |

**The key is permanent.** Every later build has to be signed with the same
`workout.jks`, or Android refuses the update and the only fix is to uninstall,
which deletes the history because backup is off. Back up the keystore *and* its
password outside this repo. This is exactly why **P1.12 (export) is the first MVP
item**: until it exists, a signature change or a lost key means data loss with no
way back.

## Built, but optional

The background rest alert — `AlarmManager` + a notification + a receiver + the
ask-on-first-set logic in
[RestAlertPermissions.kt](app/src/main/java/com/example/androidapp/ui/workout/RestAlertPermissions.kt)
— exists for exactly one thing: buzzing you when rest ends with the screen off.

It works, but it is **not required**, and it is the only reason the app requests
*any* permission. An in-app timer with sound/vibration plus keep-screen-on (P1.10)
covers the same need, and removing the alert would delete both manifest
permissions and the whole `platform/` package. Keep it only if the
phone-in-pocket case matters — and do not let anything else grow to depend on it.

## Quality bar (applies throughout)

- **Accessibility is MVP quality, not a later phase.** Set rows need an
  `onClickLabel`; write failures must be *announced*, not just drawn (the exact
  failure F7 exists to surface); add `testTag`s so UI tests stop asserting on
  English literals. That is P1.17.
- **Privacy:** local-only, **no `INTERNET` permission**, no ads, no analytics. Any
  crash reporting must be GMS-free and must never carry set values, notes or body
  measurements.
- **No Google Play services at runtime.** The app runs on a degoogled device.
  Firebase, `play-services-*`, Play Billing and Play Integrity are out by default
  — a future integration has to argue its way past this line.
- **Testing:** domain math, DAO and migration tests are covered. Missing: a Compose
  UI test for the workout screen (the one screen the app exists for); a test that
  seed ids are unique; `hilt-android-testing`, without which the Hilt-wired `Route`
  layers cannot be tested end to end — though `ExerciseDetailViewModel`'s argument
  reading is testable without it by constructing a `SavedStateHandle` directly, a
  cheap win; a test that the DAO's soft-delete filters actually hide deleted rows
  through the joined queries; a screenshot test for the design system; and any
  coverage signal in CI.
- **Errors:** writes return `DataResult`, but `ExerciseRepository.getExercise` and
  the `observeExercises()` flows can still throw and take a screen down. Make reads
  match writes, or record why they are exempt.
- **Performance:** not measured yet. Baseline profile and a Compose stability
  report once the logging screen stops changing.

## Later (still self-contained)

Post-MVP, same local-only premise. Ordered loosely by value.

| # | Feature |
| --- | --- |
| P1.5 | Per-set and per-workout notes; optional RPE/RIR, off by default |
| P1.9 | kg/lb display setting — storage is already canonical grams, so this is UI only |
| P1.10 | Keep the screen on during a workout |
| P1.11 | Onboarding: goal, experience level, weekly target |
| P1.13 | Custom exercises (the schema already reserves `isCustom` and UUID ids) |
| P1.14 | Rest sound / haptic feedback |
| P1.15 | Repeat last workout in one tap |
| P1.17 | Full accessibility pass (the MVP slice is in the quality bar) |
| P2.1 | Per-exercise history |
| P2.2 | Personal records and estimated 1RM |
| P2.3 | Charts and trends — pick a charting approach before starting |
| P2.6 | Plate calculator |
| P2.7 | Warm-up set generator |
| P2.8 | Muscle-group balance warnings |
| P3.1, P3.2 | Routines: build one, start a workout from one |
| P3.3, P3.4 | Programs / mesocycles and auto-progression (only once there is history) |
| P3.5, P3.6 | Weekly scheduling, supersets / circuits |
| P2.4, P2.5 | Body measurements, progress photos |
| F8 | Design-system layer: reusable buttons, rows, number pickers, empty/error states |

## Parked — deliberately not planned

Each is a product in its own right, or contradicts "local-only", or both. Parking
them is a decision, not a backlog.

| # | Feature | Why parked |
| --- | --- | --- |
| P4.1 | Health Connect read/write | A data-*sharing* integration. The app stores data for its user, not for a platform health graph. Revisit only if a user asks. |
| P4.2 | Foreground service | The alarm (or a plain in-app timer) already covers the one background need. |
| P4.3 | Home-screen widget | A separate surface and toolkit for a glance the app already gives. |
| P4.4 | Quick Settings / launcher shortcuts | Convenience; a second entry point to maintain and keep correct. |
| P4.5 | Wear OS companion | Expensive, and `play-services-wearable` breaks the no-GMS line. |
| P4.6 | Bluetooth heart-rate straps | A different product (heart-rate training), not logging. |
| P4.7 | WorkManager reminders | Nudges do not make logging better. |
| P4.8 | Large-screen layouts | Polish; revisit only if tablet users actually appear. |
| P4.9 | Offline-first sync | Contradicts local-only. Needs a backend, accounts, and conflict resolution — a large irreversible commitment. |
| P5.2, P3.7 | Friends, shared routines | Accounts, servers and moderation. |
| P5.3 | Monetization / Play Billing | Adds a Play-services dependency; revisit only with a concrete reason to charge. |
| P5.4 | Localization | Until there is a non-English user. |
| F6 | Modularization into `:core:*` / `:feature:*` | One module is correct at this size; the split would add build friction for no payoff. |
| F11 | Product analytics | Crash reporting may be worth it; analytics on a local tool is not. |
| F18 | CI / repo hygiene | Nice to have: Dependabot, SHA-pinned actions, wrapper validation, PR test annotations. |

## Explicit non-goals

Nutrition / calorie tracking, social feeds, live GPS route tracking, and a web
dashboard.

## Decisions worth remembering

- **Weights are whole grams in a `Long`** ([Weight.kt](app/src/main/java/com/example/androidapp/domain/Weight.kt)).
  Exact 0.5 kg and 1.25 kg steps, no floating-point drift, and a unit change is
  purely presentational.
- **Enum values are stored by name**, never by ordinal, so reordering a `MuscleGroup`
  cannot silently reinterpret rows already on disk.
- **Rows are already sync-shaped** — UUID ids, `createdAt` / `updatedAt` /
  `deletedAt` soft deletes — so a future sync stays a decision rather than a
  migration. What is still missing is a stored **zone offset**, which P1.6 (grouping
  by week) and any future Health Connect write both need.
- **The lint baseline is unwired on purpose.** Accepting a warning is a two-step,
  reviewed act, not a side effect of running the build.
- **Verified on device:** process death mid-workout resumes the same session, with
  its set and the rest countdown intact; the library renders on the very first read
  after `pm clear`.

## Publishing (only when you decide to)

Not part of the MVP. **Ship identity is done**: the label is *Workout Log*, the
`applicationId` is settled (`io.github.volt997.workout`), and the 512 px listing
icon is at [`store/icon-512.png`](store/icon-512.png).

The icon is generated rather than drawn, from the app's own adaptive-icon vector,
so it cannot drift from what the launcher shows:

```bash
java tools/MakeStoreIcon.java store/icon-512.png
```

Still missing for a real listing: a **feature graphic** (1024x500) and **phone
screenshots** — neither can be derived from the icon, so both are genuinely new
work. Then the Play Console side: Data safety form, content rating, privacy-policy
URL and Play App Signing. The last is worth reading up on first, because enrolling
changes who holds the app signing key, and this project's release process is built
around a permanent local one.

## Dependency notes

| Area | Library / API |
| --- | --- |
| Present | `room-runtime` + KSP, Hilt, `navigation-compose`, Compose BOM + Material 3, `kotlinx-serialization`, `kotlinx-coroutines` |
| P1.12 export / import | Storage Access Framework file picker + **`kotlinx-serialization-json`**, which is a *new* artifact — only `-core` is declared today. |
| P2.3 charts | Compose canvas, or a vetted library — decide before starting |
| Deliberately absent | No Google Play services, no Firebase, no Health Connect, no WorkManager, no billing. Note `kotlinx-coroutines` is transitive only: the single declared coroutines artifact is `kotlinx-coroutines-test`. |

## Suggested next PRs

The MVP and the first release are both done — every earlier entry in this list has
shipped. What is genuinely left, in the order it is worth doing:

1. **P1.17 — accessibility.** The only unfinished item the quality bar itself calls
   *"MVP quality, not a later phase"*. Set rows need an `onClickLabel`, and a failed
   write must be **announced**, not merely drawn — which is the entire point of F7.
   testTags have been added ad hoc (the set editor's fields); the pass has not been
   made systematic. This is the one item that is arguably owed rather than optional.
2. **The two trivia under *Simplicity*.** `RestTimer.format` and
   `WorkoutFormat.elapsed` both implement `m:ss`; `SessionExerciseDetail.movementPattern`
   is selected by the join and then dropped. A few lines each, and each misleads the
   next reader.
3. **F18 — CI and repo hygiene.** Worth doing *now* precisely because CI finally runs
   green: Dependabot, SHA-pinned actions, wrapper validation, `LICENSE`, `CHANGELOG`.
   Until the `android-37` fix, none of it would have been exercised.
4. **F11 — crash reporting**, once a GMS-free backend is chosen. Blocked on that
   decision, not on code; the privacy rules in the quality bar constrain it.
5. **F8 / F6 — design system and module split.** Refactors with no user-visible
   payoff yet. Do them when the duplication or the build time actually hurts, rather
   than speculatively.
