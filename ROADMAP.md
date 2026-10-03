# Workout — Roadmap

> **v1.9** is shipped and installed. Last reviewed against the code: 2026-10-03.
>
> Forward-looking only. What shipped is [CHANGELOG.md](CHANGELOG.md), how a release is cut is
> [RELEASING.md](RELEASING.md), and settled decisions with the rules that apply to every
> change are [DECISIONS.md](DECISIONS.md) — their argument, where there is one, is
> [DECISIONS-EVIDENCE.md](DECISIONS-EVIDENCE.md).

A local-only training notebook: write a plan, log what you actually did against it, and let
the app show you the difference and what to do next.

Ids (`F#` foundations, `B#` defects, `N#` the next planned changes, `P#.#` the product
backlog, `R#.#` releases) are stable and go in commit messages. They were assigned when the
work was planned, so they do not run in order — and an id this file does not list has
shipped, with its entry in [CHANGELOG.md](CHANGELOG.md).

## Next

Six rows, planned together: they are everything the last two rounds deferred, and each one
carries its own id and a spelled-out decision rather than a wish. They are ordered by value —
not a commitment to that order — and each leaves for [CHANGELOG.md](CHANGELOG.md) when it ships.

**Programming** — turns a logger into a plan

**P3.10 — Deloads, authored.**

A deload is a week the lifter marks, never one the app computes — and marking it settles P3.5's
open question by taking the week out of the ratio instead of guessing whether it counts.

- **An event keyed by program and week**, the shape of a skip (the Monday), never a dated plan
  (N16) and never a calculated week.
- **Exempt from the ratio, not from the calendar**: a deload week's scheduled occurrences are
  neither done, skipped nor missed, so a deliberate back-off cannot read as a failure, while
  its sessions still mark their days. The missed-day question still asks in that week — the
  week is exempt from judgement, not from the schedule.
- **Nothing is scaled for you.** What a deload week prescribes is what the slot prescribes
  (P3.8); the app does not reduce loads in silence, and the offer stays an offer (N22).
- **Marked and unmarked on the adherence calendar**, for a week it can show. A week that has
  not started cannot be marked, because the app has no forward view to hang it on (N16 rejected
  the dated plan) and a deload is decided by how the block is going.

**P3.11 — Substitute a workout for one occurrence.**

An event keyed by slot and week, the shape of a skip: "the rack is taken today, do the dumbbell
version" cannot be answered by editing the program, which changes every week that references
the template (N16, inherited by P3.3).

- **Chosen at the point of starting**, like the missed-day question: the slot's row in today's
  plan offers *substitute*, and the pick is recorded for that slot and week before the session
  opens. No other week changes, which is the whole point.
- **A session started from the substitute settles the substituted occurrence** — P3.3's
  matching by template and date extends to the substitute, or the app would keep asking about a
  day already trained.
- **Adherence scores it against the slot** (P3.14): the day was scheduled, and it was done,
  whatever it was done with.

**Adherence** — how often the plan happened

**P3.13 — Correcting a skip.**

P3.3's prompt is the only thing that ever records a skip, and *Continue* silences a whole week
in one tap: a mis-tap is wrong forever, and a week the app never asked about reads as misses it
did not earn (P3.5's stated limit).

- **The lifter can add or remove a skip**, because a skip is their statement rather than the
  app's: tapping a scheduled day opens what that day scheduled — one row per occurrence, with
  its state — and each row can be marked skipped or unmarked, using the same `program_skips`
  row.
- **Adding looks backwards only**: a day passed over is behind you, so a future day cannot be
  skipped (P3.3's rule), and the toggle appears only for today or earlier.
- **Removing is always allowed** and soft-deletes the row, as every table here does, returning
  the day to done, missed or pending by P3.5's same definitions.
- **The app never removes one itself**, and *Continue* keeps writing a whole week (P3.3): a
  correction is a second, explicit writer, not a second opinion.

**P3.14 — Adherence, broken down.**

A month's ratio says the program is at 70%; it does not say that the squat day is what keeps
being skipped. This is the same aggregate, read per slot and per lift.

- **Per slot first**: each of the active program's slots gets its own done / skipped / missed
  over the window, so the parts sum to the whole by construction.
- **Per lift second**: an exercise gets the occurrences of the slots whose template prescribes
  it — which answers "am I skipping this lift, or this day" from the joins N14 already has.
- **Counts, not a per-row percentage**: two of three is not 67% of anything worth printing.
- **It needs an active program**, for the ratio's reason (P3.5): the pins carry no skip record.

**P3.15 — A streak of scheduled work.**

The one adherence number a lifter reads without thinking — and the obvious version of it is
wrong here, because consecutive calendar days would break on every rest day, and an unscheduled
day is rest by P3.5's rule.

- **It counts scheduled occurrences, not days** — the ratio's unit (P3.5) — walking back from
  the most recent elapsed occurrence while each was done.
- **A skip and a miss both break it** (scheduled, and not done); an unscheduled day is
  invisible to it; a deload week (P3.10) neither extends nor breaks it, because it is not
  scored.
- **No program, no streak**, for the ratio's reason: the pins cannot tell a rest from a miss.
- **Shown as a number with its start**, never as a nudge: the app has no notifications (P4.7 is
  parked) and a streak that pushes is a coach (N22's rule).

**P3.16 — A window wider than a month.**

A month is the right grid and too short a judgement: a block is four to six weeks, so a change
that took one reads as one flat month after another.

- **The grid keeps its month and the ratio gains a history**: a chart with one point per month,
  so a point and the grid it came from cannot disagree, and P3.5's one-window rule holds.
- **A month with nothing scored is a gap, not a zero** — N37's rule for a trend line, and
  P3.5's rule for the ratio said again.
- **It arrives when a month proves too short**, which is a block the lifter is actually running
  and not a date: that is the trigger the deferred row named, and why this row is last.
- **The window is not the statistics range** (N21): that setting is a chart's window on another
  screen, and reusing it would make one number mean two things.

## Later (still self-contained)

Post-MVP on the same local-only premise, grouped by theme and ordered by value inside each: a
candidate graduates to *Next* — gaining an id and a spelled-out decision — when it is picked
up, and leaves for [CHANGELOG.md](CHANGELOG.md) when it ships.

Nothing is waiting here. Both paragraphs of deferred scope — P3.3's and P3.5's — are planned in
*Next* above, and what they named that is not a feature is already a settled decision: no dated
instances (N16), nothing automatic (N22's "the app suggests; it never writes"), a weekday-less
slot that is never missed and is order-only, and one active program, which P3.12 amends. The
next candidate comes from a parked row's trigger or a new reason, not from this queue.

## Parked — deliberately not planned

Each row is a product in its own right, contradicts "local-only", or both. Parking is a
decision, not a backlog, and every row names what would change it. Parked is **not** the same
as the non-goals below: these become possible again the moment their trigger fires, while a
non-goal is a line this app does not cross.

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
| P1.17 | Accessibility audit | The per-screen rule stops being enough — a real complaint on a device, or a screen that grew past ad-hoc tagging. The rule still applies to every change; only the sweep is parked. |
| P2.5 | Progress photos | A visual record is actually wanted, and an encrypted-storage design for it is acceptable. |
| P2.8 | Muscle-group balance warnings | Enough history exists for a rolling window to say something true rather than something plausible. |
| P2.6 | Plate calculator | Loading from a plan's target is frequent enough that the arithmetic gets in the way, and you would rather it were done for you. |
| — | **Play Store listing** | You want distribution beyond `adb install`. Self-install works today, and Play App Signing would change who holds the signing key. |
| — | **Encryption at rest / app lock** | You start carrying the phone somewhere you would not carry the data. |
| F6 | Module split into `:core:*` / `:feature:*` | **A named goal, not a refactor**: a measured build-time problem, working on one feature without compiling the rest, or a second surface (Wear, a widget). |
| F11b | Product analytics | Almost certainly never: on a single-user local tool it buys nothing, and it would breach the no-`INTERNET` line. |

### N39 — the plan's target, parked by decision

The per-metric target shipped; **the plan's target was scoped out** when the feature was
built, so it is parked rather than planned. It is a different shape of work: the training plan
is not one of the statistics screen's sources, so it means making the plan available to a
screen that knows nothing about it, plus a rule for a lift that is in two plans at once or in
none.

## Explicit non-goals

Permanent, unlike *Parked* above: nutrition / calorie tracking, social feeds, live GPS route
tracking, and a web dashboard. Each is a product in its own right and would dilute the logging
core.

## Keeping this true

Four rules, written after the drift they prevent had happened four times — stale test counts,
a dependency inventory, an enumerated feature list that v1.3 quietly outgrew, and a review
stamp still reading v1.3 while *Next* said "Nothing" after v1.4 had shipped with defects
unfound. Two of those four were this file describing itself wrongly.

1. **Nothing marked done lives here.** Shipped work goes to [CHANGELOG.md](CHANGELOG.md), and a
   finished row is deleted from this file.
2. **No hand-maintained facts.** No test counts, no dependency lists, no inventory of which
   files exist. Those are commands (`./gradlew …`) or links.
3. **Every parked row names its revisit trigger**, so parking reads as a decision rather than a
   forgotten item.
4. **Durable content lives in [DECISIONS.md](DECISIONS.md).** Settled decisions and the rules
   that apply to every change are a reference, not a queue, and the two age differently. A
   section here that accumulates rather than drains belongs there.

Bump the review stamp at the top whenever this file is checked against the code.
