# Screen-off playback and earphone controls

## Status

Implemented. On 2026-10-13 the owner asked for the video to keep playing when the screen turns off,
and for pausing and resuming it by touching Bluetooth earphones "like in the normal YouTube app".
Asked whether free accounts should keep playing too (which means hiding the "screen is off" signal
from YouTube's page, against YouTube's terms and possibly broken by a later website change), the
owner chose "Everyone" on 2026-10-13. This does not authorize merging or publishing.

## Context / problem

- The app plays videos inside the YouTube mobile website in its one WebView
  (`SingleYouTubePage`). On `ON_STOP` (screen off, Home, Back at the root) it ran
  `controller.pause()`, which pauses every page `<video>`, and `webView.onPause()`. So the app
  itself stopped every video, even for YouTube Premium accounts.
- Without a foreground service, Android freezes or kills a stopped app, so audio could not
  continue reliably even if nothing paused it.
- Android routes earphone and headset buttons to the active media session. The app had none, and
  WebView does not create one, so earphone taps went to another app (or nowhere).
- YouTube's website pauses its own video when the page becomes hidden unless the account has
  YouTube Premium. Google confirmed in early 2026 that this block on mobile browsers is intended
  ([report](https://chipp.in/entertainment/no-more-free-background-play-google-patches-one-of-youtubes-biggest-mobile-loophole/)).

## Goals

- Earphone and headset play/pause (single touch) pauses and resumes the page video, in the app and
  with the screen off.
- A media notification (lock screen and shade) shows the video title with play/pause and a seek bar.
- The app no longer pauses a playing video itself when the screen turns off or the app goes to the
  background, and stays alive while the video plays.
- The YouTube page is always told it is visible, so it keeps playing for every account, not only
  Premium (owner decision 2026-10-13).

## Non-goals

- A setting to turn screen-off playback off (not asked for).
- Next/previous video, double-touch gestures, picture-in-picture.
- A second player or a new WebView (architecture invariant).

## User-visible behavior

- After a video starts playing, a "DualSub Replay" media notification appears with the video
  title, play/pause and a seek bar. Tapping it opens the app.
- Touching the earphones pauses a playing video and resumes a paused one; the notification button
  does the same.
- Screen off, Home or Back while a video plays: the video keeps playing, with or without Premium
  (inferred; not testable without a phone). Before, leaving the app always paused it.
- The notification goes away when the page leaves the video (search, home, another non-video page)
  or the app is swiped away from recents. After 10 minutes paused it becomes dismissible.

## Technical constraints / invariants

- One WebView only: the media session is a remote control for the page `<video>` through
  JavaScript, not a player (`docs/project/tech-stack.md`).
- Every new script re-checks the executing origin is `https:` + `*.youtube.com`.
- Android 13+: media-session notifications are exempt from the notification permission, so no new
  permission prompt. Android 14+: `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and
  `foregroundServiceType="mediaPlayback"`. Android 12+: starting a foreground service from the
  background can be refused; the service then posts its notification without going foreground.
- Text in `strings_player.xml` for every interface language.

## Proposed approach / plan

1. `androidx.media:media` for `MediaSessionCompat` and the `MediaStyle` notification (small,
   no player; Media3 would bring a `Player` abstraction the app does not need).
2. `playback/WebVideoPlayback.kt`: the page video's state (`WebVideoPlayback`), a parser for a
   small JavaScript state script, the session state/actions mapping, the "keep foreground" rule
   (playing, or paused under 10 minutes) and `WebVideoBridge` (latest state + command handler).
3. `SingleYouTubePage` polls that state once a second whatever the lifecycle, publishes it to the
   bridge, binds play/pause/seek commands to scripts, and starts the service when a video plays.
   On `ON_STOP` it only pauses the video and the WebView when nothing is playing.
4. `WEB_KEEP_PLAYING_WHEN_HIDDEN_SCRIPT` makes `document.hidden`/`visibilityState` report
   visible and stops `visibilitychange` at a window capture listener. It is a document-start
   script for `https://youtube.com` and `https://*.youtube.com` where WebView supports
   `DOCUMENT_START_SCRIPT`, and is also run after every YouTube page load.
5. `playback/WebVideoPlaybackService.kt`: a `mediaPlayback` foreground service that owns the media
   session, mirrors the bridge state into it and the notification, and stops when there is no video.

## Acceptance criteria

- [x] Session state/actions, title clean-up, script parsing and the 10-minute rule are unit tested
  (`WebVideoPlaybackTest`).
- [x] Every new script keeps the YouTube origin check (`WebVideoPlaybackTest`).
- [x] The manifest declares the service with `mediaPlayback` and the two permissions; lint passes.
- [ ] Owner's phone: earphone touch pauses and resumes, in the app and with the screen off.
- [ ] Owner's phone: the notification shows the title and its button works.
- [x] The always-visible script checks the YouTube origin and overrides the visibility signals
  (`WebVideoPlaybackTest`).
- [ ] Owner's phone, free account: the video keeps playing with the screen off and after Home.

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `testDebugUnitTest` passes, including `WebVideoPlaybackTest` | Cloud container, CI |
| Android lint/build | `formatCheck complexityCheck lintDebug assembleDebug assembleDebugAndroidTest` pass | Cloud container, CI |
| Managed-device/emulator | Existing `pixel2Api36` suite still passes (offline fixtures) | CI `managed-device-tests` |
| Physical-device/manual | Earphone touch, notification, screen off and Home with a free account | Owner, preview APK |
| Live YouTube | Same as above; not testable offline | Owner |

## Risks / edge cases

- `video.play()` without a fresh tap could be refused by WebView's gesture rule; the page video was
  already started by a tap, which normally unlocks it. Unverified on a device.
- Hiding the screen-off signal goes around a limit YouTube added on purpose and breaks YouTube's
  terms; a later YouTube website change (for example, checking focus or another signal) can make
  free accounts pause again. The owner accepted this.
- Leaving the app with Home or Back no longer pauses a playing video; pause it first, or with the
  notification.
- The state poll runs once a second while the app is in the background with a video page open.

## Release intent

`release:patch` (default; new user-visible behavior but no owner instruction for minor).

## Implementation result

As planned, plus the owner's "Everyone" choice (step 4). `androidx.media:media` 1.7.1 is the
only new dependency. Notifications go through one helper with `@SuppressLint("NotificationPermission")`
because Android exempts media-session notifications from the notification permission; the app
asks for no new permission at runtime. CI's APK check (`tools/report_apk.py`) forbade every
foreground service; it now allows only `FOREGROUND_SERVICE` with the `MEDIA_PLAYBACK` type and still
fails on any other foreground service type (`tools/tests/test_report_apk.py`).

## Validation result

- Passed in the cloud container (2026-10-13): `formatCheck complexityCheck testDebugUnitTest
  lintDebug assembleDebug assembleDebugAndroidTest`, including `WebVideoPlaybackTest` (11 tests).
- Not run here: managed-device tests (CI `managed-device-tests` runs them on the PR).
- Not verifiable here: earphone buttons, the notification, and screen-off playback on a real phone
  with live YouTube. These need the owner's test of the preview APK.
