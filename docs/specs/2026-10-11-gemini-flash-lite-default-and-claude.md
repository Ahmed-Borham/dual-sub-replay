# Gemini starts on Flash-Lite, and Claude keys work

## Status

Implemented. On 2026-10-11 the owner asked to discuss first: new Gemini keys should start on
`gemini-flash-lite-latest` (changeable later), `gemini-2.5-flash` should leave the quick list (still
choosable later), and Claude API keys should work. After the plan below they answered "yes do it",
which covers all three recommendations: existing default Gemini users keep `gemini-flash-latest`,
Claude defaults to Claude Haiku 5.5, and the app uses Anthropic's own Messages API. The owner also
asked for every fix of the day in one PR to test, so this PR carries the pronunciation thread's
change too. This does not authorize merging or publishing.

## Context / problem

- `AiProvider.GEMINI.defaultModel` was `gemini-flash-latest`; the quick list under the chat box was
  flash, flash-lite, `gemini-2.5-flash`. The owner's screenshot showed flash-lite ticked only because
  they had picked it; the menu lists the current model first (`ui/AiAssistantModels.kt`).
- A key check is stored as "address + model" (`aiCheckedSetup`). Changing the default for people
  who never chose a model would change their setup, so the chat would show the check card again.
- Claude was not offered. A pasted `sk-ant-` key matched the plain `sk-` rule and was taken for an
  OpenAI key.
- Every service spoke OpenAI's `chat/completions`. Anthropic offers an OpenAI-compatible address,
  but describes it as meant for testing and comparing models rather than production use; its own
  Messages API is the supported way.

## Goals

- New Gemini keys use `gemini-flash-lite-latest`; the quick list is flash-lite, then flash.
- People whose Gemini key was checked with `gemini-flash-latest` and who never chose a model keep it.
- Claude appears in the service picker and setup card; a pasted `sk-ant-` key selects it.
- Claude chats, reads pictures and PDFs, takes the app's actions, follows the thinking menu, and
  lists its models on the model page.

## Non-goals

- No change to other services, the key store, chat history, memory, or the action list.
- No streaming, prompt caching or Anthropic-only features; no server or shared key.
- No change to the F-Droid build, which has no assistant.

## User-visible behavior

- **Gemini, new key:** the model button shows `gemini-flash-lite-latest`. The menu offers it and
  `gemini-flash-latest`; `gemini-2.5-flash` and every other model are under "All Google Gemini
  models…". Anyone who already chose `gemini-2.5-flash` keeps it (the current model is listed first).
- **Gemini, existing key on the old default:** nothing changes; the model stays `gemini-flash-latest`
  and the chat stays ready.
- **Claude:** "Claude — Paid, from Anthropic" in the service picker. Setup steps: tap "Get a key"
  (opens platform.claude.com/settings/keys), sign in and add credit, tap "Create Key", copy it, paste.
  Default model `claude-haiku-5-5`; quick list Haiku 5.5, Sonnet 5.5, Opus 5.5; "All Claude models…"
  lists the account's models. Thinking Low/Medium/High is sent as Anthropic's effort level; Auto
  sends none.
- **Claude errors:** a low credit balance shows the "limit is used up / needs credit" message;
  "overloaded" (529) is retried like any busy service; a refusal shows the unreadable-reply message
  with "Claude declined to answer this question." in the details.

## Technical constraints / invariants

- Assistant invariants in [AGENTS.md](../../AGENTS.md) and the
  [AI assistant spec](2026-10-08-ai-assistant-panel.md): the user's own key only, HTTPS only, no
  redirects, 1 MiB reply cap, the key unlocked per request and never logged.
- Claude's newer models tie each thinking block to the conversation before it. The app keeps one
  answer's requests append-only (same system prompt and tools, messages only added) and never saves
  thinking blocks into chat history, so no edited history carries a thinking block.
- Interface text in `strings_ai.xml` for every language (`AppLanguageTest`, lint `MissingTranslation`).

## Proposed approach / plan

1. `AiProvider`: Gemini default and quick list; new `CLAUDE` entry with `AiWireFormat.ANTHROPIC_MESSAGES`;
   `sk-ant-` detection before the plain `sk-` rules.
2. `aiModelsKeptFromChecks` (in `AiSettings.kt`), applied when the controller loads: a key checked
   with a model other than today's default, while no model was chosen, keeps that model.
3. `AiAnthropicMessages.kt`: request body (top-level `system`, `max_tokens`, `output_config.effort`,
   tools with `input_schema`, base64 image/document blocks, same-role messages merged, tool results
   in one user turn) and reply parsing (text, `tool_use` calls, the raw content kept when actions
   were asked for so it goes back unchanged).
4. `AiHttpTransport` (renamed from `OpenAiCompatibleTransport`) picks the address, headers
   (`x-api-key`, `anthropic-version`) and parser by format; the model list asks for one page of 1000.
5. Strings in all eight languages; README, tech stack and AGENTS.md list Claude.

## Acceptance criteria

- [x] A new Gemini key is checked and used with `gemini-flash-lite-latest`
  (`AiAssistantControllerTest.aPastedKeyPicksItsServiceAndTestsTheConnection`).
- [x] The Gemini quick list is flash-lite then flash, without `gemini-2.5-flash`
  (`AiSettingsTest`, `AiAssistantPanelUiTest.theBarUnderTheChatBoxChangesTheModelAndThinkingLevel`).
- [x] A Gemini key checked with `gemini-flash-latest` and no chosen model keeps that model and stays
  ready (`AiAssistantControllerTest.aGeminiKeyCheckedWithTheOldDefaultKeepsThatModel`).
- [x] An `sk-ant-` key selects Claude from any other service except Other, and is checked through the
  Messages format with `claude-haiku-5-5` (`AiSettingsTest`, `AiAssistantControllerTest`).
- [x] Claude requests go to `https://api.anthropic.com/v1/messages` with `x-api-key` and
  `anthropic-version`, never a Bearer header; other services keep Bearer (`AiAnthropicMessagesTest`).
- [x] An answer with actions goes back unchanged, thinking block included, and all action results
  return in one user turn (`AiAnthropicMessagesTest`).
- [x] Claude appears in the setup card with its note and steps (`AiAssistantPanelUiTest`).
- [ ] A real Claude key answers a question and runs an action on a phone (owner, preview APK).

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `testDebugUnitTest` passes, including the new Anthropic, settings and controller tests | Cloud container, CI |
| Android lint/build | `formatCheck complexityCheck lintDebug assembleDebug assembleDebugAndroidTest` pass | Cloud container, CI |
| Managed-device/emulator | `AiAssistantPanelUiTest` passes on `pixel2Api36` | CI `managed-device-tests` |
| Physical-device/manual | Paste a Claude key, ask a question, ask it to change the speed; a new Gemini key shows flash-lite | Owner, preview APK |
| Live YouTube | Not applicable | — |
| Documentation/process | README, tech stack, AGENTS.md list Claude | This PR |

## Risks / edge cases

- Anthropic's model names change; the defaults live in `AiProvider.kt` and any model can be typed.
- Claude has no free keys; the setup step says to add credit first.
- A Claude model that refuses an effort level returns 400 naming "effort", shown as the
  unsupported-thinking message (switch Thinking to Auto).

## Release intent

`release:patch` — the standing default; the owner did not ask for a minor.

## Implementation result

As planned. `OpenAiCompatibleTransport` was renamed `AiHttpTransport`, and `AiModelLister.listModels`
takes the wire format. A 400 naming "credit balance" or "billing" now counts as no credit for every
service, and "effort" joins the words that mean the thinking level was refused.

## Validation result

See the PR for final-head CI. Local results are recorded in the PR description.
