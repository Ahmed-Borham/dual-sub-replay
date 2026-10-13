# Lighter caption watcher on the YouTube page

## Status

Implemented. On 2026-10-13 the owner asked whether the app could use the native YouTube
Android app instead of the YouTube web page, because the page sometimes feels less smooth.
After discussing options (project thread "Native YouTube app idea"), the owner picked
"Smoother page": measure what our page scripts cost and trim them, keeping the single-WebView
design. That authorizes this scope, not merge or release.

## Context / problem

- The native YouTube app cannot host our features without modifying YouTube itself, which
  breaks YouTube's terms and every YouTube update. A floating overlay over the YouTube app and
  a separate player were discussed and not chosen.
- Our page scripts ([`YouTubeWebScripts.kt`](../../app/src/main/java/com/kienhoang/dualsubreplay/ui/YouTubeWebScripts.kt))
  add work on the page's main thread: a snapshot poll every 100 ms, and a `MutationObserver`
  over the whole document that reads the captions after **every** DOM change. YouTube changes
  its DOM constantly (time display, feed and comments loading while scrolling), so the observer
  ran its full caption read (document-wide caption query, `getPlayerResponse()`, `getOption()`)
  for changes that have nothing to do with captions.
- The live YouTube page cannot be measured from cloud containers (Google answers with its
  "unusual traffic" check), so the baseline below comes from a YouTube-like offline page.

## Goals

- The caption observer does its full read only when a mutation touches captions.
- Caption capture, its timestamps and every existing page behavior stay the same.

## Non-goals

- Replacing the 100 ms snapshot poll or the JavaScript polling bridge (an architecture
  invariant), and changing what the poll reports.
- Using the native YouTube app, an overlay over it, or a separate player.

## User-visible behavior

No visible change. The YouTube page has less of our work to do while it plays and scrolls,
which can only make it smoother; on the test page the difference is small (see results).

## Technical constraints / invariants

- One WebView and the polling bridge stay ([AGENTS.md](../../AGENTS.md#architecture-invariants-enforced-by-tests)).
- Scripts keep re-checking the executing origin; `PlaybackArchitectureTest` asserts literal text.
- The snapshot poll still calls `recordCaption()` every 100 ms, so anything the filter skips
  (a video or language change without caption DOM changes) is still read within one poll.

## Proposed approach / plan

1. In `webLiveCaptionConfigurationScript`, add `state.touchesCaptions(records)`: a record counts
   when its target is inside a caption element (`.ytp-caption-segment`,
   `.ytp-caption-window-container`, `.caption-window`, the same ones `visibleCaptionText`
   reads), or when an added or removed node is or contains one.
2. The observer calls `recordCaption()` only when `touchesCaptions` is true.
3. Add a literal-text assertion to `PlaybackArchitectureTest`.

## Acceptance criteria

- [x] On the offline YouTube-like page (4x CPU slowdown), the observer's time drops while the
  number of captions recorded stays the same.
- [x] Caption text changes, `characterData` edits, removal of the caption container and a newly
  added caption container are each recorded right away, exactly as before.
- [x] Unrelated mutations (time display, feed items) no longer trigger a caption read.
- [x] Existing unit and instrumented script tests pass.

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `testDebugUnitTest`, including `PlaybackArchitectureTest` | Cloud container |
| Android lint/build | `formatCheck complexityCheck lintDebug assembleDebug assembleDebugAndroidTest` | Cloud container / PR CI |
| Managed-device/emulator | `BrowseWebViewLifecycleTest.liveCaptionObserverReadsMutationsAndRestoresCaptionButton` and the other WebView fixtures | PR CI `managed-device-tests` |
| Behavior check | Headless Chromium: each caption change kind bumps the revision with the old and new script alike | Cloud container (Playwright) |
| Performance (baseline/target) | Headless Chromium, Pixel 7 emulation, 4x CPU slowdown, 15 s on a 2,800-node YouTube-like page (time display every 250 ms, captions every 2 s, 10 feed items every 1.5 s): observer time before vs after | Cloud container (Playwright) |
| Physical-device/manual | Owner compares smoothness in the preview APK | Owner's phone |
| Live YouTube | Not measurable from the cloud (Google bot check) | Owner's phone |

## Risks / edge cases

- A caption layout that YouTube renders under different class names is read only by the poll,
  up to 100 ms later. `visibleCaptionText` already reads only these classes, so such captions
  were not captured before either.

## Release intent

`release:patch` (app code change). This change rides in the shared PR owned by the
"Background play and earphone controls" thread (owner preference: one PR for concurrent
fixes), so that PR's release metadata applies.

## Implementation result

Implemented as planned in `YouTubeWebScripts.kt` and `PlaybackArchitectureTest.kt`. No
deviations.

## Validation result

- Performance, observer time per 15 s on the offline page (three runs each): before 60, 73,
  240 ms; after 22, 18, 15 ms. Captions recorded: 9 in every run, before and after. The
  100 ms snapshot poll is unchanged at about 2.6 to 3.8 ms per call (roughly 3% of the main
  thread at 4x slowdown), so it, not the observer, is now our largest page cost.
- Behavior check: the old and new scripts give identical revisions and text for an unrelated
  change, a text change, container removal, a new container and a `characterData` edit.
- Unit tests, lint, build and managed-device tests: see the shared PR's CI.
- Not verified: live YouTube and the owner's phone. Offline numbers do not prove the live page
  feels smoother.
