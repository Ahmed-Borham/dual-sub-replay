# Speak Japanese words from their reading

## Status

Implemented. The owner chose "Speak the reading" on 2026-10-11 after asking why tapped words are
sometimes pronounced wrong; that choice authorizes this scope, not merge or release.

## Context / problem

Every speaker button (word card, its per-word parts, the selection bar's Pronounce and
"Pronounce tapped words", saved words) handed the word to Android's speech engine exactly as
written, in kanji. Many kanji words have several readings (今日 きょう / こんにち, 一日 いちにち /
ついたち, 方 かた / ほう, 辛い からい / つらい), and with a single word and no sentence the engine
guesses. The app already knows the reading: Kuromoji analyzes the whole subtitle line and the card
shows it (毎朝 → まいあさ), but that reading never reached the speech engine, so the sound could
disagree with the card.

Options discussed: speak the reading (chosen); the same plus Google Translate's voice; Google
Translate's voice only. Google's voice, given kanji alone, has to guess the reading too, so it does
not fix the cause by itself, needs the network, is unofficial and cannot ship in the F-Droid build.

## Goals

- Japanese words and phrases are spoken from the kana reading the app shows.
- Words without a reading (kana-only words, names the dictionary does not know, other languages)
  are spoken as before.

## Non-goals

- Changing the speech engine, voice choice, or adding an online voice.
- Improving Kuromoji's readings; a wrong dictionary reading is now at least the one on screen.

## User-visible behavior

Before: tapping the speaker on 毎朝 sent 毎朝 to the speech engine, which picked a reading itself.
After: it sends まいあさ. A phrase such as 毎朝ヨーグルトとバナナ is spoken as
まいあさヨーグルトとバナナ. Saved words use their stored reading only when it is kana, so a
romanized or pinyin reading saved by the assistant is never read aloud in place of the word.

## Technical constraints / invariants

- Pronunciation keeps using Android TextToSpeech and the one-word `PronunciationCache`; the cache
  and `forgetUnless` now key on the spoken text, which every caller passes consistently.
- Works offline and in both builds; no new dependency.

## Proposed approach / plan

1. `ui/Pronunciation.kt`: `spokenText(text, start, words)` replaces each word that has a kana reading
   inside the selected text, keeping everything in between; mismatched offsets fall back to the text
   as written. `spokenWord(word, reading)` does the same for a saved word.
2. Call them from `DualSubApp.SelectedWordDialog` (card and parts), `SelectableSubtitleText`
   (selection bar and auto-pronounce) and `SavedWordsScreen`.

## Acceptance criteria

- [x] 毎朝 analyzed in 昔は毎朝ヨーグルトとバナナを is spoken as まいあさ (`SpokenTextTest`).
- [x] A phrase replaces only words with readings and keeps the rest (`SpokenTextTest`).
- [x] Words without readings, mismatched offsets, and non-kana saved readings speak the text as
  written (`SpokenTextTest`).
- [x] Existing pronunciation fallback tests still pass (`PronunciationTest`).

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `testDebugUnitTest` incl. `SpokenTextTest`, `PronunciationTest` pass | Cloud container |
| Android lint/build | CI `verify-build` and the other three Android CI jobs pass | GitHub Actions |
| Physical-device/manual | Tap the speaker on a kanji word with a reading on the card; it says that reading | Owner's phone, preview APK |

## Risks / edge cases

- Kana alone can blur the pitch accent of homophones (箸 / 橋 both はし).
- IPADIC readings are spelling, not sound: 言う is read いう, which engines usually say as written.

## Release intent

`release:patch` (default): app behavior changes; shipped in the owner's combined PR for 2026-10-11.

## Implementation result

As planned.

## Validation result

- Passed (cloud container): `formatCheck`, `complexityCheck`, `lintDebug`, and the full
  `testDebugUnitTest` (81 suites, none failing), including `SpokenTextTest` (5) and
  `PronunciationTest` (16).
- Not run here: instrumented tests and the other CI jobs; see the combined PR's final-head CI.
- Not verified: listening on a physical phone.
