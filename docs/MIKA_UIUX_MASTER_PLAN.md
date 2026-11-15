# Mika Duet-inspired UI/UX implementation program

> Proposed component names use HTML code formatting; they are not claims about classes already present in Mika.

## Purpose

Transform the user's fork `Ichiro786/Mika` from its current utilitarian UI into an original, native Kotlin + Jetpack Compose personal agent with a calm Duet-inspired layout and the Orbit Spark identity system. **Protect existing agent reliability first.** This is a phased UI/UX project, not a backend rewrite.

## Entry rules

- Work on a fresh dedicated feature branch; do not discard local work.
- Verify current branch/commit; the Argon audit snapshot `4ade16b1...` may be stale. Audit findings are leads, not fact. Verify repo files before acting.
- Finish one phase, run gates, update progress log, commit coherent changes, and stop. Continue only when instructed by user.
- If an acceptance criterion cannot be checked (no device, missing SDK, unavailable credentials), label it **UNVERIFIED** and keep it as an open gate rather than inventing success.
- Visual prototypes and test fixtures may use synthetic data; the shipped app must not.
- All code must be tested against the actual app architecture; preserve `:core` and services by default.

## Phase 00 — Baseline and safety rails [START HERE]

**Files:** existing Gradle config, tests, UI navigation and relevant feature docs; add only program docs/screenshot baseline support where needed.

**Tasks:** `git status` + exact branch/HEAD; inspect repository guidance; inventory current routes, `ChatScreen`, theme, agent services, overlays; inspect current Compose/Material3 dependencies; capture current light/dark screens if emulator/device available; identify exact Gradle tasks; run existing baseline tests and build. Write a compact risk ledger for high-risk surfaces. Determine provider/login handling without logging tokens. Confirm `AGENTS.md` rules and docs in repo. Do not start theme or logo implementation in this phase.

**Exit:** baseline commit SHA and test results documented, build task identified, regression checklist documented, reference files accessible to Codex, no runtime files changed.

## Phase 01 — Tokens, Material foundation and isolated preview lab

**Scope:** `ui/theme` and isolated composable previews. Review Material 3 dependency compatibility before changing versions. Implement semantic warm light + opt-in true-black AMOLED color schemes, typography, shape, surface and motion tokens. Preserve public token aliases/call sites to keep compilation green. Build preview screen showing all tokens/components without rewriting production UI yet.

**Exit:** builds/tests; preview screenshots in both themes; contrast manually/automatically reviewed; original theme toggles still work; no agent-service changes.

## Phase 02 — Orbit Spark vector identity

**Scope:** canonical reusable Compose vector mark and adaptive launcher icon assets (where compatible), monochrome and light/dark variants. Develop geometry as explicit paths, iterate against identity board; no animated effects yet. Add previews for 24/40/64/128dp and launcher scaling.

**Exit:** unique stable symbol visually reviewed at small sizes and in both themes; adaptive-icon foreground safe; dark/light/monochrome previews; no raster board shipped as runtime logo.

## Phase 03 — Agent presence animations and state adapter

**Scope:** build <code>AgentPresenceState</code> and **one** `MikaPresenceAvatar` component. Use real UI-derived execution, voice, confirmation and error states. Animation should be low-overhead; idle/listening/thinking/working/needs-you/success/error transitions; reduced-motion static fallback and lifecycle pause. Provide deterministic animation test/previews before integrating globally.

**Exit:** mapping of all states to real code/events documented, screenshots or short motion clips captured, no indefinite rendering while offscreen, no fake listening/working activity, no changes to agent runtime.

## Phase 04 — Shell, information architecture and navigation

**Scope:** establish Chat / Tasks / Make primary navigation using existing destinations/contracts; preserve deep links, system back, self-operation tool route names, all settings/secondary entries, chat session state and overlay flows. Keep drawer/history accessible if needed. Use full-screen previews/stubs only for unreachable future UI; no dead tabs in release builds.

**Exit:** every tab leads to a real functioning destination; back/IME and scroll state restoration tested; all existing routes still reachable; screenshot of shell in both themes.

## Phase 05 — Chat home and compact composer

**Scope:** redesign empty chat state, greeting, suggestions and optional honest brief using existing data. Build bottom composer with plus, voice, attachments, send/stop, keyboard insets, and retained drafts. Keep all existing behaviors, including mid-run interjections; don't "fix" audit findings without verifying first.

**Exit:** visual comparison in both themes; attachment/voice/send works; no invented proactive data; 200% font scale check; keyboard and gesture bar do not overlap.

## Phase 06 — Conversation, Markdown and live activity

**Scope:** user and assistant message hierarchy, context actions, collapsed <code>AgentActivityCard</code>, source/trace access and live PiP placement. Preserve streaming Markdown, code, tables, branch/quote, queued messages, source citations, cancel/retry and auto-scroll. Profile only if jank exists before changing parser/flow architecture.

**Exit:** snapshot and runtime testing for long chat, active streaming, markdown table/code, scroll-up during streaming, history/branch/retry and full run inspector; regression evidence included.

## Phase 07 — Tasks and scheduled work

**Scope:** elegant grouped Tasks surface using real data from `PersonalAgentScreen`, `PlansScreen`, run/event stores and existing task statuses. Design Active/Up next, Ready for review/Working/Scheduled/Resolved based on actual semantics and accessible filters. Provide edit/pause/run-now only where existing action supports it. Empty states and refresh included.

**Exit:** task counts and statuses match stores; navigation to task details works; no fictional progress percentages; task scheduling and restart persistence work.

## Phase 08 — Approval and review cards

**Scope:** reuse existing confirm/takeover permissions and actions, present a compact inline review card. Clearly distinguish PREVIEW, NEEDS APPROVAL, SENT/COMPLETED, DECLINED; do not auto-send email, initiate payments, or approve external operations. Explicitly show recipient/target, payload, attachments, and consequences when available.

**Exit:** "Not now" does not execute, "Approve" connects only to authorized existing handler, state remains accurate after process recreation, TalkBack and confirmation semantics pass.

## Phase 09 — Make, skills and capability discovery

**Scope:** link only real existing functions (skills, Teach Mika, MCP connections, file library, browser, terminal where appropriate). Validate the label "Make" against actual workflow; do not imply unsupported artifact generation. Restructure settings/drawer discoverability without removing configuration paths.

**Exit:** each visible action has an existing functional destination, permissions remain gated, system UI/back navigation works.

## Phase 10 — Capsule, voice and system surfaces

**Scope:** apply Mika mark and presence avatar to `capsule/`, voice UI, notification icons, splash and active task chips. Keep overlay window boundaries/touch pass-through/IME avoidance and existing background automation stable; test on physical phone where possible. Avoid extending runtime to keep brand animation alive when not needed.

**Exit:** no overlay touch interception regression, phone helper/capsule self-check passes, visibility/permissions and mic semantics correct, no startup delay due to splash animation.

## Phase 11 — Secondary screens and accessibility

**Scope:** polish provider setup, memory, settings, browser, file library, trace inspector, scheduled-task editor and onboarding using same semantic tokens. Scope each screen as a small internal subtask. Audit localization, contrast, font scaling, RTL and reduced motion. No unrelated behavior modifications.

**Exit:** each screen has before/after capture, no clipped forms with keyboard, no broken localization, accessibility review and existing tests green.

## Phase 12 — Performance, polish and release candidate

**Scope:** compare against Phase 00 baseline, fix only measured UI regressions, profile cold start and streaming UI, test physical phone and supported API levels, create reproducible release candidate and list known limitations. Check upstream changes for rebase conflicts separately, don't merge them casually during UI work.

**Exit:** build passes, zero high-severity functional regressions, complete screenshots (light/dark/AMOLED), motion demos, QA table, final changelog and APK artifact if build environment supports it.

## Cross-phase regression list (run relevant subset after each phase)

- `:core:test`, app unit tests and a compatible assemble task as discovered in Phase 00.
- `tools/i18n/translations.py check` if present; all existing string placeholders and locale files.
- Chat send/stream, network failure, retry, history, branch/quote, attachments, voice/hold-to-talk, task running/interjection, pending approvals, models/auth.
- Scheduled tasks/alarm state and time-zone handling, app lifecycle recovery, memory/storage, provider model settings.
- Existing background virtual screen / PiP and capsule including keyboard & touch pass-through.
- Accessibility: TalkBack, 48dp targets, 200% font, high contrast, reduced motion.
- Visual: compare actual Android screenshots to reference boards; attach screenshots, do not claim rendering without capture.

## Phase handoff template

```markdown
PHASE: 0x — [name]
BASE COMMIT / BRANCH:
CHANGED FILES:
WHAT WAS IMPLEMENTED:
DESIGN REFERENCES CONSULTED:
BUILD/TEST COMMANDS + ACTUAL RESULTS:
ANDROID SCREENSHOT / SCREEN RECORDING PATHS:
MANUAL DEVICE CHECKS (PASS/FAIL/UNVERIFIED):
ENGINE/AGENT REGRESSION CHECKS:
GAPS AND CONCERNS:
COMMIT HASH:
NEXT PHASE: [scope only, do not start]
```
