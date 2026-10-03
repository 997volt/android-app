# Workout — Roadmap

> **v1.8** is shipped and installed; **P3.3** is implemented and unreleased. Last reviewed
> against the code: 2026-10-03.
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

**P3.5 — Adherence and a calendar.**

How often what was scheduled actually happened, over weeks rather than one session, plus a
month of days trained. N20 shipped the session-level plan-versus-actual; this is the aggregate
over it, and it is answerable now only because P3.3 shipped the two things a standing pin could
not carry: a program's weekday slots, and `program_skips` as a record of what was passed over
on purpose.

- **A destination of its own, reached from Statistics** — the tab that answers "how is
  everything going" (N35) — the way Measurements already is. N34's five surfaces stand: one
  screen does not earn a sixth tab.
- **One window, the month the grid shows.** The calendar navigates back through history and
  forward no further than the current month, and the ratio covers that same month, so the
  number and the grid cannot disagree about what they are counting. Days after today are drawn
  but never scored: only a day strictly before today can have been missed (P3.3).
- **What was scheduled is the active program's weekday slots.** An unscheduled day is rest, not
  a miss, so it never enters the ratio; a weekday-less slot is order-only and is scheduled on no
  day, so it is never scored. With **no active program** the calendar still marks the days
  trained — that needs no schedule — but there is **no ratio**, because the N16 pins home
  falls back to carry no skip record, so a rest on a pinned day is indistinguishable from a
  miss. Scoring the pins was rejected for exactly that reason: it would turn every deliberate
  rest on a pinned day into a failure.
- **Done, skipped and missed come from one set of definitions:**
  - **Done** — the slot's template was started *and the session was finished* (`finishedAt`,
    the rule history and every statistic already read), matched by template and date in a
    Monday-start week taken in the session's own zone (P3.3's matching, reused rather than
    restated).
  - **Skipped** — a `program_skips` row for that slot and week.
  - **Missed** — elapsed and scheduled, and neither of the above.
  - **The unit is the occurrence, not the day.** Two slots may fall on one Tuesday, and P3.3
    settles and skips them one at a time, so the ratio counts two while the calendar marks the
    one day.
  - **The ratio** is done over done + skipped + missed for the shown month; a month with no
    elapsed scheduled day says so rather than reporting 0% or 100%.
- **A trained day is a finished session's own day**, in the zone it was performed in (N25,
  B45), so a workout performed abroad marks the day it happened even when the device's month
  has moved on. Weeks stay Monday-start in that same zone, because that is what a skip row is
  keyed by.
- **Adherence is deliberately stricter than the prompt.** P3.3 settles a day when a session is
  *started*, because what it asks is "should I nag you about Tuesday?"; this asks whether the
  training happened, so an abandoned start is a miss here and marks no trained day.
- **A week is a week.** Deloads were left out of P3.3, so nothing in the data marks one and
  every week is judged by the same standard; exempting a deload week becomes a decision on the
  day deloads are authored.
- **No schema change.** Everything it reads shipped with P3.3 at v20 — `program_slots`,
  `program_skips`, and `workout_sessions`' `templateId`, `finishedAt` and `zoneOffsetMinutes`
  — and the aggregate is the same pure, JVM-tested `ProgramSchedule` the prompt uses, fed the
  window's finished sessions.

**What v1 leaves out:** no streaks and no per-lift or per-template breakdown — a ratio and a
grid are the whole view; no editing or back-filling a skip, because a skip is a decision taken
at the moment of starting and P3.3's prompt is its only writer; and no rolling multi-month
chart, because the month is the window.

**Known limitations, stated rather than discovered:** skips exist only from P3.3 onward and
only for a week whose prompt was answered, so an older — or unanswered — past week reads as
misses, and the app cannot invent a skip it never recorded; the schedule is the program as it
is *now*, so adding a weekday slot writes misses into weeks already past and deleting one
erases them, which is P3.3/N16's living schedule inherited rather than new; deactivating a
program takes its history out of the ratio while the calendar keeps the days; and P3.3's two
inherited limits stand: a second finished session from the same template in a week goes
unmatched, and a session started by hand resolves nothing.

## Later (still self-contained)

Post-MVP on the same local-only premise, grouped by theme and ordered by value inside each.
This is where candidates live: one graduates to *Next* — gaining an id and a spelled-out
decision — when it is picked up, and leaves for [CHANGELOG.md](CHANGELOG.md) when it ships.

**Programming** — turns a logger into a plan

**The rest of programs — what v1 deliberately leaves out.** P3.3 shipped as the ordered
schedule, and the scope it does not build is the future of the same feature, deferred rather
than rejected: no deloads (a deload will be *authored* when it arrives, not calculated), no
auto-progression (a program decides *which* template; the lifter decides the numbers — this
depends on **N33**), no intensity modifiers, no percentage-of-1RM programming, no automatic
anything, no dated instances (N16 rejected the entity a calendar of planned sessions would
reintroduce), one active program only (any others fall back to the template pins), a
weekday-less slot is never "missed" because it has no day to miss and is order-only, and no
per-slot template substitution mid-cycle beyond editing the program, nor load-based rotation.

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
