# Workout Tracker — Feature Roadmap

A prioritized feature list for turning the current Compose scaffold into a
shippable workout tracker.

## Where the app stands today

The repo is a working workout tracker, not a scaffold. Concretely it now has:

- One `ComponentActivity` ([MainActivity.kt](app/src/main/java/com/example/androidapp/MainActivity.kt))
  doing nothing but host the navigation graph (F2).
- Type-safe navigation between the library, per-exercise detail, the active
  workout, and the exercise picker ([Routes.kt](app/src/main/java/com/example/androidapp/ui/navigation/Routes.kt)).
- `@HiltViewModel` ViewModels exposing `StateFlow<UiState>`, collected with
  `collectAsStateWithLifecycle` (F3) and injected with Hilt (F4).
- A Room database (F5) at schema v3 with committed, migration-tested history:
  the exercise library, workout sessions, the exercises inside them, and logged
  sets. Sync-ready columns (`id`, `createdAt`, `updatedAt`, `deletedAt`) are in
  place for P4.9.
- The MVP loop: start a workout, add exercises, log sets with a
  last-time prefill, rest between sets, and finish — with the session, its sets
  and the rest timer all surviving a process death (P1.2, P1.3, P1.4, P1.8).
- Repositories returning `DataResult` rather than throwing (F7), and an injected
  clock so session timing is testable.
- Material 3 theming, edge-to-edge, `compileSdk`/`targetSdk` 37, `minSdk` 26.
- 57 JVM tests and 23 instrumented tests, all passing.
- CI on every push and PR (F10), with lint and detekt both failing the build on
  warnings (F12), and R8 + resource shrinking on `release`.
- A manifest that opts out of cloud backup, device-to-device transfer, and iOS
  cross-platform transfer entirely (F1).

Still missing: workout history and editing (P1.6, P1.7), the unit display setting
(P1.9 — storage is already canonical, so only the UI remains), onboarding
(P1.11), export (P1.12), custom exercises (P1.13), and Health Connect (P4.1).

Several gaps were corrective rather than additive. Four have now landed:
**F13** (the rest alert finally requests its permissions), **F15** (seed delivery
works on first open *and* upgrade), **F16** (a tick no longer rebuilds the workout
screen's exercise list) and **F17** (input parsing is bounded and a set write is
guarded). Still outstanding from that pass: **F14** (ship identity — cheap now,
expensive after a release) and **F18** (CI and repo hygiene).

The one thing that was a **correctness bug rather than a missing feature** —
health data riding along in platform backups and transfers — is fixed, and the
quality gates that would catch a regression now run on every change.

## How to read this

| Field | Meaning |
| --- | --- |
| **Priority** | `P0` blocks the MVP · `P1` makes it good · `P2` makes it grow |
| **Effort** | `S` ≤ 1 day · `M` 2–5 days · `L` ≥ 1 week, or needs a design spike |

Rows marked ✅ have landed; ◐ means partly landed. Local-only storage is the
agreed starting point (sync, P4.9, is deferred), so schemas should be written
sync-ready: UUID primary keys, soft deletes, `createdAt`/`updatedAt` timestamps,
and — because "what day was this workout" is a local-time question — a stored
zone offset alongside the UTC instant. Timestamps are epoch millis today and the
UI formats them in the *current* zone, so a user who travels sees past workouts
shift; P1.6 (grouping by week/month) and P4.1 (`ExerciseSessionRecord` requires
`startZoneOffset`) both need the offset that was true at the time.

Phases are ordered by dependency, not by calendar. Phase 0 and Phase 1 are the
MVP.

---

## Phase 0 — Foundations

Work that every later feature leans on. Cheap now, expensive to retrofit.

| # | Feature | Pri | Eff | Notes |
| --- | --- | --- | --- | --- |
| F1 | ✅ **Fix backup exposure** | P0 | S | Landed. `allowBackup="false"` plus rules excluding every domain from cloud backup, D2D transfer, and iOS cross-platform transfer. Note `allowBackup="false"` alone does *not* stop D2D — the `<device-transfer>` section is what does. |
| F2 | ✅ **Navigation** | P0 | S | Landed. `navigation-compose` type-safe routes (`@Serializable` destinations) in [AppNavHost.kt](app/src/main/java/com/example/androidapp/ui/navigation/AppNavHost.kt); the detail ViewModel reads its argument with `SavedStateHandle.toRoute()`. |
| F3 | ✅ **Presentation layer** | P0 | S | Landed. `@HiltViewModel` + `StateFlow<UiState>` + `collectAsStateWithLifecycle`. Each screen splits into a stateful `…Route` and a stateless `…Screen`, so the UI is testable with no Hilt container. |
| F4 | ✅ **Dependency injection** | P0 | M | Landed. Hilt 2.60.1 via KSP. Worth recording: KSP is now versioned independently of Kotlin, so KSP 2.3.12 works with Kotlin 2.4.20 even though its own API still targets 2.3.20 — a version probe settled that before any code was written. |
| F5 | ✅ **Local persistence** | P0 | M | Landed. Room 2.8.5 via KSP behind the existing `ExerciseRepository`. Schema exported to [`app/schemas/`](app/schemas/) and committed; enums stored by name via `Converters`, never ordinals; soft deletes filtered inside the DAO; no `fallbackToDestructiveMigration` anywhere, so a forgotten migration fails loudly instead of wiping history. Rows already carry the sync-ready `id`/`createdAt`/`updatedAt`/`deletedAt` shape (P4.9). Seeded on first open rather than shipped as a prepackaged `.db`. |
| F6 | **Domain + data modules** | P1 | M | Split into `:app`, `:core:data`, `:core:domain`, `:feature:workout`, … so build times and ownership stay sane. Optional at MVP, painful later. |
| F7 | ✅ **Result/error model** | P1 | S | Landed in [`DataResult.kt`](app/src/main/java/com/example/androidapp/domain/DataResult.kt): `DataResult` + a `DataError` taxonomy, with every repository write returning a value instead of throwing. `CancellationException` is deliberately rethrown rather than reported as a failure — swallowing it would break structured concurrency — and that behaviour has its own test. |
| F8 | **Design system layer** | P1 | M | Promote `ui/theme` into a real component library: buttons, list rows, empty/error/loading states, number pickers. Workout logging is number-entry heavy. |
| F9 | ✅ **`java.time` on API 24** | P0 | S | Landed. `minSdk` raised to 26: `java.time` is native (no desugaring) and Health Connect's floor is met for P4.1. |
| F10 | ✅ **Release pipeline** | P1 | M | Landed. GitHub Actions runs `testDebugUnitTest`, `lint`, `detekt`, `assembleDebug` and `assembleRelease` on every push and PR, plus a second job for the instrumented tests on a KVM runner. Release has R8 + resource shrinking (12 MB debug → 1.4 MB release) and signs from a gitignored `keystore.properties`; without that file it still builds unsigned, which is what CI does. R8 mapping is uploaded for readable crash traces. |
| F11 | **Crash + analytics** | P1 | S | Crash reporting with symbol upload; privacy-respecting product analytics with opt-out. Pick a GMS-free backend first (see the no-GMS quality bar note): Firebase would both break degoogled devices and sit badly with the on-device health-data posture — so this decision precedes P5.3, rather than P5.3 gating it. |
| F12 | ✅ **Lint/detekt gate** | P2 | S | Landed. Lint runs with `warningsAsErrors`; detekt runs on its defaults plus the Compose ruleset, with five narrowly-scoped, commented exceptions (Compose's PascalCase naming, `@Preview` "unused" members, the colour palette, and the seed table). The Compose ruleset is pinned to 0.4.x because 0.5.0+ targets detekt 2.0 and silently registers nothing against 1.23.x. **The baseline hole is closed, but not as suggested.** Wiring
`baseline = file(...)` is a foot-gun: AGP *creates* the baseline when it is
missing, so the first failing run writes its warnings into the file and every run
after that silently accepts them — "fail on every warning" quietly becomes "fail
once". That happened while fixing F13–F17, and the auto-created baseline was
already masking a real `UseKtx` warning in the new code. The line is therefore
deliberately absent, the warning is fixed instead, and both the build file and the
README document the two deliberate steps for accepting a warning later. |
| F13 | ✅ **Request the permissions the rest alert needs** | P0 | S | **Landed.** Both are now requested on the first logged set — where the reason is obvious rather than at launch — via [`RestAlertPermissions.kt`](app/src/main/java/com/example/androidapp/ui/workout/RestAlertPermissions.kt); refusal stays non-fatal and the ask is recorded so a decline is not re-prompted. The small icon is now a monochrome silhouette. Verified on device: the dialog appears and the permission ends up granted. The exact-alarm settings prompt correctly did not fire, because a targetSdk 37 build is granted that permission by default (`appops` confirms `default`). Original finding: `POST_NOTIFICATIONS` and `SCHEDULE_EXACT_ALARM` are declared in the manifest but **never requested at runtime** — there is no `RequestPermission` call site anywhere. On API 33+ that means the permission is denied by default and the rest-over notification cannot appear until the user hunts down the toggle in Settings; on API 31+ the exact alarm silently degrades to inexact forever because the user is never sent to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Ask on the first logged set, not at launch (context beats a cold prompt), and make refusal non-fatal — the in-app timer already degrades correctly. `RestNotifications` also posts `R.drawable.ic_launcher` as the small icon, which must be a monochrome silhouette or it renders as a white blob. |
| F14 | **Ship identity + store readiness** | P0 | M | `applicationId` is still the `com.example.androidapp` placeholder, which Play rejects for new uploads and which cannot be changed later without orphaning Health Connect grants and any deep links; the label is "Android App"; the icon is one legacy vector with no adaptive/`mipmap-anydpi-v26` variant and no 512px Play asset; `versionCode`/`versionName` are hardcoded at 1/"1.0". Add the Play Console workstream: Data safety form, content rating, privacy-policy URL, Play App Signing. Health Connect (P4.1) adds its own declaration and is gated on this. |
| F15 | ✅ **Seed delivery: first open *and* upgrade** | P0 | S | **Landed.** Seeding moved from `onCreate` to `onOpen`, synchronously, with `INSERT OR IGNORE` — one path covering first launch and upgrade, no race against the first read, no `REPLACE`/`RESTRICT` trap, a user's soft-delete preserved, and the clock now injected. Extracted to [`ExerciseSeeder.kt`](app/src/main/java/com/example/androidapp/data/local/ExerciseSeeder.kt) so it is tested directly. Verified on device after `pm clear`: the library renders on the very first read with no empty-state flash. Original finding: Two problems in one place. **(a) Upgrade:** seeding runs in `RoomDatabase.Callback.onCreate`, which fires exactly once per database, so a later app version cannot deliver new seed exercises to an existing install; `ExerciseDao.count()` exists for an idempotent top-up but is called only from tests. The trap is that `insertAll` uses `OnConflictStrategy.REPLACE` (DELETE + INSERT), which violates `session_exercises.exerciseId ON DELETE RESTRICT` the moment history references the row — the top-up must upsert and skip existing ids, not REPLACE. **(b) First open:** the insert is dispatched to `@ApplicationScope` (`Dispatchers.Default`, [CoroutineModule.kt](app/src/main/java/com/example/androidapp/di/CoroutineModule.kt)) instead of running on the callback's own SQLite connection, so `observeExercises()` emits an empty library first and the first launch can flash the empty state (which P1.1 cannot distinguish from "no search match"). Insert synchronously on the `SupportSQLiteDatabase` in `onCreate` — that is the callback's whole purpose — and the race disappears. While here: the seed timestamp uses `System.currentTimeMillis()` directly rather than the injected `TimeSource`, the one place the clock abstraction leaks. Do all of this before the library grows, not after. |
| F16 | ✅ **Recomposition-safe clock** | P1 | M | **Landed.** The ticking values moved into a separate `WorkoutClock` flow holding only `elapsed` and `restSecondsRemaining`; the screen receives it as a `State` object it never reads, so only the header and the rest bar read it. `RestBar` decides whether to draw *itself* rather than the parent deciding on `isResting` — a parent check would have recomposed the list every second anyway. `isResting` is now derived (`restSecondsRemaining > 0`) instead of a second stored field. A JVM test states the property: a tick moves the clock and emits **no** new screen state, with an assertion that the tick fired, so it cannot pass trivially. Original finding: The 1-second `ticker` is combined into the single `ActiveWorkoutUiState`, and `elapsed`/`startedAt` are pre-formatted strings ([ActiveWorkoutViewModel.kt](app/src/main/java/com/example/androidapp/ui/workout/ActiveWorkoutViewModel.kt)). That state also holds `List`s, which Compose treats as unstable, so `ActiveWorkoutScreen` → `WorkoutBody` → `ExerciseList` all recompose every tick, list included. Keep the ticking values in a separate state read only by the header and rest bar (or `derivedStateOf`), and the exercise list leaves the per-second path entirely. This is the concrete cause behind the "frame timing mid-set" the quality bar names. |
| F17 | ✅ **Validate inputs at the boundary** | P1 | S | **Landed.** `parseKilograms` now requires plain decimal digits (no exponent, no hex) and caps at 1000 kg, so a fat-fingered entry can no longer become permanent history; `logSet` fails as `NotFound` unless the exercise is live *and* its session still open. Original finding: `Weight.parseKilograms` uses `toDoubleOrNull()` ([Weight.kt](app/src/main/java/com/example/androidapp/domain/Weight.kt)), which accepts `Double` grammar rather than user grammar — `"1e10"`, `"8d"` and hex floats all parse — and there is no upper bound, so a fat-fingered entry can store an absurd weight. Reject exponent notation and cap at a sane maximum. Separately, `logSet` inserts against any `sessionExerciseId` without checking the row is still live or that the session is still open ([RoomWorkoutRepository.kt](app/src/main/java/com/example/androidapp/data/RoomWorkoutRepository.kt)), so a stale UI can attach a set to a removed exercise. |
| F18 | **CI and repo hygiene** | P2 | S | No Dependabot/Renovate, and Actions are pinned to floating major tags rather than SHAs; no `gradle/wrapper-validation` even though the wrapper JAR is committed; test results are not annotated onto the PR, so a failure means digging through artifacts. Also absent repo-wide: `.editorconfig`, `LICENSE`, `CHANGELOG`. |

✅ **Placeholder deleted:** `GreetingScreen`, `Greeting.kt`, and its test are
gone. Their replacement keeps the same discipline — pure logic under `domain/`
with JVM tests, composables under `ui/`.

---

## Phase 1 — MVP: log a workout

The smallest thing a lifter will actually keep installed.

| # | Feature | Pri | Eff | Notes |
| --- | --- | --- | --- | --- |
| P1.1 | ◐ **Exercise library** | P0 | M | 30 seeded exercises with primary/secondary muscles, equipment, and movement pattern, persisted in Room and read through `ExerciseRepository`. Search matches name, muscle, and equipment — but only `primaryMuscle.label`, so "forearms" misses a row whose forearms are *secondary*, and the movement pattern is unsearchable at all. It also matches the display label, which P5.4 moves into `strings.xml` and would silently change search behaviour. Fix both (search secondary muscles too; match on a locale-stable key or keyword list) before adding P1.13. The screen also cannot tell "the library is empty" from "no search matched" — both render `No exercises match “”` — and because seeding is asynchronous on first open, that is exactly what a first launch can flash. |
| P1.2 | ✅ **Start an empty workout** | P0 | M | "Start workout" on the library opens a session and an "Add exercise" picker that reuses the library list. Start/resume is a single atomic find-or-create in the DAO, so two taps cannot open two workouts. **Two rough edges:** the picker lets the same exercise be added twice with no signal — decide whether that is intended (it is legitimate for separate blocks) and either mark already-added rows or state that it is allowed; and `ExercisePickerViewModel` silently swallows an `addExercise` failure, so the picker just fails to close with no explanation. |
| P1.3 | ◐ **Log sets/reps/weight** | P0 | M | Logging, prefill and undo landed. A "Log set" button writes a set using what you just did, else what you did last time, else a default; the exercise header shows "Last time: …"; tapping a set opens an editor; deleting offers an undo snackbar. Weights are stored as whole grams ([`Weight.kt`](app/src/main/java/com/example/androidapp/domain/Weight.kt)), so 0.5/1.25 kg steps stay exact and P1.9 becomes display-only. **Not yet:** swipe-to-delete (it is a button); the non-working set types — `SetType` has WARMUP/DROP/FAILURE but only NORMAL is reachable from the UI; and bodyweight/duration/distance sets, which the schema still cannot represent as anything but reps+weight, so adding them later is a migration rather than a UI change — settle the row shape before history accumulates. The set editor also uses plain text fields with no numeric keyboard (`keyboardOptions` appears nowhere in the app), which is the wrong shape for the one screen reached mid-set. And the "Log set" button commits instantly with no way to adjust first: `Weight.step` and `Weight.DEFAULT_STEP_GRAMS` exist for a +/- stepper but are referenced only from `WeightTest`, so either wire the stepper — it is the "fast number entry" this row promises — or delete the dead API. |
| P1.4 | ◐ **Rest timer** | P0 | S | Auto-starts on set completion; +15s/−15s and skip; the alert is scheduled with a single `AlarmManager` alarm rather than a foreground service (that is P4.2), falling back to an inexact alarm when `SCHEDULE_EXACT_ALARM` is not granted. It is stored as an absolute end instant, so it survives a process death — verified on device: after a force-stop the workout resumed with its set *and* the rest still counting down. **Not yet:** the configurable default, which needs a settings screen (90s is a constant); sound/haptic feedback (P1.14); and the fact that neither permission is ever actually requested, so the alert is likely dead on API 33+ — that is F13, and it is the highest-value fix in this table. |
| P1.5 | **Notes & RPE** | P1 | S | Per-set and per-workout notes; optional RPE/RIR field, off by default. |
| P1.6 | **Workout history** | P0 | M | Chronological list + detail view, grouped by week/month, with duration, volume, and set count. |
| P1.7 | **Edit/delete past workouts** | P1 | S | Correcting a mis-typed set is the most common post-hoc action. Until this (and P1.6) land, **Finish is irreversible from the UI** — one tap ends the workout and there is no screen to bring it back, so a confirmation dialog is cheap insurance now. |
| P1.8 | ✅ **Crash-safe in-progress session** | P0 | M | Landed with no separate mechanism: the session row is written the moment the workout opens and `finishedAt IS NULL` *is* "in progress", so a kill leaves it recoverable. Verified on device by force-stopping mid-workout — the relaunch resumed the same session with the same exercise and the elapsed clock still running from the original start time. |
| P1.9 | ◐ **Unit handling** | P1 | M | Storage landed ahead of schedule: weights are whole grams in a `Long` ([`Weight.kt`](app/src/main/java/com/example/androidapp/domain/Weight.kt)), so 0.5/1.25 kg steps stay exact and a unit change is pure presentation — the corruption this row warned about is already impossible. Remaining: the kg/lb (+ st) display setting and the parse/format switch behind it. |
| P1.10 | **Wake lock / keep screen on** | P1 | S | Keep the screen awake during an active session, with a setting. |
| P1.11 | **Onboarding + empty states** | P1 | S | Goal (strength/hypertrophy/fat loss), experience level, units, weekly target. Personalizes defaults and seeds the library ordering. |
| P1.12 | **Backup/export** | P2 | M | CSV/JSON export and import. Cheap insurance against uninstall, and a real differentiator. |
| P1.13 | **Custom exercises** | P1 | M | The entity and the domain model already carry `isCustom` and reserve UUID ids for user rows ([Exercise.kt](app/src/main/java/com/example/androidapp/domain/model/Exercise.kt)), yet nothing in this plan or the UI lets a user create one — so any movement outside the 30 seeds simply cannot be logged. Needs create/edit/soft-delete and a rule for history that references a user exercise. |
| P1.14 | **Rest feedback** | P1 | S | The rest timer is silent: no sound and no vibration, in-app or from the notification. A rest timer a lifter cannot hear from across the gym is half a feature. Also carries the monochrome small icon from F13. |
| P1.15 | **Repeat last workout** | P2 | S | One tap from the library into a fresh session holding the previous workout's exercises. `previousPerformance` already proves the query; this is the highest-retention action the app is currently missing. |
| P1.16 | **Resume affordance on the library** | P1 | S | Backing out of a workout leaves the session active but invisible: the library's FAB still reads "Start workout" with no elapsed time or sign that anything is in progress. P1.8's crash recovery is careful work that the *normal* back-out path quietly undoes. Drive the FAB from the active session — "Resume workout · 12:05" — and show the exercise count. |
| P1.17 | **Accessibility on the logging flow** | P1 | S | The concrete slice of P5.5, on the screen that matters most. Set rows are a bare `clickable` with no `onClickLabel`, so TalkBack says "double tap to activate" without saying it edits the set; the write-error `Text` has no live-region semantics, so a screen reader never hears that a set did not save (the exact failure F7 exists to surface); and the rest-over moment is only visual. Add `onClickLabel`, announce write failures, and add `testTag`s so the UI tests stop asserting on English literals P5.4 will change. |

---

## Phase 2 — Progress & insight

Why users open the app on a rest day.

| # | Feature | Pri | Eff | Notes |
| --- | --- | --- | --- | --- |
| P2.1 | **Per-exercise history** | P0 | M | Every past performance of one lift, set-by-set, with a small chart. |
| P2.2 | **Personal records** | P1 | M | Rep-max PRs, estimated 1RM (Epley/Brzycki), volume PRs. All-time and trailing-window. |
| P2.3 | **Charts & trends** | P1 | L | Volume per muscle group per week, bodyweight trend, tonnage, frequency heatmap. Needs a deliberate charting choice (Compose-native vs. a library). |
| P2.4 | **Body measurements** | P2 | M | Weight, body fat, circumferences; trend line and rate-of-change. |
| P2.5 | **Progress photos** | P2 | M | Encrypted local storage, side-by-side compare view, never uploaded without explicit consent. |
| P2.6 | **Plate calculator** | P2 | S | Per-side loading for a target weight given available plates; small, beloved, cheap. |
| P2.7 | **Warm-up set generator** | P2 | S | Percentage ramp to a working weight. |
| P2.8 | **Muscle-group balance warnings** | P2 | M | Flag neglected groups and push/pull imbalance over a rolling window. |

---

## Phase 3 — Programs & automation

Turns a logger into a training plan.

| # | Feature | Pri | Eff | Notes |
| --- | --- | --- | --- | --- |
| P3.1 | **Routine builder** | P1 | M | Named routines: ordered exercises, target sets × rep ranges, rest per exercise, supersets, drop sets. |
| P3.2 | **Start from routine** | P1 | S | Routine → session with targets shown as guidance vs. actual. |
| P3.3 | **Programs / mesocycles** | P2 | L | Multi-week schedules with scheduled deloads and progression rules (linear, double progression, percentage-based). |
| P3.4 | **Auto-progression suggestions** | P2 | L | "Last time you hit 3×8 at 60 kg — go to 62.5 kg." The highest-value differentiator once there's history. |
| P3.5 | **Rest-day & weekly scheduling** | P2 | M | Planned vs. completed adherence, calendar view. |
| P3.6 | **Superset/circuit execution** | P2 | M | Interleaved set logging with a shared timer. |
| P3.7 | **Template sharing** | P2 | M | Export/import a routine as a link or file. |

---

## Phase 4 — Platform integration

Where a phone-only logger becomes an Android app.

| # | Feature | Pri | Eff | Notes |
| --- | --- | --- | --- | --- |
| P4.1 | **Health Connect read/write** | P1 | L | Write `ExerciseSessionRecord`, read weight/body fat and steps. This is the current API — the Google Fit APIs are deprecated, so do not start there. Client SDK needs API 26+, and on Android 14+ Health Connect ships in the platform. |
| P4.2 | **Foreground service for a live session** | P1 | M | With the `health` foreground service type and `FOREGROUND_SERVICE_HEALTH`, so rest timers and session state survive backgrounding without being killed. |
| P4.3 | **Home-screen widget** | P2 | M | Glance widget: today's plan, "resume workout", streak. |
| P4.4 | **Quick Settings / launcher shortcuts** | P2 | S | Start the last routine in one tap. |
| P4.5 | **Wear OS companion** | P2 | L | Log sets and view the rest timer from the wrist. Genuinely useful mid-workout, genuinely expensive. |
| P4.6 | **Bluetooth heart-rate straps** | P2 | L | BLE GATT HR service; zone display and HR into the session record. |
| P4.7 | **Reminders & rest-day nudges** | P2 | S | WorkManager + notification channels, user-scheduled. |
| P4.8 | **Predictive back + large screens** | P2 | M | Predictive back is default at this `targetSdk`; add adaptive layouts for tablets/foldables. |
| P4.9 | **Offline-first sync** | P2 | L | Only if a multi-device or web story is planned. Local-first with a sync engine is a large commitment — decide before building the schema, not after. |

---

## Phase 5 — Growth & sustainability

| # | Feature | Pri | Eff | Notes |
| --- | --- | --- | --- | --- |
| P5.1 | **Streaks & consistency goals** | P2 | M | Weekly-target adherence rather than fragile daily streaks. |
| P5.2 | **Friends / shared routines** | P2 | L | Needs accounts, servers, and moderation. Not before product-market fit. |
| P5.3 | **Monetization** | P2 | M | Play Billing: free core logging, paid advanced analytics/auto-progression. Decide before building analytics, since it gates the boundary. |
| P5.4 | **Localization** | P2 | M | Extract strings, RTL audit (`supportsRtl` is already on), metric/imperial per locale. |
| P5.5 | **Accessibility** | P1 | M | TalkBack labels on set rows, ≥48dp targets, no color-only PR indicators, dynamic type at 200%. Number entry must work with a screen reader. Filed under Phase 5 only for scheduling: this is cross-cutting and should accompany each screen as it lands, not be retrofitted. Today set rows are bare `clickable` with no role or merged semantics, row subtitles join with `·` (announced as "middle dot"), and no screen exposes a `testTag`, so the UI tests assert on English literals that P5.4 will change. |

---

## Quality bar (applies throughout)

- **Testing:** ✅ domain math is covered (`Weight`, `WorkoutFormat`, `RestTimer`,
  `SetSuggestion`), ✅ schema history is guarded by `WorkoutDatabaseMigrationTest`
  plus the DAO and converter tests, ✅ the logging flow has ViewModel tests.
  **Not yet:** any Compose UI test for the workout screen — the one screen the
  app exists for; `ActiveWorkoutScreenTest` and a picker test are the gap the
  quality bar's own "Compose UI tests for the logging flow" names. Also missing:
  a screenshot test for the design system; `hilt-android-testing`, without which
  the Hilt-wired `Route` layers cannot be tested end-to-end — though
  `ExerciseDetailViewModel`'s argument reading *is* testable without it by
  constructing a `SavedStateHandle` directly, which is a cheap win; a test that
  the DAO's soft-delete filters actually hide deleted rows through the joined
  queries; a coverage signal in CI. And there is no test asserting the seed
  list's ids are unique — a duplicate would crash `LazyColumn`
  (`key = { it.id }`) and silently shadow the first row in `getExercise`.
- **Errors:** F7 covers *writes*. `ExerciseRepository.getExercise` and the
  `observeExercises()` flows are still unwrapped, so a Room failure there escapes
  as an exception and takes the screen down rather than rendering a failure. The
  detail screen also reads once (`flow { emit(getExercise(id)) }`) instead of
  observing, so it cannot reflect an edit and its `when` has no `else` for the
  loaded-but-null case. Make reads consistent with writes, or record why they are
  exempt.
- **Performance:** not yet measured, and one cause is already known — see **F16**,
  where the per-second ticker recomposes the whole workout screen. Then: baseline
  profiles, a Compose compiler stability report (every UI state here holds a
  `List`, which Compose treats as unstable), and frame timing on the logging
  screen since it is used mid-set while breathing hard.
- **Simplicity / no dead weight:** `Weight.step` and `DEFAULT_STEP_GRAMS` are
  referenced only from tests; `RestTimer.format` and `WorkoutFormat.elapsed` both
  implement `m:ss` and should collapse before either grows hours support;
  `SessionExerciseDetail.movementPattern` is selected by the join but dropped in
  `toDomain()`. Each is trivial, and each is a lie about what the code does.
- **Privacy:** health data stays on-device by default; any upload is explicit,
  documented, and revocable. Not yet decided: encryption at rest, an optional app
  lock, and a "delete all data" action. Ship a privacy policy before any Play
  release.
- **Observability:** crash reporting, plus a debug-only session log. A crash
  report must never carry set values, notes or body measurements.
- **CI that fails usefully:** a red build should say *what* broke without a
  download — see **F18** for Dependabot, SHA-pinned actions, wrapper validation,
  and PR test-result annotations.
- **No Google Play services at runtime:** the app is pure AndroidX today and runs
  on a degoogled device (verified against GrapheneOS). Keep that deliberate:
  F11 must not become Firebase (Crashlytics/Analytics need GMS), P4.5's
  `play-services-wearable` and P5.3's Play Billing would both stop working there,
  and Play Integrity must not be added. Consider a build check that fails if a
  `com.google.android.gms` / `firebase` / `com.google.android.play` artifact
  reaches a runtime classpath.

## Explicit non-goals (for now)

Nutrition/calorie tracking, social feeds, live GPS route tracking, and a
web dashboard. Each is a product in its own right and would dilute the logging
core.

## Suggested PR sequence

1. ✅ **F1 + F9** — backup rules and the `minSdk` decision. Landed: health data
   is excluded from every platform transfer path and `minSdk` is 26.
2. ✅ **F2 + F3 + F4** — navigation, ViewModels, and Hilt landed, the greeting
   counter is deleted, and the exercise library is a real screen backed by a
   repository interface.
3. ✅ **F5 + P1.2 + P1.3 + P1.4 + P1.8** — Room (schema v3, migrations tested),
   start/resume, set logging with prefill and undo, the rest timer, and
   crash-safe recovery all landed. It did get split into three commits, which is
   why the PR stayed reviewable.

**Next, in order:**

1. **F13 + F15 + F17** — the rest alert's permission is never requested, seed
   delivery is wrong on both first open and upgrade, and the weight parser accepts
   `Double` grammar. All three are small, all three are correctness, and all three
   get harder once there is real history.
2. **F16** — split the ticking clock out of the workout state before anything else
   is added to that screen, so the perf baseline is meaningful.
3. **F14** — ship identity (`applicationId`, label, adaptive icon, versioning) and
   the Play Console workstream. After the first real upload none of it is cheap to
   change.
4. **P1.16 + P1.6 + P1.7** — make the active session visible again, then build
   history and editing. The app can write history but cannot show or correct it,
   which is the wrong half of the loop to hold.
5. **P1.17 + F18 + F12's baseline wiring** — accessibility on the logging flow,
   and the CI/repo hygiene that keeps the gates honest.

## Appendix — features implied dependencies

| Feature | Library / API |
| --- | --- |
| F2, F8 | `navigation-compose`, `material3` + `material-icons-core` (`material-icons-extended` only if F8 needs it) |
| F3 | `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` |
| F4 | `hilt-android`, `hilt-compiler`, `hilt-navigation-compose`, KSP |
| F5, P1.1 | ✅ `room-runtime`, `room-compiler`, `room-testing` — seeded on first open, schema committed under `app/schemas/` |
| F7, P4.9 | ✅ a `DataResult` type over `kotlinx-coroutines`; serialization is already present for routes |
| F13 | `androidx.core` `NotificationManagerCompat` + `ActivityResultContracts.RequestPermission` |
| P2.3 | Compose-native canvas charts, or a vetted charting library |
| P4.1 | `androidx.health.connect:connect-client`, plus a `ACTION_SHOW_PERMISSIONS_RATIONALE` activity and the Play Health Connect declaration |
| P4.2, P4.7 | foreground service with the `health` type; `androidx.work` + `hilt-work` for reminders |
| P4.3 | `glance-appwidget` |
| P4.5 | `wear-compose` + `play-services-wearable` — ⚠️ the latter is a Play-services dependency; see the no-GMS quality bar note |
| P5.3 | `billing-ktx` — also Play-services-dependent |
| F12 | ✅ `lint`, `detekt` (`ktlint` is still referenced above but not configured) |
| F18 | `dependabot.yml`, `gradle/actions/wrapper-validation`, a PR test-report action, `.editorconfig` |

Platform notes drawn from the Android health & fitness developer guidance and
the foreground service types reference:
[Fit migration guide](https://developer.android.com/health-and-fitness/health-connect/migration/fit),
[basic fitness app guide](https://developer.android.com/health-and-fitness/guides/basic-fitness-app/overview),
[foreground service types](https://developer.android.com/guide/components/fg-service-types).
