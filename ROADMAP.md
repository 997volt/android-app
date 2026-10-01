# Workout — Roadmap

> **v1.6** is shipped and installed. Last reviewed against the code: 2026-10-01.
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
order — the `P4`/`P5` rows are simply the ones parked furthest out, and `N1`–`N24` have
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

Marked for work, in order: the corrections the v1.5 review left, then removing the background
rest alert together with the two things that replace it, then the warm-up generator. **N25**
is decided but not scheduled — it only bites if you train across timezones.

### B26–B32 — the corrections the v1.5 review left

The first two were verified against the code rather than left as suspicions. **B26 is fixed** and
lives in [CHANGELOG.md](CHANGELOG.md).

- **B27 — Superset pairing is not atomic.** `onToggleSuperset` writes one row per id through
  `handle`, which records a failure and carries on, so a failure or a process death between
  writes leaves half a group — precisely the state the code's own comment says nobody asked for.
  **Fix:** one transactional write, and stop rather than continue on failure.
- **B28 — The grouping toggle on a session's first exercise is a no-op that rewrites every
  ungrouped row.** With no previous exercise the group is null, the filter then matches every
  ungrouped row, and each is rewritten to null — churning `updatedAt` for no change. **Fix:** do
  not draw the control on row 0, or make the write a no-op.
- **B29 — A test that cannot fail.** `TemplateEditorScreenTest`'s weekday case asserts only that
  the chip exists, and `WeekdayPicker` composes every chip unconditionally — so it passes even if
  the plan's `weekday` is ignored. It wants `assertIsSelected()`. Worth watching for in any test
  written against a control that is always rendered.
- **B30 — Declarations nothing calls.** `PreviousPerformance.at()` lost its last caller when
  `SetSuggestion` stopped being indexed, and `SettingsUiState.isLoading` is written and never read
  while the screen renders no loading state. The rule is to delete them.
- **B31 — Two names that lie.** Two types are called `WorkoutSummary` — the history row in
  `domain/model`, and the review payload at the bottom of `ActiveWorkoutViewModel` — and
  `SettingsModule` lives in `DatabaseModule.kt`, whose name says database while the module binds a
  `SharedPreferences` repository.
- **B32 — Unused imports are not gated.** Two files carry imports added for an implementation
  that landed elsewhere, and `./gradlew detekt` passes on them: `UnusedImports` is `active: false`
  in detekt 1.23.8 and the compiler is not run with `-Werror`, so neither gate this project treats
  as authoritative can see an unused import. **Decide:** turn the rule on deliberately, or record
  that imports are not gated.

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

### N27 — Make the rest timer audible and visible

The replacement for what N26 removes, and the reason the two are one round rather than two. With no
background alert, the timer can only be noticed while the app is on screen.

- **P1.14** — sound and haptics when a rest ends.
- **P1.10** — keep the screen on for the duration of a workout. The settings screen exists now
  (N21), so this has somewhere to live instead of needing a surface of its own.
- Both were Tier 3 polish; removing the alert is what makes them load-bearing, which is why they
  are marked here rather than left in *Later*.

### N28 — Warm-up set generator

Was **P2.7**, and a better feature than it was before plans had roles: it can *write* warm-up sets
into a plan using the `WARMUP` role rather than only suggesting numbers. A ramp computed from the
plan's working weight and added in one action is the difference between a plan that is pleasant to
author and one that is not.

### N25 — A session remembers the timezone it was performed in

**Not scheduled.** Decided, and waiting on a reason to use it — it only bites if you train across
timezones.

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

## Later (still self-contained)

Post-MVP, same local-only premise. Grouped by theme, ordered by value inside each.

This is where candidates live. One graduates to *Next* — gaining a `B#` or `N#` id and
a spelled-out decision — when it is picked up, and leaves for
[CHANGELOG.md](CHANGELOG.md) when it ships.

**Everyday logging**
- **P1.15** Repeat last workout in one tap.

**Insight** — why the app gets opened between workouts
- **P2.4** Body measurements.

**Programming** — turns a logger into a plan
- **P3.3** Programs / mesocycles with scheduled deloads.
- **P3.5** Planned-versus-completed adherence over a longer window, and a calendar view —
  the weekly schedule itself shipped as **N16**, and session-level plan-versus-actual is
  **N20**.

**Small and self-contained**
- **P2.6** Plate calculator — unparked, because its trigger has fired: plans carry target
  weights now, and that is when the arithmetic stops being worth doing in your head.

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
