# Workout — Roadmap

> **v1.7** is shipped and installed. Last reviewed against the code: 2026-10-02.
>
> This file is forward-looking only. What shipped lives in
> [CHANGELOG.md](CHANGELOG.md); how a release is cut lives in
> [RELEASING.md](RELEASING.md); settled decisions and the rules that apply to every
> change live in [DECISIONS.md](DECISIONS.md).

**What this app is.** A local-only training notebook: write a plan, log what you actually
did against it, and let the app show you the difference and what to do next. No account, no
server, and nothing leaves the device unless you export it.

Feature ids (`F#` foundations, `B#` defects, `N#` the next planned changes,
`P#.#` the product backlog, `R#.#` releases) are stable and are referenced from
commit messages. They were assigned when the work was planned, so they do not run in
order — the `P4`/`P5` rows are simply the ones parked furthest out, and `N1`–`N29` and
`N31`–`N33` have shipped and left the file.

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

## Next

**The next round is a UI one, in seven steps**: five tabs along the bottom, a Statistics
screen that shows everything, and then the chart underneath it. N31–N33 and B44 have shipped and are in
[CHANGELOG.md](CHANGELOG.md), as has everything before them — the v1.6 review (B33–B43), the v1.5
corrections (B26–B32), the round after it (N26–N28), repeating the last workout (N29) and the session
timezone (N25). The one open thread from the rename, **N30**, stays at the end of this section.

### N34 — Five tabs along the bottom

Every surface hangs off home's overflow menu today, which is why "how is everything going" has no home
of its own. This gives the four that have earned permanence one — **Workouts · History · Statistics ·
Library · Settings** — with Workouts still the start destination.

- **A tab root, and what is pushed from it:** Workouts → ActiveWorkout, Templates, TemplateEditor,
  WorkoutDetail; History → WorkoutDetail; Statistics → Measurements; Library → ExerciseDetail; Settings
  → nothing. **Templates belong under Workouts** (decided): a plan is part of working out, and Library
  stays the exercise reference it is.
- **The bar hides during a workout.** ActiveWorkout and ExercisePicker are a modal flow with their own
  chrome, and a tab bar under a live set logger is an invitation to lose the session.
- **Each tab keeps its own back stack** — `popUpTo(start) { saveState = true }`, `launchSingleTop`,
  `restoreState` — so History keeps its place while you look at Statistics.
- **Back is defined rather than discovered:** a pushed detail pops; a non-Workouts tab root goes to
  Workouts; Workouts exits.
- **Insets move into one outer `Scaffold`**, so the bar's height is applied once rather than by each
  screen.
- **Accessibility is part of the bar**: a label per item and a selected state TalkBack can announce, or
  it reads as five unlabelled squares.
- **What it costs:** the overflow menu shrinks, the nav host gains a test, and every screen's padding
  becomes the shell's business instead of its own.

### N35 — The Statistics tab

One screen that answers "how is everything going", in the shape Waistline settled on: **one big chart
with a picker**, not a wall of small multiples.

- **Overview: three numbers for the selected range** — workouts, volume lifted, PRs. Deliberately no
  more than three for now.
- **Range:** 7d / 1m / 3m / 6m / 1y / All, plus a custom From–To. Persisted, and a range ending "today"
  stays today.
- **One picker over every metric**, in three groups — **Workout** (`TrendMetric`'s RPE, muscle feel,
  joint pain), **Exercise** (`ExerciseTrendMetric`'s eight) and **Body** (weight, body fat and muscle,
  plus `TapeSite`'s seven). Choosing an Exercise metric reveals a lift picker, and the last choice
  persists.
- **A metric registry is the piece that makes "everything" maintainable.** Twenty-one series already
  exist across three enums, reached today from three screens with three query shapes; a registry entry
  carries each one's id, label, group, unit, value formatter, supplying query, axis policy and whether
  it is a line or bars. It **promotes a pattern this codebase already arrived at** rather than inventing
  one: `ExerciseTrendMetric` already carries `isLoad` ("drawn from zero rather than on 1–10") and
  `higherIsBetter` (N17's assisted direction), which are exactly the fields a registry needs. The three
  enums stay, and the registry references them — this is not a scheme to delete three enums that each
  mean something.
- **Measurements fold in.** Their charts become picker entries; the entry and editing screen stays a
  pushed destination, because viewing and recording are different jobs.
- **ExerciseTrends folds in** as the same screen with the lift preselected, so "how is my bench going"
  arrives here. The separate screen, its ViewModel and their tests are deleted.

### N36 — The readings list

A collapsible list under the chart: every reading as date and value, newest first, with Average and
Trend rows on top. It is Waistline's timeline, it is the literal answer to "see everything", and it is
the accessible counterpart to a canvas this app deliberately blanks out for screen readers. Cheap, and
it should not wait for the chart work.

### N37 — The chart's x-axis becomes time

Points spread by elapsed time rather than by index. Today two workouts a day apart and two a month apart
are drawn the same distance apart, which is a real distortion rather than a detail.

- **The gap question is settled here, and the answer is not Waistline's:** keep **breaking** the line at
  a missing reading rather than spanning it. A time axis shows the gap as distance, which is more honest
  than a straight segment drawn across three weeks. Waistline spans gaps; this is the one place the two
  should differ, and the row says so.
- **The axis gains labels** — first and last date, minimum and maximum value.
- The projection is pure Kotlin, so it is unit-tested like the rest of the domain maths.

### N38 — Bars or a line, and where zero is

Per metric rather than a global switch: bars for count-like series (volume, total reps), lines for
continuous ones (bodyweight, ratings, tape). A zero baseline stays a property of the metric, which is
what the chart already does for ratings — this makes it explicit rather than incidental.

### N39 — Average, goal and trend lines

A horizontal average; a goal line — a target setting for a measurement, the plan's target (N14) for a
lift; and a least-squares regression line with its slope as text ("−0.3 kg/week"). Pure Kotlin with
tests, the same way Epley and the warm-up ramp are.

### N40 — A moving average

A trailing simple moving average, period configurable, default seven, drawn heavier with no dots. For
bodyweight this is the point of the whole exercise: daily weight is noise, and the seven-day mean is the
signal.

### N30 — the CI emulator thread, continued

**The emulator-options lever was already pulled.** The pinned `reactivecircus/android-emulator-runner`
at the SHA this workflow uses defaults `emulator-options` to exactly the flags this repository's local
runs use — `-no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim` — so "none of those
are set here" was true of the *file* and false of the run: an absent key inherits that default. Reading
the action's own `action.yml` at the pinned SHA is what settled it, and it is worth recording as the
lesson rather than the trivia: a workflow that omits a key is still choosing a value, and the value it
chooses lives in the pinned action, not in this repository.

The flags are now set explicitly anyway, so the workflow owns them rather than inheriting them and a
future SHA bump cannot change how the emulator boots without this file saying so. That is hardening, not
a fix, and it is stated as such.

**When it runs is now decided** (this round): **nightly, and on demand before a release**. Not on every
push — the emulator is the one piece of infrastructure here that has failed without a test running, so a
per-push run mostly reports on the runner, and the per-change guard is the local gate set. The instrumented
job is ungated again as part of that, so it is exercised on a schedule rather than when someone remembers,
and `RELEASING.md` step 5 dispatches the pipeline before a tag. The concurrency group also gained the event
name, because the group was what allowed a documentation push to cancel an instrumented run twenty minutes
in — and the cancelled job's summary was indistinguishable from the infrastructure failure this thread is
about, which is how a self-inflicted cancellation got read as evidence.

**Measured, and the answer was not what the options promised.** The first nightly run booted and then lost
the emulator without running a test: `Boot completed in 990222 ms` — sixteen and a half minutes — followed
immediately by `Failure calling service settings: Broken pipe`, `Emulator client has not yet been
configured` and the netsim wifi stream being cancelled. Sixteen minutes to boot is not a slow start, it is
a starved runner, and the emulator it eventually produced was already gone.

So the image is now **`aosp_atd` at API 34** rather than `google_apis` at 36: an Automated Test Device is
built for this job and carries none of the Google services this app does not use at runtime, which is a
second reason to prefer it. It costs two API levels, and that is a real trade — the suite now runs against
34 rather than 36 — taken because a suite that never runs tests nothing at all. Boot time and the failure
signature on the nightly run are what will say whether it worked.

**If it fails again**, the levers are about the runner rather than the flags:

- a lighter system image — `google_atd` or `aosp_atd`, which exist to be automated-test devices and boot
  far faster and leaner than `google_apis`; this is the standard answer to a hosted runner whose emulator
  dies, and it costs an API level (ATD images stop short of the newest) and therefore changes what the
  suite is tested against;
- `-memory` and `-cores`, which trade emulator stability against host pressure in either direction and
  therefore need a measurement rather than a guess.

Neither is done here, and the next change to it should be one of them, tried on its own so its result
means something.

## Later (still self-contained)

Post-MVP, same local-only premise. Grouped by theme, ordered by value inside each.

This is where candidates live. One graduates to *Next* — gaining a `B#` or `N#` id and
a spelled-out decision — when it is picked up, and leaves for
[CHANGELOG.md](CHANGELOG.md) when it ships.

**Programming** — turns a logger into a plan

**P3.3 — Programs: an ordered list of templates, each with a weekday.**

A **program** is a named, ordered list of slots; a slot is a template plus an optional weekday. It is
the container N16's pins cannot be on their own: a pin says what happens on a Tuesday, but nothing
orders the pins against each other, so "which one is next" and "was that a skip or a rest day" have no
answer.

- **Today's plan** comes from the slot pinned to today. With no program active, home falls back to the
  template pins it already reads.
- **A skipped occurrence is asked about, not assumed.** When a start is attempted and a slot's
  occurrence this week has neither a session nor a recorded skip, the app asks: *"You missed Paused
  Squat on Tuesday. Do it now, or continue with Bench?"* — **Do it now** starts that slot; **Continue**
  records a skip for **every** pending occurrence this week, because asking again for the next one turns
  two misses into two interrogations. Asked at the point of starting, not at launch: an app that
  questions you when you open it is one you stop opening.
- **A skip is an event keyed by slot and week** (`program_skips`, storing the week start), never a
  boolean on the slot: the same weekday recurs, so a flag would need resetting and would be wrong the
  moment two weeks in a row were missed. Those rows are also exactly what P3.5 needs — without them,
  "skipped" is unknowable, because a standing weekday pin carries no history.
- **A session records the template it was started from** — one nullable `templateId` on
  `workout_sessions`, written only when the session is *created* from a template, so a resumed session
  never rewrites it. That is how an occurrence is matched: **by template and date**, in a Monday-start
  week taken in the session's own zone (N25 is what makes "which day was this" answerable).
  - **It amends N16 deliberately, and the distinction is the point.** N16 rejected copying a plan's
    *targets* onto a session because that freezes what the plan prescribes. Recording *where a session
    came from* freezes nothing: the template stays living, and this is provenance rather than
    prescription.
  - **Matching:** one candidate slot with that template resolves; with several, an exact weekday match
    wins, then the latest slot earlier in the week (done late), then the earliest after it (done early),
    then the earliest unresolved. A session resolves at most one occurrence, and an occurrence is
    resolved by at most one session — the first.

**What v1 deliberately leaves out** — this list matters as much as the scope above:

- **No deloads.** No `isDeload`, no weeks-of-weeks. A deload will be *authored* when it arrives, not
  calculated.
- **No auto-progression.** A program decides *which* template; the lifter decides the numbers. This
  depends on **N33**, which stops the app's progression proposal from being the prefilled value.
- **No intensity modifiers**, no percentage-of-1RM programming, no automatic anything.
- **No dated instances.** N16 rejected them and a program is a rotation; a calendar of planned sessions
  would reintroduce the entity that decision avoided.
- **One active program.** Any others fall back to the template pins.
- **A weekday-less slot is never "missed"** — it has no day to miss. It is order-only.
- **No per-slot template substitution** mid-cycle beyond editing the program, and no load-based rotation.

**Known limitations, stated rather than discovered:** an occurrence is resolved only when the workout was
*started from* that template, so bench added by hand to an empty workout does not resolve it — matching
by exercises was rejected because it breaks the moment a template is edited and cannot tell two slots
apart; a second session from the same template in a week is unmatched; and editing a template changes
every week that references it, which is N16's living-template decision inherited rather than new.

**P3.5 — Adherence and a calendar.**

How often what was scheduled actually happened, over weeks rather than one session, plus a calendar of
days trained. Session-level plan-versus-actual shipped as N20; this is the aggregate over it.

- **Its hard part is knowing what was skipped, and it has no answer of its own.** Today the schedule is
  a standing rule — "these templates are pinned to Tuesday" — not a history, so "you missed last
  Tuesday" cannot be derived from pins. **P3.3's `program_skips` rows are what make it answerable**,
  which is why this row follows P3.3 rather than standing beside it.
- **Shape:** a month calendar with trained days marked, and a completion ratio over a window — sessions
  started against scheduled days.
- **Decisions it carries:** an unscheduled day is rest rather than a miss, so only scheduled days count;
  what counts as scheduled when nothing is pinned; and whether a deload week is judged by the same
  standard as any other.

Templates shipped their v1 as **N3**, and their targets, per-plan rest and weekday schedule
as **N14–N16**. Auto-progression shipped as **N22**, and supersets as **N24**.

The **accessibility rule still applies to every screen as it is written**
([DECISIONS.md](DECISIONS.md)); the audit sweep that used to sit here is parked, so this
section is empty until it returns or something replaces it.

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
| P1.11 | Onboarding: goal, experience level, weekly target | This stops being a single-user local tool with one obvious user. It personalises defaults, and there are no defaults to personalise. |
| P1.9 | kg/lb display setting | You start lifting in pounds. Storage is canonical grams, so this is display-only whenever it is wanted. |
| P1.17 | Accessibility audit | The per-screen rule stops being enough — a real complaint on a device, or a screen that grew past ad-hoc tagging. The rule itself still applies to every change; only the sweep is parked. |
| P2.5 | Progress photos | A visual record is actually wanted, and an encrypted-storage design for it is acceptable. |
| P2.8 | Muscle-group balance warnings | Enough history exists for a rolling window to say something true rather than something plausible. |
| P2.6 | Plate calculator | Loading from a plan's target is frequent enough that the arithmetic gets in the way, and you would rather it were done for you. |
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
