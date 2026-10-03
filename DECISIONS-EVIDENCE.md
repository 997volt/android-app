# Decision evidence

The argument behind the rules in [DECISIONS.md](DECISIONS.md): measurements, observed
history, verbatim output, and the longer case against each rejected alternative. One
heading per feature id, matching the `(evidence)` links in that file.

**This is not the rules.** Where the two differ, [DECISIONS.md](DECISIONS.md) wins. Nothing
here needs reading to follow the rules — it exists so the rules can stay short without the
reasoning behind them being lost, and so a settled question is reopened on evidence rather
than on memory.

## D1

Test forking. Measured on the 4-core CI runner, same branch, same tasks, only
`maxParallelForks` differing: **424 s without forking, 467 s with four forks** — slower,
and summing roughly six times the CPU. Measured locally the same way: 63 s vs 65 s.

Most of the task is compilation and Robolectric's resource merging, which forking cannot
overlap, so there is no wall-clock win to buy. The trigger to revisit is a runner that
measures faster, not a feeling that it should.

## D2

"No dead weight" is strict about APIs that exist to be tested, and lenient about tests'
instruments.

`Weight.step`, `DataResult.map` and `successUnit` had no production caller and no test that
used them as anything but their own subject: they are gone, and the tests that existed only
to exercise them went with them.

A DAO or store method that a test calls to *arrange or read* the subject under test is a
caller — `ExerciseDao.insertAll`, `softDelete` and `CrashLogStore.latest` stay, because
deleting them would mean testing through a different door than the app uses. The line is
what the API is for, not where it is called from.

The hard case was left open on purpose (B47) and is settled the same way: four members of the
statistics round were read only by the tests that assert on them. An accessor that exists for
its own assertion is not a test arranging or reading another subject — it is the API that
exists to be tested — so they went, with the tests that only exercised them. Where such a test
asserted a real behaviour *through* the accessor, it was rewritten to assert the behaviour
directly (the trend line's slope rather than its reading count), because the coverage was never
the accessor's to begin with.

## D4

A test tag arrives with the test that asserts on it.

A backlog of tags is applied in production today and asserted by nothing, which is drift in
the one namespace that exists to stop tests reading English. They are kept rather than
deleted in a sweep, because each marks a real control and the change that next touches it
pays it off by writing the test; the rule that stops the list growing is one-way, so a new
tag without its test is a finding. Tags that map to no control at all are still deleted on
sight. **No count is recorded here**: it is a hand-maintained fact, and those go stale.

## N15

Assistance is a magnitude in its own column, never a signed weight.

`weightGrams` stays non-negative and volume stays `weight * reps`, so an assisted set
contributes nothing rather than subtracting — a sign would have quietly corrupted every
volume trend. The editor shows the load as one signed number (`-20`), and the steppers step
*that* number, so pressing + on an assisted set reduces the help; the weight column itself
still cannot go below zero.

`Load` cannot be a `@JvmInline value class` for the same reason. A value class wraps exactly
one property, so the only way to inline `Load` is to store the load as one signed number —
which is precisely the signed weight above, and would make an assisted set subtract from
volume. The inlining would also buy nothing at the call sites that exist: `parseLoad`
returns `Load?` and the editor holds it in a nullable field, so the value is boxed anyway,
and the project's own rule is that a performance claim comes with a measurement.

## N16

A scheduled plan is a living template, not a dated instance. There is one Friday plan:
editing its sets changes every future Friday until it is edited again.

What was *performed* is the record and is already kept, so dated instances would add a
plan-per-date entity, plan generation and skipped-week handling to support a comparison the
logged sets already allow. Several plans may share a day, and a plan with no day is simply
one you start by hand.

The same instinct governs the companion rule: nothing links a session to the plan it came
from beyond the route that started it, so editing a plan changes what the next workout
prefills. Writing the targets onto the session instead would freeze them and make "living"
false.

## N17

A per-exercise trend plots the number that moves, and says which way is forward.

Load series come from *working* sets only: a warm-up must not become the "heaviest set" on a
chart, which is why N14's roles had to exist first. A set with no added weight is not a load
at all — bodyweight and assisted work carry reps and volume (both zero) exactly as N15
decided they carry. For an assisted exercise the series is the *least* assistance of the
session, labelled "less is more", because on a machine a climbing line means the machine is
doing more of the work.

The one-rep-max estimate is Epley's, taken from the heaviest working set, refused beyond
twelve reps (where it extrapolates rather than calculates) and rounded to the nearest
half-kilo, because an estimate is not precise to the gram.

## N19

The role for the next set is armed at the button, and clears itself.

The alternative — a pending role per exercise in the screen's state — was built first and
then removed: it made transient UI state part of a database-driven flow, needed a `combine`
input and a field on every row, and pushed `ActiveWorkoutViewModel` past the function
ceiling detekt enforces, which the config says means a split, not another +1.

Holding it in the composable that draws the button is smaller, survives the state rebuilds,
and puts the "one set per role choice" rule where the choice is made. The ViewModel takes
the role as an argument to `onLogSet`, so nothing there has to remember to clear it.

## N21

Settings live in `SharedPreferences`, not DataStore.

What is stored is a handful of integers, booleans and two small maps owned by one process,
which is the case `SharedPreferences` is still the right tool for — and DataStore would be a new
dependency for it. The move is warranted when a setting needs a schema or a migration. The goal
map is the closest thing to one and is still a line per metric, so the boundary has not been
reached: a hand-written `id=value` line is smaller than a dependency.

Writes are **committed**, not applied, because the screen reports a real result: a
fire-and-forget write would let it say "saved" about something that never reached disk.

Not all of it is a preference, which is the part this entry got wrong. A metric target (N39) is
something the user *authored*, so it rides in the backup file and "delete everything" clears it,
while the rest, the cue, keep-screen-on and the statistics range stay device preferences and are
deliberately not exported. Calling a target a device preference was losing it on every restore,
in silence — the codec trap (N24) wearing a different hat, and invisible for the same reason.

The default rest is a bounded choice: a typed zero would be no rest at all and a typed negative
is not a setting, so the repository refuses anything outside 5–3600 seconds as `DataError.Invalid`.
(This used to argue from the value becoming an alarm; the alert is gone with N26, and the bounds
are still right — they are about what a rest means.)

## N22

Double progression. Keep the load and add a rep until the plan's rep ceiling is reached,
then add the smallest loadable step (2.5 kg, a pair of 1.25s) and start the range again.

The alternatives were rejected deliberately: a percentage-based rule needs a true one-rep
max this app estimates rather than measures, and a linear weekly add ignores missed
sessions. Two consequences are worth stating: assisted work inverts the direction — the
machine doing less is the progress, so the step comes off the assistance — and with no plan
there is no ceiling, so the app proposes one more rep and stops there, because adding weight
without a target would be the app programming rather than the lifter.

A suggestion carries its reason, and null means "nothing to explain". The number reaches the
screen as a value *with* the rule that produced it, because a number the app chose is an
instruction unless it says why — but only the three progression reasons draw a line. A
prefilled set that is just the plan, or just a repeat of the set logged moments ago, has
nothing to explain, and a line there would train the user to ignore the line that matters.

Nothing here changes a plan or a stored set. A suggestion the app applied silently would be
a programme decision taken without the person training, and this app is a log, not a coach.
Warm-ups are excluded from progression for the reason they are excluded from a load series
and from a plan comparison (N17, N20): a warm-up is not the work a target is measured
against.

## N24 codec

A new column is added to the backup codec in the same change, and the codec is guarded by a
round trip.

The codec is hand-written and lists every field by name, so a column it does not know about
is not an error — the export simply does not contain it, and the loss is invisible until
someone restores a backup that is quietly missing data. It has happened three times: a set's
location (N9), a set's assistance (N15), a template's weekday (N16). That makes it a trap
rather than bad luck.

`BackupCodecRoundTripTest` therefore asserts that every field of every backed-up entity
survives entity → DTO → entity, so the next column fails the suite where it is introduced.
The one field deliberately excluded is a session's `restEndsAt`, because a rest countdown is
device-and-moment state rather than training history — and that line now says so, since the
guard could not tell it apart from a mistake.

## B16

A migration is amended only while its version has never shipped, and the cost is real.

`MIGRATION_15_16` gained a second column for the plan side
(`template_exercises.supersetGroup`) after N24 had already run the first version on
development devices. That is legal only because nothing has ever been released with schema
16, and it is not free: **Room refuses to open a database whose stored identity hash does
not match**, so any device that ran the earlier 15→16 fails with *"Room cannot verify the
data integrity… you have changed schema but forgot to update the version number"* until its
app data is cleared. Confirmed on the emulator rather than assumed.

The rule that follows is that amending is for a version no user has, and a device that
already ran it must be wiped. The alternative, a 16→17 migration, is correct but adds a
version step to prove for data that only exists on developer machines.

## N27

The rest cue stays inside the permission-free envelope.

Removing the background alert left the app declaring nothing, and the obvious way to make a
rest audible and felt would spend that: `Vibrator` needs `android.permission.VIBRATE`. So
the cue is view-level haptics (`performHapticFeedback`, no permission) plus a tone played
in-process, and keep-screen-on is a window flag rather than a wake lock.

This is recorded because the constraint is invisible from the feature's description: "sound
and haptics" reads like a platform call, and the platform call that does it costs the
property N26 was for. If a stronger cue is ever wanted, the trade is a permission and it
should be taken deliberately rather than as a side effect.

## B37 and B40

A removed feature still cleans up after itself.

The rest alert is gone and its notification channel is not: Android keeps one across
updates until uninstall, so a device that ran a pre-N26 build still lists "Rest timer" in
its notification settings. Deleting it costs one idempotent call that needs **no
permission**, so the app does it on every launch and the channel goes.

The alternative — documenting it as accepted — was rejected because it leaves a trace of a
deleted feature on a user's device to save four lines, and because "we removed it" should
mean the device looks like it too. The channel id survives in `AndroidApp` for exactly one
purpose, and its comment says so.

## N30 CI

CI runs nightly and before a release, not on every push.

The emulator is the one piece of infrastructure in this project that has failed without a
test running, so a per-push run would mostly report on the runner, and a red pipeline that
says nothing about the change trains people to ignore it. The per-change guard is the local
gate set in AGENTS.md — the same tasks the build job runs — and the nightly run is what
catches the drift a local run cannot see. A release dispatches the pipeline first
([RELEASING.md](RELEASING.md) step 5), because a release is the wrong time to discover the
gate set has stopped working.

The concurrency group carries the event name, so a release dispatch and the nightly run
cannot cancel each other. That is not hypothetical: a push once cancelled an instrumented
run twenty minutes in, and the cancelled job's summary was indistinguishable from an
infrastructure failure.

## N30 emulator

The emulator runs with VM acceleration, and that is what the flakiness was.

The job had failed on infrastructure four times with four signatures — a corrupt image
download, a boot that outran its timeout, an adb connection that died after a sixteen-minute
boot, and a device that booted and then could not answer `getprop`, so AGP skipped it as
"Unknown API Level" and no test ran.

The emulator's own probe named the cause every time and it was read as background noise:
*"This user doesn't have permissions to use KVM (/dev/kvm). The KVM line in /etc/group is:
[kvm:x:993:]"*. The device is on the runner and the group exists; the runner user is simply
not in it, so the emulator fell back to software emulation — "Disabling Linux hardware
acceleration" — and every one of those signatures is what a starved emulator does.

A udev rule grants the group access and `-accel auto` lets the emulator take it. The levers
tried before this one were all about tolerating a slow emulator rather than checking why it
was slow: a longer boot timeout, a lighter image, a lower API level.

The API level was still 34 rather than the 37 the app ships against, and that revisit has now
been taken, by splitting the trade instead of picking a side. The nightly keeps `aosp_atd` at 34
— the image exists because a starved emulator needed a light one, and that reasoning still holds
for a run nobody is watching — while the pre-release `workflow_dispatch` runs at the shipping API
on the default image, which is the run that can catch an API 35+ behaviour change. The one thing
not verified here is that a system image is published for the shipping API: the first dispatch is
the experiment, and if none exists the highest published API is the number to use, with the reason
recorded here rather than rediscovered.

## N32

A measurement is one entry per day, edited rather than added to. The roadmap left this to
implementation and named the two candidates. Several per day was rejected because it makes
the chart noisy and, worse, makes "what did I weigh today" a question with more than one
answer — and the second reading of a day is nearly always a correction of the first rather
than a second measurement.

The day is the local day it was taken, converted from the timestamp at the edge, which is
the same rule N25 established for a session's time: a measurement belongs to the day it
happened where it happened.

An unmeasured tape site stays blank rather than carrying the previous value forward: a
carried number is indistinguishable from a measurement and would draw a flat line through a
site nobody measured that day, which is an invented fact rather than a missing one.

## N33

One tap logs what happened; the app's idea of what should happen is an offer.

The suggestion and the prefill were one value before, which is what made a suggestion into a
decision — a proposal that *is* the prefill is committed by the next tap whether or not
anyone agreed to it. They are now different fields: the prefill is the plan's target, what
you just did, or last time unchanged, while the progression proposal is shown with its
reason and applied only when accepted.

The rule is global, not program-only: how a workout was started says nothing about whether
its lifter progresses by hand.

## N38

Test tags are exposed as resource ids. The app opts in with `testTagsAsResourceId`, so a
device-side tool can address a control by *identity* rather than by coordinates: a tap then
either lands on the control or fails, instead of silently hitting its neighbour and
producing a screen that reads as a bug in the app. Two rounds were spent guessing offsets
before this was found — a Compose app is otherwise an opaque tree of anonymous Views to
everything outside its process. The cost is that the tags become visible to accessibility
tooling, which is what an annotation meant for tooling is for.

Noted as a limit: `android_ui_tree` cannot see popup windows, so items inside a
`DropdownMenu` still have to be tapped by coordinates read from a screenshot.

## B45

A range is a window in the current zone; a session's date is where it happened. These are
two different questions and the app answers them differently on purpose.

A *window* — "the last 7 days", a custom range — is one interval, and an interval has to be
measured somewhere; the device's current zone is the only answer that makes "today" mean
today for the person reading the screen. A *session's date* is a fact about the past, so it
is shown in the zone the session was performed in, which is what N25 stores.

The consequence is real and accepted: after a long flight a workout near midnight can be
listed under one date and fall outside a window that appears to include it. The alternative
— a window per session — is not a window, and one screen cannot be drawn against several
intervals at once. The reason this is written down rather than patched is that the two
readings are both correct about different things, so the next person to notice the mismatch
should find a decision here instead of a bug report.

The companion rule: `ZoneId.systemDefault()` is a function for a reason. A `val` in a
companion object freezes the zone at class load, so a process that outlives a timezone
change keeps computing "today" in a zone the device no longer has. Every screen reads it at
the point of use, and the statistics view model now does too.

## P3.3

Programs. The shape was decided before the code, and the interesting part is what each
decision rules out.

**A skip is an event, not a flag.** The obvious model is a `skipped` boolean on the slot. It
cannot work: the same weekday recurs every week, so the flag would have to be reset, and it
would be wrong the moment two weeks in a row were missed. A row per (slot, week) needs no
resetting and answers "what did I miss in the week of the 5th" — which is exactly the query
P3.5's adherence is made of. The week's Monday is stored as an epoch day rather than a
timestamp, because "which week" is a calendar question and a millisecond would drag a
timezone into it.

**Provenance, not prescription, on the session.** An occurrence is settled by the session
started from its template, so the session has to say which template that was. N16 explicitly
rejected copying a plan's *targets* onto a session — that freezes what the plan prescribes and
makes "living template" false. Copying the template's *id* is the opposite: it records where
the session came from and changes nothing about the plan. The column is therefore written
**only on insert**: `findOrCreateActiveSession` returns early for a resumed session, so a
resumed workout keeps the provenance it was created with rather than acquiring a new one.

**Matching is by template and date, and the rule order is stated because ties are real.** More
than one slot can reference the same template (a lift trained twice a week is the normal
case), so one session has to choose which occurrence it settles. Exact weekday first, then the
latest earlier slot, then the earliest later one, then the earliest unresolved; a session
resolves one occurrence and an occurrence is settled by the first session that matches it.
Matching by *exercises* was rejected — it breaks the moment a template is edited, and it
cannot tell two slots apart. The week is Monday-start and taken in the session's own zone
(N25), which is the only reading that survives a workout performed after a flight.

**The question is asked at the point of starting.** Asking at launch was rejected outright: an
app that interrogates you when you open it is one you stop opening. Only days strictly before
today count as missed, so a Friday slot on a Wednesday is not "settled" by a Continue that
would be inventing a decision. Continue writes every pending skip in one call for the same
reason it exists at all: asking again for the next miss turns two misses into two
interrogations.

**One active program is a flag on the row.** The alternative — an id in `SharedPreferences` —
was rejected because a program is training data: it rides in the backup like a plan or a
measurement, and a preference pointing at it would split one answer across two stores and be
lost on a restore. `setActiveProgram` clears the others and sets this one in a single
transaction, so "at most one" cannot be observed half-applied. `isActive` also makes the
fallback explicit: no live row active means the home screen reads the N16 pins it always did.

**The migration leaves `templateId` null, deliberately.** A workout recorded before programs
existed cannot be given the template it was started from — that was never captured — and
stamping one on would make it settle an occurrence it never touched. Null means "unknown",
and the matcher finds no session for those weeks, which is the honest outcome.

## Truth, Turbine

New and touched tests assert with Truth, and assert Flow sequences with Turbine.

JUnit's `assertEquals(expected, actual)` puts two bare values side by side with nothing
saying which was which, and the arguments are easy to swap; `assertThat(actual).isEqualTo(expected)`
cannot be written the wrong way round. Turbine is for what a polled `.value` cannot express
at all: a *sequence* of emissions, or an invariant that has to hold across one.

Both are JVM-only (`testImplementation`), and the core Truth artifact rather than
`truth-android`, which would pull androidx.test stubs alongside Robolectric's. Adopted after
the review, so most files still use JUnit: they migrate **as they are touched**, never in a
sweep, and a file is not left half-converted. This is a preference about failure messages,
not a correctness gate — do not spend a change on migration alone.

## Releases

Releases are manual, and CI cannot attach an artifact: it builds a release APK to prove R8
succeeds but signs nothing, so automating steps 4–7 of the procedure would mean putting
`workout.jks` **and its passwords** into GitHub Secrets.

**Decided against, for now.** The automation has a genuine engineering benefit — CI building
from the tag would make the tag/APK mismatch impossible *by construction*, rather than merely
documented — weighed against moving a permanent signing key into a third party's store, where
any workflow in this repository could reach it, for an app released a few times a year. Ten
documented minutes is the cheaper side of that trade.

If the cadence ever changes, do it as a **GitHub Environment secret with required reviewers**, so
a human approves before any job can read the key. A raw repository secret readable by any
workflow is the version that should stay unbuilt.
