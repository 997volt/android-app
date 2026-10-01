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
| **A crash-logs screen** | A small screen over [`CrashLogStore`](app/src/main/java/com/example/androidapp/platform/CrashLogStore.kt). | Reading a crash through an export actually annoys you. |

Four of this table's former rows are settled rather than open. Two became work — **N25**,
a session remembering its timezone, and **N26**, removing the background rest alert. Two
are parked with their triggers: the **Play Store listing**, and **Encryption at rest / app
lock**. What is left above is the one thing genuinely still undecided.

## Next

Tier 1 (N19–N21) and Tier 2 (N22–N24) shipped in v1.5 and live in
[CHANGELOG.md](CHANGELOG.md). Two questions that were open are now decided, so they lead as
work; behind them is what is left of Tier 3.

### N25 — A session remembers the timezone it was performed in

**Decided: a displayed time is always the time the session was performed in.** Timestamps
are UTC epoch millis today and every screen formats them in the *current* zone, so a
workout done in Tokyo reads as the wrong hour — and the wrong day — once you are home.

- **Store the offset on the session**, captured when it opens, and format with it wherever
  a session's time is shown: history, the workout detail, the trends window and the review.
- **A migration, and the backfill question it brings.** Rows written before this have no
  offset; falling back to the current zone is the only honest answer, and it is what they
  already get. Say so rather than inventing a timezone for the past.
- **The offset is captured once, when the session starts.** A session that spans a DST
  change keeps its start offset — a simplification worth stating rather than discovering.
- **The other half of the zone question is "which day was this"**, and it follows from the
  same column. Worth doing as one piece rather than two.

### N26 — Remove the background rest alert

**Decided: the alarm and notification path goes.** It exists to buzz you when rest ends
with the screen off, and it is the only reason this app requests *any* permission.

- **What goes:** the scheduler and its receiver, the notification builder, the
  ask-on-first-set permission flow, the `RestNotifier` port and its binding, the receiver
  declaration, and both manifest permissions — `POST_NOTIFICATIONS` and
  `SCHEDULE_EXACT_ALARM`.
- **What stays:** the rest timer. The end instant lives on the session row and survives
  process death; only the way it *reaches* you changes.
- **The property worth stating:** the app then declares **no permissions at all**, which is
  a stronger privacy statement than any wording, and belongs in the README.
- **Why this is not pure deletion:** with no background alert the timer has to be noticed
  in-app, so **P1.14** (sound and haptics) and **P1.10** (keep the screen on) stop being
  polish and become its replacement. They lead what follows.

### Then — what is left of Tier 3

- **P1.14** Rest sound and haptics, and **P1.10** keep the screen on — the two that carry
  the timer once N26 removes the alert.
- **P2.7** Warm-up set generator — it can *write* warm-up sets into a plan using the
  `WARMUP` role, which is what makes it better than it was.
- **P1.15** Repeat last workout in one tap.
- **P1.9** kg/lb units — only if you ever lift in pounds.

### Decisions waiting

**None.** D1 through D4 were settled by the work that needed them and moved into
[DECISIONS.md](DECISIONS.md), which is where a taken decision lives.

### Rule violations found, not new work

**Nothing outstanding.** Every finding from the review is settled, and how each was settled
is recorded in [CHANGELOG.md](CHANGELOG.md) under *Unreleased* — per the rule that finished
work leaves this file.

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

**Insight** — why the app gets opened between workouts
- **P2.1** Set-by-set history for one lift — N17 delivered the per-exercise chart and its
  entry point, so what remains is the full list of past performances, if that is wanted.
- **P2.3** A fuller chart screen over months — N13 settled the drawing approach (a `Canvas`,
  no dependency) and N17 used it for one lift; this is the longer horizon. Personal records
  are **N23** and are not this row.
- **P2.4** Body measurements.

**Programming** — turns a logger into a plan
- **P3.3** Programs / mesocycles with scheduled deloads.
- **P3.5** Planned-versus-completed adherence over a longer window, and a calendar view —
  the weekly schedule itself shipped as **N16**, and session-level plan-versus-actual is
  **N20**.

Templates shipped their v1 as **N3**, and their targets, per-plan rest and weekday schedule
as **N14–N16**. Auto-progression is **N22** and supersets are **N24**, both queued above.

**Small and self-contained**
- **P2.7** Warm-up set generator.

The **accessibility rule still applies to every screen as it is written**
([DECISIONS.md](DECISIONS.md)); the audit sweep that used to sit here is parked, so this
section is empty until it returns or something replaces it.

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
| P1.11 | Onboarding: goal, experience level, weekly target | This stops being a single-user local tool with one obvious user. It personalises defaults, and there are no defaults to personalise. |
| P1.17 | Accessibility audit | The per-screen rule stops being enough — a real complaint on a device, or a screen that grew past ad-hoc tagging. The rule itself still applies to every change; only the sweep is parked. |
| P2.5 | Progress photos | A visual record is actually wanted, and an encrypted-storage design for it is acceptable. |
| P2.6 | Plate calculator | You start loading plates from a plan and want the arithmetic done rather than done in your head. |
| P2.8 | Muscle-group balance warnings | Enough history exists for a rolling window to say something true rather than something plausible. |
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
