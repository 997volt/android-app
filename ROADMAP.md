# Workout Tracker — Roadmap

> **v1.4** is shipped and installed. Last reviewed against the code: 2026-09-30.
>
> This file is forward-looking only. What shipped lives in
> [CHANGELOG.md](CHANGELOG.md); how a release is cut lives in
> [RELEASING.md](RELEASING.md); settled decisions and the rules that apply to every
> change live in [DECISIONS.md](DECISIONS.md).

**What this app is.** A local-only workout logger: start a workout, log sets, see
your history, keep your data.

**The scope rule.** If it does not help log a set faster or make the stored history
more trustworthy, it does not belong here. Anything that ships data off the device,
or needs an account or a server, is out by default.

Feature ids (`F#` foundations, `B#` defects, `N#` the next planned changes,
`P#.#` the product backlog, `R#.#` releases) are stable and are referenced from
commit messages. They were assigned when the work was planned, so they do not run in
order — the `P4`/`P5` rows are simply the ones parked furthest out, and `N1`–`N13` have
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
- **Templates became plans, and shipped**: the v1 half of P3.1 is N3, and its planned
  sets, per-plan rest and weekday schedule are N14–N16. What remains of the routine
  scope is in *Later*.

Run `./gradlew testDebugUnitTest` and `./gradlew connectedDebugAndroidTest` for the
current numbers; dependencies are declared in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml). Neither is repeated here on
purpose — see [Keeping this true](#keeping-this-true).

## Open questions

Not tasks: choices with a real cost either way. Each names a trigger, so it can be left
alone without being forgotten. Settled choices are the other document — see
[DECISIONS.md](DECISIONS.md).

| Decision | What it would take | Revisit when |
| --- | --- | --- |
| **A crash-logs screen** | A small screen over [`CrashLogStore`](app/src/main/java/com/example/androidapp/platform/CrashLogStore.kt). | Reading a crash through an export actually annoys you. |

Four of this table's former rows are settled rather than open. Two became work — **N25**,
a session remembering its timezone, and **N26**, removing the background rest alert. Two
are parked with their triggers: the **Play Store listing**, and **Encryption at rest / app
lock**. What is left above is the one thing genuinely still undecided.

## Next

Tier 1 (N19–N21) and Tier 2 (N22–N24) shipped in v1.5 and live in
[CHANGELOG.md](CHANGELOG.md). Two reviews of that release lead as a batch of twelve, because two of
them lose data or contradict a settled decision; behind it are the two decided questions, and then
what is left of Tier 3.

### The v1.5 review — B14–B25

Two reviews of v1.5 — one structural, one defect-focused — turned up more than the release note
admits. Ordered by what it costs the user. **B14 and B15 are fixed** and live in
[CHANGELOG.md](CHANGELOG.md): they were the two that lost data or contradicted a settled
decision. Several of what remain are a contract that is documented and not implemented, and the
rest are gaps.

- **B16 — N24 shipped one column where its own decision says two, and the second is missing.**
  The decision reads "a migration 15→16 adding the two nullable columns… the model is a
  `supersetGroup` ordinal on `session_exercises` **and** `template_exercises` — in the plan that
  seeds it". Only `session_exercises` got it: `template_exercises` has no such column in
  `16.json` and no field on the entity, and nothing in the template DAOs references it. So **a
  plan cannot express a superset**, and a workout started from a plan cannot carry one — the
  feature works only when grouping is done by hand inside a session. This is the largest of the
  three N24 divergences, and the one that makes the feature unavailable at the point the app
  most wants it (plans are the thing you start from).
- **B17 — A warm-up can still raise a personal record.** `isRecord` takes no role
  (`PersonalRecords.kt:26`), and the record decision at `ActiveWorkoutViewModel.kt:535` uses
  `against`, which excludes warm-ups already in the session and history but never the set being
  logged. Arm `WARMUP`, log above the best at that rep count, and the banner fires — exactly what
  `PersonalRecords.kt:48-50` and the changelog say can no longer happen. Every test on this path
  uses the default `NORMAL` role, so none can see it.
- **B18 — "The first time at this rep count" is false when the bar was set this session.**
  The record test uses `against` (`records.mergedWith(PersonalRecords.from(row.sets))`) but the
  banner's `previousBestGrams` reads `records?.bestAt(...)` — history only
  (`ActiveWorkoutViewModel.kt:540`). Lift 20 kg then 22.5 kg at 8 reps and the second banner
  says "the first time at this rep count" while claiming a record over that very 20 kg. The
  documented meaning is "what it beat", so it should read from `against`.
- **B19 — A plan with no rep ceiling renders "prescribed 2×0".** `prescribedReps` applies
  `takeIf` to the wrong expression (`PlanComparison.kt:105`):
  `plannedWork.mapNotNull { it.maxReps }.sum().takeIf { plannedWork.isNotEmpty() }` — the guard
  tests the *input* list, so working sets with no `maxReps` yield an empty map, a sum of 0, and a
  truthy guard. It violates the file's own "every nullable field is honestly nullable" contract
  and puts a zero on screen. Fix the guard to test the mapped list.
- **B20 — The review keys exercises by display name, so a repeated exercise collapses.** Both
  the performed map (`PlanComparison.kt:74`, `associateBy { it.name }`, last wins) and the
  improvised filter (`:80`) use the name, while nothing prevents the same exercise twice in a
  session. So two rows sharing a name lose the earlier one's sets from the review — while the
  totals at `ActiveWorkoutViewModel.kt:963` still count them, making the summary contradict
  itself. Matching should use `exerciseId`; the comparison types carry only `name` today.
- **B21 — The review's counts include warm-ups where its reps exclude them.** `prescribedSets`
  and `performedSets` are the unfiltered lists while the rep sums drop warm-ups
  (`PlanComparison.kt:92-104`), so "prescribed 2×3" means two sets, one of them a warm-up, with
  three total reps. Either both should exclude warm-ups or both should include them, and the
  dialog should say which.
- **B22 — The route-registration invariant is still unguarded.** A route with no
  `composable<...>` compiles and crashes at navigation. That is how the Settings bug shipped, and
  it was *both* a missing `@Serializable` **and** a missing registration — only the first got a
  test. `RoutesTest` checks the annotation over a hand-written list of names, so a new route
  added without touching the test cannot fail it, and nothing anywhere builds the graph. All
  twelve are registered today (checked), which is why this is cheap to pin now. **Do:** fail on a
  route type that has no registration, with no hand-maintained list — a `TestNavHost` navigating
  each destination catches the crash the bug produced, which no serializer assertion can. While
  there: `composable<Settings>` sits in `historyDestinations`, whose KDoc reads "finished
  workouts and one workout's detail".

### Also from the same review — gaps rather than wrong behaviour

- **B23 — The settings feature has no tests at all.** N21 shipped `SettingsScreen`,
  `SettingsViewModel` and `PreferencesSettingsRepository`, and no test in either source set
  references any of them — so the stored-vs-tapped rule its own KDoc states, the
  `onSetDefaultRest` failure branch, and the `SharedPreferences` round trip are all unverified.
- **B24 — Three new entry points never meet SQLite, and the migration has no seeded test.**
  `personalRecords` and `setSupersetGroup` are exercised only through hand-written fakes, and
  `app/src/androidTest` was not touched in this batch at all. `MIGRATION_15_16` is registered,
  matches its exported schema, and is covered end-to-end by `MigrationsTest`'s 1→16 chain — but
  it has no `MigrationTestHelper` test while its fourteen siblings each have one, and what those
  add is the thing the chain cannot check: that an upgrade **keeps the rows already in the
  table**. Smaller gaps in the same batch: the `SetSuggestion` branch for a plan that names reps
  and no load, and N24's A1/A2 label and superset toggle.
- **B25 — The instrumented job rarely finishes during a busy stretch.** `cancel-in-progress`
  meets a fast push cadence: across the last 60 runs, **40 were cancelled** against 18 successes,
  and the emulator job needs 15–30 minutes while the build job needs about 8. The fast job
  validates every commit and the suite covering DAOs, migrations and the backup round trip
  completes only when pushes are spaced out. **Not a code defect, and the fix is a workflow
  decision rather than a patch:** either accept it — the guard reports a truncated run rather
  than passing it, which is why the one genuine failure in the window was that guard firing — or
  stop cancelling the run whose job is the slow one, for instance by letting the instrumented job
  run only on `main`.

### Settled by the review rather than queued

- **Every backed-up column is carried, and the one exception is deliberate.** A machine
  comparison of `16.json` against the DTOs found no column missing from its DTO, and the mappers
  assign every column both ways. The single non-round-tripped column is
  `workout_sessions.restEndsAt`, forced to null on import because a rest countdown is
  device-and-moment state rather than training history — documented in the mapper and asserted
  explicitly by the round-trip test. Worth recording because it is the only one, and because it
  looks exactly like the three accidental drops that guard exists to catch.
- **The N21 default rest is not in the backup file.** It lives in `SharedPreferences`, so a
  restore onto a fresh install returns it to 90 s. Probably right — it is a device preference,
  not training history — but it is now a decision rather than an omission.

### Suspicions and smaller findings

Not rows, because none is proven or is a task on its own. The first is a rule that may be
implemented against its own statement; the rest are existing rules not being followed. They ride
with whichever change next touches the file.

- **The assisted-progression branch ignores the rep ceiling.** The rule everywhere it is written
  is rep-first, then the smallest loadable step; as implemented
  (`ProgressionSuggestion.kt:80-85`) an assisted lifter at the bottom of a 6–8 range is told to
  repeat the same reps with less help, which never adds the rep the rule asks for first. The one
  test sits exactly at the ceiling, so it cannot tell the two rules apart. Either the branch
  needs the ceiling test or the decision needs to say the direction inverts for assistance.
- **Declarations nothing calls.** `PreviousPerformance.at()` lost its last caller when
  `SetSuggestion` stopped being indexed; `SettingsUiState.isLoading` is written and never read,
  and the screen renders no loading state.
- **Unused imports survive the gate, which is the more interesting half.**
  `RoomTrendsRepository.kt` and `TrendsRepository.kt` carry imports added for an implementation
  that landed in a different file, and `./gradlew detekt` passes on them: detekt's
  `UnusedImports` is `active: false` in 1.23.8 and the compiler is not run with `-Werror`, so
  neither gate this project treats as authoritative can see an unused import. Either turn the
  rule on deliberately or record that imports are not gated.
- **A test that cannot fail.** `TemplateEditorScreenTest`'s weekday case asserts only that the
  chip exists, while `WeekdayPicker` composes every chip unconditionally — so it passes even if
  the plan's `weekday` is ignored. It wants `assertIsSelected()`. Worth watching for in any test
  written against a control that is always rendered.
- **Superset pairing is not transactional** (`ActiveWorkoutViewModel.kt:584`): one write per id,
  so a failure or process death between them leaves the half-a-group state the code's own comment
  says nobody asked for — and `handle` continues the loop after a failure rather than stopping.
- **The toggle on the first exercise is a no-op that rewrites every ungrouped row**
  (`:571-585`): with no previous exercise the group is null, and the filter then selects every
  ungrouped row and rewrites it, churning `updatedAt` for no change. The control is still drawn
  on row 0.
- **Two types share the name `WorkoutSummary`** — the history row in `domain/model`, and the N20
  review payload at the bottom of `ActiveWorkoutViewModel`.
- **`SettingsModule` lives in `DatabaseModule.kt`**, whose name says database while the module
  binds a `SharedPreferences` repository.

### N25 — A session remembers the timezone it was performed in

**Decided: a displayed time is always the time the session was performed in.** Timestamps
are UTC epoch millis today and every screen formats them in the *current* zone, so a
workout done in Tokyo reads as the wrong hour — and the wrong day — once you are home.

- **Store the offset on the session**, captured when it opens, and format with it wherever
  a session's time is shown: history, the workout detail, the trends window and the review.
- **A migration, and the backfill question it brings.** Rows written before this have no
  offset; falling back to the current zone is the only honest answer, and it is what they
  already get. Say so rather than inventing a timezone for the past.
- **The offset is captured once, when the session starts.** A session that spans a DST
  change keeps its start offset — a simplification worth stating rather than discovering.
- **The other half of the zone question is "which day was this"**, and it follows from the
  same column. Worth doing as one piece rather than two.

### N26 — Remove the background rest alert

**Decided: the alarm and notification path goes.** It exists to buzz you when rest ends
with the screen off, and it is the only reason this app requests *any* permission.

- **What goes:** the scheduler and its receiver, the notification builder, the
  ask-on-first-set permission flow, the `RestNotifier` port and its binding, the receiver
  declaration, and both manifest permissions — `POST_NOTIFICATIONS` and
  `SCHEDULE_EXACT_ALARM`.
- **What stays:** the rest timer. The end instant lives on the session row and survives
  process death; only the way it *reaches* you changes.
- **The property worth stating:** the app then declares **no permissions at all**, which is
  a stronger privacy statement than any wording, and belongs in the README.
- **Why this is not pure deletion:** with no background alert the timer has to be noticed
  in-app, so **P1.14** (sound and haptics) and **P1.10** (keep the screen on) stop being
  polish and become its replacement. They lead what follows.

### Then — what is left of Tier 3

- **P1.14** Rest sound and haptics, and **P1.10** keep the screen on — the two that carry
  the timer once N26 removes the alert.
- **P2.7** Warm-up set generator — it can *write* warm-up sets into a plan using the
  `WARMUP` role, which is what makes it better than it was.
- **P1.15** Repeat last workout in one tap.
- **P1.9** kg/lb units — only if you ever lift in pounds.

### Decisions waiting

**None.** D1 through D4 were settled by the work that needed them and moved into
[DECISIONS.md](DECISIONS.md), which is where a taken decision lives.

### Rule violations found, not new work

**Nothing outstanding.** Every finding from the earlier review is settled; how each was settled is
recorded in [CHANGELOG.md](CHANGELOG.md) under *Unreleased*, per the rule that finished work leaves
this file. The two stale statements this section used to carry — `Rpe.HALF_STEP`'s comment
describing itself as a whole 1–10 rating, and the Robolectric note claiming 4.15.1 was the newest
published — are both corrected in the code now. That last pair went in without a changelog line,
which is the smallest kind of drift and only worth a mention so the next reader does not go looking
for it there.

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

**Insight** — why the app gets opened between workouts
- **P2.1** Set-by-set history for one lift — N17 delivered the per-exercise chart and its
  entry point, so what remains is the full list of past performances, if that is wanted.
- **P2.3** A fuller chart screen over months — N13 settled the drawing approach (a `Canvas`,
  no dependency) and N17 used it for one lift; this is the longer horizon. Personal records
  are **N23** and are not this row.
- **P2.4** Body measurements.

**Programming** — turns a logger into a plan
- **P3.3** Programs / mesocycles with scheduled deloads.
- **P3.5** Planned-versus-completed adherence over a longer window, and a calendar view —
  the weekly schedule itself shipped as **N16**, and session-level plan-versus-actual is
  **N20**.

Templates shipped their v1 as **N3**, and their targets, per-plan rest and weekday schedule
as **N14–N16**. Auto-progression is **N22** and supersets are **N24**, both queued above.

**Small and self-contained**
- **P2.7** Warm-up set generator.

The **accessibility rule still applies to every screen as it is written**
([DECISIONS.md](DECISIONS.md)); the audit sweep that used to sit here is parked, so this
section is empty until it returns or something replaces it.

Design-system work (**F8**) is a rule rather than a row now: extract a component when
a second screen needs it, not before. *Next*'s "rule violations found" is that rule
being broken in five places, so it is enforcement of F8 rather than new work.

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
| P1.11 | Onboarding: goal, experience level, weekly target | This stops being a single-user local tool with one obvious user. It personalises defaults, and there are no defaults to personalise. |
| P1.17 | Accessibility audit | The per-screen rule stops being enough — a real complaint on a device, or a screen that grew past ad-hoc tagging. The rule itself still applies to every change; only the sweep is parked. |
| P2.5 | Progress photos | A visual record is actually wanted, and an encrypted-storage design for it is acceptable. |
| P2.6 | Plate calculator | You start loading plates from a plan and want the arithmetic done rather than done in your head. |
| P2.8 | Muscle-group balance warnings | Enough history exists for a rolling window to say something true rather than something plausible. |
| — | **Play Store listing** | You want distribution beyond `adb install`. Self-install works today, and Play App Signing would change who holds the signing key. |
| — | **Encryption at rest / app lock** | You start carrying the phone somewhere you would not carry the data. |
| F6 | Module split into `:core:*` / `:feature:*` | **A named goal, not a refactor**: a measured build-time problem, working on one feature without compiling the rest, or a second surface (Wear, a widget). Revisited after v1.2 and re-affirmed. |
| F11b | Product analytics | Almost certainly never: on a single-user local tool it buys nothing, and it would breach the no-`INTERNET` line. |

## Explicit non-goals

Permanent, unlike *Parked* above: nutrition / calorie tracking, social feeds, live GPS
route tracking, and a web dashboard. Each is a product in its own right and would
dilute the logging core.

## Keeping this true

Four rules. The drift they prevent has now happened four times — stale test counts, a
dependency inventory, an enumerated feature list that v1.3 quietly outgrew, and a
review stamp still reading v1.3 while *Next* said "Nothing" after v1.4 had shipped
with defects unfound. Two of those four were this file describing itself wrongly:

1. **Nothing marked done lives here.** Shipped work goes to
   [CHANGELOG.md](CHANGELOG.md), and a finished row is deleted from this file.
2. **No hand-maintained facts.** No test counts, no dependency lists, no inventory of
   which files exist. Those are commands (`./gradlew …`) or links
   ([libs.versions.toml](gradle/libs.versions.toml), [app/schemas](app/schemas)).
3. **Every parked row names its revisit trigger**, so parking reads as a decision
   rather than a forgotten item.
4. **Durable content lives in [DECISIONS.md](DECISIONS.md).** Settled decisions and the
   rules that apply to every change are a reference, not a queue, and the two age
   differently. A section here that accumulates rather than drains belongs there.

Bump the review stamp at the top whenever this file is checked against the code.

## References

- [DECISIONS.md](DECISIONS.md) — settled choices, and the rules that apply to every change
- [CHANGELOG.md](CHANGELOG.md) — what shipped, per version, with the reasoning
- [RELEASING.md](RELEASING.md) — the release procedure and its traps
- [README.md](README.md) — build, install on your own phone, local toolchain
