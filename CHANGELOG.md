# Changelog

Notable changes to Workout, newest first. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are the
`versionName` from [`version.properties`](version.properties), with the
`versionCode` in brackets because that is what Android actually compares.

Each entry says what shipped and why it mattered. The reasoning behind a decision — the
alternatives rejected, the measurements, the argument — lives in
[DECISIONS.md](DECISIONS.md) and [DECISIONS-EVIDENCE.md](DECISIONS-EVIDENCE.md), and is not
repeated here.

## [Unreleased]

## [1.8] — 2026-10-02 (versionCode 9)

### Added

- **An index on `workout_sessions.startedAt`, schema v19** (B46). The statistics range filter
  and the record count both scan by start time and neither could use the `finishedAt` index —
  one filters `IS NOT NULL`, the other wants a range on a different column — so the record query
  read every set ever logged. The migration test asserts the row survives *and* that the index
  exists, since a query cannot report a missing index.
- **A target for any metric, drawn on the chart.** Typed in the unit the screen shows and
  stored in canonical units, so the line is the number the readings are. **Dotted where the
  average is dashed**, because the user's own target is not the app's summary of them. It can
  be cleared, an unreadable number sets nothing rather than a zero, and **the axis widens to
  include the target** — a target you cannot see is not a target **(N39)**.
- **The readings, under the chart.** Every reading as a date and a value, newest first,
  collapsed until asked for, with **Average** and **Trend** rows above them. It is also the
  chart's accessible counterpart: a canvas is blanked for a screen reader, so for anyone who
  cannot see the line this list is the screen. A moment that recorded nothing is a gap rather
  than a zero, and the average is over readings that exist.
- **The chart's x-axis is time.** Two workouts a day apart and two a month apart no longer
  draw the same distance apart, and the axis is labelled in words. **The line still breaks at
  a missing reading**, which matters more here, not less: the gap is now visible as *distance*
  **(N37)**.
- **Bars for quantities, a line for a scale, zero where zero is.** Volume draws as bars;
  ratings, bodyweight and tape measurements stay lines, so a 1–10 rating is not flattened by
  an axis anchored at zero — which it is stays a property of the metric, not of the data.
- **An average, a fitted trend, and its slope in the metric's own unit per week.** Only the
  readings that exist are fitted, so a gap does not pull the line, and the sign is part of the
  text because the same number is progress on bodyweight and a warning on joint pain. *(A first
  attempt drew all three in a theme colour too close to the surface to see on a device; they are
  a neutral annotation now.)*
- **A trailing mean over a configurable period**, seven readings by default. The period counts
  **readings rather than days** (N40) — the same thing for a daily weigh-in, not for a lift
  trained twice a week — it emits from the first reading rather than waiting for a full period,
  and an unmeasured day neither counts nor drags the mean down.
- **A Statistics tab, and one picker over every series.** It answers "how is everything going"
  with one chart, a range and three numbers, where there used to be a workout-trends screen and
  a separate per-lift one. Every series — the ratings, the eight exercise metrics, and each
  measurement site — is described in **one registry** carrying its label, unit, formatter,
  group, line-or-bars, axis-at-zero and better-direction (N35). The three enums stay; the
  registry references them. The two screens it replaced are gone, with their ViewModels and
  tests.
- **The range is remembered as what it means**, not as the dates it resolved to, so a saved
  "last 7 days" is still the last seven days tomorrow. A custom From–To asks for its dates
  before it applies, and reads them in the calendar's own zone.
- **The overview is three numbers**: workouts, volume and records. A record needed a query of
  its own, because a set is a record by a rule about everything performed *before* it and
  nothing stores that a set was one. Where the count has not been asked for the screen shows a
  dash rather than a zero — "you set none" and "not counted" are different statements.
- **Five tabs along the bottom** — Workouts · History · Statistics · Library · Settings — with
  the overflow menu reduced to the data actions and nothing else. Templates stayed under
  Workouts, because a plan is part of working out, and **body measurements are pushed from
  Statistics** rather than sitting in the menu.
- **Each tab keeps its own place**, so History is where you left it after a look at Statistics,
  and back from a tab root goes to Workouts rather than walking back through the tabs in the
  order you visited them. The bar disappears during a workout, a detail pushed inside a tab
  keeps that tab highlighted, and each item carries a label and a selected state so TalkBack
  announces a named, selected tab rather than an unlabelled square.
- **Body measurements**, on their own screen: a dated entry with a **weight** and *optionally*
  body fat, muscle and seven tape sites, every one nullable, because a waist taken on a morning
  the scale was not stepped on is a real entry. A zero would be indistinguishable from a
  measurement of nothing; weight is the only field an entry cannot be without, and the save says
  so before it is tapped.
- **A day has one entry, and saving onto it edits it** (N32). A second reading of a day is nearly
  always a correction of the first, so "what did I weigh today" has one answer. An unmeasured
  tape site stays **blank** rather than carrying the previous value forward, which would invent a
  measurement and draw a flat line through a site nobody measured.
- **Measurements are charted** through the same chart as the training trends — bodyweight, then
  body fat, muscle and each site — sharing the chart rather than the axis, since a measurement is
  not a workout, so each series is scaled to its own readings. They travel in the **backup file**
  with a round-trip guard test, and the schema is at **version 18** (the index above takes it to
  19); the upgrade keeps every row that was already there.
- **A finished workout can become a plan.** The app went plan to session and history to session,
  never session to plan, so after a good unplanned workout the only way to keep it was rebuilding
  it by hand. Workout detail now offers **Save as plan** — it asks for a name, copies the
  exercises in order with their performed sets as targets, keeps the rest, the technique note and
  the superset grouping, and offers to open the plan. It copies neither the readiness note, the
  ratings nor the workout comment, which describe that day rather than the plan; an exercise
  deleted from the library since is skipped while the rest copy.

### Changed

- **The instrumented job runs the emulator with VM acceleration** (N30). It had gone red on
  infrastructure four times with four different signatures, and the emulator's own probe named
  the cause every time: `/dev/kvm` is on the runner and the `kvm` group exists, but the runner
  user is not in it, so the emulator fell back to software emulation. A udev rule grants it and
  `-accel auto` takes it. *(The four signatures and the probe output are in
  DECISIONS-EVIDENCE.md.)*
- **CI: the emulator's flags are set explicitly, and the job runs nightly and before a release
  rather than on every push** (N30). Reading the pinned `android-emulator-runner`'s own `action.yml`
  settled it: the flags it was told to try *are* the action's defaults, so that experiment could not
  have been the fix. Setting them anyway means a future action bump cannot change how the emulator
  boots without the workflow saying so. The
  concurrency group now includes the event name, after a documentation push cancelled an
  instrumented run twenty minutes in and its summary read exactly like an infrastructure failure.
- **The instrumented suite runs green on the hosted runner**, which it never had. An **Automated
  Test Device** (`aosp_atd`, API 34) boots in three and a half minutes where the previous image
  took sixteen and spent the run offline. The count guard is satisfied — declared 175
  instrumented tests, executed 175 — and the image carries no Google services, which is this
  app's constraint anyway. The cost stands: the suite is tested against API 34 rather than the
  37 the app ships against.
- **Progression is offered rather than applied** (N33). N22 shipped a suggestion whose own
  documentation said it "suggests; it never writes", and in two places it *was* the value one tap
  logged — with no plan, last time plus a step, and with a plan naming reps but no load, the
  weight from the same proposal — so a lifter who progresses by hand had to undo the app's
  arithmetic on every first set. One tap now logs the plan's target where it names one, then what
  you just did, then **what you did last time unchanged**, then the default; the proposal sits
  beside it with its reason and a **Use it** action, applied only when accepted.

### Fixed

- **The fitted line is inside the axis.** A series rising fast ends above its own last reading,
  and the axis was built from the readings alone, so the line was clamped flat along the top edge
  exactly where it rose fastest. Its ends are now part of the axis, and the same holds for a
  fitted end below the readings.
- **A tape or body-fat field that is not a real number is no longer stored.** `Math.round` of a
  `NaN * 10` is `0`, so typing "NaN" wrote a real 0.0% reading — a measurement nobody took, in a
  series that then draws it — and "Infinity" became −0.1%; negatives were accepted too. The guard
  is the one `Weight.parseKilograms` has always used.
- **"All" counts records instead of showing a dash.** The tile was permanently empty on the one
  range where a lifetime count means most, because "all" has no window and the count bailed when
  the window was absent.
- **A long range no longer loses its older sessions.** The training series were fetched with a cap
  of 500 *sessions* and then filtered by date, so with more history than the cap a "last year"
  range silently omitted everything before the newest 500, including from the record count.
- **The Statistics screen can change the lift again.** The picker was drawn only while *no* lift
  was selected, and every entry point — the library, the workout detail, a lift just performed —
  arrives with one already chosen, so the lift could never be changed. Its selected-name branch
  was dead code, which is the tell that it was meant to be reachable.
- **Loading and failure no longer read as "0 workouts, 0 kg".** The overview and the chart were
  drawn from the default state before anything had been read, so a first frame — and any failed
  read, which had no message at all — made a claim about data the screen did not have.
- **The exported schema for version 18 is regenerated.** It listed fifteen entities for eight
  tables, every pre-measurements table twice, byte for byte. Nothing consumed the duplicates, but
  `app/schemas` is the baseline the migration tests validate against, and a baseline that is wrong
  about its own contents is not a baseline.
- **A target that is not a real number can no longer blank the chart.** `MetricUnit.parse` was a
  bare `toDoubleOrNull()`, which accepts `NaN`, `Infinity` and a value whose unit conversion
  overflows; a `NaN` axis made every canvas coordinate `NaN`, so the chart drew nothing and
  nothing said why — `NaN.coerceIn(0f, 1f)` is still `NaN`. Parsing now requires a finite,
  non-negative number checked *after* the unit conversion as well as before, and `axisBounds`
  discards non-finite input.
- **A rating is drawn on its own scale again.** The chart promised a fixed 1–10 axis and the two
  constants that would have done it were referenced nowhere, so RPE readings of 7.1, 7.2 and 7.3
  produced an axis 0.24 wide. The scale now lives beside the metric in the registry and widens
  past 1–10 when a reading or a target leaves it, because those are claims the user made.
- **A rising trend no longer prints as "+0".** The rate used the metric's *reading* formatter,
  which truncates, so 0.2 reps a week read as "+0 reps per week" beside a line going up.
- **A target below zero is visible on a metric anchored at zero.** The axis extends below zero
  only when something actually sits there, so a bar chart's baseline is unchanged in the ordinary
  case.
- **Discarding a workout no longer leaves a white screen.** Two identical `LaunchedEffect(closed)`
  blocks in the workout route both called `popBackStack`, so the back stack was popped **twice**:
  the workout left, and so did the screen beneath it. Discard is where it was noticed, but Finish
  had it too, one tap later. A single named composable, `LeaveWhenClosed`, replaces both and is
  under test. *Verified on the emulator both ways: with the fix reverted, Discard reproduces the
  report — an empty accessibility tree and a screenshot 94.6% one flat colour — and with the fix
  the same sequence lands on home.*

## [1.7] — 2026-10-02 (versionCode 8)

### Added

- **Repeat the last workout in one tap.** It copies the **exercises and their order — not the
  loads**, because progression and the "last time" prefill already answer what to lift next, and
  freezing a week's numbers into a fresh session would put the two in conflict. An exercise
  deleted from the library since is skipped, and one performed twice repeats twice.
- **A rest is heard and felt, and a workout keeps the screen awake** (N27). A tone and a tick when
  a rest ends, and screen-on while a workout is open, each with a switch in settings and both on
  by default — an app that got quieter than it was would be a regression wearing a preference's
  clothes. **Neither asks for a permission:** the cue is an in-process tone and *view-level*
  haptics, and keep-screen-on is a window flag rather than a wake lock. `Vibrator` would have cost
  `android.permission.VIBRATE`, the one thing the app had just stopped declaring.
- **A warm-up ramp in one tap.** A plan's exercise can generate its warm-ups from the weight the
  plan already names: four sets at 40%, 60%, 75% and 85% for five, three, two and one rep, written
  as role-carrying warm-ups. Offered only where there is a weight to take a fraction of, because a
  list of zeroes to load is worse than no control.

### Changed

- **The app is called Workout, and so is the repository.** It had drifted into four names — the
  launcher said *Workout Log*, the roadmap heading *Workout Tracker*, the release assets
  `workout-log`, the repository `android-app`. This is a label, not an identity change:
  `applicationId` and the signing key are untouched, so the app updates in place with no uninstall
  and no lost history. Past release titles, the `workout-log-1.x.apk` assets and this file's own
  preamble keep the old name, because that is what they shipped as.
- **A workout remembers the timezone it was performed in** (N25). Timestamps were always UTC and
  every screen formatted them in the zone you are reading in, so a session logged in Tokyo showed
  the wrong hour — and after a late flight the wrong day — the moment you landed. The offset is
  captured once, when the session opens, and history, the workout detail and the finish review all
  use it. Sessions recorded before this show the current zone, because the data to say where they
  happened was never captured and inventing one would be a lie the rows cannot support.
- **Two names that lied, corrected.** The review a finished workout shows was called
  `WorkoutSummary`, which is also the name of the history row in the domain — two types, one name,
  and the reader left to guess which. The review payload is `WorkoutReview` now, and the dialog that
  draws it is named after it. `SettingsModule` moved out of `DatabaseModule.kt`, which said database
  while binding a `SharedPreferences` repository; the move also showed the new import gate working,
  flagging the three imports it left behind in the same commit.
- **Unused imports are gated.** detekt's `UnusedImports` is off by default and the compiler does
  not run with `-Werror`, so an import left behind by an implementation that landed elsewhere
  passed every gate this project treats as authoritative. Turning the rule on found **fifty-one**
  across twenty-one files.

### Removed

- **The background rest alert, and with it the app's last permission.** The alarm, its receiver,
  the notification, the ask-on-first-set prompt and both manifest permissions are gone
  (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`), so the app declares **no permissions at all**.
  The rest timer itself is untouched: the end instant still lives on the session row and survives
  the process dying, and the rest can now be heard and felt in the app.

### Fixed

- **The last trace of the rest alert is gone from the device too** (B37, B40). Android keeps a
  notification channel across updates until uninstall, so a phone that ran an older build still
  listed "Rest timer" — for an app that posts nothing and declares no permissions. It is deleted
  on launch by one idempotent call that needs no permission.
- **A date can no longer be rendered without saying which zone it is in.** The three formatters
  defaulted their zone to the phone's, which is precisely what let the wrong-day bug ship:
  omitting the argument was the default and looked like ordinary code. The zone is now required
  and every call site names it, which retires the whole class.
- **Two documents that had stopped describing the code**, including a KDoc claiming an open
  session is *refused* when it is *resumed* — the opposite, and the kind of sentence a reader
  plans around — and comments the removed alert left behind in `AndroidManifest.xml`, the very
  file whose emptiness the no-permissions promise rests on, and `AGENTS.md`.
- **Three tests that could not fail now can.** Deleting the rest alert took their assertions with
  it, leaving two empty-bodied tests and one that ran a whole finish flow to assert nothing; they
  now assert that skipping a rest clears it, that adjusting one reaches the repository with the
  step the button sends, and that a comment written on the way to finishing lands on the session.
- **A warm-up ramp is written in front of the work.** It was appended, so a plan read the working
  set first and then the four warm-ups that exist to prepare for it.
- **The repeat action is offered only when it would copy something**, and is absent while a
  workout is open. It asked whether history was non-empty while the copy also requires exercises
  still in the library, so a last workout whose exercises had all been deleted offered a button
  indistinguishable from *Start workout* that opened an empty session.
- **Repeating a workout keeps its rest, note and superset grouping.** Only the exercises were
  copied, so a repeated superset arrived ungrouped — and with no group the round logic
  short-circuits, which is the behaviour supersets exist to prevent.
- **Home shows a past workout's date in its own zone.** It was the one screen that dropped the
  argument and fell back to the phone's, so the same workout read as two different dates on two
  screens. The argument was missing, not the data.
- **The repeat path is exercised end to end.** Its branch was never entered: the route's state
  handle carried only `templateId`, so `repeatLast` was always false, and every part was tested
  while the path between them was not.
- **The date formatter's zone has a test of its own**, comparing two renderings of the same
  instant rather than asserting a string — the half of N25 that turned out to be wrong had no
  coverage, and the second case is the control that stops the first passing for the wrong reason.
- **Finishing a workout leaves the screen again, and the undo offers are back.** Removing the
  alert took the navigation effect and the call that draws the undo offer for a deleted set or a
  finished exercise; neither is visible in a compile and neither had a test. Both were caught by
  detekt's unused-parameter and unused-private-member rules.
- **Pairing a superset is one write.** It wrote one row at a time and carried on after a failure,
  so a failure — or the process dying — between writes could leave half a group, which the screen
  would then show as a superset of one. The group moves in a single statement now.
- **The superset tap is no longer drawn on the first exercise.** With nothing above to pair with,
  the group came out null and the write matched every ungrouped row, churning `updatedAt` for no
  change.
- **Assisted work adds a rep before it takes help off.** The progression rule tested its assisted
  branch before its rep ceiling, so an assisted lifter at the bottom of a 6–8 range was told to
  reduce the machine's help, and "add a rep first" was unreachable for assisted work —
  contradicting the double-progression rule the file states in its own KDoc (N22). The rep to add
  now carries the assistance rather than zeroing it.

## [1.6] — 2026-10-01 (versionCode 7)

### Added

- **Supersets and circuits.** Two or more exercises can be performed together: **Superset with
  above** groups an exercise with the one before it, the pair is labelled **A1/A2**, and the
  **rest belongs to the round rather than the set** (N24) — a rest starts only once nothing else
  in the group is behind, because resting between the pair would defeat the pairing the user just
  asked for. Marking a member done counts as caught up, leaving takes the whole group apart, and
  **a plan can prescribe one** so a workout started from a plan arrives already paired. Schema
  **v16**, with the grouping carried in the backup file.
- **Personal records, and noticing one when it happens.** A record here is a **rep max**: the
  heaviest working set at each rep count, so 100 kg × 5 is beaten only by more weight at five
  reps. Log a set that beats your best and the app says so straight away, above the work, and says
  **what it beat**; the first time at a rep count says that instead, because there was no bar to
  clear. Warm-up sets became excludable with N14's roles, which is why this could not have been
  built honestly before.
- **The app stops handing back the same number.** The prefill proposes the next step by **double
  progression** and says *why* — "One more rep than last time", "A step heavier", "Less help than
  last time" — because a number the app chose is an instruction unless it explains itself (N22).
- **It suggests; it never writes** (N22). No plan and no stored set is changed. **With no plan
  there is no ceiling**, so it proposes one more rep and stops rather than inventing a weight; on
  assisted work the direction inverts, and warm-up sets are excluded as they are everywhere a
  target is measured.
- **A settings screen, and the default rest is finally editable** (N21). The app-wide rest between
  sets was a hardcoded 90 seconds nothing could change. Settings now holds a **Default rest** from
  a bounded set of options; an exercise's or template's own rest still wins, and a change reaches a
  workout that is already open.
- **A review when a workout finishes, with plan versus actual** (N20). Finish used to be a dead
  end: the ratings, readiness note and totals went nowhere. The workout now ends with sets, reps
  and volume, the notes, each exercise's feel and joint pain, and — when it came from a plan —
  **the plan next to what was actually lifted** (prescribed 6×2 at 92.5, performed 6×2 at 92.5,
  top single 2.5 kg over plan). It also says what was **not** done: a skipped prescribed exercise
  is a fact about the session, and an exercise added mid-workout is labelled as not in the plan —
  a review that lists only what was done is a compliment, not a record.
- **Choosing a set's role where the set is logged** (N19). The picker sat behind the editor, so
  three warm-ups cost three log-then-edit round trips; it is now beside **Log set**, and clears
  itself once the set is written, because a role is a decision about one set.
- **Start over, with a way out first.** Deletes are soft, an import can only add, and the only
  true wipe was `adb shell pm clear`, which is not a phone feature. **Delete everything** lives
  with export and import and removes every workout, set, template, planned set, custom exercise,
  rating and comment, plus the crash logs; the seeded library stays, because the seeder restores
  it. The order is the point — export is offered in the dialog, and the confirmation is **typed**
  (`DELETE`) because what cannot be undone is said before the button is enabled. It is **hard**,
  not soft: every other delete here sets `deletedAt`, and a soft-deleted row would survive — an
  export taken afterwards would still carry it.
  It also re-seeds explicitly, because a clear that only emptied tables would leave an empty
  library until the process restarted.

### Changed

- **The two reads and writes that only ever met fakes now meet a database.** `personalRecords` and
  `setSupersetGroup` were exercised through hand-written repositories, which cannot show what the
  SQL does: that a heavier warm-up does not set a record, that the session in progress can be
  excluded so a set is never compared against itself, and that a superset group comes back through
  the projection that reads it — the session projection had already shipped once without selecting
  the column.
- **The settings feature has tests**, which it shipped without: that the screen shows what is
  *stored* rather than what was tapped, that a refused choice leaves the old value in force and
  says why, and that an invalid rest is refused *without* being written.
- **A plan that names reps and no load now says why it proposes what it does** (N22). It explains
  a *load* only: the plan already decides the reps, and "one more rep than last time" beside a set
  the plan sized would be a sentence about the wrong number.
- **The superset label and its tap have tests**, which they did not: giant-set notation is the
  only thing on the workout screen that says two exercises are performed together, and the tap is
  the only way to group them inside a session.
- **The schema upgrade proves it keeps the rows already in the tables.** `MIGRATION_15_16` had been
  amended after it ran on development devices, so a `MigrationTestHelper` case seeds a session
  exercise and a planned exercise at v15, migrates, and asserts both survive with their new columns
  empty.
- **A route that is declared but never registered now fails the test suite.** Two bugs shipped from
  that gap — a route missing `@Serializable`, and a route with no `composable<...>` registration,
  the second of which made the settings screen crash on its first run. The old test named twelve
  routes by hand, so a thirteenth was invisible; the list is now read out of `Routes.kt` and
  compared against what `AppNavHost.kt` registers.
- **A guard against the backup codec quietly losing a column** (N24's preparation). The
  hand-written codec lists every field by name and had silently dropped an unnamed column three
  times, each found by hand on a device after shipping. A round trip with every field set to
  something distinctive now fails in the ordinary suite when a field stops surviving, which is how
  one drop nobody had written down — a session's rest countdown, deliberately not restored — got
  its line to say so.

## [1.5] — 2026-09-30 (versionCode 6)

### Added

- **Trends for one exercise** — over the last ten finished sessions that recorded the lift:
  heaviest working set, estimated one-rep-max, volume, total reps, and that exercise's own average
  RPE, muscle feel and joint pain, which the app had been recording since N6 and N8 without ever
  showing them per lift. **Warm-up sets are excluded from the load series** (N17), which only
  became expressible when N14's roles existed; N13's hand-drawn chart is shared between the two
  screens, and nothing new is stored.
- **Assisted work says which way is forward.** More help is not progress, so that series is the
  least assistance of the session — the hardest set — labelled "less is more", rather than a climb
  that reads as improvement.
- **Tests for the gaps the review found** (B9–B11): the history detail's ViewModel, which had none,
  and its `volumeGrams` KDoc claiming a cross-check no test made; the trends repository, the only
  place RPE halves are divided back into chart points, where a wrong divisor would have mislabelled
  every RPE chart silently; and — most usefully — `ALL_MIGRATIONS` asserted against the runtime
  schema version, because a migration written, tested and forgotten in the array had left the suite
  green while crashing every install with data.

### Changed

- **Tests assert with Truth, and Flow sequences with Turbine**, migrating as files are touched
  rather than in a sweep. `assertEquals(expected, actual)` puts two bare values side by side with
  nothing saying which is which, and the arguments are easy to swap, which
  `assertThat(actual).isEqualTo(expected)` cannot be; Turbine covers what a polled `.value` cannot
  express, such as the readiness note and its dismissed prompt arriving in the *same* state.
- **The configuration cache is on for local builds too.** CI already passed
  `--configuration-cache` on its combined invocation and `gradle.properties` now sets it, so an
  ordinary `./gradlew` gets the same reuse and the flag and the setting cannot drift apart. The
  wrapper's `retries` went from 0 to 3, and the distribution is still validated against Gradle's
  published checksum, so a retry cannot substitute a different artifact.
- **CI: one Gradle invocation where there were five**, because each separate call paid its own
  configuration; and the emulator job now fails unless the executed test count equals the count of
  `@Test` annotations in `app/src/androidTest`, naming the classes that did not report — the job
  whose result XML once recorded 62 of 127 tests while reporting green (B8, B12).
- **The review's rule violations are settled.** `HALVES_PER_POINT` is one constant on `Rpe`; the
  two `DayOfWeek` formatters are one shared pair of composables; the two `SetType` pickers are one
  `SetRoleSelector`; the duplicate `ActiveWorkoutInfo` went with B13's dead state; and the three
  `CenteredMessage` copies are one composable, which gained the spinner its callers needed. Four
  files are named for what they hold (`DataErrorMessage.kt`, `NoteDialogs.kt`,
  `ExercisePickerRoute.kt`, and a `RestAlarmReceiver.kt` of its own, since Android instantiates it
  by name).

### Removed

- **Deleted what moved and left its shape behind** (B13). The exercise library's ViewModel still
  carried the whole "workout in progress" apparatus — a state field, two flows, a per-second ticker,
  and the `TimeSource` and `WorkoutRepository` dependencies that existed only to feed it — months
  after the resume button moved to home, kept alive by a test asserting the dead state was null.
  With it went `@ApplicationScope`, the module binding a scope nothing injects, and four
  declarations nothing called (`TemplateDao.findTemplateSets`, `WorkoutSession.isActive`,
  `WorkoutSummary.hasVolume`, `PreviousPerformance.isEmpty`). Per D2, `Weight.step`,
  `DataResult.map` and `successUnit` went too — APIs whose only caller was the test that tested
  them.

### Fixed

- **The finish review's counts now agree with its reps.** "Prescribed 2×3" meant two sets, one a
  warm-up, totalling three reps, because the counts included warm-ups while the rep sums dropped
  them; both exclude warm-ups now, as the record and progression rules do. A plan that names no
  reps no longer reads "prescribed 2×0" either.
- **The review tells two rows of the same exercise apart.** It matched by display name, so a
  movement performed twice collapsed to one row — the earlier one's sets vanished while the totals
  still counted them. Matching is by exercise id now.
- **A warm-up can no longer raise a personal best.** The record rule never saw the set's role, so a
  heavy warm-up raised the banner the app's own documentation said could not happen; the rule takes
  the role now, so a caller cannot forget it.
- **A record says what it actually beat.** The banner read history alone, so a bar set earlier in
  the same session was invisible: 20 kg then 22.5 kg at eight reps announced "the first time at
  this rep count" while claiming a record over that very 20 kg. It reads history plus what this
  session has already logged.
- **Editing a logged set no longer wipes its role or its assistance.** The editor was never told
  either, so it opened every set as a plain working set and saving wrote that over the row — a
  one-rep correction on a `-20 kg` assisted warm-up destroyed both, and history did not model the
  role, which made it unrecoverable. History records the role now.
- **A superset rests for the group's longest member** (N24). The app rested from whichever exercise
  happened to log the closing set, so a pair prescribing 180 s and 90 s rested 90 s whenever the
  latter closed the round, and the same pair rested differently depending on the order it was
  logged in.
- **Tapping a lift in a past workout showed no trends for it** (N17). The history screen passed the
  session's own row id where a series exists only under the *library* exercise's id — a query that
  silently matches nothing, so a lift just logged read as "nothing recorded yet". Found by device
  verification against the published 1.4 upgrade.
- **A cancelled export no longer reports a failed one.** Both callbacks wrapped their file IO in
  `runCatching`, which catches `Throwable` and so swallows the `CancellationException` raised when
  the user leaves the screen mid-write, and the coroutine refused to finish cancelling. Both now
  rethrow cancellation, the rule `dataResultOf` already stated.
- **An assisted set no longer loses its help in history** (B5). The live workout showed `-20 kg`;
  the workout **detail** built the row without passing the column and rendered the field's
  default, so one tap into history read N15's feature back wrong.
- **A plan's target RPE reads as a lifter writes it** (B6). The plan dialog passed the stored
  half-point count straight to the marker, so a plan saying 9.5 rendered as **"RPE 19"** — the
  confusion the halves representation exists to prevent; the three screens that show an RPE now
  share one formatter.
- **One-tap "Log set" writes the set its button described** (B7, D3). The button read *"Log set ·
  -20 kg × 8"* and wrote a set with no assistance, because the value was in the suggestion and not
  passed; undoing a deleted set dropped it too. The button does what it says rather than saying
  less.

## [1.4] — 2026-09-30 (versionCode 5)

### Added

- **A plan can be pinned to a weekday, and home shows today's plan.** Several plans may share a
  day — training twice on a Friday is a thing people do — and each is listed with a Start action; a
  plan with no day is one you start by hand, which is why "Not scheduled" is a value rather than an
  empty state. It is **a living template, not a dated instance** (N16). Migration **14→15** adds a
  nullable `templates.weekday`, stored by name like every other enum.
- **Assisted load.** The load field takes a leading minus, so `-20` means the machine took 20 kg
  off. What is stored is a separate `assistanceGrams` — **a magnitude, never a signed weight** —
  which keeps an assisted set from reporting negative tonnage and corrupting every volume trend
  (N15); a plan can prescribe it, and the prefill carries it into the workout. Migration **12→13**
  adds `set_entries.assistanceGrams`, defaulting to 0 so every set already recorded keeps meaning
  what it meant, and a nullable `targetAssistanceGrams` on a plan's sets.
- **Templates are plans now.** A template exercise carries planned sets — a role, a target weight,
  a target rep range, a target RPE and a note — plus the rest and cue the plan prescribes for that
  exercise, and starting a workout prefills each set from them. **Copy forward** duplicates an
  exercise's sets, because thirty sets of four fields on a phone is where a plan stops being
  written down. **A set's role** gives planned and performed sets one vocabulary — working,
  warm-up, **top set**, drop and failure (N14). Migrations **10→11** add `template_sets` and the
  plan's `restSeconds`/`techniqueNote`, and **11→12** adds those two to `session_exercises` so the
  plan's rest and cue reach a session — both additive, nothing backfilled, with the backup codec
  carrying all three or an export would drop them in silence.
- **Targets only:** nothing verifies a plan against what was lifted, and a logged set is a separate
  row expected to differ — declining to check is what keeps this a plan rather than a compliance
  feature.
- **A comment on the workout itself.** Finishing asks once, and skippably, how it went, at the
  moment you remember why; it needed no migration, because `workout_sessions.notes` has been in the
  schema since v1 and never had a UI.
- **How it felt can be recorded at any time**, per exercise, rather than only at Done, so the two
  ratings and the pain location are written down while the set is fresh. The Done prompt stays as
  the last chance rather than the only one, and the row is still there on a done exercise.
- **Joint pain says where** — an optional free-text box beside the rating, because a 4 means more a
  month later with a place attached. Migration **9→10**.
- **Trends: what the app collects, read back.** RPE, muscle feel and joint pain over the last ten
  finished workouts, each on the same fixed 1–10 axis with its latest value and average. A metric
  recorded once is a number rather than a line, a workout that did not record one leaves a gap
  rather than being interpolated, "not recorded" is never drawn as zero, and a workout with nine
  rated sets does not shout louder than one with a single rated set. No charting dependency — three
  series over ten points did not earn one — so the lines are drawn on a `Canvas`.

### Changed

- **RPE takes half steps.** It is stored as *halves in an integer* (`19` is 9.5) for the same
  reason weights are whole grams: 9.5 has no exact binary representation and an RPE that compares
  as `9.499999`, or drifts when a trend averages it, is worse than a unit of arithmetic (N6). The
  field accepts `9`, `9.5` and `9,5` and refuses anything finer than a half — 9.3 is not rounded to
  9.5, which would be a claim about the set nobody made — while the muscle-feel and joint-pain
  ratings stay a different, whole-number scale. Migration **13→14** renames the columns to
  `rpeHalves` and `targetRpeHalves` and doubles existing values, because re-using `rpe` for another
  unit is how a silent corruption starts; the backup keeps reading the old whole-number field, since
  renaming it would have dropped the RPE out of every earlier export on restore.
- **The 1–10 scales now say what their ends mean** — "1 = barely worked, 10 = fully worked" and
  "1 = none, 10 = severe" — while the number is being picked, and only there: the workout detail
  keeps showing a bare "Muscle feel 8" rather than repeating the vocabulary on every past workout.

### Fixed

- **Export and import are back where you start.** They lived two overflow menus deep, so the
  feature read as missing; they are in the home overflow beside Library, History and Templates, and
  the library's copy is gone — one path, not two.
- **Removing an exercise asks first.** It takes the exercise's sets with it and has no undo, so a
  mis-tap silently reshaped the workout; the dialog says what goes rather than posing a bare
  question.
- **An Undo can no longer act on something that is gone.** The deleted-set and Done snackbars share
  one host, so a deleted set's Undo could outlive its exercise: the row was gone, the button
  stayed, and tapping it failed the loggable-exercise guard without saying anything. An undo is
  offered only while its subject is in the session, and one tapped anyway says so.
- **A library read that fails is a message, not a crash.** `observeExercises` and `getExercise`
  were the last calls that could throw out of a flow and take a screen down; both return the same
  `DataResult` the writes do, and "we could not read it" is no longer the same sentence as "it is
  not there".

## [1.3] — 2026-09-29 (versionCode 4)

### Added

- **Workout templates: a plan you build once and start in one tap.** Home's start action offers
  *Start workout* or *Start from template*, and starting one opens the session with its exercises
  already in order, through the same append path a manual add takes. Resuming an open workout never
  seeds a second copy, and editing the session never touches the template. Templates ride along in
  the export file, soft-deleted ones included. Migration **8→9**.
- **How it felt: muscle feel and joint pain.** Marking an exercise done asks once, and skippably,
  for two 1–10 ratings, stored per session exercise so the same movement is measured differently on
  different days; they stay editable from the detail, and are deliberately unlabelled at first.
  Migration **7→8**.
- **Done, so an exercise stops taking sets by accident.** A per-exercise **Done** hides its **Log
  set** button, dims its sets so they cannot be edited, and stops any rest it had running, with an
  **Undo** on the snackbar and a **Reopen** button, because accident protection must not become its
  own trap. The workout-level action is already *Finish*, so this is *Done*, never *Finish* — and it
  is a session state, not a delete: the sets stay in history. Migration **6→7**.
- **An RPE and a comment on every set.** The one-tap **Log set** path writes neither, so logging
  stays fast; a set carrying either shows a small marker in the workout, and an RPE outside 1–10
  blocks Save rather than being clamped, because a silent 11 → 10 would misstate the set. Migration
  **5→6**.
- **A readiness note when a workout starts** — free text on the session, reachable again from the
  workout header, riding through the detail and export; a resumed workout is not asked again.
  Migration **4→5**.
- **Per-exercise rest and a technique cue**, falling back to the 90 s default, both editable from
  the exercise detail screen, which now edits **seeded** exercises too — the seeder tops up with
  `INSERT OR IGNORE` and never updates an existing row, so an edit survives every future top-up.
  Migration **3→4**.
- **Create a custom exercise from inside a workout**, stored `isCustom = true` with a UUID id and
  an unspecified taxonomy so it is not a dead end mid-workout, and **editable afterwards** so
  filling in its taxonomy makes it pickable in later workouts.
- **Fifteen more exercises**, mostly competition, paused and accessory variants; the seeder tops up
  on every open, so an existing install receives them without a migration.

### Changed

- **An exercise's subtitle no longer reads "Other · Other"**, since `Other` is the "not filled in
  yet" value a custom exercise is created with — the library row and the workout's exercise header
  drop that part of the `Quads · Barbell` line, so an unedited custom exercise shows its name alone.
- **The app opens on your workouts, not the exercise list.** Home is a short list of recent
  workouts with **Start workout** (or **Resume**) and a link to the full history; the library became
  a screen you navigate to, and stays the picker inside a workout.

## [1.2] — 2026-09-29 (versionCode 3)

### Added

- Set rows announce that they are editable, so a screen reader no longer reads a row and leaves the
  user to guess (`onClickLabel`).

### Fixed

- A duration of an hour or more rendered differently in the rest timer than in the workout clock —
  `90:00` against `1:30:00`. Both now share one formatter, and the rest timer gained the hours
  field.

## [1.1] — 2026-09-29 (versionCode 2)

The upgrade-test build: installed over 1.0 without uninstalling, and confirmed to keep the workout
history. **No user-visible changes** — it exists to prove that the permanent signing key accepts an
upgrade rather than demanding an uninstall, which would have cost the history.

## [1.0] — 2026-09-28 (versionCode 1)

First release. Sideloaded as a signed APK; there is no Play Store listing.

### Added

- Exercise library: 30 seeded movements with primary and secondary muscles, equipment and movement
  pattern. Search covers all of them on the display label *and* a locale-stable key, so it survives
  translation.
- Start a workout, log sets by reps and weight, with prefill from what you just did or from last
  time, tap to edit, and delete with undo.
- Rest timer with an in-app countdown and an optional background alert.
- Workout history: a chronological list grouped by month, plus a detail view with duration, volume
  and set count.
- Correct or delete a past set, and delete a whole workout behind a confirmation — so finishing a
  workout is no longer a one-way door.
- Resume affordance: the library button shows the running workout and its elapsed time instead of
  offering to start another.
- Export and import the whole database as JSON, through the Storage Access Framework. Import
  restores anything missing or deleted and never overwrites what is still there.

### Notes

- Local only. No `INTERNET` permission, no accounts, no ads, no analytics, and
  `allowBackup="false"` — the export file is the only way data leaves the device.
- Sets are `reps × weight`. Bodyweight work is reps at 0 kg; duration and distance are deliberately
  out of scope for this version.
