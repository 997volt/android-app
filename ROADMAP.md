# Workout Tracker — Roadmap

> **v1.2** is shipped and installed. Last reviewed against the code: 2026-09-29.
>
> This file is forward-looking only. What shipped lives in
> [CHANGELOG.md](CHANGELOG.md); how a release is cut lives in
> [RELEASING.md](RELEASING.md).

**What this app is.** A local-only workout logger: start a workout, log sets, see
your history, keep your data.

**The scope rule.** If it does not help log a set faster or make the stored history
more trustworthy, it does not belong here. Anything that ships data off the device,
or needs an account or a server, is out by default.

Feature ids (`F#` foundations, `N#` the next planned changes, `P#.#` the product
backlog, `R#.#` releases) are stable and are referenced from commit messages. They
were assigned when the work was planned, so they do not run in order — the
`P4`/`P5` rows are simply the ones parked furthest out.

## Current state

- **The MVP is complete and released**: exercise library, start/resume, set logging
  with prefill and undo, rest timer, crash-safe sessions, workout history, edit and
  delete, export/import.
- **Local only.** No `INTERNET` permission, `allowBackup="false"`, no accounts, no
  analytics; the export file is the only path off the device.
- **Releases are manual**, signed with a permanent local key. The procedure and its
  traps are in [RELEASING.md](RELEASING.md), including why automation was declined.
- **One module, one activity**, Compose + Room + Hilt. Compose UI tests run on the
  JVM under Robolectric rather than on a device.

Run `./gradlew testDebugUnitTest` and `./gradlew connectedDebugAndroidTest` for the
current numbers; dependencies are declared in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml). Neither is repeated here on
purpose — see [Keeping this true](#keeping-this-true).

## Open decisions

Not tasks: choices with a real cost either way. Each names a trigger, so it can be
left alone without being forgotten.

| Decision | What it would take | Revisit when |
| --- | --- | --- |
| **Play Store listing** | A feature graphic (1024×500) and phone screenshots — the 512 px icon already exists and is generated from the app's own vector by [`tools/MakeStoreIcon.java`](tools/MakeStoreIcon.java). Then the console: Data safety, content rating, privacy-policy URL. Plus a real call on **Play App Signing**, which changes who holds the app signing key, while this project's release process assumes a permanent local one. | You want distribution beyond `adb install`. Self-install works today. |
| **A crash-logs screen** | A small screen over [`CrashLogStore`](app/src/main/java/com/example/androidapp/platform/CrashLogStore.kt). | Reading a crash through an export actually annoys you. |
| **Zone offset on sessions** | A `zoneOffset` column captured at session start, plus migration 3→4. Timestamps are UTC epoch millis today, so "which day was this" is answered in the *current* zone and drifts when you travel. | You train in a second timezone, or a feature needs local-day truth. |
| **Encryption at rest / app lock** | A key-management story, not just a library: where the key lives, and what happens when the phone is lost. | You start carrying the phone somewhere you would not carry the data. |
| **The rest alert: keep or remove** | Removing the alarm and notification path deletes both manifest permissions and the whole `platform/` alert code. The in-app timer, plus sound/haptics and keep-screen-on, cover the same need. | You never use the background alert, or you want the permission surface to be zero. |

## Next — planned app changes

Changes left to land before anything in *Later*. N1 — home as the start
destination — is done; it is in [CHANGELOG.md](CHANGELOG.md) under *Unreleased*,
because shipped work lives there rather than here.

- **N3** is what can be set up in advance.
- **N2 and N4–N8** are what a workout captures while you are in it.

### N2 — Custom exercises while you train

- **Create a custom exercise from inside a workout**, where the gap is actually felt:
  a "New exercise" action in the picker that saves and immediately adds it to the
  session. Stored `isCustom = true` with a UUID id — the schema already reserves both.
- It then appears in the library and in search like any other exercise.
- Decide at implementation time: how much taxonomy a custom entry captures (name
  only, versus muscle, equipment and pattern), and whether custom exercises can be
  edited or deleted afterwards.

### N3 — Workout templates, planned before you train

A named, reusable workout defined ahead of time and started in one tap.

- Create, edit and delete a template: a name and an ordered list of exercises.
- Start a workout from one: the session opens with its exercises already in order,
  reusing the existing add-exercise path.
- Surfaced from the home screen's start action — **Start empty** or **Start from
  template**.
- **v1 scope is exercises and their order.** The fuller routine features already on
  the backlog — target sets × rep ranges, per-exercise rest, supersets, drop sets
  (P3.1) — come afterwards, once templates are in use.

This promotes P3.1 + P3.2 ahead of the rest of the backlog, with a deliberately
smaller first version. It needs two new sync-shaped tables (`templates`,
`template_exercises`) and therefore a migration, with the exported schema and a
`MigrationTestHelper` test — the path migrations 1→2 and 2→3 already took.

### N4 — A readiness note when a workout starts

A free-text field for what is not recovered today: "shoulders still sore from
Monday", "slept badly, legs heavy".

- **Decided: prompt when the session opens**, skippable, and editable afterwards
  from the workout header — the day it matters is the day a passive field gets
  ignored.
- Stored on the session (`readinessNote`), so it rides through history, the workout
  detail, and export.
- **Deliberately free text.** The structured version — picking the sore groups from
  the existing `MuscleGroup` taxonomy — is a later step, and the column does not
  change to get there.

### N5 — Per-exercise rest, and technique cues

Two attributes on a library exercise:

- **Rest, per exercise** — `restSeconds` on `exercises`, falling back to today's
  90 s default when unset. The +15 s/−15 s controls stay one-off adjustments to the
  current rest; this is the exercise's own default.
- **Technique cues** — `techniqueNote`: a short "chest up, elbows tucked" shown under
  the exercise name on the active workout screen and on the detail screen. It is the
  note you want *while* lifting, not a description of the movement.

**Decided: library-level only for now** — no per-session override. Because seeded
exercises must be editable too, the exercise detail screen gains an edit mode. That
is safe: the seeder uses `INSERT OR IGNORE` and never updates an existing row, so an
edited rest or cue survives every future top-up — which is why this can be a plain
column rather than an overrides table.

### N6 — RPE and a comment on every set

- `rpe` (1–10) and `note` on `set_entries`.
- **Decided: RPE is always visible in the set editor and may be left empty.**
- The one-tap **Log set** path still writes neither, so logging stays fast; the row
  shows a small marker when either is set, and the workout detail shows the text.

This **replaces P1.5**, which asked for per-set notes and RPE in the abstract.

### N7 — End an exercise, so sets cannot be added by accident

- `finishedAt` on `session_exercises`. A **Done** action per exercise hides the
  "Log set" button and dims the sets.
- **Decided: a done exercise's sets cannot be edited, and a Reopen button restores
  editing** — accident protection must not become its own trap. An undo on the
  snackbar covers the immediate mis-tap.
- Ending an exercise clears any running rest.
- Wording matters: the workout-level action is already called *Finish*, so this is
  *Done*, never *Finish*.
- Done is a session state, not a delete: the sets stay in history.

### N8 — How it felt: muscle and joints

Captured when an exercise is marked done (N7):

- **Muscle feel, 1–10** — how well the target muscle was worked.
- **Joint pain, 1–10** — discomfort in joints or connective tissue.
- Stored per session exercise (`muscleFeel`, `jointPain`), so the same movement is
  measured differently on different days. Skippable, and editable later from the
  workout detail.
- **Decided: numbers only for now** — no on-screen anchor for what 1 and 10 mean.
  Recorded as a decision rather than an oversight: an unlabelled scale drifts
  between sessions, so labelling the ends is the obvious first refinement.
- Feeds **P2.8** (balance warnings), or a discomfort view of its own.

**Schema (N4–N8).** Every column is nullable and additive, the shape migration 2→3
already used for `restEndsAt` — no backfill, no rewrite:

| Table | New columns |
| --- | --- |
| `exercises` | `restSeconds`, `techniqueNote` |
| `set_entries` | `rpe`, `note` |
| `session_exercises` | `finishedAt`, `muscleFeel`, `jointPain` |
| `workout_sessions` | `readinessNote` |

`workout_sessions.notes` already exists but nothing sets it, so `readinessNote` stays
separate: a future per-workout note should not collide with a readiness note.

**The version bump is shared with N3.** Whichever lands first takes 3→4 and the other
4→5, or they fold into one migration if they ship together. Decide before writing
either — two half-migrations is what the `MigrationTestHelper` tests exist to catch.

**Order:** N2 and N3 hang off the home screen N1 introduced and are otherwise
independent; so are N4, N5 and N6. The one hard dependency is **N7 before N8**, since
the ratings are captured at the moment an exercise is done. Everything else stays in
*Later* until these land.

## Later (still self-contained)

Post-MVP, same local-only premise. Grouped by theme, ordered by value inside each.

**Everyday logging**
- **P1.15** Repeat last workout in one tap.
- **P1.14** Rest sound / haptic feedback.
- **P1.10** Keep the screen on during a workout.
- **P1.9** kg/lb display setting — storage is canonical grams, so this is UI only.
- **P1.11** Onboarding: goal, experience level, weekly target.

**Insight** — why the app gets opened between workouts
- **P2.1** Per-exercise history.
- **P2.2** Personal records and estimated 1RM.
- **P2.3** Charts and trends — choose the charting approach before starting.
- **P2.8** Muscle-group balance warnings.
- **P2.4** Body measurements.
- **P2.5** Progress photos, in encrypted local storage.

**Programming** — turns a logger into a plan
- **P3.1 + P3.2** are promoted to **N3** above (templates, exercises and order only).
  The remaining routine scope — target sets × rep ranges, supersets, drop sets —
  stays here and follows N3. Per-exercise rest has moved to **N5** as a library
  attribute; a per-*routine* rest override would still belong here.
- **P3.4** Auto-progression suggestions — the strongest differentiator once there is
  enough history to base them on.
- **P3.3** Programs / mesocycles with scheduled deloads.
- **P3.5 + P3.6** Weekly scheduling; supersets and circuits.

**Small and self-contained**
- **P2.6** Plate calculator.
- **P2.7** Warm-up set generator.

**Quality follow-through**
- **P1.17** Full accessibility pass; the MVP slice is a quality-bar rule below.

Design-system work (**F8**) is a rule rather than a row now: extract a component when
a second screen needs it, not before.

## Parked — deliberately not planned

Each is a product in its own right, contradicts "local-only", or both. Parking is a
decision, not a backlog. Every row names what would change it.

| # | Feature | Revisit only if |
| --- | --- | --- |
| P4.1 | Health Connect read/write | A user asks to share with a platform health graph. It is a sharing integration; this app stores data for its user. |
| P4.2 | Foreground service | The rest timer needs to survive something the alarm and the in-app timer cannot. |
| P4.3 | Home-screen widget | The glance it would give turns out to be the missing thing. |
| P4.4 | Quick Settings / launcher shortcuts | Starting a routine becomes frequent enough to deserve a second entry point. |
| P4.5 | Wear OS companion | Wrist logging is genuinely wanted — and you accept `play-services-wearable`, which breaks the no-GMS line. |
| P4.6 | Bluetooth heart-rate straps | The product becomes heart-rate training rather than logging. |
| P4.7 | WorkManager reminders | Nudges demonstrably improve adherence. |
| P4.8 | Large-screen layouts | Tablet or foldable users actually appear. |
| P4.9 | Offline-first sync | There is a real multi-device story. It needs a backend, accounts and conflict resolution — the largest irreversible commitment on this list. |
| P3.7, P5.2 | Friends, shared routines | Accounts, servers and moderation become worth owning. |
| P5.3 | Monetization / Play Billing | There is a concrete reason to charge, and a willingness to take the Play-services dependency. |
| P5.4 | Localization | A non-English user appears. |
| F6 | Module split into `:core:*` / `:feature:*` | **A named goal, not a refactor**: a measured build-time problem, working on one feature without compiling the rest, or a second surface (Wear, a widget). Revisited after v1.2 and re-affirmed. |
| F11b | Product analytics | Almost certainly never: on a single-user local tool it buys nothing, and it would breach the no-`INTERNET` line. |

## Quality bar

Rules to follow, not a status report.

- **Accessibility accompanies each screen**; it is not a later phase. Name what a
  control does (`onClickLabel`), *announce* state changes rather than only drawing
  them, and tag things so tests do not assert on English literals.
- **Privacy:** local-only. No `INTERNET` permission, no ads, no analytics. Crash logs
  stay in app-private storage and leave only inside an export the user chose to make.
- **No Google Play services at runtime.** The app runs on a degoogled device.
  Firebase, `play-services-*`, Play Billing and Play Integrity are out by default; a
  future integration has to argue past this line.
- **Errors are values.** Writes return `DataResult`. Reads do not yet —
  `ExerciseRepository` can still throw out of a flow and take a screen down. Make them
  match, or record why they are exempt.
- **Measure before optimizing.** The one known hot spot — a per-second recomposition
  of the workout list — was found by reading the code and is fixed. Any further
  performance claim should come with a measurement.
- **Testing:** pure logic gets JVM tests, persistence gets DAO and migration tests,
  composables get Robolectric tests with no device. There is no coverage target, and
  the honest gap is a Compose test for the *active workout* screen.
- **No dead weight.** Extract a shared component at its second caller, not its first;
  delete an API the moment nothing calls it. Both hold today; this rule keeps them.

## Explicit non-goals

Nutrition / calorie tracking, social feeds, live GPS route tracking, and a web
dashboard. Each is a product in its own right and would dilute the logging core.

## Decisions already made

Recorded so they are not relitigated:

- **Weights are whole grams in a `Long`**
  ([Weight.kt](app/src/main/java/com/example/androidapp/domain/Weight.kt)) — exact
  0.5 kg and 1.25 kg steps, no floating-point drift, and units are presentational.
- **The v1 set row is `reps × weight`.** Bodyweight is reps at 0 kg — accepted,
  clamped, and asserted by a test. Duration and distance are out of scope; adding
  them later is migration 3→4 plus a test, a path this app has already walked twice.
- **Enums are stored by name**, never ordinal, so reordering cannot reinterpret rows
  already on disk.
- **Rows are sync-shaped** — UUID ids and `createdAt`/`updatedAt`/`deletedAt` soft
  deletes — so a future sync stays a decision, not a migration. The zone offset is
  the one missing piece; it is an open decision above.
- **The lint baseline is unwired on purpose.** Accepting a warning is a two-step,
  reviewed act, not a side effect of running the build.
- **Releases are manual**, and the tag must point at the commit that built the APK.
- **Verified on device:** process death mid-workout resumes the session with its set
  and rest intact; the library renders on the first read after `pm clear`; v1.1
  installed over v1.0 and kept the history.

## Keeping this true

Three rules, because the drift they prevent has already happened twice:

1. **Nothing marked done lives here.** Shipped work goes to
   [CHANGELOG.md](CHANGELOG.md), and a finished row is deleted from this file.
2. **No hand-maintained facts.** No test counts, no dependency lists, no inventory of
   which files exist. Those are commands (`./gradlew …`) or links
   ([libs.versions.toml](gradle/libs.versions.toml), [app/schemas](app/schemas)).
3. **Every parked row names its revisit trigger**, so parking reads as a decision
   rather than a forgotten item.

Bump the review stamp at the top whenever this file is checked against the code.

## References

- [CHANGELOG.md](CHANGELOG.md) — what shipped, per version, with the reasoning
- [RELEASING.md](RELEASING.md) — the release procedure and its traps
- [README.md](README.md) — build, install on your own phone, local toolchain
