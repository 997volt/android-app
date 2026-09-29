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
- **Templates are the v1 half of P3.1**: a name and an ordered list of exercises,
  started in one tap. Target sets × rep ranges, per-exercise rest, supersets and drop
  sets are still in *Later*.

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
| **Zone offset on sessions** | A `zoneOffset` column captured at session start, plus a migration. Timestamps are UTC epoch millis today, so "which day was this" is answered in the *current* zone and drifts when you travel. | You train in a second timezone, or a feature needs local-day truth. |
| **Encryption at rest / app lock** | A key-management story, not just a library: where the key lives, and what happens when the phone is lost. | You start carrying the phone somewhere you would not carry the data. |
| **The rest alert: keep or remove** | Removing the alarm and notification path deletes both manifest permissions and the whole `platform/` alert code. The in-app timer, plus sound/haptics and keep-screen-on, cover the same need. | You never use the background alert, or you want the permission surface to be zero. |

## Next — planned app changes

**Nothing.** Every change in this batch has shipped — N1 (home as the start
destination), N2 (custom exercises while you train), N3 (workout templates), N4 (the
readiness note), N5 (per-exercise rest and cues), N6 (RPE and a comment per set),
N7 (Done per exercise) and N8 (how it felt). They are in
[CHANGELOG.md](CHANGELOG.md) under *Unreleased*, because shipped work lives there
rather than here.

What comes next is chosen from *Later* below, which is where candidates live until
one is picked up and spelled out as a numbered change here.

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
- **P3.1 + P3.2** shipped their v1 as **N3** (templates: a name, exercises, order).
  The remaining routine scope — target sets × rep ranges, supersets, drop sets —
  stays here and builds on it. Per-exercise rest shipped as **N5**, a library
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
  composables get Robolectric tests with no device. There is no coverage target. The
  long-standing gap — a Compose test for the *active workout* screen — closed with
  N7, which is the feature that made the screen's Done/Reopen and set-editability
  rules worth asserting rather than eyeballing.
- **No dead weight.** Extract a shared component at its second caller, not its first;
  delete an API the moment nothing calls it. Both hold today; this rule keeps them.
- **Schema changes are migration-numbered as they ship.** A migration takes the next
  version when its feature lands; do not add columns or tables ahead of the code that
  reads them, because Room validates the declared entities against the migrated
  schema — an early column forces an entity field nothing reads. Every migration gets
  an exported schema under [app/schemas](app/schemas) and a `MigrationTestHelper`
  test that upgrades a database with real rows in it.

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
  them later is one more migration plus a test, a path this app has walked repeatedly.
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
