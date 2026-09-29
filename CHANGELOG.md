# Changelog

Notable changes to Workout Log, newest first. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are the
`versionName` from [`version.properties`](version.properties), with the
`versionCode` in brackets because that is what Android actually compares.

## [Unreleased]

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
