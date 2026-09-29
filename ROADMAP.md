# Workout Tracker — Roadmap

> **v1.3** is shipped and installed. Last reviewed against the code: 2026-09-29.
>
> This file is forward-looking only. What shipped lives in
> [CHANGELOG.md](CHANGELOG.md); how a release is cut lives in
> [RELEASING.md](RELEASING.md).

**What this app is.** A local-only workout logger: start a workout, log sets, see
your history, keep your data.

**The scope rule.** If it does not help log a set faster or make the stored history
more trustworthy, it does not belong here. Anything that ships data off the device,
or needs an account or a server, is out by default.

Feature ids (`F#` foundations, `B#` defects, `N#` the next planned changes,
`P#.#` the product backlog, `R#.#` releases) are stable and are referenced from
commit messages. They were assigned when the work was planned, so they do not run in
order — the `P4`/`P5` rows are simply the ones parked furthest out, and `N1`–`N8` have
shipped and left the file.

## Current state

- **The whole loop works and is released**: start a workout, log sets, see the
  history, get the data out. What that includes at any moment is
  [CHANGELOG.md](CHANGELOG.md)'s job, not this file's — an enumerated feature list is
  precisely the kind of fact that drifts.
- **Local only.** No `INTERNET` permission, `allowBackup="false"`, no accounts, no
  analytics; the export file is the only path off the device.
- **Releases are manual**, signed with a permanent local key. The procedure and its
  traps are in [RELEASING.md](RELEASING.md), including why automation was declined.
- **One module, one activity**, Compose + Room + Hilt. Compose UI tests run on the
  JVM under Robolectric rather than on a device.
- **Templates are the v1 half of P3.1**: a name and an ordered list of exercises,
  started in one tap. The rest of the routine scope is in *Later*.

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

## Next

Three additions to what a workout records. Everything shipped so far is in
[CHANGELOG.md](CHANGELOG.md); this section holds only what is still queued.

| # | Addition | Decision |
| --- | --- | --- |
| N9 | Joint pain location | A text box under the rating — the batch's only migration |
| N10 | Ratings before finishing | Editable at any time, not only at the Done prompt |
| N11 | A workout comment | A skippable prompt on Finish; no migration |

### N9 — Joint pain location

A text box under the joint-pain rating, for *which* joints: "left shoulder", "right
knee". `session_exercises.jointPainNote`, nullable — **this batch's only migration**
(the database is at v9 after templates, so 9→10).

The trap worth naming: the export is a hand-written codec, so a column missing from
the backup DTO is silently dropped by export and lost on restore. The migration test
and the round-trip test both need the field.

### N10 — Muscle feel and joint pain before finishing

The two ratings are only asked for at the moment an exercise is marked Done. Make
them editable at any time on the session exercise, so how a set felt can be recorded
while it is fresh.

The write path already exists (`rateExercise`); this is a UI entry point. The Done
prompt stays as a convenience, and becomes a last chance rather than the only one.
N9's location box belongs with it.

### N11 — A general comment on the workout

**Decided: a skippable prompt when the workout is finished**, with the text shown in
the workout detail afterwards — the moment you finish is when you remember why it
went well or badly.

**No migration.** `workout_sessions.notes` has been in the schema since v1, is
already carried by export and import, and has never had a domain field or a UI. This
is what it was reserved for; it needs the domain field, a repository setter, the
prompt, and a line on the detail screen.

## Later (still self-contained)

Post-MVP, same local-only premise. Grouped by theme, ordered by value inside each.

This is where candidates live. One graduates to *Next* — gaining a `B#` or `N#` id and
a spelled-out decision — when it is picked up, and leaves for
[CHANGELOG.md](CHANGELOG.md) when it ships.

**Everyday logging**
- **P1.15** Repeat last workout in one tap.
- **P1.14** Rest sound / haptic feedback.
- **P1.10** Keep the screen on during a workout.
- **P1.9** kg/lb display setting — storage is canonical grams, so this is UI only.
- **P1.11** Onboarding: goal, experience level, weekly target.
- **P1.18** Post-workout summary on Finish — duration, volume, sets, best set, and the
  readiness note and ratings the workout collected.

**Insight** — why the app gets opened between workouts
- **P2.1** Per-exercise history.
- **P2.2** Personal records and estimated 1RM.
- **P2.3** Charts and trends — N13 settled the approach for the first three series
  (hand-drawn on a `Canvas`, no dependency); a fuller chart screen, per-exercise
  history and PRs over months, is still this and can reconsider.
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
- **P1.17** Accessibility audit — a TalkBack pass over every screen, dynamic type at
  200%, and a contrast check. The per-screen rule is in the quality bar; this is the
  sweep that finds what the rule missed.

Design-system work (**F8**) is a rule rather than a row now: extract a component when
a second screen needs it, not before.

## Parked — deliberately not planned

Each is a product in its own right, contradicts "local-only", or both. Parking is a
decision, not a backlog. Every row names what would change it.

Parked is **not** the same as the non-goals below: these become possible again the
moment their trigger fires, while a non-goal is a line this app does not cross.

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
- **Errors are values.** Reads and writes both return `DataResult`, so a failure is
  something a screen can render rather than an exception that disappears inside a
  coroutine. The last two hold-outs — `ExerciseRepository`'s two reads — were closed
  by B4; keep it that way for anything new.
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

Permanent, unlike *Parked* above: nutrition / calorie tracking, social feeds, live GPS
route tracking, and a web dashboard. Each is a product in its own right and would
dilute the logging core.

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

Three rules. The drift they prevent has now happened three times — stale test counts,
a dependency inventory, and an enumerated feature list that v1.3 quietly outgrew:

1. **Nothing marked done lives here.** Shipped work goes to
   [CHANGELOG.md](CHANGELOG.md), and a finished row is deleted from this file.
2. **No hand-maintained facts.** No test counts, no dependency lists, no inventory of
   which files exist. Those are commands (`./gradlew …`) or links
   ([libs.versions.toml](gradle/libs.versions.toml), [app/schemas](app/schemas)).
3. **Every parked row names its revisit trigger**, so parking reads as a decision
   rather than a forgotten item.

Bump the review stamp at the top whenever this file is checked against the code.

**Size check.** This file is at roughly 300 lines — the point where it stops being a
queue and starts being a reference. *Decisions already made* and *Quality bar* are the
two sections that accumulate rather than drain; at the next milestone, move them to a
`DECISIONS.md` and leave this file as pure queue.

## References

- [CHANGELOG.md](CHANGELOG.md) — what shipped, per version, with the reasoning
- [RELEASING.md](RELEASING.md) — the release procedure and its traps
- [README.md](README.md) — build, install on your own phone, local toolchain
