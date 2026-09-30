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
| **Play Store listing** | A feature graphic (1024×500) and phone screenshots — the 512 px icon already exists and is generated from the app's own vector by [`tools/MakeStoreIcon.java`](tools/MakeStoreIcon.java). Then the console: Data safety, content rating, privacy-policy URL. Plus a real call on **Play App Signing**, which changes who holds the app signing key, while this project's release process assumes a permanent local one. | You want distribution beyond `adb install`. Self-install works today. |
| **A crash-logs screen** | A small screen over [`CrashLogStore`](app/src/main/java/com/example/androidapp/platform/CrashLogStore.kt). | Reading a crash through an export actually annoys you. |
| **Zone offset on sessions** | A `zoneOffset` column captured at session start, plus a migration. Timestamps are UTC epoch millis today, so "which day was this" is answered in the *current* zone and drifts when you travel. | You train in a second timezone, or a feature needs local-day truth. |
| **Encryption at rest / app lock** | A key-management story, not just a library: where the key lives, and what happens when the phone is lost. | You start carrying the phone somewhere you would not carry the data. |
| **The rest alert: keep or remove** | Removing the alarm and notification path deletes both manifest permissions and the whole `platform/` alert code. The in-app timer, plus sound/haptics and keep-screen-on, cover the same need. | You never use the background alert, or you want the permission surface to be zero. |

## Next

Assigned by the review of 2026-09-30. Nothing here is shipped. The review found three
defects in what did ship, so those lead: the ordering below is what is wrong, then what
is missing, then what is merely untidy, then the next feature. Shipped rows leave for
[CHANGELOG.md](CHANGELOG.md) per the rules at the bottom; the choices this batch depends
on are in [Decisions waiting](#decisions-waiting), and a row that cannot start until one
is answered says so.

### What the app gets wrong today

*(Nothing outstanding — B5 through B7 shipped.)*

### What is merely untidy

- **B13 — Delete what moved and left its shape behind.**
  [`ExerciseLibraryViewModel`](app/src/main/java/com/example/androidapp/ui/exercises/ExerciseLibraryViewModel.kt)
  still carries the whole "workout in progress / resume clock" apparatus — a state
  field, two flows, a ticker, and the `TimeSource` and `WorkoutRepository` dependencies
  that exist only to feed it — but the screen stopped reading it when the resume button
  moved to [`WorkoutsHomeScreen`](app/src/main/java/com/example/androidapp/ui/home/WorkoutsHomeScreen.kt).
  The only reader left is a test asserting the dead state is null. Alongside it:
  [`@ApplicationScope` and `CoroutineModule`](app/src/main/java/com/example/androidapp/di/CoroutineModule.kt)
  bind a scope nothing injects; [`TemplateDao.findTemplateSets`](app/src/main/java/com/example/androidapp/data/local/TemplateDao.kt),
  `WorkoutSession.isActive`, `WorkoutSummary.hasVolume` and `PreviousPerformance.isEmpty`
  have no caller at all. **Do:** delete the cluster and the test that keeps it alive,
  then the loose declarations. This is the rule the project already states — *delete an
  API the moment nothing calls it* — being enforced on its own code. **Decide first:**
  see **D2**, which decides where the line falls for the test-only APIs in the same
  family.

### Then — the next feature

- **N17 — Trends for one exercise.** N13 reads the app's signals back over time, but only
  across the whole app: RPE, muscle feel and joint pain over the last ten rated workouts.
  The question a lifter actually asks is narrower — *how is my bench press going?* — and
  nothing answers it.

  **Entry:** the exercise detail screen, and a lift tapped from a past workout.

  **Series**, over finished sessions, oldest first: heaviest working set, estimated 1RM
  (Epley/Brzycki), volume, and total reps — plus this exercise's own average RPE, muscle
  feel and joint pain, which the app has been recording since N6 and N8 without ever
  showing them per lift.

  **Reuse, not new machinery:** N13's hand-drawn chart and the `TrendsRepository` shape,
  narrowed by exercise. The charts stay dependency-free, and nothing new is stored —
  every series comes out of rows already written.

  **Warm-up sets are excluded from the load series.** That is only expressible now that
  N14's roles exist, and it matters: a warm-up must not become the "heaviest set" on the
  chart. Drop and failure sets are working sets, and count.

  **Assisted exercises need a direction, not just a line.** For a movement logged with
  assistance, more help is not progress, so the screen must say which way is forward
  rather than drawing a climb that reads as improvement. Decide with the screen in front
  of you: plot the assistance magnitude and label it "less is more", or keep assisted
  sets out of the load series and show reps and volume alone.

### Decisions waiting

Four choices the work above depends on. Each is a real trade, so each is recorded with
its options rather than settled here.

- **D1 — Should the JVM tests run in parallel?** The obvious CI speed-up is to fork
  them across JVMs, and it is **not** obviously right: measured on this checkout,
  `maxParallelForks = 4` was *slower* wall-clock (65 s vs 63 s) while summing six times
  the CPU, because only ~20 s of the task is test execution and the rest is compilation
  and Robolectric's resource merging.
  *Take it* if a 4-core runner measures faster; *leave it* otherwise. Either way the
  choice is made from a measurement on the runner, not from this file — the project's
  own rule is that a performance claim comes with one.
- **D2 — Do "no caller in `main`, tests only" APIs count as dead?** `Weight.step`,
  `successUnit`, `DataResult.map`, `ExerciseDao.count/insertAll/softDelete` and
  `CrashLogStore.latest/clear` are called from tests and nowhere else — and `Weight.step`
  is the clear case, since production uses `stepLoad` and the test file says out loud
  that it "had no callers".
  *Strict* — the rule says delete; tests should exercise what ships, not hold up what
  does not. *Lenient* — a test is a caller, and the DAO and store methods are the only
  way to arrange the rows the *real* tests then read. Left open deliberately: it changes
  what "no dead weight" means for every future change, which belongs in
  [DECISIONS.md](DECISIONS.md) rather than being decided by a cleanup.
- **D3 — What should one-tap "Log set" do with a plan's assistance?** B7's fix is not
  mechanical: a plan may prescribe help, and the button may display it, but the set the
  user actually performs is the one they adjusted.
  *Write the suggestion* — the button does what it says; the row records the plan's help
  unless it was edited. *Drop the display* — keep the write as it is and stop showing
  assistance on the button, so the two agree by making the button say less.
  The wrong answer is the current one: displaying a number that is not what gets stored.
- **D4 — The test tags applied in production that no test asserts on.** Each is
  referenced by nothing — either the tests they were added for were never written, or the
  tag was added ahead of its test.
  *Keep and write the tests* (they mark real controls: plan dialogs, set steppers, the
  exercise editor). *Delete them* if those tests are not coming. A tag that exists only
  to be ignored is drift in the one namespace meant to stop tests asserting on English.

### Rule violations found, not new work

Presented as findings rather than rows, because each is an existing rule not being
followed. They belong to the change that next touches those files, and F8 — *extract a
component at its second caller, not its first* — is the rule most of them break: three
`CenteredMessage` composables exist while the shared one documents that it exists to
prevent them; two near-identical `SetType` pickers; two identical `ActiveWorkoutInfo`
types; two `DayOfWeek` formatters; `HALVES_PER_POINT` defined twice. Name-content
mismatches sit with them: `ErrorText.kt` declares no `ErrorText`, `ExercisePickerScreen.kt`
declares only a route, `NoteDialog.kt` holds three dialogs, and `RestAlarmReceiver` lives
in `RestAlarmScheduler.kt`.

Two stale statements are worth correcting rather than queueing, being one line each:
the comment on `Rpe.HALF_STEP` describes it as a whole 1–10 rating when it is RPE's own
half-step parser (the behaviour is right — the comment is not), and the Robolectric
comment in [`libs.versions.toml`](gradle/libs.versions.toml) still says 4.15.1 is the
newest published while the catalog declares 4.17.

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
- **P1.20** Choose a set's role as it is logged. The one-tap **Log set** writes a working
  set, so a warm-up is currently log-then-edit; the role picker exists, but only behind
  the editor.

**Insight** — why the app gets opened between workouts
- **P2.1** Per-exercise history.
- **P2.2** Personal records and estimated 1RM.
- **P2.3** Charts and trends — N13 settled the approach for the first three series
  (hand-drawn on a `Canvas`, no dependency). A fuller chart screen, per-exercise history
  and PRs over months are still this row, and the approach can be revisited.
- **P2.8** Muscle-group balance warnings.
- **P2.4** Body measurements.
- **P2.5** Progress photos, in encrypted local storage.

**Programming** — turns a logger into a plan
- **P3.1 + P3.2** shipped their v1 as **N3** (templates: a name, exercises, order), and
  their targets, per-plan rest and weekday schedule are **N14–N16**, which shipped in
  v1.4. What remains here is **P3.6** supersets and circuits; drop sets are a set role in
  N14, and giant-set notation is deliberately not modelled.
- **P3.4** Auto-progression suggestions — the strongest differentiator once there is
  enough history to base them on.
- **P3.3** Programs / mesocycles with scheduled deloads.
- **P3.5** The weekly schedule ships as **N16**; what remains here is planned-vs-completed
  adherence over a longer window, and a calendar view.

**Small and self-contained**
- **P2.6** Plate calculator.
- **P2.7** Warm-up set generator.

**Quality follow-through**
- **P1.17** Accessibility audit — a TalkBack pass over every screen, dynamic type at
  200%, and a contrast check. The per-screen rule is in [DECISIONS.md](DECISIONS.md);
  this is the sweep that finds what the rule missed.

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
