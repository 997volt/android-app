# Working in this repository as an agent

This file is a **pointer, not a summary**. The project's rules are already written down
and are authoritative; anything here would be a second copy that drifts. Read those,
and treat this file as the map plus the handful of environment facts they do not cover.

## Where the truth lives

| Question | Read |
| --- | --- |
| What is planned, in order, with ids to reference in commits | [ROADMAP.md](ROADMAP.md) |
| What is settled, and the rules that apply to **every** change | [DECISIONS.md](DECISIONS.md) |
| What shipped, per version | [CHANGELOG.md](CHANGELOG.md) |
| How a release is cut, and its traps | [RELEASING.md](RELEASING.md) |
| Build, install on a phone, the local toolchain | [README.md](README.md) |

Shipped work leaves the roadmap for the changelog. Feature ids (`F#`, `B#`, `N#`,
`P#.#`) are stable and appear in commit messages.

## Environment facts that bite

`$HOME` is typically **not writable** here, so anything falling back to `~/.gradle` or
`~/.android` fails with `EACCES`. The repo ships a self-contained toolchain in the
gitignored `.toolchain/`; `gradlew` and `tools/android-env.sh` wire it up. For your own
shell, source it first:

```sh
. ./tools/android-env.sh
```

Use `tools/bin/adb`, not a system `adb`. Starting an emulator needs **wider sandbox
access** than the default (it opens `/dev/kvm`), although `adb`, screenshots and
`connectedDebugAndroidTest` work normally once one is running.

The `android-device-loop` skill covers the device loop in detail — build, install,
verify a screen rendered, read logcat, and the pitfalls for each. Load it before
touching a device or an emulator.

## Conventions that are easy to get wrong

These are not preferences; they are the existing ones, and matching them matters more
than improving on them in passing.

- **Errors are values.** Reads and writes return `DataResult`, never a thrown exception
  that disappears into a coroutine. This includes rethrowing `CancellationException`
  rather than swallowing it — see `dataResultOf` in
  [`DataResult.kt`](app/src/main/java/com/example/androidapp/domain/DataResult.kt).
  `runCatching` catches `Throwable` and so swallows cancellation; do not use it in a
  `suspend` function.
- **No mocking framework, and no new test dependency without asking.** Tests use
  hand-written fakes (`FakeWorkoutRepository`, `FakeTemplateRepository`) and JUnit 4
  assertions. Compose UI tests run on the JVM under Robolectric; the instrumented
  suite is for Room, DAOs and migrations.
- **Migrations are numbered as they ship.** Copy the SQL from Room's generated
  `createSql` rather than hand-writing an equivalent, export the schema, add a
  `MigrationTestHelper` test that seeds real rows, and register it in `ALL_MIGRATIONS`.
  Do not add a column ahead of the code that reads it, and do not edit a shipped
  migration.
- **Enums are stored by name, never ordinal**; weights are whole grams in a `Long`;
  RPE is half-points in an `Int`. The reasons are in [DECISIONS.md](DECISIONS.md).
- **Delete an API the moment nothing calls it**, and extract a shared component at its
  second caller rather than its first.
- **Accessibility accompanies each screen**, not a later pass: name what a control does,
  announce state changes, and address controls by `TestTags` rather than English
  literals.

## Before you call it done

`lint` runs with `warningsAsErrors`, and detekt carries the Compose ruleset, so both
fail rather than warn. Run the same set CI runs:

```sh
./gradlew testDebugUnitTest lint detekt assembleDebug assembleRelease --console=plain
```

The configuration cache is on by default (`gradle.properties`), so a repeat run is fast.

Two things to know about the instrumented job. `./gradlew connectedDebugAndroidTest`
**needs a device**, so it cannot be the thing you rely on by default. And a green run is
not by itself evidence that everything ran — `tools/ci-check-instrumented.py` exists
because a JUnit report with no failures looks exactly like a complete one, and it fails
the job when the executed count disagrees with the declared one.

## Hard constraints — do not break these

- **Local only.** No `INTERNET` permission, no accounts, no analytics, no server. The
  exported backup file is the only path off the device.
- **No Google Play services at runtime.** A degoogled device must run the app.
- **The user's data is health data.** It stays in app-private storage and leaves only
  inside an export the user chose to make.

## Finishing a change

The commit-message convention is in the history — a `type: what changed (ids)` subject,
with the *reason* in the body, including why a rejected alternative was rejected. If a
change is not covered by an id in [ROADMAP.md](ROADMAP.md), say so rather than inventing
one.
