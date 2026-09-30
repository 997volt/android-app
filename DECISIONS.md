# Decisions

Settled choices for Workout Log, kept out of [ROADMAP.md](ROADMAP.md) so that file can
stay a queue. Nothing here is a task: each entry is a decision already taken, written
down so it is not relitigated by accident.

## Data model

- **Weights are whole grams in a `Long`**
  ([Weight.kt](app/src/main/java/com/example/androidapp/domain/Weight.kt)) — exact
  0.5 kg and 1.25 kg steps, no floating-point drift, and units are presentational.
- **The v1 set row is `reps × weight`.** Bodyweight is reps at 0 kg — accepted, clamped,
  and asserted by a test. Duration and distance are out of scope; adding them later is
  one more migration plus a test, a path this app has walked repeatedly. **N15 extends
  this** with an `assistanceGrams` field beside the weight, rather than letting a signed
  weight carry two meanings.
- **Enums are stored by name**, never ordinal, so reordering cannot reinterpret rows
  already on disk.
- **Rows are sync-shaped** — UUID ids and `createdAt`/`updatedAt`/`deletedAt` soft
  deletes — so a future sync stays a decision, not a migration. The zone offset is the
  one missing piece, and it is an open decision in the roadmap.

## Rules that apply to every change

- **Accessibility accompanies each screen**; it is not a later phase. Name what a control
  does (`onClickLabel`), *announce* state changes rather than only drawing them, and tag
  things so tests do not assert on English literals.
- **Privacy:** local-only. No `INTERNET` permission, no ads, no analytics. Crash logs stay
  in app-private storage and leave only inside an export the user chose to make.
- **No Google Play services at runtime.** The app runs on a degoogled device. Firebase,
  `play-services-*`, Play Billing and Play Integrity are out by default; a future
  integration has to argue past this line.
- **Errors are values.** Reads and writes both return `DataResult`, so a failure is
  something a screen can render rather than an exception that disappears inside a
  coroutine. The last two hold-outs — `ExerciseRepository`'s two reads — were closed by
  B4; keep it that way for anything new.
- **Measure before optimizing.** The one known hot spot — a per-second recomposition of
  the workout list — was found by reading the code and is fixed. Any further performance
  claim should come with a measurement.
- **Testing:** pure logic gets JVM tests, persistence gets DAO and migration tests,
  composables get Robolectric tests with no device. There is no coverage target. The
  long-standing gap — a Compose test for the *active workout* screen — closed with N7.
- **No dead weight.** Extract a shared component at its second caller, not its first;
  delete an API the moment nothing calls it. Both hold today; this rule keeps them.
- **Schema changes are migration-numbered as they ship.** A migration takes the next
  version when its feature lands; do not add columns or tables ahead of the code that
  reads them, because Room validates the declared entities against the migrated schema —
  an early column forces an entity field nothing reads. Every migration gets an exported
  schema under [app/schemas](app/schemas) and a `MigrationTestHelper` test that upgrades a
  database with real rows in it.

## Process

- **The lint baseline is unwired on purpose.** Accepting a warning is a two-step, reviewed
  act, not a side effect of running the build.
- **Releases are manual**, and the tag must point at the commit that built the APK. The
  procedure is in [RELEASING.md](RELEASING.md).

## Verified on device

Not rules — facts that were checked on hardware, recorded because they are the kind that
quietly stop being true:

- Process death mid-workout resumes the session with its set and rest intact.
- The library renders on the very first read after `pm clear`.
- v1.1 installed over v1.0 and kept the history.
