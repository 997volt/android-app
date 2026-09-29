# Releasing

One rule matters more than everything else here:

> **The tag must point at the commit that built the APK.**

Get that wrong and `git checkout v1.2` silently disagrees with the binary people
downloaded. Nothing tests it, no build fails, and it stays wrong. The rest of this
file exists to keep that true.

This was written after the v1.2 release, which nearly went wrong twice — see
[Traps](#traps).

## The procedure

### 1. Decide the version

[`version.properties`](version.properties) is the single source of truth for both
`versionCode` and `versionName`; the build fails loudly if either is missing.

**`versionCode` must increase.** Android compares it and nothing else — publish a
lower one and no phone will accept the update.

### 2. Write the changelog *before* the bump

Add a `CHANGELOG.md` entry for the new version, and **list only what is actually in
this build**. Check which commits landed before the commit you are about to tag:

```bash
git log --oneline <last-release-tag>..HEAD
```

An entry credited to the wrong release is the same class of error as a tag that
does not match its binary: invisible, and misleading exactly to the person who
trusts it.

### 3. Bump, commit

```bash
# edit version.properties, then:
git add -A && git commit -m "release: 1.3 (versionCode 4)"
```

### 4. Build the signed APK

```bash
./tools/build-apk.sh
```

It refuses to hand over an unsigned APK, which would install for nobody. It reads
the gitignored `keystore.properties`; see
[Install on your own phone](README.md#install-on-your-own-phone) if that is missing.

> The build can fail intermittently under memory pressure on a small machine
> (`hiltJavaCompile*`, `expandReleaseArtProfileWildcards`, the lint worker). It is
> not a code problem — run it again.

### 5. Verify, before tagging

```bash
APK=app/build/outputs/apk/release/app-release.apk
BT=.toolchain/android-sdk/build-tools/36.0.0    # or the runner's build-tools

$BT/aapt2 dump badging "$APK" | grep '^package:'          # versionCode/versionName
$BT/apksigner verify --print-certs "$APK" | grep SHA-256  # must match the last release
sha256sum "$APK"                                          # goes in the release notes
```

**The certificate must be unchanged.** Any other value means the APK will be
refused as an update and the only fix is an uninstall — which deletes the workout
history, because platform backup is off. If it differs, stop and work out why.

### 6. Tag the commit you built

```bash
git tag -a v1.3 -m "Workout Log 1.3 (versionCode 4)

sha256  <from step 5>"
git push origin main
git push origin v1.3
```

**If `main` has moved since you branched, merge it in first** — `git merge
origin/main`, never `git rebase`. A rebase rewrites the release commit, changes its
SHA, and orphans the tag. Merging keeps the commit identity, and therefore the tag,
valid.

### 7. Publish the release

**Releases → Draft a new release** → pick the tag → title it `Workout Log 1.3` →
paste the notes → attach the APK → Publish.

Make sure the attached file's name and `sha256` in the notes agree with step 5:

```
**Version:** 1.3 (versionCode 4)
**File:** workout-log-1.3.apk — <bytes> bytes
**SHA-256:** <from step 5>
**Requires:** Android 8.0+ (API 26)

Install with `adb install -r workout-log-1.3.apk`, or copy it to the phone and tap
it. Signed with the same key as previous releases, so it upgrades in place — do not
uninstall first, which would delete the history.
```

## Traps

Collected from the v1.2 release, all of them real:

- **A rejected `main` push.** `git push` fails as a non-fast-forward when someone
  merged to `main` while you were working. The tag may already be up by then,
  pointing at a commit that is not on the branch. **Merge, don't rebase** (step 6)
  and re-push; the tag then becomes reachable without changing.
- **A changelog that credited the wrong version.** The v1.2 notes initially
  attributed two changes to 1.1 that landed after the 1.1 build. Check with `git
  log` (step 2) rather than memory.
- **"Generate release notes" in the GitHub UI.** It builds notes from merged PRs
  and commit subjects, which here means `chore(deps): …` and internal refactors.
  Write notes for the person downloading the APK instead.
- **An unsigned APK in the output directory.** CI signs nothing, so
  `app/build/outputs/apk/release/` can contain `app-release-unsigned.apk` after a
  CI-shaped build. Check the filename before handing anything to anyone.
- **Deleting or re-pushing a published tag.** Safe only before anyone has fetched
  it. Once a release exists against a tag, treat it as immutable and cut a new
  version instead.

## Why this is manual (decided)

CI builds a release APK to prove R8 succeeds, but signs nothing, so it cannot attach
an artifact to a release. Automating steps 4–7 would mean putting `workout.jks` **and
its passwords** into GitHub Secrets.

**Decided against, for now.** The automation has a genuine engineering benefit: CI
building from the tag would make the tag/APK mismatch impossible *by construction*,
rather than merely documented in this file. That was weighed against moving a
permanent signing key into a third party's store, where any workflow in this
repository could reach it — for an app released a few times a year. Ten documented
minutes is the cheaper side of that trade.

If the cadence ever changes, do it as a **GitHub Environment secret with required
reviewers**, so a human approves before any job can read the key. A raw repository
secret readable by any workflow is the version that should stay unbuilt.
