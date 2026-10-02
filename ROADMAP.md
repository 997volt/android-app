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

**N30 is the one item still needing work**, and behind it are **B33–B43** — what a review of the v1.6
batch found, defects and improvements together. Everything else has shipped and left this file: the
v1.5 review's corrections (B26–B32), the round after it (N26–N28), repeating the last workout (N29) and
the session timezone (N25). What they did is in [CHANGELOG.md](CHANGELOG.md). Past these the next round
is a choice rather than a queue: the rest is in *Later*, below.

### N30 — Finish the rename: the repository has no description, and nothing has validated it

The rename itself shipped — the app is **Workout**, the repository is `997volt/workout`, the old URL
redirects, and the local remote points at the new one. Two loose ends, both small and neither a code
change.

- **Fill in the repository's description and topics.** Both are empty, and a one-word name carries no
  context of its own. The description is where the specific part goes, since it can change whenever
  the app does: *"Plan a workout, log what you actually did, and see the difference. Local-only: no
  account, no server, no internet permission."* Topics: `android`, `kotlin`, `jetpack-compose`,
  `room`, `offline-first`, `workout-tracker`. This is the only place the app's genuinely distinctive
  property — that it declares no permissions at all — is visible to someone deciding whether to look.
- **Get one green CI run over the rename.** Every run on that commit was cancelled by the next push,
  so the label change rests on a local build: `aapt2 dump badging` reports
  `application-label:'Workout'` with the `applicationId` unchanged, and the suite is green (443 at the
  time of writing). That is good
  evidence and it is not the same as the pipeline having passed, which is worth knowing before a
  release is cut from it. Nothing needs deciding here — it is the same cancellation already accepted
  and recorded in [DECISIONS.md](DECISIONS.md), and the next quiet stretch closes it on its own.

### The v1.6 review — the features (B33–B37)

**B33 and B41 are fixed** and live in [CHANGELOG.md](CHANGELOG.md); the rest are open. The batch that shipped N25–N29 was reviewed function by function. Two defects are user-visible, the
rest are the removal and the tests that came with it. Ordered by what they cost.

- **B34 — The warm-up ramp lands after the working sets, not in front.** The KDoc says it "writes a
  warm-up ramp in front of what the plan already prescribes", but `RoomTemplateRepository.addSet`
  appends with `setIndex = maxSetIndex + 1` and sets are read `ORDER BY setIndex`, so a 100 kg plan
  reads working set first and then the four warm-ups. The generator's arithmetic is right — it derives
  the ramp while excluding existing warm-ups — it is written to the wrong end. The instrumented test
  only checks the warm-ups' order among themselves, so it cannot see this.
- **B35 — Three tests assert nothing, and two were the only coverage of what they name.**
  `skippingTheRest_cancelsTheAlert`, `adjustingTheRest_reschedulesTheAlert` and
  `finishingWithAComment_alsoCancelsTheRestAlert` each set up, call one function and end — no assertion
  at all, so they pass whatever the code does. The names describe the alarm N26 deleted. Worse than the
  vacuity: those are the *only* test callers of `onSkipRest` and `onAdjustRest`, so skipping and
  adjusting a rest lost its entire coverage in the same change that removed the alert — and the N27
  replacement (sound and haptics) depends on exactly that behaviour. Each test should either assert the
  new in-app behaviour or be deleted; renaming one while leaving the body empty would be worse than
  either.
- **B36 — The manifest still describes the permissions it no longer declares.** The comment block at
  `AndroidManifest.xml:5-13` explains `POST_NOTIFICATIONS` and `SCHEDULE_EXACT_ALARM`, both deleted by
  N26, and it is the last mention of either anywhere in `app/src`. Harmless to the build and misleading
  about the claim the file now carries, since this is the very file whose emptiness the no-permissions
  promise rests on. The same change left stale comments in `DataTransferActions.kt:28` (which still says
  "its only declared permission stays the optional rest alert", directly contradicting the README),
  `ActiveWorkoutScreen.kt:91`, `ActiveWorkoutViewModel.kt:798`, `RoomWorkoutRepository.kt:217` and
  `SettingsRepository.kt:52`; `AGENTS.md` still names `FakeRestNotifier`, a class that no longer exists.
- **B37 — A stranded "Rest timer" notification channel, with no cleanup and no note.** The deleted code
  created channel id `rest_timer`, and it created it *before* checking whether notifications were
  enabled — so it exists on any device that ran a pre-N26 build and had the alert fire. Android keeps a
  channel across updates until uninstall, and nothing references `NotificationManager` any more, so
  those devices keep a meaningless entry in system settings that no code can remove. That may be
  perfectly acceptable; what makes it a finding is that it is undocumented, which leaves it
  indistinguishable from an oversight. One line in the changelog, or a one-line cleanup at startup.

### The improvements the same review suggested (B38–B40)

- **B38 — Make the zone parameter required, so this class of bug cannot compile.** All three date and
  time formatters default their zone to `ZoneId.systemDefault()`, which is precisely what let B33 ship
  silently: omitting the argument is the default and looks like ordinary code. Making it required turns
  every future omission into a compile error, which is the difference between a test maybe catching it
  and the compiler always catching it. Small, and it retires the whole class.
- **B39 — Test what a screen renders, not only how it groups.** N25's grouping is well covered —
  `GroupByMonthTest` holds the Tokyo/London month boundary both ways — while no test asserts the date
  any screen actually displays, and there is no `HistoryFormat` test at all. The grouping was right and
  the rendering was wrong, so the thorough half was the half that did not need it.
- **B40 — Record the channel question rather than leaving it implicit.** Either delete `rest_timer` on
  the first launch after the upgrade, or say in [DECISIONS.md](DECISIONS.md) that a leftover channel is
  accepted. Smallest of the three and the one most likely to be forgotten, which is why it is written
  down rather than remembered.

- **B42 — Two pieces of documentation that no longer match the code.** The original `appendExercise`
  KDoc sits orphaned at `RoomWorkoutRepository.kt:140-144`, detached above `removeExercise` while the
  function it describes now lives at `:424`; and `WorkoutRepository.kt:79-83` says an open session is
  "refused rather than seeded" when it is in fact *resumed* without seeding, via `created = false`.
- **B43 — The repeat path has no end-to-end test.** `ActiveWorkoutViewModelTest.kt:104` puts only
  `templateId` in the `SavedStateHandle`, so `repeatLast` is always false and the branch at
  `ActiveWorkoutViewModel.kt:361-364` is never entered; `repeatedExerciseIds` is never assigned by any
  test. Two smaller gaps ride with it: no test asserts the action is *absent* while a session is already
  open (the existing absence test passes an empty `recent` list, which is the other rule), and no
  instrumented test covers an **unfinished** session being excluded — the "no finished workout" test
  seeds no rows at all, so dropping `finishedAt IS NOT NULL` from the query would still pass it.

### And the repeat path’s tail (B41–B43, with two smaller things)

- **"The last workout" has no tiebreaker.** Both the repeat query and `observeHistory` order by
  `finishedAt DESC` alone (`SessionExerciseDao.kt:55`, `WorkoutDao.kt:83`), so when two sessions share a
  `finishedAt` — plausible after a backup import, which round-trips the value — the workout at the top
  of Recent need not be the one Repeat picks. A secondary sort key on either side settles it.
- **A repeat can be offered that opens an empty session.** `canRepeat` only asks whether history is
  non-empty (`WorkoutsHomeScreen.kt:197`), while the query also requires live exercises, so a last
  session whose exercises were all deleted yields a button indistinguishable from *Start workout*.
  Contrived — finishing requires a logged set — but the two conditions should agree.

## Later (still self-contained)

Post-MVP, same local-only premise. Grouped by theme, ordered by value inside each.

This is where candidates live. One graduates to *Next* — gaining a `B#` or `N#` id and
a spelled-out decision — when it is picked up, and leaves for
[CHANGELOG.md](CHANGELOG.md) when it ships.

**Insight** — why the app gets opened between workouts
- **P2.4** Body measurements.

**Programming** — turns a logger into a plan
- **P3.3** Programs / mesocycles with scheduled deloads.
- **P3.5** Planned-versus-completed adherence over a longer window, and a calendar view —
  the weekly schedule itself shipped as **N16**, and session-level plan-versus-actual is
  **N20**.

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
