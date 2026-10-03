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

### Added

- **More than one program can be active at once, and a program's place is authored** (P3.12,
  amending P3.3; schema v21). `programs.isActive` is no longer exclusive, so a lifting block and
  a conditioning one are two schedules at once rather than one edited to hold both. Home's today
  plan is the **union** of every active program's slots for the day, the missed-day question
  walks every active program's pending occurrences, and adherence scores them together — the
  per-program breakdown is deliberately left to P3.14. `programs.position` gives the list and
  the union an order moved by hand (`programs` are reordered in the list), so which active
  program comes first is a decision rather than an accident of its name or when it was created.
  The upgrades are additive: the rows already there take their `rowid` as the position, and a
  program that was active stays active.
- **A program slot prescribes its own intensity, schema v22** (P3.8). A slot that names only a
  template is a schedule — the numbers come from the template, so two slots pointing at one
  template cannot train it differently. A slot now carries a prescription of its own: per exercise,
  sets × reps at a load, at a **percentage of the estimated 1RM**, or up to an RPE, with the rest
  and cue a plan already has. An empty prescription leaves the template's targets standing (N14)
  and every target stays nullable, because a target is not a claim.
- **A percentage is derived, never assumed.** The kilograms are N17's Epley estimate of the
  exercise's heaviest working set, rounded to the loadable step; an exercise with nothing estimable
  has **no number**, and history prefills the load rather than the app borrowing one. A weight the
  slot writes wins over its own percentage.
- **Progression is offered per slot, from that slot's own history** (N22, N33): a heavy Monday and a
  light Friday pointing at one template progress apart, because which slot a session settled is
  P3.3's occurrence matching. Nothing is written unless the lifter accepts it.
- **The slot travels as a start argument, not a stored fact.** Home passes the slot it is starting,
  which seeds the session's rest, cue and targets; the session still records only the template
  (P3.3), so provenance and matching are unchanged. The prescribed rows join the backup codec, with
  the round trip guarded by a field-for-field test.
- **A program's order is a run, and the app keeps the place** (P3.9). P3.3's order was real in the
  editor and nowhere else: home asked each slot for its weekday, so an order-only slot was never
  surfaced and "which one is next" had no answer on any screen. The run now follows the last slot
  **trained or consciously skipped**, derived from finished sessions and recorded skips rather than
  a stored cursor — so a day simply missed leaves it where it is (that is the missed-day question's
  job) and an edited program re-derives its place. A program with no weekdays runs A → B → C,
  because the cursor, not a day, attributes an order-only session to its slot. Home shows a
  **next up** row for a program with nothing scheduled today, and the editor marks the slot the run
  is at. Rotation is by what was done, never by load.
- **A deload is a week the lifter marks, schema v23** (P3.10). It is an event keyed by **program and
  week** — the shape of a skip — never a dated plan and never a week the app computes, because a
  deload is decided by how the block is going. **Exempt from the ratio, not from the calendar**: a
  deload week's scheduled occurrences count as neither done, skipped nor missed, so a deliberate
  back-off cannot read as a failure, while its sessions still mark their days and the missed-day
  question still asks. Nothing is scaled for you: what a deload week prescribes is what the slot
  prescribes (P3.8). Weeks are marked and unmarked on the Adherence screen, one chip per active
  program, and only a week that has started can be marked — the app has no forward view to hang a
  future week on. The new rows join the backup codec, guarded by a round trip.
- **A workout can be substituted for one occurrence, schema v24** (P3.11). "The rack is taken
  today, do the dumbbell version" cannot be answered by editing the program, which changes every
  week that references the template (N16, inherited by P3.3), so it is an **event keyed by slot and
  week** — the shape of a skip. The pick is made **at the point of starting**, from the slot's row
  in today's plan, and recorded before the session opens; no other week changes, which is the whole
  point, and the scheduled workout is offered to clear a mis-pick. A session started from the
  substitute **settles the slot's occurrence** — P3.3's matching extends to it, so the app stops
  asking about a day already trained — and adherence scores it against the slot: the day was
  scheduled and it was done, whatever it was done with. The new rows join the backup codec.

- **A skip can be corrected by hand** (P3.13). P3.3's prompt was the only thing that ever recorded
  a skip, and *Continue* silences a whole week in one tap, so a mis-tap was wrong forever and a
  week the app never asked about read as misses it did not earn. Tapping a **scheduled day** on the
  Adherence calendar now opens what that day scheduled — one row per occurrence, with its state —
  and each row can be marked skipped or unmarked, using the same `program_skips` row. Adding looks
  **backwards only** (a day passed over is behind you), removing is always allowed and soft-deletes,
  and the day returns to done, missed or pending by P3.5's same definitions. A finished session is
  not correctable — it is the record — and a deload week offers nothing to correct. The app never
  removes a skip itself: the correction is a second, explicit writer rather than a second opinion.
  No schema change.

- **The month is broken down, per day and per lift** (P3.14). A month's ratio says the program is at
  70%; it does not say that the squat day is what keeps being skipped. The same aggregate is now
  read **per slot** — each slot's own done / skipped / missed, accumulated in the same pass as the
  totals so the parts sum to the whole by construction — and **per lift**, over the join N14 already
  has: an exercise gets the occurrences of the slots whose template prescribes it, which answers "am
  I skipping this lift, or this day". A lift trained by two slots is counted in both on purpose.
  Counts, not a per-row percentage. No schema change.

- **A streak of scheduled work, with where it started** (P3.15). The obvious version — consecutive
  calendar days — is wrong here: it would break on every rest day, and by P3.5's rule an unscheduled
  day *is* rest. So the streak counts **scheduled occurrences, not days**, walking back from the
  most recent elapsed one while each was done. A skip and a miss both break it; an unscheduled day
  is invisible to it; a deload week neither extends nor breaks it, because it is not scored (P3.10);
  and an untrained today is stepped over rather than counted as a break. It is shown as a number
  **with its start** and never as a nudge. No schema change.

## [1.9] — 2026-10-03 (versionCode 10)

### Added

- **Adherence: how often the days a program scheduled actually happened, and a month of days
  trained** (P3.5). A destination of its own, reached from Statistics the way Measurements is —
  N34's five surfaces stand, because one screen does not earn a sixth tab. **One window**: the
  month the grid draws is the month the ratio covers, so the number and the grid cannot disagree
  about what they are counting, and the calendar navigates back through history and forward no
  further than the current month. Days after today are drawn but never scored.
- **Done, skipped and missed come from one set of definitions, and the unit is the occurrence,
  not the day.** *Done* is an occurrence settled by a session that was **finished**, matched by
  template and date in a Monday-start week taken in the session's own zone — P3.3's matching,
  fed this window's finished sessions. *Skipped* is a `program_skips` row for that slot and week.
  *Missed* is elapsed and scheduled and neither of the others. Two slots can fall on one Tuesday,
  so the ratio counts two while the calendar marks the one day. Adherence is deliberately
  **stricter than the prompt**: an abandoned start is a miss here and marks no trained day.
- **What was scheduled is the active program's weekday slots**, so an unscheduled day is rest
  rather than a miss and a weekday-less slot has no day to miss. A **trained day is a finished
  session's own day in the zone it was performed in** (N25, B45), so a workout done abroad marks
  the day it happened. With **no active program** the calendar still marks the days trained —
  that needs no schedule — but there is **no ratio**, because the pins home falls back to carry
  no skip record, which would make every deliberate rest on a pinned day a failure. A month with
  no elapsed scheduled day says so rather than reporting 0% or 100%.
- **No schema change.** Everything it reads shipped with P3.3 at v20 — `program_slots`,
  `program_skips` and `workout_sessions`' `templateId`, `finishedAt` and `zoneOffsetMinutes` —
  and the aggregate is the pure, JVM-tested `ProgramSchedule` the prompt already uses.

  Known limits, inherited rather than new: skips exist only from P3.3 onward and only for a week
  whose prompt was answered, so an older or unanswered week reads as misses; the schedule is the
  program as it is *now*, so adding a slot writes misses into weeks already past.

- **Programs: an ordered list of templates, each with a weekday, schema v20** (P3.3). A program
  is the container the N16 pins could not be on their own — a pin answers "what happens on a
  Tuesday", but nothing ordered the pins against each other, so "which one is next" had no
  answer. A **slot** is a template plus an optional weekday; a weekday-less slot is order-only
  and can never be missed. **Today's plan comes from the slot pinned to today**; with no program
  active, home falls back to the template pins it already read.
- **The missed day is asked about at the point of starting, not assumed and not asked at
  launch.** Starting a workout with a scheduled day this week neither trained nor skipped asks
  *"You missed Paused Squat on Tuesday. Do it now, or continue with Bench?"* — **Do it now**
  starts that slot, **Continue** records a skip for **every** pending occurrence that week, so
  two misses are not two interrogations. Only days already behind you count: a Friday slot on a
  Wednesday has not been missed yet.
- **A skip is an event keyed by slot and week** (`program_skips`, storing the Monday as an epoch
  day), never a boolean on the slot — a flag would need resetting and would be wrong the moment
  two weeks in a row were missed. Those rows are also what P3.5's adherence needs, because a
  standing weekday pin carries no history.
- **A session records the template it was started from** — one nullable `templateId` on
  `workout_sessions`, written only when the session is created from a template, so a resumed
  session never rewrites it. It amends N16 deliberately: N16 rejected copying a plan's *targets*
  onto a session because that freezes what the plan prescribes, while recording *where a session
  came from* freezes nothing. An occurrence is matched by template and date, in a Monday-start
  week taken in the session's own zone (N25).
- **The schedule, its slots and its skips ride in the backup**, and the new column joined the
  codec in the same change with a round-trip test behind it (N24's rule).

  Known limits, stated rather than discovered: an occurrence is resolved only when the workout
  was *started from* that template, so a plan added by hand does not settle it; a second session
  from the same template in one week is unmatched; and editing a template changes every week
  that references it, which is N16's living-template decision inherited rather than new.

### Changed

- **The dead code the statistics round left behind is gone, and the test-tag backlog has a gate**
  (B47, D4). Three unused `HOME_*` constants went, along with the assertions that asserted the
  absence of things already absent; the tag rule now fails a build that adds a tag without the
  test asserting on it, so the backlog can only shrink rather than silently drift.
- **A pre-release dispatch runs the instrumented job at the highest published API** (N30). The
  nightly keeps the fast `aosp_atd` image at API 34 — a run nobody is watching should not be slow
  for a reason nobody is testing that night — while the release dispatch, the run that can catch an
  API 35+ behaviour change, runs at **36**. The shipping API is 37, but Google publishes no
  `platforms;android-37` and no `default` or `aosp_atd` system image at any 37.x, so 36 is the
  highest that exists; the first dispatch found that out. The `aosp_atd` images stop at 34.

### Fixed

- **Metric targets survive a backup** (N39). A target is a setting rather than a row, and the
  export did not carry settings — so restoring a backup dropped every target in silence, which
  is the hand-written codec's trap wearing a different hat. Targets now ride in the file, and
  "delete everything" clears them; the rest of settings (rest, cue, keep-screen-on, the
  statistics range) stays a device preference and is deliberately still not exported (N21).

## [1.8] — 2026-10-02 (versionCode 9)

### Added

- **An index on `workout_sessions.startedAt`, schema v19** (B46). The statistics range filter
  and the record count both scan by start time and neither could use the `finishedAt` index —
  one filters `IS NOT NULL`, the other wants a range on a different column — so the record query
  read every set ever logged. The migration test asserts the row survives *and* that the index
  exists, since a query cannot report a missing index.
- **A target for any metric, drawn on the chart.** Typed in the unit the screen shows and stored
  canonically, **dotted where the average is dashed**, clearable, and unreadable input sets
  nothing rather than a zero. The axis widens to include it — a target you cannot see is not a
  target (N39).
- **The readings, under the chart** — date and value, newest first, collapsed until asked for,
  with **Average** and **Trend** rows. It is the chart's accessible counterpart, a moment that
  recorded nothing stays a gap rather than a zero, and the average is over readings that exist.
- **The chart's x-axis is time**, labelled in words, so two workouts a day apart and two a month
  apart no longer draw the same distance apart; the line still breaks at a missing reading
  (N37).
- **Bars for quantities, a line for a scale** — volume as bars, ratings and measurements as
  lines, so a 1–10 rating is not flattened by an axis anchored at zero.
- **An average, a least-squares trend and its slope** in the metric's own unit per week, fitted
  only to readings that exist, with the sign in the text because the same number is progress on
  bodyweight and a warning on joint pain. *(Drawn in a theme colour invisible on a device at
  first; neutral now.)*
- **A trailing mean over a configurable period**, seven readings by default: it counts readings
  rather than days (N40), emits from the first reading, and ignores unmeasured days.
- **A Statistics tab with one picker over every series**, replacing a workout-trends screen and a
  per-lift one. Every series is described in **one registry** — label, unit, formatter, group,
  line-or-bars, axis-at-zero and better-direction (N35) — and the three enums stay, referenced by
  it. The two screens it replaced are gone, with their ViewModels.
- **The range is remembered as what it means**, so a saved "last 7 days" is still the last seven
  days tomorrow; a custom From–To asks for its dates before it applies and reads them in the
  calendar's own zone.
- **The overview is three numbers** — workouts, volume and records. A record needs a query of its
  own because nothing stores that a set was one, and a count that has not been asked for shows a
  dash rather than a zero.
- **Five tabs** — Workouts · History · Statistics · Library · Settings — with the overflow menu
  reduced to the data actions. Templates stayed under Workouts, and body measurements are pushed
  from Statistics.
- **Each tab keeps its own place**, back from a tab root goes to Workouts, the bar disappears
  during a workout, a detail pushed inside a tab keeps it highlighted, and each item carries a
  label and a selected state for TalkBack.
- **Body measurements**, on their own screen: a dated entry with a weight and *optionally* body
  fat, muscle and seven tape sites, every one nullable — weight is the only field an entry cannot
  be without, and the save says so before it is tapped.
- **A day has one entry, and saving onto it edits it** (N32); an unmeasured tape site stays
  **blank** rather than carrying a value forward.
- **Measurements are charted** through the same chart as the training trends, sharing the chart
  rather than the axis, and travel in the backup file with a round-trip guard test; the schema is
  at **v18** (the index above takes it to 19).
- **A finished workout can become a plan.** **Save as plan** copies the exercises in order with
  their performed sets as targets, keeps the rest, technique note and superset grouping, and
  offers to open it. It copies neither the readiness note, the ratings nor the workout comment,
  which describe that day rather than the plan.

### Changed

- **The instrumented job runs the emulator with VM acceleration** (N30). The `kvm` group existed
  on the runner but the runner user was not in it, so the emulator fell back to software
  emulation — which is what all four of its failure signatures were. A udev rule grants the group
  and `-accel auto` takes it. *(Signatures and probe output: DECISIONS-EVIDENCE.md.)*
- **CI sets the emulator's flags explicitly and runs nightly and before a release rather than on
  every push** (N30). Reading `android-emulator-runner`'s own `action.yml` showed the flags it was
  told to try *are* the action's defaults, so that experiment could not have been the fix; setting
  them means a bump cannot change how the emulator boots unnoticed. The concurrency group now
  includes the event name, after a push cancelled an instrumented run twenty minutes in.
- **The instrumented suite runs green on the hosted runner.** An **Automated Test Device**
  (`aosp_atd`, API 34) boots in three and a half minutes where the previous image took sixteen and
  spent the run offline, and the count guard is satisfied — 175 declared, 175 executed — on an
  image with no Google services. The cost stands: the suite is tested against API 34 rather than
  the 37 the app ships against.
- **Progression is offered rather than applied** (N33). In two places the suggestion *was* the
  value one tap logged, so a lifter who progresses by hand had to undo the app's arithmetic. One
  tap now logs the plan's target, then what you just did, then **last time unchanged**, then the
  default, with the proposal beside it and a **Use it** action.

### Fixed

- **The fitted line is inside the axis.** The axis was built from the readings alone, so a
  fast-rising line was clamped flat along the top edge exactly where it rose fastest.
- **A tape or body-fat field that is not a real number is no longer stored.** `Math.round` of a
  `NaN * 10` is `0`, so typing "NaN" wrote a real 0.0% reading and "Infinity" became −0.1%;
  negatives were accepted too. The guard is the one `Weight.parseKilograms` has always used.
- **"All" counts records instead of showing a dash.** The tile was permanently empty on the one
  range where a lifetime count means most, because "all" has no window and the count bailed
  without one.
- **A long range no longer loses its older sessions.** The training series were capped at 500
  *sessions* and then date-filtered, so a "last year" range silently omitted everything before the
  newest 500.
- **The Statistics screen can change the lift again.** The picker was drawn only while *no* lift
  was selected, and every entry point arrives with one already chosen.
- **Loading and failure no longer read as "0 workouts, 0 kg".** The overview and the chart were
  drawn from the default state before anything had been read, so a first frame — and any failed
  read, which had no message — made a claim about data the screen did not have.
- **The exported schema for v18 is regenerated.** It listed fifteen entities for eight tables,
  every pre-measurements table twice, and `app/schemas` is the baseline the migration tests
  validate against.
- **A target that is not a real number can no longer blank the chart.** `MetricUnit.parse` was a
  bare `toDoubleOrNull()` accepting `NaN` and `Infinity`, which reached the axis and made every
  coordinate `NaN` — `NaN.coerceIn(0f, 1f)` is still `NaN`; parsing now requires a finite,
  non-negative number checked after the unit conversion too, and `axisBounds` discards non-finite
  input.
- **A rating is drawn on its own scale again.** The chart promised a fixed 1–10 axis and the
  constants for it were referenced nowhere, so RPE readings of 7.1 to 7.3 produced a 0.24-wide
  axis; the scale lives beside the metric now and widens past 1–10 only for real readings or
  targets.
- **A rising trend no longer prints as "+0".** The rate used the reading formatter, which
  truncates, so 0.2 reps a week read as "+0 reps per week" beside a line going up.
- **A target below zero is visible on a metric anchored at zero** — the axis extends below zero
  only when something actually sits there, so an ordinary bar chart is unchanged.
- **Discarding a workout no longer leaves a white screen.** Two identical `LaunchedEffect(closed)`
  blocks both called `popBackStack`, popping the back stack twice and leaving the navigation host
  nothing to render; Finish had it too, one tap later. A single `LeaveWhenClosed` composable
  replaces both. *Verified on the emulator both ways: with the fix reverted, Discard reproduces an
  empty accessibility tree and a screenshot 94.6% one flat colour.*

## [1.7] — 2026-10-02 (versionCode 8)

### Added

- **Repeat the last workout in one tap**, copying the **exercises and their order — not the
  loads**, which progression and the "last time" prefill already answer; an exercise deleted since
  is skipped. *(Its branch had never been entered: the route carried only `templateId`, so
  `repeatLast` was always false.)*
- **A rest is heard and felt, and a workout keeps the screen awake** (N27): a tone and a tick when
  a rest ends, and screen-on while a workout is open, each with a settings switch and both on by
  default. **Neither asks for a permission** — an in-process tone and view-level haptics, and a
  window flag rather than a wake lock, where `Vibrator` would have cost
  `android.permission.VIBRATE`.
- **A warm-up ramp in one tap**: four sets at 40%, 60%, 75% and 85% for five, three, two and one
  rep, from the weight a plan already names. Offered only where there is a weight to take a
  fraction of.

### Changed

- **The app is called Workout, and so is the repository.** It had drifted into four names — the
  launcher said *Workout Log*, the roadmap heading *Workout Tracker*, the release assets
  `workout-log`, and the repository `android-app`, which named the platform rather than the app.
  `applicationId` and the signing key are untouched, so it updates in place with no uninstall and
  no lost history, and past release titles and `workout-log-1.x.apk` assets keep the old name
  because that is what they shipped as.
- **A workout remembers the timezone it was performed in** (N25). Every screen had formatted UTC
  timestamps in the zone you are reading in, so a session logged in Tokyo showed the wrong hour
  and, after a late flight, the wrong day; the offset is captured when the session opens, and
  sessions recorded before this show the current zone.
- **Unused imports are gated.** detekt's `UnusedImports` is off by default and the compiler does
  not run with `-Werror`, so an import stranded by an implementation that landed elsewhere passed
  every gate; turning it on found fifty-one across twenty-one files.
- **Two names that lied, corrected.** The finish review's `WorkoutSummary` — also the history
  row's name — is `WorkoutReview`, and `SettingsModule` moved out of `DatabaseModule.kt`, which
  said database while binding a `SharedPreferences` repository; the move also showed the new
  import gate flagging the three imports it left behind.

### Removed

- **The background rest alert, and with it the app's last permission.** The alarm, its receiver,
  the notification, the ask-on-first-set prompt and `POST_NOTIFICATIONS` and
  `SCHEDULE_EXACT_ALARM` are gone, so the app declares **no permissions at all**. The rest timer
  is untouched: the end instant still lives on the session row and survives process death.

### Fixed

- **The alert's notification channel is deleted on launch** (B37, B40). Android keeps one until
  uninstall, so a phone that ran an older build still listed "Rest timer" — for an app that posts
  nothing and declares no permissions. One idempotent call, no permission needed.
- **A date can no longer be rendered without saying which zone it is in.** The three formatters
  defaulted their zone to the phone's, which is what let the wrong-day bug ship; the zone is
  required and every call site names it.
- **Two documents that had stopped describing the code**, including a KDoc claiming an open
  session is *refused* when it is *resumed*, and comments the removed alert left in
  `AndroidManifest.xml` — the file the no-permissions promise rests on — and `AGENTS.md`.
- **Three tests that could not fail now can.** Deleting the rest alert took their assertions with
  it, leaving two empty-bodied tests and one that ran a whole finish flow to assert nothing; they
  now assert that skipping a rest clears it, that adjusting one reaches the repository with the
  step the button sends, and that a comment written on the way to finishing lands on the session.
- **A warm-up ramp is written in front of the work**, not appended, which had a plan reading the
  working set before the four warm-ups that prepare for it.
- **The repeat action is offered only when it would copy something**, and is absent while a
  workout is open. It had asked whether history was non-empty while the copy also requires
  exercises still in the library, so a last workout whose exercises were all deleted offered a
  button indistinguishable from *Start workout* that opened an empty session.
- **Repeating a workout keeps its rest, note and superset grouping.** Only the exercises were
  copied, so a repeated superset arrived ungrouped and its round logic short-circuited.
- **Home shows a past workout's date in its own zone.** It was the one screen that dropped the
  argument and fell back to the phone's, so the same workout read as two different dates on two
  screens.
- **Finishing a workout leaves the screen again, and the undo offers are back.** Removing the
  alert took the navigation effect and the call that draws the undo offer with it; neither is
  visible in a compile, and detekt's unused-parameter and unused-private-member rules caught both.
- **Pairing a superset is one write.** It wrote row by row and carried on after a failure, so the
  process dying between writes could leave half a group, which the screen shows as a superset of
  one.
- **The superset tap is no longer drawn on the first exercise**, where there is nothing above to
  pair with and the write matched every ungrouped row, churning `updatedAt` for no change.
- **Assisted work adds a rep before it takes help off.** The progression rule tested its assisted
  branch before its rep ceiling, so "add a rep first" was unreachable for assisted work (N22); the
  rep to add now carries the assistance rather than zeroing it.

## [1.6] — 2026-10-01 (versionCode 7)

### Added

- **Supersets and circuits.** **Superset with above** groups an exercise with the one before it,
  the pair is labelled **A1/A2**, and the **rest belongs to the round rather than the set** (N24)
  — a rest starts only once nothing else in the group is behind. Marking a member done counts as
  caught up, leaving takes the whole group apart, and a plan can prescribe one. Schema **v16**,
  with the grouping carried in the backup file; a `MigrationTestHelper` case seeds rows at v15 and
  asserts the amended `MIGRATION_15_16` keeps them.
- **Personal records, and noticing one when it happens.** A record is a **rep max** — the heaviest
  working set at each rep count — announced above the work with **what it beat**; the first time
  at a rep count says that instead. Warm-ups became excludable with N14's roles, which is why this
  could not have been built honestly before.
- **The app stops handing back the same number.** The prefill proposes the next step by **double
  progression** and says why — "One more rep than last time", "A step heavier", "Less help than
  last time" (N22). **It suggests; it never writes:** no plan or stored set changes, and with no
  plan there is no ceiling, so it proposes one more rep rather than inventing a weight.
- **A settings screen, and the default rest is editable** (N21). The app-wide rest was a hardcoded
  90 seconds; settings now holds a **Default rest** from a bounded set, an exercise's or plan's own
  rest still wins, and a change reaches a workout already open.
- **A review when a workout finishes, with plan versus actual** (N20). Finish used to be a dead
  end; the workout now ends with sets, reps and volume, the notes, each exercise's feel and joint
  pain, and — when it came from a plan — **the plan next to what was actually lifted**. It also
  says what was *not* done: a skipped prescribed exercise, and an exercise added mid-workout
  labelled as not in the plan.
- **Choosing a set's role where the set is logged** (N19), beside **Log set**, so three warm-ups
  no longer cost three log-then-edit round trips; the choice clears itself once the set is written.
- **Start over, with a way out first.** Deletes are soft, an import can only add, and the only
  true wipe was `adb shell pm clear`, which is not a phone feature. **Delete everything** removes
  every workout, set, template, planned set, custom exercise, rating and comment, plus the crash
  logs — the seeded library stays. Export is offered in the dialog, the
  confirmation is **typed** (`DELETE`), and it is **hard**, not soft: every other delete here sets
  `deletedAt`, and a soft-deleted row would survive an export taken afterwards.

### Changed

- **`personalRecords` and `setSupersetGroup` now run against a real database**, not hand-written
  repositories, which could not show that a heavier warm-up must not set a record or that a
  superset group survives its projection — the session projection had already shipped once
  without selecting the column.
- **A plan that names reps and no load now says why it proposes what it does** (N22): it explains
  a *load* only, since "one more rep than last time" beside a set the plan sized would be a
  sentence about the wrong number.
- **A route that is declared but never registered now fails the test suite.** Two bugs shipped
  from that gap — a route missing `@Serializable`, and one with no `composable<...>` registration,
  which made the settings screen crash on its first run; the route list is read out of `Routes.kt`
  and compared against what `AppNavHost.kt` registers, rather than named by hand.
- **A guard against the backup codec quietly losing a column** (N24's preparation). The
  hand-written codec had already dropped an unnamed column three times, each found by hand after
  shipping; a round trip with every field set now fails where the next column is added.

## [1.5] — 2026-09-30 (versionCode 6)

### Added

- **Trends for one exercise** — over the last ten finished sessions that recorded the lift:
  heaviest working set, estimated one-rep-max, volume, total reps, and that exercise's own average
  RPE, muscle feel and joint pain. **Warm-up sets are excluded from the load series** (N17), and
  nothing new is stored; the chart is shared with the overview.
- **Assisted work says which way is forward**: that series is the session's least assistance,
  labelled "less is more", rather than a climb that reads as improvement.
- **Tests for the gaps the review found** (B9–B11), the useful one being `ALL_MIGRATIONS` asserted
  against the runtime schema version — a migration written, tested and forgotten in the array had
  left the suite green while crashing every install with data.

### Changed

- **Tests assert with Truth, and Flow sequences with Turbine**, migrating as files are touched
  (DECISIONS.md).
- **The configuration cache is on for local builds too.** CI already passed
  `--configuration-cache` and `gradle.properties` now sets it, so an ordinary `./gradlew` gets the
  same reuse; the wrapper's `retries` went from 0 to 3, with the distribution still
  checksum-validated.
- **CI: one Gradle invocation where there were five**, because each separate call paid its own
  configuration; and the emulator job now fails unless the executed test count equals the count of
  `@Test` annotations in `app/src/androidTest`, naming the classes that did not report (B8, B12).
- **The review's rule violations are settled.** `HALVES_PER_POINT` is one constant; the two
  `DayOfWeek` formatters, the two `SetType` pickers and the three `CenteredMessage` copies are one
  component each (`SetRoleSelector` among them); the duplicate `ActiveWorkoutInfo` went with B13's
  dead state; and four files are named for what they hold (`DataErrorMessage.kt`, `NoteDialogs.kt`,
  `ExercisePickerRoute.kt`, and a `RestAlarmReceiver.kt` of its own, since Android instantiates it
  by name).

### Removed

- **Deleted what moved and left its shape behind** (B13). The exercise library's ViewModel still
  carried the whole "workout in progress" apparatus — a state field, two flows, a per-second
  ticker, and the `TimeSource` and `WorkoutRepository` dependencies that fed it — months after the
  resume button moved to home. With it went `@ApplicationScope`, the module binding a scope
  nothing injects, and four declarations nothing called (`TemplateDao.findTemplateSets`,
  `WorkoutSession.isActive`, `WorkoutSummary.hasVolume`, `PreviousPerformance.isEmpty`). Per D2,
  `Weight.step`, `DataResult.map` and `successUnit` went too.

### Fixed

- **The finish review's counts now agree with its reps.** "Prescribed 2×3" meant two sets, one a
  warm-up, totalling three reps; both counts and sums exclude warm-ups now, and a plan naming no
  reps no longer reads "prescribed 2×0".
- **The review tells two rows of the same exercise apart**, matching by exercise id rather than
  display name, which had collapsed a movement performed twice and made the totals contradict the
  rows.
- **A warm-up can no longer raise a personal best** — the record rule never saw the set's role,
  and now takes it, so a caller cannot forget it.
- **A record says what it actually beat.** The banner read history alone, so a bar set earlier in
  the same session was invisible: 20 kg then 22.5 kg at eight reps announced "the first time at
  this rep count" while claiming a record over that very 20 kg.
- **Editing a logged set no longer wipes its role or its assistance.** The editor was never told
  either, so saving a one-rep correction over a `-20 kg` assisted warm-up destroyed both;
  history now records the role.
- **A superset rests for the group's longest member** (N24), not for whichever exercise happened
  to close the round, which had the same pair resting differently depending on the order it was
  logged in.
- **Tapping a lift in a past workout showed no trends for it** (N17): the history screen passed
  the session's own row id where a series exists under the *library* exercise's id. Found by
  device verification against the published 1.4 upgrade.
- **A cancelled export no longer reports a failed one.** Both callbacks wrapped their file IO in
  `runCatching`, which swallows the `CancellationException` raised when the user leaves the screen
  mid-write; both now rethrow cancellation, the rule `dataResultOf` already stated.
- **An assisted set no longer loses its help in history** (B5) — the detail built the row without
  passing the column — and **a plan's target RPE reads as a lifter writes it** (B6), where 9.5
  rendered as "RPE 19".
- **One-tap "Log set" writes the set its button described** (B7, D3): the button read `-20 kg × 8`
  and wrote a set with no assistance, because the value was in the suggestion and not passed.

## [1.4] — 2026-09-30 (versionCode 5)

### Added

- **A plan can be pinned to a weekday, and home shows today's plan.** Several plans may share a
  day, and each is listed with a Start action; a plan with no day is one you start by hand. It is
  **a living template, not a dated instance** (N16). Migration **14→15** adds a nullable
  `templates.weekday`, stored by name.
- **Assisted load.** `-20` means the machine took 20 kg off, stored as a separate
  `assistanceGrams` — **a magnitude, never a signed weight**, so an assisted set contributes
  nothing to volume rather than subtracting (N15); a plan can prescribe it. Migration **12→13**
  adds `set_entries.assistanceGrams`, defaulting to 0 so every recorded set keeps its meaning, and
  a nullable `targetAssistanceGrams` on a plan's sets.
- **Templates are plans now.** A template exercise carries planned sets — a role, a target weight,
  a target rep range, a target RPE and a note — plus the rest and cue the plan prescribes, and
  starting a workout prefills each set from them; **Copy forward** duplicates an exercise's sets.
  **A set's role** gives planned and performed sets one vocabulary — working, warm-up, **top set**,
  drop and failure (N14). Migrations **10→11** add `template_sets` and the plan's
  `restSeconds`/`techniqueNote`, and **11→12** adds those to `session_exercises`.
- **Targets only:** nothing verifies a plan against what was lifted, and a logged set is a
  separate row expected to differ.
- **A comment on the workout itself.** Finishing asks once, and skippably, how it went; it needed
  no migration, because `workout_sessions.notes` has been in the schema since v1 and never had a
  UI.
- **How it felt can be recorded at any time**, per exercise, rather than only at Done, so the two
  ratings and the pain location are written down while the set is fresh. The Done prompt stays as
  the last chance rather than the only one.
- **Joint pain says where** — an optional free-text box beside the rating. Migration **9→10**.
- **Trends: what the app collects, read back.** RPE, muscle feel and joint pain over the last ten
  finished workouts, each on a fixed 1–10 axis with its latest value and average. A metric
  recorded once is a number rather than a line, a workout that did not record one leaves a gap,
  "not recorded" is never zero, and a workout with nine rated sets does not shout louder than one
  with a single rated set. No charting dependency: the lines are drawn on a `Canvas`.

### Changed

- **RPE takes half steps**, stored as *halves in an integer* (`19` is 9.5) (N6). The field accepts
  `9`, `9.5` and `9,5` and refuses anything finer than a half rather than rounding, while the
  muscle-feel and joint-pain ratings stay a whole-number scale. Migration **13→14** renames the
  columns to `rpeHalves` and `targetRpeHalves` and doubles existing values; the backup keeps
  reading the old whole-number field, since renaming it would have dropped the RPE out of every
  earlier export on restore.
- **The 1–10 scales now say what their ends mean** — "1 = barely worked, 10 = fully worked",
  "1 = none, 10 = severe" — while the number is being picked, and only there: the workout detail
  keeps showing a bare "Muscle feel 8" rather than repeating the vocabulary on every past workout.

### Fixed

- **Export and import are back where you start**, in the home overflow rather than two menus deep,
  and the library's copy is gone — one path, not two.
- **Removing an exercise asks first**, because it takes the exercise's sets with it and has no
  undo; the dialog says what goes rather than posing a bare question.
- **An Undo can no longer act on something that is gone.** The deleted-set and Done snackbars share
  one host, so a deleted set's Undo could outlive its exercise and fail the loggable-exercise
  guard without saying anything; it is offered only while its subject is in the session.
- **A library read that fails is a message, not a crash.** `observeExercises` and `getExercise`
  were the last calls that could throw out of a flow; both return the same `DataResult` the writes
  do, and "we could not read it" is no longer the same sentence as "it is not there".

## [1.3] — 2026-09-29 (versionCode 4)

### Added

- **Workout templates**, built once and started in one tap, through the same append path a manual
  add takes; resuming an open workout never seeds a second copy, and editing the session never
  touches the template. They ride along in the export file, soft-deleted ones included. Migration
  **8→9**.
- **How it felt: muscle feel and joint pain**, asked once and skippably per exercise, stored per
  session exercise and editable from the detail; deliberately unlabelled at first. Migration
  **7→8**.
- **Done, so an exercise stops taking sets by accident**: it hides **Log set**, dims and locks the
  sets, stops any running rest, and has an **Undo** and a **Reopen**, because accident protection
  must not become its own trap. The workout-level action is *Finish*, so this is *Done*, and it is
  a session state rather than a delete. Migration **6→7**.
- **An RPE and a comment on every set**, neither written by the one-tap path, so logging stays
  fast; an RPE outside 1–10 blocks Save rather than being clamped. Migration **5→6**.
- **A readiness note when a workout starts** — free text on the session, reachable from the
  workout header; a resumed workout is not asked again. Migration **4→5**.
- **Per-exercise rest and a technique cue**, falling back to the 90 s default, editable from the
  exercise detail — which now edits seeded exercises too, since the seeder tops up with
  `INSERT OR IGNORE` and never updates a row. Migration **3→4**.
- **Create a custom exercise from inside a workout**, stored `isCustom = true` with a UUID id and
  an unspecified taxonomy, and **editable afterwards** so it becomes pickable with real taxonomy.
- **Fifteen more exercises**, mostly competition, paused and accessory variants, topped up on
  every open without a migration.

### Changed

- **An exercise's subtitle no longer reads "Other · Other"**, since `Other` is the "not filled in
  yet" value a custom exercise is created with — the `Quads · Barbell` line drops it, so an
  unedited custom exercise shows its name alone.
- **The app opens on your workouts, not the exercise list.** Home is recent workouts with **Start
  workout** (or **Resume**) and a link to history; the library became a screen you navigate to,
  and stays the picker inside a workout.

## [1.2] — 2026-09-29 (versionCode 3)

### Added

- Set rows announce that they are editable, so a screen reader no longer reads a row and leaves
  the user to guess (`onClickLabel`).

### Fixed

- A duration of an hour or more rendered differently in the rest timer than in the workout clock —
  `90:00` against `1:30:00`. Both share one formatter now, and the rest timer gained the hours
  field.

## [1.1] — 2026-09-29 (versionCode 2)

The upgrade-test build: installed over 1.0 without uninstalling, and confirmed to keep the workout
history. **No user-visible changes** — it exists to prove the permanent signing key accepts an
upgrade rather than demanding an uninstall, which would have cost the history.

## [1.0] — 2026-09-28 (versionCode 1)

First release. Sideloaded as a signed APK; there is no Play Store listing.

### Added

- Exercise library: 30 seeded movements with primary and secondary muscles, equipment and movement
  pattern; search covers the display label *and* a locale-stable key, so it survives translation.
- Start a workout, log sets by reps and weight with prefill, tap to edit, and delete with undo.
- Rest timer with an in-app countdown and an optional background alert.
- Workout history: a chronological list grouped by month, plus a detail view with duration, volume
  and set count.
- Correct or delete a past set, and delete a whole workout behind a confirmation — so finishing a
  workout is no longer a one-way door.
- Resume affordance: the library button shows the running workout and its elapsed time.
- Export and import the whole database as JSON, through the Storage Access Framework, restoring
  anything missing or deleted and never overwriting what is still there.

### Notes

- Local only, as v1.0 shipped it: no `INTERNET` permission, no accounts, no ads, no analytics, and
  `allowBackup="false"` — the export file is the only way data leaves the device.
- Sets are `reps × weight`. Bodyweight work is reps at 0 kg; duration and distance are out of scope
  for this version.
