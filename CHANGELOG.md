# Changelog

Notable changes to Workout, newest first. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are the
`versionName` from [`version.properties`](version.properties), with the
`versionCode` in brackets because that is what Android actually compares.

## [Unreleased]


### Added

- **Repeat the last workout in one tap.** Templates cover the planned session; this covers the
  unplanned one, where "same as last time" is the most common thing a lifter does and used to cost
  picking six exercises out of the library again. Home's start action offers **Repeat last workout**
  whenever a finished one exists, and it copies the **exercises and their order — not the loads**:
  progression and the "last time" prefill already answer what to lift next, and freezing a week's
  numbers into a fresh session would put the two in conflict. An exercise deleted from the library
  since is skipped while the rest repeat, and an exercise performed twice repeats twice, because that
  is what was performed.
- **A rest is now heard and felt, and a workout keeps the screen awake.** Removing the background alert
  left the timer noticeable only while you were looking at it, so this is what replaces it: a
  short tone and a tick when a rest ends, and the screen staying on while a workout is open. Both have
  a switch in settings — *Chime when a rest ends* and *Keep the screen on* — and both default to on,
  because an app that got quieter than it was would be a regression wearing a preference's clothes.
- **Neither asks for a permission.** The cue is an in-process tone and *view-level* haptics;
  keep-screen-on is a window flag rather than a wake lock. `Vibrator` would have been the obvious call
  and it costs `android.permission.VIBRATE` — the one thing the app just stopped declaring, so the
  constraint is recorded in [DECISIONS.md](DECISIONS.md) rather than left to be rediscovered.


- **A warm-up ramp, in one tap.** A plan's exercise can now generate its warm-ups from the weight the
  plan already names: four sets — 40%, 60%, 75% and 85% for five, three, two and one rep — written
  into the plan as role-carrying warm-ups. N14 made a warm-up expressible and the plan editor let you
  type one; this is what makes writing four of them a single action instead of four. It is offered
  only where there is a weight to take a fraction of: a bodyweight or assisted exercise gets no ramp,
  because a list of zeroes to load is worse than no control at all. Two screen tests hold the one
  thing the dialog owns — that the control is drawn when it is given one and absent when it is not, and
  an instrumented test writes the ramp through Room and reads it back: a column that dropped the role,
  or a projection that forgot it, fails there rather than at the gym.

### Changed

- **The app is called Workout, and so is the repository.** It had drifted into four names: the
  launcher said *Workout Log*, the roadmap heading said *Workout Tracker*, the release assets said
  `workout-log`, and the repository said `android-app` — which named the platform rather than the
  app. Everything forward-looking now says **Workout**, which matches the `applicationId` it has
  always had and, unlike "Log", will not go stale as the app does more than log. **This is a label,
  not an identity change:** `applicationId` and the signing key are untouched, so the app updates in
  place with no uninstall and no lost history. What deliberately still reads *Workout Log* is the
  record — past release titles, the `workout-log-1.x.apk` assets and the changelog's own preamble —
  because that is what the app was called when those shipped.
- **A workout remembers the timezone it was performed in.** Timestamps were always UTC, and every
  screen formatted them in the zone you are reading in — so a session logged in Tokyo showed the wrong
  hour, and after a late flight the wrong day, the moment you landed. The offset is now captured once,
  when the session opens, and every screen that shows a session's time uses it: history groups a
  workout under the month *it* was performed in, not the month it is where you are, and the workout
  detail and the finish review read it too. Sessions recorded before this show what they always did —
  the current zone — because the data to say where they happened was never captured, and inventing one
  would be a lie the rows cannot support.
- **Two names that lied, corrected.** The review a finished workout shows was called `WorkoutSummary`,
  which is also the name of the history row in the domain — two types, one name, and the reader left
  to guess which. The review payload is `WorkoutReview` now, and the dialog that draws it is named
  after it. `SettingsModule` moved out of `DatabaseModule.kt`: the file said database while binding a
  `SharedPreferences` repository. The move also showed the new import gate working — it flagged the
  three imports the moved code left behind, in the same commit that would have carried them.
- **Unused imports are gated.** detekt's `UnusedImports` is off by default and the compiler does not
  run with `-Werror`, so an import left behind by an implementation that landed elsewhere passed every
  gate this project treats as authoritative. Turning the rule on found **fifty-one** across twenty-one
  files — far more than the two that prompted it — which is the argument for the rule rather than
  against it.

### Removed

- **The background rest alert, and with it the app's last permission.** The alarm, its receiver, the
  notification, the ask-on-first-set prompt and both manifest permissions are gone —
  `POST_NOTIFICATIONS` and `SCHEDULE_EXACT_ALARM`. The app now declares **no permissions at all**,
  which is a stronger statement about your data than any wording could be. The rest timer itself is
  untouched: the end instant still lives on the session row and survives the process dying; only the
  way it reaches you changed, and since it can now be heard and felt in the app, nothing is lost.

### Fixed

- **Two documents that had stopped describing the code.** A KDoc for the append helper was left
  stranded above a different function when the helper moved, and the repeat port claimed an open
  session is *refused* when it is in fact *resumed* — the opposite, and the kind of sentence a reader
  plans around.
- **The last trace of the rest alert is gone from the device too.** Android keeps a notification
  channel across updates until uninstall, so a phone that ran a build before the alert was removed
  still listed "Rest timer" in its notification settings — for an app that posts nothing and declares
  no permissions. It is deleted on launch: one idempotent call, no permission needed, and the decision
  is recorded in [DECISIONS.md](DECISIONS.md).
- **A date can no longer be rendered without saying which zone it is in.** The three formatters
  defaulted their zone to the phone's, which is precisely what let the Home bug ship: omitting the
  argument was the default and looked like ordinary code, so the compiler had nothing to object to.
  The zone is now required, and every call site names it — a session's own offset, or the reading zone
  for rows recorded before offsets existed. It is a small change that retires the whole class.
- **The comments the removed rest alert left behind.** Six places still described permissions and
  behaviour the app no longer has — including `AndroidManifest.xml`, the very file whose emptiness the
  no-permissions promise rests on, and `AGENTS.md` naming a test fake that was deleted with the alert.
  Documentation that contradicts the code is worse than none: a reader trusts the file nearest the
  claim, which is how the stale comment outlives the change.
- **Three tests that could not fail now can.** They were written for the background rest alert, and
  when the alert was deleted their assertions went with it — leaving two empty-bodied tests whose names
  described behaviour that no longer existed, and one that ran a whole finish flow to assert nothing.
  Two of them were the only coverage of the actions they called. They now assert what remains: that
  skipping a rest clears it, that adjusting one reaches the repository with the step the button sends,
  and that a comment written on the way to finishing lands on the session.
- **A warm-up ramp is written in front of the work.** It was appended, so a plan read the working set
  first and then the four warm-ups that exist to prepare for it — while its own description said it
  wrote them in front. The ramp is now written to the head of the exercise in one transactional call.
- **The repeat action is offered only when it would copy something.** It asked whether history was
  non-empty, while the copy itself also requires exercises still in the library — so a last workout
  whose exercises had all been deleted since offered a button indistinguishable from *Start workout*
  that opened an empty session. Both now ask the same question, and Home answers it from a count the
  history row already carries.
- **The date formatter's zone is held by a test of its own.** N25's grouping was covered thoroughly and
  the half that turned out to be wrong — what a screen renders — was not, so the formatter now has two
  tests that compare two renderings of the same instant rather than asserting a string: near a day
  boundary the zone changes the day, and away from one it does not. The second is the control that stops
  the first passing for the wrong reason.
- **The repeat action is absent while a workout is open.** The start choices hide themselves when one is
  running, which is the only sane answer — a second way to start a workout mid-session is a way to lose
  one — and nothing held it: the existing absence test passed an empty history, which is a different
  rule.
- **The repeat path is exercised end to end.** Its branch was never entered by a test: the route's
  state handle carried only `templateId`, so `repeatLast` was always false, and the fake's field for
  "what the last workout held" was never assigned — every part was tested, and the path between them
  was not. Two tests now enter it in both directions, so "it repeats" cannot be passing because the
  fake seeds something regardless.
- **Repeating a workout keeps its rest, note and superset grouping.** Only the exercises were copied,
  so a repeated superset arrived ungrouped — and with no group the round logic short-circuits, so the
  pair degraded into unrelated exercises resting separately, which is the behaviour supersets exist to
  prevent. The rest and the technique note were dropped the same way.
- **Home shows a past workout's date in its own zone.** It was the one screen that dropped the
  argument and fell back to the phone's zone, so the same workout read as two different dates on two
  screens — differing by a day near midnight, which is exactly the case the stored offset exists for.
  The argument was missing, not the data: the column and the accessor were both already there.
- **Finishing a workout leaves the screen again, and the undo offers are back.** Removing the
  background rest alert took two things with it that sat beside the permission flow: the effect that
  navigates away once a workout is closed, and the call that draws the undo offer for a deleted set
  or a finished exercise. Neither is visible in a compile, and neither had a test — both were caught
  by detekt's unused-parameter and unused-private-member rules, which is the second time in this batch
  that a gate nobody was watching did the catching.


- **Pairing a superset is one write.** It wrote one row at a time and carried on after a failure, so a
  failure — or the process dying — between writes could leave half a group: the exact state the
  action exists to prevent, and one the screen would then show as a superset of one. The whole group
  moves in a single statement now, and a partial result is reported instead of assumed.
- **The superset tap is no longer drawn on the first exercise.** With nothing above it to pair with,
  the group came out null — and the write then matched every ungrouped row and rewrote each to null,
  churning `updatedAt` for no change. It is offered from the second exercise on, in the workout and in
  the plan editor alike.
- **Assisted work adds a rep before it takes help off.** The progression rule tested its assisted
  branch before its rep ceiling, so an assisted lifter at the *bottom* of a 6–8 range was told to
  reduce the machine's help and keep the reps — "add a rep first" was unreachable for assisted work,
  contradicting the double-progression rule the file states in its own KDoc. The ceiling is tested
  first now, and the rep to add carries the assistance rather than zeroing it, which would have
  turned an assisted set into a bodyweight one.

## [1.6] — 2026-10-01 (versionCode 7)


### Added

- **Supersets and circuits.** Two or more exercises can be performed together: **Superset with
  above** groups an exercise with the one before it, the pair is labelled **A1/A2**, and the
  **rest belongs to the round rather than the set** — the app only starts a rest once nothing
  else in the group is behind, because resting between the pair would defeat the pairing the
  user just asked for. Marking a member done counts as caught up, so a group does not wait for
  an exercise that is finished, and leaving a superset takes the whole group apart: a group of
  one is not a group. A circuit is the same thing with three or more members. **A plan can
  prescribe one too**, so a workout started from a plan arrives already paired rather than being
  grouped by hand every time. This is the first structural change since N14 — the schema goes to
  v16, with the grouping carried in the backup file so a restored workout keeps its pairs.


- **Personal records, and noticing one when it happens.** A record here is a **rep max**: the
  heaviest working set at each rep count, so 100 kg × 5 is beaten only by more weight at five
  reps — not by twelve reps at 90 kg. Log a set that beats your best and the app says so
  straight away, above the work rather than in a dialog, and it says **what it beat**: "your
  best before was 95 kg". The first time at a rep count says that instead, because there was
  no bar to clear and claiming one would be a lie the data does not support.
- **Records are only correct now.** Warm-up sets became excludable with N14's roles, so a
  120 kg warm-up can no longer be mistaken for the work — which is why this could not have
  been built honestly before.
- **The app stops handing back the same number.** Until now a new session prefilled exactly
  what was lifted last time, which made a written plan and a year of history worth nothing at
  the moment they should count. The prefill now proposes the next step by **double
  progression**: keep the load and add a rep until the plan's rep ceiling is reached, then
  add 2.5 kg and start the range again. It says *why* — "One more rep than last time", "A step
  heavier", "Less help than last time" — because a number the app chose is an instruction
  unless it explains itself.
- **It suggests; it never writes.** No plan and no stored set is changed: a suggestion the app
  applied silently would be a programme decision taken without the person training. **With no
  plan there is no ceiling**, so it proposes one more rep and stops rather than inventing a
  weight. On assisted work the direction inverts — the machine doing less is the progress —
  and warm-up sets are excluded, as they are everywhere a target is measured.
- **A settings screen, and the default rest is finally editable.** There was no settings
  screen at all, and the app-wide rest between sets was a hardcoded 90 seconds that nothing
  could change: every workout that had no rest of its own was stuck with it. Settings now
  holds a **Default rest**, chosen from a bounded set of options — an exercise's own rest
  from the library or the template still wins, and a change made in settings reaches a
  workout that is already open. The screen is deliberately small; it is the home that the
  units, screen-on and rest-sound rows have been waiting for.
- **A review when a workout finishes, with plan versus actual.** Finish used to be a dead
  end: the ratings the user had just given, the readiness note and the totals went nowhere.
  The workout now ends with a summary — sets, reps and volume, the notes, and each
  exercise's feel and joint pain read back — and, when the workout came from a plan, **the
  plan next to what was actually lifted**: prescribed 6×2 at 92.5, performed 6×2 at 92.5,
  top single 2.5 kg over plan. That comparison is the entire payoff for planning, and the
  app had never made it: a plan was write-only.
- **The review says what was not done.** A prescribed exercise that was skipped is a fact
  about the session, and an exercise added mid-workout is labelled as not in the plan — a
  review that lists only what was done is a compliment, not a record.
- **Choosing a set's role where the set is logged.** The role picker existed but sat behind
  the editor, so three warm-ups cost three log-then-edit round trips. It is now beside
  **Log set**: pick the role, tap, done — one tap each way. The choice clears itself once
  the set is written, because a role is a decision about one set and leaving it armed would
  mark the next one a warm-up without anyone asking.
- **Start over, with a way out first.** The app had no way to let go of its data: deletes
  are soft, an import can only add, and the only true wipe was `adb shell pm clear`, which
  is not a phone feature. **Delete everything** now lives with export and import in the
  home menu and removes every workout, set, template, planned set, custom exercise, rating
  and comment — plus the crash logs, which are diagnostics about that same data. The seeded
  exercise library stays: it is app content, and the seeder restores it.
- **The order is the point: export, then type, then go.** Platform backup is off, so an
  exported file is the only thing that can outlive the action — offering it is part of the
  dialog, not a courtesy beside it. The confirmation is **typed** (`DELETE`), because
  "delete everything" is a button someone presses by accident exactly once, and what cannot
  be undone is said before the button is enabled rather than in the aftermath.
- **Hard, not soft.** Every other delete here sets `deletedAt`, which is right for a
  mistaken tap and wrong for starting over: a soft-deleted row survives, and an export
  taken afterwards would still carry it. And clearing re-seeds explicitly, because the
  seeder runs when the database is *opened* — a clear that only emptied tables would have
  left an empty library until the process restarted, which looks broken rather than clean.

### Changed

- **The two reads and writes that only ever met fakes now meet a database.** `personalRecords` and
  `setSupersetGroup` were exercised through hand-written repositories, which cannot show what the
  SQL does: that a heavier **warm-up** does not set a record, that the session in progress can be
  excluded so a set is never compared against itself, and that a superset group written to a row
  comes back through the projection that reads it. That last one is not hypothetical — the session
  projection shipped once already without selecting the column, so a grouped exercise arrived
  ungrouped while the write succeeded.

- **The settings feature has tests.** It shipped with none in either source set, so three things it
  promises were unverified: that the screen shows what is *stored* rather than what was tapped,
  that a refused choice leaves the old value in force and says why, and that a preference survives
  the object that wrote it. The store's round trip is covered on the JVM, including that an invalid
  rest is refused *without* being written — the difference between reporting a failure and quietly
  keeping it.

- **A plan that names reps and no load now says why it proposes what it does.** The suggestion for
  that case passed the rule's reason away, so a step heavier than last time arrived with no
  explanation. It explains a *load* only: the plan already decides the reps, and "one more rep than
  last time" beside a set the plan sized would be a sentence about the wrong number.
- **The superset label and its tap have tests**, which they did not: giant-set notation is the only
  thing on the workout screen that says two exercises are performed together, and the tap is the
  only way to group them inside a session.

- **The schema upgrade keeps the rows already in the tables, and now proves it.** `MIGRATION_15_16`
  was amended after it had already run on development devices — a second column joined the same
  version — so the question a chain test cannot answer is whether an upgrade preserves what is
  there. A `MigrationTestHelper` case seeds a session exercise and a planned exercise at v15,
  migrates, and asserts both survive with their new columns empty, which is what every exercise was
  before supersets existed.

- **A route that is declared but never registered now fails the test suite.** Two bugs shipped from
  that gap: a route missing `@Serializable`, and a route with no `composable<...>` registration —
  the second is what made the settings screen crash on its first run. The old test named twelve
  routes by hand, so a thirteenth was invisible to it; the list is now read out of `Routes.kt` and
  compared against what `AppNavHost.kt` registers, with no hand-maintained list to forget. Verified
  by adding a route and watching it fail, then removing it.

- **A guard against the backup codec quietly losing a column.** The codec is hand-written,
  listing each field by name, and it has silently dropped an unnamed column three times — a
  set's location, a set's assistance, a template's weekday — each found by hand, on a device,
  after shipping. A round trip with every field set to something distinctive now fails in the
  ordinary test suite when a field stops surviving, so the next column this app adds fails
  where it is added rather than turning up missing in someone's backup. The guard immediately
  earned its place by surfacing one drop nobody had written down: a session's rest countdown
  is deliberately not restored, and the line that drops it now says so.

## [1.5] — 2026-09-30 (versionCode 6)


### Added

- **Trends for one exercise.** N13 reads the app's signals across everything; this
  answers the narrower question a lifter actually asks — *how is my bench press going* —
  from rows the app already writes. Over the last ten finished sessions that recorded the
  lift: heaviest working set, estimated one-rep-max, volume, total reps, and that
  exercise's own average RPE, muscle feel and joint pain, which the app has been
  recording since N6 and N8 without ever showing them per lift. **Warm-up sets are
  excluded from the load series**, which only became expressible when N14's roles
  existed: a warm-up must not become the "heaviest set" on a chart. N13's hand-drawn
  chart is now shared between the two screens, and nothing new is stored. Reached from
  the exercise's own screen and by tapping a lift in a past workout.
- **Assisted work says which way is forward.** On an assisted machine more help is not
  progress, so that series is the least assistance of the session — the hardest set —
  labelled "less is more", rather than a climb that reads as improvement.
- **Tests for the gaps the review found** (B9–B11). The history detail's ViewModel had no
  test at all, which is how B5 shipped, and its `volumeGrams` KDoc claimed a cross-check
  against the SQL figure that no test made — both are real now. The trends repository,
  the only place RPE halves are divided back into the points a chart shows, had no test
  in either source set: a wrong divisor would have mislabelled every RPE chart silently.
  And `ALL_MIGRATIONS` was consumed only by the database builder, so a migration written,
  tested, and forgotten in the array left the suite green while crashing every install
  with data — the array is now asserted against the runtime schema version, and the whole
  chain is run once from the first schema.

### Changed

- **Tests assert with Truth, and Flow sequences with Turbine.** `assertEquals(expected,
  actual)` puts two bare values side by side with nothing saying which is which, and the
  arguments are easy to swap the wrong way round; `assertThat(actual).isEqualTo(expected)`
  cannot be. Turbine covers what a polled `.value` cannot express at all — a sequence of
  emissions, or an invariant across one — which is how the readiness note is now checked:
  the note and the dismissed prompt must arrive in the *same* state, so no observer sees
  one without the other. Both are JVM-only test dependencies. Existing files use JUnit and
  migrate **as they are touched**, never in a sweep, so there is no half-converted file.
- **The configuration cache is on for local builds too, and the wrapper retries a
  distribution download.** CI already passed `--configuration-cache` on its combined
  invocation; `gradle.properties` now sets it, so an ordinary `./gradlew` gets the same
  reuse and the flag and the setting cannot drift apart. The wrapper's `retries` went
  from 0 to 3, so a flaky connection fails a download rather than the build — the
  distribution is still validated against Gradle's published checksum, so a retry cannot
  substitute a different artifact.
- **CI: one Gradle invocation where there were five, and a guard against a run that
  skips tests.** The build job now names all five tasks in one `./gradlew` call with the
  configuration cache on, because each separate call paid its own configuration. And the
  emulator job — whose result XML once recorded 62 of 127 tests while reporting green —
  now fails unless the executed count equals the count of `@Test` annotations in
  `app/src/androidTest`, naming the classes that did not report. A result file older than
  the sources is itself a failure, since that is what a replayed report looks like (B8,
  B12).
- **The review's rule violations are settled.** `HALVES_PER_POINT` is one constant on
  `Rpe`; the two `DayOfWeek` formatters are one shared pair of composables; the two
  `SetType` pickers are one `SetRoleSelector`; the duplicate `ActiveWorkoutInfo` went with
  B13's dead state; and the three `CenteredMessage` copies are one composable, which
  gained the spinner its callers needed — a shared component that cannot do what its
  callers do is one they stop using. Four files are named for what they hold
  (`DataErrorMessage.kt`, `NoteDialogs.kt`, `ExercisePickerRoute.kt`, and a
  `RestAlarmReceiver.kt` of its own, since Android instantiates it by name).

### Removed

- **Deleted what moved and left its shape behind** (B13). The exercise library's
  ViewModel still carried the whole "workout in progress / resume clock" apparatus — a
  state field, two flows, a per-second ticker, and the `TimeSource` and
  `WorkoutRepository` dependencies that existed only to feed it — months after the resume
  button moved to home, kept alive by a test asserting the dead state was null. With it:
  `@ApplicationScope` and the module binding a scope nothing injects, and four
  declarations nothing called (`TemplateDao.findTemplateSets`, `WorkoutSession.isActive`,
  `WorkoutSummary.hasVolume`, `PreviousPerformance.isEmpty`). Per D2, `Weight.step`,
  `DataResult.map` and `successUnit` went too — APIs whose only caller was the test that
  tested them.

### Fixed

- **The finish review's counts now agree with its reps.** "Prescribed 2×3" meant two sets, one of
  them a warm-up, totalling three reps — the counts included warm-ups while the rep sums dropped
  them. Both exclude warm-ups now, as the record and progression rules do, and the dialog says so.
- **A plan that names no reps no longer reads "prescribed 2×0".** A guard tested the wrong list, so
  a prescription with no rep ceiling produced a sum of zero and put it on screen, against the rule
  that a field with nothing to say stays empty.
- **The review tells two rows of the same exercise apart.** It matched exercises by display name,
  so a movement performed twice collapsed to one row — the earlier one's sets vanished from the
  review while the totals still counted them, which made the summary contradict itself. Matching is
  by exercise id now.

- **A warm-up can no longer raise a personal best.** The record rule never saw the set's role, so
  a heavy warm-up at a rep count with no record — or above the existing one — raised the banner,
  which the app's own documentation said could no longer happen. The rule takes the role now, so
  it cannot be forgotten by a caller.
- **A record says what it actually beat.** The banner read history alone, so a bar set earlier in
  the same session was invisible to it: lifting 20 kg and then 22.5 kg at eight reps announced
  "the first time at this rep count" while claiming a record over that very 20 kg. It reads the
  merged view now — history plus what this session has already logged — which is what "what it
  beat" was always supposed to mean.

- **Editing a logged set no longer wipes its role or its assistance.** The set editor was never
  told either, so it opened every set as a plain working set with no help — and saving wrote that
  over the row. A one-rep correction on a `-20 kg` assisted warm-up silently destroyed both, and
  in history the role was not even modelled, which made it unrecoverable. History now records the
  role, both screens open the editor on what was stored, and a screen-level test holds it.
- **A superset rests for the group's longest member.** The settled rule is that a round is paced
  by its slowest member; the app instead rested from whichever exercise happened to log the
  closing set, so a pair where one prescribed 180 s and the other 90 s rested 90 s whenever the
  latter closed the round — the group's own rest was never consulted. The same pair rested
  differently depending on the order it was logged in.

- **Tapping a lift in a past workout showed no trends for it.** The history screen passed
  the session's own row id to the trends screen, where a series exists only under the
  *library* exercise's id — a query that silently matches nothing, so the screen said
  "nothing recorded yet" for a lift just logged. Found by device verification against the
  published 1.4 upgrade, and covered by a test now (N17).
- **A cancelled export no longer reports a failed one.** The backup and restore
  callbacks run in the screen's coroutine scope, and both wrapped their file IO in
  `runCatching` — which catches `Throwable`, and so swallows the `CancellationException`
  raised when the user leaves the screen mid-write. Leaving during an export could say
  "couldn't write the backup file", and the coroutine refused to finish cancelling. Both
  now rethrow cancellation, the rule `dataResultOf` already stated and this pair
  contradicted.
- **An assisted set no longer loses its help in history.** The live workout showed a set
  on an assisted machine as `-20 kg`; the workout **detail** screen showed `0 kg`,
  because its ViewModel built the row without passing the column and the screen rendered
  the field's default. One tap into history and N15's feature read back wrong (B5).
- **A plan's target RPE reads as a lifter writes it.** The plan dialog passed the stored
  half-point count straight to the marker, so a plan saying 9.5 rendered as **"RPE 19"** —
  the confusion the halves representation exists to prevent. The three screens that show
  an RPE now share one formatter (B6).
- **One-tap "Log set" writes the set its button described.** The button reads
  *"Log set · -20 kg × 8"* and wrote a set with no assistance, because the value was in
  the suggestion and simply not passed; undoing a deleted set dropped it too (B7).
  Decided as D3: the button does what it says rather than saying less.

## [1.4] — 2026-09-30 (versionCode 5)


### Added

- **A plan can be pinned to a weekday, and home shows today's plan.** Several plans may
  share a day — training twice on a Friday is a thing people do — and each is listed with
  a Start action that goes straight into that plan's workout. A plan with no day is a
  plan you start by hand, which is why "Not scheduled" is a value rather than an empty
  state. **A living template, not a dated instance**: editing the Friday plan changes
  every future Friday until it is edited again, and what you performed is already the
  record. Dated instances would add a plan-per-date entity, generation and skipped-week
  handling to support a comparison the logged sets already allow.
- Migration **14→15** adds a nullable `templates.weekday`, stored by name like every
  other enum: an existing plan is unscheduled until it is given a day, which is what it
  already was, and reordering the enum could never reinterpret a row.
- **Assisted load.** An assisted pull-up can be written down at last: the load field
  takes a leading minus, so `-20` means the machine took 20 kg off. What is stored is a
  separate `assistanceGrams` — a magnitude, never a signed weight — which is what keeps
  an assisted set from reporting *negative* tonnage and corrupting every volume trend.
  Volume stays `weight × reps`, so an assisted set contributes what it should: nothing,
  exactly as bodyweight does. A plan can prescribe assistance too, and the prefill
  carries it into the workout.
- Migration **12→13** adds `set_entries.assistanceGrams`, defaulting to 0 so every set
  already recorded keeps meaning what it meant, and a nullable `targetAssistanceGrams`
  on a plan's sets.
- **Templates are plans now.** A template exercise carries planned sets — a role,
  a target weight, a target rep range, a target RPE and a note — plus the rest and cue
  the plan prescribes for that exercise. Starting a workout from a template prefills
  each set from the plan, so a written ramp is the numbers you actually see, and the
  plan's rest replaces the library's for that session. **Copy forward** duplicates an
  exercise's sets so a shape authored once becomes five sets in two taps, because
  thirty sets of four fields on a phone is where a plan stops being written down.
- **A set's role.** Planned and performed sets share one vocabulary — working, warm-up,
  **top set** (the one new value), drop and failure. The set editor gains a role
  selector, which is what finally makes `SetType`'s values reachable from the UI at
  all, and a logged set shows its role when it is not a plain working set. That is the
  half of this that is not about planning.
- Targets only: nothing verifies a plan against what was lifted, and a logged set is a
  separate row that is expected to differ. Declining to check the plan is what keeps
  this a plan rather than a compliance feature.
- Two migrations as the work landed: **10→11** adds `template_sets` and the plan's
  `restSeconds`/`techniqueNote`, **11→12** adds those two to `session_exercises` so the
  plan's rest and cue can reach a session. Both additive, nothing backfilled, and the
  backup codec carries all three or an export would drop them in silence.
- **A comment on the workout itself.** Finishing asks once, and skippably, how it
  went — the moment you remember why — and the comment is shown in the workout detail
  afterwards. No migration: `workout_sessions.notes` has been in the schema since v1,
  already carried by export and import, and never had a domain field or a UI. This is
  what it was reserved for.
- **How it felt can be recorded at any time, not only at Done.** Each exercise in a
  workout now carries a "How it felt" row — the same one the workout detail has — so
  the two ratings and N9's location are written down while the set is fresh rather
  than remembered afterwards. The Done prompt stays as the last chance rather than
  the only one, and the row is still there on a done exercise, so a rating given in
  passing is one tap from being corrected.
- **Joint pain says where.** The joint-pain rating gained an optional free-text box —
  "left shoulder", "right knee" — because a 4 means more a month later with a place
  attached to it. Stored per session exercise, editable wherever the rating is (the
  Done prompt and the workout detail), and shown beside it there. Adds migration
  9→10: one nullable column, no backfill, and carried by the export file like every
  other column — the hand-written codec drops anything the DTO does not name.
- **Trends: what the app collects, read back.** A screen over the last ten finished
  workouts showing RPE, muscle feel and joint pain — each as a line on the same fixed
  1–10 axis, with its latest value and its average. The details are the honest part: a
  metric recorded once is a number rather than a line, a workout that did not record
  one leaves a gap instead of being interpolated across, "not recorded" is never drawn
  as zero, and a workout with nine rated sets does not shout louder than one with a
  single rated set. No charting dependency — three series over ten points did not earn
  one, so the lines are drawn on a `Canvas`, which settles the approach P2.3 asked
  about for these series. Reached from the home overflow. No migration.

### Changed

- **RPE takes half steps.** `9.5` can be recorded, not just whole numbers. It is stored
  as *halves in an integer* (`19` is 9.5) for the same reason weights are whole grams:
  9.5 has no exact binary representation, and an RPE that compares as `9.499999` — or
  drifts when a trend averages it — is worse than one unit of arithmetic. The field
  accepts `9`, `9.5` and `9,5`, and refuses anything finer than a half (9.3 is not
  rounded to 9.5; that would be a claim about the set nobody made). The muscle-feel and
  joint-pain ratings are a different, whole-number scale and are unchanged.
- Migration **13→14** carries RPE across: the columns are renamed to `rpeHalves` and
  `targetRpeHalves` — re-using `rpe` for a different unit is how a silent corruption
  starts — and existing values are doubled, so an 8 recorded before this is still 8.0.
- The backup keeps reading the old whole-number field: renaming it would have dropped
  the RPE out of every earlier export on restore, without a word.
- **The 1–10 scales now say what their ends mean.** N8 shipped the muscle-feel and
  joint-pain ratings unlabelled on purpose — an anchor for what 3 or 7 means would be
  a claim the app has no basis for — and left labelling the ends as the obvious
  refinement. Both fields now read "1 = barely worked, 10 = fully worked" and
  "1 = none, 10 = severe" while the number is being picked, and only there: the
  workout detail keeps showing a bare "Muscle feel 8" rather than repeating the
  vocabulary on every past workout.

### Fixed

- **Export and import are back where you start.** They lived two overflow menus
  deep — home, then the exercise library N1 demoted to a reference screen — so the
  feature read as missing. They are in the home overflow now, beside Library,
  History and Templates, and the library's copy is gone: one path, not two.
- **Removing an exercise asks first.** It takes the exercise's sets with it and has
  no undo, so a mis-tap silently reshaped the workout. It now asks, and the dialog
  says what goes rather than posing a bare question.
- **An Undo can no longer act on something that is gone.** The deleted-set and Done
  snackbars share one host, so a deleted set's Undo could outlive its exercise: the
  row was gone, the button stayed, and tapping it failed the loggable-exercise guard
  without saying anything. An undo is now offered only while its subject is still in
  the session, and if one is tapped anyway the app says so instead of doing nothing.
- **A library read that fails is a message, not a crash.** `observeExercises` and
  `getExercise` were the last calls that could throw out of a flow and take a screen
  down. Both return the same `DataResult` the writes do, and the failure is shown
  where the list or the exercise would have been — "we could not read it" and "it is
  not there" are different sentences, and they used to be the same one.

## [1.3] — 2026-09-29 (versionCode 4)


### Added

- **Workout templates: a plan you build once and start in one tap.** Name a
  template, add exercises from the same picker the workout uses, and put them in the
  order you train them. Home's start action now offers the choice — *Start workout*
  or *Start from template* — and the overflow menu reaches the list for editing.
  Starting from a template opens the session with its exercises already in order,
  through the same append path a manual add-exercise takes, so the order is the same
  one the picker would have produced; resuming an open workout never seeds a second
  copy, and editing the session never touches the template. Templates ride along in
  the export/import file, soft-deleted ones included. Adds migration 8→9: two new
  sync-shaped tables, no backfill.
- **How it felt: muscle feel and joint pain.** Marking an exercise done asks, once
  and skippably, for two 1–10 ratings — how well the target muscle was worked, and
  any joint or connective-tissue discomfort — stored per session exercise so the
  same movement is measured differently on different days. They stay editable from
  the workout detail, and are deliberately unlabelled: labelling what 1 and 10 mean
  is the obvious first refinement rather than something to guess at now. Adds
  migration 7→8: two nullable columns, no backfill.
- **Done, so an exercise stops taking sets by accident.** A per-exercise **Done**
  action hides its **Log set** button and dims its sets, which then cannot be
  edited; it also stops any rest the exercise had running, and an **Undo** on the
  snackbar takes it back. A **Reopen** button restores editing, because accident
  protection must not become its own trap. The wording is deliberate — the
  workout-level action is already *Finish*, so this is *Done*, never *Finish* — and
  it is a session state, not a delete: the sets stay in history. Adds migration
  6→7: one nullable column, no backfill.
- **An RPE and a comment on every set.** The set editor gained an optional 1–10
  RPE and a free-text comment; the one-tap **Log set** path still writes neither,
  so logging stays fast. A set carrying either shows a small marker in the
  workout, and the workout detail shows the RPE and the comment's text. Adds
  migration 5→6: two nullable columns, no backfill. An RPE outside 1–10 blocks
  Save rather than being clamped, because a silent 11 → 10 would misstate the set.
- **A readiness note when a workout starts.** A new workout asks once, and
  skippably, what is not recovered today — "shoulders still sore from Monday",
  "slept badly, legs heavy". It is deliberately free text, stored on the session,
  reachable again from the workout header after the prompt is gone, and it rides
  through the workout detail and export. Adds migration 4→5: one nullable column,
  no backfill — and a resumed workout is not asked again.
- **Per-exercise rest and a technique cue.** An exercise now carries its own rest
  between sets — falling back to the 90 s default when unset — and a short
  "brace, sit back" cue shown under its name on the active workout screen. Both
  are editable from the exercise detail screen, which now edits **seeded**
  exercises too, not only custom ones; the seeder tops up with `INSERT OR IGNORE`
  and never updates an existing row, so an edit survives every future top-up.
  Adds migration 3→4: two nullable columns, no backfill.
- **Create a custom exercise from inside a workout.** The exercise picker gained
  **New exercise**, which asks for the name and immediately adds the entry to the
  session, so a movement the library does not have is not a dead end mid-workout.
  It is stored `isCustom = true` with a UUID id and an unspecified taxonomy
  (`Other`), and then appears in the library and in search like any other exercise.
- **Custom exercises are editable afterwards**, from the exercise detail screen:
  name, muscle, equipment and movement pattern. Filling those in is what makes the
  entry pickable in later workouts with real taxonomy rather than "Other".
- **Fifteen more exercises**, mostly competition and paused variants (competition and
  speed-day bench press, 3-second paused bench, paused squat, conventional deadlift,
  push press) plus accessory work (machine and assisted rows, dumbbell fly, incline
  dumbbell curl, dumbbell skullcrusher, rotator work, cable pushdown and cable curl).
  The seeder tops up with `INSERT OR IGNORE` on every open, so an existing install
  receives them without a migration.

### Changed

- **An exercise's subtitle no longer reads "Other · Other".** `Other` is the
  "not filled in yet" value a custom exercise is created with, so the library row
  and the workout's exercise header drop that part of the `Quads · Barbell` line —
  an unedited custom exercise shows its name alone.
- **The app opens on your workouts, not the exercise list.** Home is now a short
  list of recent workouts with **Start workout** (or **Resume**) and a link to the
  full history. The library became a screen you navigate to; it keeps search, and
  stays the picker inside a workout.

## [1.2] — 2026-09-29 (versionCode 3)


### Added

- Set rows announce that they are editable, so a screen reader no longer reads a
  row and leaves the user to guess (`onClickLabel`).

### Fixed

- A duration of an hour or more rendered differently in the rest timer than in the
  workout clock — `90:00` against `1:30:00`. Both now share one formatter and
  agree; the rest timer gains the hours field.

## [1.1] — 2026-09-29 (versionCode 2)


The upgrade-test build: installed over 1.0 without uninstalling, and confirmed to
keep the workout history. **No user-visible changes** — it exists to prove that the
permanent signing key accepts an upgrade rather than demanding an uninstall, which
would have cost the history.

## [1.0] — 2026-09-28 (versionCode 1)


First release. Sideloaded as a signed APK; there is no Play Store listing.

### Added

- Exercise library: 30 seeded movements with primary and secondary muscles,
  equipment and movement pattern. Search covers all of them on the display label
  *and* a locale-stable key, so it survives translation.
- Start a workout, log sets by reps and weight, with prefill from what you just did
  or from last time, tap to edit, and delete with undo.
- Rest timer with an in-app countdown and an optional background alert.
- Workout history: a chronological list grouped by month, plus a detail view with
  duration, volume and set count.
- Correct or delete a past set, and delete a whole workout behind a confirmation —
  so finishing a workout is no longer a one-way door.
- Resume affordance: the library button shows the running workout and its elapsed
  time instead of offering to start another.
- Export and import the whole database as JSON, through the Storage Access
  Framework. Import restores anything missing or deleted and never overwrites
  what is still there.

### Notes

- Local only. No `INTERNET` permission, no accounts, no ads, no analytics, and
  `allowBackup="false"` — the export file is the only way data leaves the device.
- Sets are `reps × weight`. Bodyweight work is reps at 0 kg; duration and distance
  are deliberately out of scope for this version.
