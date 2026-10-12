# Remove the F-Droid build and speed up CI

## Status

Implemented. On 2026-10-12 the owner asked to "remove F-Droid and anything related to it
completely" and to apply every CI change recommended in the CI review
(Kien, project thread "CI and tests review"). That authorizes this scope, not merge or release.

## Context / problem

- The owner no longer wants the app on F-Droid. The repository still carries a second build
  (`-Pdistribution=fdroid`) with Mozilla's Bergamot engine compiled from two git submodules,
  F-Droid store metadata, an F-Droid tagging workflow, and two CI jobs for it.
- CI review (2026-10-12, last 100 Android CI runs): median 15.3 min per push. The two F-Droid
  jobs took about 14.6 and 14.9 min, about 11 of which was compiling Bergamot.
  `managed-device-tests` waited for `verify-build`, so that chain alone took about 14 min.
- Since 2026-08-28 Android CI only runs on pull requests. `gradle/actions/setup-gradle` saves
  caches only from `main`, so every PR restored a six-week-old Gradle cache and downloaded
  newer tools (for example ktlint 1.7.1) from Maven Central on every run. That download failed
  on PR #112 and forced a rerun.
- GitHub warns on every job that the pinned actions run on deprecated Node.js 20 and that
  `actions/setup-java` v4 is deprecated.

## Goals

- One build: the GitHub build (Google Translate online by default, ML Kit on-device, AI
  assistant). No F-Droid variant, engine, submodules, metadata, workflow, tools, tests or docs.
- Android CI saves fresh Gradle caches from `main`, and PR runs need about half the time.
- Release automation requires exactly the Android CI jobs that still exist.

## Non-goals

- Changing anything the GitHub APK does. Behavior that the F-Droid review prompted but that also
  applies to the GitHub build stays: backup exclusions for the WebView profile and AI data, the
  licence screen, Safe Browsing on.
- Rewriting older specs. Dated specs are historical records; their F-Droid mentions stay.
- Deleting the existing `fdroid-v*` git tags or the fdroiddata merge request on GitLab. Those
  live outside this repository's files and are the owner's call.

## User-visible behavior

None for app users: the GitHub APK keeps every feature. CI is faster and has two jobs instead
of four. The "Send release to F-Droid" workflow is gone from the Actions tab.

## Technical constraints / invariants

- `tools/automatic_release.py` `REQUIRED_JOBS` must equal the jobs in `android.yml`
  (`test_automatic_release.py` checks it). It only accepts `pull_request` runs, so CI runs on
  `main` pushes never count as PR CI. `pr-preview-auto.yml` also only publishes `pull_request`
  runs.
- No user-visible string literals in Kotlin; `AppLanguageTest` and lint keep translations aligned.
- The single-WebView, caption-provider and AI-assistant invariants in AGENTS.md are untouched.

## Proposed approach / plan

1. Build: drop the `distribution` property, Bergamot native build, licence-notice generation and
   `ONLINE_TRANSLATION` / `AI_ASSISTANT` / `WEBVIEW_SAFE_BROWSING` flags (always on). Move
   `app/src/full` into `app/src/main`.
2. Delete `app/src/fdroid` (Kotlin and the `cpp` submodules), `app/src/fdroidAndroidTest`,
   `.gitmodules`, `BergamotCatalog.kt` and its test, `fastlane/`, `docs/fdroid/`,
   `tools/fdroid_tag.py`, `tools/fetch_bergamot_test_models.py`, their tests,
   `send-to-fdroid.yml`, and the three F-Droid-only specs.
3. Simplify code that branched on the build: translation engine default, AI action refusal,
   AI app snapshot, the Google Translate settings row, the assistant switch, Safe Browsing,
   backup rules.
4. CI: remove `fdroid-build` and `fdroid-device-tests`; run on `push` to `main`; start
   `managed-device-tests` without waiting for `verify-build`; update action pins to their
   Node 24 releases (checkout v6, setup-java v5, cache v5, upload-artifact v6,
   download-artifact v7, setup-gradle v6, setup-android v4); `REQUIRED_JOBS` to the two
   remaining jobs.
5. Docs: AGENTS.md, README, PRIVACY, THIRD_PARTY_NOTICES, mission, tech-stack, releasing,
   specs README.

## Acceptance criteria

- [x] `git grep -i -E "fdroid|f-droid|bergamot"` finds only older dated specs and this spec.
- [x] `formatCheck complexityCheck testDebugUnitTest lintDebug assembleDebug
  assembleDebugAndroidTest` and `assembleRelease` pass.
- [x] `python3 -m unittest discover -s tools/tests -p 'test_*.py'` passes, including the check
  that `REQUIRED_JOBS` matches `android.yml`.
- [ ] Android CI on the PR runs only `verify-build` and `managed-device-tests`, both green,
  in parallel.
- [x] The release APK still contains ML Kit and Safe Browsing stays on (`report_apk.py`,
  `aapt2 dump xmltree`; `BrowseWebViewLifecycleTest` runs in PR CI).

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `testDebugUnitTest`, all pass | Cloud container |
| Android lint/build | `formatCheck complexityCheck lintDebug assembleDebug assembleDebugAndroidTest assembleRelease` | Cloud container and PR CI |
| Managed-device/emulator | `pixel2Api36DebugAndroidTest`, all pass | PR CI (`managed-device-tests`) |
| Workflow/release tools | `python3 -m unittest discover -s tools/tests -p 'test_*.py' -v`; YAML parses | Cloud container and PR CI |
| Performance (baseline/target) | Baseline median 15.3 min wall per PR push (79 runs). Target: under 10 min once `main` has saved caches | PR CI after merge; the first PR run still uses the old cache |
| Physical-device/manual | Owner installs the preview APK and checks translation and the AI assistant still work | Owner's phone |
| Live YouTube | Not applicable: no playback or caption change | |

## Risks / edge cases

- A fresh `main` cache only exists after this PR merges, so this PR's own CI run is not yet
  faster for cache reasons; it is faster because the F-Droid jobs are gone and the emulator job
  starts at once.
- If `verify-build` fails, the emulator job now still runs. The repository is public, so runner
  minutes are free; the result is just reported alongside.
- Older merged PRs retried through release-on-main are checked against the new `REQUIRED_JOBS`;
  their runs have all four jobs, which include the two required ones.

## Release intent

`release:patch` (default, no label). App code and workflows change, so the docs-only skip rule
does not apply, and the GitHub APK keeps the same features.

## Implementation result

- Deleted the F-Droid variant (`app/src/fdroid`, its `cpp` submodules and `.gitmodules`,
  `app/src/fdroidAndroidTest`), `BergamotCatalog.kt` and its test, `fastlane/`, `docs/fdroid/`,
  `tools/fdroid_tag.py`, `tools/fetch_bergamot_test_models.py`, `send-to-fdroid.yml` and the three
  F-Droid-only specs. `app/src/full` moved into `app/src/main`; its licence notice is now
  `assets/licenses/translation.txt`.
- `app/build.gradle.kts` has no `distribution` property, native build, engine licence task or
  build flags. Safe Browsing is a literal `true` in the manifest and WebView settings.
- `TranslationEngine.kt`: `storedTranslationEngine(raw)` with `DEFAULT_TRANSLATION_ENGINE`
  (Google). The Google Translate switch shows whenever Settings has engine actions; the
  assistant's "This build only translates on the device" refusal and the snapshot's
  "GitHub/F-Droid build" field are gone; the assistant view model is always created.
- Backup rules no longer list `translation-models/` (only the F-Droid engine wrote there).
- CI: two jobs in parallel, `push` to `main` added, every pinned action moved to a Node 24
  release (also `pages.yml`, `pr-preview-auto.yml`, `release-on-main.yml`, including
  `upload-pages-artifact` v5.0.0, `deploy-pages` v5.0.1 and `action-gh-release` v3.0.3, whose
  only change from v2 is the Node 24 runtime), and the SDK licence text is no longer logged.
- Deviation: the CI review's ccache option for the Bergamot build is moot now that the engine is
  gone.

## Validation result

- Passed (cloud container, JDK 21 running Gradle 9.5 with the JDK 17 toolchain settings):
  `formatCheck complexityCheck testDebugUnitTest lintDebug assembleDebug
  assembleDebugAndroidTest` (654 unit tests, 0 failures) and
  `assembleRelease -PtestReleaseSigning=true`; `tools/report_apk.py` passed (30.9 MB, no forbidden
  permissions or components, `libtranslate_jni.so` present); `aapt2` shows
  `EnableSafeBrowsing=true`.
- Passed: `python3 -m unittest discover -s tools/tests -p 'test_*.py'` (31 tests); all workflow
  YAML parses.
- Not run locally: managed-device tests (no emulator here); PR CI runs them.
- Pending: PR CI timing, and the first post-merge `main` run that saves fresh caches.
- Owner check: install the preview APK and confirm Google Translate, on-device translation and the
  AI assistant still work.
