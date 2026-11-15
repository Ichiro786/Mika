# prompts/: prompt text shipped with the app

All fixed text the model sees lives here and is no longer written as strings in code; it is read through `core.prompt.Prompts` / `core.prompt.Feedback`.
`{{name}}` in the text is filled in by the code that reads it (limits, allowed values, on-screen names and so on); a missing fill fails loudly.

## Layers

| Directory | What it is | Where it goes | Who reads it |
|---|---|---|---|
| `system/identity.md` | P0 identity and stance: act for the user, within what the user authorizes, look before acting, don't pretend, say when you don't know | start of the system prompt | `SystemPrompt.identity()` |
| `system/principles.md` | P1 general principles: whose words count, how to handle tasks, answering with evidence, obstacles, finding things, safety; true for every screen and every capability | right after identity | `SystemPrompt.identity()` |
| `capabilities/` | P2 capability descriptions, one section per capability. `general.md` holds the general tools (reporting failure, follow-up questions, confirmation) and the overall rules for how a task ends; general tools are always present, so it is always placed after the principles. `groups.md` says which entry each group of tools is reached through (listing only groups that this task gave an entry for). `handover.md` holds the handing over to the user and the checks before ordering or sending, present only once a group with a screen is expanded. The phone, web and sandbox sections are added only after that group is expanded. `app-access.md` holds the app-access rules and is appended after the phone section only when the user has disabled an app | system prompt | `SystemPrompt.assemble()` |
| `tools/` | P3 one description per tool (the description in the tool definition): how to use it, parameters, how to handle results; when several tools share one template, the tool states its `promptFile` | tool definitions | the tools themselves |
| `entries/` | short descriptions of entry tools while their group is collapsed (one or two sentences: what it does and which tools appear after calling it); once expanded they are replaced by the full text in `tools/`, with the same parameters | tool definitions | `ToolRegistry` |
| `feedback/` | feedback to the model from tool results and the main loop: hints, error explanations (`failure.<error code>`), reminders, source labels for reference material; one key per entry | tool results, context messages | tools, `AgentLoop` |
| `roles/` | prompts for internal roles: verification, screen description, locating, memory extraction, route matching, skill drafts, turning a demonstration into a skill, app-knowledge additions, outcome judging after an answer is handed over | each role's own request | each role |

App and site knowledge and skills are not here; see `knowledge/` and `skills/`.

## Where things go

- The higher up, the more general and neutral: system/ names no specific tool, app or technology; anything true only for one capability goes in that capability's description.
- Text tied to one tool (how to use it, what parameters mean, how to handle its results) goes in `tools/<tool>.md`, not in system/.
- Rules that hold only when a capability is present go in `capabilities/<capability>.md`, under the same switch as that group of tools.
  When a line in a section uses a tool from another capability, start it with `<!-- needs: tool name -->`: the line stays only if that tool is provided.
- `system/` names no tool (checked by a test; words like tap and back that are ordinary English words don't count).
- Explanatory text in tool results goes in `feedback/tools.md`, and what the main loop sends goes in `feedback/loop.md`; a key is unique across the two files, and every key must be used by code.
- Every feedback entry is self-contained: no leading or trailing whitespace, and code never joins it with other text (`+` or string templates); to combine, write the separator in the template and fill the other entry in with a `{{placeholder}}`.
- Within one task the system prompt changes only when a group of tools is expanded (so the cache is invalidated at most once per group); things that change every turn (step count, time, which app) do not belong in the fixed text here.

## Format

- `tools/`, `system/`, `capabilities/`, `roles/`: one file is one passage of text; leading and trailing whitespace is stripped on reading.
- `feedback/*.md`: may start with a `<!-- note -->`; each entry begins with a line `## key` on its own (keys use only lowercase letters, digits, `_` and `.`),
  and what follows up to the next entry is the text, which may span lines; leading and trailing blank lines don't count (so a separating newline must be written inside the template).

## Size

The byte counts of each layer (UTF-8) are in the golden file `core/src/test/resources/prompt-surface/sizes.txt`, for reference only with no hard limit:
growth after each change shows up in the committed diff. Before adding content, see whether old content can be removed; when it grows noticeably, explain why in the commit.

## After changing

All the text the model sees has golden files: `core/src/test/resources/prompt-surface/` (system prompts, tool definitions, feedback, roles) and
`loop-snapshots/` (message sequences from the fake-model replay). Regenerate intended changes with `./gradlew :core:test -PupdateSnapshots`
and review them together in the commit; for changes that affect model behavior, run the evaluation comparison first. `prompt_version` in the run record is a digest of this task's system prompt plus tool definitions;
use it to tell prompt versions apart when comparing evaluation results.

## Tool names

- Phone and web each have a set of screen tools (`phone_*`, `web_*`, same verbs and parameters); tools with the same verb share a `tools/<verb>.md` template, in which `{{verb}}` is replaced with this set's tool name;
  when a screen needs its own description, write `tools/<screen>_<verb>.md`.
- `capabilities/general.md` names no capability's tools (checked by a test); "which channel to choose" is in `channels.md`, added only when there are two or more screens.
- Tool names mentioned in knowledge, skills and prompts must all be currently registered (checked by a test). Old names from before a rename are replaced with the new ones by `LegacyNames` by rule,
  used only for reading old things (saved routes, run records, personal skills and methods); when the model calls an old name it is only told the new name and the call is not executed.
