# Workout — Roadmap

> **v1.7** is shipped and installed. Last reviewed against the code: 2026-10-01.
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
order — the `P4`/`P5` rows are simply the ones parked furthest out, and `N1`–`N29` have
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

## Next

**Two things are marked, in order** — N32 and N33, with N31 now shipped and in
[CHANGELOG.md](CHANGELOG.md). **B44 — the white screen after a discard — is fixed** and lives in
[CHANGELOG.md](CHANGELOG.md) under *Unreleased*; it is kept out of this file by the same rule as
everything before it, which shipped: the v1.6 review (B33–B43), the v1.5 corrections (B26–B32), the round
after it (N26–N28), repeating the last workout (N29) and the session timezone (N25). Past the three, the
next round is a choice rather than a queue — the rest is in *Later*, and one CI thread from the rename is
at the end of this section.

### N32 — Body measurements

**Weight, with optional body-fat %, muscle %, and tape measurements.** Weight is the only required
field; the rest are filled when they are taken, which is how people actually measure.

- **One dated entry, nullable fields:** `measuredAt`, `weightGrams`, `bodyFatTenths`, `muscleTenths`,
  and the tape sites. A waist recorded without a weight that morning must not be a second screen or a
  fake zero.
- **Units and storage follow what is settled.** Weight in whole grams; percentages as **tenths of a
  percent in an `Int`**, for the same reason RPE is halves — `18.3` has no exact binary representation;
  tape in **millimetres in a `Long`**, displayed as cm.
- **A fixed set of tape sites for v1, every one optional:** neck, chest, waist, hips, upper arm, thigh,
  calf. A user-defined site list is a later row, not a first one.
- **A new sync-shaped table** (`measurements`), so it rides the export — and the codec guard means its
  columns must reach the backup DTO in the same change.
- **Shown as trends on the existing chart** — read carefully, because the chart is not the series. The
  trends screen is **session-indexed**: a `TrendPoint` is one workout, and `TrendMetric` reads a value off
  it, so a measurement cannot travel that pipeline — it is not a session and has no RPE or muscle feel to
  average. What it *can* share is `TrendChartFrame`, the component every series already draws through. So
  measurements get their own series, one point per entry, drawn by the same chart: bodyweight first, then
  body fat, muscle and each site. The alternative — new values on `TrendMetric` — would have forced them
  through a shape whose x-axis is workouts, and the mismatch would have shown up as a chart with the wrong
  axis rather than as an error.
  Entry and history live on a Measurements screen reached from home.
- **What it deliberately does not do:** change volume. Bodyweight exercises count `0 kg` today — a
  settled decision in [DECISIONS.md](DECISIONS.md) — and using a recorded bodyweight in their volume is
  that decision's own conversation. Stated here so it is not quietly assumed.
- **Decide at implementation:** one entry per day with later edits, or several; and whether an
  unmeasured tape site carries forward or stays blank. It should stay blank — carrying a number forward
  invents a measurement.

### N33 — Show the progression suggestion; do not substitute it

N22 shipped a suggestion, and its own KDoc says it "suggests; it never writes". In practice the
suggestion **is** the value the one-tap **Log set** commits, in two places: with no plan,
`prefillWithoutPlan` falls to `progressionFrom(previous)`, so the first set of an unplanned exercise
prefills **last time plus a step**; and with a plan that names reps but no load, `proposedForPlan`
supplies the weight. The reason is drawn as a caption, but the numbers beside it are already what one
tap writes. A lifter who progresses by hand has to notice and undo the app's step on every first set.

- **Prefill** — what one tap logs — becomes: the plan's target where it names one; otherwise what you
  just did; otherwise **what you did last time, unchanged**; otherwise the default.
- **Offer** — shown, not applied — becomes the progression proposal with its reason: *"Try 62.5 × 7 —
  one more rep than last time"*, applied only when it is accepted.
- **The change is that `SetSuggestion` carries the offer separately from the values**, because today one
  field is both the explanation and the number.
- **Touches:** `SetSuggestion` (a distinct offer), `suggestionForNextSet` (stop folding the proposal
  into the returned values), `WorkoutExerciseSection` (draw the offer with an accept action rather than
  using it as the button's numbers), and the precedence tests. `onLogSet` is unchanged — accepting the
  offer only changes what the prefill is.
- **Global, not program-only.** "I progress by hand" is not a property of how a workout was started, and
  a behaviour that changed on that would be an inconsistency noticed later. **P3.3 depends on this**:
  its "no auto-progression" is only true once the plan's numbers are the prefill and the app's step is
  an offer.

### N30 — the CI emulator-options thread

The rename is done: the app is **Workout**, the repository is `997volt/workout`, the description and
topics are filled in, and the evidence a green run was wanted for is in hand — a green build job, the
full instrumented suite passing locally, and the label read back with `aapt2 dump badging`. One thread
is genuinely open. The pipeline's instrumented job fails because the emulator dies and the runner's own
`adb emu kill` errors — a different failure from the boot timeout the step was already hardened for, and
one `tools/ci-check-instrumented.py` correctly refuses to call green. The next lever is the emulator's
*options*, which are unset: matching what works locally (`-no-window -gpu swiftshader_indirect
-no-snapshot -noaudio -no-boot-anim`), tried on its own so the result means something.

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
