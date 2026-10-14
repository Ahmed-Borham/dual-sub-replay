package com.kienhoang.dualsubreplay.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kienhoang.dualsubreplay.data.SubtitleSegment

internal const val AUTO_PAUSE_PREFERENCE = "learning_auto_pause"
internal const val TAP_PLAY_PAUSE_PREFERENCE = "learning_tap_play_pause"
internal const val SWIPE_LINES_PREFERENCE = "learning_swipe_lines"
private const val LEARNING_PREFERENCES_NAME = "dual_sub_preferences"

/** Auto pause waits this long after the last spoken word so the word is not cut off. */
private const val AUTO_PAUSE_TAIL_MS = 200L

@Composable
private fun rememberLearningFlag(key: String): State<Boolean> {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(LEARNING_PREFERENCES_NAME, 0) }
    val flag = remember(preferences, key) { mutableStateOf(preferences.getBoolean(key, true)) }
    DisposableEffect(preferences, key) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                if (changedKey == key) flag.value = sharedPreferences.getBoolean(key, true)
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return flag
}

/**
 * Sends the subtitle line times and the three switches (auto pause, tap to play or pause, swipe to
 * change line) to the YouTube page. The page script does the pausing and the touch handling.
 */
@Composable
internal fun BindLearningControls(
    segments: List<SubtitleSegment>,
    videoId: String?,
    controller: YouTubeWebController,
) {
    val autoPause by rememberLearningFlag(AUTO_PAUSE_PREFERENCE)
    val tapPlayPause by rememberLearningFlag(TAP_PLAY_PAUSE_PREFERENCE)
    val swipeLines by rememberLearningFlag(SWIPE_LINES_PREFERENCE)
    LaunchedEffect(
        videoId,
        segments.size,
        segments.firstOrNull()?.startMs,
        segments.lastOrNull()?.endMs,
        autoPause,
        tapPlayPause,
        swipeLines,
    ) {
        controller.configureLearning(
            webLearningControlsScript(segments, autoPause, tapPlayPause, swipeLines),
        )
    }
}

@Composable
internal fun LearningControlsSettings() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(LEARNING_PREFERENCES_NAME, 0) }
    val autoPause by rememberLearningFlag(AUTO_PAUSE_PREFERENCE)
    val tapPlayPause by rememberLearningFlag(TAP_PLAY_PAUSE_PREFERENCE)
    val swipeLines by rememberLearningFlag(SWIPE_LINES_PREFERENCE)
    SettingsSubheading("Learning controls")
    SettingsSwitchRow(
        title = "Auto pause",
        description = "Pause at the end of each subtitle line. Play repeats the same line; " +
            "swipe or tap a line to move on.",
        checked = autoPause,
        onCheckedChange = { preferences.edit().putBoolean(AUTO_PAUSE_PREFERENCE, it).apply() },
        testTag = "learning_auto_pause_switch",
    )
    SettingsSwitchRow(
        title = "Tap video to play or pause",
        description = "One tap on the video toggles playback.",
        checked = tapPlayPause,
        onCheckedChange = { preferences.edit().putBoolean(TAP_PLAY_PAUSE_PREFERENCE, it).apply() },
        testTag = "learning_tap_play_pause_switch",
    )
    SettingsSwitchRow(
        title = "Swipe video to change line",
        description = "Swipe right for the previous line, left for the next. The line plays at once.",
        checked = swipeLines,
        onCheckedChange = { preferences.edit().putBoolean(SWIPE_LINES_PREFERENCE, it).apply() },
        testTag = "learning_swipe_lines_switch",
    )
}

/** The moment auto pause stops a line: just after its last spoken word, never after its display end. */
internal fun learningPauseMs(segment: SubtitleSegment): Long {
    val lastWordEnd = segment.words.lastOrNull()?.endMs ?: return segment.endMs
    val latest = segment.endMs.coerceAtLeast(segment.startMs + 1)
    return (lastWordEnd + AUTO_PAUSE_TAIL_MS).coerceIn(segment.startMs + 1, latest)
}

internal fun webLearningControlsScript(
    segments: List<SubtitleSegment>,
    autoPause: Boolean,
    tapPlayPause: Boolean,
    swipeLines: Boolean,
): String {
    val lines = StringBuilder("[")
    segments.forEachIndexed { index, segment ->
        if (index > 0) lines.append(',')
        lines.append('[')
            .append(segment.startMs / 1000.0)
            .append(',')
            .append(learningPauseMs(segment) / 1000.0)
            .append(']')
    }
    lines.append(']')
    return LEARNING_CONTROLS_SCRIPT
        .replace("__SEGMENTS__", lines.toString())
        .replace("__AUTO__", autoPause.toString())
        .replace("__TAP__", tapPlayPause.toString())
        .replace("__SWIPE__", swipeLines.toString())
}

/**
 * Runs inside the YouTube page. Installed once per page; later calls only replace the line times and
 * switches. Auto pause pauses at the end of the current line and rewinds to its start, so Play
 * repeats it; a swipe moves to the previous or next line, plays it and pauses at its end.
 */
private val LEARNING_CONTROLS_SCRIPT: String =
    """
(function() {
  var host = String(location.hostname || '').toLowerCase();
  if (location.protocol !== 'https:' ||
      !(host === 'youtube.com' || host.slice(-12) === '.youtube.com')) return false;
  var S = window.__dualSubLearning;
  if (!S) {
    S = window.__dualSubLearning = { segs: [], cur: -1, lastT: -1, auto: false, tap: false, swipe: false };
    installLearning(S);
  }
  S.segs = __SEGMENTS__;
  S.auto = __AUTO__;
  S.tap = __TAP__;
  S.swipe = __SWIPE__;
  S.cur = -1;
  S.lastT = -1;
  return true;

  function installLearning(S) {
    function pageVideo() {
      var list = Array.prototype.slice.call(document.querySelectorAll('video'));
      var playing = list.filter(function(item) { return !item.paused && !item.ended; })[0];
      var ready = list.filter(function(item) { return item.readyState > 0; })[0];
      return playing || ready || list[0] || null;
    }
    function startPlaying(video) {
      var request = video.play();
      if (request && request.catch) request.catch(function() {});
    }
    // Index of the last line that starts at or before the time, or -1 before the first line.
    function lineAt(time) {
      var lines = S.segs;
      var low = 0;
      var high = lines.length - 1;
      var found = -1;
      while (low <= high) {
        var mid = (low + high) >> 1;
        if (lines[mid][0] <= time + 0.05) { found = mid; low = mid + 1; } else { high = mid - 1; }
      }
      return found;
    }
    function goToLine(step) {
      var video = pageVideo();
      if (!video || !S.segs.length) return;
      var base = S.cur >= 0 ? S.cur : lineAt(video.currentTime);
      if (base < 0) base = step > 0 ? -1 : 0;
      var target = base + step;
      if (target < 0 || target >= S.segs.length) return;
      S.cur = target;
      video.currentTime = S.segs[target][0];
      S.lastT = video.currentTime;
      startPlaying(video);
    }
    function togglePlay() {
      var video = pageVideo();
      if (!video) return;
      if (video.paused) startPlaying(video); else video.pause();
    }
    function tick() {
      if (!S.segs.length) return;
      var video = pageVideo();
      if (!video) return;
      if (document.querySelector('.ad-showing')) { S.cur = -1; S.lastT = -1; return; }
      var time = video.currentTime;
      var jumped = S.lastT < 0 || Math.abs(time - S.lastT) > 1.0;
      S.lastT = time;
      if (video.seeking) return;
      if (!S.auto) { S.cur = lineAt(time); return; }
      if (S.cur < 0 || jumped) S.cur = lineAt(time);
      if (video.paused || video.ended) return;
      var line = S.segs[S.cur];
      if (!line) return;
      if (time >= line[1]) {
        video.pause();
        video.currentTime = line[0];
        S.lastT = video.currentTime;
      }
    }
    var CONTROL = '.ytp-chrome-bottom,.ytp-chrome-top,.ytp-popup,.ytp-settings-menu,.ytp-ce-element,' +
      '.ytp-ad-module,button,a,input,select,textarea,[role="button"],[role="slider"],[role="dialog"]';
    function insidePlayer(element) {
      return !!element && !!element.closest && !!element.closest('.html5-video-player');
    }
    function onControl(element) {
      return !!element && !!element.closest && !!element.closest(CONTROL);
    }
    var touch = null;
    document.addEventListener('touchstart', function(event) {
      if (!event.touches || event.touches.length !== 1) { touch = null; return; }
      var point = event.touches[0];
      touch = { x: point.clientX, y: point.clientY, at: Date.now(), target: event.target };
    }, true);
    document.addEventListener('touchend', function(event) {
      var start = touch;
      touch = null;
      if (!start || !(S.tap || S.swipe)) return;
      if (!insidePlayer(start.target) || onControl(start.target)) return;
      var point = event.changedTouches && event.changedTouches[0];
      if (!point) return;
      var dx = point.clientX - start.x;
      var dy = point.clientY - start.y;
      var elapsed = Date.now() - start.at;
      if (S.swipe && elapsed < 900 && Math.abs(dx) >= 60 && Math.abs(dx) > 1.5 * Math.abs(dy)) {
        goToLine(dx > 0 ? -1 : 1);
        return;
      }
      if (S.tap && elapsed < 400 && Math.abs(dx) < 12 && Math.abs(dy) < 12) togglePlay();
    }, true);
    S.tick = tick;
    S.go = goToLine;
    S.timer = setInterval(tick, 30);
  }
})();
    """.trimIndent()
