# Decisions

Settled choices for Workout, kept out of [ROADMAP.md](ROADMAP.md) so that file can
stay a queue. Nothing here is a task: each entry is a decision already taken, written
down so it is not relitigated by accident.

A decision that constrains **unshipped** work stays with that work in the roadmap —
N15's `assistanceGrams` and N16's living-template choice live there, not here — and moves
into this file once the feature ships.

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
  one missing piece, and it is an open question in the roadmap.

## Templates and plans

- **A plan's sets are targets, and nothing verifies them** (N14). The set the user logs
  is a separate row in `set_entries` and is expected to differ; the plan describes the
  shape of a session rather than predicting it. Every target is nullable, because "work
  up to a heavy single" has no weight to write down and a zero would be a claim the app
  cannot check.
- **One role vocabulary for planned and performed sets.** `SetType` gained `TOP_SET`
  rather than a parallel enum for plans, so a plan that says "top set" and a log that
  cannot would not be two names for one idea. Enums are stored by name, so adding it
  touched no row already on disk.
- **A template is living, and a session reads it at the start.** Nothing links a session
  to the plan it came from beyond the route that started it: editing a plan changes what
  the next workout prefills, which is what N16 relies on. Writing the targets onto the
  session instead would freeze them and make "living" false.
- **The plan's rest and cue win over the library's, and null means "the library's"**
  (N14, extending N5). They are copied onto the session exercise when it is seeded from
  a plan, so a workout started from a plan that says "3m break" counts 3m.

- **Assistance is a magnitude in its own column, never a signed weight** (N15).
  `weightGrams` stays non-negative and volume stays `weight * reps`, so an assisted set
  contributes nothing rather than subtracting — a sign would have quietly corrupted
  every volume trend. The editor shows the load as one signed number (`-20`), and the
  steppers step *that* number, so pressing + on an assisted set reduces the help; the
  weight column itself still cannot go below zero.
- **`Load` stays a two-property `data class`; it cannot be a `value class`.** A
  `@JvmInline value class` wraps exactly one property, so the only way to inline `Load`
  is to store the load as one signed number — which is precisely the signed weight the
  entry above rejects, because it would make an assisted set subtract from volume. The
  inlining would also buy nothing at the call sites that exist: `parseLoad` returns
  `Load?` and the editor holds it in a nullable field, so the value is boxed anyway, and
  the project's own rule is that a performance claim comes with a measurement. Recorded
  so the suggestion is not re-litigated without new information.

- **RPE is half-points in an `Int`, and the feel and pain ratings are not** (N6,
  extended for 9.5). `19` is 9.5, exactly, for the reason weights are grams: no binary
  drift, and a trend can average it without the last digit wandering. It refuses
  anything finer than a half rather than rounding, because a rounded 9.3 would be a
  claim about the set that was never made. The column is named `rpeHalves` so its unit
  is visible at the schema, and an older backup's whole-number `rpe` is still read as
  halves rather than dropped.

- **A scheduled plan is a living template, not a dated instance** (N16). There is one
  Friday plan: editing its sets changes every future Friday until it is edited again.
  What was *performed* is the record and is already kept, so dated instances would add a
  plan-per-date entity, plan generation and skipped-week handling to support a
  comparison the logged sets already allow. Several plans may share a day, and a plan
  with no day is simply one you start by hand.

- **One-tap "Log set" writes the set its button describes** (D3, B7). The button may
  display a planned assistance, so the set it records carries it; the alternative —
  showing less so that display and storage agree — makes the app withhold something it
  knows. A logged set is still expected to differ from the plan, and the user adjusts it
  afterwards; what it must never be is *different from what the button said*.
- **"No dead weight" is strict about APIs that exist to be tested, and lenient about
  tests' instruments** (D2). `Weight.step`, `DataResult.map` and `successUnit` had no
  production caller and no test that used them as anything but their own subject: they
  are gone, and the tests that existed only to exercise them went with them. A DAO or
  store method that a test calls to *arrange or read* the subject under test is a
  caller — `ExerciseDao.insertAll`, `softDelete`, `CrashLogStore.latest` stay, because
  deleting them would mean testing through a different door than the app uses. The line
  is what the API is for, not where it is called from.

- **A per-exercise trend plots the number that moves, and says which way is forward**
  (N17). Load series come from *working* sets only: a warm-up must not become the
  "heaviest set" on a chart, which is why N14's roles had to exist first. A set with no
  added weight is not a load at all — bodyweight and assisted work carry reps and volume
  (both zero) exactly as N15 decided they carry. For an assisted exercise the series is
  the *least* assistance of the session, labelled "less is more", because on a machine a
  climbing line means the machine is doing more of the work. A one-rep-max estimate is
  Epley's, taken from the heaviest working set, refused beyond twelve reps (where it
  extrapolates rather than calculates) and rounded to the nearest half-kilo, because an
  estimate is not precise to the gram.

- **The JVM tests are not forked across JVMs** (D1). Measured on the 4-core CI runner,
  same branch, same tasks, only `maxParallelForks` differing: **424 s without forking,
  467 s with four forks** — slower, and summing roughly six times the CPU. Measured
  locally the same way: 63 s vs 65 s. Most of the task is compilation and Robolectric's
  resource merging, which forking cannot overlap; there is no wall-clock win to buy. The
  trigger to revisit is a runner that measures faster, not a feeling that it should.

- **A test tag arrives with the test that asserts on it** (D4). Twenty-five tags are
  applied in production today and asserted by nothing, which is drift in the one namespace
  that exists to stop tests reading English. They are kept rather than deleted in a sweep,
  because each marks a real control and the change that next touches it pays it off by
  writing the test; the rule that stops the list growing is one-way, so a new tag without
  its test is a finding. Tags that map to no control at all are still deleted on sight.

- **The role for the next set is armed at the button, and clears itself** (N19). The
  alternative — a pending role per exercise in the screen's state — was built first and
  then removed: it made transient UI state part of a database-driven flow, needed a
  `combine` input and a field on every row, and pushed `ActiveWorkoutViewModel` past the
  function ceiling detekt enforces (which the config says means a split, not another +1).
  Holding it in the composable that draws the button is smaller, survives the state
  rebuilds, and puts the "one set per role choice" rule where the choice is made. The
  ViewModel takes the role as an argument to `onLogSet`, so nothing there has to remember
  to clear it.

- **A plan is compared against the work, not the warm-ups** (N20). Warm-up sets are
  excluded from both sides of a comparison, for the reason N17 excluded them from a load
  series: a warm-up is not what the plan prescribes, and letting one stand in for the top
  set would flatter every review. A plan that names no weight leaves the delta *null* rather
  than zero, because zero claims the lifter matched a plan that never said.
- **The review is a moment, not a screen** (N20). It is built from state the workout screen
  already holds, so it costs no database reads and needs no schema — the session does not
  record which template it came from, and adding that would be a migration for a sentence.
  The cost is real and accepted: the review is not revisitable from history. If that becomes
  wanted, the honest fix is to store the plan with the session, not to re-derive it.

- **Settings live in `SharedPreferences`, not DataStore** (N21). What is stored is a handful
  of integers and booleans owned by one process, which is the case `SharedPreferences` is
  still the right tool for — and DataStore would be a new dependency for it. The move is
  warranted the moment settings need a collection, a schema or a migration; until then this
  is one file, one key and no ceremony. Writes are **committed**, not applied, because the
  screen reports a real result: a fire-and-forget write would let it say "saved" about
  something that never reached disk. **A consequence worth naming:** settings are therefore
  not in the export file, so restoring onto a fresh install returns the default rest to 90 s.
  That is a decision — a device preference is not training history — rather than the omission
  it would otherwise look like.
- **The default rest is a bounded choice, not a number field** (N21). The value becomes an
  alarm, so a typed zero would fire instantly and a typed negative would not be a setting at
  all; the repository refuses anything outside 5–3600 seconds as `DataError.Invalid`.

- **Progression is double progression, and it only ever suggests** (N22). Keep the load and
  add a rep until the plan's rep ceiling is reached, then add the smallest loadable step
  (2.5 kg, a pair of 1.25s) and start the range again. The alternatives were rejected
  deliberately: a percentage-based rule needs a true one-rep max this app estimates rather
  than measures, and a linear weekly add ignores missed sessions. Two consequences worth
  stating: **assisted work inverts the direction** — the machine doing less is the progress,
  so the step comes off the assistance — and with **no plan there is no ceiling**, so the app
  proposes one more rep and stops there, because adding weight without a target would be the
  app programming rather than the lifter.
- **A suggestion carries its reason, and null means "nothing to explain"** (N22). The number
  reaches the screen as a value *with* the rule that produced it, because a number the app
  chose is an instruction unless it says why — but only the three progression reasons draw a
  line. A prefilled set that is just the plan, or just a repeat of the set logged moments ago,
  has nothing to explain, and a line there would train the user to ignore the line that
  matters.
- **The app suggests; it never writes.** Nothing here changes a plan or a stored set. A
  suggestion the app applied silently would be a programme decision taken without the person
  training, and this app is a log, not a coach.
- **Warm-ups are excluded from progression too.** The third feature in a row to make that
  choice (N17, N20, N22) and for the same reason: a warm-up is not the work a target is
  measured against, and letting one set the next target would ask for a step on a bar that
  was only ever being warmed up with.

- **A superset is a group of exercises performed in rounds** (N24). The model is a nullable `supersetGroup: Int?` ordinal on `session_exercises` and
  `template_exercises` — the same integer meaning "these are done together" in a session and
  in the plan that seeds it. A separate `superset_groups` table was rejected: it needs its own
  ordering, a join on every read, and it expresses nothing an ordinal on the rows does not,
  while the ordinal survives reordering because `position` already carries the workout order.
  One concept covers circuits too — a circuit is a group with three or more members — and a
  group with one member is not a group, so nothing is labelled.
- **Round semantics: the rest belongs to the round, not the set** (N24). After a set in a
  grouped exercise the app moves to the next member with no rest, and starts the rest only
  after the **last** member — otherwise the point of pairing (no rest between the pair) is
  defeated by the app that is supposed to support it. The rest is the group's longest member,
  or the app default (N21) when none prescribes one. Labels are giant-set notation: A1, A2,
  A3 by group letter and member index.
- **N24 is a schema change, and is done by the book**: a migration 15→16 adding the two
  nullable columns, an exported `16.json`, a `MigrationTestHelper` case that seeds a real
  superset, and registration in `ALL_MIGRATIONS` — the columns land with the code that reads
  them, never ahead of it. Worth stating because it is the first *structural* change since the
  features that shipped after N14: N17's series, N20's plan-versus-actual and N22's progression
  are all per exercise, so none of them has to be revisited when a workout stops being a flat
  list.

- **A new column is added to the backup codec in the same change, and the codec is guarded by
  a round trip** (N24's preparation). The codec is hand-written and lists every field by name,
  so a column it does not know about is not an error — the export simply does not contain it,
  and the loss is invisible until someone restores a backup that is quietly missing data. It
  has happened three times (a set's location N9, a set's assistance N15, a template's weekday
  N16), which makes it a trap rather than bad luck: `BackupCodecRoundTripTest` now asserts that
  every field of every backed-up entity survives entity → DTO → entity, so the next column
  fails the suite where it is introduced. The one field deliberately excluded is a session's
  `restEndsAt`, because a rest countdown is device-and-moment state rather than training
  history — and that line now says so, since the guard could not tell it apart from a mistake.

- **A migration is amended only while its version has never shipped — and the cost is real**
  (B16). `MIGRATION_15_16` gained a second column for the plan side (`template_exercises.supersetGroup`)
  after N24 had already run the first version on development devices. That is legal only because
  nothing has ever been released with schema 16, and it is not free: **Room refuses to open a
  database whose stored identity hash does not match**, so any device that ran the earlier 15→16
  fails with *"Room cannot verify the data integrity… you have changed schema but forgot to update
  the version number"* until its app data is cleared. Confirmed on the emulator rather than
  assumed. The rule that follows: amending is for a version no user has, and a device that already
  ran it must be wiped — the alternative, a 16→17 migration, is correct but adds a version step to
  prove for data that only exists on developer machines.

- **The instrumented job keeps getting cancelled, and that is accepted** (B25). Across the last
  sixty runs, forty were cancelled against eighteen successes: `cancel-in-progress` meets a fast
  push cadence, and the emulator job needs 15–30 minutes while the build job needs about eight. The
  tempting fix — running the instrumented job only on `main` — was rejected because it trades a
  *visible* gap for an invisible one: cancelled runs are obviously missing, whereas a job that never
  starts on a branch reads like coverage that was never needed. What makes accepting safe is the
  guard from B8: a truncated run fails the job rather than passing quietly, which is why the one
  genuine failure in that window was the guard firing. **The trigger to revisit**: a real failure
  that a cancellation hid — that is, a red run noticed later than it should have been — at which
  point the jobs split by branch rather than by trust.

- **The rest cue stays inside the permission-free envelope** (N27). Removing the background alert left
  the app declaring nothing, and the obvious way to make a rest audible and felt would spend that:
  `Vibrator` needs `android.permission.VIBRATE`. So the cue is **view-level haptics**
  (`performHapticFeedback`, no permission) plus a tone played in-process, and keep-screen-on is a
  window flag rather than a wake lock. Stated here because the constraint is invisible from the
  feature's description: "sound and haptics" reads like a platform call, and the platform call that
  does it costs the property N26 was for. If a stronger cue is ever wanted, the trade is a permission
  and it should be taken deliberately rather than as a side effect.

- **A removed feature still cleans up after itself** (B37, B40). The rest alert is gone and its
  notification channel is not: Android keeps one across updates until uninstall, so a device that ran
  a pre-N26 build still lists "Rest timer" in its notification settings. Deleting it costs one
  idempotent call that needs **no permission**, so the app does it on every launch and the channel
  goes. The alternative — documenting it as accepted — was rejected because it leaves a trace of a
  deleted feature on a user's device to save four lines, and because "we removed it" should mean the
  device looks like it too. The channel id survives in `AndroidApp` for exactly one purpose, and its
  comment says so.

- **A measurement is one entry per day, edited rather than added to** (N32). The roadmap left this to
  implementation and named the two candidates. Several per day was rejected because it makes the chart
  noisy and, worse, makes "what did I weigh today" a question with more than one answer — and the second
  reading of a day is nearly always a correction of the first rather than a second measurement. So the
  day is the key: saving onto a day that already has an entry edits it. **The day is the local day it was
  taken**, converted from the timestamp at the edge, which is the same rule N25 established for a
  session's time — a measurement belongs to the day it happened where it happened.
- **An unmeasured tape site stays blank** (N32). It does not carry the previous value forward. A carried
  number is indistinguishable from a measurement and would draw a flat line through a site nobody
  measured that day, which is an invented fact rather than a missing one.

- **One tap logs what happened; the app's idea of what should happen is an offer** (N33). The suggestion
  and the prefill are different things and are now different fields: the prefill is the plan's target, what
  you just did, or last time unchanged, while the progression proposal is shown with its reason and applied
  only when accepted. They were one value before, which is what made a suggestion into a decision — a
  proposal that *is* the prefill is committed by the next tap whether or not anyone agreed to it. The rule
  is global, not program-only: how a workout was started says nothing about whether its lifter progresses by
  hand.

- **CI runs nightly and before a release, not on every push** (N30). The emulator is the one piece of
  infrastructure in this project that has failed without a test running, so a per-push run would mostly
  report on the runner, and a red pipeline that says nothing about the change trains people to ignore it.
  The per-change guard is the local gate set in AGENTS.md — the same tasks the build job runs — and the
  nightly run is what catches the drift a local run cannot see. A release dispatches the pipeline first
  (RELEASING.md step 5), because a release is the wrong time to discover the gate set has stopped working.
  The concurrency group carries the event name, so a release dispatch and the nightly run cannot cancel
  each other: that is not hypothetical, a push once cancelled an instrumented run twenty minutes in and the
  cancelled job's summary was indistinguishable from an infrastructure failure.

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
- **New and touched tests assert with Truth, and assert Flow sequences with Turbine.**
  JUnit's `assertEquals(expected, actual)` puts two bare values side by side with nothing
  saying which was which, and the arguments are easy to swap; `assertThat(actual)
  .isEqualTo(expected)` cannot be written the wrong way round. Turbine is for what a
  polled `.value` cannot express at all: a *sequence* of emissions, or an invariant that
  has to hold across one. Both are JVM-only (`testImplementation`), and the core Truth
  artifact rather than `truth-android`, which would pull androidx.test stubs alongside
  Robolectric's. Adopted after the review, so most files still use JUnit: they migrate
  **as they are touched**, never in a sweep, and a file is not left half-converted. This
  is a preference about failure messages, not a correctness gate — do not spend a change
  on migration alone.
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
