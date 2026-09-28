# Workout Tracker — Feature Roadmap

A prioritized feature list for turning the current Compose scaffold into a
shippable workout tracker.

## Where the app stands today

The repo is a Compose app with one real feature: an exercise library. Concretely
it now has:

- One `ComponentActivity` ([MainActivity.kt](app/src/main/java/com/example/androidapp/MainActivity.kt))
  doing nothing but host the navigation graph (F2).
- Type-safe navigation between the library and per-exercise detail
  ([Routes.kt](app/src/main/java/com/example/androidapp/ui/navigation/Routes.kt)).
- `@HiltViewModel` ViewModels exposing `StateFlow<UiState>`, collected with
  `collectAsStateWithLifecycle` (F3) and injected with Hilt (F4).
- A `domain`/`data` package split behind an `ExerciseRepository` interface, with
  an in-memory implementation standing in for Room until F5.
- Material 3 theming, edge-to-edge, `compileSdk`/`targetSdk` 37, `minSdk` 26.
- 11 JVM unit tests and 4 instrumented Compose tests, all passing.
- CI on every push and PR (F10), with lint and detekt both failing the build on
  warnings (F12), and R8 + resource shrinking on `release`.
- A manifest that opts out of cloud backup, device-to-device transfer, and iOS
  cross-platform transfer entirely (F1).

Still missing: a local database (F5), networking, and WorkManager.

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
sync-ready: UUID primary keys, soft deletes, and `createdAt`/`updatedAt`
timestamps.

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
| F5 | **Local persistence** | P0 | M | Room + KSP. Schema export on, migrations from day one (`exportSchema = true`, no `fallbackToDestructiveMigration` in release). |
| F6 | **Domain + data modules** | P1 | M | Split into `:app`, `:core:data`, `:core:domain`, `:feature:workout`, … so build times and ownership stay sane. Optional at MVP, painful later. |
| F7 | **Result/error model** | P1 | S | A `Result`-like type plus a `DataError` taxonomy, so failures are values rather than thrown exceptions in repositories. |
| F8 | **Design system layer** | P1 | M | Promote `ui/theme` into a real component library: buttons, list rows, empty/error/loading states, number pickers. Workout logging is number-entry heavy. |
| F9 | ✅ **`java.time` on API 24** | P0 | S | Landed. `minSdk` raised to 26: `java.time` is native (no desugaring) and Health Connect's floor is met for P4.1. |
| F10 | ✅ **Release pipeline** | P1 | M | Landed. GitHub Actions runs `testDebugUnitTest`, `lint`, `detekt`, `assembleDebug` and `assembleRelease` on every push and PR, plus a second job for the instrumented tests on a KVM runner. Release has R8 + resource shrinking (12 MB debug → 1.4 MB release) and signs from a gitignored `keystore.properties`; without that file it still builds unsigned, which is what CI does. R8 mapping is uploaded for readable crash traces. |
| F11 | **Crash + analytics** | P1 | S | Crash reporting with symbol upload; privacy-respecting product analytics with opt-out. |
| F12 | ✅ **Lint/detekt gate** | P2 | S | Landed. Lint runs with `warningsAsErrors`; detekt runs on its defaults plus the Compose ruleset, with five narrowly-scoped, commented exceptions (Compose's PascalCase naming, `@Preview` "unused" members, the colour palette, and the seed table). The Compose ruleset is pinned to 0.4.x because 0.5.0+ targets detekt 2.0 and silently registers nothing against 1.23.x. |

✅ **Placeholder deleted:** `GreetingScreen`, `Greeting.kt`, and its test are
gone. Their replacement keeps the same discipline — pure logic under `domain/`
with JVM tests, composables under `ui/`.

---

## Phase 1 — MVP: log a workout

The smallest thing a lifter will actually keep installed.

| # | Feature | Pri | Eff | Notes |
| --- | --- | --- | --- | --- |
| P1.1 | ◐ **Exercise library** | P0 | M | 30 seeded exercises with primary/secondary muscles, equipment, and movement pattern, read through `ExerciseRepository`. Search filters on name, muscle, and equipment. Currently in-memory — the prepackaged Room DB lands with F5. |
| P1.2 | **Start an empty workout** | P0 | M | "Start workout" → timed session, add exercises as you go. This flow is the product; it must be ≤ 1 tap to first set. |
| P1.3 | **Log sets/reps/weight** | P0 | M | Fast number entry, previous-session values pre-filled as placeholders, swipe to delete, undo. Support bodyweight/assisted/duration/distance set types. |
| P1.4 | **Rest timer** | P0 | S | Auto-starts on set completion, configurable default, notification when backgrounded, +15s/−15s and skip. |
| P1.5 | **Notes & RPE** | P1 | S | Per-set and per-workout notes; optional RPE/RIR field, off by default. |
| P1.6 | **Workout history** | P0 | M | Chronological list + detail view, grouped by week/month, with duration, volume, and set count. |
| P1.7 | **Edit/delete past workouts** | P1 | S | Correcting a mis-typed set is the most common post-hoc action. |
| P1.8 | **Crash-safe in-progress session** | P0 | M | Persist the active session continuously; survive process death and reboot with the timer intact. Silently losing a workout ends trust in the app. |
| P1.9 | **Unit handling** | P1 | M | kg/lb (+ st) as a display setting, stored canonically in one unit. Getting this wrong corrupts every historical record. |
| P1.10 | **Wake lock / keep screen on** | P1 | S | Keep the screen awake during an active session, with a setting. |
| P1.11 | **Onboarding + empty states** | P1 | S | Goal (strength/hypertrophy/fat loss), experience level, units, weekly target. Personalizes defaults and seeds the library ordering. |
| P1.12 | **Backup/export** | P2 | M | CSV/JSON export and import. Cheap insurance against uninstall, and a real differentiator. |

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
| P5.5 | **Accessibility** | P1 | M | TalkBack labels on set rows, ≥48dp targets, no color-only PR indicators, dynamic type at 200%. Number entry must work with a screen reader. |

---

## Quality bar (applies throughout)

- **Testing:** unit tests for all domain math (1RM, volume, unit conversion,
  progression); Room migration tests; Compose UI tests for the logging flow;
  a screenshot test for the design system.
- **Performance:** baseline profiles, Compose stability (`@Stable`/immutable
  collections), no main-thread DB access, frame timing on the logging screen
  since it is used mid-set while breathing hard.
- **Privacy:** health data stays on-device by default; any upload is explicit,
  documented, and revocable. Ship a privacy policy before any Play release.
- **Observability:** crash reporting, plus a debug-only session log.

## Explicit non-goals (for now)

Nutrition/calorie tracking, social feeds, live GPS route tracking, and a
web dashboard. Each is a product in its own right and would dilute the logging
core.

## Suggested first three PRs

1. ✅ **F1 + F9** — backup rules and the `minSdk` decision. Landed: health data
   is excluded from every platform transfer path and `minSdk` is 26.
2. ✅ **F2 + F3 + F4** — navigation, ViewModels, and Hilt landed, the greeting
   counter is deleted, and the exercise library is a real screen backed by a
   repository interface.
3. **F5 + P1.2 + P1.3** — Room plus start-workout and set logging, behind a
   repository interface with unit tests from the first commit.

## Appendix — features implied dependencies

| Feature | Library / API |
| --- | --- |
| F2, F8 | `navigation-compose`, `material3` + `material-icons-extended` |
| F3 | `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` |
| F4 | `hilt-android`, `hilt-compiler`, `hilt-navigation-compose`, KSP |
| F5, P1.1 | `room-runtime`, `room-ktx`, `room-compiler` (prepackaged DB asset) |
| F7, P4.9 | `kotlinx-serialization` or Moshi; `kotlinx-coroutines` |
| P2.3 | Compose-native canvas charts, or a vetted charting library |
| P4.1 | `androidx.health.connect:connect-client` |
| P4.3 | `glance-appwidget` |
| P4.5 | `wear-compose` + `play-services-wearable` |
| P5.3 | `billing-ktx` |
| F12 | `lint`, `detekt`, `ktlint` |

Platform notes drawn from the Android health & fitness developer guidance and
the foreground service types reference:
[Fit migration guide](https://developer.android.com/health-and-fitness/health-connect/migration/fit),
[basic fitness app guide](https://developer.android.com/health-and-fitness/guides/basic-fitness-app/overview),
[foreground service types](https://developer.android.com/guide/components/fg-service-types).
