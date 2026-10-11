# Pronunciation volume

## Status

Implemented. On 2026-10-11 the owner reported that spoken words sound quieter than in the previous
version and asked to "fix it back to normal, and have new setting so users can adjust it too". The
owner wants every fix of the day in one PR, so this rides the same PR as the
[Claude and Gemini default change](2026-10-11-gemini-flash-lite-default-and-claude.md). This does
not authorize merging or publishing.

## Context / problem

- Between v1.5.3 and this branch, the only pronunciation change is which text is spoken (Japanese
  kana readings, [spec](2026-10-11-speak-japanese-readings.md)); nothing changed the volume in code.
- Words are recorded to a WAV file with the phone's speech engine (`synthesizeToFile`) and played
  with `MediaPlayer` at the media volume. The recording is played as the engine wrote it, and engines
  and voices record at very different levels; some sit far below full scale, so words sound quiet even
  at a high media volume. Playing directly (`tts.speak`) lets the engine apply its own level instead.
- The preview APK installs as a separate app (`.preview`), and some phones keep a volume per app, so
  the preview can start quieter than the installed release. This is inferred, not verified on the
  owner's phone.
- There was no way for a learner to make words louder than the media volume.

## Goals

- Recorded words play at a steady, full loudness whatever the engine's recording level.
- A "Pronunciation volume" slider (25 % to 200 %, 25 % steps, 100 % by default) in More settings →
  Word learning. 100 % follows the media volume; below plays quieter; above plays louder.

## Non-goals

- No change to which engine or voice is used, to the cache, or to the text spoken.
- No separate audio stream or change to the phone's own volume.

## User-visible behavior

- Tapping a word speaks it as loud as the media volume allows, also with engines that record quietly.
- More settings → Word learning shows "Pronunciation volume: 100%" with a slider under "Pronounce
  tapped words". The new level applies to the next word. "Reset all settings" returns it to 100 %.
- When an engine cannot record and speaks directly, the setting can lower the volume but not raise it
  above the media volume.

## Technical constraints / invariants

- Interface text in `strings_settings.xml` for every language (`AppLanguageTest`, lint).
- Unit tests stay plain JUnit; the WAV and gain logic are `internal` functions in `ui/PronunciationVolume.kt`.

## Proposed approach / plan

1. After a successful recording, `normalizeSpeechFile` reads the 16-bit PCM WAV, finds its loudest
   sample and raises the whole recording so that sample reaches about -1 dBFS (at most ×4, never
   lowered), rounding off samples above 80 % instead of clipping. Other formats are left as they are.
2. `playSpeechFile(file, volume)` sets the player volume to `min(volume, 1)`; above 100 % a
   `LoudnessEnhancer` on the player's session adds `2000·log10(volume)` millibels (+6 dB at 200 %).
   Phones without that effect play at 100 %.
3. Direct speech passes `min(volume, 1)` as the engine's volume parameter.
4. The slider saves `pronunciation_volume` in `dual_sub_preferences`; `WordPronouncer` reads it for
   each word. The key is in `RESETTABLE_SETTING_KEYS`.

## Acceptance criteria

- [x] A quiet 16-bit recording is raised toward full scale without clipping; a loud one is unchanged;
  non-PCM files are left alone (`PronunciationVolumeTest`).
- [x] The setting is clamped to 25–200 %, maps to player volume and extra gain as above
  (`PronunciationVolumeTest`).
- [x] The slider appears in Word learning, shows the percentage and saves the value
  (`LearningPlayerUiTest.pronunciationVolumeIsSavedFromWordLearningSettings`).
- [x] "Reset all settings" clears the key (`SubtitleHighlightTest`).
- [ ] Words sound as loud as before, and 200 % is clearly louder, on the owner's phone (preview APK).

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `testDebugUnitTest` passes, including `PronunciationVolumeTest` | Cloud container, CI |
| Android lint/build | `formatCheck complexityCheck lintDebug assembleDebug assembleDebugAndroidTest` pass | Cloud container, CI |
| Managed-device/emulator | `LearningPlayerUiTest` passes on `pixel2Api36` | CI `managed-device-tests` |
| Physical-device/manual | Tap words at 100 % and 200 %; compare with the release app at the same media volume | Owner, preview APK |
| Live YouTube | Not applicable | — |

## Risks / edge cases

- Raising a very quiet recording also raises its background noise; the ×4 cap limits that.
- `LoudnessEnhancer` is missing or fails on some phones; playback then continues at 100 %.
- An engine that writes a WAV header with a wrong data size is handled by reading what is there.

## Release intent

`release:patch` — the standing default for this PR.

## Implementation result

As planned. The slider reads and writes the preference directly, like the overlay switches, so no
new view-model state was needed.

## Validation result

See the PR for final-head CI. Local results are recorded in the PR description.
