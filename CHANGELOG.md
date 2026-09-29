# Changelog

Notable changes to Workout Log, newest first. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are the
`versionName` from [`version.properties`](version.properties), with the
`versionCode` in brackets because that is what Android actually compares.

## [Unreleased]

### Added
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
